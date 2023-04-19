// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.twingraph.service

import com.cosmotech.api.rbac.ROLE_ADMIN
import com.cosmotech.api.tests.CsmRedisTestBase
import com.cosmotech.api.utils.getCurrentAuthenticatedMail
import com.cosmotech.api.utils.getCurrentAuthenticatedRoles
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationSecurity
import com.cosmotech.twingraph.api.CONNECTED_ADMIN_USER
import com.cosmotech.twingraph.api.TwingraphApiService
import com.cosmotech.twingraph.domain.GraphProperties
import com.redis.testcontainers.RedisStackContainer
import com.redis.testcontainers.junit.RedisTestContext
import com.redis.testcontainers.junit.RedisTestContextsSource
import com.redislabs.redisgraph.impl.api.RedisGraph
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockkStatic
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.util.ReflectionTestUtils
import redis.clients.jedis.JedisPool

@ActiveProfiles(profiles = ["twingraph-test"])
@ExtendWith(MockKExtension::class)
@ExtendWith(SpringExtension::class)
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TwingraphServiceIntegrationTest : CsmRedisTestBase() {

  private val logger = LoggerFactory.getLogger(TwingraphServiceIntegrationTest::class.java)

  @Autowired lateinit var twingraphApiService: TwingraphApiService
  @Autowired lateinit var organizationApiService: OrganizationApiService

  var organization = Organization()

  val graphId = "graph"
  @BeforeEach
  fun init() {
    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
    every { getCurrentAuthenticatedMail(any()) } returns CONNECTED_ADMIN_USER
    every { getCurrentAuthenticatedUserName() } returns "test.user"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("admin")

    organization =
        organizationApiService.registerOrganization(
            Organization(
                name = "Organization Name",
                ownerId = "my.account-tester@cosmotech.com",
                security =
                    OrganizationSecurity(
                        default = ROLE_ADMIN,
                        accessControlList =
                            mutableListOf(
                                OrganizationAccessControl(
                                    id = CONNECTED_ADMIN_USER, role = "admin")))))
  }
  @RedisTestContextsSource
  @ParameterizedTest
  fun `twingraph CRUD test`(context: RedisTestContext) {

    val containerIp =
        (context.server as RedisStackContainer).containerInfo.networkSettings.ipAddress
    val jedisPool = JedisPool(containerIp, 6379)
    ReflectionTestUtils.setField(twingraphApiService, "csmJedisPool", jedisPool)
    ReflectionTestUtils.setField(twingraphApiService, "csmRedisGraph", RedisGraph(jedisPool))

    context.sync().hset("${graphId}MetaData", mapOf("lastVersion" to "1"))

    logger.info("Create Nodes")
    val nodeStart =
        twingraphApiService.createEntities(
            organization.id!!,
            graphId,
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
    assertNotEquals(listOf<String>(), nodeStart)

    logger.info("Read Nodes")
    var nodeResult =
        twingraphApiService.getEntities(
            organization.id!!, graphId, "node", listOf("node_a", "node_b"))
    assertEquals(nodeStart, nodeResult)

    logger.info("Create Relationships")
    val relationshipStart =
        twingraphApiService.createEntities(
            organization.id!!,
            graphId,
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
        twingraphApiService.getEntities(
            organization.id!!, graphId, "relationship", listOf("relationship_a"))
    assertEquals(relationshipStart, relationshipResult)

    logger.info("Update Nodes")
    nodeResult =
        twingraphApiService.updateEntities(
            organization.id!!,
            graphId,
            "node",
            listOf(
                GraphProperties().apply {
                  name = "node_a"
                  params = "size:2"
                }))
    assertNotEquals(nodeStart, nodeResult)

    logger.info("Update Relationships")
    relationshipResult =
        twingraphApiService.updateEntities(
            organization.id!!,
            graphId,
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
    twingraphApiService.deleteEntities(organization.id!!, graphId, "node", listOf("relationship_a"))
    assertDoesNotThrow {
      twingraphApiService.getEntities(organization.id!!, graphId, "node", listOf("relationship_a"))
    }

    logger.info("Delete Nodes")
    twingraphApiService.deleteEntities(organization.id!!, graphId, "relationship", listOf("node_a"))
    assertDoesNotThrow {
      twingraphApiService.getEntities(organization.id!!, graphId, "relationship", listOf("node_a"))
    }
  }
}
