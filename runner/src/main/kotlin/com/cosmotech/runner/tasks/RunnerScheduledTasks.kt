// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.runner.tasks

import com.cosmotech.runner.service.RunnerService
import java.util.concurrent.TimeUnit
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    name = ["csm.platform.tasks.cleanUpArchivedRunners.enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class RunnerScheduledTasks(
    private val runnerService: RunnerService,
) {

  @Scheduled(
      timeUnit = TimeUnit.SECONDS,
      fixedDelayString = "\${csm.platform.tasks.cleanUpArchivedRunners.delay}",
  )
  fun cleanupArchivedRunners() {
    runnerService.cleanupArchived()
  }
}
