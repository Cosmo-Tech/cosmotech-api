// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.service

import com.cosmotech.api.CsmPhoenixService
import com.cosmotech.api.events.ConnectorRemoved
import com.cosmotech.api.events.OrganizationUnregistered
import com.cosmotech.api.events.TwingraphImportEvent
import com.cosmotech.api.events.TwingraphImportJobInfoRequest
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.exceptions.CsmClientException
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.rbac.CsmRbac
import com.cosmotech.api.rbac.PERMISSION_READ
import com.cosmotech.api.security.ROLE_PLATFORM_ADMIN
import com.cosmotech.api.security.coroutine.SecurityCoroutineContext
import com.cosmotech.api.utils.changed
import com.cosmotech.api.utils.compareToAndMutateIfNeeded
import com.cosmotech.api.utils.constructPageRequest
import com.cosmotech.api.utils.findAllPaginated
import com.cosmotech.api.utils.getCurrentAuthenticatedRoles
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.api.utils.unzip
import com.cosmotech.connector.api.ConnectorApiService
import com.cosmotech.dataset.api.DatasetApiService
import com.cosmotech.dataset.bulk.QueryBuffer
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.dataset.domain.DatasetCompatibility
import com.cosmotech.dataset.domain.DatasetConnector
import com.cosmotech.dataset.domain.DatasetCopyParameters
import com.cosmotech.dataset.domain.DatasetSearch
import com.cosmotech.dataset.domain.DatasetSourceType
import com.cosmotech.dataset.domain.DatasetTwinGraphInfo
import com.cosmotech.dataset.domain.DatasetTwinGraphQuery
import com.cosmotech.dataset.domain.SubDatasetGraphQuery
import com.cosmotech.dataset.domain.TwinGraphBatchResult
import com.cosmotech.dataset.repository.DatasetRepository
import com.cosmotech.dataset.utils.toJsonString
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.service.getRbac
import com.redislabs.redisgraph.RedisGraph
import java.io.InputStream
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVRecord
import org.springframework.context.event.EventListener
import org.springframework.core.io.Resource
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import redis.clients.jedis.JedisPool

const val GRAPH_NAME = "graphName"
const val GRAPH_ROTATION = "graphRotation"
const val NODES_ZIP_FOLDER = "nodes"
const val EDGES_ZIP_FOLDER = "edges"

@Service
@Suppress("TooManyFunctions")
class DatasetServiceImpl(
    private val connectorService: ConnectorApiService,
    private val organizationService: OrganizationApiService,
    private val datasetRepository: DatasetRepository,
    private val csmJedisPool: JedisPool,
    private val csmRedisGraph: RedisGraph,
    private val csmRbac: CsmRbac,
) : CsmPhoenixService(), DatasetApiService {

  override fun findAllDatasets(organizationId: String, page: Int?, size: Int?): List<Dataset> {
    val defaultPageSize = csmPlatformProperties.twincache.dataset.defaultPageSize
    val pageable = constructPageRequest(page, size, defaultPageSize)
    if (pageable != null) {
      return datasetRepository.findByOrganizationId(organizationId, pageable).toList()
    }
    return findAllPaginated(defaultPageSize) {
      datasetRepository.findByOrganizationId(organizationId, it).toList()
    }
  }

  override fun findDatasetById(organizationId: String, datasetId: String): Dataset =
      datasetRepository.findById(datasetId).orElseThrow {
        CsmResourceNotFoundException("Dataset $datasetId not found in organization $organizationId")
      }

  override fun removeAllDatasetCompatibilityElements(organizationId: String, datasetId: String) {
    val dataset = findDatasetById(organizationId, datasetId)
    if (!dataset.compatibility.isNullOrEmpty()) {
      dataset.compatibility = mutableListOf()
      datasetRepository.save(dataset)
    }
  }

  override fun createDataset(organizationId: String, dataset: Dataset): Dataset {
    dataset.takeUnless { it.name.isNullOrBlank() }
        ?: throw IllegalArgumentException("Name cannot be null or blank")

    dataset.takeUnless {
      dataset.sourceType in listOf(DatasetSourceType.ADT, DatasetSourceType.Storage) &&
          dataset.source == null
    }
        ?: throw IllegalArgumentException(
            "Source cannot be null for source type 'ADT' or 'Storage'")

    val twingraphId = idGenerator.generate("twingraph")
    if (dataset.sourceType != null) {
      dataset.connector =
          DatasetConnector(
              id = csmPlatformProperties.twincache.connectorId,
              parametersValues = mutableMapOf("TWIN_CACHE_NAME" to twingraphId))
    }

    dataset.takeUnless { it.connector == null || dataset.connector!!.id.isNullOrBlank() }
        ?: throw IllegalArgumentException("Connector or its ID cannot be null or blank")

    val existingConnector = connectorService.findConnectorById(dataset.connector!!.id!!)
    logger.debug("Found connector: {}", existingConnector)

    val datasetCopy =
        dataset.copy(
            id = idGenerator.generate("dataset"),
            twingraphId = twingraphId,
            sourceType = dataset.sourceType ?: DatasetSourceType.File,
            status = Dataset.Status.DRAFT,
            ownerId = getCurrentAuthenticatedUserName(csmPlatformProperties),
            organizationId = organizationId)
    datasetCopy.connector!!.apply {
      name = existingConnector.name
      version = existingConnector.version
    }
    return datasetRepository.save(datasetCopy)
  }

  override fun createSubDataset(
      organizationId: String,
      datasetId: String,
      subDatasetGraphQuery: SubDatasetGraphQuery
  ): Dataset {
    val dataset = findDatasetById(organizationId, datasetId)
    dataset.takeUnless { it.twingraphId.isNullOrBlank() }
        ?: throw IllegalArgumentException("TwingraphId is not defined for the dataset")

    csmJedisPool.resource.use { jedis ->
      val graphDump =
          jedis.dump(dataset.twingraphId!!)
              ?: throw CsmResourceNotFoundException(
                  "Twingraph ${dataset.twingraphId!!} " + "not found for dataset $datasetId")

      val subTwinraphId = idGenerator.generate("twingraph")
      jedis.restore(subTwinraphId.toByteArray(), 0L, graphDump)
      subDatasetGraphQuery.query?.let { csmRedisGraph.query(datasetId, it) }
      val subDataset =
          dataset.copy(
              id = idGenerator.generate("dataset"),
              name = subDatasetGraphQuery.name ?: ("Subdataset " + dataset.name),
              description = subDatasetGraphQuery.description ?: dataset.description,
              ownerId = getCurrentAuthenticatedUserName(csmPlatformProperties),
              organizationId = organizationId,
              twingraphId = subTwinraphId,
              parentId = dataset.id,
              connector = dataset.connector,
              sourceType = dataset.sourceType,
              tags = dataset.tags)
      return datasetRepository.save(subDataset)
    }
  }

  override fun uploadTwingraph(organizationId: String, datasetId: String, body: Resource) {
    val archiverType = ArchiveStreamFactory.detect(body.inputStream.buffered())
    archiverType.takeIf { it == ArchiveStreamFactory.ZIP }
        ?: throw IllegalArgumentException(
            "Invalid archive type: '$archiverType'. A Zip Archive is expected.")

    val dataset = findDatasetById(organizationId, datasetId)
    dataset.sourceType.takeIf { it == DatasetSourceType.File }
        ?: throw CsmResourceNotFoundException("SourceType Dataset must be 'File'")
    val twinGraphId =
        dataset.twingraphId
            ?: throw CsmResourceNotFoundException("TwingraphId is not defined for the dataset")
    val uploadStatus = getDatasetTwingraphStatus(organizationId, datasetId, twinGraphId)
    uploadStatus.takeUnless { it == Dataset.Status.PENDING.value }
        ?: throw CsmResourceNotFoundException("Uploading not yet completed. Wait. Cannot update")

    dataset.status = Dataset.Status.PENDING
    datasetRepository.save(dataset)

    GlobalScope.launch(SecurityCoroutineContext()) {
      val queryBuffer = QueryBuffer(csmJedisPool.resource, dataset.twingraphId!!)
      unzip(body.inputStream, listOf(NODES_ZIP_FOLDER, EDGES_ZIP_FOLDER), "csv")
          .sortedByDescending { it.prefix } // Nodes first
          .forEach { node ->
            readCSV(node.content.inputStream()) {
              if (node.prefix == NODES_ZIP_FOLDER) {
                queryBuffer.addNode(node.filename, it)
              } else {
                queryBuffer.addEdge(node.filename, it)
              }
            }
          }
      queryBuffer.send()
    }
  }

  override fun getDatasetTwingraphStatus(
      organizationId: String,
      datasetId: String,
      jobId: String
  ): String {
    val dataset = findDatasetById(organizationId, datasetId)
    return when (dataset.sourceType) {
      null -> Dataset.Status.DRAFT.value
      DatasetSourceType.File -> {
        csmJedisPool.resource.use { jedis ->
          if (!jedis.exists(dataset.twingraphId!!)) {
            Dataset.Status.PENDING.value
          } else {
            dataset
                .takeIf { it.status == Dataset.Status.PENDING }
                ?.apply {
                  this.status = Dataset.Status.COMPLETED
                  datasetRepository.save(this)
                }
            Dataset.Status.COMPLETED.value
          }
        }
      }
      DatasetSourceType.ADT,
      DatasetSourceType.Storage -> {
        val twingraphImportJobInfoRequest =
            TwingraphImportJobInfoRequest(this, jobId, organizationId)
        this.eventPublisher.publishEvent(twingraphImportJobInfoRequest)
        dataset
            .takeIf { it.status == Dataset.Status.PENDING }
            ?.apply {
              when (twingraphImportJobInfoRequest.response) {
                "Succeeded" -> status = Dataset.Status.COMPLETED
                "Error" -> status = Dataset.Status.ERROR
              }
              datasetRepository.save(this)
            }
        twingraphImportJobInfoRequest.response ?: "Unknown"
      }
    }
  }

  override fun refreshDataset(organizationId: String, datasetId: String): DatasetTwinGraphInfo {
    val dataset = findDatasetById(organizationId, datasetId)
    dataset.takeUnless { it.sourceType == DatasetSourceType.File }
        ?: throw CsmResourceNotFoundException("Cannot be applied to source type 'File'")
    dataset
        .takeUnless { it.status == Dataset.Status.PENDING }
        ?.apply {
          this.status = Dataset.Status.PENDING
          datasetRepository.save(this)
        }

    val requestJobId = this.idGenerator.generate(scope = "graphdataimport", prependPrefix = "gdi-")
    val graphImportEvent =
        TwingraphImportEvent(
            this,
            requestJobId,
            organizationId,
            dataset.twingraphId
                ?: throw CsmResourceNotFoundException("TwingraphId is not defined for the dataset"),
            dataset.source!!.name ?: "",
            dataset.source!!.location,
            dataset.source!!.path ?: "",
            dataset.sourceType!!.value,
            "1")
    this.eventPublisher.publishEvent(graphImportEvent)
    logger.debug("refreshDataset={}", graphImportEvent.response)
    return DatasetTwinGraphInfo(
        jobId = requestJobId, datasetId = dataset.id, status = dataset.status?.value)
  }

  override fun deleteDataset(organizationId: String, datasetId: String) {
    val dataset = findDatasetById(organizationId, datasetId)
    val isPlatformAdmin =
        getCurrentAuthenticatedRoles(csmPlatformProperties).contains(ROLE_PLATFORM_ADMIN)
    if (dataset.ownerId != getCurrentAuthenticatedUserName(csmPlatformProperties) &&
        !isPlatformAdmin) {
      // TODO Only the owner or an admin should be able to perform this operation
      throw CsmAccessForbiddenException("You are not allowed to delete this Resource")
    }
    datasetRepository.delete(dataset)
    csmJedisPool.resource.use { jedis ->
      if (jedis.exists(dataset.twingraphId!!)) {
        jedis.del(dataset.twingraphId!!)
      }
    }
  }

  override fun updateDataset(organizationId: String, datasetId: String, dataset: Dataset): Dataset {
    val existingDataset = findDatasetById(organizationId, datasetId)

    var hasChanged =
        existingDataset
            .compareToAndMutateIfNeeded(dataset, excludedFields = arrayOf("ownerId", "connector"))
            .isNotEmpty()

    if (dataset.ownerId != null && dataset.changed(existingDataset) { ownerId }) {
      val isPlatformAdmin =
          getCurrentAuthenticatedRoles(csmPlatformProperties).contains(ROLE_PLATFORM_ADMIN)
      // Allow to change the ownerId as well, but only the owner can transfer the ownership
      if (existingDataset.ownerId != getCurrentAuthenticatedUserName(csmPlatformProperties) &&
          !isPlatformAdmin) {
        // TODO Only the owner or an admin should be able to perform this operation
        throw CsmAccessForbiddenException(
            "You are not allowed to change the ownership of this Resource")
      }
      existingDataset.ownerId = dataset.ownerId
      hasChanged = true
    }

    if (dataset.connector != null && dataset.changed(existingDataset) { connector }) {
      // Validate connector ID
      if (dataset.connector?.id.isNullOrBlank()) {
        throw IllegalArgumentException("Connector ID is null or blank")
      }
      val existingConnector = connectorService.findConnectorById(dataset.connector!!.id!!)
      logger.debug("Found connector: {}", existingConnector)

      existingDataset.connector = dataset.connector
      existingDataset.connector!!.apply {
        name = existingConnector.name
        version = existingConnector.version
      }
      hasChanged = true
    }

    return if (hasChanged) {
      datasetRepository.save(existingDataset)
    } else {
      existingDataset
    }
  }

  override fun twingraphQuery(
      organizationId: String,
      datasetId: String,
      datasetTwinGraphQuery: DatasetTwinGraphQuery
  ): String {
    val dataset = findDatasetById(organizationId, datasetId)
    dataset.takeUnless { it.twingraphId.isNullOrBlank() }
        ?: throw CsmResourceNotFoundException("TwingraphId is not defined for the dataset")
    val resultSet =
        csmRedisGraph.query(
            dataset.twingraphId!!,
            datasetTwinGraphQuery.query,
            csmPlatformProperties.twincache.queryTimeout)
    return resultSet.toJsonString()
  }

  override fun addOrReplaceDatasetCompatibilityElements(
      organizationId: String,
      datasetId: String,
      datasetCompatibility: List<DatasetCompatibility>
  ): List<DatasetCompatibility> {
    if (datasetCompatibility.isEmpty()) {
      return datasetCompatibility
    }

    val existingDataset = findDatasetById(organizationId, datasetId)
    val datasetCompatibilityMap =
        existingDataset.compatibility
            ?.associateBy { "${it.solutionKey}-${it.minimumVersion}-${it.maximumVersion}" }
            ?.toMutableMap()
            ?: mutableMapOf()
    datasetCompatibilityMap.putAll(
        datasetCompatibility
            .filter { it.solutionKey.isNotBlank() }
            .associateBy { "${it.solutionKey}-${it.minimumVersion}-${it.maximumVersion}" })
    existingDataset.compatibility = datasetCompatibilityMap.values.toMutableList()
    datasetRepository.save(existingDataset)

    return datasetCompatibility
  }

  override fun copyDataset(
      organizationId: String,
      datasetCopyParameters: DatasetCopyParameters
  ): DatasetCopyParameters {
    TODO("Not yet implemented")
  }

  override fun searchDatasets(
      organizationId: String,
      datasetSearch: DatasetSearch,
      page: Int?,
      size: Int?,
  ): List<Dataset> {
    val defaultPageSize = csmPlatformProperties.twincache.dataset.defaultPageSize
    var pageable = constructPageRequest(page, size, defaultPageSize)
    if (pageable != null) {
      return datasetRepository
          .findDatasetByTags(datasetSearch.datasetTags.toSet(), pageable)
          .toList()
    }
    return findAllPaginated(defaultPageSize) {
      datasetRepository.findDatasetByTags(datasetSearch.datasetTags.toSet(), it).toList()
    }
  }

  @EventListener(OrganizationUnregistered::class)
  @Async("csm-in-process-event-executor")
  fun onOrganizationUnregistered(organizationUnregistered: OrganizationUnregistered) {
    var pageable = PageRequest.ofSize(csmPlatformProperties.twincache.dataset.defaultPageSize)
    do {
      val datasetList =
          datasetRepository
              .findByOrganizationId(organizationUnregistered.organizationId, pageable)
              .toList()
      datasetRepository.deleteAll(datasetList)
      pageable = pageable.next()
    } while (datasetList.isNotEmpty())
  }

  @EventListener(ConnectorRemoved::class)
  @Async("csm-in-process-event-executor")
  fun onConnectorRemoved(connectorRemoved: ConnectorRemoved) {
    val connectorId = connectorRemoved.connectorId
    var pageable = PageRequest.ofSize(csmPlatformProperties.twincache.dataset.defaultPageSize)
    do {
      val datasetList = datasetRepository.findDatasetByConnectorId(connectorId, pageable).toList()
      datasetList.forEach {
        it.connector = null
        datasetRepository.save(it)
      }
      pageable = pageable.next()
    } while (datasetList.isNotEmpty())
  }

  private fun readCSV(
      inputStream: InputStream,
      result: TwinGraphBatchResult? = null,
      actionLambda: (Map<String, String>) -> Unit
  ): Map<String, String> {
    var map = mapOf<String, String>()
    inputStream.bufferedReader().use { reader ->
      val csvFormat: CSVFormat = CSVFormat.DEFAULT.builder().setHeader().setTrim(true).build()

      val records: Iterable<CSVRecord> = csvFormat.parse(reader)
      records.forEach { record ->
        map = record.parser.headerNames.zip(record.values()).toMap()
        actionLambda(map)
        result?.let { result.totalLines++ }
      }
    }
    return map
  }

  fun updateGraphMetaData(
      organizationId: String,
      graphId: String,
      requestBody: Map<String, String>
  ): Any {
    val organization = organizationService.findOrganizationById(organizationId)
    csmRbac.verify(organization.getRbac(), PERMISSION_READ)

    val graphRotation = requestBody[GRAPH_ROTATION]?.toInt()
    if (graphRotation != null && graphRotation < 1) {
      throw CsmClientException("GraphRotation should be a positive integer")
    }

    csmJedisPool.resource.use { jedis ->
      if (jedis.exists(graphId)) {
        requestBody
            .filterKeys { it == GRAPH_NAME || it == GRAPH_ROTATION }
            .forEach { (key, value) -> jedis.hset(graphId, key, value) }
        return jedis.hgetAll(graphId)
      }
      throw CsmResourceNotFoundException("No metadata found for graphId $graphId")
    }
  }

  fun getGraphMetaData(graphId: String): Map<String, String> {
    csmJedisPool.resource.use { jedis ->
      if (jedis.exists(graphId)) {
        return jedis.hgetAll(graphId)
      }
      throw CsmResourceNotFoundException("No metadata found for graphId $graphId")
    }
  }

  override fun importDataset(organizationId: String, dataset: Dataset): Dataset {
    if (dataset.id == null) {
      throw CsmResourceNotFoundException("Dataset or Organization id is null")
    }
    return datasetRepository.save(dataset)
  }
}
