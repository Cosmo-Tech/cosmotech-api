// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.twingraph.service

import com.cosmotech.api.tests.CsmRedisTestBase
import com.cosmotech.twingraph.api.TwingraphApiService
import com.redis.testcontainers.RedisStackContainer
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
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
import org.springframework.test.util.ReflectionTestUtils
import redis.clients.jedis.JedisPool
import redis.clients.jedis.exceptions.JedisDataException

@ActiveProfiles(profiles = ["twingraph-test"])
@ExtendWith(MockKExtension::class)
@ExtendWith(SpringExtension::class)
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TwingraphServiceIntegrationTest : CsmRedisTestBase() {

  private val logger = LoggerFactory.getLogger(TwingraphServiceIntegrationTest::class.java)

  @Autowired lateinit var twingraphApiService: TwingraphApiService

  val graphId = "graph:lastVersion"
//TODO
//TODO
//TODO
//TODO



//TODO      WHEN GETTING METADATA DO SOMETHING IF NO GRAPH EXIST



//TODO
//TODO
//TODO
//TODO
  @BeforeEach fun setUp() {}

  @Test
  fun TwingraphTest() {

    val context = getContext(redisStackServer)
    val containerIp =
        (context.server as RedisStackContainer).containerInfo.networkSettings.ipAddress
    println(containerIp)
    ReflectionTestUtils.setField(twingraphApiService, "csmJedisPool", JedisPool(containerIp, 6379))

    var nodeStart = listOf<Any>()
    var nodeResult = listOf<Any>()
    var relationshipStart = listOf<Any>()
    var relationshipResult = listOf<Any>()

    //      every { TwinGraph(any(), any()) }

    logger.info("Create Nodes")
    nodeStart = twingraphApiService.createNodes(
        "orga", graphId, listOf(mapOf("name" to "node_a", "params" to "size:0,length:0")))

    logger.info("Read Nodes")
    nodeResult = twingraphApiService.getNodes("orga", graphId, listOf("node_a"))
    assertEquals(nodeStart, nodeResult)

    logger.info("Create Relationships")
    twingraphApiService.createNodes(
        "orga", graphId, listOf(mapOf("name" to "node_b", "params" to "size:1,length:1")))
    relationshipStart = twingraphApiService.createRelationships(
        "orga",
        graphId,
        listOf(
            mapOf(
                "source" to "node_a",
                "target" to "node_b",
                "name" to "relationship_a",
            "params" to "duration: 0")))

    logger.info("Read Relationships")
    relationshipResult =
        twingraphApiService.getRelationships("orga", graphId, listOf("relationship_a"))
    assertEquals(relationshipStart, relationshipResult)

    logger.info("Update Nodes")
    twingraphApiService.updateNodes(
        "orga", graphId, listOf(mapOf("name" to "node_a", "params" to "size:2,length:2")))
    nodeResult = twingraphApiService.getNodes("orga", graphId, listOf("node_a"))
    assertNotEquals(nodeStart, nodeResult)

    logger.info("Update Relationships")
    twingraphApiService.updateRelationships(
        "orga",
        graphId,
        listOf(
            mapOf(
                "source" to "node_a",
                "target" to "node_b",
                "name" to "relationship_a",
                "params" to "duration: 2")))
    relationshipResult =
        twingraphApiService.getRelationships("orga", graphId, listOf("relationship_a"))
    assertNotEquals(relationshipStart, relationshipResult)

    logger.info("Delete Relationships")
    twingraphApiService.deleteRelationships(
        "orga", "graph:0", listOf(mapOf("name" to "relationship_a")))
    assertThrows<JedisDataException> {
        twingraphApiService.getRelationships("orga", graphId, listOf("relationship_a"))
    }

    logger.info("Delete Nodes")
    twingraphApiService.deleteNodes("orga", graphId, listOf(mapOf("name" to "node_a")))
      assertThrows<JedisDataException> {
          twingraphApiService.getNodes("orga", graphId, listOf("node_a"))
      }
  }
}
