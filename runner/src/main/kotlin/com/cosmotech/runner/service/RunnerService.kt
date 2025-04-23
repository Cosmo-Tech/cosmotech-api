// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.runner.service

import com.cosmotech.api.CsmPhoenixService
import com.cosmotech.api.events.HasRunningRuns
import com.cosmotech.api.events.RunStart
import com.cosmotech.api.events.RunStop
import com.cosmotech.api.events.RunnerDeleted
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
import com.cosmotech.dataset.service.getRbac
import com.cosmotech.organization.OrganizationApiServiceInterface
import com.cosmotech.organization.domain.Organization
import com.cosmotech.runner.domain.CreatedRun
import com.cosmotech.runner.domain.Runner
import com.cosmotech.runner.domain.RunnerAccessControl
import com.cosmotech.runner.domain.RunnerCreateRequest
import com.cosmotech.runner.domain.RunnerEditInfo
import com.cosmotech.runner.domain.RunnerRunTemplateParameterValue
import com.cosmotech.runner.domain.RunnerSecurity
import com.cosmotech.runner.domain.RunnerUpdateRequest
import com.cosmotech.runner.domain.RunnerValidationStatus
import com.cosmotech.runner.repository.RunnerRepository
import com.cosmotech.solution.SolutionApiServiceInterface
import com.cosmotech.solution.domain.RunTemplate
import com.cosmotech.solution.domain.Solution
import com.cosmotech.workspace.WorkspaceApiServiceInterface
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.service.toGenericSecurity
import java.time.Instant
import kotlin.collections.mutableListOf
import org.springframework.context.annotation.Scope
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

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
    val runner =
        runnerRepository.findBy(organization!!.id, workspace!!.id, runnerId).orElseThrow {
          CsmResourceNotFoundException(
              "Runner $runnerId not found in workspace ${workspace!!.id} and organization ${organization!!.id}")
        }
    updateSecurityVisibility(runner)
    return RunnerInstance().initializeFrom(runner).userHasPermission(PERMISSION_READ)
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
    runnerInstance.setLastRunId(runId)
    runnerRepository.save(runnerInstance.getRunnerDataObjet())
    return CreatedRun(id = runId)
  }

  fun stopLastRunOf(runnerInstance: RunnerInstance) {
    val runner = runnerInstance.getRunnerDataObjet()
    runner.lastRunId
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
              datasetList = mutableListOf(),
              parametersValues = mutableListOf(),
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

      val beforeMutateDatasetList = this.runner.datasetList

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

      // take newly added datasets and propagate existing ACL on it
      this.runner.datasetList
          .filterNot { beforeMutateDatasetList.contains(it) }
          .mapNotNull { datasetApiService.findByOrganizationIdAndDatasetId(organization!!.id, it) }
          .forEach { dataset ->
            this.runner.security.accessControlList.forEach { roleDefinition ->
              addUserAccessControlOnDataset(dataset, roleDefinition)
            }
          }
    }

    fun setValueFrom(runnerCreateRequest: RunnerCreateRequest): RunnerInstance {

      val security =
          csmRbac.initSecurity(runnerCreateRequest.security.toGenericSecurity(this.runner.id))

      this.setRbacSecurity(security)

      return setValueFrom(
          Runner(
              runSizing = runnerCreateRequest.runSizing,
              tags = runnerCreateRequest.tags,
              description = runnerCreateRequest.description,
              name = runnerCreateRequest.name,
              runTemplateId = runnerCreateRequest.runTemplateId,
              datasetList = runnerCreateRequest.datasetList ?: mutableListOf(),
              parametersValues = runnerCreateRequest.parametersValues ?: mutableListOf(),
              parentId = runnerCreateRequest.parentId,
              lastRunId = this.runner.lastRunId,
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
      return setValueFrom(
          Runner(
              runSizing = runnerUpdateRequest.runSizing ?: this.runner.runSizing,
              tags = runnerUpdateRequest.tags ?: this.runner.tags,
              description = runnerUpdateRequest.description ?: this.runner.description,
              name = runnerUpdateRequest.name ?: this.runner.name,
              runTemplateId = runnerUpdateRequest.runTemplateId ?: this.runner.runTemplateId,
              datasetList = runnerUpdateRequest.datasetList ?: this.runner.datasetList,
              parametersValues =
                  runnerUpdateRequest.parametersValues ?: this.runner.parametersValues,
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
              lastRunId = this.runner.lastRunId,
              validationStatus = this.runner.validationStatus))
    }

    fun setLastRunId(runInfo: String) {
      this.runner.lastRunId = runInfo
    }

    fun initParameters(): RunnerInstance = apply {
      val parentId = this.runner.parentId
      val runnerId = this.runner.id
      if (parentId != null) {
        val parentRunner =
            runnerRepository
                .findBy(this.runner.organizationId, this.runner.workspaceId, parentId)
                .orElseThrow {
                  IllegalArgumentException(
                      "Parent Id $parentId define on $runnerId does not exists")
                }
        val solution =
            workspace?.solution?.solutionId?.let {
              solutionApiService.getSolution(organization?.id!!, it)
            }
        val runTemplateId = this.runner.runTemplateId
        val runTemplate =
            solution?.runTemplates?.find { runTemplate -> runTemplate.id == runTemplateId }
        requireNotNull(runTemplate) { "Run Template not found: $runTemplateId" }
        val inheritedParameterValues =
            constructParametersValuesFromParent(
                parentId, solution, runTemplate, parentRunner, this.runner)
        val parameterValueList = this.runner.parametersValues
        parameterValueList.addAll(inheritedParameterValues)
        this.runner.parametersValues = parameterValueList
        consolidateParametersVarType()

        // Compute rootId
        this.runner.parentId?.let {
          this.runner.rootId =
              runnerRepository
                  .findBy(organization!!.id, workspace!!.id, it)
                  .orElseThrow { IllegalArgumentException("Parent runner not found: $it") }
                  .rootId ?: this.runner.parentId
        }
      }
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

    fun initDatasetList(runnerCreateRequest: RunnerCreateRequest): RunnerInstance = apply {
      val parentId = this.runner.parentId
      val runnerId = this.runner.id
      if (parentId != null) {
        val parentRunner =
            runnerRepository
                .findBy(this.runner.organizationId, this.runner.workspaceId, parentId)
                .orElseThrow {
                  IllegalArgumentException(
                      "Parent Id $parentId define on $runnerId does not exists")
                }
        val parentDatasetList = parentRunner.datasetList

        if (parentDatasetList.isNotEmpty()) {
          if (runnerCreateRequest.datasetList == null) {
            this.runner.datasetList = parentDatasetList
          }
        }
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

      // Assign roles on linked datasets if not already present on dataset resource
      this.runner.datasetList
          .mapNotNull { datasetApiService.findByOrganizationIdAndDatasetId(organizationId, it) }
          .forEach { dataset ->
            addUserAccessControlOnDataset(dataset, RunnerAccessControl(userId, datasetRole))
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

      this.removeAccessControlToDatasets(userId)
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

    @Suppress("NestedBlockDepth")
    private fun constructParametersValuesFromParent(
        parentId: String?,
        solution: Solution?,
        runTemplate: RunTemplate?,
        parent: Runner,
        runner: Runner
    ): MutableList<RunnerRunTemplateParameterValue> {
      val parametersValuesList = mutableListOf<RunnerRunTemplateParameterValue>()
      logger.debug("Copying parameters values from parent $parentId")

      logger.debug("Getting runTemplate parameters ids")
      val runTemplateParametersIds =
          solution
              ?.parameterGroups
              ?.filter { parameterGroup ->
                runTemplate?.parameterGroups?.contains(parameterGroup.id) == true
              }
              ?.flatMap { parameterGroup -> parameterGroup.parameters }

      if (!runTemplateParametersIds.isNullOrEmpty()) {
        val parentParameters = parent.parametersValues.associate { it.parameterId to it }
        val runnerParameters = runner.parametersValues.associate { it.parameterId to it }

        // TODO:
        //  Here parameters values are only retrieved from parent runner
        //  At the moment default parameters values defined in solution.parameters are not handled
        //  This behaviour is inherited from previously removed Scenario module (in which it was not
        // handled too)
        //  Depending on request and priority, it could be defined in next API versions

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

    private fun removeAccessControlToDatasets(userId: String) {
      val organizationId = this.runner.organizationId
      this.runner.datasetList.forEach { datasetId ->
        val datasetACL =
            datasetApiService.findDatasetById(organizationId, datasetId).getRbac().accessControlList

        if (datasetACL.any { it.id == userId })
            datasetApiService.removeDatasetAccessControl(organizationId, datasetId, userId)
      }
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
    val newDatasetAcl = dataset.getRbac().accessControlList
    if (newDatasetAcl.none { it.id == roleDefinition.id }) {
      datasetApiService.addOrUpdateAccessControl(
          organization!!.id, dataset, roleDefinition.id, roleDefinition.role)
    }
  }
}

fun RunnerSecurity?.toGenericSecurity(runnerId: String) =
    RbacSecurity(
        runnerId,
        this?.default ?: ROLE_NONE,
        this?.accessControlList?.map { RbacAccessControl(it.id, it.role) }?.toMutableList()
            ?: mutableListOf())
