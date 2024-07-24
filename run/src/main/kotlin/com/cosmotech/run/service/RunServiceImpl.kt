// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run.service

import com.cosmotech.api.CsmPhoenixService
import com.cosmotech.api.events.RunStart
import com.cosmotech.api.events.RunStop
import com.cosmotech.api.rbac.CsmRbac
import com.cosmotech.api.rbac.PERMISSION_DELETE
import com.cosmotech.api.rbac.PERMISSION_READ
import com.cosmotech.api.rbac.PERMISSION_WRITE
import com.cosmotech.api.rbac.getScenarioRolesDefinition
import com.cosmotech.api.utils.constructPageRequest
import com.cosmotech.run.RunApiServiceInterface
import com.cosmotech.run.RunContainerFactory
import com.cosmotech.run.container.StartInfo
import com.cosmotech.run.domain.Run
import com.cosmotech.run.domain.RunLogs
import com.cosmotech.run.domain.RunState
import com.cosmotech.run.domain.RunStatus
import com.cosmotech.run.domain.RunTemplateParameterValue
import com.cosmotech.run.repository.RunRepository
import com.cosmotech.run.utils.isTerminal
import com.cosmotech.run.utils.withoutSensitiveData
import com.cosmotech.run.workflow.WorkflowService
import com.cosmotech.runner.RunnerApiServiceInterface
import com.cosmotech.runner.domain.Runner
import com.cosmotech.runner.service.getRbac
import java.time.Instant
import org.springframework.context.event.EventListener
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

internal const val WORKFLOW_TYPE_RUN = "container-run"

@Service
@Suppress("TooManyFunctions")
class RunServiceImpl(
    private val containerFactory: RunContainerFactory,
    private val workflowService: WorkflowService,
    private val runnerApiService: RunnerApiServiceInterface,
    private val runRepository: RunRepository,
    private val csmRbac: CsmRbac
) : CsmPhoenixService(), RunApiServiceInterface {

  override fun listRuns(
      organizationId: String,
      workspaceId: String,
      runnerId: String,
      page: Int?,
      size: Int?
  ): List<Run> {
    // This call verify the user read authorization in the Runner
    runnerApiService.getRunner(organizationId, workspaceId, runnerId)

    val defaultPageSize = csmPlatformProperties.twincache.scenariorun.defaultPageSize
    val pageable =
        constructPageRequest(page, size, defaultPageSize) ?: PageRequest.of(0, defaultPageSize)

    return runRepository
        .findByRunnerId(organizationId, workspaceId, runnerId, pageable)
        .toList()
        .map { it.withStateInformation().withoutSensitiveData() }
  }

  private fun Run.withStateInformation(): Run {
    if (this.state?.isTerminal() == true) {
      return this
    }

    val state = getRunStatus(this).state
    val run = this.copy(state = state)

    if (state?.isTerminal() == true) {
      runRepository.save(run)
    }
    return run
  }

  private fun getRunStatus(run: Run): RunStatus {
    val runStatus = this.workflowService.getRunStatus(run)
    return runStatus.copy(
        state = mapWorkflowPhaseToRunStatus(phase = runStatus.phase, runId = run.id))
  }

  private fun mapWorkflowPhaseToRunStatus(phase: String?, runId: String?): RunState {
    return when (phase) {
      "Pending",
      "Running" -> RunState.Running
      "Succeeded" -> RunState.Successful
      "Skipped",
      "Failed",
      "Error",
      "Omitted" -> RunState.Failed
      else -> {
        logger.warn("Unhandled state response for job {}: {} => Returning Unknown", runId, phase)
        RunState.Unknown
      }
    }
  }

  override fun getRun(
      organizationId: String,
      workspaceId: String,
      runnerId: String,
      runId: String
  ): Run {
    runnerApiService.getRunner(organizationId, workspaceId, runnerId)
    val run =
        runRepository
            .findBy(organizationId, workspaceId, runnerId, runId)
            .orElseThrow {
              throw IllegalArgumentException(
                  "Run #$runId not found in #$runnerId. In #$workspaceId, #$organizationId.")
            }
            .withStateInformation()
            .withoutSensitiveData()

    run.hasPermission(PERMISSION_READ)
    return run
  }

  override fun deleteRun(
      organizationId: String,
      workspaceId: String,
      runnerId: String,
      runId: String
  ) {
    val run = this.getRun(organizationId, workspaceId, runnerId, runId)
    run.hasPermission(PERMISSION_DELETE)
    try {
      runRepository.delete(run)
    } catch (exception: IllegalStateException) {
      logger.debug(
          "An error occured while deleteting Run {}: {}", run.id, exception.message, exception)
    }
  }

  override fun getRunLogs(
      organizationId: String,
      workspaceId: String,
      runnerId: String,
      runId: String
  ): RunLogs {
    return workflowService.getRunLogs(getRun(organizationId, workspaceId, runnerId, runId))
  }

  override fun getRunStatus(
      organizationId: String,
      workspaceId: String,
      runnerId: String,
      runId: String
  ): RunStatus {
    return getRunStatus(this.getRun(organizationId, workspaceId, runnerId, runId))
  }

  @EventListener(RunStart::class)
  fun onRunStart(runStartRequest: RunStart) {
    val runner = runStartRequest.runnerData as Runner
    val runId = idGenerator.generate("run", prependPrefix = "run-")

    val startInfo =
        containerFactory.getStartInfo(
            runner.organizationId!!, runner.workspaceId!!, runner.id!!, WORKFLOW_TYPE_RUN, runId)
    val runRequest =
        workflowService.launchRun(
            startInfo.startContainers,
            startInfo.runTemplate.executionTimeout,
            startInfo.solution.alwaysPull ?: false)
    val run = this.dbCreateRun(runId, runner, startInfo, runRequest)
    runStartRequest.response = run.id
  }

  private fun dbCreateRun(
      runId: String,
      runner: Runner,
      startInfo: StartInfo,
      runRequest: Run
  ): Run {
    val now = Instant.now().toString()
    var run =
        runRequest.copy(
            id = runId,
            ownerId = runner.ownerId,
            organizationId = runner.organizationId,
            workspaceId = runner.workspaceId,
            runnerId = runner.id,
            solutionId = startInfo.solution.id,
            runTemplateId = startInfo.runTemplate.id,
            generateName = startInfo.startContainers.generateName,
            computeSize = startInfo.runTemplate.computeSize,
            datasetList = runner.datasetList,
            createdAt = now,
            parametersValues =
                (runner.parametersValues?.map {
                      RunTemplateParameterValue(
                          parameterId = it.parameterId, varType = it.varType, value = it.value)
                    })
                    ?.toList(),
            nodeLabel = startInfo.startContainers.nodeLabel,
            containers = startInfo.startContainers.containers,
        )
    return runRepository.save(run)
  }

  @EventListener(RunStop::class)
  fun onRunStop(runStopRequest: RunStop) {
    val runner = runStopRequest.runnerData as Runner
    val run =
        getRun(
            runner.organizationId!!,
            runner.workspaceId!!,
            runner.id!!,
            runner.lastRun!!.runnerRunId!!)
    run.hasPermission(PERMISSION_WRITE)
    if (run.state!!.isTerminal()) {
      throw IllegalStateException(
          "Run ${run.id} is already in a terminal state (${run.state}). It can't be stopped.")
    }
    workflowService.stopWorkflow(run)
    val stoppedRun = run.copy(state = RunState.Failed)
    runRepository.save(stoppedRun)
  }

  private fun Run.hasPermission(permission: String) = apply {
    val runner =
        runnerApiService.getRunner(this.organizationId!!, this.workspaceId!!, this.runnerId!!)
    csmRbac.verify(runner.getRbac(), permission, getScenarioRolesDefinition())
  }
}
