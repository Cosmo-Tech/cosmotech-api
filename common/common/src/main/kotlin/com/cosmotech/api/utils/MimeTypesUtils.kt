// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.utils

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import java.io.BufferedInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import org.apache.tika.config.TikaConfig
import org.apache.tika.metadata.Metadata
import org.apache.tika.metadata.TikaCoreProperties
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.Resource

class MimeTypesUtils {
  val logger: Logger = LoggerFactory.getLogger(this::class.java)

  @Autowired lateinit var csmPlatformProperties: CsmPlatformProperties
  public fun scanResource(file: Resource, authorizedMimeTypes: List<String>) {
    val tika = TikaConfig()
    val metadata = Metadata()
    metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, file.filename)
    val inputStream = file.getInputStream()
    var mimetype = tika.getDetector().detect(inputStream, metadata)
    val name = file.filename ?: "Unknown"
    this.validateMimeType(mimetype.toString(), name, authorizedMimeTypes)
    this.logger.info("Detected type for file {}: {}", file.filename, mimetype)
    if (mimetype.getSubtype().equals("zip")) {
      val zipIn = ZipInputStream(inputStream)
      this.recurseScanZipFile(tika, zipIn, name, authorizedMimeTypes)
    }
  }

  fun recurseScanZipFile(
      tika: TikaConfig,
      zipInputStream: ZipInputStream,
      fileName: String,
      authorizedMimeTypes: List<String>
  ) {
    this.logger.info("Scanning Zip file {}", fileName)
    var entry: ZipEntry? = null
    while ({
      entry = zipInputStream.getNextEntry()
      entry
    }() != null) {
      this.logger.debug("Zip entry {}", entry?.getName())
      if (entry?.isDirectory() ?: false) {
        this.logger.debug("Directory detected")
      } else {
        this.logger.debug("File detected")
        val bufferedStream = BufferedInputStream(zipInputStream)
        val metadata = Metadata()
        val name = entry?.getName() ?: "Unknown"
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, name)
        var mimetype = tika.getDetector().detect(bufferedStream, metadata)
        this.validateMimeType(mimetype.toString(), name, authorizedMimeTypes)
        this.logger.info("Detected type for file {}: {}", name, mimetype)
        if (mimetype.getSubtype().equals("zip")) {
          val zipIn = ZipInputStream(bufferedStream)
          this.recurseScanZipFile(tika, zipIn, name, authorizedMimeTypes)
        }
      }
    }

    this.logger.info("Zip file end {}", fileName)
  }

  fun validateMimeType(mimetype: String, fileName: String, authorizedMimeTypes: List<String>) {
    if (!authorizedMimeTypes.contains(mimetype)) {
      throw CsmAccessForbiddenException(
          "MIME type $mimetype for file $fileName is not authorized.")
    } else {
      this.logger.debug("Valid MIME type")
    }
  }
}
