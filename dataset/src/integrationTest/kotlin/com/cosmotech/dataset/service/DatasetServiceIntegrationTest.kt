// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.service

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.events.CsmEventPublisher
import com.cosmotech.api.events.TwingraphImportEvent
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.rbac.ROLE_ADMIN
import com.cosmotech.api.rbac.ROLE_EDITOR
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.rbac.ROLE_VIEWER
import com.cosmotech.api.security.ROLE_ORGANIZATION_USER
import com.cosmotech.api.security.ROLE_PLATFORM_ADMIN
import com.cosmotech.api.tests.CsmRedisTestBase
import com.cosmotech.api.utils.ResourceScanner
import com.cosmotech.api.utils.getCurrentAccountIdentifier
import com.cosmotech.api.utils.getCurrentAuthenticatedRoles
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.connector.ConnectorApiServiceInterface
import com.cosmotech.connector.domain.Connector
import com.cosmotech.dataset.DatasetApiServiceInterface
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.dataset.domain.DatasetAccessControl
import com.cosmotech.dataset.domain.DatasetCompatibility
import com.cosmotech.dataset.domain.DatasetConnector
import com.cosmotech.dataset.domain.DatasetRole
import com.cosmotech.dataset.domain.DatasetSearch
import com.cosmotech.dataset.domain.DatasetSecurity
import com.cosmotech.dataset.domain.DatasetSourceType
import com.cosmotech.dataset.domain.DatasetTwinGraphQuery
import com.cosmotech.dataset.domain.FileUploadMetadata
import com.cosmotech.dataset.domain.FileUploadValidation
import com.cosmotech.dataset.domain.GraphProperties
import com.cosmotech.dataset.domain.SourceInfo
import com.cosmotech.dataset.domain.SubDatasetGraphQuery
import com.cosmotech.dataset.repository.DatasetRepository
import com.cosmotech.organization.OrganizationApiServiceInterface
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationSecurity
import com.cosmotech.solution.SolutionApiServiceInterface
import com.cosmotech.solution.domain.Solution
import com.cosmotech.solution.domain.SolutionAccessControl
import com.cosmotech.solution.domain.SolutionSecurity
import com.cosmotech.workspace.WorkspaceApiServiceInterface
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceAccessControl
import com.cosmotech.workspace.domain.WorkspaceSecurity
import com.cosmotech.workspace.domain.WorkspaceSolution
import com.ninjasquad.springmockk.SpykBean
import com.redis.om.spring.RediSearchIndexer
import com.redis.testcontainers.RedisStackContainer
import com.redislabs.redisgraph.impl.api.RedisGraph
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.mockkStatic
import java.io.File
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.runner.RunWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ByteArrayResource
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.util.ReflectionTestUtils
import redis.clients.jedis.JedisPool

const val REDIS_PORT = 6379
const val CONNECTED_ADMIN_USER = "test.admin@cosmotech.com"
const val TEST_USER_MAIL = "testuser@mail.fr"

@ActiveProfiles(profiles = ["dataset-test"])
@ExtendWith(MockKExtension::class)
@ExtendWith(SpringExtension::class)
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Suppress("LargeClass", "TooManyFunctions")
class DatasetServiceIntegrationTest : CsmRedisTestBase() {

  private val logger = LoggerFactory.getLogger(DatasetServiceIntegrationTest::class.java)

  @Autowired lateinit var rediSearchIndexer: RediSearchIndexer
  @SpykBean @Autowired lateinit var resourceScanner: ResourceScanner
  @SpykBean @Autowired lateinit var datasetApiService: DatasetApiServiceInterface
  @Autowired lateinit var datasetRepository: DatasetRepository
  @Autowired lateinit var connectorApiService: ConnectorApiServiceInterface
  @Autowired lateinit var organizationApiService: OrganizationApiServiceInterface
  @Autowired lateinit var solutionApiService: SolutionApiServiceInterface
  @Autowired lateinit var workspaceApiService: WorkspaceApiServiceInterface
  @SpykBean @Autowired lateinit var csmPlatformProperties: CsmPlatformProperties
  @SpykBean @Autowired lateinit var csmRedisGraph: RedisGraph
  @MockK(relaxUnitFun = true) private lateinit var eventPublisher: CsmEventPublisher

  lateinit var connectorSaved: Connector
  lateinit var dataset: Dataset
  lateinit var dataset2: Dataset
  lateinit var datasetSaved: Dataset
  lateinit var retrievedDataset1: Dataset
  lateinit var solution: Solution
  lateinit var workspace: Workspace

  lateinit var jedisPool: JedisPool
  lateinit var redisGraph: RedisGraph
  lateinit var organization: Organization
  lateinit var organizationSaved: Organization
  lateinit var solutionSaved: Solution
  lateinit var workspaceSaved: Workspace

  @BeforeAll
  fun beforeAll() {
    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
    mockkStatic("com.cosmotech.api.utils.RedisUtilsKt")
    mockkStatic("org.springframework.web.context.request.RequestContextHolder")
    val context = getContext(redisStackServer)
    val containerIp =
        (context.server as RedisStackContainer).containerInfo.networkSettings.ipAddress
    jedisPool = JedisPool(containerIp, REDIS_PORT)
    redisGraph = RedisGraph(jedisPool)
    ReflectionTestUtils.setField(datasetApiService, "csmJedisPool", jedisPool)
    ReflectionTestUtils.setField(datasetApiService, "csmRedisGraph", redisGraph)
  }

  @BeforeEach
  fun beforeEach() {
    every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "test.user"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf()
    rediSearchIndexer.createIndexFor(Connector::class.java)
    rediSearchIndexer.createIndexFor(Organization::class.java)
    rediSearchIndexer.createIndexFor(Dataset::class.java)
    rediSearchIndexer.createIndexFor(Solution::class.java)
    rediSearchIndexer.createIndexFor(Workspace::class.java)

    connectorSaved = connectorApiService.registerConnector(makeConnector())

    organization = makeOrganizationWithRole()
    organizationSaved = organizationApiService.registerOrganization(organization)
    dataset = makeDatasetWithRole()
    datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
    dataset2 = makeDataset()
    solution = makeSolution()
    solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)
    workspace = makeWorkspace()
    workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
  }

  @AfterEach
  fun afterEach() {
    clearAllMocks()
  }

  @Test
  fun `test Dataset CRUD`() {

    organizationSaved = organizationApiService.registerOrganization(organization)
    datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)

    val registeredDataset2 = datasetApiService.createDataset(organizationSaved.id!!, dataset2)

    logger.info("Fetch dataset : ${datasetSaved.id}...")
    retrievedDataset1 = datasetApiService.findDatasetById(organizationSaved.id!!, datasetSaved.id!!)
    assertNotNull(retrievedDataset1)

    logger.info("Fetch all datasets...")
    var datasetList = datasetApiService.findAllDatasets(organizationSaved.id!!, null, null)
    for (item in datasetList) {
      logger.warn(item.id)
    }
    assertTrue { datasetList.size == 2 }

    logger.info("Delete Dataset : ${registeredDataset2.id}...")
    datasetApiService.deleteDataset(organizationSaved.id!!, registeredDataset2.id!!)
    datasetList = datasetApiService.findAllDatasets(organizationSaved.id!!, null, null)
    assertTrue { datasetList.size == 1 }
  }

  @Test
  fun `test Dataset - findByOrganizationIdAndDatasetId`() {
    organizationSaved = organizationApiService.registerOrganization(organization)
    datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)

    logger.info("Fetch dataset...")
    val datasetRetrieved =
        datasetApiService.findByOrganizationIdAndDatasetId(
            organizationSaved.id!!, datasetSaved.id!!)
    assertNotNull(datasetRetrieved)
    assertEquals(datasetSaved, datasetRetrieved)
  }

  @Test
  fun `test Dataset - findByOrganizationIdAndDatasetId wrong dataset id`() {
    organizationSaved = organizationApiService.registerOrganization(organization)
    datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)

    logger.info("Fetch dataset...")
    val datasetRetrieved =
        datasetApiService.findByOrganizationIdAndDatasetId(organizationSaved.id!!, "wrong_id")
    assertNull(datasetRetrieved)
  }

  @Test
  fun `can delete dataset when user is not the owner and is Platform Admin`() {

    logger.info("Register dataset : ${dataset.id}...")
    organizationSaved = organizationApiService.registerOrganization(organization)
    datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
    assertNotNull(datasetSaved)
    logger.info("Change current user...")
    every { getCurrentAccountIdentifier(any()) } returns "test.user.admin@cosmotech.com"
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "test.admin"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf(ROLE_PLATFORM_ADMIN)
    assertNotNull(datasetSaved.id)
    datasetSaved.id?.let { datasetApiService.deleteDataset(organizationSaved.id!!, it) }

    logger.info("Fetch dataset : ${datasetSaved.id}...")
    assertThrows<CsmResourceNotFoundException> {
      datasetApiService.findDatasetById(organizationSaved.id!!, datasetSaved.id!!)
    }
  }

  @Test
  fun `can not delete dataset when user is not the owner and not Platform Admin`() {

    logger.info("Register dataset : ${dataset.id}...")
    organizationSaved = organizationApiService.registerOrganization(organization)
    datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
    assertNotNull(datasetSaved)
    logger.info("Change current user...")
    every { getCurrentAccountIdentifier(any()) } returns "test.user.other@cosmotech.com"
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "test.user.other"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf(ROLE_ORGANIZATION_USER)
    assertNotNull(datasetSaved.id)
    assertThrows<CsmAccessForbiddenException> {
      datasetSaved.id?.let { datasetApiService.deleteDataset(organizationSaved.id!!, it) }
    }
  }

  @Test
  fun `can update dataset owner when user is not the owner and is Platform Admin`() {

    logger.info("Register dataset : ${dataset.id}...")
    organizationSaved = organizationApiService.registerOrganization(organization)
    datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
    assertNotNull(datasetSaved)
    logger.info("Change current user...")
    every { getCurrentAccountIdentifier(any()) } returns "test.user.admin@cosmotech.com"
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "test.admin"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf(ROLE_PLATFORM_ADMIN)
    assertNotNull(datasetSaved.id)
    datasetSaved.ownerId = "new_owner_id"
    datasetSaved.id?.let {
      datasetApiService.updateDataset(organizationSaved.id!!, it, datasetSaved)
    }

    logger.info("Fetch dataset : ${datasetSaved.id}...")
    val datasetUpdated =
        datasetApiService.findDatasetById(organizationSaved.id!!, datasetSaved.id!!)
    assertEquals("new_owner_id", datasetUpdated.ownerId)
  }

  @Test
  fun `cannot update dataset owner when user is not the owner and is not Platform Admin`() {

    logger.info("Register dataset : ${dataset.id}...")
    organizationSaved = organizationApiService.registerOrganization(organization)
    datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
    assertNotNull(datasetSaved)
    logger.info("Change current user...")
    every { getCurrentAccountIdentifier(any()) } returns "test.user.admin@cosmotech.com"
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "test.admin"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf()
    assertNotNull(datasetSaved.id)
    datasetSaved.ownerId = "new_owner_id"
    assertThrows<CsmAccessForbiddenException> {
      datasetSaved.id?.let {
        datasetApiService.updateDataset(organizationSaved.id!!, it, datasetSaved)
      }
    }
  }

  fun `test special endpoints`() {
    logger.info("Copy a Dataset...")
    // TODO("Not yet implemented")

    logger.info("Search Datasets...")
    val datasetList =
        datasetApiService.searchDatasets(
            organizationSaved.id!!, DatasetSearch(mutableListOf("data")), null, null)
    assertTrue { datasetList.size == 2 }

    logger.info("Update Dataset : ${datasetSaved.id}...")
    val retrievedDataset1 =
        datasetApiService.updateDataset(organizationSaved.id!!, datasetSaved.id!!, dataset2)
    assertNotEquals(retrievedDataset1, datasetSaved)
  }

  fun `test dataset compatibility elements`() {
    logger.info("Add Dataset Compatibility elements...")
    var datasetCompatibilityList =
        datasetApiService.addOrReplaceDatasetCompatibilityElements(
            organizationSaved.id!!,
            datasetSaved.id!!,
            datasetCompatibility =
                listOf(
                    DatasetCompatibility(solutionKey = "solution"),
                    DatasetCompatibility(solutionKey = "test")))
    assertFalse { datasetCompatibilityList.isEmpty() }

    logger.info("Remove all Dataset Compatibility elements from dataset : ${datasetSaved.id!!}...")
    datasetApiService.removeAllDatasetCompatibilityElements(
        organizationSaved.id!!, datasetSaved.id!!)
    datasetCompatibilityList =
        datasetApiService.findDatasetById(organizationSaved.id!!, datasetSaved.id!!).compatibility!!
    assertTrue { datasetCompatibilityList.isEmpty() }
  }

  @Test
  fun `test find All Datasets as Platform Admin`() {
    organizationSaved = organizationApiService.registerOrganization(organization)
    val numberOfDatasets = 20
    val defaultPageSize = csmPlatformProperties.twincache.dataset.defaultPageSize
    val expectedSize = 15
    IntRange(1, numberOfDatasets).forEach {
      datasetApiService.createDataset(
          organizationSaved.id!!, makeDataset("d-dataset-$it", "dataset-$it"))
    }
    logger.info("Change current user...")
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "test.admin"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf(ROLE_PLATFORM_ADMIN)

    logger.info("should find all datasets and assert there are $numberOfDatasets")
    var datasetList = datasetApiService.findAllDatasets(organizationSaved.id!!, null, null)
    assertEquals(numberOfDatasets + 1, datasetList.size)

    logger.info("should find all datasets and assert it equals defaultPageSize: $defaultPageSize")
    datasetList = datasetApiService.findAllDatasets(organizationSaved.id!!, 0, null)
    assertEquals(defaultPageSize, datasetList.size)

    logger.info("should find all datasets and assert there are expected size: $expectedSize")
    datasetList = datasetApiService.findAllDatasets(organizationSaved.id!!, 0, expectedSize)
    assertEquals(expectedSize, datasetList.size)

    logger.info("should find all solutions and assert it returns the second / last page")
    datasetList = datasetApiService.findAllDatasets(organizationSaved.id!!, 1, expectedSize)
    assertEquals(numberOfDatasets - expectedSize + 1, datasetList.size)
  }

  @Test
  fun `test find All Datasets as Organization User`() {
    organizationSaved = organizationApiService.registerOrganization(organization)
    val numberOfDatasets = 20
    val defaultPageSize = csmPlatformProperties.twincache.dataset.defaultPageSize
    val expectedSize = 15
    IntRange(1, numberOfDatasets).forEach {
      datasetApiService.createDataset(
          organizationSaved.id!!,
          makeDatasetWithRole(
              organizationId = "d-dataset-$it",
              parentId = "dataset-$it",
              userName = "ANOTHER_USER"))
    }
    logger.info("should find all datasets and assert there are $numberOfDatasets")
    var datasetList = datasetApiService.findAllDatasets(organizationSaved.id!!, null, null)
    assertEquals(0, datasetList.size)

    logger.info("should find all datasets and assert it equals defaultPageSize: $defaultPageSize")
    datasetList = datasetApiService.findAllDatasets(organizationSaved.id!!, 0, null)
    assertEquals(0, datasetList.size)

    logger.info("should find all datasets and assert there are expected size: $expectedSize")
    datasetList = datasetApiService.findAllDatasets(organizationSaved.id!!, 0, expectedSize)
    assertEquals(0, datasetList.size)

    logger.info("should find all solutions and assert it returns the second / last page")
    datasetList = datasetApiService.findAllDatasets(organizationSaved.id!!, 1, expectedSize)
    assertEquals(0, datasetList.size)
  }

  @Test
  fun `test find All Datasets with different pagination params`() {
    organizationSaved = organizationApiService.registerOrganization(organization)
    val numberOfDatasets = 20
    val defaultPageSize = csmPlatformProperties.twincache.dataset.defaultPageSize
    val expectedSize = 15
    IntRange(1, numberOfDatasets).forEach {
      datasetApiService.createDataset(
          organizationSaved.id!!, makeDatasetWithRole("d-dataset-$it", "dataset-$it"))
    }

    logger.info("should find all datasets and assert there are $numberOfDatasets")
    var datasetList = datasetApiService.findAllDatasets(organizationSaved.id!!, null, null)
    assertEquals(numberOfDatasets, datasetList.size)

    logger.info("should find all datasets and assert it equals defaultPageSize: $defaultPageSize")
    datasetList = datasetApiService.findAllDatasets(organizationSaved.id!!, 0, null)
    assertEquals(defaultPageSize, datasetList.size)

    logger.info("should find all datasets and assert there are expected size: $expectedSize")
    datasetList = datasetApiService.findAllDatasets(organizationSaved.id!!, 0, expectedSize)
    assertEquals(expectedSize, datasetList.size)

    logger.info("should find all solutions and assert it returns the second / last page")
    datasetList = datasetApiService.findAllDatasets(organizationSaved.id!!, 1, expectedSize)
    assertEquals(numberOfDatasets - expectedSize, datasetList.size)
  }

  @Test
  fun `test find All Datasets with wrong pagination params`() {
    organizationSaved = organizationApiService.registerOrganization(organization)
    datasetApiService.createDataset(organizationSaved.id!!, dataset)

    logger.info("Should throw IllegalArgumentException when page and size are zeros")
    assertThrows<IllegalArgumentException> {
      datasetApiService.findAllDatasets(organizationSaved.id!!, 0, 0)
    }

    logger.info("Should throw IllegalArgumentException when page is negative")
    assertThrows<IllegalArgumentException> {
      datasetApiService.findAllDatasets(organizationSaved.id!!, -1, 10)
    }

    logger.info("Should throw IllegalArgumentException when size is negative")
    assertThrows<IllegalArgumentException> {
      datasetApiService.findAllDatasets(organizationSaved.id!!, 0, -1)
    }
  }

  @Test
  fun `should create graph & check queries on subDataset creation`() {
    logger.info("Create a Graph with a ZIP Entry")
    logger.info(
        "loading nodes: Double=2, Single=1, Users=9 & relationships: Double=2, Single=1, Follows=2")
    val file = this::class.java.getResource("/integrationTest.zip")?.file
    val resource = ByteArrayResource(File(file!!).readBytes())
    organizationSaved = organizationApiService.registerOrganization(organization)
    dataset = makeDatasetWithRole()
    datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
    datasetApiService.updateDataset(
        organizationSaved.id!!,
        datasetSaved.id!!,
        dataset.copy(sourceType = DatasetSourceType.File))

    val fileUploadValidation =
        datasetApiService.uploadTwingraph(organizationSaved.id!!, datasetSaved.id!!, resource)
    assertEquals(
        FileUploadValidation(
            mutableListOf(
                FileUploadMetadata("Double", 90),
                FileUploadMetadata("Single", 54),
                FileUploadMetadata("Users", 749),
            ),
            mutableListOf(
                FileUploadMetadata("Double", 214),
                FileUploadMetadata("Follows", 47),
                FileUploadMetadata("SingleEdge", 59),
            )),
        fileUploadValidation)

    // add timout for while loop
    val timeout = Instant.now()
    while (datasetApiService.getDatasetTwingraphStatus(organizationSaved.id!!, datasetSaved.id!!) !=
        Dataset.IngestionStatus.SUCCESS.value) {
      if (Instant.now().minusSeconds(10).isAfter(timeout)) {
        throw Exception("Timeout while waiting for dataset twingraph to be ready")
      }
      Thread.sleep(500)
    }
    datasetSaved = datasetApiService.findDatasetById(organizationSaved.id!!, datasetSaved.id!!)
    do {
      Thread.sleep(50L)
      val datasetStatus =
          datasetApiService.getDatasetTwingraphStatus(organizationSaved.id!!, datasetSaved.id!!)
    } while (datasetStatus == Dataset.IngestionStatus.PENDING.value)
    assertEquals(12, countEntities(datasetSaved.twingraphId!!, "MATCH (n) RETURN count(n)"))
    assertEquals(5, countEntities(datasetSaved.twingraphId!!, "MATCH ()-[r]-() RETURN count(r)"))

    logger.info("assert that subdataset without query has 12 initial nodes")
    val subDatasetParams =
        SubDatasetGraphQuery(
            name = "subDataset",
            description = "subDataset description",
        )
    val subDataset =
        datasetApiService.createSubDataset(
            organizationSaved.id!!, datasetSaved.id!!, subDatasetParams)
    do {
      Thread.sleep(50L)
      val datasetStatus =
          datasetApiService.getDatasetTwingraphStatus(organizationSaved.id!!, subDataset.id!!)
    } while (datasetStatus == Dataset.IngestionStatus.PENDING.value)

    assertEquals("subDataset", subDataset.name)
    assertEquals("subDataset description", subDataset.description)
    assertEquals(12, countEntities(subDataset.twingraphId!!, "MATCH (n) RETURN count(n)"))
    assertEquals(5, countEntities(subDataset.twingraphId!!, "MATCH ()-[r]-() RETURN count(r)"))

    logger.info("assert that subdataset with given query get 2 'Double' nodes")
    val subDatasetParamsQuery =
        SubDatasetGraphQuery(
            name = "subDatasetWithQuery",
            description = "subDatasetWithQuery description",
            queries = mutableListOf("MATCH (n)-[r:Double]-(m) return n,r"))

    val subDatasetWithQuery =
        datasetApiService.createSubDataset(
            organizationSaved.id!!, datasetSaved.id!!, subDatasetParamsQuery)
    do {
      Thread.sleep(50L)
      val datasetStatus =
          datasetApiService.getDatasetTwingraphStatus(
              organizationSaved.id!!, subDatasetWithQuery.id!!)
    } while (datasetStatus == Dataset.IngestionStatus.PENDING.value)

    assertEquals("subDatasetWithQuery", subDatasetWithQuery.name)
    assertEquals("subDatasetWithQuery description", subDatasetWithQuery.description)
    assertEquals(3, countEntities(subDatasetWithQuery.twingraphId!!, "MATCH (n) RETURN count(n)"))
    assertEquals(
        2, countEntities(subDatasetWithQuery.twingraphId!!, "MATCH ()-[r]-() RETURN count(r)"))
  }
  @Test
  fun `should not change parent connector parameters on subdataset creation`() {
    logger.info("Create a Graph with a ZIP Entry")
    logger.info(
        "loading nodes: Double=2, Single=1, Users=9 & relationships: Double=2, Single=1, Follows=2")
    val file = this::class.java.getResource("/integrationTest.zip")?.file
    val resource = ByteArrayResource(File(file!!).readBytes())
    organizationSaved = organizationApiService.registerOrganization(organization)
    dataset = makeDatasetWithRole()
    datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
    datasetApiService.updateDataset(
        organizationSaved.id!!,
        datasetSaved.id!!,
        datasetSaved.copy(sourceType = DatasetSourceType.File))

    val fileUploadValidation =
        datasetApiService.uploadTwingraph(organizationSaved.id!!, datasetSaved.id!!, resource)
    assertEquals(
        FileUploadValidation(
            mutableListOf(
                FileUploadMetadata("Double", 90),
                FileUploadMetadata("Single", 54),
                FileUploadMetadata("Users", 749),
            ),
            mutableListOf(
                FileUploadMetadata("Double", 214),
                FileUploadMetadata("Follows", 47),
                FileUploadMetadata("SingleEdge", 59),
            )),
        fileUploadValidation)

    // add timout for while loop
    val timeout = Instant.now()
    while (datasetApiService.getDatasetTwingraphStatus(organizationSaved.id!!, datasetSaved.id!!) !=
        Dataset.IngestionStatus.SUCCESS.value) {
      if (Instant.now().minusSeconds(10).isAfter(timeout)) {
        throw Exception("Timeout while waiting for dataset twingraph to be ready")
      }
      Thread.sleep(500)
    }
    datasetSaved = datasetApiService.findDatasetById(organizationSaved.id!!, datasetSaved.id!!)
    do {
      Thread.sleep(50L)
      val datasetStatus =
          datasetApiService.getDatasetTwingraphStatus(organizationSaved.id!!, datasetSaved.id!!)
    } while (datasetStatus == Dataset.IngestionStatus.PENDING.value)
    assertEquals(12, countEntities(datasetSaved.twingraphId!!, "MATCH (n) RETURN count(n)"))
    assertEquals(5, countEntities(datasetSaved.twingraphId!!, "MATCH ()-[r]-() RETURN count(r)"))
    assertEquals(
        datasetSaved.connector!!.parametersValues!!["TWIN_CACHE_NAME"], datasetSaved.twingraphId)

    val subDatasetParams =
        SubDatasetGraphQuery(
            name = "subDataset",
            description = "subDataset description",
        )
    val subDataset =
        datasetApiService.createSubDataset(
            organizationSaved.id!!, datasetSaved.id!!, subDatasetParams)
    do {
      Thread.sleep(50L)
      val datasetStatus =
          datasetApiService.getDatasetTwingraphStatus(organizationSaved.id!!, subDataset.id!!)
    } while (datasetStatus == Dataset.IngestionStatus.PENDING.value)

    // get parent after sub dataset creation
    datasetSaved = datasetApiService.findDatasetById(organizationSaved.id!!, datasetSaved.id!!)

    assertEquals("subDataset", subDataset.name)
    assertEquals("subDataset description", subDataset.description)
    assertEquals(12, countEntities(subDataset.twingraphId!!, "MATCH (n) RETURN count(n)"))
    assertEquals(5, countEntities(subDataset.twingraphId!!, "MATCH ()-[r]-() RETURN count(r)"))
    assertEquals(
        datasetSaved.connector!!.parametersValues!!["TWIN_CACHE_NAME"], datasetSaved.twingraphId)
    assertEquals(
        subDataset.connector!!.parametersValues!!["TWIN_CACHE_NAME"], subDataset.twingraphId)
  }

  fun countEntities(twingraphId: String, query: String): Int {
    val resultSet = redisGraph.query(twingraphId, query)
    return resultSet.next().values()[0].toString().toInt()
  }

  @Test
  fun `Twingraph CRUD test`() {

    logger.info("Create Nodes")
    organizationSaved = organizationApiService.registerOrganization(organization)
    datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
    val nodeStart =
        datasetApiService.createTwingraphEntities(
            organizationSaved.id!!,
            datasetSaved.id!!,
            "node",
            listOf(
                GraphProperties().apply {
                  type = "node"
                  name = "node_a"
                  params = "size:0"
                },
                GraphProperties().apply {
                  type = "node"
                  name = "node_b"
                  params = "size:1"
                }))
    assertNotEquals(String(), nodeStart)

    logger.info("Read Nodes")
    var nodeResult =
        datasetApiService.getTwingraphEntities(
            organizationSaved.id!!, datasetSaved.id!!, "node", listOf("node_a", "node_b"))
    assertEquals(nodeStart, nodeResult)

    logger.info("Create Relationships")
    val relationshipStart =
        datasetApiService.createTwingraphEntities(
            organizationSaved.id!!,
            datasetSaved.id!!,
            "relationship",
            listOf(
                GraphProperties().apply {
                  type = "relationship"
                  source = "node_a"
                  target = "node_b"
                  name = "relationship_a"
                  params = "duration:0"
                }))

    logger.info("Read Relationships")
    var relationshipResult =
        datasetApiService.getTwingraphEntities(
            organizationSaved.id!!, datasetSaved.id!!, "relationship", listOf("relationship_a"))
    assertEquals(relationshipStart, relationshipResult)

    logger.info("Update Nodes")
    nodeResult =
        datasetApiService.updateTwingraphEntities(
            organizationSaved.id!!,
            datasetSaved.id!!,
            "node",
            listOf(
                GraphProperties().apply {
                  name = "node_a"
                  params = "size:2"
                }))
    assertNotEquals(nodeStart, nodeResult)

    logger.info("Update Relationships")
    relationshipResult =
        datasetApiService.updateTwingraphEntities(
            organizationSaved.id!!,
            datasetSaved.id!!,
            "relationship",
            listOf(
                GraphProperties().apply {
                  source = "node_a"
                  target = "node_b"
                  name = "relationship_a"
                  params = "duration:2"
                }))
    assertNotEquals(relationshipStart, relationshipResult)

    logger.info("Delete Relationships")
    datasetApiService.deleteTwingraphEntities(
        organizationSaved.id!!, datasetSaved.id!!, "node", listOf("relationship_a"))
    assertDoesNotThrow {
      datasetApiService.getTwingraphEntities(
          organizationSaved.id!!, datasetSaved.id!!, "node", listOf("relationship_a"))
    }

    logger.info("Delete Nodes")
    datasetApiService.deleteTwingraphEntities(
        organizationSaved.id!!, datasetSaved.id!!, "relationship", listOf("node_a"))
    assertDoesNotThrow {
      datasetApiService.getTwingraphEntities(
          organizationSaved.id!!, datasetSaved.id!!, "relationship", listOf("node_a"))
    }
  }

  @Test
  fun `test get security endpoint`() {
    organizationSaved = organizationApiService.registerOrganization(organization)
    datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
    // should return the current security
    val datasetSecurity =
        datasetApiService.getDatasetSecurity(organizationSaved.id!!, datasetSaved.id!!)
    assertEquals(datasetSaved.security, datasetSecurity)
  }

  @Test
  fun `test set default security endpoint`() {
    organizationSaved = organizationApiService.registerOrganization(organization)
    datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
    // should update the default security and assert it worked
    val datasetDefaultSecurity =
        datasetApiService.setDatasetDefaultSecurity(
            organizationSaved.id!!, datasetSaved.id!!, DatasetRole(ROLE_VIEWER))
    datasetSaved = datasetApiService.findDatasetById(organizationSaved.id!!, datasetSaved.id!!)
    assertEquals(datasetSaved.security!!, datasetDefaultSecurity)
  }

  @Test
  fun `test uploadTwingraph status`() {
    organizationSaved = organizationApiService.registerOrganization(organization)
    datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
    val file = this::class.java.getResource("/integrationTest.zip")?.file
    val resource = ByteArrayResource(File(file!!).readBytes())

    datasetApiService.uploadTwingraph(organizationSaved.id!!, datasetSaved.id!!, resource)

    var datasetStatus: String
    do {
      Thread.sleep(50L)
      datasetStatus =
          datasetApiService.getDatasetTwingraphStatus(organizationSaved.id!!, datasetSaved.id!!)
    } while (datasetStatus == Dataset.IngestionStatus.PENDING.value)

    assertEquals(Dataset.IngestionStatus.SUCCESS.value, datasetStatus)

    val modifiedDataset =
        datasetApiService.findDatasetById(organizationSaved.id!!, datasetSaved.id!!)
    assertEquals(Dataset.TwincacheStatus.FULL.value, modifiedDataset.twincacheStatus!!.value)
  }

  @Test
  fun `test uploadTwingraph fail set dataset status to error`() {
    organizationSaved = organizationApiService.registerOrganization(organization)
    datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
    val file = this::class.java.getResource("/brokenGraph.zip")?.file
    val resource = ByteArrayResource(File(file!!).readBytes())

    datasetApiService.uploadTwingraph(organizationSaved.id!!, datasetSaved.id!!, resource)

    var datasetStatus: String
    do {
      Thread.sleep(50L)
      datasetStatus =
          datasetApiService.getDatasetTwingraphStatus(organizationSaved.id!!, datasetSaved.id!!)
    } while (datasetStatus == Dataset.IngestionStatus.PENDING.value)

    assertEquals(Dataset.IngestionStatus.ERROR.value, datasetStatus)

    val modifiedDataset =
        datasetApiService.findDatasetById(organizationSaved.id!!, datasetSaved.id!!)
    assertEquals(Dataset.IngestionStatus.ERROR.value, modifiedDataset.ingestionStatus!!.value)
    assertEquals(Dataset.TwincacheStatus.EMPTY.value, modifiedDataset.twincacheStatus!!.value)
  }

  @Test
  fun `access control list shouldn't contain more than one time each user on creation`() {
    connectorSaved = connectorApiService.registerConnector(makeConnector())
    organizationSaved = organizationApiService.registerOrganization(makeOrganizationWithRole())
    val brokenDataset =
        Dataset(
            name = "dataset",
            connector = DatasetConnector(connectorSaved.id!!, connectorSaved.name),
            security =
                DatasetSecurity(
                    default = ROLE_NONE,
                    accessControlList =
                        mutableListOf(
                            DatasetAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                            DatasetAccessControl(CONNECTED_ADMIN_USER, ROLE_EDITOR))))
    assertThrows<IllegalArgumentException> {
      datasetApiService.createDataset(organizationSaved.id!!, brokenDataset)
    }
  }

  @Test
  fun `access control list shouldn't contain more than one time each user on ACL addition`() {
    connectorSaved = connectorApiService.registerConnector(makeConnector())
    organizationSaved = organizationApiService.registerOrganization(makeOrganizationWithRole())
    val workingDataset = makeDatasetWithRole("dataset", sourceType = DatasetSourceType.None)
    val datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, workingDataset)

    assertThrows<IllegalArgumentException> {
      datasetApiService.addDatasetAccessControl(
          organizationSaved.id!!,
          datasetSaved.id!!,
          DatasetAccessControl(CONNECTED_ADMIN_USER, ROLE_EDITOR))
    }
  }

  @Test
  fun `reupload a twingraph in dataset with source type File`() {
    organizationSaved = organizationApiService.registerOrganization(organization)
    datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)

    val fileName = this::class.java.getResource("/integrationTest.zip")?.file
    val file = File(fileName!!)
    val resource = ByteArrayResource(file.readBytes())
    datasetApiService.uploadTwingraph(organizationSaved.id!!, datasetSaved.id!!, resource)
    var datasetStatus: String
    do {
      Thread.sleep(50L)
    } while (datasetApiService.getDatasetTwingraphStatus(
        organizationSaved.id!!, datasetSaved.id!!) == Dataset.IngestionStatus.PENDING.value)

    datasetApiService.createTwingraphEntities(
        organizationSaved.id!!,
        datasetSaved.id!!,
        "node",
        listOf(GraphProperties(type = "Node", name = "newNode", params = "value:0")))
    var queryResult =
        datasetApiService.twingraphQuery(
            organizationSaved.id!!, datasetSaved.id!!, DatasetTwinGraphQuery("MATCH (n) RETURN n"))
    val initalNodeAmount = queryResult.split("}}}").size

    datasetApiService.uploadTwingraph(organizationSaved.id!!, datasetSaved.id!!, resource)
    do {
      Thread.sleep(50L)
    } while (datasetApiService.getDatasetTwingraphStatus(
        organizationSaved.id!!, datasetSaved.id!!) == Dataset.IngestionStatus.PENDING.value)

    queryResult =
        datasetApiService.twingraphQuery(
            organizationSaved.id!!, datasetSaved.id!!, DatasetTwinGraphQuery("MATCH (n) RETURN n"))
    val newNodeAmount = queryResult.split("}}}").size

    assertNotEquals(initalNodeAmount, newNodeAmount)
  }

  @Test
  fun `rollback endpoint call should fail if status is not ERROR`() {
    organizationSaved = organizationApiService.registerOrganization(organization)
    datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)

    datasetSaved =
        datasetRepository.save(
            datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.NONE })
    var exception =
        assertThrows<IllegalArgumentException> {
          datasetApiService.rollbackRefresh(organizationSaved.id!!, datasetSaved.id!!)
        }
    assertEquals("The dataset hasn't failed and can't be rolled back", exception.message)

    datasetSaved =
        datasetRepository.save(
            datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.PENDING })
    exception =
        assertThrows<IllegalArgumentException> {
          datasetApiService.rollbackRefresh(organizationSaved.id!!, datasetSaved.id!!)
        }
    assertEquals("The dataset hasn't failed and can't be rolled back", exception.message)

    datasetSaved =
        datasetRepository.save(
            datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.SUCCESS })
    exception =
        assertThrows<IllegalArgumentException> {
          datasetApiService.rollbackRefresh(organizationSaved.id!!, datasetSaved.id!!)
        }
    assertEquals("The dataset hasn't failed and can't be rolled back", exception.message)
  }

  @Test
  fun `status should go back to normal on rollback endpoint call`() {
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
    organization = makeOrganizationWithRole("organization")
    organizationSaved = organizationApiService.registerOrganization(organization)
    makeDatasetWithRole(sourceType = DatasetSourceType.File)
    datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)

    datasetRepository.save(datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.ERROR })
    datasetApiService.rollbackRefresh(organizationSaved.id!!, datasetSaved.id!!)
    var datasetStatus =
        datasetApiService.getDatasetTwingraphStatus(organizationSaved.id!!, datasetSaved.id!!)
    assertEquals(Dataset.IngestionStatus.NONE.value, datasetStatus)

    every { datasetApiService.query(any(), any()) } returns mockk()
    val fileName = this::class.java.getResource("/integrationTest.zip")?.file
    val file = File(fileName!!)
    val resource = ByteArrayResource(file.readBytes())
    datasetApiService.uploadTwingraph(organizationSaved.id!!, datasetSaved.id!!, resource)
    do {
      Thread.sleep(50L)
      datasetStatus =
          datasetApiService.getDatasetTwingraphStatus(organizationSaved.id!!, datasetSaved.id!!)
    } while (datasetStatus == Dataset.IngestionStatus.PENDING.value)
    datasetRepository.save(datasetSaved.apply { ingestionStatus = Dataset.IngestionStatus.ERROR })
    datasetApiService.rollbackRefresh(organizationSaved.id!!, datasetSaved.id!!)
    datasetStatus =
        datasetApiService.getDatasetTwingraphStatus(organizationSaved.id!!, datasetSaved.id!!)
    assertEquals(Dataset.IngestionStatus.NONE.value, datasetStatus)
  }

  @TestFactory
  fun `test refreshDataset`() {
    ReflectionTestUtils.setField(datasetApiService, "eventPublisher", eventPublisher)
    mapOf(
            DatasetSourceType.AzureStorage to false,
            DatasetSourceType.ADT to false,
            DatasetSourceType.Twincache to false,
            DatasetSourceType.File to true,
            DatasetSourceType.None to true)
        .map { (sourceType, shouldThrow) ->
          DynamicTest.dynamicTest("Test RBAC refreshDataset : $sourceType") {
            every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
            organizationSaved =
                organizationApiService.registerOrganization(
                    makeOrganizationWithRole("organization"))
            val parentDataset =
                datasetApiService.createDataset(
                    organizationSaved.id!!, makeDatasetWithRole(sourceType = sourceType))
            datasetSaved =
                datasetApiService.createDataset(
                    organizationSaved.id!!,
                    makeDatasetWithRole(parentId = parentDataset.id!!, sourceType = sourceType))

            every { eventPublisher.publishEvent(any<TwingraphImportEvent>()) } answers
                {
                  firstArg<TwingraphImportEvent>().response = null
                }
            if (shouldThrow) {
              val exception =
                  assertThrows<CsmResourceNotFoundException> {
                    datasetApiService.refreshDataset(organizationSaved.id!!, datasetSaved.id!!)
                  }
              assertEquals("Cannot be applied to source type '$sourceType'", exception.message)
            } else {
              assertDoesNotThrow {
                datasetApiService.refreshDataset(organizationSaved.id!!, datasetSaved.id!!)
              }
            }
          }
        }
  }

  @Test
  fun `link workspace from dataset`() {

    assertNull(
        datasetApiService
            .findDatasetById(organizationSaved.id!!, datasetSaved.id!!)
            .linkedWorkspaceIdList)

    datasetApiService.linkWorkspace(organizationSaved.id!!, datasetSaved.id!!, workspaceSaved.id!!)

    val workspaceIds = listOf(workspaceSaved.id!!)
    checkLinkedWorkspaceId(workspaceIds)

    datasetApiService.linkWorkspace(organizationSaved.id!!, datasetSaved.id!!, workspaceSaved.id!!)

    checkLinkedWorkspaceId(workspaceIds)
  }

  private fun checkLinkedWorkspaceId(workspaceIds: List<String>) {
    assertEquals(
        datasetApiService
            .findDatasetById(organizationSaved.id!!, datasetSaved.id!!)
            .linkedWorkspaceIdList!!
            .size,
        workspaceIds.size)

    assertEquals(
        datasetApiService
            .findDatasetById(organizationSaved.id!!, datasetSaved.id!!)
            .linkedWorkspaceIdList!!,
        workspaceIds)
  }

  @Test
  fun `unlink workspace from dataset`() {

    assertNull(
        datasetApiService
            .findDatasetById(organizationSaved.id!!, datasetSaved.id!!)
            .linkedWorkspaceIdList)

    datasetApiService.linkWorkspace(organizationSaved.id!!, datasetSaved.id!!, workspaceSaved.id!!)

    datasetApiService.unlinkWorkspace(
        organizationSaved.id!!, datasetSaved.id!!, workspaceSaved.id!!)

    assertEquals(
        datasetApiService
            .findDatasetById(organizationSaved.id!!, datasetSaved.id!!)
            .linkedWorkspaceIdList!!
            .size,
        0)
  }

  @Test
  fun `unlink workspace from dataset when there is no link`() {

    assertNull(
        datasetApiService
            .findDatasetById(organizationSaved.id!!, datasetSaved.id!!)
            .linkedWorkspaceIdList)

    assertNull(
        workspaceApiService
            .findWorkspaceById(organizationSaved.id!!, workspaceSaved.id!!)
            .linkedDatasetIdList)

    datasetApiService.unlinkWorkspace(
        organizationSaved.id!!, datasetSaved.id!!, workspaceSaved.id!!)

    assertNull(
        datasetApiService
            .findDatasetById(organizationSaved.id!!, datasetSaved.id!!)
            .linkedWorkspaceIdList)

    assertNull(
        workspaceApiService
            .findWorkspaceById(organizationSaved.id!!, workspaceSaved.id!!)
            .linkedDatasetIdList)
  }

  @Test
  fun `getConnector return same connector`() {
    val dataset = makeDatasetWithRole()
    val dataset1 = datasetApiService.createDataset(organizationSaved.id!!, dataset)
    val dataset2 = datasetApiService.createDataset(organizationSaved.id!!, dataset)

    assertEquals(dataset1.connector!!.id, dataset2.connector!!.id)
  }

  fun makeConnector(): Connector {
    return Connector(
        key = "connector",
        name = "connector-1",
        repository = "repo",
        version = "1.0.0",
        ioTypes = listOf(),
        id = "c-AbCdEf123")
  }

  fun makeOrganizationWithRole(
      userName: String = TEST_USER_MAIL,
      role: String = ROLE_EDITOR
  ): Organization {
    return Organization(
        id = UUID.randomUUID().toString(),
        name = "Organization NameRbac",
        ownerId = "my.account-tester@cosmotech.com",
        security =
            OrganizationSecurity(
                default = ROLE_NONE,
                accessControlList =
                    mutableListOf(
                        OrganizationAccessControl(id = CONNECTED_ADMIN_USER, role = ROLE_ADMIN),
                        OrganizationAccessControl(id = userName, role = role))))
  }
  fun makeDataset(
      organizationId: String = organizationSaved.id!!,
      parentId: String = "",
      sourceType: DatasetSourceType = DatasetSourceType.File
  ): Dataset {
    return Dataset(
        id = UUID.randomUUID().toString(),
        name = "My datasetRbac",
        organizationId = organizationId,
        parentId = parentId,
        ownerId = "ownerId",
        connector = DatasetConnector(connectorSaved.id!!),
        twingraphId = "graph",
        source = SourceInfo("location", "name", "path"),
        tags = mutableListOf("dataset"),
        sourceType = sourceType)
  }

  fun makeDatasetWithRole(
      organizationId: String = organizationSaved.id!!,
      parentId: String = "",
      userName: String = TEST_USER_MAIL,
      role: String = ROLE_ADMIN,
      sourceType: DatasetSourceType = DatasetSourceType.File
  ): Dataset {
    return Dataset(
        id = UUID.randomUUID().toString(),
        name = "My datasetRbac",
        organizationId = organizationId,
        parentId = parentId,
        ownerId = "ownerId",
        connector = DatasetConnector(connectorSaved.id!!),
        twingraphId = "graph",
        source = SourceInfo("location", "name", "path"),
        tags = mutableListOf("dataset"),
        sourceType = sourceType,
        security =
            DatasetSecurity(
                default = ROLE_NONE,
                accessControlList =
                    mutableListOf(
                        DatasetAccessControl(id = CONNECTED_ADMIN_USER, role = ROLE_ADMIN),
                        DatasetAccessControl(id = userName, role = role))))
  }

  fun makeSolution(
      organizationId: String = organizationSaved.id!!,
      userName: String = TEST_USER_MAIL,
      role: String = ROLE_EDITOR
  ): Solution {
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
                        SolutionAccessControl(id = CONNECTED_ADMIN_USER, role = ROLE_ADMIN),
                        SolutionAccessControl(id = userName, role = role))))
  }

  fun makeWorkspace(
      organizationId: String = organizationSaved.id!!,
      solutionId: String = solutionSaved.id!!,
      name: String = "name",
      userName: String = TEST_USER_MAIL,
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
                        WorkspaceAccessControl(CONNECTED_ADMIN_USER, "admin"))))
  }
}
