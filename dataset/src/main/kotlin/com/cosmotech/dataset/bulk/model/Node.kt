// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.bulk.model

import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.dataset.bulk.getNodeBinaryBlobFormat

class Node(override val properties: Map<String, String>) : AbstractEntity() {

  val id: String =
      properties["id"] ?: throw CsmResourceNotFoundException("Node property 'id' not found")

  override fun toBinaryFormat(): ByteArray {
    return getNodeBinaryBlobFormat(getPropertiesBinary())
  }
}
