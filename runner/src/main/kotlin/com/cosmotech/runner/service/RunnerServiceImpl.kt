// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.runner.service

import com.cosmotech.api.CsmPhoenixService
import com.cosmotech.runner.api.RunnerApiService
import com.cosmotech.runner.domain.Runner
import com.cosmotech.runner.domain.RunnerAccessControl
import com.cosmotech.runner.domain.RunnerRole
import com.cosmotech.runner.domain.RunnerSecurity
import com.cosmotech.runner.repository.RunnerRepository
import org.springframework.stereotype.Service

@Service
@Suppress("TooManyFunctions", "UnusedPrivateMember")
internal class RunnerServiceImpl(private val runnerRepository: RunnerRepository) :
    CsmPhoenixService(), RunnerApiService {
  override fun addRunnerAccessControl(
      organizationId: String,
      workspaceId: String,
      runnerId: String,
      runnerAccessControl: RunnerAccessControl
  ): RunnerAccessControl {
    TODO("Not yet implemented")
  }

  override fun createRunner(organizationId: String, workspaceId: String, runner: Runner): Runner {
    TODO("Not yet implemented")
  }

  override fun deleteRunner(organizationId: String, workspaceId: String, runnerId: String) {
    TODO("Not yet implemented")
  }

  override fun findRunner(organizationId: String, workspaceId: String, runnerId: String): Runner {
    TODO("Not yet implemented")
  }

  override fun getRunnerAccessControl(
      organizationId: String,
      workspaceId: String,
      runnerId: String,
      identityId: String
  ): RunnerAccessControl {
    TODO("Not yet implemented")
  }

  override fun getRunnerPermissions(
      organizationId: String,
      workspaceId: String,
      runnerId: String,
      role: String
  ): List<String> {
    TODO("Not yet implemented")
  }

  override fun getRunnerSecurity(
      organizationId: String,
      workspaceId: String,
      runnerId: String
  ): RunnerSecurity {
    TODO("Not yet implemented")
  }

  override fun getRunnerSecurityUsers(
      organizationId: String,
      workspaceId: String,
      runnerId: String
  ): List<String> {
    TODO("Not yet implemented")
  }

  override fun listRunners(
      organizationId: String,
      workspaceId: String,
      page: Int?,
      size: Int?
  ): List<Runner> {
    TODO("Not yet implemented")
  }

  override fun removeRunnerAccessControl(
      organizationId: String,
      workspaceId: String,
      runnerId: String,
      identityId: String
  ) {
    TODO("Not yet implemented")
  }

  override fun setRunnerDefaultSecurity(
      organizationId: String,
      workspaceId: String,
      runnerId: String,
      runnerRole: RunnerRole
  ): RunnerSecurity {
    TODO("Not yet implemented")
  }

  override fun startRun(organizationId: String, workspaceId: String, runnerId: String): String {
    TODO("Not yet implemented")
  }

  override fun stopRun(organizationId: String, workspaceId: String, runnerId: String): String {
    TODO("Not yet implemented")
  }

  override fun updateRunner(
      organizationId: String,
      workspaceId: String,
      runnerId: String,
      runner: Runner
  ): Runner {
    TODO("Not yet implemented")
  }

  override fun updateRunnerAccessControl(
      organizationId: String,
      workspaceId: String,
      runnerId: String,
      identityId: String,
      runnerRole: RunnerRole
  ): RunnerAccessControl {
    TODO("Not yet implemented")
  }
}
