// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.utils

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/**
 * Extension function that checks whether the specified member has changed between [this] receiver
 * and the given [old] one, even if not of the same types.
 * This allows to write simple calls like so:
 * ```kotlin
 *     val myObj = ...
 *     val oldObj = ...
 *     val hasThisFieldChanged = myObj.changed(oldObj) { <fieldName> }
 * ```
 */
inline fun <reified T, U, R> T.changed(old: U?, memberAccessBlock: T.() -> R): Boolean {
  if (old == null || old !is T) {
    return true
  }
  val currentValue = with(this, memberAccessBlock)
  val oldValue = with(old, memberAccessBlock)
  return currentValue != oldValue
}

/**
 * Convert any object as a Map, using the Jackson Object Mapper
 */
fun <T> T.convertToMap(): Map<String, Any> =
        jacksonObjectMapper().convertValue(this, object: TypeReference<Map<String, Any>>() {})
