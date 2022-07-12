// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun.container

import com.cosmotech.scenario.domain.Scenario
import com.cosmotech.scenario.domain.ScenarioResourceSizing
import com.cosmotech.scenariorun.domain.ScenarioRunStartContainers
import com.cosmotech.solution.domain.RunTemplate
import com.cosmotech.solution.domain.RunTemplateResourceSizing
import com.cosmotech.solution.domain.Solution
import com.cosmotech.workspace.domain.Workspace

internal data class StartInfo(
    val startContainers: ScenarioRunStartContainers,
    val scenario: Scenario,
    val workspace: Workspace,
    val solution: Solution,
    val runTemplate: RunTemplate,
    val csmSimulationId: String,
)

data class SizingInfo(val cpu: String, val memory: String)

data class Sizing(val requests: SizingInfo, val limits: SizingInfo)


internal val BASIC_SIZING = Sizing(
  requests = SizingInfo(
    cpu = "1",
    memory = "4"
  ),
  limits = SizingInfo(
    cpu = "",
    memory = ""
  )
)

internal val HIGH_MEMORY_SIZING = Sizing(
  requests = SizingInfo(
    cpu = "1",
    memory = "4"
  ),
  limits = SizingInfo(
    cpu = "",
    memory = ""
  )
)

internal val HIGH_CPU_SIZING = Sizing(
  requests = SizingInfo(
    cpu = "1",
    memory = "4"
  ),
  limits = SizingInfo(
    cpu = "",
    memory = ""
  )
)



fun ScenarioResourceSizing.toSizing() : Sizing{
    return Sizing(
      requests = SizingInfo(
          cpu = this.requests.cpu,
          memory = this.requests.memory
      ),
      limits = SizingInfo(
          cpu = this.limits.cpu,
          memory = this.limits.memory
      )
    )
}
fun RunTemplateResourceSizing.toSizing() : Sizing{
    return Sizing(
      requests = SizingInfo(
          cpu = this.requests.cpu,
          memory = this.requests.memory
      ),
      limits = SizingInfo(
          cpu = this.limits.cpu,
          memory = this.limits.memory
      )
    )
}

