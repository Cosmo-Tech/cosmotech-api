// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.workspace.service

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
import com.cosmotech.api.rbac.ROLE_VIEWER
import com.cosmotech.api.tests.CsmRedisTestBase
import com.cosmotech.api.utils.ResourceScanner
import com.cosmotech.api.utils.getCurrentAccountIdentifier
import com.cosmotech.api.utils.getCurrentAuthenticatedRoles
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.organization.OrganizationApiServiceInterface
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationSecurity
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.solution.domain.Solution
import com.cosmotech.solution.domain.SolutionAccessControl
import com.cosmotech.solution.domain.SolutionSecurity
import com.cosmotech.workspace.WorkspaceApiServiceInterface
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceAccessControl
import com.cosmotech.workspace.domain.WorkspaceRole
import com.cosmotech.workspace.domain.WorkspaceSecurity
import com.cosmotech.workspace.domain.WorkspaceSolution
import com.redis.om.spring.RediSearchIndexer
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockkStatic
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.test.assertEquals
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.Resource
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.util.ReflectionTestUtils

@ActiveProfiles(profiles = ["workspace-test"])
@ExtendWith(MockKExtension::class)
@ExtendWith(SpringExtension::class)
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Suppress("FunctionName")
class WorkspaceServiceRBACTest : CsmRedisTestBase() {

  val TEST_USER_MAIL = "testuser@mail.fr"
  val CONNECTED_ADMIN_USER = "test.admin@cosmotech.com"

  @RelaxedMockK private lateinit var resource: Resource

  @RelaxedMockK private lateinit var resourceScanner: ResourceScanner

  @Autowired lateinit var rediSearchIndexer: RediSearchIndexer
  @Autowired lateinit var organizationApiService: OrganizationApiServiceInterface
  @Autowired lateinit var solutionApiService: SolutionApiService
  @Autowired lateinit var workspaceApiService: WorkspaceApiServiceInterface
  @Autowired lateinit var csmPlatformProperties: CsmPlatformProperties

  @BeforeEach
  fun beforeEach() {
    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "test.user"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf()

    File(csmPlatformProperties.blobPersistence.path).deleteRecursively()

    rediSearchIndexer.createIndexFor(Organization::class.java)
    rediSearchIndexer.createIndexFor(Solution::class.java)
    rediSearchIndexer.createIndexFor(Workspace::class.java)
  }

  @AfterEach
  fun afterEach() {
    File(csmPlatformProperties.blobPersistence.path).deleteRecursively()
  }

  @TestFactory
  fun `test RBAC findAllWorkspaces`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC findAllWorkspaces : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organizationSaved =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole(id = TEST_USER_MAIL, role = role))
              val solutionSaved =
                  solutionApiService.createSolution(
                      organizationSaved.id!!, makeSolution(organizationSaved.id!!))
              workspaceApiService.createWorkspace(
                  organizationSaved.id!!,
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN))
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.findAllWorkspaces(organizationSaved.id!!, null, null)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  workspaceApiService.findAllWorkspaces(organizationSaved.id!!, null, null)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC createWorkspace`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC createWorkspace : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organizationSaved =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole(id = TEST_USER_MAIL, role = role))
              val solutionSaved =
                  solutionApiService.createSolution(
                      organizationSaved.id!!, makeSolution(organizationSaved.id!!))
              workspaceApiService.createWorkspace(
                  organizationSaved.id!!,
                  makeWorkspaceWithRole(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      id = TEST_USER_MAIL,
                      role = ROLE_ADMIN))
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.createWorkspace(
                          organizationSaved.id!!,
                          makeWorkspaceWithRole(
                              organizationSaved.id!!,
                              solutionSaved.id!!,
                              id = TEST_USER_MAIL,
                              role = ROLE_ADMIN))
                    }
                if (role == ROLE_NONE) {
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
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      makeWorkspaceWithRole(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          id = TEST_USER_MAIL,
                          role = ROLE_ADMIN))
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC findWorkspaceById`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC findWorkspaceById : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organizationSaved =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole(id = TEST_USER_MAIL, role = role))
              val solutionSaved =
                  solutionApiService.createSolution(
                      organizationSaved.id!!, makeSolution(organizationSaved.id!!))
              val workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      makeWorkspaceWithRole(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          id = TEST_USER_MAIL,
                          role = ROLE_ADMIN))
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.findWorkspaceById(
                          organizationSaved.id!!, workspaceSaved.id!!)
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  workspaceApiService.findWorkspaceById(organizationSaved.id!!, workspaceSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC findWorkspaceById`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Workspace RBAC findWorkspaceById : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organizationSaved =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN))
              val solutionSaved =
                  solutionApiService.createSolution(
                      organizationSaved.id!!, makeSolution(organizationSaved.id!!))
              val workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      makeWorkspaceWithRole(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          id = TEST_USER_MAIL,
                          role = role))
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.findWorkspaceById(
                          organizationSaved.id!!, workspaceSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  workspaceApiService.findWorkspaceById(organizationSaved.id!!, workspaceSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC deleteWorkspace`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC deleteWorkspace : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organizationSaved =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole(id = TEST_USER_MAIL, role = role))
              val solutionSaved =
                  solutionApiService.createSolution(
                      organizationSaved.id!!, makeSolution(organizationSaved.id!!))
              val workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      makeWorkspaceWithRole(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          id = TEST_USER_MAIL,
                          role = ROLE_ADMIN))
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.deleteWorkspace(
                          organizationSaved.id!!, workspaceSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  workspaceApiService.deleteWorkspace(organizationSaved.id!!, workspaceSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC deleteWorkspace`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Workspace RBAC deleteWorkspace : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organizationSaved =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN))
              val solutionSaved =
                  solutionApiService.createSolution(
                      organizationSaved.id!!, makeSolution(organizationSaved.id!!))
              val workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      makeWorkspaceWithRole(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          id = TEST_USER_MAIL,
                          role = role))
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.deleteWorkspace(
                          organizationSaved.id!!, workspaceSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_DELETE",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  workspaceApiService.deleteWorkspace(organizationSaved.id!!, workspaceSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC updateWorkspace`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC updateWorkspace : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organizationSaved =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole(id = TEST_USER_MAIL, role = role))
              val solutionSaved =
                  solutionApiService.createSolution(
                      organizationSaved.id!!, makeSolution(organizationSaved.id!!))
              val workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      makeWorkspaceWithRole(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          id = TEST_USER_MAIL,
                          role = ROLE_ADMIN))
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.updateWorkspace(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          makeWorkspaceWithRole(
                              organizationSaved.id!!,
                              solutionSaved.id!!,
                              id = TEST_USER_MAIL,
                              role = role))
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  workspaceApiService.updateWorkspace(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      makeWorkspaceWithRole(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          id = TEST_USER_MAIL,
                          role = role))
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC updateWorkspace`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Workspace RBAC updateWorkspace : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organizationSaved =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN))
              val solutionSaved =
                  solutionApiService.createSolution(
                      organizationSaved.id!!, makeSolution(organizationSaved.id!!))
              val workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      makeWorkspaceWithRole(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          id = TEST_USER_MAIL,
                          role = role))
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.updateWorkspace(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          makeWorkspaceWithRole(
                              organizationSaved.id!!,
                              solutionSaved.id!!,
                              id = TEST_USER_MAIL,
                              role = role))
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_WRITE",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  workspaceApiService.updateWorkspace(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      makeWorkspaceWithRole(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          id = TEST_USER_MAIL,
                          role = role))
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC findAllWorkspaceFiles`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC findAllWorkspaceFiles : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organizationSaved =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole(id = TEST_USER_MAIL, role = role))
              val solutionSaved =
                  solutionApiService.createSolution(
                      organizationSaved.id!!, makeSolution(organizationSaved.id!!))
              val workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      makeWorkspaceWithRole(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          id = TEST_USER_MAIL,
                          role = ROLE_ADMIN))
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.findAllWorkspaceFiles(
                          organizationSaved.id!!, workspaceSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  workspaceApiService.findAllWorkspaceFiles(
                      organizationSaved.id!!, workspaceSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC findAllWorkspaceFiles`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Workspace RBAC findAllWorkspaceFiles : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organizationSaved =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN))
              val solutionSaved =
                  solutionApiService.createSolution(
                      organizationSaved.id!!, makeSolution(organizationSaved.id!!))
              val workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      makeWorkspaceWithRole(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          id = TEST_USER_MAIL,
                          role = role))
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.findAllWorkspaceFiles(
                          organizationSaved.id!!, workspaceSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  workspaceApiService.findAllWorkspaceFiles(
                      organizationSaved.id!!, workspaceSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC uploadWorkspaceFile`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC uploadWorkspaceFile : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organizationSaved =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole(id = TEST_USER_MAIL, role = role))
              val solutionSaved =
                  solutionApiService.createSolution(
                      organizationSaved.id!!, makeSolution(organizationSaved.id!!))
              val workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      makeWorkspaceWithRole(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          id = TEST_USER_MAIL,
                          role = ROLE_ADMIN))
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL
              ReflectionTestUtils.setField(workspaceApiService, "resourceScanner", resourceScanner)
              every { resourceScanner.scanMimeTypes(any(), any()) } returns Unit

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.uploadWorkspaceFile(
                          organizationSaved.id!!, workspaceSaved.id!!, resource, true, "")
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  workspaceApiService.uploadWorkspaceFile(
                      organizationSaved.id!!, workspaceSaved.id!!, resource, true, "name")
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC uploadWorkspaceFile`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Workspace RBAC uploadWorkspaceFile : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organizationSaved =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN))
              val solutionSaved =
                  solutionApiService.createSolution(
                      organizationSaved.id!!, makeSolution(organizationSaved.id!!))
              val workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      makeWorkspaceWithRole(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          id = TEST_USER_MAIL,
                          role = role))
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL
              ReflectionTestUtils.setField(workspaceApiService, "resourceScanner", resourceScanner)
              every { resourceScanner.scanMimeTypes(any(), any()) } returns Unit

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.uploadWorkspaceFile(
                          organizationSaved.id!!, workspaceSaved.id!!, resource, true, "")
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_WRITE",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  workspaceApiService.uploadWorkspaceFile(
                      organizationSaved.id!!, workspaceSaved.id!!, resource, true, "name")
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC deleteAllWorkspaceFiles`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC deleteAllWorkspaceFiles : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organizationSaved =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole(id = TEST_USER_MAIL, role = role))
              val solutionSaved =
                  solutionApiService.createSolution(
                      organizationSaved.id!!, makeSolution(organizationSaved.id!!))
              val workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      makeWorkspaceWithRole(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          id = TEST_USER_MAIL,
                          role = ROLE_ADMIN))
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.deleteAllWorkspaceFiles(
                          organizationSaved.id!!, workspaceSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  workspaceApiService.deleteAllWorkspaceFiles(
                      organizationSaved.id!!, workspaceSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC deleteAllWorkspaceFiles`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Workspace RBAC deleteAllWorkspaceFiles : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organizationSaved =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN))
              val solutionSaved =
                  solutionApiService.createSolution(
                      organizationSaved.id!!, makeSolution(organizationSaved.id!!))
              val workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      makeWorkspaceWithRole(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          id = TEST_USER_MAIL,
                          role = role))
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.deleteAllWorkspaceFiles(
                          organizationSaved.id!!, workspaceSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_WRITE",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  workspaceApiService.deleteAllWorkspaceFiles(
                      organizationSaved.id!!, workspaceSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC downloadWorkspaceFile`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC downloadWorkspaceFile : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organizationSaved =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole(id = TEST_USER_MAIL, role = role))
              val solutionSaved =
                  solutionApiService.createSolution(
                      organizationSaved.id!!, makeSolution(organizationSaved.id!!))
              val workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      makeWorkspaceWithRole(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          id = TEST_USER_MAIL,
                          role = ROLE_ADMIN))
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.downloadWorkspaceFile(
                          organizationSaved.id!!, workspaceSaved.id!!, "")
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                val filePath =
                    Path.of(
                        csmPlatformProperties.blobPersistence.path,
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        "name")
                Files.createDirectories(filePath.getParent())
                Files.createFile(filePath)
                assertDoesNotThrow {
                  workspaceApiService.downloadWorkspaceFile(
                      organizationSaved.id!!, workspaceSaved.id!!, "name")
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC downloadWorkspaceFile`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Workspace RBAC downloadWorkspaceFile : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organizationSaved =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN))
              val solutionSaved =
                  solutionApiService.createSolution(
                      organizationSaved.id!!, makeSolution(organizationSaved.id!!))
              val workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      makeWorkspaceWithRole(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          id = TEST_USER_MAIL,
                          role = role))
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.downloadWorkspaceFile(
                          organizationSaved.id!!, workspaceSaved.id!!, "")
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                val filePath =
                    Path.of(
                        csmPlatformProperties.blobPersistence.path,
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        "name")
                Files.createDirectories(filePath.getParent())
                Files.createFile(filePath)
                assertDoesNotThrow {
                  workspaceApiService.downloadWorkspaceFile(
                      organizationSaved.id!!, workspaceSaved.id!!, "name")
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC deleteWorkspaceFile`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC deleteWorkspaceFile : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organizationSaved =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole(id = TEST_USER_MAIL, role = role))
              val solutionSaved =
                  solutionApiService.createSolution(
                      organizationSaved.id!!, makeSolution(organizationSaved.id!!))
              val workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      makeWorkspaceWithRole(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          id = TEST_USER_MAIL,
                          role = ROLE_ADMIN))
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.deleteWorkspaceFile(
                          organizationSaved.id!!, workspaceSaved.id!!, "")
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  workspaceApiService.deleteWorkspaceFile(
                      organizationSaved.id!!, workspaceSaved.id!!, "")
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC deleteWorkspaceFile`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Workspace RBAC deleteWorkspaceFile : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organizationSaved =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN))
              val solutionSaved =
                  solutionApiService.createSolution(
                      organizationSaved.id!!, makeSolution(organizationSaved.id!!))
              val workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      makeWorkspaceWithRole(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          id = TEST_USER_MAIL,
                          role = role))
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.deleteWorkspaceFile(
                          organizationSaved.id!!, workspaceSaved.id!!, "")
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_WRITE",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  workspaceApiService.deleteWorkspaceFile(
                      organizationSaved.id!!, workspaceSaved.id!!, "")
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC getWorkspacePermissions`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC getWorkspacePermissions : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organizationSaved =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole(id = TEST_USER_MAIL, role = role))
              val solutionSaved =
                  solutionApiService.createSolution(
                      organizationSaved.id!!, makeSolution(organizationSaved.id!!))
              val workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      makeWorkspaceWithRole(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          id = TEST_USER_MAIL,
                          role = ROLE_ADMIN))
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.getWorkspacePermissions(
                          organizationSaved.id!!, workspaceSaved.id!!, ROLE_USER)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  workspaceApiService.getWorkspacePermissions(
                      organizationSaved.id!!, workspaceSaved.id!!, ROLE_USER)
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC getWorkspacePermissions`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Workspace RBAC getWorkspacePermissions : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organizationSaved =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN))
              val solutionSaved =
                  solutionApiService.createSolution(
                      organizationSaved.id!!, makeSolution(organizationSaved.id!!))
              val workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      makeWorkspaceWithRole(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          id = TEST_USER_MAIL,
                          role = role))
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.getWorkspacePermissions(
                          organizationSaved.id!!, workspaceSaved.id!!, ROLE_USER)
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ_SECURITY",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  workspaceApiService.getWorkspacePermissions(
                      organizationSaved.id!!, workspaceSaved.id!!, ROLE_USER)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC getWorkspaceSecurity`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC getWorkspaceSecurity : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organizationSaved =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole(id = TEST_USER_MAIL, role = role))
              val solutionSaved =
                  solutionApiService.createSolution(
                      organizationSaved.id!!, makeSolution(organizationSaved.id!!))
              val workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      makeWorkspaceWithRole(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          id = TEST_USER_MAIL,
                          role = ROLE_ADMIN))
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.getWorkspaceSecurity(
                          organizationSaved.id!!, workspaceSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  workspaceApiService.getWorkspaceSecurity(
                      organizationSaved.id!!, workspaceSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC getWorkspaceSecurity`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Workspace RBAC getWorkspaceSecurity : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organizationSaved =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN))
              val solutionSaved =
                  solutionApiService.createSolution(
                      organizationSaved.id!!, makeSolution(organizationSaved.id!!))
              val workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      makeWorkspaceWithRole(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          id = TEST_USER_MAIL,
                          role = role))
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.getWorkspaceSecurity(
                          organizationSaved.id!!, workspaceSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ_SECURITY",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  workspaceApiService.getWorkspaceSecurity(
                      organizationSaved.id!!, workspaceSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC setWorkspaceDefaultSecurity`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC setWorkspaceDefaultSecurity : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organizationSaved =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole(id = TEST_USER_MAIL, role = role))
              val solutionSaved =
                  solutionApiService.createSolution(
                      organizationSaved.id!!, makeSolution(organizationSaved.id!!))
              val workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      makeWorkspaceWithRole(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          id = TEST_USER_MAIL,
                          role = ROLE_ADMIN))
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.setWorkspaceDefaultSecurity(
                          organizationSaved.id!!, workspaceSaved.id!!, WorkspaceRole(ROLE_USER))
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  workspaceApiService.setWorkspaceDefaultSecurity(
                      organizationSaved.id!!, workspaceSaved.id!!, WorkspaceRole(ROLE_USER))
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC setWorkspaceDefaultSecurity`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Workspace RBAC setWorkspaceDefaultSecurity : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organizationSaved =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN))
              val solutionSaved =
                  solutionApiService.createSolution(
                      organizationSaved.id!!, makeSolution(organizationSaved.id!!))
              val workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      makeWorkspaceWithRole(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          id = TEST_USER_MAIL,
                          role = role))
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.setWorkspaceDefaultSecurity(
                          organizationSaved.id!!, workspaceSaved.id!!, WorkspaceRole(ROLE_USER))
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_WRITE_SECURITY",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  workspaceApiService.setWorkspaceDefaultSecurity(
                      organizationSaved.id!!, workspaceSaved.id!!, WorkspaceRole(ROLE_USER))
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC addWorkspaceAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC addWorkspaceAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organizationSaved =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole(id = TEST_USER_MAIL, role = role))
              val solutionSaved =
                  solutionApiService.createSolution(
                      organizationSaved.id!!, makeSolution(organizationSaved.id!!))
              val workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      makeWorkspaceWithRole(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          id = TEST_USER_MAIL,
                          role = ROLE_ADMIN))
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.addWorkspaceAccessControl(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          WorkspaceAccessControl("id", ROLE_USER))
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  workspaceApiService.addWorkspaceAccessControl(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      WorkspaceAccessControl("id", ROLE_USER))
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC addWorkspaceAccessControl`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Workspace RBAC addWorkspaceAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organizationSaved =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN))
              val solutionSaved =
                  solutionApiService.createSolution(
                      organizationSaved.id!!, makeSolution(organizationSaved.id!!))
              val workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      makeWorkspaceWithRole(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          id = TEST_USER_MAIL,
                          role = role))
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.addWorkspaceAccessControl(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          WorkspaceAccessControl("id", ROLE_USER))
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_WRITE_SECURITY",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  workspaceApiService.addWorkspaceAccessControl(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      WorkspaceAccessControl("id", ROLE_USER))
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC getWorkspaceAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC getWorkspaceAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organizationSaved =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole(id = TEST_USER_MAIL, role = role))
              val solutionSaved =
                  solutionApiService.createSolution(
                      organizationSaved.id!!, makeSolution(organizationSaved.id!!))
              val workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      makeWorkspaceWithRole(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          id = TEST_USER_MAIL,
                          role = ROLE_ADMIN))
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.getWorkspaceAccessControl(
                          organizationSaved.id!!, workspaceSaved.id!!, TEST_USER_MAIL)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  workspaceApiService.getWorkspaceAccessControl(
                      organizationSaved.id!!, workspaceSaved.id!!, TEST_USER_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC getWorkspaceAccessControl`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Workspace RBAC getWorkspaceAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organizationSaved =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN))
              val solutionSaved =
                  solutionApiService.createSolution(
                      organizationSaved.id!!, makeSolution(organizationSaved.id!!))
              val workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      makeWorkspaceWithRole(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          id = TEST_USER_MAIL,
                          role = role))
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.getWorkspaceAccessControl(
                          organizationSaved.id!!, workspaceSaved.id!!, TEST_USER_MAIL)
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ_SECURITY",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  workspaceApiService.getWorkspaceAccessControl(
                      organizationSaved.id!!, workspaceSaved.id!!, TEST_USER_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC removeWorkspaceAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC removeWorkspaceAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organizationSaved =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole(id = TEST_USER_MAIL, role = role))
              val solutionSaved =
                  solutionApiService.createSolution(
                      organizationSaved.id!!, makeSolution(organizationSaved.id!!))
              val workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      makeWorkspaceWithRole(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          id = TEST_USER_MAIL,
                          role = ROLE_ADMIN))
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.removeWorkspaceAccessControl(
                          organizationSaved.id!!, workspaceSaved.id!!, TEST_USER_MAIL)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  workspaceApiService.removeWorkspaceAccessControl(
                      organizationSaved.id!!, workspaceSaved.id!!, TEST_USER_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC removeWorkspaceAccessControl`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Workspace RBAC removeWorkspaceAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organizationSaved =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN))
              val solutionSaved =
                  solutionApiService.createSolution(
                      organizationSaved.id!!, makeSolution(organizationSaved.id!!))
              val workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      makeWorkspaceWithRole(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          id = TEST_USER_MAIL,
                          role = role))
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.removeWorkspaceAccessControl(
                          organizationSaved.id!!, workspaceSaved.id!!, TEST_USER_MAIL)
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_WRITE_SECURITY",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  workspaceApiService.removeWorkspaceAccessControl(
                      organizationSaved.id!!, workspaceSaved.id!!, TEST_USER_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC updateWorkspaceAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC updateWorkspaceAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organizationSaved =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole(id = TEST_USER_MAIL, role = role))
              val solutionSaved =
                  solutionApiService.createSolution(
                      organizationSaved.id!!, makeSolution(organizationSaved.id!!))
              val workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      makeWorkspaceWithRole(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          id = TEST_USER_MAIL,
                          role = ROLE_ADMIN))
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.updateWorkspaceAccessControl(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          TEST_USER_MAIL,
                          WorkspaceRole(ROLE_ADMIN))
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  workspaceApiService.updateWorkspaceAccessControl(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      TEST_USER_MAIL,
                      WorkspaceRole(ROLE_ADMIN))
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC updateWorkspaceAccessControl`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Workspace RBAC updateWorkspaceAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organizationSaved =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN))
              val solutionSaved =
                  solutionApiService.createSolution(
                      organizationSaved.id!!, makeSolution(organizationSaved.id!!))
              val workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      makeWorkspaceWithRole(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          id = TEST_USER_MAIL,
                          role = role))
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.updateWorkspaceAccessControl(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          TEST_USER_MAIL,
                          WorkspaceRole(ROLE_ADMIN))
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_WRITE_SECURITY",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  workspaceApiService.updateWorkspaceAccessControl(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      TEST_USER_MAIL,
                      WorkspaceRole(ROLE_ADMIN))
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC getWorkspaceSecurityUsers`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC getWorkspaceSecurityUsers : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organizationSaved =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole(id = TEST_USER_MAIL, role = role))
              val solutionSaved =
                  solutionApiService.createSolution(
                      organizationSaved.id!!, makeSolution(organizationSaved.id!!))
              val workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      makeWorkspaceWithRole(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          id = TEST_USER_MAIL,
                          role = ROLE_ADMIN))
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.getWorkspaceSecurityUsers(
                          organizationSaved.id!!, workspaceSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  workspaceApiService.getWorkspaceSecurityUsers(
                      organizationSaved.id!!, workspaceSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC getWorkspaceSecurityUsers`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Workspace RBAC getWorkspaceSecurityUsers : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              val organizationSaved =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN))
              val solutionSaved =
                  solutionApiService.createSolution(
                      organizationSaved.id!!, makeSolution(organizationSaved.id!!))
              val workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      makeWorkspaceWithRole(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          id = TEST_USER_MAIL,
                          role = role))
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.getWorkspaceSecurityUsers(
                          organizationSaved.id!!, workspaceSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ_SECURITY",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  workspaceApiService.getWorkspaceSecurityUsers(
                      organizationSaved.id!!, workspaceSaved.id!!)
                }
              }
            }
          }

  fun makeOrganizationWithRole(id: String, role: String): Organization {
    return Organization(
        id = UUID.randomUUID().toString(),
        name = "Organization",
        ownerId = "my.account-tester@cosmotech.com",
        security =
            OrganizationSecurity(
                default = ROLE_NONE,
                accessControlList =
                    mutableListOf(
                        OrganizationAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                        OrganizationAccessControl(id = id, role = role))))
  }

  fun makeSolution(organizationId: String): Solution {
    return Solution(
        id = UUID.randomUUID().toString(),
        key = UUID.randomUUID().toString(),
        name = "Solution",
        organizationId = organizationId,
        ownerId = "ownerId",
        security =
            SolutionSecurity(
                default = ROLE_NONE,
                mutableListOf(
                    SolutionAccessControl(id = CONNECTED_ADMIN_USER, role = ROLE_ADMIN),
                    SolutionAccessControl(id = TEST_USER_MAIL, role = ROLE_ADMIN))))
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
        id = UUID.randomUUID().toString(),
        organizationId = organizationId,
        ownerId = "ownerId",
        security =
            WorkspaceSecurity(
                default = ROLE_NONE,
                accessControlList =
                    mutableListOf(
                        WorkspaceAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                        WorkspaceAccessControl(id = id, role = role))))
  }
}
