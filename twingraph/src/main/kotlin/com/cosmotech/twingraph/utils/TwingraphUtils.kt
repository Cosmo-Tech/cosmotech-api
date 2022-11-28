// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.twingraph.utils

import com.cosmotech.api.utils.objectMapper
import redis.clients.jedis.graph.entities.Node
import redis.clients.jedis.graph.entities.Property

object TwingraphUtils {

  @JvmStatic
  fun getNodeJson(node: Node): String {
    val graphNode =
        GraphNode(node.getLabel(0), node.id, node.entityPropertyNames.map { node.getProperty(it) })
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

data class GraphNode(var label: String, var id: Long, var properties: List<Property<Any>>)
