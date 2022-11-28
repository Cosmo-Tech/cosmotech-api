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
import com.cosmotech.api.events.UserAddedToWorkspace
import com.cosmotech.api.events.UserRemovedFromOrganization
import com.cosmotech.api.events.UserRemovedFromWorkspace
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.utils.SecretManager
import com.cosmotech.api.utils.changed
import com.cosmotech.api.utils.compareToAndMutateIfNeeded
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.user.api.UserApiService
import com.cosmotech.user.domain.User
import com.cosmotech.workspace.api.WorkspaceApiService
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceFile
import com.cosmotech.workspace.domain.WorkspaceSecret
import com.cosmotech.workspace.domain.WorkspaceUser
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
    private val userService: UserApiService,
    private val organizationService: OrganizationApiService,
    private val solutionService: SolutionApiService,
    private val azureStorageBlobServiceClient: BlobServiceClient,
    private val azureStorageBlobBatchClient: BlobBatchClient,
    private val secretManager: SecretManager
) : CsmAzureService(), WorkspaceApiService {

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
      workspace.users = mutableListOf()
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
    if (!(workspace.users?.removeIf { it.id == userId } ?: false)) {
      throw CsmResourceNotFoundException("User '$userId' *not* found")
    } else {
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

    organizationService.findOrganizationById(organizationId)
    val workspace = findWorkspaceById(organizationId, workspaceId)

    val newUsersLoaded = fetchUsers(workspaceUser.mapNotNull { it.id })
    val workspaceUserWithRightNames =
        workspaceUser.map { it.copy(name = newUsersLoaded[it.id]!!.name!!) }
    val workspaceUserMap = workspaceUserWithRightNames.associateBy { it.id }

    val currentWorkspaceUsers =
        workspace.users?.associateBy { it.id }?.toMutableMap() ?: mutableMapOf()

    newUsersLoaded.forEach { (userId, _) ->
      // Add or replace
      currentWorkspaceUsers[userId] = workspaceUserMap[userId]!!
    }
    workspace.users = currentWorkspaceUsers.values.toMutableList()

    cosmosTemplate.upsert("${organizationId}_workspaces", workspace)

    // Roles might have changed => notify all users so they can update their own items
    workspace.users?.forEach { user ->
      this.eventPublisher.publishEvent(
          UserAddedToWorkspace(
              this, organizationId, user.id, user.roles.map { role -> role.value }))
    }
    return workspaceUserWithRightNames
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
    val existingWorkspace = findWorkspaceById(organizationId, workspaceId)

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

    var userIdsRemoved: List<String>? = listOf()
    if (workspace.users != null) {
      // Specifying a list of users here overrides the previous list
      val usersToSet = fetchUsers(workspace.users!!.mapNotNull { it.id })
      userIdsRemoved =
          workspace.users?.mapNotNull { it.id }?.filterNot { usersToSet.containsKey(it) }
      val usersWithNames =
          usersToSet.let { workspace.users!!.map { it.copy(name = usersToSet[it.id]!!.name!!) } }
      existingWorkspace.users = usersWithNames.toMutableList()
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
      userIdsRemoved?.forEach {
        this.eventPublisher.publishEvent(UserRemovedFromOrganization(this, organizationId, it))
      }
      workspace.users?.forEach { user ->
        this.eventPublisher.publishEvent(
            UserAddedToWorkspace(
                this, organizationId, user.id, user.roles.map { role -> role.value }))
      }
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
      secretManager.deleteSecret(csmPlatformProperties.namespace, getWorkspaceSecretName(organizationId, workspace.key))
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

  override fun createSecret(
      organizationId: String,
      workspaceId: String,
      workspaceSecret: WorkspaceSecret
  ) {
    val workspaceKey = findWorkspaceById(organizationId, workspaceId).key
    secretManager.createOrReplaceSecret(
        csmPlatformProperties.namespace,
        getWorkspaceSecretName(organizationId, workspaceKey),
        mapOf(WORKSPACE_EVENTHUB_ACCESSKEY_SECRET to (workspaceSecret.dedicatedEventHubKey ?: "")))
  }

  @EventListener(DeleteHistoricalDataOrganization::class)
  fun deleteHistoricalDataWorkspace(data: DeleteHistoricalDataOrganization) {
    val organizationId = data.organizationId
    val workspaces: List<Workspace> = findAllWorkspaces(organizationId)
    for (workspace in workspaces) {
      this.eventPublisher.publishEvent(
          DeleteHistoricalDataWorkspace(this, organizationId, workspace.id!!, data.deleteUnknown))
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
}
