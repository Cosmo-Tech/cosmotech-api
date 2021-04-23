// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.events.CsmEventPublisher
import com.cosmotech.api.utils.readYaml
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.InputStream
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier

abstract class AbstractPhoenixService {

  @Autowired protected lateinit var csmPlatformProperties: CsmPlatformProperties

  @Autowired protected lateinit var eventPublisher: CsmEventPublisher

  @Autowired @Qualifier("yamlObjectMapper") protected lateinit var yamlObjectMapper: ObjectMapper

  protected inline fun <reified T> readYaml(inputStream: InputStream): T =
      inputStream.readYaml(yamlObjectMapper)
}
