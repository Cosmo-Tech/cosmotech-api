// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.organization

import com.azure.cosmos.CosmosClient
import com.azure.cosmos.CosmosClientBuilder
import com.azure.cosmos.models.CosmosContainerProperties
import com.azure.spring.data.cosmos.core.CosmosTemplate
import com.cosmotech.api.AbstractPhoenixService
import com.cosmotech.api.events.OrganizationRegistered
import com.cosmotech.api.events.OrganizationUnregistered
import com.cosmotech.api.utils.changed
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.domain.Organization
import java.lang.IllegalStateException
import java.util.*
import javax.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class OrganizationServiceImpl(
    // TODO Consider using the async client instead + WebFlux
    val cosmosTemplate: CosmosTemplate,
    val cosmosClientBuilder: CosmosClientBuilder
) : AbstractPhoenixService(), OrganizationApiService {

  private val logger = LoggerFactory.getLogger(OrganizationServiceImpl::class.java)

  private var cosmosClient: CosmosClient? = null

  @Value("\${azure.cosmos.database}") private lateinit var databaseName: String

  @Value("\${csm.azure.cosmosdb.database.core.organizations.container}")
  private lateinit var coreOrganizationContainer: String
  @PostConstruct
  fun init() {
    // TODO Consider using the async client instead
    this.cosmosClient = cosmosClientBuilder.buildClient()
  }

  override fun findAllOrganizations() =
      cosmosTemplate.findAll(coreOrganizationContainer, Organization::class.java).toList()

  override fun findOrganizationById(organizationId: String) =
      cosmosTemplate.findById(coreOrganizationContainer, organizationId, Organization::class.java)
          ?: throw IllegalArgumentException("Organization not found: $organizationId")

  override fun registerOrganization(organization: Organization): Organization {
    logger.trace("Registering organization : $organization")

    val organizationRegistered =
        cosmosTemplate.insert(
            coreOrganizationContainer, organization.copy(id = UUID.randomUUID().toString()))

    val organizationId =
        organizationRegistered.id
            ?: throw IllegalStateException(
                "No ID returned for organization registered: $organizationRegistered")

    val database = cosmosClient!!.getDatabase(databaseName)

    database.createContainerIfNotExists(
        CosmosContainerProperties("${organizationId}_user-data", "/userId"))

    database.createContainerIfNotExists(
        CosmosContainerProperties("${organizationId}_workspaces", "/workspaceId"))

    this.eventPublisher.publishEvent(OrganizationRegistered(this, organizationId))

    // TODO Handle rollbacks in case of errors

    return organizationRegistered
  }

  override fun unregisterOrganization(organizationId: String): Organization {
    val organization = findOrganizationById(organizationId)
    cosmosTemplate.deleteContainer("${organizationId}_user-data")
    cosmosTemplate.deleteContainer("${organizationId}_workspaces")
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
      // TODO Find out which users to change
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
}
