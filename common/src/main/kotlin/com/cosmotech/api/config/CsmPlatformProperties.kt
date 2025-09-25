// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.config

import com.cosmotech.api.security.ROLE_ORGANIZATION_USER
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties

/** Configuration Properties for the Cosmo Tech Platform */
@ConfigurationProperties(prefix = "csm.platform")
data class CsmPlatformProperties(
    /** API Configuration */
    val api: Api,

    /** Platform vendor */
    val vendor: Vendor = Vendor.ON_PREMISE,

    /** Id Generator */
    val idGenerator: IdGenerator,

    /** Event Publisher */
    val eventPublisher: EventPublisher,

    /** Container Registry */
    val containerRegistry: CsmPlatformContainerRegistry = CsmPlatformContainerRegistry(),

    /** S3 service */
    val s3: S3,

    /** Argo Service */
    val argo: Argo,

    /** Authorization Configuration */
    val authorization: Authorization = Authorization(),

    /**
     * Identity provider used for (Entra ID ,Okta, Keycloak) if openapi default configuration needs
     * to be overwritten
     */
    val identityProvider: CsmIdentityProvider,

    /** Twin Data Layer configuration */
    val twincache: CsmTwinCacheProperties,

    /** RBAC / ACL configuration */
    val rbac: CsmRbac = CsmRbac(),

    /** Upload files properties */
    val upload: Upload = Upload(),

    /** Kubernetes namespace */
    val namespace: String = "phoenix",

    /** Persistent metrics configuration */
    val metrics: Metrics = Metrics(),

    /** Internal result data service configuration */
    val internalResultServices: CsmServiceResult?,
) {
  @ConditionalOnProperty(
      prefix = "csm.platform.internalResultServices.enabled",
      havingValue = "true",
      matchIfMissing = false)
  data class CsmServiceResult(
      /** Define if current API use internal result data service or cloud one */
      val enabled: Boolean = false,

      /** Storage configuration */
      val storage: CsmStorage,

      /** Queue configuration */
      val eventBus: CsmEventBus
  ) {
    data class CsmStorage(
        /** Define if current API use internal storage for probes or not */
        val enabled: Boolean = true,

        /** Storage host */
        val host: String,

        /** Storage port */
        val port: Int = 5432,

        /** Storage reader user configuration */
        val reader: CsmStorageUser,

        /** Storage writer user configuration */
        val writer: CsmStorageUser,

        /** Storage admin user configuration */
        val admin: CsmStorageUser
    ) {
      data class CsmStorageUser(val username: String, val password: String)
    }

    data class CsmEventBus(
        /** Define if current API use event bus within internal result data service or not */
        val enabled: Boolean = true,

        /** TLS Platform bundle config */
        val tls: TLSConfig = TLSConfig(),

        /** EventBus host */
        val host: String,

        /** EventBus port */
        val port: Int = 5672,

        /** EventBus default exchange */
        val defaultExchange: String = "csm-exchange",

        /** EventBus default queue */
        val defaultQueue: String = "csm",

        /** EventBus default routing key */
        val defaultRoutingKey: String = "csm",

        /** EventBus listener user configuration */
        val listener: CsmEventBusUser,

        /** EventBus sender user configuration */
        val sender: CsmEventBusUser
    ) {
      data class CsmEventBusUser(val username: String, val password: String)
    }
  }

  data class Metrics(
      /** Enable Metrics service */
      val enabled: Boolean = true,

      /** Metrics service retention days */
      val retentionDays: Int = 7,

      /** Metrics service down-sampling activation */
      val downSamplingDefaultEnabled: Boolean = false,

      /** Metrics service down-sampling retention days */
      val downSamplingRetentionDays: Int = 400,

      /** Metrics service down-sampling bucket duration (in ms) */
      val downSamplingBucketDurationMs: Int = 3600000,
  )

  data class Authorization(

      /** The JWT Claim to use to extract a unique identifier for the user account */
      val principalJwtClaim: String = "sub",

      /** The JWT Claim where the tenant id information is stored */
      val tenantIdJwtClaim: String = "iss",

      /** The JWT Claim where the mail information is stored */
      val mailJwtClaim: String = "preferred_username",

      /** The JWT Claim where the roles information is stored */
      val rolesJwtClaim: String = "roles",

      /** The JWT Claim used to define application id in ACL */
      val applicationIdJwtClaim: String = "oid",

      /** List of additional tenants allowed to register */
      val allowedTenants: List<String> = emptyList(),

      /** List of Api key allowed to access data, besides Oauth2 configuration */
      val allowedApiKeyConsumers: List<ApiKeyConsumer> = emptyList()
  ) {
    class ApiKeyConsumer(

        /** The consumer name (human-readable) to track usage */
        val name: String,

        /** Api Key associated to consumer that is passed in request */
        val apiKey: String,

        /** Default Header name that contains apiKey value in requests */
        val apiKeyHeaderName: String = "X-CSM-API-KEY",

        /** Platform Role associated to apiKey */
        val associatedRole: String = ROLE_ORGANIZATION_USER,

        /** Secured URIs */
        val securedUris: List<String> = emptyList()
    )
  }

  data class S3(
      /** Endpoint URL */
      val endpointUrl: String = "http://localhost:9000",
      /** Bucket name */
      val bucketName: String = "cosmotech-api",
      /** Credentials: access key id */
      val accessKeyId: String = "",
      /** Credentials: secret access key */
      val secretAccessKey: String = "",
      /** Storage region */
      val region: String = ""
  )

  data class Argo(
      /** Argo service base Uri */
      val baseUri: String,

      /** Image Pull Secrets */
      val imagePullSecrets: List<String>? = null,

      /** Workflow Management */
      val workflows: Workflows,

      /** Default Main Container Image Pull Policy */
      val imagePullPolicy: String = "IfNotPresent",
  ) {
    data class Workflows(
        /** The Kubernetes namespace in which Argo Workflows should be submitted */
        val namespace: String,

        /** The node label to look for Workflows placement requests */
        val nodePoolLabel: String,

        /** The Kubernetes service account name */
        val serviceAccountName: String,

        /**
         * The Kubernetes storage-class to use for volume claims. Set to null or an empty string to
         * use the default storage class available in the cluster
         */
        val storageClass: String? = null,

        /** List of AccessModes for the Kubernetes Persistent Volume Claims to use for Workflows */
        val accessModes: List<String> = emptyList(),

        /**
         * Minimum resources the volumes requested by the Persistent Volume Claims should have.
         * Example: storage: 1Gi
         */
        val requests: Map<String, String> = emptyMap(),

        /** Don't try to use node selector to schedule argo workflows */
        val ignoreNodeSelector: Boolean = false,

        /** Info on k8s secret to mount on workflow */
        val secrets: List<ExtraSecrets> = emptyList()
    ) {
      data class ExtraSecrets(val name: String, val keyPath: List<KeyPath>) {
        data class KeyPath(val key: String, val path: String)
      }
    }
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

  data class CsmPlatformContainerRegistry(

      /** Verify if solution docker image is present in container registry */
      val checkSolutionImage: Boolean = true,

      /** Scheme/protocol used to connect to the registry API */
      val scheme: String = "https",

      /** Host of the container registry */
      val host: String = "csmenginesdev.azurecr.io",

      /** Container registry username */
      val username: String? = null,

      /** Container registry password */
      val password: String? = null,
  )

  enum class Vendor {
    /** Microsoft Azure : https://azure.microsoft.com/en-us/ */
    AZURE,
    ON_PREMISE
  }

  data class CsmIdentityProvider(
      /** okta|azure|keycloak */
      val code: String,

      /**
       * entry sample :
       * - {"http://dev.api.cosmotech.com/platform" to "Platform scope"}
       * - {"default" to "Default scope"}
       */
      val defaultScopes: Map<String, String> = emptyMap(),

      /**
       * - "https://{yourOktaDomain}/oauth2/default/v1/authorize"
       * - "https://login.microsoftonline.com/common/oauth2/v2.0/authorize"
       */
      val authorizationUrl: String,

      /**
       * - "https://{yourOktaDomain}/oauth2/default/v1/token"
       * - "https://login.microsoftonline.com/common/oauth2/v2.0/token"
       */
      val tokenUrl: String,

      /**
       * entry sample :
       * - {"csm.read.scenario" to "Read access to scenarios"}
       */
      val containerScopes: Map<String, String> = emptyMap(),

      /** Custom group name used acted as Organization.Admin default: Platform.Admin */
      val adminGroup: String? = null,

      /** Custom group name used acted as Organization.User default: Organization.User */
      val userGroup: String? = null,

      /** Custom group name used acted as Organization.Viewer default: Organization.Viewer */
      val viewerGroup: String? = null,

      /** Identity available during run */
      val identity: CsmIdentity,

      /** Server base Url for identity provider (without / at the end) */
      val serverBaseUrl: String = "",

      /** Audience */
      val audience: String = "",

      /** TLS Platform bundle config */
      val tls: TLSConfig = TLSConfig(),
  ) {
    data class CsmIdentity(

        /** Tenant/realm's identifier: default cosmotech */
        val tenantId: String = "cosmotech",

        /** Client identifier: default cosmotech-api-client */
        val clientId: String = "cosmotech-api-client",

        /** Client secret */
        val clientSecret: String,
    )
  }

  data class CsmTwinCacheProperties(
      /** Twin cache host */
      val host: String,

      /** Twin cache port */
      val port: String = "6379",

      /** Twin cache user */
      val username: String = "default",

      /** Twin cache password */
      val password: String,

      /** Twin cache query timeout. Kill a query after specified timeout (in millis) default 5000 */
      val queryTimeout: Long = 5000,

      /**
       * After specified timeout the query bulk will be deleted from Redis (in seconds) default
       * 86400 = 24h
       */
      val queryBulkTTL: Long = 86400,

      /** Twin cache query page information for organization */
      val organization: PageSizing = PageSizing(),

      /** Twin cache query page information for workspace */
      val workspace: PageSizing = PageSizing(),

      /** Twin cache query page information for runner */
      val runner: PageSizing = PageSizing(),

      /** Twin cache query page information for dataset */
      val dataset: PageSizing = PageSizing(),

      /** Twin cache query page information for run */
      val run: PageSizing = PageSizing(),

      /** Twin cache query page information for solution */
      val solution: PageSizing = PageSizing(),

      /** TLS Platform bundle config */
      val tls: TLSConfig = TLSConfig()
  ) {

    data class PageSizing(
        /** Max result for a single page */
        val defaultPageSize: Int = 50
    )
  }

  data class CsmRbac(
      /** Enable Rbac */
      val enabled: Boolean = false
  )

  data class Upload(
      /** The list of files MIME types when uploading a file to the Platform */
      val authorizedMimeTypes: AuthorizedMimeTypes = AuthorizedMimeTypes(),
  ) {
    data class AuthorizedMimeTypes(
        /** List of authorized mime types for workspace file upload */
        val workspaces: List<String> = emptyList(),
        /** List of authorized mime types for solution file upload */
        val solutions: List<String> = emptyList(),
        /** List of authorized mime types for dataset file upload */
        val datasets: List<String> = emptyList(),
    )
  }

  data class TLSConfig(val enabled: Boolean = false, val bundle: String = "")
}
