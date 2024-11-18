// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.workspace.service

import com.azure.spring.cloud.core.resource.AzureStorageBlobProtocolResolver
import com.azure.storage.blob.BlobServiceClient
import com.azure.storage.blob.batch.BlobBatchClient
import com.azure.storage.blob.models.DeleteSnapshotsOptionType
import com.cosmotech.api.CsmPhoenixService
import com.cosmotech.api.azure.sanitizeForAzureStorage
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
import com.cosmotech.api.utils.SecretManager
import com.cosmotech.api.utils.compareToAndMutateIfNeeded
import com.cosmotech.api.utils.constructPageRequest
import com.cosmotech.api.utils.findAllPaginated
import com.cosmotech.api.utils.getCurrentAccountIdentifier
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.organization.OrganizationApiServiceInterface
import com.cosmotech.organization.service.getRbac
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.workspace.WorkspaceApiServiceInterface
import com.cosmotech.workspace.azure.WORKSPACE_EVENTHUB_ACCESSKEY_SECRET
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceAccessControl
import com.cosmotech.workspace.domain.WorkspaceFile
import com.cosmotech.workspace.domain.WorkspaceRole
import com.cosmotech.workspace.domain.WorkspaceSecret
import com.cosmotech.workspace.domain.WorkspaceSecurity
import com.cosmotech.workspace.repository.WorkspaceRepository
import com.cosmotech.workspace.utils.getWorkspaceSecretName
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Scope
import org.springframework.context.event.EventListener
import org.springframework.core.io.Resource
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Suppress("TooManyFunctions")
internal class WorkspaceServiceImpl(
    private val azureStorageBlobProtocolResolver: AzureStorageBlobProtocolResolver,
    private val organizationService: OrganizationApiServiceInterface,
    private val solutionService: SolutionApiService,
    private val azureStorageBlobServiceClient: BlobServiceClient,
    private val azureStorageBlobBatchClient: BlobBatchClient,
    private val csmRbac: CsmRbac,
    private val resourceScanner: ResourceScanner,
    private val secretManager: SecretManager,
    private val workspaceRepository: WorkspaceRepository
) : CsmPhoenixService(), WorkspaceApiServiceInterface {

  override fun findAllWorkspaces(organizationId: String, page: Int?, size: Int?): List<Workspace> {
    val organization = organizationService.getVerifiedOrganization(organizationId)
    val isAdmin = csmRbac.isAdmin(organization.getRbac(), getCommonRolesDefinition())
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
    return result
  }

  override fun findWorkspaceById(organizationId: String, workspaceId: String): Workspace {
    return getVerifiedWorkspace(organizationId, workspaceId)
  }

  override fun createWorkspace(organizationId: String, workspace: Workspace): Workspace {
    organizationService.getVerifiedOrganization(
        organizationId, listOf(PERMISSION_READ, PERMISSION_CREATE_CHILDREN))
    // Validate Solution ID
    workspace.solution.solutionId?.let { solutionService.findSolutionById(organizationId, it) }

    val createdWorkspace =
        workspace.copy(
            id = idGenerator.generate("workspace"),
            organizationId = organizationId,
            ownerId = getCurrentAuthenticatedUserName(csmPlatformProperties))
    createdWorkspace.setRbac(csmRbac.initSecurity(workspace.getRbac()))

    return checkReadSecurity(workspaceRepository.save(createdWorkspace))
  }

  override fun deleteAllWorkspaceFiles(organizationId: String, workspaceId: String) {
    val workspace = getVerifiedWorkspace(organizationId, workspaceId, PERMISSION_WRITE)

    logger.debug("Deleting all files for workspace #{} ({})", workspace.id, workspace.name)

    GlobalScope.launch(SecurityCoroutineContext()) {
      // TODO Consider using a smaller coroutine scope
      val workspaceFiles =
          getWorkspaceFileResources(organizationId, workspaceId)
              .map { it.url }
              .map { it.toExternalForm() }
      if (workspaceFiles.isEmpty()) {
        logger.debug("No file to delete for workspace $workspaceId")
      } else {
        azureStorageBlobBatchClient
            .deleteBlobs(workspaceFiles, DeleteSnapshotsOptionType.INCLUDE)
            .forEach { response ->
              logger.debug(
                  "Deleting blob with URL {} completed with status code {}",
                  response.request.url,
                  response.statusCode)
            }
      }
    }
  }

  override fun updateWorkspace(
      organizationId: String,
      workspaceId: String,
      workspace: Workspace
  ): Workspace {
    val existingWorkspace = getVerifiedWorkspace(organizationId, workspaceId, PERMISSION_WRITE)
    // Security cannot be changed by updateWorkspace
    var hasChanged =
        existingWorkspace
            .compareToAndMutateIfNeeded(
                workspace, excludedFields = arrayOf("ownerId", "security", "solution"))
            .isNotEmpty()

    if (workspace.solution.solutionId != null) {
      // Validate solution ID
      workspace.solution.solutionId?.let { solutionService.findSolutionById(organizationId, it) }
      existingWorkspace.solution = workspace.solution
      hasChanged = true
    }

    if (workspace.security != existingWorkspace.security) {
      logger.warn(
          "Security modification has not been applied to workspace $workspaceId," +
              " please refer to the appropriate security endpoints to perform this maneuver")
    }
    return if (hasChanged) {
      checkReadSecurity(workspaceRepository.save(existingWorkspace))
    } else {
      checkReadSecurity(existingWorkspace)
    }
  }

  override fun deleteWorkspace(organizationId: String, workspaceId: String) {
    val workspace = getVerifiedWorkspace(organizationId, workspaceId, PERMISSION_DELETE)

    try {
      deleteAllWorkspaceFiles(organizationId, workspaceId)
      secretManager.deleteSecret(
          csmPlatformProperties.namespace, getWorkspaceSecretName(organizationId, workspace.key))

      workspace.linkedDatasetIdList?.forEach { unlinkDataset(organizationId, workspaceId, it) }
    } finally {
      workspaceRepository.delete(workspace)
      if (csmPlatformProperties.internalResultServices?.enabled == true) {
        this.eventPublisher.publishEvent(WorkspaceDeleted(this, organizationId, workspaceId))
      }
    }
  }

  override fun deleteWorkspaceFile(organizationId: String, workspaceId: String, fileName: String) {
    val workspace = getVerifiedWorkspace(organizationId, workspaceId, PERMISSION_WRITE)

    logger.debug(
        "Deleting file resource from workspace #{} ({}): {}",
        workspace.id,
        workspace.name,
        fileName)
    azureStorageBlobServiceClient
        .getBlobContainerClient(organizationId.sanitizeForAzureStorage())
        .getBlobClient("${workspaceId.sanitizeForAzureStorage()}/$fileName")
        .delete()
  }

  override fun downloadWorkspaceFile(
      organizationId: String,
      workspaceId: String,
      fileName: String
  ): Resource {
    if (".." in fileName) {
      throw IllegalArgumentException("Invalid filename: '$fileName'. '..' is not allowed")
    }
    val workspace = getVerifiedWorkspace(organizationId, workspaceId, PERMISSION_READ)
    logger.debug(
        "Downloading file resource to workspace #{} ({}): {}",
        workspace.id,
        workspace.name,
        fileName)
    return azureStorageBlobProtocolResolver.getResource(
        "azure-blob://$organizationId/$workspaceId/".sanitizeForAzureStorage() + fileName)
  }

  override fun uploadWorkspaceFile(
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
    if (file?.filename?.contains("..") == true || file?.filename?.contains("/") == true) {
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
      // Using multiple consecutive '/' in the path results in Azure Storage creating
      // weird <empty> names in-between two subsequent '/'
      val destinationSanitized = destination.removePrefix("/").replace(Regex("(/)\\1+"), "/")
      fileRelativeDestinationBuilder.append(destinationSanitized)
      if (destinationSanitized.endsWith("/")) {
        fileRelativeDestinationBuilder.append(file.filename)
      }
    }

    azureStorageBlobServiceClient
        .getBlobContainerClient(organizationId.sanitizeForAzureStorage())
        .getBlobClient("${workspaceId.sanitizeForAzureStorage()}/$fileRelativeDestinationBuilder")
        .upload(file.inputStream, file.contentLength(), overwrite)
    return WorkspaceFile(fileName = fileRelativeDestinationBuilder.toString())
  }

  override fun findAllWorkspaceFiles(
      organizationId: String,
      workspaceId: String
  ): List<WorkspaceFile> {
    val workspace = getVerifiedWorkspace(organizationId, workspaceId)

    logger.debug("List all files for workspace #{} ({})", workspace.id, workspace.name)
    return getWorkspaceFileResources(organizationId, workspaceId)
        .mapNotNull { it.filename?.removePrefix("${workspaceId.sanitizeForAzureStorage()}/") }
        .map { WorkspaceFile(fileName = it) }
  }

  override fun createSecret(
      organizationId: String,
      workspaceId: String,
      workspaceSecret: WorkspaceSecret
  ) {
    val workspaceKey = getVerifiedWorkspace(organizationId, workspaceId).key
    secretManager.createOrReplaceSecret(
        csmPlatformProperties.namespace,
        getWorkspaceSecretName(organizationId, workspaceKey),
        mapOf(WORKSPACE_EVENTHUB_ACCESSKEY_SECRET to (workspaceSecret.dedicatedEventHubKey ?: "")))
  }

  @EventListener(DeleteHistoricalDataOrganization::class)
  fun deleteHistoricalDataWorkspace(data: DeleteHistoricalDataOrganization) {
    val organizationId = data.organizationId
    var pageable = Pageable.ofSize(csmPlatformProperties.twincache.workspace.defaultPageSize)
    val workspaceList = mutableListOf<Workspace>()

    do {
      val workspaces = findAllWorkspaces(organizationId, pageable.pageNumber, pageable.pageSize)
      workspaceList.addAll(workspaces)
      pageable = pageable.next()
    } while (workspaces.isNotEmpty())

    workspaceList.forEach { sendDeleteHistoricalDataWorkspaceEvent(organizationId, it, data) }
  }

  @EventListener(OrganizationUnregistered::class)
  @Async("csm-in-process-event-executor")
  fun onOrganizationUnregistered(organizationUnregistered: OrganizationUnregistered) {
    val organizationId = organizationUnregistered.organizationId
    try {
      azureStorageBlobServiceClient.deleteBlobContainer(organizationId)
    } finally {
      val pageable: Pageable =
          Pageable.ofSize(csmPlatformProperties.twincache.workspace.defaultPageSize)
      val workspaces = workspaceRepository.findByOrganizationId(organizationId, pageable).toList()
      workspaces.forEach {
        workspaceRepository.delete(it)
        if (csmPlatformProperties.internalResultServices?.enabled == true) {
          this.eventPublisher.publishEvent(WorkspaceDeleted(this, organizationId, it.id!!))
        }
      }
    }
  }

  override fun linkDataset(
      organizationId: String,
      workspaceId: String,
      datasetId: String
  ): Workspace {
    organizationService.getVerifiedOrganization(organizationId)
    sendAddWorkspaceToDatasetEvent(organizationId, datasetId, workspaceId)
    return checkReadSecurity(
        addDatasetToLinkedDatasetIdList(organizationId, workspaceId, datasetId))
  }

  @EventListener(AddDatasetToWorkspace::class)
  fun processEventAddDatasetToWorkspace(addDatasetToWorkspace: AddDatasetToWorkspace) {
    addDatasetToLinkedDatasetIdList(
        addDatasetToWorkspace.organizationId,
        addDatasetToWorkspace.workspaceId,
        addDatasetToWorkspace.datasetId)
  }

  override fun unlinkDataset(
      organizationId: String,
      workspaceId: String,
      datasetId: String
  ): Workspace {
    sendRemoveWorkspaceFromDatasetEvent(organizationId, datasetId, workspaceId)
    return checkReadSecurity(
        removeDatasetFromLinkedDatasetIdList(organizationId, workspaceId, datasetId))
  }

  @EventListener(RemoveDatasetFromWorkspace::class)
  fun processEventRemoveDatasetFromWorkspace(
      removeDatasetFromWorkspace: RemoveDatasetFromWorkspace
  ) {
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

  private fun getWorkspaceFileResources(
      organizationId: String,
      workspaceId: String
  ): List<Resource> {
    findWorkspaceById(organizationId, workspaceId)
    return azureStorageBlobProtocolResolver
        .getResources("azure-blob://$organizationId/$workspaceId/**/*".sanitizeForAzureStorage())
        .map { it as Resource }
  }

  override fun getWorkspacePermissions(
      organizationId: String,
      workspaceId: String,
      role: String
  ): List<String> {
    getVerifiedWorkspace(organizationId, workspaceId, PERMISSION_READ_SECURITY)
    return getPermissions(role, getCommonRolesDefinition())
  }

  override fun getWorkspaceSecurity(
      organizationId: String,
      workspaceId: String
  ): WorkspaceSecurity {
    val workspace = getVerifiedWorkspace(organizationId, workspaceId, PERMISSION_READ_SECURITY)
    return workspace.security
        ?: throw CsmResourceNotFoundException("RBAC not defined for ${workspace.id}")
  }

  override fun setWorkspaceDefaultSecurity(
      organizationId: String,
      workspaceId: String,
      workspaceRole: WorkspaceRole
  ): WorkspaceSecurity {
    val workspace = getVerifiedWorkspace(organizationId, workspaceId, PERMISSION_WRITE_SECURITY)
    val rbacSecurity = csmRbac.setDefault(workspace.getRbac(), workspaceRole.role)
    workspace.setRbac(rbacSecurity)
    workspaceRepository.save(workspace)
    return workspace.security as WorkspaceSecurity
  }

  override fun getWorkspaceAccessControl(
      organizationId: String,
      workspaceId: String,
      identityId: String
  ): WorkspaceAccessControl {
    val workspace = getVerifiedWorkspace(organizationId, workspaceId, PERMISSION_READ_SECURITY)
    val rbacAccessControl = csmRbac.getAccessControl(workspace.getRbac(), identityId)
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
    csmRbac.verify(workspace.getRbac(), requiredPermission)
    return checkReadSecurity(workspace)
  }

  override fun addWorkspaceAccessControl(
      organizationId: String,
      workspaceId: String,
      workspaceAccessControl: WorkspaceAccessControl
  ): WorkspaceAccessControl {
    val organization = organizationService.getVerifiedOrganization(organizationId)
    val workspace = getVerifiedWorkspace(organizationId, workspaceId, PERMISSION_WRITE_SECURITY)
    val users = csmRbac.getUsers(workspace.getRbac())
    if (users.contains(workspaceAccessControl.id)) {
      throw IllegalArgumentException("User is already in this Workspace security")
    }

    val rbacSecurity =
        csmRbac.addUserRole(
            organization.getRbac(),
            workspace.getRbac(),
            workspaceAccessControl.id,
            workspaceAccessControl.role)
    workspace.setRbac(rbacSecurity)
    workspaceRepository.save(workspace)
    val rbacAccessControl = csmRbac.getAccessControl(workspace.getRbac(), workspaceAccessControl.id)
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
        workspace.getRbac(), identityId, "User '$identityId' not found in workspace $workspaceId")
    val rbacSecurity = csmRbac.setUserRole(workspace.getRbac(), identityId, workspaceRole.role)
    workspace.setRbac(rbacSecurity)
    workspaceRepository.save(workspace)
    val rbacAccessControl = csmRbac.getAccessControl(workspace.getRbac(), identityId)
    return WorkspaceAccessControl(rbacAccessControl.id, rbacAccessControl.role)
  }

  override fun removeWorkspaceAccessControl(
      organizationId: String,
      workspaceId: String,
      identityId: String
  ) {
    val workspace = getVerifiedWorkspace(organizationId, workspaceId, PERMISSION_WRITE_SECURITY)
    val rbacSecurity = csmRbac.removeUser(workspace.getRbac(), identityId)
    workspace.setRbac(rbacSecurity)
    workspaceRepository.save(workspace)
  }

  override fun getWorkspaceSecurityUsers(
      organizationId: String,
      workspaceId: String
  ): List<String> {
    val workspace = getVerifiedWorkspace(organizationId, workspaceId, PERMISSION_READ_SECURITY)
    return csmRbac.getUsers(workspace.getRbac())
  }

  private fun addDatasetToLinkedDatasetIdList(
      organizationId: String,
      workspaceId: String,
      datasetId: String
  ): Workspace {
    val workspace = findWorkspaceById(organizationId, workspaceId)

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
        DeleteHistoricalDataWorkspace(this, organizationId, it.id!!, data.deleteUnknown))
  }

  fun checkReadSecurity(workspace: Workspace): Workspace {
    val username = getCurrentAuthenticatedUserName(csmPlatformProperties)
    var userAC = WorkspaceAccessControl("", "")
    val retrievedAC = workspace.security!!.accessControlList.filter { it.id == username }
    if (retrievedAC.isNotEmpty()) userAC = retrievedAC[0]
    val safeWorkspace =
        workspace.copy(
            security =
                WorkspaceSecurity(
                    default = workspace.security!!.default,
                    accessControlList = mutableListOf(userAC)))
    return safeWorkspace
  }
}

fun Workspace.getRbac(): RbacSecurity {
  return RbacSecurity(
      this.id,
      this.security?.default ?: ROLE_NONE,
      this.security?.accessControlList?.map { RbacAccessControl(it.id, it.role) }?.toMutableList()
          ?: mutableListOf())
}

fun Workspace.setRbac(rbacSecurity: RbacSecurity) {
  this.security =
      WorkspaceSecurity(
          rbacSecurity.default,
          rbacSecurity.accessControlList
              .map { WorkspaceAccessControl(it.id, it.role) }
              .toMutableList())
}
