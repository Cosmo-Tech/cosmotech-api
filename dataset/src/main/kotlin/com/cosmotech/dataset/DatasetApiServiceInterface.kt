// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
@file:Suppress("DEPRECATION")

package com.cosmotech.dataset

import com.cosmotech.api.rbac.PERMISSION_READ
import com.cosmotech.dataset.api.DatasetApiService
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.dataset.domain.DatasetAccessControl

interface DatasetApiServiceInterface : DatasetApiService {

  fun getVerifiedDataset(
      organizationId: String,
      datasetId: String,
      requiredPermission: String = PERMISSION_READ
  ): Dataset

  /**
   * Find Dataset by Organization Id and Dataset Id (checking ONLY READ_PERMISSION on targeted
   * organization)
   * @param organizationId an organization Id
   * @param datasetId a dataset Id
   * @return a Dataset or null
   */
  fun findByOrganizationIdAndDatasetId(organizationId: String, datasetId: String): Dataset?

  /**
   * Add a new entry (ou update existing one) on dataset passed in parameter
   * @param organizationId an organization id
   * @param dataset a dataset to update
   * @param identity a user/application identity
   * @param role new dataset role
   * @return new DatasetAccessControl with update
   */
  fun addOrUpdateAccessControl(
      organizationId: String,
      dataset: Dataset,
      identity: String,
      role: String
  ): DatasetAccessControl
}
