// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenario.azure

import com.azure.cosmos.models.*
import com.cosmotech.api.AbstractCosmosBackedService
import com.cosmotech.api.events.*
import com.cosmotech.api.utils.changed
import com.cosmotech.api.utils.convertToMap
import com.cosmotech.api.utils.toDomain
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.scenario.api.ScenarioApiService
import com.cosmotech.scenario.domain.Scenario
import com.cosmotech.scenario.domain.ScenarioComparisonResult
import com.cosmotech.scenario.domain.ScenarioRunTemplateParameterValue
import com.cosmotech.scenario.domain.ScenarioUser
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.user.api.UserApiService
import com.cosmotech.user.domain.User
import com.cosmotech.workspace.api.WorkspaceApiService
import com.fasterxml.jackson.databind.JsonNode
import java.time.OffsetDateTime
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["csm.platform.vendor"], havingValue = "azure", matchIfMissing = true)
class ScenarioServiceImpl(
    private val userService: UserApiService,
    private val solutionService: SolutionApiService,
    private val organizationService: OrganizationApiService,
    private val workspaceService: WorkspaceApiService
) : AbstractCosmosBackedService(), ScenarioApiService {

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
    if (scenarioRunTemplateParameterValue.isNotEmpty()) {
      val scenario = findScenarioById(organizationId, workspaceId, scenarioId)
      val parametersValuesMap =
          scenario.parametersValues?.associateBy { it.parameterId }?.toMutableMap()
              ?: mutableMapOf()
      parametersValuesMap.putAll(
          scenarioRunTemplateParameterValue.filter { it.parameterId.isNotBlank() }.associateBy {
            it.parameterId
          })
      scenario.parametersValues = parametersValuesMap.values.toList()
      scenario.lastUpdate = OffsetDateTime.now()
      cosmosTemplate.upsert(
          "${organizationId}_scenario_data", scenario.asMapWithAdditionalData(workspaceId))
    }
    return scenarioRunTemplateParameterValue
  }

  private fun fetchUsers(userIds: Collection<String>): Map<String, User> =
      userIds.toSet().map { userService.findUserById(it) }.associateBy { it.id!! }

  override fun addOrReplaceUsersInScenario(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      scenarioUser: List<ScenarioUser>
  ): List<ScenarioUser> {
    if (scenarioUser.isEmpty()) {
      // Nothing to do
      return scenarioUser
    }

    val organization = organizationService.findOrganizationById(organizationId)
    val workspace = workspaceService.findWorkspaceById(organizationId, workspaceId)
    val scenario = findScenarioById(organizationId, workspaceId, scenarioId)

    val scenarioUserWithoutNullIds = scenarioUser.filter { it.id != null }
    val newUsersLoaded = fetchUsers(scenarioUserWithoutNullIds.mapNotNull { it.id })
    val scenarioUserWithRightNames =
        scenarioUserWithoutNullIds.map { it.copy(name = newUsersLoaded[it.id]!!.name!!) }
    val scenarioUserMap = scenarioUserWithRightNames.associateBy { it.id!! }

    val currentScenarioUsers =
        scenario.users?.filter { it.id != null }?.associateBy { it.id!! }?.toMutableMap()
            ?: mutableMapOf()

    newUsersLoaded.forEach { (userId, _) ->
      // Add or replace
      currentScenarioUsers[userId] = scenarioUserMap[userId]!!
    }
    scenario.users = currentScenarioUsers.values.toList()
    scenario.lastUpdate = OffsetDateTime.now()

    cosmosTemplate.upsert(
        "${organizationId}_scenario_data", scenario.asMapWithAdditionalData(workspaceId))

    // Roles might have changed => notify all users so they can update their own items
    scenario.users?.forEach { user ->
      this.eventPublisher.publishEvent(
          UserAddedToScenario(
              this,
              organizationId,
              organization.name!!,
              workspaceId,
              workspace.name,
              user.id!!,
              user.roles.map { role -> role.value }))
    }
    return scenarioUserWithRightNames
  }

  override fun compareScenarios(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      comparedScenarioId: String
  ): ScenarioComparisonResult {
    TODO("Not yet implemented")
  }

  private fun fetchSolutionIdAndName(
      organizationId: String,
      solutionId: String?
  ): Pair<String?, String?> {
    var solutionId: String? = null
    var solutionName: String? = null
    if (!solutionId.isNullOrBlank()) {
      // Validate
      val solution = solutionService.findSolutionById(organizationId, solutionId)
      solutionId = solution.id
      solutionName = solution.name
    }
    return solutionId to solutionName
  }

  override fun createScenario(
      organizationId: String,
      workspaceId: String,
      scenario: Scenario
  ): Scenario {
    val organization = organizationService.findOrganizationById(organizationId)
    val workspace = workspaceService.findWorkspaceById(organizationId, workspaceId)
    val (solutionId, solutionName) = fetchSolutionIdAndName(organizationId, scenario.solutionId)

    val usersLoaded = scenario.users?.map { it.id }?.let { fetchUsers(it) }
    val usersWithNames =
        usersLoaded?.let { scenario.users?.map { it.copy(name = usersLoaded[it.id]!!.name!!) } }

    val now = OffsetDateTime.now()
    val scenarioToSave =
        scenario.copy(
            id = idGenerator.generate("scenario"),
            solutionId = solutionId,
            solutionName = solutionName,
            creationDate = now,
            lastUpdate = now,
            users = usersWithNames,
        )
    val scenarioAsMap = scenarioToSave.asMapWithAdditionalData(workspaceId)
    // We cannot use cosmosTemplate as it expects the Domain object to contain a field named 'id'
    // or annotated with @Id
    if (cosmosCoreDatabase
        .getContainer("${organizationId}_scenario_data")
        .createItem(scenarioAsMap, PartitionKey(scenarioToSave.ownerId), CosmosItemRequestOptions())
        .item == null) {
      throw IllegalArgumentException("No Scenario returned in response: $scenarioAsMap")
    }

    // Roles might have changed => notify all users so they can update their own items
    scenario.users?.forEach { user ->
      this.eventPublisher.publishEvent(
          UserAddedToScenario(
              this,
              organizationId,
              organization.name!!,
              workspaceId,
              workspace.name,
              user.id!!,
              user.roles.map { role -> role.value }))
    }

    return scenarioToSave
  }

  override fun deleteScenario(organizationId: String, workspaceId: String, scenarioId: String) {
    cosmosTemplate.deleteEntity(
        "${organizationId}_scenario_data",
        this.findScenarioById(organizationId, workspaceId, scenarioId))
    // TODO Notify users
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
    val scenario = findScenarioById(organizationId, workspaceId, scenarioId)
    if (!scenario.parametersValues.isNullOrEmpty()) {
      scenario.parametersValues = listOf()
      scenario.lastUpdate = OffsetDateTime.now()
      cosmosTemplate.upsert(
          "${organizationId}_scenario_data", scenario.asMapWithAdditionalData(workspaceId))
    }
  }

  override fun removeAllUsersOfScenario(
      organizationId: String,
      workspaceId: String,
      scenarioId: String
  ) {
    val scenario = findScenarioById(organizationId, workspaceId, scenarioId)
    if (!scenario.users.isNullOrEmpty()) {
      val userIds = scenario.users!!.mapNotNull { it.id }
      scenario.users = listOf()
      scenario.lastUpdate = OffsetDateTime.now()
      cosmosTemplate.upsert(
          "${organizationId}_scenario_data", scenario.asMapWithAdditionalData(workspaceId))

      userIds.forEach {
        this.eventPublisher.publishEvent(
            UserRemovedFromScenario(this, organizationId, workspaceId, scenarioId, it))
      }
    }
  }

  override fun removeUserFromScenario(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      userId: String
  ) {
    val scenario = findScenarioById(organizationId, workspaceId, scenarioId)
    val scenarioUserMap = scenario.users?.associateBy { it.id!! }?.toMutableMap() ?: mutableMapOf()
    if (scenarioUserMap.containsKey(userId)) {
      scenarioUserMap.remove(userId)
      scenario.users = scenarioUserMap.values.toList()
      scenario.lastUpdate = OffsetDateTime.now()
      cosmosTemplate.upsert(
          "${organizationId}_scenario_data", scenario.asMapWithAdditionalData(workspaceId))
      this.eventPublisher.publishEvent(
          UserRemovedFromScenario(this, organizationId, workspaceId, scenarioId, userId))
    }
  }

  override fun updateScenario(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      scenario: Scenario
  ): Scenario {
    val existingScenario = findScenarioById(organizationId, workspaceId, scenarioId)
    val organization = organizationService.findOrganizationById(organizationId)
    val workspace = workspaceService.findWorkspaceById(organizationId, workspaceId)

    var hasChanged = false
    if (scenario.name != null && scenario.changed(existingScenario) { name }) {
      existingScenario.name = scenario.name
      hasChanged = true
    }
    if (scenario.description != null && scenario.changed(existingScenario) { description }) {
      existingScenario.description = scenario.description
      hasChanged = true
    }

    var userIdsRemoved: List<String>? = listOf()
    if (scenario.users != null) {
      // Specifying a list of users here overrides the previous list
      val usersToSet = fetchUsers(scenario.users!!.mapNotNull { it.id })
      userIdsRemoved =
          scenario.users?.mapNotNull { it.id }?.filterNot { usersToSet.containsKey(it) }
      val usersWithNames =
          usersToSet.let { scenario.users!!.map { it.copy(name = usersToSet[it.id]!!.name!!) } }
      existingScenario.users = usersWithNames
      hasChanged = true
    }

    if (scenario.tags != null && scenario.tags?.toSet() != existingScenario.tags?.toSet()) {
      existingScenario.tags = scenario.tags
      hasChanged = true
    }

    if (scenario.datasetList != null &&
        scenario.datasetList?.toSet() != existingScenario.datasetList?.toSet()) {
      // TODO Need to validate those IDs too ?
      existingScenario.datasetList = scenario.datasetList
      hasChanged = true
    }

    // TODO Allow to change the ownerId and ownerName as well, but only the owner can transfer the
    // ownership

    if (scenario.solutionId != null && scenario.changed(existingScenario) { solutionId }) {
      val (solutionId, solutionName) = fetchSolutionIdAndName(organizationId, scenario.solutionId)
      existingScenario.solutionId = solutionId
      existingScenario.solutionName = solutionName
      hasChanged = true
    }
    if (scenario.runTemplateId != null && scenario.changed(existingScenario) { runTemplateId }) {
      existingScenario.runTemplateId = scenario.runTemplateId
      hasChanged = true
    }
    if (scenario.runTemplateName != null &&
        scenario.changed(existingScenario) { runTemplateName }) {
      existingScenario.runTemplateName = scenario.runTemplateName
      hasChanged = true
    }

    if (scenario.parametersValues != null &&
        scenario.parametersValues?.toSet() != existingScenario.parametersValues?.toSet()) {
      existingScenario.parametersValues = scenario.parametersValues
      hasChanged = true
    }

    return if (hasChanged) {
      scenario.lastUpdate = OffsetDateTime.now()
      cosmosTemplate.upsert(
          "${organizationId}_datasets", existingScenario.asMapWithAdditionalData(workspaceId))

      userIdsRemoved?.forEach {
        this.eventPublisher.publishEvent(
            UserRemovedFromScenario(this, organizationId, workspaceId, scenarioId, it))
      }
      scenario.users?.forEach { user ->
        this.eventPublisher.publishEvent(
            UserAddedToScenario(
                this,
                organizationId,
                organization.name!!,
                workspaceId,
                workspace.name,
                user.id!!,
                user.roles.map { role -> role.value }))
      }

      scenario
    } else {
      existingScenario
    }
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
