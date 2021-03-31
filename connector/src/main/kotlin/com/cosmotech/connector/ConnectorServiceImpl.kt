// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.

package com.cosmotech.connector

import com.cosmotech.api.AbstractPhoenixService
import com.cosmotech.connector.api.ConnectorsApiService
import com.cosmotech.connector.domain.Connector
import org.springframework.stereotype.Service

@Service
class ConnectorServiceImpl : AbstractPhoenixService(), ConnectorsApiService {
  override fun findAllConnectors(): List<Connector> {
    TODO("Not yet implemented")
  }

  override fun findConnectorById(connectorId: String): Connector {
    TODO("Not yet implemented")
  }

  override fun registerConnector(connector: Connector): Connector {
    TODO("Not yet implemented")
  }

  override fun uploadConnector(connector: Connector): Connector {
    TODO("Not yet implemented")
  }

  override fun unregisterConnector(connectorId: String): Connector {
    TODO("Not yet implemented")
  }

  override fun updateConnector(connectorId: String, connector: Connector): Connector {
    TODO("Not yet implemented")
  }
}
