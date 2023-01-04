// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.twingraph.utils

import com.cosmotech.api.utils.objectMapper
import com.redislabs.redisgraph.graph_entities.Node
import com.redislabs.redisgraph.graph_entities.Property

object TwingraphUtils {

  private const val NODE_ID_PROPERTY_NAME = "id"

  @JvmStatic
  fun getNodeJson(node: Node): String {
    var nodeId = node.id.toString()

    if (node.entityPropertyNames.contains(NODE_ID_PROPERTY_NAME)) {
      nodeId = node.getProperty(NODE_ID_PROPERTY_NAME).value.toString()
    }

    val graphNode =
        GraphNode(
            node.getLabel(0),
            nodeId,
            node.entityPropertyNames.filter { it != NODE_ID_PROPERTY_NAME }.map {
              node.getProperty(it)
            })
    return objectMapper().writeValueAsString(graphNode)
  }

  @JvmStatic
  fun isReadOnlyQuery(query: String): Boolean {
    val queryNormalized = query.trim().lowercase()
    val matchResults =
        "\\b(create|set|merge|delete|remove)\\b".toRegex().findAll(queryNormalized).toList()
    return matchResults.isEmpty()
  }
}

data class GraphNode(var label: String, var id: String, var properties: List<Property<Any>>)
