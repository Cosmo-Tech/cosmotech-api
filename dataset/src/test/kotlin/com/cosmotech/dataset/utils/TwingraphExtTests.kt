// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.utils

import com.redislabs.redisgraph.ResultSet
import com.redislabs.redisgraph.impl.resultset.RecordImpl
import io.mockk.every
import io.mockk.spyk
import kotlin.test.Test

class TwingraphExtTests {
  @Test
  fun `ResultSet to json on null values`() {
    val resultSet = spyk<ResultSet>()
    val rec1 = RecordImpl(listOf("name", "value1", "value2"), listOf("a", "b", null))
    val rec2 = RecordImpl(listOf("name", "value1", "value2"), listOf("c", null, "d"))
    every { resultSet.iterator() } returns mutableListOf(rec1, rec2).iterator()

    val jsonResult = resultSet.toJsonString()

    assert(jsonResult == "[{\"name\":\"a\",\"value1\":\"b\"},{\"name\":\"c\",\"value2\":\"d\"}]")
  }
}
