// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.service

import com.cosmotech.api.config.CsmPlatformProperties
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
import com.redis.om.spring.RediSearchIndexer
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockkStatic
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.runner.RunWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.junit4.SpringRunner

@ActiveProfiles(profiles = ["dataset-test"])
@ExtendWith(MockKExtension::class)
@ExtendWith(SpringExtension::class)
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DatasetServiceIntegrationTest : CsmRedisTestBase() {

  private val logger = LoggerFactory.getLogger(DatasetServiceIntegrationTest::class.java)

  @Autowired lateinit var rediSearchIndexer: RediSearchIndexer
  @Autowired lateinit var datasetApiService: DatasetApiService
  @Autowired lateinit var connectorApiService: ConnectorApiService
  @Autowired lateinit var csmPlatformProperties: CsmPlatformProperties

  val organizationId = "O-AbCdEf123"
  var connector =
      Connector(
          key = "connector",
          name = "connector-1",
          repository = "repo",
          version = "1.0.0",
          ioTypes = listOf(),
          id = "c-AbCdEf123")
  lateinit var registeredConnector: Connector
  lateinit var dataset1: Dataset
  lateinit var dataset2: Dataset
  lateinit var registeredDataset1: Dataset
  lateinit var retrievedDataset1: Dataset

  @BeforeEach
  fun setUp() {
    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
    every { getCurrentAccountIdentifier(any()) } returns "test.user@cosmotech.com"
    every { getCurrentAuthenticatedUserName() } returns "test.user"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf()
    rediSearchIndexer.createIndexFor(Dataset::class.java)

    registeredConnector = connectorApiService.registerConnector(connector)

    dataset1 = mockDataset("d-dataset-1", "dataset-1")
    dataset2 = mockDataset("d-dataset-2", "dataset-2")
  }

  fun mockDataset(id: String, name: String): Dataset {
    return Dataset(
        id = id,
        name = name,
        connector = DatasetConnector(id = registeredConnector.id, name = registeredConnector.name),
        tags = mutableListOf("test", "data"))
  }

  @Test
  fun `test Dataset CRUD`() {

    logger.info("Register dataset : ${dataset1.id}...")
    registeredDataset1 = datasetApiService.createDataset(organizationId, dataset1)
    assertNotNull(registeredDataset1)
    val registeredDataset2 = datasetApiService.createDataset(organizationId, dataset2)

    logger.info("Import a new Dataset...")
    // TODO once the problem of adding the organizationId to the dataset has been fixed
    /*val registeredDataset2 = datasetApiService.importDataset(organizationId, dataset2)
    assertNotNull(registeredDataset2)*/

    logger.info("Fetch dataset : ${registeredDataset1.id}...")
    retrievedDataset1 = datasetApiService.findDatasetById(organizationId, registeredDataset1.id!!)
    assertNotNull(retrievedDataset1)

    logger.info("Fetch all datasets...")
    var datasetList = datasetApiService.findAllDatasets(organizationId, null, null)
    for (item in datasetList) {
      logger.warn(item.id)
    }
    assertTrue { datasetList.size == 2 }

    logger.info("Delete Dataset : ${registeredDataset2.id}...")
    datasetApiService.deleteDataset(organizationId, registeredDataset2.id!!)
    datasetList = datasetApiService.findAllDatasets(organizationId, null, null)
    assertTrue { datasetList.size == 1 }
  }

  fun `test special endpoints`() {
    logger.info("Copy a Dataset...")
    // TODO("Not yet implemented")

    logger.info("Search Datasets...")
    val datasetList =
        datasetApiService.searchDatasets(
            organizationId, DatasetSearch(mutableListOf("data")), null, null)
    assertTrue { datasetList.size == 2 }

    logger.info("Update Dataset : ${registeredDataset1.id}...")
    val retrievedDataset1 =
        datasetApiService.updateDataset(organizationId, registeredDataset1.id!!, dataset2)
    assertNotEquals(retrievedDataset1, registeredDataset1)
  }

  fun `test dataset compatibility elements`() {
    logger.info("Add Dataset Compatibility elements...")
    var datasetCompatibilityList =
        datasetApiService.addOrReplaceDatasetCompatibilityElements(
            organizationId,
            registeredDataset1.id!!,
            datasetCompatibility =
                listOf(
                    DatasetCompatibility(solutionKey = "solution"),
                    DatasetCompatibility(solutionKey = "test")))
    assertFalse { datasetCompatibilityList.isEmpty() }

    logger.info(
        "Remove all Dataset Compatibility elements from dataset : ${registeredDataset1.id!!}...")
    datasetApiService.removeAllDatasetCompatibilityElements(organizationId, registeredDataset1.id!!)
    datasetCompatibilityList =
        datasetApiService.findDatasetById(organizationId, registeredDataset1.id!!).compatibility!!
    assertTrue { datasetCompatibilityList.isEmpty() }
  }

  @Test
  fun `test find All Datasets with different pagination params`() {
    val numberOfDatasets = 20
    val defaultPageSize = csmPlatformProperties.twincache.dataset.defaultPageSize
    val expectedSize = 15
    IntRange(1, numberOfDatasets).forEach {
      datasetApiService.createDataset(organizationId, mockDataset("d-dataset-$it", "dataset-$it"))
    }

    logger.info("should find all datasets and assert there are $numberOfDatasets")
    var datasetList = datasetApiService.findAllDatasets(organizationId!!, null, null)
    assertEquals(numberOfDatasets, datasetList.size)

    logger.info("should find all datasets and assert it equals defaultPageSize: $defaultPageSize")
    datasetList = datasetApiService.findAllDatasets(organizationId!!, 0, null)
    assertEquals(defaultPageSize, datasetList.size)

    logger.info("should find all datasets and assert there are expected size: $expectedSize")
    datasetList = datasetApiService.findAllDatasets(organizationId!!, 0, expectedSize)
    assertEquals(expectedSize, datasetList.size)

    logger.info("should find all solutions and assert it returns the second / last page")
    datasetList = datasetApiService.findAllDatasets(organizationId!!, 1, expectedSize)
    assertEquals(numberOfDatasets - expectedSize, datasetList.size)
  }

  @Test
  fun `test find All Datasets with wrong pagination params`() {

    datasetApiService.createDataset(organizationId, dataset1)

    logger.info("Should throw IllegalArgumentException when page and size are zeros")
    assertThrows<IllegalArgumentException> {
      datasetApiService.findAllDatasets(organizationId!!, 0, 0)
    }

    logger.info("Should throw IllegalArgumentException when page is negative")
    assertThrows<IllegalArgumentException> {
      datasetApiService.findAllDatasets(organizationId!!, -1, 10)
    }

    logger.info("Should throw IllegalArgumentException when size is negative")
    assertThrows<IllegalArgumentException> {
      datasetApiService.findAllDatasets(organizationId!!, 0, -1)
    }
  }
}
