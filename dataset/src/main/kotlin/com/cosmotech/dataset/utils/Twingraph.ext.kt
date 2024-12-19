// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
@file:Suppress("DEPRECATION")

package com.cosmotech.dataset.utils

import com.cosmotech.api.utils.objectMapper
import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.lang.IndexOutOfBoundsException
import org.json.JSONObject
import redis.clients.jedis.graph.Record
import redis.clients.jedis.graph.ResultSet
import redis.clients.jedis.graph.entities.Edge
import redis.clients.jedis.graph.entities.GraphEntity
import redis.clients.jedis.graph.entities.Node

data class CsmGraphEntity(
    var label: String,
    var id: String,
    var properties: Map<String, Any?>,
    var type: String
)

enum class CsmGraphEntityType {
  RELATION,
  NODE
}

// To support simple quoted jsonstring from ADT
val jsonObjectMapper: ObjectMapper =
    objectMapper().configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true)

@Suppress("SwallowedException")
fun GraphEntity.toCsmGraphEntity(type: CsmGraphEntityType): CsmGraphEntity {
  var entityId = this.id.toString()

  val properties = this.entityPropertyNames.associateWith { getJsonCompliantPropertyByName(it) }

  val label =
      when (type) {
        CsmGraphEntityType.RELATION -> (this as Edge).relationshipType
        CsmGraphEntityType.NODE ->
            (this as Node).takeIf { it.numberOfLabels > 0 }?.getLabel(0)
                ?: throw IndexOutOfBoundsException("Node has no label: $entityId")
      }

  return CsmGraphEntity(label, entityId, properties, type.toString())
}

fun GraphEntity.getJsonCompliantPropertyByName(propertyName: String): Any {
  val propertyValue = this.getProperty(propertyName).value
  return propertyValue.convertToJsonValue()
}

@Suppress("SwallowedException")
fun Any.convertToJsonValue(): Any {
  var result = this
  if (this is String) {
    if (this.startsWith("{") || this.startsWith("[") || this.startsWith("\"")) {
      result =
          try {
            val firstTry = jsonObjectMapper.readValue<JSONObject>(this)
            if (firstTry.isEmpty) {
              jsonObjectMapper.readValue(this)
            } else {
              firstTry
            }
          } catch (e: JacksonException) {
            return result
          }
    }
  }
  return result
}

fun ResultSet.toJsonString(): String {
  val result = mutableListOf<MutableMap<String, Any?>>()
  this.forEach { record: Record? ->
    val header = record?.keys() ?: listOf()
    val values = record?.values() ?: listOf()
    val entries = mutableMapOf<String, Any?>()
    values.forEachIndexed { valueIndex, element ->
      val columnName = header[valueIndex]
      when (element) {
        is Node -> {
          entries[columnName] = JSONObject(element.toCsmGraphEntity(CsmGraphEntityType.NODE))
        }
        is Edge -> {
          entries[columnName] = JSONObject(element.toCsmGraphEntity(CsmGraphEntityType.RELATION))
        }
        else -> {
          element?.let { entries[columnName] = element.convertToJsonValue() }
              ?: run { entries[columnName] = null }
        }
      }
    }
    result.add(entries)
  }
  return result.map { element -> JSONObject(element) }.joinToString(",", "[", "]")
}

fun String.isReadOnlyQuery(): Boolean {
  val queryNormalized = this.trim().lowercase()
  val matchResults =
      "\\b(create|set|merge|delete|remove)\\b".toRegex().findAll(queryNormalized).toList()
  return matchResults.isEmpty()
}
