// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.id.uuid

import com.cosmotech.common.id.AbstractCsmIdGenerator
import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["csm.platform.id-generator.type"], havingValue = "uuid")
internal class UUIDCsmIdGenerator : AbstractCsmIdGenerator() {

  override fun buildId(scope: String) = UUID.randomUUID().toString()
}
