// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.twingraph.api

import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.utils.objectMapper
import com.cosmotech.twingraph.domain.TwinGraphImport
import com.cosmotech.twingraph.domain.TwinGraphImportInfo
import com.cosmotech.twingraph.domain.TwinGraphQuery
import com.redislabs.redisgraph.Record
import com.redislabs.redisgraph.ResultSet
import com.redislabs.redisgraph.graph_entities.Edge
import com.redislabs.redisgraph.graph_entities.Node
import com.redislabs.redisgraph.impl.api.RedisGraph
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import redis.clients.jedis.JedisPool
import redis.clients.jedis.ScanParams
import redis.clients.jedis.ScanParams.SCAN_POINTER_START

@Service
class TwingraphServiceImpl(val jedisPool: JedisPool, val redisGraph: RedisGraph) :
    TwingraphApiService {

  val logger = LoggerFactory.getLogger(TwingraphServiceImpl::class.java)

  override fun importGraph(
      organizationId: String,
      twinGraphImport: TwinGraphImport
  ): TwinGraphImportInfo {
    TODO("Not yet implemented")
  }

  override fun delete(graphId: String) {
    val jedis = jedisPool.resource
    val matchingKeys = mutableSetOf<String>()
    var nextCursor = SCAN_POINTER_START
    do {
      var scanResult = jedis.scan(nextCursor, ScanParams().match("$graphId:*"))
      nextCursor = scanResult.cursor
      matchingKeys.addAll(scanResult.result)
    } while (!nextCursor.equals(SCAN_POINTER_START))

    @Suppress("SpreadOperator")
    val count = jedis.del(*matchingKeys.toTypedArray())
    jedisPool.returnResource(jedis)
    logger.debug("$count keys are removed from Twingraph with prefix $graphId")
  }

  override fun query(organizationId: String, twinGraphQuery: TwinGraphQuery): String {
    val redisGraphId = "${twinGraphQuery.graphId}:${twinGraphQuery.version}"
    var resultSet: ResultSet = redisGraph.query(redisGraphId, twinGraphQuery.query)

    if (!resultSet.hasNext()) {
      throw CsmResourceNotFoundException(
          "TwinGraph empty with given ${twinGraphQuery.graphId} " +
              "and version ${twinGraphQuery.version}")
    }

    val result = mutableMapOf<Int, MutableSet<String>>()

    while (resultSet.hasNext()) {
      val record: Record = resultSet.next()
      record.values().forEachIndexed { index, element ->
        val currentValue = result.getOrPut(index) { mutableSetOf() }
        when (element) {
          is Node -> {
            currentValue.add(getNodeJson(element))
            result[index] = currentValue
          }
          is Edge -> {
            currentValue.add(objectMapper().writeValueAsString(element))
            result[index] = currentValue
          }
          else -> {
            currentValue.add(objectMapper().writeValueAsString(mapOf("value" to element)))
            result[index] = currentValue
          }
        }
      }
    }

    var resultJson = mutableSetOf<String>()
    result.values.forEachIndexed { index, element ->
      resultJson.add(
          "\"${resultSet.header.schemaNames[index]}\" : " +
              "${element.joinToString(",", "[", "]") }")
    }

    return resultJson.joinToString(",", "{", "}")
  }
}
