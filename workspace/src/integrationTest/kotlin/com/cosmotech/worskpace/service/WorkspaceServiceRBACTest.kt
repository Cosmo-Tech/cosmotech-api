// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.worskpace.service

import com.azure.storage.blob.BlobServiceClient
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
import com.cosmotech.api.utils.ResourceScanner
import com.cosmotech.api.utils.SecretManager
import com.cosmotech.api.utils.getCurrentAccountIdentifier
import com.cosmotech.api.utils.getCurrentAuthenticatedRoles
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationSecurity
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.solution.domain.Solution
import com.cosmotech.workspace.api.WorkspaceApiService
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceAccessControl
import com.cosmotech.workspace.domain.WorkspaceRole
import com.cosmotech.workspace.domain.WorkspaceSecret
import com.cosmotech.workspace.domain.WorkspaceSecurity
import com.cosmotech.workspace.domain.WorkspaceSolution
import com.redis.om.spring.RediSearchIndexer
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockkStatic
import java.io.InputStream
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

  @MockK(relaxed = true) private lateinit var azureStorageBlobServiceClient: BlobServiceClient
  @MockK private lateinit var secretManagerMock: SecretManager
  @MockK private lateinit var resource: Resource
  @MockK private lateinit var inputStream: InputStream

  @RelaxedMockK private lateinit var resourceScanner: ResourceScanner

  @Autowired lateinit var rediSearchIndexer: RediSearchIndexer
  @Autowired lateinit var organizationApiService: OrganizationApiService
  @Autowired lateinit var solutionApiService: SolutionApiService
  @Autowired lateinit var workspaceApiService: WorkspaceApiService
  @Autowired lateinit var csmPlatformProperties: CsmPlatformProperties

  lateinit var organization: Organization
  lateinit var solution: Solution
  lateinit var workspace: Workspace

  lateinit var organizationSaved: Organization
  lateinit var solutionSaved: Solution
  lateinit var workspaceSaved: Workspace

  @BeforeEach
  fun setUp() {
    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "test.user"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("user")

    ReflectionTestUtils.setField(
        workspaceApiService, "azureStorageBlobServiceClient", azureStorageBlobServiceClient)

    ReflectionTestUtils.setField(workspaceApiService, "secretManager", secretManagerMock)

    rediSearchIndexer.createIndexFor(Organization::class.java)
    rediSearchIndexer.createIndexFor(Solution::class.java)
    rediSearchIndexer.createIndexFor(Workspace::class.java)
  }

  @TestFactory
  fun `test RBAC findAllWorkspaces`() =
      mapOf(
              ROLE_VIEWER to 1,
              ROLE_EDITOR to 2,
              ROLE_VALIDATOR to 3,
              ROLE_USER to 4,
              ROLE_NONE to 4,
              ROLE_ADMIN to 5,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC findAllWorkspaces : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              if (!::organizationSaved.isInitialized) {
                organizationSaved =
                    organizationApiService.registerOrganization(
                        mockOrganization(id = "id", userName = FAKE_MAIL, role = ROLE_USER))
              }
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceApiService.createWorkspace(
                  organizationSaved.id!!, mockWorkspace(userName = FAKE_MAIL, role = role))
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              val workspaces =
                  workspaceApiService.findAllWorkspaces(organizationSaved.id!!, null, null)
              when (role) {
                ROLE_VIEWER -> assertEquals(shouldThrow, workspaces.size)
                ROLE_EDITOR -> assertEquals(shouldThrow, workspaces.size)
                ROLE_VALIDATOR -> assertEquals(shouldThrow, workspaces.size)
                ROLE_USER -> assertEquals(shouldThrow, workspaces.size)
                ROLE_NONE -> assertEquals(shouldThrow, workspaces.size)
                ROLE_ADMIN -> assertEquals(shouldThrow, workspaces.size)
              }
            }
          }

  @TestFactory
  fun `test RBAC createWorkspace`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC createWorkspace : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(userName = FAKE_MAIL, role = role))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspace = mockWorkspace()
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
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
                  workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC findWorkspaceById`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC findWorkspaceById : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(userName = FAKE_MAIL, role = role))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      mockWorkspace(userName = FAKE_MAIL, role = ROLE_ADMIN))
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.findWorkspaceById(
                          organizationSaved.id!!, workspaceSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
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
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Workspace RBAC findWorkspaceById : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(userName = FAKE_MAIL, role = ROLE_ADMIN))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!, mockWorkspace(userName = FAKE_MAIL, role = role))
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

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
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC deleteWorkspace : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(userName = FAKE_MAIL, role = role))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      mockWorkspace(userName = FAKE_MAIL, role = ROLE_ADMIN))
              every { secretManagerMock.deleteSecret(any(), any()) } returns Unit
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

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
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Workspace RBAC deleteWorkspace : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(userName = FAKE_MAIL, role = ROLE_ADMIN))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())

              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL
              every { secretManagerMock.deleteSecret(any(), any()) } returns Unit

              workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!, mockWorkspace(userName = FAKE_MAIL, role = role))

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.deleteWorkspace(
                          organizationSaved.id!!, workspaceSaved.id!!)
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
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC updateWorkspace : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(userName = FAKE_MAIL, role = role))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      mockWorkspace(userName = FAKE_MAIL, role = ROLE_ADMIN))
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.updateWorkspace(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          mockWorkspace(
                              organizationId = organizationSaved.id!!,
                              solutionId = solutionSaved.id!!))
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  workspaceApiService.updateWorkspace(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      mockWorkspace(
                          organizationId = organizationSaved.id!!, solutionId = solutionSaved.id!!))
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC updateWorkspace`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Workspace RBAC updateWorkspace : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(userName = FAKE_MAIL, role = ROLE_ADMIN))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!, mockWorkspace(userName = FAKE_MAIL, role = role))
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.updateWorkspace(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          mockWorkspace(
                              organizationId = organizationSaved.id!!,
                              solutionId = solutionSaved.id!!))
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_WRITE",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  workspaceApiService.updateWorkspace(
                      organizationSaved.id!!,
                      workspaceSaved.id!!,
                      mockWorkspace(
                          organizationId = organizationSaved.id!!, solutionId = solutionSaved.id!!))
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC findAllWorkspaceFiles`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC findAllWorkspaceFiles : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(userName = FAKE_MAIL, role = role))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      mockWorkspace(userName = FAKE_MAIL, role = ROLE_ADMIN))
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

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
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Workspace RBAC findAllWorkspaceFiles : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(userName = FAKE_MAIL, role = ROLE_ADMIN))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!, mockWorkspace(userName = FAKE_MAIL, role = role))
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

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
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC uploadWorkspaceFile : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              ReflectionTestUtils.setField(workspaceApiService, "resourceScanner", resourceScanner)
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(userName = FAKE_MAIL, role = role))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      mockWorkspace(userName = FAKE_MAIL, role = ROLE_ADMIN))
              every { resourceScanner.scanMimeTypes(any(), any()) } returns Unit
              every { resource.getFilename() } returns ""
              every { resource.contentLength() } returns 1
              every { resource.getInputStream() } returns inputStream
              every {
                azureStorageBlobServiceClient
                    .getBlobContainerClient(any())
                    .getBlobClient(any(), any())
                    .upload(any(), any(), any())
              } returns Unit
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

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
                      organizationSaved.id!!, workspaceSaved.id!!, resource, true, "")
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC uploadWorkspaceFile`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Workspace RBAC uploadWorkspaceFile : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              ReflectionTestUtils.setField(workspaceApiService, "resourceScanner", resourceScanner)
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(userName = FAKE_MAIL, role = ROLE_ADMIN))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL
              every { resourceScanner.scanMimeTypes(any(), any()) } returns Unit
              every { resource.getFilename() } returns ""
              every { resource.contentLength() } returns 1
              every { resource.getInputStream() } returns inputStream
              every {
                azureStorageBlobServiceClient
                    .getBlobContainerClient(any())
                    .getBlobClient(any(), any())
                    .upload(any(), any(), any())
              } returns Unit
              workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!, mockWorkspace(userName = FAKE_MAIL, role = role))
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.uploadWorkspaceFile(
                          organizationSaved.id!!, workspaceSaved.id!!, resource, true, "")
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
                  workspaceApiService.uploadWorkspaceFile(
                      organizationSaved.id!!, workspaceSaved.id!!, resource, true, "")
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC deleteAllWorkspaceFiles`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC deleteAllWorkspaceFiles : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(userName = FAKE_MAIL, role = role))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      mockWorkspace(userName = FAKE_MAIL, role = ROLE_ADMIN))
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

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
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Workspace RBAC deleteAllWorkspaceFiles : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(userName = FAKE_MAIL, role = ROLE_ADMIN))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!, mockWorkspace(userName = FAKE_MAIL, role = role))
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.deleteAllWorkspaceFiles(
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
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC downloadWorkspaceFile : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(userName = FAKE_MAIL, role = role))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      mockWorkspace(userName = FAKE_MAIL, role = ROLE_ADMIN))
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

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
                assertDoesNotThrow {
                  workspaceApiService.downloadWorkspaceFile(
                      organizationSaved.id!!, workspaceSaved.id!!, "")
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC downloadWorkspaceFile`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Workspace RBAC downloadWorkspaceFile : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(userName = FAKE_MAIL, role = ROLE_ADMIN))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!, mockWorkspace(userName = FAKE_MAIL, role = role))
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

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
                assertDoesNotThrow {
                  workspaceApiService.downloadWorkspaceFile(
                      organizationSaved.id!!, workspaceSaved.id!!, "")
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC deleteWorkspaceFile`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC deleteWorkspaceFile : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(userName = FAKE_MAIL, role = role))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      mockWorkspace(userName = FAKE_MAIL, role = ROLE_ADMIN))
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

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
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Workspace RBAC deleteWorkspaceFile : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(userName = FAKE_MAIL, role = ROLE_ADMIN))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!, mockWorkspace(userName = FAKE_MAIL, role = role))
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.deleteWorkspaceFile(
                          organizationSaved.id!!, workspaceSaved.id!!, "")
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
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC getWorkspacePermissions : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(userName = FAKE_MAIL, role = role))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      mockWorkspace(userName = FAKE_MAIL, role = ROLE_ADMIN))
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

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
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Workspace RBAC getWorkspacePermissions : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(userName = FAKE_MAIL, role = ROLE_ADMIN))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!, mockWorkspace(userName = FAKE_MAIL, role = role))
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.getWorkspacePermissions(
                          organizationSaved.id!!, workspaceSaved.id!!, ROLE_USER)
                    }
                if (role == ROLE_VALIDATOR || role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ_SECURITY",
                      exception.message)
                }
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
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC getWorkspaceSecurity : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(userName = FAKE_MAIL, role = role))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      mockWorkspace(userName = FAKE_MAIL, role = ROLE_ADMIN))
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

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
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Workspace RBAC getWorkspaceSecurity : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(userName = FAKE_MAIL, role = ROLE_ADMIN))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!, mockWorkspace(userName = FAKE_MAIL, role = role))
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.getWorkspaceSecurity(
                          organizationSaved.id!!, workspaceSaved.id!!)
                    }
                if (role == ROLE_VALIDATOR || role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ_SECURITY",
                      exception.message)
                }
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
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC setWorkspaceDefaultSecurity : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(userName = FAKE_MAIL, role = role))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      mockWorkspace(userName = FAKE_MAIL, role = ROLE_ADMIN))
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

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
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Workspace RBAC setWorkspaceDefaultSecurity : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(userName = FAKE_MAIL, role = ROLE_ADMIN))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!, mockWorkspace(userName = FAKE_MAIL, role = role))
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

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
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC addWorkspaceAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(userName = FAKE_MAIL, role = role))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      mockWorkspace(userName = FAKE_MAIL, role = ROLE_ADMIN))
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

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
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Workspace RBAC addWorkspaceAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(userName = FAKE_MAIL, role = ROLE_ADMIN))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!, mockWorkspace(userName = FAKE_MAIL, role = role))
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

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
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC getWorkspaceAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(userName = FAKE_MAIL, role = role))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      mockWorkspace(userName = FAKE_MAIL, role = ROLE_ADMIN))
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.getWorkspaceAccessControl(
                          organizationSaved.id!!, workspaceSaved.id!!, FAKE_MAIL)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  workspaceApiService.getWorkspaceAccessControl(
                      organizationSaved.id!!, workspaceSaved.id!!, FAKE_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC getWorkspaceAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Workspace RBAC getWorkspaceAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(userName = FAKE_MAIL, role = ROLE_ADMIN))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!, mockWorkspace(userName = FAKE_MAIL, role = role))
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.getWorkspaceAccessControl(
                          organizationSaved.id!!, workspaceSaved.id!!, FAKE_MAIL)
                    }
                if (role == ROLE_VALIDATOR || role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ_SECURITY",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  workspaceApiService.getWorkspaceAccessControl(
                      organizationSaved.id!!, workspaceSaved.id!!, FAKE_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC removeWorkspaceAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC removeWorkspaceAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(userName = FAKE_MAIL, role = role))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      mockWorkspace(userName = FAKE_MAIL, role = ROLE_ADMIN))
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.removeWorkspaceAccessControl(
                          organizationSaved.id!!, workspaceSaved.id!!, FAKE_MAIL)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  workspaceApiService.removeWorkspaceAccessControl(
                      organizationSaved.id!!, workspaceSaved.id!!, FAKE_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC removeWorkspaceAccessControl`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Workspace RBAC removeWorkspaceAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(userName = FAKE_MAIL, role = ROLE_ADMIN))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!, mockWorkspace(userName = FAKE_MAIL, role = role))
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.removeWorkspaceAccessControl(
                          organizationSaved.id!!, workspaceSaved.id!!, FAKE_MAIL)
                    }
                if (role == ROLE_VALIDATOR || role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_WRITE_SECURITY",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  workspaceApiService.removeWorkspaceAccessControl(
                      organizationSaved.id!!, workspaceSaved.id!!, FAKE_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC updateWorkspaceAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC updateWorkspaceAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(userName = FAKE_MAIL, role = role))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      mockWorkspace(userName = FAKE_MAIL, role = ROLE_ADMIN))
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.updateWorkspaceAccessControl(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          FAKE_MAIL,
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
                      FAKE_MAIL,
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
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Workspace RBAC updateWorkspaceAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(userName = FAKE_MAIL, role = ROLE_ADMIN))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!, mockWorkspace(userName = FAKE_MAIL, role = role))
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.updateWorkspaceAccessControl(
                          organizationSaved.id!!,
                          workspaceSaved.id!!,
                          FAKE_MAIL,
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
                      FAKE_MAIL,
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
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC getWorkspaceSecurityUsers : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(userName = FAKE_MAIL, role = role))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      mockWorkspace(userName = FAKE_MAIL, role = ROLE_ADMIN))
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

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
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Workspace RBAC getWorkspaceSecurityUsers : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(userName = FAKE_MAIL, role = ROLE_ADMIN))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!, mockWorkspace(userName = FAKE_MAIL, role = role))
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.getWorkspaceSecurityUsers(
                          organizationSaved.id!!, workspaceSaved.id!!)
                    }
                if (role == ROLE_VALIDATOR || role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ_SECURITY",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  workspaceApiService.getWorkspaceSecurityUsers(
                      organizationSaved.id!!, workspaceSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC createSecret`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC createSecret : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(userName = FAKE_MAIL, role = role))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!,
                      mockWorkspace(userName = FAKE_MAIL, role = ROLE_ADMIN))
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL
              every { secretManagerMock.createOrReplaceSecret(any(), any(), any()) } returns Unit

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.createSecret(
                          organizationSaved.id!!, workspaceSaved.id!!, WorkspaceSecret(""))
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  workspaceApiService.createSecret(
                      organizationSaved.id!!, workspaceSaved.id!!, WorkspaceSecret(""))
                }
              }
            }
          }

  @TestFactory
  fun `test Workspace RBAC createSecret`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Workspace RBAC createSecret : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      mockOrganization(userName = FAKE_MAIL, role = ROLE_ADMIN))
              solutionSaved =
                  solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
              workspaceSaved =
                  workspaceApiService.createWorkspace(
                      organizationSaved.id!!, mockWorkspace(userName = FAKE_MAIL, role = role))
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL
              every { secretManagerMock.createOrReplaceSecret(any(), any(), any()) } returns Unit

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      workspaceApiService.createSecret(
                          organizationSaved.id!!, workspaceSaved.id!!, WorkspaceSecret(""))
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  workspaceApiService.createSecret(
                      organizationSaved.id!!, workspaceSaved.id!!, WorkspaceSecret(""))
                }
              }
            }
          }

  fun mockOrganization(
      id: String = UUID.randomUUID().toString(),
      userName: String = FAKE_MAIL,
      role: String = ROLE_USER
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
                        OrganizationAccessControl(id = userName, role = role),
                        OrganizationAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN))))
  }

  fun mockSolution(organizationId: String = organizationSaved.id!!): Solution {
    return Solution(
        id = "solutionId",
        key = UUID.randomUUID().toString(),
        name = "My solution",
        organizationId = organizationId,
        ownerId = "ownerId")
  }

  fun mockWorkspace(
      organizationId: String = organizationSaved.id!!,
      solutionId: String = solutionSaved.id!!,
      name: String = "name",
      userName: String = FAKE_MAIL,
      role: String = ROLE_USER
  ): Workspace {
    return Workspace(
        key = UUID.randomUUID().toString(),
        name = name,
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
                        WorkspaceAccessControl(id = userName, role = role),
                        WorkspaceAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN))))
  }
}
