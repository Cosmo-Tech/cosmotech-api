// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.workspace.azure

import com.azure.spring.data.cosmos.core.CosmosTemplate
import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.BlobServiceClient
import com.azure.storage.blob.batch.BlobBatchClient
import com.cosmotech.api.azure.sanitizeForAzureStorage
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceSolution
import com.cosmotech.workspace.rbac.WorkspaceRbac
import io.mockk.MockKAnnotations
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import java.lang.IllegalArgumentException
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
  @MockK private lateinit var azureStorageBlobServiceClient: BlobServiceClient

  @MockK private lateinit var azureStorageBlobBatchClient: BlobBatchClient

  @MockK private lateinit var rbac: WorkspaceRbac

  @Suppress("unused") @MockK private lateinit var cosmosTemplate: CosmosTemplate

  @InjectMockKs private lateinit var workspaceServiceImpl: WorkspaceServiceImpl

  @BeforeTest
  fun setUp() {
    this.workspaceServiceImpl =
        spyk(
            WorkspaceServiceImpl(
                resourceLoader,
                solutionService,
                azureStorageBlobServiceClient,
                azureStorageBlobBatchClient,
                rbac))
    MockKAnnotations.init(this, relaxUnitFun = true)
  }

  @Test
  fun `In uploadWorkspaceFile, filename is used if no destination set`() {
    val workspace = mockk<Workspace>()
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
    val workspace = mockk<Workspace>()
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
    val workspace = mockk<Workspace>()
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
    val workspace = mockk<Workspace>()
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
    val workspace = mockk<Workspace>()
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
    verify(exactly = 0) {
      cosmosTemplate.insert("${ORGANIZATION_ID}_workspaces", ofType(Workspace::class))
    }
    confirmVerified(cosmosTemplate)
  }

  @Test
  fun `should reject update request if solution ID is not valid`() {
    every { workspaceServiceImpl.findWorkspaceById(ORGANIZATION_ID, WORKSPACE_ID) } returns
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

    verify(exactly = 0) {
      cosmosTemplate.insert("${ORGANIZATION_ID}_workspaces", ofType(Workspace::class))
    }
    verify(exactly = 0) {
      cosmosTemplate.upsertAndReturnEntity(
          "${ORGANIZATION_ID}_workspaces", ofType(Workspace::class))
    }
    confirmVerified(cosmosTemplate)
  }
}
