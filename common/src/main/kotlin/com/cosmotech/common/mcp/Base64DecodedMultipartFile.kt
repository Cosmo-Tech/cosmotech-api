// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.mcp

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.Base64
import org.springframework.web.multipart.MultipartFile

const val MAX_FILE_SIZE_MB = 10
const val MAX_FILE_SIZE_BYTES = MAX_FILE_SIZE_MB * 1024 * 1024

/** In-memory [MultipartFile] so MCP uploads go through the same validation as REST uploads. */
class Base64DecodedMultipartFile(
    private val fileName: String,
    private val content: ByteArray,
) : MultipartFile {

  companion object {
    fun of(fileName: String, base64Content: String): Base64DecodedMultipartFile {
      val content =
          try {
            Base64.getDecoder().decode(base64Content)
          } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException(
                "File '$fileName' is not a valid base64 string: ${e.message}",
                e,
            )
          }
      return Base64DecodedMultipartFile(fileName, content)
    }
  }

  override fun getName() = "file"

  override fun getOriginalFilename() = fileName

  override fun getContentType(): String? = null

  override fun isEmpty() = content.isEmpty()

  override fun getSize() = content.size.toLong()

  override fun getBytes() = content

  override fun getInputStream(): InputStream = ByteArrayInputStream(content)

  override fun transferTo(dest: File) = dest.writeBytes(content)
}

fun requireMaxFileSize(files: List<MultipartFile>) {
  require(files.sumOf { it.size } <= MAX_FILE_SIZE_BYTES) {
    "Files exceed the maximum total size of $MAX_FILE_SIZE_MB MiB ($MAX_FILE_SIZE_BYTES bytes)"
  }
}
