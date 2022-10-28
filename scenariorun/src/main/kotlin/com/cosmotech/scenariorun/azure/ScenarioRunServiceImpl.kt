// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun.azure

import com.azure.cosmos.models.CosmosItemRequestOptions
import com.azure.cosmos.models.CosmosQueryRequestOptions
import com.azure.cosmos.models.PartitionKey
import com.azure.cosmos.models.SqlParameter
import com.azure.cosmos.models.SqlQuerySpec
import com.cosmotech.api.azure.CsmAzureService
import com.cosmotech.api.azure.adx.AzureDataExplorerClient
import com.cosmotech.api.azure.eventhubs.AzureEventHubsClient
import com.cosmotech.api.config.CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication.Strategy.SHARED_ACCESS_POLICY
import com.cosmotech.api.config.CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication.Strategy.TENANT_CLIENT_CREDENTIALS
import com.cosmotech.api.events.DeleteHistoricalDataOrganization
import com.cosmotech.api.events.DeleteHistoricalDataScenario
import com.cosmotech.api.events.DeleteHistoricalDataWorkspace
import com.cosmotech.api.events.ScenarioDataDownloadJobInfoRequest
import com.cosmotech.api.events.ScenarioDataDownloadRequest
import com.cosmotech.api.events.ScenarioDeleted
import com.cosmotech.api.events.ScenarioRunEndTimeRequest
import com.cosmotech.api.events.ScenarioRunEndToEndStateRequest
import com.cosmotech.api.events.ScenarioRunStartedForScenario
import com.cosmotech.api.events.WorkflowPhaseToStateRequest
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.scenario.ScenarioRunMetaData
import com.cosmotech.api.scenariorun.DataIngestionState
import com.cosmotech.api.utils.convertToMap
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.api.utils.toDomain
import com.cosmotech.scenario.api.ScenarioApiService
import com.cosmotech.scenario.domain.Scenario
import com.cosmotech.scenariorun.CSM_JOB_ID_LABEL_KEY
import com.cosmotech.scenariorun.ContainerFactory
import com.cosmotech.scenariorun.SCENARIO_DATA_DOWNLOAD_ARTIFACT_NAME
import com.cosmotech.scenariorun.api.ScenariorunApiService
import com.cosmotech.scenariorun.domain.RunTemplateParameterValue
import com.cosmotech.scenariorun.domain.ScenarioRun
import com.cosmotech.scenariorun.domain.ScenarioRunLogs
import com.cosmotech.scenariorun.domain.ScenarioRunSearch
import com.cosmotech.scenariorun.domain.ScenarioRunStartContainers
import com.cosmotech.scenariorun.domain.ScenarioRunState
import com.cosmotech.scenariorun.domain.ScenarioRunStatus
import com.cosmotech.scenariorun.isTerminal
import com.cosmotech.scenariorun.withoutSensitiveData
import com.cosmotech.scenariorun.workflow.WorkflowService
import com.cosmotech.solution.domain.DeleteHistoricalData
import com.cosmotech.solution.domain.RunTemplate
import com.cosmotech.solution.domain.Solution
import com.cosmotech.workspace.api.WorkspaceApiService
import com.cosmotech.workspace.domain.Workspace
import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.time.ZonedDateTime
import kotlin.reflect.full.memberProperties

private const val MIN_SDK_VERSION_MAJOR = 8
private const val MIN_SDK_VERSION_MINOR = 5
private const val DELETE_SCENARIO_RUN_DEFAULT_TIMEOUT: Long = 28800

@Service
@ConditionalOnProperty(name = ["csm.platform.vendor"], havingValue = "azure", matchIfMissing = true)
@Suppress("TooManyFunctions", "LargeClass")
internal class ScenarioRunServiceImpl(
    private val containerFactory: ContainerFactory,
    private val workflowService: WorkflowService,
    private val workspaceService: WorkspaceApiService,
    private val scenarioApiService: ScenarioApiService,
    private val azureDataExplorerClient: AzureDataExplorerClient,
    private val azureEventHubsClient: AzureEventHubsClient
) : CsmAzureService(), ScenariorunApiService {

  private fun ScenarioRun.asMapWithAdditionalData(workspaceId: String? = null): Map<String, Any> {
    val scenarioAsMap = this.convertToMap().toMutableMap()
    scenarioAsMap["type"] = "ScenarioRun"
    if (workspaceId != null) {
      scenarioAsMap["workspaceId"] = workspaceId
    }
    return scenarioAsMap
  }

  private fun ScenarioRunSearch.toQueryPredicate(): Pair<String, List<SqlParameter>> {
    val queryPredicateComponents =
        this::class
            .memberProperties
            .mapNotNull { memberProperty ->
              val propertyName = memberProperty.name
              val value: Any? = memberProperty.getter.call(this)
              if (value == null) null
              else "c.$propertyName = @$propertyName" to SqlParameter("@$propertyName", value)
            }
            .toMap()
    // TODO Joining with AND or OR ?
    return queryPredicateComponents.keys.joinToString(separator = " AND ") to
        queryPredicateComponents.values.toList()
  }

  override fun deleteScenarioRun(organizationId: String, scenariorunId: String) {
    val scenarioRun = this.findScenarioRunById(organizationId, scenariorunId)
    if (scenarioRun.ownerId != getCurrentAuthenticatedUserName()) {
      // TODO Only the owner or an admin should be able to perform this operation
      throw CsmAccessForbiddenException("You are not allowed to delete this Resource")
    }
    deleteScenarioRunWithoutAccessEnforcement(scenarioRun)
  }

  private fun deleteScenarioRunWithoutAccessEnforcement(scenarioRun: ScenarioRun) {
    // Simple way to ensure that we do not delete data if something went wrong
    try {
      logger.debug(
          "Deleting scenario run. Organization: {}, Workspace: {}, Scenario Run Id: {}, csmSimulationRun: {}",
          scenarioRun.organizationId ?: "null",
          scenarioRun.workspaceKey ?: "null",
          scenarioRun.id ?: "null",
          scenarioRun.csmSimulationRun ?: "null")

      azureDataExplorerClient.deleteDataFromADXbyExtentShard(
          scenarioRun.organizationId!!, scenarioRun.workspaceKey!!, scenarioRun.csmSimulationRun!!)
      logger.debug(
          "Scenario run {} deleted from ADX with csmSimulationRun {}",
          scenarioRun.id!!,
          scenarioRun.csmSimulationRun)

      // It seems that deleteEntity does not throw any exception
      logger.debug("Deleting Scenario Run {} from Cosmos DB", scenarioRun.id)
      cosmosTemplate.deleteEntity("${scenarioRun.organizationId}_scenario_data", scenarioRun)
      logger.debug("Scenario Run {} deleted from Cosmos DB", scenarioRun.id)
    } catch (exception: IllegalStateException) {
      logger.debug(
          "An error occurred while deleting ScenarioRun {}: {}",
          scenarioRun.id,
          exception.message,
          exception)
    }
  }

  override fun deleteHistoricalDataOrganization(organizationId: String, deleteUnknown: Boolean?) {
    this.eventPublisher.publishEvent(
        DeleteHistoricalDataOrganization(this, organizationId = organizationId, deleteUnknown))
  }

  override fun deleteHistoricalDataWorkspace(
      organizationId: String,
      workspaceId: String,
      deleteUnknown: Boolean?
  ) {
    this.eventPublisher.publishEvent(
        DeleteHistoricalDataWorkspace(
            this, organizationId = organizationId, workspaceId = workspaceId, deleteUnknown))
  }

  override fun deleteHistoricalDataScenario(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      deleteUnknown: Boolean?
  ) {
    GlobalScope.launch {
      this@ScenarioRunServiceImpl.deleteScenarioRunsByScenarioWithoutAccessEnforcement(
          organizationId, workspaceId, scenarioId, deleteUnknown)
    }
  }

  private fun deleteScenarioRunsByScenarioWithoutAccessEnforcement(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      deleteUnknown: Boolean?
  ) {
    val scenarioRuns = getScenarioRuns(organizationId, workspaceId, scenarioId).toMutableList()

      val lastRunId =
          scenarioApiService.findScenarioById(organizationId, workspaceId, scenarioId).lastRun!!
              .scenarioRunId

    scenarioRuns.filter { it.state == ScenarioRunState.Failed }.forEach {
        if (it.id != lastRunId) {
            deleteScenarioRunWithoutAccessEnforcement(it)
        }
    }

      if(deleteUnknown!!) {
          scenarioRuns.filter { it.state == ScenarioRunState.Unknown }.forEach {
              if (it.id != lastRunId) {
                  deleteScenarioRunWithoutAccessEnforcement(it)
              }
          }
      }

    scenarioRuns.filter { it.state == ScenarioRunState.Successful }.forEach {
        if (it.id != lastRunId) {
            deleteScenarioRunWithoutAccessEnforcement(it)
        }
    }
  }

  override fun findScenarioRunById(organizationId: String, scenariorunId: String) =
      findScenarioRunById(organizationId, scenariorunId, withStateInformation = true)

  private fun findScenarioRunByIdOptional(
      organizationId: String,
      scenariorunId: String,
      withStateInformation: Boolean = true
  ) =
      cosmosCoreDatabase
          .getContainer("${organizationId}_scenario_data")
          .queryItems(
              SqlQuerySpec(
                  "SELECT * FROM c WHERE c.type = 'ScenarioRun' AND c.id = @SCENARIORUN_ID",
                  listOf(SqlParameter("@SCENARIORUN_ID", scenariorunId))),
              CosmosQueryRequestOptions(),
              // It would be much better to specify the Domain Type right away and
              // avoid the map operation, but we can't due
              // to the lack of customization of the Cosmos Client Object Mapper, as reported here
              // :
              // https://github.com/Azure/azure-sdk-for-java/issues/12269
              JsonNode::class.java)
          .firstOrNull()
          ?.toDomain<ScenarioRun>()
          ?.let { if (withStateInformation) it.withStateInformation(organizationId) else it }
          ?.withoutSensitiveData()

  private fun findScenarioRunById(
      organizationId: String,
      scenariorunId: String,
      withStateInformation: Boolean
  ) =
      this.findScenarioRunByIdOptional(organizationId, scenariorunId, withStateInformation)
          ?: throw java.lang.IllegalArgumentException(
              "ScenarioRun #$scenariorunId not found in organization #$organizationId")

  private fun ScenarioRun?.withStateInformation(organizationId: String): ScenarioRun? {
    if (this == null) {
      return null
    }
    var scenarioRun = this.copy()
    if (scenarioRun.state?.isTerminal() != true) {
      // Compute and persist state if terminal
      val state = getScenarioRunStatus(organizationId, scenarioRun).state
      scenarioRun = scenarioRun.copy(state = state)
      if (state?.isTerminal() == true) {
        val scenarioRunAsMap = scenarioRun.asMapWithAdditionalData(this.workspaceId)
        cosmosCoreDatabase
            .getContainer("${organizationId}_scenario_data")
            .upsertItem(scenarioRunAsMap, PartitionKey(this.ownerId), CosmosItemRequestOptions())
      }
    }
    return scenarioRun
  }

  override fun getScenarioRunLogs(organizationId: String, scenariorunId: String): ScenarioRunLogs {
    val scenarioRun = findScenarioRunById(organizationId, scenariorunId)
    return workflowService.getScenarioRunLogs(scenarioRun)
  }

  override fun getScenarioRunCumulatedLogs(organizationId: String, scenariorunId: String): String {
    val scenarioRun = findScenarioRunById(organizationId, scenariorunId)
    val scenarioRunCumulatedLogs = workflowService.getScenarioRunCumulatedLogs(scenarioRun)
    logger.trace(scenarioRunCumulatedLogs)
    return scenarioRunCumulatedLogs
  }

  override fun getScenarioRuns(
      organizationId: String,
      workspaceId: String,
      scenarioId: String
  ): List<ScenarioRun> =
      cosmosCoreDatabase
          .getContainer("${organizationId}_scenario_data")
          .queryItems(
              SqlQuerySpec(
                  """
                            SELECT * FROM c
                              WHERE c.type = 'ScenarioRun'
                                AND c.workspaceId = @WORKSPACE_ID
                                AND c.scenarioId = @SCENARIO_ID
                          """.trimIndent(),
                  listOf(
                      SqlParameter("@WORKSPACE_ID", workspaceId),
                      SqlParameter("@SCENARIO_ID", scenarioId))),
              CosmosQueryRequestOptions(),
              // It would be much better to specify the Domain Type right away and
              // avoid the map operation, but we can't due
              // to the lack of customization of the Cosmos Client Object Mapper, as reported here :
              // https://github.com/Azure/azure-sdk-for-java/issues/12269
              JsonNode::class.java)
          .mapNotNull {
            it.toDomain<ScenarioRun>().withStateInformation(organizationId).withoutSensitiveData()
          }
          .toList()

  override fun getWorkspaceScenarioRuns(
      organizationId: String,
      workspaceId: String
  ): List<ScenarioRun> =
      cosmosCoreDatabase
          .getContainer("${organizationId}_scenario_data")
          .queryItems(
              SqlQuerySpec(
                  """
                            SELECT * FROM c
                              WHERE c.type = 'ScenarioRun'
                                AND c.workspaceId = @WORKSPACE_ID
                          """.trimIndent(),
                  listOf(SqlParameter("@WORKSPACE_ID", workspaceId))),
              CosmosQueryRequestOptions(),
              // It would be much better to specify the Domain Type right away and
              // avoid the map operation, but we can't due
              // to the lack of customization of the Cosmos Client Object Mapper, as reported here :
              // https://github.com/Azure/azure-sdk-for-java/issues/12269
              JsonNode::class.java)
          .mapNotNull {
            it.toDomain<ScenarioRun>().withStateInformation(organizationId).withoutSensitiveData()
          }
          .toList()

  @EventListener(DeleteHistoricalDataScenario::class)
  fun deleteHistoricalDataScenarioRun(data: DeleteHistoricalDataScenario) {
    deleteHistoricalDataScenario(data.organizationId, data.workspaceId, data.scenarioId, false)
  }

  @EventListener(ScenarioDataDownloadRequest::class)
  fun onScenarioDataDownloadRequest(scenarioDataDownloadRequest: ScenarioDataDownloadRequest) {
    val startInfo =
        containerFactory.getStartInfo(
            scenarioDataDownloadRequest.organizationId,
            scenarioDataDownloadRequest.workspaceId,
            scenarioDataDownloadRequest.scenarioId,
            scenarioDataDownload = true,
            scenarioDataDownloadJobId = scenarioDataDownloadRequest.jobId)
    logger.debug(startInfo.toString())
    scenarioDataDownloadRequest.response =
        workflowService
            .launchScenarioRun(startInfo.startContainers, null)
            .asMapWithAdditionalData(scenarioDataDownloadRequest.workspaceId)
  }

  @EventListener(ScenarioDataDownloadJobInfoRequest::class)
  fun onScenarioDataDownloadJobInfoRequest(
      scenarioDataDownloadJobInfoRequest: ScenarioDataDownloadJobInfoRequest
  ) {
    val jobId = scenarioDataDownloadJobInfoRequest.jobId
    val workflowStatusAndArtifactList =
        this.workflowService.findWorkflowStatusAndArtifact(
            "$CSM_JOB_ID_LABEL_KEY=${jobId}", SCENARIO_DATA_DOWNLOAD_ARTIFACT_NAME)
    if (workflowStatusAndArtifactList.isNotEmpty()) {
      scenarioDataDownloadJobInfoRequest.response =
          workflowStatusAndArtifactList[0].status to
              (workflowStatusAndArtifactList[0].artifactContent ?: "")
    }
  }

  override fun runScenario(
      organizationId: String,
      workspaceId: String,
      scenarioId: String
  ): ScenarioRun {

    val startInfo =
        containerFactory.getStartInfo(
            organizationId,
            workspaceId,
            scenarioId,
        )
    logger.debug(startInfo.toString())
    val scenarioRunRequest =
        workflowService.launchScenarioRun(
            startInfo.startContainers, startInfo.runTemplate.executionTimeout)
    val scenarioRun =
        this.dbCreateScenarioRun(
            scenarioRunRequest,
            organizationId,
            workspaceId,
            scenarioId,
            startInfo.csmSimulationId,
            startInfo.scenario,
            startInfo.workspace,
            startInfo.solution,
            startInfo.runTemplate,
            startInfo.startContainers,
        )

    this.eventPublisher.publishEvent(
        ScenarioRunStartedForScenario(
            this,
            scenarioRun.organizationId!!,
            scenarioRun.workspaceId!!,
            scenarioRun.scenarioId!!,
            ScenarioRunStartedForScenario.ScenarioRunData(
                scenarioRun.id!!,
                scenarioRun.csmSimulationRun!!,
            ),
            ScenarioRunStartedForScenario.WorkflowData(
                scenarioRun.workflowId!!, scenarioRun.workflowName!!)))

    val workspace = workspaceService.findWorkspaceById(organizationId, workspaceId)
    sendScenarioRunMetaData(organizationId, workspace, scenarioId, scenarioRun.csmSimulationRun)

    val purgeHistoricalDataConfiguration =
        startInfo.runTemplate?.deleteHistoricalData ?: DeleteHistoricalData()
    if (purgeHistoricalDataConfiguration.enable) {
      logger.debug("Start coroutine to poll simulation status")
      GlobalScope.launch {
        withTimeout(
            purgeHistoricalDataConfiguration.timeOut?.toLong()
                ?: DELETE_SCENARIO_RUN_DEFAULT_TIMEOUT) {
          deletePreviousSimulationDataIfCurrentSimulationIsSuccessful(
              scenarioRun, purgeHistoricalDataConfiguration)
        }
      }
      logger.debug("Coroutine to poll simulation status launched")
    }
    return scenarioRun.withoutSensitiveData()!!
  }

  private fun deletePreviousSimulationDataIfCurrentSimulationIsSuccessful(
      currentRun: ScenarioRun,
      purgeHistoricalDataConfiguration: DeleteHistoricalData
  ) {
    val scenarioRunId = currentRun.id!!
    val workspaceId = currentRun.workspaceId!!
    val organizationId = currentRun.organizationId!!
    val scenarioId = currentRun.scenarioId!!
    val csmSimulationRun = currentRun.csmSimulationRun
    var scenarioRunStatus = getScenarioRunStatus(organizationId, scenarioRunId).state!!.value
    while (scenarioRunStatus != ScenarioRunState.Successful.value &&
        scenarioRunStatus != ScenarioRunState.Failed.value &&
        scenarioRunStatus != ScenarioRunState.DataIngestionFailure.value) {
      logger.info("ScenarioRun {} is still running, waiting for purging data", csmSimulationRun)
      logger.info("Scenario Status => {}", scenarioRunStatus)
      Thread.sleep(purgeHistoricalDataConfiguration.pollFrequency!!.toLong())
      scenarioRunStatus = getScenarioRunStatus(organizationId, scenarioRunId).state!!.value
    }
    if (scenarioRunStatus == ScenarioRunState.Successful.value) {
      logger.info("ScenarioRun {} is Successfull => purging data", csmSimulationRun)
      deleteScenarioRunsByScenarioWithoutAccessEnforcement(organizationId, workspaceId, scenarioId, false)
    } else {
      logger.info(
          "ScenarioRun {} is in error {} => no purging data", csmSimulationRun, scenarioRunStatus)
    }
  }

  override fun searchScenarioRuns(
      organizationId: String,
      scenarioRunSearch: ScenarioRunSearch
  ): List<ScenarioRun> {
    val scenarioRunSearchPredicatePair = scenarioRunSearch.toQueryPredicate()
    val andExpr =
        if (scenarioRunSearchPredicatePair.first.isNotBlank()) {
          " AND ( ${scenarioRunSearchPredicatePair.first} )"
        } else {
          ""
        }
    return cosmosCoreDatabase
        .getContainer("${organizationId}_scenario_data")
        .queryItems(
            SqlQuerySpec(
                """
                            SELECT * FROM c
                              WHERE c.type = 'ScenarioRun'
                              $andExpr
                          """.trimIndent(),
                scenarioRunSearchPredicatePair.second),
            CosmosQueryRequestOptions(),
            // It would be much better to specify the Domain Type right away and
            // avoid the map operation, but we can't due
            // to the lack of customization of the Cosmos Client Object Mapper, as reported here :
            // https://github.com/Azure/azure-sdk-for-java/issues/12269
            JsonNode::class.java)
        .mapNotNull {
          it.toDomain<ScenarioRun>().withStateInformation(organizationId).withoutSensitiveData()
        }
        .toList()
  }

  override fun startScenarioRunContainers(
      organizationId: String,
      scenarioRunStartContainers: ScenarioRunStartContainers
  ): ScenarioRun {
    val scenarioRunRequest = workflowService.launchScenarioRun(scenarioRunStartContainers, null)
    return this.dbCreateScenarioRun(
            scenarioRunRequest,
            organizationId,
            "None",
            "None",
            scenarioRunStartContainers.csmSimulationId,
            null,
            null,
            null,
            null,
            scenarioRunStartContainers,
        )
        .withoutSensitiveData()!!
  }

  private fun dbCreateScenarioRun(
      scenarioRunRequest: ScenarioRun,
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      csmSimulationId: String,
      scenario: Scenario?,
      workspace: Workspace?,
      solution: Solution?,
      runTemplate: RunTemplate?,
      startContainers: ScenarioRunStartContainers,
  ): ScenarioRun {

    val sendParameters =
        containerFactory.getSendOptionValue(
            workspace?.sendInputToDataWarehouse, runTemplate?.sendInputParametersToDataWarehouse)
    val sendDatasets =
        containerFactory.getSendOptionValue(
            workspace?.sendInputToDataWarehouse, runTemplate?.sendDatasetsToDataWarehouse)
    // Only send containers if admin or special route
    val scenarioRun =
        scenarioRunRequest.copy(
            id = idGenerator.generate("scenariorun", prependPrefix = "sr-"),
            ownerId = getCurrentAuthenticatedUserName(),
            csmSimulationRun = csmSimulationId,
            organizationId = organizationId,
            workspaceId = workspaceId,
            workspaceKey = workspace?.key,
            scenarioId = scenarioId,
            solutionId = solution?.id,
            runTemplateId = runTemplate?.id,
            generateName = startContainers.generateName,
            computeSize = runTemplate?.computeSize,
            noDataIngestionState = runTemplate?.noDataIngestionState,
            sdkVersion = solution?.sdkVersion,
            datasetList = scenario?.datasetList,
            parametersValues =
                (scenario?.parametersValues?.map { scenarioValue ->
                      RunTemplateParameterValue(
                          parameterId = scenarioValue.parameterId,
                          varType = scenarioValue.varType,
                          value = scenarioValue.value)
                    })
                    ?.toList(),
            nodeLabel = startContainers.nodeLabel,
            containers = startContainers.containers,
            sendDatasetsToDataWarehouse = sendDatasets,
            sendInputParametersToDataWarehouse = sendParameters,
        )

    val scenarioRunAsMap = scenarioRun.asMapWithAdditionalData(workspaceId)
    // We cannot use cosmosTemplate as it expects the Domain object to contain a field named 'id'
    // or annotated with @Id
    if (cosmosCoreDatabase
        .getContainer("${organizationId}_scenario_data")
        .createItem(scenarioRunAsMap, PartitionKey(scenarioRun.ownerId), CosmosItemRequestOptions())
        .item == null) {
      throw IllegalArgumentException("No ScenarioRun returned in response: $scenarioRunAsMap")
    }

    return scenarioRun
  }

  override fun getScenarioRunStatus(organizationId: String, scenariorunId: String) =
      getScenarioRunStatus(
          organizationId,
          this.findScenarioRunById(organizationId, scenariorunId, withStateInformation = false))

  private fun getScenarioRunStatus(
      organizationId: String,
      scenarioRun: ScenarioRun,
  ): ScenarioRunStatus {
    val scenarioRunStatus = this.workflowService.getScenarioRunStatus(scenarioRun)
    // Check if SDK version used to build the Solution enable control plane for data ingestion: SDK
    // >= 8.5
    var versionWithDataIngestionState = true
    if (scenarioRun.sdkVersion != null) {
      logger.debug(
          "SDK version for scenario run status detected: {}", scenarioRun.sdkVersion ?: "ERROR")
      val splitVersion = scenarioRun.sdkVersion?.split(".") ?: listOf()
      if (splitVersion.size < 2) {
        logger.error("Malformed SDK version for scenario run status data ingestion check")
      } else {
        val major = splitVersion[0].toIntOrNull()
        val minor = splitVersion[1].toIntOrNull()
        if (major == null || minor == null) {
          logger.error(
              "Malformed SDK version for scenario run status data ingestion check:" +
                  " use int for MAJOR and MINOR version")
        } else {
          versionWithDataIngestionState =
              ((major == MIN_SDK_VERSION_MAJOR && minor >= MIN_SDK_VERSION_MINOR) ||
                  (major > MIN_SDK_VERSION_MAJOR))
        }
      }
    }

    return scenarioRunStatus.copy(
        state =
            mapWorkflowPhaseToScenarioRunState(
                organizationId = organizationId,
                workspaceKey = scenarioRun.workspaceKey!!,
                scenarioRunId = scenarioRun.id,
                phase = scenarioRunStatus.phase,
                csmSimulationRun = scenarioRun.csmSimulationRun,
                // Determine whether we need to check data ingestion state, based on whether the
                // CSM_CONTROL_PLANE_TOPIC variable is present in any of the containers
                // And if the run template send data to datawarehouse with probe consumers
                checkDataIngestionState =
                    !(scenarioRun.noDataIngestionState ?: false) && versionWithDataIngestionState))
  }

  @EventListener(WorkflowPhaseToStateRequest::class)
  fun onWorkflowPhaseToStateRequest(request: WorkflowPhaseToStateRequest) {
    request.response =
        this.mapWorkflowPhaseToScenarioRunState(
                organizationId = request.organizationId,
                workspaceKey = request.workspaceKey,
                scenarioRunId = request.jobId,
                phase = request.workflowPhase,
                csmSimulationRun = null,
                checkDataIngestionState = false)
            .value
  }

  @EventListener(ScenarioRunEndToEndStateRequest::class)
  fun onScenarioRunEndToEndStateRequest(request: ScenarioRunEndToEndStateRequest) {
    request.response =
        this.findScenarioRunByIdOptional(request.organizationId, request.scenarioRunId)
            ?.state
            ?.value
  }

  @EventListener(ScenarioDeleted::class)
  @Async("csm-in-process-event-executor")
  fun onScenarioDeleted(event: ScenarioDeleted) {
    logger.debug(
        "Caught ScenarioDeleted event => deleting all runs linked to scenario {}", event.scenarioId)
    runBlocking {
      val jobs =
          this@ScenarioRunServiceImpl.getScenarioRuns(
                  event.organizationId, event.workspaceId, event.scenarioId)
              .map { scenarioRun ->
                GlobalScope.launch {
                  // TODO Consider using a smaller coroutine scope
                  this@ScenarioRunServiceImpl.deleteScenarioRunWithoutAccessEnforcement(scenarioRun)
                }
              }
      jobs.joinAll()
      if (jobs.isNotEmpty()) {
        logger.debug("Done deleting {} run(s) linked to scenario {}!", jobs.size, event.scenarioId)
      }
    }
  }

  private fun mapWorkflowPhaseToScenarioRunState(
      organizationId: String,
      workspaceKey: String,
      scenarioRunId: String?,
      phase: String?,
      csmSimulationRun: String?,
      checkDataIngestionState: Boolean? = null,
  ): ScenarioRunState {
    logger.debug("Mapping phase $phase for job $scenarioRunId")
    return when (phase) {
      "Pending", "Running" -> ScenarioRunState.Running
      "Succeeded" -> {
        logger.trace(
            "checkDataIngestionState=$checkDataIngestionState," +
                "csmSimulationRun=$csmSimulationRun")
        if (checkDataIngestionState == true && csmSimulationRun != null) {
          logger.debug(
              "ScenarioRun $scenarioRunId (csmSimulationRun=$csmSimulationRun) reported as " +
                  "Successful by the Workflow Service => checking data ingestion status..")
          val postProcessingState =
              this.azureDataExplorerClient.getStateFor(
                  organizationId = organizationId,
                  workspaceKey = workspaceKey,
                  scenarioRunId = scenarioRunId!!,
                  csmSimulationRun = csmSimulationRun,
              )
          logger.debug(
              "Data Ingestion status for ScenarioRun $scenarioRunId " +
                  "(csmSimulationRun=$csmSimulationRun): $postProcessingState")
          when (postProcessingState) {
            null, DataIngestionState.Unknown -> ScenarioRunState.Unknown
            DataIngestionState.InProgress -> ScenarioRunState.DataIngestionInProgress
            DataIngestionState.Successful -> ScenarioRunState.Successful
            DataIngestionState.Failure -> ScenarioRunState.Failed
          }
        } else {
          ScenarioRunState.Successful
        }
      }
      "Skipped", "Failed", "Error", "Omitted" -> ScenarioRunState.Failed
      else -> {
        logger.warn(
            "Unhandled state response for job {}: {} => returning Unknown as state",
            scenarioRunId,
            phase)
        ScenarioRunState.Unknown
      }
    }
  }

  @EventListener(ScenarioRunEndTimeRequest::class)
  fun onScenarioRunWorkflowEndTimeRequest(scenarioRunEndTimeRequest: ScenarioRunEndTimeRequest) {
    val scenarioRun =
        findScenarioRunById(
            scenarioRunEndTimeRequest.organizationId,
            scenarioRunEndTimeRequest.scenarioRunId,
            withStateInformation = false)
    val endTimeString = this.workflowService.getScenarioRunStatus(scenarioRun).endTime
    val endTime = endTimeString?.let(ZonedDateTime::parse)
    scenarioRunEndTimeRequest.response = endTime
  }

  override fun stopScenarioRun(organizationId: String, scenariorunId: String): ScenarioRunStatus {
    return workflowService.stopWorkflow(findScenarioRunById(organizationId, scenariorunId))
  }

  private fun sendScenarioRunMetaData(
      organizationId: String,
      workspace: Workspace,
      scenarioId: String,
      simulationRun: String
  ) {
    if (workspace.sendScenarioMetadataToEventHub != true) {
      return
    }

    if (workspace.useDedicatedEventHubNamespace != true) {
      logger.error(
          "workspace must be configured with useDedicatedEventHubNamespace to true in order to send metadata")
      return
    }

    val eventBus = csmPlatformProperties.azure?.eventBus!!
    val eventHubNamespace = "${organizationId}-${workspace.key}".lowercase()
    val eventHubName = "scenariorunmetadata"
    val baseHostName = "${eventHubNamespace}.servicebus.windows.net".lowercase()

    val scenarioMetaData =
        ScenarioRunMetaData(
            simulationRun, scenarioId, ZonedDateTime.now().toLocalDateTime().toString())

    when (eventBus.authentication.strategy) {
      SHARED_ACCESS_POLICY -> {
        azureEventHubsClient.sendMetaData(
            baseHostName,
            eventHubName,
            eventBus.authentication.sharedAccessPolicy?.namespace?.name!!,
            eventBus.authentication.sharedAccessPolicy?.namespace?.key!!,
            scenarioMetaData)
      }
      TENANT_CLIENT_CREDENTIALS -> {
        azureEventHubsClient.sendMetaData(baseHostName, eventHubName, scenarioMetaData)
      }
    }
  }
}
