// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.twingraph.api

import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.redislabs.redisgraph.ResultSet
import com.redislabs.redisgraph.impl.api.RedisGraph
import redis.clients.jedis.JedisPool
import redis.clients.jedis.ScanParams

class TwinGraph(var graphId: String, var csmJedisPool: JedisPool) : RedisGraph() {

  private var name: String
  private var version: String = ""

  init {
    val list = graphId.split(":")
    name = list[0]
    if (list.size == 2) {
      version = list[1]
    } else if (list.size == 1) {
      version = getMetadata()["lastVersion"]!!
    } else {
      // TODO
      throw IllegalArgumentException("Invalid Id : $graphId")
    }
  }

  fun createNodes(twinsList: List<Map<String, String>>): List<ResultSet> {
    val resultList = mutableListOf<ResultSet>()
    for (args in twinsList) {
      resultList.add(graphQuery("CREATE (:${args["name"]} {${args["params"]}})"))
    }
    return resultList
  }

  fun createRelationships(relationshipList: List<Map<String, String>>): List<ResultSet> {
    val resultList = mutableListOf<ResultSet>()
    for (args in relationshipList) {
      resultList.add(
          graphQuery(
              "MATCH (a),(b) WHERE a.id=${args["source"]} AND b.id=${args["target"]}" +
                  "CREATE (a)-[:${args["name"]} {${args["params"]}}-(b)"))
    }
    return resultList
  }

  fun getNodes(listId: List<String>): List<ResultSet> {
    val resultList = mutableListOf<ResultSet>()
    for (arg in listId) {
      resultList.add(graphQuery("MATCH (t {id:$arg}) RETURN t"))
    }
    return resultList
  }

  fun getRelationships(listId: List<String>): List<ResultSet> {
    val resultList = mutableListOf<ResultSet>()
    for (arg in listId) {
      resultList.add(graphQuery("MATCH ()-[r {id:$arg}]-() RETURN r"))
    }
    return resultList
  }

  fun updateNodes(twinsList: List<Map<String, String>>): List<ResultSet> {
    val resultList = mutableListOf<ResultSet>()
    for (args in twinsList) {
      graphQuery("MATCH (a) WHERE a.id=${args["name"]} SET ${args["params"]}")
    }
    return resultList
  }

  fun updateRelationships(relationshipList: List<Map<String, String>>): List<ResultSet> {
    val resultList = mutableListOf<ResultSet>()
    for (args in relationshipList) {
      resultList.add(graphQuery("MATCH ()-[a]-() WHERE a.id=${args["name"]} SET ${args["params"]}"))
    }
    return resultList
  }

  fun deleteNodes(twinsList: List<Map<String, String>>) {
    for (args in twinsList) {
      graphQuery("MATCH (a) WHERE a.id=${args["name"]} DELETE a")
    }
  }

  fun deleteRelationships(relationshipList: List<Map<String, String>>) {
    for (args in relationshipList) {
      graphQuery("MATCH ()-[a]-() WHERE a.id=${args["name"]} DELETE a")
    }
  }

  fun delete() {
    for (item in getVersions()) {
      deleteGraph(item)
    }
  }

  private fun getVersions(): List<String> {
    val matchingKeys = mutableSetOf<String>()
    csmJedisPool.resource.use { jedis ->
      var nextCursor = ScanParams.SCAN_POINTER_START
      do {
        val scanResult = jedis.scan(nextCursor, ScanParams().match("${graphId}:*"), "graphdata")
        nextCursor = scanResult.cursor
        matchingKeys.addAll(scanResult.result)
      } while (!nextCursor.equals(ScanParams.SCAN_POINTER_START))
    }
    return matchingKeys.toList()
  }

  private fun getMetadata(): Map<String, String> {
    val metaDataKey = "${name}MetaData"
    csmJedisPool.resource.use { jedis ->
      if (jedis.exists(metaDataKey)) {
        return jedis.hgetAll(metaDataKey)
      }
      throw CsmResourceNotFoundException("No metadata found for graphId $name")
    }
  }

  fun graphQuery(queryText: String): ResultSet {
    return query("$name:$version", queryText)
  }
}
