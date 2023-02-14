package com.cosmotech.connector.service

import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.tests.CsmRedisTestBase
import com.cosmotech.api.utils.getCurrentAuthenticatedMail
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.connector.api.ConnectorApiService
import com.cosmotech.connector.domain.Connector
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockkStatic
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
import kotlin.test.assertEquals
import kotlin.test.assertNull

@ActiveProfiles(profiles = ["connector-test"])
@ExtendWith(MockKExtension::class)
@ExtendWith(SpringExtension::class)
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConnectorServiceIntegrationTest : CsmRedisTestBase() {

    private val logger = LoggerFactory.getLogger(ConnectorServiceIntegrationTest::class.java)

    @Autowired
    lateinit var connectorApiService: ConnectorApiService

    @BeforeEach
    fun setUp() {
        mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
        every { getCurrentAuthenticatedMail(any()) } returns "test.user@cosmotech.com"
        every { getCurrentAuthenticatedUserName() } returns "test.user"
    }

    //TODO Find All Connector && Import Connector

    @Test
    fun registerConnector(){
        val connector = Connector(
                            key = "key",
                            name = "name",
                            repository = "repository",
                            version = "version",
                            ioTypes = listOf())

        logger.info("Create new connector...")
        val connectorRegistered = connectorApiService.registerConnector(connector)
        logger.info("New connector created : ${connectorRegistered.id}")
        logger.info("Fetch new connector created ...")
        val connectorRetrieved = connectorApiService.findConnectorById(connectorRegistered.id!!)
        assertEquals(connectorRegistered, connectorRetrieved)
        logger.info("Fetched Connector : ${connectorRetrieved.id}")
        logger.info("Deleting connector ...")
        connectorApiService.unregisterConnector(connectorRegistered.id!!)
        assertThrows<CsmResourceNotFoundException>{ connectorApiService.findConnectorById(connectorRegistered.id!!) }
        logger.info("Deleted connector")
    }
}
