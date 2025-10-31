// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.id

import java.lang.IllegalArgumentException
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CsmIdGeneratorTests {
  @Test
  fun `generate with blank scope`() {
    assertThrows<IllegalArgumentException> { generateId("") }
    assertThrows<IllegalArgumentException> { generateId("   ") }
  }

  @Test
  fun `generate with no prepend prefix`() {
    val hashid = generateId("fake_scope")
    assertFalse { hashid.isBlank() }
    assertTrue { hashid.startsWith("f-", ignoreCase = false) }
    assertFalse { hashid.substringAfter("f-", missingDelimiterValue = "").isBlank() }
  }

  @Test
  fun `generate with custom prepend prefix`() {
    val hashid = generateId("test_scope", prependPrefix = "my-custom-prefix-")
    assertFalse { hashid.isBlank() }
    assertTrue { hashid.startsWith("my-custom-prefix-", ignoreCase = false) }
    assertFalse { hashid.substringAfter("my-custom-prefix-").isBlank() }
  }

  @Test
  fun `2 generations within a same scope do not return the same ID`() {
    val hashid1 = generateId("test_scope")
    val hashid2 = generateId("test_scope")
    assertFalse { hashid1.isBlank() }
    assertTrue { hashid1.startsWith("t-", ignoreCase = false) }
    assertFalse { hashid2.isBlank() }
    assertTrue { hashid2.startsWith("t-", ignoreCase = false) }
    assertNotEquals(hashid1, hashid2)
  }

  @Test
  fun `2 generations within different scopes do not return the same ID`() {
    val hashid1 = generateId("test scope")
    val hashid2 = generateId("another scope")
    assertFalse { hashid1.isBlank() }
    assertTrue { hashid1.startsWith("t-", ignoreCase = false) }
    assertFalse { hashid2.isBlank() }
    assertTrue { hashid2.startsWith("a-", ignoreCase = false) }
    assertNotEquals(hashid1, hashid2)
  }
}
