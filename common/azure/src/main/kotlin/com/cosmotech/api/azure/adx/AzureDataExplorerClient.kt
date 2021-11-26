// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.azure.adx

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.events.CsmEventPublisher
import com.cosmotech.api.events.ScenarioRunEndTimeRequest
import com.cosmotech.api.scenariorun.DataIngestionState
import com.microsoft.azure.kusto.data.Client
import com.microsoft.azure.kusto.data.ClientImpl
import com.microsoft.azure.kusto.data.ClientRequestProperties
import com.microsoft.azure.kusto.data.auth.ConnectionStringBuilder
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
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
class AzureDataExplorerClient(
    private val csmPlatformProperties: CsmPlatformProperties,
    val eventPublisher: CsmEventPublisher
) : HealthIndicator {

  private val logger = LoggerFactory.getLogger(AzureDataExplorerClient::class.java)

  private val baseUri = csmPlatformProperties.azure!!.dataWarehouseCluster.baseUri

  private lateinit var kustoClient: Client

  private val dataIngestionStateIfNoControlPlaneInfoButProbeMeasuresData =
      DataIngestionState.valueOf(
          csmPlatformProperties.dataIngestion.state.stateIfNoControlPlaneInfoButProbeMeasuresData)

  private val dataIngestionStateExceptionIfNoControlPlaneInfoAndNoProbeMeasuresData =
      csmPlatformProperties.dataIngestion.state.exceptionIfNoControlPlaneInfoAndNoProbeMeasuresData

  private val waitingTimeBeforeIngestion =
      csmPlatformProperties.dataIngestion.waitingTimeBeforeIngestionSeconds

  private val ingestionObservationWindowToBeConsideredAFailureMinutes =
      csmPlatformProperties.dataIngestion.ingestionObservationWindowToBeConsideredAFailureMinutes

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

  fun getStateFor(
      organizationId: String,
      workspaceKey: String,
      scenarioRunId: String,
      csmSimulationRun: String
  ): DataIngestionState? {
    logger.trace("getStateFor($organizationId,$workspaceKey,$scenarioRunId,$csmSimulationRun)")

    val scenarioRunWorkflowEndTime =
        queryScenarioRunWorkflowEndTime(organizationId, workspaceKey, scenarioRunId)
    val seconds =
        scenarioRunWorkflowEndTime?.until(ZonedDateTime.now(), ChronoUnit.SECONDS)
            ?: return DataIngestionState.Unknown

    if (seconds < waitingTimeBeforeIngestion) {
      return DataIngestionState.InProgress
    }

    val sentMessagesTotal = querySentMessagesTotal(organizationId, workspaceKey, csmSimulationRun)
    val probesMeasuresCount =
        queryProbesMeasuresCount(organizationId, workspaceKey, csmSimulationRun)

    logger.debug(
        "Scenario run {} (csmSimulationRun={}): (sentMessagesTotal,probesMeasuresCount)=(" +
            "$sentMessagesTotal,$probesMeasuresCount)",
        scenarioRunId,
        csmSimulationRun)
    return if (sentMessagesTotal == null) {
      logger.debug(
          "Scenario run {} (csmSimulationRun={}) produced {} measures, " +
              "but no data found in SimulationTotalFacts control plane table",
          scenarioRunId,
          csmSimulationRun,
          probesMeasuresCount)
      if (probesMeasuresCount > 0) {
        // For backward compatibility purposes
        this.dataIngestionStateIfNoControlPlaneInfoButProbeMeasuresData
      } else if (this.dataIngestionStateExceptionIfNoControlPlaneInfoAndNoProbeMeasuresData) {
        throw UnsupportedOperationException(
            "Case not handled: probesMeasuresCount=0 and sentMessagesTotal=NULL. " +
                "Scenario run $scenarioRunId (csmSimulationRun=$csmSimulationRun) " +
                "probably ran no AMQP consumers. " +
                "To mitigate this, either make sure to build your Simulator using " +
                "a version of SDK >= 8.5 or configure the " +
                "'csm.platform.data-ingestion.state.exception-if-no-control-plane-info-" +
                "and-no-probe-measures-data' property flag for this API")
      } else {
        // THis is the case where sentMessagesTotal == null, probesMeasuresCount = 0 and we don't
        // want to throw an exception the simulation is successful but there's no probe measures
        DataIngestionState.Successful
      }
    } else if (probesMeasuresCount < sentMessagesTotal) {
      if (doesProbesMeasuresTableContainIngestionFailures(organizationId, workspaceKey)) {
        DataIngestionState.Failure
      } else {
        DataIngestionState.InProgress
      }
    } else {
      DataIngestionState.Successful
    }
  }

  internal fun queryProbesMeasuresCount(
      organizationId: String,
      workspaceKey: String,
      csmSimulationRun: String
  ): Long {
    val probesMeasuresCountQueryPrimaryResults =
        this.kustoClient.execute(
                getDatabaseName(organizationId, workspaceKey),
                """
                ProbesMeasures
                | where SimulationRun == '${csmSimulationRun}'
                | count
            """,
                ClientRequestProperties().apply {
                  timeoutInMilliSec = TimeUnit.SECONDS.toMillis(REQUEST_TIMEOUT_SECONDS)
                })
            .primaryResults
    if (!probesMeasuresCountQueryPrimaryResults.next()) {
      throw IllegalStateException("Missing ProbesMeasures table")
    }
    return probesMeasuresCountQueryPrimaryResults.getLongObject("Count")!!
  }

  internal fun querySentMessagesTotal(
      organizationId: String,
      workspaceKey: String,
      csmSimulationRun: String
  ): Long? {
    val sentMessagesTotalQueryPrimaryResults =
        this.kustoClient.execute(
                getDatabaseName(organizationId, workspaceKey),
                """
                SimulationTotalFacts
                | where SimulationId == '${csmSimulationRun}'
                | project SentMessagesTotal
            """,
                ClientRequestProperties().apply {
                  timeoutInMilliSec = TimeUnit.SECONDS.toMillis(REQUEST_TIMEOUT_SECONDS)
                })
            .primaryResults

    val sentMessagesTotalRowCount = sentMessagesTotalQueryPrimaryResults.count()
    if (sentMessagesTotalRowCount > 1) {
      throw IllegalStateException(
          "Unexpected number of rows in SimulationTotalFacts ADX Table for SimulationId=" +
              csmSimulationRun +
              ". Expected at most 1, but got " +
              sentMessagesTotalRowCount)
    }
    return if (sentMessagesTotalQueryPrimaryResults.next()) {
      sentMessagesTotalQueryPrimaryResults.getLongObject("SentMessagesTotal")
    } else {
      // No row
      null
    }
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

  internal fun doesProbesMeasuresTableContainIngestionFailures(
      organizationId: String,
      workspaceKey: String,
  ): Boolean {
    val databaseName = getDatabaseName(organizationId, workspaceKey)
    val failureQueryPrimaryResults =
        this.kustoClient.execute(
                databaseName,
                """
                .show ingestion failures
                | where  Table  == 'ProbesMeasures'
                | order by FailedOn desc
                | project FailedOn
            """,
                ClientRequestProperties().apply {
                  timeoutInMilliSec = TimeUnit.SECONDS.toMillis(REQUEST_TIMEOUT_SECONDS)
                })
            .primaryResults
    if (failureQueryPrimaryResults.next()) {
      val count = failureQueryPrimaryResults.count()
      val failedOn = failureQueryPrimaryResults.getKustoDateTime("FailedOn")!!
      val now = LocalDateTime.now()
      val minutes = failedOn.until(now, ChronoUnit.MINUTES)
      return count > 0 && minutes < this.ingestionObservationWindowToBeConsideredAFailureMinutes
    }
    return false
  }

  private fun queryScenarioRunWorkflowEndTime(
      organizationId: String,
      workspaceId: String,
      scenarioRunId: String
  ): ZonedDateTime? {
    val scenarioRunEndTimeRequest =
        ScenarioRunEndTimeRequest(this, organizationId, workspaceId, scenarioRunId)
    this.eventPublisher.publishEvent(scenarioRunEndTimeRequest)
    return scenarioRunEndTimeRequest.response
  }
}

private fun getDatabaseName(organizationId: String, workspaceKey: String) =
    "${organizationId}-${workspaceKey}"
