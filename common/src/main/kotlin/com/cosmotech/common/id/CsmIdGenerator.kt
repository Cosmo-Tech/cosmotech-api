// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.id

import java.util.UUID
import kotlin.math.min
import org.hashids.Hashids
import org.hashids.Hashids.MAX_NUMBER

private const val MIN_HASH_LENGTH = 0
private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

fun generateId(scope: String, prependPrefix: String? = null): String {
  if (scope.isBlank()) {
    throw IllegalArgumentException("scope must not be blank")
  }

  // We do not intend to decode generated IDs afterwards => we can safely generate a unique salt.
  // This will give us different ids even with equal numbers to encode
  val id =
      Hashids("$scope-${UUID.randomUUID()}", MIN_HASH_LENGTH, ALPHABET)
          .encode(
              // PROD-8703: encodedElement might be higher than the maximum number supported
              min(System.nanoTime(), MAX_NUMBER))

  return "${prependPrefix ?: "${scope[0].lowercaseChar()}-"}$id"
}
