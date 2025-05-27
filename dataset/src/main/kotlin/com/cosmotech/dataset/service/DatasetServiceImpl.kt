// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.service

import com.cosmotech.api.CsmPhoenixService
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.rbac.CsmRbac
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
import com.cosmotech.dataset.repository.DatasetRepository
import com.cosmotech.workspace.WorkspaceApiServiceInterface
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service

@Service
@Suppress("EmptyDefaultConstructor", "TooManyFunctions")
class DatasetServiceImpl(
    private val workspaceService: WorkspaceApiServiceInterface,
    private val datasetRepository: DatasetRepository,
    private val csmRbac: CsmRbac,
) : CsmPhoenixService(), DatasetApiServiceInterface {

  override fun getVerifiedDataset(
      organizationId: String,
      workspaceId: String,
      datasetId: String,
      requiredPermission: String
  ): Dataset {
    workspaceService.getVerifiedWorkspace(organizationId, workspaceId)
    val dataset =
        datasetRepository.findBy(organizationId, workspaceId, datasetId).orElseThrow {
          CsmResourceNotFoundException(
              "Dataset $datasetId not found in organization $organizationId and workspace $workspaceId")
        }
    csmRbac.verify(dataset.security.toGenericSecurity(datasetId), requiredPermission)
    return dataset
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

fun DatasetSecurity?.toGenericSecurity(datasetId: String) =
    RbacSecurity(
        datasetId,
        this?.default ?: ROLE_NONE,
        this?.accessControlList?.map { RbacAccessControl(it.id, it.role) }?.toMutableList()
            ?: mutableListOf())

fun RbacSecurity.toResourceSecurity() =
    DatasetSecurity(
        this.default,
        this.accessControlList.map { DatasetAccessControl(it.id, it.role) }.toMutableList())
