// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.service

import com.cosmotech.api.CsmPhoenixService
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.rbac.model.RbacAccessControl
import com.cosmotech.api.rbac.model.RbacSecurity
import com.cosmotech.dataset.DatasetApiServiceInterface
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.dataset.domain.DatasetAccessControl
import com.cosmotech.dataset.domain.DatasetCreateRequest
import com.cosmotech.dataset.domain.DatasetRole
import com.cosmotech.dataset.domain.DatasetSecurity
import com.cosmotech.dataset.domain.DatasetUpdateRequest
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service

@Service
@Suppress("EmptyDefaultConstructor", "TooManyFunctions")
class DatasetServiceImpl() : CsmPhoenixService(), DatasetApiServiceInterface {

  override fun getVerifiedDataset(
      organizationId: String,
      workspaceId: String,
      datasetId: String,
      requiredPermission: String
  ): Dataset {
    TODO("Not yet implemented")
  }

  override fun findByOrganizationIdWorkspaceIdAndDatasetId(
      organizationId: String,
      workspaceId: String,
      datasetId: String
  ): Dataset {
    TODO("Not yet implemented")
  }

  override fun addDatasetAccessControl(
      organizationId: String,
      workspaceId: String,
      datasetId: String,
      datasetAccessControl: DatasetAccessControl
  ): DatasetAccessControl {
    TODO("Not yet implemented")
  }

  override fun createDataset(
      organizationId: String,
      workspaceId: String,
      files: List<Resource>?,
      dataset: DatasetCreateRequest?
  ): Dataset {
    TODO("Not yet implemented")
  }

  override fun deleteDataset(organizationId: String, workspaceId: String, datasetId: String) {
    TODO("Not yet implemented")
  }

  override fun getDataset(organizationId: String, workspaceId: String, datasetId: String): Dataset {
    TODO("Not yet implemented")
  }

  override fun getDatasetAccessControl(
      organizationId: String,
      workspaceId: String,
      datasetId: String,
      identityId: String
  ): DatasetAccessControl {
    TODO("Not yet implemented")
  }

  override fun getDatasetSecurityUsers(
      organizationId: String,
      workspaceId: String,
      datasetId: String
  ): List<String> {
    TODO("Not yet implemented")
  }

  override fun listDatasets(organizationId: String, workspaceId: String): List<Dataset> {
    TODO("Not yet implemented")
  }

  override fun removeDatasetAccessControl(
      organizationId: String,
      workspaceId: String,
      datasetId: String,
      identityId: String
  ) {
    TODO("Not yet implemented")
  }

  override fun setDatasetDefaultSecurity(
      organizationId: String,
      workspaceId: String,
      datasetId: String,
      datasetRole: DatasetRole
  ): DatasetSecurity {
    TODO("Not yet implemented")
  }

  override fun updateDataset(
      organizationId: String,
      workspaceId: String,
      datasetId: String,
      files: List<Resource>?,
      dataset: DatasetUpdateRequest?
  ): List<Dataset> {
    TODO("Not yet implemented")
  }

  override fun updateDatasetAccessControl(
      organizationId: String,
      workspaceId: String,
      datasetId: String,
      identityId: String,
      datasetRole: DatasetRole
  ): DatasetAccessControl {
    TODO("Not yet implemented")
  }
}

fun DatasetSecurity?.toGenericSecurity(organizationId: String) =
    RbacSecurity(
        organizationId,
        this?.default ?: ROLE_NONE,
        this?.accessControlList?.map { RbacAccessControl(it.id, it.role) }?.toMutableList()
            ?: mutableListOf())

fun RbacSecurity.toResourceSecurity() =
    DatasetSecurity(
        this.default,
        this.accessControlList.map { DatasetAccessControl(it.id, it.role) }.toMutableList())
