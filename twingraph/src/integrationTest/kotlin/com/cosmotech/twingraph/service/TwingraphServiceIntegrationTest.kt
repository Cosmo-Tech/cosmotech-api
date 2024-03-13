// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
@file:Suppress("DEPRECATION")

package com.cosmotech.twingraph.service

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.rbac.ROLE_ADMIN
import com.cosmotech.api.tests.CsmRedisTestBase
import com.cosmotech.api.utils.getCurrentAccountIdentifier
import com.cosmotech.api.utils.getCurrentAuthenticatedRoles
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.organization.OrganizationApiServiceInterface
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationSecurity
import com.cosmotech.twingraph.TwingraphApiServiceInterface
import com.cosmotech.twingraph.domain.GraphProperties
import com.cosmotech.twingraph.domain.TwinGraphBatchResult
import com.cosmotech.twingraph.domain.TwinGraphQuery
import com.redis.testcontainers.RedisStackContainer
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockkStatic
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.runner.RunWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.util.ReflectionTestUtils
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.JedisPool
import redis.clients.jedis.Protocol
import redis.clients.jedis.UnifiedJedis

const val CONNECTED_ADMIN_USER = "test.admin@cosmotech.com"

@ActiveProfiles(profiles = ["twingraph-test"])
@ExtendWith(MockKExtension::class)
@ExtendWith(SpringExtension::class)
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TwingraphServiceIntegrationTest : CsmRedisTestBase() {

  private val logger = LoggerFactory.getLogger(TwingraphServiceIntegrationTest::class.java)

  @Autowired lateinit var twingraphApiService: TwingraphApiServiceInterface
  @Autowired lateinit var organizationApiService: OrganizationApiServiceInterface
  @Autowired lateinit var csmPlatformProperties: CsmPlatformProperties

  lateinit var jedisPool: JedisPool
  lateinit var unifiedJedis: UnifiedJedis
  lateinit var organization: Organization

  val graphId = "graph"

  @BeforeAll
  fun beforeAll() {
    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
    val context = getContext(redisStackServer)
    val containerIp =
        (context.server as RedisStackContainer).containerInfo.networkSettings.ipAddress
    jedisPool = JedisPool(containerIp, 6379)
    unifiedJedis = UnifiedJedis(HostAndPort(containerIp, Protocol.DEFAULT_PORT))
    ReflectionTestUtils.setField(twingraphApiService, "unifiedJedis", unifiedJedis)

    context.sync().hset("${graphId}MetaData", mapOf("lastVersion" to "1"))
  }

  @BeforeEach
  fun beforeEach() {
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "test.user"
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

  @Test
  fun `graph creation`() {
    logger.info("Create an empty graph")
    twingraphApiService.createGraph(organization.id!!, graphId, null)
    assertEquals(1, twingraphApiService.findAllTwingraphs(organization.id!!).size)

    logger.info("Create a Graph with a ZIP Entry")
    val file = this::class.java.getResource("/integrationTest.zip")?.file
    var resource = ByteArrayResource(File(file!!).readBytes())
    twingraphApiService.createGraph(organization.id!!, "2$graphId", resource)

    logger.info("Create a graph with an already existing Id")
    assertThrows<Exception> { twingraphApiService.createGraph(organization.id!!, graphId, null) }
  }

  @Test
  fun `twingraph CRUD test`() {

    twingraphApiService.createGraph(organization.id!!, graphId, null)

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
    assertNotEquals(String(), nodeStart)

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

  @Test
  fun `twingraph create update delete nodes relationship test`() {

    val fileNodeName = this::class.java.getResource("/Users.csv")?.file
    val fileNode: Resource = ByteArrayResource(File(fileNodeName!!).readBytes())

    val fileRelationshipName = this::class.java.getResource("/Follows.csv")?.file
    val fileRelationship: Resource = ByteArrayResource(File(fileRelationshipName!!).readBytes())

    unifiedJedis.graphQuery("$graphId:1", "CREATE (n)")

    listOf(
            listOf(
                "CREATE (:Person {id: toInteger(\$id), name:\$name, rank: toInteger(\$rank), object: \$object})",
                TwinGraphBatchResult(9, 9, mutableListOf()),
                fileNode),
            listOf(
                "MATCH (p:Person {id: toInteger(\$id)}) SET p.rank = \$rank",
                TwinGraphBatchResult(9, 9, mutableListOf()),
                fileNode),
            listOf(
                "MERGE (p1:Person {id: toInteger(\$UserId1)}) " +
                    "MERGE (p2:Person {id: toInteger(\$UserId2)}) " +
                    "CREATE (p1)-[:FOLLOWS {reaction_count: \$reaction_count}]->(p2)",
                TwinGraphBatchResult(2, 2, mutableListOf()),
                fileRelationship),
            listOf(
                "MATCH (p:Person {id: toInteger(\$id)}) DELETE p",
                TwinGraphBatchResult(9, 9, mutableListOf()),
                fileNode))
        .forEach { (query, result, file) ->
          val twinGraphQuery = TwinGraphQuery(query = query as String, version = "1")
          val queryResult =
              twingraphApiService.batchUploadUpdate(
                  organization.id!!, graphId, twinGraphQuery, file as Resource)
          val expected = result as TwinGraphBatchResult
          assertEquals(expected.totalLines, queryResult.totalLines)
          assertEquals(expected.processedLines, queryResult.processedLines)
          assertEquals(expected.errors.size, queryResult.errors.size)
        }
  }
}
