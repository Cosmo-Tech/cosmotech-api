// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.utils

import com.cosmotech.common.exceptions.CsmAccessForbiddenException
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import org.apache.tika.config.TikaConfig
import org.apache.tika.metadata.Metadata
import org.apache.tika.metadata.TikaCoreProperties
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

private const val ZIP_MIME_TYPE = "zip"
private const val ENTRY_NAME_UNKNOWN = "Unknown"

@Component
class ResourceScanner {
  private val logger: Logger = LoggerFactory.getLogger(this::class.java)

  fun scanMimeTypes(
      fileName: String = ENTRY_NAME_UNKNOWN,
      inputStream: InputStream,
      authorizedMimeTypes: List<String>
  ) {
    val tika = TikaConfig()
    this.scanStream(tika, inputStream, fileName, authorizedMimeTypes)
  }

  private fun scanStream(
      tika: TikaConfig,
      inputStream: InputStream,
      name: String,
      authorizedMimeTypes: List<String>
  ) {
    val metadata = Metadata()
    metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, name)
    var mimetype = tika.detector.detect(inputStream, metadata)
    this.validateMimeType(mimetype.toString(), name, authorizedMimeTypes)
    this.logger.info("Detected type for file $name: $mimetype")
    if (mimetype.subtype == ZIP_MIME_TYPE) {
      val zipIn = ZipInputStream(inputStream)
      this.recurseScanZipFile(tika, zipIn, name, authorizedMimeTypes)
    }
  }

  private fun recurseScanZipFile(
      tika: TikaConfig,
      zipInputStream: ZipInputStream,
      fileName: String,
      authorizedMimeTypes: List<String>
  ) {
    this.logger.info("Scanning Zip file $fileName")
    var entry: ZipEntry?
    while (run {
      entry = zipInputStream.nextEntry
      entry
    } != null) {
      this.logger.debug("Zip entry ${entry?.name}")
      if (entry?.isDirectory == true) {
        this.logger.debug("Directory detected")
      } else {
        this.logger.debug("File detected")
        val bufferedStream = BufferedInputStream(zipInputStream)
        val name = entry?.name ?: ENTRY_NAME_UNKNOWN
        this.scanStream(tika, bufferedStream, name, authorizedMimeTypes)
      }
    }

    this.logger.info("Zip file end $fileName")
  }

  private fun validateMimeType(
      mimetype: String,
      fileName: String,
      authorizedMimeTypes: List<String>
  ) {
    if (!authorizedMimeTypes.contains(mimetype)) {
      throw CsmAccessForbiddenException("MIME type $mimetype for file $fileName is not authorized.")
    } else {
      this.logger.debug("Valid MIME type $mimetype for file $fileName")
    }
  }
}
