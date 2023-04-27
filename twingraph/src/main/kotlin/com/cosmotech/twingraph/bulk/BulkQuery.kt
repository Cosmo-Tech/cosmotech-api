// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.twingraph.bulk
class BulkQuery
private constructor(
    private val graphName: String,
    private val begin: Boolean,
    private val typeNodesList: List<TypeNodes>,
    private val typeEdgesList: List<TypeEdges>
) {

  data class Builder(
      private var graphName: String? = null,
      private var begin: Boolean = false,
      private var typeNodes: Map<String, TypeNodes> = mapOf(),
      private var typeEdges: Map<String, TypeEdges> = mapOf()
  ) {
    fun graphName(name: String) = apply { this.graphName = name }
    fun first() = apply { this.begin = true }

    fun addTypeNode(type: String, typeNode: TypeNodes) = apply {
      if (type !in typeNodes) this.typeNodes += type to typeNode
    }
    fun addNodeToTypeNode(type: String, node: Node) = apply {
      if (type in this.typeNodes) this.typeNodes[type]?.addNodes(node)
    }

    fun addTypeEdge(type: String, typeEdge: TypeEdges) = apply {
      if (type !in typeEdges) this.typeEdges += type to typeEdge
    }
    fun addEdgeToTypeEdge(type: String, edge: Edge) = apply {
      if (type in this.typeEdges) this.typeEdges[type]?.addEdges(edge)
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
    args += typeNodesList.map { it.nodesBinaryBlobFormat }.flatten().size.toString().toByteArray()
    args += typeEdgesList.map { it.edgesBinaryBlobFormat }.flatten().size.toString().toByteArray()
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
