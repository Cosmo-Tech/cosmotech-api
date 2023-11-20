// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.worskpace.service

import com.azure.storage.blob.BlobServiceClient
import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.rbac.ROLE_ADMIN
import com.cosmotech.api.rbac.ROLE_EDITOR
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.rbac.ROLE_VIEWER
import com.cosmotech.api.tests.CsmRedisTestBase
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
import com.cosmotech.solution.domain.SolutionAccessControl
import com.cosmotech.solution.domain.SolutionSecurity
import com.cosmotech.workspace.api.WorkspaceApiService
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceAccessControl
import com.cosmotech.workspace.domain.WorkspaceRole
import com.cosmotech.workspace.domain.WorkspaceSecurity
import com.cosmotech.workspace.domain.WorkspaceSolution
import com.redis.om.spring.RediSearchIndexer
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockkStatic
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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

@ActiveProfiles(profiles = ["workspace-test"])
@ExtendWith(MockKExtension::class)
@ExtendWith(SpringExtension::class)
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Suppress("FunctionName")
class WorkspaceServiceIntegrationTest : CsmRedisTestBase() {
  val TEST_USER_MAIL = "testuser@mail.fr"
  val CONNECTED_ADMIN_USER = "test.admin@cosmotech.com"
  val CONNECTED_DEFAULT_USER = "test.user@cosmotech.com"
  private val logger = LoggerFactory.getLogger(WorkspaceServiceIntegrationTest::class.java)

  @MockK(relaxed = true) private lateinit var azureStorageBlobServiceClient: BlobServiceClient
  @MockK private lateinit var secretManagerMock: SecretManager

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

    organization = mockOrganization("Organization test")
    organizationSaved = organizationApiService.registerOrganization(organization)

    solution = mockSolution(organizationSaved.id!!)
    solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

    workspace = mockWorkspace(organizationSaved.id!!, solutionSaved.id!!, "Workspace")
    workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
  }

  @Test
  fun `test CRUD operations on Workspace as User Admin`() {

    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")
    every { secretManagerMock.deleteSecret(any(), any()) } returns Unit

    logger.info("should create a second new workspace")
    val workspace2 = mockWorkspace(organizationSaved.id!!, solutionSaved.id!!, "Workspace 2")
    val workspaceRegistered2 =
        workspaceApiService.createWorkspace(organizationSaved.id!!, workspace2)
    val workspaceRetrieved =
        workspaceApiService.findWorkspaceById(organizationSaved.id!!, workspaceSaved.id!!)
    assertEquals(workspaceSaved, workspaceRetrieved)

    logger.info("should find all workspaces and assert there are 2")
    val workspacesList: List<Workspace> =
        workspaceApiService.findAllWorkspaces(organizationSaved.id!!, null, null)
    assertTrue(workspacesList.size == 2)

    logger.info("should update the name of the first workspace")
    val updatedWorkspace =
        workspaceApiService.updateWorkspace(
            organizationSaved.id!!,
            workspaceSaved.id!!,
            workspaceSaved.copy(name = "Workspace 1 updated", organizationId = null))
    assertEquals("Workspace 1 updated", updatedWorkspace.name)
    assertEquals(workspaceSaved.organizationId, updatedWorkspace.organizationId)

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

    every { getCurrentAccountIdentifier(any()) } returns "userLambda"

    logger.info("should not create a new workspace")
    val workspace2 = mockWorkspace(organizationSaved.id!!, solutionSaved.id!!, "Workspace 2")
    assertThrows<CsmAccessForbiddenException> {
      workspaceApiService.createWorkspace(organizationSaved.id!!, workspace2)
    }

    logger.info("should not retrieve a workspace")
    assertThrows<CsmAccessForbiddenException> {
      workspaceApiService.findWorkspaceById(organizationSaved.id!!, workspaceSaved.id!!)
    }

    logger.info("should not find all workspaces")
    val workspacesList: List<Workspace> =
        workspaceApiService.findAllWorkspaces(organizationSaved.id!!, null, null)
    assertTrue(workspacesList.isEmpty())

    logger.info("should not update a workspace")
    assertThrows<CsmAccessForbiddenException> {
      workspaceApiService.updateWorkspace(
          organizationSaved.id!!,
          workspaceSaved.id!!,
          workspaceSaved.copy(name = "Workspace 1 updated"))
    }

    logger.info("should not delete a workspace")
    assertThrows<CsmAccessForbiddenException> {
      workspaceApiService.deleteWorkspace(organizationSaved.id!!, workspaceSaved.id!!)
    }
  }

  @Test
  fun `test find All Workspaces with different pagination params`() {

    val workspaceNumber = 20
    val defaultPageSize = csmPlatformProperties.twincache.workspace.defaultPageSize
    val expectedSize = 15
    IntRange(1, workspaceNumber - 1).forEach {
      val workspace = mockWorkspace(organizationSaved.id!!, solutionSaved.id!!, "w-workspace-$it")
      workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
    }
    logger.info("should find all workspaces and assert there are $workspaceNumber")
    var workspacesList = workspaceApiService.findAllWorkspaces(organizationSaved.id!!, null, null)
    assertEquals(workspaceNumber, workspacesList.size)

    logger.info("should find all workspaces and assert it equals defaultPageSize: $defaultPageSize")
    workspacesList = workspaceApiService.findAllWorkspaces(organizationSaved.id!!, 0, null)
    assertEquals(defaultPageSize, workspacesList.size)

    logger.info("should find all workspaces and assert there are expected size: $expectedSize")
    workspacesList = workspaceApiService.findAllWorkspaces(organizationSaved.id!!, 0, expectedSize)
    assertEquals(expectedSize, workspacesList.size)

    logger.info("should find all workspaces and assert it returns the  second / last page")
    workspacesList = workspaceApiService.findAllWorkspaces(organizationSaved.id!!, 1, expectedSize)
    assertEquals(workspaceNumber - expectedSize, workspacesList.size)
  }

  @Test
  fun `test find All Workspaces with wrong pagination params`() {
    logger.info("should throw IllegalArgumentException when page and size are zero")
    assertThrows<IllegalArgumentException> {
      workspaceApiService.findAllWorkspaces(organizationSaved.id!!, 0, 0)
    }
    logger.info("should throw IllegalArgumentException when page is negative")
    assertThrows<IllegalArgumentException> {
      workspaceApiService.findAllWorkspaces(organizationSaved.id!!, -1, 1)
    }
    logger.info("should throw IllegalArgumentException when size is negative")
    assertThrows<IllegalArgumentException> {
      workspaceApiService.findAllWorkspaces(organizationSaved.id!!, 0, -1)
    }
  }

  @Test
  fun `test RBAC WorkspaceSecurity as User Admin`() {

    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")

    logger.info("should get default security with role NONE")
    val workspaceSecurity =
        workspaceApiService.getWorkspaceSecurity(organizationSaved.id!!, workspaceSaved.id!!)
    assertEquals(ROLE_NONE, workspaceSecurity.default)

    logger.info("should set default security with role VIEWER")
    val workspaceRole = WorkspaceRole(ROLE_VIEWER)
    val workspaceSecurityRegistered =
        workspaceApiService.setWorkspaceDefaultSecurity(
            organizationSaved.id!!, workspaceSaved.id!!, workspaceRole)
    assertEquals(workspaceRole.role, workspaceSecurityRegistered.default)
  }

  /* @Test
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
  }*/

  @Test
  fun `test RBAC as User Unauthorized`() {
    every { getCurrentAccountIdentifier(any()) } returns "userLambda"

    assertEquals(0, workspaceApiService.findAllWorkspaces(organizationSaved.id!!, null, null).size)
  }

  @Test
  fun `test RBAC AccessControls on Workspace as User Admin`() {

    logger.info("should add a new access control")
    val workspaceAccessControl = WorkspaceAccessControl(TEST_USER_MAIL, ROLE_VIEWER)
    var workspaceAccessControlRegistered =
        workspaceApiService.addWorkspaceAccessControl(
            organizationSaved.id!!, workspaceSaved.id!!, workspaceAccessControl)
    assertEquals(workspaceAccessControl, workspaceAccessControlRegistered)

    logger.info("should get the access control")
    workspaceAccessControlRegistered =
        workspaceApiService.getWorkspaceAccessControl(
            organizationSaved.id!!, workspaceSaved.id!!, TEST_USER_MAIL)
    assertEquals(workspaceAccessControl, workspaceAccessControlRegistered)

    logger.info("should update the access control")
    workspaceAccessControlRegistered =
        workspaceApiService.updateWorkspaceAccessControl(
            organizationSaved.id!!, workspaceSaved.id!!, TEST_USER_MAIL, WorkspaceRole(ROLE_EDITOR))
    assertEquals(ROLE_EDITOR, workspaceAccessControlRegistered.role)

    logger.info("should get the list of users and assert there are 3")
    val userList =
        workspaceApiService.getWorkspaceSecurityUsers(organizationSaved.id!!, workspaceSaved.id!!)
    assertEquals(3, userList.size)

    logger.info("should remove the access control")
    workspaceApiService.removeWorkspaceAccessControl(
        organizationSaved.id!!, workspaceSaved.id!!, TEST_USER_MAIL)
    assertThrows<CsmResourceNotFoundException> {
      workspaceAccessControlRegistered =
          workspaceApiService.getWorkspaceAccessControl(
              organizationSaved.id!!, workspaceSaved.id!!, TEST_USER_MAIL)
    }
  }

  @Test
  fun `test RBAC AccessControls on Workspace as User Unauthorized`() {

    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_DEFAULT_USER

    logger.info("should throw CsmAccessForbiddenException when adding a new access control")
    val workspaceAccessControl = WorkspaceAccessControl(TEST_USER_MAIL, ROLE_VIEWER)
    assertThrows<CsmAccessForbiddenException> {
      workspaceApiService.addWorkspaceAccessControl(
          organizationSaved.id!!, workspaceSaved.id!!, workspaceAccessControl)
    }

    logger.info("should throw CsmAccessForbiddenException when getting the access control")
    assertThrows<CsmAccessForbiddenException> {
      workspaceApiService.getWorkspaceAccessControl(
          organizationSaved.id!!, workspaceSaved.id!!, "userLambda")
    }

    logger.info("should throw CsmAccessForbiddenException when updating the access control")
    assertThrows<CsmAccessForbiddenException> {
      workspaceApiService.updateWorkspaceAccessControl(
          organizationSaved.id!!, workspaceSaved.id!!, TEST_USER_MAIL, WorkspaceRole(ROLE_VIEWER))
    }

    logger.info("should throw CsmAccessForbiddenException when getting the list of users")
    assertThrows<CsmAccessForbiddenException> {
      workspaceApiService.getWorkspaceSecurityUsers(organizationSaved.id!!, workspaceSaved.id!!)
    }

    logger.info("should throw CsmAccessForbiddenException when removing the access control")
    assertThrows<CsmAccessForbiddenException> {
      workspaceApiService.removeWorkspaceAccessControl(
          organizationSaved.id!!, workspaceSaved.id!!, TEST_USER_MAIL)
    }
  }

  @Test
  fun `access control list shouldn't contain more than one time each user`() {
    organizationSaved =
        organizationApiService.registerOrganization(mockOrganization("organization"))
    solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, mockSolution())
    logger.info("testing workspace creation")
    val brokenWorkspace =
        Workspace(
            name = "workspace",
            key = "key",
            solution = WorkspaceSolution(solutionSaved.id!!),
            security =
                WorkspaceSecurity(
                    default = ROLE_NONE,
                    accessControlList =
                        mutableListOf(
                            WorkspaceAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                            WorkspaceAccessControl(CONNECTED_ADMIN_USER, ROLE_EDITOR))))
    assertThrows<IllegalArgumentException> {
      workspaceApiService.createWorkspace(organizationSaved.id!!, brokenWorkspace)
    }

    val workingWorkspace = mockWorkspace()
    workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id!!, workingWorkspace)

    logger.info("testing adding access control")
    assertThrows<IllegalArgumentException> {
      workspaceApiService.addWorkspaceAccessControl(
          organizationSaved.id!!,
          workspaceSaved.id!!,
          WorkspaceAccessControl(CONNECTED_ADMIN_USER, ROLE_EDITOR))
    }
  }

  fun mockOrganization(
      id: String,
      userName: String = CONNECTED_ADMIN_USER,
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
                        OrganizationAccessControl(id = userName, role = role),
                        OrganizationAccessControl("userLambda", "viewer"))))
  }

  fun mockSolution(organizationId: String = organizationSaved.id!!): Solution {
    return Solution(
        id = "solutionId",
        key = UUID.randomUUID().toString(),
        name = "My solution",
        organizationId = organizationId,
        ownerId = "ownerId",
        security =
            SolutionSecurity(
                default = ROLE_NONE,
                accessControlList =
                    mutableListOf(
                        SolutionAccessControl(id = CONNECTED_ADMIN_USER, role = ROLE_ADMIN))))
  }

  fun mockWorkspace(
      organizationId: String = organizationSaved.id!!,
      solutionId: String = solutionSaved.id!!,
      name: String = "name",
      userName: String = CONNECTED_ADMIN_USER,
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
                        WorkspaceAccessControl(id = userName, role = role),
                        WorkspaceAccessControl(CONNECTED_DEFAULT_USER, "viewer"))))
  }
}
