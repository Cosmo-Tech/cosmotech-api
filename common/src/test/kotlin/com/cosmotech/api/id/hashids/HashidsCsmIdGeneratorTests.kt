// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.id.hashids

import java.lang.IllegalArgumentException
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.assertThrows

class HashidsCsmIdGeneratorTests {

  private lateinit var hashidsCsmIdGenerator: HashidsCsmIdGenerator

  @BeforeTest
  fun beforeEachTest() {
    this.hashidsCsmIdGenerator = HashidsCsmIdGenerator()
  }

  @Test
  fun `generate with blank scope`() {
    assertThrows<IllegalArgumentException> { this.hashidsCsmIdGenerator.generate("") }
    assertThrows<IllegalArgumentException> { this.hashidsCsmIdGenerator.generate("   ") }
  }

  @Test
  fun `generate with no prepend prefix`() {
    val hashid = this.hashidsCsmIdGenerator.generate("fake_scope")
    assertFalse { hashid.isBlank() }
    assertTrue { hashid.startsWith("f_", ignoreCase = false) }
    assertFalse { hashid.substringAfter("f_", missingDelimiterValue = "").isBlank() }
  }

  @Test
  fun `generate with custom prepend prefix`() {
    val hashid =
        this.hashidsCsmIdGenerator.generate("test_scope", prependPrefix = "my-custom-prefix-")
    assertFalse { hashid.isBlank() }
    assertTrue { hashid.startsWith("my-custom-prefix-", ignoreCase = false) }
    assertFalse { hashid.substringAfter("my-custom-prefix-").isBlank() }
  }

  @Test
  fun `2 generations within a same scope do not return the same ID`() {
    val hashid1 = this.hashidsCsmIdGenerator.generate("test_scope")
    val hashid2 = this.hashidsCsmIdGenerator.generate("test_scope")
    assertFalse { hashid1.isBlank() }
    assertTrue { hashid1.startsWith("t_", ignoreCase = false) }
    assertFalse { hashid2.isBlank() }
    assertTrue { hashid2.startsWith("t_", ignoreCase = false) }
    assertNotEquals(hashid1, hashid2)
  }

  @Test
  fun `2 generations within different scopes do not return the same ID`() {
    val hashid1 = this.hashidsCsmIdGenerator.generate("test scope")
    val hashid2 = this.hashidsCsmIdGenerator.generate("another scope")
    assertFalse { hashid1.isBlank() }
    assertTrue { hashid1.startsWith("t_", ignoreCase = false) }
    assertFalse { hashid2.isBlank() }
    assertTrue { hashid2.startsWith("a_", ignoreCase = false) }
    assertNotEquals(hashid1, hashid2)
  }
}
