// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.utils

import io.kubernetes.client.custom.QuantityFormatException
import io.kubernetes.client.custom.QuantityFormatter

/** Validates resource sizing by parsing quantity values */
fun validateResourceSizing(
    propertyName: String,
    requestCpu: String,
    requestMemory: String,
    limitsCpu: String,
    limitsMemory: String,
) {
  val valuesToParse =
      mapOf(
          "requests.cpu" to requestCpu,
          "requests.memory" to requestMemory,
          "limits.cpu" to limitsCpu,
          "limits.memory" to limitsMemory,
      )

  try {
    valuesToParse.values.forEach { QuantityFormatter().parse(it) }
  } catch (e: QuantityFormatException) {
    throw IllegalArgumentException(
        "Invalid quantity format. Please check $propertyName values $valuesToParse",
        e,
    )
  }
}
