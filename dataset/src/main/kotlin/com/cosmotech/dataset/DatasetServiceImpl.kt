// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset

import com.cosmotech.api.AbstractPhoenixService
import com.cosmotech.dataset.api.DatasetApiService
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.dataset.domain.DatasetCopyParameters
import org.springframework.stereotype.Service

@Service
class DatasetServiceImpl : AbstractPhoenixService(), DatasetApiService {
  override fun findAllDatasets(organizationId: String): List<Dataset> {
    TODO("Not yet implemented")
  }

  override fun findDatasetById(organizationId: String, datasetId: String): Dataset {
    TODO("Not yet implemented")
  }

  override fun createDataset(organizationId: String, dataset: Dataset): Dataset {
    TODO("Not yet implemented")
  }

  override fun deleteDataset(organizationId: String, datasetId: String): Dataset {
    TODO("Not yet implemented")
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
}
