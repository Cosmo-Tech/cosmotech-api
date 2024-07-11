// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.connector

import com.cosmotech.connector.api.ConnectorApiService
import com.cosmotech.connector.domain.Connector

interface ConnectorApiServiceInterface : ConnectorApiService {
  fun findConnectorByName(connectorName: String): Connector
}
