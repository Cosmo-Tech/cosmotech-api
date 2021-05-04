// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenario

import com.azure.cosmos.models.*
import com.cosmotech.api.AbstractCosmosBackedService
import com.cosmotech.api.events.OrganizationRegistered
import com.cosmotech.api.events.OrganizationUnregistered
import com.cosmotech.api.utils.convertToMap
import com.cosmotech.api.utils.toDomain
import com.cosmotech.scenario.api.ScenarioApiService
import com.cosmotech.scenario.domain.Scenario
import com.cosmotech.scenario.domain.ScenarioComparisonResult
import com.cosmotech.scenario.domain.ScenarioRunTemplateParameterValue
import com.cosmotech.scenario.domain.ScenarioUser
import com.fasterxml.jackson.databind.JsonNode
import java.util.*
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["csm.platform.vendor"], havingValue = "azure", matchIfMissing = true)
class ScenarioServiceImpl : AbstractCosmosBackedService(), ScenarioApiService {

  protected fun Scenario.asMapWithAdditionalData(workspaceId: String): Map<String, Any> {
    val scenarioAsMap = this.convertToMap().toMutableMap()
    scenarioAsMap["type"] = "Scenario"
    scenarioAsMap["workspaceId"] = workspaceId
    return scenarioAsMap
  }

  override fun addOrReplaceScenarioParameterValues(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      scenarioRunTemplateParameterValue: List<ScenarioRunTemplateParameterValue>
  ): List<ScenarioRunTemplateParameterValue> {
    TODO("Not yet implemented")
  }

  override fun addOrReplaceUsersInScenario(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      scenarioUser: List<ScenarioUser>
  ): List<ScenarioUser> {
    TODO("Not yet implemented")
  }

  override fun compareScenarios(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      comparedScenarioId: String
  ): ScenarioComparisonResult {
    TODO("Not yet implemented")
  }

  override fun createScenario(
      organizationId: String,
      workspaceId: String,
      scenario: Scenario
  ): Scenario {
    val scenarioToSave = scenario.copy(id = idGenerator.generate("scenario"))
    val scenarioAsMap = scenarioToSave.asMapWithAdditionalData(workspaceId)
    // We cannot use cosmosTemplate as it expects the Domain object to contain a field named 'id'
    // or annotated with @Id
    if (cosmosCoreDatabase
        .getContainer("${organizationId}_scenario_data")
        .createItem(scenarioAsMap, PartitionKey(scenarioToSave.ownerId), CosmosItemRequestOptions())
        .item == null) {
      throw IllegalArgumentException("No Scenario returned in response: $scenarioAsMap")
    }
    return scenarioToSave
  }

  override fun deleteScenario(organizationId: String, workspaceId: String, scenarioId: String) {
    cosmosTemplate.deleteEntity(
        "${organizationId}_scenario_data",
        this.findScenarioById(organizationId, workspaceId, scenarioId))
  }

  override fun findAllScenarios(organizationId: String, workspaceId: String): List<Scenario> =
      cosmosCoreDatabase
          .getContainer("${organizationId}_scenario_data")
          .queryItems(
              SqlQuerySpec(
                  "SELECT * FROM c WHERE c.type = 'Scenario' AND c.workspaceId = @WORKSPACE_ID",
                  listOf(SqlParameter("@WORKSPACE_ID", workspaceId))),
              CosmosQueryRequestOptions(),
              // It would be much better to specify the Domain Type right away and
              // avoid the map operation, but we can't due
              // to the lack of customization of the Cosmos Client Object Mapper, as reported here :
              // https://github.com/Azure/azure-sdk-for-java/issues/12269
              JsonNode::class.java)
          .mapNotNull { it.toDomain<Scenario>() }
          .toList()

  override fun findScenarioById(
      organizationId: String,
      workspaceId: String,
      scenarioId: String
  ): Scenario =
      cosmosCoreDatabase
          .getContainer("${organizationId}_scenario_data")
          .queryItems(
              SqlQuerySpec(
                  "SELECT * FROM c WHERE c.type = 'Scenario' AND c.id = @SCENARIO_ID AND c.workspaceId = @WORKSPACE_ID",
                  listOf(
                      SqlParameter("@SCENARIO_ID", scenarioId),
                      SqlParameter("@WORKSPACE_ID", workspaceId))),
              CosmosQueryRequestOptions(),
              // It would be much better to specify the Domain Type right away and
              // avoid the map operation, but we can't due
              // to the lack of customization of the Cosmos Client Object Mapper, as reported here :
              // https://github.com/Azure/azure-sdk-for-java/issues/12269
              JsonNode::class.java)
          .firstOrNull()
          ?.toDomain<Scenario>()
          ?: throw java.lang.IllegalArgumentException(
              "Scenario #$scenarioId not found in workspace #$workspaceId in organization #$organizationId")

  override fun getScenariosTree(organizationId: String, workspaceId: String): List<Scenario> {
    TODO("Not yet implemented")
  }

  override fun removeAllScenarioParameterValues(
      organizationId: String,
      workspaceId: String,
      scenarioId: String
  ) {
    TODO("Not yet implemented")
  }

  override fun removeAllUsersOfScenario(
      organizationId: String,
      workspaceId: String,
      scenarioId: String
  ) {
    TODO("Not yet implemented")
  }

  override fun removeUserFromScenario(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      userId: String
  ) {
    TODO("Not yet implemented")
  }

  override fun updateScenario(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      scenario: Scenario
  ): Scenario {
    TODO("Not yet implemented")
  }

  @EventListener(OrganizationRegistered::class)
  fun onOrganizationRegistered(organizationRegistered: OrganizationRegistered) {
    cosmosCoreDatabase.createContainerIfNotExists(
        CosmosContainerProperties(
            "${organizationRegistered.organizationId}_scenario_data", "/ownerId"))
  }

  @EventListener(OrganizationUnregistered::class)
  @Async("csm-in-process-event-executor")
  fun onOrganizationUnregistered(organizationUnregistered: OrganizationUnregistered) {
    cosmosTemplate.deleteContainer("${organizationUnregistered.organizationId}_scenario_data")
  }
}
