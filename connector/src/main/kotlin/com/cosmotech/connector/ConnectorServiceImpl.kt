// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.

package com.cosmotech.connector

import com.cosmotech.api.AbstractPhoenixService
import com.cosmotech.connector.api.ConnectorsApiService
import com.cosmotech.connector.domain.Connector
import com.cosmotech.connector.domain.ConnectorParameterGroup
import java.io.BufferedReader
import org.springframework.stereotype.Service
import org.springframework.util.FileCopyUtils

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

  override fun uploadConnector(body: org.springframework.core.io.Resource): Connector {
    print("hello")
    print("length: " + body.contentLength().toString())
    val content = body.getInputStream().bufferedReader().use(BufferedReader::readText)
    print(content)

    return Connector()
  }

  override fun unregisterConnector(connectorId: String): Connector {
    TODO("Not yet implemented")
  }

  override fun updateConnector(connectorId: String, connector: Connector): Connector {
    TODO("Not yet implemented")
  }
}
