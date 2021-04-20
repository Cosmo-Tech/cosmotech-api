// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun

import com.cosmotech.api.AbstractPhoenixService
import com.cosmotech.scenariorun.api.ScenariorunApiService
import com.cosmotech.scenariorun.domain.ScenarioRun
import com.cosmotech.scenariorun.domain.ScenarioRunBase
import com.cosmotech.scenariorun.domain.ScenarioRunLogs
import com.cosmotech.scenariorun.domain.ScenarioRunLogsOptions
import com.cosmotech.scenariorun.domain.ScenarioRunSearch
import com.cosmotech.scenariorun.domain.ScenarioRunStart
import com.cosmotech.scenariorun.domain.ScenarioRunStartContainers
import com.cosmotech.scenariorun.domain.ScenarioRunStartSolution
import org.springframework.stereotype.Service

@Service
class ScenariorunServiceImpl : AbstractPhoenixService(), ScenariorunApiService {

  override fun deleteScenarioRun(
      organizationId: kotlin.String,
      scenariorunId: kotlin.String
  ): ScenarioRun {
    TODO("Not implemented yet")
  }

  override fun findScenarioRunById(
      organizationId: kotlin.String,
      scenariorunId: kotlin.String
  ): ScenarioRun {
    TODO("Not implemented yet")
  }

  override fun getScenarioScenarioRun(
      organizationId: kotlin.String,
      workspaceId: kotlin.String,
      scenarioId: kotlin.String,
      scenariorunId: kotlin.String
  ): ScenarioRun {
    TODO("Not implemented yet")
  }

  override fun getScenarioScenarioRunLogs(
      organizationId: kotlin.String,
      workspaceId: kotlin.String,
      scenarioId: kotlin.String,
      scenariorunId: kotlin.String
  ): ScenarioRunLogs {
    TODO("Not implemented yet")
  }

  override fun getScenarioScenarioRuns(
      organizationId: kotlin.String,
      workspaceId: kotlin.String,
      scenarioId: kotlin.String
  ): List<ScenarioRunBase> {
    TODO("Not implemented yet")
  }

  override fun getWorkspaceScenarioRuns(
      organizationId: kotlin.String,
      workspaceId: kotlin.String
  ): List<ScenarioRunBase> {
    TODO("Not implemented yet")
  }

  override fun runScenario(
      organizationId: kotlin.String,
      workspaceId: kotlin.String,
      scenarioId: kotlin.String
  ): ScenarioRunBase {
    TODO("Not implemented yet")
  }

  override fun searchScenarioRunLogs(
      organizationId: kotlin.String,
      scenariorunId: kotlin.String,
      scenarioRunLogsOptions: ScenarioRunLogsOptions
  ): ScenarioRunLogs {
    TODO("Not implemented yet")
  }

  override fun searchScenarioRuns(
      organizationId: kotlin.String,
      scenarioRunSearch: ScenarioRunSearch
  ): List<ScenarioRunBase> {
    TODO("Not implemented yet")
  }

  override fun startScenarioRunContainers(
      organizationId: kotlin.String,
      scenarioRunStartContainers: ScenarioRunStartContainers
  ): ScenarioRun {
    TODO("Not implemented yet")
  }

  override fun startScenarioRunScenario(
      organizationId: kotlin.String,
      scenarioRunStart: ScenarioRunStart
  ): ScenarioRun {
    TODO("Not implemented yet")
  }

  override fun startScenarioRunSolution(
      organizationId: kotlin.String,
      scenarioRunStartSolution: ScenarioRunStartSolution
  ): ScenarioRun {
    TODO("Not implemented yet")
  }
}
