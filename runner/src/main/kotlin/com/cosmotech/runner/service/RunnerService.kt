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
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.dataset.DatasetApiServiceInterface
import com.cosmotech.dataset.domain.DatasetAccessControl
import com.cosmotech.dataset.domain.DatasetRole
import com.cosmotech.dataset.service.getRbac
import com.cosmotech.organization.OrganizationApiServiceInterface
import com.cosmotech.organization.domain.Organization
import com.cosmotech.runner.domain.CreatedRun
import com.cosmotech.runner.domain.Runner
import com.cosmotech.runner.domain.RunnerAccessControl
import com.cosmotech.runner.domain.RunnerRunTemplateParameterValue
import com.cosmotech.runner.domain.RunnerSecurity
import com.cosmotech.runner.repository.RunnerRepository
import com.cosmotech.solution.SolutionApiServiceInterface
import com.cosmotech.solution.domain.RunTemplate
import com.cosmotech.solution.domain.Solution
import com.cosmotech.workspace.WorkspaceApiServiceInterface
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.service.getRbac
import java.time.Instant
import kotlin.collections.mutableListOf
import org.springframework.context.annotation.Scope
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

@Component
@Scope("prototype")
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

  fun inOrganization(organizationId: String): RunnerService = apply {
    this.organization = organizationApiService.findOrganizationById(organizationId)
  }

  fun inWorkspace(workspaceId: String): RunnerService = apply {
    if (this.organization == null) {
      throw IllegalArgumentException(
          "RunnerService's organization needs to be set. use inOrganization to do so.")
    }

    this.workspace = workspaceApiService.findWorkspaceById(this.organization!!.id!!, workspaceId)
  }

  fun userHasPermissionOnWorkspace(permission: String): RunnerService = apply {
    if (this.workspace == null) {
      throw IllegalArgumentException(
          "RunnerService's workspace needs to be set. Use inWorkspace to do so.")
    }

    csmRbac.verify(workspace!!.getRbac(), permission)
  }

  fun deleteInstance(runnerInstance: RunnerInstance) {
    val runner = runnerInstance.runner

    // Check there are no running runs
    val hasRunningRuns =
        HasRunningRuns(this, runner.organizationId!!, runner.workspaceId!!, runner.id!!)
    this.eventPublisher.publishEvent(hasRunningRuns)
    if (hasRunningRuns.response == true) {
      throw CsmClientException(
          "Can't delete runner ${runner.id!!}: at least one run is still running")
    }

    // Update parent and root references to deleted runner
    var newRoots = mutableListOf<Runner>()
    listAllRunnerByParentId(runner.organizationId!!, runner.workspaceId!!, runner.id!!).forEach {
      it.parentId = runner.parentId
      // Runner was root, child is now root
      if (runner.rootId == null) {
        it.rootId = null
        newRoots.add(it)
      }
      runnerRepository.save(it)
    }

    // Update new root ids
    newRoots.forEach { updateChildrenRootId(parent = it, newRootId = it.id!!) }

    // Notify the deletion
    val runnerDeleted =
        RunnerDeleted(this, runner.organizationId!!, runner.workspaceId!!, runner.id!!)
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

    var runners = mutableListOf<Runner>()

    do {
      val pagedRunners =
          runnerRepository.findByParentId(organizationId, workspaceId, parentId, pageRequest)
      runners.addAll(pagedRunners.toList())
      pageRequest = pagedRunners.nextPageable()
    } while (pagedRunners.hasNext())

    return runners
  }

  private fun updateChildrenRootId(parent: Runner, newRootId: String) {
    listAllRunnerByParentId(parent.organizationId!!, parent.workspaceId!!, parent.id!!).forEach {
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
        runnerRepository.findBy(organization!!.id!!, workspace!!.id!!, runnerId).orElseThrow {
          CsmResourceNotFoundException(
              "Runner $runnerId not found in workspace ${workspace!!.id} and organization ${organization!!.id}")
        }

    return RunnerInstance(runner).userHasPermission(PERMISSION_READ)
  }

  fun listInstances(pageRequest: PageRequest): List<Runner> {
    val isPlatformAdmin =
        getCurrentAuthenticatedRoles(this.csmPlatformProperties).contains(ROLE_PLATFORM_ADMIN)
    return if (!this.csmPlatformProperties.rbac.enabled || isPlatformAdmin) {
      runnerRepository
          .findByWorkspaceId(organization!!.id!!, workspace!!.id!!, pageRequest)
          .toList()
    } else {
      val currentUser = getCurrentAccountIdentifier(this.csmPlatformProperties)
      runnerRepository
          .findByWorkspaceIdAndSecurity(
              organization!!.id!!, workspace!!.id!!, currentUser, pageRequest)
          .toList()
    }
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
  inner class RunnerInstance(var runner: Runner = Runner()) {
    private val roleDefinition: RolesDefinition = getRunnerRolesDefinition()

    fun initialize(): RunnerInstance = apply {
      val now = Instant.now().toEpochMilli()
      this.runner =
          Runner(
              id = idGenerator.generate("runner"),
              ownerId = getCurrentAuthenticatedUserName(csmPlatformProperties),
              organizationId = organization!!.id,
              workspaceId = workspace!!.id,
              creationDate = now,
              lastUpdate = now)
    }

    fun getRunnerDataObjet(): Runner = this.runner

    fun userHasPermission(permission: String): RunnerInstance = apply {
      csmRbac.verify(this.getRbacSecurity(), permission, this.roleDefinition)
    }

    fun setValueFrom(runner: Runner): RunnerInstance = apply {
      if (runner.runTemplateId.isNullOrEmpty())
          throw IllegalArgumentException("runner does not have a runTemplateId define")
      if (!solutionApiService.isRunTemplateExist(
          organization!!.id!!,
          workspace!!.id!!,
          workspace!!.solution.solutionId!!,
          runner.runTemplateId!!))
          throw IllegalArgumentException("Run Template not found: ${runner.runTemplateId}")

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
          ?.filterNot { beforeMutateDatasetList.contains(it) }
          ?.mapNotNull {
            datasetApiService.findByOrganizationIdAndDatasetId(organization!!.id!!, it)
          }
          ?.forEach { newDataset ->
            this.runner.security?.accessControlList?.forEach { roleDefinition ->
              val newDatasetAcl = newDataset.getRbac().accessControlList
              if (newDatasetAcl.none { it.id == roleDefinition.id }) {
                datasetApiService.addOrUpdateAccessControl(
                    organization!!.id!!, newDataset, roleDefinition.id, roleDefinition.role)
              }
            }
          }
    }

    fun setLastRunId(runInfo: String) {
      this.runner.lastRunId = runInfo
    }

    fun initSecurity(runner: Runner): RunnerInstance = apply {
      val rbacSecurity = csmRbac.initSecurity(extractRbacSecurity(runner))
      setRbacSecurity(rbacSecurity)
    }

    fun initParameters(): RunnerInstance = apply {
      val parentId = this.runner.parentId
      val runnerId = this.runner.id
      if (parentId != null) {
        val parentRunner =
            runnerRepository
                .findBy(this.runner.organizationId!!, this.runner.workspaceId!!, parentId)
                .orElseThrow {
                  IllegalArgumentException(
                      "Parent Id $parentId define on $runnerId does not exists")
                }
        val solution =
            workspace?.solution?.solutionId?.let {
              solutionApiService.findSolutionById(organization?.id!!, it)
            }
        val runTemplateId = this.runner.runTemplateId
        val runTemplate =
            solution?.runTemplates?.find { runTemplate -> runTemplate.id == runTemplateId }
        if (runTemplateId != null && runTemplate == null) {
          throw IllegalArgumentException("Run Template not found: $runTemplateId")
        }
        val inheritedParameterValues =
            constructParametersValuesFromParent(
                parentId, solution, runTemplate, parentRunner, this.runner)
        val parameterValueList = this.runner.parametersValues ?: mutableListOf()
        parameterValueList.addAll(inheritedParameterValues)
        this.runner.parametersValues = parameterValueList
        consolidateParametersVarType()

        // Compute rootId
        this.runner.parentId?.let {
          this.runner.rootId =
              runnerRepository
                  .findBy(organization!!.id!!, workspace!!.id!!, it)
                  .orElseThrow { IllegalArgumentException("Parent runner not found: ${it}") }
                  .rootId
                  ?: this.runner.parentId
        }
      }
    }

    fun consolidateParametersVarType() {
      val solutionParameters =
          workspace
              ?.solution
              ?.solutionId
              ?.let { solutionApiService.findSolutionById(organization?.id!!, it) }
              ?.parameters

      this.runner.parametersValues?.forEach { runnerParam ->
        solutionParameters
            ?.find { it.id == runnerParam.parameterId }
            ?.varType
            ?.let { runnerParam.varType = it }
      }
    }

    fun initDatasetList(): RunnerInstance = apply {
      val parentId = this.runner.parentId
      val runnerId = this.runner.id
      if (parentId != null) {
        val parentRunner =
            runnerRepository
                .findBy(this.runner.organizationId!!, this.runner.workspaceId!!, parentId)
                .orElseThrow {
                  IllegalArgumentException(
                      "Parent Id $parentId define on $runnerId does not exists")
                }
        val parentDatasetList = parentRunner.datasetList ?: mutableListOf()
        val runnerDatasetList = this.runner.datasetList

        if (parentDatasetList.isNotEmpty()) {
          if (runnerDatasetList == null) {
            this.runner.datasetList = parentDatasetList
          }
        }
      }

      if (this.runner.datasetList == null) {
        this.runner.datasetList = mutableListOf()
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
      val organizationId = this.runner.organizationId!!

      // Assign roles on linked datasets if not already present on dataset resource
      this.runner.datasetList!!
          .mapNotNull { datasetApiService.findByOrganizationIdAndDatasetId(organizationId, it) }
          .forEach { dataset ->
            val datasetAcl = dataset.getRbac().accessControlList
            if (datasetAcl.none { it.id == userId }) {
              datasetApiService.addOrUpdateAccessControl(
                  organizationId, dataset, userId, datasetRole)
            }
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
      return extractRbacSecurity(this.runner)
    }

    private fun setRbacSecurity(rbacSecurity: RbacSecurity) {
      this.runner.security =
          RunnerSecurity(
              rbacSecurity.default,
              rbacSecurity.accessControlList
                  .distinctBy { it.id }
                  .map { RunnerAccessControl(it.id, it.role) }
                  .toMutableList())
    }

    private fun extractRbacSecurity(runner: Runner): RbacSecurity {
      return RbacSecurity(
          runner.id,
          runner.security?.default ?: ROLE_NONE,
          runner.security
              ?.accessControlList
              ?.map { RbacAccessControl(it.id, it.role) }
              ?.toMutableList()
              ?: mutableListOf())
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
              ?.flatMap { parameterGroup -> parameterGroup.parameters ?: mutableListOf() }

      if (!runTemplateParametersIds.isNullOrEmpty()) {
        val parentParameters =
            parent.parametersValues?.associate { it.parameterId to it } ?: mutableMapOf()
        val runnerParameters =
            runner.parametersValues?.associate { it.parameterId to it } ?: mutableMapOf()

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

    private fun propagateAccessControlToDatasets(userId: String, role: String) {
      this.runner.datasetList?.forEach { datasetId ->
        propagateAccessControlToDataset(datasetId, userId, role)
      }
    }

    private fun propagateAccessControlToDataset(datasetId: String, userId: String, role: String) {
      val datasetRole = role.takeUnless { it == ROLE_VALIDATOR } ?: ROLE_USER
      val organizationId = this.runner.organizationId!!

      val datasetUsers = datasetApiService.getDatasetSecurityUsers(organizationId, datasetId)
      if (datasetUsers.contains(userId)) {
        datasetApiService.updateDatasetAccessControl(
            organizationId, datasetId, userId, DatasetRole(datasetRole))
      } else {
        datasetApiService.addDatasetAccessControl(
            organizationId, datasetId, DatasetAccessControl(userId, datasetRole))
      }
    }

    private fun removeAccessControlToDatasets(userId: String) {
      val organizationId = this.runner.organizationId!!
      this.runner.datasetList?.forEach { datasetId ->
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
}
