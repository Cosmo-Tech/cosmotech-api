// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.utils

import com.cosmotech.common.exceptions.CsmAccessForbiddenException
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private const val CSV_MIME_TYPE = "text/csv"
private const val PLAIN_TEXT_MIME_TYPE = "text/plain"
private const val ZIP_MIME_TYPE = "application/zip"
private const val SHELL_SCRIPT_MIME_TYPE = "application/x-sh"

private val CSV_CONTENT = "id,name\n1,Cosmo\n2,Tech\n".toByteArray()
private val SHELL_SCRIPT_CONTENT = "#!/bin/bash\nrm -rf /\n".toByteArray()

class ResourceScannerTests {

  private val resourceScanner = ResourceScanner()

  @Test
  fun `scanMimeTypes - should accept a valid csv file`() {
    resourceScanner.scanMimeTypes(
        "nodes.csv",
        ByteArrayInputStream(CSV_CONTENT),
        listOf(CSV_MIME_TYPE),
    )
  }

  @Test
  fun `scanMimeTypes - should accept a zip archive with only authorized entries`() {
    resourceScanner.scanMimeTypes(
        "archive.zip",
        zipStream(mapOf("nodes.csv" to CSV_CONTENT, "edges.csv" to CSV_CONTENT)),
        listOf(ZIP_MIME_TYPE, CSV_MIME_TYPE),
    )
  }

  @Test
  fun `scanMimeTypes - should accept an empty file when its detected type is authorized`() {
    // An empty file carries no magic bytes: Tika falls back to file name based detection
    resourceScanner.scanMimeTypes(
        "empty.csv",
        ByteArrayInputStream(ByteArray(0)),
        listOf(CSV_MIME_TYPE),
    )
  }

  @Test
  fun `scanMimeTypes - should reject an empty file when its detected type is not authorized`() {
    val exception =
        assertFailsWith<CsmAccessForbiddenException> {
          resourceScanner.scanMimeTypes(
              "empty.csv",
              ByteArrayInputStream(ByteArray(0)),
              listOf("image/png"),
          )
        }
    assertEquals(
        "MIME type $CSV_MIME_TYPE for file empty.csv is not authorized.",
        exception.message,
    )
  }

  @Test
  fun `scanMimeTypes - should reject an empty file without extension`() {
    val exception =
        assertFailsWith<CsmAccessForbiddenException> {
          resourceScanner.scanMimeTypes(
              "empty",
              ByteArrayInputStream(ByteArray(0)),
              listOf(CSV_MIME_TYPE),
          )
        }
    assertEquals(
        "MIME type application/octet-stream for file empty is not authorized.",
        exception.message,
    )
  }

  @Test
  fun `scanMimeTypes - should reject a file whose mime type is not authorized`() {
    val exception =
        assertFailsWith<CsmAccessForbiddenException> {
          resourceScanner.scanMimeTypes(
              "script.sh",
              ByteArrayInputStream(SHELL_SCRIPT_CONTENT),
              listOf(CSV_MIME_TYPE),
          )
        }
    assertEquals(
        "MIME type $SHELL_SCRIPT_MIME_TYPE for file script.sh is not authorized.",
        exception.message,
    )
  }

  @Test
  fun `scanMimeTypes - should reject any file when no mime type is authorized`() {
    assertFailsWith<CsmAccessForbiddenException> {
      resourceScanner.scanMimeTypes("nodes.csv", ByteArrayInputStream(CSV_CONTENT), emptyList())
    }
  }

  @Test
  fun `scanMimeTypes - should use the default file name when none is provided`() {
    val exception =
        assertFailsWith<CsmAccessForbiddenException> {
          resourceScanner.scanMimeTypes(
              inputStream = ByteArrayInputStream(CSV_CONTENT),
              authorizedMimeTypes = emptyList(),
          )
        }
    assertEquals(
        "MIME type $PLAIN_TEXT_MIME_TYPE for file Unknown is not authorized.",
        exception.message,
    )
  }

  @Test
  fun `scanMimeTypes - should reject a binary content hidden behind a csv extension`() {
    val elfHeader = byteArrayOf(0x7F, 0x45, 0x4C, 0x46) + ByteArray(64)
    val exception =
        assertFailsWith<CsmAccessForbiddenException> {
          resourceScanner.scanMimeTypes(
              "nodes.csv",
              ByteArrayInputStream(elfHeader),
              listOf(CSV_MIME_TYPE, PLAIN_TEXT_MIME_TYPE),
          )
        }
    assertEquals(
        "MIME type application/x-elf for file nodes.csv is not authorized.",
        exception.message,
    )
  }

  @Test
  fun `scanMimeTypes - should reject a zip archive hidden behind a csv extension`() {
    val exception =
        assertFailsWith<CsmAccessForbiddenException> {
          resourceScanner.scanMimeTypes(
              "nodes.csv",
              zipStream(mapOf("nodes.csv" to CSV_CONTENT)),
              listOf(CSV_MIME_TYPE, PLAIN_TEXT_MIME_TYPE),
          )
        }
    assertEquals(
        "MIME type $ZIP_MIME_TYPE for file nodes.csv is not authorized.",
        exception.message,
    )
  }

  @Test
  fun `scanMimeTypes - should reject a zip archive containing an unauthorized entry`() {
    val exception =
        assertFailsWith<CsmAccessForbiddenException> {
          resourceScanner.scanMimeTypes(
              "archive.zip",
              zipStream(mapOf("nodes.csv" to CSV_CONTENT, "malicious.sh" to SHELL_SCRIPT_CONTENT)),
              listOf(ZIP_MIME_TYPE, CSV_MIME_TYPE),
          )
        }
    assertEquals(
        "MIME type $SHELL_SCRIPT_MIME_TYPE for file malicious.sh is not authorized.",
        exception.message,
    )
  }

  @Test
  fun `scanMimeTypes - should reject a zip entry whose extension hides a shell script`() {
    val exception =
        assertFailsWith<CsmAccessForbiddenException> {
          resourceScanner.scanMimeTypes(
              "archive.zip",
              zipStream(mapOf("nodes.csv" to SHELL_SCRIPT_CONTENT)),
              listOf(ZIP_MIME_TYPE, CSV_MIME_TYPE, PLAIN_TEXT_MIME_TYPE),
          )
        }
    assertEquals(
        "MIME type $SHELL_SCRIPT_MIME_TYPE for file nodes.csv is not authorized.",
        exception.message,
    )
  }

  private fun zipStream(entries: Map<String, ByteArray>) =
      ByteArrayInputStream(zipBytesWithFileNames(entries)!!)
}
