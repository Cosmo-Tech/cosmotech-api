// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.twingraph.service

import com.cosmotech.api.tests.CsmRedisTestBase
import com.cosmotech.twingraph.api.TwingraphApiService
import com.redis.testcontainers.RedisStackContainer
import com.redis.testcontainers.junit.RedisTestContext
import com.redis.testcontainers.junit.RedisTestContextsSource
import com.redislabs.redisgraph.ResultSet
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
    ReflectionTestUtils.setField(twingraphApiService, "csmJedisPool", JedisPool(containerIp, 6379))

    context.sync().hset(graphId + "MetaData", mapOf("lastVersion" to "1"))

    logger.info("Create Nodes")
    val nodeStart =
        (twingraphApiService
                .createNodes(
                    "orga",
                    graphId,
                    listOf(mapOf("type" to "node", "name" to "node_a", "params" to "size:0")))
                .first() as
                ResultSet)
            .next()
            .values()
    assertNotEquals(listOf<Any>(), nodeStart)

    logger.info("Read Nodes")
    var nodeResult =
        (twingraphApiService.getNodes("orga", graphId, listOf("node_a")).first() as ResultSet)
            .next()
            .values()
    assertEquals(nodeStart, nodeResult)

    logger.info("Create Relationships")
    twingraphApiService.createNodes(
        "orga", graphId, listOf(mapOf("type" to "node", "name" to "node_b", "params" to "size:1")))
    val relationshipStart =
        (twingraphApiService
                .createRelationships(
                    "orga",
                    graphId,
                    listOf(
                        mapOf(
                            "type" to "relationship",
                            "source" to "node_a",
                            "target" to "node_b",
                            "name" to "relationship_a",
                            "params" to "duration:0")))
                .first() as
                ResultSet)
            .next()
            .values()

    logger.info("Read Relationships")
    var relationshipResult =
        (twingraphApiService.getRelationships("orga", graphId, listOf("relationship_a")).first() as
                ResultSet)
            .next()
            .values()
    assertEquals(relationshipStart, relationshipResult)

    logger.info("Update Nodes")
    nodeResult =
        (twingraphApiService
                .updateNodes(
                    "orga", graphId, listOf(mapOf("name" to "node_a", "params" to "size:2")))
                .first() as
                ResultSet)
            .next()
            .values()
    assertNotEquals(nodeStart, nodeResult)

    logger.info("Update Relationships")
    relationshipResult =
        (twingraphApiService
                .updateRelationships(
                    "orga",
                    graphId,
                    listOf(
                        mapOf(
                            "source" to "node_a",
                            "target" to "node_b",
                            "name" to "relationship_a",
                            "params" to "duration:2")))
                .first() as
                ResultSet)
            .next()
            .values()
    assertNotEquals(relationshipStart, relationshipResult)

    logger.info("Delete Relationships")
    twingraphApiService.deleteRelationships(
        "orga", "graph:0", listOf(mapOf("name" to "relationship_a")))
    assertDoesNotThrow {
      twingraphApiService.getRelationships("orga", graphId, listOf("relationship_a"))
    }

    logger.info("Delete Nodes")
    twingraphApiService.deleteNodes("orga", graphId, listOf(mapOf("name" to "node_a")))
    assertDoesNotThrow { twingraphApiService.getNodes("orga", graphId, listOf("node_a")) }
  }
}
