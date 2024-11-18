// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.runner.service

import com.cosmotech.api.azure.adx.AzureDataExplorerClient
import com.cosmotech.api.config.CsmPlatformProperties
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
import com.cosmotech.dataset.domain.DatasetAccessControl
import com.cosmotech.dataset.domain.DatasetConnector
import com.cosmotech.dataset.domain.DatasetSecurity
import com.cosmotech.dataset.repository.DatasetRepository
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationSecurity
import com.cosmotech.runner.api.RunnerApiService
import com.cosmotech.runner.domain.Runner
import com.cosmotech.runner.domain.RunnerAccessControl
import com.cosmotech.runner.domain.RunnerRole
import com.cosmotech.runner.domain.RunnerSecurity
import com.cosmotech.runner.domain.RunnerValidationStatus
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
import com.ninjasquad.springmockk.SpykBean
import com.redis.om.spring.RediSearchIndexer
import com.redis.testcontainers.RedisStackContainer
import com.redislabs.redisgraph.impl.api.RedisGraph
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.mockkStatic
import java.util.*
import kotlin.test.Test
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
import redis.clients.jedis.JedisPool

@ActiveProfiles(profiles = ["runner-test"])
@ExtendWith(MockKExtension::class)
@ExtendWith(SpringExtension::class)
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Suppress("FunctionName")
class RunnerServiceRBACTest : CsmRedisTestBase() {

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
  @Autowired lateinit var runnerApiService: RunnerApiService
  @Autowired lateinit var csmPlatformProperties: CsmPlatformProperties

  lateinit var jedisPool: JedisPool
  lateinit var redisGraph: RedisGraph

  @BeforeEach
  fun setUp() {
    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "test.user"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf()

    rediSearchIndexer.createIndexFor(Organization::class.java)
    rediSearchIndexer.createIndexFor(Solution::class.java)
    rediSearchIndexer.createIndexFor(Dataset::class.java)
    rediSearchIndexer.createIndexFor(Workspace::class.java)
    rediSearchIndexer.createIndexFor(Runner::class.java)

    val context = getContext(redisStackServer)
    val containerIp =
        (context.server as RedisStackContainer).containerInfo.networkSettings.ipAddress
    jedisPool = JedisPool(containerIp, 6379)
    redisGraph = RedisGraph(jedisPool)
    ReflectionTestUtils.setField(datasetApiService, "csmJedisPool", jedisPool)
    ReflectionTestUtils.setField(datasetApiService, "csmRedisGraph", redisGraph)
  }

  @TestFactory
  fun `test Organization RBAC listRunners`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Organization RBAC.listRunners : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
              every { datasetApiService.createSubDataset(any(), any(), any()) } returns datasetSaved
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.listRunners(
                          organizationSaved.id!!, workspaceSaved.id!!, null, null)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.listRunners(
                      organizationSaved.id!!, workspaceSaved.id!!, null, null)
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC listRunners`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Workspace RBAC.listRunners : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
              every { datasetApiService.createSubDataset(any(), any(), any()) } returns datasetSaved
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!, solutionSaved.id!!, id = TEST_USER_MAIL, role = role)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.listRunners(
                          organizationSaved.id!!, workspaceSaved.id!!, null, null)
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.listRunners(
                      organizationSaved.id!!, workspaceSaved.id!!, null, null)
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC listRunners`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Solution RBAC.listRunners : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
              every { datasetApiService.createSubDataset(any(), any(), any()) } returns datasetSaved
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.listRunners(
                          organizationSaved.id!!, workspaceSaved.id!!, null, null)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.listRunners(
                      organizationSaved.id!!, workspaceSaved.id!!, null, null)
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC listRunners`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Dataset RBAC.listRunners : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
              every { datasetApiService.createSubDataset(any(), any(), any()) } returns datasetSaved
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.listRunners(
                          organizationSaved.id!!, workspaceSaved.id!!, null, null)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.listRunners(
                      organizationSaved.id!!, workspaceSaved.id!!, null, null)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC createRunner`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Organization RBAC createRunner : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.createRunner(
                          organizationSaved.id!!, workspaceSaved.id!!, runner)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC createRunner`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Dataset RBAC createRunner : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.createRunner(
                          organizationSaved.id!!, workspaceSaved.id!!, runner)
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
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC createRunner`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Solution RBAC createRunner : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.createRunner(
                          organizationSaved.id!!, workspaceSaved.id!!, runner)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC createRunner`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Workspace RBAC createRunner : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!, solutionSaved.id!!, id = TEST_USER_MAIL, role = role)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.createRunner(
                          organizationSaved.id!!, workspaceSaved.id!!, runner)
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
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC findRunner`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Organization RBAC.findRunner : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.getRunner(
                          organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.getRunner(
                      organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC findRunner`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Dataset RBAC.findRunner : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.getRunner(
                          organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.getRunner(
                      organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC findRunner`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Solution RBAC.findRunner : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.getRunner(
                          organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.getRunner(
                      organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC findRunner`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Workspace RBAC.findRunner : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!, solutionSaved.id!!, id = TEST_USER_MAIL, role = role)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.getRunner(
                          organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.getRunner(
                      organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Runner RBAC findRunner`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Runner RBAC.findRunner : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = role)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.getRunner(
                          organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${runnerSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.getRunner(
                      organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC deleteRunner`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Organization RBAC deleteRunner : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
              every { datasetApiService.createSubDataset(any(), any(), any()) } returns datasetSaved
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.deleteRunner(
                          organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.deleteRunner(
                      organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC deleteRunner`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Dataset RBAC deleteRunner : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
              every { datasetApiService.createSubDataset(any(), any(), any()) } returns datasetSaved
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.deleteRunner(
                          organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.deleteRunner(
                      organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC deleteRunner`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Solution RBAC deleteRunner : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
              every { datasetApiService.createSubDataset(any(), any(), any()) } returns datasetSaved
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.deleteRunner(
                          organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.deleteRunner(
                      organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC deleteRunner`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Workspace RBAC deleteRunner : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
              every { datasetApiService.createSubDataset(any(), any(), any()) } returns datasetSaved
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.deleteRunner(
                          organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.deleteRunner(
                      organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Runner RBAC deleteRunner`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_VALIDATOR to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false)
          .map { (role, shouldThrow) ->
            dynamicTest("Test Runner RBAC deleteRunner : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
              every { datasetApiService.createSubDataset(any(), any(), any()) } returns datasetSaved
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = role)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.deleteRunner(
                          organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${runnerSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${runnerSaved.id!!} - User does not have permission $PERMISSION_DELETE",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  runnerApiService.deleteRunner(
                      organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC updateRunner`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Organization RBAC updateRunner : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.updateRunner(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          runnerSaved.id!!,
                          makeRunnerWithRole(
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
                  runnerApiService.updateRunner(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      runnerSaved.id!!,
                      makeRunnerWithRole(
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
  fun `test Dataset RBAC updateRunner`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Dataset RBAC updateRunner : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.updateRunner(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          runnerSaved.id!!,
                          makeRunnerWithRole(
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
                  runnerApiService.updateRunner(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      runnerSaved.id!!,
                      makeRunnerWithRole(
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
  fun `test Solution RBAC updateRunner`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Solution RBAC updateRunner : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.updateRunner(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          runnerSaved.id!!,
                          makeRunnerWithRole(
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
                  runnerApiService.updateRunner(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      runnerSaved.id!!,
                      makeRunnerWithRole(
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
  fun `test Workspace RBAC updateRunner`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Workspace RBAC updateRunner : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!, solutionSaved.id!!, id = TEST_USER_MAIL, role = role)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.updateRunner(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          runnerSaved.id!!,
                          makeRunnerWithRole(
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
                  runnerApiService.updateRunner(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      runnerSaved.id!!,
                      makeRunnerWithRole(
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
  fun `test Runner RBAC updateRunner`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Runner RBAC updateRunner : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = role)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.updateRunner(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          runnerSaved.id!!,
                          makeRunnerWithRole(
                              organizationSaved.id!!,
                              workspaceSaved.id!!,
                              solutionSaved.id!!,
                              mutableListOf(datasetSaved.id!!),
                              id = TEST_USER_MAIL,
                              role = role))
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${runnerSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${runnerSaved.id!!} - User does not have permission $PERMISSION_WRITE",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  runnerApiService.updateRunner(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      runnerSaved.id!!,
                      makeRunnerWithRole(
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
  fun `test Organization RBAC getRunnerPermissions`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Organization RBAC getRunnerPermissions : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              every { datasetApiService.createSubDataset(any(), any(), any()) } returns datasetSaved
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.getRunnerPermissions(
                          organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, role)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.getRunnerPermissions(
                      organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, role)
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC getRunnerPermissions`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Dataset RBAC getRunnerPermissions : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              every { datasetApiService.createSubDataset(any(), any(), any()) } returns datasetSaved
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.getRunnerPermissions(
                          organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, role)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.getRunnerPermissions(
                      organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, role)
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC getRunnerPermissions`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Solution RBAC getRunnerPermissions : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              every { datasetApiService.createSubDataset(any(), any(), any()) } returns datasetSaved
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.getRunnerPermissions(
                          organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, role)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.getRunnerPermissions(
                      organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, role)
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC getRunnerPermissions`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Workspace RBAC getRunnerPermissions : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!, solutionSaved.id!!, id = TEST_USER_MAIL, role = role)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              every { datasetApiService.createSubDataset(any(), any(), any()) } returns datasetSaved
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.getRunnerPermissions(
                          organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, role)
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.getRunnerPermissions(
                      organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, role)
                }
              }
            }
          }

  @TestFactory
  fun `test Runner RBAC getRunnerPermissions`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Runner RBAC getRunnerPermissions : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = role)
              every { datasetApiService.createSubDataset(any(), any(), any()) } returns datasetSaved
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.getRunnerPermissions(
                          organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, role)
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${runnerSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${runnerSaved.id!!} - User does not have permission $PERMISSION_READ_SECURITY",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  runnerApiService.getRunnerPermissions(
                      organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, role)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC getRunnerSecurity`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Organization RBAC getRunnerSecurity : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.getRunnerSecurity(
                          organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.getRunnerSecurity(
                      organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC getRunnerSecurity`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Dataset RBAC getRunnerSecurity : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.getRunnerSecurity(
                          organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.getRunnerSecurity(
                      organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC getRunnerSecurity`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Solution RBAC getRunnerSecurity : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.getRunnerSecurity(
                          organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.getRunnerSecurity(
                      organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC getRunnerSecurity`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Workspace RBAC getRunnerSecurity : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!, solutionSaved.id!!, id = TEST_USER_MAIL, role = role)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.getRunnerSecurity(
                          organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.getRunnerSecurity(
                      organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Runner RBAC getRunnerSecurity`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Runner RBAC getRunnerSecurity : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = role)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.getRunnerSecurity(
                          organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${runnerSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${runnerSaved.id!!} - User does not have permission $PERMISSION_READ_SECURITY",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  runnerApiService.getRunnerSecurity(
                      organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC setRunnerDefaultSecurity`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Organization RBAC setRunnerDefaultSecurity : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.setRunnerDefaultSecurity(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          runnerSaved.id!!,
                          RunnerRole(ROLE_ADMIN))
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.setRunnerDefaultSecurity(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      runnerSaved.id!!,
                      RunnerRole(ROLE_ADMIN))
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC setRunnerDefaultSecurity`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Dataset RBAC setRunnerDefaultSecurity : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.setRunnerDefaultSecurity(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          runnerSaved.id!!,
                          RunnerRole(ROLE_ADMIN))
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.setRunnerDefaultSecurity(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      runnerSaved.id!!,
                      RunnerRole(ROLE_ADMIN))
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC setRunnerDefaultSecurity`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Solution RBAC setRunnerDefaultSecurity : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.setRunnerDefaultSecurity(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          runnerSaved.id!!,
                          RunnerRole(ROLE_ADMIN))
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.setRunnerDefaultSecurity(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      runnerSaved.id!!,
                      RunnerRole(ROLE_ADMIN))
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC setRunnerDefaultSecurity`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Workspace RBAC setRunnerDefaultSecurity : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!, solutionSaved.id!!, id = TEST_USER_MAIL, role = role)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.setRunnerDefaultSecurity(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          runnerSaved.id!!,
                          RunnerRole(ROLE_ADMIN))
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.setRunnerDefaultSecurity(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      runnerSaved.id!!,
                      RunnerRole(ROLE_ADMIN))
                }
              }
            }
          }

  @TestFactory
  fun `test Runner RBAC setRunnerDefaultSecurity`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_VALIDATOR to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Runner RBAC setRunnerDefaultSecurity : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = role)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.setRunnerDefaultSecurity(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          runnerSaved.id!!,
                          RunnerRole(ROLE_ADMIN))
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${runnerSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${runnerSaved.id!!} - User does not have permission $PERMISSION_WRITE_SECURITY",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  runnerApiService.setRunnerDefaultSecurity(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      runnerSaved.id!!,
                      RunnerRole(ROLE_ADMIN))
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC addRunnerAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Organization RBAC addRunnerAccessControl : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
              every { datasetApiService.createSubDataset(any(), any(), any()) } returns datasetSaved
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.addRunnerAccessControl(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          runnerSaved.id!!,
                          RunnerAccessControl("id", ROLE_ADMIN))
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.addRunnerAccessControl(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      runnerSaved.id!!,
                      RunnerAccessControl("id", ROLE_ADMIN))
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC addRunnerAccessControl`() =
      listOf(
              ROLE_VIEWER,
              ROLE_EDITOR,
              ROLE_VALIDATOR,
              ROLE_USER,
              ROLE_NONE,
              ROLE_ADMIN,
          )
          .map { role ->
            dynamicTest("Test Dataset RBAC addRunnerAccessControl : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
              every { datasetApiService.createSubDataset(any(), any(), any()) } returns datasetSaved
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)

              assertDoesNotThrow {
                assertEquals(
                    true,
                    datasetSaved.security
                        ?.accessControlList
                        ?.filter { datasetAccessControl ->
                          datasetAccessControl.id == TEST_USER_MAIL
                        }
                        ?.any { datasetAccessControl -> datasetAccessControl.role == role })

                runnerApiService.addRunnerAccessControl(
                    organizationSaved.id!!,
                    workspaceSaved.id!!,
                    runnerSaved.id!!,
                    RunnerAccessControl(TEST_USER_MAIL, ROLE_ADMIN))

                val datasetWithUpgradedACL =
                    datasetApiService.findDatasetById(organizationSaved.id!!, datasetSaved.id!!)
                assertEquals(
                    true,
                    datasetWithUpgradedACL.security
                        ?.accessControlList
                        ?.filter { datasetAccessControl ->
                          datasetAccessControl.id == TEST_USER_MAIL
                        }
                        ?.any { datasetAccessControl -> datasetAccessControl.role == role })
              }
            }
          }

  @Test
  fun `test Dataset RBAC addRunnerAccessControl with new ACL entry`() {
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
    val connector = makeConnector()
    val connectorSaved = connectorApiService.registerConnector(connector)
    val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
    val organizationSaved = organizationApiService.registerOrganization(organization)
    val dataset =
        makeDataset(
            organizationSaved.id!!, connectorSaved, id = "unknown_user@test.com", role = ROLE_NONE)
    var datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
    datasetSaved =
        datasetRepository.save(
            datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
    every { datasetApiService.createSubDataset(any(), any(), any()) } returns datasetSaved

    assertEquals(
        false,
        datasetSaved.security?.accessControlList?.any { datasetAccessControl ->
          datasetAccessControl.id == TEST_USER_MAIL
        })

    val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
    val solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)
    val workspace =
        makeWorkspaceWithRole(
            organizationSaved.id!!, solutionSaved.id!!, id = TEST_USER_MAIL, role = ROLE_ADMIN)
    val workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
    val runner =
        makeRunnerWithRole(
            organizationSaved.id!!,
            workspaceSaved.id!!,
            solutionSaved.id!!,
            mutableListOf(datasetSaved.id!!),
            id = "unknown_user@test.com",
            role = ROLE_ADMIN)
    val runnerSaved =
        runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)

    runnerApiService.addRunnerAccessControl(
        organizationSaved.id!!,
        workspaceSaved.id!!,
        runnerSaved.id!!,
        RunnerAccessControl(TEST_USER_MAIL, ROLE_ADMIN))
    val datasetWithUpgradedACL =
        datasetApiService.findDatasetById(organizationSaved.id!!, datasetSaved.id!!)
    assertEquals(
        true,
        datasetWithUpgradedACL.security?.accessControlList?.any { datasetAccessControl ->
          datasetAccessControl.id == TEST_USER_MAIL
        })
  }

  @TestFactory
  fun `test Solution RBAC addRunnerAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Solution RBAC addRunnerAccessControl : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
              every { datasetApiService.createSubDataset(any(), any(), any()) } returns datasetSaved
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.addRunnerAccessControl(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          runnerSaved.id!!,
                          RunnerAccessControl("id", ROLE_ADMIN))
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.addRunnerAccessControl(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      runnerSaved.id!!,
                      RunnerAccessControl("id", ROLE_ADMIN))
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC addRunnerAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Workspace RBAC addRunnerAccessControl : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
              materializeTwingraph(datasetSaved)
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!, solutionSaved.id!!, id = TEST_USER_MAIL, role = role)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.addRunnerAccessControl(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          runnerSaved.id!!,
                          RunnerAccessControl("id", ROLE_ADMIN))
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.addRunnerAccessControl(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      runnerSaved.id!!,
                      RunnerAccessControl("id", ROLE_ADMIN))
                }
              }
            }
          }

  @TestFactory
  fun `test Runner RBAC addRunnerAccessControl`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_VALIDATOR to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Runner RBAC addRunnerAccessControl : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
              every { datasetApiService.createSubDataset(any(), any(), any()) } returns datasetSaved
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = role)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.addRunnerAccessControl(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          runnerSaved.id!!,
                          RunnerAccessControl("id", ROLE_ADMIN))
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${runnerSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${runnerSaved.id!!} - User does not have permission $PERMISSION_WRITE_SECURITY",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  runnerApiService.addRunnerAccessControl(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      runnerSaved.id!!,
                      RunnerAccessControl("id", ROLE_ADMIN))
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC getRunnerAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Organization RBAC getRunnerAccessControl : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.getRunnerAccessControl(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          runnerSaved.id!!,
                          TEST_USER_MAIL)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.getRunnerAccessControl(
                      organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, TEST_USER_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC getRunnerAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Dataset RBAC getRunnerAccessControl : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.getRunnerAccessControl(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          runnerSaved.id!!,
                          TEST_USER_MAIL)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.getRunnerAccessControl(
                      organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, TEST_USER_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC getRunnerAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Solution RBAC getRunnerAccessControl : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.getRunnerAccessControl(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          runnerSaved.id!!,
                          TEST_USER_MAIL)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.getRunnerAccessControl(
                      organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, TEST_USER_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC getRunnerAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Workspace RBAC getRunnerAccessControl : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!, solutionSaved.id!!, id = TEST_USER_MAIL, role = role)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.getRunnerAccessControl(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          runnerSaved.id!!,
                          TEST_USER_MAIL)
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.getRunnerAccessControl(
                      organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, TEST_USER_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test Runner RBAC getRunnerAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Runner RBAC getRunnerAccessControl : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = role)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.getRunnerAccessControl(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          runnerSaved.id!!,
                          TEST_USER_MAIL)
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${runnerSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${runnerSaved.id!!} - User does not have permission $PERMISSION_READ_SECURITY",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  runnerApiService.getRunnerAccessControl(
                      organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, TEST_USER_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC removeRunnerAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Organization RBAC removeRunnerAccessControl : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
              every { datasetApiService.createSubDataset(any(), any(), any()) } returns datasetSaved
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.removeRunnerAccessControl(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          runnerSaved.id!!,
                          TEST_USER_MAIL)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.removeRunnerAccessControl(
                      organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, TEST_USER_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC removeRunnerAccessControl`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Dataset RBAC removeRunnerAccessControl : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
              every { datasetApiService.createSubDataset(any(), any(), any()) } returns datasetSaved
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.removeRunnerAccessControl(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          runnerSaved.id!!,
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
                  runnerApiService.removeRunnerAccessControl(
                      organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, TEST_USER_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC removeRunnerAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Solution RBAC removeRunnerAccessControl : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
              every { datasetApiService.createSubDataset(any(), any(), any()) } returns datasetSaved
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.removeRunnerAccessControl(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          runnerSaved.id!!,
                          TEST_USER_MAIL)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.removeRunnerAccessControl(
                      organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, TEST_USER_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC removeRunnerAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Workspace RBAC removeRunnerAccessControl : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
              every { datasetApiService.createSubDataset(any(), any(), any()) } returns datasetSaved
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!, solutionSaved.id!!, id = TEST_USER_MAIL, role = role)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.removeRunnerAccessControl(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          runnerSaved.id!!,
                          TEST_USER_MAIL)
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.removeRunnerAccessControl(
                      organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, TEST_USER_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test Runner RBAC removeRunnerAccessControl`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_VALIDATOR to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Runner RBAC removeRunnerAccessControl : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
              every { datasetApiService.createSubDataset(any(), any(), any()) } returns datasetSaved
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = role)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.removeRunnerAccessControl(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          runnerSaved.id!!,
                          TEST_USER_MAIL)
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${runnerSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${runnerSaved.id!!} - User does not have permission $PERMISSION_WRITE_SECURITY",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  runnerApiService.removeRunnerAccessControl(
                      organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, TEST_USER_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC updateRunnerAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Organization RBAC updateRunnerAccessControl : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
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
              every { datasetApiService.createSubDataset(any(), any(), any()) } returns datasetSaved
              every { datasetApiService.getDatasetSecurityUsers(any(), any()) } returns
                  listOf(TEST_USER_MAIL, CONNECTED_ADMIN_USER)
              every {
                datasetApiService.updateDatasetAccessControl(any(), any(), any(), any())
              } returns mockk()
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.updateRunnerAccessControl(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          runnerSaved.id!!,
                          TEST_USER_MAIL,
                          RunnerRole(ROLE_VIEWER))
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.updateRunnerAccessControl(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      runnerSaved.id!!,
                      TEST_USER_MAIL,
                      RunnerRole(ROLE_VIEWER))
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC updateRunnerAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Dataset RBAC updateRunnerAccessControl : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
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
              every { datasetApiService.createSubDataset(any(), any(), any()) } returns datasetSaved
              every { datasetApiService.getDatasetSecurityUsers(any(), any()) } returns
                  listOf(TEST_USER_MAIL, CONNECTED_ADMIN_USER)
              every {
                datasetApiService.updateDatasetAccessControl(any(), any(), any(), any())
              } returns mockk()
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.updateRunnerAccessControl(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          runnerSaved.id!!,
                          TEST_USER_MAIL,
                          RunnerRole(ROLE_VIEWER))
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.updateRunnerAccessControl(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      runnerSaved.id!!,
                      TEST_USER_MAIL,
                      RunnerRole(ROLE_VIEWER))
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC updateRunnerAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Solution RBAC updateRunnerAccessControl : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
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
              every { datasetApiService.createSubDataset(any(), any(), any()) } returns datasetSaved
              every { datasetApiService.getDatasetSecurityUsers(any(), any()) } returns
                  listOf(TEST_USER_MAIL, CONNECTED_ADMIN_USER)
              every {
                datasetApiService.updateDatasetAccessControl(any(), any(), any(), any())
              } returns mockk()
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.updateRunnerAccessControl(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          runnerSaved.id!!,
                          TEST_USER_MAIL,
                          RunnerRole(ROLE_VIEWER))
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.updateRunnerAccessControl(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      runnerSaved.id!!,
                      TEST_USER_MAIL,
                      RunnerRole(ROLE_VIEWER))
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC updateRunnerAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Workspace RBAC updateRunnerAccessControl : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!, solutionSaved.id!!, id = TEST_USER_MAIL, role = role)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
              every { datasetApiService.createSubDataset(any(), any(), any()) } returns datasetSaved
              every { datasetApiService.getDatasetSecurityUsers(any(), any()) } returns
                  listOf(TEST_USER_MAIL, CONNECTED_ADMIN_USER)
              every {
                datasetApiService.updateDatasetAccessControl(any(), any(), any(), any())
              } returns mockk()
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.updateRunnerAccessControl(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          runnerSaved.id!!,
                          TEST_USER_MAIL,
                          RunnerRole(ROLE_VIEWER))
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.updateRunnerAccessControl(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      runnerSaved.id!!,
                      TEST_USER_MAIL,
                      RunnerRole(ROLE_VIEWER))
                }
              }
            }
          }

  @TestFactory
  fun `test Runner RBAC updateRunnerAccessControl`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_VALIDATOR to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Runner RBAC updateRunnerAccessControl : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
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
              every { datasetApiService.createSubDataset(any(), any(), any()) } returns datasetSaved
              every { datasetApiService.getDatasetSecurityUsers(any(), any()) } returns
                  listOf(TEST_USER_MAIL, CONNECTED_ADMIN_USER)
              every {
                datasetApiService.updateDatasetAccessControl(any(), any(), any(), any())
              } returns mockk()
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = role)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.updateRunnerAccessControl(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          runnerSaved.id!!,
                          TEST_USER_MAIL,
                          RunnerRole(ROLE_VIEWER))
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${runnerSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${runnerSaved.id!!} - User does not have permission $PERMISSION_WRITE_SECURITY",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  runnerApiService.updateRunnerAccessControl(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      runnerSaved.id!!,
                      TEST_USER_MAIL,
                      RunnerRole(ROLE_VIEWER))
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC getRunnerSecurityUsers`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Organization RBAC getRunnerSecurityUsers : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              every { datasetApiService.createSubDataset(any(), any(), any()) } returns datasetSaved
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.getRunnerSecurityUsers(
                          organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.getRunnerSecurityUsers(
                      organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC getRunnerSecurityUsers`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Dataset RBAC getRunnerSecurityUsers : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              every { datasetApiService.createSubDataset(any(), any(), any()) } returns datasetSaved
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.getRunnerSecurityUsers(
                          organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.getRunnerSecurityUsers(
                      organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC getRunnerSecurityUsers`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Solution RBAC getRunnerSecurityUsers : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              every { datasetApiService.createSubDataset(any(), any(), any()) } returns datasetSaved
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.getRunnerSecurityUsers(
                          organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.getRunnerSecurityUsers(
                      organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC getRunnerSecurityUsers`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Workspace RBAC getRunnerSecurityUsers : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
              val solution = makeSolution(organizationSaved.id!!, TEST_USER_MAIL, ROLE_ADMIN)
              val solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
              val workspace =
                  makeWorkspaceWithRole(
                      organizationSaved.id!!, solutionSaved.id!!, id = TEST_USER_MAIL, role = role)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              every { datasetApiService.createSubDataset(any(), any(), any()) } returns datasetSaved
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.getRunnerSecurityUsers(
                          organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.getRunnerSecurityUsers(
                      organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Runner RBAC getRunnerSecurityUsers`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Runner RBAC getRunnerSecurityUsers : $role") {
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
                      datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
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
              val runner =
                  makeRunnerWithRole(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      solutionSaved.id!!,
                      mutableListOf(datasetSaved.id!!),
                      id = TEST_USER_MAIL,
                      role = role)
              every { datasetApiService.createSubDataset(any(), any(), any()) } returns datasetSaved
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.getRunnerSecurityUsers(
                          organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${runnerSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${runnerSaved.id!!} - User does not have permission $PERMISSION_READ_SECURITY",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  runnerApiService.getRunnerSecurityUsers(
                      organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
                }
              }
            }
          }

  private fun materializeTwingraph(dataset: Dataset, createTwingraph: Boolean = true): Dataset {
    dataset.apply {
      if (createTwingraph) {
        redisGraph.query(this.twingraphId, "CREATE (n:labelrouge)")
      }
      this.ingestionStatus = Dataset.IngestionStatus.SUCCESS
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
        ioTypes = listOf(Connector.IoTypes.read))
  }

  fun makeDataset(organizationId: String, connector: Connector, id: String, role: String): Dataset {
    return Dataset(
        name = "Dataset",
        organizationId = organizationId,
        ownerId = "ownerId",
        ingestionStatus = Dataset.IngestionStatus.SUCCESS,
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
        runTemplates = mutableListOf(RunTemplate("runTemplateId")),
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

  fun makeRunnerWithRole(
      organizationId: String,
      workspaceId: String,
      solutionId: String,
      datasetList: MutableList<String>,
      id: String,
      role: String,
      validationStatus: RunnerValidationStatus = RunnerValidationStatus.Draft
  ): Runner {
    return Runner(
        id = UUID.randomUUID().toString(),
        name = "Runner",
        organizationId = organizationId,
        workspaceId = workspaceId,
        solutionId = solutionId,
        runTemplateId = "runTemplateId",
        ownerId = "ownerId",
        datasetList = datasetList,
        parentId = null,
        validationStatus = validationStatus,
        security =
            RunnerSecurity(
                ROLE_NONE,
                mutableListOf(
                    RunnerAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                    RunnerAccessControl(id, role))))
  }
}
