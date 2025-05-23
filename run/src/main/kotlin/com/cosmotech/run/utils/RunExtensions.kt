// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run.utils

import com.cosmotech.run.NODE_LABEL_DEFAULT
import com.cosmotech.run.domain.Run
import com.cosmotech.run.domain.RunContainer
import com.cosmotech.run.domain.RunState

internal fun Run.withoutSensitiveData(): Run = this.copy(containers = null)

internal fun RunState.isTerminal() =
    when (this) {
      RunState.Failed,
      RunState.Successful -> true
      RunState.Unknown,
      RunState.Running,
      RunState.NotStarted -> false
    }

internal fun RunContainer.getNodeLabelSize(): Map<String, String> {
  val currentNodeLabel = this.nodeLabel ?: NODE_LABEL_DEFAULT
  return mapOf("cosmotech.com/size" to currentNodeLabel)
}
