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
import com.cosmotech.connector.ConnectorApiServiceInterface
import com.cosmotech.connector.domain.Connector
import com.cosmotech.connector.domain.IoTypesEnum
import com.cosmotech.dataset.DatasetApiServiceInterface
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.dataset.domain.DatasetAccessControl
import com.cosmotech.dataset.domain.DatasetConnector
import com.cosmotech.dataset.domain.DatasetSecurity
import com.cosmotech.dataset.domain.IngestionStatusEnum
import com.cosmotech.dataset.repository.DatasetRepository
import com.cosmotech.organization.OrganizationApiServiceInterface
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationSecurity
import com.cosmotech.scenario.ScenarioApiServiceInterface
import com.cosmotech.scenario.domain.Scenario
import com.cosmotech.scenario.domain.ScenarioAccessControl
import com.cosmotech.scenario.domain.ScenarioSecurity
import com.cosmotech.scenariorun.ContainerFactory
import com.cosmotech.scenariorun.ScenarioRunApiServiceInterface
import com.cosmotech.scenariorun.domain.ScenarioRun
import com.cosmotech.scenariorun.domain.ScenarioRunContainer
import com.cosmotech.scenariorun.domain.ScenarioRunSearch
import com.cosmotech.scenariorun.domain.ScenarioRunStartContainers
import com.cosmotech.scenariorun.workflow.WorkflowService
import com.cosmotech.solution.SolutionApiServiceInterface
import com.cosmotech.solution.domain.RunTemplate
import com.cosmotech.solution.domain.Solution
import com.cosmotech.solution.domain.SolutionAccessControl
import com.cosmotech.solution.domain.SolutionSecurity
import com.cosmotech.workspace.WorkspaceApiServiceInterface
import com.cosmotech.workspace.azure.IWorkspaceEventHubService
import com.cosmotech.workspace.azure.WorkspaceEventHubInfo
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceAccessControl
import com.cosmotech.workspace.domain.WorkspaceSecurity
import com.cosmotech.workspace.domain.WorkspaceSolution
import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.SpykBean
import com.redis.om.spring.RediSearchIndexer
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockkStatic
import java.util.*
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

  @Autowired lateinit var csmPlatformProperties: CsmPlatformProperties
  val CONNECTED_ADMIN_USER = "test.admin@cosmotech.com"
  val TEST_USER_MAIL = "testuser@mail.fr"

  @MockkBean lateinit var csmADX: AzureDataExplorerClient
  @MockK private lateinit var workspaceEventHubService: IWorkspaceEventHubService
  @MockK(relaxed = true) private lateinit var azureDataExplorerClient: AzureDataExplorerClient
  @MockK(relaxed = true) private lateinit var containerFactory: ContainerFactory
  @MockK(relaxed = true) private lateinit var workflowService: WorkflowService

  @Autowired lateinit var datasetRepository: DatasetRepository
  @Autowired lateinit var rediSearchIndexer: RediSearchIndexer
  @Autowired lateinit var connectorApiService: ConnectorApiServiceInterface
  @Autowired lateinit var organizationApiService: OrganizationApiServiceInterface
  @SpykBean @Autowired lateinit var datasetApiService: DatasetApiServiceInterface
  @Autowired lateinit var solutionApiService: SolutionApiServiceInterface
  @Autowired lateinit var workspaceApiService: WorkspaceApiServiceInterface
  @Autowired lateinit var scenarioApiService: ScenarioApiServiceInterface
  @Autowired lateinit var scenariorunApiService: ScenarioRunApiServiceInterface

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

    rediSearchIndexer.createIndexFor(Dataset::class.java)
    rediSearchIndexer.createIndexFor(Solution::class.java)
    rediSearchIndexer.createIndexFor(Workspace::class.java)
    rediSearchIndexer.createIndexFor(Scenario::class.java)
    rediSearchIndexer.createIndexFor(ScenarioRun::class.java)
  }

  @TestFactory
  fun `test Organization RBAC deleteHistoricalDataOrganization`() =
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
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              //// TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
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
                      id = TEST_USER_MAIL,
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
  fun `test Organization RBAC deleteHistoricalDataWorkspace`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest(
                "Test Organization RBAC deleteHistoricalDataWorkspace : $role") {
                  every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
                  val connector = makeConnector()
                  val connectorSaved = connectorApiService.registerConnector(connector)
                  val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
                  val organizationSaved = organizationApiService.registerOrganization(organization)
                  val dataset =
                      makeDataset(organizationSaved.id!!, connectorSaved, role = ROLE_ADMIN)
                  val datasetSaved =
                      datasetApiService.createDataset(organizationSaved.id!!, dataset)
                  every { datasetApiService.findDatasetById(any(), any()) } returns
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS }
                  // TODO replace by copy or remove
                  // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
                  // mockk(relaxed = true)
                  val solution = makeSolution(organizationSaved.id!!, role = ROLE_ADMIN)
                  val solutionSaved =
                      solutionApiService.createSolution(organizationSaved.id!!, solution)
                  val workspace =
                      makeWorkspaceWithRole(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          id = TEST_USER_MAIL,
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
                          id = TEST_USER_MAIL,
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
                    assertEquals(
                        "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                        exception.message)
                  } else {
                    assertDoesNotThrow {
                      scenariorunApiService.deleteHistoricalDataWorkspace(
                          organizationSaved.id!!, workspaceSaved.id!!, true)
                    }
                  }
                }
          }

  @TestFactory
  fun `test Dataset RBAC deleteHistoricalDataWorkspace`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Dataset RBAC deleteHistoricalDataWorkspace : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = role)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              //// TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
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
                      id = TEST_USER_MAIL,
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
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenariorunApiService.deleteHistoricalDataWorkspace(
                      organizationSaved.id!!, workspaceSaved.id!!, true)
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC deleteHistoricalDataWorkspace`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Solution RBAC deleteHistoricalDataWorkspace : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              //// TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = role)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
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
                      id = TEST_USER_MAIL,
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
                assertEquals(
                    "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenariorunApiService.deleteHistoricalDataWorkspace(
                      organizationSaved.id!!, workspaceSaved.id!!, true)
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC deleteHistoricalDataWorkspace`() =
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
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              //// TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!, solutionSaved.id!!, id = TEST_USER_MAIL, role = role)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
              val scenario =
                  makeScenarioWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      solutionSaved.runTemplates!!.first().id,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
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
  fun `test Organization RBAC deleteHistoricalDataScenario`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC deleteHistoricalDataScenario : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              //// TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
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
                      id = TEST_USER_MAIL,
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
                      scenariorunApiService.deleteHistoricalDataScenario(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, true)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenariorunApiService.deleteHistoricalDataScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, true)
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC deleteHistoricalDataScenario`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Dataset RBAC deleteHistoricalDataScenario : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = role)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              //// TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
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
                      id = TEST_USER_MAIL,
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
                      scenariorunApiService.deleteHistoricalDataScenario(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, true)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenariorunApiService.deleteHistoricalDataScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, true)
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC deleteHistoricalDataScenario`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Solution RBAC deleteHistoricalDataScenario : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              //// TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = role)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
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
                      id = TEST_USER_MAIL,
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
                      scenariorunApiService.deleteHistoricalDataScenario(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, true)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenariorunApiService.deleteHistoricalDataScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, true)
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC deleteHistoricalDataScenario`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Workspace RBAC deleteHistoricalDataScenario : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              //// TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!, solutionSaved.id!!, id = TEST_USER_MAIL, role = role)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
              val scenario =
                  makeScenarioWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      solutionSaved.runTemplates!!.first().id,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
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
                      scenariorunApiService.deleteHistoricalDataScenario(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, true)
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenariorunApiService.deleteHistoricalDataScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, true)
                }
              }
            }
          }

  @TestFactory
  fun `test Scenario RBAC deleteHistoricalDataScenario`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Scenario RBAC deleteHistoricalDataScenario : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              //// TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
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
                      id = TEST_USER_MAIL,
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
  fun `test Organization RBAC searchScenarioRuns`() =
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
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              //// TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
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
                      id = TEST_USER_MAIL,
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
  fun `test Organization RBAC startScenarioRunContainers`() =
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
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              //// TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
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
                      id = TEST_USER_MAIL,
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
  fun `test Organization RBAC findScenarioRunById`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC findScenarioRunById : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              //// TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
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
                      id = TEST_USER_MAIL,
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
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
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
  fun `test Dataset RBAC findScenarioRunById`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Dataset RBAC findScenarioRunById : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = role)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })

              // TODO replace by copy or remove
              val solution = makeSolution(organizationSaved.id!!, role = ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
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
                      id = TEST_USER_MAIL,
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
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
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
  fun `test Solution RBAC findScenarioRunById`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Solution RBAC findScenarioRunById : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = role)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
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
                      id = TEST_USER_MAIL,
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
                    "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
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
  fun `test Workspace RBAC findScenarioRunById`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Workspace RBAC findScenarioRunById : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!, solutionSaved.id!!, id = TEST_USER_MAIL, role = role)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
              val scenario =
                  makeScenarioWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      solutionSaved.runTemplates!!.first().id,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
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
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
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
  fun `test Scenario RBAC findScenarioRunById`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC findScenarioRunById : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
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
                      id = TEST_USER_MAIL,
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
  fun `test Organization RBAC deleteScenarioRun`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC deleteScenarioRun : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
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
                      id = TEST_USER_MAIL,
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
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenariorunApiService.deleteScenarioRun(
                      organizationSaved.id!!, scenarioRunSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC deleteScenarioRun`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Dataset RBAC deleteScenarioRun : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = role)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
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
                      id = TEST_USER_MAIL,
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
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenariorunApiService.deleteScenarioRun(
                      organizationSaved.id!!, scenarioRunSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC deleteScenarioRun`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Solution RBAC deleteScenarioRun : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = role)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
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
                      id = TEST_USER_MAIL,
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
                assertEquals(
                    "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenariorunApiService.deleteScenarioRun(
                      organizationSaved.id!!, scenarioRunSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC deleteScenarioRun`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Workspace RBAC deleteScenarioRun : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!, solutionSaved.id!!, id = TEST_USER_MAIL, role = role)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
              val scenario =
                  makeScenarioWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      solutionSaved.runTemplates!!.first().id,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
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
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenariorunApiService.deleteScenarioRun(
                      organizationSaved.id!!, scenarioRunSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Scenario RBAC deleteScenarioRun`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Scenario RBAC deleteScenarioRun : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
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
                      id = TEST_USER_MAIL,
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
  fun `test Organization RBAC getScenarioRunStatus`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC getScenarioRunStatus : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
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
                      id = TEST_USER_MAIL,
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
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
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
  fun `test Dataset RBAC getScenarioRunStatus`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Dataset RBAC getScenarioRunStatus : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = role)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
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
                      id = TEST_USER_MAIL,
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
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
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
  fun `test Solution RBAC getScenarioRunStatus`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Solution RBAC getScenarioRunStatus : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = role)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
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
                      id = TEST_USER_MAIL,
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
                    "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
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
  fun `test Workspace RBAC getScenarioRunStatus`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Workspace RBAC getScenarioRunStatus : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!, solutionSaved.id!!, id = TEST_USER_MAIL, role = role)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
              val scenario =
                  makeScenarioWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      solutionSaved.runTemplates!!.first().id,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
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
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
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
  fun `test Scenario RBAC getScenarioRunStatus`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Scenario RBAC getScenarioRunStatus : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
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
                      id = TEST_USER_MAIL,
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
  fun `test Organization RBAC getScenarioRunLogs`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC getScenarioRunLogs : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
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
                      id = TEST_USER_MAIL,
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
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
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
  fun `test Dataset RBAC getScenarioRunLogs`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Dataset RBAC getScenarioRunLogs : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = role)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
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
                      id = TEST_USER_MAIL,
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
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
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
  fun `test Solution RBAC getScenarioRunLogs`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Solution RBAC getScenarioRunLogs : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = role)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
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
                      id = TEST_USER_MAIL,
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
                    "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
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
  fun `test Workspace RBAC getScenarioRunLogs`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Workspace RBAC getScenarioRunLogs : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!, solutionSaved.id!!, id = TEST_USER_MAIL, role = role)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
              val scenario =
                  makeScenarioWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      solutionSaved.runTemplates!!.first().id,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
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
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
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
  fun `test Scenario RBAC getScenarioRunLogs`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Scenario RBAC getScenarioRunLogs : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
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
                      id = TEST_USER_MAIL,
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
  fun `test Organization RBAC getScenarioRunCumulatedLogs`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC getScenarioRunCumulatedLogs : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
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
                      id = TEST_USER_MAIL,
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
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
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
  fun `test Dataset RBAC getScenarioRunCumulatedLogs`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Dataset RBAC getScenarioRunCumulatedLogs : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = role)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
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
                      id = TEST_USER_MAIL,
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
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
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
  fun `test Solution RBAC getScenarioRunCumulatedLogs`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Solution RBAC getScenarioRunCumulatedLogs : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = role)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
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
                      id = TEST_USER_MAIL,
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
                    "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
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
  fun `test Workspace RBAC getScenarioRunCumulatedLogs`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Workspace RBAC getScenarioRunCumulatedLogs : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!, solutionSaved.id!!, id = TEST_USER_MAIL, role = role)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
              val scenario =
                  makeScenarioWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      solutionSaved.runTemplates!!.first().id,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
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
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
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
  fun `test Scenario RBAC getScenarioRunCumulatedLogs`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Scenario RBAC getScenarioRunCumulatedLogs : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
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
                      id = TEST_USER_MAIL,
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
  fun `test Organization RBAC getWorkspaceScenarioRuns`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC getWorkspaceScenarioRuns : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
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
                      id = TEST_USER_MAIL,
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
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
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
  fun `test Dataset RBAC getWorkspaceScenarioRuns`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Dataset RBAC getWorkspaceScenarioRuns : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = role)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
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
                      id = TEST_USER_MAIL,
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
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
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
  fun `test Solution RBAC getWorkspaceScenarioRuns`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Solution RBAC getWorkspaceScenarioRuns : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = role)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
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
                      id = TEST_USER_MAIL,
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
                    "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
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
  fun `test Workspace RBAC getWorkspaceScenarioRuns`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Workspace RBAC getWorkspaceScenarioRuns : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!, solutionSaved.id!!, id = TEST_USER_MAIL, role = role)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
              val scenario =
                  makeScenarioWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      solutionSaved.runTemplates!!.first().id,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
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
  fun `test Organization RBAC getScenarioRuns`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC getScenarioRuns : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
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
                      id = TEST_USER_MAIL,
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
                      scenariorunApiService.getScenarioRuns(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          scenarioSaved.id!!,
                          null,
                          null)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
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
  fun `test Dataset RBAC getScenarioRuns`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Dataset RBAC getScenarioRuns : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = role)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
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
                      id = TEST_USER_MAIL,
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
                      scenariorunApiService.getScenarioRuns(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          scenarioSaved.id!!,
                          null,
                          null)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
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
  fun `test Solution RBAC getScenarioRuns`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Solution RBAC getScenarioRuns : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = role)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
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
                      id = TEST_USER_MAIL,
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
                      scenariorunApiService.getScenarioRuns(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          scenarioSaved.id!!,
                          null,
                          null)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
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
  fun `test Workspace RBAC getScenarioRuns`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Workspace RBAC getScenarioRuns : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!, solutionSaved.id!!, id = TEST_USER_MAIL, role = role)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
              val scenario =
                  makeScenarioWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      solutionSaved.runTemplates!!.first().id,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
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
                      scenariorunApiService.getScenarioRuns(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          scenarioSaved.id!!,
                          null,
                          null)
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
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
  fun `test Scenario RBAC getScenarioRuns`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Scenario RBAC getScenarioRuns : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
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
                      id = TEST_USER_MAIL,
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
  fun `test Organization RBAC runScenario`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC runScenario : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
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
                      id = TEST_USER_MAIL,
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
                      scenariorunApiService.runScenario(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenariorunApiService.runScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC runScenario`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Dataset RBAC runScenario : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = role)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
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
                      id = TEST_USER_MAIL,
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
                      scenariorunApiService.runScenario(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenariorunApiService.runScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC runScenario`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Solution RBAC runScenario : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = role)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
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
                      id = TEST_USER_MAIL,
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
                      scenariorunApiService.runScenario(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenariorunApiService.runScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC runScenario`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Workspace RBAC runScenario : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!, solutionSaved.id!!, id = TEST_USER_MAIL, role = role)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
              val scenario =
                  makeScenarioWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      solutionSaved.runTemplates!!.first().id,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
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
                      scenariorunApiService.runScenario(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenariorunApiService.runScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Scenario RBAC runScenario`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Scenario RBAC runScenario : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
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
                      id = TEST_USER_MAIL,
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
  fun `test Organization RBAC stopScenarioRun`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC stopScenarioRun : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
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
                      id = TEST_USER_MAIL,
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
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenariorunApiService.stopScenarioRun(
                      organizationSaved.id!!, scenarioRunSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC stopScenarioRun`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Dataset RBAC stopScenarioRun : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = role)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
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
                      id = TEST_USER_MAIL,
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
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenariorunApiService.stopScenarioRun(
                      organizationSaved.id!!, scenarioRunSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC stopScenarioRun`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Solution RBAC stopScenarioRun : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = role)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
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
                      id = TEST_USER_MAIL,
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
                assertEquals(
                    "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenariorunApiService.stopScenarioRun(
                      organizationSaved.id!!, scenarioRunSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC stopScenarioRun`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Workspace RBAC stopScenarioRun : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!, solutionSaved.id!!, id = TEST_USER_MAIL, role = role)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
              val scenario =
                  makeScenarioWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      solutionSaved.runTemplates!!.first().id,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
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
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenariorunApiService.stopScenarioRun(
                      organizationSaved.id!!, scenarioRunSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Scenario RBAC stopScenarioRun`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Scenario RBAC stopScenarioRun : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDataset(organizationSaved.id!!, connectorSaved, role = ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, role = ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
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
                      id = TEST_USER_MAIL,
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
            CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication.Strategy
                .SHARED_ACCESS_POLICY)
  }

  fun makeConnector(name: String = "connector"): Connector {
    return Connector(
        key = UUID.randomUUID().toString(),
        name = name,
        repository = "/repository",
        version = "1.0",
        ioTypes = listOf(IoTypesEnum.read))
  }

  fun makeDataset(
      organizationId: String,
      connector: Connector,
      id: String = TEST_USER_MAIL,
      role: String = ROLE_ADMIN
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
        security =
            DatasetSecurity(
                default = ROLE_NONE,
                accessControlList =
                    mutableListOf(
                        DatasetAccessControl(id = CONNECTED_ADMIN_USER, role = ROLE_ADMIN),
                        DatasetAccessControl(id = id, role = role))))
  }

  fun makeSolution(
      organizationId: String,
      id: String = TEST_USER_MAIL,
      role: String = ROLE_ADMIN
  ): Solution {
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
                    SolutionAccessControl(id = id, role = role))))
  }

  fun makeOrganizationWithRole(id: String, role: String): Organization {
    return Organization(
        id = UUID.randomUUID().toString(),
        name = "Organization Name",
        ownerId = "my.account-tester@cosmotech.com",
        security =
            OrganizationSecurity(
                default = ROLE_NONE,
                accessControlList =
                    mutableListOf(
                        OrganizationAccessControl(id = CONNECTED_ADMIN_USER, role = ROLE_ADMIN),
                        OrganizationAccessControl(id, role))))
  }

  fun makeWorkspaceWithRole(
      organizationId: String,
      solutionId: String,
      id: String,
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
                    WorkspaceAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                    WorkspaceAccessControl(id, role))))
  }

  fun makeScenarioWithRole(
      organizationId: String,
      workspaceId: String,
      solutionId: String,
      runTemplateId: String,
      datasetList: MutableList<String>,
      id: String,
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
                    ScenarioAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                    ScenarioAccessControl(id, role))))
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
