// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.organization

import com.azure.cosmos.CosmosClient
import com.azure.cosmos.models.CosmosContainerProperties
import com.azure.cosmos.models.CosmosQueryRequestOptions
import com.cosmotech.api.AbstractPhoenixService
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.domain.Organization
import com.fasterxml.jackson.databind.node.ObjectNode
import java.util.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class OrganizationServiceImpl(
    // TODO Consider using the async client instead + WebFlux
    val cosmosClient: CosmosClient
) : AbstractPhoenixService(), OrganizationApiService {

  private val logger = LoggerFactory.getLogger(OrganizationServiceImpl::class.java)

  @Value("\${csm.azure.cosmosdb.database.core}")
  private lateinit var azureCosmosDbDatabaseCore: String

  @Value("\${csm.azure.cosmosdb.database.core.organizations.container}")
  private lateinit var azureCosmosDbDatabaseCoreOrganizationContainer: String
  override fun findAllOrganizations(): List<Organization> {
    return cosmosClient
        .getDatabase(azureCosmosDbDatabaseCore)
        .getContainer(azureCosmosDbDatabaseCoreOrganizationContainer)
        .queryItems("SELECT * FROM c", CosmosQueryRequestOptions(), ObjectNode::class.java)
        .map {
          // Workaround because of the issue reported in
          // https://github.com/Azure/azure-sdk-for-java/issues/12269
          // Azure Cosmos SDK is not able to deserialize Kotlin data classes
          convertTo<Organization>(it)
        }
        .toList()
  }

  override fun findOrganizationById(organizationId: String): Organization {
    TODO("Not yet implemented")
  }

  override fun registerOrganization(organization: Organization): Organization {
    logger.trace("Registering organization : $organization")
    val database = cosmosClient.getDatabase(azureCosmosDbDatabaseCore)
    val organizationId = UUID.randomUUID().toString()
    val organizationToCreate = organization.copy(id = organizationId)
    val createItemResponse =
        database
            .getContainer(azureCosmosDbDatabaseCoreOrganizationContainer)
            .createItem(organizationToCreate)

    logger.trace("[registerOrganization($organization)] : createItemResponse: $createItemResponse")

    database.createContainerIfNotExists(
        CosmosContainerProperties("${organizationId}_user-data", "/userId"))

    database.createContainerIfNotExists(
        CosmosContainerProperties("${organizationId}_workspaces", "/workspaceId"))

    // TODO Publish event and handle rollbacks in case of errors
    return organizationToCreate
  }

  override fun unregisterOrganization(organizationId: String): Organization {
    TODO("Not yet implemented")
  }

  override fun updateOrganization(
      organizationId: String,
      organization: Organization
  ): Organization {
    TODO("Not yet implemented")
  }
}
