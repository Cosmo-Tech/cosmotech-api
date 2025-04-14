// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.connector.service

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.security.ROLE_ORGANIZATION_USER
import com.cosmotech.api.security.ROLE_PLATFORM_ADMIN
import com.cosmotech.api.tests.CsmRedisTestBase
import com.cosmotech.api.utils.getCurrentAccountIdentifier
import com.cosmotech.api.utils.getCurrentAuthenticatedRoles
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.api.utils.getCurrentAuthentication
import com.cosmotech.connector.ConnectorApiServiceInterface
import com.cosmotech.connector.domain.Connector
import com.redis.om.spring.indexing.RediSearchIndexer
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.runner.RunWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication
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
  @Autowired lateinit var connectorApiService: ConnectorApiServiceInterface
  @Autowired lateinit var csmPlatformProperties: CsmPlatformProperties

  /**
   * Run once before ALL tests. Should be used to define global setup (static mock, global settings,
   * ...)
   */
  @BeforeAll
  fun globalSetup() {
    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
  }

  /**
   * Run once before Each tests. Should be used to define volatile configuration (e.g. indexes
   * creation as db is flush after each test) EXCEPT test context (e.g. no mock for current user)
   */
  @BeforeEach
  fun setUp() {
    rediSearchIndexer.createIndexFor(Connector::class.java)
  }

  /** Tests in this nested class are launched as Organization.User */
  @Nested
  inner class AsOrganizationUser {

    @BeforeEach
    fun setUp() {
      runAsOrganizationUser()
    }

    @Test
    fun `register Connector`() {
      testRegisterConnector()
    }

    @Test
    fun `findConnectorById`() {
      testFindConnectorById()
    }

    @Test
    fun `unregister Connector as owner`() {
      testUnregisterConnectorAsOwner()
    }

    @Test
    fun `unregister Connector as not owner`() {
      runAsDifferentOrganizationUser()
      val connectorName = "connectorTest"
      val connector = createTestConnector(connectorName)
      logger.info("Create new connector named $connectorName ...")
      val savedConnector = connectorApiService.registerConnector(connector)
      runAsOrganizationUser()
      assertThrows<CsmAccessForbiddenException> {
        connectorApiService.unregisterConnector(savedConnector.id!!)
      }
    }

    @Test
    fun `find All Connectors with correct values`() {
      val numberOfConnectorToCreate = 20
      val defaultPageSize = csmPlatformProperties.twincache.connector.defaultPageSize

      batchConnectorCreation(numberOfConnectorToCreate)
      testFindAllConnectors(null, null, numberOfConnectorToCreate)
      testFindAllConnectors(0, null, defaultPageSize)
      testFindAllConnectors(0, 10, 10)
      testFindAllConnectors(1, 200, 0)
      testFindAllConnectors(1, 15, 5)
    }

    @Test
    fun `find All Connectors with wrong values`() {
      testFindAllConnectorsWithWrongValues()
    }

    /** Run a test with current user as Organization.User */
    private fun runAsOrganizationUser() {
      mockkStatic(::getCurrentAuthentication)
      every { getCurrentAuthentication() } returns mockk<BearerTokenAuthentication>()
      every { getCurrentAccountIdentifier(any()) } returns "test.user@cosmotech.com"
      every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "test.user"
      every { getCurrentAuthenticatedRoles(any()) } returns listOf(ROLE_ORGANIZATION_USER)
    }
  }

  /** Tests in this nested class are launched as Platform.Admin */
  @Nested
  inner class AsPlatformAdmin {

    @BeforeEach
    fun setUp() {
      runAsPlatformAdmin()
    }

    @Test
    fun `register Connector`() {
      testRegisterConnector()
    }

    @Test
    fun `findConnectorById`() {
      testFindConnectorById()
    }

    @Test
    fun `unregister Connector as owner`() {
      testUnregisterConnectorAsOwner()
    }

    @Test
    fun `unregister Connector as not owner`() {
      runAsDifferentOrganizationUser()
      val connectorName = "connectorTest"
      val connector = createTestConnector(connectorName)
      logger.info("Create new connector named $connectorName ...")
      val savedConnector = connectorApiService.registerConnector(connector)
      runAsPlatformAdmin()
      connectorApiService.unregisterConnector(savedConnector.id!!)
      assertThrows<CsmResourceNotFoundException> {
        connectorApiService.findConnectorById(savedConnector.id!!)
      }
    }

    @Test
    fun `find All Connectors with correct values`() {
      val numberOfConnectorToCreate = 20
      val defaultPageSize = csmPlatformProperties.twincache.connector.defaultPageSize

      batchConnectorCreation(numberOfConnectorToCreate)
      testFindAllConnectors(null, null, numberOfConnectorToCreate)
      testFindAllConnectors(0, null, defaultPageSize)
      testFindAllConnectors(0, 10, 10)
      testFindAllConnectors(1, 200, 0)
      testFindAllConnectors(1, 15, 5)
    }

    @Test
    fun `find All Connectors with wrong values`() {
      testFindAllConnectorsWithWrongValues()
    }

    /** Run a test with current user as Platform.Admin */
    private fun runAsPlatformAdmin() {
      mockkStatic(::getCurrentAuthentication)
      every { getCurrentAuthentication() } returns mockk<BearerTokenAuthentication>()
      every { getCurrentAccountIdentifier(any()) } returns "test.admin@cosmotech.com"
      every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "test.admin"
      every { getCurrentAuthenticatedRoles(any()) } returns listOf(ROLE_PLATFORM_ADMIN)
    }
  }

  internal fun testFindAllConnectors(page: Int?, size: Int?, expectedResultSize: Int) {
    val connectorList = connectorApiService.findAllConnectors(page, size)
    logger.info("Connector list retrieved contains : ${connectorList.size} elements")
    assertEquals(expectedResultSize, connectorList.size)
  }

  private fun testFindAllConnectorsWithWrongValues() {
    logger.info("Should throw IllegalArgumentException when page and size are zeros")
    assertThrows<IllegalArgumentException> { connectorApiService.findAllConnectors(0, 0) }

    logger.info("Should throw IllegalArgumentException when page is negative")
    assertThrows<IllegalArgumentException> { connectorApiService.findAllConnectors(-1, 10) }

    logger.info("Should throw IllegalArgumentException when size is negative")
    assertThrows<IllegalArgumentException> { connectorApiService.findAllConnectors(0, -1) }
  }

  internal fun testRegisterConnector() {
    val connectorName = "connectorTest"
    val connector = createTestConnector(connectorName)
    logger.info("Create new connector named $connectorName ...")
    assertDoesNotThrow {
      val savedConnector = connectorApiService.registerConnector(connector)
      assertNotNull(savedConnector)
    }
  }

  internal fun testFindConnectorById() {
    val connectorName = "connectorTest"
    val connector = createTestConnector(connectorName)
    logger.info("Create new connector named $connectorName ...")
    assertDoesNotThrow {
      val savedConnector = connectorApiService.registerConnector(connector)
      val retrievedConnector = connectorApiService.findConnectorById(savedConnector.id!!)
      assertEquals(savedConnector, retrievedConnector)
    }
  }

  internal fun testUnregisterConnectorAsOwner() {
    val connectorName = "connectorTest"
    val connector = createTestConnector(connectorName)
    logger.info("Create new connector named $connectorName ...")
    assertDoesNotThrow {
      val savedConnector = connectorApiService.registerConnector(connector)
      connectorApiService.unregisterConnector(savedConnector.id!!)
      assertThrows<CsmResourceNotFoundException> {
        connectorApiService.findConnectorById(savedConnector.id!!)
      }
    }
  }

  /** Run a test with different Organization.User */
  internal fun runAsDifferentOrganizationUser() {
    every { getCurrentAccountIdentifier(any()) } returns "test.other.user@cosmotech.com"
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "test.other.user"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf(ROLE_ORGANIZATION_USER)
  }

  /** Create default test Connector */
  internal fun createTestConnector(name: String): Connector {
    return Connector(
        id = "connector_id",
        key = "key",
        name = name,
        repository = "repository",
        version = "version",
        ioTypes = listOf())
  }

  /** Connector batch creation */
  internal fun batchConnectorCreation(numberOfConnectorToCreate: Int) {
    logger.info("Creating $numberOfConnectorToCreate connectors...")
    IntRange(1, numberOfConnectorToCreate).forEach {
      val newConnector = createTestConnector("o-connector-test-$it")
      connectorApiService.registerConnector(newConnector)
    }
  }
}
