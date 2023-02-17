// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.service

import com.cosmotech.api.tests.CsmRedisTestBase
import com.cosmotech.api.utils.getCurrentAuthenticatedMail
import com.cosmotech.api.utils.getCurrentAuthenticatedRoles
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.dataset.api.DatasetApiService
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.dataset.domain.DatasetConnector
import com.redis.om.spring.RediSearchIndexer
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockkStatic
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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

  val organizationId = "O-AbCdEf123"

  @BeforeEach
  fun setUp() {
    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
    every { getCurrentAuthenticatedMail(any()) } returns "test.user@cosmotech.com"
    every { getCurrentAuthenticatedUserName() } returns "test.user"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf()
  }

  fun mockDataset(id: String, name: String): Dataset {
    return Dataset(id = id, name = name, connector = DatasetConnector(id = "id"))
  }

  @Test
  fun testDataset() {
    val dataset1 = mockDataset("d-dataset-1", "dataset-1")
    val dataset2 = mockDataset("d-dataset-2", "dataset-2")

    /*logger.info("Register dataset : ${dataset1.id}")
    val registeredDataset1 = datasetApiService.createDataset(organizationId, dataset1)
    val registeredDataset2 = datasetApiService.createDataset(organizationId, dataset2)

    logger.info("Fetch dataset : ${registeredDataset1.id}")
    val retrievedDataset1 = datasetApiService.findDatasetById(organizationId, registeredDataset1.id!!)
    assertNotNull(retrievedDataset1)
    val retrievedDataset2 = datasetApiService.findDatasetById(organizationId, registeredDataset2.id!!)

    logger.info("Fetch all datasets...")
    val datasetList = datasetApiService.findAllDatasets(organizationId)*/
  }
}
