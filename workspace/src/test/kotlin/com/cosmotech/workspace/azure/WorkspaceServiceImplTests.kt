// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.workspace.azure

import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.BlobServiceClient
import com.azure.storage.blob.batch.BlobBatchClient
import com.cosmotech.api.azure.sanitizeForAzureStorage
import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.rbac.CsmRbac
import com.cosmotech.api.utils.ResourceScanner
import com.cosmotech.api.utils.SecretManager
import com.cosmotech.api.utils.getCurrentAuthenticatedMail
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.repository.OrganizationRepository
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceSolution
import com.cosmotech.workspace.repository.WorkspaceRepository
import com.cosmotech.workspace.service.WorkspaceServiceImpl
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader

const val ORGANIZATION_ID = "O-AbCdEf123"
const val WORKSPACE_ID = "W-BcDeFg123"

@ExtendWith(MockKExtension::class)
class WorkspaceServiceImplTests {

  @MockK private lateinit var resourceLoader: ResourceLoader
  @MockK private lateinit var solutionService: SolutionApiService
  @RelaxedMockK private lateinit var organizationService: OrganizationApiService
  @MockK private lateinit var azureStorageBlobServiceClient: BlobServiceClient
  @RelaxedMockK private lateinit var csmPlatformProperties: CsmPlatformProperties
  @MockK private lateinit var secretManager: SecretManager

  @MockK private lateinit var azureStorageBlobBatchClient: BlobBatchClient

  @RelaxedMockK private lateinit var csmRbac: CsmRbac
  @RelaxedMockK private lateinit var resourceScanner: ResourceScanner

  @Suppress("unused") @MockK private lateinit var organizationRepository: OrganizationRepository
  @Suppress("unused") @MockK private lateinit var workspaceRepository: WorkspaceRepository

  @InjectMockKs private lateinit var workspaceServiceImpl: WorkspaceServiceImpl

  @BeforeTest
  fun setUp() {
    this.workspaceServiceImpl =
        spyk(
            WorkspaceServiceImpl(
                resourceLoader,
                organizationService,
                solutionService,
                azureStorageBlobServiceClient,
                azureStorageBlobBatchClient,
                csmRbac,
                resourceScanner,
                secretManager,
                organizationRepository,
                workspaceRepository))
    mockkStatic(::getCurrentAuthenticatedMail)
    every { getCurrentAuthenticatedMail(csmPlatformProperties) } returns "dummy@cosmotech.com"

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
    every { csmPlatformProperties.upload } returns csmPlatformPropertiesUpload

    every { workspaceServiceImpl getProperty "csmPlatformProperties" } returns csmPlatformProperties

    MockKAnnotations.init(this, relaxUnitFun = true)
    this.csmRbac = mockk<CsmRbac>(relaxed = true)
  }

  @Test
  fun `In uploadWorkspaceFile, filename is used if no destination set`() {
    val workspace = mockk<Workspace>(relaxed = true)
    every { workspace.id } returns WORKSPACE_ID
    every { workspace.name } returns "test workspace"
    every { workspaceServiceImpl.findWorkspaceById(ORGANIZATION_ID, WORKSPACE_ID) } returns
        workspace

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
    every { workspaceServiceImpl.findWorkspaceById(ORGANIZATION_ID, WORKSPACE_ID) } returns
        workspace

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
    every { workspaceServiceImpl.findWorkspaceById(ORGANIZATION_ID, WORKSPACE_ID) } returns
        workspace

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
    every { workspaceServiceImpl.findWorkspaceById(ORGANIZATION_ID, WORKSPACE_ID) } returns
        workspace

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
    every { workspaceServiceImpl.findWorkspaceById(ORGANIZATION_ID, WORKSPACE_ID) } returns
        workspace

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

    verify(exactly = 0) { resourceLoader.getResource(any()) }
    confirmVerified(resourceLoader)
  }

  @Test
  fun `should reject creation request if solution ID is not valid`() {
    every { solutionService.findSolutionById(ORGANIZATION_ID, any()) } throws
        CsmResourceNotFoundException("Solution not found")
    assertThrows<CsmResourceNotFoundException> {
      workspaceServiceImpl.createWorkspace(
          ORGANIZATION_ID,
          Workspace(
              key = "my-workspace-key",
              name = "my workspace name",
              solution = WorkspaceSolution(solutionId = "SOL-my-solution-id")))
    }
    verify(exactly = 0) { workspaceRepository.save(ofType(Workspace::class)) }
    confirmVerified(workspaceRepository)
  }

  @Test
  fun `should reject update request if solution ID is not valid`() {
    every { workspaceServiceImpl.findWorkspaceByIdNoSecurity(WORKSPACE_ID) } returns
        Workspace(
            id = WORKSPACE_ID,
            key = "my-workspace-key",
            name = "my workspace name",
            solution = WorkspaceSolution(solutionId = "SOL-my-solution-id"))
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
}
