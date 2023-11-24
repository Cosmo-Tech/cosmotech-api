// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun.utils

import com.cosmotech.scenariorun.NODE_LABEL_DEFAULT
import com.cosmotech.scenariorun.domain.ScenarioRun
import com.cosmotech.scenariorun.domain.ScenarioRunContainer
import com.cosmotech.scenariorun.domain.ScenarioRunSearch
import com.cosmotech.scenariorun.domain.ScenarioRunState
import kotlin.reflect.full.memberProperties

/**
 * PROD-8473 : containers listed in a ScenarioRun return environment variables, which are likely to
 * contain sensitive information, like secrets or connection strings.
 *
 * For security purposes, this extension method purposely hides such data, and aims at being used by
 * public-facing API endpoints.
 *
 * We might manually see the actual scenario run containers either via the Workflow Service, or
 * right by inspecting the Kubernetes Cluster.
 */
internal fun ScenarioRun?.withoutSensitiveData(): ScenarioRun? = this?.copy(containers = null)

internal fun ScenarioRunState.isTerminal() =
    when (this) {
      ScenarioRunState.DataIngestionFailure,
      ScenarioRunState.Failed,
      ScenarioRunState.Successful -> true
      ScenarioRunState.Unknown,
      ScenarioRunState.DataIngestionInProgress,
      ScenarioRunState.Running -> false
    }

internal fun ScenarioRunContainer.getNodeLabelSize(): Map<String, String> {
  val currentNodeLabel = this.nodeLabel ?: NODE_LABEL_DEFAULT
  return mapOf("cosmotech.com/size" to currentNodeLabel)
}

internal fun ScenarioRunSearch.toRedisPredicate(): List<String> {
  return this::class
      .memberProperties
      .mapNotNull { Pair(it.name, it.getter.call(this)) }
      .filter { it.second != null }
      .map {
        when (it.first) {
          "state" -> getRedisQuery(it, true)
          else -> getRedisQuery(it, false)
        }
      }
}

internal fun getRedisQuery(pair: Pair<String, Any?>, isSearchable: Boolean): String {
  val openBracket = if (isSearchable) "" else "{"
  val closeBracket = if (isSearchable) "" else "}"
  return "@${pair.first}:$openBracket${pair.second.toString().sanitizeForRedisQuery(isSearchable)}$closeBracket"
}

fun String.sanitizeForRedisQuery(searchable: Boolean = false): String {
  val result = this.replace("@", "\\\\@").replace(".", "\\\\.").replace("-", "\\\\-")
  return if (searchable) {
    if (result.startsWith("*") || result.endsWith("*")) {
      return result
    }
    return "*$result*"
  } else {
    result
  }
}
