// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.utils

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.InputStream

inline fun <reified T> InputStream.readYaml(yamlObjectMapper: ObjectMapper): T =
    this.use { yamlObjectMapper.readValue(it, T::class.java) }
