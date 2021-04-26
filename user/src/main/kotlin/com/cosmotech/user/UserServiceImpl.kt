// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.user

import com.azure.cosmos.models.CosmosContainerProperties
import com.cosmotech.api.AbstractCosmosBackedService
import com.cosmotech.api.events.OrganizationRegistered
import com.cosmotech.api.events.OrganizationUnregistered
import com.cosmotech.api.events.UserRegistered
import com.cosmotech.api.events.UserUnregistered
import com.cosmotech.api.utils.findAll
import com.cosmotech.api.utils.findByIdOrError
import com.cosmotech.user.api.UserApiService
import com.cosmotech.user.domain.User
import java.lang.IllegalStateException
import java.util.*
import javax.annotation.PostConstruct
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["csm.platform.vendor"], havingValue = "azure", matchIfMissing = true)
class UserServiceImpl : AbstractCosmosBackedService(), UserApiService {

  private lateinit var coreUserContainer: String

  @PostConstruct
  fun initService() {
    this.coreUserContainer = csmPlatformProperties.azure!!.cosmos.coreDatabase.users.container
    cosmosClient
        .getDatabase(databaseName)
        .createContainerIfNotExists(CosmosContainerProperties(coreUserContainer, "/id"))
  }

  override fun authorizeUser() {
    TODO("Not yet implemented")
  }

  override fun findAllUsers() = cosmosTemplate.findAll<User>(coreUserContainer)

  override fun findUserById(userId: String): User =
      cosmosTemplate.findByIdOrError(coreUserContainer, userId)

  override fun getCurrentUser(): User {
    TODO("Not yet implemented")
  }

  override fun getOrganizationCurrentUser(organizationId: kotlin.String): User {
    TODO("Not yet implemented")
  }

  override fun getWorkspaceCurrentUser(
      organizationId: kotlin.String,
      workspaceId: kotlin.String
  ): User {
    TODO("Not yet implemented")
  }

  override fun registerUser(user: User): User {
    val userRegistered =
        cosmosTemplate.insert(coreUserContainer, user.copy(id = UUID.randomUUID().toString()))
    val userId =
        userRegistered.id
            ?: throw IllegalStateException(
                "No ID returned for organization registered: $userRegistered")
    this.eventPublisher.publishEvent(UserRegistered(this, userId))
    return userRegistered
  }

  override fun unregisterUser(userId: String): User {
    val user = findUserById(userId)
    cosmosTemplate.deleteEntity(coreUserContainer, user)
    this.eventPublisher.publishEvent(UserUnregistered(this, userId))
    return user
  }

  override fun updateUser(userId: String, user: User): User {
    TODO("Not yet implemented")
  }

  @EventListener(OrganizationRegistered::class)
  fun onOrganizationRegistered(organizationRegistered: OrganizationRegistered) {
    cosmosClient
        .getDatabase(databaseName)
        .createContainerIfNotExists(
            CosmosContainerProperties(
                "${organizationRegistered.organizationId}_user-data", "/userId"))
  }

  @EventListener(OrganizationUnregistered::class)
  fun onOrganizationUnregistered(organizationUnregistered: OrganizationUnregistered) {
    // TODO Handle deletion asynchronously
    cosmosTemplate.deleteContainer("${organizationUnregistered.organizationId}_user-data")
  }
}
