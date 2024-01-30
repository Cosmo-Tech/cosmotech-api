// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset

import com.cosmotech.api.rbac.PERMISSION_READ
import com.cosmotech.dataset.api.DatasetApiService
import com.cosmotech.dataset.domain.Dataset
import redis.clients.jedis.graph.ResultSet

interface DatasetApiServiceInterface : DatasetApiService {

  fun getVerifiedDataset(
      organizationId: String,
      datasetId: String,
      requiredPermission: String = PERMISSION_READ
  ): Dataset

  fun query(dataset: Dataset, query: String, isReadOnly: Boolean = false): ResultSet
}
