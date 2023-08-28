// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.worskpace.service // Copyright (c) Cosmo Tech.
// Licensed under the MIT license.

import com.azure.storage.blob.BlobServiceClient
import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
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
import io.mockk.junit5.MockKExtension
import io.mockk.mockkStatic
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
import org.springframework.core.io.Resource
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.util.ReflectionTestUtils

const val CONNECTED_ADMIN_USER = "test.admin@cosmotech.com"
const val CONNECTED_DEFAULT_USER = "test.user@cosmotech.com"
const val FAKE_MAIL = "fake@mail.fr"

@ActiveProfiles(profiles = ["workspace-test"])
@ExtendWith(MockKExtension::class)
@ExtendWith(SpringExtension::class)
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Suppress("FunctionName")
class WorkspaceServiceIntegrationTest : CsmRedisTestBase() {

  private val logger = LoggerFactory.getLogger(WorkspaceServiceIntegrationTest::class.java)
  private val defaultName = "my.account-tester@cosmotech.com"

  @MockK(relaxed = true) private lateinit var azureStorageBlobServiceClient: BlobServiceClient
  @MockK private lateinit var secretManagerMock: SecretManager
  @MockK private lateinit var resource: Resource
  @MockK private lateinit var resourceScanner: ResourceScanner

  @Autowired lateinit var rediSearchIndexer: RediSearchIndexer
  @Autowired lateinit var organizationApiService: OrganizationApiService
  @Autowired lateinit var solutionApiService: SolutionApiService
  @Autowired lateinit var workspaceApiService: WorkspaceApiService
  @Autowired lateinit var csmPlatformProperties: CsmPlatformProperties

  lateinit var orga: Organization
  lateinit var sol: Solution
  lateinit var work: Workspace

  lateinit var organization: Organization
  lateinit var solution: Solution
  lateinit var workspace: Workspace

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

    orga = mockOrganization("Organization test")
    organization = organizationApiService.registerOrganization(orga)

    sol = mockSolution(organization.id!!)
    solution = solutionApiService.createSolution(organization.id!!, sol)

    work = mockWorkspace(organization.id!!, solution.id!!, "Workspace")
    workspace = workspaceApiService.createWorkspace(organization.id!!, work)
  }

  @Test
  fun `test CRUD operations on Workspace as User Admin`() {

    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")
    every { secretManagerMock.deleteSecret(any(), any()) } returns Unit

    logger.info("should create a second new workspace")
    val workspace2 = mockWorkspace(organization.id!!, solution.id!!, "Workspace 2")
    val workspaceRegistered2 = workspaceApiService.createWorkspace(organization.id!!, workspace2)
    val workspaceRetrieved =
        workspaceApiService.findWorkspaceById(organization.id!!, workspace.id!!)
    assertEquals(workspace, workspaceRetrieved)

    logger.info("should find all workspaces and assert there are 2")
    val workspacesList: List<Workspace> =
        workspaceApiService.findAllWorkspaces(organization.id!!, null, null)
    assertTrue(workspacesList.size == 2)

    logger.info("should update the name of the first workspace")
    val updatedWorkspace =
        workspaceApiService.updateWorkspace(
            organization.id!!,
            workspace.id!!,
            workspace.copy(name = "Workspace 1 updated", organizationId = null))
    assertEquals("Workspace 1 updated", updatedWorkspace.name)
    assertEquals(workspace.organizationId, updatedWorkspace.organizationId)

    /*
     TODO : Fix the corountine effect

    logger.info("should delete the first workspace")
     workspaceApiService.deleteWorkspace(organizationRegistered.id!!, workspaceRegistered2.id!!)
     val workspacesListAfterDelete: List<Workspace> =
         workspaceApiService.findAllWorkspaces(organizationRegistered.id!!, null, null)
     assertTrue(workspacesListAfterDelete.size == 1)*/
  }

  @Test
  fun `test CRUD operations on Workspace as User Unauthorized`() {

    every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

    logger.info("should not create a new workspace")
    val workspace2 = mockWorkspace(organization.id!!, solution.id!!, "Workspace 2")
    assertThrows<CsmAccessForbiddenException> {
      workspaceApiService.createWorkspace(organization.id!!, workspace2)
    }

    logger.info("should not retrieve a workspace")
    assertThrows<CsmAccessForbiddenException> {
      workspaceApiService.findWorkspaceById(organization.id!!, workspace.id!!)
    }

    logger.info("should not find all workspaces")
    val workspacesList: List<Workspace> =
        workspaceApiService.findAllWorkspaces(organization.id!!, null, null)
    assertTrue(workspacesList.isEmpty())

    logger.info("should not update a workspace")
    assertThrows<CsmAccessForbiddenException> {
      workspaceApiService.updateWorkspace(
          organization.id!!, workspace.id!!, workspace.copy(name = "Workspace 1 updated"))
    }

    logger.info("should not delete a workspace")
    assertThrows<CsmAccessForbiddenException> {
      workspaceApiService.deleteWorkspace(organization.id!!, workspace.id!!)
    }
  }

  @Test
  fun `test find All Workspaces with different pagination params`() {

    val workspaceNumber = 20
    val defaultPageSize = csmPlatformProperties.twincache.workspace.defaultPageSize
    val expectedSize = 15
    IntRange(1, workspaceNumber - 1).forEach {
      val workspace = mockWorkspace(organization.id!!, solution.id!!, "w-workspace-$it")
      workspaceApiService.createWorkspace(organization.id!!, workspace)
    }
    logger.info("should find all workspaces and assert there are $workspaceNumber")
    var workspacesList = workspaceApiService.findAllWorkspaces(organization.id!!, null, null)
    assertEquals(workspaceNumber, workspacesList.size)

    logger.info("should find all workspaces and assert it equals defaultPageSize: $defaultPageSize")
    workspacesList = workspaceApiService.findAllWorkspaces(organization.id!!, 0, null)
    assertEquals(defaultPageSize, workspacesList.size)

    logger.info("should find all workspaces and assert there are expected size: $expectedSize")
    workspacesList = workspaceApiService.findAllWorkspaces(organization.id!!, 0, expectedSize)
    assertEquals(expectedSize, workspacesList.size)

    logger.info("should find all workspaces and assert it returns the  second / last page")
    workspacesList = workspaceApiService.findAllWorkspaces(organization.id!!, 1, expectedSize)
    assertEquals(workspaceNumber - expectedSize, workspacesList.size)
  }

  @Test
  fun `test find All Workspaces with wrong pagination params`() {
    logger.info("should throw IllegalArgumentException when page and size are zero")
    assertThrows<IllegalArgumentException> {
      workspaceApiService.findAllWorkspaces(organization.id!!, 0, 0)
    }
    logger.info("should throw IllegalArgumentException when page is negative")
    assertThrows<IllegalArgumentException> {
      workspaceApiService.findAllWorkspaces(organization.id!!, -1, 1)
    }
    logger.info("should throw IllegalArgumentException when size is negative")
    assertThrows<IllegalArgumentException> {
      workspaceApiService.findAllWorkspaces(organization.id!!, 0, -1)
    }
  }

  @Test
  fun `test RBAC WorkspaceSecurity as User Admin`() {

    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")

    logger.info("should get default security with role NONE")
    val workspaceSecurity =
        workspaceApiService.getWorkspaceSecurity(organization.id!!, workspace.id!!)
    assertEquals(ROLE_NONE, workspaceSecurity.default)

    logger.info("should set default security with role VIEWER")
    val workspaceRole = WorkspaceRole(ROLE_VIEWER)
    val workspaceSecurityRegistered =
        workspaceApiService.setWorkspaceDefaultSecurity(
            organization.id!!, workspace.id!!, workspaceRole)
    assertEquals(workspaceRole.role, workspaceSecurityRegistered.default)
  }

  @Test
  fun `test RBAC WorkspaceSecurity as User Unauthorized`() {

    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_DEFAULT_USER

    logger.info("should throw CsmAccessForbiddenException when getting default security")
    assertThrows<CsmAccessForbiddenException> {
      workspaceApiService.getWorkspaceSecurity(organization.id!!, workspace.id!!)
    }

    logger.info("should throw CsmAccessForbiddenException when setting default security")
    val workspaceRole = WorkspaceRole(ROLE_VIEWER)
    assertThrows<CsmAccessForbiddenException> {
      workspaceApiService.setWorkspaceDefaultSecurity(
          organization.id!!, workspace.id!!, workspaceRole)
    }
  }

  @Test
  fun `test RBAC as User Unauthorized`() {
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_DEFAULT_USER

    assertEquals(0, workspaceApiService.findAllWorkspaces(organization.id!!, null, null).size)
  }

  @Test
  fun `test RBAC AccessControls on Workspace as User Admin`() {

    logger.info("should add a new access control")
    val workspaceAccessControl = WorkspaceAccessControl(FAKE_MAIL, ROLE_VIEWER)
    var workspaceAccessControlRegistered =
        workspaceApiService.addWorkspaceAccessControl(
            organization.id!!, workspace.id!!, workspaceAccessControl)
    assertEquals(workspaceAccessControl, workspaceAccessControlRegistered)

    logger.info("should get the access control")
    workspaceAccessControlRegistered =
        workspaceApiService.getWorkspaceAccessControl(organization.id!!, workspace.id!!, FAKE_MAIL)
    assertEquals(workspaceAccessControl, workspaceAccessControlRegistered)

    logger.info("should update the access control")
    workspaceAccessControlRegistered =
        workspaceApiService.updateWorkspaceAccessControl(
            organization.id!!, workspace.id!!, FAKE_MAIL, WorkspaceRole(ROLE_EDITOR))
    assertEquals(ROLE_EDITOR, workspaceAccessControlRegistered.role)

    logger.info("should get the list of users and assert there are 3")
    val userList = workspaceApiService.getWorkspaceSecurityUsers(organization.id!!, workspace.id!!)
    assertEquals(3, userList.size)

    logger.info("should remove the access control")
    workspaceApiService.removeWorkspaceAccessControl(organization.id!!, workspace.id!!, FAKE_MAIL)
    assertThrows<CsmResourceNotFoundException> {
      workspaceAccessControlRegistered =
          workspaceApiService.getWorkspaceAccessControl(
              organization.id!!, workspace.id!!, FAKE_MAIL)
    }
  }

  @Test
  fun `test RBAC AccessControls on Workspace as User Unauthorized`() {

    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_DEFAULT_USER

    logger.info("should throw CsmAccessForbiddenException when adding a new access control")
    val workspaceAccessControl = WorkspaceAccessControl(FAKE_MAIL, ROLE_VIEWER)
    assertThrows<CsmAccessForbiddenException> {
      workspaceApiService.addWorkspaceAccessControl(
          organization.id!!, workspace.id!!, workspaceAccessControl)
    }

    logger.info("should throw CsmAccessForbiddenException when getting the access control")
    assertThrows<CsmAccessForbiddenException> {
      workspaceApiService.getWorkspaceAccessControl(organization.id!!, workspace.id!!, FAKE_MAIL)
    }

    logger.info("should throw CsmAccessForbiddenException when updating the access control")
    assertThrows<CsmAccessForbiddenException> {
      workspaceApiService.updateWorkspaceAccessControl(
          organization.id!!, workspace.id!!, FAKE_MAIL, WorkspaceRole(ROLE_VIEWER))
    }

    logger.info("should throw CsmAccessForbiddenException when getting the list of users")
    assertThrows<CsmAccessForbiddenException> {
      workspaceApiService.getWorkspaceSecurityUsers(organization.id!!, workspace.id!!)
    }

    logger.info("should throw CsmAccessForbiddenException when removing the access control")
    assertThrows<CsmAccessForbiddenException> {
      workspaceApiService.removeWorkspaceAccessControl(organization.id!!, workspace.id!!, FAKE_MAIL)
    }
  }

  @Nested
  inner class RBACTests {

    @TestFactory
    fun `test RBAC findAllWorkspaces`() =
        mapOf(
                ROLE_VIEWER to false,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to false,
                ROLE_USER to false,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              DynamicTest.dynamicTest("Test RBAC findAllWorkspaces : $role") {
                organization =
                    organizationApiService.registerOrganization(
                        mockOrganization(id = "id", roleName = defaultName))
                solution = solutionApiService.createSolution(organization.id!!, mockSolution())

                every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
                every { getCurrentAccountIdentifier(any()) } returns defaultName

                val allWorkspaces =
                    workspaceApiService.findAllWorkspaces(organization.id!!, null, null)
                if (shouldThrow) {
                  assertEquals(0, allWorkspaces.size)
                } else {
                  assertNotNull(allWorkspaces)
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
                every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
                every { getCurrentAccountIdentifier(any()) } returns defaultName
                organization =
                    organizationApiService.registerOrganization(
                        mockOrganization(id = "id", roleName = defaultName, role = role))
                solution = solutionApiService.createSolution(organization.id!!, mockSolution())
                work = mockWorkspace(roleName = defaultName, role = role)

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    workspaceApiService.createWorkspace(organization.id!!, work)
                  }
                } else {
                  assertDoesNotThrow {
                    workspaceApiService.createWorkspace(organization.id!!, work)
                  }
                }
              }
            }

    @TestFactory
    fun `test RBAC findWorkspaceById`() =
        mapOf(
                ROLE_VIEWER to false,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to true,
                ROLE_USER to false,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              DynamicTest.dynamicTest("Test RBAC findWorkspaceById : $role") {
                organization =
                    organizationApiService.registerOrganization(
                        mockOrganization(id = "id", roleName = defaultName))
                solution = solutionApiService.createSolution(organization.id!!, mockSolution())

                every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
                every { getCurrentAccountIdentifier(any()) } returns defaultName

                workspace =
                    workspaceApiService.createWorkspace(
                        organization.id!!, mockWorkspace(roleName = defaultName, role = role))

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    workspaceApiService.findWorkspaceById(organization.id!!, workspace.id!!)
                  }
                } else {
                  assertDoesNotThrow {
                    workspaceApiService.findWorkspaceById(organization.id!!, workspace.id!!)
                  }
                }
              }
            }

    @TestFactory
    fun `test RBAC deleteWorkspace`() =
        mapOf(
                ROLE_VIEWER to true,
                ROLE_EDITOR to true,
                ROLE_VALIDATOR to true,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              DynamicTest.dynamicTest("Test RBAC deleteWorkspace : $role") {
                organization =
                    organizationApiService.registerOrganization(
                        mockOrganization(id = "id", roleName = defaultName))
                solution = solutionApiService.createSolution(organization.id!!, mockSolution())

                every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
                every { getCurrentAccountIdentifier(any()) } returns defaultName
                every { secretManagerMock.deleteSecret(any(), any()) } returns Unit

                workspace =
                    workspaceApiService.createWorkspace(
                        organization.id!!, mockWorkspace(roleName = defaultName, role = role))

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    workspaceApiService.deleteWorkspace(organization.id!!, workspace.id!!)
                  }
                } else {
                  assertDoesNotThrow {
                    workspaceApiService.deleteWorkspace(organization.id!!, workspace.id!!)
                  }
                }
              }
            }

    @TestFactory
    fun `test RBAC updateWorkspace`() =
        mapOf(
                ROLE_VIEWER to true,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to true,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              DynamicTest.dynamicTest("Test RBAC updateWorkspace : $role") {
                organization =
                    organizationApiService.registerOrganization(
                        mockOrganization(id = "id", roleName = defaultName))
                solution = solutionApiService.createSolution(organization.id!!, mockSolution())

                every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
                every { getCurrentAccountIdentifier(any()) } returns defaultName

                workspace =
                    workspaceApiService.createWorkspace(
                        organization.id!!, mockWorkspace(roleName = defaultName, role = role))

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    workspaceApiService.updateWorkspace(
                        organization.id!!,
                        workspace.id!!,
                        mockWorkspace(
                            organizationId = organization.id!!, solutionId = solution.id!!))
                  }
                } else {
                  assertDoesNotThrow {
                    workspaceApiService.updateWorkspace(
                        organization.id!!,
                        workspace.id!!,
                        mockWorkspace(
                            organizationId = organization.id!!, solutionId = solution.id!!))
                  }
                }
              }
            }

    @TestFactory
    fun `test RBAC findAllWorkspaceFiles`() =
        mapOf(
                ROLE_VIEWER to false,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to true,
                ROLE_USER to false,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              DynamicTest.dynamicTest("Test RBAC findAllWorkspaceFiles : $role") {
                organization =
                    organizationApiService.registerOrganization(
                        mockOrganization(id = "id", roleName = defaultName))
                solution = solutionApiService.createSolution(organization.id!!, mockSolution())

                every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
                every { getCurrentAccountIdentifier(any()) } returns defaultName

                workspace =
                    workspaceApiService.createWorkspace(
                        organization.id!!, mockWorkspace(roleName = defaultName, role = role))

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    workspaceApiService.findAllWorkspaceFiles(organization.id!!, workspace.id!!)
                  }
                } else {
                  assertDoesNotThrow {
                    workspaceApiService.findAllWorkspaceFiles(organization.id!!, workspace.id!!)
                  }
                }
              }
            }

    @TestFactory
    fun `test RBAC uploadWorkspaceFile`() =
        mapOf(
                ROLE_VIEWER to true,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to true,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              DynamicTest.dynamicTest("Test RBAC uploadWorkspaceFile : $role") {
                organization =
                    organizationApiService.registerOrganization(
                        mockOrganization(id = "id", roleName = defaultName))
                solution = solutionApiService.createSolution(organization.id!!, mockSolution())

                every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
                every { getCurrentAccountIdentifier(any()) } returns defaultName
                every { resource.getFilename() } returns ""
                every { resourceScanner.scanMimeTypes(any(), any()) } returns Unit
                every {
                  azureStorageBlobServiceClient
                      .getBlobContainerClient(any())
                      .getBlobClient(any(), any())
                      .upload(any(), any(), any())
                } returns Unit

                workspace =
                    workspaceApiService.createWorkspace(
                        organization.id!!, mockWorkspace(roleName = defaultName, role = role))

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    workspaceApiService.uploadWorkspaceFile(
                        organization.id!!, workspace.id!!, resource, true, "")
                  }
                } else {
                  assertDoesNotThrow {
                    workspaceApiService.uploadWorkspaceFile(
                        organization.id!!, workspace.id!!, resource, true, "")
                  }
                }
              }
            }

    @TestFactory
    fun `test RBAC deleteAllWorkspaceFiles`() =
        mapOf(
                ROLE_VIEWER to true,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to true,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              DynamicTest.dynamicTest("Test RBAC deleteAllWorkspaceFiles : $role") {
                organization =
                    organizationApiService.registerOrganization(
                        mockOrganization(id = "id", roleName = defaultName))
                solution = solutionApiService.createSolution(organization.id!!, mockSolution())

                every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
                every { getCurrentAccountIdentifier(any()) } returns defaultName

                workspace =
                    workspaceApiService.createWorkspace(
                        organization.id!!, mockWorkspace(roleName = defaultName, role = role))

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    workspaceApiService.deleteAllWorkspaceFiles(organization.id!!, workspace.id!!)
                  }
                } else {
                  assertDoesNotThrow {
                    workspaceApiService.deleteAllWorkspaceFiles(organization.id!!, workspace.id!!)
                  }
                }
              }
            }

    @TestFactory
    fun `test RBAC downloadWorkspaceFile`() =
        mapOf(
                ROLE_VIEWER to false,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to true,
                ROLE_USER to false,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              DynamicTest.dynamicTest("Test RBAC downloadWorkspaceFile : $role") {
                organization =
                    organizationApiService.registerOrganization(
                        mockOrganization(id = "id", roleName = defaultName))
                solution = solutionApiService.createSolution(organization.id!!, mockSolution())

                every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
                every { getCurrentAccountIdentifier(any()) } returns defaultName

                workspace =
                    workspaceApiService.createWorkspace(
                        organization.id!!, mockWorkspace(roleName = defaultName, role = role))

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    workspaceApiService.downloadWorkspaceFile(organization.id!!, workspace.id!!, "")
                  }
                } else {
                  assertDoesNotThrow {
                    workspaceApiService.downloadWorkspaceFile(organization.id!!, workspace.id!!, "")
                  }
                }
              }
            }

    @TestFactory
    fun `test RBAC deleteWorkspaceFile`() =
        mapOf(
                ROLE_VIEWER to true,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to true,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              DynamicTest.dynamicTest("Test RBAC deleteWorkspaceFile : $role") {
                organization =
                    organizationApiService.registerOrganization(
                        mockOrganization(id = "id", roleName = defaultName))
                solution = solutionApiService.createSolution(organization.id!!, mockSolution())

                every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
                every { getCurrentAccountIdentifier(any()) } returns defaultName

                workspace =
                    workspaceApiService.createWorkspace(
                        organization.id!!, mockWorkspace(roleName = defaultName, role = role))

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    workspaceApiService.deleteWorkspaceFile(organization.id!!, workspace.id!!, "")
                  }
                } else {
                  assertDoesNotThrow {
                    workspaceApiService.deleteWorkspaceFile(organization.id!!, workspace.id!!, "")
                  }
                }
              }
            }

    @TestFactory
    fun `test RBAC getWorkspacePermissions`() =
        mapOf(
                ROLE_VIEWER to false,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to false,
                ROLE_USER to false,
                ROLE_NONE to false,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              DynamicTest.dynamicTest("Test RBAC getWorkspacePermissions : $role") {
                organization =
                    organizationApiService.registerOrganization(
                        mockOrganization(id = "id", roleName = defaultName))
                solution = solutionApiService.createSolution(organization.id!!, mockSolution())

                every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
                every { getCurrentAccountIdentifier(any()) } returns defaultName

                workspace =
                    workspaceApiService.createWorkspace(
                        organization.id!!, mockWorkspace(roleName = defaultName, role = role))

                assertDoesNotThrow {
                  workspaceApiService.getWorkspacePermissions(
                      organization.id!!, workspace.id!!, ROLE_USER)
                }
              }
            }

    @TestFactory
    fun `test RBAC getWorkspaceSecurity`() =
        mapOf(
                ROLE_VIEWER to false,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to true,
                ROLE_USER to false,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              DynamicTest.dynamicTest("Test RBAC getWorkspaceSecurity : $role") {
                organization =
                    organizationApiService.registerOrganization(
                        mockOrganization(id = "id", roleName = defaultName))
                solution = solutionApiService.createSolution(organization.id!!, mockSolution())

                every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
                every { getCurrentAccountIdentifier(any()) } returns defaultName

                workspace =
                    workspaceApiService.createWorkspace(
                        organization.id!!, mockWorkspace(roleName = defaultName, role = role))

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    workspaceApiService.getWorkspaceSecurity(organization.id!!, workspace.id!!)
                  }
                } else {
                  assertDoesNotThrow {
                    workspaceApiService.getWorkspaceSecurity(organization.id!!, workspace.id!!)
                  }
                }
              }
            }

    @TestFactory
    fun `test RBAC setWorkspaceDefaultSecurity`() =
        mapOf(
                ROLE_VIEWER to true,
                ROLE_EDITOR to true,
                ROLE_VALIDATOR to true,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              DynamicTest.dynamicTest("Test RBAC setWorkspaceDefaultSecurity : $role") {
                organization =
                    organizationApiService.registerOrganization(
                        mockOrganization(id = "id", roleName = defaultName))
                solution = solutionApiService.createSolution(organization.id!!, mockSolution())

                every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
                every { getCurrentAccountIdentifier(any()) } returns defaultName

                workspace =
                    workspaceApiService.createWorkspace(
                        organization.id!!, mockWorkspace(roleName = defaultName, role = role))

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    workspaceApiService.setWorkspaceDefaultSecurity(
                        organization.id!!, workspace.id!!, WorkspaceRole(ROLE_USER))
                  }
                } else {
                  assertDoesNotThrow {
                    workspaceApiService.setWorkspaceDefaultSecurity(
                        organization.id!!, workspace.id!!, WorkspaceRole(ROLE_USER))
                  }
                }
              }
            }

    @TestFactory
    fun `test RBAC addWorkspaceAccessControl`() =
        mapOf(
                ROLE_VIEWER to true,
                ROLE_EDITOR to true,
                ROLE_VALIDATOR to true,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              DynamicTest.dynamicTest("Test RBAC addWorkspaceAccessControl : $role") {
                organization =
                    organizationApiService.registerOrganization(
                        mockOrganization(id = "id", roleName = defaultName))
                solution = solutionApiService.createSolution(organization.id!!, mockSolution())

                every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
                every { getCurrentAccountIdentifier(any()) } returns defaultName

                workspace =
                    workspaceApiService.createWorkspace(
                        organization.id!!, mockWorkspace(roleName = defaultName, role = role))

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    workspaceApiService.addWorkspaceAccessControl(
                        organization.id!!, workspace.id!!, WorkspaceAccessControl("id", ROLE_USER))
                  }
                } else {
                  assertDoesNotThrow {
                    workspaceApiService.addWorkspaceAccessControl(
                        organization.id!!, workspace.id!!, WorkspaceAccessControl("id", ROLE_USER))
                  }
                }
              }
            }

    @TestFactory
    fun `test RBAC getWorkspaceAccessControl`() =
        mapOf(
                ROLE_VIEWER to false,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to true,
                ROLE_USER to false,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              DynamicTest.dynamicTest("Test RBAC getWorkspaceAccessControl : $role") {
                organization =
                    organizationApiService.registerOrganization(
                        mockOrganization(id = "id", roleName = defaultName))
                solution = solutionApiService.createSolution(organization.id!!, mockSolution())

                every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
                every { getCurrentAccountIdentifier(any()) } returns defaultName

                workspace =
                    workspaceApiService.createWorkspace(
                        organization.id!!, mockWorkspace(roleName = defaultName, role = role))

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    workspaceApiService.getWorkspaceAccessControl(
                        organization.id!!, workspace.id!!, defaultName)
                  }
                } else {
                  assertDoesNotThrow {
                    workspaceApiService.getWorkspaceAccessControl(
                        organization.id!!, workspace.id!!, defaultName)
                  }
                }
              }
            }

    @TestFactory
    fun `test RBAC removeWorkspaceAccessControl`() =
        mapOf(
                ROLE_VIEWER to true,
                ROLE_EDITOR to true,
                ROLE_VALIDATOR to true,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              DynamicTest.dynamicTest("Test RBAC removeWorkspaceAccessControl : $role") {
                organization =
                    organizationApiService.registerOrganization(
                        mockOrganization(id = "id", roleName = defaultName))
                solution = solutionApiService.createSolution(organization.id!!, mockSolution())

                every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
                every { getCurrentAccountIdentifier(any()) } returns defaultName

                workspace =
                    workspaceApiService.createWorkspace(
                        organization.id!!, mockWorkspace(roleName = defaultName, role = role))

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    workspaceApiService.removeWorkspaceAccessControl(
                        organization.id!!, workspace.id!!, "name")
                  }
                } else {
                  assertDoesNotThrow {
                    workspaceApiService.removeWorkspaceAccessControl(
                        organization.id!!, workspace.id!!, "name")
                  }
                }
              }
            }

    @TestFactory
    fun `test RBAC updateWorkspaceAccessControl`() =
        mapOf(
                ROLE_VIEWER to true,
                ROLE_EDITOR to true,
                ROLE_VALIDATOR to true,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              DynamicTest.dynamicTest("Test RBAC updateWorkspaceAccessControl : $role") {
                organization =
                    organizationApiService.registerOrganization(
                        mockOrganization(id = "id", roleName = defaultName))
                solution = solutionApiService.createSolution(organization.id!!, mockSolution())

                every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
                every { getCurrentAccountIdentifier(any()) } returns defaultName

                workspace =
                    workspaceApiService.createWorkspace(
                        organization.id!!, mockWorkspace(roleName = defaultName, role = role))

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    workspaceApiService.updateWorkspaceAccessControl(
                        organization.id!!, workspace.id!!, "name", WorkspaceRole(ROLE_ADMIN))
                  }
                } else {
                  assertDoesNotThrow {
                    workspaceApiService.updateWorkspaceAccessControl(
                        organization.id!!, workspace.id!!, "name", WorkspaceRole(ROLE_ADMIN))
                  }
                }
              }
            }

    @TestFactory
    fun `test RBAC getWorkspaceSecurityUsers`() =
        mapOf(
                ROLE_VIEWER to false,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to true,
                ROLE_USER to false,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              DynamicTest.dynamicTest("Test RBAC getWorkspaceSecurityUsers : $role") {
                organization =
                    organizationApiService.registerOrganization(
                        mockOrganization(id = "id", roleName = defaultName))
                solution = solutionApiService.createSolution(organization.id!!, mockSolution())

                every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
                every { getCurrentAccountIdentifier(any()) } returns defaultName

                workspace =
                    workspaceApiService.createWorkspace(
                        organization.id!!, mockWorkspace(roleName = defaultName, role = role))

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    workspaceApiService.getWorkspaceSecurityUsers(organization.id!!, workspace.id!!)
                  }
                } else {
                  assertDoesNotThrow {
                    workspaceApiService.getWorkspaceSecurityUsers(organization.id!!, workspace.id!!)
                  }
                }
              }
            }

    @TestFactory
    fun `test RBAC createSecret`() =
        mapOf(
                ROLE_VIEWER to false,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to true,
                ROLE_USER to false,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              DynamicTest.dynamicTest("Test RBAC createSecret : $role") {
                organization =
                    organizationApiService.registerOrganization(
                        mockOrganization(id = "id", roleName = defaultName))
                solution = solutionApiService.createSolution(organization.id!!, mockSolution())

                every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
                every { getCurrentAccountIdentifier(any()) } returns defaultName
                every { secretManagerMock.createOrReplaceSecret(any(), any(), any()) } returns Unit

                workspace =
                    workspaceApiService.createWorkspace(
                        organization.id!!, mockWorkspace(roleName = defaultName, role = role))

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    workspaceApiService.getWorkspaceSecurityUsers(organization.id!!, workspace.id!!)
                  }
                } else {
                  assertDoesNotThrow {
                    workspaceApiService.createSecret(
                        organization.id!!, workspace.id!!, WorkspaceSecret(""))
                  }
                }
              }
            }

    @TestFactory
    fun `test RBAC importWorkspace`() =
        mapOf(
                ROLE_VIEWER to false,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to false,
                ROLE_USER to false,
                ROLE_NONE to false,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              DynamicTest.dynamicTest("Test RBAC importWorkspace : $role") {
                organization =
                    organizationApiService.registerOrganization(
                        mockOrganization(id = "id", roleName = defaultName))
                solution = solutionApiService.createSolution(organization.id!!, mockSolution())

                every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
                every { getCurrentAccountIdentifier(any()) } returns defaultName

                assertDoesNotThrow {
                  workspaceApiService.importWorkspace(organization.id!!, mockWorkspace())
                }
              }
            }
  }

  fun mockOrganization(
      id: String,
      roleName: String = CONNECTED_ADMIN_USER,
      role: String = ROLE_ADMIN
  ): Organization {
    return Organization(
        id = UUID.randomUUID().toString(),
        name = "Organization Name",
        ownerId = "my.account-tester@cosmotech.com",
        security =
            OrganizationSecurity(
                default = ROLE_NONE,
                accessControlList =
                    mutableListOf(
                        OrganizationAccessControl(id = roleName, role = role),
                        OrganizationAccessControl("userLambda", "viewer"))))
  }

  fun mockSolution(organizationId: String = organization.id!!): Solution {
    return Solution(
        id = "solutionId",
        key = UUID.randomUUID().toString(),
        name = "My solution",
        organizationId = organizationId,
        ownerId = "ownerId")
  }

  fun mockWorkspace(
      organizationId: String = organization.id!!,
      solutionId: String = solution.id!!,
      name: String = "name",
      roleName: String = CONNECTED_ADMIN_USER,
      role: String = ROLE_ADMIN
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
                        WorkspaceAccessControl(id = roleName, role = role),
                        WorkspaceAccessControl(name, "viewer"))))
  }
}
