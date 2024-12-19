// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.twingraph.bulk

import com.cosmotech.dataset.bulk.model.BinaryEntities
import com.cosmotech.dataset.bulk.model.Edge
import com.cosmotech.dataset.bulk.model.Node

class BulkQuery
private constructor(
    private val graphName: String,
    private val begin: Boolean,
    private val typeNodesList: List<BinaryEntities>,
    private val typeEdgesList: List<BinaryEntities>
) {

  data class Builder(
      private var graphName: String? = null,
      private var begin: Boolean = false,
      private var typedBinaryNodes: Map<String, BinaryEntities> = mapOf(),
      private var typedBinaryEdges: Map<String, BinaryEntities> = mapOf()
  ) {
    fun graphName(name: String) = apply { this.graphName = name }

    fun first() = apply { this.begin = true }

    fun addNodeTypeGroup(type: String, binaryEntities: BinaryEntities) = apply {
      if (type !in typedBinaryNodes) this.typedBinaryNodes += type to binaryEntities
    }

    fun addNodeToNodeTypeGroup(type: String, node: Node) = apply {
      if (type in this.typedBinaryNodes) this.typedBinaryNodes[type]?.addEntity(node)
    }

    fun addEdgeTypeGroup(type: String, typeEdge: BinaryEntities) = apply {
      if (type !in typedBinaryEdges) this.typedBinaryEdges += type to typeEdge
    }

    fun addEdgeToEdgeTypeGroup(type: String, edge: Edge) = apply {
      if (type in this.typedBinaryEdges) this.typedBinaryEdges[type]?.addEntity(edge)
    }

    // For ease, those sizes are not calculated as there are very small
    //   GRAPH.BULK     string to byteArray size
    //   graphName      string to byteArray size
    //   BEGIN          string to byteArray size
    //   nodeCount      string to byteArray size
    //   nodeTypeCount  string to byteArray size
    //   edgeCount      string to byteArray size
    //   edgtypeCount   string to byteArray size
    fun size(): Int {
      return typedBinaryNodes.values.sumOf { it.size } + typedBinaryEdges.values.sumOf { it.size }
    }

    fun build() =
        BulkQuery(
            graphName!!, begin, typedBinaryNodes.values.toList(), typedBinaryEdges.values.toList())
  }

  fun generateQueryArgs(): Array<ByteArray> {
    var args: Array<ByteArray> = arrayOf()

    args += graphName.toByteArray()
    if (begin) args += "BEGIN".toByteArray()
    // count of node and edge to create
    args += typeNodesList.map { it.binaryBlobFormat }.flatten().size.toString().toByteArray()
    args += typeEdgesList.map { it.binaryBlobFormat }.flatten().size.toString().toByteArray()
    // count of node type and reltype
    args += typeNodesList.size.toString().toByteArray()
    args += typeEdgesList.size.toString().toByteArray()

    this.typeNodesList.forEach { args += it.toBinary() }
    this.typeEdgesList.forEach { args += it.toBinary() }

    return args
  }

  private fun ByteArray.toHex(): String =
      joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

  override fun toString(): String {
    return generateQueryArgs().joinToString(separator = " - ") { bytes -> bytes.toHex() }
  }
}
