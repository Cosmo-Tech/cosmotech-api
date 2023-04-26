// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.twingraph.extension

import com.cosmotech.api.utils.objectMapper
import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.redislabs.redisgraph.Record
import com.redislabs.redisgraph.ResultSet
import com.redislabs.redisgraph.graph_entities.Edge
import com.redislabs.redisgraph.graph_entities.GraphEntity
import com.redislabs.redisgraph.graph_entities.Node
import org.json.JSONObject

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
// To support simple quoted jsonstring from ADT
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
