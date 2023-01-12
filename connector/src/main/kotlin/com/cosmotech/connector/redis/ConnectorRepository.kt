// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.connector.redis

import com.cosmotech.connector.domain.Connector
import com.redis.om.spring.repository.RedisDocumentRepository
import org.springframework.stereotype.Repository

@Repository interface ConnectorRepository : RedisDocumentRepository<Connector, String>
