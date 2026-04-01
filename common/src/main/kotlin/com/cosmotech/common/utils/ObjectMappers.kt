// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.utils

import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.dataformat.yaml.YAMLFactory
import tools.jackson.dataformat.yaml.YAMLMapper
import tools.jackson.module.kotlin.jacksonMapperBuilder
import tools.jackson.module.kotlin.kotlinModule

fun jsonObjectMapper(): JsonMapper =
    jacksonMapperBuilder()
        .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()

fun yamlObjectMapper(): YAMLMapper =
    YAMLMapper.Builder(YAMLFactory())
        .addModule(kotlinModule())
        .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()
