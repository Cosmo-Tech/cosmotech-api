// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run.service

import com.cosmotech.run.api.RunApiService
import com.cosmotech.run.domain.Run
import com.cosmotech.run.domain.RunLogs
import com.cosmotech.run.domain.RunStatus
import org.springframework.stereotype.Service

@Service
class RunServiceImpl : RunApiService {
  override fun deleteRun(
      organizationId: String,
      workspaceId: String,
      runnerId: String,
      runId: String
  ) {
    TODO("Not yet implemented")
  }

  override fun findRunById(
      organizationId: String,
      workspaceId: String,
      runnerId: String,
      runId: String
  ): Run {
    TODO("Not yet implemented")
  }

  override fun getRunCumulatedLogs(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      runId: String
  ): String {
    TODO("Not yet implemented")
  }

  override fun getRunLogs(
      organizationId: String,
      workspaceId: String,
      runnerId: String,
      runId: String
  ): RunLogs {
    TODO("Not yet implemented")
  }

  override fun getRunStatus(
      organizationId: String,
      workspaceId: String,
      runnerId: String,
      runId: String
  ): RunStatus {
    TODO("Not yet implemented")
  }

  override fun getRuns(
      organizationId: String,
      workspaceId: String,
      runnerId: String,
      page: Int?,
      size: Int?
  ): List<Run> {
    TODO("Not yet implemented")
  }
}
