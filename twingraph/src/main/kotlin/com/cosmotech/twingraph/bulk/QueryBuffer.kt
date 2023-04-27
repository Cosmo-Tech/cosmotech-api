// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.twingraph.bulk

import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import java.nio.charset.Charset
import redis.clients.jedis.Jedis

@Suppress("MagicNumber", "SpreadOperator")
class QueryBuffer(
    val jedis: Jedis,
    val graphName: String,
    val maxQuerySize: Int = 512 * 1024 * 1024 /*512 Mo*/
) {
  private var first: Boolean = true

  val tasks: MutableList<BulkQuery> = mutableListOf()
  // map of redis node creation id to node id
  private var nodesCreationId = mapOf<String, Long>()

  private var currentBulkQueryBuilder: BulkQuery.Builder =
      BulkQuery.Builder().graphName(graphName).first()
  private var currentType: String? = null

  fun addNode(type: String, row: Map<String, Any>) {
    var addedSize = 0
    var typeNodes: TypeNodes? = null

    // create a new TypeNodes (for binary header)
    if (currentType != type) {
      currentType = type
      typeNodes = TypeNodes(type, row.keys.toList())
      addedSize += typeNodes.size
    }
    // create the node
    val node = Node(row)
    addedSize += node.size()
    nodesCreationId += node.id to nodesCreationId.size.toLong()

    // check query size limit
    if (currentBulkQueryBuilder.size() + addedSize > maxQuerySize) {
      tasks.add(currentBulkQueryBuilder.build())
      currentBulkQueryBuilder = BulkQuery.Builder().graphName(graphName)
      typeNodes = TypeNodes(type, row.keys.toList())
    }

    // add to bulk query builder
    if (typeNodes != null) currentBulkQueryBuilder.addTypeNode(type, typeNodes)
    currentBulkQueryBuilder.addNodeToTypeNode(type, node)
  }

  fun addEdge(type: String, row: Map<String, Any>) {
    var addedSize: Int = 0
    var typeEdges: TypeEdges? = null

    // extact source and target from row
    val sourceKey = row.keys.elementAt(0)
    val targetKey = row.keys.elementAt(1)
    val sourceName = row[sourceKey]
    val targetName = row[targetKey]

    val newRows = row.minus(sourceKey).minus(targetKey)

    // create a new edge type (for binary header)
    if (type != currentType) {
      currentType = type
      typeEdges = TypeEdges(type, newRows.keys.toList())
      addedSize += typeEdges.size
    }

    // create edge
    if (sourceName !in nodesCreationId.keys)
        throw CsmResourceNotFoundException("Node $sourceName doesn't exist")
    if (targetName !in nodesCreationId.keys)
        throw CsmResourceNotFoundException("Node $targetName doesn't exist")
    var edge = Edge(nodesCreationId[sourceName]!!, nodesCreationId[targetName]!!, newRows)
    addedSize += edge.size()

    // check query max size
    if (currentBulkQueryBuilder.size() + addedSize > maxQuerySize) {
      tasks.add(currentBulkQueryBuilder.build())
      currentBulkQueryBuilder = BulkQuery.Builder().graphName(graphName)
      typeEdges = TypeEdges(type, newRows.keys.toList())
    }

    // add to bulk query builder
    if (typeEdges != null) currentBulkQueryBuilder.addTypeEdge(type, typeEdges)
    currentBulkQueryBuilder.addEdgeToTypeEdge(type, edge)
  }

  fun send() {
    tasks.add(currentBulkQueryBuilder.build())

    tasks.forEach { query ->
      println(query.toString())
      val result = jedis.sendCommand({ "GRAPH.BULK".toByteArray() }, *query.generateQueryArgs())

      println((result as ByteArray).toString(Charset.defaultCharset()))
    }
  }
}
