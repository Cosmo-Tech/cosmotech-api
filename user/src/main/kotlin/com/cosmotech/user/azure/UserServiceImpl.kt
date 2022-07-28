// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.user.azure

import com.cosmotech.api.CsmPhoenixService
import com.cosmotech.api.events.UserAddedToOrganization
import com.cosmotech.api.events.UserRegistered
import com.cosmotech.api.events.UserRemovedFromOrganization
import com.cosmotech.api.events.UserUnregistered
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.utils.changed
import com.cosmotech.user.api.UserApiService
import com.cosmotech.user.azure.repositories.UserRepository
import com.cosmotech.user.domain.User
import com.cosmotech.user.domain.UserOrganization
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

@Service
//@ConditionalOnProperty(name = ["csm.platform.vendor"], havingValue = "azure", matchIfMissing = true)
@Suppress("TooManyFunctions")
internal class UserServiceImpl (var userRepository: UserRepository): CsmPhoenixService(), UserApiService {

  override fun authorizeUser() {
    TODO("Not yet implemented")
  }

  override fun findAllUsers() = userRepository.findAll().toList()

  override fun findUserById(userId: String): User = userRepository.findById((userId)).orElseThrow()

  override fun getCurrentUser(): User {
    val principal = SecurityContextHolder.getContext().authentication

    logger.debug(
        "Principal (isAuthenticated={}) : '{}' - authorities={}",
        principal.isAuthenticated,
        principal.name,
        principal.authorities)
    return User(name = principal.name, platformRoles = mutableListOf())
  }

  override fun getOrganizationCurrentUser(organizationId: String): User {
    TODO("Not yet implemented")
  }

  override fun getWorkspaceCurrentUser(organizationId: String, workspaceId: String): User {
    TODO("Not yet implemented")
  }

  override fun registerUser(user: User): User {
    if (user.name.isNullOrBlank()) {
      throw IllegalArgumentException("User name must not be null or blank")
    }
    val userRegistered = userRepository.save(user)
    val userId =
        userRegistered.id
            ?: throw IllegalStateException(
                "No ID returned for organization registered: $userRegistered")
    this.eventPublisher.publishEvent(UserRegistered(this, userId))
    return userRegistered
  }

  override fun unregisterUser(userId: String) {
    userRepository.deleteById(userId)
    this.eventPublisher.publishEvent(UserUnregistered(this, userId))
  }

  override fun updateUser(userId: String, user: User): User {
    val existingUser = findUserById(userId)
    var hasChanged = false
    if (user.name != null && user.changed(existingUser) { name }) {
      existingUser.name = user.name
      hasChanged = true
    }
    if (user.platformRoles != null &&
        user.platformRoles?.toSet() != existingUser.platformRoles?.toSet()) {
      // A list preserves the order, but here we actually do not care that much about the order
      existingUser.platformRoles = user.platformRoles
      hasChanged = true
    }
    // Changing the list of Organizations a User is member of can be done via the
    // '/organizations/:id/users' endpoint
    return if (hasChanged) {
      userRepository.save(user)
    } else {
      existingUser
    }
  }

  /*@EventListener(OrganizationRegistered::class)
  fun onOrganizationRegistered(organizationRegistered: OrganizationRegistered) {
    cosmosCoreDatabase.createContainerIfNotExists(
        CosmosContainerProperties("${organizationRegistered.organizationId}_user-data", "/ownerId"))
  }*/

  /*@EventListener(OrganizationUnregistered::class)
  @Async("csm-in-process-event-executor")
  fun onOrganizationUnregistered(organizationUnregistered: OrganizationUnregistered) {
    cosmosTemplate.deleteContainer("${organizationUnregistered.organizationId}_user-data")
    // TODO Remove organization from all users that reference it
  }*/

  @EventListener(UserAddedToOrganization::class)
  @Async("csm-in-process-event-executor")
  fun onUserAddedToOrganization(userAddedToOrganization: UserAddedToOrganization) {
    val user = this.findUserById(userAddedToOrganization.userId)
    val organizationMap =
        user.organizations?.associateBy { it.id }?.toMutableMap() ?: mutableMapOf()
    organizationMap[userAddedToOrganization.organizationId] =
        UserOrganization(
            id = userAddedToOrganization.organizationId,
            name = userAddedToOrganization.organizationName,
            roles = userAddedToOrganization.roles?.toMutableList())
    user.organizations = organizationMap.values.toMutableList()
    userRepository.save(user)
  }

  @EventListener(UserRemovedFromOrganization::class)
  @Async("csm-in-process-event-executor")
  fun onUserUserRemovedFromOrganization(userRemovedFromOrganization: UserRemovedFromOrganization) {
    val user = this.findUserById(userRemovedFromOrganization.userId)
    if (user.organizations?.removeIf { it.id == userRemovedFromOrganization.organizationId } != true) {
      throw CsmResourceNotFoundException(
          "Organization '${userRemovedFromOrganization.organizationId}' *not* found")
    }
    userRepository.save(user)
  }

  override fun testPlatform(): String {
    return "TEST OK. Welcome to the Cosmo Tech Platform"
  }
}
