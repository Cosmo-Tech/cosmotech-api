// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.workspace.service

import com.cosmotech.common.config.CsmPlatformProperties
import com.cosmotech.common.exceptions.CsmAccessForbiddenException
import com.cosmotech.common.exceptions.CsmResourceNotFoundException
import com.cosmotech.common.rbac.*
import com.cosmotech.common.tests.CsmTestBase
import com.cosmotech.common.utils.getCurrentAccountIdentifier
import com.cosmotech.common.utils.getCurrentAuthenticatedRoles
import com.cosmotech.common.utils.getCurrentAuthenticatedUserName
import com.cosmotech.dataset.api.DatasetApiService
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.dataset.domain.DatasetCreateRequest
import com.cosmotech.organization.OrganizationApiServiceInterface
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationCreateRequest
import com.cosmotech.organization.domain.OrganizationSecurity
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.solution.domain.*
import com.cosmotech.workspace.WorkspaceApiServiceInterface
import com.cosmotech.workspace.domain.*
import com.redis.om.spring.indexing.RediSearchIndexer
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockkStatic
import java.io.FileInputStream
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.apache.commons.io.IOUtils
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.runner.RunWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ResourceLoader
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.junit4.SpringRunner
import software.amazon.awssdk.services.s3.model.NoSuchKeyException

@ActiveProfiles(profiles = ["workspace-test"])
@ExtendWith(MockKExtension::class)
@ExtendWith(SpringExtension::class)
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Suppress("FunctionName")
class WorkspaceServiceIntegrationTest : CsmTestBase() {
  val TEST_USER_MAIL = "testuser@mail.fr"
  val CONNECTED_ADMIN_USER = "test.admin@cosmotech.com"
  val CONNECTED_DEFAULT_USER = "test.user@cosmotech.com"
  val fileName = "test_workspace_file.txt"
  private val logger = LoggerFactory.getLogger(WorkspaceServiceIntegrationTest::class.java)

  @Autowired lateinit var rediSearchIndexer: RediSearchIndexer
  @Autowired lateinit var organizationApiService: OrganizationApiServiceInterface
  @Autowired lateinit var solutionApiService: SolutionApiService
  @Autowired lateinit var workspaceApiService: WorkspaceApiServiceInterface
  @Autowired lateinit var datasetApiService: DatasetApiService
  @Autowired lateinit var csmPlatformProperties: CsmPlatformProperties
  @Autowired lateinit var resourceLoader: ResourceLoader

  lateinit var organization: OrganizationCreateRequest
  lateinit var solution: SolutionCreateRequest
  lateinit var workspace: WorkspaceCreateRequest
  lateinit var dataset: DatasetCreateRequest

  lateinit var organizationSaved: Organization
  lateinit var solutionSaved: Solution
  lateinit var workspaceSaved: Workspace
  lateinit var datasetSaved: Dataset

  private var startTime: Long = 0

  @BeforeEach
  fun setUp() {
    mockkStatic("com.cosmotech.common.utils.SecurityUtilsKt")
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "test.user"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("user")

    startTime = Instant.now().toEpochMilli()

    rediSearchIndexer.createIndexFor(Organization::class.java)
    rediSearchIndexer.createIndexFor(Solution::class.java)
    rediSearchIndexer.createIndexFor(Workspace::class.java)
    rediSearchIndexer.createIndexFor(Dataset::class.java)

    organization = makeOrganizationCreateRequest("Organization test")
    organizationSaved = organizationApiService.createOrganization(organization)

    solution = makeSolution(organizationSaved.id)
    solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

    workspace = makeWorkspaceCreateRequest(solutionSaved.id, "Workspace")
    workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id, workspace)

    dataset = makeDatasetCreateRequest()
    datasetSaved =
        datasetApiService.createDataset(
            organizationSaved.id, workspaceSaved.id, dataset, emptyArray())
  }

  @Test
  fun `test CRUD operations on Workspace as User Admin`() {

    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")

    logger.info("should create a second new workspace")
    val workspace2 = makeWorkspaceCreateRequest(solutionSaved.id, "Workspace 2")
    workspaceApiService.createWorkspace(organizationSaved.id, workspace2)
    val workspaceRetrieved =
        workspaceApiService.getWorkspace(organizationSaved.id, workspaceSaved.id)
    assertEquals(workspaceSaved, workspaceRetrieved)

    logger.info("should find all workspaces and assert there are 2")
    val workspacesList: List<Workspace> =
        workspaceApiService.listWorkspaces(organizationSaved.id, null, null)
    assertTrue(workspacesList.size == 2)

    logger.info("should update the name of the first workspace")
    val updatedWorkspace =
        workspaceApiService.updateWorkspace(
            organizationSaved.id,
            workspaceSaved.id,
            WorkspaceUpdateRequest(key = "key", name = "Workspace 1 updated"))
    assertEquals("Workspace 1 updated", updatedWorkspace.name)
    assertEquals(workspaceSaved.organizationId, updatedWorkspace.organizationId)
  }

  @Test
  fun `test create workspace file`() {

    val resourceTestFile = resourceLoader.getResource("classpath:/$fileName").file
    val input = FileInputStream(resourceTestFile)
    val multipartFile =
        MockMultipartFile(
            "file", resourceTestFile.getName(), "text/plain", IOUtils.toByteArray(input))
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")

    logger.info("should create a workspace file")
    val savedFile =
        workspaceApiService.createWorkspaceFile(
            organizationSaved.id, workspaceSaved.id, multipartFile, true, null)

    assertEquals(fileName, savedFile.fileName)
  }

  @Test
  fun `test get workspace file`() {
    logger.info("should get a workspace file")
    val resourceTestFile = resourceLoader.getResource("classpath:/$fileName").file
    val input = FileInputStream(resourceTestFile)
    val expectedFile = FileInputStream(resourceTestFile)
    val multipartFile =
        MockMultipartFile(
            "file", resourceTestFile.getName(), "text/plain", IOUtils.toByteArray(input))
    workspaceApiService.createWorkspaceFile(
        organizationSaved.id, workspaceSaved.id, multipartFile, true, null)

    val fetchedFile =
        workspaceApiService.getWorkspaceFile(organizationSaved.id, workspaceSaved.id, fileName)
    val expectedText = expectedFile.bufferedReader().use { it.readText() }
    val retrievedText = fetchedFile.inputStream.bufferedReader().use { it.readText() }
    assertEquals(expectedText, retrievedText)
  }

  @Test
  fun `test list workspace files`() {
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")

    logger.info("should list all workspace file")
    val resourceTestFile = resourceLoader.getResource("classpath:/$fileName").file
    val input = FileInputStream(resourceTestFile)
    val multipartFile =
        MockMultipartFile(
            "file", resourceTestFile.getName(), "text/plain", IOUtils.toByteArray(input))

    var workspaceFiles =
        workspaceApiService.listWorkspaceFiles(organizationSaved.id, workspaceSaved.id)
    assertTrue(workspaceFiles.isEmpty())

    workspaceApiService.createWorkspaceFile(
        organizationSaved.id, workspaceSaved.id, multipartFile, true, null)

    workspaceFiles = workspaceApiService.listWorkspaceFiles(organizationSaved.id, workspaceSaved.id)
    assertEquals(1, workspaceFiles.size)
  }

  @Test
  fun `test delete workspace file`() {
    logger.info("should delete a workspace file")
    val resourceTestFile = resourceLoader.getResource("classpath:/$fileName").file
    val input = FileInputStream(resourceTestFile)
    val multipartFile =
        MockMultipartFile(
            "file", resourceTestFile.getName(), "text/plain", IOUtils.toByteArray(input))

    workspaceApiService.createWorkspaceFile(
        organizationSaved.id, workspaceSaved.id, multipartFile, true, null)

    workspaceApiService.deleteWorkspaceFile(organizationSaved.id, workspaceSaved.id, fileName)

    val exception =
        assertThrows<NoSuchKeyException> {
          workspaceApiService.getWorkspaceFile(organizationSaved.id, workspaceSaved.id, fileName)
        }

    assertEquals("The specified key does not exist.", exception.awsErrorDetails().errorMessage())
  }

  @Test
  fun `test deleteAll workspace file`() {
    logger.info("should delete all workspace files")
    val resourceTestFile = resourceLoader.getResource("classpath:/$fileName").file
    val input = FileInputStream(resourceTestFile)
    val multipartFile =
        MockMultipartFile(
            "file", resourceTestFile.getName(), "text/plain", IOUtils.toByteArray(input))

    workspaceApiService.createWorkspaceFile(
        organizationSaved.id, workspaceSaved.id, multipartFile, true, null)
    workspaceApiService.createWorkspaceFile(
        organizationSaved.id, workspaceSaved.id, multipartFile, true, null)

    workspaceApiService.deleteWorkspaceFiles(organizationSaved.id, workspaceSaved.id)

    var numberOfTry = 0
    var workspaceFilesIsEmpty: Boolean
    do {
      workspaceFilesIsEmpty =
          workspaceApiService.listWorkspaceFiles(organizationSaved.id, workspaceSaved.id).isEmpty()
      numberOfTry++
      Thread.sleep(50)
    } while (numberOfTry < 100 && !workspaceFilesIsEmpty)

    assertTrue(workspaceFilesIsEmpty)
  }

  @Test
  fun `test CRUD operations on Workspace as User Unauthorized`() {

    every { getCurrentAccountIdentifier(any()) } returns "userLambda"

    logger.info("should not create a new workspace")
    val workspace2 =
        makeWorkspaceCreateRequest(organizationSaved.id, solutionSaved.id, "Workspace 2")
    assertThrows<CsmAccessForbiddenException> {
      workspaceApiService.createWorkspace(organizationSaved.id, workspace2)
    }

    logger.info("should not retrieve a workspace")
    assertThrows<CsmAccessForbiddenException> {
      workspaceApiService.getWorkspace(organizationSaved.id, workspaceSaved.id)
    }

    logger.info("should not find all workspaces")
    val workspacesList: List<Workspace> =
        workspaceApiService.listWorkspaces(organizationSaved.id, null, null)
    assertTrue(workspacesList.isEmpty())

    logger.info("should not update a workspace")
    assertThrows<CsmAccessForbiddenException> {
      workspaceApiService.updateWorkspace(
          organizationSaved.id,
          workspaceSaved.id,
          WorkspaceUpdateRequest(key = "key", name = "Workspace 1 updated"))
    }

    logger.info("should not delete a workspace")
    assertThrows<CsmAccessForbiddenException> {
      workspaceApiService.deleteWorkspace(organizationSaved.id, workspaceSaved.id)
    }
  }

  @Test
  fun `test find All Workspaces with different pagination params`() {

    val workspaceNumber = 20
    val defaultPageSize = csmPlatformProperties.databases.resources.workspace.defaultPageSize
    val expectedSize = 15
    IntRange(1, workspaceNumber - 1).forEach {
      val workspace = makeWorkspaceCreateRequest(solutionSaved.id, "w-workspace-$it")
      workspaceApiService.createWorkspace(organizationSaved.id, workspace)
    }
    logger.info("should find all workspaces and assert there are $workspaceNumber")
    var workspacesList = workspaceApiService.listWorkspaces(organizationSaved.id, null, null)
    assertEquals(workspaceNumber, workspacesList.size)

    logger.info("should find all workspaces and assert it equals defaultPageSize: $defaultPageSize")
    workspacesList = workspaceApiService.listWorkspaces(organizationSaved.id, 0, null)
    assertEquals(defaultPageSize, workspacesList.size)

    logger.info("should find all workspaces and assert there are expected size: $expectedSize")
    workspacesList = workspaceApiService.listWorkspaces(organizationSaved.id, 0, expectedSize)
    assertEquals(expectedSize, workspacesList.size)

    logger.info("should find all workspaces and assert it returns the  second / last page")
    workspacesList = workspaceApiService.listWorkspaces(organizationSaved.id, 1, expectedSize)
    assertEquals(workspaceNumber - expectedSize, workspacesList.size)
  }

  @Test
  fun `test find All Workspaces with wrong pagination params`() {
    logger.info("should throw IllegalArgumentException when page and size are zero")
    assertThrows<IllegalArgumentException> {
      workspaceApiService.listWorkspaces(organizationSaved.id, 0, 0)
    }
    logger.info("should throw IllegalArgumentException when page is negative")
    assertThrows<IllegalArgumentException> {
      workspaceApiService.listWorkspaces(organizationSaved.id, -1, 1)
    }
    logger.info("should throw IllegalArgumentException when size is negative")
    assertThrows<IllegalArgumentException> {
      workspaceApiService.listWorkspaces(organizationSaved.id, 0, -1)
    }
  }

  @Test
  fun `test RBAC WorkspaceSecurity as User Admin`() {

    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")

    logger.info("should get default security with role NONE")
    val workspaceSecurity =
        workspaceApiService.getWorkspaceSecurity(organizationSaved.id, workspaceSaved.id)
    assertEquals(ROLE_NONE, workspaceSecurity.default)

    logger.info("should set default security with role VIEWER")
    val workspaceRole = WorkspaceRole(ROLE_VIEWER)
    val workspaceSecurityRegistered =
        workspaceApiService.updateWorkspaceDefaultSecurity(
            organizationSaved.id, workspaceSaved.id, workspaceRole)
    assertEquals(workspaceRole.role, workspaceSecurityRegistered.default)
  }

  @Test
  fun `test RBAC as User Unauthorized`() {
    every { getCurrentAccountIdentifier(any()) } returns "userLambda"

    assertEquals(0, workspaceApiService.listWorkspaces(organizationSaved.id, null, null).size)
  }

  @Test
  fun `test RBAC AccessControls on Workspace as User Admin`() {

    logger.info("should add a new access control")
    val workspaceAccessControl = WorkspaceAccessControl(TEST_USER_MAIL, ROLE_VIEWER)
    var workspaceAccessControlRegistered =
        workspaceApiService.createWorkspaceAccessControl(
            organizationSaved.id, workspaceSaved.id, workspaceAccessControl)
    assertEquals(workspaceAccessControl, workspaceAccessControlRegistered)

    logger.info("should get the access control")
    workspaceAccessControlRegistered =
        workspaceApiService.getWorkspaceAccessControl(
            organizationSaved.id, workspaceSaved.id, TEST_USER_MAIL)
    assertEquals(workspaceAccessControl, workspaceAccessControlRegistered)

    logger.info("should update the access control")
    workspaceAccessControlRegistered =
        workspaceApiService.updateWorkspaceAccessControl(
            organizationSaved.id, workspaceSaved.id, TEST_USER_MAIL, WorkspaceRole(ROLE_EDITOR))
    assertEquals(ROLE_EDITOR, workspaceAccessControlRegistered.role)

    logger.info("should get the list of users and assert there are 3")
    val userList =
        workspaceApiService.listWorkspaceSecurityUsers(organizationSaved.id, workspaceSaved.id)
    assertEquals(3, userList.size)

    logger.info("should remove the access control")
    workspaceApiService.deleteWorkspaceAccessControl(
        organizationSaved.id, workspaceSaved.id, TEST_USER_MAIL)
    assertThrows<CsmResourceNotFoundException> {
      workspaceAccessControlRegistered =
          workspaceApiService.getWorkspaceAccessControl(
              organizationSaved.id, workspaceSaved.id, TEST_USER_MAIL)
    }
  }

  @Test
  fun `test RBAC AccessControls on Workspace as User Unauthorized`() {

    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_DEFAULT_USER

    logger.info("should throw CsmAccessForbiddenException when adding a new access control")
    val workspaceAccessControl = WorkspaceAccessControl(TEST_USER_MAIL, ROLE_VIEWER)
    assertThrows<CsmAccessForbiddenException> {
      workspaceApiService.createWorkspaceAccessControl(
          organizationSaved.id, workspaceSaved.id, workspaceAccessControl)
    }

    logger.info("should throw CsmAccessForbiddenException when getting the access control")
    assertThrows<CsmAccessForbiddenException> {
      workspaceApiService.getWorkspaceAccessControl(
          organizationSaved.id, workspaceSaved.id, "userLambda")
    }

    logger.info("should throw CsmAccessForbiddenException when updating the access control")
    assertThrows<CsmAccessForbiddenException> {
      workspaceApiService.updateWorkspaceAccessControl(
          organizationSaved.id, workspaceSaved.id, TEST_USER_MAIL, WorkspaceRole(ROLE_VIEWER))
    }

    logger.info("should throw CsmAccessForbiddenException when getting the list of users")
    assertThrows<CsmAccessForbiddenException> {
      workspaceApiService.listWorkspaceSecurityUsers(organizationSaved.id, workspaceSaved.id)
    }

    logger.info("should throw CsmAccessForbiddenException when removing the access control")
    assertThrows<CsmAccessForbiddenException> {
      workspaceApiService.deleteWorkspaceAccessControl(
          organizationSaved.id, workspaceSaved.id, TEST_USER_MAIL)
    }
  }

  @Test
  fun `access control list shouldn't contain more than one time each user on creation`() {
    organizationSaved =
        organizationApiService.createOrganization(makeOrganizationCreateRequest("organization"))
    solutionSaved = solutionApiService.createSolution(organizationSaved.id, makeSolution())
    val brokenWorkspace =
        WorkspaceCreateRequest(
            name = "workspace",
            key = "key",
            solution = WorkspaceSolution(solutionSaved.id),
            security =
                WorkspaceSecurity(
                    default = ROLE_NONE,
                    accessControlList =
                        mutableListOf(
                            WorkspaceAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                            WorkspaceAccessControl(CONNECTED_ADMIN_USER, ROLE_EDITOR))))
    assertThrows<IllegalArgumentException> {
      workspaceApiService.createWorkspace(organizationSaved.id, brokenWorkspace)
    }
  }

  @Test
  fun `access control list shouldn't contain more than one time each user on ACL addition`() {
    organizationSaved =
        organizationApiService.createOrganization(makeOrganizationCreateRequest("organization"))
    solutionSaved = solutionApiService.createSolution(organizationSaved.id, makeSolution())
    val workingWorkspace = makeWorkspaceCreateRequest()
    workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id, workingWorkspace)

    assertThrows<IllegalArgumentException> {
      workspaceApiService.createWorkspaceAccessControl(
          organizationSaved.id,
          workspaceSaved.id,
          WorkspaceAccessControl(CONNECTED_ADMIN_USER, ROLE_EDITOR))
    }
  }

  @Test
  fun `As a viewer, I can only see my information in security property for getWorkspace`() {
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_DEFAULT_USER
    organization =
        makeOrganizationCreateRequest(
            name = "Organization test", userName = CONNECTED_DEFAULT_USER, role = ROLE_VIEWER)
    organizationSaved = organizationApiService.createOrganization(organization)
    solution = makeSolution(userName = CONNECTED_DEFAULT_USER, role = ROLE_VIEWER)
    solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

    workspace = makeWorkspaceCreateRequest()
    workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id, workspace)

    val workspaceRetrieved =
        workspaceApiService.getWorkspace(organizationSaved.id, workspaceSaved.id)
    assertEquals(
        WorkspaceSecurity(
            default = ROLE_NONE,
            mutableListOf(WorkspaceAccessControl(CONNECTED_DEFAULT_USER, ROLE_VIEWER))),
        workspaceRetrieved.security)
    assertEquals(1, workspaceRetrieved.security.accessControlList.size)
  }

  @Test
  fun `As a viewer, I can only see my information in security property for listWorkspaces`() {
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_DEFAULT_USER
    organization =
        makeOrganizationCreateRequest(
            name = "Organization test", userName = CONNECTED_DEFAULT_USER, role = ROLE_VIEWER)
    organizationSaved = organizationApiService.createOrganization(organization)
    solution = makeSolution(userName = CONNECTED_DEFAULT_USER, role = ROLE_VIEWER)
    solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

    workspace = makeWorkspaceCreateRequest()
    workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id, workspace)

    var workspaces = workspaceApiService.listWorkspaces(organizationSaved.id, null, null)
    workspaces.forEach {
      assertEquals(
          WorkspaceSecurity(
              default = ROLE_NONE,
              mutableListOf(WorkspaceAccessControl(CONNECTED_DEFAULT_USER, ROLE_VIEWER))),
          it.security)
      assertEquals(1, it.security.accessControlList.size)
    }
  }

  @Test
  fun `assert createWorkspace take all infos in considerations`() {
    val workspaceToCreate =
        Workspace(
            id = "id",
            organizationId = organizationSaved.id,
            key = "key",
            name = "name",
            createInfo = WorkspaceEditInfo(0, ""),
            updateInfo = WorkspaceEditInfo(0, ""),
            solution = WorkspaceSolution(solutionSaved.id),
            description = "description",
            version = "1.0.0",
            tags = mutableListOf("tag1", "tag2"),
            webApp = WorkspaceWebApp(url = "url"),
            datasetCopy = true,
            security =
                WorkspaceSecurity(
                    default = ROLE_NONE,
                    accessControlList = mutableListOf(WorkspaceAccessControl("id", ROLE_ADMIN))),
        )
    val workspaceCreateRequest =
        WorkspaceCreateRequest(
            key = workspaceToCreate.key,
            name = workspaceToCreate.name,
            solution = workspaceToCreate.solution,
            description = workspaceToCreate.description,
            version = workspaceToCreate.version,
            tags = workspaceToCreate.tags,
            webApp = workspaceToCreate.webApp,
            datasetCopy = workspaceToCreate.datasetCopy,
            security = workspaceToCreate.security)

    workspaceSaved =
        workspaceApiService.createWorkspace(organizationSaved.id, workspaceCreateRequest)

    workspaceToCreate.id = workspaceSaved.id
    workspaceToCreate.createInfo = workspaceSaved.createInfo
    workspaceToCreate.updateInfo = workspaceSaved.updateInfo
    assertEquals(workspaceToCreate, workspaceSaved)
  }

  @Test
  fun `assert updateWorkspace take all infos in considerations`() {
    var workspaceToCreate =
        Workspace(
            id = "id",
            organizationId = organizationSaved.id,
            key = "key",
            name = "name",
            createInfo = WorkspaceEditInfo(0, ""),
            updateInfo = WorkspaceEditInfo(0, ""),
            solution = WorkspaceSolution(solutionSaved.id),
            description = "description",
            version = "1.0.0",
            tags = mutableListOf("tag1", "tag2"),
            webApp = WorkspaceWebApp(url = "url"),
            datasetCopy = true,
            security =
                WorkspaceSecurity(
                    default = ROLE_ADMIN,
                    accessControlList = mutableListOf(WorkspaceAccessControl("id", ROLE_ADMIN))),
        )
    val workspaceCreateRequest =
        WorkspaceCreateRequest(
            key = workspaceToCreate.key,
            name = workspaceToCreate.name,
            solution = workspaceToCreate.solution,
            description = workspaceToCreate.description,
            version = workspaceToCreate.version,
            tags = workspaceToCreate.tags,
            webApp = workspaceToCreate.webApp,
            datasetCopy = workspaceToCreate.datasetCopy,
            security = workspaceToCreate.security)
    workspaceSaved =
        workspaceApiService.createWorkspace(organizationSaved.id, workspaceCreateRequest)
    solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
    val workspaceUpdateRequest =
        WorkspaceUpdateRequest(
            key = "new key",
            name = "new name",
            solution = WorkspaceSolution(solutionSaved.id),
            description = "new description",
            tags = mutableListOf("newTag1", "newTag2"),
            webApp = WorkspaceWebApp(url = "new url"),
            datasetCopy = false,
        )
    workspaceToCreate =
        workspaceToCreate.copy(
            id = workspaceSaved.id,
            key = workspaceUpdateRequest.key!!,
            name = workspaceUpdateRequest.name!!,
            solution = workspaceUpdateRequest.solution!!,
            description = workspaceUpdateRequest.description,
            tags = workspaceUpdateRequest.tags,
            webApp = workspaceUpdateRequest.webApp,
            datasetCopy = workspaceUpdateRequest.datasetCopy)

    workspaceSaved =
        workspaceApiService.updateWorkspace(
            organizationSaved.id, workspaceSaved.id, workspaceUpdateRequest)
    workspaceToCreate.createInfo = workspaceSaved.createInfo
    workspaceToCreate.updateInfo = workspaceSaved.updateInfo

    assertEquals(workspaceToCreate, workspaceSaved)
  }

  @Test
  fun `test updateWorkspace apply only specified changes`() {
    val workspace = makeWorkspaceCreateRequest()
    workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id, workspace)

    workspaceSaved =
        workspaceApiService.updateWorkspace(
            organizationSaved.id, workspaceSaved.id, WorkspaceUpdateRequest(key = "new_key"))
    assertEquals("new_key", workspaceSaved.key)
    assertEquals(workspace.name, workspaceSaved.name)
    assertEquals(workspace.solution, workspaceSaved.solution)
    assertEquals(workspace.security, workspaceSaved.security)
  }

  @Test
  fun `test createWorkspace and updateWorkspace with only required parameters`() {
    logger.info("should create workspace with only required parameters")
    val minimalRequest =
        WorkspaceCreateRequest(
            key = "minimal-key",
            name = "Minimal Workspace",
            solution = WorkspaceSolution(solutionSaved.id))
    val createdWorkspace = workspaceApiService.createWorkspace(organizationSaved.id, minimalRequest)
    assertEquals("minimal-key", createdWorkspace.key)
    assertEquals("Minimal Workspace", createdWorkspace.name)
    assertEquals(solutionSaved.id, createdWorkspace.solution.solutionId)
    assertNull(createdWorkspace.description)
    assertNull(createdWorkspace.webApp)

    logger.info("should update workspace with only required parameters")
    val updatedWorkspace =
        workspaceApiService.updateWorkspace(
            organizationSaved.id,
            createdWorkspace.id,
            WorkspaceUpdateRequest(key = "updated-key", name = "Updated Workspace"))
    assertEquals("updated-key", updatedWorkspace.key)
    assertEquals("Updated Workspace", updatedWorkspace.name)
    assertEquals(createdWorkspace.solution, updatedWorkspace.solution)
    assertEquals(createdWorkspace.description, updatedWorkspace.description)
    assertEquals(createdWorkspace.webApp, updatedWorkspace.webApp)
  }

  @Test
  fun `SDCOSMO-2543 - test workspace is not accessible from another organization`() {
    val workspace = makeWorkspaceCreateRequest()
    workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id, workspace)

    val anotherOrganization = makeOrganizationCreateRequest("Another Organization")
    val anotherOrganizationSaved = organizationApiService.createOrganization(anotherOrganization)

    assertDoesNotThrow { workspaceApiService.getWorkspace(organizationSaved.id, workspaceSaved.id) }

    val exception =
        assertThrows<CsmResourceNotFoundException> {
          workspaceApiService.getWorkspace(anotherOrganizationSaved.id, workspaceSaved.id)
        }

    assertEquals(
        "Workspace ${workspaceSaved.id} not found in organization ${anotherOrganizationSaved.id}",
        exception.message)
  }

  @Test
  fun `assert timestamps are functional for base CRUD`() {
    workspaceSaved =
        workspaceApiService.createWorkspace(organizationSaved.id, makeWorkspaceCreateRequest())
    assertTrue(workspaceSaved.createInfo.timestamp > startTime)
    assertEquals(workspaceSaved.createInfo, workspaceSaved.updateInfo)

    val updateTime = Instant.now().toEpochMilli()
    val workspaceUpdated =
        workspaceApiService.updateWorkspace(
            organizationSaved.id, workspaceSaved.id, WorkspaceUpdateRequest("workspaceUpdated"))

    assertTrue { updateTime < workspaceUpdated.updateInfo.timestamp }
    assertEquals(workspaceSaved.createInfo, workspaceUpdated.createInfo)
    assertTrue { workspaceSaved.createInfo.timestamp < workspaceUpdated.updateInfo.timestamp }
    assertTrue { workspaceSaved.updateInfo.timestamp < workspaceUpdated.updateInfo.timestamp }

    val workspaceFetched = workspaceApiService.getWorkspace(organizationSaved.id, workspaceSaved.id)

    assertEquals(workspaceUpdated.createInfo, workspaceFetched.createInfo)
    assertEquals(workspaceUpdated.updateInfo, workspaceFetched.updateInfo)
  }

  @Test
  fun `assert timestamps are functional for RBAC CRUD`() {
    workspaceSaved =
        workspaceApiService.createWorkspace(organizationSaved.id, makeWorkspaceCreateRequest())
    workspaceApiService.createWorkspaceAccessControl(
        organizationSaved.id, workspaceSaved.id, WorkspaceAccessControl("newUser", ROLE_USER))
    val rbacAdded = workspaceApiService.getWorkspace(organizationSaved.id, workspaceSaved.id)

    assertEquals(workspaceSaved.createInfo, rbacAdded.createInfo)
    assertTrue { workspaceSaved.updateInfo.timestamp < rbacAdded.updateInfo.timestamp }

    workspaceApiService.getWorkspaceAccessControl(
        organizationSaved.id, workspaceSaved.id, "newUser")
    val rbacFetched = workspaceApiService.getWorkspace(organizationSaved.id, workspaceSaved.id)

    assertEquals(rbacAdded.createInfo, rbacFetched.createInfo)
    assertEquals(rbacAdded.updateInfo, rbacFetched.updateInfo)

    workspaceApiService.updateWorkspaceAccessControl(
        organizationSaved.id, workspaceSaved.id, "newUser", WorkspaceRole(ROLE_VIEWER))
    val rbacUpdated = workspaceApiService.getWorkspace(organizationSaved.id, workspaceSaved.id)

    assertEquals(rbacFetched.createInfo, rbacUpdated.createInfo)
    assertTrue { rbacFetched.updateInfo.timestamp < rbacUpdated.updateInfo.timestamp }

    workspaceApiService.deleteWorkspaceAccessControl(
        organizationSaved.id, workspaceSaved.id, "newUser")
    val rbacDeleted = workspaceApiService.getWorkspace(organizationSaved.id, workspaceSaved.id)

    assertEquals(rbacUpdated.createInfo, rbacDeleted.createInfo)
    assertTrue { rbacUpdated.updateInfo.timestamp < rbacDeleted.updateInfo.timestamp }
  }

  fun makeDatasetCreateRequest() = DatasetCreateRequest(name = "Dataset test")

  fun makeOrganizationCreateRequest(
      name: String = "Organization Name",
      userName: String = CONNECTED_ADMIN_USER,
      role: String = ROLE_ADMIN
  ) =
      OrganizationCreateRequest(
          name = name,
          security =
              OrganizationSecurity(
                  default = ROLE_NONE,
                  accessControlList =
                      mutableListOf(
                          OrganizationAccessControl(id = userName, role = role),
                          OrganizationAccessControl("userLambda", "viewer"))))

  fun makeSolution(userName: String = CONNECTED_DEFAULT_USER, role: String = ROLE_USER) =
      SolutionCreateRequest(
          key = UUID.randomUUID().toString(),
          name = "My solution",
          runTemplates = mutableListOf(RunTemplateCreateRequest("template")),
          parameters = mutableListOf(RunTemplateParameterCreateRequest("parameter", "string")),
          parameterGroups = mutableListOf(RunTemplateParameterGroupCreateRequest("group")),
          repository = "repository",
          version = "1.0.0",
          security =
              SolutionSecurity(
                  default = ROLE_NONE,
                  accessControlList =
                      mutableListOf(
                          SolutionAccessControl(id = CONNECTED_ADMIN_USER, role = ROLE_ADMIN),
                          SolutionAccessControl(id = userName, role = role))))

  fun makeWorkspaceCreateRequest(
      solutionId: String = solutionSaved.id,
      name: String = "name",
      userName: String = CONNECTED_DEFAULT_USER,
      role: String = ROLE_VIEWER
  ) =
      WorkspaceCreateRequest(
          key = UUID.randomUUID().toString(),
          name = name,
          solution =
              WorkspaceSolution(
                  solutionId = solutionId,
              ),
          security =
              WorkspaceSecurity(
                  default = ROLE_NONE,
                  accessControlList =
                      mutableListOf(
                          WorkspaceAccessControl(id = userName, role = role),
                          WorkspaceAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN))))

  fun makeDataset(
      name: String = "my_dataset_test",
  ) = DatasetCreateRequest(name)
}
