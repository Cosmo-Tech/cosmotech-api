// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.workspace.azure

import com.azure.cosmos.models.CosmosContainerProperties
import com.azure.spring.autoconfigure.storage.resource.AzureStorageResourcePatternResolver
import com.azure.spring.autoconfigure.storage.resource.BlobStorageResource
import com.azure.storage.blob.BlobServiceClient
import com.azure.storage.blob.batch.BlobBatchClient
import com.azure.storage.blob.models.DeleteSnapshotsOptionType
import com.cosmotech.api.azure.CsmAzureService
import com.cosmotech.api.azure.findAll
import com.cosmotech.api.azure.findByIdOrThrow
import com.cosmotech.api.azure.sanitizeForAzureStorage
import com.cosmotech.api.events.OrganizationRegistered
import com.cosmotech.api.events.OrganizationUnregistered
import com.cosmotech.api.rbac.COMMON_PERMISSION_ADMIN
import com.cosmotech.api.rbac.COMMON_PERMISSION_DELETE
import com.cosmotech.api.rbac.COMMON_PERMISSION_READ
import com.cosmotech.api.rbac.COMMON_PERMISSION_WRITE
import com.cosmotech.api.utils.changed
import com.cosmotech.api.utils.compareToAndMutateIfNeeded
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.workspace.api.WorkspaceApiService
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceAccessControl
import com.cosmotech.workspace.domain.WorkspaceAccessControlWithPermissions
import com.cosmotech.workspace.domain.WorkspaceFile
import com.cosmotech.workspace.domain.WorkspaceRoleItems
import com.cosmotech.workspace.domain.WorkspaceSecurity
import com.cosmotech.workspace.domain.WorkspaceSecurityUsers
import com.cosmotech.workspace.rbac.RbacConfiguration
import com.cosmotech.workspace.rbac.rbac
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Scope
import org.springframework.context.event.EventListener
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@ConditionalOnProperty(name = ["csm.platform.vendor"], havingValue = "azure", matchIfMissing = true)
@Suppress("TooManyFunctions")
internal class WorkspaceServiceImpl(
    private val resourceLoader: ResourceLoader,
    private val solutionService: SolutionApiService,
    private val azureStorageBlobServiceClient: BlobServiceClient,
    private val azureStorageBlobBatchClient: BlobBatchClient,
    @Qualifier("Workspace") private val rbacConfiguration: RbacConfiguration,
) : CsmAzureService(), WorkspaceApiService {

  // where ARRAY_CONTAINS(c.security.accessControlList, { id: "vincent.carluer@cosmotech.com" },
  // true)
  override fun findAllWorkspaces(organizationId: String) =
      cosmosTemplate.findAll<Workspace>("${organizationId}_workspaces")

  internal fun findWorkspaceByIdNoSecurity(organizationId: String, workspaceId: String): Workspace =
      cosmosTemplate.findByIdOrThrow(
          "${organizationId}_workspaces",
          workspaceId,
          "Workspace $workspaceId not found in organization $organizationId")

  override fun findWorkspaceById(organizationId: String, workspaceId: String): Workspace {
    val workspace: Workspace = this.findWorkspaceByIdNoSecurity(organizationId, workspaceId)
    rbac(this.rbacConfiguration, workspace, COMMON_PERMISSION_READ)
    return workspace
  }

  override fun createWorkspace(organizationId: String, workspace: Workspace): Workspace {
    // Needs security on Organization to check RBAC
    // Validate Solution ID
    workspace.solution.solutionId?.let { solutionService.findSolutionById(organizationId, it) }
    return cosmosTemplate.insert(
        "${organizationId}_workspaces",
        workspace.copy(
            id = idGenerator.generate("workspace"), ownerId = getCurrentAuthenticatedUserName()))
        ?: throw IllegalArgumentException("No Workspace returned in response: $workspace")
  }

  override fun deleteAllWorkspaceFiles(organizationId: String, workspaceId: String) {
    val workspace = findWorkspaceById(organizationId, workspaceId)
    rbac(this.rbacConfiguration, workspace, COMMON_PERMISSION_READ)
    logger.debug("Deleting all files for workspace #{} ({})", workspace.id, workspace.name)

    GlobalScope.launch {
      // TODO Consider using a smaller coroutine scope
      val workspaceFiles =
          getWorkspaceFileResources(organizationId, workspaceId).map { it.url }.map {
            it.toExternalForm()
          }
      if (workspaceFiles.isEmpty()) {
        logger.debug("No file to delete for workspace $workspaceId")
      } else {
        azureStorageBlobBatchClient.deleteBlobs(workspaceFiles, DeleteSnapshotsOptionType.INCLUDE)
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
    val existingWorkspace = findWorkspaceByIdNoSecurity(organizationId, workspaceId)
    val rbacObj = rbac(this.rbacConfiguration, existingWorkspace, COMMON_PERMISSION_WRITE, true)
    if (rbacObj.newSecurity) {
      logger.warn("Updating workspace ${workspaceId} without any ACL security set")
    }

    // Security cannot be changed by updateWorkspace
    var hasChanged =
        existingWorkspace
            .compareToAndMutateIfNeeded(workspace, excludedFields = arrayOf("security", "solution"))
            .isNotEmpty()

    val securityChanged = workspace.changed(existingWorkspace) { security }
    if ((rbacObj.check(COMMON_PERMISSION_ADMIN) || rbacObj.newSecurity) && securityChanged) {
      logger.debug("Writing new security information for workspace ${workspace.id}")
      // handle new and deleted users for permission propagation
      existingWorkspace.security = workspace.security
      hasChanged = true
    } else {
      if (securityChanged)
          logger.warn(
              "workspace ${workspace.id} security cannot be changed due to missing permission")
    }

    if (workspace.solution.solutionId != null) {
      // Validate solution ID
      workspace.solution.solutionId?.let { solutionService.findSolutionById(organizationId, it) }
      existingWorkspace.solution = workspace.solution
      hasChanged = true
    }

    return if (hasChanged) {
      val responseEntity =
          cosmosTemplate.upsertAndReturnEntity("${organizationId}_workspaces", existingWorkspace)
      responseEntity
    } else {
      existingWorkspace
    }
  }

  override fun deleteWorkspace(organizationId: String, workspaceId: String): Workspace {
    val workspace = findWorkspaceById(organizationId, workspaceId)
    rbac(this.rbacConfiguration, workspace, COMMON_PERMISSION_DELETE)
    try {
      deleteAllWorkspaceFiles(organizationId, workspaceId)
    } finally {
      cosmosTemplate.deleteEntity("${organizationId}_workspaces", workspace)
    }
    return workspace
  }

  override fun deleteWorkspaceFile(organizationId: String, workspaceId: String, fileName: String) {
    val workspace = findWorkspaceById(organizationId, workspaceId)
    rbac(this.rbacConfiguration, workspace, COMMON_PERMISSION_READ)
    logger.debug(
        "Deleting file resource from workspace #{} ({}): {}",
        workspace.id,
        workspace.name,
        fileName)
    azureStorageBlobServiceClient
        .getBlobContainerClient(organizationId.sanitizeForAzureStorage())
        .getBlobClient("${workspaceId.sanitizeForAzureStorage()}/${fileName}")
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
    val workspace = findWorkspaceById(organizationId, workspaceId)
    rbac(this.rbacConfiguration, workspace, COMMON_PERMISSION_READ)
    logger.debug(
        "Downloading file resource to workspace #{} ({}): {}",
        workspace.id,
        workspace.name,
        fileName)
    return resourceLoader.getResource(
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

    val workspace = findWorkspaceById(organizationId, workspaceId)
    logger.debug(
        "Uploading file resource to workspace #{} ({}): {} => {}",
        workspace.id,
        workspace.name,
        file.filename,
        destination)

    // Create a new custom permission for file upload?
    rbac(this.rbacConfiguration, workspace, COMMON_PERMISSION_READ)

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
    val workspace = findWorkspaceById(organizationId, workspaceId)
    rbac(this.rbacConfiguration, workspace, COMMON_PERMISSION_READ)
    logger.debug("List all files for workspace #{} ({})", workspace.id, workspace.name)
    return getWorkspaceFileResources(organizationId, workspaceId)
        .mapNotNull { it.filename?.removePrefix("${workspaceId.sanitizeForAzureStorage()}/") }
        .map { WorkspaceFile(fileName = it) }
  }

  @EventListener(OrganizationRegistered::class)
  fun onOrganizationRegistered(organizationRegistered: OrganizationRegistered) {
    cosmosCoreDatabase.createContainerIfNotExists(
        CosmosContainerProperties("${organizationRegistered.organizationId}_workspaces", "/id"))
  }

  @EventListener(OrganizationUnregistered::class)
  @Async("csm-in-process-event-executor")
  fun onOrganizationUnregistered(organizationUnregistered: OrganizationUnregistered) {
    val organizationId = organizationUnregistered.organizationId
    try {
      azureStorageBlobServiceClient.deleteBlobContainer(organizationId)
    } finally {
      cosmosTemplate.deleteContainer("${organizationId}_workspaces")
    }
  }

  private fun getWorkspaceFileResources(
      organizationId: String,
      workspaceId: String
  ): List<BlobStorageResource> {
    val workspace = findWorkspaceById(organizationId, workspaceId)
    rbac(this.rbacConfiguration, workspace, COMMON_PERMISSION_READ)
    return AzureStorageResourcePatternResolver(azureStorageBlobServiceClient)
        .getResources("azure-blob://$organizationId/$workspaceId/**/*".sanitizeForAzureStorage())
        .map { it as BlobStorageResource }
  }

  override fun addWorkspaceAccess(
      organizationId: kotlin.String,
      workspaceId: kotlin.String,
      workspaceAccessControl: WorkspaceAccessControl
  ): WorkspaceAccessControlWithPermissions {
    val workspace = findWorkspaceByIdNoSecurity(organizationId, workspaceId)
    val rbacObj = rbac(this.rbacConfiguration, workspace, COMMON_PERMISSION_ADMIN, true)

    // add restricted list from organization. Add reader permission to solution
    rbacObj.setWorkspaceAccess(workspaceAccessControl)
    rbacObj.update(workspace)
    this.updateWorkspace(organizationId, workspaceId, workspace)
    return rbacObj.getWorkspaceAccessControlWithPermissions(workspaceAccessControl.id)
  }

  override fun getWorkspaceAccess(
      organizationId: kotlin.String,
      workspaceId: kotlin.String,
      identityId: kotlin.String
  ): WorkspaceAccessControlWithPermissions {
    val workspace = findWorkspaceById(organizationId, workspaceId)
    val rbacObj = rbac(this.rbacConfiguration, workspace, COMMON_PERMISSION_READ)
    return rbacObj.getWorkspaceAccessControlWithPermissions(identityId)
  }

  override fun getWorkspaceSecurity(
      organizationId: kotlin.String,
      workspaceId: kotlin.String
  ): WorkspaceSecurity {
    val workspace = findWorkspaceById(organizationId, workspaceId)
    rbac(this.rbacConfiguration, workspace, COMMON_PERMISSION_READ)
    return workspace.security ?: WorkspaceSecurity()
  }

  override fun removeWorkspaceAccess(
      organizationId: kotlin.String,
      workspaceId: kotlin.String,
      identityId: kotlin.String
  ): Unit {
    val workspace = findWorkspaceById(organizationId, workspaceId)
    val rbacObj = rbac(this.rbacConfiguration, workspace, COMMON_PERMISSION_ADMIN)
    rbacObj.removeUser(identityId)
    // propagate removal to scenario, solution?
    rbacObj.update(workspace)
    this.updateWorkspace(organizationId, workspaceId, workspace)
  }

  override fun setWorkspaceDefaultSecurity(
      organizationId: kotlin.String,
      workspaceId: kotlin.String,
      workspaceRoleItems: WorkspaceRoleItems
  ): WorkspaceSecurity {
    val workspace = findWorkspaceByIdNoSecurity(organizationId, workspaceId)

    val rbacObj = rbac(this.rbacConfiguration, workspace, COMMON_PERMISSION_ADMIN, true)
    rbacObj.setDefault(workspaceRoleItems)
    rbacObj.update(workspace)

    this.updateWorkspace(organizationId, workspaceId, workspace)

    return workspace.security ?: WorkspaceSecurity()
  }

  override fun getWorkspaceSecurityUsers(
      organizationId: kotlin.String,
      workspaceId: kotlin.String
  ): WorkspaceSecurityUsers {
    val workspace = findWorkspaceById(organizationId, workspaceId)

    val rbacObj = rbac(this.rbacConfiguration, workspace, COMMON_PERMISSION_READ)
    return rbacObj.getWorkspaceUsers()
  }
}
