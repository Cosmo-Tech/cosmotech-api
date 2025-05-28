// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.service

import com.cosmotech.api.CsmPhoenixService
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.rbac.CsmRbac
import com.cosmotech.api.rbac.PERMISSION_DELETE
import com.cosmotech.api.rbac.PERMISSION_READ
import com.cosmotech.api.rbac.PERMISSION_READ_SECURITY
import com.cosmotech.api.rbac.PERMISSION_WRITE_SECURITY
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.rbac.getCommonRolesDefinition
import com.cosmotech.api.rbac.model.RbacAccessControl
import com.cosmotech.api.rbac.model.RbacSecurity
import com.cosmotech.api.utils.constructPageRequest
import com.cosmotech.api.utils.findAllPaginated
import com.cosmotech.api.utils.getCurrentAccountIdentifier
import com.cosmotech.dataset.DatasetApiServiceInterface
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.dataset.domain.DatasetAccessControl
import com.cosmotech.dataset.domain.DatasetCreateRequest
import com.cosmotech.dataset.domain.DatasetPart
import com.cosmotech.dataset.domain.DatasetPartTypeEnum
import com.cosmotech.dataset.domain.DatasetRole
import com.cosmotech.dataset.domain.DatasetSecurity
import com.cosmotech.dataset.domain.DatasetUpdateRequest
import com.cosmotech.dataset.domain.EditInfo
import com.cosmotech.dataset.repository.DatasetRepository
import com.cosmotech.workspace.WorkspaceApiServiceInterface
import com.cosmotech.workspace.service.toGenericSecurity
import java.time.Instant
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service

@Service
@Suppress("TooManyFunctions")
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
    workspaceService.getVerifiedWorkspace(organizationId, workspaceId)
    return datasetRepository.findBy(organizationId, workspaceId, datasetId).orElseThrow {
      CsmResourceNotFoundException(
          "Dataset $datasetId not found in organization $organizationId and workspace $workspaceId")
    }
  }

  override fun createDatasetAccessControl(
      organizationId: String,
      workspaceId: String,
      datasetId: String,
      datasetAccessControl: DatasetAccessControl
  ): DatasetAccessControl {
    val dataset =
        getVerifiedDataset(organizationId, workspaceId, datasetId, PERMISSION_WRITE_SECURITY)
    val users = listDatasetSecurityUsers(organizationId, workspaceId, datasetId)
    require(!users.contains(datasetAccessControl.id)) { "User is already in this Dataset security" }

    val rbacSecurity =
        csmRbac.setUserRole(
            dataset.security.toGenericSecurity(datasetId),
            datasetAccessControl.id,
            datasetAccessControl.role)
    dataset.security = rbacSecurity.toResourceSecurity()
    save(dataset)

    val rbacAccessControl =
        csmRbac.getAccessControl(
            dataset.security.toGenericSecurity(datasetId), datasetAccessControl.id)
    return DatasetAccessControl(id = rbacAccessControl.id, role = rbacAccessControl.role)
  }

  override fun createDataset(
      organizationId: String,
      workspaceId: String,
      datasetCreateRequest: DatasetCreateRequest,
      files: List<Resource>?
  ): Dataset {
    logger.trace("Registering Dataset: {}", datasetCreateRequest)

    require(datasetCreateRequest.name.isNotBlank()) { "Dataset name must not be null or blank" }
    val datasetId = idGenerator.generate("dataset")
    val now = Instant.now().toEpochMilli()
    val userId = getCurrentAccountIdentifier(csmPlatformProperties)
    val editInfo = EditInfo(timestamp = now, userId = userId)
    val security =
        csmRbac
            .initSecurity(datasetCreateRequest.security.toGenericSecurity(datasetId))
            .toResourceSecurity()

    val datasetParts =
        datasetCreateRequest.parts
            ?.map { part ->
              DatasetPart(
                  id = idGenerator.generate("datasetpart", prependPrefix = "dp-"),
                  datasetId = datasetId,
                  name = part.name,
                  description = part.description,
                  tags = part.tags ?: mutableListOf(),
                  type = part.type ?: DatasetPartTypeEnum.Relational,
                  organizationId = organizationId,
                  workspaceId = workspaceId,
                  createInfo = editInfo,
                  updateInfo = editInfo)
            }
            ?.toMutableList()

    val createdDataset =
        Dataset(
            id = datasetId,
            name = datasetCreateRequest.name,
            description = datasetCreateRequest.description,
            organizationId = organizationId,
            workspaceId = workspaceId,
            tags = datasetCreateRequest.tags ?: mutableListOf(),
            parts = datasetParts ?: mutableListOf(),
            createInfo = editInfo,
            updateInfo = editInfo,
            security = security)

    return datasetRepository.save(createdDataset)
  }

  override fun deleteDataset(organizationId: String, workspaceId: String, datasetId: String) {
    val dataset = getVerifiedDataset(organizationId, workspaceId, datasetId, PERMISSION_DELETE)
    datasetRepository.delete(dataset)
  }

  override fun getDataset(organizationId: String, workspaceId: String, datasetId: String): Dataset {
    return updateSecurityVisibility(
        getVerifiedDataset(organizationId, workspaceId, datasetId, PERMISSION_READ))
  }

  override fun getDatasetAccessControl(
      organizationId: String,
      workspaceId: String,
      datasetId: String,
      identityId: String
  ): DatasetAccessControl {
    val dataset =
        getVerifiedDataset(organizationId, workspaceId, datasetId, PERMISSION_READ_SECURITY)
    val rbacAccessControl =
        csmRbac.getAccessControl(dataset.security.toGenericSecurity(datasetId), identityId)
    return DatasetAccessControl(id = rbacAccessControl.id, role = rbacAccessControl.role)
  }

  override fun listDatasetSecurityUsers(
      organizationId: String,
      workspaceId: String,
      datasetId: String
  ): List<String> {

    val dataset =
        getVerifiedDataset(organizationId, workspaceId, datasetId, PERMISSION_READ_SECURITY)

    return csmRbac.getUsers(dataset.security.toGenericSecurity(datasetId))
  }

  override fun listDatasets(
      organizationId: String,
      workspaceId: String,
      page: Int?,
      size: Int?
  ): List<Dataset> {
    val workspace = workspaceService.getVerifiedWorkspace(organizationId, workspaceId)
    val defaultPageSize = csmPlatformProperties.twincache.dataset.defaultPageSize
    val pageable = constructPageRequest(page, size, defaultPageSize)
    val isAdmin =
        csmRbac.isAdmin(
            workspace.security.toGenericSecurity(workspaceId), getCommonRolesDefinition())
    val result: MutableList<Dataset>
    val rbacEnabled = !isAdmin && this.csmPlatformProperties.rbac.enabled
    if (pageable == null) {
      result =
          findAllPaginated(defaultPageSize) {
            if (rbacEnabled) {
              val currentUser = getCurrentAccountIdentifier(this.csmPlatformProperties)
              datasetRepository
                  .findByOrganizationIdAndWorkspaceId(organizationId, workspaceId, currentUser, it)
                  .toList()
            } else {
              datasetRepository.findAll(it).toList()
            }
          }
    } else {
      result =
          if (rbacEnabled) {
            val currentUser = getCurrentAccountIdentifier(this.csmPlatformProperties)
            datasetRepository
                .findByOrganizationIdAndWorkspaceId(
                    organizationId, workspaceId, currentUser, pageable)
                .toList()
          } else {
            datasetRepository.findAll(pageable).toList()
          }
    }
    result.forEach { it.security = updateSecurityVisibility(it).security }
    return result
  }

  override fun deleteDatasetAccessControl(
      organizationId: String,
      workspaceId: String,
      datasetId: String,
      identityId: String
  ) {

    val dataset =
        getVerifiedDataset(organizationId, workspaceId, datasetId, PERMISSION_WRITE_SECURITY)
    val rbacSecurity = csmRbac.removeUser(dataset.security.toGenericSecurity(datasetId), identityId)
    dataset.security = rbacSecurity.toResourceSecurity()
    save(dataset)
  }

  override fun updateDatasetDefaultSecurity(
      organizationId: String,
      workspaceId: String,
      datasetId: String,
      datasetRole: DatasetRole
  ): DatasetSecurity {

    val dataset =
        getVerifiedDataset(organizationId, workspaceId, datasetId, PERMISSION_WRITE_SECURITY)
    val rbacSecurity =
        csmRbac.setDefault(dataset.security.toGenericSecurity(datasetId), datasetRole.role)
    dataset.security = rbacSecurity.toResourceSecurity()
    save(dataset)
    return dataset.security
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

    val dataset =
        getVerifiedDataset(organizationId, workspaceId, datasetId, PERMISSION_WRITE_SECURITY)
    csmRbac.checkUserExists(
        dataset.security.toGenericSecurity(datasetId),
        identityId,
        "User '$identityId' not found in dataset $datasetId")
    val rbacSecurity =
        csmRbac.setUserRole(
            dataset.security.toGenericSecurity(datasetId), identityId, datasetRole.role)
    dataset.security = rbacSecurity.toResourceSecurity()
    save(dataset)
    val rbacAccessControl =
        csmRbac.getAccessControl(dataset.security.toGenericSecurity(datasetId), identityId)
    return DatasetAccessControl(id = rbacAccessControl.id, role = rbacAccessControl.role)
  }

  fun updateSecurityVisibility(dataset: Dataset): Dataset {
    if (csmRbac
        .check(dataset.security.toGenericSecurity(dataset.id), PERMISSION_READ_SECURITY)
        .not()) {
      val username = getCurrentAccountIdentifier(csmPlatformProperties)
      val retrievedAC = dataset.security.accessControlList.firstOrNull { it.id == username }

      val accessControlList =
          if (retrievedAC != null) {
            mutableListOf(retrievedAC)
          } else {
            mutableListOf()
          }
      return dataset.copy(
          security =
              DatasetSecurity(
                  default = dataset.security.default, accessControlList = accessControlList))
    }
    return dataset
  }

  fun save(dataset: Dataset): Dataset {
    dataset.updateInfo =
        EditInfo(
            timestamp = Instant.now().toEpochMilli(),
            userId = getCurrentAccountIdentifier(csmPlatformProperties))
    return datasetRepository.save(dataset)
  }
}

fun DatasetSecurity?.toGenericSecurity(id: String) =
    RbacSecurity(
        id,
        this?.default ?: ROLE_NONE,
        this?.accessControlList?.map { RbacAccessControl(it.id, it.role) }?.toMutableList()
            ?: mutableListOf())

fun RbacSecurity.toResourceSecurity() =
    DatasetSecurity(
        this.default,
        this.accessControlList.map { DatasetAccessControl(it.id, it.role) }.toMutableList())
