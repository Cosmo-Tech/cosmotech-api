// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.twingraph.bulk

import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.twingraph.bulk.model.Edge
import com.cosmotech.twingraph.bulk.model.Node
import com.cosmotech.twingraph.bulk.model.TypeEntity
import redis.clients.jedis.UnifiedJedis

const val BULK_QUERY_MAX_SIZE = 512 * 1024 * 1024 /*512 Mo*/

@Suppress("MagicNumber", "SpreadOperator", "ThrowsCount")
class QueryBuffer(private val jedis: UnifiedJedis, private val graphName: String) {

  private val tasks: MutableList<BulkQuery> = mutableListOf()
  // map of redis node creation id to node id
  private var nodesCreationId = mapOf<String, Long>()

  private var currentBulkQueryBuilder: BulkQuery.Builder =
      BulkQuery.Builder().graphName(graphName).first()
  private var currentType: String? = null

  fun addNode(type: String, row: Map<String, String>) {
    var addedSize = 0
    var typeNodes: TypeEntity? = null

    if (!row.keys.contains("id"))
        throw CsmResourceNotFoundException(
            "Node property 'id' not found, '${row.keys.elementAt(0)}' found instead")

    // create a new TypeNodes (for binary header)
    if (currentType != type) {
      currentType = type
      typeNodes = TypeEntity(type, row.keys.toList())
      addedSize += typeNodes.size
    }
    // create the node
    val node = Node(row)
    addedSize += node.size()
    nodesCreationId += node.id to nodesCreationId.size.toLong()

    // check query size limit
    if (currentBulkQueryBuilder.size() + addedSize > BULK_QUERY_MAX_SIZE) {
      tasks.add(currentBulkQueryBuilder.build())
      currentBulkQueryBuilder = BulkQuery.Builder().graphName(graphName)
      typeNodes = TypeEntity(type, row.keys.toList())
    }

    // add to bulk query builder
    if (typeNodes != null) currentBulkQueryBuilder.addTypeNode(type, typeNodes)
    currentBulkQueryBuilder.addNodeToTypeNode(type, node)
  }

  fun addEdge(type: String, row: Map<String, String>) {
    var addedSize = 0
    var typeEdges: TypeEntity? = null

    if (!row.keys.contains("source"))
        throw CsmResourceNotFoundException(
            "Edge property 'source' not found, '${row.keys.elementAt(0)}' found instead")
    if (!row.keys.contains("target"))
        throw CsmResourceNotFoundException(
            "Edge property 'target' not found, '${row.keys.elementAt(1)}' found instead")

    // extact source and target from row
    val sourceKey = row.keys.elementAt(0)
    val targetKey = row.keys.elementAt(1)
    val sourceName = row[sourceKey].toString().trim()
    val targetName = row[targetKey].toString().trim()

    val newRows = row.minus(sourceKey).minus(targetKey)

    // create a new edge type (for binary header)
    if (type != currentType) {
      currentType = type
      typeEdges = TypeEntity(type, newRows.keys.toList())
      addedSize += typeEdges.size
    }

    // create edge
    if (sourceName !in nodesCreationId.keys)
        throw CsmResourceNotFoundException("Source Node $sourceName doesn't exist")
    if (targetName !in nodesCreationId.keys)
        throw CsmResourceNotFoundException("Target Node $targetName doesn't exist")
    val edge = Edge(nodesCreationId[sourceName]!!, nodesCreationId[targetName]!!, newRows)
    addedSize += edge.size()

    // check query max size
    if (currentBulkQueryBuilder.size() + addedSize > BULK_QUERY_MAX_SIZE) {
      tasks.add(currentBulkQueryBuilder.build())
      currentBulkQueryBuilder = BulkQuery.Builder().graphName(graphName)
      typeEdges = TypeEntity(type, newRows.keys.toList())
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
