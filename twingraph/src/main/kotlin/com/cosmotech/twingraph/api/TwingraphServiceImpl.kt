// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.twingraph.api

import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.utils.objectMapper
import com.cosmotech.twingraph.domain.TwinGraphImport
import com.cosmotech.twingraph.domain.TwinGraphImportInfo
import com.cosmotech.twingraph.domain.TwinGraphQuery
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import redis.clients.jedis.UnifiedJedis
import redis.clients.jedis.graph.Record
import redis.clients.jedis.graph.ResultSet
import redis.clients.jedis.graph.entities.Edge
import redis.clients.jedis.graph.entities.Node
import redis.clients.jedis.params.ScanParams
import redis.clients.jedis.params.ScanParams.SCAN_POINTER_START
import kotlin.collections.EmptyMap.keys
import kotlin.jvm.internal.SpreadBuilder

@Service
class TwingraphServiceImpl(val jedis: UnifiedJedis) : TwingraphApiService {

  val logger: Logger = LoggerFactory.getLogger(TwingraphServiceImpl::class.java)

  override fun importGraph(
      organizationId: String,
      twinGraphImport: TwinGraphImport
  ): TwinGraphImportInfo {
    TODO("Not yet implemented")
  }

  override fun delete(graphId: String) {
    val matchingKeys = mutableSetOf<String>()
    var nextCursor = SCAN_POINTER_START
    do {
      val scanResult = jedis.scan(nextCursor, ScanParams().match("$graphId*"))
      nextCursor = scanResult.cursor
      matchingKeys.addAll(scanResult.result)
    } while (!nextCursor.equals(SCAN_POINTER_START))

    @Suppress("SpreadOperator") val count = jedis.del(*matchingKeys.toTypedArray())
    logger.debug("$count keys are removed from Twingraph with prefix $graphId")
  }

  override fun query(organizationId: String, twinGraphQuery: TwinGraphQuery): String {

    if (twinGraphQuery.version.isNullOrEmpty()) {
      twinGraphQuery.version = jedis.hget("${twinGraphQuery.graphId}MetaData", "lastVersion")
      if (twinGraphQuery.version.isNullOrEmpty()) {
          throw CsmResourceNotFoundException("Cannot query. Any version found for ${twinGraphQuery.graphId}")
      }
    }

    val redisGraphId = "${twinGraphQuery.graphId}:${twinGraphQuery.version}"
    val resultSet: ResultSet = jedis.graphQuery(redisGraphId, twinGraphQuery.query)

    val iterator = resultSet.iterator()
    if (!iterator.hasNext()) {
      throw CsmResourceNotFoundException(
          "TwinGraph empty with given ${twinGraphQuery.graphId} " +
              "and version ${twinGraphQuery.version}")
    }

    val result = mutableMapOf<Int, MutableSet<String>>()
    val elementTypes = mutableMapOf<Int, String>()

    while (iterator.hasNext()) {
      val record: Record? = iterator.next()
      record?.values()?.forEachIndexed { index, element ->
        val currentValue = result.getOrPut(index) { mutableSetOf() }
        when (element) {
          is Node -> {
            currentValue.add(getNodeJson(element))
            result[index] = currentValue
            elementTypes[index] = "nodes"
          }
          is Edge -> {
            currentValue.add(objectMapper().writeValueAsString(element))
            result[index] = currentValue
            elementTypes[index] = "relationships"
          }
          else -> {
            currentValue.add(objectMapper().writeValueAsString(mapOf("value" to element)))
            result[index] = currentValue
            elementTypes[index] = "variables"
          }
        }
      }
    }

    val resultJson = mutableMapOf<String, String>()
    result.values.forEachIndexed { index, element ->
      val elementType = elementTypes[index]!!
      resultJson.computeIfAbsent(elementType) { getJsonValue(resultSet, index, element) }
      resultJson.computeIfPresent(elementType) { _, value ->
        value + ", " + getJsonValue(resultSet, index, element)
      }
    }
    return resultJson.map { "\"${it.key}\": {${it.value}}" }.joinToString(",", "{", "}")
  }

  private fun getJsonValue(resultSet: ResultSet, index: Int, element: MutableSet<String>): String {
    return "\"${resultSet.header.schemaNames[index]}\" : " + element.joinToString(",", "[", "]")
  }
}
