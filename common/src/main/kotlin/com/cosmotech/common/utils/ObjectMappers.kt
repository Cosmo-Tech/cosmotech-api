// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.utils

import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.dataformat.yaml.YAMLMapper
import tools.jackson.datatype.jsr310.JavaTimeModule
import tools.jackson.module.kotlin.jsonMapper
import tools.jackson.module.kotlin.kotlinModule

fun objectMapper(): ObjectMapper = jsonMapper {
  addModule(kotlinModule())
  addModule(JavaTimeModule())
  disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
  disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
}

fun yamlObjectMapper(): ObjectMapper =
    YAMLMapper.builder()
        .addModule(kotlinModule())
        .addModule(JavaTimeModule())
        .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()
