// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset

import com.cosmotech.api.rbac.PERMISSION_READ
import com.cosmotech.dataset.api.DatasetApiService
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.dataset.domain.DatasetPart
import com.cosmotech.dataset.domain.DatasetPartCreateRequest

interface DatasetApiServiceInterface : DatasetApiService {

  /**
   * Find Dataset by Organization Id, Workspace Id and Dataset Id, and check if the current user has
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

  /**
   * Removes a specific part of a dataset within the given dataset.
   *
   * @param dataset the dataset
   * @param datasetPartId the unique identifier of the dataset part to be removed
   */
  fun removeDatasetPartFromDataset(dataset: Dataset, datasetPartId: String)

  /**
   * Adds a specified `DatasetPart` to the given `Dataset`.
   *
   * @param dataset The dataset to which the dataset part will be added.
   * @param datasetPart The dataset part to be added to the dataset.
   * @return The added dataset part.
   */
  fun addDatasetPartToDataset(dataset: Dataset, datasetPart: DatasetPart): DatasetPart

  /**
   * Constructs a dataset part within a specified organization, workspace, and dataset.
   *
   * @param organizationId The ID of the organization where the dataset part is created.
   * @param workspaceId The ID of the workspace where the dataset part is created.
   * @param datasetId The ID of the dataset where the dataset part is created.
   * @param datasetPartCreateRequest The details of the dataset part to be created.
   * @return The created dataset part containing its metadata and associated details.
   */
  fun constructDatasetPart(
      organizationId: String,
      workspaceId: String,
      datasetId: String,
      datasetPartCreateRequest: DatasetPartCreateRequest
  ): DatasetPart
}
