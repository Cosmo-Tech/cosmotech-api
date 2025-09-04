// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.bulk

import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.dataset.bulk.model.AbstractEntity
import com.cosmotech.dataset.bulk.model.BinaryEntities
import com.cosmotech.dataset.bulk.model.Edge
import com.cosmotech.dataset.bulk.model.Node
import com.cosmotech.twingraph.bulk.BulkQuery
import redis.clients.jedis.UnifiedJedis

const val BULK_QUERY_MAX_SIZE = 512 * 1024 * 1024 /*512 Mo*/

@Suppress("MagicNumber", "SpreadOperator")
class QueryBuffer(val unifiedJedis: UnifiedJedis, val graphName: String) {

  val tasks: MutableList<BulkQuery> = mutableListOf()
  // map of redis node creation id to node id
  private var nodesCreationId = mapOf<String, Long>()

  private var currentBulkQueryBuilder: BulkQuery.Builder =
      BulkQuery.Builder().graphName(graphName).first()

  private var currentNodeType: String? = null
  private var currentEdgeType: String? = null

  private val nodeSet: HashSet<String> = hashSetOf()
  private val edgeSet: HashSet<String> = hashSetOf()

  fun addNode(type: String, id: String, properties: Map<String, Any?>) {
    addEntity(type, properties) {
      if (id in nodeSet) return@addEntity null

      val node = Node(id, properties)
      nodeSet.add(id)
      nodesCreationId += node.id to nodesCreationId.size.toLong()
      node
    }
  }

  fun addEdge(type: String, sourceId: Long, targetId: Long, properties: Map<String, Any?>) {
    addEntity(type, properties) {
      if ("$sourceId-$targetId" in edgeSet) return@addEntity null

      val edge = Edge(sourceId, targetId, properties)
      edgeSet.add("$sourceId-$targetId")
      edge
    }
  }

  fun addEdge(type: String, source: String, target: String, properties: Map<String, Any?>) {
    return addEntity(type, properties) {
      // create edge
      if (source !in nodesCreationId.keys)
          throw CsmResourceNotFoundException("Source Node $source doesn't exist")
      if (target !in nodesCreationId.keys)
          throw CsmResourceNotFoundException("Target Node $target doesn't exist")
      Edge(nodesCreationId[source]!!, nodesCreationId[target]!!, properties)
    }
  }

  fun addEntity(
      type: String,
      properties: Map<String, Any?>,
      addingEntityLambda: () -> AbstractEntity?
  ) {
    var addedSize = 0
    var typeEntities: BinaryEntities? = null

    val entity = addingEntityLambda() ?: return

    when (entity) {
      is Node -> {
        if (currentNodeType != type) {
          currentNodeType = type
          typeEntities = BinaryEntities(type, properties.keys.toList())
          addedSize += typeEntities.size
        }
      }
      is Edge -> {
        if (currentEdgeType != type) {
          currentEdgeType = type
          typeEntities = BinaryEntities(type, properties.keys.toList())
          addedSize += typeEntities.size
        }
      }
    }

    addedSize += entity.size()

    // check query max size
    if (currentBulkQueryBuilder.size() + addedSize > BULK_QUERY_MAX_SIZE) {
      tasks.add(currentBulkQueryBuilder.build())
      currentBulkQueryBuilder = BulkQuery.Builder().graphName(graphName)
      typeEntities = BinaryEntities(type, entity.properties.keys.toList())
    }

    // add to bulk query builder
    when (entity) {
      is Node -> {
        typeEntities?.let { currentBulkQueryBuilder.addNodeTypeGroup(type, it) }
        currentBulkQueryBuilder.addNodeToNodeTypeGroup(type, entity)
      }
      is Edge -> {
        typeEntities?.let { currentBulkQueryBuilder.addEdgeTypeGroup(type, it) }
        currentBulkQueryBuilder.addEdgeToEdgeTypeGroup(type, entity)
      }
    }
  }

  fun send() {
    tasks.add(currentBulkQueryBuilder.build())
    tasks.forEach { query ->
      unifiedJedis.sendCommand({ "GRAPH.BULK".toByteArray() }, *query.generateQueryArgs())
    }
  }
}
