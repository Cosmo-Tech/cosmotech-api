// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.twingraph.extension

import com.cosmotech.api.utils.objectMapper
import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.json.JSONObject
import redis.clients.jedis.graph.Record
import redis.clients.jedis.graph.ResultSet
import redis.clients.jedis.graph.entities.Edge
import redis.clients.jedis.graph.entities.GraphEntity
import redis.clients.jedis.graph.entities.Node

private const val ID_PROPERTY_NAME = "id"

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

val jsonObjectMapper: ObjectMapper =
    objectMapper().configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true)

@Suppress("SwallowedException")
fun GraphEntity.toCsmGraphEntity(type: CsmGraphEntityType): CsmGraphEntity {
  var entityId = this.id.toString()

  if (this.entityPropertyNames.contains(ID_PROPERTY_NAME)) {
    entityId = this.getProperty(ID_PROPERTY_NAME).value.toString()
  }

  val properties =
      this.entityPropertyNames.filter { it != ID_PROPERTY_NAME }.associateWith {
        getJsonCompliantPropertyByName(it)
      }

  val label =
      if (type == CsmGraphEntityType.RELATION) (this as Edge).relationshipType
      else (this as Node).getLabel(0)

  return CsmGraphEntity(label, entityId, properties, type.toString())
}

fun GraphEntity.getJsonCompliantPropertyByName(propertyName: String): Any {
  val propertyValue = this.getProperty(propertyName).value
  return propertyValue.convertToJsonValue()
}

@Suppress("SwallowedException")
fun Any.convertToJsonValue(): Any =
    if (this is String) {
      try {
        val firstTry = jsonObjectMapper.readValue<JSONObject>(this)
        if (firstTry.isEmpty) {
          jsonObjectMapper.readValue(this)
        } else {
          firstTry
        }
      } catch (e: JacksonException) {
        this
      }
    } else {
      this
    }

fun ResultSet.toJsonString(): String {
  val result = mutableListOf<MutableMap<String, Any>>()
  this.forEach { record: Record ->
    val header = record.keys()
    val values = record.values()
    val entries = mutableMapOf<String, Any>()
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
          entries[columnName] = element.convertToJsonValue()
        }
      }
    }
    result.add(entries)
  }
  return result.map { element -> JSONObject(element) }.joinToString(",", "[", "]")
}
