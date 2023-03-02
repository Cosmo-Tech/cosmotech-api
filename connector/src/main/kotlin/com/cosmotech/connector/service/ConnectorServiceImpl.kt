// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.connector.service

import com.cosmotech.api.CsmPhoenixService
import com.cosmotech.api.events.ConnectorRemoved
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.utils.constructPageRequest
import com.cosmotech.api.utils.findAllPaginated
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.connector.api.ConnectorApiService
import com.cosmotech.connector.domain.Connector
import com.cosmotech.connector.repository.ConnectorRepository
import org.springframework.stereotype.Service

@Service
internal class ConnectorServiceImpl(var connectorRepository: ConnectorRepository) :
    CsmPhoenixService(), ConnectorApiService {

  override fun findAllConnectors(page: Int?, size: Int?): List<Connector> {
    val maxResult = csmPlatformProperties.twincache.connector.maxResult
    var pageRequest = constructPageRequest(page, size, maxResult)
    if (pageRequest != null) {
      return connectorRepository.findAll(pageRequest).toList()
    }
    return findAllPaginated(maxResult) { connectorRepository.findAll(it).toList() }
  }

  override fun findConnectorById(connectorId: String): Connector {
    return connectorRepository.findById(connectorId).orElseThrow {
      CsmResourceNotFoundException(
          "Resource of type '${Connector::class.java.simpleName}' and identifier '$connectorRepository' not found")
    }
  }

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

  override fun importConnector(connector: Connector): Connector {
    if (connector.id == null) {
      throw CsmResourceNotFoundException("Connector id is null")
    }
    return connectorRepository.save(connector)
  }
}
