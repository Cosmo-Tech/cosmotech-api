// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenario.service

import com.cosmotech.api.CsmPhoenixService
import com.cosmotech.api.azure.adx.AzureDataExplorerClient
import com.cosmotech.api.azure.eventhubs.AzureEventHubsClient
import com.cosmotech.api.config.CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication.Strategy.SHARED_ACCESS_POLICY
import com.cosmotech.api.config.CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication.Strategy.TENANT_CLIENT_CREDENTIALS
import com.cosmotech.api.events.DeleteHistoricalDataScenario
import com.cosmotech.api.events.DeleteHistoricalDataWorkspace
import com.cosmotech.api.events.OrganizationUnregistered
import com.cosmotech.api.events.ScenarioDataDownloadJobInfoRequest
import com.cosmotech.api.events.ScenarioDataDownloadRequest
import com.cosmotech.api.events.ScenarioDatasetListChanged
import com.cosmotech.api.events.ScenarioDeleted
import com.cosmotech.api.events.ScenarioLastRunChanged
import com.cosmotech.api.events.ScenarioRunEndToEndStateRequest
import com.cosmotech.api.events.ScenarioRunStartedForScenario
import com.cosmotech.api.events.WorkflowPhaseToStateRequest
import com.cosmotech.api.exceptions.CsmClientException
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
import com.cosmotech.api.rbac.getCommonRolesDefinition
import com.cosmotech.api.rbac.getScenarioRolesDefinition
import com.cosmotech.api.scenario.ScenarioMetaData
import com.cosmotech.api.utils.changed
import com.cosmotech.api.utils.compareToAndMutateIfNeeded
import com.cosmotech.api.utils.constructPageRequest
import com.cosmotech.api.utils.findAllPaginated
import com.cosmotech.api.utils.getCurrentAccountIdentifier
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.dataset.api.DatasetApiService
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.dataset.domain.SubDatasetGraphQuery
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.service.getRbac
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
import com.cosmotech.scenario.repository.ScenarioRepository
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.solution.domain.RunTemplate
import com.cosmotech.solution.domain.Solution
import com.cosmotech.workspace.api.WorkspaceApiService
import com.cosmotech.workspace.azure.EventHubRole
import com.cosmotech.workspace.azure.IWorkspaceEventHubService
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.service.getRbac
import java.time.Instant
import java.time.ZonedDateTime
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.springframework.context.event.EventListener
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
@Suppress("LargeClass", "TooManyFunctions", "LongParameterList")
internal class ScenarioServiceImpl(
    private val datasetService: DatasetApiService,
    private val solutionService: SolutionApiService,
    private val organizationService: OrganizationApiService,
    private val workspaceService: WorkspaceApiService,
    private val azureDataExplorerClient: AzureDataExplorerClient,
    private val azureEventHubsClient: AzureEventHubsClient,
    private val csmRbac: CsmRbac,
    private val workspaceEventHubService: IWorkspaceEventHubService,
    private val scenarioRepository: ScenarioRepository
) : CsmPhoenixService(), ScenarioApiService {

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
      upsertScenarioData(scenario)
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

  @Suppress("LongMethod")
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

    var scenarioSecurity = scenario.security
    if (scenarioSecurity == null) {
      scenarioSecurity = initSecurity(getCurrentAccountIdentifier(this.csmPlatformProperties))
    }

    val datasetCopyList =
        datasetList
            ?.map {
              val dataset = datasetService.findDatasetById(organizationId, it)
              when {
                dataset.twingraphId == null -> it
                dataset.status == Dataset.Status.COMPLETED ->
                    datasetService
                        .createSubDataset(
                            organizationId,
                            it,
                            SubDatasetGraphQuery(name = "Scenario - ${scenario.name})", main = false))
                        .id!!
                else -> throw CsmClientException("Dataset ${dataset.id} is not completed")
              }
            }
            ?.toMutableList()

    val now = Instant.now().toEpochMilli()
    val scenarioToSave =
        scenario.copy(
            id = idGenerator.generate("scenario"),
            ownerId = getCurrentAuthenticatedUserName(csmPlatformProperties),
            organizationId = organizationId,
            workspaceId = workspaceId,
            solutionId = solution?.id,
            solutionName = solution?.name,
            runTemplateName = runTemplate?.name,
            creationDate = now,
            lastUpdate = now,
            state = ScenarioJobState.Created,
            datasetList = datasetCopyList,
            rootId = rootId,
            parametersValues = newParametersValuesList,
            validationStatus = ScenarioValidationStatus.Draft,
            security = scenarioSecurity)

    scenarioRepository.save(scenarioToSave)

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
        solution
            ?.parameterGroups
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
                "Skipping parameter $parameterId, defined neither in the parent nor in this Scenario")
          }
        } else {
          logger.debug(
              "Skipping parameter $parameterId since it is already defined in this Scenario")
        }
      }
    }
  }

  override fun deleteScenario(organizationId: String, workspaceId: String, scenarioId: String) {
    val scenario = this.findScenarioById(organizationId, workspaceId, scenarioId)
    csmRbac.verify(scenario.getRbac(), PERMISSION_DELETE, scenarioPermissions)

    if (scenario.state == ScenarioJobState.Running)
        throw CsmClientException("Can't delete a running scenario : ${scenario.id}")
    scenarioRepository.delete(scenario)

    scenario.datasetList?.forEach {
      datasetService.deleteDataset(organizationId, it)
    }

    this.handleScenarioDeletion(organizationId, workspaceId, scenario)
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
    // This call verify by itself that we have the read authorization in the scenario
    val scenario = this.findScenarioById(organizationId, workspaceId, scenarioId)
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
    var pageable = Pageable.ofSize(csmPlatformProperties.twincache.scenario.defaultPageSize)

    do {
      val scenarioList =
          this.findPaginatedScenariosStateOption(
              organizationId, workspaceId, pageable.pageNumber, pageable.pageSize, false)
      scenarioList.forEach {
        scenarioRepository.delete(it)
        eventPublisher.publishEvent(ScenarioDeleted(this, organizationId, workspaceId, it.id!!))
      }
      pageable = pageable.next()
    } while (scenarioList.isNotEmpty())
  }

  /** See https://spaceport.cosmotech.com/jira/browse/PROD-7939 */
  private fun handleScenarioDeletion(
      organizationId: String,
      workspaceId: String,
      deletedScenario: Scenario,
  ) {
    val rootId = deletedScenario.rootId
    val children = this.findScenarioChildrenById(organizationId, workspaceId, deletedScenario.id!!)
    val childrenUpdatesCoroutines =
        children.map { child ->
          GlobalScope.launch {
            // TODO Consider using a smaller coroutine scope
            child.parentId = deletedScenario.parentId
            child.rootId = rootId
            if (child.parentId == null) {
              child.rootId = null
            }
            this@ScenarioServiceImpl.upsertScenarioData(child)
          }
        }
    runBlocking { childrenUpdatesCoroutines.joinAll() }
    if (rootId == null) children.forEach { updateRootId(organizationId, workspaceId, it) }
  }

  private fun updateRootId(organizationId: String, workspaceId: String, scenario: Scenario) {
    val rootId = if (scenario.rootId == null) scenario.id else scenario.rootId
    val children = this.findScenarioChildrenById(organizationId, workspaceId, scenario.id!!)
    val childrenUpdatesCoroutines =
        children.map { child ->
          GlobalScope.launch {
            // TODO Consider using a smaller coroutine scope
            child.rootId = rootId
            this@ScenarioServiceImpl.upsertScenarioData(child)
          }
        }
    runBlocking { childrenUpdatesCoroutines.joinAll() }
    children.forEach { updateRootId(organizationId, workspaceId, it) }
  }

  override fun findAllScenarios(
      organizationId: String,
      workspaceId: String,
      page: Int?,
      size: Int?
  ): List<Scenario> {
    // This call verify by itself that we have the read authorization in the workspace
    workspaceService.findWorkspaceById(organizationId, workspaceId)
    val defaultPageSize = csmPlatformProperties.twincache.scenario.defaultPageSize
    var pageable = constructPageRequest(page, size, defaultPageSize)
    if (pageable != null) {
      return this.findPaginatedScenariosStateOption(
              organizationId, workspaceId, pageable.pageNumber, pageable.pageSize, true)
          .addLastRunsInfo(this, organizationId, workspaceId)
    }
    return findAllPaginated(defaultPageSize) {
      this.findPaginatedScenariosStateOption(
              organizationId, workspaceId, it.pageNumber, it.pageSize, true)
          .addLastRunsInfo(this, organizationId, workspaceId)
          .toMutableList()
    }
  }

  override fun findAllScenariosByValidationStatus(
      organizationId: String,
      workspaceId: String,
      validationStatus: ScenarioValidationStatus,
      page: Int?,
      size: Int?
  ): List<Scenario> {
    // This call verify by itself that we have the read authorization in the workspace
    workspaceService.findWorkspaceById(organizationId, workspaceId)
    val status = validationStatus.toString()
    val defaultPageSize = csmPlatformProperties.twincache.scenario.defaultPageSize
    var pageRequest = constructPageRequest(page, size, defaultPageSize)
    val rbacEnabled = isRbacEnabled(organizationId, workspaceId)

    if (pageRequest != null) {
      if (rbacEnabled) {
        val currentUser = getCurrentAccountIdentifier(this.csmPlatformProperties)
        return scenarioRepository
            .findByValidationStatusAndSecurity(status, currentUser, pageRequest!!)
            .toList()
      } else {
        return scenarioRepository.findByValidationStatus(status, pageRequest!!).toList()
      }
    }

    var findAllScenarioByValidationStatus = mutableListOf<Scenario>()
    pageRequest = PageRequest.ofSize(csmPlatformProperties.twincache.scenario.defaultPageSize)
    do {
      var scenarioList: List<Scenario>
      if (rbacEnabled) {
        val currentUser = getCurrentAccountIdentifier(this.csmPlatformProperties)
        scenarioList =
            scenarioRepository
                .findByValidationStatusAndSecurity(status, currentUser, pageRequest!!)
                .toList()
      } else {
        scenarioList = scenarioRepository.findByValidationStatus(status, pageRequest!!).toList()
      }
      findAllScenarioByValidationStatus.addAll(scenarioList)
      pageRequest = pageRequest.next()
    } while (scenarioList.isNotEmpty())

    return findAllScenarioByValidationStatus
  }

  internal fun findPaginatedScenariosStateOption(
      organizationId: String,
      workspaceId: String,
      page: Int,
      size: Int,
      addState: Boolean
  ): List<Scenario> {
    val pageable = PageRequest.of(page, size)
    if (isRbacEnabled(organizationId, workspaceId)) {
      val currentUser = getCurrentAccountIdentifier(this.csmPlatformProperties)
      return scenarioRepository
          .findByWorkspaceIdAndSecurity(workspaceId, currentUser, pageable)
          .toList()
    }

    val scenarios = scenarioRepository.findByWorkspaceId(workspaceId, pageable).toList()
    if (addState) {
      scenarios.forEach { this.addStateToScenario(organizationId, it) }
    }

    return scenarios
  }

  private fun findAllScenariosByRootId(
      organizationId: String,
      workspaceId: String,
      rootId: String
  ): List<Scenario> {
    var pageable = PageRequest.ofSize(csmPlatformProperties.twincache.scenario.defaultPageSize)
    var findAllScenariosByRootId = mutableListOf<Scenario>()
    val rbacEnabled = isRbacEnabled(organizationId, workspaceId)
    do {
      var scenarioList: List<Scenario>
      if (rbacEnabled) {
        val currentUser = getCurrentAccountIdentifier(this.csmPlatformProperties)
        scenarioList =
            scenarioRepository.findByRootIdAndSecurity(rootId, currentUser, pageable).toList()
      } else {
        scenarioList = scenarioRepository.findByRootId(rootId, pageable).toList()
      }
      findAllScenariosByRootId.addAll(scenarioList)
      pageable = pageable.next()
    } while (scenarioList.isNotEmpty())

    return findAllScenariosByRootId
  }

  internal fun findScenarioChildrenById(
      organizationId: String,
      workspaceId: String,
      parentId: String
  ): List<Scenario> {
    var pageable = PageRequest.ofSize(csmPlatformProperties.twincache.scenario.defaultPageSize)
    var findScenarioChildrenById = mutableListOf<Scenario>()
    val rbacEnabled = isRbacEnabled(organizationId, workspaceId)
    do {
      var scenarioList: List<Scenario>
      if (rbacEnabled) {
        val currentUser = getCurrentAccountIdentifier(this.csmPlatformProperties)
        scenarioList =
            scenarioRepository.findByParentIdAndSecurity(parentId, currentUser, pageable).toList()
      } else {
        scenarioList = scenarioRepository.findByParentId(parentId, pageable).toList()
      }
      findScenarioChildrenById.addAll(scenarioList)
      pageable = pageable.next()
    } while (scenarioList.isNotEmpty())

    return findScenarioChildrenById
  }

  fun isRbacEnabled(organizationId: String, workspaceId: String): Boolean {
    val workspace = workspaceService.findWorkspaceById(organizationId, workspaceId)
    val isAdmin = csmRbac.isAdmin(workspace.getRbac(), getCommonRolesDefinition())
    return (!isAdmin && this.csmPlatformProperties.rbac.enabled)
  }

  override fun findScenarioById(
      organizationId: String,
      workspaceId: String,
      scenarioId: String
  ): Scenario {
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
    // This call verify by itself that we have the read authorization in the scenario
    val scenario = this.findScenarioById(organizationId, workspaceId, scenarioId)
    return scenario.validationStatus ?: ScenarioValidationStatus.Unknown
  }

  override fun getScenarioDataDownloadJobInfo(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      downloadId: String
  ): ScenarioDataDownloadInfo {
    // This call verify by itself that we have the read authorization in the scenario
    val scenario = this.findScenarioById(organizationId, workspaceId, scenarioId)
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
  internal fun findScenarioByIdNoState(
      organizationId: String,
      workspaceId: String,
      scenarioId: String
  ): Scenario =
      scenarioRepository.findBy(organizationId, workspaceId, scenarioId).orElseThrow {
        CsmResourceNotFoundException(
            "Resource of type '${Scenario::class.java.simpleName}' not found" +
                " with workspaceId=$workspaceId, scenarioId=$scenarioId")
      }

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
            "Failed",
            "DataIngestionFailure" -> ScenarioJobState.Failed
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
    val workspace = workspaceService.findWorkspaceById(organizationId, workspaceId)
    csmRbac.verify(workspace.getRbac(), PERMISSION_READ)

    var pageable = Pageable.ofSize(csmPlatformProperties.twincache.scenario.defaultPageSize)
    val scenarioTree = mutableListOf<Scenario>()

    do {
      val scenarioList =
          this.findPaginatedScenariosStateOption(
                  organizationId, workspaceId, pageable.pageNumber, pageable.pageSize, true)
              .addLastRunsInfo(this, organizationId, workspaceId)
      scenarioTree.addAll(scenarioList)
      pageable = pageable.next()
    } while (scenarioList.isNotEmpty())

    return scenarioTree
  }

  override fun removeAllScenarioParameterValues(
      organizationId: String,
      workspaceId: String,
      scenarioId: String
  ) {
    val scenario = findScenarioById(organizationId, workspaceId, scenarioId)
    csmRbac.verify(scenario.getRbac(), PERMISSION_WRITE, scenarioPermissions)
    if (!scenario.parametersValues.isNullOrEmpty()) {
      scenario.parametersValues = mutableListOf()
      scenario.lastUpdate = Instant.now().toEpochMilli()

      upsertScenarioData(scenario)
    }
  }
  @Suppress("LongMethod", "CyclomaticComplexMethod")
  override fun updateScenario(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      scenario: Scenario
  ): Scenario {
    // This call verify by itself that we have the read authorization in the organization
    var organization = organizationService.findOrganizationById(organizationId)
    // This call verify by itself that we have the read authorization in the workspace
    val workspace = workspaceService.findWorkspaceById(organizationId, workspaceId)
    val existingScenario = findScenarioById(organizationId, workspaceId, scenarioId)
    csmRbac.verify(existingScenario.getRbac(), PERMISSION_WRITE, scenarioPermissions)

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
                        "parametersValues",
                        "security"))
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

    if (scenario.security != null && existingScenario.security == null) {
      if (csmRbac.isAdmin(organization.getRbac(), getCommonRolesDefinition())) {
        existingScenario.security = scenario.security
        hasChanged = true
      } else {
        logger.warn(
            "Security cannot by updated directly without admin permissions for ${scenario.id}")
      }
    }

    if (hasChanged) {
      existingScenario.lastUpdate = Instant.now().toEpochMilli()
      upsertScenarioData(existingScenario)

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
                "No run template '$newRunTemplateId' in solution ${solution?.id}")
    existingScenario.runTemplateId = scenario.runTemplateId
    existingScenario.runTemplateName = runTemplate.name
  }
  internal fun upsertScenarioData(scenario: Scenario) {
    scenario.lastUpdate = Instant.now().toEpochMilli()
    scenarioRepository.save(scenario)
  }

  private fun sendScenarioMetaData(
      organizationId: String,
      workspace: Workspace,
      scenario: Scenario
  ) {
    val eventHubInfo =
        this.workspaceEventHubService.getWorkspaceEventHubInfo(
            organizationId, workspace, EventHubRole.SCENARIO_METADATA)
    if (!eventHubInfo.eventHubAvailable) {
      logger.warn(
          "Workspace must be configured with sendScenarioMetadataToEventHub to true in order to send metadata")
      return
    }

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

    when (eventHubInfo.eventHubCredentialType) {
      SHARED_ACCESS_POLICY -> {
        azureEventHubsClient.sendMetaData(
            eventHubInfo.eventHubNamespace,
            eventHubInfo.eventHubName,
            eventHubInfo.eventHubSasKeyName,
            eventHubInfo.eventHubSasKey,
            scenarioMetaData)
      }
      TENANT_CLIENT_CREDENTIALS -> {
        azureEventHubsClient.sendMetaData(
            eventHubInfo.eventHubNamespace, eventHubInfo.eventHubName, scenarioMetaData)
      }
    }
  }

  @EventListener(DeleteHistoricalDataWorkspace::class)
  fun deleteHistoricalDataScenario(data: DeleteHistoricalDataWorkspace) {
    val organizationId = data.organizationId
    val workspaceId = data.workspaceId
    val scenarioList = this.findAllScenarios(organizationId, workspaceId, null, null)
    scenarioList.forEach {
      this.eventPublisher.publishEvent(
          DeleteHistoricalDataScenario(
              this, organizationId, workspaceId, it.id!!, data.deleteUnknown))
    }
  }

  @EventListener(OrganizationUnregistered::class)
  @Async("csm-in-process-event-executor")
  fun onOrganizationUnregistered(organizationUnregistered: OrganizationUnregistered) {
    var pageable: Pageable =
        Pageable.ofSize(csmPlatformProperties.twincache.scenario.defaultPageSize)
    do {
      var scenarioToDelete =
          scenarioRepository
              .findByOrganizationId(organizationUnregistered.organizationId, pageable)
              .toList()
      scenarioRepository.deleteAll(scenarioToDelete)
      pageable = pageable.next()
    } while (scenarioToDelete.isNotEmpty())
  }

  @EventListener(ScenarioRunStartedForScenario::class)
  fun onScenarioRunStartedForScenario(scenarioRunStarted: ScenarioRunStartedForScenario) {
    logger.debug("onScenarioRunStartedForScenario $scenarioRunStarted")
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
    logger.debug("onScenarioDatasetListChanged $scenarioDatasetListChanged")
    val children =
        this.findAllScenariosByRootId(
            scenarioDatasetListChanged.organizationId,
            scenarioDatasetListChanged.workspaceId,
            scenarioDatasetListChanged.scenarioId)
    children.forEach {
      it.datasetList = scenarioDatasetListChanged.datasetList?.toMutableList() ?: mutableListOf()
      it.lastUpdate = Instant.now().toEpochMilli()
      upsertScenarioData(it)
    }
  }

  @EventListener(ScenarioLastRunChanged::class)
  fun onScenarioLastRunChanged(scenarioLastRunChanged: ScenarioLastRunChanged) {
    logger.debug("onScenarioLastRunChanged $scenarioLastRunChanged")
    this.upsertScenarioData(scenarioLastRunChanged.scenario as Scenario)
  }

  override fun getScenarioPermissions(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      role: String
  ): List<String> {
    val scenario = findScenarioById(organizationId, workspaceId, scenarioId)
    csmRbac.verify(scenario.getRbac(), PERMISSION_READ_SECURITY, scenarioPermissions)
    return com.cosmotech.api.rbac.getPermissions(role, getScenarioRolesDefinition())
  }

  override fun getScenarioSecurity(
      organizationId: String,
      workspaceId: String,
      scenarioId: String
  ): ScenarioSecurity {
    val scenario = findScenarioById(organizationId, workspaceId, scenarioId)
    csmRbac.verify(scenario.getRbac(), PERMISSION_READ_SECURITY, scenarioPermissions)
    return scenario.security
        ?: throw CsmResourceNotFoundException("RBAC not defined for ${scenario.id}")
  }

  override fun setScenarioDefaultSecurity(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      scenarioRole: ScenarioRole
  ): ScenarioSecurity {
    val scenario = findScenarioByIdNoState(organizationId, workspaceId, scenarioId)
    csmRbac.verify(scenario.getRbac(), PERMISSION_WRITE_SECURITY, scenarioPermissions)
    val rbacSecurity =
        csmRbac.setDefault(scenario.getRbac(), scenarioRole.role, scenarioPermissions)
    scenario.setRbac(rbacSecurity)
    upsertScenarioData(scenario)
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
    upsertScenarioData(scenario)
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
    upsertScenarioData(scenario)
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
    upsertScenarioData(scenario)
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
