// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun.container

import com.cosmotech.scenario.domain.Scenario
import com.cosmotech.scenariorun.domain.ScenarioRunStartContainers
import com.cosmotech.solution.domain.RunTemplate
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
