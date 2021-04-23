// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.connector

import com.cosmotech.api.AbstractPhoenixService
import com.cosmotech.connector.api.ConnectorApiService
import com.cosmotech.connector.domain.Connector
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.stereotype.Service

@Service
class ConnectorServiceImpl : AbstractPhoenixService(), ConnectorApiService {
  override fun findAllConnectors(): List<Connector> {
    TODO("Not yet implemented")
  }

  override fun findConnectorById(connectorId: String): Connector {
    TODO("Not yet implemented")
  }

  override fun registerConnector(connector: Connector): Connector {
    TODO("Not yet implemented")
  }

  override fun uploadConnector(body: org.springframework.core.io.Resource) =
      body.inputStream.use {
        val connector =
            ObjectMapper(YAMLFactory())
                .registerKotlinModule()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .readValue(it, Connector::class.java)
        registerConnector(connector)
      }

  override fun unregisterConnector(connectorId: String): Connector {
    TODO("Not yet implemented")
  }
}
