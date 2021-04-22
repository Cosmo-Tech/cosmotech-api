// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.config

import com.azure.cosmos.ConnectionMode
import com.azure.cosmos.ConsistencyLevel
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

/** Configuration Properties for the Cosmo Tech Platform */
@ConstructorBinding
@ConfigurationProperties(prefix = "csm.platform")
data class CsmPlatformProperties(
    /** Platform vendor */
    val vendor: Vendor,

    /** Azure Platform */
    val azure: CsmPlatformAzure?
) {

  data class CsmPlatformAzure(
      /** Azure Cosmos DB */
      val cosmos: CsmPlatformAzureCosmos
  ) {
    data class CsmPlatformAzureCosmos(

        /** DNS URI of the Azure Cosmos DB account */
        val uri: String,

        /** Azure Cosmos DB Database used for Phoenix */
        val coreDatabase: CoreDatabase,

        /** Access Key of the Azure Cosmos DB database */
        val key: String,

        /** Consistency level */
        val consistencyLevel: ConsistencyLevel?,

        /** Whether to populate Diagnostics Strings and Query metrics */
        val populateQueryMetrics: Boolean,

        /** Whether to allow Microsoft to collect telemetry data. */
        val allowTelemetry: Boolean,

        /** The connection mode to be used by the clients to Azure Cosmos DB. */
        val connectionMode: ConnectionMode?
    ) {
      data class CoreDatabase(
          /** The core database name in Azure Cosmos DB. Must already exist there. */
          val name: String,

          /** The Organizations configuration */
          val organizations: Organizations,

          /** The Users configuration */
          val users: Users
      ) {
        data class Organizations(

            /** Container name for storing all Organizations */
            val container: String
        )
        data class Users(

            /** Container name for storing all Users */
            val container: String
        )
      }
    }
  }

  enum class Vendor {
    azure
  }
}
