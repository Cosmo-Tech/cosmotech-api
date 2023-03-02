// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.service

import com.cosmotech.api.CsmPhoenixService
import com.cosmotech.api.events.ConnectorRemoved
import com.cosmotech.api.events.OrganizationUnregistered
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.utils.changed
import com.cosmotech.api.utils.compareToAndMutateIfNeeded
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.connector.api.ConnectorApiService
import com.cosmotech.dataset.api.DatasetApiService
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.dataset.domain.DatasetCompatibility
import com.cosmotech.dataset.domain.DatasetCopyParameters
import com.cosmotech.dataset.domain.DatasetSearch
import com.cosmotech.dataset.repository.DatasetRepository
import org.springframework.context.event.EventListener
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
@Suppress("TooManyFunctions")
internal class DatasetServiceImpl(
    private val connectorService: ConnectorApiService,
    private val datasetRepository: DatasetRepository
) : CsmPhoenixService(), DatasetApiService {

  override fun findAllDatasets(organizationId: String, page: Int?, size: Int?): List<Dataset> {
    var pageRequest = constructPageRequest(page, size)
    if (pageRequest != null) {
      return datasetRepository.findByOrganizationId(organizationId, pageRequest).toList()
    }

    var allDatasetByOrganizationId = mutableListOf<Dataset>()
    pageRequest = PageRequest.ofSize(csmPlatformProperties.twincache.dataset.defaultPageSize)
    do {
      val paginatedSolutions =
          datasetRepository.findByOrganizationId(organizationId, pageRequest!!).toList()
      allDatasetByOrganizationId.addAll(paginatedSolutions)
      pageRequest = pageRequest!!.next()
    } while (paginatedSolutions.isNotEmpty())

    return allDatasetByOrganizationId
  }

  internal fun constructPageRequest(page: Int?, size: Int?): PageRequest? {
    var result: PageRequest? = null
    if (page != null && size != null) {
      result = PageRequest.of(page, size)
    }
    if (page != null && size == null) {
      result = PageRequest.of(page, csmPlatformProperties.twincache.dataset.defaultPageSize)
    }
    if (page == null && size != null) {
      result = PageRequest.of(0, size)
    }
    return result
  }

  override fun findDatasetById(organizationId: String, datasetId: String): Dataset =
      datasetRepository.findById(datasetId).orElseThrow {
        CsmAccessForbiddenException("Dataset $datasetId not found in organization $organizationId")
      }

  override fun removeAllDatasetCompatibilityElements(organizationId: String, datasetId: String) {
    val dataset = findDatasetById(organizationId, datasetId)
    if (!dataset.compatibility.isNullOrEmpty()) {
      dataset.compatibility = mutableListOf()
      datasetRepository.save(dataset)
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
            id = idGenerator.generate("dataset"),
            ownerId = getCurrentAuthenticatedUserName(),
            organizationId = organizationId)
    datasetCopy.connector!!.apply {
      name = existingConnector.name
      version = existingConnector.version
    }
    return datasetRepository.save(datasetCopy)
  }

  override fun deleteDataset(organizationId: String, datasetId: String) {
    val dataset = findDatasetById(organizationId, datasetId)
    if (dataset.ownerId != getCurrentAuthenticatedUserName()) {
      // TODO Only the owner or an admin should be able to perform this operation
      throw CsmAccessForbiddenException("You are not allowed to delete this Resource")
    }
    datasetRepository.delete(dataset)
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
      datasetRepository.save(existingDataset)
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
    var pageRequest = constructPageRequest(page, size)
    if (pageRequest != null) {
      return datasetRepository
          .findDatasetByTags(datasetSearch.datasetTags.toSet(), pageRequest)
          .toList()
    }
    pageRequest = PageRequest.ofSize(csmPlatformProperties.twincache.dataset.defaultPageSize)
    var result = mutableListOf<Dataset>()
    do {
      var datasets =
          datasetRepository
              .findDatasetByTags(datasetSearch.datasetTags.toSet(), pageRequest!!)
              .toList()
      result.addAll(datasets)
      pageRequest = pageRequest.next()
    } while (datasets.isNotEmpty())
    return result
  }

  @EventListener(OrganizationUnregistered::class)
  @Async("csm-in-process-event-executor")
  fun onOrganizationUnregistered(organizationUnregistered: OrganizationUnregistered) {
    var pageable = PageRequest.ofSize(csmPlatformProperties.twincache.organization.defaultPageSize)
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

  override fun importDataset(organizationId: String, dataset: Dataset): Dataset {
    if (dataset.id == null || organizationId == null) {
      throw CsmResourceNotFoundException("Dataset or Organization id is null")
    }
    return datasetRepository.save(dataset)
  }
}
