// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.connector.service

import com.cosmotech.api.CsmPhoenixService
import com.cosmotech.api.events.ConnectorRemoved
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.connector.api.ConnectorApiService
import com.cosmotech.connector.domain.Connector
import com.cosmotech.connector.repository.ConnectorRepository
import com.redis.om.spring.annotations.EnableRedisDocumentRepositories
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["csm.platform.vendor"], havingValue = "azure", matchIfMissing = true)
@EnableRedisDocumentRepositories(basePackages = arrayOf("com.cosmotech.connector.azure.*"))
internal class ConnectorServiceImpl(var connectorRepository: ConnectorRepository) :
    CsmPhoenixService(), ConnectorApiService {

  override fun findAllConnectors(): List<Connector> = connectorRepository.findAll().toList()

  override fun findConnectorById(connectorId: String): Connector = findByIdOrThrow(connectorId)

  override fun registerConnector(connector: Connector): Connector {
    val connectorToSave =
        connector.copy(
            id = idGenerator.generate("connector"), ownerId = getCurrentAuthenticatedUserName())
    return connectorRepository.save(connectorToSave)
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

  private fun findByIdOrThrow(id: String, errorMessage: String? = null): Connector =
      connectorRepository.findById(id).orElseThrow {
        CsmResourceNotFoundException(
            errorMessage
                ?: "Resource of type '${Connector::class.java.simpleName}' and identifier '$id' not found")
      }

  override fun importConnector(connector: Connector): Connector {
    if (connector.id == null) {
      throw CsmResourceNotFoundException("Connector id is null")
    }
    return connectorRepository.save(connector)
  }
}
