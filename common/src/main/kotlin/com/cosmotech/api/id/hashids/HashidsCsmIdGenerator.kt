// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.id.hashids

import com.cosmotech.api.id.AbstractCsmIdGenerator
import java.util.UUID
import org.hashids.Hashids
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    name = ["csm.platform.id-generator.type"], havingValue = "hashid", matchIfMissing = true)
class HashidsCsmIdGenerator : AbstractCsmIdGenerator() {

  override fun buildId(scope: String): String {
    if (scope.isBlank()) {
      throw IllegalArgumentException("scope must not be blank")
    }
    // We do not intend to decode generated IDs afterwards => we can safely generate a unique salt
    return Hashids("${scope}-${UUID.randomUUID()}").encode(System.nanoTime())
  }
}
