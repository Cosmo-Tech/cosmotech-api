// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.workspace.service

import com.cosmotech.common.config.CsmPlatformProperties
import com.cosmotech.common.events.CsmEventPublisher
import com.cosmotech.common.exceptions.CsmAccessForbiddenException
import com.cosmotech.common.exceptions.CsmResourceNotFoundException
import com.cosmotech.common.id.CsmIdGenerator
import com.cosmotech.common.rbac.CsmAdmin
import com.cosmotech.common.rbac.CsmRbac
import com.cosmotech.common.rbac.PERMISSION_CREATE_CHILDREN
import com.cosmotech.common.rbac.PERMISSION_READ
import com.cosmotech.common.rbac.ROLE_ADMIN
import com.cosmotech.common.rbac.ROLE_EDITOR
import com.cosmotech.common.rbac.ROLE_NONE
import com.cosmotech.common.rbac.ROLE_USER
import com.cosmotech.common.rbac.ROLE_VALIDATOR
import com.cosmotech.common.rbac.ROLE_VIEWER
import com.cosmotech.common.utils.ResourceScanner
import com.cosmotech.common.utils.getCurrentAccountGroups
import com.cosmotech.common.utils.getCurrentAccountIdentifier
import com.cosmotech.common.utils.getCurrentAuthenticatedRoles
import com.cosmotech.common.utils.getCurrentAuthenticatedUserName
import com.cosmotech.organization.OrganizationApiServiceInterface
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationEditInfo
import com.cosmotech.organization.domain.OrganizationSecurity
import com.cosmotech.organization.repository.OrganizationRepository
import com.cosmotech.organization.service.toGenericSecurity
import com.cosmotech.solution.SolutionApiServiceInterface
import com.cosmotech.solution.domain.RunTemplate
import com.cosmotech.solution.domain.RunTemplateParameter
import com.cosmotech.solution.domain.RunTemplateParameterGroup
import com.cosmotech.solution.domain.Solution
import com.cosmotech.solution.domain.SolutionAccessControl
import com.cosmotech.solution.domain.SolutionEditInfo
import com.cosmotech.solution.domain.SolutionSecurity
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceAccessControl
import com.cosmotech.workspace.domain.WorkspaceCreateRequest
import com.cosmotech.workspace.domain.WorkspaceEditInfo
import com.cosmotech.workspace.domain.WorkspaceRole
import com.cosmotech.workspace.domain.WorkspaceSecurity
import com.cosmotech.workspace.domain.WorkspaceSolution
import com.cosmotech.workspace.domain.WorkspaceUpdateRequest
import com.cosmotech.workspace.repository.WorkspaceRepository
import io.awspring.cloud.s3.S3Template
import io.mockk.MockKAnnotations
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.repository.findByIdOrNull
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.services.s3.S3Client

const val ORGANIZATION_ID = "o-AbCdEf1234"
const val WORKSPACE_ID = "w-BcDeFg1234"
const val CONNECTED_ADMIN_USER = "test.admin@cosmotech.com"
const val CONNECTED_DEFAULT_USER = "test.user@cosmotech.com"
const val S3_BUCKET_NAME = "test-bucket"

@ExtendWith(MockKExtension::class)
@Suppress("LargeClass")
class WorkspaceServiceImplTests {

  val defaultGroup = listOf("myTestGroup")

  @MockK private lateinit var solutionService: SolutionApiServiceInterface
  @RelaxedMockK private lateinit var organizationService: OrganizationApiServiceInterface

  @Suppress("unused") @RelaxedMockK private lateinit var s3Client: S3Client
  @RelaxedMockK private lateinit var s3Template: S3Template

  @RelaxedMockK private lateinit var workspaceFile: MultipartFile

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

  @InjectMockKs private lateinit var workspaceServiceImpl: WorkspaceServiceImpl

  @BeforeEach
  fun beforeEach() {
    mockkStatic("com.cosmotech.common.utils.SecurityUtilsKt")
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_DEFAULT_USER
    every { getCurrentAccountGroups(any()) } returns defaultGroup
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "my.account-tester"
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

    every { csmPlatformProperties.s3.bucketName } returns S3_BUCKET_NAME
    every { s3Template.objectExists(any(), any()) } returns false

    MockKAnnotations.init(this)
  }

  @Test
  fun `In uploadWorkspaceFile, filename is used if no destination set`() {
    val workspace = mockk<Workspace>(relaxed = true)
    every { workspace.id } returns WORKSPACE_ID
    every { workspace.name } returns "test workspace"
    every { workspace.security } returns WorkspaceSecurity(ROLE_ADMIN, mutableListOf())
    every { workspaceRepository.findBy(any(), any()) } returns Optional.of(workspace)

    val file = mockk<MultipartFile>(relaxed = true)
    every { file.originalFilename } returns "my_file.txt"

    val workspaceFile =
        workspaceServiceImpl.createWorkspaceFile(ORGANIZATION_ID, WORKSPACE_ID, file, false, null)
    assertNotNull(workspaceFile.fileName)
    assertEquals("my_file.txt", workspaceFile.fileName)
  }

  @Test
  fun `In uploadWorkspaceFile, filename is used if destination is blank`() {
    val workspace = mockk<Workspace>(relaxed = true)
    every { workspace.id } returns WORKSPACE_ID
    every { workspace.name } returns "test workspace"
    every { workspace.security } returns WorkspaceSecurity(ROLE_ADMIN, mutableListOf())
    every { organizationService.getVerifiedOrganization(ORGANIZATION_ID) } returns
        mockk<Organization>()
    every { workspaceRepository.findBy(any(), any()) } returns Optional.of(workspace)

    val file = mockk<MultipartFile>(relaxed = true)
    every { file.originalFilename } returns "my_file.txt"

    val workspaceFile =
        workspaceServiceImpl.createWorkspaceFile(ORGANIZATION_ID, WORKSPACE_ID, file, false, "  ")
    assertNotNull(workspaceFile.fileName)
    assertEquals("my_file.txt", workspaceFile.fileName)
  }

  @Test
  fun `In uploadWorkspaceFile, filename is appended to destination directory (ending with slash)`() {
    val workspace = mockk<Workspace>(relaxed = true)
    every { workspace.id } returns WORKSPACE_ID
    every { workspace.name } returns "test workspace"
    every { workspace.security } returns WorkspaceSecurity(ROLE_ADMIN, mutableListOf())
    every { workspaceRepository.findBy(any(), any()) } returns Optional.of(workspace)

    val file = mockk<MultipartFile>(relaxed = true)
    every { file.originalFilename } returns "my_file.txt"

    val workspaceFile =
        workspaceServiceImpl.createWorkspaceFile(
            ORGANIZATION_ID, WORKSPACE_ID, file, false, "my/destination/")
    assertNotNull(workspaceFile.fileName)
    assertEquals("my/destination/my_file.txt", workspaceFile.fileName)
  }

  @Test
  fun `In uploadWorkspaceFile, destination is used as is as file path if not ending with slash)`() {
    val workspace = mockk<Workspace>(relaxed = true)
    every { workspace.id } returns WORKSPACE_ID
    every { workspace.name } returns "test workspace"
    every { workspace.security } returns WorkspaceSecurity(ROLE_ADMIN, mutableListOf())
    every { workspaceRepository.findBy(any(), any()) } returns Optional.of(workspace)

    val file = mockk<MultipartFile>(relaxed = true)
    every { file.originalFilename } returns "my_file.txt"

    val workspaceFile =
        workspaceServiceImpl.createWorkspaceFile(
            ORGANIZATION_ID, WORKSPACE_ID, file, false, "my/destination/file")
    assertNotNull(workspaceFile.fileName)
    assertEquals("my/destination/file", workspaceFile.fileName)
  }

  @Test
  fun `In uploadWorkspaceFile, multiple slash characters in destination result in a single slash being used`() {
    val workspace = mockk<Workspace>(relaxed = true)
    every { workspace.id } returns WORKSPACE_ID
    every { workspace.name } returns "test workspace"
    every { workspace.security } returns WorkspaceSecurity(ROLE_ADMIN, mutableListOf())
    every { workspaceRepository.findBy(any(), any()) } returns Optional.of(workspace)

    val file = mockk<MultipartFile>(relaxed = true)
    every { file.originalFilename } returns "my_file.txt"

    val workspaceFile =
        workspaceServiceImpl.createWorkspaceFile(
            ORGANIZATION_ID, WORKSPACE_ID, file, false, "my//other/destination////////file")
    assertNotNull(workspaceFile.fileName)
    assertEquals("my/other/destination/file", workspaceFile.fileName)
  }

  @Test
  fun `Calling uploadWorkspaceFile is not allowed when destination contains double-dot`() {
    assertThrows<IllegalArgumentException> {
      workspaceServiceImpl.createWorkspaceFile(
          ORGANIZATION_ID, WORKSPACE_ID, mockk(), false, "my/../other/destination/../../file")
    }
  }

  @Test
  fun `Calling downloadWorkspaceFile is not allowed when filename contains double-dot`() {
    assertThrows<IllegalArgumentException> {
      workspaceServiceImpl.getWorkspaceFile(
          ORGANIZATION_ID, WORKSPACE_ID, "my/../../other/destination/file")
    }
  }

  @Test
  fun `should reject creation request if solution ID is not valid`() {
    val organization = mockOrganization()
    every { organizationService.getOrganization(ORGANIZATION_ID) } returns organization
    val workspace =
        mockWorkspaceCreateRequest(
            solutionId = "SOL-my-solution-id",
            workspaceName = "my workspace name",
            roleName = "",
            role = "")
    workspace.security = WorkspaceSecurity(ROLE_ADMIN, mutableListOf())
    every { solutionService.getSolution(ORGANIZATION_ID, any()) } throws
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
        mockWorkspace(
            solutionId = "SOL-my-solution-id",
            organizationId = ORGANIZATION_ID,
            workspaceName = "my workspace name",
            roleName = CONNECTED_DEFAULT_USER,
            role = ROLE_ADMIN)
    every { workspaceRepository.findBy(any(), WORKSPACE_ID) } returns Optional.of(workspace)
    every { solutionService.getSolution(ORGANIZATION_ID, any()) } throws
        CsmResourceNotFoundException("Solution not found")
    assertThrows<CsmResourceNotFoundException> {
      workspaceServiceImpl.updateWorkspace(
          ORGANIZATION_ID,
          WORKSPACE_ID,
          WorkspaceUpdateRequest(
              key = "my-workspace-key-renamed",
              name = "my workspace name (renamed)",
              solution = WorkspaceSolution(solutionId = "SOL-my-new-solution-id")))
    }

    verify(exactly = 0) { workspaceRepository.save(ofType(Workspace::class)) }
  }

  @TestFactory
  fun `test RBAC read workspace`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_ADMIN to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true)
          .map { (role, shouldThrow) ->
            rbacTest("Test RBAC read workspace: $role", role, shouldThrow) {
              every { workspaceRepository.findBy(any(), any()) } returns Optional.of(it.workspace)
              workspaceServiceImpl.getVerifiedWorkspace(it.organization.id, it.workspace.id)
            }
          }

  @TestFactory
  fun `test RBAC create workspace`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_ADMIN to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true)
          .map { (role, shouldThrow) ->
            rbacTest("Test RBAC create workspace: $role", role, shouldThrow) {
              every { organizationRepository.findByIdOrNull(any()) } returns it.organization
              listOf(PERMISSION_READ, PERMISSION_CREATE_CHILDREN).forEach { permission ->
                csmRbac.verify(
                    it.organization.security.toGenericSecurity(it.organization.id), permission)
              }
              every { workspaceRepository.save(any()) } returns it.workspace
              every { solutionService.getSolution(any(), any()) } returns it.solution
              workspaceServiceImpl.createWorkspace(
                  it.organization.id,
                  mockWorkspaceCreateRequest(
                      solutionId = it.solution.id, workspaceName = "workspace"))
            }
          }

  @TestFactory
  fun `test RBAC delete all workspace files`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_ADMIN to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true)
          .map { (role, shouldThrow) ->
            rbacTest("Test RBAC delete all workspace files: $role", role, shouldThrow) {
              every { workspaceRepository.findBy(any(), any()) } returns Optional.of(it.workspace)
              workspaceServiceImpl.deleteWorkspaceFiles(it.organization.id, it.workspace.id)
            }
          }

  @TestFactory
  fun `test RBAC update workspace`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_ADMIN to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true)
          .map { (role, shouldThrow) ->
            rbacTest("test RBAC update workspace: $role", role, shouldThrow) {
              every { solutionService.getSolution(any(), any()) } returns it.solution
              every { workspaceRepository.findBy(any(), any()) } returns Optional.of(it.workspace)
              every { workspaceRepository.save(any()) } returns it.workspace
              workspaceServiceImpl.updateWorkspace(
                  it.organization.id,
                  it.workspace.id,
                  WorkspaceUpdateRequest(key = it.workspace.key, name = "new name"))
            }
          }

  @TestFactory
  fun `test RBAC delete workspace`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_ADMIN to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true)
          .map { (role, shouldThrow) ->
            rbacTest("Test RBAC delete workspace: $role", role, shouldThrow) {
              every { workspaceRepository.findBy(any(), any()) } returns Optional.of(it.workspace)
              workspaceServiceImpl.deleteWorkspace(it.organization.id, it.workspace.id)
            }
          }

  @TestFactory
  fun `test RBAC delete workspace file`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_ADMIN to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true)
          .map { (role, shouldThrow) ->
            rbacTest("Test RBAC delete workspace file: $role", role, shouldThrow) {
              every { workspaceRepository.findBy(any(), any()) } returns Optional.of(it.workspace)
              workspaceServiceImpl.deleteWorkspaceFile(it.organization.id, it.workspace.id, "")
            }
          }

  @TestFactory
  fun `test RBAC download workspace file`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_ADMIN to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true)
          .map { (role, shouldThrow) ->
            rbacTest("Test RBAC download workspace file: $role", role, shouldThrow) {
              every { workspaceRepository.findBy(any(), any()) } returns Optional.of(it.workspace)
              workspaceServiceImpl.getWorkspaceFile(it.organization.id, it.workspace.id, "name")
            }
          }

  @TestFactory
  fun `test RBAC upload workspace file`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_ADMIN to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true)
          .map { (role, shouldThrow) ->
            rbacTest("Test RBAC upload workspace file: $role", role, shouldThrow) {
              every { workspaceRepository.findBy(any(), any()) } returns Optional.of(it.workspace)
              every { workspaceFile.originalFilename } returns "fakeName"
              workspaceServiceImpl.createWorkspaceFile(
                  it.organization.id, it.workspace.id, workspaceFile, true, "name")
            }
          }

  @TestFactory
  fun `test RBAC findAllWorkspaceFiles`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_ADMIN to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true)
          .map { (role, shouldThrow) ->
            rbacTest("Test RBAC findAllWorkspaceFiles: $role", role, shouldThrow) {
              every { workspaceRepository.findBy(any(), any()) } returns Optional.of(it.workspace)
              workspaceServiceImpl.listWorkspaceFiles(it.organization.id, it.workspace.id)
            }
          }

  @TestFactory
  fun `test RBAC get workspace security`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_ADMIN to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true)
          .map { (role, shouldThrow) ->
            rbacTest("Test RBAC get workspace security: $role", role, shouldThrow) {
              every { workspaceRepository.findBy(any(), any()) } returns Optional.of(it.workspace)
              workspaceServiceImpl.getWorkspaceSecurity(it.organization.id, it.workspace.id)
            }
          }

  @TestFactory
  fun `test RBAC set workspace default security`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_ADMIN to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true)
          .map { (role, shouldThrow) ->
            rbacTest("Test RBAC set workspace default security: $role", role, shouldThrow) {
              every { workspaceRepository.findBy(any(), any()) } returns Optional.of(it.workspace)
              every { workspaceRepository.save(any()) } returns it.workspace
              workspaceServiceImpl.updateWorkspaceDefaultSecurity(
                  it.organization.id, it.workspace.id, WorkspaceRole(ROLE_NONE))
            }
          }

  @TestFactory
  fun `test RBAC get workspace access control`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_ADMIN to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true)
          .map { (role, shouldThrow) ->
            rbacTest("test RBAC get workspace access control: $role", role, shouldThrow) {
              every { workspaceRepository.findBy(any(), any()) } returns Optional.of(it.workspace)
              workspaceServiceImpl.getWorkspaceAccessControl(
                  it.organization.id, it.workspace.id, CONNECTED_DEFAULT_USER)
            }
          }

  @TestFactory
  fun `test RBAC add workspace access control`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_ADMIN to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true)
          .map { (role, shouldThrow) ->
            rbacTest("test RBAC add workspace access control: $role", role, shouldThrow) {
              every { workspaceRepository.save(any()) } returns it.workspace
              every { workspaceRepository.findBy(any(), any()) } returns Optional.of(it.workspace)
              workspaceServiceImpl.createWorkspaceAccessControl(
                  it.organization.id,
                  it.workspace.id,
                  WorkspaceAccessControl("3$CONNECTED_DEFAULT_USER", ROLE_USER))
            }
          }

  @TestFactory
  fun `test RBAC update workspace access control`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_ADMIN to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true)
          .map { (role, shouldThrow) ->
            rbacTest("test RBAC update workspace access control: $role", role, shouldThrow) {
              every { workspaceRepository.findBy(any(), any()) } returns Optional.of(it.workspace)
              every { workspaceRepository.save(any()) } returns it.workspace
              workspaceServiceImpl.updateWorkspaceAccessControl(
                  it.organization.id,
                  it.workspace.id,
                  "2$CONNECTED_DEFAULT_USER",
                  WorkspaceRole(ROLE_USER))
            }
          }

  @TestFactory
  fun `test RBAC remove workspace access control`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_ADMIN to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true)
          .map { (role, shouldThrow) ->
            rbacTest("test RBAC remove workspace access control: $role", role, shouldThrow) {
              every { workspaceRepository.findBy(any(), any()) } returns Optional.of(it.workspace)
              every { workspaceRepository.save(any()) } returns it.workspace
              workspaceServiceImpl.deleteWorkspaceAccessControl(
                  it.organization.id, it.workspace.id, "2$CONNECTED_DEFAULT_USER")
            }
          }

  @TestFactory
  fun `test RBAC get workspace security users`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_ADMIN to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true)
          .map { (role, shouldThrow) ->
            rbacTest("test RBAC get workspace security users: $role", role, shouldThrow) {
              every { workspaceRepository.findBy(any(), any()) } returns Optional.of(it.workspace)
              every { workspaceRepository.save(any()) } returns it.workspace
              workspaceServiceImpl.listWorkspaceSecurityUsers(it.organization.id, it.workspace.id)
            }
          }

  private fun rbacTest(
      testName: String,
      role: String,
      shouldThrow: Boolean,
      testLambda: (ctx: WorkspaceTestContext) -> Unit
  ): DynamicTest? {
    val organization = mockOrganization(username = CONNECTED_DEFAULT_USER, role = role)
    val solution = mockSolution(organization.id)
    val workspace =
        mockWorkspace(organization.id, solution.id, "Workspace", CONNECTED_DEFAULT_USER, role)
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
      username: String = CONNECTED_DEFAULT_USER,
      role: String = ROLE_ADMIN
  ): Organization {
    return Organization(
        id = "organizationId",
        name = "Organization Name",
        createInfo = OrganizationEditInfo(0, ""),
        updateInfo = OrganizationEditInfo(0, ""),
        security =
            OrganizationSecurity(
                default = ROLE_NONE,
                accessControlList =
                    mutableListOf(
                        OrganizationAccessControl(id = username, role = role),
                        OrganizationAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN))))
  }

  fun mockSolution(organizationId: String): Solution {
    return Solution(
        id = "solutionId",
        key = UUID.randomUUID().toString(),
        name = "My solution",
        organizationId = organizationId,
        createInfo = SolutionEditInfo(0, ""),
        updateInfo = SolutionEditInfo(0, ""),
        version = "1.0.0",
        repository = "repository",
        parameters = mutableListOf(RunTemplateParameter("parameter", "string")),
        parameterGroups =
            mutableListOf(
                RunTemplateParameterGroup(
                    id = "group", isTable = false, parameters = mutableListOf())),
        runTemplates =
            mutableListOf(RunTemplate(id = "template", parameterGroups = mutableListOf())),
        security =
            SolutionSecurity(
                ROLE_ADMIN, mutableListOf(SolutionAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN))))
  }

  private fun mockWorkspaceCreateRequest(
      solutionId: String,
      workspaceName: String,
      roleName: String = CONNECTED_ADMIN_USER,
      role: String = ROLE_ADMIN
  ) =
      WorkspaceCreateRequest(
          key = UUID.randomUUID().toString(),
          name = workspaceName,
          solution = WorkspaceSolution(solutionId = solutionId),
          security =
              WorkspaceSecurity(
                  default = ROLE_NONE,
                  accessControlList =
                      mutableListOf(
                          WorkspaceAccessControl(id = roleName, role = role),
                          WorkspaceAccessControl("2$roleName", "viewer"))))

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
        createInfo = WorkspaceEditInfo(0, ""),
        updateInfo = WorkspaceEditInfo(0, ""),
        security =
            WorkspaceSecurity(
                default = ROLE_NONE,
                accessControlList =
                    mutableListOf(
                        WorkspaceAccessControl(id = roleName, role = role),
                        WorkspaceAccessControl("2$roleName", "viewer"))))
  }
}
