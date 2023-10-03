// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.bulk

import com.cosmotech.api.exceptions.CsmResourceNotFoundException
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

  fun addNode(type: String, id: String, properties: Map<String, Any?>) {
    var addedSize = 0
    var typeNodes: TypeEntity? = null

    // create a new TypeNodes (for binary header)
    if (currentType != type) {
      currentType = type
      typeNodes = TypeEntity(type, properties.keys.toList())
      addedSize += typeNodes.size
    }
    // create the node
    val node = Node(id, properties)
    addedSize += node.size()
    nodesCreationId += node.id to nodesCreationId.size.toLong()

    // check query size limit
    if (currentBulkQueryBuilder.size() + addedSize > BULK_QUERY_MAX_SIZE) {
      tasks.add(currentBulkQueryBuilder.build())
      currentBulkQueryBuilder = BulkQuery.Builder().graphName(graphName)
      typeNodes = TypeEntity(type, properties.keys.toList())
    }

    // add to bulk query builder
    if (typeNodes != null) currentBulkQueryBuilder.addTypeNode(type, typeNodes)
    currentBulkQueryBuilder.addNodeToTypeNode(type, node)
  }

  fun addEdge(type: String, sourceId: Long, targetId: Long, properties: Map<String, Any?>) {
    // get SourceKey from nodesCreationId by value if it exists
    val sourceKey =
        nodesCreationId.filterValues { it == sourceId }.keys.firstOrNull()
            ?: throw CsmResourceNotFoundException("Source Node $sourceId doesn't exist")
    val targetKey =
        nodesCreationId.filterValues { it == targetId }.keys.firstOrNull()
            ?: throw CsmResourceNotFoundException("Target Node $targetId doesn't exist")
    addEdge(type, sourceKey, targetKey, properties)
  }

  fun addEdge(type: String, source: String, target: String, properties: Map<String, Any?>) {
    var addedSize = 0
    var typeEdges: TypeEntity? = null

    // create a new edge type (for binary header)
    if (type != currentType) {
      currentType = type
      typeEdges = TypeEntity(type, properties.keys.toList())
      addedSize += typeEdges.size
    }

    // create edge
    if (source !in nodesCreationId.keys)
        throw CsmResourceNotFoundException("Source Node $source doesn't exist")
    if (target !in nodesCreationId.keys)
        throw CsmResourceNotFoundException("Target Node $target doesn't exist")
    val edge = Edge(nodesCreationId[source]!!, nodesCreationId[target]!!, properties)
    addedSize += edge.size()

    // check query max size
    if (currentBulkQueryBuilder.size() + addedSize > BULK_QUERY_MAX_SIZE) {
      tasks.add(currentBulkQueryBuilder.build())
      currentBulkQueryBuilder = BulkQuery.Builder().graphName(graphName)
      typeEdges = TypeEntity(type, properties.keys.toList())
    }

    // add to bulk query builder
    if (typeEdges != null) currentBulkQueryBuilder.addTypeEdge(type, typeEdges)
    currentBulkQueryBuilder.addEdgeToTypeEdge(type, edge)
  }

  fun send() {
    tasks.add(currentBulkQueryBuilder.build())
    tasks.forEach { query ->
      jedis.sendCommand({ "GRAPH.BULK".toByteArray() }, *query.generateQueryArgs())
    }
  }
}
