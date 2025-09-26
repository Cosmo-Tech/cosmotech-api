// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.id

interface CsmIdGenerator {

  fun generate(scope: String, prependPrefix: String? = null): String
}

internal abstract class AbstractCsmIdGenerator : CsmIdGenerator {

  final override fun generate(scope: String, prependPrefix: String?): String {
    if (scope.isBlank()) {
      throw IllegalArgumentException("scope must not be blank")
    }

    val id = this.buildId(scope)
    return "${prependPrefix ?: "${scope[0].lowercaseChar()}-"}$id"
  }

  protected abstract fun buildId(scope: String): String
}
