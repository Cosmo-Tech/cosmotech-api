// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.events.CsmEventPublisher
import com.cosmotech.api.id.CsmIdGenerator
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

@Suppress("UnnecessaryAbstractClass")
abstract class CsmPhoenixService {

  protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

  @Autowired protected lateinit var csmPlatformProperties: CsmPlatformProperties

  @Autowired protected lateinit var idGenerator: CsmIdGenerator

  @Autowired protected lateinit var eventPublisher: CsmEventPublisher
}
