// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
@file:Suppress("MatchingDeclarationName")

package com.cosmotech.api.events

class ScenarioRunStartedForScenario(
    publisher: Any,
    val organizationId: String,
    val workspaceId: String,
    val scenarioId: String,
    val scenarioRunData: ScenarioRunData,
    val workflowData: WorkflowData
) : CsmEvent(publisher) {
  data class ScenarioRunData(val scenarioRunId: String, val csmSimulationRun: String)
  data class WorkflowData(val workflowId: String, val workflowName: String)
}
