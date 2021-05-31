// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.workspace.azure

import com.azure.cosmos.models.CosmosContainerProperties
import com.azure.spring.autoconfigure.storage.resource.AzureStorageResourcePatternResolver
import com.azure.spring.autoconfigure.storage.resource.BlobStorageResource
import com.azure.storage.blob.BlobServiceClient
import com.azure.storage.blob.batch.BlobBatchClient
import com.azure.storage.blob.models.DeleteSnapshotsOptionType
import com.cosmotech.api.azure.AbstractCosmosBackedService
import com.cosmotech.api.azure.findAll
import com.cosmotech.api.azure.findByIdOrThrow
import com.cosmotech.api.azure.sanitizeForAzureStorage
import com.cosmotech.api.events.*
import com.cosmotech.api.utils.changed
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.user.api.UserApiService
import com.cosmotech.user.domain.User
import com.cosmotech.workspace.api.WorkspaceApiService
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceFile
import com.cosmotech.workspace.domain.WorkspaceUser
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.event.EventListener
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["csm.platform.vendor"], havingValue = "azure", matchIfMissing = true)
class WorkspaceServiceImpl(
    private val resourceLoader: ResourceLoader,
    private val userService: UserApiService,
    private val organizationService: OrganizationApiService,
    private val azureStorageBlobServiceClient: BlobServiceClient,
    private val azureStorageBlobBatchClient: BlobBatchClient,
) : AbstractCosmosBackedService(), WorkspaceApiService {

  private fun fetchUsers(userIds: Collection<String>): Map<String, User> =
      userIds.toSet().map { userService.findUserById(it) }.associateBy { it.id!! }

  override fun findAllWorkspaces(organizationId: String) =
      cosmosTemplate.findAll<Workspace>("${organizationId}_workspaces")

  override fun findWorkspaceById(organizationId: String, workspaceId: String): Workspace =
      cosmosTemplate.findByIdOrThrow(
          "${organizationId}_workspaces",
          workspaceId,
          "Workspace $workspaceId not found in organization $organizationId")

  override fun removeAllUsersOfWorkspace(organizationId: String, workspaceId: String) {
    val workspace = findWorkspaceById(organizationId, workspaceId)
    if (!workspace.users.isNullOrEmpty()) {
      val userIds = workspace.users!!.mapNotNull { it.id }
      workspace.users = listOf()
      cosmosTemplate.upsert("${organizationId}_workspaces", workspace)

      userIds.forEach {
        this.eventPublisher.publishEvent(
            UserRemovedFromWorkspace(this, organizationId, workspaceId, it))
      }
    }
  }

  override fun removeUserFromOrganizationWorkspace(
      organizationId: String,
      workspaceId: String,
      userId: String
  ) {
    val workspace = findWorkspaceById(organizationId, workspaceId)
    val workspaceUserMap =
        workspace.users?.associateBy { it.id!! }?.toMutableMap() ?: mutableMapOf()
    if (workspaceUserMap.containsKey(userId)) {
      workspaceUserMap.remove(userId)
      workspace.users = workspaceUserMap.values.toList()
      cosmosTemplate.upsert("${organizationId}_workspaces", workspace)
      this.eventPublisher.publishEvent(
          UserRemovedFromWorkspace(this, organizationId, workspaceId, userId))
    }
  }

  override fun addOrReplaceUsersInOrganizationWorkspace(
      organizationId: String,
      workspaceId: String,
      workspaceUser: List<WorkspaceUser>
  ): List<WorkspaceUser> {
    if (workspaceUser.isEmpty()) {
      // Nothing to do
      return workspaceUser
    }

    val organization = organizationService.findOrganizationById(organizationId)
    val workspace = findWorkspaceById(organizationId, workspaceId)

    val workspaceUserWithoutNullIds = workspaceUser.filter { it.id != null }
    val newUsersLoaded = fetchUsers(workspaceUserWithoutNullIds.mapNotNull { it.id })
    val workspaceUserWithRightNames =
        workspaceUserWithoutNullIds.map { it.copy(name = newUsersLoaded[it.id]!!.name!!) }
    val workspaceUserMap = workspaceUserWithRightNames.associateBy { it.id!! }

    val currentWorkspaceUsers =
        workspace.users?.filter { it.id != null }?.associateBy { it.id!! }?.toMutableMap()
            ?: mutableMapOf()

    newUsersLoaded.forEach { (userId, _) ->
      // Add or replace
      currentWorkspaceUsers[userId] = workspaceUserMap[userId]!!
    }
    workspace.users = currentWorkspaceUsers.values.toList()

    cosmosTemplate.upsert("${organizationId}_workspaces", workspace)

    // Roles might have changed => notify all users so they can update their own items
    workspace.users?.forEach { user ->
      this.eventPublisher.publishEvent(
          UserAddedToWorkspace(
              this,
              organizationId,
              organization.name!!,
              workspaceId,
              workspace.name,
              user.id!!,
              user.roles.map { role -> role.value }))
    }
    return workspaceUserWithRightNames
  }

  override fun createWorkspace(organizationId: String, workspace: Workspace): Workspace =
      cosmosTemplate.insert(
          "${organizationId}_workspaces", workspace.copy(id = idGenerator.generate("workspace")))
          ?: throw IllegalArgumentException("No Workspace returned in response: $workspace")

  override fun deleteAllWorkspaceFiles(organizationId: String, workspaceId: String) {
    val workspace = findWorkspaceById(organizationId, workspaceId)
    logger.debug("Deleting all files for workspace #{} ({})", workspace.id, workspace.name)

    // TODO This is done synchronously for now, but we should definitely consider sending the
    // response to the client faster.
    // TODO Using a Kotlin Coroutine is a good candidate here (or at least an Async Azure Storage
    // client)

    val workspaceFiles =
        getWorkspaceFileResources(organizationId, workspaceId).map { it.url }.map {
          it.toExternalForm()
        }
    if (workspaceFiles.isEmpty()) {
      logger.debug("No file to delete for workspace $workspaceId")
      return
    }

    azureStorageBlobBatchClient.deleteBlobs(workspaceFiles, DeleteSnapshotsOptionType.INCLUDE)
        .forEach { response ->
          logger.debug(
              "Deleting blob with URL {} completed with status code {}",
              response.request.url,
              response.statusCode)
        }
  }

  override fun updateWorkspace(
      organizationId: String,
      workspaceId: String,
      workspace: Workspace
  ): Workspace {
    val existingWorkspace = findWorkspaceById(organizationId, workspaceId)

    var hasChanged = false
    if (workspace.name != null && workspace.changed(existingWorkspace) { name }) {
      existingWorkspace.name = workspace.name
      hasChanged = true
    }
    if (workspace.description != null && workspace.changed(existingWorkspace) { description }) {
      existingWorkspace.description = workspace.description
      hasChanged = true
    }
    // TODO Allow to change the ownerId as well, but only the owner can transfer the ownership

    var userIdsRemoved: List<String>? = listOf()
    if (workspace.users != null) {
      // Specifying a list of users here overrides the previous list
      val usersToSet = fetchUsers(workspace.users!!.mapNotNull { it.id })
      userIdsRemoved =
          workspace.users?.mapNotNull { it.id }?.filterNot { usersToSet.containsKey(it) }
      val usersWithNames =
          usersToSet.let { workspace.users!!.map { it.copy(name = usersToSet[it.id]!!.name!!) } }
      existingWorkspace.users = usersWithNames
      hasChanged = true
    }

    if (workspace.tags != null && workspace.tags?.toSet() != existingWorkspace.tags?.toSet()) {
      existingWorkspace.tags = workspace.tags
      hasChanged = true
    }
    if (workspace.solution != null) {
      existingWorkspace.solution = workspace.solution
      hasChanged = true
    }
    if (workspace.webApp != null && workspace.changed(existingWorkspace) { webApp }) {
      existingWorkspace.webApp = workspace.webApp
      hasChanged = true
    }

    return if (hasChanged) {
      val responseEntity =
          cosmosTemplate.upsertAndReturnEntity("${organizationId}_workspaces", existingWorkspace)
      userIdsRemoved?.forEach {
        this.eventPublisher.publishEvent(UserRemovedFromOrganization(this, organizationId, it))
      }
      workspace.users?.forEach { user ->
        this.eventPublisher.publishEvent(
            UserAddedToWorkspace(
                this,
                organizationId,
                responseEntity.name!!,
                workspaceId,
                workspace.name,
                user.id!!,
                user.roles.map { role -> role.value }))
      }
      responseEntity
    } else {
      existingWorkspace
    }
  }

  override fun deleteWorkspace(organizationId: String, workspaceId: String): Workspace {
    val workspace = findWorkspaceById(organizationId, workspaceId)
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
      overwrite: Boolean
  ): WorkspaceFile {
    val workspace = findWorkspaceById(organizationId, workspaceId)
    logger.debug(
        "Uploading file resource to workspace #{} ({}): {}",
        workspace.id,
        workspace.name,
        file.filename)
    azureStorageBlobServiceClient
        .getBlobContainerClient(organizationId.sanitizeForAzureStorage())
        .getBlobClient("${workspaceId.sanitizeForAzureStorage()}/${file.filename}")
        .upload(file.inputStream, file.contentLength(), overwrite)
    return WorkspaceFile(fileName = file.filename)
  }

  override fun findAllWorkspaceFiles(
      organizationId: String,
      workspaceId: String
  ): List<WorkspaceFile> {
    val workspace = findWorkspaceById(organizationId, workspaceId)
    logger.debug("List all files for workspace #{} ({})", workspace.id, workspace.name)
    return getWorkspaceFileResources(organizationId, workspaceId)
        .mapNotNull { it.filename?.substringAfterLast("/") }
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
          .getResources("azure-blob://$organizationId/$workspaceId/*".sanitizeForAzureStorage())
          .map { it as BlobStorageResource }
}
