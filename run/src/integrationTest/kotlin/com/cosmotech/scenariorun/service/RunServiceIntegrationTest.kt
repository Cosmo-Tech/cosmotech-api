// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run.service

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.events.RunStart
import com.cosmotech.api.rbac.ROLE_ADMIN
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.tests.CsmRedisTestBase
import com.cosmotech.api.utils.getCurrentAccountIdentifier
import com.cosmotech.api.utils.getCurrentAuthenticatedRoles
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.connector.api.ConnectorApiService
import com.cosmotech.connector.domain.Connector
import com.cosmotech.dataset.api.DatasetApiService
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.dataset.domain.DatasetConnector
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationSecurity
import com.cosmotech.run.ContainerFactory
import com.cosmotech.run.domain.Run
import com.cosmotech.run.workflow.WorkflowService
import com.cosmotech.runner.api.RunnerApiService
import com.cosmotech.runner.domain.Runner
import com.cosmotech.runner.domain.RunnerAccessControl
import com.cosmotech.runner.domain.RunnerSecurity
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.solution.domain.RunTemplate
import com.cosmotech.solution.domain.Solution
import com.cosmotech.solution.domain.SolutionAccessControl
import com.cosmotech.solution.domain.SolutionSecurity
import com.cosmotech.workspace.api.WorkspaceApiService
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceAccessControl
import com.cosmotech.workspace.domain.WorkspaceSecurity
import com.cosmotech.workspace.domain.WorkspaceSolution
import com.ninjasquad.springmockk.SpykBean
import com.redis.om.spring.RediSearchIndexer
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.mockkStatic
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.runner.RunWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.util.ReflectionTestUtils

@ActiveProfiles(profiles = ["run-test"])
@ExtendWith(MockKExtension::class)
@ExtendWith(SpringExtension::class)
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RunServiceIntegrationTest : CsmRedisTestBase() {

  @Autowired lateinit var csmPlatformProperties: CsmPlatformProperties

  val CONNECTED_ADMIN_USER = "test.admin@cosmotech.com"
  val CONNECTED_READER_USER = "test.user@cosmotech.com"
  private val logger = LoggerFactory.getLogger(RunServiceIntegrationTest::class.java)
  private val defaultName = "my.account-tester@cosmotech.com"

  @MockK(relaxed = true) private lateinit var containerFactory: ContainerFactory
  @MockK(relaxed = true) private lateinit var workflowService: WorkflowService

  @Autowired lateinit var rediSearchIndexer: RediSearchIndexer
  @Autowired lateinit var connectorApiService: ConnectorApiService
  @Autowired lateinit var organizationApiService: OrganizationApiService
  @SpykBean @Autowired lateinit var datasetApiService: DatasetApiService
  @Autowired lateinit var solutionApiService: SolutionApiService
  @Autowired lateinit var workspaceApiService: WorkspaceApiService
  @SpykBean @Autowired lateinit var runnerApiService: RunnerApiService
  @Autowired lateinit var runApiService: RunServiceImpl
  @Autowired lateinit var eventPublisher: com.cosmotech.api.events.CsmEventPublisher

  lateinit var connector: Connector
  lateinit var dataset: Dataset
  lateinit var solution: Solution
  lateinit var organization: Organization
  lateinit var workspace: Workspace

  lateinit var connectorSaved: Connector
  lateinit var datasetSaved: Dataset
  lateinit var solutionSaved: Solution
  lateinit var organizationSaved: Organization
  lateinit var workspaceSaved: Workspace
  lateinit var runnerSaved: Runner
  lateinit var runSavedId: String

  @BeforeEach
  fun setUp() {
    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "test.user"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("user")

    ReflectionTestUtils.setField(runApiService, "containerFactory", containerFactory)
    ReflectionTestUtils.setField(runApiService, "workflowService", workflowService)
    ReflectionTestUtils.setField(runApiService, "eventPublisher", eventPublisher)

    rediSearchIndexer.createIndexFor(Solution::class.java)
    rediSearchIndexer.createIndexFor(Workspace::class.java)
    rediSearchIndexer.createIndexFor(Runner::class.java)
    rediSearchIndexer.createIndexFor(Run::class.java)

    connector = mockConnector("Connector")
    connectorSaved = connectorApiService.registerConnector(connector)

    organization = mockOrganization("Organization")
    organizationSaved = organizationApiService.registerOrganization(organization)

    dataset = mockDataset(organizationSaved.id!!, "Dataset", connectorSaved)
    datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)

    solution = mockSolution(organizationSaved.id!!)
    solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

    workspace = mockWorkspace(organizationSaved.id!!, solutionSaved.id!!, "Workspace")
    workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)

    runnerSaved =
        mockRunner(
            organizationSaved.id!!,
            workspaceSaved.id!!,
            solutionSaved.id!!,
            solutionSaved.runTemplates?.get(0)?.id!!,
            "Runner",
            mutableListOf(datasetSaved.id!!))

    every { workflowService.launchRun(any(), any()) } returns
        mockWorkflowRun(organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
    every { datasetApiService.findDatasetById(any(), any()) } returns
        datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS }
    every { datasetApiService.createSubDataset(any(), any(), any()) } returns mockk(relaxed = true)

    every { runnerApiService.getRunner(any(), any(), any()) } returns runnerSaved
  }

  @Test
  fun `test CRUD operations on Run`() {
    logger.info("test CRUD operations on Run")
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")

    logger.info("should create 1 run")

    runSavedId =
        mockStartRun(
            organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, solutionSaved.id!!)
    assertNotEquals("", runSavedId)

    logger.info("should find 1 Run")
    var runs =
        runApiService.listRuns(
            organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, null, null)
    assertEquals(1, runs.size)

    logger.info("should find Run by id")
    val foundRun =
        runApiService.getRun(
            organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, runSavedId)
    assertEquals(runSavedId, foundRun.id)
    assertEquals(organizationSaved.id!!, foundRun.organizationId)
    assertEquals(workspaceSaved.id!!, foundRun.workspaceId)
    assertEquals(runnerSaved.id!!, foundRun.runnerId)

    logger.info("should create second Run")
    val runSaved2id =
        mockStartRun(
            organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, solutionSaved.id!!)

    logger.info("should find all Runs by Runner id and assert size is 2")
    runs =
        runApiService.listRuns(
            organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, null, null)
    assertEquals(2, runs.size)

    logger.info("should delete second Run and assert size is 1")
    runApiService.deleteRun(
        organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, runSavedId)
    runs =
        runApiService.listRuns(
            organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, null, null)
    assertEquals(1, runs.size)
  }

  @Test
  fun `test find All Runs with different pagination params`() {
    val numberOfRuns = 20
    val defaultPageSize = csmPlatformProperties.twincache.scenariorun.defaultPageSize
    val expectedSize = 15

    IntRange(1, numberOfRuns).forEach {
      mockStartRun(
          organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, solutionSaved.id!!)
    }

    logger.info("should find all Runs and assert there are $numberOfRuns")
    var runs =
        runApiService.listRuns(
            organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, null, numberOfRuns * 2)
    assertEquals(numberOfRuns, runs.size)

    logger.info("should find all Runs and assert it equals defaultPageSize: $defaultPageSize")
    runs =
        runApiService.listRuns(
            organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, 0, null)
    assertEquals(defaultPageSize, runs.size)

    logger.info("should find all Runs and assert there are expected size: $expectedSize")
    runs =
        runApiService.listRuns(
            organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, 0, expectedSize)
    assertEquals(expectedSize, runs.size)

    logger.info("should find all Runs and assert it returns the second / last page")
    runs =
        runApiService.listRuns(
            organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, 1, expectedSize)
    assertEquals(numberOfRuns - expectedSize, runs.size)
  }

  @Test
  fun `test find All Runs with wrong pagination params`() {
    logger.info("Should throw IllegalArgumentException when page and size are zeros")
    assertThrows<IllegalArgumentException> {
      runApiService.listRuns(organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, 0, 0)
    }

    logger.info("Should throw IllegalArgumentException when page is negative")
    assertThrows<IllegalArgumentException> {
      runApiService.listRuns(organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, -1, 10)
    }

    logger.info("Should throw IllegalArgumentException when size is negative")
    assertThrows<IllegalArgumentException> {
      runApiService.listRuns(organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, 0, -1)
    }
  }

  private fun mockConnector(name: String = "connector"): Connector {
    return Connector(
        key = UUID.randomUUID().toString(),
        name = name,
        repository = "/repository",
        version = "1.0",
        ioTypes = listOf(Connector.IoTypes.read))
  }

  fun mockDataset(
      organizationId: String = organizationSaved.id!!,
      name: String = "Dataset",
      connector: Connector = connectorSaved,
      roleName: String = defaultName,
      role: String = ROLE_ADMIN
  ): Dataset {
    return Dataset(
        name = name,
        organizationId = organizationId,
        ownerId = "ownerId",
        connector =
            DatasetConnector(
                id = connector.id,
                name = connector.name,
                version = connector.version,
            ),
    )
  }

  fun mockSolution(organizationId: String = organizationSaved.id!!): Solution {
    return Solution(
        id = "solutionId",
        key = UUID.randomUUID().toString(),
        name = "My solution",
        organizationId = organizationId,
        ownerId = "ownerId",
        runTemplates =
            mutableListOf(
                RunTemplate(
                    id = UUID.randomUUID().toString(),
                    name = "RunTemplate1",
                    description = "RunTemplate1 description")),
        security =
            SolutionSecurity(
                default = ROLE_NONE,
                accessControlList =
                    mutableListOf(
                        SolutionAccessControl(id = CONNECTED_ADMIN_USER, role = ROLE_ADMIN),
                        SolutionAccessControl(id = CONNECTED_READER_USER, role = ROLE_ADMIN))))
  }

  fun mockOrganization(id: String = "organizationId"): Organization {
    return Organization(
        id = id,
        name = "Organization Name",
        ownerId = "my.account-tester@cosmotech.com",
        security =
            OrganizationSecurity(
                default = ROLE_NONE,
                accessControlList =
                    mutableListOf(
                        OrganizationAccessControl(id = CONNECTED_READER_USER, role = "reader"),
                        OrganizationAccessControl(id = CONNECTED_ADMIN_USER, role = "admin"))))
  }

  fun mockWorkspace(
      organizationId: String = organizationSaved.id!!,
      solutionId: String = solutionSaved.id!!,
      name: String = "workspace"
  ): Workspace {
    return Workspace(
        key = UUID.randomUUID().toString(),
        name = name,
        solution =
            WorkspaceSolution(
                solutionId = solutionId,
            ),
        organizationId = organizationId,
        ownerId = "ownerId",
        security =
            WorkspaceSecurity(
                ROLE_NONE, mutableListOf(WorkspaceAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN))))
  }

  fun mockRunner(
      organizationId: String = organizationSaved.id!!,
      workspaceId: String = workspaceSaved.id!!,
      solutionId: String = solutionSaved.id!!,
      runTemplateId: String = solutionSaved.runTemplates?.get(0)?.id!!,
      name: String = "runner",
      datasetList: MutableList<String> = mutableListOf(datasetSaved.id!!)
  ): Runner {
    return Runner(
        id = "RunnerId",
        name = name,
        organizationId = organizationId,
        workspaceId = workspaceId,
        solutionId = solutionId,
        runTemplateId = runTemplateId,
        ownerId = "ownerId",
        datasetList = datasetList,
        security =
            RunnerSecurity(
                ROLE_NONE, mutableListOf(RunnerAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN))))
  }

  fun mockStartRun(
      organizationId: String,
      workspaceId: String,
      runnerId: String,
      solutionId: String
  ): String {
    val runner =
        Runner(
            id = runnerId,
            solutionId = solutionId,
            organizationId = organizationId,
            workspaceId = workspaceId)
    val runStart = RunStart(this, runner)
    eventPublisher.publishEvent(runStart)
    return runStart.response!!
  }

  fun mockWorkflowRun(organizationId: String, workspaceId: String, runnerId: String): Run {
    return Run(organizationId = organizationId, workspaceId = workspaceId, runnerId = runnerId)
  }
}
