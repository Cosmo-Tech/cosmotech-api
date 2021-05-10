// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.id.uuid

import java.lang.IllegalArgumentException
import java.util.*
import kotlin.test.*
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class UUIDCsmIdGeneratorTests {

  private lateinit var uuidCsmIdGenerator: UUIDCsmIdGenerator

  @BeforeTest
  fun beforeEachTest() {
    this.uuidCsmIdGenerator = UUIDCsmIdGenerator()
  }

  @Test
  fun `generate with blank scope`() {
    assertThrows<IllegalArgumentException> { this.uuidCsmIdGenerator.generate("") }
    assertThrows<IllegalArgumentException> { this.uuidCsmIdGenerator.generate("    ") }
  }

  @Test
  fun `generate with no prepend prefix`() {
    val uuidId = this.uuidCsmIdGenerator.generate("test_scope")
    assertFalse { uuidId.isBlank() }
    assertTrue { uuidId.startsWith("T-", ignoreCase = false) }
    assertDoesNotThrow { UUID.fromString(uuidId.substringAfter("T-")) }
  }

  @Test
  fun `generate with custom prepend prefix`() {
    val uuidId = this.uuidCsmIdGenerator.generate("test_scope", prependPrefix = "my-custom-prefix-")
    assertFalse { uuidId.isBlank() }
    assertTrue { uuidId.startsWith("my-custom-prefix-", ignoreCase = false) }
    assertDoesNotThrow { UUID.fromString(uuidId.substringAfter("my-custom-prefix-")) }
  }

  @Test
  fun `2 generations within a same scope do not return the same ID`() {
    val uuidId1 = this.uuidCsmIdGenerator.generate("test_scope")
    val uuidId2 = this.uuidCsmIdGenerator.generate("test_scope")
    assertFalse { uuidId1.isBlank() }
    assertTrue { uuidId1.startsWith("T-", ignoreCase = false) }
    assertFalse { uuidId2.isBlank() }
    assertTrue { uuidId2.startsWith("T-", ignoreCase = false) }
    assertTrue { uuidId2 != uuidId1 }
  }

  @Test
  fun `2 generations within different scopes do not return the same ID`() {
    val uuidId1 = this.uuidCsmIdGenerator.generate("test scope")
    val uuidId2 = this.uuidCsmIdGenerator.generate("another scope")
    assertFalse { uuidId1.isBlank() }
    assertTrue { uuidId1.startsWith("T-", ignoreCase = false) }
    assertFalse { uuidId2.isBlank() }
    assertTrue { uuidId2.startsWith("A-", ignoreCase = false) }
    assertTrue { uuidId2 != uuidId1 }
  }
}
