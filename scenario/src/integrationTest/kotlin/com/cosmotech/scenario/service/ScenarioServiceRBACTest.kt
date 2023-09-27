// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenario.service

import com.cosmotech.api.azure.adx.AzureDataExplorerClient
import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.events.ScenarioDataDownloadJobInfoRequest
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.rbac.PERMISSION_CREATE_CHILDREN
import com.cosmotech.api.rbac.PERMISSION_DELETE
import com.cosmotech.api.rbac.PERMISSION_READ
import com.cosmotech.api.rbac.PERMISSION_READ_SECURITY
import com.cosmotech.api.rbac.PERMISSION_WRITE
import com.cosmotech.api.rbac.PERMISSION_WRITE_SECURITY
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
import com.cosmotech.scenario.domain.ScenarioRole
import com.cosmotech.scenario.domain.ScenarioRunTemplateParameterValue
import com.cosmotech.scenario.domain.ScenarioSecurity
import com.cosmotech.scenario.domain.ScenarioValidationStatus
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.solution.domain.Solution
import com.cosmotech.workspace.api.WorkspaceApiService
import com.cosmotech.workspace.azure.IWorkspaceEventHubService
import com.cosmotech.workspace.azure.WorkspaceEventHubInfo
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceAccessControl
import com.cosmotech.workspace.domain.WorkspaceRole
import com.cosmotech.workspace.domain.WorkspaceSecurity
import com.cosmotech.workspace.domain.WorkspaceSolution
import com.ninjasquad.springmockk.MockkBean
import com.redis.om.spring.RediSearchIndexer
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import java.util.UUID
import kotlin.test.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest.dynamicTest
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

@ActiveProfiles(profiles = ["scenario-test"])
@ExtendWith(MockKExtension::class)
@ExtendWith(SpringExtension::class)
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ScenarioServiceRBACTest : CsmRedisTestBase() {

  private val FAKE_MAIL = "my.account-tester@cosmotech.com"

  @MockkBean lateinit var csmADX: AzureDataExplorerClient
  @MockK(relaxed = true) private lateinit var workspaceEventHubService: IWorkspaceEventHubService
  @MockK(relaxed = true) private lateinit var azureDataExplorerClient: AzureDataExplorerClient
  @MockK private lateinit var scenarioDataDownloadJobInfoRequest: ScenarioDataDownloadJobInfoRequest

  @Autowired lateinit var rediSearchIndexer: RediSearchIndexer
  @Autowired lateinit var connectorApiService: ConnectorApiService
  @Autowired lateinit var organizationApiService: OrganizationApiService
  @Autowired lateinit var datasetApiService: DatasetApiService
  @Autowired lateinit var solutionApiService: SolutionApiService
  @Autowired lateinit var workspaceApiService: WorkspaceApiService
  @Autowired lateinit var scenarioApiService: ScenarioApiService
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

  @BeforeEach
  fun setUp() {
    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "test.user"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf()

    ReflectionTestUtils.setField(
        scenarioApiService, "workspaceEventHubService", workspaceEventHubService)
    ReflectionTestUtils.setField(
        scenarioApiService, "azureDataExplorerClient", azureDataExplorerClient)
    every { workspaceEventHubService.getWorkspaceEventHubInfo(any(), any(), any()) } returns
        mockWorkspaceEventHubInfo(false)

    rediSearchIndexer.createIndexFor(Organization::class.java)
    rediSearchIndexer.createIndexFor(Solution::class.java)
    rediSearchIndexer.createIndexFor(Workspace::class.java)
    rediSearchIndexer.createIndexFor(Scenario::class.java)

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
            "Scenario",
            mutableListOf(datasetSaved.id!!))

    scenarioSaved =
        scenarioApiService.createScenario(organizationSaved.id!!, workspaceSaved.id!!, scenario)
  }

  @TestFactory
  fun `test RBAC findAllScenarios`() =
      mapOf(
              ROLE_VIEWER to 2,
              ROLE_EDITOR to 3,
              ROLE_VALIDATOR to 4,
              ROLE_NONE to 4,
              ROLE_ADMIN to 5,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test RBAC findAllWorkspaces : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              scenarioApiService.createScenario(
                  organizationSaved.id!!, workspaceSaved.id!!, mockScenario(role = role))
              workspaceApiService.updateWorkspaceAccessControl(
                  organizationSaved.id!!, workspaceSaved.id!!, FAKE_MAIL, WorkspaceRole(ROLE_USER))
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              val scenarios =
                  scenarioApiService.findAllScenarios(
                      organizationSaved.id!!, workspaceSaved.id!!, null, null)
              assertEquals(shouldThrow, scenarios.size)
            }
          }

  @TestFactory
  fun `test RBAC createScenario`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test RBAC createScenario : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(id = "id", userName = FAKE_MAIL))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!, mockWorkspace(userName = FAKE_MAIL, role = role))
              scenario = mockScenario()

              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.createScenario(
                          organizationSaved.id!!, workspaceSaved.id!!, scenario)
                    }
                if (role == ROLE_VALIDATOR || role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_CREATE_CHILDREN",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC deleteAllScenarios`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test RBAC deleteAllScenarios : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(id = "id", userName = FAKE_MAIL))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!, mockWorkspace(userName = FAKE_MAIL, role = role))
              scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, mockScenario())

              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.deleteAllScenarios(
                          organizationSaved.id!!, workspaceSaved.id!!)
                    }
                if (role == ROLE_VALIDATOR || role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_WRITE",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  scenarioApiService.deleteAllScenarios(organizationSaved.id!!, workspaceSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC findAllScenariosByValidationStatus`() =
      mapOf(
              ROLE_VIEWER to 2,
              ROLE_EDITOR to 3,
              ROLE_VALIDATOR to 4,
              ROLE_NONE to 4,
              ROLE_ADMIN to 5,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test RBAC findAllWorkspaces : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              scenarioApiService.createScenario(
                  organizationSaved.id!!, workspaceSaved.id!!, mockScenario(role = role))
              workspaceApiService.updateWorkspaceAccessControl(
                  organizationSaved.id!!, workspaceSaved.id!!, FAKE_MAIL, WorkspaceRole(ROLE_USER))
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              var scenarios =
                  scenarioApiService.findAllScenariosByValidationStatus(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      ScenarioValidationStatus.Draft,
                      null,
                      null)
              assertEquals(shouldThrow, scenarios.size)
              scenarios =
                  scenarioApiService.findAllScenariosByValidationStatus(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      ScenarioValidationStatus.Validated,
                      null,
                      null)
              assertEquals(0, scenarios.size)
              scenarios =
                  scenarioApiService.findAllScenariosByValidationStatus(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      ScenarioValidationStatus.Unknown,
                      null,
                      null)
              assertEquals(0, scenarios.size)
              scenarios =
                  scenarioApiService.findAllScenariosByValidationStatus(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      ScenarioValidationStatus.Rejected,
                      null,
                      null)
              assertEquals(0, scenarios.size)
            }
          }

  @TestFactory
  fun `test RBAC getScenariosTree`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test RBAC getScenariosTree : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(id = "id", userName = FAKE_MAIL))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!, mockWorkspace(userName = FAKE_MAIL, role = role))
              scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, mockScenario())

              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.getScenariosTree(
                          organizationSaved.id!!, workspaceSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.getScenariosTree(organizationSaved.id!!, workspaceSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC findScenarioById`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test RBAC findScenarioById : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(id = "id", userName = FAKE_MAIL))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, mockWorkspace())
              scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      mockScenario(userName = FAKE_MAIL, role = role))

              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.findScenarioById(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${scenarioSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.findScenarioById(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC deleteScenario`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_VALIDATOR to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test RBAC deleteScenario : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(id = "id", userName = FAKE_MAIL))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, mockWorkspace())
              scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      mockScenario(userName = FAKE_MAIL, role = role))

              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.deleteScenario(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                    }
                if (role == ROLE_NONE) {
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
                  scenarioApiService.deleteScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC updateScenario`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test RBAC updateScenario : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(id = "id", userName = FAKE_MAIL))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, mockWorkspace())
              scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      mockScenario(userName = FAKE_MAIL, role = role))

              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.updateScenario(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          scenarioSaved.id!!,
                          mockScenario())
                    }
                if (role == ROLE_NONE) {
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
                  scenarioApiService.updateScenario(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      scenarioSaved.id!!,
                      mockScenario())
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC getScenarioValidationStatusById`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test RBAC getScenarioValidationStatusById : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(id = "id", userName = FAKE_MAIL))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, mockWorkspace())
              scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      mockScenario(userName = FAKE_MAIL, role = role))

              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.getScenarioValidationStatusById(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${scenarioSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.getScenarioValidationStatusById(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC addOrReplaceScenarioParameterValues`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test RBAC addOrReplaceScenarioParameterValues : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(id = "id", userName = FAKE_MAIL))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, mockWorkspace())
              scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      mockScenario(userName = FAKE_MAIL, role = role))

              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.addOrReplaceScenarioParameterValues(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          scenarioSaved.id!!,
                          listOf(ScenarioRunTemplateParameterValue("id", "0")))
                    }
                if (role == ROLE_NONE) {
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
                  scenarioApiService.addOrReplaceScenarioParameterValues(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      scenarioSaved.id!!,
                      listOf(ScenarioRunTemplateParameterValue("id", "0")))
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC removeAllScenarioParameterValues`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test RBAC removeAllScenarioParameterValues : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(id = "id", userName = FAKE_MAIL))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, mockWorkspace())
              scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      mockScenario(userName = FAKE_MAIL, role = role))

              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.removeAllScenarioParameterValues(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                    }
                if (role == ROLE_NONE) {
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
                  scenarioApiService.removeAllScenarioParameterValues(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC downloadScenarioData`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test RBAC downloadScenarioData : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(id = "id", userName = FAKE_MAIL))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, mockWorkspace())
              scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      mockScenario(userName = FAKE_MAIL, role = role))

              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.downloadScenarioData(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${scenarioSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.downloadScenarioData(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC getScenarioDataDownloadJobInfo`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test RBAC getScenarioDataDownloadJobInfo : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(id = "id", userName = FAKE_MAIL))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, mockWorkspace())
              scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      mockScenario(userName = FAKE_MAIL, role = role))

              mockkConstructor(ScenarioDataDownloadJobInfoRequest::class)
              every { anyConstructed<ScenarioDataDownloadJobInfoRequest>().response } returns
                  ("" to "")

              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.getScenarioDataDownloadJobInfo(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, "jobid")
                    }
                assertEquals(
                    "RBAC ${scenarioSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.getScenarioDataDownloadJobInfo(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, "jobid")
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC getScenarioPermissions`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test RBAC getScenarioPermissions : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(id = "id", userName = FAKE_MAIL))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, mockWorkspace())
              scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      mockScenario(userName = FAKE_MAIL, role = role))

              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.getScenarioPermissions(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, role)
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${scenarioSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${scenarioSaved.id!!} - User does not have permission $PERMISSION_READ_SECURITY",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  scenarioApiService.getScenarioPermissions(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, role)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC getScenarioSecurity`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test RBAC getScenarioSecurity : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(id = "id", userName = FAKE_MAIL))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, mockWorkspace())
              scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      mockScenario(userName = FAKE_MAIL, role = role))

              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.getScenarioSecurity(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${scenarioSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${scenarioSaved.id!!} - User does not have permission $PERMISSION_READ_SECURITY",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  scenarioApiService.getScenarioSecurity(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC setScenarioDefaultSecurity`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_VALIDATOR to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test RBAC setScenarioDefaultSecurity : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(id = "id", userName = FAKE_MAIL))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, mockWorkspace())
              scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      mockScenario(userName = FAKE_MAIL, role = role))

              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.setScenarioDefaultSecurity(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          scenarioSaved.id!!,
                          ScenarioRole(ROLE_ADMIN))
                    }
                assertEquals(
                    "RBAC ${scenarioSaved.id!!} - User does not have permission $PERMISSION_WRITE_SECURITY",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.setScenarioDefaultSecurity(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      scenarioSaved.id!!,
                      ScenarioRole(ROLE_ADMIN))
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC addScenarioAccessControl`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_VALIDATOR to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test RBAC addScenarioAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(id = "id", userName = FAKE_MAIL))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, mockWorkspace())
              scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      mockScenario(userName = FAKE_MAIL, role = role))

              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.addScenarioAccessControl(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          scenarioSaved.id!!,
                          ScenarioAccessControl("id", ROLE_ADMIN))
                    }
                assertEquals(
                    "RBAC ${scenarioSaved.id!!} - User does not have permission $PERMISSION_WRITE_SECURITY",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.addScenarioAccessControl(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      scenarioSaved.id!!,
                      ScenarioAccessControl("id", ROLE_ADMIN))
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC getScenarioAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test RBAC getScenarioAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(id = "id", userName = FAKE_MAIL))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, mockWorkspace())
              scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      mockScenario(userName = FAKE_MAIL, role = role))

              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.getScenarioAccessControl(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          scenarioSaved.id!!,
                          FAKE_MAIL)
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${scenarioSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${scenarioSaved.id!!} - User does not have permission $PERMISSION_READ_SECURITY",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  scenarioApiService.getScenarioAccessControl(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, FAKE_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC removeScenarioAccessControl`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_VALIDATOR to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test RBAC removeScenarioAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(id = "id", userName = FAKE_MAIL))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, mockWorkspace())
              scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      mockScenario(userName = FAKE_MAIL, role = role))

              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.removeScenarioAccessControl(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          scenarioSaved.id!!,
                          FAKE_MAIL)
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${scenarioSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${scenarioSaved.id!!} - User does not have permission $PERMISSION_WRITE_SECURITY",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  scenarioApiService.removeScenarioAccessControl(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, FAKE_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC updateScenarioAccessControl`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_VALIDATOR to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test RBAC updateScenarioAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(id = "id", userName = FAKE_MAIL))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, mockWorkspace())
              scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      mockScenario(userName = FAKE_MAIL, role = role))

              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.updateScenarioAccessControl(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          scenarioSaved.id!!,
                          FAKE_MAIL,
                          ScenarioRole(ROLE_VIEWER))
                    }
                assertEquals(
                    "RBAC ${scenarioSaved.id!!} - User does not have permission $PERMISSION_WRITE_SECURITY",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.updateScenarioAccessControl(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      scenarioSaved.id!!,
                      FAKE_MAIL,
                      ScenarioRole(ROLE_VIEWER))
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC getScenarioSecurityUsers`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test RBAC getScenarioSecurityUsers : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(id = "id", userName = FAKE_MAIL))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, mockWorkspace())
              scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      mockScenario(userName = FAKE_MAIL, role = role))

              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.getScenarioSecurityUsers(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${scenarioSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${scenarioSaved.id!!} - User does not have permission $PERMISSION_READ_SECURITY",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  scenarioApiService.getScenarioSecurityUsers(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC importScenario`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test RBAC importScenario : $role") {
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(id = "id", userName = FAKE_MAIL))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())

              workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!, mockWorkspace(userName = FAKE_MAIL))

              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              scenario = mockScenario(userName = FAKE_MAIL, role = role)

              assertDoesNotThrow {
                scenarioApiService.importScenario(
                    organizationSaved.id!!, workspaceSaved.id!!, scenario)
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

  private fun mockConnector(name: String = "name"): Connector {
    return Connector(
        key = UUID.randomUUID().toString(),
        name = name,
        repository = "/repository",
        version = "1.0",
        ioTypes = listOf(Connector.IoTypes.read))
  }

  fun mockDataset(
      organizationId: String = organizationSaved.id!!,
      name: String = "name",
      connector: Connector = connectorSaved
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
        ownerId = "ownerId")
  }

  fun mockOrganization(
      id: String = "id",
      userName: String = FAKE_MAIL,
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
                        OrganizationAccessControl(id = userName, role = role))))
  }

  fun mockWorkspace(
      organizationId: String = organizationSaved.id!!,
      solutionId: String = solutionSaved.id!!,
      name: String = "name",
      userName: String = FAKE_MAIL,
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
                default = ROLE_NONE,
                mutableListOf(
                    WorkspaceAccessControl(id = userName, role = role),
                    WorkspaceAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN))))
  }

  fun mockScenario(
      organizationId: String = organizationSaved.id!!,
      workspaceId: String = workspaceSaved.id!!,
      solutionId: String = solutionSaved.id!!,
      name: String = "name",
      datasetList: MutableList<String> = mutableListOf<String>(),
      parentId: String? = null,
      userName: String = FAKE_MAIL,
      role: String = ROLE_USER,
      validationStatus: ScenarioValidationStatus = ScenarioValidationStatus.Draft
  ): Scenario {
    return Scenario(
        id = UUID.randomUUID().toString(),
        name = name,
        organizationId = organizationId,
        workspaceId = workspaceId,
        solutionId = solutionId,
        ownerId = "ownerId",
        datasetList = datasetList,
        parentId = parentId,
        validationStatus = validationStatus,
        security =
            ScenarioSecurity(
                ROLE_NONE,
                mutableListOf(
                    ScenarioAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                    ScenarioAccessControl(userName, role))))
  }
}
