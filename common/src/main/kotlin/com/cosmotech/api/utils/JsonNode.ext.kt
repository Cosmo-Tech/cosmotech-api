// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.utils

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

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
