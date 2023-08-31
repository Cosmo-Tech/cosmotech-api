// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.utils

object TwingraphUtils {

  @JvmStatic
  fun isReadOnlyQuery(query: String): Boolean {
    val queryNormalized = query.trim().lowercase()
    val matchResults =
        "\\b(create|set|merge|delete|remove)\\b".toRegex().findAll(queryNormalized).toList()
    return matchResults.isEmpty()
  }
}
