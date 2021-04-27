// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.events.CsmEventPublisher
import com.cosmotech.api.utils.buildYamlObjectMapper
import com.cosmotech.api.utils.readYaml
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.InputStream
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

abstract class AbstractPhoenixService {

  protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

  @Autowired protected lateinit var csmPlatformProperties: CsmPlatformProperties

  @Autowired protected lateinit var eventPublisher: CsmEventPublisher

  protected val yamlObjectMapper: ObjectMapper by lazy { buildYamlObjectMapper() }

  protected inline fun <reified T> readYaml(inputStream: InputStream): T =
      inputStream.readYaml(yamlObjectMapper)
}
