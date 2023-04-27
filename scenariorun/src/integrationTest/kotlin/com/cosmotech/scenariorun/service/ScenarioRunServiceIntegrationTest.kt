// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun.service

import com.cosmotech.api.azure.adx.AzureDataExplorerClient
import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.tests.CsmRedisTestBase
import com.cosmotech.api.utils.getCurrentAuthenticatedMail
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
import com.cosmotech.scenario.api.ScenarioApiService
import com.cosmotech.scenario.domain.Scenario
import com.cosmotech.scenariorun.ContainerFactory
import com.cosmotech.scenariorun.api.ScenariorunApiService
import com.cosmotech.scenariorun.domain.ScenarioRun
import com.cosmotech.scenariorun.domain.ScenarioRunSearch
import com.cosmotech.scenariorun.workflow.WorkflowService
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.solution.domain.RunTemplate
import com.cosmotech.solution.domain.Solution
import com.cosmotech.workspace.api.WorkspaceApiService
import com.cosmotech.workspace.azure.IWorkspaceEventHubService
import com.cosmotech.workspace.azure.WorkspaceEventHubInfo
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceSolution
import com.ninjasquad.springmockk.MockkBean
import com.redis.om.spring.RediSearchIndexer
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockkStatic
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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

const val CONNECTED_ADMIN_USER = "test.admin@cosmotech.com"
const val CONNECTED_READER_USER = "test.user@cosmotech.com"

@ActiveProfiles(profiles = ["scenariorun-test"])
@ExtendWith(MockKExtension::class)
@ExtendWith(SpringExtension::class)
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ScenarioRunServiceIntegrationTest : CsmRedisTestBase() {

  private val logger = LoggerFactory.getLogger(ScenarioRunServiceIntegrationTest::class.java)

  @MockkBean lateinit var csmADX: AzureDataExplorerClient
  @MockK private lateinit var workspaceEventHubService: IWorkspaceEventHubService
  @MockK(relaxed = true) private lateinit var azureDataExplorerClient: AzureDataExplorerClient
  @MockK(relaxed = true) private lateinit var containerFactory: ContainerFactory
  @MockK(relaxed = true) private lateinit var workflowService: WorkflowService

  @Autowired lateinit var rediSearchIndexer: RediSearchIndexer
  @Autowired lateinit var connectorApiService: ConnectorApiService
  @Autowired lateinit var organizationApiService: OrganizationApiService
  @Autowired lateinit var datasetApiService: DatasetApiService
  @Autowired lateinit var solutionApiService: SolutionApiService
  @Autowired lateinit var workspaceApiService: WorkspaceApiService
  @Autowired lateinit var scenarioApiService: ScenarioApiService
  @Autowired lateinit var scenariorunApiService: ScenariorunApiService
  @Autowired lateinit var csmPlatformProperties: CsmPlatformProperties

  lateinit var connector: Connector
  lateinit var dataset: Dataset
  lateinit var solution: Solution
  lateinit var organization: Organization
  lateinit var workspace: Workspace
  lateinit var scenario: Scenario

  lateinit var connectorSaved: Connector
  lateinit var datasetSaved: Dataset
  lateinit var solutionSaved: Solution
  lateinit var organizationSaved: Organization
  lateinit var workspaceSaved: Workspace
  lateinit var scenarioSaved: Scenario
  lateinit var scenarioRunSaved: ScenarioRun

  @BeforeEach
  fun setUp() {
    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
    every { getCurrentAuthenticatedMail(any()) } returns CONNECTED_ADMIN_USER
    every { getCurrentAuthenticatedUserName() } returns "test.user"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("user")

    every { workspaceEventHubService.getWorkspaceEventHubInfo(any(), any(), any()) } returns
        mockWorkspaceEventHubInfo(false)

    ReflectionTestUtils.setField(
        scenarioApiService, "workspaceEventHubService", workspaceEventHubService)
    ReflectionTestUtils.setField(
        scenariorunApiService, "workspaceEventHubService", workspaceEventHubService)
    ReflectionTestUtils.setField(
        scenarioApiService, "azureDataExplorerClient", azureDataExplorerClient)
    ReflectionTestUtils.setField(
        scenariorunApiService, "azureDataExplorerClient", azureDataExplorerClient)
    ReflectionTestUtils.setField(scenariorunApiService, "containerFactory", containerFactory)
    ReflectionTestUtils.setField(scenariorunApiService, "workflowService", workflowService)

    rediSearchIndexer.createIndexFor(ScenarioRun::class.java)

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

    scenario =
        mockScenario(
            organizationSaved.id!!,
            workspaceSaved.id!!,
            solutionSaved.id!!,
            solutionSaved.runTemplates?.get(0)?.id!!,
            "Scenario",
            mutableListOf(datasetSaved.id!!))

    scenarioSaved =
        scenarioApiService.createScenario(organizationSaved.id!!, workspaceSaved.id!!, scenario)

    every { workflowService.launchScenarioRun(any(), any()) } returns
        mockScenarioRun(
            organizationSaved.id!!,
            workspaceSaved.id!!,
            scenarioSaved.id!!,
            solutionSaved.id!!,
            mutableListOf())

    scenarioRunSaved =
        scenariorunApiService.runScenario(
            organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
  }

  @Test
  fun `test CRUD operations on ScenarioRun`() {
    logger.info("test CRUD operations on ScenarioRun")
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")

    logger.info("should find ScenarioRun by id")
    val foundScenarioRun =
        scenariorunApiService.findScenarioRunById(organizationSaved.id!!, scenarioRunSaved.id!!)
    assertEquals(foundScenarioRun.id, scenarioRunSaved.id)

    logger.info("should create second ScenarioRun")
    val scenarioRunSaved2 =
        scenariorunApiService.runScenario(
            organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)

    logger.info("should find all ScenarioRuns by Scenario id and assert size is 2")
    var scenarioRuns =
        scenariorunApiService.getScenarioRuns(
            organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, null, null)
    assertTrue(scenarioRuns.size == 2)

    logger.info("should find all ScenarioRuns by Workspace id and assert size is 2")
    val scenarioWorkspaceRuns =
        scenariorunApiService.getWorkspaceScenarioRuns(
            organizationSaved.id!!, workspaceSaved.id!!, null, null)
    assertTrue(scenarioWorkspaceRuns.size == 2)

    logger.info("should find all ScenarioRuns by Scenario id and assert size is 2")
    val scenarioRunSearch = ScenarioRunSearch(scenarioId = scenarioSaved.id!!)
    scenariorunApiService.searchScenarioRuns(organizationSaved.id!!, scenarioRunSearch, null, null)
    assertTrue(scenarioRuns.size == 2)

    logger.info("should delete second ScenarioRun and assert size is 1")
    scenariorunApiService.deleteScenarioRun(organizationSaved.id!!, scenarioRunSaved2.id!!)
    scenarioRuns =
        scenariorunApiService.getScenarioRuns(
            organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, null, null)
    assertTrue(scenarioRuns.size == 1)
  }

  @Test
  fun `test find All ScenarioRuns with different pagination params`() {
    val numberOfScenarioRuns = 20
    val defaultPageSize = csmPlatformProperties.twincache.scenariorun.defaultPageSize
    val expectedSize = 15
    IntRange(1, numberOfScenarioRuns - 1).forEach {
      scenariorunApiService.runScenario(
          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
    }
    logger.info("should find all ScenarioRuns and assert there are $numberOfScenarioRuns")
    var scenarioRuns =
        scenariorunApiService.getScenarioRuns(
            organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, null, null)
    assertEquals(numberOfScenarioRuns, scenarioRuns.size)

    logger.info(
        "should find all ScenarioRuns and assert it equals defaultPageSize: $defaultPageSize")
    scenarioRuns =
        scenariorunApiService.getScenarioRuns(
            organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, 0, null)
    assertEquals(defaultPageSize, scenarioRuns.size)

    logger.info("should find all ScenarioRuns and assert there are expected size: $expectedSize")
    scenarioRuns =
        scenariorunApiService.getScenarioRuns(
            organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, 0, expectedSize)
    assertEquals(expectedSize, scenarioRuns.size)

    logger.info("should find all ScenarioRuns and assert it returns the second / last page")
    scenarioRuns =
        scenariorunApiService.getScenarioRuns(
            organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, 1, expectedSize)
    assertEquals(numberOfScenarioRuns - expectedSize, scenarioRuns.size)
  }

  @Test
  fun `test find All ScenarioRuns with wrong pagination params`() {
    logger.info("Should throw IllegalArgumentException when page and size are zeros")
    assertThrows<IllegalArgumentException> {
      scenariorunApiService.getScenarioRuns(
          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, 0, 0)
    }

    logger.info("Should throw IllegalArgumentException when page is negative")
    assertThrows<IllegalArgumentException> {
      scenariorunApiService.getScenarioRuns(
          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, -1, 10)
    }

    logger.info("Should throw IllegalArgumentException when size is negative")
    assertThrows<IllegalArgumentException> {
      scenariorunApiService.getScenarioRuns(
          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, 0, -1)
    }
  }

  private fun mockWorkspaceEventHubInfo(eventHubAvailable: Boolean): WorkspaceEventHubInfo {
    return WorkspaceEventHubInfo(
        eventHubNamespace = "eventHubNamespace",
        eventHubAvailable = eventHubAvailable,
        eventHubName = "eventHubName",
        eventHubUri = "eventHubUri",
        eventHubSasKeyName = "eventHubSasKeyName",
        eventHubSasKey = "eventHubSasKey",
        eventHubCredentialType =
            CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication.Strategy
                .SHARED_ACCESS_POLICY)
  }

  private fun mockConnector(name: String): Connector {
    return Connector(
        key = UUID.randomUUID().toString(),
        name = name,
        repository = "/repository",
        version = "1.0",
        ioTypes = listOf(Connector.IoTypes.read))
  }

  fun mockDataset(organizationId: String, name: String, connector: Connector): Dataset {
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

  fun mockSolution(organizationId: String): Solution {
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
                    description = "RunTemplate1 description")))
  }

  fun mockOrganization(id: String): Organization {
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

  fun mockWorkspace(organizationId: String, solutionId: String, name: String): Workspace {
    return Workspace(
        key = UUID.randomUUID().toString(),
        name = name,
        solution =
            WorkspaceSolution(
                solutionId = solutionId,
            ),
        organizationId = organizationId,
        ownerId = "ownerId",
    )
  }

  fun mockScenario(
      organizationId: String,
      workspaceId: String,
      solutionId: String,
      runTemplateId: String,
      name: String,
      datasetList: MutableList<String>
  ): Scenario {
    return Scenario(
        name = name,
        organizationId = organizationId,
        workspaceId = workspaceId,
        solutionId = solutionId,
        runTemplateId = runTemplateId,
        ownerId = "ownerId",
        datasetList = datasetList)
  }

  fun mockScenarioRun(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      solutionId: String,
      datasetList: MutableList<String>
  ): ScenarioRun {
    return ScenarioRun(
        organizationId = organizationId,
        workspaceId = workspaceId,
        solutionId = solutionId,
        scenarioId = scenarioId,
        ownerId = "ownerId",
        workflowId = "workflowId",
        workflowName = "workflowName",
        datasetList = datasetList)
  }
}
