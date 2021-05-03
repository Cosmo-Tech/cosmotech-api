// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.organization

import com.azure.cosmos.models.CosmosContainerProperties
import com.cosmotech.api.AbstractCosmosBackedService
import com.cosmotech.api.events.OrganizationRegistered
import com.cosmotech.api.events.OrganizationUnregistered
import com.cosmotech.api.events.UserUnregistered
import com.cosmotech.api.utils.changed
import com.cosmotech.api.utils.findAll
import com.cosmotech.api.utils.findByIdOrThrow
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.domain.Organization
import java.lang.IllegalStateException
import java.util.*
import javax.annotation.PostConstruct
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["csm.platform.vendor"], havingValue = "azure", matchIfMissing = true)
class OrganizationServiceImpl : AbstractCosmosBackedService(), OrganizationApiService {

  private lateinit var coreOrganizationContainer: String

  @PostConstruct
  fun initService() {
    this.coreOrganizationContainer =
        csmPlatformProperties.azure!!.cosmos.coreDatabase.organizations.container
    cosmosCoreDatabase.createContainerIfNotExists(
        CosmosContainerProperties(coreOrganizationContainer, "/id"))
  }

  override fun findAllOrganizations() =
      cosmosTemplate.findAll<Organization>(coreOrganizationContainer)

  override fun findOrganizationById(organizationId: String): Organization =
      cosmosTemplate.findByIdOrThrow(coreOrganizationContainer, organizationId)

  override fun registerOrganization(organization: Organization): Organization {
    logger.trace("Registering organization : $organization")

    // TODO Validate list of users passed

    val organizationRegistered =
        cosmosTemplate.insert(
            coreOrganizationContainer, organization.copy(id = idGenerator.generate("organization")))

    val organizationId =
        organizationRegistered.id
            ?: throw IllegalStateException(
                "No ID returned for organization registered: $organizationRegistered")

    this.eventPublisher.publishEvent(OrganizationRegistered(this, organizationId))

    // TODO Handle rollbacks in case of errors

    return organizationRegistered
  }

  override fun unregisterOrganization(organizationId: String): Organization {
    val organization = findOrganizationById(organizationId)
    cosmosTemplate.deleteEntity(coreOrganizationContainer, organization)

    this.eventPublisher.publishEvent(OrganizationUnregistered(this, organizationId))

    // TODO Handle rollbacks in case of errors

    return organization
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
    if (organization.users != null && organization.changed(existingOrganization) { users }) {
      // TODO Find out which users to change or provide a separate endpoint for such updates
      if (organization.users!!.isEmpty()) {
        existingOrganization.users = listOf()
      }
      hasChanged = true
    }
    return if (hasChanged) {
      cosmosTemplate.upsertAndReturnEntity(coreOrganizationContainer, existingOrganization)
    } else {
      existingOrganization
    }
  }

  @EventListener(UserUnregistered::class)
  @Async("csm-in-process-event-executor")
  fun onUserUnregistered(userUnregisteredEvent: UserUnregistered) {
    logger.info(
        "User ${userUnregisteredEvent.userId} unregistered => removing them from all organizations they belong to..")
    // TODO
  }
}
