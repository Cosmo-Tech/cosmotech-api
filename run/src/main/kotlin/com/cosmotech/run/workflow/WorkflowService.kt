// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run.workflow

import com.cosmotech.run.domain.Run
import com.cosmotech.run.domain.RunLogs
import com.cosmotech.run.domain.RunStartContainers
import com.cosmotech.run.domain.RunStatus
import org.springframework.boot.actuate.health.HealthIndicator

interface WorkflowService : HealthIndicator {

  /**
   * Launch a new Scenario run, using the request specified
   * @param runStartContainers the scenario run start request
   * @param executionTimeout the duration in which the workflow is allowed to run
   * @param alwaysPull the image pull policy
   * @return a new Run
   */
  fun launchRun(
      runStartContainers: RunStartContainers,
      executionTimeout: Int?,
      alwaysPull: Boolean = false
  ): Run

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

data class WorkflowStatusAndArtifact(
    val workflowId: String,
    val status: String? = null,
    val artifactContent: String? = null
)

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
