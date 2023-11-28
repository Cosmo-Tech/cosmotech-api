// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.twingraph.utils

import com.cosmotech.twingraph.extension.toJsonString
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import redis.clients.jedis.graph.ResultSet

class TwingraphExtTests {

  @Test
  fun `ResultSet to json on null values`() {
    val resultSet = mockk<ResultSet>()
    val record1 = mockk<redis.clients.jedis.graph.Record>()
    val record2 = mockk<redis.clients.jedis.graph.Record>()

    every { resultSet.iterator() } returns arrayListOf(record1, record2).listIterator()
    every { record1.keys() } returns listOf("name", "value1", "value2")
    every { record1.values() } returns listOf("a", "b", null)

    every { record2.keys() } returns listOf("name", "value1", "value2")
    every { record2.values() } returns listOf("c", null, "d")

    val jsonResult = resultSet.toJsonString()

    assert(jsonResult == "[{\"name\":\"a\",\"value1\":\"b\"},{\"name\":\"c\",\"value2\":\"d\"}]")
  }
}
