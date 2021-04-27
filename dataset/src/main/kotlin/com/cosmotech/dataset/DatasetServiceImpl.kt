// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset

import com.azure.cosmos.models.CosmosContainerProperties
import com.cosmotech.api.AbstractCosmosBackedService
import com.cosmotech.api.events.OrganizationRegistered
import com.cosmotech.api.events.OrganizationUnregistered
import com.cosmotech.api.utils.findAll
import com.cosmotech.api.utils.findByIdOrThrow
import com.cosmotech.dataset.api.DatasetApiService
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.dataset.domain.DatasetCopyParameters
import java.util.*
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["csm.platform.vendor"], havingValue = "azure", matchIfMissing = true)
class DatasetServiceImpl : AbstractCosmosBackedService(), DatasetApiService {
  override fun findAllDatasets(organizationId: String) =
      cosmosTemplate.findAll<Dataset>("${organizationId}_datasets")

  override fun findDatasetById(organizationId: String, datasetId: String): Dataset =
      cosmosTemplate.findByIdOrThrow(
          "${organizationId}_datasets",
          datasetId,
          "Dataset $datasetId not found in organization $organizationId")

  override fun createDataset(organizationId: String, dataset: Dataset) =
      cosmosTemplate.insert(
          "${organizationId}_datasets", dataset.copy(id = UUID.randomUUID().toString()))
          ?: throw IllegalArgumentException("No Dataset returned in response: $dataset")

  override fun deleteDataset(organizationId: String, datasetId: String): Dataset {
    val dataset = findDatasetById(organizationId, datasetId)
    cosmosTemplate.deleteEntity("${organizationId}_datasets", dataset)
    return dataset
  }

  override fun updateDataset(organizationId: String, datasetId: String, dataset: Dataset): Dataset {
    TODO("Not yet implemented")
  }

  override fun copyDataset(
      organizationId: String,
      datasetCopyParameters: DatasetCopyParameters
  ): DatasetCopyParameters {
    TODO("Not yet implemented")
  }

  @EventListener(OrganizationRegistered::class)
  fun onOrganizationRegistered(organizationRegistered: OrganizationRegistered) {
    cosmosClient
        .getDatabase(databaseName)
        .createContainerIfNotExists(
            CosmosContainerProperties("${organizationRegistered.organizationId}_datasets", "/id"))
  }

  @EventListener(OrganizationUnregistered::class)
  @Async("csm-in-process-event-executor")
  fun onOrganizationUnregistered(organizationUnregistered: OrganizationUnregistered) {
    cosmosTemplate.deleteContainer("${organizationUnregistered.organizationId}_datasets")
  }
}
