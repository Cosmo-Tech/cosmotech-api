// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.runner.service

import org.springframework.beans.factory.annotation.Lookup
import org.springframework.stereotype.Component

@Component
abstract class RunnerServiceManager {

  @Lookup abstract fun createRunnerService(): RunnerService

  fun getRunnerService(): RunnerService {
    return createRunnerService()
  }

  companion object {
    fun getRunnerService(): RunnerService {
      return this.getRunnerService()
    }
  }
}
