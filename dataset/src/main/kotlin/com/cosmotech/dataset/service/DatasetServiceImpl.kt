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
import com.cosmotech.api.rbac.PERMISSION_DELETE
import com.cosmotech.api.rbac.PERMISSION_READ
import com.cosmotech.api.security.ROLE_PLATFORM_ADMIN
import com.cosmotech.api.security.coroutine.SecurityCoroutineContext
import com.cosmotech.api.utils.ResourceScanner
import com.cosmotech.api.utils.bulkQueryKey
import com.cosmotech.api.utils.changed
import com.cosmotech.api.utils.compareToAndMutateIfNeeded
import com.cosmotech.api.utils.constructPageRequest
import com.cosmotech.api.utils.findAllPaginated
import com.cosmotech.api.utils.formatQuery
import com.cosmotech.api.utils.getCurrentAuthenticatedRoles
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.api.utils.unzip
import com.cosmotech.api.utils.zipBytesWithFileNames
import com.cosmotech.connector.api.ConnectorApiService
import com.cosmotech.connector.domain.Connector
import com.cosmotech.connector.domain.ConnectorParameter
import com.cosmotech.connector.domain.ConnectorParameterGroup
import com.cosmotech.dataset.api.DatasetApiService
import com.cosmotech.dataset.bulk.QueryBuffer
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.dataset.domain.DatasetCompatibility
import com.cosmotech.dataset.domain.DatasetConnector
import com.cosmotech.dataset.domain.DatasetCopyParameters
import com.cosmotech.dataset.domain.DatasetSearch
import com.cosmotech.dataset.domain.DatasetSourceType
import com.cosmotech.dataset.domain.DatasetTwinGraphHash
import com.cosmotech.dataset.domain.DatasetTwinGraphInfo
import com.cosmotech.dataset.domain.DatasetTwinGraphQuery
import com.cosmotech.dataset.domain.GraphProperties
import com.cosmotech.dataset.domain.SubDatasetGraphQuery
import com.cosmotech.dataset.domain.TwinGraphBatchResult
import com.cosmotech.dataset.repository.DatasetRepository
import com.cosmotech.dataset.utils.CsmGraphEntityType
import com.cosmotech.dataset.utils.isReadOnlyQuery
import com.cosmotech.dataset.utils.toCsmGraphEntity
import com.cosmotech.dataset.utils.toJsonString
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.service.getRbac
import com.redislabs.redisgraph.Record
import com.redislabs.redisgraph.RedisGraph
import com.redislabs.redisgraph.ResultSet
import com.redislabs.redisgraph.graph_entities.Edge
import com.redislabs.redisgraph.graph_entities.Node
import java.io.InputStream
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVRecord
import org.springframework.context.event.EventListener
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.data.domain.PageRequest
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import redis.clients.jedis.JedisPool
import redis.clients.jedis.exceptions.JedisDataException

const val TYPE_NODE = "node"
const val TYPE_RELATIONSHIP = "relationship"
const val NODES_ZIP_FOLDER = "nodes"
const val EDGES_ZIP_FOLDER = "edges"
const val TWINCACHE_CONNECTOR = "TwincacheConnector"
const val TWINCACHE_NAME = "TWIN_CACHE_NAME"

@Service
@Suppress("TooManyFunctions", "LongParameterList", "LargeClass")
class DatasetServiceImpl(
    private val connectorService: ConnectorApiService,
    private val organizationService: OrganizationApiService,
    private val datasetRepository: DatasetRepository,
    private val csmJedisPool: JedisPool,
    private val csmRedisGraph: RedisGraph,
    private val csmRbac: CsmRbac,
    private val resourceScanner: ResourceScanner
) : CsmPhoenixService(), DatasetApiService {

  override fun findAllDatasets(organizationId: String, page: Int?, size: Int?): List<Dataset> {
    val organization = organizationService.findOrganizationById(organizationId)
    csmRbac.verify(organization.getRbac(), PERMISSION_READ)

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
      val twincacheConnector = getCreateTwincacheConnector()
      dataset.connector =
          DatasetConnector(
              id = twincacheConnector.id,
              parametersValues = mutableMapOf(TWINCACHE_NAME to twingraphId))
    }

    dataset.takeUnless { it.connector == null || dataset.connector!!.id.isNullOrBlank() }
        ?: throw IllegalArgumentException("Connector or its ID cannot be null or blank")

    val existingConnector = connectorService.findConnectorById(dataset.connector!!.id!!)
    logger.debug("Found connector: {}", existingConnector)

    val datasetCopy =
        dataset.copy(
            id = idGenerator.generate("dataset"),
            twingraphId = twingraphId,
            sourceType = dataset.sourceType ?: DatasetSourceType.None,
            main = dataset.main ?: true,
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
    val dataset = getDatasetWithStatus(organizationId, datasetId, status = Dataset.Status.READY)

    val subTwinraphId = idGenerator.generate("twingraph")
    trx(dataset) {
      csmJedisPool.resource.use { jedis ->
        if (subDatasetGraphQuery.queries.isNullOrEmpty()) {
          jedis.eval(
              "local o = redis.call('DUMP', KEYS[1]);redis.call('RENAME', KEYS[1], KEYS[2]);" +
                  "redis.call('RESTORE', KEYS[1], 0, o)",
              2,
              dataset.twingraphId,
              subTwinraphId)
        } else {
          val queryBuffer = QueryBuffer(csmJedisPool.resource, subTwinraphId)
          subDatasetGraphQuery.queries?.forEach { query ->
            val resultSet = query(dataset, query)
            query.takeIf { it.isReadOnlyQuery() }?.apply { bulkQueryResult(queryBuffer, resultSet) }
          }
          queryBuffer.send()
        }
      }
    }
    val subDataset =
        dataset.copy(
            id = idGenerator.generate("dataset"),
            name = subDatasetGraphQuery.name ?: ("Subdataset " + dataset.name),
            description = subDatasetGraphQuery.description ?: dataset.description,
            ownerId = getCurrentAuthenticatedUserName(csmPlatformProperties),
            organizationId = organizationId,
            twingraphId = subTwinraphId,
            queries = subDatasetGraphQuery.queries,
            main = subDatasetGraphQuery.main ?: false,
            parentId = dataset.id,
            status = Dataset.Status.READY,
            connector =
                dataset.connector?.apply { parametersValues?.set(TWINCACHE_NAME, subTwinraphId) },
            sourceType = dataset.sourceType,
            tags = dataset.tags)
    return datasetRepository.save(subDataset)
  }

  fun bulkQueryResult(queryBuffer: QueryBuffer, resultSet: ResultSet) {

    resultSet.forEach { record: Record? ->
      record?.values()?.forEach { element ->
        when (element) {
          is Node -> {
            val csmGraphEntity = element.toCsmGraphEntity(CsmGraphEntityType.NODE)
            queryBuffer.addNode(csmGraphEntity.label, csmGraphEntity.id, csmGraphEntity.properties)
          }
          is Edge -> {
            val csmGraphEntity = element.toCsmGraphEntity(CsmGraphEntityType.RELATION)
            queryBuffer.addEdge(
                csmGraphEntity.label,
                element.source,
                element.destination,
                csmGraphEntity.properties)
          }
          else -> throw CsmClientException("Query doesn't match Node, either Edge")
        }
      }
    }
  }

  override fun uploadTwingraph(organizationId: String, datasetId: String, body: Resource) {
    val archiverType = ArchiveStreamFactory.detect(body.inputStream.buffered())
    archiverType.takeIf { it == ArchiveStreamFactory.ZIP }
        ?: throw IllegalArgumentException(
            "Invalid archive type: '$archiverType'. A Zip Archive is expected.")

    val dataset = getDatasetWithStatus(organizationId, datasetId)
    dataset.sourceType.takeIf { it == DatasetSourceType.File }
        ?: throw CsmResourceNotFoundException("SourceType Dataset must be 'File'")
    val uploadStatus = getDatasetTwingraphStatus(organizationId, datasetId, null)
    uploadStatus.takeUnless { it == Dataset.Status.PENDING.value }
        ?: throw CsmResourceNotFoundException("Dataset in use, cannot update. Retry later")

    datasetRepository.save(dataset.apply { status = Dataset.Status.PENDING })

    GlobalScope.launch(SecurityCoroutineContext()) {
      val queryBuffer = QueryBuffer(csmJedisPool.resource, dataset.twingraphId!!)
      unzip(body.inputStream, listOf(NODES_ZIP_FOLDER, EDGES_ZIP_FOLDER), "csv")
          .sortedByDescending { it.prefix } // Nodes first
          .forEach { node ->
            readCSV(node.content.inputStream()) {
              if (node.prefix == NODES_ZIP_FOLDER) {
                val id: String =
                    it["id"] ?: throw CsmResourceNotFoundException("Node property 'id' not found")
                queryBuffer.addNode(node.filename, id, it.minus("id"))
              } else {
                val sourceKey = it.keys.elementAt(0)
                val targetKey = it.keys.elementAt(1)
                val source = it[sourceKey].toString().trim()
                val target = it[targetKey].toString().trim()
                val properties = it.minus(sourceKey).minus(targetKey)
                queryBuffer.addEdge(node.filename, source, target, properties)
              }
            }
          }
      queryBuffer.send()
      datasetRepository.save(dataset.apply { status = Dataset.Status.READY })
    }
  }

  override fun getDatasetTwingraphStatus(
      organizationId: String,
      datasetId: String,
      jobId: String?
  ): String {
    val dataset = findDatasetById(organizationId, datasetId)
    return when (dataset.sourceType) {
      null,
      DatasetSourceType.None -> Dataset.Status.DRAFT.value
      DatasetSourceType.File -> {
        if (dataset.status == Dataset.Status.DRAFT) {
          return Dataset.Status.DRAFT.value
        }
        csmJedisPool.resource.use { jedis ->
          if (!jedis.exists(dataset.twingraphId!!)) {
            Dataset.Status.PENDING.value
          } else {
            dataset
                .takeIf { it.status == Dataset.Status.PENDING }
                ?.apply { datasetRepository.save(this.apply { status = Dataset.Status.READY }) }
            Dataset.Status.READY.value
          }
        }
      }
      DatasetSourceType.ADT,
      DatasetSourceType.Storage -> {
        val twingraphImportJobInfoRequest =
            TwingraphImportJobInfoRequest(this, jobId!!, organizationId)
        this.eventPublisher.publishEvent(twingraphImportJobInfoRequest)
        dataset
            .takeIf { it.status == Dataset.Status.PENDING }
            ?.apply {
              when (twingraphImportJobInfoRequest.response) {
                "Succeeded" -> status = Dataset.Status.READY
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
    dataset.status?.takeUnless { it == Dataset.Status.PENDING }
        ?: throw CsmClientException("Dataset in use, cannot update. Retry later")

    datasetRepository.save(dataset.apply { status = Dataset.Status.PENDING })

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
    val organization = organizationService.findOrganizationById(organizationId)
    csmRbac.verify(organization.getRbac(), PERMISSION_DELETE)

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

    dataset.main?.let {
      existingDataset.main = it
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
    val dataset = getDatasetWithStatus(organizationId, datasetId, status = Dataset.Status.READY)
    return trx(dataset) { query(dataset, datasetTwinGraphQuery.query).toJsonString() }
  }

  fun query(dataset: Dataset, query: String): ResultSet {
    return if (query.isReadOnlyQuery()) {
      csmRedisGraph.readOnlyQuery(
          dataset.twingraphId!!, query, csmPlatformProperties.twincache.queryTimeout)
    } else {
      csmRedisGraph.query(
          dataset.twingraphId!!, query, csmPlatformProperties.twincache.queryTimeout)
    }
  }

  fun <T> trx(dataset: Dataset, actionLambda: (Dataset) -> T): T {
    dataset.status?.takeUnless { it == Dataset.Status.PENDING }
        ?: throw CsmClientException("Dataset in use, cannot update. Retry later")
    datasetRepository.save(dataset.apply { status = Dataset.Status.PENDING })
    try {
      return actionLambda(dataset)
    } finally {
      datasetRepository.save(dataset.apply { status = Dataset.Status.READY })
    }
  }

  override fun twingraphBatchUpdate(
      organizationId: String,
      datasetId: String,
      twinGraphQuery: DatasetTwinGraphQuery,
      body: Resource
  ): TwinGraphBatchResult {

    val dataset = getDatasetWithStatus(organizationId, datasetId)
    resourceScanner.scanMimeTypes(body, listOf("text/csv", "text/plain"))

    val result = TwinGraphBatchResult(0, 0, mutableListOf())
    trx(dataset) { localDataset ->
      processCSVBatch(body.inputStream, twinGraphQuery, result) {
        try {
          result.processedLines++
          csmRedisGraph.query(localDataset.twingraphId, it)
        } catch (e: JedisDataException) {
          result.errors.add("#${result.totalLines}: ${e.message}")
        }
      }
    }
    return result
  }

  override fun twingraphBatchQuery(
      organizationId: String,
      datasetId: String,
      datasetTwinGraphQuery: DatasetTwinGraphQuery
  ): DatasetTwinGraphHash {
    val dataset = getDatasetWithStatus(organizationId, datasetId, status = Dataset.Status.READY)
    val bulkQueryKey = bulkQueryKey(dataset.twingraphId!!, datasetTwinGraphQuery.query, null)
    val twinGraphHash = DatasetTwinGraphHash(bulkQueryKey.second)
    csmJedisPool.resource.use { jedis ->
      val keyExists = jedis.exists(bulkQueryKey.first)
      if (keyExists) {
        return twinGraphHash
      }
      val resultSet = csmRedisGraph.query(dataset.twingraphId, datasetTwinGraphQuery.query)

      GlobalScope.launch(SecurityCoroutineContext()) {
        val zip =
            zipBytesWithFileNames(
                mapOf(
                    "bulkQuery.json" to
                        resultSet.toJsonString().toByteArray(StandardCharsets.UTF_8)))
        jedis.setex(bulkQueryKey.first, csmPlatformProperties.twincache.queryBulkTTL, zip)
      }
    }
    return twinGraphHash
  }

  override fun downloadTwingraph(organizationId: String, hash: String): Resource {
    val organization = organizationService.findOrganizationById(organizationId)
    csmRbac.verify(organization.getRbac(), PERMISSION_READ)

    val bulkQueryId = bulkQueryKey(hash)
    csmJedisPool.resource.use { jedis ->
      if (!jedis.exists(bulkQueryId)) {
        throw CsmResourceNotFoundException("No graph found with hash: $hash  Try later")
      } else if (jedis.ttl(bulkQueryId) < 0) {
        throw CsmResourceNotFoundException(
            "Graph with hash: $hash is expired. Try to repeat bulk query")
      }
    }
    val httpServletResponse =
        ((RequestContextHolder.getRequestAttributes() as ServletRequestAttributes).response)
    val contentDisposition =
        ContentDisposition.builder("attachment").filename("TwinGraph-$hash.zip").build()
    httpServletResponse!!.setHeader(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
    return ByteArrayResource(csmJedisPool.resource.use { jedis -> jedis.get(bulkQueryId) })
  }

  @Suppress("ThrowsCount")
  fun getDatasetWithStatus(
      organizationId: String,
      datasetId: String,
      status: Dataset.Status? = null
  ): Dataset {
    val organization = organizationService.findOrganizationById(organizationId)
    csmRbac.verify(organization.getRbac(), PERMISSION_READ)

    val dataset = findDatasetById(organizationId, datasetId)
    dataset.takeUnless { it.twingraphId.isNullOrBlank() }
        ?: throw CsmResourceNotFoundException("TwingraphId is not defined for the dataset")
    status?.let {
      dataset.status?.takeIf { it == status }
          ?: throw CsmClientException("Dataset status is ${dataset.status?.value}, not $status")
    }
    return dataset
  }

  fun processCSVBatch(
      inputStream: InputStream,
      twinGraphQuery: DatasetTwinGraphQuery,
      result: TwinGraphBatchResult,
      actionLambda: (String) -> Unit
  ) = readCSV(inputStream, result) { actionLambda(twinGraphQuery.query.formatQuery(it)) }

  override fun createTwingraphEntities(
      organizationId: String,
      datasetId: String,
      type: String,
      graphProperties: List<GraphProperties>
  ): String {
    val dataset = getDatasetWithStatus(organizationId, datasetId)
    var result = ""
    trx(dataset) { localDataset ->
      when (type) {
        TYPE_NODE ->
            graphProperties.forEach {
              result +=
                  csmRedisGraph
                      .query(
                          localDataset.twingraphId,
                          "CREATE (a:${it.type} {id:'${it.name}',${it.params}}) RETURN a")
                      .toJsonString()
            }
        TYPE_RELATIONSHIP ->
            graphProperties.forEach {
              result +=
                  csmRedisGraph
                      .query(
                          dataset.twingraphId,
                          "MATCH (a),(b) WHERE a.id='${it.source}' AND b.id='${it.target}'" +
                              "CREATE (a)-[r:${it.type} {id:'${it.name}', ${it.params}}]->(b) RETURN r")
                      .toJsonString()
            }
        else -> throw CsmResourceNotFoundException("Bad Type : $type")
      }
    }
    return result
  }

  override fun getTwingraphEntities(
      organizationId: String,
      datasetId: String,
      type: String,
      ids: List<String>
  ): String {
    val dataset = getDatasetWithStatus(organizationId, datasetId, status = Dataset.Status.READY)

    var result = ""
    when (type) {
      TYPE_NODE ->
          ids.forEach {
            result +=
                csmRedisGraph
                    .readOnlyQuery(dataset.twingraphId, "MATCH (a) WHERE a.id='$it' RETURN a")
                    .toJsonString()
          }
      TYPE_RELATIONSHIP ->
          ids.forEach {
            result +=
                csmRedisGraph
                    .readOnlyQuery(
                        dataset.twingraphId, "MATCH ()-[r]->() WHERE r.id='$it' RETURN r")
                    .toJsonString()
          }
      else -> throw CsmResourceNotFoundException("Bad Type : $type")
    }
    return result
  }

  override fun updateTwingraphEntities(
      organizationId: String,
      datasetId: String,
      type: String,
      graphProperties: List<GraphProperties>
  ): String {
    val dataset = getDatasetWithStatus(organizationId, datasetId, status = Dataset.Status.READY)
    var result = ""
    trx(dataset) { localDataset ->
      when (type) {
        TYPE_NODE ->
            graphProperties.forEach {
              result +=
                  csmRedisGraph
                      .query(
                          localDataset.twingraphId,
                          "MATCH (a {id:'${it.name}'}) SET a = {id:'${it.name}',${it.params}} RETURN a")
                      .toJsonString()
            }
        TYPE_RELATIONSHIP ->
            graphProperties.forEach {
              result +=
                  csmRedisGraph
                      .query(
                          dataset.twingraphId,
                          "MATCH ()-[r {id:'${it.name}'}]-() SET r = {id:'${it.name}', " +
                              "${it.params}} RETURN r")
                      .toJsonString()
            }
        else -> throw CsmResourceNotFoundException("Bad Type : $type")
      }
    }
    return result
  }

  override fun deleteTwingraphEntities(
      organizationId: String,
      datasetId: String,
      type: String,
      ids: List<String>
  ) {
    val dataset = getDatasetWithStatus(organizationId, datasetId)
    return trx(dataset) { localDataset ->
      when (type) {
        TYPE_NODE ->
            ids.forEach {
              csmRedisGraph.query(localDataset.twingraphId, "MATCH (a) WHERE a.id='$it' DELETE a")
            }
        TYPE_RELATIONSHIP ->
            ids.forEach {
              csmRedisGraph.query(
                  localDataset.twingraphId, "MATCH ()-[r]-() WHERE r.id='$it' DELETE r")
            }
        else -> throw CsmResourceNotFoundException("Bad Type : $type")
      }
    }
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

  fun getCreateTwincacheConnector(): Connector {
    val twinCacheConnectorProperties =
        csmPlatformProperties.containers.find { it.name == TWINCACHE_CONNECTOR }
            ?: throw CsmResourceNotFoundException(
                "Connector $TWINCACHE_CONNECTOR not found in application.yml")
    val connectorName =
        twinCacheConnectorProperties.name + " " + twinCacheConnectorProperties.imageVersion
    try {
      return connectorService.findConnectorByName(connectorName)
    } catch (exception: CsmClientException) {
      if (exception is CsmResourceNotFoundException) {
        logger.debug("Connector $connectorName not found. Registering it...")
        return connectorService.registerConnector(
            Connector(
                name = connectorName,
                key = twinCacheConnectorProperties.name,
                version = twinCacheConnectorProperties.imageVersion,
                repository = twinCacheConnectorProperties.imageName,
                description = "Auto-generated connector for TwinCache",
                ioTypes = listOf(Connector.IoTypes.read),
                parameterGroups =
                    listOf(
                        ConnectorParameterGroup(
                            "parameters",
                            "Parameters",
                            listOf(
                                ConnectorParameter(
                                    TWINCACHE_NAME,
                                    "TwinCache name",
                                    "string",
                                    envVar = TWINCACHE_NAME))))))
      } else {
        throw exception
      }
    }
  }

  override fun importDataset(organizationId: String, dataset: Dataset): Dataset {
    if (dataset.id == null) {
      throw CsmResourceNotFoundException("Dataset or Organization id is null")
    }
    return datasetRepository.save(dataset)
  }
}
