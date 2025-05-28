// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.runner.service

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.containerregistry.ContainerRegistryService
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
import com.cosmotech.dataset.DatasetApiServiceInterface
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.dataset.domain.DatasetAccessControl
import com.cosmotech.dataset.domain.DatasetCreateRequest
import com.cosmotech.dataset.domain.DatasetSecurity
import com.cosmotech.dataset.repository.DatasetRepository
import com.cosmotech.organization.OrganizationApiServiceInterface
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationCreateRequest
import com.cosmotech.organization.domain.OrganizationSecurity
import com.cosmotech.runner.RunnerApiServiceInterface
import com.cosmotech.runner.domain.Runner
import com.cosmotech.runner.domain.RunnerAccessControl
import com.cosmotech.runner.domain.RunnerCreateRequest
import com.cosmotech.runner.domain.RunnerRole
import com.cosmotech.runner.domain.RunnerSecurity
import com.cosmotech.runner.domain.RunnerUpdateRequest
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
import com.redis.testcontainers.RedisStackContainer
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.mockkStatic
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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

@ActiveProfiles(profiles = ["runner-test"])
@ExtendWith(MockKExtension::class)
@ExtendWith(SpringExtension::class)
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Suppress("FunctionName")
class RunnerServiceRBACTest : CsmRedisTestBase() {

  val CONNECTED_ADMIN_USER = "test.admin@cosmotech.com"
  val TEST_USER_MAIL = "testuser@mail.fr"

  @Autowired lateinit var datasetRepository: DatasetRepository
  @Autowired lateinit var rediSearchIndexer: RediSearchIndexer
  @Autowired lateinit var organizationApiService: OrganizationApiServiceInterface
  @SpykBean lateinit var datasetApiService: DatasetApiServiceInterface
  @Autowired lateinit var solutionApiService: SolutionApiServiceInterface
  @Autowired lateinit var workspaceApiService: WorkspaceApiServiceInterface
  @Autowired lateinit var runnerApiService: RunnerApiServiceInterface
  @Autowired lateinit var csmPlatformProperties: CsmPlatformProperties

  private var containerRegistryService: ContainerRegistryService = mockk(relaxed = true)

  lateinit var jedis: UnifiedJedis

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
    jedis = UnifiedJedis(HostAndPort(containerIp, Protocol.DEFAULT_PORT))
    ReflectionTestUtils.setField(datasetApiService, "unifiedJedis", jedis)
    ReflectionTestUtils.setField(
        solutionApiService, "containerRegistryService", containerRegistryService)
    every { containerRegistryService.getImageLabel(any(), any(), any()) } returns null
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
              val organization = makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = role)
              val organizationSaved = organizationApiService.createOrganization(organization)

              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              var datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)

              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.listRunners(
                          organizationSaved.id, workspaceSaved.id, null, null)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.listRunners(organizationSaved.id, workspaceSaved.id, null, null)
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
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(solutionSaved.id, userId = TEST_USER_MAIL, role = role)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)
              val dataset = makeDataset(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.listRunners(
                          organizationSaved.id, workspaceSaved.id, null, null)
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.listRunners(organizationSaved.id, workspaceSaved.id, null, null)
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
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = role)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)
              val dataset = makeDataset(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.listRunners(
                          organizationSaved.id, workspaceSaved.id, null, null)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.listRunners(organizationSaved.id, workspaceSaved.id, null, null)
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
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.listRunners(
                          organizationSaved.id, workspaceSaved.id, null, null)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.listRunners(organizationSaved.id, workspaceSaved.id, null, null)
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
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
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
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
                    }
                if (role == ROLE_NONE || role == ROLE_VALIDATOR) {
                  assertEquals(
                      "RBAC ${datasetSaved.id} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${datasetSaved.id} - User does not have permission $PERMISSION_READ",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
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
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
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
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
                    }
                if (role == ROLE_VALIDATOR || role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${workspaceSaved.id} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${workspaceSaved.id} - User does not have permission $PERMISSION_CREATE_CHILDREN",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
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
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.getRunner(
                          organizationSaved.id, workspaceSaved.id, runnerSaved.id)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.getRunner(
                      organizationSaved.id, workspaceSaved.id, runnerSaved.id)
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
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.getRunner(
                          organizationSaved.id, workspaceSaved.id, runnerSaved.id)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.getRunner(
                      organizationSaved.id, workspaceSaved.id, runnerSaved.id)
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
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.getRunner(
                          organizationSaved.id, workspaceSaved.id, runnerSaved.id)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.getRunner(
                      organizationSaved.id, workspaceSaved.id, runnerSaved.id)
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
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.getRunner(
                          organizationSaved.id, workspaceSaved.id, runnerSaved.id)
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.getRunner(
                      organizationSaved.id, workspaceSaved.id, runnerSaved.id)
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
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.getRunner(
                          organizationSaved.id, workspaceSaved.id, runnerSaved.id)
                    }
                assertEquals(
                    "RBAC ${runnerSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.getRunner(
                      organizationSaved.id, workspaceSaved.id, runnerSaved.id)
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
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.deleteRunner(
                          organizationSaved.id, workspaceSaved.id, runnerSaved.id)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.deleteRunner(
                      organizationSaved.id, workspaceSaved.id, runnerSaved.id)
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
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.deleteRunner(
                          organizationSaved.id, workspaceSaved.id, runnerSaved.id)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.deleteRunner(
                      organizationSaved.id, workspaceSaved.id, runnerSaved.id)
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
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.deleteRunner(
                          organizationSaved.id, workspaceSaved.id, runnerSaved.id)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.deleteRunner(
                      organizationSaved.id, workspaceSaved.id, runnerSaved.id)
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
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.deleteRunner(
                          organizationSaved.id, workspaceSaved.id, runnerSaved.id)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.deleteRunner(
                      organizationSaved.id, workspaceSaved.id, runnerSaved.id)
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
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.deleteRunner(
                          organizationSaved.id, workspaceSaved.id, runnerSaved.id)
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${runnerSaved.id} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${runnerSaved.id} - User does not have permission $PERMISSION_DELETE",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  runnerApiService.deleteRunner(
                      organizationSaved.id, workspaceSaved.id, runnerSaved.id)
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
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.updateRunner(
                          organizationSaved.id,
                          workspaceSaved.id,
                          runnerSaved.id,
                          RunnerUpdateRequest(datasetList = mutableListOf(datasetSaved.id)))
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.updateRunner(
                      organizationSaved.id,
                      workspaceSaved.id,
                      runnerSaved.id,
                      RunnerUpdateRequest(datasetList = mutableListOf(datasetSaved.id)))
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
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.updateRunner(
                          organizationSaved.id,
                          workspaceSaved.id,
                          runnerSaved.id,
                          RunnerUpdateRequest(datasetList = mutableListOf(datasetSaved.id)))
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.updateRunner(
                      organizationSaved.id,
                      workspaceSaved.id,
                      runnerSaved.id,
                      RunnerUpdateRequest(datasetList = mutableListOf(datasetSaved.id)))
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
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.updateRunner(
                          organizationSaved.id,
                          workspaceSaved.id,
                          runnerSaved.id,
                          RunnerUpdateRequest(datasetList = mutableListOf(datasetSaved.id)))
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.updateRunner(
                      organizationSaved.id,
                      workspaceSaved.id,
                      runnerSaved.id,
                      RunnerUpdateRequest(datasetList = mutableListOf(datasetSaved.id)))
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
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.updateRunner(
                          organizationSaved.id,
                          workspaceSaved.id,
                          runnerSaved.id,
                          RunnerUpdateRequest(datasetList = mutableListOf(datasetSaved.id)))
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.updateRunner(
                      organizationSaved.id,
                      workspaceSaved.id,
                      runnerSaved.id,
                      RunnerUpdateRequest(datasetList = mutableListOf(datasetSaved.id)))
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
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.updateRunner(
                          organizationSaved.id,
                          workspaceSaved.id,
                          runnerSaved.id,
                          RunnerUpdateRequest(datasetList = mutableListOf(datasetSaved.id)))
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${runnerSaved.id} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${runnerSaved.id} - User does not have permission $PERMISSION_WRITE",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  runnerApiService.updateRunner(
                      organizationSaved.id,
                      workspaceSaved.id,
                      runnerSaved.id,
                      RunnerUpdateRequest(datasetList = mutableListOf(datasetSaved.id)))
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
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.listRunnerPermissions(
                          organizationSaved.id, workspaceSaved.id, runnerSaved.id, role)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.listRunnerPermissions(
                      organizationSaved.id, workspaceSaved.id, runnerSaved.id, role)
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC listRunnerPermissions`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Dataset RBAC listRunnerPermissions : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.listRunnerPermissions(
                          organizationSaved.id, workspaceSaved.id, runnerSaved.id, role)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.listRunnerPermissions(
                      organizationSaved.id, workspaceSaved.id, runnerSaved.id, role)
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC listRunnerPermissions`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Solution RBAC listRunnerPermissions : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.listRunnerPermissions(
                          organizationSaved.id, workspaceSaved.id, runnerSaved.id, role)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.listRunnerPermissions(
                      organizationSaved.id, workspaceSaved.id, runnerSaved.id, role)
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC listRunnerPermissions`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Workspace RBAC listRunnerPermissions : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.listRunnerPermissions(
                          organizationSaved.id, workspaceSaved.id, runnerSaved.id, role)
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.listRunnerPermissions(
                      organizationSaved.id, workspaceSaved.id, runnerSaved.id, role)
                }
              }
            }
          }

  @TestFactory
  fun `test Runner RBAC listRunnerPermissions`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Runner RBAC listRunnerPermissions : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.listRunnerPermissions(
                          organizationSaved.id, workspaceSaved.id, runnerSaved.id, role)
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${runnerSaved.id} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${runnerSaved.id} - User does not have permission $PERMISSION_READ_SECURITY",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  runnerApiService.listRunnerPermissions(
                      organizationSaved.id, workspaceSaved.id, runnerSaved.id, role)
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
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.getRunnerSecurity(
                          organizationSaved.id, workspaceSaved.id, runnerSaved.id)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.getRunnerSecurity(
                      organizationSaved.id, workspaceSaved.id, runnerSaved.id)
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
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.getRunnerSecurity(
                          organizationSaved.id, workspaceSaved.id, runnerSaved.id)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.getRunnerSecurity(
                      organizationSaved.id, workspaceSaved.id, runnerSaved.id)
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
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.getRunnerSecurity(
                          organizationSaved.id, workspaceSaved.id, runnerSaved.id)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.getRunnerSecurity(
                      organizationSaved.id, workspaceSaved.id, runnerSaved.id)
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
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.getRunnerSecurity(
                          organizationSaved.id, workspaceSaved.id, runnerSaved.id)
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.getRunnerSecurity(
                      organizationSaved.id, workspaceSaved.id, runnerSaved.id)
                }
              }
            }
          }

  @TestFactory
  fun `test Runner RBAC getRunnerSecurity`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Runner RBAC getRunnerSecurity : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.getRunnerSecurity(
                          organizationSaved.id, workspaceSaved.id, runnerSaved.id)
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${runnerSaved.id} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${runnerSaved.id} - User does not have permission $PERMISSION_READ_SECURITY",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  runnerApiService.getRunnerSecurity(
                      organizationSaved.id, workspaceSaved.id, runnerSaved.id)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC updateRunnerDefaultSecurity`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Organization RBAC updateRunnerDefaultSecurity : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.updateRunnerDefaultSecurity(
                          organizationSaved.id,
                          workspaceSaved.id,
                          runnerSaved.id,
                          RunnerRole(ROLE_ADMIN))
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.updateRunnerDefaultSecurity(
                      organizationSaved.id,
                      workspaceSaved.id,
                      runnerSaved.id,
                      RunnerRole(ROLE_ADMIN))
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC updateRunnerDefaultSecurity`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Dataset RBAC updateRunnerDefaultSecurity : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.updateRunnerDefaultSecurity(
                          organizationSaved.id,
                          workspaceSaved.id,
                          runnerSaved.id,
                          RunnerRole(ROLE_ADMIN))
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.updateRunnerDefaultSecurity(
                      organizationSaved.id,
                      workspaceSaved.id,
                      runnerSaved.id,
                      RunnerRole(ROLE_ADMIN))
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC updateRunnerDefaultSecurity`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Solution RBAC updateRunnerDefaultSecurity : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.updateRunnerDefaultSecurity(
                          organizationSaved.id,
                          workspaceSaved.id,
                          runnerSaved.id,
                          RunnerRole(ROLE_ADMIN))
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.updateRunnerDefaultSecurity(
                      organizationSaved.id,
                      workspaceSaved.id,
                      runnerSaved.id,
                      RunnerRole(ROLE_ADMIN))
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC updateRunnerDefaultSecurity`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Workspace RBAC updateRunnerDefaultSecurity : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.updateRunnerDefaultSecurity(
                          organizationSaved.id,
                          workspaceSaved.id,
                          runnerSaved.id,
                          RunnerRole(ROLE_ADMIN))
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.updateRunnerDefaultSecurity(
                      organizationSaved.id,
                      workspaceSaved.id,
                      runnerSaved.id,
                      RunnerRole(ROLE_ADMIN))
                }
              }
            }
          }

  @TestFactory
  fun `test Runner RBAC updateRunnerDefaultSecurity`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_VALIDATOR to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Runner RBAC updateRunnerDefaultSecurity : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.updateRunnerDefaultSecurity(
                          organizationSaved.id,
                          workspaceSaved.id,
                          runnerSaved.id,
                          RunnerRole(ROLE_ADMIN))
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${runnerSaved.id} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${runnerSaved.id} - User does not have permission $PERMISSION_WRITE_SECURITY",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  runnerApiService.updateRunnerDefaultSecurity(
                      organizationSaved.id,
                      workspaceSaved.id,
                      runnerSaved.id,
                      RunnerRole(ROLE_ADMIN))
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC createRunnerAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Organization RBAC createRunnerAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.createRunnerAccessControl(
                          organizationSaved.id,
                          workspaceSaved.id,
                          runnerSaved.id,
                          RunnerAccessControl("id", ROLE_ADMIN))
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.createRunnerAccessControl(
                      organizationSaved.id,
                      workspaceSaved.id,
                      runnerSaved.id,
                      RunnerAccessControl("id", ROLE_ADMIN))
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC modification when createRunnerAccessControl is called on a runner`() =
      listOf(
              ROLE_VIEWER,
              ROLE_EDITOR,
              ROLE_VALIDATOR,
              ROLE_ADMIN,
          )
          .map { role ->
            dynamicTest(
                "Check Dataset RBAC modification " +
                    "when createRunnerAccessControl is called on a runner with role : $role") {
                  every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
                  val organization =
                      makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
                  val organizationSaved = organizationApiService.createOrganization(organization)
                  val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
                  val solutionSaved =
                      solutionApiService.createSolution(organizationSaved.id, solution)
                  val workspace =
                      makeWorkspaceCreateRequest(
                          solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
                  val workspaceSaved =
                      workspaceApiService.createWorkspace(organizationSaved.id, workspace)

                  val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
                  val datasetSaved =
                      datasetApiService.createDataset(
                          organizationSaved.id, workspaceSaved.id, dataset, null)
                  val runner =
                      makeRunnerWithRole(
                          solutionId = solutionSaved.id,
                          datasetList = mutableListOf(datasetSaved.id),
                          userId = TEST_USER_MAIL,
                          role = ROLE_ADMIN)
                  val runnerSaved =
                      runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
                  assertDoesNotThrow {
                    assertTrue(
                        datasetSaved.security.accessControlList
                            .filter { datasetAccessControl ->
                              datasetAccessControl.id == "unknown_user@test.com"
                            }
                            .isEmpty())

                    runnerApiService.createRunnerAccessControl(
                        organizationSaved.id,
                        workspaceSaved.id,
                        runnerSaved.id,
                        RunnerAccessControl("unknown_user@test.com", role))

                    val datasetWithUpgradedACL =
                        datasetApiService.getDataset(
                            organizationSaved.id, workspaceSaved.id, datasetSaved.id)
                    var datasetRole = role
                    if (role == ROLE_VALIDATOR) {
                      datasetRole = ROLE_USER
                    }
                    assertEquals(
                        true,
                        datasetWithUpgradedACL.security.accessControlList
                            .filter { datasetAccessControl ->
                              datasetAccessControl.id == "unknown_user@test.com"
                            }
                            .any { datasetAccessControl ->
                              datasetAccessControl.role == datasetRole
                            })
                  }
                }
          }

  @Test
  fun `test createRunnerAccessControl when called on a runner with an user that do not exist in Dataset RBAC`() {
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
    val organization = makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
    val organizationSaved = organizationApiService.createOrganization(organization)
    val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
    val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
    val workspace =
        makeWorkspaceCreateRequest(solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
    val workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id, workspace)
    val dataset = makeDataset(userId = "unknown_user@test.com", role = ROLE_NONE)
    var datasetSaved =
        datasetApiService.createDataset(organizationSaved.id, workspaceSaved.id, dataset, null)
    val runner =
        makeRunnerWithRole(
            solutionSaved.id,
            mutableListOf(datasetSaved.id),
            userId = "unknown_user@test.com",
            role = ROLE_ADMIN)

    assertEquals(
        false,
        datasetSaved.security.accessControlList.any { datasetAccessControl ->
          datasetAccessControl.id == TEST_USER_MAIL
        })

    val runnerSaved = runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)

    runnerApiService.createRunnerAccessControl(
        organizationSaved.id,
        workspaceSaved.id,
        runnerSaved.id,
        RunnerAccessControl(TEST_USER_MAIL, ROLE_ADMIN))
    val datasetWithUpgradedACL =
        datasetApiService.getDataset(organizationSaved.id, workspaceSaved.id, datasetSaved.id)
    assertEquals(
        true,
        datasetWithUpgradedACL.security.accessControlList.any { datasetAccessControl ->
          datasetAccessControl.id == TEST_USER_MAIL
        })
  }

  @TestFactory
  fun `test Solution RBAC createRunnerAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Solution RBAC createRunnerAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.createRunnerAccessControl(
                          organizationSaved.id,
                          workspaceSaved.id,
                          runnerSaved.id,
                          RunnerAccessControl("id", ROLE_ADMIN))
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.createRunnerAccessControl(
                      organizationSaved.id,
                      workspaceSaved.id,
                      runnerSaved.id,
                      RunnerAccessControl("id", ROLE_ADMIN))
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC createRunnerAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Workspace RBAC createRunnerAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.createRunnerAccessControl(
                          organizationSaved.id,
                          workspaceSaved.id,
                          runnerSaved.id,
                          RunnerAccessControl("id", ROLE_ADMIN))
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.createRunnerAccessControl(
                      organizationSaved.id,
                      workspaceSaved.id,
                      runnerSaved.id,
                      RunnerAccessControl("id", ROLE_ADMIN))
                }
              }
            }
          }

  @TestFactory
  fun `test Runner RBAC createRunnerAccessControl`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_VALIDATOR to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Runner RBAC createRunnerAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.createRunnerAccessControl(
                          organizationSaved.id,
                          workspaceSaved.id,
                          runnerSaved.id,
                          RunnerAccessControl("id", ROLE_ADMIN))
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${runnerSaved.id} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${runnerSaved.id} - User does not have permission $PERMISSION_WRITE_SECURITY",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  runnerApiService.createRunnerAccessControl(
                      organizationSaved.id,
                      workspaceSaved.id,
                      runnerSaved.id,
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
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.getRunnerAccessControl(
                          organizationSaved.id, workspaceSaved.id, runnerSaved.id, TEST_USER_MAIL)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.getRunnerAccessControl(
                      organizationSaved.id, workspaceSaved.id, runnerSaved.id, TEST_USER_MAIL)
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
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.getRunnerAccessControl(
                          organizationSaved.id, workspaceSaved.id, runnerSaved.id, TEST_USER_MAIL)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.getRunnerAccessControl(
                      organizationSaved.id, workspaceSaved.id, runnerSaved.id, TEST_USER_MAIL)
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
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.getRunnerAccessControl(
                          organizationSaved.id, workspaceSaved.id, runnerSaved.id, TEST_USER_MAIL)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.getRunnerAccessControl(
                      organizationSaved.id, workspaceSaved.id, runnerSaved.id, TEST_USER_MAIL)
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
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.getRunnerAccessControl(
                          organizationSaved.id, workspaceSaved.id, runnerSaved.id, TEST_USER_MAIL)
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.getRunnerAccessControl(
                      organizationSaved.id, workspaceSaved.id, runnerSaved.id, TEST_USER_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test Runner RBAC getRunnerAccessControl`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Runner RBAC getRunnerAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.getRunnerAccessControl(
                          organizationSaved.id, workspaceSaved.id, runnerSaved.id, TEST_USER_MAIL)
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${runnerSaved.id} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${runnerSaved.id} - User does not have permission $PERMISSION_READ_SECURITY",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  runnerApiService.getRunnerAccessControl(
                      organizationSaved.id, workspaceSaved.id, runnerSaved.id, TEST_USER_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC deleteRunnerAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Organization RBAC deleteRunnerAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.deleteRunnerAccessControl(
                          organizationSaved.id, workspaceSaved.id, runnerSaved.id, TEST_USER_MAIL)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.deleteRunnerAccessControl(
                      organizationSaved.id, workspaceSaved.id, runnerSaved.id, TEST_USER_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC deleteRunnerAccessControl`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Dataset RBAC deleteRunnerAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.deleteRunnerAccessControl(
                          organizationSaved.id, workspaceSaved.id, runnerSaved.id, TEST_USER_MAIL)
                    }
                if (role == ROLE_NONE || role == ROLE_VALIDATOR) {
                  assertEquals(
                      "RBAC ${datasetSaved.id} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${datasetSaved.id} - User does not have permission $PERMISSION_WRITE_SECURITY",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  runnerApiService.deleteRunnerAccessControl(
                      organizationSaved.id, workspaceSaved.id, runnerSaved.id, TEST_USER_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC deleteRunnerAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Solution RBAC deleteRunnerAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.deleteRunnerAccessControl(
                          organizationSaved.id, workspaceSaved.id, runnerSaved.id, TEST_USER_MAIL)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.deleteRunnerAccessControl(
                      organizationSaved.id, workspaceSaved.id, runnerSaved.id, TEST_USER_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC deleteRunnerAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Workspace RBAC deleteRunnerAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.deleteRunnerAccessControl(
                          organizationSaved.id, workspaceSaved.id, runnerSaved.id, TEST_USER_MAIL)
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.deleteRunnerAccessControl(
                      organizationSaved.id, workspaceSaved.id, runnerSaved.id, TEST_USER_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test Runner RBAC deleteRunnerAccessControl`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_VALIDATOR to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Runner RBAC deleteRunnerAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.deleteRunnerAccessControl(
                          organizationSaved.id, workspaceSaved.id, runnerSaved.id, TEST_USER_MAIL)
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${runnerSaved.id} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${runnerSaved.id} - User does not have permission $PERMISSION_WRITE_SECURITY",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  runnerApiService.deleteRunnerAccessControl(
                      organizationSaved.id, workspaceSaved.id, runnerSaved.id, TEST_USER_MAIL)
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
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.updateRunnerAccessControl(
                          organizationSaved.id,
                          workspaceSaved.id,
                          runnerSaved.id,
                          TEST_USER_MAIL,
                          RunnerRole(ROLE_VIEWER))
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.updateRunnerAccessControl(
                      organizationSaved.id,
                      workspaceSaved.id,
                      runnerSaved.id,
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
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.updateRunnerAccessControl(
                          organizationSaved.id,
                          workspaceSaved.id,
                          runnerSaved.id,
                          TEST_USER_MAIL,
                          RunnerRole(ROLE_VIEWER))
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.updateRunnerAccessControl(
                      organizationSaved.id,
                      workspaceSaved.id,
                      runnerSaved.id,
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
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.updateRunnerAccessControl(
                          organizationSaved.id,
                          workspaceSaved.id,
                          runnerSaved.id,
                          TEST_USER_MAIL,
                          RunnerRole(ROLE_VIEWER))
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.updateRunnerAccessControl(
                      organizationSaved.id,
                      workspaceSaved.id,
                      runnerSaved.id,
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
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.updateRunnerAccessControl(
                          organizationSaved.id,
                          workspaceSaved.id,
                          runnerSaved.id,
                          TEST_USER_MAIL,
                          RunnerRole(ROLE_VIEWER))
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.updateRunnerAccessControl(
                      organizationSaved.id,
                      workspaceSaved.id,
                      runnerSaved.id,
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
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.updateRunnerAccessControl(
                          organizationSaved.id,
                          workspaceSaved.id,
                          runnerSaved.id,
                          TEST_USER_MAIL,
                          RunnerRole(ROLE_VIEWER))
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${runnerSaved.id} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${runnerSaved.id} - User does not have permission $PERMISSION_WRITE_SECURITY",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  runnerApiService.updateRunnerAccessControl(
                      organizationSaved.id,
                      workspaceSaved.id,
                      runnerSaved.id,
                      TEST_USER_MAIL,
                      RunnerRole(ROLE_VIEWER))
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC listRunnerSecurityUsers`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Organization RBAC listRunnerSecurityUsers : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.listRunnerSecurityUsers(
                          organizationSaved.id, workspaceSaved.id, runnerSaved.id)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.listRunnerSecurityUsers(
                      organizationSaved.id, workspaceSaved.id, runnerSaved.id)
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC listRunnerSecurityUsers`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Dataset RBAC listRunnerSecurityUsers : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.listRunnerSecurityUsers(
                          organizationSaved.id, workspaceSaved.id, runnerSaved.id)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.listRunnerSecurityUsers(
                      organizationSaved.id, workspaceSaved.id, runnerSaved.id)
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC listRunnerSecurityUsers`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Solution RBAC listRunnerSecurityUsers : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.listRunnerSecurityUsers(
                          organizationSaved.id, workspaceSaved.id, runnerSaved.id)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.listRunnerSecurityUsers(
                      organizationSaved.id, workspaceSaved.id, runnerSaved.id)
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC listRunnerSecurityUsers`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Workspace RBAC listRunnerSecurityUsers : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.listRunnerSecurityUsers(
                          organizationSaved.id, workspaceSaved.id, runnerSaved.id)
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  runnerApiService.listRunnerSecurityUsers(
                      organizationSaved.id, workspaceSaved.id, runnerSaved.id)
                }
              }
            }
          }

  @TestFactory
  fun `test Runner RBAC listRunnerSecurityUsers`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test Runner RBAC listRunnerSecurityUsers : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organization =
                  makeOrganizationCreateRequest(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolution(userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
              val workspace =
                  makeWorkspaceCreateRequest(
                      solutionId = solutionSaved.id, userId = TEST_USER_MAIL, role = ROLE_ADMIN)
              val workspaceSaved =
                  workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              val dataset = makeDataset(userId = TEST_USER_MAIL, role = role)
              val datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, null)
              val runner =
                  makeRunnerWithRole(
                      solutionId = solutionSaved.id,
                      datasetList = mutableListOf(datasetSaved.id),
                      userId = TEST_USER_MAIL,
                      role = ROLE_ADMIN)
              val runnerSaved =
                  runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      runnerApiService.listRunnerSecurityUsers(
                          organizationSaved.id, workspaceSaved.id, runnerSaved.id)
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${runnerSaved.id} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${runnerSaved.id} - User does not have permission $PERMISSION_READ_SECURITY",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  runnerApiService.listRunnerSecurityUsers(
                      organizationSaved.id, workspaceSaved.id, runnerSaved.id)
                }
              }
            }
          }

  fun makeDataset(name: String = "my_dataset_test", userId: String, role: String) =
      DatasetCreateRequest(
          name = name,
          security =
              DatasetSecurity(
                  default = ROLE_NONE,
                  accessControlList =
                      mutableListOf(
                          DatasetAccessControl(id = CONNECTED_ADMIN_USER, role = ROLE_ADMIN),
                          DatasetAccessControl(id = userId, role = role))))

  fun makeSolution(userId: String, role: String) =
      SolutionCreateRequest(
          key = UUID.randomUUID().toString(),
          name = "My solution",
          runTemplates = mutableListOf(RunTemplateCreateRequest("runTemplateId")),
          parameters = mutableListOf(RunTemplateParameterCreateRequest("parameter", "string")),
          repository = "repository",
          version = "1.0.0",
          parameterGroups = mutableListOf(RunTemplateParameterGroupCreateRequest("group")),
          security =
              SolutionSecurity(
                  default = ROLE_NONE,
                  mutableListOf(
                      SolutionAccessControl(id = CONNECTED_ADMIN_USER, role = ROLE_ADMIN),
                      SolutionAccessControl(id = userId, role = role))))

  fun makeOrganizationCreateRequest(userId: String, role: String) =
      OrganizationCreateRequest(
          name = "Organization Name",
          security =
              OrganizationSecurity(
                  default = ROLE_NONE,
                  accessControlList =
                      mutableListOf(
                          OrganizationAccessControl(id = CONNECTED_ADMIN_USER, role = ROLE_ADMIN),
                          OrganizationAccessControl(id = userId, role = role))))

  fun makeWorkspaceCreateRequest(solutionId: String, userId: String, role: String) =
      WorkspaceCreateRequest(
          key = UUID.randomUUID().toString(),
          name = "Workspace",
          solution =
              WorkspaceSolution(
                  solutionId = solutionId,
              ),
          security =
              WorkspaceSecurity(
                  default = ROLE_NONE,
                  mutableListOf(
                      WorkspaceAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                      WorkspaceAccessControl(id = userId, role = role))))

  fun makeRunnerWithRole(
      solutionId: String,
      datasetList: MutableList<String>,
      userId: String,
      role: String
  ) =
      RunnerCreateRequest(
          name = "Runner",
          solutionId = solutionId,
          runTemplateId = "runTemplateId",
          datasetList = datasetList,
          parentId = null,
          ownerName = "owner",
          security =
              RunnerSecurity(
                  ROLE_NONE,
                  mutableListOf(
                      RunnerAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                      RunnerAccessControl(userId, role))))
}
