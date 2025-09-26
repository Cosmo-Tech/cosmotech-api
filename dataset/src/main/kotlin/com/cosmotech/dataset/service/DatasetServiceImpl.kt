// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.service

import com.cosmotech.common.CsmPhoenixService
import com.cosmotech.common.exceptions.CsmResourceNotFoundException
import com.cosmotech.common.rbac.CsmRbac
import com.cosmotech.common.rbac.PERMISSION_CREATE_CHILDREN
import com.cosmotech.common.rbac.PERMISSION_DELETE
import com.cosmotech.common.rbac.PERMISSION_READ
import com.cosmotech.common.rbac.PERMISSION_READ_SECURITY
import com.cosmotech.common.rbac.PERMISSION_WRITE
import com.cosmotech.common.rbac.PERMISSION_WRITE_SECURITY
import com.cosmotech.common.rbac.ROLE_NONE
import com.cosmotech.common.rbac.getCommonRolesDefinition
import com.cosmotech.common.rbac.model.RbacAccessControl
import com.cosmotech.common.rbac.model.RbacSecurity
import com.cosmotech.common.utils.ResourceScanner
import com.cosmotech.common.utils.constructPageRequest
import com.cosmotech.common.utils.findAllPaginated
import com.cosmotech.common.utils.getCurrentAccountIdentifier
import com.cosmotech.dataset.DatasetApiServiceInterface
import com.cosmotech.dataset.domain.CreateInfo
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
import com.cosmotech.dataset.part.factories.DatasetPartManagementFactory
import com.cosmotech.dataset.repositories.DatasetPartRepository
import com.cosmotech.dataset.repositories.DatasetRepository
import com.cosmotech.workspace.WorkspaceApiServiceInterface
import com.cosmotech.workspace.service.toGenericSecurity
import java.time.Instant
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

@Service
@Suppress("TooManyFunctions", "LargeClass")
class DatasetServiceImpl(
    private val workspaceService: WorkspaceApiServiceInterface,
    private val datasetRepository: DatasetRepository,
    private val datasetPartRepository: DatasetPartRepository,
    private val csmRbac: CsmRbac,
    private val datasetPartManagementFactory: DatasetPartManagementFactory,
    private val resourceScanner: ResourceScanner
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
    datasetRepository.update(dataset)
  }

  override fun addDatasetPartToDataset(dataset: Dataset, datasetPart: DatasetPart): DatasetPart {
    dataset.parts.add(datasetPart)
    datasetRepository.update(dataset)
    return datasetPart
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
      files: Array<MultipartFile>?
  ): Dataset {
    val filesUploaded = files ?: emptyArray()
    workspaceService.getVerifiedWorkspace(organizationId, workspaceId, PERMISSION_CREATE_CHILDREN)
    logger.debug("Registering Dataset: {}", datasetCreateRequest)
    validDatasetCreateRequest(datasetCreateRequest, filesUploaded)

    val datasetId = idGenerator.generate("dataset")
    val now = Instant.now().toEpochMilli()
    val userId = getCurrentAccountIdentifier(csmPlatformProperties)
    val editInfo = EditInfo(timestamp = now, userId = userId)
    val createInfo =
        CreateInfo(timestamp = now, userId = userId, runnerId = datasetCreateRequest.runnerId)
    val security =
        csmRbac
            .initSecurity(datasetCreateRequest.security.toGenericSecurity(datasetId))
            .toResourceSecurity()

    val datasetParts =
        datasetCreateRequest.parts
            ?.map { part ->
              val constructDatasetPart =
                  constructDatasetPart(organizationId, workspaceId, datasetId, part)
              datasetPartManagementFactory.storeData(
                  constructDatasetPart,
                  filesUploaded.first { it.originalFilename == part.sourceName })
              constructDatasetPart
            }
            ?.toMutableList()

    if (!datasetParts.isNullOrEmpty()) {
      datasetPartRepository.saveAll(datasetParts)
    }

    val createdDataset =
        Dataset(
            id = datasetId,
            name = datasetCreateRequest.name,
            description = datasetCreateRequest.description,
            organizationId = organizationId,
            workspaceId = workspaceId,
            tags = datasetCreateRequest.tags ?: mutableListOf(),
            parts = datasetParts ?: mutableListOf(),
            createInfo = createInfo,
            updateInfo = editInfo,
            security = security)
    logger.debug("Registering Dataset: {}", createdDataset)

    return datasetRepository.save(createdDataset)
  }

  override fun deleteDataset(organizationId: String, workspaceId: String, datasetId: String) {
    val dataset = getVerifiedDataset(organizationId, workspaceId, datasetId, PERMISSION_DELETE)
    datasetRepository.delete(dataset)
    dataset.parts.forEach {
      datasetPartRepository.delete(it)
      datasetPartManagementFactory.removeData(it)
    }
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
              datasetRepository
                  .findByOrganizationIdAndWorkspaceIdNoSecurity(organizationId, workspaceId, it)
                  .toList()
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
            datasetRepository
                .findByOrganizationIdAndWorkspaceIdNoSecurity(organizationId, workspaceId, pageable)
                .toList()
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
      datasetUpdateRequest: DatasetUpdateRequest,
      files: Array<MultipartFile>?
  ): Dataset {
    logger.debug("Updating Dataset: {}", datasetUpdateRequest)
    val filesUploaded = files ?: emptyArray()
    val previousDataset =
        getVerifiedDataset(organizationId, workspaceId, datasetId, PERMISSION_WRITE)
    validDatasetUpdateRequest(datasetUpdateRequest, filesUploaded)

    val newDatasetParts =
        datasetUpdateRequest.parts
            ?.map { part ->
              val constructDatasetPart =
                  constructDatasetPart(organizationId, workspaceId, datasetId, part)
              datasetPartManagementFactory.storeData(
                  constructDatasetPart,
                  filesUploaded.first { it.originalFilename == part.sourceName })
              constructDatasetPart
            }
            ?.toMutableList()

    val updatedDataset =
        Dataset(
            id = datasetId,
            name = datasetUpdateRequest.name ?: previousDataset.name,
            description = datasetUpdateRequest.description ?: previousDataset.description,
            organizationId = organizationId,
            workspaceId = workspaceId,
            tags = datasetUpdateRequest.tags ?: previousDataset.tags,
            parts = newDatasetParts ?: previousDataset.parts,
            createInfo = previousDataset.createInfo,
            updateInfo =
                EditInfo(
                    timestamp = Instant.now().toEpochMilli(),
                    userId = getCurrentAccountIdentifier(csmPlatformProperties)),
            security = datasetUpdateRequest.security ?: previousDataset.security)

    logger.debug("New Dataset info to register: {}", updatedDataset)

    if (newDatasetParts != null) {
      datasetPartRepository.saveAll(newDatasetParts)
    }
    val newDataset = datasetRepository.update(updatedDataset)

    if (previousDataset.parts.isNotEmpty()) {
      datasetPartRepository.deleteAll(previousDataset.parts)
      previousDataset.parts.forEach { datasetPartManagementFactory.removeData(it) }
    }

    return newDataset
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
    val dataset = getVerifiedDataset(organizationId, workspaceId, datasetId, PERMISSION_WRITE)
    validDatasetPartCreateRequest(datasetPartCreateRequest, file)

    val createdDatasetPart =
        constructDatasetPart(organizationId, workspaceId, datasetId, datasetPartCreateRequest)
    datasetPartManagementFactory.storeData(createdDatasetPart, file)
    datasetPartRepository.save(createdDatasetPart)
    return addDatasetPartToDataset(dataset, createdDatasetPart)
  }

  override fun createDatasetPartFromResource(
      organizationId: String,
      workspaceId: String,
      datasetId: String,
      file: Resource,
      datasetPartCreateRequest: DatasetPartCreateRequest
  ): DatasetPart {
    val dataset = getVerifiedDataset(organizationId, workspaceId, datasetId, PERMISSION_WRITE)
    val createdDatasetPart =
        constructDatasetPart(organizationId, workspaceId, datasetId, datasetPartCreateRequest)
    datasetPartManagementFactory.storeData(createdDatasetPart, file)
    datasetPartRepository.save(createdDatasetPart)
    return addDatasetPartToDataset(dataset, createdDatasetPart)
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
            type = datasetPartCreateRequest.type ?: DatasetPartTypeEnum.File,
            organizationId = organizationId,
            workspaceId = workspaceId,
            createInfo = editInfo,
            updateInfo = editInfo,
            sourceName = datasetPartCreateRequest.sourceName)
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
    datasetPartManagementFactory.removeData(datasetPart)
    datasetPartRepository.delete(datasetPart)
  }

  override fun downloadDatasetPart(
      organizationId: String,
      workspaceId: String,
      datasetId: String,
      datasetPartId: String
  ): Resource {
    val datasetPart = getDatasetPart(organizationId, workspaceId, datasetId, datasetPartId)
    return datasetPartManagementFactory.getData(datasetPart)
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
              datasetPartRepository
                  .findByOrganizationIdAndWorkspaceIdAndDatasetIdNoSecurity(
                      organizationId, workspaceId, datasetId, it)
                  .toList()
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
            datasetPartRepository
                .findByOrganizationIdAndWorkspaceIdAndDatasetIdNoSecurity(
                    organizationId, workspaceId, datasetId, pageable)
                .toList()
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
    val datasetPart = getDatasetPart(organizationId, workspaceId, datasetId, datasetPartId)
    if (datasetPart.type == DatasetPartTypeEnum.File) {
      throw IllegalArgumentException("Cannot query data on a File Dataset Part")
    }
    // TODO handle DatasetPartTypeEnum == Relational
    return emptyList()
  }

  override fun updateDatasetPart(
      organizationId: String,
      workspaceId: String,
      datasetId: String,
      datasetPartId: String,
      datasetPartUpdateRequest: DatasetPartUpdateRequest
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
    val now = Instant.now().toEpochMilli()
    val userId = getCurrentAccountIdentifier(csmPlatformProperties)
    val editInfo = EditInfo(timestamp = now, userId = userId)

    dataset.parts
        .find { it.id == datasetPartId }
        ?.let {
          it.sourceName = datasetPartUpdateRequest.sourceName ?: it.sourceName
          it.description = datasetPartUpdateRequest.description ?: it.description
          it.tags = datasetPartUpdateRequest.tags ?: it.tags
          it.updateInfo = editInfo
        }

    val datasetPartUpdater = datasetPart.copy()
    datasetPartUpdater.sourceName = datasetPartUpdateRequest.sourceName ?: datasetPart.sourceName
    datasetPartUpdater.description = datasetPartUpdateRequest.description ?: datasetPart.description
    datasetPartUpdater.tags = datasetPartUpdateRequest.tags ?: datasetPart.tags
    datasetPartUpdater.updateInfo = editInfo

    datasetRepository.update(dataset)
    return datasetPartRepository.update(datasetPartUpdater)
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
    validateFile(file)
    val datasetPart =
        datasetPartRepository
            .findBy(organizationId, workspaceId, datasetId, datasetPartId)
            .orElseThrow {
              CsmResourceNotFoundException(
                  "Dataset Part $datasetPartId not found in organization $organizationId, " +
                      "workspace $workspaceId and dataset $datasetId")
            }

    val now = Instant.now().toEpochMilli()
    val userId = getCurrentAccountIdentifier(csmPlatformProperties)
    val editInfo = EditInfo(timestamp = now, userId = userId)
    val datasetPartUpdater = datasetPart.copy()
    dataset.parts
        .find { it.id == datasetPartId }
        ?.let {
          it.sourceName = datasetPartUpdateRequest?.sourceName ?: it.sourceName
          it.description = datasetPartUpdateRequest?.description ?: it.description
          it.tags = datasetPartUpdateRequest?.tags ?: it.tags
          it.updateInfo = editInfo
        }

    datasetPartUpdater.sourceName = datasetPartUpdateRequest?.sourceName ?: datasetPart.sourceName
    datasetPartUpdater.description =
        datasetPartUpdateRequest?.description ?: datasetPart.description
    datasetPartUpdater.tags = datasetPartUpdateRequest?.tags ?: datasetPart.tags
    datasetPartUpdater.updateInfo = editInfo

    datasetPartManagementFactory.removeData(datasetPart)
    datasetPartManagementFactory.storeData(datasetPartUpdater, file, true)
    datasetRepository.update(dataset)
    return datasetPartRepository.update(datasetPartUpdater)
  }

  override fun searchDatasetParts(
      organizationId: String,
      workspaceId: String,
      datasetId: String,
      requestBody: List<String>,
      page: Int?,
      size: Int?
  ): List<DatasetPart> {
    if (requestBody.isEmpty()) {
      return listDatasetParts(organizationId, workspaceId, datasetId, page, size)
    }
    getVerifiedDataset(organizationId, workspaceId, datasetId)

    val defaultPageSize = csmPlatformProperties.twincache.dataset.defaultPageSize
    val pageable = constructPageRequest(page, size, defaultPageSize)
    val datasetPartList =
        if (pageable != null) {
          datasetPartRepository
              .findDatasetPartByTags(organizationId, workspaceId, datasetId, requestBody, pageable)
              .toList()
        } else {
          findAllPaginated(defaultPageSize) {
            datasetPartRepository
                .findDatasetPartByTags(organizationId, workspaceId, datasetId, requestBody, it)
                .toList()
          }
        }

    return datasetPartList
  }

  override fun searchDatasets(
      organizationId: String,
      workspaceId: String,
      requestBody: List<String>,
      page: Int?,
      size: Int?
  ): List<Dataset> {
    if (requestBody.isEmpty()) {
      return listDatasets(organizationId, workspaceId, page, size)
    }
    workspaceService.getVerifiedWorkspace(organizationId, workspaceId)

    val defaultPageSize = csmPlatformProperties.twincache.dataset.defaultPageSize
    val pageable = constructPageRequest(page, size, defaultPageSize)
    val datasetList =
        if (pageable != null) {
          datasetRepository
              .findDatasetByTags(organizationId, workspaceId, requestBody, pageable)
              .toList()
        } else {
          findAllPaginated(defaultPageSize) {
            datasetRepository
                .findDatasetByTags(organizationId, workspaceId, requestBody, it)
                .toList()
          }
        }

    datasetList.forEach { it.security = updateSecurityVisibility(it).security }
    return datasetList
  }

  private fun validDatasetPartCreateRequest(
      datasetPartCreateRequest: DatasetPartCreateRequest,
      file: MultipartFile
  ) {
    require(datasetPartCreateRequest.name.isNotBlank()) { "Dataset Part name must not be blank" }
    require(datasetPartCreateRequest.sourceName == file.originalFilename) {
      "You must upload a file with the same name as the Dataset Part sourceName. " +
          "You provided ${datasetPartCreateRequest.sourceName} and ${file.originalFilename} instead."
    }

    validateFile(file)
  }

  private fun validateFile(file: MultipartFile) {
    val originalFilename = file.originalFilename

    require(!originalFilename.isNullOrBlank()) { "File name must not be null or blank" }
    require(!originalFilename.contains("..") && !originalFilename.startsWith("/")) {
      "Invalid filename: '${originalFilename}'. File name should neither contains '..' nor starts by '/'."
    }
    resourceScanner.scanMimeTypes(
        originalFilename,
        file.inputStream,
        csmPlatformProperties.upload.authorizedMimeTypes.datasets)
  }

  private fun validDatasetCreateRequest(
      datasetCreateRequest: DatasetCreateRequest,
      files: Array<MultipartFile>
  ) {
    require(datasetCreateRequest.name.isNotBlank()) { "Dataset name must not be blank" }
    require(files.size == datasetCreateRequest.parts?.size) {
      "Number of files must be equal to the number of parts if specified. " +
          "${files.size} != ${datasetCreateRequest.parts?.size}"
    }
    require(
        files.groupingBy { it.originalFilename }.eachCount().filter { it.value > 1 }.isEmpty()) {
          "Part File names should be unique during dataset creation. " +
              "Multipart file names: ${files.map { it.originalFilename }}. " +
              "Dataset parts source names: ${datasetCreateRequest.parts?.map { it.sourceName }}."
        }
    require(
        files.mapNotNull { it.originalFilename }.toSortedSet(naturalOrder()) ==
            datasetCreateRequest.parts?.map { it.sourceName }?.toSortedSet(naturalOrder())) {
          "All files must have the same name as corresponding sourceName in a Dataset Part. " +
              "Multipart file names: ${files.map { it.originalFilename }}. " +
              "Dataset parts source names: ${datasetCreateRequest.parts?.map { it.sourceName }}."
        }
    files.forEach { file -> validateFile(file) }
  }

  private fun validDatasetUpdateRequest(
      datasetUpdateRequest: DatasetUpdateRequest,
      files: Array<MultipartFile>
  ) {
    require(
        datasetUpdateRequest.name == null ||
            (datasetUpdateRequest.name != null && datasetUpdateRequest.name!!.isNotBlank())) {
          "Dataset name must not be blank"
        }
    require(files.size == (datasetUpdateRequest.parts?.size ?: 0)) {
      "Number of files must be equal to the number of parts if specified. " +
          "${files.size} != ${datasetUpdateRequest.parts?.size}"
    }
    require(
        files.groupingBy { it.originalFilename }.eachCount().filter { it.value > 1 }.isEmpty()) {
          "Multipart file names should be unique during dataset update. " +
              "Multipart file names: ${files.map { it.originalFilename }}. " +
              "Dataset parts source names: ${datasetUpdateRequest.parts?.map { it.sourceName }}."
        }
    require(
        files.mapNotNull { it.originalFilename }.toSortedSet(naturalOrder()) ==
            (datasetUpdateRequest.parts?.map { it.sourceName }?.toSortedSet(naturalOrder())
                ?: emptySet<String>())) {
          "All files must have the same name as corresponding sourceName in a Dataset Part. " +
              "Multipart file names: ${files.map { it.originalFilename }}. " +
              "Dataset parts source names: ${datasetUpdateRequest.parts?.map { it.sourceName } ?: emptyList()}."
        }

    files.forEach { file -> validateFile(file) }
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
