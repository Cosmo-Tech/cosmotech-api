// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.workspace.service

import com.cosmotech.api.CsmPhoenixService
import com.cosmotech.api.events.AddDatasetToWorkspace
import com.cosmotech.api.events.AddWorkspaceToDataset
import com.cosmotech.api.events.DeleteHistoricalDataOrganization
import com.cosmotech.api.events.DeleteHistoricalDataWorkspace
import com.cosmotech.api.events.OrganizationUnregistered
import com.cosmotech.api.events.RemoveDatasetFromWorkspace
import com.cosmotech.api.events.RemoveWorkspaceFromDataset
import com.cosmotech.api.events.WorkspaceDeleted
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.rbac.CsmRbac
import com.cosmotech.api.rbac.PERMISSION_CREATE_CHILDREN
import com.cosmotech.api.rbac.PERMISSION_DELETE
import com.cosmotech.api.rbac.PERMISSION_READ
import com.cosmotech.api.rbac.PERMISSION_READ_SECURITY
import com.cosmotech.api.rbac.PERMISSION_WRITE
import com.cosmotech.api.rbac.PERMISSION_WRITE_SECURITY
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.rbac.getCommonRolesDefinition
import com.cosmotech.api.rbac.getPermissions
import com.cosmotech.api.rbac.model.RbacAccessControl
import com.cosmotech.api.rbac.model.RbacSecurity
import com.cosmotech.api.security.coroutine.SecurityCoroutineContext
import com.cosmotech.api.utils.ResourceScanner
import com.cosmotech.api.utils.compareToAndMutateIfNeeded
import com.cosmotech.api.utils.constructPageRequest
import com.cosmotech.api.utils.convertToMap
import com.cosmotech.api.utils.findAllPaginated
import com.cosmotech.api.utils.getCurrentAccountIdentifier
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.organization.OrganizationApiServiceInterface
import com.cosmotech.organization.service.toGenericSecurity
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.workspace.WorkspaceApiServiceInterface
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceAccessControl
import com.cosmotech.workspace.domain.WorkspaceCreateRequest
import com.cosmotech.workspace.domain.WorkspaceFile
import com.cosmotech.workspace.domain.WorkspaceRole
import com.cosmotech.workspace.domain.WorkspaceSecurity
import com.cosmotech.workspace.domain.WorkspaceSolution
import com.cosmotech.workspace.domain.WorkspaceUpdateRequest
import com.cosmotech.workspace.repository.WorkspaceRepository
import com.cosmotech.workspace.utils.getWorkspaceFilePath
import com.cosmotech.workspace.utils.getWorkspaceFilesDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Scope
import org.springframework.context.event.EventListener
import org.springframework.core.io.PathResource
import org.springframework.core.io.Resource
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Suppress("TooManyFunctions")
internal class WorkspaceServiceImpl(
    private val organizationService: OrganizationApiServiceInterface,
    private val solutionService: SolutionApiService,
    private val csmRbac: CsmRbac,
    private val resourceScanner: ResourceScanner,
    private val workspaceRepository: WorkspaceRepository
) : CsmPhoenixService(), WorkspaceApiServiceInterface {

  override fun listWorkspaces(organizationId: String, page: Int?, size: Int?): List<Workspace> {
    val organization = organizationService.getVerifiedOrganization(organizationId)
    val isAdmin =
        csmRbac.isAdmin(
            organization.security.toGenericSecurity(organizationId), getCommonRolesDefinition())
    val defaultPageSize = csmPlatformProperties.twincache.workspace.defaultPageSize
    var result: List<Workspace>
    var pageable = constructPageRequest(page, size, defaultPageSize)

    if (pageable == null) {
      result =
          findAllPaginated(defaultPageSize) {
            if (isAdmin || !this.csmPlatformProperties.rbac.enabled) {
              workspaceRepository.findByOrganizationId(organizationId, it).toList()
            } else {
              val currentUser = getCurrentAccountIdentifier(this.csmPlatformProperties)
              workspaceRepository
                  .findByOrganizationIdAndSecurity(organizationId, currentUser, it)
                  .toList()
            }
          }
    } else {
      if (isAdmin || !this.csmPlatformProperties.rbac.enabled) {
        result = workspaceRepository.findByOrganizationId(organizationId, pageable).toList()
      } else {
        val currentUser = getCurrentAccountIdentifier(this.csmPlatformProperties)
        result =
            workspaceRepository
                .findByOrganizationIdAndSecurity(organizationId, currentUser, pageable)
                .toList()
      }
    }
    result.forEach { it.security = updateSecurityVisibility(it).security }
    return result
  }

  override fun getWorkspace(organizationId: String, workspaceId: String): Workspace {
    return updateSecurityVisibility(getVerifiedWorkspace(organizationId, workspaceId))
  }

  override fun createWorkspace(organizationId: String, workspaceCreateRequest: WorkspaceCreateRequest): Workspace {
    organizationService.getVerifiedOrganization(
        organizationId, listOf(PERMISSION_READ, PERMISSION_CREATE_CHILDREN))

    // Validate Solution ID
    workspaceCreateRequest.solution.solutionId.let { solutionService.getSolution(organizationId, it) }

    val workspaceId = idGenerator.generate("workspace")
    val security = csmRbac.initSecurity(
      workspaceCreateRequest.security.toGenericSecurity(workspaceId)).toResourceSecurity()
    val createdWorkspace =
      Workspace(
        id = workspaceId,
        organizationId = organizationId,
        ownerId = getCurrentAuthenticatedUserName(csmPlatformProperties),
        key = workspaceCreateRequest.key,
        name = workspaceCreateRequest.name,
        solution = workspaceCreateRequest.solution,
        security = security,
        version = workspaceCreateRequest.version,
        tags = workspaceCreateRequest.tags,
        description = workspaceCreateRequest.description,
        webApp = workspaceCreateRequest.webApp,
        datasetCopy = workspaceCreateRequest.datasetCopy,
        )

    return workspaceRepository.save(createdWorkspace)
  }

  override fun deleteWorkspaceFiles(organizationId: String, workspaceId: String) {
    val workspace = getVerifiedWorkspace(organizationId, workspaceId, PERMISSION_WRITE)
    deleteAllFilesystemWorkspaceFiles(organizationId, workspace)
  }

  override fun updateWorkspace(
    organizationId: String,
    workspaceId: String,
    workspaceUpdateRequest: WorkspaceUpdateRequest
  ): Workspace {
    val existingWorkspace = this.getVerifiedWorkspace(organizationId, workspaceId, PERMISSION_WRITE)
    // Security cannot be changed by updateWorkspace

    val updatedWorkspace = Workspace(
      key = workspaceUpdateRequest.key ?: existingWorkspace.key,
      name = workspaceUpdateRequest.name ?: existingWorkspace.name,
      solution = workspaceUpdateRequest.solution ?: existingWorkspace.solution,
      id = existingWorkspace.id,
      organizationId = organizationId,
      description = workspaceUpdateRequest.description ?: existingWorkspace.description,
      tags = workspaceUpdateRequest.tags ?: existingWorkspace.tags,
      ownerId = existingWorkspace.ownerId,
      webApp = workspaceUpdateRequest.webApp ?: existingWorkspace.webApp,
      datasetCopy = workspaceUpdateRequest.datasetCopy ?: existingWorkspace.datasetCopy,
      security = existingWorkspace.security
    )

    var hasChanged =
        existingWorkspace
            .compareToAndMutateIfNeeded(
                updatedWorkspace, excludedFields = arrayOf("ownerId", "security", "solution"))
            .isNotEmpty()

    if (updatedWorkspace.solution.solutionId != existingWorkspace.solution.solutionId) {
      // Validate solution ID
      updatedWorkspace.solution.solutionId.let { solutionService.getSolution(organizationId, it) }
      existingWorkspace.solution = updatedWorkspace.solution
      hasChanged = true
    }

    return if (hasChanged) {
      workspaceRepository.save(existingWorkspace)
    } else {
      existingWorkspace
    }
  }

  override fun deleteWorkspace(organizationId: String, workspaceId: String) {
    val workspace = getVerifiedWorkspace(organizationId, workspaceId, PERMISSION_DELETE)

    try {
      deleteAllFilesystemWorkspaceFiles(organizationId, workspace)
      workspace.linkedDatasetIdList?.forEach { deleteDatasetLink(organizationId, workspaceId, it) }
    } finally {
      workspaceRepository.delete(workspace)
      if (csmPlatformProperties.internalResultServices?.enabled == true) {
        this.eventPublisher.publishEvent(WorkspaceDeleted(this, organizationId, workspaceId))
      }
    }
  }

  override fun deleteWorkspaceFile(organizationId: String, workspaceId: String, fileName: String) {
    if (".." in fileName) {
      throw IllegalArgumentException("Invalid filename: '$fileName'. '..' is not allowed")
    }
    val workspace = getVerifiedWorkspace(organizationId, workspaceId, PERMISSION_WRITE)
    deleteFilesystemWorkspaceFile(organizationId, workspace, fileName)
  }

  override fun getWorkspaceFile(
      organizationId: String,
      workspaceId: String,
      fileName: String
  ): Resource {
    if (".." in fileName) {
      throw IllegalArgumentException("Invalid filename: '$fileName'. '..' is not allowed")
    }
    val workspace = getVerifiedWorkspace(organizationId, workspaceId, PERMISSION_READ)
    logger.debug(
        "Downloading file resource from workspace #{} ({}): {}",
        workspace.id,
        workspace.name,
        fileName)
    val filePath =
        getWorkspaceFilePath(csmPlatformProperties, organizationId, workspaceId, fileName)
    if (!Files.isRegularFile(filePath)) {
      throw CsmResourceNotFoundException("$fileName not found")
    }
    return PathResource(filePath)
  }

  override fun createWorkspaceFile(
      organizationId: String,
      workspaceId: String,
      file: Resource,
      overwrite: Boolean,
      destination: String?
  ): WorkspaceFile {
    if (destination?.contains("..") == true) {
      throw IllegalArgumentException("Invalid destination: '$destination'. '..' is not allowed")
    }
    val workspace = getVerifiedWorkspace(organizationId, workspaceId, PERMISSION_WRITE)
    if (file.filename?.contains("..") == true || file.filename?.contains("/") == true) {
      throw IllegalArgumentException(
          "Invalid filename: '${file.filename}'. '..' and '/' are not allowed")
    }

    logger.debug(
        "Uploading file resource to workspace #{} ({}): {} => {}",
        workspace.id,
        workspace.name,
        file.filename,
        destination)

    resourceScanner.scanMimeTypes(file, csmPlatformProperties.upload.authorizedMimeTypes.workspaces)
    val fileRelativeDestinationBuilder = StringBuilder()
    if (destination.isNullOrBlank()) {
      fileRelativeDestinationBuilder.append(file.filename)
    } else {
      // Using multiple consecutive '/' in the path is not supported in the storage upload
      val destinationSanitized = destination.removePrefix("/").replace(Regex("(/)\\1+"), "/")
      fileRelativeDestinationBuilder.append(destinationSanitized)
      if (destinationSanitized.endsWith("/")) {
        fileRelativeDestinationBuilder.append(file.filename)
      }
    }

    val filePath =
        getWorkspaceFilePath(
            csmPlatformProperties,
            organizationId,
            workspaceId,
            fileRelativeDestinationBuilder.toString())

    if (overwrite == false && Files.exists(filePath)) {
      throw IllegalArgumentException(
          "File '$fileRelativeDestinationBuilder' already exists, not overwriting it")
    }

    Files.createDirectories(filePath.getParent())
    Files.copy(file.inputStream, filePath, REPLACE_EXISTING)
    return WorkspaceFile(fileName = fileRelativeDestinationBuilder.toString())
  }

  override fun listWorkspaceFiles(
      organizationId: String,
      workspaceId: String
  ): List<WorkspaceFile> {
    val workspace = getVerifiedWorkspace(organizationId, workspaceId)

    logger.debug("List all files for workspace #{} ({})", workspace.id, workspace.name)

    return getWorkspaceFiles(organizationId, workspaceId)
  }

  @EventListener(DeleteHistoricalDataOrganization::class)
  fun deleteHistoricalDataWorkspace(data: DeleteHistoricalDataOrganization) {
    val organizationId = data.organizationId
    var pageable = Pageable.ofSize(csmPlatformProperties.twincache.workspace.defaultPageSize)
    val workspaceList = mutableListOf<Workspace>()

    do {
      val workspaces = listWorkspaces(organizationId, pageable.pageNumber, pageable.pageSize)
      workspaceList.addAll(workspaces)
      pageable = pageable.next()
    } while (workspaces.isNotEmpty())

    workspaceList.forEach { sendDeleteHistoricalDataWorkspaceEvent(organizationId, it, data) }
  }

  @EventListener(OrganizationUnregistered::class)
  @Async("csm-in-process-event-executor")
  fun onOrganizationUnregistered(organizationUnregistered: OrganizationUnregistered) {
    val organizationId = organizationUnregistered.organizationId
    val pageable: Pageable =
        Pageable.ofSize(csmPlatformProperties.twincache.workspace.defaultPageSize)
    val workspaces = workspaceRepository.findByOrganizationId(organizationId, pageable).toList()
    workspaces.forEach {
      workspaceRepository.delete(it)
      if (csmPlatformProperties.internalResultServices?.enabled == true) {
        this.eventPublisher.publishEvent(WorkspaceDeleted(this, organizationId, it.id))
      }
    }
    File(
            Path.of(csmPlatformProperties.blobPersistence.path, organizationId)
                .toAbsolutePath()
                .toString())
        .deleteRecursively()
  }

  override fun createDatasetLink(
      organizationId: String,
      workspaceId: String,
      datasetId: String
  ): Workspace {
    organizationService.getVerifiedOrganization(organizationId)
    this.getVerifiedWorkspace(organizationId, workspaceId, PERMISSION_WRITE)
    sendAddWorkspaceToDatasetEvent(organizationId, datasetId, workspaceId)
    return addDatasetToLinkedDatasetIdList(organizationId, workspaceId, datasetId)
  }

  @EventListener(AddDatasetToWorkspace::class)
  fun processEventAddDatasetToWorkspace(addDatasetToWorkspace: AddDatasetToWorkspace) {
    this.getVerifiedWorkspace(
        addDatasetToWorkspace.organizationId, addDatasetToWorkspace.workspaceId, PERMISSION_WRITE)
    addDatasetToLinkedDatasetIdList(
        addDatasetToWorkspace.organizationId,
        addDatasetToWorkspace.workspaceId,
        addDatasetToWorkspace.datasetId)
  }

  override fun deleteDatasetLink(
      organizationId: String,
      workspaceId: String,
      datasetId: String
  ) {
    this.getVerifiedWorkspace(organizationId, workspaceId, PERMISSION_WRITE)
    sendRemoveWorkspaceFromDatasetEvent(organizationId, datasetId, workspaceId)
    removeDatasetFromLinkedDatasetIdList(organizationId, workspaceId, datasetId)
  }

  @EventListener(RemoveDatasetFromWorkspace::class)
  fun processEventRemoveDatasetFromWorkspace(
      removeDatasetFromWorkspace: RemoveDatasetFromWorkspace
  ) {
    this.getVerifiedWorkspace(
        removeDatasetFromWorkspace.organizationId,
        removeDatasetFromWorkspace.workspaceId,
        PERMISSION_WRITE)
    removeDatasetFromLinkedDatasetIdList(
        removeDatasetFromWorkspace.organizationId,
        removeDatasetFromWorkspace.workspaceId,
        removeDatasetFromWorkspace.datasetId)
  }

  fun removeDatasetFromLinkedDatasetIdList(
      organizationId: String,
      workspaceId: String,
      datasetId: String
  ): Workspace {
    val workspace = getVerifiedWorkspace(organizationId, workspaceId)

    if (workspace.linkedDatasetIdList != null) {
      if (workspace.linkedDatasetIdList!!.contains(datasetId)) {
        workspace.linkedDatasetIdList!!.remove(datasetId)
        return workspaceRepository.save(workspace)
      }
    }

    return workspace
  }

  private fun getWorkspaceFiles(organizationId: String, workspaceId: String): List<WorkspaceFile> {
    val rootPath = getWorkspaceFilesDir(csmPlatformProperties, organizationId, workspaceId)
    if (!Files.exists(rootPath)) {
      return listOf()
    }
    return Files.walk(rootPath)
        .filter { Files.isRegularFile(it) }
        .map { WorkspaceFile(fileName = rootPath.relativize(it).toString()) }
        .toList()
  }

  private fun deleteFilesystemWorkspaceFile(
      organizationId: String,
      workspace: Workspace,
      fileName: String
  ) {
    logger.debug(
        "Deleting file resource from workspace #{} ({}): {}",
        workspace.id,
        workspace.name,
        fileName)
    Files.deleteIfExists(
        getWorkspaceFilePath(csmPlatformProperties, organizationId, workspace.id, fileName))
  }

  private fun deleteAllFilesystemWorkspaceFiles(organizationId: String, workspace: Workspace) {
    GlobalScope.launch(SecurityCoroutineContext()) {
      // TODO Consider using a smaller coroutine scope
      logger.debug("Deleting all files for workspace #{} ({})", workspace.id, workspace.name)
      File(getWorkspaceFilesDir(csmPlatformProperties, organizationId, workspace.id).toString())
          .deleteRecursively()
    }
  }

  override fun listWorkspaceRolePermissions(organizationId: String, workspaceId: String, role: String): List<String> {
    getVerifiedWorkspace(organizationId, workspaceId, PERMISSION_READ_SECURITY)
    return getPermissions(role, getCommonRolesDefinition())
  }

  override fun getWorkspaceSecurity(
      organizationId: String,
      workspaceId: String
  ): WorkspaceSecurity {
    val workspace = getVerifiedWorkspace(organizationId, workspaceId, PERMISSION_READ_SECURITY)
    return workspace.security
  }

  override fun updateWorkspaceDefaultSecurity(
      organizationId: String,
      workspaceId: String,
      workspaceRole: WorkspaceRole
  ): WorkspaceSecurity {
    val workspace = getVerifiedWorkspace(organizationId, workspaceId, PERMISSION_WRITE_SECURITY)
    val rbacSecurity = csmRbac.setDefault(workspace.security.toGenericSecurity(workspaceId), workspaceRole.role)
    workspace.security = rbacSecurity.toResourceSecurity()
    workspaceRepository.save(workspace)
    return workspace.security
  }

  override fun getWorkspaceAccessControl(
      organizationId: String,
      workspaceId: String,
      identityId: String
  ): WorkspaceAccessControl {
    val workspace = getVerifiedWorkspace(organizationId, workspaceId, PERMISSION_READ_SECURITY)
    val rbacAccessControl = csmRbac.getAccessControl(workspace.security.toGenericSecurity(workspaceId), identityId)
    return WorkspaceAccessControl(rbacAccessControl.id, rbacAccessControl.role)
  }

  override fun getVerifiedWorkspace(
      organizationId: String,
      workspaceId: String,
      requiredPermission: String
  ): Workspace {
    organizationService.getVerifiedOrganization(organizationId)
    val workspace =
        workspaceRepository.findByIdOrNull(workspaceId)
            ?: throw CsmResourceNotFoundException("Workspace $workspaceId does not exist!")
    csmRbac.verify(workspace.security.toGenericSecurity(workspaceId), requiredPermission)
    return workspace
  }

  override fun createWorkspaceAccessControl(
      organizationId: String,
      workspaceId: String,
      workspaceAccessControl: WorkspaceAccessControl
  ): WorkspaceAccessControl {
    val organization = organizationService.getVerifiedOrganization(organizationId)
    val workspace = getVerifiedWorkspace(organizationId, workspaceId, PERMISSION_WRITE_SECURITY)
    val users = csmRbac.getUsers(workspace.security.toGenericSecurity(workspaceId))
    if (users.contains(workspaceAccessControl.id)) {
      throw IllegalArgumentException("User is already in this Workspace security")
    }

    val rbacSecurity =
        csmRbac.addUserRole(
            organization.security.toGenericSecurity(organizationId),
            workspace.security.toGenericSecurity(workspaceId),
            workspaceAccessControl.id,
            workspaceAccessControl.role)
    workspace.security = rbacSecurity.toResourceSecurity()
    workspaceRepository.save(workspace)
    val rbacAccessControl = csmRbac.getAccessControl(
      workspace.security.toGenericSecurity(workspaceId), workspaceAccessControl.id)
    return WorkspaceAccessControl(rbacAccessControl.id, rbacAccessControl.role)
  }

  override fun updateWorkspaceAccessControl(
      organizationId: String,
      workspaceId: String,
      identityId: String,
      workspaceRole: WorkspaceRole
  ): WorkspaceAccessControl {
    val workspace = getVerifiedWorkspace(organizationId, workspaceId, PERMISSION_WRITE_SECURITY)
    csmRbac.checkUserExists(
        workspace.security.toGenericSecurity(workspaceId),
      identityId,
      "User '$identityId' not found in workspace $workspaceId")
    val rbacSecurity =
      csmRbac.setUserRole(workspace.security.toGenericSecurity(workspaceId), identityId, workspaceRole.role)
    workspace.security = rbacSecurity.toResourceSecurity()
    workspaceRepository.save(workspace)
    val rbacAccessControl = csmRbac.getAccessControl(workspace.security.toGenericSecurity(workspaceId), identityId)
    return WorkspaceAccessControl(rbacAccessControl.id, rbacAccessControl.role)
  }

  override fun deleteWorkspaceAccessControl(
      organizationId: String,
      workspaceId: String,
      identityId: String
  ) {
    val workspace = getVerifiedWorkspace(organizationId, workspaceId, PERMISSION_WRITE_SECURITY)
    val rbacSecurity = csmRbac.removeUser(workspace.security.toGenericSecurity(workspaceId), identityId)
    workspace.security = rbacSecurity.toResourceSecurity()
    workspaceRepository.save(workspace)
  }

  override fun listWorkspaceSecurityUsers(organizationId: String, workspaceId: String): List<String> {
    val workspace = getVerifiedWorkspace(organizationId, workspaceId, PERMISSION_READ_SECURITY)
    return csmRbac.getUsers(workspace.security.toGenericSecurity(workspaceId))
  }

  private fun addDatasetToLinkedDatasetIdList(
      organizationId: String,
      workspaceId: String,
      datasetId: String
  ): Workspace {
    val workspace = getWorkspace(organizationId, workspaceId)

    if (workspace.linkedDatasetIdList != null) {
      if (workspace.linkedDatasetIdList!!.contains(datasetId)) {
        return workspace
      } else {
        workspace.linkedDatasetIdList!!.add(datasetId)
      }
    } else {
      workspace.linkedDatasetIdList = mutableListOf(datasetId)
    }
    return workspaceRepository.save(workspace)
  }

  private fun sendRemoveWorkspaceFromDatasetEvent(
      organizationId: String,
      datasetId: String,
      workspaceId: String
  ) {
    this.eventPublisher.publishEvent(
        RemoveWorkspaceFromDataset(this, organizationId, datasetId, workspaceId))
  }

  private fun sendAddWorkspaceToDatasetEvent(
      organizationId: String,
      datasetId: String,
      workspaceId: String
  ) {
    this.eventPublisher.publishEvent(
        AddWorkspaceToDataset(this, organizationId, datasetId, workspaceId))
  }

  private fun sendDeleteHistoricalDataWorkspaceEvent(
      organizationId: String,
      it: Workspace,
      data: DeleteHistoricalDataOrganization
  ) {
    this.eventPublisher.publishEvent(
        DeleteHistoricalDataWorkspace(this, organizationId, it.id, data.deleteUnknown))
  }

  fun updateSecurityVisibility(workspace: Workspace): Workspace {
    if (csmRbac.check(workspace.security.toGenericSecurity(workspace.id), PERMISSION_READ_SECURITY).not()) {
      val username = getCurrentAccountIdentifier(csmPlatformProperties)
      val retrievedAC = workspace.security.accessControlList.firstOrNull { it.id == username }
      if (retrievedAC != null) {
        return workspace.copy(
            security =
                WorkspaceSecurity(
                    default = workspace.security.default,
                    accessControlList = mutableListOf(retrievedAC)))
      } else {
        return workspace.copy(
            security =
                WorkspaceSecurity(
                    default = workspace.security.default, accessControlList = mutableListOf()))
      }
    }
    return workspace
  }
}

fun WorkspaceSecurity?.toGenericSecurity(workspaceId: String) = RbacSecurity(
  workspaceId,
  this?.default ?: ROLE_NONE,
  this?.accessControlList?.map { RbacAccessControl(it.id, it.role) }?.toMutableList() ?: mutableListOf()
)

fun RbacSecurity.toResourceSecurity() =
  WorkspaceSecurity(
    this.default,
    this.accessControlList
      .map { WorkspaceAccessControl(it.id, it.role) }
      .toMutableList())
