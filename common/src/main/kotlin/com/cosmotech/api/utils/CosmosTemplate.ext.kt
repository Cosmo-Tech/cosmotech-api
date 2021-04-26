// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.utils

import com.azure.spring.data.cosmos.core.CosmosTemplate

inline fun <reified T> CosmosTemplate.findAll(container: String): List<T> =
    this.findAll(container, T::class.java).toList()

inline fun <reified T, ID> CosmosTemplate.findById(container: String, id: ID): T? =
    this.findById(container, id, T::class.java)

inline fun <reified T, ID> CosmosTemplate.findByIdOrError(
    container: String,
    id: ID,
    errorMessage: String? = null
): T =
    this.findById<T, ID>(container, id)
        ?: throw IllegalArgumentException(
            errorMessage ?: "Resource of type ${T::class.java.simpleName} and ID $id not found")
