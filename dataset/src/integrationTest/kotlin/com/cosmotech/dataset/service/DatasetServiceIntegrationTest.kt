// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.service

import com.cosmotech.common.config.CsmPlatformProperties
import com.cosmotech.common.config.existTable
import com.cosmotech.common.exceptions.CsmAccessForbiddenException
import com.cosmotech.common.exceptions.CsmResourceNotFoundException
import com.cosmotech.common.rbac.ROLE_ADMIN
import com.cosmotech.common.rbac.ROLE_EDITOR
import com.cosmotech.common.rbac.ROLE_NONE
import com.cosmotech.common.rbac.ROLE_USER
import com.cosmotech.common.tests.CsmTestBase
import com.cosmotech.common.utils.getCurrentAccountGroups
import com.cosmotech.common.utils.getCurrentAccountIdentifier
import com.cosmotech.common.utils.getCurrentAuthenticatedRoles
import com.cosmotech.common.utils.getCurrentAuthenticatedUserName
import com.cosmotech.dataset.DatasetApiServiceInterface
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.dataset.domain.DatasetAccessControl
import com.cosmotech.dataset.domain.DatasetCreateRequest
import com.cosmotech.dataset.domain.DatasetPart
import com.cosmotech.dataset.domain.DatasetPartCreateRequest
import com.cosmotech.dataset.domain.DatasetPartTypeEnum
import com.cosmotech.dataset.domain.DatasetPartUpdateRequest
import com.cosmotech.dataset.domain.DatasetRole
import com.cosmotech.dataset.domain.DatasetSecurity
import com.cosmotech.dataset.domain.DatasetUpdateRequest
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
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
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
import org.springframework.jdbc.core.JdbcTemplate
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
  val EMPTY_SOURCE_FILE_NAME = "emptyfile.csv"
  val CUSTOMER_SOURCE_FILE_NAME = "customers.csv"
  val CUSTOMER_50K_SOURCE_FILE_NAME = "customers_50K.csv"
  val CUSTOMERS_WITH_QUOTES_SOURCE_FILE_NAME = "customerswithquotes.csv"
  val CUSTOMERS_WITH_DOUBLE_QUOTES_SOURCE_FILE_NAME = "customerswithdoublequotes.csv"
  val CUSTOMERS_1000_SOURCE_FILE_NAME = "customers1000.csv"
  val CUSTOMERS_10000_SOURCE_FILE_NAME = "customers10000.csv"
  val UNALLOWED_CHAR_HEADERS_FILE_NAME = "filewithunallowedcharinheaders.csv"
  val SAME_HEADERS_FILE_NAME = "filewithsameheaders.csv"
  val UNALLOWED_MIME_TYPE_SOURCE_FILE_NAME = "wrong_mimetype.yaml"
  val INVENTORY_SOURCE_FILE_NAME = "product_inventory.csv"
  val WRONG_ORIGINAL_FILE_NAME = "../../wrong_name_pattern.csv"
  val defaultGroup = listOf("myTestGroup")

  private val logger = LoggerFactory.getLogger(DatasetServiceIntegrationTest::class.java)

  @Autowired lateinit var rediSearchIndexer: RediSearchIndexer
  @Autowired lateinit var organizationApiService: OrganizationApiServiceInterface
  @Autowired lateinit var workspaceApiService: WorkspaceApiServiceInterface
  @Autowired lateinit var solutionApiService: SolutionApiServiceInterface
  @Autowired lateinit var csmPlatformProperties: CsmPlatformProperties
  @Autowired lateinit var datasetApiService: DatasetApiServiceInterface
  @Autowired lateinit var s3Template: S3Template
  @Autowired lateinit var resourceLoader: ResourceLoader
  @Autowired lateinit var writerJdbcTemplate: JdbcTemplate

  lateinit var organization: OrganizationCreateRequest
  lateinit var workspace: WorkspaceCreateRequest
  lateinit var solution: SolutionCreateRequest
  lateinit var organizationSaved: Organization
  lateinit var workspaceSaved: Workspace
  lateinit var solutionSaved: Solution

  @BeforeEach
  fun setUp() {
    mockkStatic("com.cosmotech.common.utils.SecurityUtilsKt")
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
    every { getCurrentAccountGroups(any()) } returns defaultGroup
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
  fun `test createDataset with no dataset part`() {

    val datasetName = "Customer Dataset"
    val datasetDescription = "Dataset for customers"
    val datasetTags = mutableListOf("dataset", "public", "customers")
    val datasetCreateRequest =
        DatasetCreateRequest(
            name = datasetName,
            description = datasetDescription,
            tags = datasetTags,
            runnerId = "r-12354678910")

    val createdDataset =
        datasetApiService.createDataset(
            organizationSaved.id, workspaceSaved.id, datasetCreateRequest, arrayOf())

    assertNotNull(createdDataset)
    assertEquals(datasetName, createdDataset.name)
    assertEquals(datasetDescription, createdDataset.description)
    assertEquals(datasetTags, createdDataset.tags)
    assertEquals("r-12354678910", createdDataset.createInfo.runnerId)
    assertEquals(0, createdDataset.parts.size)
  }

  @Test
  fun `test createDataset with one File dataset part`() {

    val datasetPartName = "Customers list"
    val datasetPartDescription = "List of customers"
    val datasetPartTags = mutableListOf("part", "public", "customers")
    val datasetPartAdditionalData =
        mutableMapOf("part" to "data", "complex" to mutableMapOf("nested" to "data"))
    val datasetPartCreateRequest =
        DatasetPartCreateRequest(
            name = datasetPartName,
            sourceName = CUSTOMER_SOURCE_FILE_NAME,
            description = datasetPartDescription,
            tags = datasetPartTags,
            additionalData = datasetPartAdditionalData,
            type = DatasetPartTypeEnum.File)

    val datasetName = "Customer Dataset"
    val datasetDescription = "Dataset for customers"
    val datasetTags = mutableListOf("dataset", "public", "customers")
    val datasetAdditionalData =
        mutableMapOf("dataset" to "data", "complex" to mutableMapOf("nested" to "data"))
    val datasetCreateRequest =
        DatasetCreateRequest(
            name = datasetName,
            description = datasetDescription,
            tags = datasetTags,
            additionalData = datasetAdditionalData,
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
    assertEquals(datasetAdditionalData, createdDataset.additionalData)
    assertEquals(1, createdDataset.parts.size)
    val createdDatasetPart = createdDataset.parts[0]
    assertNotNull(createdDatasetPart)
    assertEquals(datasetPartName, createdDatasetPart.name)
    assertEquals(datasetPartDescription, createdDatasetPart.description)
    assertEquals(datasetPartTags, createdDatasetPart.tags)
    assertEquals(datasetPartAdditionalData, createdDatasetPart.additionalData)
    assertEquals(CUSTOMER_SOURCE_FILE_NAME, createdDatasetPart.sourceName)
    assertEquals(DatasetPartTypeEnum.File, createdDatasetPart.type)
  }

  @Test
  fun `test createDataset with a File dataset part with unallowed mimetype`() {

    val datasetPartCreateRequest =
        DatasetPartCreateRequest(
            name = "Customers list",
            sourceName = UNALLOWED_MIME_TYPE_SOURCE_FILE_NAME,
            description = "List of customers",
            tags = mutableListOf("part", "public", "customers"),
            type = DatasetPartTypeEnum.File)

    val datasetCreateRequest =
        DatasetCreateRequest(
            name = "Customer Dataset",
            description = "Dataset for customers",
            tags = mutableListOf("dataset", "public", "customers"),
            parts = mutableListOf(datasetPartCreateRequest))

    val resourceTestFile =
        resourceLoader.getResource("classpath:/$UNALLOWED_MIME_TYPE_SOURCE_FILE_NAME").file

    val fileToSend = FileInputStream(resourceTestFile)

    val mockMultipartFile =
        MockMultipartFile(
            "files",
            UNALLOWED_MIME_TYPE_SOURCE_FILE_NAME,
            MediaType.MULTIPART_FORM_DATA_VALUE,
            IOUtils.toByteArray(fileToSend))

    val exception =
        assertThrows<CsmAccessForbiddenException> {
          datasetApiService.createDataset(
              organizationSaved.id,
              workspaceSaved.id,
              datasetCreateRequest,
              arrayOf(mockMultipartFile))
        }

    assertEquals(
        "MIME type text/x-yaml for file $UNALLOWED_MIME_TYPE_SOURCE_FILE_NAME is not authorized.",
        exception.message)
  }

  @Test
  fun `test createDataset with a File dataset part with unallowed name`() {

    val datasetPartCreateRequest =
        DatasetPartCreateRequest(
            name = "Customers list",
            sourceName = WRONG_ORIGINAL_FILE_NAME,
            description = "List of customers",
            tags = mutableListOf("part", "public", "customers"),
            type = DatasetPartTypeEnum.File)

    val datasetCreateRequest =
        DatasetCreateRequest(
            name = "Customer Dataset",
            description = "Dataset for customers",
            tags = mutableListOf("dataset", "public", "customers"),
            parts = mutableListOf(datasetPartCreateRequest))

    val mockMultipartFile =
        MockMultipartFile(
            "files",
            WRONG_ORIGINAL_FILE_NAME,
            MediaType.MULTIPART_FORM_DATA_VALUE,
            InputStream.nullInputStream())

    val exception =
        assertThrows<IllegalArgumentException> {
          datasetApiService.createDataset(
              organizationSaved.id,
              workspaceSaved.id,
              datasetCreateRequest,
              arrayOf(mockMultipartFile))
        }

    assertEquals(
        "Invalid filename: '$WRONG_ORIGINAL_FILE_NAME'. File name should neither contains '..' nor starts by '/'.",
        exception.message)
  }

  @Test
  fun `test createDataset with two Files dataset part`() {

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

    val customerDownloadFile =
        s3Template.download(csmPlatformProperties.s3.bucketName, customerPartFilePath)
    val inventoryDownloadFile =
        s3Template.download(csmPlatformProperties.s3.bucketName, inventoryPartFilePath)

    val customerExpectedText =
        FileInputStream(customerTestFile).bufferedReader().use { it.readText() }
    val customerRetrievedText =
        InputStreamResource(customerDownloadFile).inputStream.bufferedReader().use { it.readText() }

    val inventoryExpectedText =
        FileInputStream(inventoryTestFile).bufferedReader().use { it.readText() }
    val inventoryRetrievedText =
        InputStreamResource(inventoryDownloadFile).inputStream.bufferedReader().use {
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
  fun `test createDataset with two dataset parts and same multipart file name`() {

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

    val datasetName = "Shop Dataset"
    val datasetDescription = "Dataset for shop"
    val datasetTags = mutableListOf("dataset", "public", "shop")
    val datasetCreateRequest =
        DatasetCreateRequest(
            name = datasetName,
            description = datasetDescription,
            tags = datasetTags,
            parts =
                mutableListOf(
                    customerPartCreateRequest,
                    DatasetPartCreateRequest(
                        name = "Part create request 2", sourceName = "anotherFile.txt")))

    val customerTestFile = resourceLoader.getResource("classpath:/$CUSTOMER_SOURCE_FILE_NAME").file

    val customerFileToSend = FileInputStream(customerTestFile)

    val customerMockMultipartFile =
        MockMultipartFile(
            "files",
            CUSTOMER_SOURCE_FILE_NAME,
            MediaType.MULTIPART_FORM_DATA_VALUE,
            IOUtils.toByteArray(customerFileToSend))

    val customerMockMultipartFile2 =
        MockMultipartFile(
            "files",
            CUSTOMER_SOURCE_FILE_NAME,
            MediaType.MULTIPART_FORM_DATA_VALUE,
            InputStream.nullInputStream())

    val exception =
        assertThrows<IllegalArgumentException> {
          datasetApiService.createDataset(
              organizationSaved.id,
              workspaceSaved.id,
              datasetCreateRequest,
              arrayOf(customerMockMultipartFile, customerMockMultipartFile2))
        }
    assertEquals(
        "Part File names should be unique during dataset creation. " +
            "Multipart file names: [$CUSTOMER_SOURCE_FILE_NAME, $CUSTOMER_SOURCE_FILE_NAME]. " +
            "Dataset parts source names: [$CUSTOMER_SOURCE_FILE_NAME, anotherFile.txt].",
        exception.message)
  }

  @Test
  fun `test createDataset with empty files`() {

    val datasetCreateRequest = DatasetCreateRequest(name = "Dataset Test")

    val datasetCreated =
        datasetApiService.createDataset(
            organizationSaved.id, workspaceSaved.id, datasetCreateRequest, arrayOf())
    assertNotNull(datasetCreated)
  }

  @Test
  fun `test createDataset with blank name`() {

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
        "All files must have the same name as corresponding sourceName in a Dataset Part. " +
            "Multipart file names: [customers.csv]. " +
            "Dataset parts source names: [wrongname.csv].",
        exception.message)
  }

  @Test
  fun `test deleteDataset with no dataset parts`() {

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

  @Test
  fun `test searchDatasets`() {

    val datasetName = "Customer Dataset"
    val datasetDescription = "Dataset for customers"
    val datasetTags = mutableListOf("dataset", "public", "customers")
    val datasetCreateRequest =
        DatasetCreateRequest(
            name = datasetName, description = datasetDescription, tags = datasetTags)

    datasetApiService.createDataset(
        organizationSaved.id, workspaceSaved.id, datasetCreateRequest, arrayOf())

    datasetApiService.createDataset(
        organizationSaved.id,
        workspaceSaved.id,
        DatasetCreateRequest(
            name = "Other Dataset",
            description = "Other Dataset ",
            tags = mutableListOf("dataset", "public", "other")),
        arrayOf())

    val foundDatasets =
        datasetApiService.searchDatasets(
            organizationSaved.id, workspaceSaved.id, listOf("customers"), null, null)

    assertTrue(foundDatasets.size == 1)
    assertEquals(datasetName, foundDatasets[0].name)
    assertEquals(datasetDescription, foundDatasets[0].description)
    assertEquals(datasetTags, foundDatasets[0].tags)
    assertEquals(0, foundDatasets[0].parts.size)
  }

  @Test
  fun `test createDatasetPart type FILE`() {

    val datasetCreateRequest = DatasetCreateRequest(name = "Dataset Test")

    val createDataset =
        datasetApiService.createDataset(
            organizationSaved.id, workspaceSaved.id, datasetCreateRequest, arrayOf())

    assertTrue(createDataset.parts.isEmpty())

    val resourceTestFile = resourceLoader.getResource("classpath:/$CUSTOMER_SOURCE_FILE_NAME").file

    val fileToSend = FileInputStream(resourceTestFile)

    val mockMultipartFile =
        MockMultipartFile(
            "file",
            CUSTOMER_SOURCE_FILE_NAME,
            MediaType.MULTIPART_FORM_DATA_VALUE,
            IOUtils.toByteArray(fileToSend))

    val datasetPartName = "Customer list"
    val datasetPartDescription = "List of customers"
    val datasetPartTags = mutableListOf("part", "public", "customers")
    val datasetPartAdditionalData =
        mutableMapOf("part" to "data", "complex" to mutableMapOf("nested" to "data"))

    val createDatasetPart =
        datasetApiService.createDatasetPart(
            organizationSaved.id,
            workspaceSaved.id,
            createDataset.id,
            mockMultipartFile,
            DatasetPartCreateRequest(
                name = datasetPartName,
                sourceName = CUSTOMER_SOURCE_FILE_NAME,
                description = datasetPartDescription,
                tags = datasetPartTags,
                additionalData = datasetPartAdditionalData,
                type = DatasetPartTypeEnum.File))

    assertNotNull(createDatasetPart)
    assertEquals(datasetPartName, createDatasetPart.name)
    assertEquals(datasetPartDescription, createDatasetPart.description)
    assertEquals(datasetPartTags, createDatasetPart.tags)
    assertEquals(datasetPartAdditionalData, createDatasetPart.additionalData)
    assertEquals(CUSTOMER_SOURCE_FILE_NAME, createDatasetPart.sourceName)

    val retrievedDataset =
        datasetApiService.getDataset(organizationSaved.id, workspaceSaved.id, createDataset.id)

    assertTrue(retrievedDataset.parts.isNotEmpty())
    assertTrue(retrievedDataset.parts.size == 1)
    assertEquals(createDatasetPart, retrievedDataset.parts[0])

    val fileKeyPath = constructFilePathForDatasetPart(retrievedDataset, 0)

    assertTrue(s3Template.objectExists(csmPlatformProperties.s3.bucketName, fileKeyPath))
    val downloadFile = s3Template.download(csmPlatformProperties.s3.bucketName, fileKeyPath)

    val expectedText = FileInputStream(resourceTestFile).bufferedReader().use { it.readText() }
    val retrievedText =
        InputStreamResource(downloadFile).inputStream.bufferedReader().use { it.readText() }

    assertEquals(expectedText, retrievedText)
  }

  @Test
  fun `test createDatasetPart type DB`() {
    testCreateDatasetPartWithSimpleFile(CUSTOMER_SOURCE_FILE_NAME, CUSTOMER_SOURCE_FILE_NAME)
    testCreateDatasetPartWithSimpleFile(
        CUSTOMERS_1000_SOURCE_FILE_NAME, CUSTOMERS_1000_SOURCE_FILE_NAME)
    testCreateDatasetPartWithSimpleFile(
        CUSTOMERS_10000_SOURCE_FILE_NAME, CUSTOMERS_10000_SOURCE_FILE_NAME)
    testCreateDatasetPartWithSimpleFile(
        CUSTOMERS_WITH_QUOTES_SOURCE_FILE_NAME, CUSTOMERS_WITH_QUOTES_SOURCE_FILE_NAME)
    testCreateDatasetPartWithSimpleFile(
        CUSTOMERS_WITH_DOUBLE_QUOTES_SOURCE_FILE_NAME, CUSTOMER_SOURCE_FILE_NAME)
  }

  @Test
  fun `test create Dataset with File and DB parts`() {

    val customers1000File =
        resourceLoader.getResource("classpath:/$CUSTOMERS_1000_SOURCE_FILE_NAME").file

    val customers1000FileToSend = FileInputStream(customers1000File)

    val customers1000MultipartFile =
        MockMultipartFile(
            "file",
            CUSTOMERS_1000_SOURCE_FILE_NAME,
            MediaType.MULTIPART_FORM_DATA_VALUE,
            IOUtils.toByteArray(customers1000FileToSend))

    val customers10000File =
        resourceLoader.getResource("classpath:/$CUSTOMERS_10000_SOURCE_FILE_NAME").file

    val customers10000FileToSend = FileInputStream(customers10000File)

    val customers10000MultipartFile =
        MockMultipartFile(
            "file",
            CUSTOMERS_10000_SOURCE_FILE_NAME,
            MediaType.MULTIPART_FORM_DATA_VALUE,
            IOUtils.toByteArray(customers10000FileToSend))

    val datasetCreateRequest =
        DatasetCreateRequest(
            name = "Dataset Test with File and DB",
            parts =
                mutableListOf(
                    DatasetPartCreateRequest(
                        name = "File part",
                        sourceName = CUSTOMERS_1000_SOURCE_FILE_NAME,
                        type = DatasetPartTypeEnum.File),
                    DatasetPartCreateRequest(
                        name = "DB part",
                        sourceName = CUSTOMERS_10000_SOURCE_FILE_NAME,
                        type = DatasetPartTypeEnum.DB)))

    val createdDataset =
        datasetApiService.createDataset(
            organizationSaved.id,
            workspaceSaved.id,
            datasetCreateRequest,
            arrayOf(customers1000MultipartFile, customers10000MultipartFile))

    val fileDatasetPartId = createdDataset.parts.first { it.type == DatasetPartTypeEnum.File }.id
    val dBDatasetPartId = createdDataset.parts.first { it.type == DatasetPartTypeEnum.DB }.id

    assertTrue(writerJdbcTemplate.existTable(dBDatasetPartId.replace('-', '_')))

    val fileKeyPath =
        "${createdDataset.organizationId}/${createdDataset.workspaceId}/${createdDataset.id}/$fileDatasetPartId"

    assertTrue(s3Template.objectExists(csmPlatformProperties.s3.bucketName, fileKeyPath))

    val datasetPartFile =
        datasetApiService.downloadDatasetPart(
            organizationSaved.id, workspaceSaved.id, createdDataset.id, fileDatasetPartId)
    var expectedText = FileInputStream(customers1000File).bufferedReader().use { it.readText() }
    var retrievedText = datasetPartFile.inputStream.bufferedReader().use { it.readText() }
    assertEquals(expectedText, retrievedText)

    val datasetPartDB =
        datasetApiService.downloadDatasetPart(
            organizationSaved.id, workspaceSaved.id, createdDataset.id, dBDatasetPartId)
    expectedText = FileInputStream(customers10000File).bufferedReader().use { it.readText() }
    retrievedText = datasetPartDB.inputStream.bufferedReader().use { it.readText() }
    assertEquals(expectedText, retrievedText)
  }

  @Test
  fun `test delete Dataset part DB`() {

    val customers10000File =
        resourceLoader.getResource("classpath:/$CUSTOMERS_10000_SOURCE_FILE_NAME").file

    val customers10000FileToSend = FileInputStream(customers10000File)

    val customers10000MultipartFile =
        MockMultipartFile(
            "file",
            CUSTOMERS_10000_SOURCE_FILE_NAME,
            MediaType.MULTIPART_FORM_DATA_VALUE,
            IOUtils.toByteArray(customers10000FileToSend))

    val datasetCreateRequest =
        DatasetCreateRequest(
            name = "Dataset Test with File and DB",
            parts =
                mutableListOf(
                    DatasetPartCreateRequest(
                        name = "DB part",
                        sourceName = CUSTOMERS_10000_SOURCE_FILE_NAME,
                        type = DatasetPartTypeEnum.DB)))

    val createdDataset =
        datasetApiService.createDataset(
            organizationSaved.id,
            workspaceSaved.id,
            datasetCreateRequest,
            arrayOf(customers10000MultipartFile))

    val dBDatasetPartId = createdDataset.parts.first { it.type == DatasetPartTypeEnum.DB }.id

    assertTrue(writerJdbcTemplate.existTable(dBDatasetPartId.replace('-', '_')))

    datasetApiService.deleteDatasetPart(
        organizationSaved.id, workspaceSaved.id, createdDataset.id, dBDatasetPartId)

    assertFalse(writerJdbcTemplate.existTable(dBDatasetPartId.replace('-', '_')))
  }

  @Test
  fun `test update Dataset part DB`() {

    val customers10000File =
        resourceLoader.getResource("classpath:/$CUSTOMERS_10000_SOURCE_FILE_NAME").file

    val customers10000FileToSend = FileInputStream(customers10000File)

    val customers10000MultipartFile =
        MockMultipartFile(
            "file",
            CUSTOMERS_10000_SOURCE_FILE_NAME,
            MediaType.MULTIPART_FORM_DATA_VALUE,
            IOUtils.toByteArray(customers10000FileToSend))

    val datasetCreateRequest =
        DatasetCreateRequest(
            name = "Dataset Test with DB part",
            parts =
                mutableListOf(
                    DatasetPartCreateRequest(
                        name = "DB part",
                        sourceName = CUSTOMERS_10000_SOURCE_FILE_NAME,
                        type = DatasetPartTypeEnum.DB)))

    val createdDataset =
        datasetApiService.createDataset(
            organizationSaved.id,
            workspaceSaved.id,
            datasetCreateRequest,
            arrayOf(customers10000MultipartFile))

    val firstDBDatasetPartId = createdDataset.parts.first { it.type == DatasetPartTypeEnum.DB }.id

    assertTrue(writerJdbcTemplate.existTable(firstDBDatasetPartId.replace('-', '_')))

    var datasetPartDB =
        datasetApiService.downloadDatasetPart(
            organizationSaved.id, workspaceSaved.id, createdDataset.id, firstDBDatasetPartId)
    var expectedText = FileInputStream(customers10000File).bufferedReader().use { it.readText() }
    var retrievedText = datasetPartDB.inputStream.bufferedReader().use { it.readText() }
    assertEquals(expectedText, retrievedText)

    val customers1000File =
        resourceLoader.getResource("classpath:/$CUSTOMERS_1000_SOURCE_FILE_NAME").file

    val customers1000FileToSend = FileInputStream(customers1000File)

    val customers1000MultipartFile =
        MockMultipartFile(
            "file",
            CUSTOMERS_1000_SOURCE_FILE_NAME,
            MediaType.MULTIPART_FORM_DATA_VALUE,
            IOUtils.toByteArray(customers1000FileToSend))

    val updatedDataset =
        datasetApiService.updateDataset(
            organizationSaved.id,
            workspaceSaved.id,
            createdDataset.id,
            DatasetUpdateRequest(
                parts =
                    mutableListOf(
                        DatasetPartCreateRequest(
                            name = "New part DB",
                            sourceName = CUSTOMERS_1000_SOURCE_FILE_NAME,
                            type = DatasetPartTypeEnum.DB))),
            arrayOf(customers1000MultipartFile))

    assertFalse(writerJdbcTemplate.existTable(firstDBDatasetPartId.replace('-', '_')))

    val updatedDBDatasetPartId = updatedDataset.parts.first { it.type == DatasetPartTypeEnum.DB }.id

    assertTrue(writerJdbcTemplate.existTable(updatedDBDatasetPartId.replace('-', '_')))

    datasetPartDB =
        datasetApiService.downloadDatasetPart(
            organizationSaved.id, workspaceSaved.id, createdDataset.id, updatedDBDatasetPartId)
    expectedText = FileInputStream(customers1000File).bufferedReader().use { it.readText() }
    retrievedText = datasetPartDB.inputStream.bufferedReader().use { it.readText() }
    assertEquals(expectedText, retrievedText)
  }

  @Test
  fun `test create Dataset part DB with unallowed char in headers`() {

    val unallowedCharInHeaderFile =
        resourceLoader.getResource("classpath:/$UNALLOWED_CHAR_HEADERS_FILE_NAME").file

    val unallowedCharInHeaderFileToSend = FileInputStream(unallowedCharInHeaderFile)

    val unallowedCharInHeaderMultipartFile =
        MockMultipartFile(
            "file",
            UNALLOWED_CHAR_HEADERS_FILE_NAME,
            MediaType.MULTIPART_FORM_DATA_VALUE,
            IOUtils.toByteArray(unallowedCharInHeaderFileToSend))

    val datasetCreateRequest =
        DatasetCreateRequest(
            name = "Dataset Test with DB part",
            parts =
                mutableListOf(
                    DatasetPartCreateRequest(
                        name = "DB part",
                        sourceName = UNALLOWED_CHAR_HEADERS_FILE_NAME,
                        type = DatasetPartTypeEnum.DB)))

    val exception =
        assertThrows<IllegalArgumentException> {
          datasetApiService.createDataset(
              organizationSaved.id,
              workspaceSaved.id,
              datasetCreateRequest,
              arrayOf(unallowedCharInHeaderMultipartFile))
        }

    assertEquals(
        "Invalid header name found in dataset part file: " +
            "header name must match [a-zA-Z0-9_\"' ]+ (found: [\"%1325 \\ sr\", \"-#()char'\\`\\\"\"])",
        exception.message)
  }

  @Test
  fun `test create Dataset part DB with empty headers`() {

    val unallowedCharInHeaderFile =
        resourceLoader.getResource("classpath:/$EMPTY_SOURCE_FILE_NAME").file

    val unallowedCharInHeaderFileToSend = FileInputStream(unallowedCharInHeaderFile)

    val unallowedCharInHeaderMultipartFile =
        MockMultipartFile(
            "file",
            EMPTY_SOURCE_FILE_NAME,
            MediaType.MULTIPART_FORM_DATA_VALUE,
            IOUtils.toByteArray(unallowedCharInHeaderFileToSend))

    val datasetCreateRequest =
        DatasetCreateRequest(
            name = "Dataset Test with DB part",
            parts =
                mutableListOf(
                    DatasetPartCreateRequest(
                        name = "DB part",
                        sourceName = EMPTY_SOURCE_FILE_NAME,
                        type = DatasetPartTypeEnum.DB)))

    val exception =
        assertThrows<IllegalArgumentException> {
          datasetApiService.createDataset(
              organizationSaved.id,
              workspaceSaved.id,
              datasetCreateRequest,
              arrayOf(unallowedCharInHeaderMultipartFile))
        }

    assertEquals("No headers found in dataset part file", exception.message)
  }

  @Test
  fun `test create Dataset part DB with multiple same header names`() {

    val unallowedCharInHeaderFile =
        resourceLoader.getResource("classpath:/$SAME_HEADERS_FILE_NAME").file

    val unallowedCharInHeaderFileToSend = FileInputStream(unallowedCharInHeaderFile)

    val unallowedCharInHeaderMultipartFile =
        MockMultipartFile(
            "file",
            SAME_HEADERS_FILE_NAME,
            MediaType.MULTIPART_FORM_DATA_VALUE,
            IOUtils.toByteArray(unallowedCharInHeaderFileToSend))

    val datasetCreateRequest =
        DatasetCreateRequest(
            name = "Dataset Test with DB part",
            parts =
                mutableListOf(
                    DatasetPartCreateRequest(
                        name = "DB part",
                        sourceName = SAME_HEADERS_FILE_NAME,
                        type = DatasetPartTypeEnum.DB)))

    val exception =
        assertThrows<IllegalArgumentException> {
          datasetApiService.createDataset(
              organizationSaved.id,
              workspaceSaved.id,
              datasetCreateRequest,
              arrayOf(unallowedCharInHeaderMultipartFile))
        }

    assertEquals("Duplicate headers found in dataset part file", exception.message)
  }

  @Test
  fun `test createDatasetPart with unallowed mimetype`() {

    val datasetCreateRequest = DatasetCreateRequest(name = "Dataset Test")

    val createDataset =
        datasetApiService.createDataset(
            organizationSaved.id, workspaceSaved.id, datasetCreateRequest, arrayOf())

    assertTrue(createDataset.parts.isEmpty())

    val resourceTestFile =
        resourceLoader.getResource("classpath:/$UNALLOWED_MIME_TYPE_SOURCE_FILE_NAME").file

    val fileToSend = FileInputStream(resourceTestFile)

    val mockMultipartFile =
        MockMultipartFile(
            "file",
            UNALLOWED_MIME_TYPE_SOURCE_FILE_NAME,
            MediaType.MULTIPART_FORM_DATA_VALUE,
            IOUtils.toByteArray(fileToSend))

    val exception =
        assertThrows<CsmAccessForbiddenException> {
          datasetApiService.createDatasetPart(
              organizationSaved.id,
              workspaceSaved.id,
              createDataset.id,
              mockMultipartFile,
              DatasetPartCreateRequest(
                  name = "Customer list",
                  sourceName = UNALLOWED_MIME_TYPE_SOURCE_FILE_NAME,
                  description = "List of customers",
                  tags = mutableListOf("part", "public", "customers"),
                  type = DatasetPartTypeEnum.File))
        }
    assertEquals(
        "MIME type text/x-yaml for file $UNALLOWED_MIME_TYPE_SOURCE_FILE_NAME is not authorized.",
        exception.message)
  }

  @Test
  fun `test createDatasetPart with unallowed file name`() {

    val datasetCreateRequest = DatasetCreateRequest(name = "Dataset Test")

    val createDataset =
        datasetApiService.createDataset(
            organizationSaved.id, workspaceSaved.id, datasetCreateRequest, arrayOf())

    assertTrue(createDataset.parts.isEmpty())

    val mockMultipartFile =
        MockMultipartFile(
            "file",
            WRONG_ORIGINAL_FILE_NAME,
            MediaType.MULTIPART_FORM_DATA_VALUE,
            InputStream.nullInputStream())

    val exception =
        assertThrows<IllegalArgumentException> {
          datasetApiService.createDatasetPart(
              organizationSaved.id,
              workspaceSaved.id,
              createDataset.id,
              mockMultipartFile,
              DatasetPartCreateRequest(
                  name = "Customer list",
                  sourceName = WRONG_ORIGINAL_FILE_NAME,
                  description = "List of customers",
                  tags = mutableListOf("part", "public", "customers"),
                  type = DatasetPartTypeEnum.File))
        }
    assertEquals(
        "Invalid filename: '$WRONG_ORIGINAL_FILE_NAME'. File name should neither contains '..' nor starts by '/'.",
        exception.message)
  }

  @Test
  fun `test createDatasetPart with blank name`() {

    val datasetCreateRequest = DatasetCreateRequest(name = "Dataset Test")

    val createDataset =
        datasetApiService.createDataset(
            organizationSaved.id, workspaceSaved.id, datasetCreateRequest, arrayOf())

    assertTrue(createDataset.parts.isEmpty())

    val mockMultipartFile =
        MockMultipartFile(
            "file",
            CUSTOMER_SOURCE_FILE_NAME,
            MediaType.MULTIPART_FORM_DATA_VALUE,
            InputStream.nullInputStream())

    val exception =
        assertThrows<IllegalArgumentException> {
          datasetApiService.createDatasetPart(
              organizationSaved.id,
              workspaceSaved.id,
              createDataset.id,
              mockMultipartFile,
              DatasetPartCreateRequest(
                  name = "",
                  sourceName = CUSTOMER_SOURCE_FILE_NAME,
                  type = DatasetPartTypeEnum.File))
        }
    assertEquals("Dataset Part name must not be blank", exception.message)
  }

  @Test
  fun `test createDatasetPart with mismatch sourceName and originalFileName`() {

    val datasetCreateRequest = DatasetCreateRequest(name = "Dataset Test")

    val createDataset =
        datasetApiService.createDataset(
            organizationSaved.id, workspaceSaved.id, datasetCreateRequest, arrayOf())

    assertTrue(createDataset.parts.isEmpty())

    val mockMultipartFile =
        MockMultipartFile(
            "file",
            "wrongFileName.csv",
            MediaType.MULTIPART_FORM_DATA_VALUE,
            InputStream.nullInputStream())

    val exception =
        assertThrows<IllegalArgumentException> {
          datasetApiService.createDatasetPart(
              organizationSaved.id,
              workspaceSaved.id,
              createDataset.id,
              mockMultipartFile,
              DatasetPartCreateRequest(
                  name = "Dataset Part Name",
                  sourceName = CUSTOMER_SOURCE_FILE_NAME,
                  type = DatasetPartTypeEnum.File))
        }
    assertEquals(
        "You must upload a file with the same name as the Dataset Part sourceName. " +
            "You provided $CUSTOMER_SOURCE_FILE_NAME and wrongFileName.csv instead.",
        exception.message)
  }

  @Test
  fun `test deleteDatasetPart FILE`() {

    val datasetCreateRequest = DatasetCreateRequest(name = "Dataset Test")

    val createDataset =
        datasetApiService.createDataset(
            organizationSaved.id, workspaceSaved.id, datasetCreateRequest, arrayOf())

    assertTrue(createDataset.parts.isEmpty())

    val resourceTestFile = resourceLoader.getResource("classpath:/$CUSTOMER_SOURCE_FILE_NAME").file

    val fileToSend = FileInputStream(resourceTestFile)

    val mockMultipartFile =
        MockMultipartFile(
            "file",
            CUSTOMER_SOURCE_FILE_NAME,
            MediaType.MULTIPART_FORM_DATA_VALUE,
            IOUtils.toByteArray(fileToSend))

    val datasetPartName = "Customer list"
    val datasetPartDescription = "List of customers"
    val datasetPartTags = mutableListOf("part", "public", "customers")

    val createDatasetPart =
        datasetApiService.createDatasetPart(
            organizationSaved.id,
            workspaceSaved.id,
            createDataset.id,
            mockMultipartFile,
            DatasetPartCreateRequest(
                name = datasetPartName,
                sourceName = CUSTOMER_SOURCE_FILE_NAME,
                description = datasetPartDescription,
                tags = datasetPartTags,
                type = DatasetPartTypeEnum.File))

    assertNotNull(createDatasetPart)
    datasetApiService.deleteDatasetPart(
        organizationSaved.id, workspaceSaved.id, createDataset.id, createDatasetPart.id)

    val retrievedDataset =
        datasetApiService.getDataset(organizationSaved.id, workspaceSaved.id, createDataset.id)

    assertTrue(retrievedDataset.parts.isEmpty())

    val deletedFileKeyPath =
        retrievedDataset.organizationId +
            "/${retrievedDataset.workspaceId}" +
            "/${retrievedDataset.id}" +
            "/${createDatasetPart.id}" +
            "/${createDatasetPart.sourceName}"

    assertFalse(s3Template.objectExists(csmPlatformProperties.s3.bucketName, deletedFileKeyPath))
  }

  @Test
  fun `test deleteDatasetPart DB`() {

    val datasetCreateRequest = DatasetCreateRequest(name = "Dataset Test")

    val createDataset =
        datasetApiService.createDataset(
            organizationSaved.id, workspaceSaved.id, datasetCreateRequest, arrayOf())

    assertTrue(createDataset.parts.isEmpty())

    val resourceTestFile = resourceLoader.getResource("classpath:/$CUSTOMER_SOURCE_FILE_NAME").file

    val fileToSend = FileInputStream(resourceTestFile)

    val mockMultipartFile =
        MockMultipartFile(
            "file",
            CUSTOMER_SOURCE_FILE_NAME,
            MediaType.MULTIPART_FORM_DATA_VALUE,
            IOUtils.toByteArray(fileToSend))

    val datasetPartName = "Customer list"
    val datasetPartDescription = "List of customers"
    val datasetPartTags = mutableListOf("part", "public", "customers")

    val createDatasetPart =
        datasetApiService.createDatasetPart(
            organizationSaved.id,
            workspaceSaved.id,
            createDataset.id,
            mockMultipartFile,
            DatasetPartCreateRequest(
                name = datasetPartName,
                sourceName = CUSTOMER_SOURCE_FILE_NAME,
                description = datasetPartDescription,
                tags = datasetPartTags,
                type = DatasetPartTypeEnum.DB))

    assertNotNull(createDatasetPart)
    datasetApiService.deleteDatasetPart(
        organizationSaved.id, workspaceSaved.id, createDataset.id, createDatasetPart.id)

    val retrievedDataset =
        datasetApiService.getDataset(organizationSaved.id, workspaceSaved.id, createDataset.id)

    assertTrue(retrievedDataset.parts.isEmpty())

    assertFalse(writerJdbcTemplate.existTable(createDatasetPart.id.replace('-', '_')))
  }

  @Test
  fun `test getDatasetPart`() {

    val datasetCreateRequest = DatasetCreateRequest(name = "Dataset Test")

    val createDataset =
        datasetApiService.createDataset(
            organizationSaved.id, workspaceSaved.id, datasetCreateRequest, arrayOf())

    assertTrue(createDataset.parts.isEmpty())

    val resourceTestFile = resourceLoader.getResource("classpath:/$CUSTOMER_SOURCE_FILE_NAME").file

    val fileToSend = FileInputStream(resourceTestFile)

    val mockMultipartFile =
        MockMultipartFile(
            "file",
            CUSTOMER_SOURCE_FILE_NAME,
            MediaType.MULTIPART_FORM_DATA_VALUE,
            IOUtils.toByteArray(fileToSend))

    val datasetPartName = "Customer list"
    val datasetPartDescription = "List of customers"
    val datasetPartTags = mutableListOf("part", "public", "customers")

    val createDatasetPart =
        datasetApiService.createDatasetPart(
            organizationSaved.id,
            workspaceSaved.id,
            createDataset.id,
            mockMultipartFile,
            DatasetPartCreateRequest(
                name = datasetPartName,
                sourceName = CUSTOMER_SOURCE_FILE_NAME,
                description = datasetPartDescription,
                tags = datasetPartTags,
                type = DatasetPartTypeEnum.File))

    val retrievedDatasetPart =
        datasetApiService.getDatasetPart(
            organizationSaved.id, workspaceSaved.id, createDataset.id, createDatasetPart.id)

    assertEquals(createDatasetPart, retrievedDatasetPart)
  }

  @Test
  fun `test searchDatasetParts`() {

    val datasetCreateRequest = DatasetCreateRequest(name = "Dataset Test")

    val createDataset =
        datasetApiService.createDataset(
            organizationSaved.id, workspaceSaved.id, datasetCreateRequest, arrayOf())

    assertTrue(createDataset.parts.isEmpty())

    val resourceTestFile = resourceLoader.getResource("classpath:/$CUSTOMER_SOURCE_FILE_NAME").file

    val fileToSend = FileInputStream(resourceTestFile)

    val mockMultipartFile =
        MockMultipartFile(
            "file",
            CUSTOMER_SOURCE_FILE_NAME,
            MediaType.MULTIPART_FORM_DATA_VALUE,
            IOUtils.toByteArray(fileToSend))

    val datasetPartName = "Customer list"
    val datasetPartDescription = "List of customers"
    val datasetPartTags = mutableListOf("part", "public", "customers")

    val createDatasetPart =
        datasetApiService.createDatasetPart(
            organizationSaved.id,
            workspaceSaved.id,
            createDataset.id,
            mockMultipartFile,
            DatasetPartCreateRequest(
                name = datasetPartName,
                sourceName = CUSTOMER_SOURCE_FILE_NAME,
                description = datasetPartDescription,
                tags = datasetPartTags,
                type = DatasetPartTypeEnum.File))

    val foundDatasetParts =
        datasetApiService.searchDatasetParts(
            organizationSaved.id,
            workspaceSaved.id,
            createDataset.id,
            listOf("customers"),
            null,
            null)

    assertTrue(foundDatasetParts.size == 1)
    assertEquals(createDatasetPart.id, foundDatasetParts[0].id)
    assertEquals(datasetPartName, foundDatasetParts[0].name)
    assertEquals(datasetPartDescription, foundDatasetParts[0].description)
    assertEquals(datasetPartTags, foundDatasetParts[0].tags)
    assertEquals(CUSTOMER_SOURCE_FILE_NAME, foundDatasetParts[0].sourceName)
    assertEquals(DatasetPartTypeEnum.File, foundDatasetParts[0].type)
  }

  @Test
  fun `test getDatasetPart with wrong id`() {

    val datasetCreateRequest = DatasetCreateRequest(name = "Dataset Test")

    val createDataset =
        datasetApiService.createDataset(
            organizationSaved.id, workspaceSaved.id, datasetCreateRequest, arrayOf())

    assertTrue(createDataset.parts.isEmpty())

    val exception =
        assertThrows<CsmResourceNotFoundException> {
          datasetApiService.getDatasetPart(
              organizationSaved.id, workspaceSaved.id, createDataset.id, "wrongDatasetPartId")
        }

    assertEquals(
        "Dataset Part wrongDatasetPartId not found in organization ${organizationSaved.id}, " +
            "workspace ${workspaceSaved.id} and dataset ${createDataset.id}",
        exception.message)
  }

  @Test
  fun `test listDatasetParts`() {

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

    val emptyCustomerMockMultipartFile =
        MockMultipartFile(
            "files",
            CUSTOMER_SOURCE_FILE_NAME,
            MediaType.MULTIPART_FORM_DATA_VALUE,
            InputStream.nullInputStream())

    val emptyInventoryMockMultipartFile =
        MockMultipartFile(
            "files",
            INVENTORY_SOURCE_FILE_NAME,
            MediaType.MULTIPART_FORM_DATA_VALUE,
            InputStream.nullInputStream())

    val createdDataset =
        datasetApiService.createDataset(
            organizationSaved.id,
            workspaceSaved.id,
            datasetCreateRequest,
            arrayOf(emptyCustomerMockMultipartFile, emptyInventoryMockMultipartFile))

    var listDatasetParts =
        datasetApiService.listDatasetParts(
            organizationSaved.id, workspaceSaved.id, createdDataset.id, null, null)

    assertNotNull(listDatasetParts)
    assertEquals(2, listDatasetParts.size)
    var customerDatasetPart = listDatasetParts[0]
    assertEquals(customerPartName, customerDatasetPart.name)
    assertEquals(customerPartDescription, customerDatasetPart.description)
    assertEquals(customerPartTags, customerDatasetPart.tags)
    assertEquals(CUSTOMER_SOURCE_FILE_NAME, customerDatasetPart.sourceName)
    assertEquals(DatasetPartTypeEnum.File, customerDatasetPart.type)
    val inventoryDatasetPart = listDatasetParts[1]
    assertEquals(inventoryPartName, inventoryDatasetPart.name)
    assertEquals(inventoryPartDescription, inventoryDatasetPart.description)
    assertEquals(inventoryPartTags, inventoryDatasetPart.tags)
    assertEquals(INVENTORY_SOURCE_FILE_NAME, inventoryDatasetPart.sourceName)
    assertEquals(DatasetPartTypeEnum.File, inventoryDatasetPart.type)

    listDatasetParts =
        datasetApiService.listDatasetParts(
            organizationSaved.id, workspaceSaved.id, createdDataset.id, 0, 1)

    assertNotNull(listDatasetParts)
    assertEquals(1, listDatasetParts.size)
    customerDatasetPart = listDatasetParts[0]
    assertEquals(customerPartName, customerDatasetPart.name)
    assertEquals(customerPartDescription, customerDatasetPart.description)
    assertEquals(customerPartTags, customerDatasetPart.tags)
    assertEquals(CUSTOMER_SOURCE_FILE_NAME, customerDatasetPart.sourceName)
    assertEquals(DatasetPartTypeEnum.File, customerDatasetPart.type)

    listDatasetParts =
        datasetApiService.listDatasetParts(
            organizationSaved.id, workspaceSaved.id, createdDataset.id, 1, 5)
    assertNotNull(listDatasetParts)
    assertTrue(listDatasetParts.isEmpty())
  }

  @Test
  fun `test downloadDatasetPart`() {

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

    val downloadFile =
        datasetApiService.downloadDatasetPart(
            organizationSaved.id, workspaceSaved.id, createdDataset.id, createdDataset.parts[0].id)

    val expectedText = FileInputStream(resourceTestFile).bufferedReader().use { it.readText() }
    val retrievedText =
        InputStreamResource(downloadFile).inputStream.bufferedReader().use { it.readText() }

    assertEquals(expectedText, retrievedText)
  }

  @Test
  fun `test updateDataset with File dataset part`() {

    // Create a Dataset with dataset Part
    val datasetPartName = "Customers list"
    val datasetPartDescription = "List of customers"
    val datasetPartTags = mutableListOf("part", "public", "customers")
    val datasetPartAdditionalData =
        mutableMapOf("part" to "data", "complex" to mutableMapOf("nested" to "data"))
    val datasetPartCreateRequest =
        DatasetPartCreateRequest(
            name = datasetPartName,
            sourceName = CUSTOMER_SOURCE_FILE_NAME,
            description = datasetPartDescription,
            tags = datasetPartTags,
            additionalData = datasetPartAdditionalData,
            type = DatasetPartTypeEnum.File)

    val datasetName = "Customer Dataset"
    val datasetDescription = "Dataset for customers"
    val datasetTags = mutableListOf("dataset", "public", "customers")
    val datasetAdditionalData =
        mutableMapOf("dataset" to "data", "complex" to mutableMapOf("nested" to "data"))
    val datasetCreateRequest =
        DatasetCreateRequest(
            name = datasetName,
            description = datasetDescription,
            tags = datasetTags,
            additionalData = datasetAdditionalData,
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

    // Create a DatasetUpdateRequest with new dataset part
    val newDatasetPartName = "Product list"
    val newDatasetPartDescription = "List of Product"
    val newDatasetPartTags = mutableListOf("part", "public", "product")
    val newDatasetPartAdditionalData = mutableMapOf<String, Any>("part" to "new data")
    val newDatasetPartCreateRequest =
        DatasetPartCreateRequest(
            name = newDatasetPartName,
            sourceName = INVENTORY_SOURCE_FILE_NAME,
            description = newDatasetPartDescription,
            tags = newDatasetPartTags,
            additionalData = newDatasetPartAdditionalData,
            type = DatasetPartTypeEnum.File)

    val newDatasetName = "Shop Dataset"
    val newDatasetDescription = "Dataset for shop"
    val newDatasetTags = mutableListOf("dataset", "public", "shop")
    val newDatasetAdditionalData = mutableMapOf<String, Any>("dataset" to "new data")
    val newDatasetSecurity =
        DatasetSecurity(
            default = ROLE_NONE,
            accessControlList =
                mutableListOf(
                    DatasetAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                    DatasetAccessControl(CONNECTED_DEFAULT_USER, ROLE_EDITOR)))
    val datasetUpdateRequest =
        DatasetUpdateRequest(
            name = newDatasetName,
            description = newDatasetDescription,
            tags = newDatasetTags,
            additionalData = newDatasetAdditionalData,
            parts = mutableListOf(newDatasetPartCreateRequest),
            security = newDatasetSecurity)

    val newDatasetPartTestFile =
        resourceLoader.getResource("classpath:/$INVENTORY_SOURCE_FILE_NAME").file

    val newDatasetPartFileToSend = FileInputStream(newDatasetPartTestFile)

    val newDatasetPartMockMultipartFile =
        MockMultipartFile(
            "files",
            INVENTORY_SOURCE_FILE_NAME,
            MediaType.MULTIPART_FORM_DATA_VALUE,
            IOUtils.toByteArray(newDatasetPartFileToSend))

    // Update dataset with all new information + new part instead of the previous one
    val updatedDataset =
        datasetApiService.updateDataset(
            organizationSaved.id,
            workspaceSaved.id,
            createdDataset.id,
            datasetUpdateRequest,
            arrayOf(newDatasetPartMockMultipartFile))

    // check new Dataset simple data
    assertEquals(newDatasetName, updatedDataset.name)
    assertEquals(newDatasetDescription, updatedDataset.description)
    assertEquals(newDatasetTags, updatedDataset.tags)
    assertEquals(newDatasetSecurity, updatedDataset.security)
    assertEquals(createdDataset.createInfo, updatedDataset.createInfo)
    assertEquals(createdDataset.updateInfo.userId, updatedDataset.updateInfo.userId)
    assertTrue(createdDataset.updateInfo.timestamp < updatedDataset.updateInfo.timestamp)

    // check the new Dataset part is created and corresponding to data specified during update
    val newDatasetPart =
        datasetApiService.getDatasetPart(
            organizationSaved.id, workspaceSaved.id, updatedDataset.id, updatedDataset.parts[0].id)
    assertTrue(updatedDataset.parts.size == 1)
    assertEquals(newDatasetPart, updatedDataset.parts[0])

    // check the old Dataset part is not in DB anymore
    val oldDatasetPartShouldNotExistInDB =
        assertThrows<CsmResourceNotFoundException> {
          datasetApiService.getDatasetPart(
              organizationSaved.id,
              workspaceSaved.id,
              createdDataset.id,
              createdDataset.parts[0].id)
        }
    assertEquals(
        "Dataset Part ${createdDataset.parts[0].id} not found " +
            "in organization ${organizationSaved.id}, " +
            "workspace ${workspaceSaved.id} and dataset ${createdDataset.id}",
        oldDatasetPartShouldNotExistInDB.message)

    // check the old Dataset part is not in s3 storage anymore
    assertFalse(
        s3Template.objectExists(
            csmPlatformProperties.s3.bucketName,
            constructFilePathForDatasetPart(createdDataset, 0)))

    // check the new Dataset part is in s3 storage now
    assertTrue(
        s3Template.objectExists(
            csmPlatformProperties.s3.bucketName,
            constructFilePathForDatasetPart(updatedDataset, 0)))
  }

  @Test
  fun `test updateDataset with two dataset parts and same multipart file name`() {

    val initialDataset =
        datasetApiService.createDataset(
            organizationSaved.id,
            workspaceSaved.id,
            DatasetCreateRequest(name = "Dataset without parts"),
            emptyArray())

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

    val datasetName = "Shop Dataset"
    val datasetDescription = "Dataset for shop"
    val datasetTags = mutableListOf("dataset", "public", "shop")
    val datasetUpdateRequest =
        DatasetUpdateRequest(
            name = datasetName,
            description = datasetDescription,
            tags = datasetTags,
            parts =
                mutableListOf(
                    customerPartCreateRequest,
                    DatasetPartCreateRequest(
                        name = "Part create request 2", sourceName = "anotherFile.txt")))

    val customerTestFile = resourceLoader.getResource("classpath:/$CUSTOMER_SOURCE_FILE_NAME").file

    val customerFileToSend = FileInputStream(customerTestFile)

    val customerMockMultipartFile =
        MockMultipartFile(
            "files",
            CUSTOMER_SOURCE_FILE_NAME,
            MediaType.MULTIPART_FORM_DATA_VALUE,
            IOUtils.toByteArray(customerFileToSend))

    val customerMockMultipartFile2 =
        MockMultipartFile(
            "files",
            CUSTOMER_SOURCE_FILE_NAME,
            MediaType.MULTIPART_FORM_DATA_VALUE,
            InputStream.nullInputStream())

    val exception =
        assertThrows<IllegalArgumentException> {
          datasetApiService.updateDataset(
              organizationSaved.id,
              workspaceSaved.id,
              initialDataset.id,
              datasetUpdateRequest,
              arrayOf(customerMockMultipartFile, customerMockMultipartFile2))
        }
    assertEquals(
        "Multipart file names should be unique during dataset update. " +
            "Multipart file names: [$CUSTOMER_SOURCE_FILE_NAME, $CUSTOMER_SOURCE_FILE_NAME]. " +
            "Dataset parts source names: [$CUSTOMER_SOURCE_FILE_NAME, anotherFile.txt].",
        exception.message)
  }

  @Test
  fun `test updateDataset with File dataset part with unallowed mimetype`() {

    // Create a Dataset with dataset Part
    val datasetPartCreateRequest =
        DatasetPartCreateRequest(
            name = "Customers list",
            sourceName = CUSTOMER_SOURCE_FILE_NAME,
            description = "List of customers",
            tags = mutableListOf("part", "public", "customers"),
            type = DatasetPartTypeEnum.File)

    val datasetCreateRequest =
        DatasetCreateRequest(
            name = "Customer Dataset",
            description = "Dataset for customers",
            tags = mutableListOf("dataset", "public", "customers"),
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

    // Create a DatasetUpdateRequest with new dataset part
    val newDatasetPartCreateRequest =
        DatasetPartCreateRequest(
            name = "Product list",
            sourceName = UNALLOWED_MIME_TYPE_SOURCE_FILE_NAME,
            description = "List of Product",
            tags = mutableListOf("part", "public", "product"),
            type = DatasetPartTypeEnum.File)

    val datasetUpdateRequest =
        DatasetUpdateRequest(
            name = "Shop Dataset",
            description = "Dataset for shop",
            tags = mutableListOf("dataset", "public", "shop"),
            parts = mutableListOf(newDatasetPartCreateRequest),
            security =
                DatasetSecurity(
                    default = ROLE_NONE,
                    accessControlList =
                        mutableListOf(
                            DatasetAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                            DatasetAccessControl(CONNECTED_DEFAULT_USER, ROLE_EDITOR))))

    val wrongTypeTestFile =
        resourceLoader.getResource("classpath:/$UNALLOWED_MIME_TYPE_SOURCE_FILE_NAME").file

    val wrongTypeFileToSend = FileInputStream(wrongTypeTestFile)

    val wrongTypeMockMultipartFile =
        MockMultipartFile(
            "files",
            UNALLOWED_MIME_TYPE_SOURCE_FILE_NAME,
            MediaType.MULTIPART_FORM_DATA_VALUE,
            IOUtils.toByteArray(wrongTypeFileToSend))

    val exception =
        assertThrows<CsmAccessForbiddenException> {
          datasetApiService.updateDataset(
              organizationSaved.id,
              workspaceSaved.id,
              createdDataset.id,
              datasetUpdateRequest,
              arrayOf(wrongTypeMockMultipartFile))
        }
    assertEquals(
        "MIME type text/x-yaml for file $UNALLOWED_MIME_TYPE_SOURCE_FILE_NAME is not authorized.",
        exception.message)
  }

  @Test
  fun `test updateDataset with File dataset part with unallowed file name`() {

    // Create a Dataset with dataset Part
    val datasetPartCreateRequest =
        DatasetPartCreateRequest(
            name = "Customers list",
            sourceName = CUSTOMER_SOURCE_FILE_NAME,
            description = "List of customers",
            tags = mutableListOf("part", "public", "customers"),
            type = DatasetPartTypeEnum.File)

    val datasetCreateRequest =
        DatasetCreateRequest(
            name = "Customer Dataset",
            description = "Dataset for customers",
            tags = mutableListOf("dataset", "public", "customers"),
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

    // Create a DatasetUpdateRequest with new dataset part
    val newDatasetPartCreateRequest =
        DatasetPartCreateRequest(
            name = "Product list",
            sourceName = WRONG_ORIGINAL_FILE_NAME,
            description = "List of Product",
            tags = mutableListOf("part", "public", "product"),
            type = DatasetPartTypeEnum.File)

    val datasetUpdateRequest =
        DatasetUpdateRequest(
            name = "Shop Dataset",
            description = "Dataset for shop",
            tags = mutableListOf("dataset", "public", "shop"),
            parts = mutableListOf(newDatasetPartCreateRequest),
            security =
                DatasetSecurity(
                    default = ROLE_NONE,
                    accessControlList =
                        mutableListOf(
                            DatasetAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                            DatasetAccessControl(CONNECTED_DEFAULT_USER, ROLE_EDITOR))))

    val wrongTypeMockMultipartFile =
        MockMultipartFile(
            "files",
            WRONG_ORIGINAL_FILE_NAME,
            MediaType.MULTIPART_FORM_DATA_VALUE,
            InputStream.nullInputStream())

    val exception =
        assertThrows<IllegalArgumentException> {
          datasetApiService.updateDataset(
              organizationSaved.id,
              workspaceSaved.id,
              createdDataset.id,
              datasetUpdateRequest,
              arrayOf(wrongTypeMockMultipartFile))
        }
    assertEquals(
        "Invalid filename: '$WRONG_ORIGINAL_FILE_NAME'. File name should neither contains '..' nor starts by '/'.",
        exception.message)
  }

  @Test
  fun `test updateDatasetPart`() {

    // Initiate Dataset with a customer dataset part (that will be replaced by the inventory part)
    val customerPartName = "Customers list"
    val customerPartDescription = "List of customers"
    val customerPartTags = mutableListOf("part", "public", "customers")
    val customerPartAdditionalData =
        mutableMapOf("part" to "data", "complex" to mutableMapOf("nested" to "data"))
    val customerPartCreateRequest =
        DatasetPartCreateRequest(
            name = customerPartName,
            sourceName = CUSTOMER_SOURCE_FILE_NAME,
            description = customerPartDescription,
            tags = customerPartTags,
            additionalData = customerPartAdditionalData,
            type = DatasetPartTypeEnum.File)

    val datasetName = "Shop Dataset"
    val datasetDescription = "Dataset for shop"
    val datasetTags = mutableListOf("dataset", "public", "shop")
    val datasetCreateRequest =
        DatasetCreateRequest(
            name = datasetName,
            description = datasetDescription,
            tags = datasetTags,
            parts = mutableListOf(customerPartCreateRequest))

    val customerTestFile = resourceLoader.getResource("classpath:/$CUSTOMER_SOURCE_FILE_NAME").file
    val customerFileToSend = FileInputStream(customerTestFile)

    val customerMockMultipartFile =
        MockMultipartFile(
            "files",
            CUSTOMER_SOURCE_FILE_NAME,
            MediaType.MULTIPART_FORM_DATA_VALUE,
            IOUtils.toByteArray(customerFileToSend))

    val createdDataset =
        datasetApiService.createDataset(
            organizationSaved.id,
            workspaceSaved.id,
            datasetCreateRequest,
            arrayOf(customerMockMultipartFile))

    val customerPartFilePath = constructFilePathForDatasetPart(createdDataset, 0)
    assertTrue(s3Template.objectExists(csmPlatformProperties.s3.bucketName, customerPartFilePath))

    val customerDownloadFile =
        s3Template.download(csmPlatformProperties.s3.bucketName, customerPartFilePath)

    val customerExpectedText =
        FileInputStream(customerTestFile).bufferedReader().use { it.readText() }

    val customerRetrievedText =
        InputStreamResource(customerDownloadFile).inputStream.bufferedReader().use { it.readText() }

    assertEquals(customerExpectedText, customerRetrievedText)

    assertNotNull(createdDataset)
    assertEquals(datasetName, createdDataset.name)
    assertEquals(datasetDescription, createdDataset.description)
    assertEquals(datasetTags, createdDataset.tags)
    assertEquals(1, createdDataset.parts.size)
    val datasetPartToReplace = createdDataset.parts[0]
    assertNotNull(datasetPartToReplace)
    assertEquals(customerPartName, datasetPartToReplace.name)
    assertEquals(customerPartDescription, datasetPartToReplace.description)
    assertEquals(customerPartTags, datasetPartToReplace.tags)
    assertEquals(customerPartAdditionalData, datasetPartToReplace.additionalData)
    assertEquals(CUSTOMER_SOURCE_FILE_NAME, datasetPartToReplace.sourceName)
    assertEquals(DatasetPartTypeEnum.File, datasetPartToReplace.type)

    // New Part to replace the existing one in the dataset
    val newDatasetSourceName = "updatedResourceFile.csv"
    val newDatasetPartDescription = "New Data for customer list"
    val newDatasetPartTags = mutableListOf("part", "public", "new", "customer")
    val newDatasetPartAdditionalData = mutableMapOf<String, Any>("part" to "new data")
    val datasetPartUpdateRequest =
        DatasetPartUpdateRequest(
            sourceName = newDatasetSourceName,
            description = newDatasetPartDescription,
            tags = newDatasetPartTags,
            additionalData = newDatasetPartAdditionalData,
        )

    val replacedDatasetPart =
        datasetApiService.updateDatasetPart(
            organizationSaved.id,
            workspaceSaved.id,
            createdDataset.id,
            datasetPartToReplace.id,
            datasetPartUpdateRequest)

    val datasetWithReplacedPart =
        datasetApiService.getDataset(organizationSaved.id, workspaceSaved.id, createdDataset.id)
    assertTrue(datasetWithReplacedPart.parts.size == 1)
    assertEquals(replacedDatasetPart, datasetWithReplacedPart.parts[0])

    assertEquals(datasetPartToReplace.id, replacedDatasetPart.id)
    assertEquals(datasetPartToReplace.name, replacedDatasetPart.name)
    assertEquals(newDatasetSourceName, replacedDatasetPart.sourceName)
    assertEquals(newDatasetPartDescription, replacedDatasetPart.description)
    assertEquals(newDatasetPartTags, replacedDatasetPart.tags)
    assertEquals(newDatasetPartAdditionalData, replacedDatasetPart.additionalData)
    assertEquals(newDatasetSourceName, replacedDatasetPart.sourceName)
    assertEquals(DatasetPartTypeEnum.File, replacedDatasetPart.type)

    val datasetPartFilePath = constructFilePathForDatasetPart(datasetWithReplacedPart, 0)
    assertTrue(s3Template.objectExists(csmPlatformProperties.s3.bucketName, datasetPartFilePath))

    val unchangedDatasetPartDownloadFile =
        s3Template.download(csmPlatformProperties.s3.bucketName, datasetPartFilePath)

    val unchangedDatasetPartRetrievedText =
        InputStreamResource(unchangedDatasetPartDownloadFile).inputStream.bufferedReader().use {
          it.readText()
        }

    assertEquals(customerExpectedText, unchangedDatasetPartRetrievedText)
  }

  @Test
  fun `test replaceDatasetPart`() {

    // Initiate Dataset with a customer dataset part (that will be replaced by the inventory part)
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

    val datasetName = "Shop Dataset"
    val datasetDescription = "Dataset for shop"
    val datasetTags = mutableListOf("dataset", "public", "shop")
    val datasetCreateRequest =
        DatasetCreateRequest(
            name = datasetName,
            description = datasetDescription,
            tags = datasetTags,
            parts = mutableListOf(customerPartCreateRequest))

    val customerTestFile = resourceLoader.getResource("classpath:/$CUSTOMER_SOURCE_FILE_NAME").file
    val customerFileToSend = FileInputStream(customerTestFile)

    val customerMockMultipartFile =
        MockMultipartFile(
            "files",
            CUSTOMER_SOURCE_FILE_NAME,
            MediaType.MULTIPART_FORM_DATA_VALUE,
            IOUtils.toByteArray(customerFileToSend))

    val createdDataset =
        datasetApiService.createDataset(
            organizationSaved.id,
            workspaceSaved.id,
            datasetCreateRequest,
            arrayOf(customerMockMultipartFile))

    val customerPartFilePath = constructFilePathForDatasetPart(createdDataset, 0)
    assertTrue(s3Template.objectExists(csmPlatformProperties.s3.bucketName, customerPartFilePath))

    val customerDownloadFile =
        s3Template.download(csmPlatformProperties.s3.bucketName, customerPartFilePath)

    val customerExpectedText =
        FileInputStream(customerTestFile).bufferedReader().use { it.readText() }

    val customerRetrievedText =
        InputStreamResource(customerDownloadFile).inputStream.bufferedReader().use { it.readText() }

    assertEquals(customerExpectedText, customerRetrievedText)

    assertNotNull(createdDataset)
    assertEquals(datasetName, createdDataset.name)
    assertEquals(datasetDescription, createdDataset.description)
    assertEquals(datasetTags, createdDataset.tags)
    assertEquals(1, createdDataset.parts.size)
    val datasetPartToReplace = createdDataset.parts[0]
    assertNotNull(datasetPartToReplace)
    assertEquals(customerPartName, datasetPartToReplace.name)
    assertEquals(customerPartDescription, datasetPartToReplace.description)
    assertEquals(customerPartTags, datasetPartToReplace.tags)
    assertEquals(CUSTOMER_SOURCE_FILE_NAME, datasetPartToReplace.sourceName)
    assertEquals(DatasetPartTypeEnum.File, datasetPartToReplace.type)

    // New Part to replace the existing one in the dataset
    val newDatasetSourceName = "updatedResourceFile.csv"
    val newDatasetPartDescription = "New Data for customer list"
    val newDatasetPartTags = mutableListOf("part", "public", "new", "customer")
    val datasetPartUpdateRequest =
        DatasetPartUpdateRequest(
            sourceName = newDatasetSourceName,
            description = newDatasetPartDescription,
            tags = newDatasetPartTags,
        )
    val newDatasetPartTestFile =
        resourceLoader.getResource("classpath:/$INVENTORY_SOURCE_FILE_NAME").file

    val newDatasetPartFileToSend = FileInputStream(newDatasetPartTestFile)

    val newDatasetPartMockMultipartFile =
        MockMultipartFile(
            "files",
            INVENTORY_SOURCE_FILE_NAME,
            MediaType.MULTIPART_FORM_DATA_VALUE,
            IOUtils.toByteArray(newDatasetPartFileToSend))

    val replacedDatasetPart =
        datasetApiService.replaceDatasetPart(
            organizationSaved.id,
            workspaceSaved.id,
            createdDataset.id,
            datasetPartToReplace.id,
            newDatasetPartMockMultipartFile,
            datasetPartUpdateRequest)

    val datasetWithReplacedPart =
        datasetApiService.getDataset(organizationSaved.id, workspaceSaved.id, createdDataset.id)
    assertTrue(datasetWithReplacedPart.parts.size == 1)
    assertEquals(replacedDatasetPart, datasetWithReplacedPart.parts[0])

    assertEquals(datasetPartToReplace.id, replacedDatasetPart.id)
    assertEquals(datasetPartToReplace.name, replacedDatasetPart.name)
    assertEquals(replacedDatasetPart.sourceName, newDatasetSourceName)
    assertEquals(replacedDatasetPart.description, newDatasetPartDescription)
    assertEquals(replacedDatasetPart.tags, newDatasetPartTags)
    assertEquals("updatedResourceFile.csv", replacedDatasetPart.sourceName)
    assertEquals(DatasetPartTypeEnum.File, replacedDatasetPart.type)

    val newDatasetPartFilePath = constructFilePathForDatasetPart(datasetWithReplacedPart, 0)
    assertTrue(s3Template.objectExists(csmPlatformProperties.s3.bucketName, newDatasetPartFilePath))

    val newDatasetPartDownloadFile =
        s3Template.download(csmPlatformProperties.s3.bucketName, newDatasetPartFilePath)

    val newDatasetPartExpectedText =
        FileInputStream(newDatasetPartTestFile).bufferedReader().use { it.readText() }

    val newDatasetPartRetrievedText =
        InputStreamResource(newDatasetPartDownloadFile).inputStream.bufferedReader().use {
          it.readText()
        }

    assertEquals(newDatasetPartExpectedText, newDatasetPartRetrievedText)
  }

  @Test
  fun `test replaceDatasetPart with File dataset part with unallowed mimetype`() {

    // Create a Dataset with dataset Part
    val datasetPartCreateRequest =
        DatasetPartCreateRequest(
            name = "Customers list",
            sourceName = CUSTOMER_SOURCE_FILE_NAME,
            description = "List of customers",
            tags = mutableListOf("part", "public", "customers"),
            type = DatasetPartTypeEnum.File)

    val datasetCreateRequest =
        DatasetCreateRequest(
            name = "Customer Dataset",
            description = "Dataset for customers",
            tags = mutableListOf("dataset", "public", "customers"),
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

    // Create a DatasetUpdateRequest with new dataset part
    val datasetPartUpdateRequest =
        DatasetPartUpdateRequest(
            sourceName = "updatedResourceFile.csv",
            description = "Dataset for shop",
            tags = mutableListOf("dataset", "public", "shop"))

    val wrongTypeTestFile =
        resourceLoader.getResource("classpath:/$UNALLOWED_MIME_TYPE_SOURCE_FILE_NAME").file

    val wrongTypeFileToSend = FileInputStream(wrongTypeTestFile)

    val wrongTypeMockMultipartFile =
        MockMultipartFile(
            "files",
            UNALLOWED_MIME_TYPE_SOURCE_FILE_NAME,
            MediaType.MULTIPART_FORM_DATA_VALUE,
            IOUtils.toByteArray(wrongTypeFileToSend))

    val exception =
        assertThrows<CsmAccessForbiddenException> {
          datasetApiService.replaceDatasetPart(
              organizationSaved.id,
              workspaceSaved.id,
              createdDataset.id,
              createdDataset.parts[0].id,
              wrongTypeMockMultipartFile,
              datasetPartUpdateRequest)
        }
    assertEquals(
        "MIME type text/x-yaml for file $UNALLOWED_MIME_TYPE_SOURCE_FILE_NAME is not authorized.",
        exception.message)
  }

  @Test
  fun `test replaceDatasetPart with File dataset part with unallowed file name`() {

    // Create a Dataset with dataset Part
    val datasetPartCreateRequest =
        DatasetPartCreateRequest(
            name = "Customers list",
            sourceName = CUSTOMER_SOURCE_FILE_NAME,
            description = "List of customers",
            tags = mutableListOf("part", "public", "customers"),
            type = DatasetPartTypeEnum.File)

    val datasetCreateRequest =
        DatasetCreateRequest(
            name = "Customer Dataset",
            description = "Dataset for customers",
            tags = mutableListOf("dataset", "public", "customers"),
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

    // Create a DatasetUpdateRequest with new dataset part

    val datasetPartUpdateRequest =
        DatasetPartUpdateRequest(
            sourceName = "updatedResourceFile.csv",
            description = "Dataset for shop",
            tags = mutableListOf("dataset", "public", "shop"))

    val wrongTypeMockMultipartFile =
        MockMultipartFile(
            "files",
            WRONG_ORIGINAL_FILE_NAME,
            MediaType.MULTIPART_FORM_DATA_VALUE,
            InputStream.nullInputStream())

    val exception =
        assertThrows<IllegalArgumentException> {
          datasetApiService.replaceDatasetPart(
              organizationSaved.id,
              workspaceSaved.id,
              createdDataset.id,
              createdDataset.parts[0].id,
              wrongTypeMockMultipartFile,
              datasetPartUpdateRequest)
        }
    assertEquals(
        "Invalid filename: '$WRONG_ORIGINAL_FILE_NAME'. File name should neither contains '..' nor starts by '/'.",
        exception.message)
  }

  @Test
  fun `test queryData without parameters`() {

    val resourceTestFile = resourceLoader.getResource("classpath:/$CUSTOMER_SOURCE_FILE_NAME").file

    val createdDataset = createDatasetWithCustomersDatasetPart(resourceTestFile)

    val datasetPartId = createdDataset.parts.first { it.type == DatasetPartTypeEnum.DB }.id

    val queryResult =
        datasetApiService.queryData(
            organizationSaved.id,
            workspaceSaved.id,
            createdDataset.id,
            datasetPartId,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
        )

    val expectedText = FileInputStream(resourceTestFile).bufferedReader().use { it.readText() }
    val retrievedText =
        InputStreamResource(queryResult).inputStream.bufferedReader().use { it.readText() }

    assertEquals(expectedText, retrievedText)
  }

  @Test
  fun `test queryData - list customers orderby age desc`() {

    val resourceTestFile = resourceLoader.getResource("classpath:/$CUSTOMER_SOURCE_FILE_NAME").file

    val createdDataset = createDatasetWithCustomersDatasetPart(resourceTestFile)

    val datasetPartId = createdDataset.parts.first { it.type == DatasetPartTypeEnum.DB }.id

    val queryResult =
        datasetApiService.queryData(
            organizationSaved.id,
            workspaceSaved.id,
            createdDataset.id,
            datasetPartId,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            mutableListOf("!age"),
        )

    val expectedTestFile =
        resourceLoader.getResource("classpath:/query/customers_order_by_age_desc.csv").file
    val expectedText = FileInputStream(expectedTestFile).bufferedReader().use { it.readText() }
    val retrievedText =
        InputStreamResource(queryResult).inputStream.bufferedReader().use { it.readText() }

    assertEquals(expectedText, retrievedText)
  }

  @Test
  fun `test queryData with only selects ok`() {

    val customersTestFile = resourceLoader.getResource("classpath:/$CUSTOMER_SOURCE_FILE_NAME").file

    val createdDataset = createDatasetWithCustomersDatasetPart(customersTestFile)

    val datasetPartId = createdDataset.parts.first { it.type == DatasetPartTypeEnum.DB }.id

    val queryResult =
        datasetApiService.queryData(
            organizationSaved.id,
            workspaceSaved.id,
            createdDataset.id,
            datasetPartId,
            mutableListOf("customerID"),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
        )

    val expectedTestFile =
        resourceLoader.getResource("classpath:/query/customers_customerID_only.csv").file
    val expectedText = FileInputStream(expectedTestFile).bufferedReader().use { it.readText() }
    val retrievedText =
        InputStreamResource(queryResult).inputStream.bufferedReader().use { it.readText() }

    assertEquals(expectedText, retrievedText)
  }

  @Test
  fun `test queryData with only selects with 50K lines ok`() {
    val customersTestFile =
        resourceLoader.getResource("classpath:/$CUSTOMER_50K_SOURCE_FILE_NAME").file

    val startTime = System.currentTimeMillis()
    val createdDataset = createDatasetWithCustomersDatasetPart(customersTestFile)

    val datasetPartId = createdDataset.parts.first { it.type == DatasetPartTypeEnum.DB }.id

    val queryResult =
        datasetApiService.queryData(
            organizationSaved.id,
            workspaceSaved.id,
            createdDataset.id,
            datasetPartId,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
        )

    val expectedTestFile =
        resourceLoader.getResource("classpath:/$CUSTOMER_50K_SOURCE_FILE_NAME").file
    val expectedText = FileInputStream(expectedTestFile).bufferedReader().use { it.readText() }
    val retrievedText =
        InputStreamResource(queryResult).inputStream.bufferedReader().use { it.readText() }
    val endTime = System.currentTimeMillis()
    logger.error("${endTime - startTime}")
    assertEquals(expectedText, retrievedText)
  }

  @Test
  fun `test queryData with only sum ok`() {

    val customersTestFile = resourceLoader.getResource("classpath:/$CUSTOMER_SOURCE_FILE_NAME").file

    val createdDataset = createDatasetWithCustomersDatasetPart(customersTestFile)

    val datasetPartId = createdDataset.parts.first { it.type == DatasetPartTypeEnum.DB }.id

    val queryResult =
        datasetApiService.queryData(
            organizationSaved.id,
            workspaceSaved.id,
            createdDataset.id,
            datasetPartId,
            null,
            mutableListOf("age"),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
        )

    val expectedTestFile = resourceLoader.getResource("classpath:/query/customers_sum_age.csv").file
    val expectedText = FileInputStream(expectedTestFile).bufferedReader().use { it.readText() }
    val retrievedText =
        InputStreamResource(queryResult).inputStream.bufferedReader().use { it.readText() }

    assertEquals(expectedText, retrievedText)
  }

  @Test
  fun `test queryData with only avg ok`() {

    val customersTestFile = resourceLoader.getResource("classpath:/$CUSTOMER_SOURCE_FILE_NAME").file

    val createdDataset = createDatasetWithCustomersDatasetPart(customersTestFile)

    val datasetPartId = createdDataset.parts.first { it.type == DatasetPartTypeEnum.DB }.id

    val queryResult =
        datasetApiService.queryData(
            organizationSaved.id,
            workspaceSaved.id,
            createdDataset.id,
            datasetPartId,
            null,
            null,
            mutableListOf("age"),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
        )

    val expectedTestFile = resourceLoader.getResource("classpath:/query/customers_avg_age.csv").file
    val expectedText = FileInputStream(expectedTestFile).bufferedReader().use { it.readText() }
    val retrievedText =
        InputStreamResource(queryResult).inputStream.bufferedReader().use { it.readText() }

    assertEquals(expectedText, retrievedText)
  }

  @Test
  fun `test queryData with only distincts ok`() {

    val customersTestFile = resourceLoader.getResource("classpath:/$CUSTOMER_SOURCE_FILE_NAME").file

    val createdDataset = createDatasetWithCustomersDatasetPart(customersTestFile)

    val datasetPartId = createdDataset.parts.first { it.type == DatasetPartTypeEnum.DB }.id

    val queryResult =
        datasetApiService.queryData(
            organizationSaved.id,
            workspaceSaved.id,
            createdDataset.id,
            datasetPartId,
            mutableListOf("customerID*"),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
        )

    val expectedTestFile =
        resourceLoader.getResource("classpath:/query/customers_distinct_customerID_only.csv").file
    val expectedText = FileInputStream(expectedTestFile).bufferedReader().use { it.readText() }
    val retrievedText =
        InputStreamResource(queryResult).inputStream.bufferedReader().use { it.readText() }

    assertEquals(expectedText, retrievedText)
  }

  @Test
  fun `test queryData with only counts ok`() {

    val customersTestFile = resourceLoader.getResource("classpath:/$CUSTOMER_SOURCE_FILE_NAME").file

    val createdDataset = createDatasetWithCustomersDatasetPart(customersTestFile)

    val datasetPartId = createdDataset.parts.first { it.type == DatasetPartTypeEnum.DB }.id

    val queryResult =
        datasetApiService.queryData(
            organizationSaved.id,
            workspaceSaved.id,
            createdDataset.id,
            datasetPartId,
            null,
            null,
            null,
            mutableListOf("customerID"),
            null,
            null,
            null,
            null,
            null,
            null,
        )

    val expectedTestFile =
        resourceLoader.getResource("classpath:/query/customers_count_customerID.csv").file
    val expectedText = FileInputStream(expectedTestFile).bufferedReader().use { it.readText() }
    val retrievedText =
        InputStreamResource(queryResult).inputStream.bufferedReader().use { it.readText() }

    assertEquals(expectedText, retrievedText)
  }

  @Test
  fun `test queryData with only mins ok`() {

    val customersTestFile = resourceLoader.getResource("classpath:/$CUSTOMER_SOURCE_FILE_NAME").file

    val createdDataset = createDatasetWithCustomersDatasetPart(customersTestFile)

    val datasetPartId = createdDataset.parts.first { it.type == DatasetPartTypeEnum.DB }.id

    val queryResult =
        datasetApiService.queryData(
            organizationSaved.id,
            workspaceSaved.id,
            createdDataset.id,
            datasetPartId,
            null,
            null,
            null,
            null,
            mutableListOf("age"),
            null,
            null,
            null,
            null,
            null,
        )

    val expectedTestFile = resourceLoader.getResource("classpath:/query/customers_min_age.csv").file
    val expectedText = FileInputStream(expectedTestFile).bufferedReader().use { it.readText() }
    val retrievedText =
        InputStreamResource(queryResult).inputStream.bufferedReader().use { it.readText() }

    assertEquals(expectedText, retrievedText)
  }

  @Test
  fun `test queryData with only maxs ok`() {

    val customersTestFile = resourceLoader.getResource("classpath:/$CUSTOMER_SOURCE_FILE_NAME").file

    val createdDataset = createDatasetWithCustomersDatasetPart(customersTestFile)

    val datasetPartId = createdDataset.parts.first { it.type == DatasetPartTypeEnum.DB }.id

    val queryResult =
        datasetApiService.queryData(
            organizationSaved.id,
            workspaceSaved.id,
            createdDataset.id,
            datasetPartId,
            null,
            null,
            null,
            null,
            null,
            mutableListOf("age"),
            null,
            null,
            null,
            null,
        )

    val expectedTestFile = resourceLoader.getResource("classpath:/query/customers_max_age.csv").file
    val expectedText = FileInputStream(expectedTestFile).bufferedReader().use { it.readText() }
    val retrievedText =
        InputStreamResource(queryResult).inputStream.bufferedReader().use { it.readText() }

    assertEquals(expectedText, retrievedText)
  }

  @Test
  fun `test queryData with selects, sum and groupby ok`() {

    val customersTestFile = resourceLoader.getResource("classpath:/$CUSTOMER_SOURCE_FILE_NAME").file

    val createdDataset = createDatasetWithCustomersDatasetPart(customersTestFile)

    val datasetPartId = createdDataset.parts.first { it.type == DatasetPartTypeEnum.DB }.id

    val queryResult =
        datasetApiService.queryData(
            organizationSaved.id,
            workspaceSaved.id,
            createdDataset.id,
            datasetPartId,
            mutableListOf("customerID"),
            mutableListOf("age"),
            null,
            null,
            null,
            null,
            null,
            null,
            mutableListOf("customerID"),
            null,
        )

    val expectedTestFile =
        resourceLoader.getResource("classpath:/query/customers_customerID_sum_age.csv").file
    val expectedText = FileInputStream(expectedTestFile).bufferedReader().use { it.readText() }
    val retrievedText =
        InputStreamResource(queryResult).inputStream.bufferedReader().use { it.readText() }

    assertEquals(expectedText, retrievedText)
  }

  @Test
  fun `test queryData with selects and groupby ok`() {

    val customersTestFile = resourceLoader.getResource("classpath:/$CUSTOMER_SOURCE_FILE_NAME").file

    val createdDataset = createDatasetWithCustomersDatasetPart(customersTestFile)

    val datasetPartId = createdDataset.parts.first { it.type == DatasetPartTypeEnum.DB }.id

    val queryResult =
        datasetApiService.queryData(
            organizationSaved.id,
            workspaceSaved.id,
            createdDataset.id,
            datasetPartId,
            mutableListOf("country"),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            mutableListOf("country"),
            null,
        )

    val expectedTestFile =
        resourceLoader.getResource("classpath:/query/customers_country_group_by_country.csv").file
    val expectedText = FileInputStream(expectedTestFile).bufferedReader().use { it.readText() }
    val retrievedText =
        InputStreamResource(queryResult).inputStream.bufferedReader().use { it.readText() }

    assertEquals(expectedText, retrievedText)
  }

  @Test
  fun `test queryData with selects and orderby ok`() {

    val customersTestFile = resourceLoader.getResource("classpath:/$CUSTOMER_SOURCE_FILE_NAME").file

    val createdDataset = createDatasetWithCustomersDatasetPart(customersTestFile)

    val datasetPartId = createdDataset.parts.first { it.type == DatasetPartTypeEnum.DB }.id

    val queryResult =
        datasetApiService.queryData(
            organizationSaved.id,
            workspaceSaved.id,
            createdDataset.id,
            datasetPartId,
            mutableListOf("customerID", "age"),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            mutableListOf("age"))

    val expectedTestFile =
        resourceLoader.getResource("classpath:/query/customers_customerID_orderby_age_asc.csv").file
    val expectedText = FileInputStream(expectedTestFile).bufferedReader().use { it.readText() }
    val retrievedText =
        InputStreamResource(queryResult).inputStream.bufferedReader().use { it.readText() }

    assertEquals(expectedText, retrievedText)
  }

  @Test
  fun `test queryData with only limit ok`() {

    val customersTestFile = resourceLoader.getResource("classpath:/$CUSTOMER_SOURCE_FILE_NAME").file

    val createdDataset = createDatasetWithCustomersDatasetPart(customersTestFile)

    val datasetPartId = createdDataset.parts.first { it.type == DatasetPartTypeEnum.DB }.id

    val queryResult =
        datasetApiService.queryData(
            organizationSaved.id,
            workspaceSaved.id,
            createdDataset.id,
            datasetPartId,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            5,
            null,
            null,
        )

    val expectedTestFile = resourceLoader.getResource("classpath:/query/customers_limit_5.csv").file
    val expectedText = FileInputStream(expectedTestFile).bufferedReader().use { it.readText() }
    val retrievedText =
        InputStreamResource(queryResult).inputStream.bufferedReader().use { it.readText() }

    assertEquals(expectedText, retrievedText)
  }

  @Test
  fun `test queryData with only offset ok`() {

    val customersTestFile = resourceLoader.getResource("classpath:/$CUSTOMER_SOURCE_FILE_NAME").file

    val createdDataset = createDatasetWithCustomersDatasetPart(customersTestFile)

    val datasetPartId = createdDataset.parts.first { it.type == DatasetPartTypeEnum.DB }.id

    val queryResult =
        datasetApiService.queryData(
            organizationSaved.id,
            workspaceSaved.id,
            createdDataset.id,
            datasetPartId,
            null,
            null,
            null,
            null,
            null,
            null,
            5,
            null,
            null,
            null,
        )

    val expectedTestFile =
        resourceLoader.getResource("classpath:/query/customers_offset_5.csv").file
    val expectedText = FileInputStream(expectedTestFile).bufferedReader().use { it.readText() }
    val retrievedText =
        InputStreamResource(queryResult).inputStream.bufferedReader().use { it.readText() }

    assertEquals(expectedText, retrievedText)
  }

  @Test
  fun `test queryData with limit and offset ok`() {

    val customersTestFile = resourceLoader.getResource("classpath:/$CUSTOMER_SOURCE_FILE_NAME").file

    val createdDataset = createDatasetWithCustomersDatasetPart(customersTestFile)

    val datasetPartId = createdDataset.parts.first { it.type == DatasetPartTypeEnum.DB }.id

    val queryResult =
        datasetApiService.queryData(
            organizationSaved.id,
            workspaceSaved.id,
            createdDataset.id,
            datasetPartId,
            null,
            null,
            null,
            null,
            null,
            null,
            5,
            2,
            null,
            null,
        )

    val expectedTestFile =
        resourceLoader.getResource("classpath:/query/customers_offset_5_limit_2.csv").file
    val expectedText = FileInputStream(expectedTestFile).bufferedReader().use { it.readText() }
    val retrievedText =
        InputStreamResource(queryResult).inputStream.bufferedReader().use { it.readText() }

    assertEquals(expectedText, retrievedText)
  }

  @Test
  fun `test queryData with count distinct country ok`() {

    val customersTestFile = resourceLoader.getResource("classpath:/$CUSTOMER_SOURCE_FILE_NAME").file

    val createdDataset = createDatasetWithCustomersDatasetPart(customersTestFile)

    val datasetPartId = createdDataset.parts.first { it.type == DatasetPartTypeEnum.DB }.id

    val queryResult =
        datasetApiService.queryData(
            organizationSaved.id,
            workspaceSaved.id,
            createdDataset.id,
            datasetPartId,
            null,
            null,
            null,
            mutableListOf("country*"),
            null,
            null,
            null,
            null,
            null,
            null,
        )

    val expectedTestFile =
        resourceLoader.getResource("classpath:/query/customers_count_distinct_country.csv").file
    val expectedText = FileInputStream(expectedTestFile).bufferedReader().use { it.readText() }
    val retrievedText =
        InputStreamResource(queryResult).inputStream.bufferedReader().use { it.readText() }

    assertEquals(expectedText, retrievedText)
  }

  @Test
  fun `test queryData - list countries with customers in it ok`() {

    val customersTestFile = resourceLoader.getResource("classpath:/$CUSTOMER_SOURCE_FILE_NAME").file

    val createdDataset = createDatasetWithCustomersDatasetPart(customersTestFile)

    val datasetPartId = createdDataset.parts.first { it.type == DatasetPartTypeEnum.DB }.id

    val queryResult =
        datasetApiService.queryData(
            organizationSaved.id,
            workspaceSaved.id,
            createdDataset.id,
            datasetPartId,
            mutableListOf("country*"),
            null,
            null,
            mutableListOf("country"),
            null,
            null,
            null,
            null,
            mutableListOf("country"),
            mutableListOf("country"),
        )

    val expectedTestFile =
        resourceLoader
            .getResource("classpath:/query/customers_advanced_count_distinct_country.csv")
            .file
    val expectedText = FileInputStream(expectedTestFile).bufferedReader().use { it.readText() }
    val retrievedText =
        InputStreamResource(queryResult).inputStream.bufferedReader().use { it.readText() }

    assertEquals(expectedText, retrievedText)
  }

  @Test
  fun `test queryData with selects, avg and groupby ok`() {

    val customersTestFile = resourceLoader.getResource("classpath:/$CUSTOMER_SOURCE_FILE_NAME").file

    val createdDataset = createDatasetWithCustomersDatasetPart(customersTestFile)

    val datasetPartId = createdDataset.parts.first { it.type == DatasetPartTypeEnum.DB }.id

    val queryResult =
        datasetApiService.queryData(
            organizationSaved.id,
            workspaceSaved.id,
            createdDataset.id,
            datasetPartId,
            mutableListOf("customerID"),
            null,
            mutableListOf("age"),
            null,
            null,
            null,
            null,
            null,
            mutableListOf("customerID"),
            null,
        )

    val expectedTestFile =
        resourceLoader.getResource("classpath:/query/customers_customerID_avg_age.csv").file
    val expectedText = FileInputStream(expectedTestFile).bufferedReader().use { it.readText() }
    val retrievedText =
        InputStreamResource(queryResult).inputStream.bufferedReader().use { it.readText() }

    assertEquals(expectedText, retrievedText)
  }

  private fun createDatasetWithCustomersDatasetPart(resourceTestFile: File): Dataset {
    val datasetPartName = "Customers list"
    val datasetPartDescription = "List of customers"
    val datasetPartTags = mutableListOf("part", "public", "customers")
    val datasetPartCreateRequest =
        DatasetPartCreateRequest(
            name = datasetPartName,
            sourceName = CUSTOMER_SOURCE_FILE_NAME,
            description = datasetPartDescription,
            tags = datasetPartTags,
            type = DatasetPartTypeEnum.DB)

    val datasetName = "Customer Dataset"
    val datasetDescription = "Dataset for customers"
    val datasetTags = mutableListOf("dataset", "public", "customers")
    val datasetCreateRequest =
        DatasetCreateRequest(
            name = datasetName,
            description = datasetDescription,
            tags = datasetTags,
            parts = mutableListOf(datasetPartCreateRequest))

    val fileToSend = FileInputStream(resourceTestFile)

    val mockMultipartFile =
        MockMultipartFile(
            "files",
            CUSTOMER_SOURCE_FILE_NAME,
            MediaType.MULTIPART_FORM_DATA_VALUE,
            IOUtils.toByteArray(fileToSend))

    return datasetApiService.createDataset(
        organizationSaved.id, workspaceSaved.id, datasetCreateRequest, arrayOf(mockMultipartFile))
  }

  private fun testCreateDatasetPartWithSimpleFile(fileName: String, expectedFileName: String) {
    val datasetCreateRequest = DatasetCreateRequest(name = "Dataset Test $fileName")

    val createDataset =
        datasetApiService.createDataset(
            organizationSaved.id, workspaceSaved.id, datasetCreateRequest, arrayOf())

    assertTrue(createDataset.parts.isEmpty())

    val resourceTestFile = resourceLoader.getResource("classpath:/$fileName").file

    val fileToSend = FileInputStream(resourceTestFile)

    val mockMultipartFile =
        MockMultipartFile(
            "file", fileName, MediaType.MULTIPART_FORM_DATA_VALUE, IOUtils.toByteArray(fileToSend))

    val datasetPartName = "Customer list"
    val datasetPartDescription = "List of customers"
    val datasetPartTags = mutableListOf("part", "public", "customers")

    val createDatasetPart =
        datasetApiService.createDatasetPart(
            organizationSaved.id,
            workspaceSaved.id,
            createDataset.id,
            mockMultipartFile,
            DatasetPartCreateRequest(
                name = datasetPartName,
                sourceName = fileName,
                description = datasetPartDescription,
                tags = datasetPartTags,
                type = DatasetPartTypeEnum.DB))

    assertNotNull(createDatasetPart)
    assertEquals(datasetPartName, createDatasetPart.name)
    assertEquals(datasetPartDescription, createDatasetPart.description)
    assertEquals(datasetPartTags, createDatasetPart.tags)
    assertEquals(fileName, createDatasetPart.sourceName)

    val retrievedDataset =
        datasetApiService.getDataset(organizationSaved.id, workspaceSaved.id, createDataset.id)

    assertTrue(retrievedDataset.parts.isNotEmpty())
    assertEquals(retrievedDataset.parts.size, 1)
    assertEquals(createDatasetPart, retrievedDataset.parts[0])

    assertTrue(writerJdbcTemplate.existTable(createDatasetPart.id.replace('-', '_')))

    val datasetPartFile =
        datasetApiService.downloadDatasetPart(
            organizationSaved.id, workspaceSaved.id, createDataset.id, createDatasetPart.id)

    val expectedTestFile = resourceLoader.getResource("classpath:/$expectedFileName").file
    val expectedText = FileInputStream(expectedTestFile).bufferedReader().use { it.readText() }
    val retrievedText = datasetPartFile.inputStream.bufferedReader().use { it.readText() }
    assertEquals(expectedText, retrievedText)
  }

  private fun constructFilePathForDatasetPart(createdDataset: Dataset, partIndex: Int): String =
      "${createdDataset.organizationId}/${createdDataset.workspaceId}/${createdDataset.id}/${createdDataset.parts[partIndex].id}"

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
