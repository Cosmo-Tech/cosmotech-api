// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.runner.scheduled

import com.cosmotech.runner.service.RunnerService
import org.springframework.stereotype.Component

@Component
class RunnerScheduledTasks(
    private val runnerService: RunnerService,
) {
  fun cleanupArchivedRunners() {
    runnerService.cleanupArchived()
  }
}
