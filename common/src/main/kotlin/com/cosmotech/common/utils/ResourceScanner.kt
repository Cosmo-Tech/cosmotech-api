// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.utils

import com.cosmotech.common.exceptions.CsmAccessForbiddenException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import org.apache.commons.io.input.CloseShieldInputStream
import org.apache.tika.config.loader.TikaLoader
import org.apache.tika.io.TikaInputStream
import org.apache.tika.metadata.Metadata
import org.apache.tika.metadata.TikaCoreProperties
import org.apache.tika.parser.ParseContext
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
      authorizedMimeTypes: List<String>,
  ) {
    val tika = TikaLoader.loadDefault()
    // Detection consumes the stream, so it must be wrapped in a rewindable TikaInputStream in
    // order to be readable again when scanning the entries of an archive
    // See
    // https://tika.apache.org/docs/4.0.x/migration-to-4x/migrating-to-4x.html#tika-input-stream-spi
    TikaInputStream.get(inputStream).use {
      this.scanStream(tika, it, fileName, authorizedMimeTypes)
    }
  }

  private fun scanStream(
      tika: TikaLoader,
      inputStream: TikaInputStream,
      name: String,
      authorizedMimeTypes: List<String>,
  ) {
    val metadata = Metadata()
    metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, name)
    val parseContext = ParseContext()
    val mimetype = tika.loadDetectors().detect(inputStream, metadata, parseContext)
    this.validateMimeType(mimetype.toString(), name, authorizedMimeTypes)
    this.logger.info("Detected type for file $name: $mimetype")
    if (mimetype.subtype == ZIP_MIME_TYPE) {
      val zipIn = ZipInputStream(inputStream)
      this.recurseScanZipFile(tika, zipIn, name, authorizedMimeTypes)
    }
  }

  private fun recurseScanZipFile(
      tika: TikaLoader,
      zipInputStream: ZipInputStream,
      fileName: String,
      authorizedMimeTypes: List<String>,
  ) {
    this.logger.info("Scanning Zip file $fileName")
    var entry: ZipEntry?
    while (
        run {
          entry = zipInputStream.nextEntry
          entry
        } != null
    ) {
      this.logger.debug("Zip entry ${entry?.name}")
      if (entry?.isDirectory == true) {
        this.logger.debug("Directory detected")
      } else {
        this.logger.debug("File detected")
        val name = entry?.name ?: ENTRY_NAME_UNKNOWN
        // Shielded so that closing the wrapper does not close the zip stream being iterated over
        // We do not want to close the whole stream after reading the first entry in the archive
        // See
        // https://commons.apache.org/proper/commons-io/apidocs/org/apache/commons/io/input/CloseShieldInputStream.html
        TikaInputStream.get(CloseShieldInputStream.wrap(zipInputStream)).use {
          this.scanStream(tika, it, name, authorizedMimeTypes)
        }
      }
    }

    this.logger.info("Zip file end $fileName")
  }

  private fun validateMimeType(
      mimetype: String,
      fileName: String,
      authorizedMimeTypes: List<String>,
  ) {
    if (!authorizedMimeTypes.contains(mimetype)) {
      throw CsmAccessForbiddenException("MIME type $mimetype for file $fileName is not authorized.")
    } else {
      this.logger.debug("Valid MIME type $mimetype for file $fileName")
    }
  }
}
