// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.bulk.model

import com.cosmotech.dataset.bulk.getEdgeBinaryBlobFormat

class Edge(
    private val source: Long,
    private val target: Long,
    override val properties: Map<String, Any?>
) : AbstractEntity() {
  override fun toBinaryFormat(): ByteArray {
    return getEdgeBinaryBlobFormat(source, target, getPropertiesBinary())
  }
}
