// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset

import com.cosmotech.api.rbac.PERMISSION_READ
import com.cosmotech.dataset.api.DatasetApiService
import com.cosmotech.dataset.domain.Dataset

interface DatasetApiServiceInterface : DatasetApiService {

  /**
   * Find Dataset by Organization Id, Workspace Id and Dataset Id and check if the current user has
   * the minimal permission to access to it
   *
   * @param organizationId an organization Id
   * @param workspaceId a workspace Id
   * @param datasetId a dataset Id
   * @param requiredPermission a minimal permission
   * @return a Dataset or null
   */
  fun getVerifiedDataset(
      organizationId: String,
      workspaceId: String,
      datasetId: String,
      requiredPermission: String = PERMISSION_READ
  ): Dataset

  /**
   * Find Dataset by Organization Id, Workspace Id and Dataset Id (checking ONLY READ_PERMISSION on
   * targeted organization and workspace)
   *
   * @param organizationId an organization Id
   * @param workspaceId a workspace Id
   * @param datasetId a dataset Id
   * @return a Dataset or null
   */
  fun findByOrganizationIdWorkspaceIdAndDatasetId(
      organizationId: String,
      workspaceId: String,
      datasetId: String
  ): Dataset?
}
