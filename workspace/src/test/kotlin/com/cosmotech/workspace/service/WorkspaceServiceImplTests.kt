// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.workspace.service

import com.azure.spring.cloud.core.resource.AzureStorageBlobProtocolResolver
import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.BlobServiceClient
import com.azure.storage.blob.batch.BlobBatchClient
import com.cosmotech.api.azure.sanitizeForAzureStorage
import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.events.CsmEventPublisher
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.id.CsmIdGenerator
import com.cosmotech.api.rbac.CsmAdmin
import com.cosmotech.api.rbac.CsmRbac
import com.cosmotech.api.rbac.PERMISSION_CREATE_CHILDREN
import com.cosmotech.api.rbac.PERMISSION_READ
import com.cosmotech.api.rbac.ROLE_ADMIN
import com.cosmotech.api.rbac.ROLE_EDITOR
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.rbac.ROLE_USER
import com.cosmotech.api.rbac.ROLE_VALIDATOR
import com.cosmotech.api.rbac.ROLE_VIEWER
import com.cosmotech.api.utils.ResourceScanner
import com.cosmotech.api.utils.SecretManager
import com.cosmotech.api.utils.getCurrentAccountIdentifier
import com.cosmotech.api.utils.getCurrentAuthenticatedRoles
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.organization.OrganizationApiServiceInterface
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationSecurity
import com.cosmotech.organization.repository.OrganizationRepository
import com.cosmotech.organization.service.getRbac
import com.cosmotech.solution.SolutionApiServiceInterface
import com.cosmotech.solution.domain.Solution
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceAccessControl
import com.cosmotech.workspace.domain.WorkspaceRole
import com.cosmotech.workspace.domain.WorkspaceSecurity
import com.cosmotech.workspace.domain.WorkspaceSolution
import com.cosmotech.workspace.repository.WorkspaceRepository
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
import java.io.InputStream
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
import org.springframework.core.io.Resource
import org.springframework.data.repository.findByIdOrNull

const val ORGANIZATION_ID = "O-AbCdEf123"
const val WORKSPACE_ID = "W-BcDeFg123"
const val CONNECTED_ADMIN_USER = "test.admin@cosmotech.com"
const val CONNECTED_DEFAULT_USER = "test.user@cosmotech.com"

@ExtendWith(MockKExtension::class)
@Suppress("LargeClass")
class WorkspaceServiceImplTests {

  @MockK private lateinit var azureStorageBlobProtocolResolver: AzureStorageBlobProtocolResolver
  @MockK private lateinit var solutionService: SolutionApiServiceInterface
  @MockK private lateinit var resource: Resource
  @MockK private lateinit var inputStream: InputStream
  @RelaxedMockK private lateinit var organizationService: OrganizationApiServiceInterface
  @MockK private lateinit var azureStorageBlobServiceClient: BlobServiceClient
  @Suppress("unused") @MockK private lateinit var secretManager: SecretManager

  @Suppress("unused") @MockK private lateinit var azureStorageBlobBatchClient: BlobBatchClient

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
  fun setUp() {
    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_DEFAULT_USER
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

    MockKAnnotations.init(this)
  }

  @Test
  fun `In uploadWorkspaceFile, filename is used if no destination set`() {
    val workspace = mockk<Workspace>(relaxed = true)
    every { workspace.id } returns WORKSPACE_ID
    every { workspace.name } returns "test workspace"
    every { workspace.security } returns WorkspaceSecurity(ROLE_ADMIN, mutableListOf())
    every { workspaceRepository.findByIdOrNull(any()) } returns workspace

    val file = mockk<Resource>(relaxed = true)
    every { file.filename } returns "my_file.txt"

    val blobContainerClient = mockk<BlobContainerClient>(relaxed = true)
    every { azureStorageBlobServiceClient.getBlobContainerClient(any()) } returns
        blobContainerClient

    val workspaceFile =
        workspaceServiceImpl.uploadWorkspaceFile(ORGANIZATION_ID, WORKSPACE_ID, file, false, null)
    assertNotNull(workspaceFile.fileName)
    assertEquals("my_file.txt", workspaceFile.fileName)

    verify(exactly = 1) {
      azureStorageBlobServiceClient.getBlobContainerClient(
          ORGANIZATION_ID.sanitizeForAzureStorage())
    }
    verify(exactly = 1) {
      blobContainerClient.getBlobClient("${WORKSPACE_ID.sanitizeForAzureStorage()}/my_file.txt")
    }
    confirmVerified(azureStorageBlobServiceClient, blobContainerClient)
  }

  @Test
  fun `In uploadWorkspaceFile, filename is used if destination is blank`() {
    val workspace = mockk<Workspace>(relaxed = true)
    every { workspace.id } returns WORKSPACE_ID
    every { workspace.name } returns "test workspace"
    every { workspace.security } returns WorkspaceSecurity(ROLE_ADMIN, mutableListOf())
    every { organizationService.getVerifiedOrganization(ORGANIZATION_ID) } returns
        mockk<Organization>()
    every { workspaceRepository.findByIdOrNull(any()) } returns workspace

    val file = mockk<Resource>(relaxed = true)
    every { file.filename } returns "my_file.txt"

    val blobContainerClient = mockk<BlobContainerClient>(relaxed = true)
    every { azureStorageBlobServiceClient.getBlobContainerClient(any()) } returns
        blobContainerClient

    val workspaceFile =
        workspaceServiceImpl.uploadWorkspaceFile(ORGANIZATION_ID, WORKSPACE_ID, file, false, "  ")
    assertNotNull(workspaceFile.fileName)
    assertEquals("my_file.txt", workspaceFile.fileName)

    verify(exactly = 1) {
      azureStorageBlobServiceClient.getBlobContainerClient(
          ORGANIZATION_ID.sanitizeForAzureStorage())
    }
    verify(exactly = 1) {
      blobContainerClient.getBlobClient("${WORKSPACE_ID.sanitizeForAzureStorage()}/my_file.txt")
    }
    confirmVerified(azureStorageBlobServiceClient, blobContainerClient)
  }

  @Test
  fun `In uploadWorkspaceFile, filename is appended to destination directory (ending with slash)`() {
    val workspace = mockk<Workspace>(relaxed = true)
    every { workspace.id } returns WORKSPACE_ID
    every { workspace.name } returns "test workspace"
    every { workspace.security } returns WorkspaceSecurity(ROLE_ADMIN, mutableListOf())
    every { workspaceRepository.findByIdOrNull(any()) } returns workspace

    val file = mockk<Resource>(relaxed = true)
    every { file.filename } returns "my_file.txt"

    val blobContainerClient = mockk<BlobContainerClient>(relaxed = true)
    every { azureStorageBlobServiceClient.getBlobContainerClient(any()) } returns
        blobContainerClient

    val workspaceFile =
        workspaceServiceImpl.uploadWorkspaceFile(
            ORGANIZATION_ID, WORKSPACE_ID, file, false, "my/destination/")
    assertNotNull(workspaceFile.fileName)
    assertEquals("my/destination/my_file.txt", workspaceFile.fileName)

    verify(exactly = 1) {
      azureStorageBlobServiceClient.getBlobContainerClient(
          ORGANIZATION_ID.sanitizeForAzureStorage())
    }
    verify(exactly = 1) {
      blobContainerClient.getBlobClient(
          "${WORKSPACE_ID.sanitizeForAzureStorage()}/my/destination/my_file.txt")
    }
    confirmVerified(azureStorageBlobServiceClient, blobContainerClient)
  }

  @Test
  fun `In uploadWorkspaceFile, destination is used as is as file path if not ending with slash)`() {
    val workspace = mockk<Workspace>(relaxed = true)
    every { workspace.id } returns WORKSPACE_ID
    every { workspace.name } returns "test workspace"
    every { workspace.security } returns WorkspaceSecurity(ROLE_ADMIN, mutableListOf())
    every { workspaceRepository.findByIdOrNull(any()) } returns workspace

    val file = mockk<Resource>(relaxed = true)
    every { file.filename } returns "my_file.txt"

    val blobContainerClient = mockk<BlobContainerClient>(relaxed = true)
    every { azureStorageBlobServiceClient.getBlobContainerClient(any()) } returns
        blobContainerClient

    val workspaceFile =
        workspaceServiceImpl.uploadWorkspaceFile(
            ORGANIZATION_ID, WORKSPACE_ID, file, false, "my/destination/file")
    assertNotNull(workspaceFile.fileName)
    assertEquals("my/destination/file", workspaceFile.fileName)

    verify(exactly = 1) {
      azureStorageBlobServiceClient.getBlobContainerClient(
          ORGANIZATION_ID.sanitizeForAzureStorage())
    }
    verify(exactly = 1) {
      blobContainerClient.getBlobClient(
          "${WORKSPACE_ID.sanitizeForAzureStorage()}/my/destination/file")
    }
    confirmVerified(azureStorageBlobServiceClient, blobContainerClient)
  }

  @Test
  fun `In uploadWorkspaceFile, multiple slash characters in destination result in a single slash being used`() {
    val workspace = mockk<Workspace>(relaxed = true)
    every { workspace.id } returns WORKSPACE_ID
    every { workspace.name } returns "test workspace"
    every { workspace.security } returns WorkspaceSecurity(ROLE_ADMIN, mutableListOf())
    every { workspaceRepository.findByIdOrNull(any()) } returns workspace

    val file = mockk<Resource>(relaxed = true)
    every { file.filename } returns "my_file.txt"

    val blobContainerClient = mockk<BlobContainerClient>(relaxed = true)
    every { azureStorageBlobServiceClient.getBlobContainerClient(any()) } returns
        blobContainerClient

    val workspaceFile =
        workspaceServiceImpl.uploadWorkspaceFile(
            ORGANIZATION_ID, WORKSPACE_ID, file, false, "my//other/destination////////file")
    assertNotNull(workspaceFile.fileName)
    assertEquals("my/other/destination/file", workspaceFile.fileName)

    verify(exactly = 1) {
      azureStorageBlobServiceClient.getBlobContainerClient(
          ORGANIZATION_ID.sanitizeForAzureStorage())
    }
    verify(exactly = 1) {
      blobContainerClient.getBlobClient(
          "${WORKSPACE_ID.sanitizeForAzureStorage()}/my/other/destination/file")
    }
    confirmVerified(azureStorageBlobServiceClient, blobContainerClient)
  }

  @Test
  fun `Calling uploadWorkspaceFile is not allowed when destination contains double-dot`() {
    val blobContainerClient = mockk<BlobContainerClient>(relaxed = true)
    every { azureStorageBlobServiceClient.getBlobContainerClient(any()) } returns
        blobContainerClient

    assertThrows<IllegalArgumentException> {
      workspaceServiceImpl.uploadWorkspaceFile(
          ORGANIZATION_ID, WORKSPACE_ID, mockk(), false, "my/../other/destination/../../file")
    }

    verify(exactly = 0) {
      azureStorageBlobServiceClient.getBlobContainerClient(
          ORGANIZATION_ID.sanitizeForAzureStorage())
    }
    verify(exactly = 0) {
      blobContainerClient.getBlobClient(
          "${WORKSPACE_ID.sanitizeForAzureStorage()}/my/other/destination/file")
    }
    confirmVerified(azureStorageBlobServiceClient, blobContainerClient)
  }

  @Test
  fun `Calling downloadWorkspaceFile is not allowed when filename contains double-dot`() {
    assertThrows<IllegalArgumentException> {
      workspaceServiceImpl.downloadWorkspaceFile(
          ORGANIZATION_ID, WORKSPACE_ID, "my/../../other/destination/file")
    }

    verify(exactly = 0) { azureStorageBlobProtocolResolver.getResource(any()) }
    confirmVerified(azureStorageBlobProtocolResolver)
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
            solution = WorkspaceSolution(solutionId = "SOL-my-solution-id"),
            security = WorkspaceSecurity(ROLE_ADMIN, mutableListOf()))
    every { workspaceRepository.findByIdOrNull(WORKSPACE_ID) } returns workspace
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
              every { workspaceRepository.findByIdOrNull(any()) } returns it.workspace
              workspaceServiceImpl.getVerifiedWorkspace(it.organization.id!!, it.workspace.id!!)
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
                csmRbac.verify(it.organization.getRbac(), permission)
              }
              every { workspaceRepository.save(any()) } returns it.workspace
              every { solutionService.findSolutionById(any(), any()) } returns it.solution
              workspaceServiceImpl.createWorkspace(it.organization.id!!, it.workspace)
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
              every { workspaceRepository.findByIdOrNull(any()) } returns it.workspace
              workspaceServiceImpl.deleteAllWorkspaceFiles(it.organization.id!!, it.workspace.id!!)
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
              every { solutionService.findSolutionById(any(), any()) } returns it.solution
              every { workspaceRepository.findByIdOrNull(any()) } returns it.workspace
              every { workspaceRepository.save(any()) } returns it.workspace
              workspaceServiceImpl.updateWorkspace(
                  it.organization.id!!, it.workspace.id!!, it.workspace)
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
              every { workspaceRepository.findByIdOrNull(any()) } returns it.workspace
              every { secretManager.deleteSecret(any(), any()) } returns Unit
              workspaceServiceImpl.deleteWorkspace(it.organization.id!!, it.workspace.id!!)
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
              every { workspaceRepository.findByIdOrNull(any()) } returns it.workspace
              every {
                azureStorageBlobServiceClient
                    .getBlobContainerClient(any())
                    .getBlobClient(any())
                    .delete()
              } returns mockk()
              workspaceServiceImpl.deleteWorkspaceFile(it.organization.id!!, it.workspace.id!!, "")
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
              every { workspaceRepository.findByIdOrNull(any()) } returns it.workspace
              every { azureStorageBlobProtocolResolver.getResource(any()) } returns mockk()
              workspaceServiceImpl.downloadWorkspaceFile(
                  it.organization.id!!, it.workspace.id!!, "")
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
              every { workspaceRepository.findByIdOrNull(any()) } returns it.workspace
              every { resource.filename } returns "name"
              every {
                azureStorageBlobServiceClient
                    .getBlobContainerClient(any())
                    .getBlobClient(any())
                    .upload(any() as InputStream, any(), any())
              } returns mockk()
              every { resource.inputStream } returns inputStream
              every { resource.contentLength() } returns 0
              workspaceServiceImpl.uploadWorkspaceFile(
                  it.organization.id!!, it.workspace.id!!, resource, true, "")
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
              every { workspaceRepository.findByIdOrNull(any()) } returns it.workspace
              every { azureStorageBlobServiceClient.getBlobContainerClient(any()) } returns mockk()
              every { azureStorageBlobProtocolResolver.getResources(any()) } returns arrayOf()
              workspaceServiceImpl.findAllWorkspaceFiles(it.organization.id!!, it.workspace.id!!)
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
              every { workspaceRepository.findByIdOrNull(any()) } returns it.workspace
              workspaceServiceImpl.getWorkspaceSecurity(it.organization.id!!, it.workspace.id!!)
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
              every { workspaceRepository.findByIdOrNull(any()) } returns it.workspace
              every { workspaceRepository.save(any()) } returns it.workspace
              workspaceServiceImpl.setWorkspaceDefaultSecurity(
                  it.organization.id!!, it.workspace.id!!, WorkspaceRole(ROLE_NONE))
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
              every { workspaceRepository.findByIdOrNull(any()) } returns it.workspace
              workspaceServiceImpl.getWorkspaceAccessControl(
                  it.organization.id!!, it.workspace.id!!, CONNECTED_DEFAULT_USER)
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
              every { workspaceRepository.findByIdOrNull(any()) } returns it.workspace
              workspaceServiceImpl.addWorkspaceAccessControl(
                  it.organization.id!!,
                  it.workspace.id!!,
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
              every { workspaceRepository.findByIdOrNull(any()) } returns it.workspace
              every { workspaceRepository.save(any()) } returns it.workspace
              workspaceServiceImpl.updateWorkspaceAccessControl(
                  it.organization.id!!,
                  it.workspace.id!!,
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
              every { workspaceRepository.findByIdOrNull(any()) } returns it.workspace
              every { workspaceRepository.save(any()) } returns it.workspace
              workspaceServiceImpl.removeWorkspaceAccessControl(
                  it.organization.id!!, it.workspace.id!!, "2$CONNECTED_DEFAULT_USER")
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
              every { workspaceRepository.findByIdOrNull(any()) } returns it.workspace
              every { workspaceRepository.save(any()) } returns it.workspace
              workspaceServiceImpl.getWorkspaceSecurityUsers(
                  it.organization.id!!, it.workspace.id!!)
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
                        WorkspaceAccessControl("2$roleName", "viewer"))))
  }
}
