// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.id.hashids

import com.cosmotech.common.id.AbstractCsmIdGenerator
import java.util.UUID
import kotlin.math.min
import org.hashids.Hashids
import org.hashids.Hashids.MAX_NUMBER
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

private const val MIN_HASH_LENGTH = 0
private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

@Component
@ConditionalOnProperty(
    name = ["csm.platform.id-generator.type"], havingValue = "hashid", matchIfMissing = true)
internal class HashidsCsmIdGenerator() : AbstractCsmIdGenerator() {

  private val logger = LoggerFactory.getLogger(HashidsCsmIdGenerator::class.java)

  private var encodedElementSupplier: (() -> ULong)? = null

  constructor(encodedElementSupplier: () -> ULong) : this() {
    this.encodedElementSupplier = encodedElementSupplier
  }

  override fun buildId(scope: String): String {
    if (scope.isBlank()) {
      throw IllegalArgumentException("scope must not be blank")
    }
    val encodedElement =
        this.encodedElementSupplier?.invoke()?.toLong()
            ?: run {
              logger.trace(
                  "encoded element supplier is not defined " +
                      "=> defaulting to current time in nanos for ID Generation")
              System.nanoTime()
            }
    // We do not intend to decode generated IDs afterwards => we can safely generate a unique salt.
    // This will give us different ids even with equal numbers to encode
    return Hashids("$scope-${UUID.randomUUID()}", MIN_HASH_LENGTH, ALPHABET)
        .encode(
            // PROD-8703: encodedElement might be higher than the maximum number supported
            min(encodedElement, MAX_NUMBER))
  }
}
