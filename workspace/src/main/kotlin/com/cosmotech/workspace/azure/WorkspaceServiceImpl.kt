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
import com.cosmotech.api.azure.AbstractCosmosBackedService
import com.cosmotech.api.azure.findAll
import com.cosmotech.api.azure.findByIdOrThrow
import com.cosmotech.api.azure.sanitizeForAzureStorage
import com.cosmotech.api.events.OrganizationRegistered
import com.cosmotech.api.events.OrganizationUnregistered
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.utils.changed
import com.cosmotech.api.utils.compareToAndMutateIfNeeded
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.api.utils.getCurrentAuthenticatedUserRoles
import com.cosmotech.api.utils.getCurrentAuthenticatedUserUPN
import com.cosmotech.api.utils.toDomain
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.workspace.api.WorkspaceApiService
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceFile
import com.fasterxml.jackson.databind.JsonNode
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
) : AbstractCosmosBackedService(), WorkspaceApiService {

  private fun validateUser(workspace: Workspace) {
    // Owner is always a valid user
    if (workspace.ownerId == getCurrentAuthenticatedUserName()) {
      logger.debug(
          "Owner {} is authorized on Workspace {} - {}",
          workspace.ownerId,
          workspace.name,
          workspace.id)
      return
    }
    // User mail must be the same as upn attribute in token
    val currentUserUPN = getCurrentAuthenticatedUserUPN()
    logger.debug("Validating user with UPN: {}", currentUserUPN)
    val workspaceUsers = workspace.users
    if (workspaceUsers != null &&
        workspaceUsers.isNotEmpty() &&
        !workspaceUsers.contains(currentUserUPN))
        throw CsmAccessForbiddenException("You are not allowed to access this workspace")
    if (workspaceUsers == null || workspaceUsers.isEmpty()) {
      logger.warn("No users list set on Workspace: {} - {}", workspace.name, workspace.id)
    } else {
      logger.debug(
          "User {} is authorized on Workspace {} - {}",
          currentUserUPN,
          workspace.name,
          workspace.id)
    }
  }

  override fun findAllWorkspaces(organizationId: String): List<Workspace> {
    val roles = getCurrentAuthenticatedUserRoles()
    if ("Platform.Admin" in roles) {
      return cosmosTemplate.findAll<Workspace>("${organizationId}_workspaces")
    }

    val userName = getCurrentAuthenticatedUserName()
    val userUPN = getCurrentAuthenticatedUserUPN()
    return cosmosCoreDatabase
        .getContainer("${organizationId}_workspaces")
        .queryItems(
            SqlQuerySpec(
                "SELECT * FROM c where c.ownerId = @USER_ID or c.users = [] or c.users = null " +
                    "or ARRAY_CONTAINS(c.users, @USER_MAIL)",
                listOf(SqlParameter("@USER_ID", userName), SqlParameter("@USER_MAIL", userUPN))),
            CosmosQueryRequestOptions(),
            // It would be much better to specify the Domain Type right away and
            // avoid the map operation, but we can't due
            // to the lack of customization of the Cosmos Client Object Mapper, as reported here
            // :
            // https://github.com/Azure/azure-sdk-for-java/issues/12269
            JsonNode::class.java)
        .mapNotNull { it.toDomain<Workspace>() }
  }

  override fun findWorkspaceById(organizationId: String, workspaceId: String): Workspace {
    val workspace: Workspace =
        cosmosTemplate.findByIdOrThrow(
            "${organizationId}_workspaces",
            workspaceId,
            "Workspace $workspaceId not found in organization $organizationId")
    this.validateUser(workspace)
    return workspace
  }

  override fun removeAllUsersOfWorkspace(organizationId: String, workspaceId: String) {
    val workspace = findWorkspaceById(organizationId, workspaceId)
    if (workspace.ownerId != getCurrentAuthenticatedUserName()) {
      // TODO Only the owner or an admin should be able to perform this operation
      throw CsmAccessForbiddenException("You are not allowed to update this Resource")
    }
    if (!workspace.users.isNullOrEmpty()) {
      workspace.users = listOf()
      cosmosTemplate.upsert("${organizationId}_workspaces", workspace)
    }
  }

  override fun removeUserFromWorkspace(
      organizationId: String,
      workspaceId: String,
      userMail: String
  ) {
    val workspace = findWorkspaceById(organizationId, workspaceId)
    if (workspace.ownerId != getCurrentAuthenticatedUserName()) {
      // TODO Only the owner or an admin should be able to perform this operation
      throw CsmAccessForbiddenException("You are not allowed to update this Resource")
    }
    workspace.users = workspace.users?.filter { it != userMail }

    cosmosTemplate.upsert("${organizationId}_workspaces", workspace)
  }

  override fun addUsersInWorkspace(
      organizationId: kotlin.String,
      workspaceId: kotlin.String,
      requestBody: kotlin.collections.List<kotlin.String>
  ): List<kotlin.String> {
    val workspace = findWorkspaceById(organizationId, workspaceId)
    if (workspace.ownerId != getCurrentAuthenticatedUserName()) {
      // TODO Only the owner or an admin should be able to perform this operation
      throw CsmAccessForbiddenException("You are not allowed to update this Resource")
    }

    if (requestBody.isEmpty()) {
      // Nothing to do
      return workspace.users ?: listOf()
    }

    val userList = requestBody.toMutableList()
    userList.addAll(workspace.users ?: listOf())
    workspace.users = userList.toSet().toList()

    cosmosTemplate.upsert("${organizationId}_workspaces", workspace)

    return workspace.users ?: listOf()
  }

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
    if (existingWorkspace.ownerId != getCurrentAuthenticatedUserName()) {
      // TODO Only the owner or an admin should be able to perform this operation
      throw CsmAccessForbiddenException("You are not allowed to update this Resource")
    }

    var hasChanged =
        existingWorkspace
            .compareToAndMutateIfNeeded(
                workspace, excludedFields = arrayOf("ownerId", "users", "solution"))
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

    if (workspace.solution != null) {
      // Validate solution ID
      workspace.solution.solutionId?.let { solutionService.findSolutionById(organizationId, it) }
      existingWorkspace.solution = workspace.solution
      hasChanged = true
    }

    return if (hasChanged) {
      cosmosTemplate.upsertAndReturnEntity("${organizationId}_workspaces", existingWorkspace)
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
    if (fileName.contains("..")) {
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
}
