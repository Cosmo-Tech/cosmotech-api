// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.

package com.cosmotech.dataset

import com.cosmotech.api.AbstractPhoenixService
import com.cosmotech.dataset.api.DatasetsApiService
import com.cosmotech.dataset.domain.Dataset
import org.springframework.stereotype.Service

@Service
class DatasetServiceImpl : AbstractPhoenixService(), DatasetsApiService {
  override fun findAllDatasets(): List<Dataset> {
    TODO("Not yet implemented")
  }

  override fun findDatasetById(datasetId: String): Dataset {
    TODO("Not yet implemented")
  }

  override fun createDataset(dataset: Dataset): Dataset {
    TODO("Not yet implemented")
  }

  override fun deleteDataset(datasetId: String): Dataset {
    TODO("Not yet implemented")
  }

  override fun updateDataset(datasetId: String, dataset: Dataset): Dataset {
    TODO("Not yet implemented")
  }
}
