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
import com.cosmotech.api.events.DeleteHistoricalDataOrganization
import com.cosmotech.api.events.DeleteHistoricalDataWorkspace
import com.cosmotech.api.events.OrganizationRegistered
import com.cosmotech.api.events.OrganizationUnregistered
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
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
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.event.EventListener
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["csm.platform.vendor"], havingValue = "azure", matchIfMissing = true)
@Suppress("TooManyFunctions")
internal class WorkspaceServiceImpl(
    private val resourceLoader: ResourceLoader,
    private val solutionService: SolutionApiService,
    private val azureStorageBlobServiceClient: BlobServiceClient,
    private val azureStorageBlobBatchClient: BlobBatchClient,
) : CsmAzureService(), WorkspaceApiService {

  override fun findAllWorkspaces(organizationId: String) =
      cosmosTemplate.findAll<Workspace>("${organizationId}_workspaces")

  override fun findWorkspaceById(organizationId: String, workspaceId: String): Workspace =
      cosmosTemplate.findByIdOrThrow(
          "${organizationId}_workspaces",
          workspaceId,
          "Workspace $workspaceId not found in organization $organizationId")

  override fun createWorkspace(organizationId: String, workspace: Workspace): Workspace {
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
    val existingWorkspace = findWorkspaceById(organizationId, workspaceId)

    var hasChanged =
        existingWorkspace
            .compareToAndMutateIfNeeded(workspace, excludedFields = arrayOf("ownerId", "solution"))
            .isNotEmpty()

    if (workspace.ownerId != null && workspace.changed(existingWorkspace) { ownerId }) {
      // Allow to change the ownerId as well, but only the owner can transfer the ownership
      if (existingWorkspace.ownerId != getCurrentAuthenticatedUserName()) {
        // TODO Only the owner or an admin should be able to perform this operation
        throw CsmAccessForbiddenException(
            "You are not allowed to change the ownership of this Resource")
      }
      existingWorkspace.ownerId = workspace.ownerId
      hasChanged = true
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
    if (workspace.ownerId != getCurrentAuthenticatedUserName()) {
      // TODO Only the owner or an admin should be able to perform this operation
      throw CsmAccessForbiddenException("You are not allowed to delete this Resource")
    }
    try {
      deleteAllWorkspaceFiles(organizationId, workspaceId)
    } finally {
      cosmosTemplate.deleteEntity("${organizationId}_workspaces", workspace)
    }
    return workspace
  }

  override fun deleteWorkspaceFile(organizationId: String, workspaceId: String, fileName: String) {
    val workspace = findWorkspaceById(organizationId, workspaceId)
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
    logger.debug("List all files for workspace #{} ({})", workspace.id, workspace.name)
    return getWorkspaceFileResources(organizationId, workspaceId)
        .mapNotNull { it.filename?.removePrefix("${workspaceId.sanitizeForAzureStorage()}/") }
        .map { WorkspaceFile(fileName = it) }
  }

  @EventListener(DeleteHistoricalDataOrganization::class)
  fun deleteHistoricalDataWorkspace(data: DeleteHistoricalDataOrganization) {
    val organizationId = data.organizationId
    val workspaces: List<Workspace> = findAllWorkspaces(organizationId)
    for (workspace in workspaces) {
      this.eventPublisher.publishEvent(
          DeleteHistoricalDataWorkspace(this, organizationId, workspace.id!!))
    }
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
  ): List<BlobStorageResource> =
      AzureStorageResourcePatternResolver(azureStorageBlobServiceClient)
          .getResources("azure-blob://$organizationId/$workspaceId/**/*".sanitizeForAzureStorage())
          .map { it as BlobStorageResource }

  override fun addWorkspaceAccess(
      organizationId: kotlin.String,
      workspaceId: kotlin.String,
      workspaceAccessControl: WorkspaceAccessControl
  ): WorkspaceAccessControlWithPermissions {
    val workspace = findWorkspaceById(organizationId, workspaceId)
    this.initSecurity(workspace)

    workspace.security?.accessControlList?.put(workspaceAccessControl.id, workspaceAccessControl)
    this.updateWorkspace(organizationId, workspaceId, workspace)

    val accessControlWithPermissions: WorkspaceAccessControlWithPermissions =
        WorkspaceAccessControlWithPermissions(
          workspaceAccessControl.id,
          workspaceAccessControl.roles,
          permissions = mutableListOf("HardCodedReader")
        )
    return accessControlWithPermissions
  }

  override fun getWorkspaceAccess(
      organizationId: kotlin.String,
      workspaceId: kotlin.String,
      identityId: kotlin.String
  ): WorkspaceAccessControlWithPermissions {
    val workspace = findWorkspaceById(organizationId, workspaceId)
    val acl =
        workspace.security?.accessControlList?.get(identityId)?.copy() as
            WorkspaceAccessControlWithPermissions

    return acl
        ?: throw CsmResourceNotFoundException(
            "The requested control access for $identityId does not exist")
  }

  override fun getWorkspaceSecurity(
      organizationId: kotlin.String,
      workspaceId: kotlin.String
  ): WorkspaceSecurity {
    val workspace = findWorkspaceById(organizationId, workspaceId)
    return workspace.security ?: WorkspaceSecurity()
  }

  override fun removeWorkspaceAccess(
      organizationId: kotlin.String,
      workspaceId: kotlin.String,
      identityId: kotlin.String
  ): Unit {
    val workspace = findWorkspaceById(organizationId, workspaceId)
    val acl = workspace.security?.accessControlList?.get(identityId)
    if (acl == null) {
      throw CsmResourceNotFoundException(
          "The requested control access for $identityId does not exist")
    }
    workspace.security?.accessControlList?.remove(identityId)
  }

  override fun setWorkspaceDefaultSecurity(
      organizationId: kotlin.String,
      workspaceId: kotlin.String,
      workspaceRoleItems: WorkspaceRoleItems
  ): WorkspaceSecurity {
    val workspace = findWorkspaceById(organizationId, workspaceId)
    this.initSecurity(workspace)

    workspace.security?.default = workspaceRoleItems.roles
    this.updateWorkspace(organizationId, workspaceId, workspace)

    return workspace.security ?: WorkspaceSecurity()
  }

  private fun initSecurity(workspace: Workspace) {
    if (workspace.security == null) {
      logger.debug("Creating workspace security since it does not exist yet")
      workspace.security = WorkspaceSecurity()
      workspace.security?.accessControlList = mutableMapOf()
    }
  }
}
