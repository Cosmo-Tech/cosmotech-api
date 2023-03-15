// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.workspace.service

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.events.CsmEventPublisher
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.id.CsmIdGenerator
import com.cosmotech.api.rbac.CsmAdmin
import com.cosmotech.api.rbac.CsmRbac
import com.cosmotech.api.rbac.ROLE_ADMIN
import com.cosmotech.api.rbac.ROLE_EDITOR
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.rbac.ROLE_USER
import com.cosmotech.api.rbac.ROLE_VALIDATOR
import com.cosmotech.api.rbac.ROLE_VIEWER
import com.cosmotech.api.utils.ResourceScanner
import com.cosmotech.api.utils.SecretManager
import com.cosmotech.api.utils.getCurrentAuthenticatedMail
import com.cosmotech.api.utils.getCurrentAuthenticatedRoles
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationSecurity
import com.cosmotech.organization.repository.OrganizationRepository
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.solution.domain.Solution
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceAccessControl
import com.cosmotech.workspace.domain.WorkspaceSecurity
import com.cosmotech.workspace.domain.WorkspaceSolution
import com.cosmotech.workspace.repository.WorkspaceRepository
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import java.util.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.testcontainers.shaded.org.bouncycastle.asn1.x500.style.RFC4519Style.name

const val ORGANIZATION_ID = "O-AbCdEf123"
const val WORKSPACE_ID = "W-BcDeFg123"
const val CONNECTED_ADMIN_USER = "test.admin@cosmotech.com"
const val CONNECTED_DEFAULT_USER = "test.user@cosmotech.com"

@ExtendWith(MockKExtension::class)
class WorkspaceServiceImplTests {

  @MockK private lateinit var solutionService: SolutionApiService
  @RelaxedMockK private lateinit var organizationService: OrganizationApiService
  @Suppress("unused") @MockK private lateinit var secretManager: SecretManager

  @Suppress("unused") @MockK private var eventPublisher: CsmEventPublisher = mockk(relaxed = true)
  @Suppress("unused") @MockK private var idGenerator: CsmIdGenerator = mockk(relaxed = true)

  @Suppress("unused")
  @MockK
  private var csmPlatformProperties: CsmPlatformProperties = mockk(relaxed = true)
  @Suppress("unused") @MockK private var csmAdmin: CsmAdmin = CsmAdmin(csmPlatformProperties)

  @Suppress("unused") @SpyK private var csmRbac: CsmRbac = CsmRbac(csmPlatformProperties, csmAdmin)

  @Suppress("unused") @RelaxedMockK private lateinit var resourceScanner: ResourceScanner

  @Suppress("unused")
  @MockK
  private var organizationRepository: OrganizationRepository = mockk(relaxed = true)
  @Suppress("unused")
  @MockK
  private var workspaceRepository: WorkspaceRepository = mockk(relaxed = true)

  @SpyK @InjectMockKs private lateinit var workspaceServiceImpl: WorkspaceServiceImpl

  @BeforeEach
  fun setUp() {
    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
    every { getCurrentAuthenticatedMail(any()) } returns CONNECTED_DEFAULT_USER
    every { getCurrentAuthenticatedUserName() } returns "my.account-tester"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf()

    val csmPlatformPropertiesUpload = mockk<CsmPlatformProperties.Upload>()
    val csmPlatformPropertiesAuthorizedMimeTypes =
        mockk<CsmPlatformProperties.Upload.AuthorizedMimeTypes>()
    every { csmPlatformPropertiesAuthorizedMimeTypes.workspaces } returns
        listOf(
            "application/zip",
            "application/xml",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/x-tika-ooxml",
            "text/csv",
            "text/plain",
            "text/x-yaml",
        )
    every { csmPlatformPropertiesUpload.authorizedMimeTypes } returns
        csmPlatformPropertiesAuthorizedMimeTypes

    every { csmPlatformProperties.rbac.enabled } returns true
    every { csmPlatformProperties.upload } returns csmPlatformPropertiesUpload

    MockKAnnotations.init(this)
  }

  @Test
  fun `should reject creation request if solution ID is not valid`() {

    val organization = mockOrganization(ORGANIZATION_ID)
    organization.security = OrganizationSecurity(ROLE_ADMIN, mutableListOf())
    every { organizationService.findOrganizationById(ORGANIZATION_ID) } returns organization
    val workspace =
        Workspace(
            key = "my-workspace-key",
            name = "my workspace name",
            solution = WorkspaceSolution(solutionId = "SOL-my-solution-id"))
    workspace.security = WorkspaceSecurity(ROLE_ADMIN, mutableListOf())
    every { solutionService.findSolutionById(ORGANIZATION_ID, any()) } throws
        CsmResourceNotFoundException("Solution not found")
    assertThrows<CsmResourceNotFoundException> {
      workspaceServiceImpl.createWorkspace(ORGANIZATION_ID, workspace)
    }
    verify(exactly = 0) { workspaceRepository.save(ofType(Workspace::class)) }
    confirmVerified(workspaceRepository)
  }

  @Test
  fun `should reject update request if solution ID is not valid`() {
    val workspace =
        Workspace(
            id = WORKSPACE_ID,
            key = "my-workspace-key",
            name = "my workspace name",
            solution = WorkspaceSolution(solutionId = "SOL-my-solution-id"))
    workspace.security = WorkspaceSecurity(ROLE_ADMIN, mutableListOf())
    every { workspaceServiceImpl.findWorkspaceByIdNoSecurity(WORKSPACE_ID) } returns workspace
    every { solutionService.findSolutionById(ORGANIZATION_ID, any()) } throws
        CsmResourceNotFoundException("Solution not found")
    assertThrows<CsmResourceNotFoundException> {
      workspaceServiceImpl.updateWorkspace(
          ORGANIZATION_ID,
          WORKSPACE_ID,
          Workspace(
              key = "my-workspace-key-renamed",
              name = "my workspace name (renamed)",
              solution = WorkspaceSolution(solutionId = "SOL-my-new-solution-id")))
    }

    verify(exactly = 0) { workspaceRepository.save(ofType(Workspace::class)) }
    confirmVerified(workspaceRepository)
  }

  @TestFactory
  fun `test RBAC read worskpace`() =
      mapOf(
          ROLE_VIEWER to false,
          ROLE_EDITOR to false,
          ROLE_ADMIN to false,
          ROLE_VALIDATOR to true,
          ROLE_USER to false,
          ROLE_NONE to true)
          .map { (role, shouldThrow) ->
            rbacTest("Test RBAC read workspace: $role", role, shouldThrow) {
              every { workspaceRepository.findById(any()) } returns Optional.of(it.workspace)
              workspaceServiceImpl.findWorkspaceById(it.organization.id!!, it.workspace.id!!)
            }
          }

  private fun rbacTest(
      testName: String,
      role: String,
      shouldThrow: Boolean,
      testLambda: (ctx: WorkspaceTestContext) -> Unit
  ): DynamicTest? {
    val organization = mockOrganization("o-org-id", CONNECTED_DEFAULT_USER, role)
    val solution = mockSolution(organization.id!!)
    val workspace =
        mockWorkspace(organization.id!!, solution.id!!, "Workspace", CONNECTED_DEFAULT_USER, role)
    return DynamicTest.dynamicTest(testName) {
      if (shouldThrow) {
        assertThrows<CsmAccessForbiddenException> {
          testLambda(WorkspaceTestContext(organization, solution, workspace))
        }
      } else {
        assertDoesNotThrow { testLambda(WorkspaceTestContext(organization, solution, workspace)) }
      }
    }
  }

  data class WorkspaceTestContext(
      val organization: Organization,
      val solution: Solution,
      val workspace: Workspace
  )

  fun mockOrganization(
      id: String,
      roleName: String = CONNECTED_ADMIN_USER,
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
                        OrganizationAccessControl(id = roleName, role = role),
                        OrganizationAccessControl("userLambda", "viewer"))))
  }

  fun mockSolution(organizationId: String): Solution {
    return Solution(
        id = "solutionId",
        key = UUID.randomUUID().toString(),
        name = "My solution",
        organizationId = organizationId,
        ownerId = "ownerId")
  }

  private fun mockWorkspace(
      organizationId: String,
      solutionId: String,
      workspaceName: String,
      roleName: String = CONNECTED_ADMIN_USER,
      role: String = ROLE_ADMIN
  ): Workspace {
    return Workspace(
        id = UUID.randomUUID().toString(),
        key = UUID.randomUUID().toString(),
        name = workspaceName,
        solution =
            WorkspaceSolution(
                solutionId = solutionId,
            ),
        organizationId = organizationId,
        ownerId = "ownerId",
        security =
            WorkspaceSecurity(
                default = ROLE_NONE,
                accessControlList =
                    mutableListOf(
                        WorkspaceAccessControl(id = roleName, role = role),
                        WorkspaceAccessControl("2$name", "viewer"))))
  }
}
