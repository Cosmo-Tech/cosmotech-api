// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class StringExtensionsTests {

  @Test
  fun `sanitizeForKubernetes should replace special characters with '-' and takeLast`() {
    val input = "ALL.my/namespace:with-SPECIAL_chars"
    val expected = "all-my-namespace-with-special-chars"
    var actual = input.sanitizeForKubernetes()
    assertEquals(expected, actual)
    actual = input.sanitizeForKubernetes(13)
    assertEquals("special-chars", actual)
  }

  @Test
  fun `sanitizeForRedis should escape special characters`() {
    val input = "te.st@cosmo-tech.com"
    val expected = "te\\\\.st\\\\@cosmo\\\\-tech\\\\.com"
    val actual = input.sanitizeForRedis()
    assertEquals(expected, actual)
  }

  @Test
  fun `shaHash should return a SHA-256 hash of the input string`() {
    val input = "test"
    val expected = "9F86D081884C7D659A2FEAA0C55AD015A3BF4F1B2B0B822CD15D6C15B0F00A08"
    val actual = input.shaHash()
    assertEquals(expected, actual)
  }

  @Test
  fun `redisMetadataKey should return the metadata key for the given key`() {
    val input = "test"
    val expected = "testMetaData"
    val actual = input.toRedisMetaDataKey()
    assertEquals(expected, actual)
  }

  @Test
  fun `should format Cypher Query with variables`() {
    val map =
        mapOf(
            "name" to "Joe Doe",
            "id" to "100",
            "object" to "{id:\"id\"}",
            "array" to "{id:['one', 'two']}")
    val actual = "CREATE (:Person {name: \$name, id: \$id, object: \$object, array: \$array)"
    val expected =
        "CREATE (:Person {name: \"Joe Doe\", id: \"100\", object: \"{id:\\\"id\\\"}\", " +
            "array: \"{id:['one', 'two']}\")"
    assertEquals(expected, actual.formatQuery(map))
  }

  @Test
  fun `should format Cypher Query & replace empty value with null`() {
    val map = mapOf("name" to "Joe Doe", "id" to " ")
    val actual = "CREATE (:Person {name: \$name, id: \$id})"
    val expected = "CREATE (:Person {name: \"Joe Doe\", id: null})"
    assertEquals(expected, actual.formatQuery(map))
  }

  @Test
  fun `should cut file name from path`() {
    val input = "/path/to/file.txt"
    val expected = "file"
    val actual = input.extractFileNameFromPath()
    assertEquals(expected, actual)
  }

  @Test
  fun `should cut file name from file without extension`() {
    val input = "file"
    val expected = "file"
    val actual = input.extractFileNameFromPath()
    assertEquals(expected, actual)
  }
}
