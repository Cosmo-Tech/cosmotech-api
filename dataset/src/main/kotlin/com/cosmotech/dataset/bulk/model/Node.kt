// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.bulk.model

import com.cosmotech.dataset.bulk.getNodeBinaryBlobFormat

class Node(val id: String, override val properties: Map<String, Any?>) : AbstractEntity() {

  override fun toBinaryFormat(): ByteArray {
    return getNodeBinaryBlobFormat(getPropertiesBinary())
  }
}
