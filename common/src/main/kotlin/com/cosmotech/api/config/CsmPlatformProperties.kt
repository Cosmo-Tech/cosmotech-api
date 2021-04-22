// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.config

import com.azure.cosmos.ConnectionMode
import com.azure.cosmos.ConsistencyLevel
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "csm.platform")
data class CsmPlatformProperties(val vendor: Vendor, val azure: CsmPlatformAzure?) {

  data class CsmPlatformAzure(val cosmos: CsmPlatformAzureCosmos) {
    data class CsmPlatformAzureCosmos(
        val uri: String,
        val key: String,
        val consistencyLevel: ConsistencyLevel?,
        val populateQueryMetrics: Boolean,
        val allowTelemetry: Boolean,
        val connectionMode: ConnectionMode?,
        val coreDatabase: CoreDatabase
    ) {
      data class CoreDatabase(
          val name: String,
          val organizations: Organizations,
          val users: Users
      ) {
        data class Organizations(val container: String)
        data class Users(val container: String)
      }
    }
  }

  enum class Vendor {
    azure
  }
}
