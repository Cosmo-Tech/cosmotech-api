// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun.workflow

import com.cosmotech.scenariorun.domain.ScenarioRun
import com.cosmotech.scenariorun.domain.ScenarioRunLogs
import com.cosmotech.scenariorun.domain.ScenarioRunStartContainers
import com.cosmotech.scenariorun.domain.ScenarioRunStatus
import org.springframework.boot.actuate.health.HealthIndicator

interface WorkflowService : HealthIndicator {

  /**
   * Launch a new Scenario run, using the request specified
   * @param scenarioRunStartContainers the scenario run start request
   * @return a new ScenarioRun
   */
  fun launchScenarioRun(scenarioRunStartContainers: ScenarioRunStartContainers): ScenarioRun

  /**
   * Get a ScenarioRun status
   * @param scenarioRun the ScenarioRun
   * @return the ScenarioRun status
   */
  fun getScenarioRunStatus(scenarioRun: ScenarioRun): ScenarioRunStatus

  /**
   * Get all logs of a ScenarioRun, as a structured object
   * @param scenarioRun the ScenarioRun
   * @return the ScenarioRunLogs object
   */
  fun getScenarioRunLogs(scenarioRun: ScenarioRun): ScenarioRunLogs

  /**
   * Get all cumulated logs of a ScenarioRun, as a single String. All logs are loaded into memory
   * and there is no streaming here, so use this with caution.
   * @param scenarioRun the ScenarioRun
   * @return all the ScenarioRun logs
   */
  fun getScenarioRunCumulatedLogs(scenarioRun: ScenarioRun): String
}
