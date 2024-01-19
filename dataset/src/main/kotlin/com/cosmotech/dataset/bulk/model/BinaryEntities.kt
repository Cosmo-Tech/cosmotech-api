// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.bulk.model

import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.dataset.bulk.getHeaderBinaryBlobFormat

class BinaryEntities(val type: String, private val header: List<String>) {

  private val headerBinaryBlobFormat = getHeaderBinaryBlobFormat(type, header)
  var size: Int = headerBinaryBlobFormat.size
  var binaryBlobFormat: List<ByteArray> = mutableListOf()

  fun addEntity(vararg nodesArg: AbstractEntity) {
    nodesArg.forEach {
      if (it.properties.keys.any { s -> s !in header })
          throw CsmResourceNotFoundException(
              "Header mismatch, one or more properties are not in $header")
      // TODO: fix match header and properties size
      binaryBlobFormat += it.toBinaryFormat()
      size += it.toBinaryFormat().size
    }
  }

  fun toBinary(): ByteArray {
    var byteArray = byteArrayOf()
    byteArray += headerBinaryBlobFormat
    for (bytes in binaryBlobFormat) {
      byteArray += bytes
    }
    return byteArray
  }
}
