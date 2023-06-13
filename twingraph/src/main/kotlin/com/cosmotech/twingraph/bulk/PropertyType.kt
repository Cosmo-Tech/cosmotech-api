// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.twingraph.bulk

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
  }

  (this as String).also {
    if (it == "") type = PropertyType.BI_NULL
    if (it.lowercase() == "true" || it.lowercase() == "false") type = PropertyType.BI_BOOL
    if (it.startsWith('[') and it.endsWith(']')) type = PropertyType.BI_ARRAY
  }
  return type
}
