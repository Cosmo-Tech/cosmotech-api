// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.utils

import java.io.ByteArrayInputStream
import java.io.File
import java.util.*
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class ZipUtilsTests {

  @Test
  fun `zipBytesWithFileNames - should check empty List`() {
    val actual = zipBytesWithFileNames(emptyMap())
    assertEquals(null, actual)
  }

  @Test
  fun `zipBytesWithFileNames - should check non-empty List`() {
    val actual = zipBytesWithFileNames(mapOf("test.json" to ByteArray(10)))
    ByteArrayInputStream(actual).use {
      val zipInputStream = java.util.zip.ZipInputStream(it)
      val entry = zipInputStream.nextEntry
      assertEquals("test.json", entry.name)
      assertEquals(ByteArray(10).size, zipInputStream.readAllBytes().size)
    }
  }

  @Test
  fun `unzip - should check empty List`() {
    val actual = unzip(ByteArrayInputStream(ByteArray(10)), listOf("test"), ".json")
    assertEquals(emptyList(), actual)
  }

  @Test
  fun `unzip - should check non-empty List`() {
    val fileName = this::class.java.getResource("/test.zip")?.file!!
    val file = File(fileName)
    val actual = unzip(file.inputStream(), listOf("nodes", "edges"), ".csv")
    assertEquals(3, actual.size)
  }
}
