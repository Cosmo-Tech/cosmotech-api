// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenario.azure

import com.azure.cosmos.models.*
import com.cosmotech.api.argo.WorkflowUtils
import com.cosmotech.api.azure.AbstractCosmosBackedService
import com.cosmotech.api.events.*
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.utils.changed
import com.cosmotech.api.utils.convertToMap
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.api.utils.toDomain
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.scenario.api.ScenarioApiService
import com.cosmotech.scenario.domain.Scenario
import com.cosmotech.scenario.domain.Scenario.State
import com.cosmotech.scenario.domain.ScenarioComparisonResult
import com.cosmotech.scenario.domain.ScenarioLastRun
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
    private val workspaceService: WorkspaceApiService,
    private val workflowUtils: WorkflowUtils,
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
      upsertScenarioData(organizationId, scenario, workspaceId)
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

    upsertScenarioData(organizationId, scenario, workspaceId)

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
            ownerId = getCurrentAuthenticatedUserName(),
            solutionId = solutionId,
            solutionName = solutionName,
            creationDate = now,
            lastUpdate = now,
            users = usersWithNames,
            state = State.Created,
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
    val scenario = this.findScenarioById(organizationId, workspaceId, scenarioId)

    if (scenario.ownerId != getCurrentAuthenticatedUserName()) {
      // TODO Only the owner or an admin should be able to perform this operation
      throw CsmAccessForbiddenException("You are not allowed to delete this Resource")
    }

    cosmosTemplate.deleteEntity("${organizationId}_scenario_data", scenario)
    // TODO Notify users
  }

  override fun deleteAllScenarios(organizationId: kotlin.String, workspaceId: kotlin.String) {
    // TODO Only the workspace owner should be able to do this
    val scenarios = this.findAllScenarios(organizationId, workspaceId)
    scenarios.forEach { cosmosTemplate.deleteEntity("${organizationId}_scenario_data", it) }
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
          .mapNotNull {
            val scenario = it.toDomain<Scenario>()
            this.addStateToScenario(scenario)
            return@mapNotNull scenario
          }
          .toList()

  override fun findScenarioById(
      organizationId: String,
      workspaceId: String,
      scenarioId: String
  ): Scenario {
    val scenario =
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
                // to the lack of customization of the Cosmos Client Object Mapper, as reported here
                // :
                // https://github.com/Azure/azure-sdk-for-java/issues/12269
                JsonNode::class.java)
            .firstOrNull()
            ?.toDomain<Scenario>()
            ?: throw java.lang.IllegalArgumentException(
                "Scenario #$scenarioId not found in workspace #$workspaceId in organization #$organizationId")
    this.addStateToScenario(scenario)
    return scenario
  }

  private fun addStateToScenario(scenario: Scenario?) {
    if (scenario != null && scenario.lastRun != null) {
      val workflowId = scenario.lastRun?.workflowId
      val workflowName = scenario.lastRun?.workflowName
      if (workflowId != null && workflowName != null) {
        val workflowStatus = workflowUtils.getWorkflowStatus(workflowId, workflowName)
        scenario.state = this.mapPhaseToState(workflowStatus?.phase)
      } else {
        throw IllegalStateException(
            "Scenario has a last Scenario Run but workflowId or workflowName is null")
      }
    }
  }

  private fun mapPhaseToState(phase: String?): State {
    logger.debug("Mapping phase ${phase}")
    when (phase) {
      "Pending" -> return State.Running
      "Running" -> return State.Running
      "Succeeded" -> return State.Successful
      "Skipped" -> return State.Failed
      "Failed" -> return State.Failed
      "Error" -> return State.Failed
      "Omitted" -> return State.Failed
      else -> return State.Failed
    }
  }

  override fun getScenariosTree(organizationId: String, workspaceId: String): List<Scenario> {
    return this.findAllScenarios(organizationId, workspaceId)
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

      upsertScenarioData(organizationId, scenario, workspaceId)
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

      upsertScenarioData(organizationId, scenario, workspaceId)

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
      upsertScenarioData(organizationId, scenario, workspaceId)
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

    if (scenario.ownerId != null && scenario.changed(existingScenario) { ownerId }) {
      // Allow to change the ownerId as well, but only the owner can transfer the ownership
      if (existingScenario.ownerId != getCurrentAuthenticatedUserName()) {
        // TODO Only the owner or an admin should be able to perform this operation
        throw CsmAccessForbiddenException(
            "You are not allowed to change the ownership of this Resource")
      }
      existingScenario.ownerId = scenario.ownerId
      hasChanged = true
    }

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

    if (scenario.lastRun != null && scenario.changed(existingScenario) { lastRun }) {
      existingScenario.lastRun = scenario.lastRun
      hasChanged = true
    }

    return if (hasChanged) {
      existingScenario.lastUpdate = OffsetDateTime.now()
      upsertScenarioData(organizationId, existingScenario, workspaceId)

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

  private fun upsertScenarioData(organizationId: String, scenario: Scenario, workspaceId: String) {
    cosmosCoreDatabase
        .getContainer("${organizationId}_scenario_data")
        .upsertItem(
            scenario.asMapWithAdditionalData(workspaceId),
            PartitionKey(scenario.ownerId),
            CosmosItemRequestOptions())
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

  @EventListener(ScenarioRunStartedForScenario::class)
  fun onScenarioRunStartedForScenario(scenarioRunStarted: ScenarioRunStartedForScenario) {
    logger.debug("onScenarioRunStartedForScenario ${scenarioRunStarted}")
    this.updateScenario(
        scenarioRunStarted.organizationId,
        scenarioRunStarted.workspaceId,
        scenarioRunStarted.scenarioId,
        Scenario(
            lastRun =
                ScenarioLastRun(
                    scenarioRunStarted.scenarioRunId,
                    scenarioRunStarted.csmSimulationRun,
                    scenarioRunStarted.workflowId,
                    scenarioRunStarted.workflowName,
                )))
  }
}
