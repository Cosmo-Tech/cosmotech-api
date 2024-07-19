// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run.workflow

import com.cosmotech.run.domain.Run
import com.cosmotech.run.domain.RunContainer
import com.cosmotech.run.domain.RunLogs
import com.cosmotech.run.domain.RunStatus
import org.springframework.boot.actuate.health.HealthIndicator

data class RunStartContainers(
    val generateName: String? = null,
    val csmSimulationId: String,
    val nodeLabel: String? = null,
    val labels: Map<String, String>? = null,
    val containers: List<RunContainer>
)

interface WorkflowService : HealthIndicator {

  /**
   * Launch a new Scenario run, using the request specified
   * @param runStartContainers the scenario run start request
   * @param executionTimeout the duration in which the workflow is allowed to run
   * @param alwaysPull the image pull policy
   * @return a new Run
   */
  fun launchRun(
      organizationId: String,
      workspaceId: String?,
      runStartContainers: RunStartContainers,
      executionTimeout: Int?,
      alwaysPull: Boolean = false
  ): Run

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
   * Get a Run status
   * @param run the Run
   * @return the Run status
   */
  fun getRunStatus(run: Run): RunStatus

  /**
   * Get all logs of a Run, as a structured object
   * @param run the Run
   * @return the RunLogs object
   */
  fun getRunLogs(run: Run): RunLogs

  /**
   * Stop the running workflow
   * @param run the run object
   * @return the Run status
   */
  fun stopWorkflow(run: Run): RunStatus
}

data class WorkflowContextData(
    val organizationId: String? = null,
    val workspaceId: String? = null,
    val runnerId: String? = null,
)

data class WorkflowStatus(
    val workflowId: String,
    val status: String? = null,
    val contextData: WorkflowContextData? = null
)
