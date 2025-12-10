// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenario.service

import com.cosmotech.api.CsmPhoenixService
import com.cosmotech.api.clients.EventBusClient
import com.cosmotech.api.clients.ResultDataClient
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
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.exceptions.CsmClientException
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.rbac.CsmRbac
import com.cosmotech.api.rbac.PERMISSION_CREATE_CHILDREN
import com.cosmotech.api.rbac.PERMISSION_DELETE
import com.cosmotech.api.rbac.PERMISSION_READ_SECURITY
import com.cosmotech.api.rbac.PERMISSION_WRITE
import com.cosmotech.api.rbac.PERMISSION_WRITE_SECURITY
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.rbac.ROLE_USER
import com.cosmotech.api.rbac.ROLE_VALIDATOR
import com.cosmotech.api.rbac.ROLE_VIEWER
import com.cosmotech.api.rbac.getCommonRolesDefinition
import com.cosmotech.api.rbac.getScenarioRolesDefinition
import com.cosmotech.api.scenario.ScenarioMetaData
import com.cosmotech.api.utils.changed
import com.cosmotech.api.utils.compareToAndMutateIfNeeded
import com.cosmotech.api.utils.constructPageRequest
import com.cosmotech.api.utils.findAllPaginated
import com.cosmotech.api.utils.getCurrentAccountIdentifier
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.dataset.DatasetApiServiceInterface
import com.cosmotech.dataset.domain.IngestionStatusEnum
import com.cosmotech.dataset.domain.SubDatasetGraphQuery
import com.cosmotech.dataset.service.getRbac
import com.cosmotech.scenario.ScenarioApiServiceInterface
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
import com.cosmotech.solution.SolutionApiServiceInterface
import com.cosmotech.solution.domain.Solution
import com.cosmotech.workspace.WorkspaceApiServiceInterface
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
import org.apache.commons.lang3.NotImplementedException
import org.springframework.context.event.EventListener
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
@Suppress("LargeClass", "TooManyFunctions", "LongParameterList")
internal class ScenarioServiceImpl(
    private val datasetService: DatasetApiServiceInterface,
    private val solutionService: SolutionApiServiceInterface,
    private val workspaceService: WorkspaceApiServiceInterface,
    private val azureDataExplorerClient: ResultDataClient,
    private val azureEventHubsClient: EventBusClient,
    private val csmRbac: CsmRbac,
    private val workspaceEventHubService: IWorkspaceEventHubService,
    private val scenarioRepository: ScenarioRepository
) : CsmPhoenixService(), ScenarioApiServiceInterface {

  val scenarioPermissions = getScenarioRolesDefinition()

  private val notImplementedExceptionMessage =
      "The API is configured to use the internal result data service. " +
          "This endpoint is deactivated so, use run/runner endpoints instead. " +
          "To change that, set the API configuration entry 'csm.platform.use-internal-result-services' to false"

  override fun addOrReplaceScenarioParameterValues(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      scenarioRunTemplateParameterValue: List<ScenarioRunTemplateParameterValue>
  ): List<ScenarioRunTemplateParameterValue> {
    checkInternalResultDataServiceConfiguration()
    if (scenarioRunTemplateParameterValue.isNotEmpty()) {
      val scenario = getVerifiedScenario(organizationId, workspaceId, scenarioId, PERMISSION_WRITE)
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
    checkInternalResultDataServiceConfiguration()
    TODO("Not yet implemented")
  }

  @Suppress("LongMethod")
  override fun createScenario(
      organizationId: String,
      workspaceId: String,
      scenario: Scenario
  ): Scenario {
    checkInternalResultDataServiceConfiguration()
    val workspace =
        workspaceService.getVerifiedWorkspace(
            organizationId, workspaceId, PERMISSION_CREATE_CHILDREN)
    val solution =
        workspace.solution.solutionId?.let {
          solutionService.getVerifiedSolution(organizationId, it)
        }
    val runTemplate =
        solution?.runTemplates?.find { runTemplate -> runTemplate.id == scenario.runTemplateId }
    if (scenario.runTemplateId != null && runTemplate == null) {
      throw IllegalArgumentException("Run Template not found: ${scenario.runTemplateId}")
    }

    var datasetList = scenario.datasetList ?: mutableListOf()
    val parentId = scenario.parentId
    var rootId: String? = null
    val newParametersValuesList = scenario.parametersValues?.toMutableList() ?: mutableListOf()

    val runTemplateParametersIds =
        solution
            ?.parameterGroups
            ?.filter { parameterGroup ->
              runTemplate?.parameterGroups?.contains(parameterGroup.id) == true
            }
            ?.flatMap { parameterGroup -> parameterGroup.parameters ?: mutableListOf() }

    if (parentId != null) {
      logger.debug("Applying / Overwriting Dataset list from parent $parentId")
      val parent = getVerifiedScenario(organizationId, workspaceId, parentId)
      datasetList = parent.datasetList ?: mutableListOf()
      rootId = parent.rootId
      if (rootId == null) {
        rootId = parentId
      }

      handleScenarioRunTemplateParametersValues(
          parentId, runTemplateParametersIds, parent, scenario, newParametersValuesList)
    }

    if (workspace.datasetCopy == true) {
      datasetList =
          datasetList
              .map {
                val dataset = datasetService.findDatasetById(organizationId, it)
                when {
                  dataset.twingraphId == null -> it
                  dataset.ingestionStatus == IngestionStatusEnum.SUCCESS ->
                      datasetService
                          .createSubDataset(
                              organizationId,
                              it,
                              SubDatasetGraphQuery(
                                  name = "Scenario - ${scenario.name}", main = false))
                          .id!!
                  else -> throw CsmClientException("Dataset ${dataset.id} is not ready")
                }
              }
              .toMutableList()
    }

    val newParametersValues =
        runTemplateParametersIds?.let {
          consolidateParameters(solution, it, newParametersValuesList)
        }

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
            datasetList = datasetList,
            rootId = rootId,
            parametersValues = newParametersValues,
            validationStatus = ScenarioValidationStatus.Draft)
    scenarioToSave.setRbac(csmRbac.initSecurity(scenario.getRbac()))

    scenarioRepository.save(scenarioToSave)

    sendScenarioMetaData(organizationId, workspace, scenarioToSave)
    return scenarioToSave
  }

  private fun consolidateParameters(
      solution: Solution,
      runTemplateParametersIds: List<String>,
      newParametersValuesList: MutableList<ScenarioRunTemplateParameterValue>
  ): MutableList<ScenarioRunTemplateParameterValue> {
    val result = mutableListOf<ScenarioRunTemplateParameterValue>()

    runTemplateParametersIds.forEach { parameterId ->
      val solutionParameter =
          solution.parameters?.firstOrNull { runTemplateParameter ->
            runTemplateParameter.id == parameterId
          }

      if (solutionParameter == null) {
        logger.debug(
            "Skipping parameter $parameterId, " +
                "defined neither in the parent nor in this Scenario nor in the Solution")
      } else {

        val currentParameterDefinition =
            newParametersValuesList.firstOrNull { newScenarioParameter ->
              newScenarioParameter.parameterId == parameterId
            }

        if (currentParameterDefinition == null) {
          result.add(
              ScenarioRunTemplateParameterValue(
                  parameterId = parameterId,
                  value = solutionParameter.defaultValue ?: "",
                  varType = solutionParameter.varType,
              ))
        } else {
          result.add(currentParameterDefinition.apply { varType = solutionParameter.varType })
        }
      }
    }
    return result
  }

  @Suppress("NestedBlockDepth")
  private fun handleScenarioRunTemplateParametersValues(
      parentId: String?,
      runTemplateParametersIds: List<String>?,
      parent: Scenario,
      scenario: Scenario,
      newParametersValuesList: MutableList<ScenarioRunTemplateParameterValue>
  ) {
    logger.debug("Copying parameters values from parent $parentId")

    logger.debug("Getting runTemplate parameters ids")
    if (!runTemplateParametersIds.isNullOrEmpty()) {
      val parentParameters = parent.parametersValues?.associate { it.parameterId to it }
      val scenarioParameters = scenario.parametersValues?.associate { it.parameterId to it }
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
    checkInternalResultDataServiceConfiguration()
    val scenario = getVerifiedScenario(organizationId, workspaceId, scenarioId, PERMISSION_DELETE)
    this.addStateToScenario(organizationId, scenario)

    if (scenario.state == ScenarioJobState.Running)
        throw CsmClientException("Can't delete a running scenario : ${scenario.id}")

    val workspace = workspaceService.getVerifiedWorkspace(organizationId, workspaceId)
    if (workspace.datasetCopy == true) {
      scenario.datasetList?.forEach {
        try {
          // This check that it's a v3 dataset that meant to be deleted with the scenario
          // TODO remove went retro compat to  v2.x is remove
          if (datasetService.findDatasetById(organizationId, it).creationDate != null) {
            datasetService.deleteDataset(organizationId, it)
          }
        } catch (e: CsmAccessForbiddenException) {
          logger.warn("Error while deleting dataset $it", e)
        }
      }
    }

    scenarioRepository.delete(scenario)
    this.handleScenarioDeletion(organizationId, workspaceId, scenario)
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
    checkInternalResultDataServiceConfiguration()
    val scenario = getVerifiedScenario(organizationId, workspaceId, scenarioId)
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
    checkInternalResultDataServiceConfiguration()
    workspaceService.getVerifiedWorkspace(organizationId, workspaceId, PERMISSION_WRITE)
    val pageable = Pageable.ofSize(csmPlatformProperties.twincache.scenario.defaultPageSize)

    do {
      val scenarioList =
          this.findPaginatedScenariosStateOption(
              organizationId, workspaceId, pageable.pageNumber, pageable.pageSize, false)
      scenarioList.forEach {
        scenarioRepository.delete(it)
        eventPublisher.publishEvent(ScenarioDeleted(this, organizationId, workspaceId, it.id!!))
      }
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
    checkInternalResultDataServiceConfiguration()
    workspaceService.getVerifiedWorkspace(organizationId, workspaceId)
    val defaultPageSize = csmPlatformProperties.twincache.scenario.defaultPageSize
    val pageable = constructPageRequest(page, size, defaultPageSize)
    var scenarios = listOf<Scenario>()
    if (pageable != null) {
      scenarios =
          this.findPaginatedScenariosStateOption(
              organizationId, workspaceId, pageable.pageNumber, pageable.pageSize, true)
    } else {
      scenarios =
          findAllPaginated(defaultPageSize) {
            this.findPaginatedScenariosStateOption(
                    organizationId, workspaceId, it.pageNumber, it.pageSize, true)
                .toMutableList()
          }
    }
    scenarios.forEach { it.security = updateSecurityVisibility(it).security }
    return scenarios
  }

  override fun findAllScenariosByValidationStatus(
      organizationId: String,
      workspaceId: String,
      validationStatus: ScenarioValidationStatus,
      page: Int?,
      size: Int?
  ): List<Scenario> {
    checkInternalResultDataServiceConfiguration()
    workspaceService.getVerifiedWorkspace(organizationId, workspaceId)
    val status = validationStatus.toString()
    val defaultPageSize = csmPlatformProperties.twincache.scenario.defaultPageSize
    var pageRequest = constructPageRequest(page, size, defaultPageSize)
    val rbacEnabled = isRbacEnabled(organizationId, workspaceId)

    if (pageRequest != null) {
      if (rbacEnabled) {
        val currentUser = getCurrentAccountIdentifier(this.csmPlatformProperties)
        return scenarioRepository
            .findByValidationStatusAndSecurity(
                organizationId, workspaceId, status, currentUser, pageRequest)
            .toList()
      } else {
        return scenarioRepository
            .findByValidationStatus(organizationId, workspaceId, status, pageRequest)
            .toList()
      }
    }

    val findAllScenarioByValidationStatus = mutableListOf<Scenario>()
    pageRequest = PageRequest.ofSize(csmPlatformProperties.twincache.scenario.defaultPageSize)
    do {
      var scenarioList: List<Scenario>
      if (rbacEnabled) {
        val currentUser = getCurrentAccountIdentifier(this.csmPlatformProperties)
        scenarioList =
            scenarioRepository
                .findByValidationStatusAndSecurity(
                    organizationId, workspaceId, status, currentUser, pageRequest!!)
                .toList()
      } else {
        scenarioList =
            scenarioRepository
                .findByValidationStatus(organizationId, workspaceId, status, pageRequest!!)
                .toList()
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
    val scenarios =
        if (isRbacEnabled(organizationId, workspaceId)) {
          val currentUser = getCurrentAccountIdentifier(this.csmPlatformProperties)
          scenarioRepository
              .findByWorkspaceIdAndSecurity(organizationId, workspaceId, currentUser, pageable)
              .toList()
        } else {
          scenarioRepository.findByWorkspaceId(organizationId, workspaceId, pageable).toList()
        }

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
    val findAllScenariosByRootId = mutableListOf<Scenario>()
    val rbacEnabled = isRbacEnabled(organizationId, workspaceId)
    do {
      var scenarioList: List<Scenario>
      if (rbacEnabled) {
        val currentUser = getCurrentAccountIdentifier(this.csmPlatformProperties)
        scenarioList =
            scenarioRepository
                .findByRootIdAndSecurity(organizationId, workspaceId, rootId, currentUser, pageable)
                .toList()
      } else {
        scenarioList =
            scenarioRepository.findByRootId(organizationId, workspaceId, rootId, pageable).toList()
      }
      findAllScenariosByRootId.addAll(scenarioList)
      pageable = pageable.next()
    } while (scenarioList.isNotEmpty())

    return findAllScenariosByRootId
  }

  override fun findScenarioChildrenById(
      organizationId: String,
      workspaceId: String,
      parentId: String
  ): List<Scenario> {
    checkInternalResultDataServiceConfiguration()
    var pageable = PageRequest.ofSize(csmPlatformProperties.twincache.scenario.defaultPageSize)
    val findScenarioChildrenById = mutableListOf<Scenario>()
    val rbacEnabled = isRbacEnabled(organizationId, workspaceId)
    do {
      var scenarioList: List<Scenario>
      if (rbacEnabled) {
        val currentUser = getCurrentAccountIdentifier(this.csmPlatformProperties)
        scenarioList =
            scenarioRepository
                .findByParentIdAndSecurity(
                    organizationId, workspaceId, parentId, currentUser, pageable)
                .toList()
      } else {
        scenarioList =
            scenarioRepository
                .findByParentId(organizationId, workspaceId, parentId, pageable)
                .toList()
      }
      findScenarioChildrenById.addAll(scenarioList)
      pageable = pageable.next()
    } while (scenarioList.isNotEmpty())

    return findScenarioChildrenById
  }

  internal fun isRbacEnabled(organizationId: String, workspaceId: String): Boolean {
    val workspace = workspaceService.getVerifiedWorkspace(organizationId, workspaceId)
    val isAdmin = csmRbac.isAdmin(workspace.getRbac(), getCommonRolesDefinition())
    return (!isAdmin && this.csmPlatformProperties.rbac.enabled)
  }

  override fun findScenarioById(
      organizationId: String,
      workspaceId: String,
      scenarioId: String
  ): Scenario {
    checkInternalResultDataServiceConfiguration()
    val scenario = findScenarioById(organizationId, workspaceId, scenarioId, withState = true)
    return updateSecurityVisibility(scenario)
  }

  override fun findScenarioById(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      withState: Boolean
  ): Scenario {
    checkInternalResultDataServiceConfiguration()
    val scenario = getVerifiedScenario(organizationId, workspaceId, scenarioId)
    if (withState) addStateToScenario(organizationId, scenario)

    return scenario
  }

  override fun getScenarioValidationStatusById(
      organizationId: String,
      workspaceId: String,
      scenarioId: String
  ): ScenarioValidationStatus {
    checkInternalResultDataServiceConfiguration()
    val scenario = getVerifiedScenario(organizationId, workspaceId, scenarioId)
    return scenario.validationStatus ?: ScenarioValidationStatus.Unknown
  }

  override fun getScenarioDataDownloadJobInfo(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      downloadId: String
  ): ScenarioDataDownloadInfo {
    checkInternalResultDataServiceConfiguration()
    val scenario = getVerifiedScenario(organizationId, workspaceId, scenarioId)
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
    // TODO: remove this endpoint
    return findAllScenarios(organizationId, workspaceId, null, null)
  }

  override fun removeAllScenarioParameterValues(
      organizationId: String,
      workspaceId: String,
      scenarioId: String
  ) {
    checkInternalResultDataServiceConfiguration()
    val scenario = getVerifiedScenario(organizationId, workspaceId, scenarioId, PERMISSION_WRITE)
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
    checkInternalResultDataServiceConfiguration()
    val existingScenario =
        getVerifiedScenario(organizationId, workspaceId, scenarioId, PERMISSION_WRITE)
    val workspace = workspaceService.findWorkspaceById(organizationId, workspaceId)

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

      val solution =
          workspace.solution.solutionId?.let {
            solutionService.getVerifiedSolution(organizationId, it)
          }
      val runTemplate =
          solution?.runTemplates?.find { runTemplate ->
            runTemplate.id == existingScenario.runTemplateId
          }
      val runTemplateParametersIds =
          solution
              ?.parameterGroups
              ?.filter { parameterGroup ->
                runTemplate?.parameterGroups?.contains(parameterGroup.id) == true
              }
              ?.flatMap { parameterGroup -> parameterGroup.parameters ?: mutableListOf() }

      val updatedParameters =
          consolidateParameters(solution!!, runTemplateParametersIds!!, scenario.parametersValues!!)

      existingScenario.parametersValues = updatedParameters
      existingScenario.parametersValues?.forEach { it.isInherited = false }

      hasChanged = true
    }

    if (scenario.security != existingScenario.security) {
      logger.warn(
          "Security modification has not been applied to scenario $scenarioId," +
              " please refer to the appropriate security endpoints to perform this maneuver")
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
    scenario.security!!.accessControlList.forEach {
      updateLinkedDatasetsAccessControl(scenario.organizationId!!, scenario, it)
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
    checkInternalResultDataServiceConfiguration()
    getVerifiedScenario(organizationId, workspaceId, scenarioId, PERMISSION_READ_SECURITY)
    return com.cosmotech.api.rbac.getPermissions(role, getScenarioRolesDefinition())
  }

  override fun getScenarioSecurity(
      organizationId: String,
      workspaceId: String,
      scenarioId: String
  ): ScenarioSecurity {
    checkInternalResultDataServiceConfiguration()
    val scenario =
        getVerifiedScenario(organizationId, workspaceId, scenarioId, PERMISSION_READ_SECURITY)
    return scenario.security
        ?: throw CsmResourceNotFoundException("RBAC not defined for ${scenario.id}")
  }

  override fun setScenarioDefaultSecurity(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      scenarioRole: ScenarioRole
  ): ScenarioSecurity {
    checkInternalResultDataServiceConfiguration()
    val scenario =
        getVerifiedScenario(organizationId, workspaceId, scenarioId, PERMISSION_WRITE_SECURITY)
    val rbacSecurity =
        csmRbac.setDefault(scenario.getRbac(), scenarioRole.role, scenarioPermissions)
    scenario.setRbac(rbacSecurity)
    upsertScenarioData(scenario)
    setLinkedDatasetDefaultSecurity(organizationId, scenario, scenarioRole.role)
    return scenario.security as ScenarioSecurity
  }

  override fun getScenarioAccessControl(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      identityId: String
  ): ScenarioAccessControl {
    checkInternalResultDataServiceConfiguration()
    val scenario =
        getVerifiedScenario(organizationId, workspaceId, scenarioId, PERMISSION_READ_SECURITY)
    val rbacAccessControl = csmRbac.getAccessControl(scenario.getRbac(), identityId)
    return ScenarioAccessControl(rbacAccessControl.id, rbacAccessControl.role)
  }

  override fun addScenarioAccessControl(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      scenarioAccessControl: ScenarioAccessControl
  ): ScenarioAccessControl {
    checkInternalResultDataServiceConfiguration()
    val scenario =
        getVerifiedScenario(organizationId, workspaceId, scenarioId, PERMISSION_WRITE_SECURITY)

    val workspace = workspaceService.getVerifiedWorkspace(organizationId, workspaceId)

    val users = getScenarioSecurityUsers(organizationId, workspaceId, scenarioId)
    if (users.contains(scenarioAccessControl.id)) {
      throw IllegalArgumentException("User is already in this Scenario security")
    }

    val rbacSecurity =
        csmRbac.addUserRole(
            workspace.getRbac(),
            scenario.getRbac(),
            scenarioAccessControl.id,
            scenarioAccessControl.role,
            scenarioPermissions)
    scenario.setRbac(rbacSecurity)
    updateLinkedDatasetsAccessControl(organizationId, scenario, scenarioAccessControl)
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
    checkInternalResultDataServiceConfiguration()
    val scenario =
        getVerifiedScenario(organizationId, workspaceId, scenarioId, PERMISSION_WRITE_SECURITY)
    csmRbac.checkUserExists(
        scenario.getRbac(), identityId, "User '$identityId' not found in scenario $scenarioId")
    val rbacSecurity =
        csmRbac.setUserRole(scenario.getRbac(), identityId, scenarioRole.role, scenarioPermissions)
    scenario.setRbac(rbacSecurity)
    upsertScenarioData(scenario)
    updateLinkedDatasetsAccessControl(
        organizationId, scenario, ScenarioAccessControl(identityId, scenarioRole.role))
    val rbacAccessControl = csmRbac.getAccessControl(scenario.getRbac(), identityId)
    return ScenarioAccessControl(rbacAccessControl.id, rbacAccessControl.role)
  }

  fun updateLinkedDatasetsAccessControl(
      organizationId: String,
      scenario: Scenario,
      scenarioAccessControl: ScenarioAccessControl
  ) {
    val userIdForAcl = scenarioAccessControl.id

    val workspace = workspaceService.getVerifiedWorkspace(organizationId, scenario.workspaceId!!)

    if (workspace.datasetCopy == true) {
      // Scenario and Dataset don't have the same roles
      // This function translates the role set from one to another
      val datasetRoleFromScenarioAcl: String =
          if (scenarioAccessControl.role == ROLE_VALIDATOR) {
            ROLE_USER
          } else {
            scenarioAccessControl.role
          }

      scenario.datasetList!!
          .mapNotNull { datasetService.findByOrganizationIdAndDatasetId(organizationId, it) }
          .forEach { dataset ->
            if (dataset.main == true) {
              val datasetAcl = dataset.getRbac().accessControlList
              if (datasetAcl.none { it.id == userIdForAcl }) {
                // If the access for this user is not defined on the dataset
                // As its a main dataset, we can add access for this user
                // but not update the existing access to prevent issues
                // if the dataset is shared between several scenarios
                datasetService.addOrUpdateAccessControl(
                    organizationId, dataset, userIdForAcl, datasetRoleFromScenarioAcl)
              }
            } else {
              // Dataset roles should be similar to scenario ones if dataset are copy of a master
              // one
              // This is possible when workspace.datasetCopy is true
              // Filter on dataset copy (cause we do not want update main dataset as it can be
              // shared
              // between scenarios)
              datasetService.addOrUpdateAccessControl(
                  organizationId, dataset, userIdForAcl, datasetRoleFromScenarioAcl)
            }
          }
    } else {
      scenario.datasetList!!
          .mapNotNull { datasetService.findByOrganizationIdAndDatasetId(organizationId, it) }
          .forEach { dataset ->
            val datasetAcl = dataset.getRbac().accessControlList
            if (datasetAcl.none { it.id == userIdForAcl }) {
              // If the access for this user is not defined on the dataset
              // As its a main dataset, we can add access for this user
              // but not update the existing access to prevent issues
              // if the dataset is shared between several scenarios
              datasetService.addOrUpdateAccessControl(
                  organizationId, dataset, userIdForAcl, ROLE_VIEWER)
            }
          }
    }
  }

  override fun removeScenarioAccessControl(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      identityId: String
  ) {
    checkInternalResultDataServiceConfiguration()
    val scenario =
        getVerifiedScenario(organizationId, workspaceId, scenarioId, PERMISSION_WRITE_SECURITY)
    val rbacSecurity = csmRbac.removeUser(scenario.getRbac(), identityId, scenarioPermissions)
    scenario.setRbac(rbacSecurity)
    removeLinkedDatasetsAccessControl(organizationId, scenario, identityId)
    upsertScenarioData(scenario)
  }

  fun removeLinkedDatasetsAccessControl(
      organizationId: String,
      scenario: Scenario,
      identityId: String
  ) {
    scenario.datasetList!!.forEach { datasetId ->
      val dataset = datasetService.findDatasetById(organizationId, datasetId)
      // Filter on dataset copy (cause we do not want update main dataset as it can be shared
      // between scenarios)
      if (dataset.main != true) {
        val datasetRBACIds = dataset.getRbac().accessControlList.map { it.id }
        if (datasetRBACIds.contains(identityId)) {
          datasetService.removeDatasetAccessControl(organizationId, datasetId, identityId)
        }
      }
    }
  }

  fun setLinkedDatasetDefaultSecurity(
      organizationId: String,
      scenario: Scenario,
      scenarioRole: String
  ) {
    var datasetRole = ROLE_VIEWER
    if (scenarioRole == ROLE_NONE) datasetRole = ROLE_NONE
    scenario.datasetList!!.forEach { datasetId ->
      val dataset = datasetService.findByOrganizationIdAndDatasetId(organizationId, datasetId)
      if (datasetRole == ROLE_NONE && dataset!!.main == true) return@forEach
      // We do not want to lower the default security if it's higher than viewer
      if (datasetRole == ROLE_VIEWER && dataset!!.security!!.default != ROLE_NONE) return@forEach
      if (datasetRole == ROLE_NONE && dataset!!.security!!.default != ROLE_VIEWER) return@forEach
      // Filter on dataset copy (because we do not want to update main dataset as it can be shared
      // between scenarios)
      datasetService.updateDefaultSecurity(organizationId, dataset!!, datasetRole)
    }
  }

  override fun getScenarioSecurityUsers(
      organizationId: String,
      workspaceId: String,
      scenarioId: String
  ): List<String> {
    checkInternalResultDataServiceConfiguration()
    val scenario =
        getVerifiedScenario(organizationId, workspaceId, scenarioId, PERMISSION_READ_SECURITY)
    return csmRbac.getUsers(scenario.getRbac())
  }

  override fun getVerifiedScenario(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      requiredPermission: String
  ): Scenario {
    checkInternalResultDataServiceConfiguration()
    workspaceService.getVerifiedWorkspace(organizationId, workspaceId)
    val scenario =
        scenarioRepository.findBy(organizationId, workspaceId, scenarioId).orElseThrow {
          CsmResourceNotFoundException(
              "Resource of type '${Scenario::class.java.simpleName}' not found" +
                  " with workspaceId=$workspaceId, scenarioId=$scenarioId, organizationId=$organizationId")
        }
    csmRbac.verify(scenario.getRbac(), requiredPermission, scenarioPermissions)

    return scenario
  }

  internal fun checkInternalResultDataServiceConfiguration() {
    if (csmPlatformProperties.internalResultServices?.enabled == true) {
      throw NotImplementedException(notImplementedExceptionMessage)
    }
  }

  fun updateSecurityVisibility(scenario: Scenario): Scenario {
    if (csmRbac.check(scenario.getRbac(), PERMISSION_READ_SECURITY).not()) {
      val username = getCurrentAccountIdentifier(csmPlatformProperties)
      val retrievedAC = scenario.security!!.accessControlList.firstOrNull { it.id == username }
      return if (retrievedAC != null) {
        scenario.copy(
            security =
                ScenarioSecurity(
                    default = scenario.security!!.default,
                    accessControlList = mutableListOf(retrievedAC)))
      } else {
        scenario.copy(
            security =
                ScenarioSecurity(
                    default = scenario.security!!.default, accessControlList = mutableListOf()))
      }
    }
    return scenario
  }
}
