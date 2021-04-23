// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.events.CsmEventPublisher
import com.cosmotech.api.utils.readYaml
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.InputStream
import org.springframework.beans.factory.annotation.Autowired

abstract class AbstractPhoenixService {

  @Autowired protected lateinit var csmPlatformProperties: CsmPlatformProperties

  @Autowired protected lateinit var eventPublisher: CsmEventPublisher

  protected val yamlObjectMapper: ObjectMapper by lazy {
    ObjectMapper(YAMLFactory())
        .registerKotlinModule()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
  }

  protected inline fun <reified T> readYaml(inputStream: InputStream): T =
      inputStream.readYaml(yamlObjectMapper)
}
