// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.connector.repository

import com.cosmotech.connector.domain.Connector
import com.redis.om.spring.repository.RedisDocumentRepository

interface ConnectorRepository : RedisDocumentRepository<Connector, String> {
  fun findFirstByName(name: String): Connector?
}
