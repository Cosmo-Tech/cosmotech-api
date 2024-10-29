// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
@file:Suppress("DEPRECATION")

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
import com.cosmotech.connector.domain.IoTypesEnum
import com.cosmotech.dataset.api.DatasetApiService
import com.cosmotech.dataset.domain.*
import com.cosmotech.dataset.repository.DatasetRepository
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
import com.ninjasquad.springmockk.SpykBean
import com.redis.om.spring.RediSearchIndexer
import com.redis.testcontainers.RedisStackContainer
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import java.util.*
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
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.Protocol
import redis.clients.jedis.UnifiedJedis

@ActiveProfiles(profiles = ["scenario-test"])
@ExtendWith(MockKExtension::class)
@ExtendWith(SpringExtension::class)
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Suppress("FunctionName")
class ScenarioServiceRBACTest : CsmRedisTestBase() {

  val CONNECTED_ADMIN_USER = "test.admin@cosmotech.com"
  val TEST_USER_MAIL = "testuser@mail.fr"

  @MockkBean lateinit var csmADX: AzureDataExplorerClient
  @MockK(relaxed = true) private lateinit var workspaceEventHubService: IWorkspaceEventHubService
  @MockK(relaxed = true) private lateinit var azureDataExplorerClient: AzureDataExplorerClient

  @Autowired lateinit var datasetRepository: DatasetRepository
  @Autowired lateinit var rediSearchIndexer: RediSearchIndexer
  @Autowired lateinit var connectorApiService: ConnectorApiService
  @Autowired lateinit var organizationApiService: OrganizationApiService
  @SpykBean @Autowired lateinit var datasetApiService: DatasetApiService
  @Autowired lateinit var solutionApiService: SolutionApiService
  @Autowired lateinit var workspaceApiService: WorkspaceApiService
  @Autowired lateinit var scenarioApiService: ScenarioApiService
  @Autowired lateinit var csmPlatformProperties: CsmPlatformProperties

  lateinit var unfiedJedis: UnifiedJedis

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
        makeWorkspaceEventHubInfo()

    rediSearchIndexer.createIndexFor(Organization::class.java)
    rediSearchIndexer.createIndexFor(Solution::class.java)
    rediSearchIndexer.createIndexFor(Dataset::class.java)
    rediSearchIndexer.createIndexFor(Workspace::class.java)
    rediSearchIndexer.createIndexFor(Scenario::class.java)
    rediSearchIndexer.createIndexFor(Connector::class.java)

    val context = getContext(redisStackServer)
    val containerIp =
        (context.server as RedisStackContainer).containerInfo.networkSettings.ipAddress
    unfiedJedis = UnifiedJedis(HostAndPort(containerIp, Protocol.DEFAULT_PORT))
    ReflectionTestUtils.setField(datasetApiService, "unifiedJedis", unfiedJedis)
  }

  @TestFactory
  fun `test Organization RBAC findAllScenarios`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Organization RBAC findAllScenarios : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              scenarioApiService.createScenario(
                  organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.findAllScenarios(
                          organizationSaved.id!!, workspaceSaved.id!!, null, null)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.findAllScenarios(
                      organizationSaved.id!!, workspaceSaved.id!!, null, null)
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC findAllScenarios`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Dataset RBAC findAllScenarios : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, role)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              scenarioApiService.createScenario(
                  organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.findAllScenarios(
                          organizationSaved.id!!, workspaceSaved.id!!, null, null)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.findAllScenarios(
                      organizationSaved.id!!, workspaceSaved.id!!, null, null)
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC findAllScenarios`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Solution RBAC findAllScenarios : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, role)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              scenarioApiService.createScenario(
                  organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.findAllScenarios(
                          organizationSaved.id!!, workspaceSaved.id!!, null, null)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.findAllScenarios(
                      organizationSaved.id!!, workspaceSaved.id!!, null, null)
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC findAllScenarios`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Workspace RBAC findAllScenarios : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              scenarioApiService.createScenario(
                  organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.findAllScenarios(
                          organizationSaved.id!!, workspaceSaved.id!!, null, null)
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.findAllScenarios(
                      organizationSaved.id!!, workspaceSaved.id!!, null, null)
                }
              }
            }
          }

  // parent scenario RBAC should not have influence SDCOSMO-1729
  @TestFactory
  fun `test parent scenario RBAC findAllScenarios`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Workspace RBAC findAllScenarios : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioParent =
                  makeScenarioWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = role)
              val scenarioParentSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioParent)
              scenario.parentId = scenarioParentSaved.id
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.findAllScenarios(
                          organizationSaved.id!!, workspaceSaved.id!!, null, null)
                    }
                assertEquals(
                    "RBAC ${scenarioParentSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.findAllScenarios(
                      organizationSaved.id!!, workspaceSaved.id!!, null, null)
                }
              }
            }
          }
  @TestFactory
  fun `test Organization RBAC createScenario`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Organization RBAC createScenario : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              scenarioApiService.createScenario(
                  organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.createScenario(
                          organizationSaved.id!!, workspaceSaved.id!!, scenario)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC createScenario`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Dataset RBAC createScenario : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, role)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              scenarioApiService.createScenario(
                  organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.createScenario(
                          organizationSaved.id!!, workspaceSaved.id!!, scenario)
                    }
                if (role == ROLE_NONE || role == ROLE_VALIDATOR) {
                  assertEquals(
                      "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
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
  fun `test Solution RBAC createScenario`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Solution RBAC createScenario : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, role)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              scenarioApiService.createScenario(
                  organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.createScenario(
                          organizationSaved.id!!, workspaceSaved.id!!, scenario)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC createScenario`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Workspace RBAC createScenario : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              scenarioApiService.createScenario(
                  organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.createScenario(
                          organizationSaved.id!!, workspaceSaved.id!!, scenario)
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_CREATE_CHILDREN",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC deleteAllScenarios`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Organization RBAC deleteAllScenarios : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              scenarioApiService.createScenario(
                  organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.deleteAllScenarios(
                          organizationSaved.id!!, workspaceSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.deleteAllScenarios(organizationSaved.id!!, workspaceSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC deleteAllScenarios`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Dataset RBAC deleteAllScenarios : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, role)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              scenarioApiService.createScenario(
                  organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.deleteAllScenarios(
                          organizationSaved.id!!, workspaceSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.deleteAllScenarios(organizationSaved.id!!, workspaceSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC deleteAllScenarios`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Solution RBAC deleteAllScenarios : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, role)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              scenarioApiService.createScenario(
                  organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.deleteAllScenarios(
                          organizationSaved.id!!, workspaceSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.deleteAllScenarios(organizationSaved.id!!, workspaceSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC deleteAllScenarios`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Workspace RBAC deleteAllScenarios : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              scenarioApiService.createScenario(
                  organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.deleteAllScenarios(
                          organizationSaved.id!!, workspaceSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_WRITE",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.deleteAllScenarios(organizationSaved.id!!, workspaceSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC findAllScenariosByValidationStatus`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Organization RBAC findAllScenariosByValidationStatus : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              scenarioApiService.createScenario(
                  organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.findAllScenarios(
                          organizationSaved.id!!, workspaceSaved.id!!, null, null)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.findAllScenarios(
                      organizationSaved.id!!, workspaceSaved.id!!, null, null)
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC findAllScenariosByValidationStatus`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Dataset RBAC findAllScenariosByValidationStatus : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, role)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              scenarioApiService.createScenario(
                  organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.findAllScenarios(
                          organizationSaved.id!!, workspaceSaved.id!!, null, null)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.findAllScenarios(
                      organizationSaved.id!!, workspaceSaved.id!!, null, null)
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC findAllScenariosByValidationStatus`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test solutionSaved RBAC findAllScenariosByValidationStatus : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, role)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              scenarioApiService.createScenario(
                  organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.findAllScenarios(
                          organizationSaved.id!!, workspaceSaved.id!!, null, null)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.findAllScenarios(
                      organizationSaved.id!!, workspaceSaved.id!!, null, null)
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC findAllScenariosByValidationStatus`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Workspace RBAC findAllScenariosByValidationStatus : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              scenarioApiService.createScenario(
                  organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.findAllScenarios(
                          organizationSaved.id!!, workspaceSaved.id!!, null, null)
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.findAllScenarios(
                      organizationSaved.id!!, workspaceSaved.id!!, null, null)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC getScenariosTree`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Organization RBAC getScenariosTree : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              scenarioApiService.createScenario(
                  organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.getScenariosTree(
                          organizationSaved.id!!, workspaceSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.getScenariosTree(organizationSaved.id!!, workspaceSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC getScenariosTree`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Dataset RBAC getScenariosTree : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, role)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              scenarioApiService.createScenario(
                  organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.getScenariosTree(
                          organizationSaved.id!!, workspaceSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.getScenariosTree(organizationSaved.id!!, workspaceSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC getScenariosTree`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Solution RBAC getScenariosTree : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, role)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              scenarioApiService.createScenario(
                  organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.getScenariosTree(
                          organizationSaved.id!!, workspaceSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.getScenariosTree(organizationSaved.id!!, workspaceSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC getScenariosTree`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Workspace RBAC getScenariosTree : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              scenarioApiService.createScenario(
                  organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

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
  fun `test Organization RBAC findScenarioById`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Organization RBAC findScenarioById : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.findScenarioById(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
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
  fun `test Dataset RBAC findScenarioById`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Dataset RBAC findScenarioById : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, role)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.findScenarioById(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
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
  fun `test Solution RBAC findScenarioById`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Solution RBAC findScenarioById : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, role)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.findScenarioById(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
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
  fun `test Workspace RBAC findScenarioById`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Workspace RBAC findScenarioById : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.findScenarioById(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
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
  fun `test Scenario RBAC findScenarioById`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Scenario RBAC findScenarioById : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = role)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

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
  fun `test Organization RBAC deleteScenario`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Organization RBAC deleteScenario : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              every { datasetApiService.deleteDataset(any(), any()) } returns Unit
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.deleteScenario(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.deleteScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC deleteScenario`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Dataset RBAC deleteScenario : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, role)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              every { datasetApiService.deleteDataset(any(), any()) } returns Unit
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.deleteScenario(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.deleteScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC deleteScenario`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Solution RBAC deleteScenario : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              every { datasetApiService.deleteDataset(any(), any()) } returns Unit
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, role)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.deleteScenario(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.deleteScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC deleteScenario`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Workspace RBAC deleteScenario : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              every { datasetApiService.deleteDataset(any(), any()) } returns Unit
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.deleteScenario(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.deleteScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Scenario RBAC deleteScenario`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_VALIDATOR to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false)
          .map { (role, shouldThrow) ->
            dynamicTest("Test Scenario RBAC deleteScenario : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              every { datasetApiService.deleteDataset(any(), any()) } returns Unit
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = role)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.deleteScenario(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${scenarioSaved.id!!} - User does not have permission $PERMISSION_DELETE",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.deleteScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC updateScenario`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Organization RBAC updateScenario : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.updateScenario(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          scenarioSaved.id!!,
                          makeScenarioWithRole(
                              organizationSaved.id!!,
                              workspaceSaved.id!!,
                              solutionSaved.id!!,
                              mutableListOf(datasetSaved.id!!),
                              id = TEST_USER_MAIL,
                              role = role))
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.updateScenario(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      scenarioSaved.id!!,
                      makeScenarioWithRole(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          solutionSaved.id!!,
                          mutableListOf(datasetSaved.id!!),
                          id = TEST_USER_MAIL,
                          role = role))
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC updateScenario`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Dataset RBAC updateScenario : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, role)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.updateScenario(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          scenarioSaved.id!!,
                          makeScenarioWithRole(
                              organizationSaved.id!!,
                              workspaceSaved.id!!,
                              solutionSaved.id!!,
                              mutableListOf(datasetSaved.id!!),
                              id = TEST_USER_MAIL,
                              role = role))
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.updateScenario(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      scenarioSaved.id!!,
                      makeScenarioWithRole(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          solutionSaved.id!!,
                          mutableListOf(datasetSaved.id!!),
                          id = TEST_USER_MAIL,
                          role = role))
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC updateScenario`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Solution RBAC updateScenario : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, role)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.updateScenario(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          scenarioSaved.id!!,
                          makeScenarioWithRole(
                              organizationSaved.id!!,
                              workspaceSaved.id!!,
                              solutionSaved.id!!,
                              mutableListOf(datasetSaved.id!!),
                              id = TEST_USER_MAIL,
                              role = role))
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.updateScenario(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      scenarioSaved.id!!,
                      makeScenarioWithRole(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          solutionSaved.id!!,
                          mutableListOf(datasetSaved.id!!),
                          id = TEST_USER_MAIL,
                          role = role))
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC updateScenario`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Workspace RBAC updateScenario : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.updateScenario(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          scenarioSaved.id!!,
                          makeScenarioWithRole(
                              organizationSaved.id!!,
                              workspaceSaved.id!!,
                              solutionSaved.id!!,
                              mutableListOf(datasetSaved.id!!),
                              id = TEST_USER_MAIL,
                              role = role))
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.updateScenario(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      scenarioSaved.id!!,
                      makeScenarioWithRole(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          solutionSaved.id!!,
                          mutableListOf(datasetSaved.id!!),
                          id = TEST_USER_MAIL,
                          role = role))
                }
              }
            }
          }

  @TestFactory
  fun `test Scenario RBAC updateScenario`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Scenario RBAC updateScenario : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = role)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.updateScenario(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          scenarioSaved.id!!,
                          makeScenarioWithRole(
                              organizationSaved.id!!,
                              workspaceSaved.id!!,
                              solutionSaved.id!!,
                              mutableListOf(datasetSaved.id!!),
                              id = TEST_USER_MAIL,
                              role = role))
                    }
                assertEquals(
                    "RBAC ${scenarioSaved.id!!} - User does not have permission $PERMISSION_WRITE",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.updateScenario(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      scenarioSaved.id!!,
                      makeScenarioWithRole(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          solutionSaved.id!!,
                          mutableListOf(datasetSaved.id!!),
                          id = TEST_USER_MAIL,
                          role = role))
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC getScenarioValidationStatusById`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Organization RBAC getScenarioValidationStatusById : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.getScenarioValidationStatusById(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
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
  fun `test Dataset RBAC getScenarioValidationStatusById`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Dataset RBAC getScenarioValidationStatusById : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, role)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.getScenarioValidationStatusById(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
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
  fun `test Solution RBAC getScenarioValidationStatusById`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Solution RBAC getScenarioValidationStatusById : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, role)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.getScenarioValidationStatusById(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
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
  fun `test Workspace RBAC getScenarioValidationStatusById`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Workspace RBAC getScenarioValidationStatusById : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.getScenarioValidationStatusById(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
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
  fun `test Scenario RBAC getScenarioValidationStatusById`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Scenario RBAC getScenarioValidationStatusById : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              materializeTwingraph(datasetSaved)
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = role)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

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
  fun `test Organization RBAC addOrReplaceScenarioParameterValues`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Organization RBAC addOrReplaceScenarioParameterValues : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.addOrReplaceScenarioParameterValues(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          scenarioSaved.id!!,
                          listOf(ScenarioRunTemplateParameterValue("id", "0")))
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
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
  fun `test Dataset RBAC addOrReplaceScenarioParameterValues`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Dataset RBAC addOrReplaceScenarioParameterValues : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.addOrReplaceScenarioParameterValues(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          scenarioSaved.id!!,
                          listOf(ScenarioRunTemplateParameterValue("id", "0")))
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
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
  fun `test Solution RBAC addOrReplaceScenarioParameterValues`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Solution RBAC addOrReplaceScenarioParameterValues : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, role)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.addOrReplaceScenarioParameterValues(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          scenarioSaved.id!!,
                          listOf(ScenarioRunTemplateParameterValue("id", "0")))
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
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
  fun `test Workspace RBAC addOrReplaceScenarioParameterValues`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Workspace RBAC addOrReplaceScenarioParameterValues : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.addOrReplaceScenarioParameterValues(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          scenarioSaved.id!!,
                          listOf(ScenarioRunTemplateParameterValue("id", "0")))
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
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
  fun `test Scenario RBAC addOrReplaceScenarioParameterValues`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Scenario RBAC addOrReplaceScenarioParameterValues : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = role)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.addOrReplaceScenarioParameterValues(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          scenarioSaved.id!!,
                          listOf(ScenarioRunTemplateParameterValue("id", "0")))
                    }
                assertEquals(
                    "RBAC ${scenarioSaved.id!!} - User does not have permission $PERMISSION_WRITE",
                    exception.message)
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
  fun `test Organization RBAC removeAllScenarioParameterValues`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Organization RBAC removeAllScenarioParameterValues : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.removeAllScenarioParameterValues(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.removeAllScenarioParameterValues(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC removeAllScenarioParameterValues`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Dataset RBAC removeAllScenarioParameterValues : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, role)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.removeAllScenarioParameterValues(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.removeAllScenarioParameterValues(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC removeAllScenarioParameterValues`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Solution RBAC removeAllScenarioParameterValues : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, role)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.removeAllScenarioParameterValues(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.removeAllScenarioParameterValues(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC removeAllScenarioParameterValues`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Workspace RBAC removeAllScenarioParameterValues : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              materializeTwingraph(datasetSaved)
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.removeAllScenarioParameterValues(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.removeAllScenarioParameterValues(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Scenario RBAC removeAllScenarioParameterValues`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Scenario RBAC removeAllScenarioParameterValues : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = role)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.removeAllScenarioParameterValues(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${scenarioSaved.id!!} - User does not have permission $PERMISSION_WRITE",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.removeAllScenarioParameterValues(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC downloadScenarioData`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Organization RBAC downloadScenarioData : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.downloadScenarioData(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
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
  fun `test Dataset RBAC downloadScenarioData`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Dataset RBAC downloadScenarioData : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, role)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.downloadScenarioData(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
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
  fun `test Solution RBAC downloadScenarioData`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Solution RBAC downloadScenarioData : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, role)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.downloadScenarioData(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
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
  fun `test Workspace RBAC downloadScenarioData`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Workspace RBAC downloadScenarioData : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.downloadScenarioData(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
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
  fun `test Scenario RBAC downloadScenarioData`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Scenario RBAC downloadScenarioData : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = role)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

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
  fun `test Organization RBAC getScenarioDataDownloadJobInfo`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Organization RBAC getScenarioDataDownloadJobInfo : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)

              mockkConstructor(ScenarioDataDownloadJobInfoRequest::class)
              every { anyConstructed<ScenarioDataDownloadJobInfoRequest>().response } returns
                  ("" to "")

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.getScenarioDataDownloadJobInfo(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, "jobId")
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.getScenarioDataDownloadJobInfo(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, "jobId")
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC getScenarioDataDownloadJobInfo`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Dataset RBAC getScenarioDataDownloadJobInfo : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, role)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)

              mockkConstructor(ScenarioDataDownloadJobInfoRequest::class)
              every { anyConstructed<ScenarioDataDownloadJobInfoRequest>().response } returns
                  ("" to "")

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.getScenarioDataDownloadJobInfo(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, "jobId")
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.getScenarioDataDownloadJobInfo(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, "jobId")
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC getScenarioDataDownloadJobInfo`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Solution RBAC getScenarioDataDownloadJobInfo : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, role)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)

              mockkConstructor(ScenarioDataDownloadJobInfoRequest::class)
              every { anyConstructed<ScenarioDataDownloadJobInfoRequest>().response } returns
                  ("" to "")

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.getScenarioDataDownloadJobInfo(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, "jobId")
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.getScenarioDataDownloadJobInfo(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, "jobId")
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC getScenarioDataDownloadJobInfo`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Workspace RBAC getScenarioDataDownloadJobInfo : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)

              mockkConstructor(ScenarioDataDownloadJobInfoRequest::class)
              every { anyConstructed<ScenarioDataDownloadJobInfoRequest>().response } returns
                  ("" to "")

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.getScenarioDataDownloadJobInfo(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, "jobId")
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.getScenarioDataDownloadJobInfo(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, "jobId")
                }
              }
            }
          }

  @TestFactory
  fun `test Scenario RBAC getScenarioDataDownloadJobInfo`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Scenario RBAC getScenarioDataDownloadJobInfo : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = role)
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)

              mockkConstructor(ScenarioDataDownloadJobInfoRequest::class)
              every { anyConstructed<ScenarioDataDownloadJobInfoRequest>().response } returns
                  ("" to "")

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.getScenarioDataDownloadJobInfo(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, "jobId")
                    }
                assertEquals(
                    "RBAC ${scenarioSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.getScenarioDataDownloadJobInfo(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, "jobId")
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC getScenarioPermissions`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Organization RBAC getScenarioPermissions : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.getScenarioPermissions(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, role)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.getScenarioPermissions(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, role)
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC getScenarioPermissions`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Dataset RBAC getScenarioPermissions : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, role)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.getScenarioPermissions(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, role)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.getScenarioPermissions(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, role)
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC getScenarioPermissions`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Solution RBAC getScenarioPermissions : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, role)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.getScenarioPermissions(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, role)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.getScenarioPermissions(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, role)
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC getScenarioPermissions`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Workspace RBAC getScenarioPermissions : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.getScenarioPermissions(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, role)
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.getScenarioPermissions(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, role)
                }
              }
            }
          }

  @TestFactory
  fun `test Scenario RBAC getScenarioPermissions`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Scenario RBAC getScenarioPermissions : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = role)
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.getScenarioPermissions(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, role)
                    }
                assertEquals(
                    "RBAC ${scenarioSaved.id!!} - User does not have permission $PERMISSION_READ_SECURITY",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.getScenarioPermissions(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, role)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC getScenarioSecurity`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Organization RBAC getScenarioSecurity : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.getScenarioSecurity(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.getScenarioSecurity(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC getScenarioSecurity`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Dataset RBAC getScenarioSecurity : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, role)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.getScenarioSecurity(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.getScenarioSecurity(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC getScenarioSecurity`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Solution RBAC getScenarioSecurity : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, role)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.getScenarioSecurity(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.getScenarioSecurity(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC getScenarioSecurity`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Workspace RBAC getScenarioSecurity : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.getScenarioSecurity(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.getScenarioSecurity(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Scenario RBAC getScenarioSecurity`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Scenario RBAC getScenarioSecurity : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = role)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.getScenarioSecurity(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${scenarioSaved.id!!} - User does not have permission $PERMISSION_READ_SECURITY",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.getScenarioSecurity(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC setScenarioDefaultSecurity`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Organization RBAC setScenarioDefaultSecurity : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              materializeTwingraph(datasetSaved)
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

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
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
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
  fun `test Dataset RBAC setScenarioDefaultSecurity`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Dataset RBAC setScenarioDefaultSecurity : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, role)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              materializeTwingraph(datasetSaved)
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

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
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
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
  fun `test Solution RBAC setScenarioDefaultSecurity`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Solution RBAC setScenarioDefaultSecurity : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              materializeTwingraph(datasetSaved)
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, role)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

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
                    "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
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
  fun `test Workspace RBAC setScenarioDefaultSecurity`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Workspace RBAC setScenarioDefaultSecurity : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

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
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
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
  fun `test Scenario RBAC setScenarioDefaultSecurity`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_VALIDATOR to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Scenario RBAC setScenarioDefaultSecurity : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = role)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

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
  fun `test Organization RBAC addScenarioAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Organization RBAC addScenarioAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

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
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
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
  fun `test Dataset RBAC addScenarioAccessControl with workspace datasetCopy to true`() =
      listOf(
              ROLE_VIEWER,
              ROLE_EDITOR,
              ROLE_VALIDATOR,
              ROLE_USER,
              ROLE_NONE,
              ROLE_ADMIN,
          )
          .forEach { role ->
            dynamicTest("Test Dataset RBAC addScenarioAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, role)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              assertDoesNotThrow {
                scenarioApiService.addScenarioAccessControl(
                    organizationSaved.id!!,
                    workspaceSaved.id!!,
                    scenarioSaved.id!!,
                    ScenarioAccessControl("id", ROLE_ADMIN))
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC addScenarioAccessControl with workspace datasetCopy to false`() =
      listOf(
              ROLE_VIEWER,
              ROLE_EDITOR,
              ROLE_VALIDATOR,
              ROLE_USER,
              ROLE_NONE,
              ROLE_ADMIN,
          )
          .map { role ->
            dynamicTest("Test Dataset RBAC addScenarioAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, role)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              workspace.datasetCopy = false
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
              val scenario =
                  makeScenarioWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              assertDoesNotThrow {
                scenarioApiService.addScenarioAccessControl(
                    organizationSaved.id!!,
                    workspaceSaved.id!!,
                    scenarioSaved.id!!,
                    ScenarioAccessControl("id", ROLE_ADMIN))
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC addScenarioAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Solution RBAC addScenarioAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, role)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

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
                    "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
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
  fun `test Workspace RBAC addScenarioAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Workspace RBAC addScenarioAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              materializeTwingraph(datasetSaved)
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

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
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
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
  fun `test Scenario RBAC addScenarioAccessControl`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_VALIDATOR to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Scenario RBAC addScenarioAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })

              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = role)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

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
  fun `test Organization RBAC getScenarioAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Organization RBAC getScenarioAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.getScenarioAccessControl(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          scenarioSaved.id!!,
                          TEST_USER_MAIL)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.getScenarioAccessControl(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      scenarioSaved.id!!,
                      TEST_USER_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC getScenarioAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Dataset RBAC getScenarioAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, role)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.getScenarioAccessControl(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          scenarioSaved.id!!,
                          TEST_USER_MAIL)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.getScenarioAccessControl(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      scenarioSaved.id!!,
                      TEST_USER_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC getScenarioAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Solution RBAC getScenarioAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, role)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.getScenarioAccessControl(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          scenarioSaved.id!!,
                          TEST_USER_MAIL)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.getScenarioAccessControl(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      scenarioSaved.id!!,
                      TEST_USER_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC getScenarioAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Workspace RBAC getScenarioAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.getScenarioAccessControl(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          scenarioSaved.id!!,
                          TEST_USER_MAIL)
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.getScenarioAccessControl(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      scenarioSaved.id!!,
                      TEST_USER_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test Scenario RBAC getScenarioAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Scenario RBAC getScenarioAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = role)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.getScenarioAccessControl(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          scenarioSaved.id!!,
                          TEST_USER_MAIL)
                    }
                assertEquals(
                    "RBAC ${scenarioSaved.id!!} - User does not have permission $PERMISSION_READ_SECURITY",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.getScenarioAccessControl(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      scenarioSaved.id!!,
                      TEST_USER_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC removeScenarioAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Organization RBAC removeScenarioAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.removeScenarioAccessControl(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          scenarioSaved.id!!,
                          TEST_USER_MAIL)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.removeScenarioAccessControl(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      scenarioSaved.id!!,
                      TEST_USER_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC removeScenarioAccessControl`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Dataset RBAC removeScenarioAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, role)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.removeScenarioAccessControl(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          scenarioSaved.id!!,
                          TEST_USER_MAIL)
                    }
                if (role == ROLE_NONE || role == ROLE_VALIDATOR) {
                  assertEquals(
                      "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_WRITE_SECURITY",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  scenarioApiService.removeScenarioAccessControl(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      scenarioSaved.id!!,
                      TEST_USER_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC removeScenarioAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Solution RBAC removeScenarioAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.removeScenarioAccessControl(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          scenarioSaved.id!!,
                          TEST_USER_MAIL)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.removeScenarioAccessControl(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      scenarioSaved.id!!,
                      TEST_USER_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC removeScenarioAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Workspace RBAC removeScenarioAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.removeScenarioAccessControl(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          scenarioSaved.id!!,
                          TEST_USER_MAIL)
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.removeScenarioAccessControl(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      scenarioSaved.id!!,
                      TEST_USER_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test Scenario RBAC removeScenarioAccessControl`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_VALIDATOR to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Scenario RBAC removeScenarioAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = role)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.removeScenarioAccessControl(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          scenarioSaved.id!!,
                          TEST_USER_MAIL)
                    }
                assertEquals(
                    "RBAC ${scenarioSaved.id!!} - User does not have permission $PERMISSION_WRITE_SECURITY",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.removeScenarioAccessControl(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      scenarioSaved.id!!,
                      TEST_USER_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC updateScenarioAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Organization RBAC updateScenarioAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              every { datasetApiService.getDatasetSecurityUsers(any(), any()) } returns
                  listOf(TEST_USER_MAIL, CONNECTED_ADMIN_USER)
              every {
                datasetApiService.updateDatasetAccessControl(any(), any(), any(), any())
              } returns mockk()
              val scenario =
                  makeScenarioWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.updateScenarioAccessControl(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          scenarioSaved.id!!,
                          TEST_USER_MAIL,
                          ScenarioRole(ROLE_VIEWER))
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.updateScenarioAccessControl(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      scenarioSaved.id!!,
                      TEST_USER_MAIL,
                      ScenarioRole(ROLE_VIEWER))
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC updateScenarioAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Dataset RBAC updateScenarioAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, role)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              every { datasetApiService.getDatasetSecurityUsers(any(), any()) } returns
                  listOf(TEST_USER_MAIL, CONNECTED_ADMIN_USER)
              every {
                datasetApiService.updateDatasetAccessControl(any(), any(), any(), any())
              } returns mockk()
              val scenario =
                  makeScenarioWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.updateScenarioAccessControl(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          scenarioSaved.id!!,
                          TEST_USER_MAIL,
                          ScenarioRole(ROLE_VIEWER))
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.updateScenarioAccessControl(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      scenarioSaved.id!!,
                      TEST_USER_MAIL,
                      ScenarioRole(ROLE_VIEWER))
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC updateScenarioAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Solution RBAC updateScenarioAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, role)
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
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              every { datasetApiService.getDatasetSecurityUsers(any(), any()) } returns
                  listOf(TEST_USER_MAIL, CONNECTED_ADMIN_USER)
              every {
                datasetApiService.updateDatasetAccessControl(any(), any(), any(), any())
              } returns mockk()
              val scenario =
                  makeScenarioWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.updateScenarioAccessControl(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          scenarioSaved.id!!,
                          TEST_USER_MAIL,
                          ScenarioRole(ROLE_VIEWER))
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.updateScenarioAccessControl(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      scenarioSaved.id!!,
                      TEST_USER_MAIL,
                      ScenarioRole(ROLE_VIEWER))
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC updateScenarioAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Workspace RBAC updateScenarioAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!, solutionSaved.id!!, id = TEST_USER_MAIL, role = role)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              every { datasetApiService.getDatasetSecurityUsers(any(), any()) } returns
                  listOf(TEST_USER_MAIL, CONNECTED_ADMIN_USER)
              every {
                datasetApiService.updateDatasetAccessControl(any(), any(), any(), any())
              } returns mockk()
              val scenario =
                  makeScenarioWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.updateScenarioAccessControl(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          scenarioSaved.id!!,
                          TEST_USER_MAIL,
                          ScenarioRole(ROLE_VIEWER))
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.updateScenarioAccessControl(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      scenarioSaved.id!!,
                      TEST_USER_MAIL,
                      ScenarioRole(ROLE_VIEWER))
                }
              }
            }
          }

  @TestFactory
  fun `test Scenario RBAC updateScenarioAccessControl`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_VALIDATOR to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Scenario RBAC updateScenarioAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              every { datasetApiService.getDatasetSecurityUsers(any(), any()) } returns
                  listOf(TEST_USER_MAIL, CONNECTED_ADMIN_USER)
              every {
                datasetApiService.updateDatasetAccessControl(any(), any(), any(), any())
              } returns mockk()
              val scenario =
                  makeScenarioWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = role)
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.updateScenarioAccessControl(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          scenarioSaved.id!!,
                          TEST_USER_MAIL,
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
                      TEST_USER_MAIL,
                      ScenarioRole(ROLE_VIEWER))
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC getScenarioSecurityUsers`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Organization RBAC getScenarioSecurityUsers : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.getScenarioSecurityUsers(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.getScenarioSecurityUsers(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC getScenarioSecurityUsers`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Dataset RBAC getScenarioSecurityUsers : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, role)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.getScenarioSecurityUsers(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.getScenarioSecurityUsers(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC getScenarioSecurityUsers`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Solution RBAC getScenarioSecurityUsers : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, role)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.getScenarioSecurityUsers(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.getScenarioSecurityUsers(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC getScenarioSecurityUsers`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Workspace RBAC getScenarioSecurityUsers : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.getScenarioSecurityUsers(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.getScenarioSecurityUsers(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Scenario RBAC getScenarioSecurityUsers`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Scenario RBAC getScenarioSecurityUsers : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val connector = makeConnector()
              val connectorSaved = connectorApiService.registerConnector(connector)
              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDataset(organizationSaved.id!!, connectorSaved, TEST_USER_MAIL, ROLE_ADMIN)
              var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              datasetSaved =
                  datasetRepository.save(
                      datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
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
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = role)
              // TODO replace by copy or remove
              // every { datasetApiService.createSubDataset(any(), any(), any()) } returns
              // datasetSaved
              val scenarioSaved =
                  scenarioApiService.createScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      scenarioApiService.getScenarioSecurityUsers(
                          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${scenarioSaved.id!!} - User does not have permission $PERMISSION_READ_SECURITY",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  scenarioApiService.getScenarioSecurityUsers(
                      organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                }
              }
            }
          }

  private fun materializeTwingraph(dataset: Dataset, createTwingraph: Boolean = true): Dataset {
    dataset.apply {
      if (createTwingraph) {
        unfiedJedis.graphQuery(this.twingraphId, "CREATE (n:labelrouge)")
      }
      this.ingestionStatus = IngestionStatusEnum.SUCCESS
    }
    return datasetRepository.save(dataset)
  }

  private fun makeWorkspaceEventHubInfo(): WorkspaceEventHubInfo {
    return WorkspaceEventHubInfo(
        eventHubNamespace = "eventHubNamespace",
        eventHubAvailable = false,
        eventHubName = "eventHubName",
        eventHubUri = "eventHubUri",
        eventHubSasKeyName = "eventHubSasKeyName",
        eventHubSasKey = "eventHubSasKey",
        eventHubCredentialType =
            CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication.Strategy
                .SHARED_ACCESS_POLICY)
  }

  private fun makeConnector(): Connector {
    return Connector(
        key = UUID.randomUUID().toString(),
        name = "Connector",
        repository = "/repository",
        version = "1.0",
        ioTypes = listOf(IoTypesEnum.read))
  }

  fun makeDataset(
      organizationId: String,
      connector: Connector,
      id: String,
      role: String,
      sourceType: DatasetSourceType = DatasetSourceType.Twincache
  ): Dataset {
    return Dataset(
        name = "Dataset",
        organizationId = organizationId,
        ownerId = "ownerId",
        ingestionStatus = IngestionStatusEnum.SUCCESS,
        sourceType = sourceType,
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

  fun makeSolution(organizationId: String, id: String, role: String): Solution {
    return Solution(
        id = "solutionId",
        key = UUID.randomUUID().toString(),
        name = "My solution",
        organizationId = organizationId,
        ownerId = "ownerId",
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
                        OrganizationAccessControl(id = id, role = role))))
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
                default = ROLE_NONE,
                mutableListOf(
                    WorkspaceAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                    WorkspaceAccessControl(id = id, role = role))))
  }

  fun makeScenarioWithRole(
      organizationId: String,
      workspaceId: String,
      solutionId: String,
      datasetList: MutableList<String>,
      id: String,
      role: String,
      validationStatus: ScenarioValidationStatus = ScenarioValidationStatus.Draft
  ): Scenario {
    return Scenario(
        id = UUID.randomUUID().toString(),
        name = "Scenario",
        organizationId = organizationId,
        workspaceId = workspaceId,
        solutionId = solutionId,
        ownerId = "ownerId",
        datasetList = datasetList,
        parentId = null,
        validationStatus = validationStatus,
        security =
            ScenarioSecurity(
                ROLE_NONE,
                mutableListOf(
                    ScenarioAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                    ScenarioAccessControl(id, role))))
  }
}
