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
import com.cosmotech.api.rbac.ROLE_ADMIN
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.rbac.ROLE_USER
import com.cosmotech.api.rbac.ROLE_VALIDATOR
import com.cosmotech.api.rbac.RolesDefinition
import com.cosmotech.api.rbac.getRunnerRolesDefinition
import com.cosmotech.api.rbac.model.RbacAccessControl
import com.cosmotech.api.rbac.model.RbacSecurity
import com.cosmotech.api.utils.compareToAndMutateIfNeeded
import com.cosmotech.api.utils.getCurrentAccountIdentifier
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
import org.springframework.context.annotation.Scope
import org.springframework.data.domain.PageRequest
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

    // Notify the deletion
    val runnerDeleted =
        RunnerDeleted(this, runner.organizationId!!, runner.workspaceId!!, runner.id!!)
    this.eventPublisher.publishEvent(runnerDeleted)

    return runnerRepository.delete(runnerInstance.getRunnerDataObjet())
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
    val currentUser = getCurrentAccountIdentifier(this.csmPlatformProperties)

    return runnerRepository
        .findByWorkspaceIdAndSecurity(
            organization!!.id!!, workspace!!.id!!, currentUser, pageRequest)
        .toList()
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
          arrayOf("id", "ownerId", "organizationId", "workspaceId", "creationDate", "security")
      this.runner.compareToAndMutateIfNeeded(runner, excludedFields = excludeFields)

      // take newly added datasets and propagate existing ACL on it
      this.runner.datasetList
          .filterNot { beforeMutateDatasetList.contains(it) }
          .forEach { newDatasetId ->
            this.runner.security?.accessControlList?.forEach {
              this.propagateAccessControlToDataset(newDatasetId, it.id, it.role)
            }
          }
    }

    fun setSecurityFrom(runner: Runner): RunnerInstance = apply {
      val rbacSecurity = extractRbacSecurity(runner) ?: return@apply
      this.setRbacSecurity(rbacSecurity)
    }

    fun setLastRunId(runInfo: String) {
      this.runner.lastRunId = runInfo
    }

    fun initSecurity(): RunnerInstance = apply {
      val userId = getCurrentAccountIdentifier(csmPlatformProperties)
      this.runner.security =
          RunnerSecurity(
              default = ROLE_NONE,
              accessControlList = mutableListOf(RunnerAccessControl(userId, ROLE_ADMIN)))
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
        this.runner.parametersValues?.addAll(inheritedParameterValues)
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

      this.propagateAccessControlToDatasets(runnerAccessControl.id, runnerAccessControl.role)
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

    private fun getRbacSecurity(): RbacSecurity {
      return extractRbacSecurity(this.runner)!!
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

    private fun extractRbacSecurity(runner: Runner): RbacSecurity? {
      if (runner.security == null) {
        return null
      }
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
      this.runner.datasetList.forEach { datasetId ->
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
}
