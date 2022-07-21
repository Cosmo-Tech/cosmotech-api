// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.connector.azure

import com.cosmotech.api.CsmPhoenixService
import com.cosmotech.api.events.ConnectorRemoved
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.connector.api.ConnectorApiService
import com.cosmotech.connector.domain.Connector
import com.cosmotech.connector.repositories.ConnectorRepository
import org.springframework.stereotype.Service

@Service
internal class ConnectorServiceImpl(var connectorRepository: ConnectorRepository) :
    CsmPhoenixService(), ConnectorApiService {

    override fun findAllConnectors() = connectorRepository.findAll().toList()

    override fun findConnectorById(connectorId: String): Connector =
        connectorRepository.findById(connectorId).orElseThrow()

    override fun registerConnector(connector: Connector): Connector {

        if (connector.azureManagedIdentity == true &&
            connector.azureAuthenticationWithCustomerAppRegistration == true) {
            throw IllegalArgumentException(
                "Don't know which authentication mechanism to use to connect " +
                        "against Azure services. " +
                        "Both azureManagedIdentity and azureAuthenticationWithCustomerAppRegistration " +
                        "cannot be set to true")
        }

        return connectorRepository.save(
            connector.copy(
                id = idGenerator.generate("connector"), ownerId = getCurrentAuthenticatedUserName()))
    }

    override fun unregisterConnector(connectorId: String) {
        val connector = this.findConnectorById(connectorId)
        if (connector.ownerId != getCurrentAuthenticatedUserName()) {
            // TODO Only the owner or an admin should be able to perform this operation
            throw CsmAccessForbiddenException("You are not allowed to delete this Resource")
        }
        connectorRepository.delete(connector)
        this.eventPublisher.publishEvent(ConnectorRemoved(this, connectorId))
    }
}
