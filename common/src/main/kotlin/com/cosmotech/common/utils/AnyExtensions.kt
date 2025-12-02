// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.utils

import com.fasterxml.jackson.core.type.TypeReference
import java.lang.IllegalArgumentException
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty

/**
 * Extension function that checks whether the specified member has changed between [this] receiver
 * and the given [old] one, even if not of the same types. This allows to write simple calls like
 * so:
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

/** Convert any object as a Map, using the Jackson Object Mapper */
fun <T> T.convertToMap(): Map<String, Any> =
    objectMapper().convertValue(this, object : TypeReference<Map<String, Any>>() {})

/**
 * Compare this object against another one of the same type and mutate the former if {@code
 * mutateIfChanged = true}.
 *
 * Note this is purposely limited to data class instances. Comparison is done against non-null
 * fields of the new object. This means that detecting changes against a null value is not
 * supported.
 *
 * @param new the new object against which comparison is performed
 * @param mutateIfChanged whether to mutate the current object if for fields that have changed
 * @param excludedFields an array of fields to exlude from comparison. Note that fields named 'id´
 *   are automatically excluded, by convention.
 * @throws UnsupportedOperationException if the object being compared are not instances of a data
 *   class
 * @throws IllegalArgumentException if changes were detected and {@code mutateIfChanged = true}, but
 *   the field is not mutable
 */
@Suppress("NestedBlockDepth")
inline fun <reified T> T.compareToAndMutateIfNeeded(
    new: T,
    mutateIfChanged: Boolean = true,
    excludedFields: Array<String> = emptyArray(),
): Set<String> {
  if (!T::class.isData) {
    throw UnsupportedOperationException("${T::class} is not a data class")
  }

  val excludedMembersAsSet = excludedFields.toSet() + setOf("id")

  val membersChanged = mutableListOf<String>()
  T::class
      .members
      .filterIsInstance(KProperty::class.java)
      .filterNot { excludedMembersAsSet.isNotEmpty() && it.name in excludedMembersAsSet }
      .forEach { member ->
        val getter = member.getter
        val oldValue: Any? = getter.call(this)
        val newValue: Any? = getter.call(new)
        if (newValue != null) {
          val changed =
              if (newValue is Collection<*>) {
                // Compare regardless of the order
                oldValue == null || (oldValue as Collection<*>).toSet() != newValue.toSet()
              } else {
                oldValue != newValue
              }
          if (changed) {
            membersChanged.add(member.name)
            if (mutateIfChanged) {
              if (member !is KMutableProperty) {
                throw IllegalArgumentException(
                    "Detected change but cannot mutate " +
                        "this object because property ${member.name} " +
                        "(on class ${T::class}) is not mutable. " +
                        "Either exclude this field or call this function with " +
                        "mutateIfChanged=false to view the changes detected"
                )
              }
              member.setter.call(this, newValue)
            }
          }
        }
      }

  return membersChanged.toSet()
}
