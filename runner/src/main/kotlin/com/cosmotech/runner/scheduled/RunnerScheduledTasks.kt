// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.runner.scheduled

import com.cosmotech.runner.service.RunnerService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class RunnerScheduledTasks(
    private val runnerService: RunnerService,
) {
  @Scheduled(fixedDelay = 60 * 1000) // every 1 minute
  fun cleanupArchivedRunners() {
    runnerService.cleanupArchived()
  }
}
