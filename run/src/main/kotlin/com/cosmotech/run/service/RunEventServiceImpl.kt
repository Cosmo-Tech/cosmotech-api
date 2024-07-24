// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run.service

import com.cosmotech.api.events.AskRunStatusEvent
import com.cosmotech.api.events.HasRunningRuns
import com.cosmotech.run.RunApiServiceInterface
import com.cosmotech.run.RunEventServiceInterface
import com.cosmotech.run.domain.RunState
import org.springframework.stereotype.Service

@Service
class RunEventServiceImpl(private val runApiService: RunApiServiceInterface) :
    RunEventServiceInterface {
  override fun getRunStatus(askRunStatusEvent: AskRunStatusEvent) {
    val organizationId = askRunStatusEvent.organizationId
    val workspaceId = askRunStatusEvent.workspaceId
    val runnerId = askRunStatusEvent.runnerId
    val runId = askRunStatusEvent.runId

    val runStatus = runApiService.getRunStatus(organizationId, workspaceId, runnerId, runId)
    askRunStatusEvent.response =
        runStatus.state?.value ?: throw IllegalStateException("Run ${runId} doesn't have state")
  }

  override fun hasRunningRuns(hasRunningRuns: HasRunningRuns) {
    val organizationId = hasRunningRuns.organizationId
    val workspaceId = hasRunningRuns.workspaceId
    val runnerId = hasRunningRuns.runnerId

    hasRunningRuns.response =
        runApiService.listAllRuns(organizationId, workspaceId, runnerId).any {
          val runState =
              runApiService.getRunStatus(organizationId, workspaceId, runnerId, it.id!!).state
                  ?: RunState.Unknown
          runState == RunState.Running
        }
  }
}
