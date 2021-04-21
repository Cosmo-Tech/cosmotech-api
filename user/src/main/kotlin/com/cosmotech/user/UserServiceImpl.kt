// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.user

import com.azure.cosmos.models.CosmosContainerProperties
import com.cosmotech.api.AbstractCosmosBackedService
import com.cosmotech.user.api.UserApiService
import com.cosmotech.user.domain.User
import java.util.*
import javax.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class UserServiceImpl : AbstractCosmosBackedService(), UserApiService {

  @Value("\${csm.azure.cosmosdb.database.core.users.container}")
  private lateinit var coreUserContainer: String

  @Value("\${azure.cosmos.database}") private lateinit var databaseName: String

  @PostConstruct
  fun initService() {
    cosmosClient
        .getDatabase(databaseName)
        .createContainerIfNotExists(CosmosContainerProperties(coreUserContainer, "/id"))
  }

  override fun authorizeUser() {
    TODO("Not yet implemented")
  }

  override fun findAllUsers() = cosmosTemplate.findAll(coreUserContainer, User::class.java).toList()

  override fun findUserById(userId: String) =
      cosmosTemplate.findById(coreUserContainer, userId, UserDetails::class.java)
          ?: throw IllegalArgumentException("User not found: $userId")

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
    TODO("Not yet implemented")
  }

  override fun registerUser(user: User): User =
      cosmosTemplate.insert(coreUserContainer, user.copy(id = UUID.randomUUID().toString()))

  override fun unregisterUser(userId: String): User {
    // TODO Why is UserDetails.platformRoles # User.platformRoles ??
    val user = findUserById(userId)
    cosmosTemplate.deleteEntity(coreUserContainer, user)
    return User(
        id = userId,
        name = user.name,
        platformRoles = user.platformRoles.map { User.PlatformRoles.valueOf(it.toString()) })
  }

  override fun updateUser(userId: String, user: User): User {
    TODO("Not yet implemented")
  }
}
