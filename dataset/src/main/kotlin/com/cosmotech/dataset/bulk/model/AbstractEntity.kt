// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.bulk.model

import com.cosmotech.dataset.bulk.getPropertyBinaryBlobFormat
import com.cosmotech.dataset.bulk.toPropertyType

abstract class AbstractEntity {

  abstract val properties: Map<String, Any?>

  fun getPropertiesBinary(): List<ByteArray> {
    return properties.values.map { getPropertyBinaryBlobFormat(it.toPropertyType(), it) }
  }

  abstract fun toBinaryFormat(): ByteArray

  fun size(): Int {
    return toBinaryFormat().size
  }
}
