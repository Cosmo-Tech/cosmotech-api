// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.twingraph.service

import com.cosmotech.api.tests.CsmRedisTestBase
import com.cosmotech.twingraph.api.TwingraphApiService
import com.cosmotech.twingraph.domain.GraphProperties
import com.redis.testcontainers.RedisStackContainer
import com.redis.testcontainers.junit.RedisTestContext
import com.redis.testcontainers.junit.RedisTestContextsSource
import com.redislabs.redisgraph.ResultSet
import com.redislabs.redisgraph.impl.api.RedisGraph
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.runner.RunWith
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

  val graphId = "graph"
  @BeforeEach fun setUp() {}

  @RedisTestContextsSource
  @ParameterizedTest
  fun TwingraphTest(context: RedisTestContext) {

    val containerIp =
        (context.server as RedisStackContainer).containerInfo.networkSettings.ipAddress
    println(containerIp)
    val jedisPool = JedisPool(containerIp, 6379)
    ReflectionTestUtils.setField(twingraphApiService, "csmJedisPool", jedisPool)
    ReflectionTestUtils.setField(twingraphApiService, "csmRedisGraph", RedisGraph(jedisPool))

    context.sync().hset(graphId + "MetaData", mapOf("lastVersion" to "1"))

    logger.info("Create Nodes")
    val nodeStart =
        (twingraphApiService
                .createEntities(
                    "orga",
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
                .first() as
                ResultSet)
            .next()
            .values()
    assertNotEquals(listOf<Any>(), nodeStart)

    logger.info("Read Nodes")
    var nodeResult =
        (twingraphApiService.getEntities("orga", graphId, "node", listOf("node_a")).first() as
                ResultSet)
            .next()
            .values()
    assertEquals(nodeStart, nodeResult)

    logger.info("Create Relationships")
    val relationshipStart =
        (twingraphApiService
                .createEntities(
                    "orga",
                    graphId,
                    "relationship",
                    listOf(
                        GraphProperties(
                            "relationship", "node_a", "node_b", "relationship_a", "duration:0")))
                .first() as
                ResultSet)
            .next()
            .values()

    logger.info("Read Relationships")
    var relationshipResult =
        (twingraphApiService
                .getEntities("orga", graphId, "relationship", listOf("relationship_a"))
                .first() as
                ResultSet)
            .next()
            .values()
    assertEquals(relationshipStart, relationshipResult)

    logger.info("Update Nodes")
    nodeResult =
        (twingraphApiService
                .updateEntities(
                    "orga",
                    graphId,
                    "node",
                    listOf(
                        GraphProperties().apply {
                          name = "node_a"
                          params = "size:2"
                        }))
                .first() as
                ResultSet)
            .next()
            .values()
    assertNotEquals(nodeStart, nodeResult)

    logger.info("Update Relationships")
    relationshipResult =
        (twingraphApiService
                .updateEntities(
                    "orga",
                    graphId,
                    "relationship",
                    listOf(
                        GraphProperties().apply {
                          source = "node_a"
                          target = "node_b"
                          name = "relationship_a"
                          params = "duration:2"
                        }))
                .first() as
                ResultSet)
            .next()
            .values()
    assertNotEquals(relationshipStart, relationshipResult)

    logger.info("Delete Relationships")
    twingraphApiService.deleteEntities("orga", "graph:0", "node", listOf("relationship_a"))
    assertDoesNotThrow {
      twingraphApiService.getEntities("orga", graphId, "node", listOf("relationship_a"))
    }

    logger.info("Delete Nodes")
    twingraphApiService.deleteEntities("orga", graphId, "relationship", listOf("node_a"))
    assertDoesNotThrow {
      twingraphApiService.getEntities("orga", graphId, "relationship", listOf("node_a"))
    }
  }
}
