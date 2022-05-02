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
import com.microsoft.azure.kusto.ingest.ColumnMapping
import com.microsoft.azure.kusto.ingest.IngestClient
import com.microsoft.azure.kusto.ingest.IngestClientFactory
import com.microsoft.azure.kusto.ingest.IngestionMapping
import com.microsoft.azure.kusto.ingest.IngestionMapping.IngestionMappingKind
import com.microsoft.azure.kusto.ingest.IngestionProperties
import com.microsoft.azure.kusto.ingest.exceptions.IngestionClientException
import com.microsoft.azure.kusto.ingest.result.IngestionStatus
import com.microsoft.azure.kusto.ingest.result.OperationStatus
import com.microsoft.azure.kusto.ingest.source.StreamSourceInfo
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
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
@Suppress("TooManyFunctions")
@ConditionalOnProperty(name = ["csm.platform.vendor"], havingValue = "azure", matchIfMissing = true)
class AzureDataExplorerClient(
    private val csmPlatformProperties: CsmPlatformProperties,
    val eventPublisher: CsmEventPublisher
) : HealthIndicator {

  private val logger = LoggerFactory.getLogger(AzureDataExplorerClient::class.java)

  private val baseUri = csmPlatformProperties.azure!!.dataWarehouseCluster.baseUri

  private val ingestionUri = csmPlatformProperties.azure!!.dataWarehouseCluster.options.ingestionUri

  private lateinit var kustoClient: Client

  private lateinit var ingestClient: IngestClient

  private val waitingTimeBeforeIngestion =
      csmPlatformProperties.dataIngestion.waitingTimeBeforeIngestionSeconds

  private val ingestionObservationWindowToBeConsideredAFailureMinutes =
      csmPlatformProperties.dataIngestion.ingestionObservationWindowToBeConsideredAFailureMinutes

  private val noDataTimeOutSeconds = csmPlatformProperties.dataIngestion.state.noDataTimeOutSeconds

  private val sleepingTimeScenarioValidationStatus =
      csmPlatformProperties.dataIngestion.sleepingTimeBeforeQueryingScenarioValidationStatusSeconds

  private val maxRetryAuthorized = csmPlatformProperties.dataIngestion.maxRetryAuthorized

  internal fun getConnectionStringBuilder(clusterUri: String): ConnectionStringBuilder {
    val csmPlatformAzure = csmPlatformProperties.azure!!
    // TODO Investigate whether we need to use core or customer creds
    val csmPlatformAzureCredentials = csmPlatformAzure.credentials.core
    return ConnectionStringBuilder.createWithAadApplicationCredentials(
        clusterUri,
        csmPlatformAzureCredentials.clientId,
        csmPlatformAzureCredentials.clientSecret,
        csmPlatformAzureCredentials.tenantId)
  }

  @PostConstruct
  internal fun init() {
    this.kustoClient = ClientImpl(getConnectionStringBuilder(baseUri))

    this.ingestClient = IngestClientFactory.createClient(getConnectionStringBuilder(ingestionUri))
  }

  internal fun setKustoClient(kustoClient: Client) {
    this.kustoClient = kustoClient
  }

  internal fun setIngestClient(ingestClient: IngestClient) {
    this.ingestClient = ingestClient
  }

  fun getStateFor(
      organizationId: String,
      workspaceKey: String,
      scenarioRunId: String,
      csmSimulationRun: String,
  ): DataIngestionState? {
    // Important: We only enter in this function if ingestion state can be fetch with control plane
    // information
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
    return if (sentMessagesTotal == 0L) {
      this.handleNoDataTimeOut(scenarioRunId, csmSimulationRun, seconds, probesMeasuresCount)
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

  private fun handleNoDataTimeOut(
      scenarioRunId: String,
      csmSimulationRun: String,
      seconds: Long,
      probesMeasuresCount: Long,
  ): DataIngestionState {
    logger.debug(
        "Scenario run {} (csmSimulationRun={}) produced {} measures, " +
            "but no data found in SimulationTotalFacts control plane table",
        scenarioRunId,
        csmSimulationRun,
        probesMeasuresCount)
    if (seconds > this.noDataTimeOutSeconds) {
      var error =
          "Time out of ${this.noDataTimeOutSeconds} seconds reached for sentMessagesTotal=0 " +
              "with probesMeasuresCount=${probesMeasuresCount} " +
              "for Scenario run $scenarioRunId (csmSimulationRun=$csmSimulationRun). "
      if (probesMeasuresCount > 0) {
        error += "There is maybe an issue in data ingestion of control plane to datawarehouse. "
      } else {
        error += "Simulation seems to send not data at all or there is an issue in data ingestion. "
      }
      logger.error(
          error +
              "Solution can set a sdkVersion < 8.5 to disable data ingestion state with control plane. " +
              "Solution can set noDataIngestionState to true on the run template to disable data ingestion " +
              "state with control plane. " +
              "Devops admin can configure 'csm.platform.data-ingestion.state.no-data-time-out-seconds' to set this " +
              "timeout property flag for this API.")

      return DataIngestionState.Failure
    } else {
      // No data time out not reached yet
      return DataIngestionState.InProgress
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
  ): Long {
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
      throw IllegalStateException(
          "Missing record in table SimulationTotalFacts for simulation ${csmSimulationRun}")
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

  fun ingestScenarioValidationStatus(
      organizationId: String,
      workspaceKey: String,
      scenarioId: String,
      scenarioStatus: String
  ): String {
    val databaseName = getDatabaseName(organizationId, workspaceKey.replace("\\s".toRegex(), ""))
    val ingestionProperties = IngestionProperties(databaseName, "ScenarioValidationStatus")
    ingestionProperties.ingestionMapping = buildScenarioValidationStatusIngestionMapping()
    ingestionProperties.setReportMethod(IngestionProperties.IngestionReportMethod.QUEUE_AND_TABLE)
    ingestionProperties.setReportLevel(
        IngestionProperties.IngestionReportLevel.FAILURES_AND_SUCCESSES)
    ingestionProperties.dataFormat = IngestionProperties.DataFormat.CSV

    val data = "${scenarioId},${scenarioStatus},${ZonedDateTime.now().toLocalDateTime().toString()}"
    val inputStream: InputStream = ByteArrayInputStream(StandardCharsets.UTF_8.encode(data).array())
    val streamSourceInfo = StreamSourceInfo(inputStream)

    return ingestFromStream(streamSourceInfo, ingestionProperties).status.toString()
  }

  private fun buildScenarioValidationStatusIngestionMapping(): IngestionMapping {
    val scenarioIdColumn = ColumnMapping("ScenarioId", "string")
    val statusColumn = ColumnMapping("Status", "string")
    val modifiedDateColumn = ColumnMapping("ModifiedDate", "string")
    scenarioIdColumn.setOrdinal(0)
    statusColumn.setOrdinal(1)
    modifiedDateColumn.setOrdinal(2)

    var columnMappings = mutableListOf<ColumnMapping>()
    columnMappings.add(scenarioIdColumn)
    columnMappings.add(statusColumn)
    columnMappings.add(modifiedDateColumn)

    return IngestionMapping(columnMappings.toTypedArray(), IngestionMappingKind.CSV)
  }

  internal fun ingestFromStream(
      streamSourceInfo: StreamSourceInfo,
      ingestionProperties: IngestionProperties
  ): IngestionStatus {
    var status = IngestionStatus()
    try {
      val result = ingestClient.ingestFromStream(streamSourceInfo, ingestionProperties)
      if (result.ingestionStatusCollection.size > 0) {
        status = result.ingestionStatusCollection[0]
      }
      var retryCount: Int = 0
      while (status.status == OperationStatus.Pending && retryCount < maxRetryAuthorized) {
        Thread.sleep(sleepingTimeScenarioValidationStatus)
        if (result.ingestionStatusCollection.size > 0) {
          status = result.ingestionStatusCollection[0]
        }
        retryCount++
      }

      logger.info("Ingestion completed")
      logger.info("Final status: ${status.status}")
    } catch (e: IngestionClientException) {
      logger.info("Failed to initiate ingestion: ${e.message}")
      status.status = OperationStatus.Failed
    }

    return status
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
