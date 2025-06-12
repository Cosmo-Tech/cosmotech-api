// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.service

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.rbac.ROLE_ADMIN
import com.cosmotech.api.rbac.ROLE_EDITOR
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.rbac.ROLE_USER
import com.cosmotech.api.tests.CsmTestBase
import com.cosmotech.api.utils.getCurrentAccountIdentifier
import com.cosmotech.api.utils.getCurrentAuthenticatedRoles
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.dataset.DatasetApiServiceInterface
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.dataset.domain.DatasetAccessControl
import com.cosmotech.dataset.domain.DatasetCreateRequest
import com.cosmotech.dataset.domain.DatasetPart
import com.cosmotech.dataset.domain.DatasetPartCreateRequest
import com.cosmotech.dataset.domain.DatasetPartTypeEnum
import com.cosmotech.dataset.domain.DatasetRole
import com.cosmotech.dataset.domain.DatasetSecurity
import com.cosmotech.organization.OrganizationApiServiceInterface
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationCreateRequest
import com.cosmotech.organization.domain.OrganizationSecurity
import com.cosmotech.solution.SolutionApiServiceInterface
import com.cosmotech.solution.domain.RunTemplateCreateRequest
import com.cosmotech.solution.domain.RunTemplateParameterCreateRequest
import com.cosmotech.solution.domain.RunTemplateParameterGroupCreateRequest
import com.cosmotech.solution.domain.Solution
import com.cosmotech.solution.domain.SolutionAccessControl
import com.cosmotech.solution.domain.SolutionCreateRequest
import com.cosmotech.solution.domain.SolutionSecurity
import com.cosmotech.workspace.WorkspaceApiServiceInterface
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceAccessControl
import com.cosmotech.workspace.domain.WorkspaceCreateRequest
import com.cosmotech.workspace.domain.WorkspaceSecurity
import com.cosmotech.workspace.domain.WorkspaceSolution
import com.redis.om.spring.indexing.RediSearchIndexer
import io.awspring.cloud.s3.S3Template
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockkStatic
import java.io.FileInputStream
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.apache.commons.io.IOUtils
import org.junit.Assert.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.runner.RunWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.InputStreamResource
import org.springframework.core.io.ResourceLoader
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.junit4.SpringRunner

@ActiveProfiles(profiles = ["dataset-test"])
@ExtendWith(MockKExtension::class)
@ExtendWith(SpringExtension::class)
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DatasetServiceIntegrationTest() : CsmTestBase() {
  val CONNECTED_ADMIN_USER = "test.admin@cosmotech.com"
  val CONNECTED_DEFAULT_USER = "test.user@cosmotech.com"
  val CUSTOMER_SOURCE_FILE_NAME = "customers.csv"
  val INVENTORY_SOURCE_FILE_NAME = "product_inventory.csv"

  private val logger = LoggerFactory.getLogger(DatasetServiceIntegrationTest::class.java)

  @Autowired lateinit var rediSearchIndexer: RediSearchIndexer
  @Autowired lateinit var organizationApiService: OrganizationApiServiceInterface
  @Autowired lateinit var workspaceApiService: WorkspaceApiServiceInterface
  @Autowired lateinit var solutionApiService: SolutionApiServiceInterface
  @Autowired lateinit var csmPlatformProperties: CsmPlatformProperties
  @Autowired lateinit var datasetApiService: DatasetApiServiceInterface
  @Autowired lateinit var s3Template: S3Template
  @Autowired lateinit var resourceLoader: ResourceLoader

  lateinit var organization: OrganizationCreateRequest
  lateinit var workspace: WorkspaceCreateRequest
  lateinit var solution: SolutionCreateRequest
  lateinit var organizationSaved: Organization
  lateinit var workspaceSaved: Workspace
  lateinit var solutionSaved: Solution

  @BeforeEach
  fun setUp() {
    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "test.user"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("user")

    rediSearchIndexer.createIndexFor(Organization::class.java)
    rediSearchIndexer.createIndexFor(Workspace::class.java)
    rediSearchIndexer.createIndexFor(Solution::class.java)
    rediSearchIndexer.createIndexFor(Dataset::class.java)
    rediSearchIndexer.createIndexFor(DatasetPart::class.java)

    organization = makeOrganizationCreateRequest("Organization test")
    organizationSaved = organizationApiService.createOrganization(organization)

    solution = makeSolution(organizationSaved.id)
    solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

    workspace = makeWorkspaceCreateRequest()
    workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id, workspace)
  }

  @Test
  fun `test createDataset with one File dataset part`() {

    logger.info("Test createDataset with a dataset part")

    val datasetPartName = "Customers list"
    val datasetPartDescription = "List of customers"
    val datasetPartTags = mutableListOf("part", "public", "customers")
    val datasetPartCreateRequest =
        DatasetPartCreateRequest(
            name = datasetPartName,
            sourceName = CUSTOMER_SOURCE_FILE_NAME,
            description = datasetPartDescription,
            tags = datasetPartTags,
            type = DatasetPartTypeEnum.File)

    val datasetName = "Customer Dataset"
    val datasetDescription = "Dataset for customers"
    val datasetTags = mutableListOf("dataset", "public", "customers")
    val datasetCreateRequest =
        DatasetCreateRequest(
            name = datasetName,
            description = datasetDescription,
            tags = datasetTags,
            parts = mutableListOf(datasetPartCreateRequest))

    val resourceTestFile = resourceLoader.getResource("classpath:/$CUSTOMER_SOURCE_FILE_NAME").file

    val fileToSend = FileInputStream(resourceTestFile)

    val mockMultipartFile =
        MockMultipartFile(
            "files",
            CUSTOMER_SOURCE_FILE_NAME,
            MediaType.MULTIPART_FORM_DATA_VALUE,
            IOUtils.toByteArray(fileToSend))

    val createdDataset =
        datasetApiService.createDataset(
            organizationSaved.id,
            workspaceSaved.id,
            datasetCreateRequest,
            arrayOf(mockMultipartFile))

    val datasetPartFilePath = constructFilePathForDatasetPart(createdDataset, 0)
    assertTrue(s3Template.objectExists(csmPlatformProperties.s3.bucketName, datasetPartFilePath))
    val downloadFile = s3Template.download(csmPlatformProperties.s3.bucketName, datasetPartFilePath)

    val expectedText = FileInputStream(resourceTestFile).bufferedReader().use { it.readText() }
    val retrievedText =
        InputStreamResource(downloadFile).inputStream.bufferedReader().use { it.readText() }

    assertEquals(expectedText, retrievedText)
    assertNotNull(createdDataset)
    assertEquals(datasetName, createdDataset.name)
    assertEquals(datasetDescription, createdDataset.description)
    assertEquals(datasetTags, createdDataset.tags)
    assertEquals(1, createdDataset.parts.size)
    val createdDatasetPart = createdDataset.parts[0]
    assertNotNull(createdDatasetPart)
    assertEquals(datasetPartName, createdDatasetPart.name)
    assertEquals(datasetPartDescription, createdDatasetPart.description)
    assertEquals(datasetPartTags, createdDatasetPart.tags)
    assertEquals(CUSTOMER_SOURCE_FILE_NAME, createdDatasetPart.sourceName)
    assertEquals(DatasetPartTypeEnum.File, createdDatasetPart.type)
  }

  @Test
  fun `test createDataset with two Files dataset part`() {

    logger.info("Test createDataset with two file dataset parts")

    val customerPartName = "Customers list"
    val customerPartDescription = "List of customers"
    val customerPartTags = mutableListOf("part", "public", "customers")
    val customerPartCreateRequest =
        DatasetPartCreateRequest(
            name = customerPartName,
            sourceName = CUSTOMER_SOURCE_FILE_NAME,
            description = customerPartDescription,
            tags = customerPartTags,
            type = DatasetPartTypeEnum.File)

    val inventoryPartName = "Product list"
    val inventoryPartDescription = "List of Product"
    val inventoryPartTags = mutableListOf("part", "public", "product")
    val inventoryPartCreateRequest =
        DatasetPartCreateRequest(
            name = inventoryPartName,
            sourceName = INVENTORY_SOURCE_FILE_NAME,
            description = inventoryPartDescription,
            tags = inventoryPartTags,
            type = DatasetPartTypeEnum.File)

    val datasetName = "Shop Dataset"
    val datasetDescription = "Dataset for shop"
    val datasetTags = mutableListOf("dataset", "public", "shop")
    val datasetCreateRequest =
        DatasetCreateRequest(
            name = datasetName,
            description = datasetDescription,
            tags = datasetTags,
            parts = mutableListOf(customerPartCreateRequest, inventoryPartCreateRequest))

    val customerTestFile = resourceLoader.getResource("classpath:/$CUSTOMER_SOURCE_FILE_NAME").file
    val inventoryTestFile =
        resourceLoader.getResource("classpath:/$INVENTORY_SOURCE_FILE_NAME").file

    val customerFileToSend = FileInputStream(customerTestFile)
    val inventoryFileToSend = FileInputStream(inventoryTestFile)

    val customerMockMultipartFile =
        MockMultipartFile(
            "files",
            CUSTOMER_SOURCE_FILE_NAME,
            MediaType.MULTIPART_FORM_DATA_VALUE,
            IOUtils.toByteArray(customerFileToSend))

    val inventoryMockMultipartFile =
        MockMultipartFile(
            "files",
            INVENTORY_SOURCE_FILE_NAME,
            MediaType.MULTIPART_FORM_DATA_VALUE,
            IOUtils.toByteArray(inventoryFileToSend))

    val createdDataset =
        datasetApiService.createDataset(
            organizationSaved.id,
            workspaceSaved.id,
            datasetCreateRequest,
            arrayOf(customerMockMultipartFile, inventoryMockMultipartFile))

    val customerPartFilePath = constructFilePathForDatasetPart(createdDataset, 0)
    val inventoryPartFilePath = constructFilePathForDatasetPart(createdDataset, 1)
    assertTrue(s3Template.objectExists(csmPlatformProperties.s3.bucketName, customerPartFilePath))
    assertTrue(s3Template.objectExists(csmPlatformProperties.s3.bucketName, inventoryPartFilePath))

    val customerFownloadFile =
        s3Template.download(csmPlatformProperties.s3.bucketName, customerPartFilePath)
    val inventoryFownloadFile =
        s3Template.download(csmPlatformProperties.s3.bucketName, inventoryPartFilePath)

    val customerExpectedText =
        FileInputStream(customerTestFile).bufferedReader().use { it.readText() }
    val customerRetrievedText =
        InputStreamResource(customerFownloadFile).inputStream.bufferedReader().use { it.readText() }

    val inventoryExpectedText =
        FileInputStream(inventoryTestFile).bufferedReader().use { it.readText() }
    val inventoryRetrievedText =
        InputStreamResource(inventoryFownloadFile).inputStream.bufferedReader().use {
          it.readText()
        }

    assertEquals(inventoryExpectedText, inventoryRetrievedText)
    assertEquals(customerExpectedText, customerRetrievedText)
    assertNotNull(createdDataset)
    assertEquals(datasetName, createdDataset.name)
    assertEquals(datasetDescription, createdDataset.description)
    assertEquals(datasetTags, createdDataset.tags)
    assertEquals(2, createdDataset.parts.size)
    val customerCreatedDatasetPart = createdDataset.parts[0]
    assertNotNull(customerCreatedDatasetPart)
    assertEquals(customerPartName, customerCreatedDatasetPart.name)
    assertEquals(customerPartDescription, customerCreatedDatasetPart.description)
    assertEquals(customerPartTags, customerCreatedDatasetPart.tags)
    assertEquals(CUSTOMER_SOURCE_FILE_NAME, customerCreatedDatasetPart.sourceName)
    assertEquals(DatasetPartTypeEnum.File, customerCreatedDatasetPart.type)
    val inventoryCreatedDatasetPart = createdDataset.parts[1]
    assertNotNull(inventoryCreatedDatasetPart)
    assertEquals(inventoryPartName, inventoryCreatedDatasetPart.name)
    assertEquals(inventoryPartDescription, inventoryCreatedDatasetPart.description)
    assertEquals(inventoryPartTags, inventoryCreatedDatasetPart.tags)
    assertEquals(INVENTORY_SOURCE_FILE_NAME, inventoryCreatedDatasetPart.sourceName)
    assertEquals(DatasetPartTypeEnum.File, inventoryCreatedDatasetPart.type)
  }

  @Test
  fun `test createDataset with empty files`() {

    logger.info("Test createDataset with a dataset part")

    val datasetCreateRequest = DatasetCreateRequest(name = "Dataset Test")

    val datasetCreated =
        datasetApiService.createDataset(
            organizationSaved.id, workspaceSaved.id, datasetCreateRequest, arrayOf())
    assertNotNull(datasetCreated)
  }

  @Test
  fun `test createDataset with blank name`() {

    logger.info("Test createDataset with a dataset part")

    val datasetName = ""
    val datasetCreateRequest = DatasetCreateRequest(name = datasetName)

    val resourceTestFile = resourceLoader.getResource("classpath:/$CUSTOMER_SOURCE_FILE_NAME").file

    val fileToSend = FileInputStream(resourceTestFile)

    val mockMultipartFile =
        MockMultipartFile(
            "files",
            CUSTOMER_SOURCE_FILE_NAME,
            MediaType.MULTIPART_FORM_DATA_VALUE,
            IOUtils.toByteArray(fileToSend))

    val exception =
        assertThrows<IllegalArgumentException> {
          datasetApiService.createDataset(
              organizationSaved.id,
              workspaceSaved.id,
              datasetCreateRequest,
              arrayOf(mockMultipartFile))
        }
    assertEquals("Dataset name must not be blank", exception.message)
  }

  @Test
  fun `test createDataset with more parts than files`() {

    logger.info("Test createDataset with a dataset part")

    val datasetCreateRequest =
        DatasetCreateRequest(
            name = "Dataset Test",
            parts =
                mutableListOf(
                    DatasetPartCreateRequest(name = "part1", sourceName = "filepart1.csv"),
                ))

    val exception =
        assertThrows<IllegalArgumentException> {
          datasetApiService.createDataset(
              organizationSaved.id, workspaceSaved.id, datasetCreateRequest, arrayOf())
        }
    assertEquals(
        "Number of files must be equal to the number of parts if specified. 0 != 1",
        exception.message)
  }

  @Test
  fun `test createDataset with more files than part`() {

    logger.info("Test createDataset with a dataset part")

    val datasetCreateRequest = DatasetCreateRequest(name = "Dataset Test")

    val resourceTestFile = resourceLoader.getResource("classpath:/$CUSTOMER_SOURCE_FILE_NAME").file

    val fileToSend = FileInputStream(resourceTestFile)

    val mockMultipartFile =
        MockMultipartFile(
            "files",
            CUSTOMER_SOURCE_FILE_NAME,
            MediaType.MULTIPART_FORM_DATA_VALUE,
            IOUtils.toByteArray(fileToSend))

    val exception =
        assertThrows<IllegalArgumentException> {
          datasetApiService.createDataset(
              organizationSaved.id,
              workspaceSaved.id,
              datasetCreateRequest,
              arrayOf(mockMultipartFile))
        }
    assertEquals(
        "Number of files must be equal to the number of parts if specified. 1 != 0",
        exception.message)
  }

  @Test
  fun `test createDataset with wrong dataset part source name`() {

    logger.info("Test createDataset with a dataset part")

    val datasetCreateRequest =
        DatasetCreateRequest(
            name = "Dataset Test",
            parts =
                mutableListOf(
                    DatasetPartCreateRequest(name = "part1", sourceName = "wrongname.csv")))

    val resourceTestFile = resourceLoader.getResource("classpath:/$CUSTOMER_SOURCE_FILE_NAME").file

    val fileToSend = FileInputStream(resourceTestFile)

    val mockMultipartFile =
        MockMultipartFile(
            "files",
            CUSTOMER_SOURCE_FILE_NAME,
            MediaType.MULTIPART_FORM_DATA_VALUE,
            IOUtils.toByteArray(fileToSend))

    val exception =
        assertThrows<IllegalArgumentException> {
          datasetApiService.createDataset(
              organizationSaved.id,
              workspaceSaved.id,
              datasetCreateRequest,
              arrayOf(mockMultipartFile))
        }
    assertEquals(
        "All files must have the same name as their corresponding Dataset Part. " +
            "Files: [customers.csv]. " +
            "Dataset Parts: [wrongname.csv].",
        exception.message)
  }

  @Test
  fun `test deleteDataset with no dataset parts`() {

    logger.info("Test deleteDataset with no dataset parts")

    val datasetCreateRequest = DatasetCreateRequest(name = "Dataset Test")

    val createDataset =
        datasetApiService.createDataset(
            organizationSaved.id, workspaceSaved.id, datasetCreateRequest, arrayOf())

    val datasetId = createDataset.id
    datasetApiService.deleteDataset(organizationSaved.id, workspaceSaved.id, datasetId)

    val exception =
        assertThrows<CsmResourceNotFoundException> {
          datasetApiService.getDataset(organizationSaved.id, workspaceSaved.id, datasetId)
        }

    assertEquals(
        "Dataset $datasetId not found " +
            "in organization ${organizationSaved.id} " +
            "and workspace ${workspaceSaved.id}",
        exception.message)
  }

  @Test
  fun `test deleteDataset with a File dataset part`() {

    logger.info("Test deleteDataset with a File dataset part")

    val datasetPartName = "Customers list"
    val datasetPartDescription = "List of customers"
    val datasetPartTags = mutableListOf("part", "public", "customers")
    val datasetPartCreateRequest =
        DatasetPartCreateRequest(
            name = datasetPartName,
            sourceName = CUSTOMER_SOURCE_FILE_NAME,
            description = datasetPartDescription,
            tags = datasetPartTags,
            type = DatasetPartTypeEnum.File)

    val datasetName = "Customer Dataset"
    val datasetDescription = "Dataset for customers"
    val datasetTags = mutableListOf("dataset", "public", "customers")
    val datasetCreateRequest =
        DatasetCreateRequest(
            name = datasetName,
            description = datasetDescription,
            tags = datasetTags,
            parts = mutableListOf(datasetPartCreateRequest))

    val resourceTestFile = resourceLoader.getResource("classpath:/$CUSTOMER_SOURCE_FILE_NAME").file

    val fileToSend = FileInputStream(resourceTestFile)

    val mockMultipartFile =
        MockMultipartFile(
            "files",
            CUSTOMER_SOURCE_FILE_NAME,
            MediaType.MULTIPART_FORM_DATA_VALUE,
            IOUtils.toByteArray(fileToSend))

    val createdDataset =
        datasetApiService.createDataset(
            organizationSaved.id,
            workspaceSaved.id,
            datasetCreateRequest,
            arrayOf(mockMultipartFile))

    val fileKeyPath = constructFilePathForDatasetPart(createdDataset, 0)

    assertTrue(s3Template.objectExists(csmPlatformProperties.s3.bucketName, fileKeyPath))

    val datasetId = createdDataset.id
    datasetApiService.deleteDataset(organizationSaved.id, workspaceSaved.id, datasetId)

    val exception =
        assertThrows<CsmResourceNotFoundException> {
          datasetApiService.getDataset(organizationSaved.id, workspaceSaved.id, datasetId)
        }

    assertEquals(
        "Dataset $datasetId not found " +
            "in organization ${organizationSaved.id} " +
            "and workspace ${workspaceSaved.id}",
        exception.message)

    assertFalse(s3Template.objectExists(csmPlatformProperties.s3.bucketName, fileKeyPath))
  }

  @Test
  fun `test getDataset with no dataset part`() {

    logger.info("test getDataset with no dataset part")

    val datasetCreateRequest = DatasetCreateRequest(name = "Dataset Test")

    val createDataset =
        datasetApiService.createDataset(
            organizationSaved.id, workspaceSaved.id, datasetCreateRequest, arrayOf())

    val retrievedDataset =
        datasetApiService.getDataset(organizationSaved.id, workspaceSaved.id, createDataset.id)

    assertNotNull(retrievedDataset)
    assertEquals(createDataset, retrievedDataset)
  }

  @Test
  fun `test getDataset with dataset parts`() {

    logger.info("test getDataset with dataset parts")

    val datasetPartName = "Customers list"
    val datasetPartDescription = "List of customers"
    val datasetPartTags = mutableListOf("part", "public", "customers")
    val datasetPartCreateRequest =
        DatasetPartCreateRequest(
            name = datasetPartName,
            sourceName = CUSTOMER_SOURCE_FILE_NAME,
            description = datasetPartDescription,
            tags = datasetPartTags,
            type = DatasetPartTypeEnum.File)

    val datasetName = "Customer Dataset"
    val datasetDescription = "Dataset for customers"
    val datasetTags = mutableListOf("dataset", "public", "customers")
    val datasetCreateRequest =
        DatasetCreateRequest(
            name = datasetName,
            description = datasetDescription,
            tags = datasetTags,
            parts = mutableListOf(datasetPartCreateRequest))

    val resourceTestFile = resourceLoader.getResource("classpath:/$CUSTOMER_SOURCE_FILE_NAME").file

    val fileToSend = FileInputStream(resourceTestFile)

    val mockMultipartFile =
        MockMultipartFile(
            "files",
            CUSTOMER_SOURCE_FILE_NAME,
            MediaType.MULTIPART_FORM_DATA_VALUE,
            IOUtils.toByteArray(fileToSend))

    val createdDataset =
        datasetApiService.createDataset(
            organizationSaved.id,
            workspaceSaved.id,
            datasetCreateRequest,
            arrayOf(mockMultipartFile))

    val retrievedDataset =
        datasetApiService.getDataset(organizationSaved.id, workspaceSaved.id, createdDataset.id)

    assertNotNull(retrievedDataset)
    assertEquals(createdDataset, retrievedDataset)
  }

  @Test
  fun `test getDatasetAccessControl`() {

    logger.info("test getDatasetAccessControl")

    val datasetCreateRequest = DatasetCreateRequest(name = "Dataset Test")

    val createDataset =
        datasetApiService.createDataset(
            organizationSaved.id, workspaceSaved.id, datasetCreateRequest, arrayOf())

    val retrievedDatasetAccessControl =
        datasetApiService.getDatasetAccessControl(
            organizationSaved.id, workspaceSaved.id, createDataset.id, CONNECTED_ADMIN_USER)

    assertNotNull(retrievedDatasetAccessControl)
    assertEquals(CONNECTED_ADMIN_USER, retrievedDatasetAccessControl.id)
    assertEquals(ROLE_ADMIN, retrievedDatasetAccessControl.role)
  }

  @Test
  fun `test listDatasetSecurityUsers`() {

    logger.info("test listDatasetSecurityUsers")

    val datasetCreateRequest = DatasetCreateRequest(name = "Dataset Test")

    val createDataset =
        datasetApiService.createDataset(
            organizationSaved.id, workspaceSaved.id, datasetCreateRequest, arrayOf())

    val retrievedDatasetListUsers =
        datasetApiService.listDatasetSecurityUsers(
            organizationSaved.id, workspaceSaved.id, createDataset.id)

    assertNotNull(retrievedDatasetListUsers)
    assertTrue(retrievedDatasetListUsers.size == 1)
    assertEquals(mutableListOf(CONNECTED_ADMIN_USER), retrievedDatasetListUsers)
  }

  @Test
  fun `test listDatasets`() {

    logger.info("test listDatasets")

    val datasetCreateRequest = DatasetCreateRequest(name = "Dataset Test")

    val createDataset =
        datasetApiService.createDataset(
            organizationSaved.id, workspaceSaved.id, datasetCreateRequest, arrayOf())

    val datasetCreateRequest2 = DatasetCreateRequest(name = "Dataset Test 2")

    val createDataset2 =
        datasetApiService.createDataset(
            organizationSaved.id, workspaceSaved.id, datasetCreateRequest2, arrayOf())

    var retrievedDatasetList =
        datasetApiService.listDatasets(organizationSaved.id, workspaceSaved.id, null, null)

    assertNotNull(retrievedDatasetList)
    assertTrue(retrievedDatasetList.size == 2)
    assertEquals(mutableListOf(createDataset, createDataset2), retrievedDatasetList)

    retrievedDatasetList =
        datasetApiService.listDatasets(organizationSaved.id, workspaceSaved.id, 0, 1)

    assertNotNull(retrievedDatasetList)
    assertTrue(retrievedDatasetList.size == 1)
    assertEquals(mutableListOf(createDataset), retrievedDatasetList)

    retrievedDatasetList =
        datasetApiService.listDatasets(organizationSaved.id, workspaceSaved.id, 1, 5)

    assertNotNull(retrievedDatasetList)
    assertTrue(retrievedDatasetList.isEmpty())
  }

  @Test
  fun `test deleteDatasetAccessControl`() {

    logger.info("test deleteDatasetAccessControl")

    val datasetCreateRequest =
        DatasetCreateRequest(
            name = "Dataset Test",
            security =
                DatasetSecurity(
                    default = ROLE_NONE,
                    accessControlList =
                        mutableListOf(
                            DatasetAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                            DatasetAccessControl(CONNECTED_DEFAULT_USER, ROLE_EDITOR))))

    val createDataset =
        datasetApiService.createDataset(
            organizationSaved.id, workspaceSaved.id, datasetCreateRequest, arrayOf())

    datasetApiService.deleteDatasetAccessControl(
        organizationSaved.id, workspaceSaved.id, createDataset.id, CONNECTED_DEFAULT_USER)

    val retrievedDatasetAccessControl =
        datasetApiService.getDatasetAccessControl(
            organizationSaved.id, workspaceSaved.id, createDataset.id, CONNECTED_ADMIN_USER)

    assertNotNull(retrievedDatasetAccessControl)
    assertEquals(CONNECTED_ADMIN_USER, retrievedDatasetAccessControl.id)
    assertEquals(ROLE_ADMIN, retrievedDatasetAccessControl.role)
  }

  @Test
  fun `test updateDatasetDefaultSecurity`() {

    logger.info("test updateDatasetDefaultSecurity")

    val datasetCreateRequest =
        DatasetCreateRequest(
            name = "Dataset Test",
            security =
                DatasetSecurity(
                    default = ROLE_NONE,
                    accessControlList =
                        mutableListOf(
                            DatasetAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                            DatasetAccessControl(CONNECTED_DEFAULT_USER, ROLE_EDITOR))))

    val createDataset =
        datasetApiService.createDataset(
            organizationSaved.id, workspaceSaved.id, datasetCreateRequest, arrayOf())

    val newDatasetSecurity =
        datasetApiService.updateDatasetDefaultSecurity(
            organizationSaved.id,
            workspaceSaved.id,
            createDataset.id,
            DatasetRole(role = ROLE_EDITOR))
    assertNotNull(newDatasetSecurity)
    assertEquals(ROLE_EDITOR, newDatasetSecurity.default)
    val accessControlList = newDatasetSecurity.accessControlList
    assertEquals(2, accessControlList.size)
    assertEquals(CONNECTED_ADMIN_USER, accessControlList[0].id)
    assertEquals(ROLE_ADMIN, accessControlList[0].role)
    assertEquals(CONNECTED_DEFAULT_USER, accessControlList[1].id)
    assertEquals(ROLE_EDITOR, accessControlList[1].role)
  }

  @Test
  fun `test updateDatasetAccessControl`() {

    logger.info("test updateDatasetAccessControl")

    val datasetCreateRequest =
        DatasetCreateRequest(
            name = "Dataset Test",
            security =
                DatasetSecurity(
                    default = ROLE_NONE,
                    accessControlList =
                        mutableListOf(
                            DatasetAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                            DatasetAccessControl(CONNECTED_DEFAULT_USER, ROLE_EDITOR))))

    val createDataset =
        datasetApiService.createDataset(
            organizationSaved.id, workspaceSaved.id, datasetCreateRequest, arrayOf())

    val newDatasetAccessControl =
        datasetApiService.updateDatasetAccessControl(
            organizationSaved.id,
            workspaceSaved.id,
            createDataset.id,
            CONNECTED_DEFAULT_USER,
            DatasetRole(role = ROLE_ADMIN))
    assertNotNull(newDatasetAccessControl)
    assertEquals(ROLE_ADMIN, newDatasetAccessControl.role)
    assertEquals(CONNECTED_DEFAULT_USER, newDatasetAccessControl.id)
  }

  private fun constructFilePathForDatasetPart(createdDataset: Dataset, partIndex: Int): String =
      "${createdDataset.organizationId}/${createdDataset.workspaceId}/${createdDataset.id}/${createdDataset.parts[partIndex].id}/${createdDataset.parts[partIndex].sourceName}"

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
      userName: String = CONNECTED_ADMIN_USER,
      role: String = ROLE_ADMIN
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
                          WorkspaceAccessControl(CONNECTED_DEFAULT_USER, "viewer"))))
}
