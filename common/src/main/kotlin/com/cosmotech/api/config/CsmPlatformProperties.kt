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

    /** the Platform summary */
    val summary: String?,

    /** the Platform description */
    val description: String?,

    /** the Platform version (MAJOR.MINOR.PATCH). */
    val version: String?,

    /** the Platform exact commit ID. */
    val commitId: String? = null,

    /** the Platform exact Version-Control System reference. */
    val vcsRef: String? = null,

    /** API Configuration */
    val api: Api,

    /** Platform vendor */
    val vendor: Vendor,

    /** Id Generator */
    val idGenerator: IdGenerator,

    /** Event Publisher */
    val eventPublisher: EventPublisher,

    /** Azure Platform */
    val azure: CsmPlatformAzure?,

    /** Argo Service */
    val argo: Argo,

    /** Cosmo Tech core images */
    val images: CsmImages,

    /** Authorization Configuration */
    val authorization: Authorization = Authorization(),
) {

  data class Authorization(

      /** The JWT Claim to use to extract a unique identifier for the user account */
      val principalJwtClaim: String = "sub",

      /** The JWT Claim where the tenant id information is stored */
      val tenantIdJwtClaim: String = "iss",

      /**
       * List of additional tenants allowed to register, besides the configured
       * `csm.platform.azure.credentials.tenantId`
       */
      val allowedTenants: List<String> = emptyList()
  )

  data class CsmImages(
      /** Container image to fetch Scenario Parameters */
      val scenarioFetchParameters: String,

      /** Container image to send data to DataWaregouse */
      val sendDataWarehouse: String,
  )

  data class Argo(
      /** Argo service base Uri */
      val baseUri: String,

      /** Image Pull Secrets */
      val imagePullSecrets: List<String>? = null,

      /** Workflow Management */
      val workflows: Workflows,
  ) {
    data class Workflows(
        /** The Kubernetes namespace in which Argo Workflows should be submitted */
        val namespace: String,

        /** The node label to look for Workflows placement requests */
        val nodePoolLabel: String,

        /** The Kubernetes service account name */
        val serviceAccountName: String
    )
  }

  data class Api(
      /** API Version, e.g.: latest, or v1 */
      val version: String,

      /** API Base URL */
      val baseUrl: String,

      /**
       * Base path under which the API is exposed at root, e.g.: /cosmotech-api/. Typically when
       * served behind a reverse-proxy under a dedicated path, this would be such path.
       */
      val basePath: String,
  )

  data class IdGenerator(val type: Type) {
    enum class Type {
      /** short unique UIDs */
      HASHID,

      /** UUIDs */
      UUID
    }
  }

  data class EventPublisher(val type: Type) {
    enum class Type {
      /** In-process, via Spring Application Events */
      IN_PROCESS
    }
  }

  data class CsmPlatformAzure(
      /** Azure Credentials */
      val credentials: CsmPlatformAzureCredentials,
      val storage: CsmPlatformAzureStorage,
      val containerRegistries: CsmPlatformAzureContainerRegistries,
      val eventBus: CsmPlatformAzureEventBus,
      val dataWarehouseCluster: CsmPlatformAzureDataWarehouseCluster,
      val keyVault: String,
      val analytics: CsmPlatformAzureAnalytics,
      /** Azure Cosmos DB */
      val cosmos: CsmPlatformAzureCosmos,
      val appIdUri: String,
  ) {

    data class CsmPlatformAzureCredentials(
        /** The Azure Tenant ID (core App) */
        val tenantId: String,

        /** The Azure Client ID (core App) */
        val clientId: String,

        /** The Azure Client Secret (core App) */
        val clientSecret: String,

        /** The Azure Active Directory Pod Id binding
        bound to an AKS pod identity linked to a managed identity */
        val aadPodIdBinding: String,
    )

    data class CsmPlatformAzureStorage(
        val connectionString: String,
        val baseUri: String,
        val resourceUri: String
    )

    data class CsmPlatformAzureContainerRegistries(val core: String, val solutions: String)

    data class CsmPlatformAzureEventBus(val baseUri: String)

    data class CsmPlatformAzureDataWarehouseCluster(val baseUri: String, val options: Options) {
      data class Options(val ingestionUri: String)
    }

    data class CsmPlatformAzureAnalytics(
        val resourceUri: String,
        val instrumentationKey: String,
        val connectionString: String
    )

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

          /** The Connectors configuration */
          val connectors: Connectors,

          /** The Organizations configuration */
          val organizations: Organizations,

          /** The Users configuration */
          val users: Users
      ) {
        data class Connectors(

            /** Container name for storing all Connectors */
            val container: String
        )
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
    /** Microsoft Azure : https://azure.microsoft.com/en-us/ */
    AZURE
  }
}
