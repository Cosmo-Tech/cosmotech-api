// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.utils

import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory

class TwingraphUtilsTests {

  @BeforeTest
  fun before() {
    mockkStatic(TwingraphUtils::class)
  }

  @AfterTest
  fun after() {
    unmockkStatic(TwingraphUtils::class)
  }

  @TestFactory
  fun `given a query, when we check the query syntax, then return if the query is readonly`() =
      listOf(
              "CREATE (n {name: 'john doe'})" to false,
              "MATCH (a:Person), (b:Person) WHERE a.name = 'A' AND b.name = 'B' " +
                  "CREATE (a)-[r:RELTYPE]->(b) RETURN type(r)" to false,
              "SET e.property1 = wonderfulPropertyValue" to false,
              "MATCH (at {name: 'Andy'}), (pn {name: 'Peter'}) SET at = properties(pn) " +
                  "RETURN at.name, at.age, at.hungry, pn.name, pn.age" to false,
              "MERGE (robert:Critic) RETURN robert, labels(robert)" to false,
              "MATCH (charlie:Person {name: 'Charlie Sheen'}), (wallStreet:Movie {title: 'Wall Street'}) " +
                  "MERGE (charlie)-[r:ACTED_IN]->(wallStreet) RETURN charlie.name, type(r), wallStreet.title" to
                  false,
              "MATCH (n:Person {name: 'UNKNOWN'}) DELETE n" to false,
              "MATCH (n1:Person)-[r {id: 123}]->(n2:Person) " +
                  "CALL { WITH n1 MATCH (n1)-[r1]-() RETURN count(r1) AS rels1 } " +
                  "CALL { WITH n2 MATCH (n2)-[r2]-() RETURN count(r2) AS rels2 } " +
                  "DELETE r " +
                  "RETURN n1.name AS node1, " +
                  "rels1 - 1 AS relationships1, " +
                  "n2.name AS node2, " +
                  "rels2 - 1 AS relationships2" to false,
              "MATCH (a {name: 'Andy'}) REMOVE a.age RETURN a.name, a.age" to false,
              "MATCH (n {name: 'Peter'}) REMOVE n:German:Swedish RETURN n.name, labels(n)" to false,
              "MATCH (n {created: 'yes'}) RETURN n.name" to true,
              "MATCH (n {labelSet: 'no'}) RETURN n.name" to true,
              "MATCH (n {mergedFrom: 'yes'}) RETURN n.name" to true,
              "MATCH (n {hasbeendeleted: 'no'}) RETURN n.name" to true,
              "MATCH (n {hasbeenremoved: 'yes'}) RETURN n.name" to true,
          )
          .map { (query, expectedResult) ->
            dynamicTest(
                "given \"$query\", when readonly checks then result should be \"$expectedResult\"") {
                  assertEquals(expectedResult, TwingraphUtils.isReadOnlyQuery(query))
                }
          }
}
