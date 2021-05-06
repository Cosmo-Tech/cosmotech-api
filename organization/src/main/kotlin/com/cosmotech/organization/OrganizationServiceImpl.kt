// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.organization

import com.azure.cosmos.models.CosmosContainerProperties
import com.cosmotech.api.AbstractCosmosBackedService
import com.cosmotech.api.events.*
import com.cosmotech.api.utils.changed
import com.cosmotech.api.utils.findAll
import com.cosmotech.api.utils.findByIdOrThrow
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationService
import com.cosmotech.organization.domain.OrganizationUser
import com.cosmotech.user.api.UserApiService
import com.cosmotech.user.domain.User
import java.lang.IllegalStateException
import javax.annotation.PostConstruct
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["csm.platform.vendor"], havingValue = "azure", matchIfMissing = true)
class OrganizationServiceImpl(val userService: UserApiService) :
    AbstractCosmosBackedService(), OrganizationApiService {

  private lateinit var coreOrganizationContainer: String

  @PostConstruct
  fun initService() {
    this.coreOrganizationContainer =
        csmPlatformProperties.azure!!.cosmos.coreDatabase.organizations.container
    cosmosCoreDatabase.createContainerIfNotExists(
        CosmosContainerProperties(coreOrganizationContainer, "/id"))
  }

  override fun addOrReplaceUsersInOrganization(
      organizationId: String,
      organizationUser: List<OrganizationUser>
  ): List<OrganizationUser> {
    if (organizationUser.isEmpty()) {
      // Nothing to do
      return organizationUser
    }

    val organization = findOrganizationById(organizationId)

    val organizationUserWithoutNullIds = organizationUser.filter { it.id != null }
    val newUsersLoaded = fetchUsers(organizationUserWithoutNullIds.mapNotNull { it.id })
    val organizationUserWithRightNames =
        organizationUserWithoutNullIds.map { it.copy(name = newUsersLoaded[it.id]!!.name!!) }
    val organizationUserMap = organizationUserWithRightNames.associateBy { it.id!! }

    val currentOrganizationUsers =
        organization.users?.filter { it.id != null }?.associateBy { it.id!! }?.toMutableMap()
            ?: mutableMapOf()

    newUsersLoaded.forEach { (userId, _) ->
      // Add or replace
      currentOrganizationUsers[userId] = organizationUserMap[userId]!!
    }
    organization.users = currentOrganizationUsers.values.toList()

    cosmosTemplate.upsert(coreOrganizationContainer, organization)

    // Roles might have changed => notify all users so they can update their own items
    organization.users?.forEach { user ->
      this.eventPublisher.publishEvent(
          UserAddedToOrganization(
              this,
              organizationId,
              organization.name!!,
              user.id!!,
              user.roles.map { role -> role.value }))
    }
    return organizationUserWithRightNames
  }

  override fun findAllOrganizations() =
      cosmosTemplate.findAll<Organization>(coreOrganizationContainer)

  override fun findOrganizationById(organizationId: String): Organization =
      cosmosTemplate.findByIdOrThrow(coreOrganizationContainer, organizationId)

  /**
   * Return list of users with the specified identifiers. TODO It would be better to have
   * UserService expose a findUsersByIds, rather than performing a network call for each user id,
   * which has a performance impact
   */
  private fun fetchUsers(userIds: Collection<String>): Map<String, User> =
      userIds.toSet().map { userService.findUserById(it) }.associateBy { it.id!! }

  override fun registerOrganization(organization: Organization): Organization {
    logger.trace("Registering organization : $organization")

    if (organization.name.isNullOrBlank()) {
      throw IllegalArgumentException("Organization name must not be null or blank")
    }

    val usersLoaded = organization.users?.mapNotNull { it.id }?.let { fetchUsers(it) }

    val newOrganizationId = idGenerator.generate("organization")

    val usersWithNames =
        usersLoaded?.let { organization.users?.map { it.copy(name = usersLoaded[it.id]!!.name!!) } }

    // TODO Set the ownerID to the logged-in user

    val organizationRegistered =
        cosmosTemplate.insert(
            coreOrganizationContainer,
            organization.copy(id = newOrganizationId, users = usersWithNames))

    val organizationId =
        organizationRegistered.id
            ?: throw IllegalStateException(
                "No ID returned for organization registered: $organizationRegistered")

    this.eventPublisher.publishEvent(OrganizationRegistered(this, organizationId))
    organization.users?.forEach { user ->
      this.eventPublisher.publishEvent(
          UserAddedToOrganization(
              this,
              organizationId,
              organizationRegistered.name!!,
              user.id!!,
              user.roles.map { role -> role.value }))
    }

    // TODO Handle rollbacks in case of errors

    return organizationRegistered
  }

  override fun removeAllUsersInOrganization(organizationId: String) {
    val organization = findOrganizationById(organizationId)
    if (!organization.users.isNullOrEmpty()) {
      val userIds = organization.users!!.mapNotNull { it.id }
      organization.users = listOf()
      cosmosTemplate.upsert(coreOrganizationContainer, organization)

      userIds.forEach {
        this.eventPublisher.publishEvent(UserRemovedFromOrganization(this, organizationId, it))
      }
    }
  }

  override fun removeUserFromOrganization(organizationId: String, userId: String) {
    val organization = findOrganizationById(organizationId)
    val organizationUserMap =
        organization.users?.associateBy { it.id!! }?.toMutableMap() ?: mutableMapOf()
    if (organizationUserMap.containsKey(userId)) {
      organizationUserMap.remove(userId)
      organization.users = organizationUserMap.values.toList()
      cosmosTemplate.upsert(coreOrganizationContainer, organization)
      this.eventPublisher.publishEvent(UserRemovedFromOrganization(this, organizationId, userId))
    }
  }

  override fun unregisterOrganization(organizationId: String) {
    cosmosTemplate.deleteEntity(coreOrganizationContainer, findOrganizationById(organizationId))

    this.eventPublisher.publishEvent(OrganizationUnregistered(this, organizationId))

    // TODO Handle rollbacks in case of errors
  }

  override fun updateOrganization(
      organizationId: String,
      organization: Organization
  ): Organization {
    val existingOrganization = findOrganizationById(organizationId)

    var hasChanged = false
    if (organization.name != null && organization.changed(existingOrganization) { name }) {
      existingOrganization.name = organization.name
      hasChanged = true
    }
    // TODO Allow to change the ownerId as well, but only the owner can transfer the ownership

    var usersToSet = mapOf<String, User>()
    if (organization.users != null) {
      // Specifying a list of users here overrides the previous list
      usersToSet = fetchUsers(organization.users!!.mapNotNull { it.id })
      val usersWithNames =
          usersToSet.let { organization.users!!.map { it.copy(name = usersToSet[it.id]!!.name!!) } }
      existingOrganization.users = usersWithNames
      hasChanged = true
    }
    if (organization.services != null && organization.changed(existingOrganization) { services }) {
      existingOrganization.services = organization.services
      hasChanged = true
    }
    return if (hasChanged) {
      val responseEntity =
          cosmosTemplate.upsertAndReturnEntity(coreOrganizationContainer, existingOrganization)
      usersToSet.mapNotNull { it.value.id }.forEach {
        this.eventPublisher.publishEvent(UserRemovedFromOrganization(this, organizationId, it))
      }
      organization.users?.forEach { user ->
        this.eventPublisher.publishEvent(
            UserAddedToOrganization(
                this,
                organizationId,
                responseEntity.name!!,
                user.id!!,
                user.roles.map { role -> role.value }))
      }
      responseEntity
    } else {
      existingOrganization
    }
  }

  override fun updateSolutionsContainerRegistryByOrganizationId(
      organizationId: String,
      organizationService: OrganizationService
  ): OrganizationService {
    TODO("Not yet implemented")
  }

  override fun updateStorageByOrganizationId(
      organizationId: String,
      organizationService: OrganizationService
  ): OrganizationService {
    TODO("Not yet implemented")
  }

  override fun updateTenantCredentialsByOrganizationId(
      organizationId: String,
      requestBody: Map<String, Any>
  ): Map<String, Any> {
    TODO("Not yet implemented")
  }

  @EventListener(UserUnregistered::class)
  @Async("csm-in-process-event-executor")
  fun onUserUnregistered(userUnregisteredEvent: UserUnregistered) {
    logger.info(
        "User ${userUnregisteredEvent.userId} unregistered => removing them from all organizations they belong to..")
    // TODO
  }
}
