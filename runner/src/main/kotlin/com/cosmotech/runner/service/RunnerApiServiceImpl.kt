// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.runner.service

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.events.RunDeleted
import com.cosmotech.api.rbac.PERMISSION_CREATE_CHILDREN
import com.cosmotech.api.rbac.PERMISSION_DELETE
import com.cosmotech.api.rbac.PERMISSION_LAUNCH
import com.cosmotech.api.rbac.PERMISSION_READ_SECURITY
import com.cosmotech.api.rbac.PERMISSION_WRITE
import com.cosmotech.api.rbac.PERMISSION_WRITE_SECURITY
import com.cosmotech.api.rbac.getScenarioRolesDefinition
import com.cosmotech.api.utils.constructPageRequest
import com.cosmotech.runner.RunnerApiServiceInterface
import com.cosmotech.runner.domain.Runner
import com.cosmotech.runner.domain.RunnerAccessControl
import com.cosmotech.runner.domain.RunnerLastRun
import com.cosmotech.runner.domain.RunnerRole
import com.cosmotech.runner.domain.RunnerSecurity
import org.springframework.context.event.EventListener
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

@Service
@Suppress("TooManyFunctions", "UnusedPrivateMember")
internal class RunnerApiServiceImpl(
    private val csmPlatformProperties: CsmPlatformProperties,
    private val runnerServiceManager: RunnerServiceManager
) : RunnerApiServiceInterface {
  override fun getRunnerService(): RunnerService = runnerServiceManager.getRunnerService()

  override fun createRunner(organizationId: String, workspaceId: String, runner: Runner): Runner {
    val runnerService =
        getRunnerService()
            .inOrganization(organizationId)
            .inWorkspace(workspaceId)
            .userHasPermissionOnWorkspace(PERMISSION_CREATE_CHILDREN)
    val runnerInstance =
        runnerService.getNewInstance().setValueFrom(runner).initSecurity(runner).initParameters()

    return runnerService.saveInstance(runnerInstance)
  }

  override fun getRunner(organizationId: String, workspaceId: String, runnerId: String): Runner {
    val runnerService = getRunnerService().inOrganization(organizationId).inWorkspace(workspaceId)
    val runnerInstance = runnerService.getInstance(runnerId)

    return runnerInstance.getRunnerDataObjet()
  }

  override fun updateRunner(
      organizationId: String,
      workspaceId: String,
      runnerId: String,
      runner: Runner
  ): Runner {
    val runnerService = getRunnerService().inOrganization(organizationId).inWorkspace(workspaceId)
    val runnerInstance = runnerService.getInstance(runnerId).userHasPermission(PERMISSION_WRITE)

    return runnerService.saveInstance(runnerInstance.setValueFrom(runner))
  }

  override fun deleteRunner(organizationId: String, workspaceId: String, runnerId: String) {
    val runnerService = getRunnerService().inOrganization(organizationId).inWorkspace(workspaceId)
    val runnerInstance = runnerService.getInstance(runnerId).userHasPermission(PERMISSION_DELETE)

    runnerService.deleteInstance(runnerInstance)
  }

  override fun listRunners(
      organizationId: String,
      workspaceId: String,
      page: Int?,
      size: Int?
  ): List<Runner> {
    val runnerService = getRunnerService().inOrganization(organizationId).inWorkspace(workspaceId)

    val defaultPageSize = csmPlatformProperties.twincache.scenario.defaultPageSize
    val pageRequest =
        constructPageRequest(page, size, defaultPageSize) ?: PageRequest.of(0, defaultPageSize)
    return runnerService.listInstances(pageRequest)
  }

  override fun startRun(
      organizationId: String,
      workspaceId: String,
      runnerId: String
  ): RunnerLastRun {
    val runnerService = getRunnerService().inOrganization(organizationId).inWorkspace(workspaceId)

    val runnerInstance = runnerService.getInstance(runnerId).userHasPermission(PERMISSION_LAUNCH)

    return runnerService.startRunWith(runnerInstance)
  }

  override fun stopRun(organizationId: String, workspaceId: String, runnerId: String) {
    val runnerService = getRunnerService().inOrganization(organizationId).inWorkspace(workspaceId)

    val runnerInstance = runnerService.getInstance(runnerId).userHasPermission(PERMISSION_WRITE)

    runnerService.stopLastRunOf(runnerInstance)
  }

  override fun addRunnerAccessControl(
      organizationId: String,
      workspaceId: String,
      runnerId: String,
      runnerAccessControl: RunnerAccessControl
  ): RunnerAccessControl {
    val runnerService = getRunnerService().inOrganization(organizationId).inWorkspace(workspaceId)
    val runnerInstance =
        runnerService.getInstance(runnerId).userHasPermission(PERMISSION_WRITE_SECURITY)

    val users = getRunnerSecurityUsers(organizationId, workspaceId, runnerId)
    if (users.contains(runnerAccessControl.id)) {
      throw IllegalArgumentException("User is already in this Runner security")
    }

    runnerInstance.setAccessControl(runnerAccessControl)
    runnerService.saveInstance(runnerInstance)

    return runnerInstance.getAccessControlFor(runnerAccessControl.id)
  }

  override fun getRunnerAccessControl(
      organizationId: String,
      workspaceId: String,
      runnerId: String,
      identityId: String
  ): RunnerAccessControl {
    val runnerService = getRunnerService().inOrganization(organizationId).inWorkspace(workspaceId)
    val runnerInstance =
        runnerService.getInstance(runnerId).userHasPermission(PERMISSION_READ_SECURITY)

    return runnerInstance.getAccessControlFor(identityId)
  }

  override fun updateRunnerAccessControl(
      organizationId: String,
      workspaceId: String,
      runnerId: String,
      identityId: String,
      runnerRole: RunnerRole
  ): RunnerAccessControl {
    val runnerService = getRunnerService().inOrganization(organizationId).inWorkspace(workspaceId)
    val runnerInstance =
        runnerService.getInstance(runnerId).userHasPermission(PERMISSION_WRITE_SECURITY)
    runnerInstance.checkUserExists(identityId)

    val runnerAccessControl = RunnerAccessControl(identityId, runnerRole.role)
    runnerInstance.setAccessControl(runnerAccessControl)

    runnerService.saveInstance(runnerInstance)

    return runnerInstance.getAccessControlFor(identityId)
  }

  override fun removeRunnerAccessControl(
      organizationId: String,
      workspaceId: String,
      runnerId: String,
      identityId: String
  ) {
    val runnerService = getRunnerService().inOrganization(organizationId).inWorkspace(workspaceId)
    val runnerInstance =
        runnerService.getInstance(runnerId).userHasPermission(PERMISSION_WRITE_SECURITY)

    runnerInstance.deleteAccessControlFor(identityId)

    runnerService.saveInstance(runnerInstance)
  }

  override fun getRunnerSecurity(
      organizationId: String,
      workspaceId: String,
      runnerId: String
  ): RunnerSecurity {
    val runnerService = getRunnerService().inOrganization(organizationId).inWorkspace(workspaceId)
    val runnerInstance =
        runnerService.getInstance(runnerId).userHasPermission(PERMISSION_READ_SECURITY)

    return runnerInstance.getRunnerDataObjet().security!!
  }

  override fun getRunnerPermissions(
      organizationId: String,
      workspaceId: String,
      runnerId: String,
      role: String
  ): List<String> {
    val runnerService = getRunnerService().inOrganization(organizationId).inWorkspace(workspaceId)
    runnerService.getInstance(runnerId).userHasPermission(PERMISSION_READ_SECURITY)

    return com.cosmotech.api.rbac.getPermissions(role, getScenarioRolesDefinition())
  }

  override fun getRunnerSecurityUsers(
      organizationId: String,
      workspaceId: String,
      runnerId: String
  ): List<String> {
    val runnerService = getRunnerService().inOrganization(organizationId).inWorkspace(workspaceId)
    val runnerInstance =
        runnerService.getInstance(runnerId).userHasPermission(PERMISSION_READ_SECURITY)

    return runnerInstance.getUsers()
  }

  override fun setRunnerDefaultSecurity(
      organizationId: String,
      workspaceId: String,
      runnerId: String,
      runnerRole: RunnerRole
  ): RunnerSecurity {
    val runnerService = getRunnerService().inOrganization(organizationId).inWorkspace(workspaceId)
    val runnerInstance =
        runnerService.getInstance(runnerId).userHasPermission(PERMISSION_WRITE_SECURITY)

    runnerInstance.setDefaultSecurity(runnerRole.role)
    runnerService.saveInstance(runnerInstance)

    return runnerInstance.getRunnerDataObjet().security!!
  }

  @EventListener(RunDeleted::class)
  fun onRunDeleted(runDeleted: RunDeleted) {
    val runnerService =
        getRunnerService()
            .inOrganization(runDeleted.organizationId)
            .inWorkspace(runDeleted.workspaceId)
    val runnerInstance = runnerService.getInstance(runDeleted.runnerId)
    if (runnerInstance.getRunnerDataObjet().lastRunId == runDeleted.runId) {
      runnerInstance.getRunnerDataObjet().lastRunId = null
    }
    runnerService.saveInstance(runnerInstance)
  }
}
