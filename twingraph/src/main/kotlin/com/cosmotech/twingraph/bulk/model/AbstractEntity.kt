// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.twingraph.bulk.model

import com.cosmotech.twingraph.bulk.getPropertyBinaryBlobFormat
import com.cosmotech.twingraph.bulk.toPropertyType

abstract class AbstractEntity {

  abstract val properties: Map<String, String>

  fun getPropertiesBinary(): List<ByteArray> {
    return properties.values.map { getPropertyBinaryBlobFormat(it.toPropertyType(), it) }
  }

  abstract fun toBinaryFormat(): ByteArray

  fun size(): Int {
    return toBinaryFormat().size
  }
}
