// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.runner.tasks

import com.cosmotech.runner.service.RunnerService
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val logger = LoggerFactory.getLogger(RunnerScheduledTasks::class.java)

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
    logger.debug("Scheduled task: Cleaning up archived runners")
    runnerService.cleanupArchived()
  }
}
