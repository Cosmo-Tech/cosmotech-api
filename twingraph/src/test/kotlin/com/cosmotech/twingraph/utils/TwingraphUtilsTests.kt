// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.twingraph.utils

import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlin.test.*
import kotlin.test.assertFalse

class TwingraphUtilsTests {

  @BeforeTest
  fun before() {
    mockkStatic(TwingraphUtils::class)
  }

  @AfterTest
  fun after() {
    unmockkStatic(TwingraphUtils::class)
  }

  @Test
  fun `checkReadOnlyQuery with simple create query`() {
    val simpleCreateQuery = "CREATE (n {name: 'john doe'})"
    assertFalse(TwingraphUtils.checkReadOnlyQuery(simpleCreateQuery))
  }

  @Test
  fun `checkReadOnlyQuery with complex create query`() {
    val complexCreateQuery =
        "MATCH (a:Person), (b:Person) " +
            "WHERE a.name = 'A' AND b.name = 'B' " +
            "CREATE (a)-[r:RELTYPE]->(b) " +
            "RETURN type(r)"
    assertFalse(TwingraphUtils.checkReadOnlyQuery(complexCreateQuery))
  }

  @Test
  fun `checkReadOnlyQuery with simple set query`() {
    val simpleQuery = "SET e.property1 = wonderfulPropertyValue"
    assertFalse(TwingraphUtils.checkReadOnlyQuery(simpleQuery))
  }

  @Test
  fun `checkReadOnlyQuery with complex set query`() {
    val complexQuery =
        "MATCH (at {name: 'Andy'}), (pn {name: 'Peter'}) " +
            "SET at = properties(pn) " +
            "RETURN at.name, at.age, at.hungry, pn.name, pn.age"
    assertFalse(TwingraphUtils.checkReadOnlyQuery(complexQuery))
  }

  @Test
  fun `checkReadOnlyQuery with simple merge query`() {
    val simpleQuery = "MERGE (robert:Critic) RETURN robert, labels(robert)"
    assertFalse(TwingraphUtils.checkReadOnlyQuery(simpleQuery))
  }

  @Test
  fun `checkReadOnlyQuery with complex merge query`() {
    val complexQuery =
        "MATCH (charlie:Person {name: 'Charlie Sheen'}), (wallStreet:Movie {title: 'Wall Street'}) " +
            "MERGE (charlie)-[r:ACTED_IN]->(wallStreet) " +
            "RETURN charlie.name, type(r), wallStreet.title"
    assertFalse(TwingraphUtils.checkReadOnlyQuery(complexQuery))
  }

  @Test
  fun `checkReadOnlyQuery with simple delete query`() {
    val simpleQuery = "MATCH (n:Person {name: 'UNKNOWN'}) DELETE n"
    assertFalse(TwingraphUtils.checkReadOnlyQuery(simpleQuery))
  }

  @Test
  fun `checkReadOnlyQuery with complex delete query`() {
    val complexQuery =
        "MATCH (n1:Person)-[r {id: 123}]->(n2:Person) " +
            "CALL { WITH n1 MATCH (n1)-[r1]-() RETURN count(r1) AS rels1 } " +
            "CALL { WITH n2 MATCH (n2)-[r2]-() RETURN count(r2) AS rels2 } " +
            "DELETE r " +
            "RETURN " +
            "  n1.name AS node1, rels1 - 1 AS relationships1, " +
            "  n2.name AS node2, rels2 - 1 AS relationships2"
    assertFalse(TwingraphUtils.checkReadOnlyQuery(complexQuery))
  }

  @Test
  fun `checkReadOnlyQuery with simple remove query`() {
    val simpleQuery = "MATCH (a {name: 'Andy'}) REMOVE a.age RETURN a.name, a.age"
    assertFalse(TwingraphUtils.checkReadOnlyQuery(simpleQuery))
  }

  @Test
  fun `checkReadOnlyQuery with complex remove query`() {
    val complexQuery = "MATCH (n {name: 'Peter'}) REMOVE n:German:Swedish RETURN n.name, labels(n)"
    assertFalse(TwingraphUtils.checkReadOnlyQuery(complexQuery))
  }

  @Test
  fun `checkReadOnlyQuery readonly query containing property names with forbidden vers `() {
    val queryWithCreatedProperty = "MATCH (n {created: 'yes'}) RETURN n.name"
    assertTrue(TwingraphUtils.checkReadOnlyQuery(queryWithCreatedProperty))

    val queryWithSetProperty = "MATCH (n {labelSet: 'no'}) RETURN n.name"
    assertTrue(TwingraphUtils.checkReadOnlyQuery(queryWithSetProperty))

    val queryWithMergeProperty = "MATCH (n {mergedFrom: 'yes'}) RETURN n.name"
    assertTrue(TwingraphUtils.checkReadOnlyQuery(queryWithMergeProperty))

    val queryWithDeleteProperty = "MATCH (n {hasbeendeleted: 'no'}) RETURN n.name"
    assertTrue(TwingraphUtils.checkReadOnlyQuery(queryWithDeleteProperty))

    val queryWithRemoveProperty = "MATCH (n {hasbeenremoved: 'yes'}) RETURN n.name"
    assertTrue(TwingraphUtils.checkReadOnlyQuery(queryWithRemoveProperty))
  }
}
