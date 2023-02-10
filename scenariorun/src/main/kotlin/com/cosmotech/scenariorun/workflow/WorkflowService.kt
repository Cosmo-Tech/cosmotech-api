// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun.workflow

import com.cosmotech.scenariorun.domain.ScenarioRun
import com.cosmotech.scenariorun.domain.ScenarioRunLogs
import com.cosmotech.scenariorun.domain.ScenarioRunStartContainers
import com.cosmotech.scenariorun.domain.ScenarioRunStatus
import org.springframework.boot.actuate.health.HealthIndicator

internal interface WorkflowService : HealthIndicator {

  /**
   * Launch a new Scenario run, using the request specified
   * @param scenarioRunStartContainers the scenario run start request
   * @param executionTimeout the duration in which the workflow is allowed to run
   * @return a new ScenarioRun
   */
  fun launchScenarioRun(
      scenarioRunStartContainers: ScenarioRunStartContainers,
      executionTimeout: Int?
  ): ScenarioRun

  /**
   * Find WorkflowStatus by label and artifact name filter
   * @param labelSelector a string used to filter workflow (by label)
   * @param artifactNameFilter a string used to filter workflow (by artifactName)
   * @return a list of all WorkflowStatus corresponding to the labelSelector and artifactName
   */
  fun findWorkflowStatusAndArtifact(
      labelSelector: String,
      artifactNameFilter: String
  ): List<WorkflowStatusAndArtifact>

  /**
   * Find WorkflowStatus by label
   * @param labelSelector a label used to filter workflow
   * @param skipArchive Do not search workflows in archive
   * @return a list of all WorkflowStatus corresponding to the labelSelector
   */
  fun findWorkflowStatusByLabel(
      labelSelector: String,
      skipArchive: Boolean = false
  ): List<WorkflowStatus>

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

  /**
   * Stop the running workflow
   * @param scenarioRun the scenarioRun object
   * @return the ScenarioRun status
   */
  fun stopWorkflow(scenarioRun: ScenarioRun): ScenarioRunStatus
}

data class WorkflowStatusAndArtifact(
    val workflowId: String,
    val status: String? = null,
    val artifactContent: String? = null
)

data class WorkflowStatus(val workflowId: String, val status: String? = null)
