// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationPropertiesScan(basePackages = ["com.cosmotech"])
class CsmApiConfiguration {

  @Bean("yamlObjectMapper")
  fun yamlObjectMapper(): ObjectMapper =
      ObjectMapper(YAMLFactory())
          .registerKotlinModule()
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}
