// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.runner.service

import com.cosmotech.api.CsmPhoenixService
import com.cosmotech.api.events.HasRunningRuns
import com.cosmotech.api.events.RunStart
import com.cosmotech.api.events.RunStop
import com.cosmotech.api.events.RunnerDeleted
import com.cosmotech.api.events.UpdateRunnerStatus
import com.cosmotech.api.exceptions.CsmClientException
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.rbac.CsmRbac
import com.cosmotech.api.rbac.PERMISSION_READ
import com.cosmotech.api.rbac.PERMISSION_READ_SECURITY
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.rbac.ROLE_USER
import com.cosmotech.api.rbac.ROLE_VALIDATOR
import com.cosmotech.api.rbac.RolesDefinition
import com.cosmotech.api.rbac.getRunnerRolesDefinition
import com.cosmotech.api.rbac.model.RbacAccessControl
import com.cosmotech.api.rbac.model.RbacSecurity
import com.cosmotech.api.security.ROLE_PLATFORM_ADMIN
import com.cosmotech.api.utils.compareToAndMutateIfNeeded
import com.cosmotech.api.utils.getCurrentAccountIdentifier
import com.cosmotech.api.utils.getCurrentAuthenticatedRoles
import com.cosmotech.dataset.DatasetApiServiceInterface
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.dataset.domain.DatasetAccessControl
import com.cosmotech.dataset.domain.DatasetCreateRequest
import com.cosmotech.dataset.domain.DatasetPart
import com.cosmotech.dataset.domain.DatasetPartCreateRequest
import com.cosmotech.dataset.domain.DatasetPartTypeEnum
import com.cosmotech.organization.OrganizationApiServiceInterface
import com.cosmotech.organization.domain.Organization
import com.cosmotech.runner.domain.CreatedRun
import com.cosmotech.runner.domain.LastRunInfo
import com.cosmotech.runner.domain.LastRunInfo.LastRunStatus
import com.cosmotech.runner.domain.Runner
import com.cosmotech.runner.domain.RunnerAccessControl
import com.cosmotech.runner.domain.RunnerCreateRequest
import com.cosmotech.runner.domain.RunnerDatasets
import com.cosmotech.runner.domain.RunnerEditInfo
import com.cosmotech.runner.domain.RunnerRunTemplateParameterValue
import com.cosmotech.runner.domain.RunnerSecurity
import com.cosmotech.runner.domain.RunnerUpdateRequest
import com.cosmotech.runner.domain.RunnerValidationStatus
import com.cosmotech.runner.repository.RunnerRepository
import com.cosmotech.solution.SolutionApiServiceInterface
import com.cosmotech.solution.domain.RunTemplateParameter
import com.cosmotech.workspace.WorkspaceApiServiceInterface
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.service.toGenericSecurity
import java.time.Instant
import kotlin.collections.mutableListOf
import org.springframework.context.annotation.Scope
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

const val DATASET_PART_VARTYPE_FILE = "%DATASET_PART_ID_FILE%"
const val DATASET_PART_VARTYPE_DB = "%DATASET_PART_ID_DB%"

@Component
@Scope("prototype")
@Suppress("TooManyFunctions")
class RunnerService(
    private val runnerRepository: RunnerRepository,
    private val organizationApiService: OrganizationApiServiceInterface,
    private val workspaceApiService: WorkspaceApiServiceInterface,
    private val solutionApiService: SolutionApiServiceInterface,
    private val datasetApiService: DatasetApiServiceInterface,
    private val csmRbac: CsmRbac,
    private var organization: Organization? = null,
    private var workspace: Workspace? = null,
) : CsmPhoenixService() {

  fun updateSecurityVisibility(runner: Runner): Runner {
    if (csmRbac
        .check(runner.getRbac(), PERMISSION_READ_SECURITY, getRunnerRolesDefinition())
        .not()) {
      val username = getCurrentAccountIdentifier(csmPlatformProperties)
      val retrievedAC = runner.security.accessControlList.firstOrNull { it.id == username }

      val accessControlList =
          if (retrievedAC != null) {
            mutableListOf(retrievedAC)
          } else {
            mutableListOf()
          }
      runner.security =
          RunnerSecurity(default = runner.security.default, accessControlList = accessControlList)
    }
    return runner
  }

  fun inOrganization(organizationId: String): RunnerService = apply {
    this.organization = organizationApiService.getOrganization(organizationId)
  }

  fun inWorkspace(workspaceId: String): RunnerService = apply {
    requireNotNull(this.organization) {
      "RunnerService's organization needs to be set. use inOrganization to do so."
    }
    this.workspace = workspaceApiService.getWorkspace(this.organization!!.id, workspaceId)
  }

  fun userHasPermissionOnWorkspace(permission: String): RunnerService = apply {
    requireNotNull(this.workspace) {
      "RunnerService's workspace needs to be set. Use inWorkspace to do so."
    }
    csmRbac.verify(workspace!!.security.toGenericSecurity(workspace!!.id), permission)
  }

  fun deleteInstance(runnerInstance: RunnerInstance) {
    val runner = runnerInstance.getRunnerDataObjet()

    // Check there are no running runs
    val hasRunningRuns = HasRunningRuns(this, runner.organizationId, runner.workspaceId, runner.id)
    this.eventPublisher.publishEvent(hasRunningRuns)
    if (hasRunningRuns.response == true) {
      throw CsmClientException(
          "Can't delete runner ${runner.id}: at least one run is still running")
    }

    // Update parent and root references to deleted runner
    val newRoots = mutableListOf<Runner>()
    listAllRunnerByParentId(runner.organizationId, runner.workspaceId, runner.id).forEach {
      it.parentId = runner.parentId
      // Runner was root, child is now root
      if (runner.rootId == null) {
        it.rootId = null
        newRoots.add(it)
      }
      runnerRepository.save(it)
    }

    // Update new root ids
    newRoots.forEach { updateChildrenRootId(parent = it, newRootId = it.id) }

    // Notify the deletion
    val runnerDeleted = RunnerDeleted(this, runner.organizationId, runner.workspaceId, runner.id)
    this.eventPublisher.publishEvent(runnerDeleted)
    datasetApiService.deleteDataset(
        runner.organizationId, runner.workspaceId, runner.datasets.parameter)
    return runnerRepository.delete(runnerInstance.getRunnerDataObjet())
  }

  private fun listAllRunnerByParentId(
      organizationId: String,
      workspaceId: String,
      parentId: String
  ): List<Runner> {
    val defaultPageSize = csmPlatformProperties.twincache.runner.defaultPageSize
    var pageRequest: Pageable = PageRequest.ofSize(defaultPageSize)

    val runners = mutableListOf<Runner>()

    do {
      val pagedRunners =
          runnerRepository.findByParentId(organizationId, workspaceId, parentId, pageRequest)
      runners.addAll(pagedRunners.toList())
      pageRequest = pagedRunners.nextPageable()
    } while (pagedRunners.hasNext())

    return runners
  }

  private fun updateChildrenRootId(parent: Runner, newRootId: String) {
    listAllRunnerByParentId(parent.organizationId, parent.workspaceId, parent.id).forEach {
      it.rootId = newRootId
      runnerRepository.save(it)
      updateChildrenRootId(it, newRootId)
    }
  }

  fun saveInstance(runnerInstance: RunnerInstance): Runner {
    return runnerRepository.save(runnerInstance.getRunnerDataObjet())
  }

  fun getNewInstance(): RunnerInstance {
    val runnerInstance = RunnerInstance()
    return runnerInstance.initialize()
  }

  fun getInstance(runnerId: String): RunnerInstance {
    var runner =
        runnerRepository.findBy(organization!!.id, workspace!!.id, runnerId).orElseThrow {
          CsmResourceNotFoundException(
              "Runner $runnerId not found in workspace ${workspace!!.id} and organization ${organization!!.id}")
        }
    if (runner.lastRunInfo.lastRunId != null) {
      if (runner.lastRunInfo.lastRunStatus != LastRunStatus.Failed ||
          runner.lastRunInfo.lastRunStatus != LastRunStatus.Successful) {
        runner = updateRunnerStatus(runner)
      }
    }
    updateSecurityVisibility(runner)
    return RunnerInstance().initializeFrom(runner).userHasPermission(PERMISSION_READ)
  }

  fun updateRunnerStatus(runner: Runner): Runner {
    val updateRunnerStatusEvent =
        UpdateRunnerStatus(
            this,
            runner.organizationId,
            runner.workspaceId,
            runner.id,
            runner.lastRunInfo.lastRunId ?: "")
    eventPublisher.publishEvent(updateRunnerStatusEvent)
    val runStatus = LastRunStatus.forValue(updateRunnerStatusEvent.response!!)
    return runnerRepository.save(runner.apply { lastRunInfo.lastRunStatus = runStatus })
  }

  fun listInstances(pageRequest: PageRequest): List<Runner> {
    val isPlatformAdmin =
        getCurrentAuthenticatedRoles(this.csmPlatformProperties).contains(ROLE_PLATFORM_ADMIN)
    val runners: List<Runner> =
        if (!this.csmPlatformProperties.rbac.enabled || isPlatformAdmin) {
          runnerRepository
              .findByWorkspaceId(organization!!.id, workspace!!.id, pageRequest)
              .toList()
        } else {
          val currentUser = getCurrentAccountIdentifier(this.csmPlatformProperties)
          runnerRepository
              .findByWorkspaceIdAndSecurity(
                  organization!!.id, workspace!!.id, currentUser, pageRequest)
              .toList()
        }
    runners.forEach { it.security = updateSecurityVisibility(it).security }
    return runners
  }

  fun startRunWith(runnerInstance: RunnerInstance): CreatedRun {
    val startEvent = RunStart(this, runnerInstance.getRunnerDataObjet())
    this.eventPublisher.publishEvent(startEvent)
    val runId = startEvent.response ?: throw IllegalStateException("Run Service did not respond")
    runnerInstance.setLastRunInfo(runId)
    runnerRepository.save(runnerInstance.getRunnerDataObjet())
    return CreatedRun(id = runId)
  }

  fun stopLastRunOf(runnerInstance: RunnerInstance) {
    val runner = runnerInstance.getRunnerDataObjet()
    runner.lastRunInfo.lastRunId
        ?: throw IllegalArgumentException("Runner ${runner.id} doesn't have a last run")
    this.eventPublisher.publishEvent(RunStop(this, runner))
  }

  @Suppress("TooManyFunctions")
  inner class RunnerInstance {
    private val roleDefinition: RolesDefinition = getRunnerRolesDefinition()
    lateinit var runner: Runner

    fun initialize(): RunnerInstance = apply {
      val now = Instant.now().toEpochMilli()
      this.runner =
          Runner(
              id = idGenerator.generate("runner"),
              name = "init",
              createInfo =
                  RunnerEditInfo(
                      timestamp = now, userId = getCurrentAccountIdentifier(csmPlatformProperties)),
              updateInfo =
                  RunnerEditInfo(
                      timestamp = now, userId = getCurrentAccountIdentifier(csmPlatformProperties)),
              solutionId = "init",
              runTemplateId = "init",
              organizationId = organization!!.id,
              workspaceId = workspace!!.id,
              ownerName = "init",
              datasets = RunnerDatasets(bases = mutableListOf(), parameter = "init"),
              parametersValues = mutableListOf(),
              lastRunInfo = LastRunInfo(lastRunId = null, lastRunStatus = LastRunStatus.NotStarted),
              validationStatus = RunnerValidationStatus.Draft,
              security = RunnerSecurity("", accessControlList = mutableListOf()),
          )
    }

    fun initializeFrom(runner: Runner): RunnerInstance = apply { this.runner = runner }

    fun stamp(): RunnerInstance = apply {
      this.runner.updateInfo =
          RunnerEditInfo(
              timestamp = Instant.now().toEpochMilli(),
              userId = getCurrentAccountIdentifier(csmPlatformProperties))
    }

    fun getRunnerDataObjet(): Runner = this.runner

    fun userHasPermission(permission: String): RunnerInstance = apply {
      csmRbac.verify(this.getRbacSecurity(), permission, this.roleDefinition)
    }

    fun setValueFrom(runner: Runner): RunnerInstance = apply {
      require(runner.runTemplateId.isNotEmpty()) { "runner does not have a runTemplateId define" }
      require(
          solutionApiService.isRunTemplateExist(
              organization!!.id,
              workspace!!.id,
              workspace!!.solution.solutionId,
              runner.runTemplateId)) {
            "Run Template not found: ${runner.runTemplateId}"
          }

      val excludeFields =
          arrayOf(
              "id",
              "ownerId",
              "rootId",
              "organizationId",
              "workspaceId",
              "creationDate",
              "security")
      this.runner.compareToAndMutateIfNeeded(runner, excludedFields = excludeFields)
      consolidateParametersVarType()
    }

    fun setValueFrom(runnerCreateRequest: RunnerCreateRequest): RunnerInstance {

      val security =
          csmRbac.initSecurity(runnerCreateRequest.security.toGenericSecurity(this.runner.id))

      this.setRbacSecurity(security)

      val parameterDataset =
          datasetApiService.createDataset(
              organization!!.id,
              workspace!!.id,
              DatasetCreateRequest(name = "Dataset attached to ${this.runner.id}"),
              emptyArray())

      val filteredParameterValues =
          useOnlyDefinedParameterValues(
              runnerCreateRequest.solutionId,
              runnerCreateRequest.runTemplateId,
              runnerCreateRequest.parametersValues)

      return setValueFrom(
          Runner(
              runSizing = runnerCreateRequest.runSizing,
              tags = runnerCreateRequest.tags,
              description = runnerCreateRequest.description,
              name = runnerCreateRequest.name,
              runTemplateId = runnerCreateRequest.runTemplateId,
              datasets =
                  RunnerDatasets(
                      bases = runnerCreateRequest.datasetList ?: mutableListOf(),
                      parameter = parameterDataset.id),
              parametersValues = filteredParameterValues,
              parentId = runnerCreateRequest.parentId,
              lastRunInfo =
                  LastRunInfo(
                      lastRunId = this.runner.lastRunInfo.lastRunId,
                      lastRunStatus = this.runner.lastRunInfo.lastRunStatus),
              solutionName = runnerCreateRequest.solutionName,
              solutionId = runnerCreateRequest.solutionId,
              validationStatus = this.runner.validationStatus,
              ownerName = runnerCreateRequest.ownerName,
              runTemplateName = runnerCreateRequest.runTemplateName,
              security = this.runner.security,
              id = this.runner.id,
              createInfo = this.runner.createInfo,
              updateInfo = this.runner.updateInfo,
              organizationId = this.runner.organizationId,
              workspaceId = this.runner.workspaceId,
          ))
    }

    fun setValueFrom(runnerUpdateRequest: RunnerUpdateRequest): RunnerInstance {

      val runTemplateId = runnerUpdateRequest.runTemplateId ?: this.runner.runTemplateId

      val filteredParameterValues =
          if (runnerUpdateRequest.parametersValues == null) {
            this.runner.parametersValues
          } else {
            useOnlyDefinedParameterValues(
                this.runner.solutionId, runTemplateId, runnerUpdateRequest.parametersValues)
          }

      return setValueFrom(
          Runner(
              runSizing = runnerUpdateRequest.runSizing ?: this.runner.runSizing,
              tags = runnerUpdateRequest.tags ?: this.runner.tags,
              description = runnerUpdateRequest.description ?: this.runner.description,
              name = runnerUpdateRequest.name ?: this.runner.name,
              runTemplateId = runTemplateId,
              datasets =
                  RunnerDatasets(
                      bases = runnerUpdateRequest.datasetList ?: this.runner.datasets.bases,
                      parameter = this.runner.datasets.parameter),
              parametersValues = filteredParameterValues,
              id = this.runner.id,
              organizationId = this.runner.organizationId,
              security = this.runner.security,
              runTemplateName = runnerUpdateRequest.runTemplateName ?: this.runner.runTemplateName,
              solutionName = runnerUpdateRequest.solutionName ?: this.runner.solutionName,
              solutionId = this.runner.solutionId,
              rootId = this.runner.rootId,
              ownerName = runnerUpdateRequest.ownerName ?: this.runner.ownerName,
              createInfo = this.runner.createInfo,
              updateInfo =
                  RunnerEditInfo(
                      timestamp = Instant.now().toEpochMilli(),
                      userId = getCurrentAccountIdentifier(csmPlatformProperties)),
              parentId = this.runner.parentId,
              workspaceId = this.runner.workspaceId,
              lastRunInfo =
                  LastRunInfo(
                      lastRunStatus = this.runner.lastRunInfo.lastRunStatus,
                      lastRunId = this.runner.lastRunInfo.lastRunId),
              validationStatus = this.runner.validationStatus))
    }

    fun setLastRunInfo(runId: String, runStatus: LastRunStatus = LastRunStatus.Running) {
      this.runner.lastRunInfo = LastRunInfo(lastRunId = runId, lastRunStatus = runStatus)
    }

    fun initParametersFromSolution(): RunnerInstance = apply {
      val solution =
          workspace?.solution?.solutionId?.let {
            solutionApiService.getSolution(organization?.id!!, it)
          }
      val runTemplateId = this.runner.runTemplateId
      val runTemplate =
          solution?.runTemplates?.find { runTemplate -> runTemplate.id == runTemplateId }
      requireNotNull(runTemplate) { "Run Template not found: $runTemplateId" }

      val runTemplateParameters =
          solution.parameterGroups
              .filter { runTemplate.parameterGroups.contains(it.id) }
              .flatMap { it.parameters }
              .mapNotNull { parameterId -> solution.parameters.find { it.id == parameterId } }

      val solutionParameterValues = constructParametersValuesFromSolution(runTemplateParameters)

      val mergedParameterList = mutableListOf<RunnerRunTemplateParameterValue>()

      solutionParameterValues.forEach { solutionParameter ->
        val parameter =
            this.runner.parametersValues.firstOrNull {
              solutionParameter.parameterId == it.parameterId
            }
        if (parameter != null) {
          mergedParameterList.add(parameter)
        } else {
          mergedParameterList.add(solutionParameter)
        }
      }

      this.runner.parametersValues = mergedParameterList

      constructDatasetParametersFromSolution(runTemplateParameters, solution.id)
    }

    fun initParametersFromParent(parentId: String): RunnerInstance = apply {
      val runnerId = this.runner.id
      val parentRunner =
          runnerRepository
              .findBy(this.runner.organizationId, this.runner.workspaceId, parentId)
              .orElseThrow {
                IllegalArgumentException("Parent Id $parentId define on $runnerId does not exists")
              }
      val solution =
          workspace?.solution?.solutionId?.let {
            solutionApiService.getSolution(organization?.id!!, it)
          }
      val runTemplateId = this.runner.runTemplateId
      val runTemplate =
          solution?.runTemplates?.find { runTemplate -> runTemplate.id == runTemplateId }
      requireNotNull(runTemplate) { "Run Template not found: $runTemplateId" }

      val runTemplateParametersIds =
          solution.parameterGroups
              .filter { runTemplate.parameterGroups.contains(it.id) }
              .flatMap { it.parameters }

      val inheritedParameterValues =
          constructParametersValuesFromParent(
              runTemplateParametersIds, parentId, parentRunner, this.runner)
      val parameterValueList = this.runner.parametersValues
      parameterValueList.addAll(inheritedParameterValues)
      this.runner.parametersValues = parameterValueList
      consolidateParametersVarType()

      constructDatasetParametersFromParent(
          runTemplateParametersIds, parentId, parentRunner, this.runner)

      // Compute rootId
      this.runner.parentId?.let {
        this.runner.rootId =
            runnerRepository
                .findBy(organization!!.id, workspace!!.id, it)
                .orElseThrow { IllegalArgumentException("Parent runner not found: $it") }
                .rootId ?: this.runner.parentId
      }
    }

    private fun useOnlyDefinedParameterValues(
        solutionId: String,
        runTemplateId: String,
        parametersValues: MutableList<RunnerRunTemplateParameterValue>?
    ): MutableList<RunnerRunTemplateParameterValue> {
      if (parametersValues.isNullOrEmpty()) return mutableListOf()

      val allowedParametersIds = mutableListOf<String>()
      val runTemplateParameterGroups =
          solutionApiService
              .getRunTemplate(organization!!.id, solutionId, runTemplateId)
              .parameterGroups
              .toList()

      runTemplateParameterGroups.forEach {
        val solutionParameterGroup =
            solutionApiService.getSolutionParameterGroup(organization!!.id, solutionId, it)
        allowedParametersIds.addAll(solutionParameterGroup.parameters)
      }

      return parametersValues
          .filter { allowedParametersIds.contains(it.parameterId) }
          .toMutableList()
    }

    private fun constructDatasetParametersFromParent(
        runTemplateParametersIds: List<String>,
        parentId: String,
        parent: Runner,
        runner: Runner
    ): List<DatasetPart> {

      val datasetPartList = mutableListOf<DatasetPart>()
      if (runTemplateParametersIds.isNotEmpty()) {
        val parentDatasetParameters =
            datasetApiService
                .listDatasetParts(
                    organization!!.id, workspace!!.id, parent.datasets.parameter, null, null)
                .associateBy { it.name }

        runTemplateParametersIds
            .filter { parentDatasetParameters.contains(it) }
            .forEach { parameterId ->
              logger.debug(
                  "Creating dataset part from parent for parameter $parameterId " +
                      "in Dataset ${runner.datasets.parameter}")
              val parentDatasetParameter = parentDatasetParameters[parameterId]
              if (parentDatasetParameter != null) {
                datasetPartList.add(
                    addDatasetPartInDatasetParameter(
                        parentDatasetParameter, runner.datasets.parameter, parameterId, runner.id))
              } else {
                logger.warn(
                    "Parameter $parameterId not found in parent ($parentId) dataset parameters: " +
                        "No dataset part will be created")
              }
            }
      }

      return datasetPartList
    }

    private fun addDatasetPartInDatasetParameter(
        datasetPartToAdd: DatasetPart,
        datasetId: String,
        parameterId: String,
        runnerId: String
    ): DatasetPart {

      val sourceName = datasetPartToAdd.sourceName
      val datasetPartCreateRequest =
          DatasetPartCreateRequest(
              name = parameterId,
              description = "Dataset Part associated to $parameterId parameter",
              sourceName = sourceName,
              tags = mutableListOf(runnerId, parameterId),
              type = datasetPartToAdd.type)
      val datasetPartResource =
          datasetApiService.downloadDatasetPart(
              datasetPartToAdd.organizationId,
              datasetPartToAdd.workspaceId,
              datasetPartToAdd.datasetId,
              datasetPartToAdd.id)
      return datasetApiService.createDatasetPartFromResource(
          datasetPartToAdd.organizationId,
          datasetPartToAdd.workspaceId,
          datasetId,
          datasetPartResource,
          datasetPartCreateRequest)
    }

    private fun createDatasetPartInDatasetParameter(
        organizationId: String,
        workspaceId: String,
        solutionId: String,
        datasetId: String,
        parameterValue: RunnerRunTemplateParameterValue,
        parameterId: String,
        runnerId: String
    ): DatasetPart {

      val partType =
          when (parameterValue.varType) {
            DATASET_PART_VARTYPE_FILE -> DatasetPartTypeEnum.File
            DATASET_PART_VARTYPE_DB -> DatasetPartTypeEnum.Relational
            else -> DatasetPartTypeEnum.File
          }
      val sourceName = parameterValue.value
      val solutionFileResource =
          solutionApiService.getSolutionFile(organizationId, solutionId, sourceName)
      val datasetPartCreateRequest =
          DatasetPartCreateRequest(
              name = parameterId,
              description = "Dataset Part associated to $parameterId parameter",
              sourceName = sourceName.substringAfterLast("/"),
              tags = mutableListOf(runnerId, parameterId),
              type = partType)
      return datasetApiService.createDatasetPartFromResource(
          organizationId, workspaceId, datasetId, solutionFileResource, datasetPartCreateRequest)
    }

    private fun consolidateParametersVarType() {
      val solutionParameters =
          workspace
              ?.solution
              ?.solutionId
              ?.let { solutionApiService.getSolution(organization?.id!!, it) }
              ?.parameters

      this.runner.parametersValues.forEach { runnerParam ->
        solutionParameters
            ?.find { it.id == runnerParam.parameterId }
            ?.varType
            ?.let { runnerParam.varType = it }
      }
    }

    fun initBaseDatasetListFromParent(
        parentId: String,
        newDatasetList: List<String>?
    ): RunnerInstance = apply {
      val runnerId = this.runner.id
      val parentRunner =
          runnerRepository
              .findBy(this.runner.organizationId, this.runner.workspaceId, parentId)
              .orElseThrow {
                IllegalArgumentException("Parent Id $parentId define on $runnerId does not exists")
              }
      val parentBaseDatasets = parentRunner.datasets.bases
      if (parentBaseDatasets.isNotEmpty() && newDatasetList == null) {
        this.runner.datasets.bases = parentBaseDatasets
      }
    }

    fun setAccessControl(runnerAccessControl: RunnerAccessControl) {
      // create a rbacSecurity object from runner Rbac by adding user with id and role in
      // runnerAccessControl
      val rbacSecurity =
          csmRbac.setUserRole(
              this.runner.getRbac(),
              runnerAccessControl.id,
              runnerAccessControl.role,
              this.roleDefinition)
      this.setRbacSecurity(rbacSecurity)

      val userId = runnerAccessControl.id
      val datasetRole = runnerAccessControl.role.takeUnless { it == ROLE_VALIDATOR } ?: ROLE_USER
      val organizationId = this.runner.organizationId
      val workspaceId = this.runner.workspaceId

      // Assign roles on parameter datasets if not already present on dataset resource
      val parameterDataset =
          datasetApiService.findByOrganizationIdWorkspaceIdAndDatasetId(
              organizationId, workspaceId, this.runner.datasets.parameter)
      parameterDataset?.let {
        addUserAccessControlOnDataset(parameterDataset, RunnerAccessControl(userId, datasetRole))
      }
    }

    fun getAccessControlFor(userId: String): RunnerAccessControl {
      val rbacAccessControl = csmRbac.getAccessControl(this.getRbacSecurity(), userId)
      return RunnerAccessControl(rbacAccessControl.id, rbacAccessControl.role)
    }

    fun deleteAccessControlFor(userId: String) {
      // create a rbacSecurity object from runner Rbac by removing user
      val rbacSecurity = csmRbac.removeUser(this.getRbacSecurity(), userId, this.roleDefinition)
      this.setRbacSecurity(rbacSecurity)

      this.removeAccessControlToDatasetParameter(userId)
    }

    fun checkUserExists(userId: String) {
      csmRbac.checkUserExists(
          runner.getRbac(), userId, "User '$userId' not found in runner ${runner.id}")
    }

    private fun getRbacSecurity(): RbacSecurity {
      return extractRbacSecurity(this.runner.security)
    }

    fun setRbacSecurity(rbacSecurity: RbacSecurity) = apply {
      this.runner.security =
          RunnerSecurity(
              rbacSecurity.default,
              rbacSecurity.accessControlList
                  .distinctBy { it.id }
                  .map { RunnerAccessControl(it.id, it.role) }
                  .toMutableList())
    }

    private fun extractRbacSecurity(security: RunnerSecurity): RbacSecurity {
      return RbacSecurity(
          this.runner.id,
          security.default,
          security.accessControlList.map { RbacAccessControl(it.id, it.role) }.toMutableList())
    }

    private fun constructParametersValuesFromSolution(
        runTemplateParameters: List<RunTemplateParameter>
    ): List<RunnerRunTemplateParameterValue> {

      val runnerParameters = mutableListOf<RunnerRunTemplateParameterValue>()

      runTemplateParameters
          .filter {
            it.varType != DATASET_PART_VARTYPE_FILE && it.varType != DATASET_PART_VARTYPE_DB
          }
          .forEach { runTemplateParameter ->
            runnerParameters.add(
                RunnerRunTemplateParameterValue(
                    parameterId = runTemplateParameter.id,
                    varType = runTemplateParameter.varType,
                    value = runTemplateParameter.defaultValue ?: "",
                ))
          }

      return runnerParameters
    }

    private fun constructDatasetParametersFromSolution(
        runTemplateParameters: List<RunTemplateParameter>,
        solutionId: String
    ): List<DatasetPart> {

      val datasetPartList = mutableListOf<DatasetPart>()
      runTemplateParameters
          .filter {
            it.varType == DATASET_PART_VARTYPE_FILE || it.varType == DATASET_PART_VARTYPE_DB
          }
          .filter { !it.defaultValue.isNullOrBlank() }
          .forEach { runTemplateParameter ->
            datasetPartList.add(
                createDatasetPartInDatasetParameter(
                    organization!!.id,
                    workspace!!.id,
                    solutionId,
                    runner.datasets.parameter,
                    RunnerRunTemplateParameterValue(
                        parameterId = runTemplateParameter.id,
                        varType = runTemplateParameter.varType,
                        value = runTemplateParameter.defaultValue!!),
                    runTemplateParameter.id,
                    runner.id))
          }
      return datasetPartList
    }

    @Suppress("NestedBlockDepth")
    private fun constructParametersValuesFromParent(
        runTemplateParametersIds: List<String>,
        parentId: String,
        parent: Runner,
        runner: Runner
    ): List<RunnerRunTemplateParameterValue> {
      val parametersValuesList = mutableListOf<RunnerRunTemplateParameterValue>()

      if (runTemplateParametersIds.isNotEmpty()) {
        logger.debug("Copying parameters values from parent $parentId")
        val parentParameters =
            parent.parametersValues
                .filter { !it.varType.isNullOrBlank() }
                .filter {
                  it.varType!! != DATASET_PART_VARTYPE_FILE &&
                      it.varType!! != DATASET_PART_VARTYPE_DB
                }
                .associateBy { it.parameterId }
        val runnerParameters =
            runner.parametersValues
                .filter { !it.varType.isNullOrBlank() }
                .filter {
                  it.varType!! != DATASET_PART_VARTYPE_FILE &&
                      it.varType!! != DATASET_PART_VARTYPE_DB
                }
                .associateBy { it.parameterId }

        runTemplateParametersIds
            .filter { !runnerParameters.contains(it) }
            .filter { parentParameters.contains(it) }
            .forEach { parameterId ->
              logger.debug("Copying parameter value from parent for parameter $parameterId")
              val parameterValue = parentParameters[parameterId]
              if (parameterValue != null) {
                parameterValue.isInherited = true
                parametersValuesList.add(parameterValue)
              } else {
                logger.warn(
                    "Parameter $parameterId not found in parent ($parentId) parameters values")
              }
            }
      }
      return parametersValuesList
    }

    private fun removeAccessControlToDatasetParameter(userId: String) {
      val organizationId = this.runner.organizationId
      val workspaceId = this.runner.workspaceId
      val datasetId = this.runner.datasets.parameter
      val datasetACL =
          datasetApiService
              .findByOrganizationIdWorkspaceIdAndDatasetId(organizationId, workspaceId, datasetId)
              ?.security
              ?.accessControlList
              ?.toList()

      if (datasetACL != null && datasetACL.any { it.id == userId })
          datasetApiService.deleteDatasetAccessControl(
              organizationId, workspaceId, datasetId, userId)
    }

    fun getUsers(): List<String> {
      return csmRbac.getUsers(this.getRbacSecurity())
    }

    fun setDefaultSecurity(role: String) {
      // create a rbacSecurity object from runner Rbac by changing default value
      val rbacSecurity = csmRbac.setDefault(this.getRbacSecurity(), role, this.roleDefinition)
      this.setRbacSecurity(rbacSecurity)
    }
  }

  private fun addUserAccessControlOnDataset(dataset: Dataset, roleDefinition: RunnerAccessControl) {
    val newDatasetAcl = dataset.security.accessControlList.toList()
    if (newDatasetAcl.none { it.id == roleDefinition.id }) {
      datasetApiService.createDatasetAccessControl(
          organization!!.id,
          workspace!!.id,
          dataset.id,
          DatasetAccessControl(roleDefinition.id, roleDefinition.role))
    }
  }
}

fun RunnerSecurity?.toGenericSecurity(runnerId: String) =
    RbacSecurity(
        runnerId,
        this?.default ?: ROLE_NONE,
        this?.accessControlList?.map { RbacAccessControl(it.id, it.role) }?.toMutableList()
            ?: mutableListOf())
