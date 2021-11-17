// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.azure

import com.azure.cosmos.models.CosmosContainerProperties
import com.azure.cosmos.models.CosmosQueryRequestOptions
import com.azure.cosmos.models.SqlParameter
import com.azure.cosmos.models.SqlQuerySpec
import com.cosmotech.api.azure.AbstractCosmosBackedService
import com.cosmotech.api.azure.findAll
import com.cosmotech.api.azure.findByIdOrThrow
import com.cosmotech.api.events.ConnectorRemoved
import com.cosmotech.api.events.ConnectorRemovedForOrganization
import com.cosmotech.api.events.OrganizationRegistered
import com.cosmotech.api.events.OrganizationUnregistered
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.utils.changed
import com.cosmotech.api.utils.compareToAndMutateIfNeeded
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.api.utils.toDomain
import com.cosmotech.connector.api.ConnectorApiService
import com.cosmotech.dataset.api.DatasetApiService
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.dataset.domain.DatasetCompatibility
import com.cosmotech.dataset.domain.DatasetCopyParameters
import com.cosmotech.dataset.domain.DatasetSearch
import com.cosmotech.organization.api.OrganizationApiService
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["csm.platform.vendor"], havingValue = "azure", matchIfMissing = true)
@Suppress("TooManyFunctions")
internal class DatasetServiceImpl(
    private val organizationService: OrganizationApiService,
    private val connectorService: ConnectorApiService
) : AbstractCosmosBackedService(), DatasetApiService {
  override fun findAllDatasets(organizationId: String) =
      cosmosTemplate.findAll<Dataset>("${organizationId}_datasets")

  override fun findDatasetById(organizationId: String, datasetId: String): Dataset =
      cosmosTemplate.findByIdOrThrow(
          "${organizationId}_datasets",
          datasetId,
          "Dataset $datasetId not found in organization $organizationId")

  override fun removeAllDatasetCompatibilityElements(organizationId: String, datasetId: String) {
    val dataset = findDatasetById(organizationId, datasetId)
    if (!dataset.compatibility.isNullOrEmpty()) {
      dataset.compatibility = listOf()
      cosmosTemplate.upsert("${organizationId}_datasets", dataset)
    }
  }

  override fun createDataset(organizationId: String, dataset: Dataset): Dataset {
    if (dataset.name.isNullOrBlank()) {
      throw IllegalArgumentException("Name cannot be null or blank")
    }
    if (dataset.connector == null || dataset.connector!!.id.isNullOrBlank()) {
      throw IllegalArgumentException("Connector or its ID cannot be null or blank")
    }
    val existingConnector = connectorService.findConnectorById(dataset.connector!!.id!!)
    logger.debug("Found connector: {}", existingConnector)

    val datasetCopy =
        dataset.copy(
            id = idGenerator.generate("dataset"), ownerId = getCurrentAuthenticatedUserName())
    datasetCopy.connector!!.apply {
      name = existingConnector.name
      version = existingConnector.version
    }
    return cosmosTemplate.insert("${organizationId}_datasets", datasetCopy)
        ?: throw IllegalArgumentException("No Dataset returned in response: $dataset")
  }

  override fun deleteDataset(organizationId: String, datasetId: String) {
    val dataset = findDatasetById(organizationId, datasetId)
    if (dataset.ownerId != getCurrentAuthenticatedUserName()) {
      // TODO Only the owner or an admin should be able to perform this operation
      throw CsmAccessForbiddenException("You are not allowed to delete this Resource")
    }
    cosmosTemplate.deleteEntity("${organizationId}_datasets", dataset)
  }

  override fun updateDataset(organizationId: String, datasetId: String, dataset: Dataset): Dataset {
    val existingDataset = findDatasetById(organizationId, datasetId)

    var hasChanged =
        existingDataset
            .compareToAndMutateIfNeeded(dataset, excludedFields = arrayOf("ownerId", "connector"))
            .isNotEmpty()

    if (dataset.ownerId != null && dataset.changed(existingDataset) { ownerId }) {
      // Allow to change the ownerId as well, but only the owner can transfer the ownership
      if (existingDataset.ownerId != getCurrentAuthenticatedUserName()) {
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
      cosmosTemplate.upsertAndReturnEntity("${organizationId}_datasets", existingDataset)
    } else {
      existingDataset
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
        existingDataset
            .compatibility
            ?.associateBy { "${it.solutionKey}-${it.minimumVersion}-${it.maximumVersion}" }
            ?.toMutableMap()
            ?: mutableMapOf()
    datasetCompatibilityMap.putAll(
        datasetCompatibility.filter { it.solutionKey.isNotBlank() }.associateBy {
          "${it.solutionKey}-${it.minimumVersion}-${it.maximumVersion}"
        })
    existingDataset.compatibility = datasetCompatibilityMap.values.toList()
    cosmosTemplate.upsert("${organizationId}_datasets", existingDataset)

    return datasetCompatibility
  }

  override fun copyDataset(
      organizationId: String,
      datasetCopyParameters: DatasetCopyParameters
  ): DatasetCopyParameters {
    TODO("Not yet implemented")
  }

  override fun searchDatasets(organizationId: String, datasetSearch: DatasetSearch): List<Dataset> {
    val arrayContainsList: MutableList<String> = mutableListOf()
    val sqlParameters: MutableList<SqlParameter> = mutableListOf()
    datasetSearch.datasetTags.toSet().forEachIndexed { index, tag ->
      var operator = "WHERE"
      if (index > 0) operator = "AND"
      val tagName = "@TAG${index}"
      arrayContainsList.add("${operator} ARRAY_CONTAINS(c.tags, ${tagName})")
      sqlParameters.add(SqlParameter(tagName, tag))
    }
    val arrayContains = arrayContainsList.joinToString(separator = " ")
    return cosmosCoreDatabase
        .getContainer("${organizationId}_datasets")
        .queryItems(
            SqlQuerySpec("SELECT * FROM c ${arrayContains}", sqlParameters),
            CosmosQueryRequestOptions(),
            JsonNode::class.java)
        .mapNotNull { it.toDomain<Dataset>() }
        .toList()
  }

  @EventListener(OrganizationRegistered::class)
  fun onOrganizationRegistered(organizationRegistered: OrganizationRegistered) {
    cosmosCoreDatabase.createContainerIfNotExists(
        CosmosContainerProperties("${organizationRegistered.organizationId}_datasets", "/id"))
  }

  @EventListener(OrganizationUnregistered::class)
  @Async("csm-in-process-event-executor")
  fun onOrganizationUnregistered(organizationUnregistered: OrganizationUnregistered) {
    cosmosTemplate.deleteContainer("${organizationUnregistered.organizationId}_datasets")
  }

  @EventListener(ConnectorRemoved::class)
  @Async("csm-in-process-event-executor")
  fun onConnectorRemoved(connectorRemoved: ConnectorRemoved) {
    organizationService.findAllOrganizations().forEach {
      this.eventPublisher.publishEvent(
          ConnectorRemovedForOrganization(this, it.id!!, connectorRemoved.connectorId))
    }
  }

  @EventListener(ConnectorRemovedForOrganization::class)
  @Async("csm-in-process-event-executor")
  fun onConnectorRemovedForOrganization(
      connectorRemovedForOrganization: ConnectorRemovedForOrganization
  ) {
    val organizationId = connectorRemovedForOrganization.organizationId
    val connectorId = connectorRemovedForOrganization.connectorId
    cosmosCoreDatabase
        .getContainer("${organizationId}_datasets")
        .queryItems(
            SqlQuerySpec(
                "SELECT * FROM c WHERE c.connector.id = @CONNECTOR_ID",
                listOf(SqlParameter("@CONNECTOR_ID", connectorId))),
            CosmosQueryRequestOptions(),
            // It would be much better to specify the Domain Type right away and
            // avoid the map operation, but we can't due
            // to the lack of customization of the Cosmos Client Object Mapper, as reported here :
            // https://github.com/Azure/azure-sdk-for-java/issues/12269
            JsonNode::class.java)
        .mapNotNull { it.toDomain<Dataset>() }
        .forEach { dataset ->
          dataset.connector = null
          cosmosTemplate.upsert("${organizationId}_datasets", dataset)
        }
  }
}
