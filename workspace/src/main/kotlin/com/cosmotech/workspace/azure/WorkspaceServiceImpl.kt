// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.workspace.azure

import com.azure.cosmos.models.CosmosContainerProperties
import com.cosmotech.api.AbstractCosmosBackedService
import com.cosmotech.api.events.*
import com.cosmotech.api.utils.changed
import com.cosmotech.api.utils.findAll
import com.cosmotech.api.utils.findByIdOrThrow
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
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["csm.platform.vendor"], havingValue = "azure", matchIfMissing = true)
class WorkspaceServiceImpl(
    private val userService: UserApiService,
    private val organizationService: OrganizationApiService
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
    TODO("Not yet implemented")
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

    var usersToSet = mapOf<String, User>()
    if (workspace.users != null) {
      // Specifying a list of users here overrides the previous list
      usersToSet = fetchUsers(workspace.users!!.mapNotNull { it.id })
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
    if (workspace.services != null && workspace.changed(existingWorkspace) { services }) {
      existingWorkspace.services = workspace.services
      hasChanged = true
    }

    return if (hasChanged) {
      val responseEntity =
          cosmosTemplate.upsertAndReturnEntity("${organizationId}_workspaces", existingWorkspace)
      usersToSet.mapNotNull { it.value.id }.forEach {
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
    cosmosTemplate.deleteEntity("${organizationId}_workspaces", workspace)
    return workspace
  }

  override fun deleteWorkspaceFile(organizationId: String, workspaceId: String, fileName: String) {
    TODO("Not yet implemented")
  }

  override fun downloadWorkspaceFile(
      organizationId: String,
      workspaceId: String,
      fileName: String
  ): Resource {
    TODO("Not yet implemented")
  }

  override fun uploadWorkspaceFile(
      organizationId: kotlin.String,
      workspaceId: kotlin.String,
      fileName: org.springframework.core.io.Resource?
  ): WorkspaceFile {
    TODO("Not yet implemented")
  }
  override fun findAllWorkspaceFiles(
      organizationId: kotlin.String,
      workspaceId: kotlin.String
  ): List<WorkspaceFile> {
    TODO("Not yet implemented")
  }

  @EventListener(OrganizationRegistered::class)
  fun onOrganizationRegistered(organizationRegistered: OrganizationRegistered) {
    cosmosCoreDatabase.createContainerIfNotExists(
        CosmosContainerProperties("${organizationRegistered.organizationId}_workspaces", "/id"))
  }

  @EventListener(OrganizationUnregistered::class)
  @Async("csm-in-process-event-executor")
  fun onOrganizationUnregistered(organizationUnregistered: OrganizationUnregistered) {
    cosmosTemplate.deleteContainer("${organizationUnregistered.organizationId}_workspaces")
  }
}
