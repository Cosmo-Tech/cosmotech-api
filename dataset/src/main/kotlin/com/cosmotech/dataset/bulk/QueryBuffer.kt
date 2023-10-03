// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.bulk

import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.dataset.bulk.model.AbstractEntity
import com.cosmotech.dataset.bulk.model.Edge
import com.cosmotech.dataset.bulk.model.Node
import com.cosmotech.dataset.bulk.model.TypeEntity
import com.cosmotech.twingraph.bulk.BulkQuery
import redis.clients.jedis.Jedis

const val BULK_QUERY_MAX_SIZE = 512 * 1024 * 1024 /*512 Mo*/

@Suppress("MagicNumber", "SpreadOperator")
class QueryBuffer(val jedis: Jedis, val graphName: String) {

  val tasks: MutableList<BulkQuery> = mutableListOf()
  // map of redis node creation id to node id
  private var nodesCreationId = mapOf<String, Long>()

  private var currentBulkQueryBuilder: BulkQuery.Builder =
      BulkQuery.Builder().graphName(graphName).first()
  private var currentType: String? = null

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
    var typeEntities: TypeEntity? = null

    // create a new edge type (for binary header)
    if (type != currentType) {
      currentType = type
      typeEntities = TypeEntity(type, properties.keys.toList())
      addedSize += typeEntities.size
    }

    // create edge
    val entity = addingEntityLambda() ?: return
    addedSize += entity.size()

    // check query max size
    if (currentBulkQueryBuilder.size() + addedSize > BULK_QUERY_MAX_SIZE) {
      tasks.add(currentBulkQueryBuilder.build())
      currentBulkQueryBuilder = BulkQuery.Builder().graphName(graphName)
      typeEntities = TypeEntity(type, entity.properties.keys.toList())
    }

    // add to bulk query builder
    when (entity) {
      is Node -> {
        typeEntities?.let { currentBulkQueryBuilder.addTypeNode(type, it) }
        currentBulkQueryBuilder.addNodeToTypeNode(type, entity)
      }
      is Edge -> {
        typeEntities?.let { currentBulkQueryBuilder.addTypeEdge(type, it) }
        currentBulkQueryBuilder.addEdgeToTypeEdge(type, entity)
      }
    }
  }

  fun send() {
    tasks.add(currentBulkQueryBuilder.build())
    tasks.forEach { query ->
      jedis.sendCommand({ "GRAPH.BULK".toByteArray() }, *query.generateQueryArgs())
    }
  }
}
