// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.bulk

@Suppress("MagicNumber")
enum class PropertyType(val code: Byte) {
  BI_NULL(0),
  BI_BOOL(1),
  BI_DOUBLE(2),
  BI_STRING(3),
  BI_LONG(4),
  BI_ARRAY(5)
}

fun Any?.toPropertyType(): PropertyType {

  if (this == null) return PropertyType.BI_NULL
  var type = PropertyType.BI_STRING

  when (this) {
    is Int -> type = PropertyType.BI_LONG
    is Double -> type = PropertyType.BI_DOUBLE
    is String -> {
      when (this.lowercase()) {
        "" -> type = PropertyType.BI_NULL
        "true",
        "false" -> type = PropertyType.BI_BOOL
        else -> {
          // Putting BI_String instead of BI_ARRAY is a workaround to avoid having empty arrays
          // breaking redis graph
          if (this.startsWith('[') and this.endsWith(']')) type = PropertyType.BI_STRING
        }
      }
    }
  }
  return type
}
