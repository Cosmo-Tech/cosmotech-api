// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
@file:Suppress("DEPRECATION")

package com.cosmotech.dataset.service

import com.azure.storage.blob.BlobServiceClient
import com.azure.storage.common.sas.AccountSasPermission
import com.azure.storage.common.sas.AccountSasResourceType
import com.azure.storage.common.sas.AccountSasService
import com.azure.storage.common.sas.AccountSasSignatureValues
import com.cosmotech.api.CsmPhoenixService
import com.cosmotech.api.azure.sanitizeForAzureStorage
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
import com.cosmotech.api.utils.changed
import com.cosmotech.api.utils.compareToAndMutateIfNeeded
import com.cosmotech.api.utils.constructPageRequest
import com.cosmotech.api.utils.findAllPaginated
import com.cosmotech.api.utils.getCurrentAccountIdentifier
import com.cosmotech.api.utils.getCurrentAuthenticatedRoles
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.api.utils.unzip
import com.cosmotech.connector.ConnectorApiServiceInterface
import com.cosmotech.connector.domain.Connector
import com.cosmotech.connector.domain.ConnectorParameter
import com.cosmotech.connector.domain.ConnectorParameterGroup
import com.cosmotech.connector.domain.IoTypesEnum
import com.cosmotech.dataset.DatasetApiServiceInterface
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.dataset.domain.DatasetAccessControl
import com.cosmotech.dataset.domain.DatasetCompatibility
import com.cosmotech.dataset.domain.DatasetConnector
import com.cosmotech.dataset.domain.DatasetCopyParameters
import com.cosmotech.dataset.domain.DatasetRole
import com.cosmotech.dataset.domain.DatasetSearch
import com.cosmotech.dataset.domain.DatasetSecurity
import com.cosmotech.dataset.domain.DatasetSourceType
import com.cosmotech.dataset.domain.DatasetTwinGraphInfo
import com.cosmotech.dataset.domain.FileUploadMetadata
import com.cosmotech.dataset.domain.FileUploadValidation
import com.cosmotech.dataset.domain.GraphProperties
import com.cosmotech.dataset.domain.IngestionStatusEnum
import com.cosmotech.dataset.domain.SourceInfo
import com.cosmotech.dataset.domain.TwincacheStatusEnum
import com.cosmotech.dataset.repository.DatasetRepository
import com.cosmotech.organization.OrganizationApiServiceInterface
import com.cosmotech.organization.service.getRbac
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.jvm.optionals.getOrNull
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.csv.CSVFormat
import org.apache.commons.lang3.NotImplementedException
import org.neo4j.driver.internal.InternalNode
import org.neo4j.driver.internal.InternalRelationship
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.event.EventListener
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.data.domain.PageRequest
import org.springframework.data.neo4j.core.Neo4jClient
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

const val TYPE_NODE = "node"
const val TYPE_RELATIONSHIP = "relationship"
const val NODES_ZIP_FOLDER = "nodes"
const val EDGES_ZIP_FOLDER = "edges"
const val TWINCACHE_CONNECTOR = "TwincacheConnector"
const val TWINCACHE_NAME = "TWIN_CACHE_NAME"
const val SAS_TOKEN_EXPIRATION_TIME_IN_MINUTES = 5L

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
    private val azureStorageBlobServiceClient: BlobServiceClient,
    private val csmRbac: CsmRbac,
    private val csmAdmin: CsmAdmin,
    private val neo4jClient: Neo4jClient,
) : CsmPhoenixService(), DatasetApiServiceInterface {

  @Value("\${csm.platform.twincache.useGraphModule}") private var useGraphModule: Boolean = true

  private val notImplementedExceptionMessage =
      "The API is not configured to use Graph functionalities. " +
          "This endpoint is deactivated. " +
          "To activate that, set the API configuration correctly."

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
              datasetRepository.findAll(it).toList()
            }
          }
    } else {
      result =
          if (rbacEnabled) {
            val currentUser = getCurrentAccountIdentifier(this.csmPlatformProperties)
            datasetRepository.findByOrganizationId(organizationId, currentUser, pageable).toList()
          } else {
            datasetRepository.findAll(pageable).toList()
          }
    }

    return result
  }

  override fun findDatasetById(organizationId: String, datasetId: String): Dataset {
    return getVerifiedDataset(organizationId, datasetId)
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
    }
        ?: throw IllegalArgumentException(
            "Source cannot be null for source type 'ADT' or 'Storage'")

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

  private fun checkIfGraphFunctionalityIsAvailable() {
    if (!useGraphModule) {
      throw NotImplementedException(notImplementedExceptionMessage)
    }
  }

  @Suppress("MagicNumber")
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
      try {
        trx(dataset) {
          val sasToken = buildSasToken()
          val csvFormat = CSVFormat.DEFAULT.builder().setHeader().setTrim(true).build()

          nodes.forEach {
            val blobName =
                "graph/datasets/${datasetId.sanitizeForAzureStorage()}" +
                    "/${NODES_ZIP_FOLDER}/${it.filename}.csv"

            azureStorageBlobServiceClient
                .getBlobContainerClient(organizationId.sanitizeForAzureStorage())
                .getBlobClient(blobName)
                .upload(it.content.inputStream(), it.content.size.toLong(), true)

            val headers = csvFormat.parse(it.content.inputStream().reader()).headerNames

            val properties =
                headers
                    .mapIndexed { index, header -> "$header:row[$index]" }
                    .joinToString(
                        separator = " , ", prefix = "{", postfix = ", datasetId: \"$datasetId\"}")

            val blobUrl =
                "${azureStorageBlobServiceClient.accountUrl}/${organizationId.sanitizeForAzureStorage()}" +
                    "/$blobName?$sasToken"

            val nodeName = blobName.substringAfterLast("/").substringBeforeLast(".")
            val query =
                "LOAD CSV FROM \"$blobUrl\" AS row " +
                    "WITH row SKIP 1 " +
                    "CALL(row) { " +
                    "MERGE (d:$nodeName $properties) " +
                    "} IN TRANSACTIONS"
            logger.debug(query)
            neo4jClient.query(query).run()
            val queryIndex =
                "CREATE TEXT INDEX ${nodeName}_datasetId_index " +
                    "IF NOT EXISTS FOR (n:$nodeName) ON (n.datasetId)"
            logger.debug(queryIndex)
            neo4jClient.query(queryIndex).run()
          }

          edges.forEach {
            val blobName =
                "graph/datasets/${datasetId.sanitizeForAzureStorage()}" +
                    "/${EDGES_ZIP_FOLDER}/${it.filename}.csv"

            azureStorageBlobServiceClient
                .getBlobContainerClient(organizationId.sanitizeForAzureStorage())
                .getBlobClient(blobName)
                .upload(it.content.inputStream(), it.content.size.toLong(), true)

            val csvParser = csvFormat.parse(it.content.inputStream().reader())
            val headers = csvParser.headerNames
            val firstDataLine = csvParser.elementAtOrNull(1)?.toList()
            if (firstDataLine != null) {

              val properties =
                  headers
                      .mapIndexed { index, header -> "$header:row[$index]" }
                      .joinToString(
                          separator = " , ", prefix = "{", postfix = ", datasetId: \"$datasetId\"}")

              val blobUrl =
                  "${azureStorageBlobServiceClient.accountUrl}/${organizationId.sanitizeForAzureStorage()}" +
                      "/$blobName?$sasToken"

              val relationName = blobName.substringAfterLast("/").substringBeforeLast(".")
              val query =
                  "LOAD CSV FROM \"$blobUrl\" AS row " +
                      "WITH row SKIP 1 " +
                      "CALL(row) { " +
                      "MATCH (a:${firstDataLine[1]} {id: row[0], datasetId: \"$datasetId\"}) " +
                      "MATCH (b:${firstDataLine[3]} {id: row[2], datasetId: \"$datasetId\"}) " +
                      "MERGE(a)-[:$relationName $properties]->(b)" +
                      "} IN TRANSACTIONS"
              logger.debug(query)
              neo4jClient.query(query).run()

              val queryIndex =
                  "CREATE TEXT INDEX ${relationName}_datasetId_index " +
                      "IF NOT EXISTS FOR ()-[r:$relationName]-() ON (r.datasetId)"
              logger.debug(queryIndex)
              neo4jClient.query(queryIndex).run()
            }
          }
        }
        datasetRepository.save(dataset.apply { twincacheStatus = TwincacheStatusEnum.FULL })
      } catch (e: Exception) {
        throw CsmClientException(e.message ?: "Twingraph upload error", e)
      }
    }
    return fileUpload
  }

  private fun buildSasToken(): String {
    val expiryTime = OffsetDateTime.now().plusMinutes(SAS_TOKEN_EXPIRATION_TIME_IN_MINUTES)
    val accountSasPermission =
        AccountSasPermission().setListPermission(true).setReadPermission(true)
    val services = AccountSasService().setBlobAccess(true)
    val resourceTypes = AccountSasResourceType().setObject(true)

    // Generate the account SAS
    val accountSasValues =
        AccountSasSignatureValues(expiryTime, accountSasPermission, services, resourceTypes)
    val sasToken = azureStorageBlobServiceClient.generateAccountSas(accountSasValues)

    logger.debug("SAS Token {}", sasToken)
    return sasToken
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
        if (useGraphModule && neo4jClient.exists(datasetId)) {
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
        } else if (useGraphModule && !neo4jClient.exists(datasetId)) {
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

    if (useGraphModule && neo4jClient.exists(datasetId)) {
      val deletionResults = neo4jClient.del(datasetId)
      logger.debug(
          "${deletionResults["nodesDeleted"]} have been deleted and " +
              "${deletionResults["relationshipsDeleted"]} have been deleted")
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

  override fun getAllData(organizationId: String, datasetId: String, name: String?): List<Any> {
    if (name.isNullOrBlank()) {
      return neo4jClient
          .query(
              "MATCH (n)-[r]->(m) " +
                  "WHERE n.datasetId= \"$datasetId\" AND " +
                  "r.datasetId= \"$datasetId\" AND " +
                  "m.datasetId= \"$datasetId\" " +
                  "return n,r,m")
          .fetch()
          .all()
          .map { it.values.toList() }
          .filter { it.isNotEmpty() }
          .map {
            listOf(
                (it[0] as InternalNode).toMap(),
                (it[1] as InternalRelationship).toMap(),
                (it[2] as InternalNode).toMap())
          }
    }
    val nodeResult =
        neo4jClient
            .query("MATCH (n:$name) WHERE n.datasetId= \"$datasetId\"return n")
            .fetch()
            .all()
            .map { it.values.toList() }
            .filter { it.isNotEmpty() }
            .map { listOf((it[0] as InternalNode).toMap()) }
    if (nodeResult.isNotEmpty()) {
      return nodeResult
    }

    val edgeResult =
        neo4jClient
            .query(
                "MATCH (n)-[r:$name]->(m) " +
                    "WHERE n.datasetId= \"$datasetId\" AND " +
                    "r.datasetId= \"$datasetId\" AND " +
                    "m.datasetId= \"$datasetId\" " +
                    "return n,r,m")
            .fetch()
            .all()
            .map { it.values.toList() }
            .filter { it.isNotEmpty() }
            .map {
              listOf(
                  (it[0] as InternalNode).toMap(),
                  (it[1] as InternalRelationship).toMap(),
                  (it[2] as InternalNode).toMap())
            }
    if (edgeResult.isNotEmpty()) {
      return edgeResult
    }

    return emptyList()
  }

  override fun getDataInfo(organizationId: String, datasetId: String): Map<String, Any> {
    val result = mutableMapOf<String, Any>()
    val nodeCount = mutableMapOf<String, Any>()
    val relCount = mutableMapOf<String, Any>()
    val entityProperties = mutableMapOf<String, Any>()

    neo4jClient
        .query(
            "MATCH(n) " +
                "WHERE n.datasetId = \"$datasetId\" " +
                "UNWIND keys(n) AS key " +
                "RETURN labels(n) as labels, " +
                "collect(distinct key) AS distinct_keys " +
                "ORDER BY labels ASC")
        .fetch()
        .all()
        .map { it.values.toList() }
        .filter { it.isNotEmpty() }
        .forEach {
          entityProperties[(it[0] as List<*>).first().toString()] =
              (it[1] as List<String>).sortedBy { propertyName -> propertyName.lowercase() }
        }

    neo4jClient
        .query(
            "MATCH (n) " +
                "WHERE n.datasetId= \"$datasetId\"" +
                "RETURN distinct labels(n), count(*)")
        .fetch()
        .all()
        .map { it.values.toList() }
        .filter { it.isNotEmpty() }
        .forEach { nodeCount[(it[0] as List<*>).first().toString()] = it[1] }

    result["nodes"] =
        nodeCount.map { (key, value) ->
          mapOf(key to mapOf("count" to value, "properties" to entityProperties[key]))
        }

    neo4jClient
        .query(
            "MATCH ()-[r]-() " +
                "WHERE r.datasetId= \"$datasetId\"" +
                "RETURN distinct type(r), count(*)")
        .fetch()
        .all()
        .map { it.values.toList() }
        .filter { it.isNotEmpty() }
        .forEach { relCount[it[0].toString()] = it[1] }

    result["relationships"] =
        relCount.map { (key, value) ->
          mapOf(key to mapOf("count" to value, "properties" to entityProperties[key]))
        }

    return result
  }

  fun InternalNode.toMap(): MutableMap<String, Any> {
    val properties = this.asValue().asMap().toMutableMap()
    properties["id"] = this.elementId()
    properties["labels"] = this.labels()
    return properties
  }

  fun InternalRelationship.toMap(): MutableMap<String, Any> {
    val properties = this.asValue().asMap().toMutableMap()
    properties["type"] = this.type()
    properties["id"] = this.elementId()
    properties["start_id"] = this.startNodeElementId()
    properties["end_id"] = this.endNodeElementId()
    return properties
  }

  @Suppress("MagicNumber")
  override fun downloadTwingraph(organizationId: String, datasetId: String): Resource {
    checkIfGraphFunctionalityIsAvailable()
    organizationService.getVerifiedOrganization(organizationId)

    if (!neo4jClient.exists(datasetId)) {
      throw CsmResourceNotFoundException("No graph found with datasetId: $datasetId  Try later")
    }

    /*
        val twinData = unifiedJedis.get(bulkQueryId)

        val httpServletResponse =
            ((RequestContextHolder.getRequestAttributes() as ServletRequestAttributes).response)
        val contentDisposition =
            ContentDisposition.builder("attachment").filename("TwinGraph-$datasetId.zip").build()
        httpServletResponse!!.setHeader(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
    */

    return ByteArrayResource(ByteArray(10))
  }

  @Suppress("ThrowsCount")
  fun getDatasetWithStatus(
      organizationId: String,
      datasetId: String,
      status: IngestionStatusEnum? = null
  ): Dataset {
    val dataset = getVerifiedDataset(organizationId, datasetId)
    status?.let {
      dataset.ingestionStatus?.takeIf { it == status }
          ?: throw CsmClientException(
              "Dataset status is ${dataset.ingestionStatus?.value}, not $status")
    }
    return dataset
  }

  override fun createTwingraphEntities(
      organizationId: String,
      datasetId: String,
      type: String,
      graphProperties: List<GraphProperties>
  ): Map<String, Any> {
    checkIfGraphFunctionalityIsAvailable()
    val dataset = getDatasetWithStatus(organizationId, datasetId)
    csmRbac.verify(dataset.getRbac(), PERMISSION_WRITE)
    val result = mutableMapOf<String, Any>()
    trx(dataset) { localDataset ->
      when (type) {
        TYPE_NODE ->
            graphProperties.forEach {
              val nodeInfo =
                  neo4jClient
                      .query(
                          "CREATE (a:${it.type} {id:'${it.name}',datasetId:'$datasetId',${it.params}}) " +
                              "RETURN a")
                      .fetch()
                      .all()
                      .map { queryResult -> queryResult.values.toList() }
                      .filter { values -> values.isNotEmpty() }
              result.putAll((nodeInfo[0] as InternalNode).toMap())
            }
        TYPE_RELATIONSHIP ->
            graphProperties.forEach {
              val relInfo =
                  neo4jClient
                      .query(
                          "MATCH (a),(b) " +
                              "WHERE a.id='${it.source}' " +
                              "AND b.id='${it.target}' " +
                              "AND a.datasetId='$datasetId' " +
                              "AND b.datasetId='$datasetId' " +
                              "CREATE (a)-[r:${it.type} {id:'${it.name}',datasetId:'$datasetId',${it.params}}]->(b) " +
                              "RETURN r")
                      .fetch()
                      .all()
                      .map { queryResult -> queryResult.values.toList() }
                      .filter { values -> values.isNotEmpty() }
              result.putAll((relInfo[0] as InternalRelationship).toMap())
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
  ): Map<String, Any> {
    checkIfGraphFunctionalityIsAvailable()
    getDatasetWithStatus(organizationId, datasetId)
    var result = mutableMapOf<String, Any>()
    when (type) {
      TYPE_NODE ->
          ids.forEach {
            val nodeInfo =
                neo4jClient
                    .query(
                        "MATCH (a) " +
                            "WHERE a.id='$it' AND a.datasetId='$datasetId' " +
                            "RETURN a")
                    .fetch()
                    .all()
                    .map { queryResult -> queryResult.values.toList() }
                    .filter { values -> values.isNotEmpty() }
            result.putAll((nodeInfo[0] as InternalNode).toMap())
          }
      TYPE_RELATIONSHIP ->
          ids.forEach {
            val relInfo =
                neo4jClient
                    .query(
                        "MATCH ()-[r]->() " +
                            "WHERE r.id='$it' AND r.datasetId='$datasetId' " +
                            "RETURN r")
                    .fetch()
                    .all()
                    .map { queryResult -> queryResult.values.toList() }
                    .filter { values -> values.isNotEmpty() }
            result.putAll((relInfo[0] as InternalRelationship).toMap())
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
    sendAddDatasetToWorkspaceEvent(organizationId, workspaceId, datasetId)
    return addWorkspaceToLinkedWorkspaceIdList(organizationId, datasetId, workspaceId)
  }

  @EventListener(AddWorkspaceToDataset::class)
  fun processEventAddWorkspace(addWorkspaceToDataset: AddWorkspaceToDataset) {
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

    sendRemoveDatasetFromWorkspaceEvent(organizationId, workspaceId, datasetId)

    return removeWorkspaceFromLinkedWorkspaceIdList(organizationId, datasetId, workspaceId)
  }

  @EventListener(RemoveWorkspaceFromDataset::class)
  fun processEventRemoveWorkspace(removeWorkspaceFromDataset: RemoveWorkspaceFromDataset) {
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
  ): Map<String, Any> {
    checkIfGraphFunctionalityIsAvailable()
    val dataset = getDatasetWithStatus(organizationId, datasetId)
    csmRbac.verify(dataset.getRbac(), PERMISSION_WRITE)
    val result = mutableMapOf<String, Any>()
    trx(dataset) { localDataset ->
      when (type) {
        TYPE_NODE ->
            graphProperties.forEach {
              val nodeInfo =
                  neo4jClient
                      .query(
                          "MATCH (a {id:'${it.name}',datasetId='$datasetId'}) " +
                              "SET a = {id:'${it.name}',datasetId='$datasetId',${it.params}} " +
                              "RETURN a")
                      .fetch()
                      .all()
                      .map { queryResult -> queryResult.values.toList() }
                      .filter { values -> values.isNotEmpty() }
              result.putAll((nodeInfo[0] as InternalNode).toMap())
            }
        TYPE_RELATIONSHIP ->
            graphProperties.forEach {
              val relInfo =
                  neo4jClient
                      .query(
                          "MATCH ()-[r {id:'${it.name},datasetId='$datasetId'}]-() " +
                              "SET r = {id:'${it.name}',datasetId='$datasetId',${it.params}} " +
                              "RETURN r")
                      .fetch()
                      .all()
                      .map { queryResult -> queryResult.values.toList() }
                      .filter { values -> values.isNotEmpty() }
              result.putAll((relInfo[0] as InternalRelationship).toMap())
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
    return trx(dataset) {
      var nodesDeleted = 0
      var relsDeleted = 0
      when (type) {
        TYPE_NODE ->
            ids.forEach {
              nodesDeleted +=
                  neo4jClient
                      .query(
                          "MATCH (a) " +
                              "WHERE a.id='$it' AND a.datasetId='$datasetId' " +
                              "DELETE a")
                      .run()
                      .counters()
                      .nodesDeleted()
            }
        TYPE_RELATIONSHIP ->
            ids.forEach {
              relsDeleted +=
                  neo4jClient
                      .query(
                          "MATCH ()-[r]-() " +
                              "WHERE r.id='$it' AND r.datasetId='$datasetId' " +
                              "DELETE r")
                      .run()
                      .counters()
                      .relationshipsDeleted()
            }
        else -> throw CsmResourceNotFoundException("Bad Type : $type")
      }
      logger.debug(
          "$nodesDeleted nodes have been deleted and $relsDeleted relationships have been deleted")
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
    organizationService.getVerifiedOrganization(organizationId)

    val defaultPageSize = csmPlatformProperties.twincache.dataset.defaultPageSize
    val pageable = constructPageRequest(page, size, defaultPageSize)
    if (pageable != null) {
      return datasetRepository
          .findDatasetByTags(organizationId, datasetSearch.datasetTags.toSet(), pageable)
          .toList()
    }
    return findAllPaginated(defaultPageSize) {
      datasetRepository
          .findDatasetByTags(organizationId, datasetSearch.datasetTags.toSet(), it)
          .toList()
    }
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

fun Neo4jClient.exists(datasetId: String): Boolean {
  return !this.query("MATCH (n) WHERE n.datasetId='$datasetId' RETURN n").fetch().all().isEmpty()
}

fun Neo4jClient.del(datasetId: String): Map<String, Int> {
  val summaryCounters =
      this.query("MATCH (n) WHERE n.datasetId='$datasetId' DETACH DELETE n").run().counters()
  return mapOf(
      "nodesDeleted" to summaryCounters.nodesDeleted(),
      "relationshipsDeleted" to summaryCounters.relationshipsDeleted())
}
