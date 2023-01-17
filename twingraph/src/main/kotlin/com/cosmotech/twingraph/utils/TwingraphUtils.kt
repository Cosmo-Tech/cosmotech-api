// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.twingraph.utils

import com.cosmotech.api.utils.objectMapper
import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.module.kotlin.readValue
import redis.clients.jedis.graph.entities.Node

object TwingraphUtils {

  private const val NODE_ID_PROPERTY_NAME = "id"

  @Suppress("SwallowedException")
  @JvmStatic
  fun getNodeJson(node: Node): String {
    var nodeId = node.id.toString()
    if (node.entityPropertyNames.contains(NODE_ID_PROPERTY_NAME)) {
      nodeId = node.getProperty(NODE_ID_PROPERTY_NAME).value.toString()
    }
    val jsonObjectMapper = objectMapper()

    val properties = mutableMapOf<String, Any>()
    node.entityPropertyNames.filter { it != NODE_ID_PROPERTY_NAME }.map { propertyName ->
      val propertyValue = node.getProperty(propertyName).value
      if (propertyValue is String) {
        try {
          properties[propertyName] = jsonObjectMapper.readValue(propertyValue)
        } catch (e: JacksonException) {
          properties[propertyName] = propertyValue
        }
      } else {
        properties[propertyName] = propertyValue
      }
    }

    val graphNode = GraphNode(node.getLabel(0), nodeId, properties)
    return jsonObjectMapper.writeValueAsString(graphNode)
  }

  @JvmStatic
  fun isReadOnlyQuery(query: String): Boolean {
    val queryNormalized = query.trim().lowercase()
    val matchResults =
        "\\b(create|set|merge|delete|remove)\\b".toRegex().findAll(queryNormalized).toList()
    return matchResults.isEmpty()
  }
}

data class GraphNode(var label: String, var id: String, var properties: MutableMap<String, Any>)
