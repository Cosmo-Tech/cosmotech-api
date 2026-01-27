// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run.service

import com.cosmotech.common.config.CsmPlatformProperties
import com.cosmotech.common.events.RunStart
import com.cosmotech.common.rbac.ROLE_ADMIN
import com.cosmotech.common.rbac.ROLE_NONE
import com.cosmotech.common.tests.CsmTestBase
import com.cosmotech.common.utils.getCurrentAccountGroups
import com.cosmotech.common.utils.getCurrentAccountIdentifier
import com.cosmotech.common.utils.getCurrentAuthenticatedRoles
import com.cosmotech.common.utils.getCurrentAuthenticatedUserName
import com.cosmotech.dataset.DatasetApiServiceInterface
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.dataset.domain.DatasetCreateRequest
import com.cosmotech.dataset.domain.DatasetPart
import com.cosmotech.organization.OrganizationApiServiceInterface
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationCreateRequest
import com.cosmotech.organization.domain.OrganizationSecurity
import com.cosmotech.run.RunApiServiceInterface
import com.cosmotech.run.RunContainerFactory
import com.cosmotech.run.domain.Run
import com.cosmotech.run.domain.RunEditInfo
import com.cosmotech.run.domain.RunStatus
import com.cosmotech.run.workflow.WorkflowService
import com.cosmotech.runner.RunnerApiServiceInterface
import com.cosmotech.runner.domain.LastRunInfo
import com.cosmotech.runner.domain.LastRunInfo.LastRunStatus
import com.cosmotech.runner.domain.Runner
import com.cosmotech.runner.domain.RunnerAccessControl
import com.cosmotech.runner.domain.RunnerCreateRequest
import com.cosmotech.runner.domain.RunnerDatasets
import com.cosmotech.runner.domain.RunnerEditInfo
import com.cosmotech.runner.domain.RunnerSecurity
import com.cosmotech.runner.domain.RunnerValidationStatus
import com.cosmotech.solution.SolutionApiServiceInterface
import com.cosmotech.solution.domain.*
import com.cosmotech.workspace.WorkspaceApiServiceInterface
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceAccessControl
import com.cosmotech.workspace.domain.WorkspaceCreateRequest
import com.cosmotech.workspace.domain.WorkspaceSecurity
import com.cosmotech.workspace.domain.WorkspaceSolution
import com.ninjasquad.springmockk.SpykBean
import com.redis.om.spring.indexing.RediSearchIndexer
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.mockkStatic
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.web.client.RestClientResponseException

@ActiveProfiles(profiles = ["run-test"])
@ExtendWith(MockKExtension::class)
@ExtendWith(SpringExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RunServiceIntegrationTest : CsmTestBase() {

  val CONNECTED_ADMIN_USER = "test.admin@cosmotech.com"
  val CONNECTED_READER_USER = "test.user@cosmotech.com"

  private val logger = LoggerFactory.getLogger(RunServiceIntegrationTest::class.java)

  @MockK(relaxed = true) private lateinit var containerFactory: RunContainerFactory
  @MockK(relaxed = true) private lateinit var workflowService: WorkflowService

  @Autowired lateinit var csmPlatformProperties: CsmPlatformProperties
  @Autowired lateinit var rediSearchIndexer: RediSearchIndexer
  @Autowired lateinit var organizationApiService: OrganizationApiServiceInterface
  @Autowired lateinit var datasetApiService: DatasetApiServiceInterface
  @Autowired lateinit var solutionApiService: SolutionApiServiceInterface
  @Autowired lateinit var workspaceApiService: WorkspaceApiServiceInterface
  @Autowired lateinit var runnerApiService: RunnerApiServiceInterface
  @SpykBean lateinit var runServiceImpl: RunServiceImpl
  @Autowired lateinit var runApiService: RunApiServiceInterface
  @Autowired lateinit var eventPublisher: com.cosmotech.common.events.CsmEventPublisher

  lateinit var dataset: DatasetCreateRequest
  lateinit var solution: SolutionCreateRequest
  lateinit var organization: OrganizationCreateRequest
  lateinit var workspace: WorkspaceCreateRequest
  lateinit var runner: RunnerCreateRequest

  lateinit var datasetSaved: Dataset
  lateinit var solutionSaved: Solution
  lateinit var organizationSaved: Organization
  lateinit var workspaceSaved: Workspace
  lateinit var runnerSaved: Runner
  lateinit var runSavedId: String

  @BeforeEach
  fun setUp() {
    mockkStatic("com.cosmotech.common.utils.SecurityUtilsKt")
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
    every { getCurrentAccountGroups(any()) } returns listOf("myTestGroup")
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "test.user"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("user")

    ReflectionTestUtils.setField(runApiService, "containerFactory", containerFactory)
    ReflectionTestUtils.setField(runApiService, "workflowService", workflowService)
    ReflectionTestUtils.setField(runApiService, "eventPublisher", eventPublisher)

    rediSearchIndexer.createIndexFor(Solution::class.java)
    rediSearchIndexer.createIndexFor(Workspace::class.java)
    rediSearchIndexer.createIndexFor(Dataset::class.java)
    rediSearchIndexer.createIndexFor(DatasetPart::class.java)
    rediSearchIndexer.createIndexFor(Runner::class.java)
    rediSearchIndexer.createIndexFor(Run::class.java)

    organization = makeOrganizationCreateRequest()
    organizationSaved = organizationApiService.createOrganization(organization)

    solution = makeSolutionCreateRequest()
    solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

    workspace = makeWorkspaceCreateRequest(solutionSaved.id, "Workspace")
    workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id, workspace)

    dataset = makeDatasetCreateRequest()

    datasetSaved =
        datasetApiService.createDataset(
            organizationSaved.id,
            workspaceSaved.id,
            dataset,
            emptyArray(),
        )

    runner =
        makeRunnerCreateRequest(
            solutionSaved.id,
            solutionSaved.runTemplates[0].id,
            "Runner",
            mutableListOf(datasetSaved.id),
        )
    runnerSaved = runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)

    every { workflowService.launchRun(any(), any(), any(), any()) } returns
        mockWorkflowRun(organizationSaved.id, workspaceSaved.id, runnerSaved.id)
  }

  fun makeDatasetCreateRequest() = DatasetCreateRequest(name = "Dataset Test")

  fun makeSolutionCreateRequest() =
      SolutionCreateRequest(
          key = UUID.randomUUID().toString(),
          name = "My solution",
          runTemplates =
              mutableListOf(
                  RunTemplateCreateRequest(
                      id = UUID.randomUUID().toString(),
                      name = "RunTemplate1",
                      description = "RunTemplate1 description",
                  )
              ),
          parameters = mutableListOf(RunTemplateParameterCreateRequest("parameter", "string")),
          version = "1.0.0",
          repository = "repository",
          parameterGroups = mutableListOf(RunTemplateParameterGroupCreateRequest("group")),
          security =
              SolutionSecurity(
                  default = ROLE_NONE,
                  accessControlList =
                      mutableListOf(
                          SolutionAccessControl(id = CONNECTED_ADMIN_USER, role = ROLE_ADMIN),
                          SolutionAccessControl(id = CONNECTED_READER_USER, role = ROLE_ADMIN),
                      ),
              ),
      )

  fun makeOrganizationCreateRequest() =
      OrganizationCreateRequest(
          name = "Organization Name",
          security =
              OrganizationSecurity(
                  default = ROLE_NONE,
                  accessControlList =
                      mutableListOf(
                          OrganizationAccessControl(id = CONNECTED_READER_USER, role = "reader"),
                          OrganizationAccessControl(id = CONNECTED_ADMIN_USER, role = "admin"),
                      ),
              ),
      )

  fun makeWorkspaceCreateRequest(
      solutionId: String = solutionSaved.id,
      name: String = "workspace",
  ) =
      WorkspaceCreateRequest(
          key = UUID.randomUUID().toString(),
          name = name,
          solution =
              WorkspaceSolution(
                  solutionId = solutionId,
              ),
          security =
              WorkspaceSecurity(
                  ROLE_NONE,
                  mutableListOf(WorkspaceAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN)),
              ),
      )

  fun makeRunnerCreateRequest(
      solutionId: String = solutionSaved.id,
      runTemplateId: String = solutionSaved.runTemplates[0].id,
      name: String = "runner",
      datasetList: MutableList<String> = mutableListOf(datasetSaved.id),
  ) =
      RunnerCreateRequest(
          name = name,
          solutionId = solutionId,
          runTemplateId = runTemplateId,
          datasetList = datasetList,
          security =
              RunnerSecurity(
                  ROLE_NONE,
                  mutableListOf(RunnerAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN)),
              ),
      )

  fun mockStartRun(
      organizationId: String,
      workspaceId: String,
      runnerId: String,
      solutionId: String,
  ): String {
    val runner =
        Runner(
            id = runnerId,
            solutionId = solutionId,
            organizationId = organizationId,
            name = "name",
            runTemplateId = "runTemplate",
            createInfo =
                RunnerEditInfo(
                    timestamp = Instant.now().toEpochMilli(),
                    userId = getCurrentAccountIdentifier(csmPlatformProperties),
                ),
            updateInfo =
                RunnerEditInfo(
                    timestamp = Instant.now().toEpochMilli(),
                    userId = getCurrentAccountIdentifier(csmPlatformProperties),
                ),
            datasets = RunnerDatasets(bases = mutableListOf(), parameter = ""),
            workspaceId = workspaceId,
            validationStatus = RunnerValidationStatus.Draft,
            parametersValues = mutableListOf(),
            lastRunInfo = LastRunInfo(lastRunId = null, lastRunStatus = LastRunStatus.NotStarted),
            security =
                RunnerSecurity(ROLE_ADMIN, mutableListOf(RunnerAccessControl("user", ROLE_ADMIN))),
        )
    val runStart = RunStart(this, runner)
    eventPublisher.publishEvent(runStart)
    return runStart.response!!
  }

  fun mockWorkflowRun(organizationId: String, workspaceId: String, runnerId: String): Run {
    return Run(
        organizationId = organizationId,
        workspaceId = workspaceId,
        runnerId = runnerId,
        createInfo =
            RunEditInfo(
                timestamp = Instant.now().toEpochMilli(),
                userId = getCurrentAccountIdentifier(csmPlatformProperties),
            ),
    )
  }

  @Test
  fun `test CRUD operations on Run`() {
    logger.info("test CRUD operations on Run")
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")

    logger.info("should create 1 run")

    runSavedId =
        mockStartRun(organizationSaved.id, workspaceSaved.id, runnerSaved.id, solutionSaved.id)
    assertNotEquals("", runSavedId)

    logger.info("should find 1 Run")
    var runs =
        runApiService.listRuns(organizationSaved.id, workspaceSaved.id, runnerSaved.id, null, null)
    assertEquals(1, runs.size)

    logger.info("should find Run by id")
    val foundRun =
        runApiService.getRun(organizationSaved.id, workspaceSaved.id, runnerSaved.id, runSavedId)
    assertEquals(runSavedId, foundRun.id)
    assertEquals(organizationSaved.id, foundRun.organizationId)
    assertEquals(workspaceSaved.id, foundRun.workspaceId)
    assertEquals(runnerSaved.id, foundRun.runnerId)

    logger.info("should create second Run")
    val runSaved2id =
        mockStartRun(organizationSaved.id, workspaceSaved.id, runnerSaved.id, solutionSaved.id)

    logger.info("should find all Runs by Runner id and assert size is 2")
    runs =
        runApiService.listRuns(organizationSaved.id, workspaceSaved.id, runnerSaved.id, null, null)
    assertEquals(2, runs.size)

    logger.info("should delete second Run and assert size is 1")
    runApiService.deleteRun(organizationSaved.id, workspaceSaved.id, runnerSaved.id, runSaved2id)
    runs =
        runApiService.listRuns(organizationSaved.id, workspaceSaved.id, runnerSaved.id, null, null)
    assertEquals(1, runs.size)
  }

  @Test
  fun `test find All Runs with different pagination params`() {
    val numberOfRuns = 20
    val defaultPageSize = csmPlatformProperties.databases.resources.run.defaultPageSize
    val expectedSize = 15

    IntRange(1, numberOfRuns).forEach {
      mockStartRun(organizationSaved.id, workspaceSaved.id, runnerSaved.id, solutionSaved.id)
    }

    logger.info("should find all Runs and assert there are $numberOfRuns")
    var runs =
        runApiService.listRuns(
            organizationSaved.id,
            workspaceSaved.id,
            runnerSaved.id,
            null,
            numberOfRuns * 2,
        )
    assertEquals(numberOfRuns, runs.size)

    logger.info("should find all Runs and assert it equals defaultPageSize: $defaultPageSize")
    runs = runApiService.listRuns(organizationSaved.id, workspaceSaved.id, runnerSaved.id, 0, null)
    assertEquals(defaultPageSize, runs.size)

    logger.info("should find all Runs and assert there are expected size: $expectedSize")
    runs =
        runApiService.listRuns(
            organizationSaved.id,
            workspaceSaved.id,
            runnerSaved.id,
            0,
            expectedSize,
        )
    assertEquals(expectedSize, runs.size)

    logger.info("should find all Runs and assert it returns the second / last page")
    runs =
        runApiService.listRuns(
            organizationSaved.id,
            workspaceSaved.id,
            runnerSaved.id,
            1,
            expectedSize,
        )
    assertEquals(numberOfRuns - expectedSize, runs.size)
  }

  @Test
  fun `test find All Runs with wrong pagination params`() {
    logger.info("Should throw IllegalArgumentException when page and size are zeros")
    assertThrows<IllegalArgumentException> {
      runApiService.listRuns(organizationSaved.id, workspaceSaved.id, runnerSaved.id, 0, 0)
    }

    logger.info("Should throw IllegalArgumentException when page is negative")
    assertThrows<IllegalArgumentException> {
      runApiService.listRuns(organizationSaved.id, workspaceSaved.id, runnerSaved.id, -1, 10)
    }

    logger.info("Should throw IllegalArgumentException when size is negative")
    assertThrows<IllegalArgumentException> {
      runApiService.listRuns(organizationSaved.id, workspaceSaved.id, runnerSaved.id, 0, -1)
    }
  }

  @Test
  fun `test get running logs`() {
    runSavedId =
        mockStartRun(organizationSaved.id, workspaceSaved.id, runnerSaved.id, solutionSaved.id)
    val run = mockk<Run>()
    every { runApiService.getRun(any(), any(), any(), any()) } returns run
    every { run.id } returns "id"
    every { runServiceImpl.getRunStatus(any(), any(), any(), any()) } returns
        RunStatus(endTime = null)
    every { workflowService.getRunningLogs(any()) } returns
        "first line of result" + "|second line of result" + "|third line of result"
    every { workflowService.getArchivedLogs(any()) } throws Exception()

    assertEquals(
        "first line of result" + "|second line of result" + "|third line of result",
        runApiService.getRunLogs(
            organizationSaved.id,
            workspaceSaved.id,
            runnerSaved.id,
            runSavedId,
        ),
    )
  }

  @Test
  fun `test get archived logs`() {
    runSavedId =
        mockStartRun(organizationSaved.id, workspaceSaved.id, runnerSaved.id, solutionSaved.id)
    val run = mockk<Run>()
    every { runApiService.getRun(any(), any(), any(), any()) } returns run
    every { run.id } returns "id"
    every { runServiceImpl.getRunStatus(any(), any(), any(), any()) } returns
        RunStatus(endTime = "endTime")
    logger.info("should get archived logs")
    every { workflowService.getRunningLogs(any()) } throws
        RestClientResponseException("message", HttpStatus.NOT_FOUND, "statusTest", null, null, null)
    every { workflowService.getArchivedLogs(any()) } returns
        "first line of result" + "|second line of result" + "|third line of result"
    assertEquals(
        "first line of result" + "|second line of result" + "|third line of result",
        runApiService.getRunLogs(
            organizationSaved.id,
            workspaceSaved.id,
            runnerSaved.id,
            runSavedId,
        ),
    )
  }

  @Test
  fun `test should get archived logs after a status change`() {
    runSavedId =
        mockStartRun(organizationSaved.id, workspaceSaved.id, runnerSaved.id, solutionSaved.id)
    val run = mockk<Run>()
    every { runApiService.getRun(any(), any(), any(), any()) } returns run
    every { run.id } returns "id"
    every { runServiceImpl.getRunStatus(any(), any(), any(), any()) } returns
        RunStatus(endTime = null)
    logger.info("should get logs even with a status change after assertion")
    every { workflowService.getRunningLogs(any()) } throws
        RestClientResponseException("message", HttpStatus.NOT_FOUND, "statusTest", null, null, null)
    every { workflowService.getArchivedLogs(any()) } returns
        "first line of result" + "|second line of result" + "|third line of result"
    assertDoesNotThrow {
      runApiService.getRunLogs(organizationSaved.id, workspaceSaved.id, runnerSaved.id, runSavedId)
    }
    assertEquals(
        "first line of result" + "|second line of result" + "|third line of result",
        runApiService.getRunLogs(
            organizationSaved.id,
            workspaceSaved.id,
            runnerSaved.id,
            runSavedId,
        ),
    )
  }

  @Test
  fun `test should fail after exception other than not_found`() {
    runSavedId =
        mockStartRun(organizationSaved.id, workspaceSaved.id, runnerSaved.id, solutionSaved.id)
    val run = mockk<Run>()
    every { runApiService.getRun(any(), any(), any(), any()) } returns run
    every { runServiceImpl.getRunStatus(any(), any(), any(), any()) } returns
        RunStatus(endTime = null)
    logger.info("should throw an error outside of status change error")
    every { workflowService.getRunningLogs(any()) } throws Exception()
    assertThrows<Exception> {
      runApiService.getRunLogs(organizationSaved.id, workspaceSaved.id, runnerSaved.id, runSavedId)
    }
  }
}
