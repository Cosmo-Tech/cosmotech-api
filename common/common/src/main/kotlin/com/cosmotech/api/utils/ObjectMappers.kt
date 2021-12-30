// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.utils

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

fun objectMapper(jsonFactory: JsonFactory? = null): ObjectMapper =
    ObjectMapper(jsonFactory)
        .registerKotlinModule()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

fun yamlObjectMapper(): ObjectMapper = objectMapper(YAMLFactory())

/**
 * Convert any JsonNode as an object of the domain type specified.
 *
 * This is a workaround due to Azure Cosmos SDK not being able to deserialize Kotlin data classes
 * [1].
 *
 * @see [1] https://github.com/Azure/azure-sdk-for-java/issues/12269
 */
inline fun <reified T> JsonNode?.toDomain(objectMapper: ObjectMapper? = null): T? =
    if (this == null) null
    else {
      (objectMapper ?: objectMapper()).treeToValue(this, T::class.java)
    }
