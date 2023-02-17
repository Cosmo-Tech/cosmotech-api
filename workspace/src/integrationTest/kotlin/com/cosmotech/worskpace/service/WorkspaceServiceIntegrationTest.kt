// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.worskpace.service // Copyright (c) Cosmo Tech.
// Licensed under the MIT license.

import com.azure.storage.blob.BlobServiceClient
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.rbac.ROLE_EDITOR
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.rbac.ROLE_VIEWER
import com.cosmotech.api.tests.CsmRedisTestBase
import com.cosmotech.api.utils.getCurrentAuthenticatedMail
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

const val CONNECTED_ADMIN_USER = "test.admin@cosmotech.com"
const val CONNECTED_READER_USER = "test.user@cosmotech.com"
const val FAKE_MAIL = "fake@mail.fr"

@ActiveProfiles(profiles = ["workspace-test"])
@ExtendWith(MockKExtension::class)
@ExtendWith(SpringExtension::class)
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WorkspaceServiceIntegrationTest : CsmRedisTestBase() {

  private val logger = LoggerFactory.getLogger(WorkspaceServiceIntegrationTest::class.java)

  @MockK(relaxed = true) private lateinit var azureStorageBlobServiceClient: BlobServiceClient

  @Autowired lateinit var rediSearchIndexer: RediSearchIndexer
  @Autowired lateinit var organizationApiService: OrganizationApiService
  @Autowired lateinit var solutionApiService: SolutionApiService
  @Autowired lateinit var workspaceApiService: WorkspaceApiService

  lateinit var organization: Organization
  lateinit var solution: Solution
  lateinit var workspace: Workspace

  lateinit var organizationRegistered: Organization
  lateinit var solutionRegistered: Solution
  lateinit var workspaceRegistered: Workspace

  @BeforeEach
  fun setUp() {
    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
    every { getCurrentAuthenticatedMail(any()) } returns CONNECTED_ADMIN_USER
    every { getCurrentAuthenticatedUserName() } returns "test.user"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("user")

    ReflectionTestUtils.setField(
        workspaceApiService, "azureStorageBlobServiceClient", azureStorageBlobServiceClient)

    rediSearchIndexer.createIndexFor(Organization::class.java)
    rediSearchIndexer.createIndexFor(Workspace::class.java)

    organization = mockOrganization("Organization test")
    organizationRegistered = organizationApiService.registerOrganization(organization)

    solution = mockSolution(organizationRegistered.id!!)
    solutionRegistered = solutionApiService.createSolution(organizationRegistered.id!!, solution)

    workspace = mockWorkspace(organizationRegistered.id!!, solutionRegistered.id!!, "Workspace")
    workspaceRegistered =
        workspaceApiService.createWorkspace(organizationRegistered.id!!, workspace)
  }

  @Test
  fun `test CRUD operations on Workspace as User Admin`() {

    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")

    val workspace2 =
        mockWorkspace(organizationRegistered.id!!, solutionRegistered.id!!, "Workspace 2")
    val workspaceRegistered2 =
        workspaceApiService.createWorkspace(organizationRegistered.id!!, workspace2)
    logger.info("New workspaces created : ${workspaceRegistered.id} and ${workspaceRegistered2.id}")
    val workspaceRetrieved =
        workspaceApiService.findWorkspaceById(organizationRegistered.id!!, workspaceRegistered.id!!)
    assertEquals(workspaceRegistered, workspaceRetrieved)

    val workspacesList: List<Workspace> =
        workspaceApiService.findAllWorkspaces(organizationRegistered.id!!)
    assertTrue(workspacesList.size == 2)

    val updatedWorkspace =
        workspaceApiService.updateWorkspace(
            organizationRegistered.id!!,
            workspaceRegistered.id!!,
            workspaceRegistered.copy(name = "Workspace 1 updated"))
    assertEquals("Workspace 1 updated", updatedWorkspace.name)

    workspaceApiService.deleteWorkspace(organizationRegistered.id!!, workspaceRegistered.id!!)
    val workspacesListAfterDelete: List<Workspace> =
        workspaceApiService.findAllWorkspaces(organizationRegistered.id!!)
    assertTrue(workspacesListAfterDelete.size == 1)
  }

  @Test
  fun `test CRUD operations on Workspace as User Unauthorized`() {

    every { getCurrentAuthenticatedMail(any()) } returns FAKE_MAIL

    // User can't create a workspace
    val workspace2 =
        mockWorkspace(organizationRegistered.id!!, solutionRegistered.id!!, "Workspace 2")
    assertThrows<CsmAccessForbiddenException> {
      workspaceApiService.createWorkspace(organizationRegistered.id!!, workspace2)
    }
    // User can't retrieve a workspace
    assertThrows<CsmAccessForbiddenException> {
      workspaceApiService.findWorkspaceById(organizationRegistered.id!!, workspaceRegistered.id!!)
    }
    // User can't retrieve all workspaces
    val workspacesList: List<Workspace> =
        workspaceApiService.findAllWorkspaces(organizationRegistered.id!!)
    assertTrue(workspacesList.isEmpty())
    // User can't update a workspace
    assertThrows<CsmAccessForbiddenException> {
      workspaceApiService.updateWorkspace(
          organizationRegistered.id!!,
          workspaceRegistered.id!!,
          workspaceRegistered.copy(name = "Workspace 1 updated"))
    }
    // User can't delete a workspace
    assertThrows<CsmAccessForbiddenException> {
      workspaceApiService.deleteWorkspace(organizationRegistered.id!!, workspaceRegistered.id!!)
    }
  }

  @Test
  fun `test RBAC WorkspaceSecurity as User Admin`() {

    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")

    // Test default security
    val workspaceSecurity =
        workspaceApiService.getWorkspaceSecurity(
            organizationRegistered.id!!, workspaceRegistered.id!!)
    assertEquals(ROLE_NONE, workspaceSecurity.default)

    val workspaceRole = WorkspaceRole(ROLE_VIEWER)
    val workspaceSecurityRegistered =
        workspaceApiService.setWorkspaceDefaultSecurity(
            organizationRegistered.id!!, workspaceRegistered.id!!, workspaceRole)
    assertEquals(workspaceRole.role, workspaceSecurityRegistered.default)
  }

  @Test
  fun `test RBAC WorkspaceSecurity as User Unauthorized`() {

    every { getCurrentAuthenticatedMail(any()) } returns CONNECTED_READER_USER

    // Test default security
    assertThrows<CsmAccessForbiddenException> {
      workspaceApiService.getWorkspaceSecurity(
          organizationRegistered.id!!, workspaceRegistered.id!!)
    }

    val workspaceRole = WorkspaceRole(ROLE_VIEWER)
    assertThrows<CsmAccessForbiddenException> {
      workspaceApiService.setWorkspaceDefaultSecurity(
          organizationRegistered.id!!, workspaceRegistered.id!!, workspaceRole)
    }
  }

  @Test
  fun `test RBAC AccessControls on Workspace as User Admin`() {

    val workspaceAccessControl = WorkspaceAccessControl(FAKE_MAIL, ROLE_VIEWER)
    var workspaceAccessControlRegistered =
        workspaceApiService.addWorkspaceAccessControl(
            organizationRegistered.id!!, workspaceRegistered.id!!, workspaceAccessControl)
    assertEquals(workspaceAccessControl, workspaceAccessControlRegistered)

    workspaceAccessControlRegistered =
        workspaceApiService.getWorkspaceAccessControl(
            organizationRegistered.id!!, workspaceRegistered.id!!, FAKE_MAIL)
    assertEquals(workspaceAccessControl, workspaceAccessControlRegistered)

    workspaceAccessControlRegistered =
        workspaceApiService.updateWorkspaceAccessControl(
            organizationRegistered.id!!,
            workspaceRegistered.id!!,
            FAKE_MAIL,
            WorkspaceRole(ROLE_EDITOR))
    assertEquals(ROLE_EDITOR, workspaceAccessControlRegistered.role)

    workspaceApiService.removeWorkspaceAccessControl(
        organizationRegistered.id!!, workspaceRegistered.id!!, FAKE_MAIL)
    assertThrows<CsmResourceNotFoundException> {
      workspaceAccessControlRegistered =
          workspaceApiService.getWorkspaceAccessControl(
              organizationRegistered.id!!, workspaceRegistered.id!!, FAKE_MAIL)
    }
  }

  @Test
  fun `test RBAC AccessControls on Workspace as User Unauthorized`() {

    every { getCurrentAuthenticatedMail(any()) } returns CONNECTED_READER_USER

    val workspaceAccessControl = WorkspaceAccessControl(FAKE_MAIL, ROLE_VIEWER)
    assertThrows<CsmAccessForbiddenException> {
      workspaceApiService.addWorkspaceAccessControl(
          organizationRegistered.id!!, workspaceRegistered.id!!, workspaceAccessControl)
    }
    assertThrows<CsmAccessForbiddenException> {
      workspaceApiService.getWorkspaceAccessControl(
          organizationRegistered.id!!, workspaceRegistered.id!!, FAKE_MAIL)
    }
    assertThrows<CsmAccessForbiddenException> {
      workspaceApiService.updateWorkspaceAccessControl(
          organizationRegistered.id!!,
          workspaceRegistered.id!!,
          FAKE_MAIL,
          WorkspaceRole(ROLE_VIEWER))
    }
    assertThrows<CsmAccessForbiddenException> {
      workspaceApiService.removeWorkspaceAccessControl(
          organizationRegistered.id!!, workspaceRegistered.id!!, FAKE_MAIL)
    }
  }

  fun mockOrganization(id: String): Organization {
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
                        OrganizationAccessControl(id = CONNECTED_ADMIN_USER, role = "admin"))))
  }

  fun mockSolution(organizationId: String): Solution {
    return Solution(
        id = "solutionId",
        key = UUID.randomUUID().toString(),
        name = "My solution",
        organizationId = organizationId,
        ownerId = "ownerId")
  }

  fun mockWorkspace(organizationId: String, solutionId: String, name: String): Workspace {
    return Workspace(
        key = UUID.randomUUID().toString(),
        name = name,
        solution =
            WorkspaceSolution(
                solutionId = solutionId,
            ),
        organizationId = organizationId,
        ownerId = "ownerId",
    )
  }
}
