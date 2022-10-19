// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenario.azure

import com.azure.cosmos.models.CosmosContainerProperties
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
import com.cosmotech.api.events.DeleteHistoricalDataScenario
import com.cosmotech.api.events.DeleteHistoricalDataWorkspace
import com.cosmotech.api.events.OrganizationRegistered
import com.cosmotech.api.events.OrganizationUnregistered
import com.cosmotech.api.events.ScenarioDataDownloadJobInfoRequest
import com.cosmotech.api.events.ScenarioDataDownloadRequest
import com.cosmotech.api.events.ScenarioDatasetListChanged
import com.cosmotech.api.events.ScenarioDeleted
import com.cosmotech.api.events.ScenarioRunEndToEndStateRequest
import com.cosmotech.api.events.ScenarioRunStartedForScenario
import com.cosmotech.api.events.WorkflowPhaseToStateRequest
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.rbac.CsmRbac
import com.cosmotech.api.rbac.PERMISSION_CREATE_CHILDREN
import com.cosmotech.api.rbac.PERMISSION_DELETE
import com.cosmotech.api.rbac.PERMISSION_READ
import com.cosmotech.api.rbac.PERMISSION_READ_SECURITY
import com.cosmotech.api.rbac.PERMISSION_WRITE
import com.cosmotech.api.rbac.PERMISSION_WRITE_SECURITY
import com.cosmotech.api.rbac.ROLE_ADMIN
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.rbac.getScenarioRolesDefinition
import com.cosmotech.api.scenario.ScenarioMetaData
import com.cosmotech.api.utils.changed
import com.cosmotech.api.utils.compareToAndMutateIfNeeded
import com.cosmotech.api.utils.getCurrentAuthenticatedMail
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.api.utils.toDomain
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.azure.getRbac
import com.cosmotech.scenario.api.ScenarioApiService
import com.cosmotech.scenario.domain.Scenario
import com.cosmotech.scenario.domain.ScenarioAccessControl
import com.cosmotech.scenario.domain.ScenarioComparisonResult
import com.cosmotech.scenario.domain.ScenarioDataDownloadInfo
import com.cosmotech.scenario.domain.ScenarioDataDownloadJob
import com.cosmotech.scenario.domain.ScenarioJobState
import com.cosmotech.scenario.domain.ScenarioLastRun
import com.cosmotech.scenario.domain.ScenarioRole
import com.cosmotech.scenario.domain.ScenarioRunTemplateParameterValue
import com.cosmotech.scenario.domain.ScenarioSecurity
import com.cosmotech.scenario.domain.ScenarioValidationStatus
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.solution.domain.RunTemplate
import com.cosmotech.solution.domain.Solution
import com.cosmotech.workspace.api.WorkspaceApiService
import com.cosmotech.workspace.azure.getRbac
import com.cosmotech.workspace.azure.setRbac
import com.cosmotech.workspace.domain.Workspace
import com.fasterxml.jackson.databind.JsonNode
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["csm.platform.vendor"], havingValue = "azure", matchIfMissing = true)
@Suppress("LargeClass", "TooManyFunctions")
internal class ScenarioServiceImpl(
    private val solutionService: SolutionApiService,
    private val organizationService: OrganizationApiService,
    private val workspaceService: WorkspaceApiService,
    private val azureDataExplorerClient: AzureDataExplorerClient,
    private val azureEventHubsClient: AzureEventHubsClient,
    private val csmRbac: CsmRbac
) : CsmAzureService(), ScenarioApiService {

  val scenarioPermissions = getScenarioRolesDefinition()

  override fun addOrReplaceScenarioParameterValues(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      scenarioRunTemplateParameterValue: List<ScenarioRunTemplateParameterValue>
  ): List<ScenarioRunTemplateParameterValue> {
    if (scenarioRunTemplateParameterValue.isNotEmpty()) {
      val scenario = findScenarioById(organizationId, workspaceId, scenarioId)
      csmRbac.verify(scenario.getRbac(), PERMISSION_WRITE, scenarioPermissions)
      val parametersValuesMap =
          scenario.parametersValues?.associateBy { it.parameterId }?.toMutableMap()
              ?: mutableMapOf()
      parametersValuesMap.putAll(
          scenarioRunTemplateParameterValue
              .filter { it.parameterId.isNotBlank() }
              .map { it.copy(isInherited = false) }
              .associateBy { it.parameterId })
      scenario.parametersValues = parametersValuesMap.values.toMutableList()
      upsertScenarioData(organizationId, scenario, workspaceId)
    }
    return scenarioRunTemplateParameterValue
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
    val workspace = workspaceService.findWorkspaceById(organizationId, workspaceId)
    csmRbac.verify(workspace.getRbac(), PERMISSION_CREATE_CHILDREN)
    val solution =
        workspace.solution.solutionId?.let { solutionService.findSolutionById(organizationId, it) }
    val runTemplate =
        solution?.runTemplates?.find { runTemplate -> runTemplate.id == scenario.runTemplateId }
    if (scenario.runTemplateId != null && runTemplate == null) {
      throw IllegalArgumentException("Run Template not found: ${scenario.runTemplateId}")
    }

    var datasetList = scenario.datasetList
    val parentId = scenario.parentId
    var rootId: String? = null
    val newParametersValuesList = scenario.parametersValues?.toMutableList() ?: mutableListOf()

    if (parentId != null) {
      logger.debug("Applying / Overwriting Dataset list from parent $parentId")
      val parent = this.findScenarioByIdNoState(organizationId, workspaceId, parentId)
      datasetList = parent.datasetList
      rootId = parent.rootId
      if (rootId == null) {
        rootId = parentId
      }

      handleScenarioRunTemplateParametersValues(
          parentId, solution, runTemplate, parent, scenario, newParametersValuesList)
    }

    val currentUser = getCurrentAuthenticatedMail(this.csmPlatformProperties)
    val now = OffsetDateTime.now()
    val scenarioToSave =
        scenario.copy(
            id = idGenerator.generate("scenario"),
            ownerId = getCurrentAuthenticatedUserName(),
            solutionId = solution?.id,
            solutionName = solution?.name,
            runTemplateName = runTemplate?.name,
            creationDate = now,
            lastUpdate = now,
            state = ScenarioJobState.Created,
            datasetList = datasetList,
            rootId = rootId,
            parametersValues = newParametersValuesList,
            validationStatus = ScenarioValidationStatus.Draft,
            security = scenario.security ?: initSecurity(currentUser))
    val scenarioAsMap = scenarioToSave.asMapWithAdditionalData(workspaceId)
    // We cannot use cosmosTemplate as it expects the Domain object to contain a field named 'id'
    // or annotated with @Id
    if (cosmosCoreDatabase
        .getContainer("${organizationId}_scenario_data")
        .createItem(scenarioAsMap, PartitionKey(scenarioToSave.ownerId), CosmosItemRequestOptions())
        .item == null) {
      throw IllegalArgumentException("No Scenario returned in response: $scenarioAsMap")
    }

    sendScenarioMetaData(organizationId, workspace, scenarioToSave)
    return scenarioToSave
  }

  @Suppress("NestedBlockDepth")
  private fun handleScenarioRunTemplateParametersValues(
      parentId: String?,
      solution: Solution?,
      runTemplate: RunTemplate?,
      parent: Scenario,
      scenario: Scenario,
      newParametersValuesList: MutableList<ScenarioRunTemplateParameterValue>
  ) {
    logger.debug("Copying parameters values from parent $parentId")

    logger.debug("Getting runTemplate parameters ids")
    val runTemplateParametersIds =
        solution?.parameterGroups
            ?.filter { parameterGroup ->
              runTemplate?.parameterGroups?.contains(parameterGroup.id) == true
            }
            ?.flatMap { parameterGroup -> parameterGroup.parameters ?: mutableListOf() }
    if (!runTemplateParametersIds.isNullOrEmpty()) {
      val parentParameters = parent.parametersValues?.associate { it.parameterId to it }
      val scenarioParameters = scenario.parametersValues?.associate { it.parameterId to it }
      // TODO Handle default value
      runTemplateParametersIds.forEach { parameterId ->
        if (scenarioParameters?.contains(parameterId) != true) {
          logger.debug(
              "Parameter $parameterId is not defined in the Scenario. " +
                  "Checking if it is defined in its parent $parentId")
          if (parentParameters?.contains(parameterId) == true) {
            logger.debug("Copying parameter value from parent for parameter $parameterId")
            val parameterValue = parentParameters[parameterId]
            if (parameterValue != null) {
              parameterValue.isInherited = true
              newParametersValuesList.add(parameterValue)
            } else {
              logger.warn(
                  "Parameter $parameterId not found in parent ($parentId) parameters values")
            }
          } else {
            logger.debug(
                "Skipping parameter ${parameterId}, defined neither in the parent nor in this Scenario")
          }
        } else {
          logger.debug(
              "Skipping parameter $parameterId since it is already defined in this Scenario")
        }
      }
    }
  }

  override fun deleteScenario(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      waitRelationshipPropagation: Boolean
  ) {
    val scenario = this.findScenarioById(organizationId, workspaceId, scenarioId)
    csmRbac.verify(scenario.getRbac(), PERMISSION_DELETE, scenarioPermissions)

    cosmosTemplate.deleteEntity("${organizationId}_scenario_data", scenario)

    this.handleScenarioDeletion(organizationId, workspaceId, scenario, waitRelationshipPropagation)
    val workspace = workspaceService.findWorkspaceById(organizationId, workspaceId)
    deleteScenarioMetadata(organizationId, workspace.key, scenarioId)

    eventPublisher.publishEvent(ScenarioDeleted(this, organizationId, workspaceId, scenarioId))
  }

  private fun deleteScenarioMetadata(
      organizationId: String,
      workspaceKey: String,
      scenarioId: String
  ) {
    logger.debug(
        "Deleting scenario metadata. Organization: {}, Workspace: {}, scenarioId: {}",
        organizationId,
        workspaceKey,
        scenarioId)

    azureDataExplorerClient.deleteDataFromADXbyExtentShard(organizationId, workspaceKey, scenarioId)
    logger.debug("Scenario metadata deleted from ADX for scenario {}", scenarioId)
  }

  override fun downloadScenarioData(
      organizationId: String,
      workspaceId: String,
      scenarioId: String
  ): ScenarioDataDownloadJob {
    val scenario = this.findScenarioById(organizationId, workspaceId, scenarioId)
    csmRbac.verify(scenario.getRbac(), PERMISSION_READ)
    val resourceId =
        this.idGenerator.generate(scope = "scenariodatadownload", prependPrefix = "sdl-")
    val scenarioDataDownloadRequest =
        ScenarioDataDownloadRequest(this, resourceId, organizationId, workspaceId, scenario.id!!)
    this.eventPublisher.publishEvent(scenarioDataDownloadRequest)
    val scenarioDataDownloadResponse = scenarioDataDownloadRequest.response
    logger.debug("scenarioDataDownloadResponse={}", scenarioDataDownloadResponse)
    return ScenarioDataDownloadJob(id = resourceId)
  }

  override fun deleteAllScenarios(organizationId: String, workspaceId: String) {
    // TODO Only the workspace owner should be able to do this
    val workspace = workspaceService.findWorkspaceById(organizationId, workspaceId)
    csmRbac.verify(workspace.getRbac(), PERMISSION_WRITE)
    val scenarios = this.findAllScenariosStateOption(organizationId, workspaceId, false)
    scenarios.forEach {
      cosmosTemplate.deleteEntity("${organizationId}_scenario_data", it)
      eventPublisher.publishEvent(ScenarioDeleted(this, organizationId, workspaceId, it.id!!))
    }
  }

  /** See https://spaceport.cosmotech.com/jira/browse/PROD-7939 */
  private fun handleScenarioDeletion(
      organizationId: String,
      workspaceId: String,
      scenario: Scenario,
      waitRelationshipPropagation: Boolean
  ) {
    val parentId = scenario.parentId
    val children = this.findScenarioChildrenById(organizationId, workspaceId, scenario.id!!)
    val childrenUpdatesCoroutines =
        children.map { child ->
          GlobalScope.launch {
            // TODO Consider using a smaller coroutine scope
            child.parentId = parentId
            this@ScenarioServiceImpl.upsertScenarioData(organizationId, child, workspaceId)
          }
        }
    if (waitRelationshipPropagation) {
      runBlocking { childrenUpdatesCoroutines.joinAll() }
    }
  }

  override fun findAllScenarios(organizationId: String, workspaceId: String): List<Scenario> {
    var organization = organizationService.findOrganizationById(organizationId)
    csmRbac.verify(organization.getRbac(), PERMISSION_READ)
    val workspace = workspaceService.findWorkspaceById(organizationId, workspaceId)
    csmRbac.verify(workspace.getRbac(), PERMISSION_READ)
    return this.findAllScenariosStateOption(organizationId, workspaceId, true)
        .addLastRunsInfo(this, organizationId, workspaceId)
  }

  override fun findAllScenariosByValidationStatus(
      organizationId: String,
      workspaceId: String,
      validationStatus: ScenarioValidationStatus
  ): List<Scenario> =
      findAllScenarioByValidationStatus(organizationId, workspaceId, validationStatus.toString())

  internal fun findAllScenariosStateOption(
      organizationId: String,
      workspaceId: String,
      addState: Boolean
  ): List<Scenario> {
    val currentUser = getCurrentAuthenticatedMail(this.csmPlatformProperties)
    var templateQuery  = "SELECT * FROM c " +
              "WHERE c.type = 'Scenario' AND c.workspaceId = @WORKSPACE_ID"
    val params = mutableListOf(SqlParameter("@WORKSPACE_ID", workspaceId))
    if (this.csmPlatformProperties.rbac.enabled) {
      templateQuery  += " AND ARRAY_CONTAINS(c.security.accessControlList, {id: @ACL_USER}, true) " +
             "OR c.security.default NOT LIKE 'none'"
      params.add(SqlParameter("@ACL_USER", currentUser))
    }
    return cosmosCoreDatabase
        .getContainer("${organizationId}_scenario_data")
        .queryItems(
            SqlQuerySpec(templateQuery, params),
            CosmosQueryRequestOptions(),
            // It would be much better to specify the Domain Type right away and
            // avoid the map operation, but we can't due
            // to the lack of customization of the Cosmos Client Object Mapper, as reported here :
            // https://github.com/Azure/azure-sdk-for-java/issues/12269
            JsonNode::class.java)
        .mapNotNull {
          val scenario = it.toDomain<Scenario>()
          if (addState) {
            this.addStateToScenario(organizationId, scenario)
          }
          return@mapNotNull scenario
        }
  }

  private fun findAllScenarioByValidationStatus(
      organizationId: String,
      workspaceId: String,
      validationStatus: String
  ): List<Scenario> {
    val currentUser = getCurrentAuthenticatedMail(this.csmPlatformProperties)
    return cosmosCoreDatabase
        .getContainer("${organizationId}_scenario_data")
        .queryItems(
            SqlQuerySpec(
                "SELECT * FROM c WHERE c.type = 'Scenario' " +
                    "AND c.workspaceId = @WORKSPACE_ID " +
                    "AND c.validationStatus = @VALIDATION_STATUS" +
                    "AND ( ARRAY_CONTAINS(c.security.accessControlList, { id: @ACL_USER}, true) " +
                    "OR NOT IS_DEFINED(c.security) " +
                    "OR ARRAY_LENGTH(c.security.default) > 0 )",
                listOf(
                    SqlParameter("@WORKSPACE_ID", workspaceId),
                    SqlParameter("@VALIDATION_STATUS", validationStatus),
                    SqlParameter("@ACL_USER", currentUser))),
            CosmosQueryRequestOptions(),
            // It would be much better to specify the Domain Type right away and
            // avoid the map operation, but we can't due
            // to the lack of customization of the Cosmos Client Object Mapper, as reported here :
            // https://github.com/Azure/azure-sdk-for-java/issues/12269
            JsonNode::class.java)
        .mapNotNull { it.toDomain<Scenario>() }
        .toList()
  }

  private fun findAllScenariosByRootId(
      organizationId: String,
      workspaceId: String,
      rootId: String
  ): List<Scenario> {
    val currentUser = getCurrentAuthenticatedMail(this.csmPlatformProperties)
    return cosmosCoreDatabase
        .getContainer("${organizationId}_scenario_data")
        .queryItems(
            SqlQuerySpec(
                "SELECT * FROM c " +
                    "WHERE c.type = 'Scenario' " +
                    "AND c.workspaceId = @WORKSPACE_ID " +
                    "AND c.rootId = @ROOT_ID " +
                    "AND ( ARRAY_CONTAINS(c.security.accessControlList, { id: @ACL_USER}, true) " +
                    "OR NOT IS_DEFINED(c.security) " +
                    "OR ARRAY_LENGTH(c.security.default) > 0 )",
                listOf(
                    SqlParameter("@WORKSPACE_ID", workspaceId),
                    SqlParameter("@ROOT_ID", rootId),
                    SqlParameter("@ACL_USER", currentUser))),
            CosmosQueryRequestOptions(),
            // It would be much better to specify the Domain Type right away and
            // avoid the map operation, but we can't due
            // to the lack of customization of the Cosmos Client Object Mapper, as reported here :
            // https://github.com/Azure/azure-sdk-for-java/issues/12269
            JsonNode::class.java)
        .mapNotNull { it.toDomain<Scenario>() }
        .toList()
  }

  override fun findScenarioById(
      organizationId: String,
      workspaceId: String,
      scenarioId: String
  ): Scenario {
    var organization = organizationService.findOrganizationById(organizationId)
    csmRbac.verify(organization.getRbac(), PERMISSION_READ)
    val workspace = workspaceService.findWorkspaceById(organizationId, workspaceId)
    csmRbac.verify(workspace.getRbac(), PERMISSION_READ)
    val scenario =
        this.findScenarioByIdNoState(organizationId, workspaceId, scenarioId)
            .addLastRunsInfo(this, organizationId, workspaceId)
    csmRbac.verify(scenario.getRbac(), PERMISSION_READ, scenarioPermissions)
    this.addStateToScenario(organizationId, scenario)
    return scenario
  }

  override fun getScenarioValidationStatusById(
      organizationId: String,
      workspaceId: String,
      scenarioId: String
  ): ScenarioValidationStatus {
    val scenario = this.findScenarioById(organizationId, workspaceId, scenarioId)
    csmRbac.verify(scenario.getRbac(), PERMISSION_READ, scenarioPermissions)
    return scenario.validationStatus ?: ScenarioValidationStatus.Unknown
  }

  override fun getScenarioDataDownloadJobInfo(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      downloadId: String
  ): ScenarioDataDownloadInfo {
    val scenario = this.findScenarioById(organizationId, workspaceId, scenarioId)
    csmRbac.verify(scenario.getRbac(), PERMISSION_READ, scenarioPermissions)
    val scenarioDataDownloadJobInfoRequest =
        ScenarioDataDownloadJobInfoRequest(this, downloadId, organizationId)
    this.eventPublisher.publishEvent(scenarioDataDownloadJobInfoRequest)
    val response =
        scenarioDataDownloadJobInfoRequest.response
            ?: throw CsmResourceNotFoundException(
                "No scenario data download job found with id $downloadId for scenario ${scenario.id})")
    return ScenarioDataDownloadInfo(
        state = mapWorkflowPhaseToState(organizationId, workspaceId, downloadId, response.first),
        url = response.second)
  }

  internal fun findScenarioChildrenById(
      organizationId: String,
      workspaceId: String,
      parentId: String
  ) =
      cosmosCoreDatabase
          .getContainer("${organizationId}_scenario_data")
          .queryItems(
              SqlQuerySpec(
                  "SELECT * FROM c WHERE c.type = 'Scenario' " +
                      "AND c.workspaceId = @WORKSPACE_ID " +
                      "AND c.parentId = @PARENT_ID",
                  listOf(
                      SqlParameter("@WORKSPACE_ID", workspaceId),
                      SqlParameter("@PARENT_ID", parentId))),
              CosmosQueryRequestOptions(),
              // It would be much better to specify the Domain Type right away and
              // avoid the map operation, but we can't due
              // to the lack of customization of the Cosmos Client Object Mapper, as reported here :
              // https://github.com/Azure/azure-sdk-for-java/issues/12269
              JsonNode::class.java)
          .mapNotNull { it.toDomain<Scenario>() }
          .toList()

  internal fun findScenarioByIdNoState(
      organizationId: String,
      workspaceId: String,
      scenarioId: String
  ): Scenario =
      cosmosCoreDatabase
          .getContainer("${organizationId}_scenario_data")
          .queryItems(
              SqlQuerySpec(
                  "SELECT * FROM c " +
                      "WHERE c.type = 'Scenario' " +
                      "AND c.id = @SCENARIO_ID " +
                      "AND c.workspaceId = @WORKSPACE_ID",
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

  private fun addStateToScenario(organizationId: String, scenario: Scenario?) {
    if (scenario?.lastRun != null) {
      val scenarioRunId = scenario.lastRun?.scenarioRunId
      if (scenarioRunId.isNullOrBlank()) {
        throw IllegalStateException(
            "Scenario has a last Scenario Run but scenarioRunId is null or blank")
      }
      val endToEndStateRequest =
          ScenarioRunEndToEndStateRequest(
              this, organizationId, scenario.workspaceId!!, scenarioRunId)
      this.eventPublisher.publishEvent(endToEndStateRequest)
      scenario.state =
          when (endToEndStateRequest.response) {
            "Running" -> ScenarioJobState.Running
            "DataIngestionInProgress" -> ScenarioJobState.DataIngestionInProgress
            "Successful" -> ScenarioJobState.Successful
            "Failed", "DataIngestionFailure" -> ScenarioJobState.Failed
            else -> ScenarioJobState.Unknown
          }
    }
  }

  private fun mapWorkflowPhaseToState(
      organizationId: String,
      workspaceId: String,
      jobId: String?,
      phase: String?,
  ): ScenarioJobState {
    logger.debug("Mapping phase $phase for job $jobId")
    val workflowPhaseToStateRequest =
        WorkflowPhaseToStateRequest(
            publisher = this,
            organizationId = organizationId,
            workspaceKey = workspaceService.findWorkspaceById(organizationId, workspaceId).key,
            jobId = jobId,
            workflowPhase = phase)
    this.eventPublisher.publishEvent(workflowPhaseToStateRequest)
    return when (workflowPhaseToStateRequest.response) {
      "Running" -> ScenarioJobState.Running
      "Successful" -> ScenarioJobState.Successful
      "Failed" -> ScenarioJobState.Failed
      else -> {
        logger.warn(
            "Unhandled state response for job {}: {} => returning Unknown as state", jobId, phase)
        ScenarioJobState.Unknown
      }
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
    var organization = organizationService.findOrganizationById(organizationId)
    csmRbac.verify(organization.getRbac(), PERMISSION_READ)
    val workspace = workspaceService.findWorkspaceById(organizationId, workspaceId)
    csmRbac.verify(workspace.getRbac(), PERMISSION_READ)
    val scenario = findScenarioById(organizationId, workspaceId, scenarioId)
    csmRbac.verify(scenario.getRbac(), PERMISSION_WRITE, scenarioPermissions)
    if (!scenario.parametersValues.isNullOrEmpty()) {
      scenario.parametersValues = mutableListOf()
      scenario.lastUpdate = OffsetDateTime.now()

      upsertScenarioData(organizationId, scenario, workspaceId)
    }
  }

  override fun updateScenario(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      scenario: Scenario
  ): Scenario {
    var organization = organizationService.findOrganizationById(organizationId)
    csmRbac.verify(organization.getRbac(), PERMISSION_READ)
    val workspace = workspaceService.findWorkspaceById(organizationId, workspaceId)
    csmRbac.verify(workspace.getRbac(), PERMISSION_READ)
    val existingScenario = findScenarioById(organizationId, workspaceId, scenarioId)
    csmRbac.verify(scenario.getRbac(), PERMISSION_WRITE, scenarioPermissions)

    var hasChanged =
        existingScenario
            .compareToAndMutateIfNeeded(
                scenario,
                excludedFields =
                    arrayOf(
                        "ownerId",
                        "datasetList",
                        "solutionId",
                        "runTemplateId",
                        "parametersValues"))
            .isNotEmpty()

    var datasetListUpdated = false
    if (scenario.datasetList != null &&
        scenario.datasetList?.toSet() != existingScenario.datasetList?.toSet()) {
      // Only root Scenarios can update their Dataset list
      datasetListUpdated = updateDatasetList(scenario, existingScenario)
      if (datasetListUpdated) {
        hasChanged = true
      }
    }

    if (scenario.solutionId != null && scenario.changed(existingScenario) { solutionId }) {
      logger.debug("solutionId is a read-only property => ignored ! ")
    }

    if (scenario.runTemplateId != null && scenario.changed(existingScenario) { runTemplateId }) {
      updateScenarioRunTemplate(workspace, organizationId, scenario, existingScenario)
      hasChanged = true
    }

    if (scenario.parametersValues != null &&
        scenario.parametersValues?.toSet() != existingScenario.parametersValues?.toSet()) {
      updateScenarioParametersValues(existingScenario, scenario)
      hasChanged = true
    }

    if (hasChanged) {
      existingScenario.lastUpdate = OffsetDateTime.now()
      upsertScenarioData(organizationId, existingScenario, workspaceId)

      if (datasetListUpdated) {
        publishDatasetListChangedEvent(organizationId, workspaceId, scenarioId, scenario)
      }

      sendScenarioMetaData(organizationId, workspace, existingScenario)
    }

    return existingScenario
  }

  private fun updateDatasetList(scenario: Scenario, existingScenario: Scenario): Boolean {
    if (scenario.parentId != null) {
      logger.info(
          "Cannot set Dataset list on child Scenario ${scenario.id}. Only root scenarios can be set.")
      return false
    }
    // TODO Need to validate those IDs too ?
    existingScenario.datasetList = scenario.datasetList
    return true
  }

  private fun publishDatasetListChangedEvent(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      scenario: Scenario
  ) {
    this.eventPublisher.publishEvent(
        ScenarioDatasetListChanged(
            this, organizationId, workspaceId, scenarioId, scenario.datasetList))
  }

  private fun updateScenarioParametersValues(existingScenario: Scenario, scenario: Scenario) {
    existingScenario.parametersValues = scenario.parametersValues
    existingScenario.parametersValues?.forEach { it.isInherited = false }
  }

  private fun updateScenarioRunTemplate(
      workspace: Workspace,
      organizationId: String,
      scenario: Scenario,
      existingScenario: Scenario
  ) {
    // Validate the runTemplateId
    val solution =
        workspace.solution.solutionId?.let { solutionService.findSolutionById(organizationId, it) }
    val newRunTemplateId = scenario.runTemplateId
    val runTemplate =
        solution?.runTemplates?.find { it.id == newRunTemplateId }
            ?: throw IllegalArgumentException(
                "No run template '${newRunTemplateId}' in solution ${solution?.id}")
    existingScenario.runTemplateId = scenario.runTemplateId
    existingScenario.runTemplateName = runTemplate.name
  }

  internal fun upsertScenarioData(organizationId: String, scenario: Scenario, workspaceId: String) {
    scenario.lastUpdate = OffsetDateTime.now()
    cosmosCoreDatabase
        .getContainer("${organizationId}_scenario_data")
        .upsertItem(
            scenario.asMapWithAdditionalData(workspaceId),
            PartitionKey(scenario.ownerId),
            CosmosItemRequestOptions())
  }

  private fun sendScenarioMetaData(
      organizationId: String,
      workspace: Workspace,
      scenario: Scenario
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
    val eventHubName = "scenariometadata"
    val baseHostName = "${eventHubNamespace}.servicebus.windows.net".lowercase()

    val scenarioMetaData =
        ScenarioMetaData(
            organizationId,
            workspace.id!!,
            scenario.id!!,
            scenario.name ?: "",
            scenario.description ?: "",
            scenario.parentId ?: "",
            scenario.solutionName ?: "",
            scenario.runTemplateName ?: "",
            scenario.validationStatus.toString(),
            ZonedDateTime.now().toLocalDateTime().toString())

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

  @EventListener(DeleteHistoricalDataWorkspace::class)
  fun deleteHistoricalDataScenario(data: DeleteHistoricalDataWorkspace) {
    val organizationId = data.organizationId
    val workspaceId = data.workspaceId
    val scenarios: List<Scenario> = findAllScenarios(organizationId, workspaceId)
    for (scenario in scenarios) {
      this.eventPublisher.publishEvent(
          DeleteHistoricalDataScenario(this, organizationId, workspaceId, scenario.id!!))
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
                    scenarioRunStarted.scenarioRunData.scenarioRunId,
                    scenarioRunStarted.scenarioRunData.csmSimulationRun,
                    scenarioRunStarted.workflowData.workflowId,
                    scenarioRunStarted.workflowData.workflowName,
                )))
  }

  @EventListener(ScenarioDatasetListChanged::class)
  fun onScenarioDatasetListChanged(scenarioDatasetListChanged: ScenarioDatasetListChanged) {
    logger.debug("onScenarioDatasetListChanged ${scenarioDatasetListChanged}")
    val children =
        this.findAllScenariosByRootId(
            scenarioDatasetListChanged.organizationId,
            scenarioDatasetListChanged.workspaceId,
            scenarioDatasetListChanged.scenarioId)
    children.forEach {
      it.datasetList = scenarioDatasetListChanged.datasetList?.toMutableList() ?: mutableListOf()
      it.lastUpdate = OffsetDateTime.now()
      upsertScenarioData(
          scenarioDatasetListChanged.organizationId, it, scenarioDatasetListChanged.workspaceId)
    }
  }

  override fun getScenarioPermissions(
      organizationId: String,
      workspaceId: String,
      role: String
  ): List<String> {
    return com.cosmotech.api.rbac.getPermissions(role, getScenarioRolesDefinition())
  }

  override fun getScenarioSecurity(
      organizationId: String,
      workspaceId: String,
      scenarioId: String
  ): ScenarioSecurity {
    val scenario = findScenarioById(organizationId, workspaceId, scenarioId)
    csmRbac.verify(scenario.getRbac(), PERMISSION_READ_SECURITY, scenarioPermissions)
    return scenario.security as ScenarioSecurity
  }

  override fun setScenarioDefaultSecurity(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      scenarioRole: ScenarioRole
  ): ScenarioSecurity {
    val scenario = findScenarioByIdNoState(organizationId, workspaceId, scenarioId)
    csmRbac.verify(scenario.getRbac(), PERMISSION_WRITE_SECURITY, scenarioPermissions)
    val rbacSecurity = csmRbac.setDefault(scenario.getRbac(), scenarioRole.role)
    scenario.setRbac(rbacSecurity)
    upsertScenarioData(organizationId, scenario, workspaceId)
    return scenario.security as ScenarioSecurity
  }

  override fun getScenarioAccessControl(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      identityId: String
  ): ScenarioAccessControl {
    val scenario = findScenarioById(organizationId, workspaceId, scenarioId)
    csmRbac.verify(scenario.getRbac(), PERMISSION_READ_SECURITY, scenarioPermissions)
    val rbacAccessControl = csmRbac.getAccessControl(scenario.getRbac(), identityId)
    return ScenarioAccessControl(rbacAccessControl.id, rbacAccessControl.role)
  }

  override fun addScenarioAccessControl(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      scenarioAccessControl: ScenarioAccessControl
  ): ScenarioAccessControl {
    val scenario = findScenarioByIdNoState(organizationId, workspaceId, scenarioId)
    csmRbac.verify(scenario.getRbac(), PERMISSION_WRITE_SECURITY, scenarioPermissions)
    val workspace = workspaceService.findWorkspaceById(organizationId, workspaceId)
    val rbacSecurity =
        csmRbac.addUserRole(
            workspace.getRbac(),
            scenario.getRbac(),
            scenarioAccessControl.id,
            scenarioAccessControl.role,
            scenarioPermissions)
    scenario.setRbac(rbacSecurity)
    upsertScenarioData(organizationId, scenario, workspaceId)
    val rbacAccessControl = csmRbac.getAccessControl(scenario.getRbac(), scenarioAccessControl.id)
    return ScenarioAccessControl(rbacAccessControl.id, rbacAccessControl.role)
  }

  override fun updateScenarioAccessControl(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      identityId: String,
      scenarioRole: ScenarioRole
  ): ScenarioAccessControl {
    val scenario = findScenarioByIdNoState(organizationId, workspaceId, scenarioId)
    csmRbac.verify(scenario.getRbac(), PERMISSION_WRITE_SECURITY, scenarioPermissions)
    csmRbac.checkUserExists(
        scenario.getRbac(), identityId, "User '$identityId' not found in scenario $workspaceId")
    val rbacSecurity =
        csmRbac.setUserRole(scenario.getRbac(), identityId, scenarioRole.role, scenarioPermissions)
    scenario.setRbac(rbacSecurity)
    upsertScenarioData(organizationId, scenario, workspaceId)
    val rbacAccessControl = csmRbac.getAccessControl(scenario.getRbac(), identityId)
    return ScenarioAccessControl(rbacAccessControl.id, rbacAccessControl.role)
  }

  override fun removeScenarioAccessControl(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      identityId: String
  ) {
    val scenario = findScenarioById(organizationId, workspaceId, scenarioId)
    csmRbac.verify(scenario.getRbac(), PERMISSION_WRITE_SECURITY, scenarioPermissions)
    val rbacSecurity = csmRbac.removeUser(scenario.getRbac(), identityId, scenarioPermissions)
    scenario.setRbac(rbacSecurity)
    upsertScenarioData(organizationId, scenario, workspaceId)
  }

  override fun getScenarioSecurityUsers(
      organizationId: String,
      workspaceId: String,
      scenarioId: String
  ): List<String> {
    val scenario = findScenarioById(organizationId, workspaceId, scenarioId)
    csmRbac.verify(scenario.getRbac(), PERMISSION_READ_SECURITY, scenarioPermissions)
    return csmRbac.getUsers(scenario.getRbac())
  }

  private fun initSecurity(userId: String): ScenarioSecurity {
    return ScenarioSecurity(
        default = ROLE_NONE,
        accessControlList = mutableListOf(ScenarioAccessControl(userId, ROLE_ADMIN)))
  }
}
