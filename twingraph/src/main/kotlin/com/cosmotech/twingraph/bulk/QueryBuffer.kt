// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.twingraph.bulk

import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.twingraph.bulk.model.Edge
import com.cosmotech.twingraph.bulk.model.Node
import com.cosmotech.twingraph.bulk.model.TypeEntity
import java.nio.charset.Charset
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

  fun addNode(type: String, row: Map<String, String>) {
    var addedSize = 0
    var typeNodes: TypeEntity? = null

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
