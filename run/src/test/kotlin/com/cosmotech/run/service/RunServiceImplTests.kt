// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run.service

import com.cosmotech.common.events.RunStop
import com.cosmotech.common.rbac.CsmRbac
import com.cosmotech.common.rbac.ROLE_ADMIN
import com.cosmotech.run.RunContainerFactory
import com.cosmotech.run.domain.Run
import com.cosmotech.run.domain.RunEditInfo
import com.cosmotech.run.domain.RunState
import com.cosmotech.run.domain.RunStatus
import com.cosmotech.run.repository.RunRepository
import com.cosmotech.run.workflow.WorkflowService
import com.cosmotech.runner.RunnerApiServiceInterface
import com.cosmotech.runner.domain.LastRunInfo
import com.cosmotech.runner.domain.Runner
import com.cosmotech.runner.domain.RunnerAccessControl
import com.cosmotech.runner.domain.RunnerDatasets
import com.cosmotech.runner.domain.RunnerEditInfo
import com.cosmotech.runner.domain.RunnerSecurity
import com.cosmotech.runner.domain.RunnerValidationStatus
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import java.util.Optional
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.extension.ExtendWith

private const val ORGANIZATION_ID = "o-organizationid"
private const val WORKSPACE_ID = "w-workspaceid"
private const val RUNNER_ID = "r-runnerid"
private const val RUN_ID = "run-runid"

@ExtendWith(MockKExtension::class)
class RunServiceImplTests {

  @Suppress("unused") @MockK private lateinit var containerFactory: RunContainerFactory
  @MockK private lateinit var workflowService: WorkflowService
  @MockK private lateinit var runnerApiService: RunnerApiServiceInterface
  @MockK private lateinit var runRepository: RunRepository
  @Suppress("unused") @RelaxedMockK private lateinit var csmRbac: CsmRbac

  @InjectMockKs private lateinit var runServiceImpl: RunServiceImpl

  @BeforeTest
  fun setUp() {
    MockKAnnotations.init(this)
  }

  private fun buildRunner(lastRunId: String? = RUN_ID) =
      Runner(
          id = RUNNER_ID,
          name = "runner",
          createInfo = RunnerEditInfo(timestamp = 0L, userId = "user"),
          updateInfo = RunnerEditInfo(timestamp = 0L, userId = "user"),
          solutionId = "sol-id",
          runTemplateId = "runtemplate-id",
          organizationId = ORGANIZATION_ID,
          workspaceId = WORKSPACE_ID,
          datasets = RunnerDatasets(bases = mutableListOf(), parameter = ""),
          parametersValues = mutableListOf(),
          lastRunInfo =
              LastRunInfo(
                  lastRunId = lastRunId,
                  lastRunStatus = LastRunInfo.LastRunStatus.Running,
              ),
          validationStatus = RunnerValidationStatus.Validated,
          security =
              RunnerSecurity(
                  default = ROLE_ADMIN,
                  accessControlList =
                      mutableListOf(RunnerAccessControl(id = "user", role = ROLE_ADMIN)),
              ),
      )

  private fun buildRun(state: RunState?) =
      Run(
          id = RUN_ID,
          organizationId = ORGANIZATION_ID,
          workspaceId = WORKSPACE_ID,
          runnerId = RUNNER_ID,
          state = state,
          createInfo = RunEditInfo(timestamp = 0L, userId = "user"),
      )

  @Test
  fun `onRunStop stops workflow and sets run state to Failed when run is not in terminal state`() {
    val runner = buildRunner()
    val run = buildRun(RunState.Running)
    val runStopRequest = RunStop(this, runner)

    every { runnerApiService.getRunner(ORGANIZATION_ID, WORKSPACE_ID, RUNNER_ID) } returns runner
    every { runRepository.findBy(ORGANIZATION_ID, WORKSPACE_ID, RUNNER_ID, RUN_ID) } returns
        Optional.of<Run>(run)
    every { workflowService.getRunStatus(run) } returns RunStatus(id = RUN_ID, phase = "Running")
    every { workflowService.stopWorkflow(run) } returns RunStatus(id = RUN_ID)

    val savedRunSlot = slot<Run>()
    every { runRepository.save(capture(savedRunSlot)) } answers { savedRunSlot.captured }

    runServiceImpl.onRunStop(runStopRequest)

    verify(exactly = 1) { workflowService.stopWorkflow(run) }
    assertEquals(RunState.Failed, savedRunSlot.captured.state)
  }

  @Test
  fun `onRunStop throws IllegalStateException when run is Successful (terminal state)`() {
    val runner = buildRunner()
    val run = buildRun(RunState.Successful)
    val runStopRequest = RunStop(this, runner)

    every { runnerApiService.getRunner(ORGANIZATION_ID, WORKSPACE_ID, RUNNER_ID) } returns runner
    every { runRepository.findBy(ORGANIZATION_ID, WORKSPACE_ID, RUNNER_ID, RUN_ID) } returns
        Optional.of(run)

    assertFailsWith<IllegalStateException> { runServiceImpl.onRunStop(runStopRequest) }

    verify(exactly = 0) { workflowService.stopWorkflow(any()) }
    verify(exactly = 0) { runRepository.save(any()) }
  }

  @Test
  fun `onRunStop throws IllegalStateException when run is Failed (terminal state)`() {
    val runner = buildRunner()
    val run = buildRun(RunState.Failed)
    val runStopRequest = RunStop(this, runner)

    every { runnerApiService.getRunner(ORGANIZATION_ID, WORKSPACE_ID, RUNNER_ID) } returns runner
    every { runRepository.findBy(ORGANIZATION_ID, WORKSPACE_ID, RUNNER_ID, RUN_ID) } returns
        Optional.of(run)

    assertFailsWith<IllegalStateException> { runServiceImpl.onRunStop(runStopRequest) }

    verify(exactly = 0) { workflowService.stopWorkflow(any()) }
    verify(exactly = 0) { runRepository.save(any()) }
  }
}
