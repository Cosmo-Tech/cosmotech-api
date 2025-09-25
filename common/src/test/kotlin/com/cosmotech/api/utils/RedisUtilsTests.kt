// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.utils

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import org.springframework.data.domain.PageRequest

const val DEFAULT_MAX_PAGE_SIZE = 100

class RedisUtilsTests {

  @Test
  fun `constructPageRequest - should check multiple cases for page and size`() {
    listOf(
            Pair(1, 10) to PageRequest.of(1, 10),
            Pair(null, 10) to PageRequest.of(0, 10),
            Pair(1, null) to PageRequest.of(1, DEFAULT_MAX_PAGE_SIZE),
            Pair(null, null) to null)
        .forEach { (input, expected) ->
          val actual = constructPageRequest(input.first, input.second, DEFAULT_MAX_PAGE_SIZE)
          assertEquals(expected, actual)
        }
  }

  @Test
  fun `findAllPaginated - should check empty List`() {
    val maxResult = 100
    val findAllLambda = { _: PageRequest -> mutableListOf<Any>() }
    val actual = findAllPaginated(maxResult, findAllLambda)
    assertEquals(0, actual.size)
  }

  @Test
  fun `findAllPaginated - should check non-empty List`() {
    var counter = 3
    val findAllLambda = { _: PageRequest ->
      if (counter-- == 0) mutableListOf() else mutableListOf(Any(), Any(), Any(), Any(), Any())
    }
    listOf(5, 10, 15, 20, 50).forEach { maxResult ->
      counter = 3
      val actual = findAllPaginated(maxResult, findAllLambda)
      assertEquals(15, actual.size)
    }
  }

  @Test
  fun `getDateNow - should check time pattern`() {
    val formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")
    assertEquals(LocalDate.now().format(formatter), getLocalDateNow(("yyyy/MM/dd")))
  }
}
