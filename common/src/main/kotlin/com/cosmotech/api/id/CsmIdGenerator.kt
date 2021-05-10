// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.id

interface CsmIdGenerator {

  fun generate(scope: String, prependPrefix: String? = null): String
}

abstract class AbstractCsmIdGenerator : CsmIdGenerator {

  final override fun generate(scope: String, prependPrefix: String?): String {
    val id = this.buildId(scope)
    return "${prependPrefix ?: "${scope[0].uppercaseChar()}-"}${id}"
  }

  protected abstract fun buildId(scope: String): String
}
