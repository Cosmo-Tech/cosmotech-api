// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.connector.azure

import com.azure.cosmos.models.CosmosContainerProperties
import com.cosmotech.api.AbstractCosmosBackedService
import com.cosmotech.api.events.ConnectorRemoved
import com.cosmotech.api.utils.findAll
import com.cosmotech.api.utils.findByIdOrThrow
import com.cosmotech.connector.api.ConnectorApiService
import com.cosmotech.connector.domain.Connector
import java.util.*
import javax.annotation.PostConstruct
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["csm.platform.vendor"], havingValue = "azure", matchIfMissing = true)
class ConnectorServiceImpl : AbstractCosmosBackedService(), ConnectorApiService {

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

  override fun registerConnector(connector: Connector): Connector =
      cosmosTemplate.insert(
          coreConnectorContainer, connector.copy(id = idGenerator.generate("connector")))
          ?: throw IllegalArgumentException("No connector returned in response: $connector")

  override fun unregisterConnector(connectorId: String) {
    cosmosTemplate.deleteEntity(coreConnectorContainer, this.findConnectorById(connectorId))
    this.eventPublisher.publishEvent(ConnectorRemoved(this, connectorId))
  }
}
