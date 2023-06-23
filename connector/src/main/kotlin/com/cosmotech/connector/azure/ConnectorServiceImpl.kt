// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.connector.azure

import com.azure.cosmos.models.CosmosContainerProperties
import com.cosmotech.api.azure.cosmosdb.ext.findAll
import com.cosmotech.api.azure.cosmosdb.ext.findByIdOrThrow
import com.cosmotech.api.azure.cosmosdb.service.CsmCosmosDBService
import com.cosmotech.api.events.ConnectorRemoved
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.security.ROLE_PLATFORM_ADMIN
import com.cosmotech.api.utils.getCurrentAuthenticatedRoles
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.connector.api.ConnectorApiService
import com.cosmotech.connector.domain.Connector
import javax.annotation.PostConstruct
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["csm.platform.vendor"], havingValue = "azure", matchIfMissing = true)
internal class ConnectorServiceImpl : CsmCosmosDBService(), ConnectorApiService {

  private lateinit var coreConnectorContainer: String

  @PostConstruct
  fun initService() {
    this.coreConnectorContainer =
        csmPlatformProperties.azure!!.cosmos.coreDatabase.connectors.container
    cosmosCoreDatabase.createContainerIfNotExists(
        CosmosContainerProperties(coreConnectorContainer, "/id"))
  }

  override fun findAllConnectors() = cosmosTemplate.findAll<Connector>(coreConnectorContainer)

  override fun findConnectorById(connectorId: String): Connector =
      cosmosTemplate.findByIdOrThrow(coreConnectorContainer, connectorId)

  override fun registerConnector(connector: Connector): Connector {
    if (connector.azureManagedIdentity == true &&
        connector.azureAuthenticationWithCustomerAppRegistration == true) {
      throw IllegalArgumentException(
          "Don't know which authentication mechanism to use to connect " +
              "against Azure services. " +
              "Both azureManagedIdentity and azureAuthenticationWithCustomerAppRegistration " +
              "cannot be set to true")
    }
    return cosmosTemplate.insert(
        coreConnectorContainer,
        connector.copy(
            id = idGenerator.generate("connector"),
            ownerId = getCurrentAuthenticatedUserName(csmPlatformProperties)))
        ?: throw IllegalStateException("No connector returned in response: $connector")
  }

  override fun unregisterConnector(connectorId: String) {
    val connector = this.findConnectorById(connectorId)
    val isPlatformAdmin =
        getCurrentAuthenticatedRoles(csmPlatformProperties).contains(ROLE_PLATFORM_ADMIN)
    if (connector.ownerId != getCurrentAuthenticatedUserName(csmPlatformProperties) &&
        !isPlatformAdmin) {
      throw CsmAccessForbiddenException("You are not allowed to delete this Resource")
    }
    cosmosTemplate.deleteEntity(coreConnectorContainer, connector)
    this.eventPublisher.publishEvent(ConnectorRemoved(this, connectorId))
  }
}
