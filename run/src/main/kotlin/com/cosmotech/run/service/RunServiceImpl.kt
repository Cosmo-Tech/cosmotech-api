// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run.service

import com.cosmotech.common.CsmPhoenixService
import com.cosmotech.common.events.RunDeleted
import com.cosmotech.common.events.RunStart
import com.cosmotech.common.events.RunStop
import com.cosmotech.common.events.RunnerDeleted
import com.cosmotech.common.events.UpdateRunnerStatus
import com.cosmotech.common.id.generateId
import com.cosmotech.common.rbac.CsmRbac
import com.cosmotech.common.rbac.PERMISSION_DELETE
import com.cosmotech.common.rbac.PERMISSION_READ
import com.cosmotech.common.rbac.PERMISSION_WRITE
import com.cosmotech.common.rbac.getRunnerRolesDefinition
import com.cosmotech.common.utils.constructPageRequest
import com.cosmotech.common.utils.getCurrentAccountIdentifier
import com.cosmotech.run.RunApiServiceInterface
import com.cosmotech.run.RunContainerFactory
import com.cosmotech.run.container.StartInfo
import com.cosmotech.run.domain.Run
import com.cosmotech.run.domain.RunEditInfo
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
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientResponseException

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

    val defaultPageSize = csmPlatformProperties.databases.resources.run.defaultPageSize
    val pageable =
        constructPageRequest(page, size, defaultPageSize) ?: PageRequest.of(0, defaultPageSize)

    return runRepository
        .findByRunnerId(organizationId, workspaceId, runnerId, pageable)
        .toList()
        .map { it.withStateInformation().withoutSensitiveData() }
  }

  override fun listAllRuns(
      organizationId: String,
      workspaceId: String,
      runnerId: String
  ): List<Run> {
    // This call verify the user read authorization in the Runner
    runnerApiService.getRunner(organizationId, workspaceId, runnerId)

    val defaultPageSize = csmPlatformProperties.databases.resources.run.defaultPageSize
    var pageRequest: Pageable = PageRequest.ofSize(defaultPageSize)

    val runs = mutableListOf<Run>()

    do {
      val pagedRuns =
          runRepository.findByRunnerId(organizationId, workspaceId, runnerId, pageRequest)
      runs.addAll(pagedRuns.toList().map { it.withStateInformation().withoutSensitiveData() })
      pageRequest = pagedRuns.nextPageable()
    } while (pagedRuns.hasNext())

    return runs
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

  fun getRunStatus(run: Run): RunStatus {
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
    deleteRun(run)
  }

  private fun deleteRun(run: Run) {
    try {

      workflowService.stopWorkflow(run)

      val defaultPageSize = csmPlatformProperties.databases.resources.run.defaultPageSize
      val pageRequest = PageRequest.ofSize(defaultPageSize)
      val runs =
          runRepository
              .findByRunnerId(run.organizationId!!, run.workspaceId!!, run.runnerId!!, pageRequest)
              .toList()

      val previousRuns = runs.filter { it.id != run.id }
      val lastRun =
          if (previousRuns.isEmpty()) {
            null
          } else {
            previousRuns.maxBy { it.createInfo.timestamp }.id
          }
      val runDeleted =
          RunDeleted(this, run.organizationId, run.workspaceId, run.runnerId, run.id!!, lastRun)
      this.eventPublisher.publishEvent(runDeleted)

      runRepository.delete(run)
    } catch (exception: IllegalStateException) {
      logger.debug(
          "An error occurred while deleting Run {}: {}", run.id, exception.message, exception)
    }
  }

  override fun getRunLogs(
      organizationId: String,
      workspaceId: String,
      runnerId: String,
      runId: String
  ): String {
    val run = getRun(organizationId, workspaceId, runnerId, runId)
    val status = getRunStatus(run)
    if (status.endTime.isNullOrEmpty()) {
      try {
        return workflowService.getRunningLogs(run)
      } catch (e: RestClientResponseException) {
        if (e.statusCode != HttpStatus.NOT_FOUND) throw e
      }
    }
    return workflowService.getArchivedLogs(run)
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
    val runId = generateId("run", prependPrefix = "run-")

    val startInfo =
        containerFactory.getStartInfo(
            runner.organizationId, runner.workspaceId, runner.id, WORKFLOW_TYPE_RUN, runId)
    val runRequest =
        workflowService.launchRun(
            runner.organizationId,
            runner.workspaceId,
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
    val now = Instant.now().toEpochMilli()
    val run =
        runRequest.copy(
            id = runId,
            organizationId = runner.organizationId,
            workspaceId = runner.workspaceId,
            runnerId = runner.id,
            solutionId = startInfo.solution.id,
            runTemplateId = startInfo.runTemplate.id,
            generateName = startInfo.startContainers.generateName,
            computeSize = startInfo.runTemplate.computeSize,
            datasetList = runner.datasets.bases + runner.datasets.parameter,
            createInfo =
                RunEditInfo(
                    timestamp = now, userId = getCurrentAccountIdentifier(csmPlatformProperties)),
            parametersValues =
                (runner.parametersValues.map {
                      RunTemplateParameterValue(
                          parameterId = it.parameterId, varType = it.varType, value = it.value)
                    })
                    .toList(),
            nodeLabel = startInfo.startContainers.nodeLabel,
            containers = startInfo.startContainers.containers,
        )
    logger.info(
        "[organizationId=${run.organizationId}]" +
            "[workspaceId=${run.workspaceId}]" +
            "[runnerId=${run.runnerId}]" +
            "[runId=${run.id}] has been launched by " +
            "[ownerId=${run.createInfo.userId}]")
    return runRepository.save(run)
  }

  @EventListener(UpdateRunnerStatus::class)
  fun updateRunnerStatus(updateRunnerStatus: UpdateRunnerStatus) {
    val organizationId = updateRunnerStatus.organizationId
    val workspaceId = updateRunnerStatus.workspaceId
    val runnerId = updateRunnerStatus.runnerId
    val runId = updateRunnerStatus.lastRunId
    if (runId.isNotEmpty()) {
      val run =
          runRepository
              .findBy(organizationId, workspaceId, runnerId, runId)
              .orElseThrow {
                throw IllegalArgumentException(
                    "Run #$runId not found in #$runnerId. In #$workspaceId, #$organizationId.")
              }
              .withStateInformation()
              .withoutSensitiveData()

      val status = getRunStatus(run).state
      updateRunnerStatus.response = status.toString()
      return
    }
    throw IllegalStateException("LastRunId for runner $runnerId cannot be null!")
  }

  @EventListener(RunStop::class)
  fun onRunStop(runStopRequest: RunStop) {
    val runner = runStopRequest.runnerData as Runner
    val run =
        getRun(runner.organizationId, runner.workspaceId, runner.id, runner.lastRunInfo.lastRunId!!)
    run.hasPermission(PERMISSION_WRITE)

    check(!(run.state!!.isTerminal())) {
      "Run ${run.id} is already in a terminal state (${run.state}). It can't be stopped."
    }

    workflowService.stopWorkflow(run)
    val stoppedRun = run.copy(state = RunState.Failed)
    runRepository.save(stoppedRun)
  }

  private fun Run.hasPermission(permission: String) = apply {
    val runner =
        runnerApiService.getRunner(this.organizationId!!, this.workspaceId!!, this.runnerId!!)
    csmRbac.verify(runner.getRbac(), permission, getRunnerRolesDefinition())
  }

  @EventListener(RunnerDeleted::class)
  fun onRunnerDeleted(runnerDeleted: RunnerDeleted) {
    listAllRuns(runnerDeleted.organizationId, runnerDeleted.workspaceId, runnerDeleted.runnerId)
        .forEach { deleteRun(it) }
  }
}
