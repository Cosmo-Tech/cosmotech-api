// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.connector.service

import com.cosmotech.api.CsmPhoenixService
import com.cosmotech.api.events.ConnectorRemoved
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.security.ROLE_PLATFORM_ADMIN
import com.cosmotech.api.utils.constructPageRequest
import com.cosmotech.api.utils.findAllPaginated
import com.cosmotech.api.utils.getCurrentAuthenticatedRoles
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.connector.ConnectorApiServiceInterface
import com.cosmotech.connector.domain.Connector
import com.cosmotech.connector.repository.ConnectorRepository
import org.springframework.stereotype.Service

@Service
class ConnectorServiceImpl(var connectorRepository: ConnectorRepository) :
    CsmPhoenixService(), ConnectorApiServiceInterface {

  override fun findAllConnectors(page: Int?, size: Int?): List<Connector> {
    val defaultPageSize = csmPlatformProperties.twincache.connector.defaultPageSize
    val pageRequest = constructPageRequest(page, size, defaultPageSize)
    if (pageRequest != null) {
      return connectorRepository.findAll(pageRequest).toList()
    }
    return findAllPaginated(defaultPageSize) { connectorRepository.findAll(it).toList() }
  }

  override fun findConnectorById(connectorId: String): Connector {
    return connectorRepository.findById(connectorId).orElseThrow {
      CsmResourceNotFoundException(
          "Resource of type '${Connector::class.java.simpleName}' and identifier '$connectorId' not found")
    }
  }

  override fun findConnectorByName(connectorName: String): Connector {
    return connectorRepository.findFirstByName(connectorName)
        ?: throw CsmResourceNotFoundException(
            "Resource of type '${Connector::class.java.simpleName}' and name '$connectorName' not found")
  }

  override fun registerConnector(connector: Connector): Connector {
    val connectorToSave =
        connector.copy(
            id = idGenerator.generate("connector"),
            ownerId = getCurrentAuthenticatedUserName(csmPlatformProperties))
    return connectorRepository.save(connectorToSave)
  }

  override fun unregisterConnector(connectorId: String) {
    val connector = this.findConnectorById(connectorId)
    val isPlatformAdmin =
        getCurrentAuthenticatedRoles(csmPlatformProperties).contains(ROLE_PLATFORM_ADMIN)
    if (connector.ownerId != getCurrentAuthenticatedUserName(csmPlatformProperties) &&
        !isPlatformAdmin) {
      // TODO Only the owner or an admin should be able to perform this operation
      throw CsmAccessForbiddenException("You are not allowed to delete this Resource")
    }

    connectorRepository.delete(connector)
    this.eventPublisher.publishEvent(ConnectorRemoved(this, connectorId))
  }
}
