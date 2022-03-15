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
import com.microsoft.azure.kusto.data.KustoOperationResult
import com.microsoft.azure.kusto.data.auth.ConnectionStringBuilder
import com.microsoft.azure.kusto.data.exceptions.DataServiceException
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.Temporal
import java.util.concurrent.TimeUnit
import javax.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

private const val REQUEST_TIMEOUT_SECONDS = 30L
private const val KUSTO_RESPONSE_VERSION = "v1"
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

    if (seconds <= waitingTimeBeforeIngestion) {
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
      if (anyDataPlaneIngestionFailures(organizationId, workspaceKey, scenarioRunWorkflowEndTime)) {
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
                | summarize SentMessagesTotal = sum(SentMessagesTotal)
            """,
                ClientRequestProperties().apply {
                  timeoutInMilliSec = TimeUnit.SECONDS.toMillis(REQUEST_TIMEOUT_SECONDS)
                })
            .primaryResults
      if (!sentMessagesTotalQueryPrimaryResults.next()) {
          throw IllegalStateException("Missing record in table SimulationTotalFacts for simulation ${csmSimulationRun}")
      }
      return sentMessagesTotalQueryPrimaryResults.getLongObject("SentMessagesTotal")!!
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

  // PROD-8858 to avoid server 500 error use the v1 version of the KustoOperationResult
  // see KustoOperationResult.java
  internal fun <T : Temporal> anyDataPlaneIngestionFailures(
      organizationId: String,
      workspaceKey: String,
      scenarioRunWorkflowEndTime: T,
  ): Boolean {
    val databaseName = getDatabaseName(organizationId, workspaceKey)
    val failureQueryResultsJsonString =
        this.kustoClient.executeToJsonResult(
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

    val failureQueryPrimaryResults =
        KustoOperationResult(failureQueryResultsJsonString, KUSTO_RESPONSE_VERSION).primaryResults

    return if (failureQueryPrimaryResults.next()) {
      failureQueryPrimaryResults.count() > 0 &&
          failureQueryPrimaryResults.getKustoDateTime("FailedOn")!!.until(
              scenarioRunWorkflowEndTime, ChronoUnit.MINUTES) <
              this.ingestionObservationWindowToBeConsideredAFailureMinutes
    } else {
      false
    }
  }

  fun deleteDataFromScenarioRunId(
      organizationId: String,
      workspaceKey: String,
      scenarioRunId: String
  ): String {
    val databaseName = getDatabaseName(organizationId, workspaceKey)
    try {
      return this.kustoClient.executeToJsonResult(
          databaseName,
          """
        .drop extents <|
            .show database ['${databaseName}'] extents
            where tags has 'drop-by:${scenarioRunId}'
    """,
          ClientRequestProperties().apply {
            timeoutInMilliSec = TimeUnit.SECONDS.toMillis(REQUEST_TIMEOUT_SECONDS)
          })
    } catch (exception: DataServiceException) {
      logger.debug("An error occurred while deleting data: {}", exception.message, exception)
      throw IllegalStateException("An error occurred while deleting data")
    }
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
