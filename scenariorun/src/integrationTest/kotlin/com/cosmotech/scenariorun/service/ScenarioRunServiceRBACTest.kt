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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.runner.RunWith
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
class ScenarioRunServiceRBACTest : CsmRedisTestBase() {

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
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "fake@mail.fr"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf()

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
              connector = mockConnector()
              connectorSaved = connectorApiService.registerConnector(connector)
              organization = mockOrganization(role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              dataset = mockDataset()
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              solution = mockSolution()
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)
              workspace = mockWorkspace()
              workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
              scenario = mockScenario(role = ROLE_ADMIN)
              scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { workflowService.launchScenarioRun(any(), any()) } returns mockScenarioRun()
              scenarioRunSaved =
                  scenariorunApiService.runScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

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
              connector = mockConnector()
              connectorSaved = connectorApiService.registerConnector(connector)
              organization = mockOrganization()
              organizationSaved = organizationApiService.registerOrganization(organization)
              dataset = mockDataset()
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              solution = mockSolution()
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)
              workspace = mockWorkspace(role = role)
              workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
              scenario = mockScenario()
              scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { workflowService.launchScenarioRun(any(), any()) } returns mockScenarioRun()
              scenarioRunSaved =
                  scenariorunApiService.runScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

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
              connector = mockConnector()
              connectorSaved = connectorApiService.registerConnector(connector)
              organization = mockOrganization()
              organizationSaved = organizationApiService.registerOrganization(organization)
              dataset = mockDataset()
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              solution = mockSolution()
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)
              workspace = mockWorkspace()
              workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
              scenario = mockScenario(role = role)
              scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { workflowService.launchScenarioRun(any(), any()) } returns mockScenarioRun()
              scenarioRunSaved =
                  scenariorunApiService.runScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

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
              connector = mockConnector()
              connectorSaved = connectorApiService.registerConnector(connector)
              organization = mockOrganization(role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              dataset = mockDataset()
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              solution = mockSolution()
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)
              workspace = mockWorkspace()
              workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
              scenario = mockScenario()
              scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { workflowService.launchScenarioRun(any(), any()) } returns mockScenarioRun()
              scenarioRunSaved =
                  scenariorunApiService.runScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

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
              connector = mockConnector()
              connectorSaved = connectorApiService.registerConnector(connector)
              organization = mockOrganization(role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              dataset = mockDataset()
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              solution = mockSolution()
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)
              workspace = mockWorkspace()
              workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
              scenario = mockScenario()
              scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { workflowService.launchScenarioRun(any(), any()) } returns mockScenarioRun()
              scenarioRunSaved =
                  scenariorunApiService.runScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

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
              connector = mockConnector()
              connectorSaved = connectorApiService.registerConnector(connector)
              organization = mockOrganization()
              organizationSaved = organizationApiService.registerOrganization(organization)
              dataset = mockDataset()
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              solution = mockSolution()
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)
              workspace = mockWorkspace()
              workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
              scenario = mockScenario(role = role)
              scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { workflowService.launchScenarioRun(any(), any()) } returns mockScenarioRun()
              scenarioRunSaved =
                  scenariorunApiService.runScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

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
              connector = mockConnector()
              connectorSaved = connectorApiService.registerConnector(connector)
              organization = mockOrganization()
              organizationSaved = organizationApiService.registerOrganization(organization)
              dataset = mockDataset()
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              solution = mockSolution()
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)
              workspace = mockWorkspace()
              workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
              scenario = mockScenario(role = role)
              scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { workflowService.launchScenarioRun(any(), any()) } returns mockScenarioRun()
              scenarioRunSaved =
                  scenariorunApiService.runScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

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
              connector = mockConnector()
              connectorSaved = connectorApiService.registerConnector(connector)
              organization = mockOrganization()
              organizationSaved = organizationApiService.registerOrganization(organization)
              dataset = mockDataset()
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              solution = mockSolution()
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)
              workspace = mockWorkspace()
              workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
              scenario = mockScenario(role = role)
              scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { workflowService.launchScenarioRun(any(), any()) } returns mockScenarioRun()
              scenarioRunSaved =
                  scenariorunApiService.runScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

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
              connector = mockConnector()
              connectorSaved = connectorApiService.registerConnector(connector)
              organization = mockOrganization()
              organizationSaved = organizationApiService.registerOrganization(organization)
              dataset = mockDataset()
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              solution = mockSolution()
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)
              workspace = mockWorkspace()
              workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
              scenario = mockScenario(role = role)
              scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { workflowService.launchScenarioRun(any(), any()) } returns mockScenarioRun()
              scenarioRunSaved =
                  scenariorunApiService.runScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

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
              connector = mockConnector()
              connectorSaved = connectorApiService.registerConnector(connector)
              organization = mockOrganization()
              organizationSaved = organizationApiService.registerOrganization(organization)
              dataset = mockDataset()
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              solution = mockSolution()
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)
              workspace = mockWorkspace()
              workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
              scenario = mockScenario(role = role)
              scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { workflowService.launchScenarioRun(any(), any()) } returns mockScenarioRun()
              scenarioRunSaved =
                  scenariorunApiService.runScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

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
              connector = mockConnector()
              connectorSaved = connectorApiService.registerConnector(connector)
              organization = mockOrganization()
              organizationSaved = organizationApiService.registerOrganization(organization)
              dataset = mockDataset()
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              solution = mockSolution()
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)
              workspace = mockWorkspace(role = role)
              workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
              scenario = mockScenario()
              scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { workflowService.launchScenarioRun(any(), any()) } returns mockScenarioRun()
              scenarioRunSaved =
                  scenariorunApiService.runScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

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
              connector = mockConnector()
              connectorSaved = connectorApiService.registerConnector(connector)
              organization = mockOrganization()
              organizationSaved = organizationApiService.registerOrganization(organization)
              dataset = mockDataset()
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              solution = mockSolution()
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)
              workspace = mockWorkspace()
              workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
              scenario = mockScenario(role = role)
              scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { workflowService.launchScenarioRun(any(), any()) } returns mockScenarioRun()
              scenarioRunSaved =
                  scenariorunApiService.runScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

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
              connector = mockConnector()
              connectorSaved = connectorApiService.registerConnector(connector)
              organization = mockOrganization()
              organizationSaved = organizationApiService.registerOrganization(organization)
              dataset = mockDataset()
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              solution = mockSolution()
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)
              workspace = mockWorkspace()
              workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
              scenario = mockScenario(role = role)
              scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { workflowService.launchScenarioRun(any(), any()) } returns mockScenarioRun()
              scenarioRunSaved =
                  scenariorunApiService.runScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

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
              connector = mockConnector()
              connectorSaved = connectorApiService.registerConnector(connector)
              organization = mockOrganization()
              organizationSaved = organizationApiService.registerOrganization(organization)
              dataset = mockDataset()
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              solution = mockSolution()
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)
              workspace = mockWorkspace()
              workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
              scenario = mockScenario(role = role)
              scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { workflowService.launchScenarioRun(any(), any()) } returns mockScenarioRun()
              scenarioRunSaved =
                  scenariorunApiService.runScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

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

  @TestFactory
  fun `test RBAC importScenarioRun`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC importScenarioRun : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              connector = mockConnector()
              connectorSaved = connectorApiService.registerConnector(connector)
              organization = mockOrganization()
              organizationSaved = organizationApiService.registerOrganization(organization)
              dataset = mockDataset()
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              solution = mockSolution()
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)
              workspace = mockWorkspace()
              workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
              scenario = mockScenario(role = role)
              scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { workflowService.launchScenarioRun(any(), any()) } returns mockScenarioRun()
              scenarioRunSaved =
                  scenariorunApiService.runScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              val scenarioRun = ScenarioRun(id = "id")

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenariorunApiService.importScenarioRun(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          scenarioSaved.id!!,
                          scenarioRun)
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
                  scenariorunApiService.importScenarioRun(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, scenarioRun)
                }
              }
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
      roleName: String = FAKE_MAIL,
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
                    description = "RunTemplate1 description")))
  }

  fun mockOrganization(
      id: String = "organizationId",
      roleName: String = FAKE_MAIL,
      role: String = ROLE_ADMIN
  ): Organization {
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
                        OrganizationAccessControl(id = CONNECTED_ADMIN_USER, role = "admin"),
                        OrganizationAccessControl(roleName, role))))
  }

  fun mockWorkspace(
      organizationId: String = organizationSaved.id!!,
      solutionId: String = solutionSaved.id!!,
      name: String = "workspace",
      roleName: String = FAKE_MAIL,
      role: String = ROLE_ADMIN
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
                mutableListOf(
                    WorkspaceAccessControl(roleName, role),
                    WorkspaceAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN))))
  }

  fun mockScenario(
      organizationId: String = organizationSaved.id!!,
      workspaceId: String = workspaceSaved.id!!,
      solutionId: String = solutionSaved.id!!,
      runTemplateId: String = solutionSaved.runTemplates?.get(0)?.id!!,
      name: String = "scenario",
      datasetList: MutableList<String> = mutableListOf(datasetSaved.id!!),
      userName: String = FAKE_MAIL,
      role: String = ROLE_ADMIN
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
                mutableListOf(
                    ScenarioAccessControl(userName, role),
                    ScenarioAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN))))
  }

  fun mockScenarioRun(
      organizationId: String = organizationSaved.id!!,
      workspaceId: String = workspaceSaved.id!!,
      scenarioId: String = scenarioSaved.id!!,
      solutionId: String = solutionSaved.id!!,
      datasetList: MutableList<String> = mutableListOf(datasetSaved.id!!)
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
