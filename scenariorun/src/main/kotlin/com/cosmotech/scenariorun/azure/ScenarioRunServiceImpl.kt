// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun.azure

import com.azure.cosmos.models.CosmosItemRequestOptions
import com.azure.cosmos.models.CosmosQueryRequestOptions
import com.azure.cosmos.models.PartitionKey
import com.azure.cosmos.models.SqlParameter
import com.azure.cosmos.models.SqlQuerySpec
import com.cosmotech.api.azure.CsmAzureService
import com.cosmotech.api.events.ScenarioDataDownloadJobInfoRequest
import com.cosmotech.api.events.ScenarioDataDownloadRequest
import com.cosmotech.api.events.ScenarioRunEndToEndStateRequest
import com.cosmotech.api.events.ScenarioRunStartedForScenario
import com.cosmotech.api.events.WorkflowPhaseToStateRequest
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.scenariorun.DataIngestionState
import com.cosmotech.api.scenariorun.PostProcessingDataIngestionStateProvider
import com.cosmotech.api.utils.convertToMap
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.api.utils.toDomain
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
import com.cosmotech.scenariorun.domain.ScenarioRunStatus
import com.cosmotech.scenariorun.withoutSensitiveData
import com.cosmotech.scenariorun.workflow.WorkflowService
import com.cosmotech.solution.domain.RunTemplate
import com.cosmotech.solution.domain.Solution
import com.cosmotech.workspace.domain.Workspace
import com.fasterxml.jackson.databind.JsonNode
import kotlin.reflect.full.memberProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["csm.platform.vendor"], havingValue = "azure", matchIfMissing = true)
@Suppress("TooManyFunctions")
internal class ScenarioRunServiceImpl(
    private val containerFactory: ContainerFactory,
    private val workflowService: WorkflowService,
    private val postProcessingDataIngestionStateProvider: PostProcessingDataIngestionStateProvider,
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
    cosmosTemplate.deleteEntity("${organizationId}_scenario_data", scenarioRun)
  }

  override fun findScenarioRunById(organizationId: String, scenariorunId: String): ScenarioRun =
      cosmosCoreDatabase
          .getContainer("${organizationId}_scenario_data")
          .queryItems(
              SqlQuerySpec(
                  "SELECT * FROM c WHERE c.type = 'ScenarioRun' AND c.id = @SCENARIORUN_ID",
                  listOf(SqlParameter("@SCENARIORUN_ID", scenariorunId))),
              CosmosQueryRequestOptions(),
              // It would be much better to specify the Domain Type right away and
              // avoid the map operation, but we can't due
              // to the lack of customization of the Cosmos Client Object Mapper, as reported here :
              // https://github.com/Azure/azure-sdk-for-java/issues/12269
              JsonNode::class.java)
          .firstOrNull()
          ?.toDomain<ScenarioRun>()
          ?.withoutSensitiveData()
          ?: throw java.lang.IllegalArgumentException(
              "ScenarioRun #$scenariorunId not found in organization #$organizationId")

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
          .mapNotNull { it.toDomain<ScenarioRun>()?.withoutSensitiveData() }
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
          .mapNotNull { it.toDomain<ScenarioRun>()?.withoutSensitiveData() }
          .toList()

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
            .launchScenarioRun(startInfo.startContainers)
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
    val scenarioRunRequest = workflowService.launchScenarioRun(startInfo.startContainers)
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
    return scenarioRun.withoutSensitiveData()!!
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
        .mapNotNull { it.toDomain<ScenarioRun>().withoutSensitiveData() }
        .toList()
  }

  override fun startScenarioRunContainers(
      organizationId: String,
      scenarioRunStartContainers: ScenarioRunStartContainers
  ): ScenarioRun {
    val scenarioRunRequest = workflowService.launchScenarioRun(scenarioRunStartContainers)
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

  override fun getScenarioRunStatus(
      organizationId: String,
      scenariorunId: String
  ): ScenarioRunStatus {
    val scenarioRun = this.findScenarioRunById(organizationId, scenariorunId)
    val scenarioRunStatus = this.workflowService.getScenarioRunStatus(scenarioRun)
    return scenarioRunStatus.copy(
        state =
            mapWorkflowPhaseToScenarioRunState(
                organizationId,
                scenarioRun.workspaceKey!!,
                scenariorunId,
                scenarioRunStatus.phase,
                scenarioRun.csmSimulationRun))
  }

  @EventListener(WorkflowPhaseToStateRequest::class)
  fun onWorkflowPhaseToStateRequest(request: WorkflowPhaseToStateRequest) {
    request.response =
        this.mapWorkflowPhaseToScenarioRunState(
                organizationId = request.organizationId,
                workspaceKey = request.workspaceKey,
                scenarioRunId = request.jobId,
                phase = request.workflowPhase,
                csmSimulationRun = request.csmSimulationRun)
            .value
  }

  @EventListener(ScenarioRunEndToEndStateRequest::class)
  fun onScenarioRunEndToEndStateRequest(request: ScenarioRunEndToEndStateRequest) {
    request.response =
        this.findScenarioRunById(request.organizationId, request.scenarioRunId).state?.value
  }

  private fun mapWorkflowPhaseToScenarioRunState(
      organizationId: String,
      workspaceKey: String,
      scenarioRunId: String?,
      phase: String?,
      csmSimulationRun: String?
  ): ScenarioRunStatus.State {
    logger.debug("Mapping phase $phase for job $scenarioRunId")
    return when (phase) {
      "Pending", "Running" -> ScenarioRunStatus.State.Running
      "Succeeded" -> {
        if (csmSimulationRun != null) {
          logger.debug(
              "ScenarioRun $scenarioRunId (csmSimulationRun=$csmSimulationRun) reported as " +
                  "Successful by the Workflow Service => checking data ingestion status..")
          val postProcessingState =
              this.postProcessingDataIngestionStateProvider.getStateFor(
                  organizationId = organizationId,
                  workspaceKey = workspaceKey,
                  scenarioRunId = scenarioRunId!!,
                  //                  csmSimulationRun = "1e46cee6-1ea2-4da7-98a7-3bd81212a793",
                  csmSimulationRun = csmSimulationRun,
              )
          logger.debug(
              "Data Ingestion status for ScenarioRun $scenarioRunId " +
                  "(csmSimulationRun=$csmSimulationRun): $postProcessingState")
          when (postProcessingState) {
            null -> ScenarioRunStatus.State.Unknown
            DataIngestionState.InProgress -> ScenarioRunStatus.State.DataIngestionInProgress
            DataIngestionState.Successful -> ScenarioRunStatus.State.Successful
            DataIngestionState.Failure -> ScenarioRunStatus.State.Failed
          }
        } else {
          ScenarioRunStatus.State.Successful
        }
      }
      "Skipped", "Failed", "Error", "Omitted" -> ScenarioRunStatus.State.Failed
      else -> {
        logger.warn(
            "Unhandled state response for job {}: {} => returning Unknown as state",
            scenarioRunId,
            phase)
        ScenarioRunStatus.State.Unknown
      }
    }
  }
}
