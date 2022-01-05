// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.azure.adx

import com.cosmotech.api.config.CsmPlatformProperties
import com.microsoft.azure.kusto.data.Client
import com.microsoft.azure.kusto.data.ClientImpl
import com.microsoft.azure.kusto.data.ClientRequestProperties
import com.microsoft.azure.kusto.data.auth.ConnectionStringBuilder
import java.util.concurrent.TimeUnit
import javax.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

private const val REQUEST_TIMEOUT_SECONDS = 30L
// Default database name as defined as a private field in
// com.microsoft.azure.kusto.data.ClientImpl. Used for health-checking.
internal const val DEFAULT_DATABASE_NAME = "NetDefaultDb"
internal const val HEALTH_KUSTO_QUERY =
    """
                      .show diagnostics
                      | project IsHealthy
                  """

@Service("csmADX")
@ConditionalOnProperty(name = ["csm.platform.vendor"], havingValue = "azure", matchIfMissing = true)
class AzureDataExplorerClient(private val csmPlatformProperties: CsmPlatformProperties) :
    HealthIndicator {

  private val logger = LoggerFactory.getLogger(AzureDataExplorerClient::class.java)

  private val baseUri = csmPlatformProperties.azure!!.dataWarehouseCluster.baseUri

  private lateinit var kustoClient: Client

  @PostConstruct
  internal fun init() {
    val csmPlatformAzure = csmPlatformProperties.azure!!
    // TODO Investigate whether we need to use core or customer creds
    val csmPlatformAzureCredentials = csmPlatformAzure.credentials.core
    this.kustoClient =
        ClientImpl(
            ConnectionStringBuilder.createWithAadApplicationCredentials(
                baseUri,
                csmPlatformAzureCredentials.clientId,
                csmPlatformAzureCredentials.clientSecret,
                csmPlatformAzureCredentials.tenantId))
  }

  internal fun setKustoClient(kustoClient: Client) {
    this.kustoClient = kustoClient
  }

  @Suppress("TooGenericExceptionCaught")
  override fun health(): Health {
    val healthBuilder =
        try {
          val diagnosticsResult =
              this.kustoClient.execute(
                      DEFAULT_DATABASE_NAME,
                      HEALTH_KUSTO_QUERY.trimIndent(),
                      ClientRequestProperties().apply {
                        timeoutInMilliSec = TimeUnit.SECONDS.toMillis(REQUEST_TIMEOUT_SECONDS)
                      })
                  .primaryResults
          if (!diagnosticsResult.next()) {
            throw IllegalStateException(
                "Could not determine cluster health. " +
                    "Diagnostics query returned no result at all.")
          }
          val isHealthyResult = diagnosticsResult.getIntegerObject("IsHealthy")
          if (isHealthyResult != 1) {
            throw IllegalStateException("Unhealthy cluster. isHealthyResult=$isHealthyResult")
          }
          Health.up()
        } catch (exception: Exception) {
          logger.debug("Error in health-check: {}", exception.message, exception)
          Health.down(exception)
        }
    return healthBuilder.withDetail("baseUri", baseUri).build()
  }
}

@Suppress("Unused", "UnusedPrivateMember") // Used in the context of PROD-7420 and PROD-8148
private fun getDatabaseName(organizationId: String, workspaceKey: String) =
    "${organizationId}-${workspaceKey}"
