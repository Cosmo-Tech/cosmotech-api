// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun.azure

import com.azure.cosmos.models.CosmosItemRequestOptions
import com.azure.cosmos.models.CosmosQueryRequestOptions
import com.azure.cosmos.models.PartitionKey
import com.azure.cosmos.models.SqlParameter
import com.azure.cosmos.models.SqlQuerySpec
import com.cosmotech.api.azure.AbstractCosmosBackedService
import com.cosmotech.api.events.ScenarioRunStartedForScenario
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.utils.convertToMap
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.api.utils.toDomain
import com.cosmotech.scenario.domain.Scenario
import com.cosmotech.scenariorun.ContainerFactory
import com.cosmotech.scenariorun.api.ScenariorunApiService
import com.cosmotech.scenariorun.domain.RunTemplateParameterValue
import com.cosmotech.scenariorun.domain.ScenarioRun
import com.cosmotech.scenariorun.domain.ScenarioRunLogs
import com.cosmotech.scenariorun.domain.ScenarioRunSearch
import com.cosmotech.scenariorun.domain.ScenarioRunStartContainers
import com.cosmotech.scenariorun.workflow.WorkflowService
import com.cosmotech.solution.domain.RunTemplate
import com.cosmotech.solution.domain.Solution
import com.cosmotech.workspace.domain.Workspace
import com.fasterxml.jackson.databind.JsonNode
import kotlin.reflect.full.memberProperties
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["csm.platform.vendor"], havingValue = "azure", matchIfMissing = true)
class ScenariorunServiceImpl(
    private val workflowService: WorkflowService,
) : AbstractCosmosBackedService(), ScenariorunApiService {

  @Autowired private lateinit var containerFactory: ContainerFactory

  protected fun ScenarioRun.asMapWithAdditionalData(workspaceId: String? = null): Map<String, Any> {
    val scenarioAsMap = this.convertToMap().toMutableMap()
    scenarioAsMap["type"] = "ScenarioRun"
    if (workspaceId != null) {
      scenarioAsMap["workspaceId"] = workspaceId
    }
    return scenarioAsMap
  }

  protected fun ScenarioRunSearch.toQueryPredicate(): Pair<String, List<SqlParameter>> {
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
          .mapNotNull { it.toDomain<ScenarioRun>() }
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
          .mapNotNull { it.toDomain<ScenarioRun>() }
          .toList()

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
            scenarioRun.id!!,
            scenarioRun.csmSimulationRun!!,
            scenarioRun.workflowId!!,
            scenarioRun.workflowName!!))
    return scenarioRun
  }

  override fun searchScenarioRuns(
      organizationId: String,
      scenarioRunSearch: ScenarioRunSearch
  ): List<ScenarioRun> {
    val scenarioRunSearchPredicatePair = scenarioRunSearch.toQueryPredicate()
    return cosmosCoreDatabase
        .getContainer("${organizationId}_scenario_data")
        .queryItems(
            SqlQuerySpec(
                """
                            SELECT * FROM c 
                              WHERE c.type = 'ScenarioRun' 
                              ${if (scenarioRunSearchPredicatePair.first.isNotBlank()) " AND ( ${scenarioRunSearchPredicatePair.first} )" else ""}
                          """.trimIndent(),
                scenarioRunSearchPredicatePair.second),
            CosmosQueryRequestOptions(),
            // It would be much better to specify the Domain Type right away and
            // avoid the map operation, but we can't due
            // to the lack of customization of the Cosmos Client Object Mapper, as reported here :
            // https://github.com/Azure/azure-sdk-for-java/issues/12269
            JsonNode::class.java)
        .mapNotNull { it.toDomain<ScenarioRun>() }
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
            id = idGenerator.generate("scenariorun", prependPrefix = "SR-"),
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

  override fun getScenarioRunStatus(organizationId: String, scenariorunId: String) =
      this.workflowService.getScenarioRunStatus(
          this.findScenarioRunById(organizationId, scenariorunId))
}
