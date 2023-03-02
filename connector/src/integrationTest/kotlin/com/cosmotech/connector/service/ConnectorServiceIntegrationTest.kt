// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.connector.service

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.tests.CsmRedisTestBase
import com.cosmotech.api.utils.getCurrentAuthenticatedMail
import com.cosmotech.api.utils.getCurrentAuthenticatedRoles
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.connector.api.ConnectorApiService
import com.cosmotech.connector.domain.Connector
import com.redis.om.spring.RediSearchIndexer
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockkStatic
import kotlin.test.assertEquals
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

@ActiveProfiles(profiles = ["connector-test"])
@ExtendWith(MockKExtension::class)
@ExtendWith(SpringExtension::class)
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConnectorServiceIntegrationTest : CsmRedisTestBase() {

  private val logger = LoggerFactory.getLogger(ConnectorServiceIntegrationTest::class.java)

  @Autowired lateinit var rediSearchIndexer: RediSearchIndexer
  @Autowired lateinit var connectorApiService: ConnectorApiService
  @Autowired lateinit var csmPlatformProperties: CsmPlatformProperties

  @BeforeEach
  fun setUp() {
    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
    every { getCurrentAuthenticatedMail(any()) } returns "test.user@cosmotech.com"
    every { getCurrentAuthenticatedUserName() } returns "test.user"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf()
    rediSearchIndexer.createIndexFor(Connector::class.java)
  }

  // TODO Import Connector

  fun mockConnector(name: String): Connector {
    return Connector(
        key = "key",
        name = name,
        repository = "repository",
        version = "version",
        ioTypes = listOf())
  }

  @Test
  fun `test find All Connectors with different pagination params`() {
    val numberOfConnector = 20
    val defaultPageSize = csmPlatformProperties.twincache.connector.defaultPageSize
    val expectedSize = 15
    logger.info("Creating $numberOfConnector connectors...")
    IntRange(1, numberOfConnector).forEach {
      var newConnector = mockConnector("o-connector-test-$it")
      connectorApiService.registerConnector(newConnector)
    }
    var connectorList = connectorApiService.findAllConnectors(null, null)
    logger.info("Connector list retrieved contains : ${connectorList.size} elements")
    assertEquals(numberOfConnector, connectorList.size)

    connectorList = connectorApiService.findAllConnectors(0, null)
    logger.info("Connector list retrieved contains : ${connectorList.size} elements")
    assertEquals(defaultPageSize, connectorList.size)

    connectorList = connectorApiService.findAllConnectors(0, expectedSize)
    logger.info("Connector list retrieved contains : ${connectorList.size} elements")
    assertEquals(expectedSize, connectorList.size)

    logger.info("Should return the last page")
    connectorList = connectorApiService.findAllConnectors(1, expectedSize)
    assertEquals(numberOfConnector - expectedSize, connectorList.size)
  }

  @Test
  fun `test find All Connector with wrong pagination params`() {
    var newConnector = mockConnector("o-connector-test-1")
    connectorApiService.registerConnector(newConnector)

    logger.info("Should throw IllegalArgumentException when page and size are zeros")
    assertThrows<IllegalArgumentException> { connectorApiService.findAllConnectors(0, 0) }

    logger.info("Should throw IllegalArgumentException when page is negative")
    assertThrows<IllegalArgumentException> { connectorApiService.findAllConnectors(-1, 10) }

    logger.info("Should throw IllegalArgumentException when size is negative")
    assertThrows<IllegalArgumentException> { connectorApiService.findAllConnectors(0, -1) }
  }

  @Test
  fun registerConnector() {
    val connector1 = mockConnector("connector1")
    val connector2 = mockConnector("connector2")

    logger.info("Create new connector...")
    val connectorRegistered1 = connectorApiService.registerConnector(connector1)
    connectorApiService.registerConnector(connector2)

    logger.info("Fetch new connector created ...")
    val connectorRetrieved = connectorApiService.findConnectorById(connectorRegistered1.id!!)
    assertEquals(connectorRegistered1, connectorRetrieved)

    logger.info("Deleting connector ...")
    connectorApiService.unregisterConnector(connectorRegistered1.id!!)
    assertThrows<CsmResourceNotFoundException> {
      connectorApiService.findConnectorById(connectorRegistered1.id!!)
    }
  }
}
