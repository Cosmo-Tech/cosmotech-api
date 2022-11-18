// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.twingraph.api

import com.cosmotech.api.utils.objectMapper
import redis.clients.jedis.graph.entities.Node
import redis.clients.jedis.graph.entities.Property

fun getNodeJson(node: Node): String {
  val graphNode =
      GraphNode(node.getLabel(0), node.id, node.entityPropertyNames.map { node.getProperty(it) })
  return objectMapper().writeValueAsString(graphNode)
}

data class GraphNode(var label: String, var id: Long, var properties: List<Property<Any>>)
