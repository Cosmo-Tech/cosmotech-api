// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun.service

import com.cosmotech.api.azure.adx.AzureDataExplorerClient
import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.rbac.PERMISSION_CREATE_CHILDREN
import com.cosmotech.api.rbac.PERMISSION_DELETE
import com.cosmotech.api.rbac.PERMISSION_LAUNCH
import com.cosmotech.api.rbac.PERMISSION_READ
import com.cosmotech.api.rbac.PERMISSION_WRITE
import com.cosmotech.api.rbac.ROLE_ADMIN
import com.cosmotech.api.rbac.ROLE_EDITOR
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.rbac.ROLE_USER
import com.cosmotech.api.rbac.ROLE_VALIDATOR
import com.cosmotech.api.rbac.ROLE_VIEWER
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
import com.cosmotech.scenario.api.ScenarioApiService
import com.cosmotech.scenario.domain.Scenario
import com.cosmotech.scenario.domain.ScenarioAccessControl
import com.cosmotech.scenario.domain.ScenarioSecurity
import com.cosmotech.scenariorun.ContainerFactory
import com.cosmotech.scenariorun.api.ScenariorunApiService
import com.cosmotech.scenariorun.domain.ScenarioRun
import com.cosmotech.scenariorun.domain.ScenarioRunContainer
import com.cosmotech.scenariorun.domain.ScenarioRunSearch
import com.cosmotech.scenariorun.domain.ScenarioRunStartContainers
import com.cosmotech.scenariorun.workflow.WorkflowService
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.solution.domain.RunTemplate
import com.cosmotech.solution.domain.Solution
import com.cosmotech.solution.domain.SolutionAccessControl
import com.cosmotech.solution.domain.SolutionSecurity
import com.cosmotech.workspace.api.WorkspaceApiService
import com.cosmotech.workspace.azure.IWorkspaceEventHubService
import com.cosmotech.workspace.azure.WorkspaceEventHubInfo
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceAccessControl
import com.cosmotech.workspace.domain.WorkspaceSecurity
import com.cosmotech.workspace.domain.WorkspaceSolution
import com.ninjasquad.springmockk.MockkBean
import com.redis.om.spring.RediSearchIndexer
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockkStatic
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertDoesNotThrow
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

@ActiveProfiles(profiles = ["scenariorun-test"])
@ExtendWith(MockKExtension::class)
@ExtendWith(SpringExtension::class)
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ScenarioRunServiceIntegrationTest : CsmRedisTestBase() {

  @Autowired lateinit var csmPlatformProperties: CsmPlatformProperties
  @Nested
  inner class ScenarioRunServiceIntegrationFunctionnalityTest {
    val CONNECTED_ADMIN_USER = "test.admin@cosmotech.com"
    val CONNECTED_READER_USER = "test.user@cosmotech.com"
    private val logger = LoggerFactory.getLogger(ScenarioRunServiceIntegrationTest::class.java)
    private val defaultName = "my.account-tester@cosmotech.com"

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
      every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
      every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "test.user"
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

      rediSearchIndexer.createIndexFor(Solution::class.java)
      rediSearchIndexer.createIndexFor(Workspace::class.java)
      rediSearchIndexer.createIndexFor(Scenario::class.java)
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
      scenariorunApiService.searchScenarioRuns(
          organizationSaved.id!!, scenarioRunSearch, null, null)
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
              CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication
                  .Strategy
                  .SHARED_ACCESS_POLICY)
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
                  ROLE_NONE,
                  mutableListOf(WorkspaceAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN))))
    }

    fun mockScenario(
        organizationId: String = organizationSaved.id!!,
        workspaceId: String = workspaceSaved.id!!,
        solutionId: String = solutionSaved.id!!,
        runTemplateId: String = solutionSaved.runTemplates?.get(0)?.id!!,
        name: String = "scenario",
        datasetList: MutableList<String> = mutableListOf(datasetSaved.id!!)
    ): Scenario {
      return Scenario(
          name = name,
          organizationId = organizationId,
          workspaceId = workspaceId,
          solutionId = solutionId,
          runTemplateId = runTemplateId,
          ownerId = "ownerId",
          datasetList = datasetList,
          security =
              ScenarioSecurity(
                  ROLE_NONE,
                  mutableListOf(ScenarioAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN))))
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

  @Nested
  inner class ScenarioRunRBACTest {
    val CONNECTED_ADMIN_USER = "test.admin@cosmotech.com"
    val TEST_USER_MAIL = "testuser@mail.fr"

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

    @BeforeEach
    fun setUp() {
      mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
      every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
      every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns TEST_USER_MAIL
      every { getCurrentAuthenticatedRoles(any()) } returns listOf()

      every { workspaceEventHubService.getWorkspaceEventHubInfo(any(), any(), any()) } returns
          makeWorkspaceEventHubInfo(false)

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

      rediSearchIndexer.createIndexFor(Solution::class.java)
      rediSearchIndexer.createIndexFor(Workspace::class.java)
      rediSearchIndexer.createIndexFor(Scenario::class.java)
      rediSearchIndexer.createIndexFor(ScenarioRun::class.java)
    }

    @TestFactory
    fun `test RBAC deleteHistoricalDataOrganization`() =
        mapOf(
                ROLE_VIEWER to true,
                ROLE_EDITOR to true,
                ROLE_VALIDATOR to true,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              DynamicTest.dynamicTest("Test RBAC deleteHistoricalDataOrganization : $role") {
                every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
                val connector = makeConnector()
                val connectorSaved = connectorApiService.registerConnector(connector)
                val organization = makeOrganizationWithRole(userName = TEST_USER_MAIL, role = role)
                val organizationSaved = organizationApiService.registerOrganization(organization)
                val dataset = makeDataset(organizationSaved.id!!, connectorSaved)
                val datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
                val solution = makeSolution(organizationSaved.id!!)
                val solutionSaved =
                    solutionApiService.createSolution(organizationSaved.id!!, solution)
                val workspace =
                    makeWorkspaceWithRole(
                        organizationSaved.id!!,
                        solutionSaved.id!!,
                        userName = TEST_USER_MAIL,
                        role = ROLE_ADMIN)
                val workspaceSaved =
                    workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
                val scenario =
                    makeScenarioWithRole(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        solutionSaved.id!!,
                        solutionSaved.runTemplates!!.first().id,
                        mutableListOf(datasetSaved.id!!),
                        userName = TEST_USER_MAIL,
                        role = ROLE_ADMIN)
                val scenarioSaved =
                    scenarioApiService.createScenario(
                        organizationSaved.id!!, workspaceSaved.id!!, scenario)
                every { workflowService.launchScenarioRun(any(), any()) } returns
                    makeScenarioRun(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        solutionSaved.id!!,
                        solutionSaved.id!!,
                        mutableListOf(datasetSaved.id!!))
                scenariorunApiService.runScenario(
                    organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

                if (shouldThrow) {
                  val exception =
                      assertThrows<CsmAccessForbiddenException> {
                        scenariorunApiService.deleteHistoricalDataOrganization(
                            organizationSaved.id!!, true)
                      }
                  if (role == ROLE_VALIDATOR || role == ROLE_NONE) {
                    assertEquals(
                        "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                        exception.message)
                  } else {
                    assertEquals(
                        "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_DELETE",
                        exception.message)
                  }
                } else {
                  assertDoesNotThrow {
                    scenariorunApiService.deleteHistoricalDataOrganization(
                        organizationSaved.id!!, true)
                  }
                }
              }
            }

    @TestFactory
    fun `test RBAC deleteHistoricalDataWorkspace`() =
        mapOf(
                ROLE_VIEWER to true,
                ROLE_EDITOR to true,
                ROLE_VALIDATOR to true,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              DynamicTest.dynamicTest("Test RBAC deleteHistoricalDataWorkspace : $role") {
                every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
                val connector = makeConnector()
                val connectorSaved = connectorApiService.registerConnector(connector)
                val organization =
                    makeOrganizationWithRole(userName = TEST_USER_MAIL, role = ROLE_ADMIN)
                val organizationSaved = organizationApiService.registerOrganization(organization)
                val dataset = makeDataset(organizationSaved.id!!, connectorSaved)
                val datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
                val solution = makeSolution(organizationSaved.id!!)
                val solutionSaved =
                    solutionApiService.createSolution(organizationSaved.id!!, solution)
                val workspace =
                    makeWorkspaceWithRole(
                        organizationSaved.id!!,
                        solutionSaved.id!!,
                        userName = TEST_USER_MAIL,
                        role = role)
                val workspaceSaved =
                    workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
                val scenario =
                    makeScenarioWithRole(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        solutionSaved.id!!,
                        solutionSaved.runTemplates!!.first().id,
                        mutableListOf(datasetSaved.id!!),
                        userName = TEST_USER_MAIL,
                        role = ROLE_ADMIN)
                val scenarioSaved =
                    scenarioApiService.createScenario(
                        organizationSaved.id!!, workspaceSaved.id!!, scenario)
                every { workflowService.launchScenarioRun(any(), any()) } returns
                    makeScenarioRun(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        solutionSaved.id!!,
                        solutionSaved.id!!,
                        mutableListOf(datasetSaved.id!!))
                scenariorunApiService.runScenario(
                    organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

                if (shouldThrow) {
                  val exception =
                      assertThrows<CsmAccessForbiddenException> {
                        scenariorunApiService.deleteHistoricalDataWorkspace(
                            organizationSaved.id!!, workspaceSaved.id!!, true)
                      }
                  if (role == ROLE_VALIDATOR || role == ROLE_NONE) {
                    assertEquals(
                        "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
                        exception.message)
                  } else {
                    assertEquals(
                        "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_DELETE",
                        exception.message)
                  }
                } else {
                  assertDoesNotThrow {
                    scenariorunApiService.deleteHistoricalDataWorkspace(
                        organizationSaved.id!!, workspaceSaved.id!!, true)
                  }
                }
              }
            }

    @TestFactory
    fun `test RBAC deleteHistoricalDataScenario`() =
        mapOf(
                ROLE_VIEWER to true,
                ROLE_EDITOR to true,
                ROLE_VALIDATOR to true,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              DynamicTest.dynamicTest("Test RBAC deleteHistoricalDataScenario : $role") {
                every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
                val connector = makeConnector()
                val connectorSaved = connectorApiService.registerConnector(connector)
                val organization =
                    makeOrganizationWithRole(userName = TEST_USER_MAIL, role = ROLE_ADMIN)
                val organizationSaved = organizationApiService.registerOrganization(organization)
                val dataset = makeDataset(organizationSaved.id!!, connectorSaved)
                val datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
                val solution = makeSolution(organizationSaved.id!!)
                val solutionSaved =
                    solutionApiService.createSolution(organizationSaved.id!!, solution)
                val workspace =
                    makeWorkspaceWithRole(
                        organizationSaved.id!!,
                        solutionSaved.id!!,
                        userName = TEST_USER_MAIL,
                        role = ROLE_ADMIN)
                val workspaceSaved =
                    workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
                val scenario =
                    makeScenarioWithRole(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        solutionSaved.id!!,
                        solutionSaved.runTemplates!!.first().id,
                        mutableListOf(datasetSaved.id!!),
                        userName = TEST_USER_MAIL,
                        role = role)
                val scenarioSaved =
                    scenarioApiService.createScenario(
                        organizationSaved.id!!, workspaceSaved.id!!, scenario)
                every { workflowService.launchScenarioRun(any(), any()) } returns
                    makeScenarioRun(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        solutionSaved.id!!,
                        solutionSaved.id!!,
                        mutableListOf(datasetSaved.id!!))
                scenariorunApiService.runScenario(
                    organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

                if (shouldThrow) {
                  val exception =
                      assertThrows<CsmAccessForbiddenException> {
                        scenariorunApiService.deleteHistoricalDataScenario(
                            organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, true)
                      }
                  if (role == ROLE_USER || role == ROLE_NONE) {
                    assertEquals(
                        "RBAC ${scenarioSaved.id!!} - User does not have permission $PERMISSION_READ",
                        exception.message)
                  } else {
                    assertEquals(
                        "RBAC ${scenarioSaved.id!!} - User does not have permission $PERMISSION_DELETE",
                        exception.message)
                  }
                } else {
                  assertDoesNotThrow {
                    scenariorunApiService.deleteHistoricalDataScenario(
                        organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, true)
                  }
                }
              }
            }

    @TestFactory
    fun `test RBAC searchScenarioRuns`() =
        mapOf(
                ROLE_VIEWER to false,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to true,
                ROLE_USER to false,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              DynamicTest.dynamicTest("Test RBAC searchScenarioRuns : $role") {
                every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
                val connector = makeConnector()
                val connectorSaved = connectorApiService.registerConnector(connector)
                val organization = makeOrganizationWithRole(userName = TEST_USER_MAIL, role = role)
                val organizationSaved = organizationApiService.registerOrganization(organization)
                val dataset = makeDataset(organizationSaved.id!!, connectorSaved)
                val datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
                val solution = makeSolution(organizationSaved.id!!)
                val solutionSaved =
                    solutionApiService.createSolution(organizationSaved.id!!, solution)
                val workspace =
                    makeWorkspaceWithRole(
                        organizationSaved.id!!,
                        solutionSaved.id!!,
                        userName = TEST_USER_MAIL,
                        role = ROLE_ADMIN)
                val workspaceSaved =
                    workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
                val scenario =
                    makeScenarioWithRole(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        solutionSaved.id!!,
                        solutionSaved.runTemplates!!.first().id,
                        mutableListOf(datasetSaved.id!!),
                        userName = TEST_USER_MAIL,
                        role = ROLE_ADMIN)
                val scenarioSaved =
                    scenarioApiService.createScenario(
                        organizationSaved.id!!, workspaceSaved.id!!, scenario)
                every { workflowService.launchScenarioRun(any(), any()) } returns
                    makeScenarioRun(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        solutionSaved.id!!,
                        solutionSaved.id!!,
                        mutableListOf(datasetSaved.id!!))
                scenariorunApiService.runScenario(
                    organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

                val scenarioRunSearch = ScenarioRunSearch(scenarioSaved.id!!)

                if (shouldThrow) {
                  val exception =
                      assertThrows<CsmAccessForbiddenException> {
                        scenariorunApiService.searchScenarioRuns(
                            organizationSaved.id!!, scenarioRunSearch, null, null)
                      }
                  assertEquals(
                      "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertDoesNotThrow {
                    scenariorunApiService.searchScenarioRuns(
                        organizationSaved.id!!, scenarioRunSearch, null, null)
                  }
                }
              }
            }

    @TestFactory
    fun `test RBAC startScenarioRunContainers`() =
        mapOf(
                ROLE_VIEWER to true,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to true,
                ROLE_USER to false,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              DynamicTest.dynamicTest("Test RBAC startScenarioRunContainers : $role") {
                every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
                val connector = makeConnector()
                val connectorSaved = connectorApiService.registerConnector(connector)
                val organization = makeOrganizationWithRole(userName = TEST_USER_MAIL, role = role)
                val organizationSaved = organizationApiService.registerOrganization(organization)
                val dataset = makeDataset(organizationSaved.id!!, connectorSaved)
                val datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
                val solution = makeSolution(organizationSaved.id!!)
                val solutionSaved =
                    solutionApiService.createSolution(organizationSaved.id!!, solution)
                val workspace =
                    makeWorkspaceWithRole(
                        organizationSaved.id!!,
                        solutionSaved.id!!,
                        userName = TEST_USER_MAIL,
                        role = ROLE_ADMIN)
                val workspaceSaved =
                    workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
                val scenario =
                    makeScenarioWithRole(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        solutionSaved.id!!,
                        solutionSaved.runTemplates!!.first().id,
                        mutableListOf(datasetSaved.id!!),
                        userName = TEST_USER_MAIL,
                        role = ROLE_ADMIN)
                val scenarioSaved =
                    scenarioApiService.createScenario(
                        organizationSaved.id!!, workspaceSaved.id!!, scenario)
                every { workflowService.launchScenarioRun(any(), any()) } returns
                    makeScenarioRun(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        solutionSaved.id!!,
                        solutionSaved.id!!,
                        mutableListOf(datasetSaved.id!!))
                scenariorunApiService.runScenario(
                    organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

                val scenarioRunStartContainers =
                    ScenarioRunStartContainers("id", listOf(ScenarioRunContainer("name", "image")))

                if (shouldThrow) {
                  val exception =
                      assertThrows<CsmAccessForbiddenException> {
                        scenariorunApiService.startScenarioRunContainers(
                            organizationSaved.id!!, scenarioRunStartContainers)
                      }
                  if (role == ROLE_VALIDATOR || role == ROLE_NONE) {
                    assertEquals(
                        "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                        exception.message)
                  } else {
                    assertEquals(
                        "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_CREATE_CHILDREN",
                        exception.message)
                  }
                } else {
                  assertDoesNotThrow {
                    scenariorunApiService.startScenarioRunContainers(
                        organizationSaved.id!!, scenarioRunStartContainers)
                  }
                }
              }
            }

    @TestFactory
    fun `test RBAC findScenarioRunById`() =
        mapOf(
                ROLE_VIEWER to false,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to false,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              DynamicTest.dynamicTest("Test RBAC findScenarioRunById : $role") {
                every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
                val connector = makeConnector()
                val connectorSaved = connectorApiService.registerConnector(connector)
                val organization =
                    makeOrganizationWithRole(userName = TEST_USER_MAIL, role = ROLE_ADMIN)
                val organizationSaved = organizationApiService.registerOrganization(organization)
                val dataset = makeDataset(organizationSaved.id!!, connectorSaved)
                val datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
                val solution = makeSolution(organizationSaved.id!!)
                val solutionSaved =
                    solutionApiService.createSolution(organizationSaved.id!!, solution)
                val workspace =
                    makeWorkspaceWithRole(
                        organizationSaved.id!!,
                        solutionSaved.id!!,
                        userName = TEST_USER_MAIL,
                        role = ROLE_ADMIN)
                val workspaceSaved =
                    workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
                val scenario =
                    makeScenarioWithRole(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        solutionSaved.id!!,
                        solutionSaved.runTemplates!!.first().id,
                        mutableListOf(datasetSaved.id!!),
                        userName = TEST_USER_MAIL,
                        role = role)
                val scenarioSaved =
                    scenarioApiService.createScenario(
                        organizationSaved.id!!, workspaceSaved.id!!, scenario)
                every { workflowService.launchScenarioRun(any(), any()) } returns
                    makeScenarioRun(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        solutionSaved.id!!,
                        solutionSaved.id!!,
                        mutableListOf(datasetSaved.id!!))
                val scenarioRunSaved =
                    scenariorunApiService.runScenario(
                        organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

                if (shouldThrow) {
                  val exception =
                      assertThrows<CsmAccessForbiddenException> {
                        scenariorunApiService.findScenarioRunById(
                            organizationSaved.id!!, scenarioRunSaved.id!!)
                      }
                  assertEquals(
                      "RBAC ${scenarioSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertDoesNotThrow {
                    scenariorunApiService.findScenarioRunById(
                        organizationSaved.id!!, scenarioRunSaved.id!!)
                  }
                }
              }
            }

    @TestFactory
    fun `test RBAC deleteScenarioRun`() =
        mapOf(
                ROLE_VIEWER to true,
                ROLE_EDITOR to true,
                ROLE_VALIDATOR to true,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              DynamicTest.dynamicTest("Test RBAC deleteScenarioRun : $role") {
                every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
                val connector = makeConnector()
                val connectorSaved = connectorApiService.registerConnector(connector)
                val organization =
                    makeOrganizationWithRole(userName = TEST_USER_MAIL, role = ROLE_ADMIN)
                val organizationSaved = organizationApiService.registerOrganization(organization)
                val dataset = makeDataset(organizationSaved.id!!, connectorSaved)
                val datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
                val solution = makeSolution(organizationSaved.id!!)
                val solutionSaved =
                    solutionApiService.createSolution(organizationSaved.id!!, solution)
                val workspace =
                    makeWorkspaceWithRole(
                        organizationSaved.id!!,
                        solutionSaved.id!!,
                        userName = TEST_USER_MAIL,
                        role = ROLE_ADMIN)
                val workspaceSaved =
                    workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
                val scenario =
                    makeScenarioWithRole(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        solutionSaved.id!!,
                        solutionSaved.runTemplates!!.first().id,
                        mutableListOf(datasetSaved.id!!),
                        userName = TEST_USER_MAIL,
                        role = role)
                val scenarioSaved =
                    scenarioApiService.createScenario(
                        organizationSaved.id!!, workspaceSaved.id!!, scenario)
                every { workflowService.launchScenarioRun(any(), any()) } returns
                    makeScenarioRun(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        solutionSaved.id!!,
                        solutionSaved.id!!,
                        mutableListOf(datasetSaved.id!!))
                val scenarioRunSaved =
                    scenariorunApiService.runScenario(
                        organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

                if (shouldThrow) {
                  val exception =
                      assertThrows<CsmAccessForbiddenException> {
                        scenariorunApiService.deleteScenarioRun(
                            organizationSaved.id!!, scenarioRunSaved.id!!)
                      }
                  if (role == ROLE_USER || role == ROLE_NONE) {
                    assertEquals(
                        "RBAC ${scenarioSaved.id!!} - User does not have permission $PERMISSION_READ",
                        exception.message)
                  } else {
                    assertEquals(
                        "RBAC ${scenarioSaved.id!!} - User does not have permission $PERMISSION_DELETE",
                        exception.message)
                  }
                } else {
                  assertDoesNotThrow {
                    scenariorunApiService.deleteScenarioRun(
                        organizationSaved.id!!, scenarioRunSaved.id!!)
                  }
                }
              }
            }

    @TestFactory
    fun `test RBAC getScenarioRunStatus`() =
        mapOf(
                ROLE_VIEWER to false,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to false,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              DynamicTest.dynamicTest("Test RBAC getScenarioRunStatus : $role") {
                every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
                val connector = makeConnector()
                val connectorSaved = connectorApiService.registerConnector(connector)
                val organization =
                    makeOrganizationWithRole(userName = TEST_USER_MAIL, role = ROLE_ADMIN)
                val organizationSaved = organizationApiService.registerOrganization(organization)
                val dataset = makeDataset(organizationSaved.id!!, connectorSaved)
                val datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
                val solution = makeSolution(organizationSaved.id!!)
                val solutionSaved =
                    solutionApiService.createSolution(organizationSaved.id!!, solution)
                val workspace =
                    makeWorkspaceWithRole(
                        organizationSaved.id!!,
                        solutionSaved.id!!,
                        userName = TEST_USER_MAIL,
                        role = ROLE_ADMIN)
                val workspaceSaved =
                    workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
                val scenario =
                    makeScenarioWithRole(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        solutionSaved.id!!,
                        solutionSaved.runTemplates!!.first().id,
                        mutableListOf(datasetSaved.id!!),
                        userName = TEST_USER_MAIL,
                        role = role)
                val scenarioSaved =
                    scenarioApiService.createScenario(
                        organizationSaved.id!!, workspaceSaved.id!!, scenario)
                every { workflowService.launchScenarioRun(any(), any()) } returns
                    makeScenarioRun(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        solutionSaved.id!!,
                        solutionSaved.id!!,
                        mutableListOf(datasetSaved.id!!))
                val scenarioRunSaved =
                    scenariorunApiService.runScenario(
                        organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

                if (shouldThrow) {
                  val exception =
                      assertThrows<CsmAccessForbiddenException> {
                        scenariorunApiService.getScenarioRunStatus(
                            organizationSaved.id!!, scenarioRunSaved.id!!)
                      }
                  assertEquals(
                      "RBAC ${scenarioSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertDoesNotThrow {
                    scenariorunApiService.getScenarioRunStatus(
                        organizationSaved.id!!, scenarioRunSaved.id!!)
                  }
                }
              }
            }

    @TestFactory
    fun `test RBAC getScenarioRunLogs`() =
        mapOf(
                ROLE_VIEWER to false,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to false,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              DynamicTest.dynamicTest("Test RBAC getScenarioRunLogs : $role") {
                every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
                val connector = makeConnector()
                val connectorSaved = connectorApiService.registerConnector(connector)
                val organization =
                    makeOrganizationWithRole(userName = TEST_USER_MAIL, role = ROLE_ADMIN)
                val organizationSaved = organizationApiService.registerOrganization(organization)
                val dataset = makeDataset(organizationSaved.id!!, connectorSaved)
                val datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
                val solution = makeSolution(organizationSaved.id!!)
                val solutionSaved =
                    solutionApiService.createSolution(organizationSaved.id!!, solution)
                val workspace =
                    makeWorkspaceWithRole(
                        organizationSaved.id!!,
                        solutionSaved.id!!,
                        userName = TEST_USER_MAIL,
                        role = ROLE_ADMIN)
                val workspaceSaved =
                    workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
                val scenario =
                    makeScenarioWithRole(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        solutionSaved.id!!,
                        solutionSaved.runTemplates!!.first().id,
                        mutableListOf(datasetSaved.id!!),
                        userName = TEST_USER_MAIL,
                        role = role)
                val scenarioSaved =
                    scenarioApiService.createScenario(
                        organizationSaved.id!!, workspaceSaved.id!!, scenario)
                every { workflowService.launchScenarioRun(any(), any()) } returns
                    makeScenarioRun(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        solutionSaved.id!!,
                        solutionSaved.id!!,
                        mutableListOf(datasetSaved.id!!))
                val scenarioRunSaved =
                    scenariorunApiService.runScenario(
                        organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

                if (shouldThrow) {
                  val exception =
                      assertThrows<CsmAccessForbiddenException> {
                        scenariorunApiService.getScenarioRunLogs(
                            organizationSaved.id!!, scenarioRunSaved.id!!)
                      }
                  assertEquals(
                      "RBAC ${scenarioSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertDoesNotThrow {
                    scenariorunApiService.getScenarioRunLogs(
                        organizationSaved.id!!, scenarioRunSaved.id!!)
                  }
                }
              }
            }

    @TestFactory
    fun `test RBAC getScenarioRunCumulatedLogs`() =
        mapOf(
                ROLE_VIEWER to false,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to false,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              DynamicTest.dynamicTest("Test RBAC getScenarioRunCumulatedLogs : $role") {
                every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
                val connector = makeConnector()
                val connectorSaved = connectorApiService.registerConnector(connector)
                val organization =
                    makeOrganizationWithRole(userName = TEST_USER_MAIL, role = ROLE_ADMIN)
                val organizationSaved = organizationApiService.registerOrganization(organization)
                val dataset = makeDataset(organizationSaved.id!!, connectorSaved)
                val datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
                val solution = makeSolution(organizationSaved.id!!)
                val solutionSaved =
                    solutionApiService.createSolution(organizationSaved.id!!, solution)
                val workspace =
                    makeWorkspaceWithRole(
                        organizationSaved.id!!,
                        solutionSaved.id!!,
                        userName = TEST_USER_MAIL,
                        role = ROLE_ADMIN)
                val workspaceSaved =
                    workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
                val scenario =
                    makeScenarioWithRole(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        solutionSaved.id!!,
                        solutionSaved.runTemplates!!.first().id,
                        mutableListOf(datasetSaved.id!!),
                        userName = TEST_USER_MAIL,
                        role = role)
                val scenarioSaved =
                    scenarioApiService.createScenario(
                        organizationSaved.id!!, workspaceSaved.id!!, scenario)
                every { workflowService.launchScenarioRun(any(), any()) } returns
                    makeScenarioRun(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        solutionSaved.id!!,
                        solutionSaved.id!!,
                        mutableListOf(datasetSaved.id!!))
                val scenarioRunSaved =
                    scenariorunApiService.runScenario(
                        organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

                if (shouldThrow) {
                  val exception =
                      assertThrows<CsmAccessForbiddenException> {
                        scenariorunApiService.getScenarioRunCumulatedLogs(
                            organizationSaved.id!!, scenarioRunSaved.id!!)
                      }
                  assertEquals(
                      "RBAC ${scenarioSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertDoesNotThrow {
                    scenariorunApiService.getScenarioRunCumulatedLogs(
                        organizationSaved.id!!, scenarioRunSaved.id!!)
                  }
                }
              }
            }

    @TestFactory
    fun `test RBAC getWorkspaceScenarioRuns`() =
        mapOf(
                ROLE_VIEWER to false,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to true,
                ROLE_USER to false,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              DynamicTest.dynamicTest("Test RBAC getWorkspaceScenarioRuns : $role") {
                every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
                val connector = makeConnector()
                val connectorSaved = connectorApiService.registerConnector(connector)
                val organization =
                    makeOrganizationWithRole(userName = TEST_USER_MAIL, role = ROLE_ADMIN)
                val organizationSaved = organizationApiService.registerOrganization(organization)
                val dataset = makeDataset(organizationSaved.id!!, connectorSaved)
                val datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
                val solution = makeSolution(organizationSaved.id!!)
                val solutionSaved =
                    solutionApiService.createSolution(organizationSaved.id!!, solution)
                val workspace =
                    makeWorkspaceWithRole(
                        organizationSaved.id!!,
                        solutionSaved.id!!,
                        userName = TEST_USER_MAIL,
                        role = role)
                val workspaceSaved =
                    workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
                val scenario =
                    makeScenarioWithRole(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        solutionSaved.id!!,
                        solutionSaved.runTemplates!!.first().id,
                        mutableListOf(datasetSaved.id!!),
                        userName = TEST_USER_MAIL,
                        role = ROLE_ADMIN)
                val scenarioSaved =
                    scenarioApiService.createScenario(
                        organizationSaved.id!!, workspaceSaved.id!!, scenario)
                every { workflowService.launchScenarioRun(any(), any()) } returns
                    makeScenarioRun(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        solutionSaved.id!!,
                        solutionSaved.id!!,
                        mutableListOf(datasetSaved.id!!))
                scenariorunApiService.runScenario(
                    organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

                if (shouldThrow) {
                  val exception =
                      assertThrows<CsmAccessForbiddenException> {
                        scenariorunApiService.getWorkspaceScenarioRuns(
                            organizationSaved.id!!, workspaceSaved.id!!, null, null)
                      }
                  assertEquals(
                      "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertDoesNotThrow {
                    scenariorunApiService.getWorkspaceScenarioRuns(
                        organizationSaved.id!!, workspaceSaved.id!!, null, null)
                  }
                }
              }
            }

    @TestFactory
    fun `test RBAC getScenarioRuns`() =
        mapOf(
                ROLE_VIEWER to false,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to false,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              DynamicTest.dynamicTest("Test RBAC getScenarioRuns : $role") {
                every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
                val connector = makeConnector()
                val connectorSaved = connectorApiService.registerConnector(connector)
                val organization =
                    makeOrganizationWithRole(userName = TEST_USER_MAIL, role = ROLE_ADMIN)
                val organizationSaved = organizationApiService.registerOrganization(organization)
                val dataset = makeDataset(organizationSaved.id!!, connectorSaved)
                val datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
                val solution = makeSolution(organizationSaved.id!!)
                val solutionSaved =
                    solutionApiService.createSolution(organizationSaved.id!!, solution)
                val workspace =
                    makeWorkspaceWithRole(
                        organizationSaved.id!!,
                        solutionSaved.id!!,
                        userName = TEST_USER_MAIL,
                        role = ROLE_ADMIN)
                val workspaceSaved =
                    workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
                val scenario =
                    makeScenarioWithRole(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        solutionSaved.id!!,
                        solutionSaved.runTemplates!!.first().id,
                        mutableListOf(datasetSaved.id!!),
                        userName = TEST_USER_MAIL,
                        role = role)
                val scenarioSaved =
                    scenarioApiService.createScenario(
                        organizationSaved.id!!, workspaceSaved.id!!, scenario)
                every { workflowService.launchScenarioRun(any(), any()) } returns
                    makeScenarioRun(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        solutionSaved.id!!,
                        solutionSaved.id!!,
                        mutableListOf(datasetSaved.id!!))
                scenariorunApiService.runScenario(
                    organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

                if (shouldThrow) {
                  val exception =
                      assertThrows<CsmAccessForbiddenException> {
                        scenariorunApiService.getScenarioRuns(
                            organizationSaved.id!!,
                            workspaceSaved.id!!,
                            scenarioSaved.id!!,
                            null,
                            null)
                      }
                  assertEquals(
                      "RBAC ${scenarioSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertDoesNotThrow {
                    scenariorunApiService.getScenarioRuns(
                        organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, null, null)
                  }
                }
              }
            }

    @TestFactory
    fun `test RBAC runScenario`() =
        mapOf(
                ROLE_VIEWER to true,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to false,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              DynamicTest.dynamicTest("Test RBAC runScenario : $role") {
                every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
                val connector = makeConnector()
                val connectorSaved = connectorApiService.registerConnector(connector)
                val organization =
                    makeOrganizationWithRole(userName = TEST_USER_MAIL, role = ROLE_ADMIN)
                val organizationSaved = organizationApiService.registerOrganization(organization)
                val dataset = makeDataset(organizationSaved.id!!, connectorSaved)
                val datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
                val solution = makeSolution(organizationSaved.id!!)
                val solutionSaved =
                    solutionApiService.createSolution(organizationSaved.id!!, solution)
                val workspace =
                    makeWorkspaceWithRole(
                        organizationSaved.id!!,
                        solutionSaved.id!!,
                        userName = TEST_USER_MAIL,
                        role = ROLE_ADMIN)
                val workspaceSaved =
                    workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
                val scenario =
                    makeScenarioWithRole(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        solutionSaved.id!!,
                        solutionSaved.runTemplates!!.first().id,
                        mutableListOf(datasetSaved.id!!),
                        userName = TEST_USER_MAIL,
                        role = role)
                val scenarioSaved =
                    scenarioApiService.createScenario(
                        organizationSaved.id!!, workspaceSaved.id!!, scenario)
                every { workflowService.launchScenarioRun(any(), any()) } returns
                    makeScenarioRun(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        solutionSaved.id!!,
                        solutionSaved.id!!,
                        mutableListOf(datasetSaved.id!!))
                scenariorunApiService.runScenario(
                    organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

                if (shouldThrow) {
                  val exception =
                      assertThrows<CsmAccessForbiddenException> {
                        scenariorunApiService.runScenario(
                            organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                      }
                  if (role == ROLE_USER || role == ROLE_NONE) {
                    assertEquals(
                        "RBAC ${scenarioSaved.id!!} - User does not have permission $PERMISSION_READ",
                        exception.message)
                  } else {
                    assertEquals(
                        "RBAC ${scenarioSaved.id!!} - User does not have permission $PERMISSION_LAUNCH",
                        exception.message)
                  }
                } else {
                  assertDoesNotThrow {
                    scenariorunApiService.runScenario(
                        organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                  }
                }
              }
            }

    @TestFactory
    fun `test RBAC stopScenarioRun`() =
        mapOf(
                ROLE_VIEWER to true,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to false,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              DynamicTest.dynamicTest("Test RBAC stopScenarioRun : $role") {
                every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
                val connector = makeConnector()
                val connectorSaved = connectorApiService.registerConnector(connector)
                val organization =
                    makeOrganizationWithRole(userName = TEST_USER_MAIL, role = ROLE_ADMIN)
                val organizationSaved = organizationApiService.registerOrganization(organization)
                val dataset = makeDataset(organizationSaved.id!!, connectorSaved)
                val datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
                val solution = makeSolution(organizationSaved.id!!)
                val solutionSaved =
                    solutionApiService.createSolution(organizationSaved.id!!, solution)
                val workspace =
                    makeWorkspaceWithRole(
                        organizationSaved.id!!,
                        solutionSaved.id!!,
                        userName = TEST_USER_MAIL,
                        role = ROLE_ADMIN)
                val workspaceSaved =
                    workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
                val scenario =
                    makeScenarioWithRole(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        solutionSaved.id!!,
                        solutionSaved.runTemplates!!.first().id,
                        mutableListOf(datasetSaved.id!!),
                        userName = TEST_USER_MAIL,
                        role = role)
                val scenarioSaved =
                    scenarioApiService.createScenario(
                        organizationSaved.id!!, workspaceSaved.id!!, scenario)
                every { workflowService.launchScenarioRun(any(), any()) } returns
                    makeScenarioRun(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        solutionSaved.id!!,
                        solutionSaved.id!!,
                        mutableListOf(datasetSaved.id!!))
                val scenarioRunSaved =
                    scenariorunApiService.runScenario(
                        organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

                if (shouldThrow) {
                  val exception =
                      assertThrows<CsmAccessForbiddenException> {
                        scenariorunApiService.stopScenarioRun(
                            organizationSaved.id!!, scenarioRunSaved.id!!)
                      }
                  if (role == ROLE_USER || role == ROLE_NONE) {
                    assertEquals(
                        "RBAC ${scenarioSaved.id!!} - User does not have permission $PERMISSION_READ",
                        exception.message)
                  } else {
                    assertEquals(
                        "RBAC ${scenarioSaved.id!!} - User does not have permission $PERMISSION_WRITE",
                        exception.message)
                  }
                } else {
                  assertDoesNotThrow {
                    scenariorunApiService.stopScenarioRun(
                        organizationSaved.id!!, scenarioRunSaved.id!!)
                  }
                }
              }
            }

    fun makeWorkspaceEventHubInfo(eventHubAvailable: Boolean): WorkspaceEventHubInfo {
      return WorkspaceEventHubInfo(
          eventHubNamespace = "eventHubNamespace",
          eventHubAvailable = eventHubAvailable,
          eventHubName = "eventHubName",
          eventHubUri = "eventHubUri",
          eventHubSasKeyName = "eventHubSasKeyName",
          eventHubSasKey = "eventHubSasKey",
          eventHubCredentialType =
              CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication
                  .Strategy
                  .SHARED_ACCESS_POLICY)
    }

    fun makeConnector(name: String = "connector"): Connector {
      return Connector(
          key = UUID.randomUUID().toString(),
          name = name,
          repository = "/repository",
          version = "1.0",
          ioTypes = listOf(Connector.IoTypes.read))
    }

    fun makeDataset(
        organizationId: String,
        connector: Connector,
    ): Dataset {
      return Dataset(
          name = "Dataset",
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

    fun makeSolution(organizationId: String): Solution {
      return Solution(
          id = UUID.randomUUID().toString(),
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
                  mutableListOf(
                      SolutionAccessControl(id = CONNECTED_ADMIN_USER, role = ROLE_ADMIN),
                      SolutionAccessControl(id = TEST_USER_MAIL, role = ROLE_ADMIN))))
    }

    fun makeOrganizationWithRole(userName: String, role: String): Organization {
      return Organization(
          id = UUID.randomUUID().toString(),
          name = "Organization Name",
          ownerId = "my.account-tester@cosmotech.com",
          security =
              OrganizationSecurity(
                  default = ROLE_NONE,
                  accessControlList =
                      mutableListOf(
                          OrganizationAccessControl(id = CONNECTED_ADMIN_USER, role = "admin"),
                          OrganizationAccessControl(userName, role))))
    }

    fun makeWorkspaceWithRole(
        organizationId: String,
        solutionId: String,
        userName: String,
        role: String
    ): Workspace {
      return Workspace(
          key = UUID.randomUUID().toString(),
          name = "Workspace",
          solution =
              WorkspaceSolution(
                  solutionId = solutionId,
              ),
          organizationId = organizationId,
          ownerId = "ownerId",
          security =
              WorkspaceSecurity(
                  ROLE_NONE,
                  mutableListOf(
                      WorkspaceAccessControl(userName, role),
                      WorkspaceAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN))))
    }

    fun makeScenarioWithRole(
        organizationId: String,
        workspaceId: String,
        solutionId: String,
        runTemplateId: String,
        datasetList: MutableList<String>,
        userName: String,
        role: String,
    ): Scenario {
      return Scenario(
          name = UUID.randomUUID().toString(),
          organizationId = organizationId,
          workspaceId = workspaceId,
          solutionId = solutionId,
          runTemplateId = runTemplateId,
          ownerId = "ownerId",
          datasetList = datasetList,
          security =
              ScenarioSecurity(
                  ROLE_NONE,
                  mutableListOf(
                      ScenarioAccessControl(userName, role),
                      ScenarioAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN))))
    }

    fun makeScenarioRun(
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
}
