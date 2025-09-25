// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.id.hashids

import java.lang.IllegalArgumentException
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.hashids.Hashids
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertThrows

private typealias Tester = (hashidGenerator: HashidsCsmIdGenerator) -> Unit

class HashidsCsmIdGeneratorTests {

  private fun performTestWithDifferentHashidGenerators(tester: Tester) =
      mapOf(
              "default with current time in nanos" to HashidsCsmIdGenerator(),
              "PROD-8703: number higher than the max allowed" to
                  HashidsCsmIdGenerator { (Hashids.MAX_NUMBER + 100).toULong() })
          .map { (purpose, idGenerator) -> dynamicTest(purpose) { tester(idGenerator) } }

  @TestFactory
  fun `generate with blank scope`() = performTestWithDifferentHashidGenerators {
    assertThrows<IllegalArgumentException> { it.generate("") }
    assertThrows<IllegalArgumentException> { it.generate("   ") }
  }

  @TestFactory
  fun `generate with no prepend prefix`() = performTestWithDifferentHashidGenerators {
    val hashid = it.generate("fake_scope")
    assertFalse { hashid.isBlank() }
    assertTrue { hashid.startsWith("f-", ignoreCase = false) }
    assertFalse { hashid.substringAfter("f-", missingDelimiterValue = "").isBlank() }
  }

  @TestFactory
  fun `generate with custom prepend prefix`() = performTestWithDifferentHashidGenerators {
    val hashid = it.generate("test_scope", prependPrefix = "my-custom-prefix-")
    assertFalse { hashid.isBlank() }
    assertTrue { hashid.startsWith("my-custom-prefix-", ignoreCase = false) }
    assertFalse { hashid.substringAfter("my-custom-prefix-").isBlank() }
  }

  @TestFactory
  fun `2 generations within a same scope do not return the same ID`() =
      performTestWithDifferentHashidGenerators {
        val hashid1 = it.generate("test_scope")
        val hashid2 = it.generate("test_scope")
        assertFalse { hashid1.isBlank() }
        assertTrue { hashid1.startsWith("t-", ignoreCase = false) }
        assertFalse { hashid2.isBlank() }
        assertTrue { hashid2.startsWith("t-", ignoreCase = false) }
        assertNotEquals(hashid1, hashid2)
      }

  @TestFactory
  fun `2 generations within different scopes do not return the same ID`() =
      performTestWithDifferentHashidGenerators {
        val hashid1 = it.generate("test scope")
        val hashid2 = it.generate("another scope")
        assertFalse { hashid1.isBlank() }
        assertTrue { hashid1.startsWith("t-", ignoreCase = false) }
        assertFalse { hashid2.isBlank() }
        assertTrue { hashid2.startsWith("a-", ignoreCase = false) }
        assertNotEquals(hashid1, hashid2)
      }
}
