// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.service

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.rbac.ROLE_ADMIN
import com.cosmotech.api.security.ROLE_ORGANIZATION_USER
import com.cosmotech.api.security.ROLE_PLATFORM_ADMIN
import com.cosmotech.api.tests.CsmRedisTestBase
import com.cosmotech.api.utils.getCurrentAccountIdentifier
import com.cosmotech.api.utils.getCurrentAuthenticatedRoles
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.connector.api.ConnectorApiService
import com.cosmotech.connector.domain.Connector
import com.cosmotech.dataset.api.DatasetApiService
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.dataset.domain.DatasetCompatibility
import com.cosmotech.dataset.domain.DatasetConnector
import com.cosmotech.dataset.domain.DatasetSearch
import com.cosmotech.dataset.domain.DatasetSourceType
import com.cosmotech.dataset.domain.GraphProperties
import com.cosmotech.dataset.domain.SubDatasetGraphQuery
import com.cosmotech.dataset.repository.DatasetRepository
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationSecurity
import com.redis.om.spring.RediSearchIndexer
import com.redis.testcontainers.RedisStackContainer
import com.redislabs.redisgraph.impl.api.RedisGraph
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockkStatic
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
import org.testcontainers.shaded.org.bouncycastle.asn1.x500.style.RFC4519Style.name
import redis.clients.jedis.JedisPool

const val REDIS_PORT = 6379
const val CONNECTED_ADMIN_USER = "test.admin@cosmotech.com"

@ActiveProfiles(profiles = ["dataset-test"])
@ExtendWith(MockKExtension::class)
@ExtendWith(SpringExtension::class)
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Suppress("LargeClass", "TooManyFunctions")
class DatasetServiceIntegrationTest : CsmRedisTestBase() {

  private val logger = LoggerFactory.getLogger(DatasetServiceIntegrationTest::class.java)

  @Autowired lateinit var rediSearchIndexer: RediSearchIndexer
  @Autowired lateinit var datasetApiService: DatasetApiService
  @Autowired lateinit var datasetRepository: DatasetRepository
  @Autowired lateinit var connectorApiService: ConnectorApiService
  @Autowired lateinit var organizationApiService: OrganizationApiService
  @Autowired lateinit var csmPlatformProperties: CsmPlatformProperties

  lateinit var connectorSaved: Connector
  lateinit var dataset: Dataset
  lateinit var dataset2: Dataset
  lateinit var datasetSaved: Dataset
  lateinit var retrievedDataset1: Dataset

  lateinit var jedisPool: JedisPool
  lateinit var redisGraph: RedisGraph
  lateinit var organization: Organization
  lateinit var organizationSaved: Organization

  @BeforeAll
  fun beforeAll() {
    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
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
    every { getCurrentAccountIdentifier(any()) } returns "test.user@cosmotech.com"
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "test.user"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf()
    rediSearchIndexer.createIndexFor(Dataset::class.java)

    connectorSaved = connectorApiService.registerConnector(makeConnector())

    organization = makeOrganization("Organization")
    organizationSaved = organizationApiService.registerOrganization(organization)

    dataset = makeDataset("d-dataset-1", "dataset-1")
    datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
    materializeTwingraph(datasetSaved)

    dataset2 = makeDataset("d-dataset-2", "dataset-2")
  }

  @Test
  fun `test Dataset CRUD`() {

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
  fun `can delete dataset when user is not the owner and is Platform Admin`() {

    logger.info("Register dataset : ${dataset.id}...")
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
  fun `test find All Datasets with different pagination params`() {
    val numberOfDatasets = 20
    val defaultPageSize = csmPlatformProperties.twincache.dataset.defaultPageSize
    val expectedSize = 15
    IntRange(1, numberOfDatasets).forEach {
      datasetApiService.createDataset(
          organizationSaved.id!!, makeDataset("d-dataset-$it", "dataset-$it"))
    }

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
    assertEquals(numberOfDatasets + 1 - expectedSize, datasetList.size)
  }

  @Test
  fun `test find All Datasets with wrong pagination params`() {

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

    dataset = makeDataset("d-dataset-1", "dataset-1")
    datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
    datasetApiService.updateDataset(
        organizationSaved.id!!,
        datasetSaved.id!!,
        dataset.copy(sourceType = DatasetSourceType.File))

    datasetApiService.uploadTwingraph(organizationSaved.id!!, datasetSaved.id!!, resource)
    while (datasetApiService.getDatasetTwingraphStatus(
        organizationSaved.id!!, datasetSaved.id!!, null) != Dataset.Status.COMPLETED.value) {
      Thread.sleep(500)
    }
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

    assertEquals("subDatasetWithQuery", subDatasetWithQuery.name)
    assertEquals("subDatasetWithQuery description", subDatasetWithQuery.description)
    assertEquals(3, countEntities(subDatasetWithQuery.twingraphId!!, "MATCH (n) RETURN count(n)"))
    assertEquals(
        2, countEntities(subDatasetWithQuery.twingraphId!!, "MATCH ()-[r]-() RETURN count(r)"))
  }

  fun countEntities(twingraphId: String, query: String): Int {
    val resultSet = redisGraph.query(twingraphId, query)
    return resultSet.next().values()[0].toString().toInt()
  }

  @Test
  fun `twingraph CRUD test`() {

    logger.info("Create Nodes")
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

  private fun materializeTwingraph(dataset: Dataset): Dataset {
    dataset.apply {
      redisGraph.query(this.twingraphId, "CREATE (n)")
      this.status = Dataset.Status.COMPLETED
    }
    return datasetRepository.save(dataset)
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
  fun makeDataset(id: String, name: String): Dataset {
    return Dataset(
        id = id,
        name = name,
        main = true,
        connector = DatasetConnector(id = connectorSaved.id, name = connectorSaved.name),
        tags = mutableListOf("test", "data"))
  }

  fun makeOrganization(name: String): Organization {
    return Organization(
        name = name,
        ownerId = "my.account-tester@cosmotech.com",
        security =
            OrganizationSecurity(
                default = ROLE_ADMIN,
                accessControlList =
                    mutableListOf(
                        OrganizationAccessControl(id = CONNECTED_ADMIN_USER, role = "admin"))))
  }
}
