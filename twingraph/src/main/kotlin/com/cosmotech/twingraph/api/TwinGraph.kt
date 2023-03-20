// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.twingraph.api

import com.redislabs.redisgraph.impl.api.RedisGraph
import java.util.Dictionary

class TwinGraph(private val graphId: String) : RedisGraph() {


  fun createTwins(twinsList: List<Dictionary<String, String>>) {
    for (args in twinsList) {
      graphQuery("CREATE (:${args["name"]} ${args["params"]})")
    }
  }

  fun createRelationships(relationshipList: List<Dictionary<String, String>>) {
    for (args in relationshipList) graphQuery(
      "MATCH (a),(b) WHERE a.id=${args["source"]} AND b.id=${args["target"]}" +
              "CREATE (a)-[:${args["name"]} ${args["params"]}-(b)")
  }

  fun getTwins(listId: List<String>) {
    for (arg in listId) {
      graphQuery("MATCH (t {id:$arg}) RETURN t")
    }
  }

  fun getRelationships(listId: List<String>) {
    for (arg in listId) {
      graphQuery("MATCH ()-[r {id:$arg}]-() RETURN r")
    }
  }

  fun updateTwins(twinsList: List<Dictionary<String, String>>) {
    for (args in twinsList) {
      graphQuery("MATCH (a) WHERE a.id=${args["name"]}" + "SET ${args["params"]}")
    }
  }

  fun updateRelationships(relationshipList: List<Dictionary<String, String>>) {
    for (args in relationshipList) {
      graphQuery("MATCH ()-[a]-() WHERE a.id=${args["name"]} SET ${args["params"]}")
    }
  }

  fun deleteTwins(twinsList: List<Dictionary<String, String>>){
    for (args in twinsList) {
      graphQuery("MATCH (a) WHERE a.id=${args["name"]} DELETE a")
    }
  }

  fun deleteRelationships(relationshipList: List<Dictionary<String, String>>){
      for (args in relationshipList) {
        graphQuery("MATCH ()-[a]-() WHERE a.id=${args["name"]} DELETE a")
      }
  }

  fun deleteGraph() {
    deleteGraph(graphId)
  }

  fun graphQuery(queryText: String) {
    query(graphId, queryText)
  }
}
