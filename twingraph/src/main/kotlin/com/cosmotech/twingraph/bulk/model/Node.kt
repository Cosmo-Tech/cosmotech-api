// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.twingraph.bulk.model

import com.cosmotech.twingraph.bulk.getNodeBinaryBlobFormat

class Node(override val properties: Map<String, String>) : AbstractEntity() {

  val id: String = (properties["id"] ?: properties.entries.iterator().next().value)

  override fun toBinaryFormat(): ByteArray {
    return getNodeBinaryBlobFormat(getPropertiesBinary())
  }
}
