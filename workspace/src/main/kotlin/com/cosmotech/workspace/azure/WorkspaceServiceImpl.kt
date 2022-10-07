// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.workspace.azure

import com.azure.cosmos.models.CosmosContainerProperties
import com.azure.cosmos.models.CosmosQueryRequestOptions
import com.azure.cosmos.models.SqlParameter
import com.azure.cosmos.models.SqlQuerySpec
import com.azure.spring.autoconfigure.storage.resource.AzureStorageResourcePatternResolver
import com.azure.spring.autoconfigure.storage.resource.BlobStorageResource
import com.azure.storage.blob.BlobServiceClient
import com.azure.storage.blob.batch.BlobBatchClient
import com.azure.storage.blob.models.DeleteSnapshotsOptionType
import com.cosmotech.api.azure.CsmAzureService
import com.cosmotech.api.azure.findByIdOrThrow
import com.cosmotech.api.azure.sanitizeForAzureStorage
import com.cosmotech.api.events.DeleteHistoricalDataOrganization
import com.cosmotech.api.events.DeleteHistoricalDataWorkspace
import com.cosmotech.api.events.OrganizationRegistered
import com.cosmotech.api.events.OrganizationUnregistered
import com.cosmotech.api.rbac.CsmRbac
import com.cosmotech.api.rbac.PERMISSION_CREATE_CHILDREN
import com.cosmotech.api.rbac.PERMISSION_WRITE
import com.cosmotech.api.rbac.PERMISSION_WRITE_SECURITY
import com.cosmotech.api.rbac.PERMISSION_READ
import com.cosmotech.api.rbac.PERMISSION_READ_SECURITY
import com.cosmotech.api.rbac.PERMISSION_DELETE
import com.cosmotech.api.rbac.ROLE_ADMIN
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.rbac.getCommonRolesDefinition
import com.cosmotech.api.rbac.getPermissions
import com.cosmotech.api.rbac.model.RbacAccessControl
import com.cosmotech.api.utils.changed
import com.cosmotech.api.utils.compareToAndMutateIfNeeded
import com.cosmotech.api.utils.getCurrentAuthenticatedMail
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.api.utils.toDomain
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.workspace.api.WorkspaceApiService
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceAccessControl
import com.cosmotech.workspace.domain.WorkspaceFile
import com.cosmotech.workspace.domain.WorkspaceSecurity
import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
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
    private val organizationService: OrganizationApiService,
    private val solutionService: SolutionApiService,
    private val azureStorageBlobServiceClient: BlobServiceClient,
    private val azureStorageBlobBatchClient: BlobBatchClient,
    private val csmRbac: CsmRbac
) : CsmAzureService(), WorkspaceApiService {

  override fun findAllWorkspaces(organizationId: String): List<Workspace> {
    val currentUser = getCurrentAuthenticatedMail(this.csmPlatformProperties)
    logger.debug("Getting workspaces for user ${currentUser}")
    val templateQuery =
        "SELECT * FROM c " +
            "WHERE ARRAY_CONTAINS(c.security.accessControlList, { id: @ACL_USER}, true)" +
            " OR NOT IS_DEFINED(c.security)" +
            " OR ARRAY_LENGTH(c.security.default) > 0"
    logger.debug("Template query: ${templateQuery}")

    return cosmosCoreDatabase
        .getContainer("${organizationId}_workspaces")
        .queryItems(
            SqlQuerySpec(templateQuery, listOf(SqlParameter("@ACL_USER", currentUser))),
            CosmosQueryRequestOptions(),
            // It would be much better to specify the Domain Type right away and
            // avoid the map operation, but we can't due
            // to the lack of customization of the Cosmos Client Object Mapper, as reported here :
            // https://github.com/Azure/azure-sdk-for-java/issues/12269
            JsonNode::class.java)
        .mapNotNull { it.toDomain<Workspace>() }
        .toList()
  }

  internal fun findWorkspaceByIdNoSecurity(organizationId: String, workspaceId: String): Workspace =
      cosmosTemplate.findByIdOrThrow(
          "${organizationId}_workspaces",
          workspaceId,
          "Workspace $workspaceId not found in organization $organizationId")

  override fun findWorkspaceById(organizationId: String, workspaceId: String): Workspace {
    val workspace: Workspace = this.findWorkspaceByIdNoSecurity(organizationId, workspaceId)
    csmRbac.verify(workspace.security, PERMISSION_READ)
    return workspace
  }

  override fun createWorkspace(organizationId: String, workspace: Workspace): Workspace {
    val organization = organizationService.findOrganizationById(organizationId)
    // Needs security on Organization to check RBAC
    csmRbac.verify(organization.security, PERMISSION_CREATE_CHILDREN)
    // Validate Solution ID
    workspace.solution?.solutionId?.let { solutionService.findSolutionById(organizationId, it) }
    val currentUser = getCurrentAuthenticatedMail(this.csmPlatformProperties)

    return cosmosTemplate.insert(
        "${organizationId}_workspaces",
        workspace.copy(
            id = idGenerator.generate("workspace"),
            ownerId = getCurrentAuthenticatedUserName(),
            security = workspace.security ?: initSecurity(currentUser)))
        ?: throw IllegalArgumentException("No Workspace returned in response: $workspace")
  }

  override fun deleteAllWorkspaceFiles(organizationId: String, workspaceId: String) {
    val workspace = findWorkspaceById(organizationId, workspaceId)
    csmRbac.verify(workspace.security, PERMISSION_WRITE)
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
    val isNoRbac = existingWorkspace.security == null
    csmRbac.verify(existingWorkspace.security, PERMISSION_WRITE)
    // Security cannot be changed by updateWorkspace
    var hasChanged =
        existingWorkspace
            .compareToAndMutateIfNeeded(workspace, excludedFields = arrayOf("security", "solution"))
            .isNotEmpty()

    val securityChanged = workspace.changed(existingWorkspace) { security }
    if ((csmRbac.check(existingWorkspace.security, PERMISSION_WRITE_SECURITY) || isNoRbac) &&
        securityChanged) {
      logger.debug("Writing new security information for workspace ${workspace.id}")
      // handle new and deleted users for permission propagation
      existingWorkspace.security = workspace.security
      hasChanged = true
    } else {
      if (securityChanged)
          logger.warn(
              "workspace ${workspace.id} security cannot be changed due to missing permission")
    }

    if (workspace.solution?.solutionId != null) {
      // Validate solution ID
      workspace.solution?.solutionId?.let { solutionService.findSolutionById(organizationId, it) }
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
    csmRbac.verify(workspace.security, PERMISSION_DELETE)
    try {
      deleteAllWorkspaceFiles(organizationId, workspaceId)
    } finally {
      cosmosTemplate.deleteEntity("${organizationId}_workspaces", workspace)
    }
    return workspace
  }

  override fun deleteWorkspaceFile(organizationId: String, workspaceId: String, fileName: String) {
    val workspace = findWorkspaceById(organizationId, workspaceId)
    csmRbac.verify(workspace.security, PERMISSION_WRITE)
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
    csmRbac.verify(workspace.security, PERMISSION_READ)
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
    csmRbac.verify(workspace.security, PERMISSION_WRITE)
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
    csmRbac.verify(workspace.security, PERMISSION_READ)
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
  ): List<BlobStorageResource> {
    findWorkspaceById(organizationId, workspaceId)
    return AzureStorageResourcePatternResolver(azureStorageBlobServiceClient)
        .getResources("azure-blob://$organizationId/$workspaceId/**/*".sanitizeForAzureStorage())
        .map { it as BlobStorageResource }
  }

  override fun getWorkspacePermissions(
      organizationId: String,
      workspaceId: String,
      role: String
  ): List<String> {
    return getPermissions(role, getCommonRolesDefinition())
  }

  override fun getWorkspaceSecurity(
      organizationId: String,
      workspaceId: String
  ): WorkspaceSecurity {
    val workspace = findWorkspaceById(organizationId, workspaceId)
    csmRbac.verify(workspace.security, PERMISSION_READ_SECURITY)
    return workspace.security as WorkspaceSecurity
  }

  override fun setWorkspaceDefaultSecurity(
      organizationId: String,
      workspaceId: String,
      workspaceRole: String
  ): WorkspaceSecurity {
    val workspace = findWorkspaceByIdNoSecurity(organizationId, workspaceId)
    csmRbac.verify(workspace.security, PERMISSION_WRITE_SECURITY)
    csmRbac.setDefault(workspace.security, workspaceRole)
    this.updateWorkspace(organizationId, workspaceId, workspace)
    return workspace.security as WorkspaceSecurity
  }

  override fun getWorkspaceAccessControl(
      organizationId: String,
      workspaceId: String,
      identityId: String
  ): WorkspaceAccessControl {
    val workspace = findWorkspaceById(organizationId, workspaceId)
    csmRbac.verify(workspace.security, PERMISSION_READ_SECURITY)
    return csmRbac.getAccessControl(workspace.security, identityId) as WorkspaceAccessControl
  }

  override fun addWorkspaceAccessControl(
      organizationId: String,
      workspaceId: String,
      workspaceAccessControl: WorkspaceAccessControl
  ): WorkspaceAccessControl {
    val workspace = findWorkspaceByIdNoSecurity(organizationId, workspaceId)
    csmRbac.verify(workspace.security, PERMISSION_WRITE_SECURITY)
    csmRbac.setUserRole(workspace.security, workspaceAccessControl.id, workspaceAccessControl.role)
    this.updateWorkspace(organizationId, workspaceId, workspace)
    return csmRbac.getAccessControl(workspace.security, workspaceAccessControl.id) as
        WorkspaceAccessControl
  }

  override fun removeWorkspaceAccessControl(
      organizationId: String,
      workspaceId: String,
      identityId: String
  ) {
    val workspace = findWorkspaceById(organizationId, workspaceId)
    csmRbac.verify(workspace.security, PERMISSION_WRITE_SECURITY)
    csmRbac.removeUser(workspace.security, identityId)
    this.updateWorkspace(organizationId, workspaceId, workspace)
  }

  override fun getWorkspaceSecurityUsers(
      organizationId: String,
      workspaceId: String
  ): List<String> {
    val workspace = findWorkspaceById(organizationId, workspaceId)
    csmRbac.verify(workspace.security, PERMISSION_READ_SECURITY)
    return csmRbac.getUsers(workspace.security)
  }

  private fun initSecurity(userId: String): WorkspaceSecurity {
    return WorkspaceSecurity(
        default = ROLE_NONE,
        accessControlList = mutableListOf(RbacAccessControl(userId, ROLE_ADMIN)))
  }
}
