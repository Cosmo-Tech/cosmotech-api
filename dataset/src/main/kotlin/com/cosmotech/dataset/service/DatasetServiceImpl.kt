// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.service

import com.cosmotech.api.CsmPhoenixService
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.rbac.CsmRbac
import com.cosmotech.api.rbac.PERMISSION_DELETE
import com.cosmotech.api.rbac.PERMISSION_READ
import com.cosmotech.api.rbac.PERMISSION_READ_SECURITY
import com.cosmotech.api.rbac.PERMISSION_WRITE
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
import com.cosmotech.dataset.domain.DatasetPartCreateRequest
import com.cosmotech.dataset.domain.DatasetPartTypeEnum
import com.cosmotech.dataset.domain.DatasetPartUpdateRequest
import com.cosmotech.dataset.domain.DatasetRole
import com.cosmotech.dataset.domain.DatasetSecurity
import com.cosmotech.dataset.domain.DatasetUpdateRequest
import com.cosmotech.dataset.domain.EditInfo
import com.cosmotech.dataset.repositories.DatasetPartRepository
import com.cosmotech.dataset.repositories.DatasetRepository
import com.cosmotech.workspace.WorkspaceApiServiceInterface
import com.cosmotech.workspace.service.toGenericSecurity
import java.time.Instant
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

@Service
@Suppress("TooManyFunctions")
class DatasetServiceImpl(
    private val workspaceService: WorkspaceApiServiceInterface,
    private val datasetRepository: DatasetRepository,
    private val datasetPartRepository: DatasetPartRepository,
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

  override fun removeDatasetPartFromDataset(dataset: Dataset, datasetPartId: String) {
    dataset.parts.removeIf { it.id == datasetPartId }
    // Handle DB/Files deletion here
    datasetRepository.update(dataset)
  }

  override fun replaceDatasetPartFromDataset(
      dataset: Dataset,
      datasetPartIdToReplace: String,
      datasetPart: DatasetPart
  ) {
    val now = Instant.now().toEpochMilli()
    val userId = getCurrentAccountIdentifier(csmPlatformProperties)
    val editInfo = EditInfo(timestamp = now, userId = userId)

    dataset.parts
        .find { it.id == datasetPartIdToReplace }
        ?.let {
          it.description = datasetPart.description
          it.tags = datasetPart.tags
          it.updateInfo = editInfo
        }
    // Handle DB/Files replacement here
    datasetRepository.update(dataset)
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
      files: Array<MultipartFile>
  ): Dataset {
    logger.debug("Registering Dataset: {}", datasetCreateRequest)
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
            ?.map { part -> constructDatasetPart(organizationId, workspaceId, datasetId, part) }
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
    logger.debug("Registering Dataset: {}", createdDataset)

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
      files: Array<MultipartFile>,
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

  override fun createDatasetPart(
      organizationId: String,
      workspaceId: String,
      datasetId: String,
      file: MultipartFile,
      datasetPartCreateRequest: DatasetPartCreateRequest
  ): DatasetPart {
    getVerifiedDataset(organizationId, workspaceId, datasetId, PERMISSION_READ)
    require(datasetPartCreateRequest.name.isNotBlank()) {
      "Dataset Part name must not be null or blank"
    }

    val createdDatasetPart =
        constructDatasetPart(organizationId, workspaceId, datasetId, datasetPartCreateRequest)

    return datasetPartRepository.save(createdDatasetPart)
  }

  override fun constructDatasetPart(
      organizationId: String,
      workspaceId: String,
      datasetId: String,
      datasetPartCreateRequest: DatasetPartCreateRequest
  ): DatasetPart {
    logger.debug("Registering DatasetPart: {}", datasetPartCreateRequest)
    val now = Instant.now().toEpochMilli()
    val userId = getCurrentAccountIdentifier(csmPlatformProperties)
    val editInfo = EditInfo(timestamp = now, userId = userId)

    val createdDatasetPart =
        DatasetPart(
            id = idGenerator.generate("datasetpart", prependPrefix = "dp-"),
            datasetId = datasetId,
            name = datasetPartCreateRequest.name,
            description = datasetPartCreateRequest.description,
            tags = datasetPartCreateRequest.tags ?: mutableListOf(),
            type = datasetPartCreateRequest.type ?: DatasetPartTypeEnum.Relational,
            organizationId = organizationId,
            workspaceId = workspaceId,
            createInfo = editInfo,
            updateInfo = editInfo)
    logger.debug("Registering DatasetPart: {}", createdDatasetPart)
    return createdDatasetPart
  }

  override fun deleteDatasetPart(
      organizationId: String,
      workspaceId: String,
      datasetId: String,
      datasetPartId: String
  ) {
    val dataset = getVerifiedDataset(organizationId, workspaceId, datasetId, PERMISSION_WRITE)

    val datasetPart =
        datasetPartRepository
            .findBy(organizationId, workspaceId, datasetId, datasetPartId)
            .orElseThrow {
              CsmResourceNotFoundException(
                  "Dataset Part $datasetPartId not found in organization $organizationId, " +
                      "workspace $workspaceId and dataset $datasetId")
            }

    removeDatasetPartFromDataset(dataset, datasetPartId)

    datasetPartRepository.delete(datasetPart)
  }

  override fun downloadDatasetPart(
      organizationId: String,
      workspaceId: String,
      datasetId: String,
      datasetPartId: String
  ): Resource {
    TODO("Not yet implemented")
  }

  override fun getDatasetPart(
      organizationId: String,
      workspaceId: String,
      datasetId: String,
      datasetPartId: String
  ): DatasetPart {
    getVerifiedDataset(organizationId, workspaceId, datasetId, PERMISSION_READ)

    return datasetPartRepository
        .findBy(organizationId, workspaceId, datasetId, datasetPartId)
        .orElseThrow {
          CsmResourceNotFoundException(
              "Dataset Part $datasetPartId not found in organization $organizationId, " +
                  "workspace $workspaceId and dataset $datasetId")
        }
  }

  override fun listDatasetParts(
      organizationId: String,
      workspaceId: String,
      datasetId: String,
      page: Int?,
      size: Int?
  ): List<DatasetPart> {
    val dataset = getVerifiedDataset(organizationId, workspaceId, datasetId, PERMISSION_READ)

    val defaultPageSize = csmPlatformProperties.twincache.dataset.defaultPageSize
    val pageable = constructPageRequest(page, size, defaultPageSize)
    val isAdmin =
        csmRbac.isAdmin(dataset.security.toGenericSecurity(datasetId), getCommonRolesDefinition())

    val result: MutableList<DatasetPart>
    val rbacEnabled = !isAdmin && this.csmPlatformProperties.rbac.enabled
    if (pageable == null) {
      result =
          findAllPaginated(defaultPageSize) {
            if (rbacEnabled) {
              val currentUser = getCurrentAccountIdentifier(this.csmPlatformProperties)
              datasetPartRepository
                  .findByOrganizationIdAndWorkspaceIdAndDatasetId(
                      organizationId, workspaceId, datasetId, currentUser, it)
                  .toList()
            } else {
              datasetPartRepository.findAll(it).toList()
            }
          }
    } else {
      result =
          if (rbacEnabled) {
            val currentUser = getCurrentAccountIdentifier(this.csmPlatformProperties)
            datasetPartRepository
                .findByOrganizationIdAndWorkspaceIdAndDatasetId(
                    organizationId, workspaceId, datasetId, currentUser, pageable)
                .toList()
          } else {
            datasetPartRepository.findAll(pageable).toList()
          }
    }

    return result
  }

  override fun queryData(
      organizationId: String,
      workspaceId: String,
      datasetId: String,
      datasetPartId: String,
      filters: List<String>?,
      sums: List<String>?,
      counts: List<String>?,
      offset: Int?,
      limit: Int?
  ): List<Any> {
    TODO("Not yet implemented")
  }

  override fun replaceDatasetPart(
      organizationId: String,
      workspaceId: String,
      datasetId: String,
      datasetPartId: String,
      file: MultipartFile,
      datasetPartUpdateRequest: DatasetPartUpdateRequest?
  ): DatasetPart {
    val dataset = getVerifiedDataset(organizationId, workspaceId, datasetId, PERMISSION_WRITE)

    val datasetPart =
        datasetPartRepository
            .findBy(organizationId, workspaceId, datasetId, datasetPartId)
            .orElseThrow {
              CsmResourceNotFoundException(
                  "Dataset Part $datasetPartId not found in organization $organizationId, " +
                      "workspace $workspaceId and dataset $datasetId")
            }
    datasetPart.description = datasetPartUpdateRequest?.description ?: datasetPart.description
    datasetPart.tags = datasetPartUpdateRequest?.tags ?: datasetPart.tags

    replaceDatasetPartFromDataset(dataset, datasetPartId, datasetPart)

    return datasetPartRepository.update(datasetPart)
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
