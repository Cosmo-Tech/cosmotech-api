// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.utils

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Unzip a zip file and return a list of files with their content
 *
 * @param file The zip file to unzip
 * @param prefixNames The prefix names of the files or folders to unzip
 * @param fileExtension The file extension of the files to unzip
 */
fun unzip(file: InputStream, prefixNames: List<String>, fileExtension: String): List<UnzippedFile> =
    ZipInputStream(file).use { zipInputStream ->
      generateSequence { zipInputStream.nextEntry }
          .filterNot { it.isDirectory }
          .filter { prefixNames.any { prefix -> it.name.contains(prefix, true) } }
          .filter { it.name.endsWith(fileExtension, true) }
          .map {
            UnzippedFile(
                filename = it.name.extractFileNameFromPath(),
                prefix = prefixNames.first { prefix -> it.name.contains(prefix, true) },
                content = zipInputStream.readAllBytes())
          }
          .toList()
    }

/** Data class representing a file name and its content */
data class UnzippedFile(val filename: String, val prefix: String, val content: ByteArray)

/**
 * Zip a map of file names and byte arrays into a single byte array
 *
 * @param fileNameByteArray The map of file names and byte arrays to zip
 */
fun zipBytesWithFileNames(fileNameByteArray: Map<String, ByteArray>): ByteArray? {
  if (fileNameByteArray.isEmpty()) return null
  val byteArrayOutputStream = ByteArrayOutputStream()
  val zipOutputStream = ZipOutputStream(byteArrayOutputStream)
  for (file in fileNameByteArray) {
    val entry = ZipEntry(file.key).apply { size = file.value.size.toLong() }
    zipOutputStream.putNextEntry(entry)
    zipOutputStream.write(file.value)
  }
  zipOutputStream.closeEntry()
  zipOutputStream.close()
  return byteArrayOutputStream.toByteArray()
}
