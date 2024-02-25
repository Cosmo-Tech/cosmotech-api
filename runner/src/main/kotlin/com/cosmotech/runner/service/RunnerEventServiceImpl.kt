// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.runner.service

import com.cosmotech.api.events.TriggerRunnerEvent
import com.cosmotech.runner.RunnerEventServiceInterface
import com.cosmotech.runner.api.RunnerApiService
import org.springframework.stereotype.Service

@Service
class RunnerEventServiceImpl(private val runnerApiService: RunnerApiService) :
    RunnerEventServiceInterface {
  override fun startNewRun(triggerEvent: TriggerRunnerEvent) {
    val organizationId = triggerEvent.organizationId
    val workspaceId = triggerEvent.workspaceId
    val runnerId = triggerEvent.runnerId

    val runInfo = runnerApiService.startRun(organizationId, workspaceId, runnerId)
    triggerEvent.response = runInfo.runnerRunId
  }
}
