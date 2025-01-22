// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
@file:Suppress("DEPRECATION")

package com.cosmotech.dataset.service

import com.cosmotech.api.CsmPhoenixService
import com.cosmotech.api.events.AddDatasetToWorkspace
import com.cosmotech.api.events.AddWorkspaceToDataset
import com.cosmotech.api.events.AskRunStatusEvent
import com.cosmotech.api.events.ConnectorRemoved
import com.cosmotech.api.events.OrganizationUnregistered
import com.cosmotech.api.events.RemoveDatasetFromWorkspace
import com.cosmotech.api.events.RemoveWorkspaceFromDataset
import com.cosmotech.api.events.TriggerRunnerEvent
import com.cosmotech.api.events.TwingraphImportEvent
import com.cosmotech.api.events.TwingraphImportJobInfoRequest
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.exceptions.CsmClientException
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.rbac.CsmAdmin
import com.cosmotech.api.rbac.CsmRbac
import com.cosmotech.api.rbac.PERMISSION_CREATE_CHILDREN
import com.cosmotech.api.rbac.PERMISSION_DELETE
import com.cosmotech.api.rbac.PERMISSION_READ_SECURITY
import com.cosmotech.api.rbac.PERMISSION_WRITE
import com.cosmotech.api.rbac.PERMISSION_WRITE_SECURITY
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
import com.cosmotech.connector.ConnectorApiServiceInterface
import com.cosmotech.connector.domain.Connector
import com.cosmotech.connector.domain.ConnectorParameter
import com.cosmotech.connector.domain.ConnectorParameterGroup
import com.cosmotech.connector.domain.IoTypesEnum
import com.cosmotech.dataset.DatasetApiServiceInterface
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
import com.cosmotech.dataset.domain.FileUploadMetadata
import com.cosmotech.dataset.domain.FileUploadValidation
import com.cosmotech.dataset.domain.GraphProperties
import com.cosmotech.dataset.domain.IngestionStatusEnum
import com.cosmotech.dataset.domain.SourceInfo
import com.cosmotech.dataset.domain.SubDatasetGraphQuery
import com.cosmotech.dataset.domain.TwinGraphBatchResult
import com.cosmotech.dataset.domain.TwincacheStatusEnum
import com.cosmotech.dataset.repository.DatasetRepository
import com.cosmotech.dataset.utils.CsmGraphEntityType
import com.cosmotech.dataset.utils.isReadOnlyQuery
import com.cosmotech.dataset.utils.toCsmGraphEntity
import com.cosmotech.dataset.utils.toJsonString
import com.cosmotech.organization.OrganizationApiServiceInterface
import com.cosmotech.organization.service.toGenericSecurity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import kotlin.jvm.optionals.getOrNull
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVRecord
import org.apache.commons.lang3.NotImplementedException
import org.springframework.beans.factory.annotation.Value
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
import redis.clients.jedis.UnifiedJedis
import redis.clients.jedis.exceptions.JedisDataException
import redis.clients.jedis.graph.Record
import redis.clients.jedis.graph.ResultSet
import redis.clients.jedis.graph.entities.Edge
import redis.clients.jedis.graph.entities.Node

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
    private val connectorService: ConnectorApiServiceInterface,
    private val organizationService: OrganizationApiServiceInterface,
    private val datasetRepository: DatasetRepository,
    private val unifiedJedis: UnifiedJedis,
    private val csmRbac: CsmRbac,
    private val csmAdmin: CsmAdmin,
    private val resourceScanner: ResourceScanner
) : CsmPhoenixService(), DatasetApiServiceInterface {

  @Value("\${csm.platform.twincache.useGraphModule}") private var useGraphModule: Boolean = true

  private val notImplementedExceptionMessage =
      "The API is not configured to use Graph functionnalities. " +
          "This endpoint is deactivated. " +
          "To change that, set the API configuration entry 'csm.platform.twincache.useGraphModule' to true"

  override fun findAllDatasets(organizationId: String, page: Int?, size: Int?): List<Dataset> {
    organizationService.getVerifiedOrganization(organizationId)
    val defaultPageSize = csmPlatformProperties.twincache.dataset.defaultPageSize
    val pageable = constructPageRequest(page, size, defaultPageSize)
    val isAdmin = csmAdmin.verifyCurrentRolesAdmin()
    val result: MutableList<Dataset>

    val rbacEnabled = !isAdmin && this.csmPlatformProperties.rbac.enabled

    if (pageable == null) {
      result =
          findAllPaginated(defaultPageSize) {
            if (rbacEnabled) {
              val currentUser = getCurrentAccountIdentifier(this.csmPlatformProperties)
              datasetRepository.findByOrganizationId(organizationId, currentUser, it).toList()
            } else {
              datasetRepository.findByOrganizationIdNoSecurity(organizationId, it).toList()
            }
          }
    } else {
      result =
          if (rbacEnabled) {
            val currentUser = getCurrentAccountIdentifier(this.csmPlatformProperties)
            datasetRepository.findByOrganizationId(organizationId, currentUser, pageable).toList()
          } else {
            datasetRepository.findByOrganizationIdNoSecurity(organizationId, pageable).toList()
          }
    }
    result.forEach { it.security = updateSecurityVisibility(it).security }
    return result
  }

  override fun findDatasetById(organizationId: String, datasetId: String): Dataset {
    return updateSecurityVisibility(getVerifiedDataset(organizationId, datasetId))
  }

  override fun removeAllDatasetCompatibilityElements(organizationId: String, datasetId: String) {
    val dataset = getVerifiedDataset(organizationId, datasetId, PERMISSION_WRITE)

    if (!dataset.compatibility.isNullOrEmpty()) {
      dataset.compatibility = mutableListOf()
      datasetRepository.save(dataset)
    }
  }

  override fun createDataset(organizationId: String, dataset: Dataset): Dataset {
    organizationService.getVerifiedOrganization(organizationId, PERMISSION_CREATE_CHILDREN)

    dataset.takeUnless { it.name.isNullOrBlank() }
        ?: throw IllegalArgumentException("Name cannot be null or blank")

    val datasetSourceType = dataset.sourceType
    dataset.takeUnless {
      datasetSourceType in listOf(DatasetSourceType.ADT, DatasetSourceType.AzureStorage) &&
          dataset.source == null
    } ?: throw IllegalArgumentException("Source cannot be null for source type 'ADT' or 'Storage'")

    var twingraphId: String? = null

    if (datasetSourceType == DatasetSourceType.Twincache && useGraphModule) {

      twingraphId = idGenerator.generate("twingraph")
      val twincacheConnector = getCreateTwincacheConnector()
      dataset.connector =
          DatasetConnector(
              id = twincacheConnector.id,
              parametersValues = mutableMapOf(TWINCACHE_NAME to twingraphId))
    }

    val createdDataset =
        dataset.copy(
            id = idGenerator.generate("dataset"),
            sourceType = datasetSourceType ?: DatasetSourceType.None,
            source = dataset.source ?: SourceInfo("none"),
            main = dataset.main ?: true,
            creationDate = Instant.now().toEpochMilli(),
            ingestionStatus = IngestionStatusEnum.NONE,
            twincacheStatus = TwincacheStatusEnum.EMPTY,
            ownerId = getCurrentAuthenticatedUserName(csmPlatformProperties),
            organizationId = organizationId)
    createdDataset.apply {
      if (!twingraphId.isNullOrBlank()) {
        this.twingraphId = twingraphId
      }
    }
    createdDataset.setRbac(csmRbac.initSecurity(dataset.getRbac()))

    if (dataset.connector != null && !dataset.connector!!.id.isNullOrBlank()) {
      val existingConnector = connectorService.findConnectorById(dataset.connector!!.id!!)
      logger.debug("Found connector: {}", existingConnector)

      createdDataset.connector!!.apply {
        name = existingConnector.name
        version = existingConnector.version
      }
    }
    return datasetRepository.save(createdDataset)
  }

  override fun createSubDataset(
      organizationId: String,
      datasetId: String,
      subDatasetGraphQuery: SubDatasetGraphQuery
  ): Dataset {

    checkIfGraphFunctionalityIsAvailable()

    val dataset =
        getDatasetWithStatus(organizationId, datasetId, status = IngestionStatusEnum.SUCCESS)
    csmRbac.verify(dataset.getRbac(), PERMISSION_CREATE_CHILDREN)
    val subTwingraphId = idGenerator.generate("twingraph")
    val subDatasetId = idGenerator.generate("dataset")
    val subDataset =
        dataset.copy(
            id = subDatasetId,
            name = subDatasetGraphQuery.name ?: ("Subdataset " + dataset.name),
            description = subDatasetGraphQuery.description ?: dataset.description,
            ownerId = getCurrentAuthenticatedUserName(csmPlatformProperties),
            organizationId = organizationId,
            twingraphId = subTwingraphId,
            queries = subDatasetGraphQuery.queries,
            main = subDatasetGraphQuery.main ?: dataset.main,
            creationDate = Instant.now().toEpochMilli(),
            parentId = dataset.id,
            ingestionStatus = IngestionStatusEnum.PENDING,
            twincacheStatus = TwincacheStatusEnum.EMPTY,
            connector =
                dataset.connector?.apply { parametersValues?.set(TWINCACHE_NAME, subTwingraphId) },
            sourceType = DatasetSourceType.Twincache,
            tags = dataset.tags)

    val datasetSaved = datasetRepository.save(subDataset)

    GlobalScope.launch(SecurityCoroutineContext()) {
      trx(dataset) {
        if (subDatasetGraphQuery.queries.isNullOrEmpty()) {

          unifiedJedis.eval(
              "local o = redis.call('DUMP', KEYS[1]);redis.call('RENAME', KEYS[1], KEYS[2]);" +
                  "redis.call('RESTORE', KEYS[1], 0, o)",
              2,
              dataset.twingraphId,
              subTwingraphId)
        } else {
          val queryBuffer = QueryBuffer(unifiedJedis, subTwingraphId)
          subDatasetGraphQuery.queries?.forEach { query ->
            val resultSet = query(dataset, query)
            query.takeIf { it.isReadOnlyQuery() }?.apply { bulkQueryResult(queryBuffer, resultSet) }
          }
          queryBuffer.send()
        }

        datasetRepository.save(
            datasetSaved.apply {
              ingestionStatus = IngestionStatusEnum.SUCCESS
              twincacheStatus = TwincacheStatusEnum.FULL
            })
      }
    }
    return datasetSaved
  }

  private fun checkIfGraphFunctionalityIsAvailable() {
    if (!useGraphModule) {
      throw NotImplementedException(notImplementedExceptionMessage)
    }
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

  override fun uploadTwingraph(
      organizationId: String,
      datasetId: String,
      body: Resource
  ): FileUploadValidation {

    checkIfGraphFunctionalityIsAvailable()
    val dataset = getDatasetWithStatus(organizationId, datasetId)
    csmRbac.verify(dataset.getRbac(), PERMISSION_WRITE)

    dataset.sourceType.takeIf { it == DatasetSourceType.File }
        ?: throw CsmResourceNotFoundException("SourceType Dataset must be 'File'")

    // TODO: Validated with ressourceScanner PROD-12822
    val archiverType = ArchiveStreamFactory.detect(body.inputStream.buffered())
    archiverType.takeIf { it == ArchiveStreamFactory.ZIP }
        ?: throw IllegalArgumentException(
            "Invalid archive type: '$archiverType'. A Zip Archive is expected.")

    val csvFiles = unzip(body.inputStream, listOf(NODES_ZIP_FOLDER, EDGES_ZIP_FOLDER), "csv")
    val nodes = csvFiles.filter { it.prefix == NODES_ZIP_FOLDER }
    val edges = csvFiles.filter { it.prefix == EDGES_ZIP_FOLDER }

    val fileUpload =
        FileUploadValidation(
            nodes.mapTo(mutableListOf()) { FileUploadMetadata(it.filename, it.content.size) },
            edges.mapTo(mutableListOf()) { FileUploadMetadata(it.filename, it.content.size) })
    logger.info(fileUpload.toString())

    if (nodes.isEmpty()) {
      throw CsmClientException(
          "No nodes csv file found in zip. Nodes files must placed under a folder named 'nodes' " +
              "and edges under a folder named 'edges'")
    }
    if (nodes.all { it.content.isEmpty() }) {
      throw CsmClientException("All nodes files ${nodes.map { it.filename }} found are empty")
    }

    GlobalScope.launch(SecurityCoroutineContext()) {
      var safeReplace = false
      if (unifiedJedis.exists(dataset.twingraphId!!)) {
        unifiedJedis.eval(
            "redis.call('RENAME', KEYS[1], KEYS[2]);",
            2,
            dataset.twingraphId,
            "backupGraph-$datasetId")
        safeReplace = true
      }
      try {
        trx(dataset) {
          val queryBuffer = QueryBuffer(unifiedJedis, dataset.twingraphId!!)
          nodes.forEach { file ->
            readCSV(file.content.inputStream()) {
              queryBuffer.addNode(file.filename, it.values.first(), it)
            }
          }
          edges.forEach { file ->
            readCSV(file.content.inputStream()) {
              val sourceKey = it.keys.elementAt(0)
              val targetKey = it.keys.elementAt(1)
              val source = it[sourceKey].toString().trim()
              val target = it[targetKey].toString().trim()
              val properties = it.minus(sourceKey).minus(targetKey)
              queryBuffer.addEdge(file.filename, source, target, properties)
            }
          }
          queryBuffer.send()
          if (safeReplace) {
            unifiedJedis.eval("redis.call('DEL', KEYS[1]);", 1, "backupGraph-$datasetId")
          }
        }
        datasetRepository.save(dataset.apply { twincacheStatus = TwincacheStatusEnum.FULL })
      } catch (e: Exception) {
        if (safeReplace) {
          unifiedJedis.eval(
              "redis.call('RENAME', KEYS[2], KEYS[1]);",
              2,
              dataset.twingraphId,
              "backupGraph-$datasetId")
        }
        throw CsmClientException(e.message ?: "Twingraph upload error", e)
      }
    }
    return fileUpload
  }

  override fun getDatasetTwingraphStatus(
      organizationId: String,
      datasetId: String,
  ): String {
    val dataset = getVerifiedDataset(organizationId, datasetId)
    return when (dataset.sourceType) {
      null -> IngestionStatusEnum.NONE.value
      DatasetSourceType.None -> {
        var twincacheStatus = TwincacheStatusEnum.EMPTY
        if (useGraphModule && unifiedJedis.exists(dataset.twingraphId!!)) {
          twincacheStatus = TwincacheStatusEnum.FULL
        }
        datasetRepository.apply { dataset.twincacheStatus = twincacheStatus }

        dataset.ingestionStatus!!.value
      }
      DatasetSourceType.File -> {
        if (dataset.ingestionStatus == IngestionStatusEnum.NONE) {
          return IngestionStatusEnum.NONE.value
        }
        if (dataset.ingestionStatus == IngestionStatusEnum.ERROR) {
          return IngestionStatusEnum.ERROR.value
        } else if (useGraphModule && !unifiedJedis.exists(dataset.twingraphId!!)) {
          IngestionStatusEnum.PENDING.value
        } else {
          dataset
              .takeIf { it.ingestionStatus == IngestionStatusEnum.PENDING }
              ?.apply {
                ingestionStatus = IngestionStatusEnum.SUCCESS
                twincacheStatus = TwincacheStatusEnum.FULL
              }
          datasetRepository.save(dataset)
          IngestionStatusEnum.SUCCESS.value
        }
      }
      DatasetSourceType.ADT,
      DatasetSourceType.Twincache,
      DatasetSourceType.AzureStorage -> {
        if (dataset.ingestionStatus == IngestionStatusEnum.PENDING) {
          dataset.source!!.takeIf { !it.jobId.isNullOrEmpty() }
              ?: return dataset.ingestionStatus!!.value

          val twingraphImportJobInfoRequest =
              sendTwingraphImportJobInfoRequestEvent(dataset, organizationId)

          dataset.apply {
            when (twingraphImportJobInfoRequest.response) {
              "Succeeded" -> {
                ingestionStatus = IngestionStatusEnum.SUCCESS
                twincacheStatus = TwincacheStatusEnum.FULL
              }
              "Error",
              "Failed" -> ingestionStatus = IngestionStatusEnum.ERROR
            }
            datasetRepository.save(this)
          }
        }
        return dataset.ingestionStatus!!.value
      }
      DatasetSourceType.ETL -> {
        if (dataset.ingestionStatus == IngestionStatusEnum.PENDING) {
          dataset.source!!.takeIf { !it.jobId.isNullOrEmpty() }
              ?: return dataset.ingestionStatus!!.value

          val askRunStatusEvent =
              AskRunStatusEvent(
                  this,
                  organizationId,
                  dataset.source!!.location,
                  dataset.source!!.name!!,
                  dataset.source!!.jobId!!)
          this.eventPublisher.publishEvent(askRunStatusEvent)

          dataset.apply {
            when (askRunStatusEvent.response) {
              "Successful" -> {
                ingestionStatus = IngestionStatusEnum.SUCCESS
                twincacheStatus = TwincacheStatusEnum.FULL
              }
              "Error",
              "Unknown",
              "Failed" -> ingestionStatus = IngestionStatusEnum.ERROR
            }
            datasetRepository.save(this)
          }
        }
        return dataset.ingestionStatus!!.value
      }
    }
  }

  override fun refreshDataset(organizationId: String, datasetId: String): DatasetTwinGraphInfo {
    checkIfGraphFunctionalityIsAvailable()
    val dataset = getVerifiedDataset(organizationId, datasetId, PERMISSION_WRITE)

    dataset.takeUnless { it.sourceType == DatasetSourceType.File }
        ?: throw CsmResourceNotFoundException("Cannot be applied to source type 'File'")
    dataset.takeUnless { it.sourceType == DatasetSourceType.None }
        ?: throw CsmResourceNotFoundException("Cannot be applied to source type 'None'")
    dataset.ingestionStatus?.takeUnless { it == IngestionStatusEnum.PENDING }
        ?: throw CsmClientException("Dataset in use, cannot update. Retry later")

    datasetRepository.save(
        dataset.apply {
          refreshDate = Instant.now().toEpochMilli()
          ingestionStatus = IngestionStatusEnum.PENDING
        })

    val dataSourceLocation =
        if (dataset.sourceType == DatasetSourceType.Twincache) {
          val parentDataset = findDatasetById(organizationId, dataset.parentId!!)
          parentDataset.twingraphId
        } else {
          dataset.source!!.location
        }

    val requestJobId =
        if (dataset.sourceType == DatasetSourceType.ETL) {
          val triggerRunnerEvent =
              TriggerRunnerEvent(
                  this, organizationId, dataset.source!!.location, dataset.source!!.name!!)
          this.eventPublisher.publishEvent(triggerRunnerEvent)
          triggerRunnerEvent.response
        } else {
          val requestJobId =
              this.idGenerator.generate(scope = "graphdataimport", prependPrefix = "gdi-")
          val graphImportEvent =
              sendTwingraphImportEvent(requestJobId, organizationId, dataset, dataSourceLocation)
          logger.debug("refreshDataset={}", graphImportEvent.response)
          requestJobId
        }
    datasetRepository.save(dataset.apply { source!!.jobId = requestJobId })
    return DatasetTwinGraphInfo(
        jobId = requestJobId, datasetId = dataset.id, status = dataset.ingestionStatus?.value)
  }

  override fun rollbackRefresh(organizationId: String, datasetId: String): String {
    checkIfGraphFunctionalityIsAvailable()
    var dataset = getVerifiedDataset(organizationId, datasetId, PERMISSION_WRITE)

    val status = getDatasetTwingraphStatus(organizationId, datasetId)
    if (status != IngestionStatusEnum.ERROR.value) {
      throw IllegalArgumentException("The dataset hasn't failed and can't be rolled back")
    }

    dataset =
        if (dataset.twincacheStatus == TwincacheStatusEnum.FULL) {
          datasetRepository.save(
              dataset.apply { dataset.ingestionStatus = IngestionStatusEnum.SUCCESS })
        } else {
          datasetRepository.save(
              dataset.apply { dataset.ingestionStatus = IngestionStatusEnum.NONE })
        }
    return "Dataset $datasetId status is now ${dataset.ingestionStatus}"
  }

  override fun deleteDataset(organizationId: String, datasetId: String) {
    val dataset = getVerifiedDataset(organizationId, datasetId, PERMISSION_DELETE)

    if (useGraphModule && unifiedJedis.exists(dataset.twingraphId!!)) {
      unifiedJedis.del(dataset.twingraphId!!)
    }

    dataset.linkedWorkspaceIdList?.forEach { unlinkWorkspace(organizationId, datasetId, it) }

    datasetRepository.delete(dataset)
  }

  override fun updateDataset(organizationId: String, datasetId: String, dataset: Dataset): Dataset {
    val existingDataset = getVerifiedDataset(organizationId, datasetId, PERMISSION_WRITE)

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
  ): List<Any> {
    checkIfGraphFunctionalityIsAvailable()
    val dataset =
        getDatasetWithStatus(organizationId, datasetId, status = IngestionStatusEnum.SUCCESS)

    val gson = Gson()
    val mapAdapter = gson.getAdapter(object : TypeToken<List<Any>>() {})
    return mapAdapter.fromJson(query(dataset, datasetTwinGraphQuery.query).toJsonString())
  }

  override fun query(dataset: Dataset, query: String, isReadOnly: Boolean): ResultSet {
    return if (isReadOnly) {
      unifiedJedis.graphReadonlyQuery(
          dataset.twingraphId!!, query, csmPlatformProperties.twincache.queryTimeout)
    } else {
      unifiedJedis.graphQuery(
          dataset.twingraphId!!, query, csmPlatformProperties.twincache.queryTimeout)
    }
  }

  override fun findByOrganizationIdAndDatasetId(
      organizationId: String,
      datasetId: String
  ): Dataset? {
    organizationService.getVerifiedOrganization(organizationId)
    return datasetRepository.findBy(organizationId, datasetId).getOrNull()
  }

  override fun addOrUpdateAccessControl(
      organizationId: String,
      dataset: Dataset,
      identity: String,
      role: String
  ): DatasetAccessControl {
    val rbacSecurity = csmRbac.setUserRole(dataset.getRbac(), identity, role)
    dataset.setRbac(rbacSecurity)
    datasetRepository.save(dataset)
    val rbacAccessControl = csmRbac.getAccessControl(dataset.getRbac(), identity)
    return DatasetAccessControl(rbacAccessControl.id, rbacAccessControl.role)
  }

  fun <T> trx(dataset: Dataset, actionLambda: (Dataset) -> T): T {
    dataset.ingestionStatus?.takeUnless { it == IngestionStatusEnum.PENDING }
        ?: throw CsmClientException("Dataset in use, cannot update. Retry later")
    datasetRepository.save(dataset.apply { ingestionStatus = IngestionStatusEnum.PENDING })
    try {
      val returnData = actionLambda(dataset)
      datasetRepository.save(dataset.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
      return returnData
    } catch (e: Exception) {
      logger.error(e.message)
      datasetRepository.save(dataset.apply { ingestionStatus = IngestionStatusEnum.ERROR })
      throw e
    }
  }

  override fun twingraphBatchUpdate(
      organizationId: String,
      datasetId: String,
      twinGraphQuery: DatasetTwinGraphQuery,
      body: Resource
  ): TwinGraphBatchResult {
    checkIfGraphFunctionalityIsAvailable()
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
    checkIfGraphFunctionalityIsAvailable()
    val dataset =
        getDatasetWithStatus(organizationId, datasetId, status = IngestionStatusEnum.SUCCESS)
    val bulkQueryKey = bulkQueryKey(dataset.twingraphId!!, datasetTwinGraphQuery.query, null)
    val twinGraphHash = DatasetTwinGraphHash(bulkQueryKey.second)
    val keyExists = unifiedJedis.exists(bulkQueryKey.first)
    if (keyExists) {
      return twinGraphHash
    }
    val resultSet = query(dataset, datasetTwinGraphQuery.query)

    GlobalScope.launch(SecurityCoroutineContext()) {
      val zip =
          zipBytesWithFileNames(
              mapOf(
                  "bulkQuery.json" to resultSet.toJsonString().toByteArray(StandardCharsets.UTF_8)))
      unifiedJedis.setex(bulkQueryKey.first, csmPlatformProperties.twincache.queryBulkTTL, zip)
    }

    return twinGraphHash
  }

  override fun downloadTwingraph(organizationId: String, hash: String): Resource {
    checkIfGraphFunctionalityIsAvailable()
    organizationService.getVerifiedOrganization(organizationId)

    val bulkQueryId = bulkQueryKey(hash)
    if (!unifiedJedis.exists(bulkQueryId)) {
      throw CsmResourceNotFoundException("No graph found with hash: $hash  Try later")
    } else if (unifiedJedis.ttl(bulkQueryId) < 0) {
      throw CsmResourceNotFoundException(
          "Graph with hash: $hash is expired. Try to repeat bulk query")
    }
    val twinData = unifiedJedis.get(bulkQueryId)

    val httpServletResponse =
        ((RequestContextHolder.getRequestAttributes() as ServletRequestAttributes).response)
    val contentDisposition =
        ContentDisposition.builder("attachment").filename("TwinGraph-$hash.zip").build()
    httpServletResponse!!.setHeader(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())

    return ByteArrayResource(twinData)
  }

  @Suppress("ThrowsCount")
  fun getDatasetWithStatus(
      organizationId: String,
      datasetId: String,
      status: IngestionStatusEnum? = null
  ): Dataset {
    val dataset = getVerifiedDataset(organizationId, datasetId)
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
    checkIfGraphFunctionalityIsAvailable()
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
    checkIfGraphFunctionalityIsAvailable()
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

  override fun linkWorkspace(
      organizationId: String,
      datasetId: String,
      workspaceId: String
  ): Dataset {
    this.getVerifiedDataset(organizationId, datasetId, PERMISSION_WRITE)
    sendAddDatasetToWorkspaceEvent(organizationId, workspaceId, datasetId)
    return addWorkspaceToLinkedWorkspaceIdList(organizationId, datasetId, workspaceId)
  }

  @EventListener(AddWorkspaceToDataset::class)
  fun processEventAddWorkspace(addWorkspaceToDataset: AddWorkspaceToDataset) {
    this.getVerifiedDataset(
        addWorkspaceToDataset.organizationId, addWorkspaceToDataset.datasetId, PERMISSION_WRITE)
    addWorkspaceToLinkedWorkspaceIdList(
        addWorkspaceToDataset.organizationId,
        addWorkspaceToDataset.datasetId,
        addWorkspaceToDataset.workspaceId)
  }

  fun addWorkspaceToLinkedWorkspaceIdList(
      organizationId: String,
      datasetId: String,
      workspaceId: String
  ): Dataset {
    val dataset = findDatasetById(organizationId, datasetId)

    if (dataset.linkedWorkspaceIdList != null) {
      if (dataset.linkedWorkspaceIdList!!.contains(workspaceId)) {
        return dataset
      } else {
        dataset.linkedWorkspaceIdList!!.add(workspaceId)
      }
    } else {
      dataset.linkedWorkspaceIdList = mutableListOf(workspaceId)
    }
    return datasetRepository.save(dataset)
  }

  override fun unlinkWorkspace(
      organizationId: String,
      datasetId: String,
      workspaceId: String
  ): Dataset {
    this.getVerifiedDataset(organizationId, datasetId, PERMISSION_WRITE)
    sendRemoveDatasetFromWorkspaceEvent(organizationId, workspaceId, datasetId)
    return removeWorkspaceFromLinkedWorkspaceIdList(organizationId, datasetId, workspaceId)
  }

  @EventListener(RemoveWorkspaceFromDataset::class)
  fun processEventRemoveWorkspace(removeWorkspaceFromDataset: RemoveWorkspaceFromDataset) {
    this.getVerifiedDataset(
        removeWorkspaceFromDataset.organizationId,
        removeWorkspaceFromDataset.datasetId,
        PERMISSION_WRITE)
    removeWorkspaceFromLinkedWorkspaceIdList(
        removeWorkspaceFromDataset.organizationId,
        removeWorkspaceFromDataset.datasetId,
        removeWorkspaceFromDataset.workspaceId)
  }

  fun removeWorkspaceFromLinkedWorkspaceIdList(
      organizationId: String,
      datasetId: String,
      workspaceId: String
  ): Dataset {
    val dataset = findDatasetById(organizationId, datasetId)

    if (dataset.linkedWorkspaceIdList != null) {
      if (dataset.linkedWorkspaceIdList!!.contains(workspaceId)) {
        dataset.linkedWorkspaceIdList!!.remove(workspaceId)
        return datasetRepository.save(dataset)
      }
    }

    return dataset
  }

  override fun updateTwingraphEntities(
      organizationId: String,
      datasetId: String,
      type: String,
      graphProperties: List<GraphProperties>
  ): String {
    checkIfGraphFunctionalityIsAvailable()
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
    checkIfGraphFunctionalityIsAvailable()
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
    val existingDataset = getVerifiedDataset(organizationId, datasetId, PERMISSION_WRITE)
    if (datasetCompatibility.isEmpty()) {
      return datasetCompatibility
    }

    val datasetCompatibilityMap =
        existingDataset.compatibility
            ?.associateBy { "${it.solutionKey}-${it.minimumVersion}-${it.maximumVersion}" }
            ?.toMutableMap() ?: mutableMapOf()
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
    organizationService.getVerifiedOrganization(organizationId)

    val defaultPageSize = csmPlatformProperties.twincache.dataset.defaultPageSize
    val pageable = constructPageRequest(page, size, defaultPageSize)
    var datasetList: List<Dataset>
    if (pageable != null) {
      datasetList =
          datasetRepository
              .findDatasetByTags(organizationId, datasetSearch.datasetTags.toSet(), pageable)
              .toList()
    }
    datasetList =
        findAllPaginated(defaultPageSize) {
          datasetRepository
              .findDatasetByTags(organizationId, datasetSearch.datasetTags.toSet(), it)
              .toList()
        }
    datasetList.forEach { it.security = updateSecurityVisibility(it).security }
    return datasetList
  }

  override fun getDatasetSecurity(organizationId: String, datasetId: String): DatasetSecurity {
    val dataset = getVerifiedDataset(organizationId, datasetId, PERMISSION_READ_SECURITY)
    return dataset.security
        ?: throw CsmResourceNotFoundException("RBAC not defined for ${dataset.id}")
  }

  override fun setDatasetDefaultSecurity(
      organizationId: String,
      datasetId: String,
      datasetRole: DatasetRole
  ): DatasetSecurity {
    val dataset = getVerifiedDataset(organizationId, datasetId, PERMISSION_WRITE_SECURITY)
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
    val dataset = getVerifiedDataset(organizationId, datasetId, PERMISSION_WRITE_SECURITY)
    val organization = organizationService.getVerifiedOrganization(organizationId)

    val users = getDatasetSecurityUsers(organizationId, datasetId)
    if (users.contains(datasetAccessControl.id)) {
      throw IllegalArgumentException("User is already in this Dataset security")
    }

    val rbacSecurity =
        csmRbac.addUserRole(
            organization.security.toGenericSecurity(organizationId),
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
    val dataset = getVerifiedDataset(organizationId, datasetId, PERMISSION_READ_SECURITY)
    val rbacAccessControl = csmRbac.getAccessControl(dataset.getRbac(), identityId)
    return DatasetAccessControl(rbacAccessControl.id, rbacAccessControl.role)
  }

  override fun updateDatasetAccessControl(
      organizationId: String,
      datasetId: String,
      identityId: String,
      datasetRole: DatasetRole
  ): DatasetAccessControl {
    val dataset = getVerifiedDataset(organizationId, datasetId, PERMISSION_WRITE_SECURITY)
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
    val dataset = getVerifiedDataset(organizationId, datasetId, PERMISSION_WRITE_SECURITY)
    val rbacSecurity = csmRbac.removeUser(dataset.getRbac(), identityId)
    dataset.setRbac(rbacSecurity)
    datasetRepository.save(dataset)
  }

  override fun getDatasetSecurityUsers(organizationId: String, datasetId: String): List<String> {
    val dataset = getVerifiedDataset(organizationId, datasetId, PERMISSION_READ_SECURITY)
    return csmRbac.getUsers(dataset.getRbac())
  }

  @EventListener(OrganizationUnregistered::class)
  @Async("csm-in-process-event-executor")
  fun onOrganizationUnregistered(organizationUnregistered: OrganizationUnregistered) {
    var pageable = PageRequest.ofSize(csmPlatformProperties.twincache.dataset.defaultPageSize)
    do {
      val datasetList =
          datasetRepository
              .findByOrganizationIdNoSecurity(organizationUnregistered.organizationId, pageable)
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
    val connectorName = "PlatformGenerated" + twinCacheConnectorProperties.name
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
                ioTypes = listOf(IoTypesEnum.read),
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

  private fun sendTwingraphImportJobInfoRequestEvent(
      dataset: Dataset,
      organizationId: String
  ): TwingraphImportJobInfoRequest {
    val twingraphImportJobInfoRequest =
        TwingraphImportJobInfoRequest(this, dataset.source!!.jobId!!, organizationId)

    this.eventPublisher.publishEvent(twingraphImportJobInfoRequest)
    return twingraphImportJobInfoRequest
  }

  private fun sendTwingraphImportEvent(
      requestJobId: String,
      organizationId: String,
      dataset: Dataset,
      dataSourceLocation: String?
  ): TwingraphImportEvent {
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
    return graphImportEvent
  }

  private fun sendRemoveDatasetFromWorkspaceEvent(
      organizationId: String,
      workspaceId: String,
      datasetId: String
  ) {
    this.eventPublisher.publishEvent(
        RemoveDatasetFromWorkspace(this, organizationId, workspaceId, datasetId))
  }

  private fun sendAddDatasetToWorkspaceEvent(
      organizationId: String,
      workspaceId: String,
      datasetId: String
  ) {
    this.eventPublisher.publishEvent(
        AddDatasetToWorkspace(this, organizationId, workspaceId, datasetId))
  }

  override fun getVerifiedDataset(
      organizationId: String,
      datasetId: String,
      requiredPermission: String
  ): Dataset {
    organizationService.getVerifiedOrganization(organizationId)
    val dataset =
        datasetRepository.findBy(organizationId, datasetId).orElseThrow {
          CsmResourceNotFoundException(
              "Dataset $datasetId not found in organization $organizationId")
        }
    csmRbac.verify(dataset.getRbac(), requiredPermission)
    return dataset
  }

  fun updateSecurityVisibility(dataset: Dataset): Dataset {
    if (csmRbac.check(dataset.getRbac(), PERMISSION_READ_SECURITY).not()) {
      val username = getCurrentAccountIdentifier(csmPlatformProperties)
      val retrievedAC = dataset.security!!.accessControlList.firstOrNull { it.id == username }
      if (retrievedAC != null) {
        return dataset.copy(
            security =
                DatasetSecurity(
                    default = dataset.security!!.default,
                    accessControlList = mutableListOf(retrievedAC)))
      } else {
        return dataset.copy(
            security =
                DatasetSecurity(
                    default = dataset.security!!.default, accessControlList = mutableListOf()))
      }
    }
    return dataset
  }
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
