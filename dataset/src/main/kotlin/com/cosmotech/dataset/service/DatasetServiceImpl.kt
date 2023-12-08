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
import com.cosmotech.api.rbac.PERMISSION_CREATE_CHILDREN
import com.cosmotech.api.rbac.PERMISSION_DELETE
import com.cosmotech.api.rbac.PERMISSION_READ
import com.cosmotech.api.rbac.PERMISSION_READ_SECURITY
import com.cosmotech.api.rbac.PERMISSION_WRITE
import com.cosmotech.api.rbac.PERMISSION_WRITE_SECURITY
import com.cosmotech.api.rbac.ROLE_ADMIN
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.rbac.model.RbacAccessControl
import com.cosmotech.api.rbac.model.RbacSecurity
import com.cosmotech.api.security.ROLE_PLATFORM_ADMIN
import com.cosmotech.api.security.coroutine.SecurityCoroutineContext
import com.cosmotech.api.utils.ResourceScanner
import com.cosmotech.api.utils.bulkQueryKey
import com.cosmotech.api.utils.changed
import com.cosmotech.api.utils.compareToAndMutateIfNeeded
import com.cosmotech.api.utils.constructPageRequest
import com.cosmotech.api.utils.findAllPaginated
import com.cosmotech.api.utils.formatQuery
import com.cosmotech.api.utils.getCurrentAccountIdentifier
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
import com.cosmotech.dataset.domain.DatasetAccessControl
import com.cosmotech.dataset.domain.DatasetCompatibility
import com.cosmotech.dataset.domain.DatasetConnector
import com.cosmotech.dataset.domain.DatasetCopyParameters
import com.cosmotech.dataset.domain.DatasetRole
import com.cosmotech.dataset.domain.DatasetSearch
import com.cosmotech.dataset.domain.DatasetSecurity
import com.cosmotech.dataset.domain.DatasetSourceType
import com.cosmotech.dataset.domain.DatasetTwinGraphHash
import com.cosmotech.dataset.domain.DatasetTwinGraphInfo
import com.cosmotech.dataset.domain.DatasetTwinGraphQuery
import com.cosmotech.dataset.domain.GraphProperties
import com.cosmotech.dataset.domain.SourceInfo
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
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.time.Instant

const val TYPE_NODE = "node"
const val TYPE_RELATIONSHIP = "relationship"
const val NODES_ZIP_FOLDER = "nodes"
const val EDGES_ZIP_FOLDER = "edges"
const val TWINCACHE_CONNECTOR = "TwincacheConnector"
const val TWINCACHE_NAME = "TWIN_CACHE_NAME"

@Service
@Suppress(
    "TooManyFunctions",
    "LongParameterList",
    "LargeClass",
    "ReturnCount",
    "ComplexMethod",
    "LongMethod",
    "NestedBlockDepth")
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
    // This call verify by itself that we have the read authorization in the organization
    organizationService.findOrganizationById(organizationId)

    val defaultPageSize = csmPlatformProperties.twincache.dataset.defaultPageSize
    val pageable = constructPageRequest(page, size, defaultPageSize)
    if (pageable != null) {
      return datasetRepository.findByOrganizationId(organizationId, pageable).toList()
    }
    return findAllPaginated(defaultPageSize) {
      datasetRepository.findByOrganizationId(organizationId, it).toList()
    }
  }

  override fun findDatasetById(organizationId: String, datasetId: String): Dataset {
    val dataset =
        datasetRepository.findById(datasetId).orElseThrow {
          CsmResourceNotFoundException(
              "Dataset $datasetId not found in organization $organizationId")
        }
    csmRbac.verify(dataset.getRbac(), PERMISSION_READ)
    return dataset
  }

  override fun removeAllDatasetCompatibilityElements(organizationId: String, datasetId: String) {
    val dataset = findDatasetById(organizationId, datasetId)
    csmRbac.verify(dataset.getRbac(), PERMISSION_WRITE)

    if (!dataset.compatibility.isNullOrEmpty()) {
      dataset.compatibility = mutableListOf()
      datasetRepository.save(dataset)
    }
  }

  override fun createDataset(organizationId: String, dataset: Dataset): Dataset {
    val organization = organizationService.findOrganizationById(organizationId)
    csmRbac.verify(organization.getRbac(), PERMISSION_CREATE_CHILDREN)

    dataset.takeUnless { it.name.isNullOrBlank() }
        ?: throw IllegalArgumentException("Name cannot be null or blank")

    dataset.takeUnless {
      dataset.sourceType in listOf(DatasetSourceType.ADT, DatasetSourceType.AzureStorage) &&
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

    var datasetSecurity = dataset.security
    if (datasetSecurity == null) {
      datasetSecurity = initSecurity(getCurrentAccountIdentifier(this.csmPlatformProperties))
    } else {
      val accessControls = mutableListOf<String>()
      datasetSecurity.accessControlList.forEach {
        if (!accessControls.contains(it.id)) {
          accessControls.add(it.id)
        } else {
          throw IllegalArgumentException("User $it is referenced multiple times in the security")
        }
      }
    }

    val datasetCopy =
        dataset.copy(
            id = idGenerator.generate("dataset"),
            twingraphId = twingraphId,
            sourceType = dataset.sourceType ?: DatasetSourceType.None,
            source = dataset.source ?: SourceInfo("none"),
            main = dataset.main ?: true,
            creationDate = Instant.now().toEpochMilli(),
            ingestionStatus = Dataset.IngestionStatus.NONE,
            twincacheStatus = Dataset.TwincacheStatus.EMPTY,
            ownerId = getCurrentAuthenticatedUserName(csmPlatformProperties),
            organizationId = organizationId,
            security = datasetSecurity)
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
    val dataset =
        getDatasetWithStatus(organizationId, datasetId, status = Dataset.IngestionStatus.SUCCESS)
    csmRbac.verify(dataset.getRbac(), PERMISSION_CREATE_CHILDREN)
    var twincacheStatus = Dataset.TwincacheStatus.EMPTY
    val subTwingraphId = idGenerator.generate("twingraph")
    trx(dataset) {
      csmJedisPool.resource.use { jedis ->
        if (subDatasetGraphQuery.queries.isNullOrEmpty()) {
          jedis.eval(
              "local o = redis.call('DUMP', KEYS[1]);redis.call('RENAME', KEYS[1], KEYS[2]);" +
                  "redis.call('RESTORE', KEYS[1], 0, o)",
              2,
              dataset.twingraphId,
              subTwingraphId)
        } else {
          val queryBuffer = QueryBuffer(csmJedisPool.resource, subTwingraphId)
          subDatasetGraphQuery.queries?.forEach { query ->
            val resultSet = query(dataset, query)
            query.takeIf { it.isReadOnlyQuery() }?.apply { bulkQueryResult(queryBuffer, resultSet) }
          }
          queryBuffer.send()
          twincacheStatus = Dataset.TwincacheStatus.FULL
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
            twingraphId = subTwingraphId,
            queries = subDatasetGraphQuery.queries,
            main = subDatasetGraphQuery.main ?: dataset.main,
            creationDate = Instant.now().toEpochMilli(),
            parentId = dataset.id,
            ingestionStatus = Dataset.IngestionStatus.SUCCESS,
            twincacheStatus = twincacheStatus,
            connector =
                dataset.connector?.apply { parametersValues?.set(TWINCACHE_NAME, subTwingraphId) },
            sourceType = DatasetSourceType.Twincache,
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

  @Suppress("LongMethod")
  override fun uploadTwingraph(organizationId: String, datasetId: String, body: Resource) {
    val dataset = getDatasetWithStatus(organizationId, datasetId)
    csmRbac.verify(dataset.getRbac(), PERMISSION_WRITE)

    val archiverType = ArchiveStreamFactory.detect(body.inputStream.buffered())
    archiverType.takeIf { it == ArchiveStreamFactory.ZIP }
        ?: throw IllegalArgumentException(
            "Invalid archive type: '$archiverType'. A Zip Archive is expected.")

    dataset.sourceType.takeIf { it == DatasetSourceType.File }
        ?: throw CsmResourceNotFoundException("SourceType Dataset must be 'File'")
    val uploadStatus = getDatasetTwingraphStatus(organizationId, datasetId)
    uploadStatus.takeUnless { it == Dataset.IngestionStatus.PENDING.value }
        ?: throw CsmResourceNotFoundException("Dataset in use, cannot update. Retry later")

    datasetRepository.save(dataset.apply { ingestionStatus = Dataset.IngestionStatus.PENDING })
    GlobalScope.launch(SecurityCoroutineContext()) {
      var safeReplace = false
      csmJedisPool.resource.use { jedis ->
        if (jedis.exists(dataset.twingraphId!!)) {
          jedis.eval(
              "redis.call('RENAME', KEYS[1], KEYS[2]);",
              2,
              dataset.twingraphId,
              "backupGraph-$datasetId")
          safeReplace = true
        }
      }
      try {
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
        if (safeReplace) {
          csmJedisPool.resource.use { jedis ->
            jedis.eval("redis.call('DEL', KEYS[1]);", 1, "backupGraph-$datasetId")
          }
        }
        datasetRepository.save(dataset.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
      } catch (e: Exception) {
        if (safeReplace) {
          csmJedisPool.resource.use { jedis ->
            jedis.eval(
                "redis.call('RENAME', KEYS[2], KEYS[1]);",
                2,
                dataset.twingraphId,
                "backupGraph-$datasetId")
          }
        }
        datasetRepository.save(dataset.apply { ingestionStatus = Dataset.IngestionStatus.ERROR })
      }
    }
  }

  override fun getDatasetTwingraphStatus(
      organizationId: String,
      datasetId: String,
  ): String {
    // This call verify by itself that we have the read authorization in the dataset
    val dataset = findDatasetById(organizationId, datasetId)
    return when (dataset.sourceType) {
      null -> Dataset.IngestionStatus.NONE.value
      DatasetSourceType.None -> dataset.ingestionStatus!!.value
      DatasetSourceType.File -> {
        if (dataset.ingestionStatus == Dataset.IngestionStatus.NONE) {
          return Dataset.IngestionStatus.NONE.value
        }
        csmJedisPool.resource.use { jedis ->
          if (dataset.ingestionStatus == Dataset.IngestionStatus.ERROR) {
            return Dataset.IngestionStatus.ERROR.value
          } else if (!jedis.exists(dataset.twingraphId!!)) {
            Dataset.IngestionStatus.PENDING.value
          } else {
            dataset
                .takeIf { it.ingestionStatus == Dataset.IngestionStatus.PENDING }
                ?.apply {
                  datasetRepository.save(
                      this.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
                }
            if (dataset.twincacheStatus == Dataset.TwincacheStatus.EMPTY) {
              apply {
                datasetRepository.save(
                    dataset.apply { twincacheStatus = Dataset.TwincacheStatus.FULL })
              }
            }
            Dataset.IngestionStatus.SUCCESS.value
          }
        }
      }
      DatasetSourceType.ADT,
      DatasetSourceType.Twincache,
      DatasetSourceType.AzureStorage -> {
        if (dataset.ingestionStatus == Dataset.IngestionStatus.PENDING) {
          dataset.source!!.takeIf { !it.jobId.isNullOrEmpty() }
              ?: throw IllegalStateException(
                  "Dataset doesn't have a job id to get status from. Refresh dataset first.")
          val twingraphImportJobInfoRequest =
              TwingraphImportJobInfoRequest(this, dataset.source!!.jobId!!, organizationId)

          this.eventPublisher.publishEvent(twingraphImportJobInfoRequest)

          dataset.apply {
            when (twingraphImportJobInfoRequest.response) {
              "Succeeded" -> {
                ingestionStatus = Dataset.IngestionStatus.SUCCESS
                if (dataset.twincacheStatus == Dataset.TwincacheStatus.EMPTY) {
                  apply {
                    datasetRepository.save(
                        dataset.apply { twincacheStatus = Dataset.TwincacheStatus.FULL })
                  }
                }
              }
              "Error",
              "Failed" -> ingestionStatus = Dataset.IngestionStatus.ERROR
            }
            datasetRepository.save(this)
          }
        }
        return dataset.ingestionStatus!!.value
      }
    }
  }

  override fun refreshDataset(organizationId: String, datasetId: String): DatasetTwinGraphInfo {
    val dataset = findDatasetById(organizationId, datasetId)
    csmRbac.verify(dataset.getRbac(), PERMISSION_WRITE)

    dataset.takeUnless { it.sourceType == DatasetSourceType.File }
        ?: throw CsmResourceNotFoundException("Cannot be applied to source type 'File'")
    dataset.takeUnless { it.sourceType == DatasetSourceType.None }
        ?: throw CsmResourceNotFoundException("Cannot be applied to source type 'None'")
    dataset.ingestionStatus?.takeUnless { it == Dataset.IngestionStatus.PENDING }
        ?: throw CsmClientException("Dataset in use, cannot update. Retry later")

    datasetRepository.save(
        dataset.apply {
          refreshDate = Instant.now().toEpochMilli()
          ingestionStatus = Dataset.IngestionStatus.PENDING
        })

    csmJedisPool.resource.use { jedis ->
      if (jedis.exists(dataset.twingraphId!!)) {
        jedis.eval(
            "redis.call('RENAME', KEYS[1], KEYS[2]);",
            2,
            dataset.twingraphId,
            "backupGraph-$datasetId")
      }
    }

    val dataSourceLocation =
        if (dataset.sourceType == DatasetSourceType.Twincache) {
          val parentDataset = findDatasetById(organizationId, dataset.parentId!!)
          parentDataset.twingraphId
        } else {
          dataset.source!!.location
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
            dataSourceLocation ?: "",
            dataset.source!!.path ?: "",
            dataset.sourceType!!.value,
            "",
            dataset.queries)
    this.eventPublisher.publishEvent(graphImportEvent)

    datasetRepository.save(dataset.apply { source!!.jobId = requestJobId })
    logger.debug("refreshDataset={}", graphImportEvent.response)
    return DatasetTwinGraphInfo(
        jobId = requestJobId, datasetId = dataset.id, status = dataset.ingestionStatus?.value)
  }

  override fun rollbackRefresh(organizationId: String, datasetId: String): String {
    // This call verify by itself that we have the read authorization in the organization
    organizationService.findOrganizationById(organizationId)
    var dataset = findDatasetById(organizationId, datasetId)
    csmRbac.verify(dataset.getRbac(), PERMISSION_WRITE)

    if (dataset.ingestionStatus != Dataset.IngestionStatus.ERROR) {
      throw IllegalArgumentException("The dataset hasn't failed and can't be rolled back")
    }

    dataset =
        if (dataset.twincacheStatus == Dataset.TwincacheStatus.FULL) {
          datasetRepository.save(
              dataset.apply { dataset.ingestionStatus = Dataset.IngestionStatus.SUCCESS })
        } else {
          datasetRepository.save(
              dataset.apply { dataset.ingestionStatus = Dataset.IngestionStatus.NONE })
        }
    return "Dataset $datasetId status is now ${dataset.ingestionStatus}"
  }

  override fun deleteDataset(organizationId: String, datasetId: String) {
    val dataset = findDatasetById(organizationId, datasetId)
    csmRbac.verify(dataset.getRbac(), PERMISSION_DELETE)

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
    csmRbac.verify(existingDataset.getRbac(), PERMISSION_WRITE)

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

    if (dataset.security != existingDataset.security) {
      logger.warn(
          "Security modification has not been applied to dataset $datasetId," +
              " please refer to the appropriate security endpoints to perform this maneuver")
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
    // This call verify by itself that we have the read authorization in the dataset
    val dataset =
        getDatasetWithStatus(organizationId, datasetId, status = Dataset.IngestionStatus.SUCCESS)
    return trx(dataset) { query(dataset, datasetTwinGraphQuery.query).toJsonString() }
  }

  fun query(dataset: Dataset, query: String, isReadOnly: Boolean = false): ResultSet {
    return if (isReadOnly) {
      csmRedisGraph.readOnlyQuery(
          dataset.twingraphId!!, query, csmPlatformProperties.twincache.queryTimeout)
    } else {
      csmRedisGraph.query(
          dataset.twingraphId!!, query, csmPlatformProperties.twincache.queryTimeout)
    }
  }

  fun <T> trx(dataset: Dataset, actionLambda: (Dataset) -> T): T {
    dataset.ingestionStatus?.takeUnless { it == Dataset.IngestionStatus.PENDING }
        ?: throw CsmClientException("Dataset in use, cannot update. Retry later")
    datasetRepository.save(dataset.apply { ingestionStatus = Dataset.IngestionStatus.PENDING })
    try {
      return actionLambda(dataset)
    } finally {
      datasetRepository.save(dataset.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
    }
  }

  override fun twingraphBatchUpdate(
      organizationId: String,
      datasetId: String,
      twinGraphQuery: DatasetTwinGraphQuery,
      body: Resource
  ): TwinGraphBatchResult {

    val dataset = getDatasetWithStatus(organizationId, datasetId)
    csmRbac.verify(dataset.getRbac(), PERMISSION_WRITE)
    resourceScanner.scanMimeTypes(body, listOf("text/csv", "text/plain"))

    val result = TwinGraphBatchResult(0, 0, mutableListOf())
    trx(dataset) { localDataset ->
      processCSVBatch(body.inputStream, twinGraphQuery, result) {
        try {
          result.processedLines++
          query(localDataset, it)
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
    // This call verify by itself that we have the read authorization in the dataset
    val dataset =
        getDatasetWithStatus(organizationId, datasetId, status = Dataset.IngestionStatus.SUCCESS)
    val bulkQueryKey = bulkQueryKey(dataset.twingraphId!!, datasetTwinGraphQuery.query, null)
    val twinGraphHash = DatasetTwinGraphHash(bulkQueryKey.second)
    csmJedisPool.resource.use { jedis ->
      val keyExists = jedis.exists(bulkQueryKey.first)
      if (keyExists) {
        return twinGraphHash
      }
      val resultSet = query(dataset, datasetTwinGraphQuery.query)

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
    // This call verify by itself that we have the read authorization in the organization
    organizationService.findOrganizationById(organizationId)

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
      status: Dataset.IngestionStatus? = null
  ): Dataset {
    // This call verify by itself that we have the read authorization in the organization
    organizationService.findOrganizationById(organizationId)

    val dataset = findDatasetById(organizationId, datasetId)
    dataset.takeUnless { it.twingraphId.isNullOrBlank() }
        ?: throw CsmResourceNotFoundException("TwingraphId is not defined for the dataset")
    status?.let {
      dataset.ingestionStatus?.takeIf { it == status }
          ?: throw CsmClientException(
              "Dataset status is ${dataset.ingestionStatus?.value}, not $status")
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
    csmRbac.verify(dataset.getRbac(), PERMISSION_WRITE)
    var result = ""
    trx(dataset) { localDataset ->
      when (type) {
        TYPE_NODE ->
            graphProperties.forEach {
              result +=
                  query(
                          localDataset,
                          "CREATE (a:${it.type} {id:'${it.name}',${it.params}}) RETURN a")
                      .toJsonString()
            }
        TYPE_RELATIONSHIP ->
            graphProperties.forEach {
              result +=
                  query(
                          localDataset,
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
    // This call verify by itself that we have the read authorization in the dataset
    val dataset = getDatasetWithStatus(organizationId, datasetId)
    var result = ""
    when (type) {
      TYPE_NODE ->
          ids.forEach {
            result += query(dataset, "MATCH (a) WHERE a.id='$it' RETURN a", true).toJsonString()
          }
      TYPE_RELATIONSHIP ->
          ids.forEach {
            result +=
                query(dataset, "MATCH ()-[r]->() WHERE r.id='$it' RETURN r", true).toJsonString()
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
    val dataset = getDatasetWithStatus(organizationId, datasetId)
    csmRbac.verify(dataset.getRbac(), PERMISSION_WRITE)
    var result = ""
    trx(dataset) { localDataset ->
      when (type) {
        TYPE_NODE ->
            graphProperties.forEach {
              result +=
                  query(
                          localDataset,
                          "MATCH (a {id:'${it.name}'}) SET a = {id:'${it.name}',${it.params}} RETURN a")
                      .toJsonString()
            }
        TYPE_RELATIONSHIP ->
            graphProperties.forEach {
              result +=
                  query(
                          dataset,
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
    csmRbac.verify(dataset.getRbac(), PERMISSION_WRITE)
    return trx(dataset) { localDataset ->
      when (type) {
        TYPE_NODE -> ids.forEach { query(localDataset, "MATCH (a) WHERE a.id='$it' DELETE a") }
        TYPE_RELATIONSHIP ->
            ids.forEach { query(localDataset, "MATCH ()-[r]-() WHERE r.id='$it' DELETE r") }
        else -> throw CsmResourceNotFoundException("Bad Type : $type")
      }
    }
  }

  override fun addOrReplaceDatasetCompatibilityElements(
      organizationId: String,
      datasetId: String,
      datasetCompatibility: List<DatasetCompatibility>
  ): List<DatasetCompatibility> {
    val existingDataset = findDatasetById(organizationId, datasetId)
    csmRbac.verify(existingDataset.getRbac(), PERMISSION_WRITE)
    if (datasetCompatibility.isEmpty()) {
      return datasetCompatibility
    }

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
    // This call verify by itself that we have the read authorization in the organization
    organizationService.findOrganizationById(organizationId)

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

  override fun getDatasetSecurity(organizationId: String, datasetId: String): DatasetSecurity {

    val dataset = findDatasetById(organizationId, datasetId)
    csmRbac.verify(dataset.getRbac(), PERMISSION_READ_SECURITY)
    return dataset.security
        ?: throw CsmResourceNotFoundException("RBAC not defined for ${dataset.id}")
  }

  override fun setDatasetDefaultSecurity(
      organizationId: String,
      datasetId: String,
      datasetRole: DatasetRole
  ): DatasetSecurity {
    // This call verify by itself that we have the read authorization in the organization
    organizationService.findOrganizationById(organizationId)
    val dataset = findDatasetById(organizationId, datasetId)
    csmRbac.verify(dataset.getRbac(), PERMISSION_WRITE_SECURITY)
    val rbacSecurity = csmRbac.setDefault(dataset.getRbac(), datasetRole.role)
    dataset.setRbac(rbacSecurity)
    datasetRepository.save(dataset)
    return dataset.security as DatasetSecurity
  }

  override fun addDatasetAccessControl(
      organizationId: String,
      datasetId: String,
      datasetAccessControl: DatasetAccessControl
  ): DatasetAccessControl {
    val dataset = findDatasetById(organizationId, datasetId)
    csmRbac.verify(dataset.getRbac(), PERMISSION_WRITE_SECURITY)
    val organization = organizationService.findOrganizationById(organizationId)

    val users = getDatasetSecurityUsers(organizationId, datasetId)
    if (users.contains(datasetAccessControl.id)) {
      throw IllegalArgumentException("User is already in this Dataset security")
    }

    val rbacSecurity =
        csmRbac.addUserRole(
            organization.getRbac(),
            dataset.getRbac(),
            datasetAccessControl.id,
            datasetAccessControl.role)
    dataset.setRbac(rbacSecurity)
    datasetRepository.save(dataset)
    val rbacAccessControl = csmRbac.getAccessControl(dataset.getRbac(), datasetAccessControl.id)
    return DatasetAccessControl(rbacAccessControl.id, rbacAccessControl.role)
  }

  override fun getDatasetAccessControl(
      organizationId: String,
      datasetId: String,
      identityId: String
  ): DatasetAccessControl {
    val dataset = findDatasetById(organizationId, datasetId)
    csmRbac.verify(dataset.getRbac(), PERMISSION_READ_SECURITY)
    val rbacAccessControl = csmRbac.getAccessControl(dataset.getRbac(), identityId)
    return DatasetAccessControl(rbacAccessControl.id, rbacAccessControl.role)
  }

  override fun updateDatasetAccessControl(
      organizationId: String,
      datasetId: String,
      identityId: String,
      datasetRole: DatasetRole
  ): DatasetAccessControl {
    val dataset = findDatasetById(organizationId, datasetId)
    csmRbac.verify(dataset.getRbac(), PERMISSION_WRITE_SECURITY)
    csmRbac.checkUserExists(
        dataset.getRbac(), identityId, "User '$identityId' not found in dataset $datasetId")
    val rbacSecurity = csmRbac.setUserRole(dataset.getRbac(), identityId, datasetRole.role)
    dataset.setRbac(rbacSecurity)
    datasetRepository.save(dataset)
    val rbacAccessControl = csmRbac.getAccessControl(dataset.getRbac(), identityId)
    return DatasetAccessControl(rbacAccessControl.id, rbacAccessControl.role)
  }

  override fun removeDatasetAccessControl(
      organizationId: String,
      datasetId: String,
      identityId: String
  ) {
    val dataset = findDatasetById(organizationId, datasetId)
    csmRbac.verify(dataset.getRbac(), PERMISSION_WRITE_SECURITY)
    val rbacSecurity = csmRbac.removeUser(dataset.getRbac(), identityId)
    dataset.setRbac(rbacSecurity)
    datasetRepository.save(dataset)
  }

  override fun getDatasetSecurityUsers(organizationId: String, datasetId: String): List<String> {
    val dataset = findDatasetById(organizationId, datasetId)
    csmRbac.verify(dataset.getRbac(), PERMISSION_READ_SECURITY)
    return csmRbac.getUsers(dataset.getRbac())
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
}

private fun initSecurity(userId: String): DatasetSecurity {
  return DatasetSecurity(
      default = ROLE_NONE,
      accessControlList = mutableListOf(DatasetAccessControl(userId, ROLE_ADMIN)))
}

fun Dataset.getRbac(): RbacSecurity {
  return RbacSecurity(
      this.id,
      this.security?.default ?: ROLE_NONE,
      this.security?.accessControlList?.map { RbacAccessControl(it.id, it.role) }?.toMutableList()
          ?: mutableListOf())
}

fun Dataset.setRbac(rbacSecurity: RbacSecurity) {
  this.security =
      DatasetSecurity(
          rbacSecurity.default,
          rbacSecurity.accessControlList
              .map { DatasetAccessControl(it.id, it.role) }
              .toMutableList())
}
