// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.twingraph.bulk

import com.cosmotech.twingraph.bulk.model.Edge
import com.cosmotech.twingraph.bulk.model.Node
import com.cosmotech.twingraph.bulk.model.TypeEntity

class BulkQuery
private constructor(
    private val graphName: String,
    private val begin: Boolean,
    private val typeNodesList: List<TypeEntity>,
    private val typeEdgesList: List<TypeEntity>
) {

  data class Builder(
      private var graphName: String? = null,
      private var begin: Boolean = false,
      private var typeNodes: Map<String, TypeEntity> = mapOf(),
      private var typeEdges: Map<String, TypeEntity> = mapOf()
  ) {
    fun graphName(name: String) = apply { this.graphName = name }
    fun first() = apply { this.begin = true }

    fun addTypeNode(type: String, typeNode: TypeEntity) = apply {
      if (type !in typeNodes) this.typeNodes += type to typeNode
    }
    fun addNodeToTypeNode(type: String, node: Node) = apply {
      if (type in this.typeNodes) this.typeNodes[type]?.addEntity(node)
    }

    fun addTypeEdge(type: String, typeEdge: TypeEntity) = apply {
      if (type !in typeEdges) this.typeEdges += type to typeEdge
    }
    fun addEdgeToTypeEdge(type: String, edge: Edge) = apply {
      if (type in this.typeEdges) this.typeEdges[type]?.addEntity(edge)
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
      return typeNodes.values.sumOf { it.size } + typeEdges.values.sumOf { it.size }
    }

    fun build() =
        BulkQuery(graphName!!, begin, typeNodes.values.toList(), typeEdges.values.toList())
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
