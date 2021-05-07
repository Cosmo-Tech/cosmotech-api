// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun

import com.azure.cosmos.models.*
import com.cosmotech.api.AbstractCosmosBackedService
import com.cosmotech.api.argo.WorkflowUtils
import com.cosmotech.api.utils.convertToMap
import com.cosmotech.api.utils.toDomain
import com.cosmotech.scenariorun.api.ScenariorunApiService
import com.cosmotech.scenariorun.domain.ScenarioRun
import com.cosmotech.scenariorun.domain.ScenarioRunContainerLogs
import com.cosmotech.scenariorun.domain.ScenarioRunLogs
import com.cosmotech.scenariorun.domain.ScenarioRunSearch
import com.cosmotech.scenariorun.domain.ScenarioRunStart
import com.cosmotech.scenariorun.domain.ScenarioRunStartContainers
import com.cosmotech.scenariorun.domain.ScenarioRunStartSolution
import com.fasterxml.jackson.databind.JsonNode
import io.argoproj.workflow.ApiException
import io.argoproj.workflow.Configuration
import io.argoproj.workflow.apis.WorkflowServiceApi
import io.argoproj.workflow.models.*
import java.util.*
import kotlin.reflect.full.memberProperties
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["csm.platform.vendor"], havingValue = "azure", matchIfMissing = true)
class ScenariorunServiceImpl(
    val argoAdapter: ArgoAdapter,
    @Value("\${csm.platform.argo.base-url:}") val argoBaseUrl: String,
    val workflowUtils: WorkflowUtils
) : AbstractCosmosBackedService(), ScenariorunApiService {

  private val CSM_K8S_NAMESPACE = "phoenix"

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
    cosmosTemplate.deleteEntity(
        "${organizationId}_scenario_data", this.findScenarioRunById(organizationId, scenariorunId))
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

  override fun getScenarioRunLogs(
      organizationId: String,
      scenariorunId: String
  ): ScenarioRunLogs {
    val scenario = findScenarioRunById(organizationId, scenariorunId)
    val workflowId = scenario.workflowId
    val workflowName = scenario.workflowName
    var containersLogs: Map<String, ScenarioRunContainerLogs> = mapOf()
    if (workflowId != null && workflowName != null) {
      val workflow = workflowUtils.getActiveWorkflow(workflowId, workflowName)
      var nodeLogs =
            workflowUtils.getWorkflowLogs(workflow)
      containersLogs = nodeLogs.map {(nodeId, logs) ->
        nodeId to ScenarioRunContainerLogs(
          nodeId = nodeId,
          containerName = workflow.status?.nodes?.get(nodeId)?.displayName,
          children = workflow.status?.nodes?.get(nodeId)?.children,
          logs = logs
        )
      }.toMap()
    }
    val logs = 
        ScenarioRunLogs(
          scenariorunId = scenariorunId,
            containers = containersLogs)
    return logs
  }

  override fun getScenarioRunCumulatedLogs(
      organizationId: kotlin.String,
      scenariorunId: kotlin.String
  ): kotlin.String {
    val scenario = findScenarioRunById(organizationId, scenariorunId)
    val workflowId = scenario.workflowId
    val workflowName = scenario.workflowName
    var cumulatedLogs =
        if (workflowId != null && workflowName != null)
            workflowUtils.getCumulatedLogs(workflowId, workflowName)
        else ""
    logger.debug(cumulatedLogs)
    return cumulatedLogs
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

    // TODO Implement logic to submit Kubernetes Job

    val scenarioRun =
        ScenarioRun(
            id = idGenerator.generate("scenariorun", prependPrefix = "SR-"),
            scenarioId = scenarioId,
            workspaceId = workspaceId,
            // TODO Set other parameters here
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

    TODO("Not implemented yet")
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

    val defaultClient = Configuration.getDefaultApiClient()
    defaultClient.setVerifyingSsl(false)
    defaultClient.setBasePath(argoBaseUrl)

    val apiInstance = WorkflowServiceApi(defaultClient)
    val body = WorkflowCreateRequest()

    body.workflow(argoAdapter.buildWorkflow(scenarioRunStartContainers))

    try {
      val result = apiInstance.workflowServiceCreateWorkflow(CSM_K8S_NAMESPACE, body)
      if (result.metadata.uid == null)
          throw IllegalStateException("Argo Workflow metadata.uid is null")
      if (result.metadata.name == null)
          throw IllegalStateException("Argo Workflow metadata.name is null")
      val scenarioRun =
          this.dbCreateScenarioRun(
              organizationId, "None", "None", result.metadata.uid ?: "", result.metadata.name ?: "")

      return scenarioRun
    } catch (e: ApiException) {
      println("Exception when calling WorkflowServiceApi#workflowServiceCreateWorkflow")
      println("Status code: " + e.getCode())
      println("Reason: " + e.getResponseBody())
      println("Response headers: " + e.getResponseHeaders())
      e.printStackTrace()
      throw IllegalStateException(e)
    }
  }

  private fun dbCreateScenarioRun(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      workflowId: String,
      workflowName: String,
  ): ScenarioRun {

    // TODO Implement logic to submit Kubernetes Job

    val scenarioRun =
        ScenarioRun(
            id = idGenerator.generate("scenariorun", prependPrefix = "SR-"),
            scenarioId = scenarioId,
            workspaceId = workspaceId,
            workflowId = workflowId,
            workflowName = workflowName,
            // TODO Set other parameters here
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
}
