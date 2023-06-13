// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.twingraph.bulk.model

import com.cosmotech.twingraph.bulk.getEdgeBinaryBlobFormat

class Edge(
    private val source: Long,
    private val target: Long,
    override val properties: Map<String, String>
) : AbstractEntity() {
  override fun toBinaryFormat(): ByteArray {
    return getEdgeBinaryBlobFormat(source, target, getPropertiesBinary())
  }
}
