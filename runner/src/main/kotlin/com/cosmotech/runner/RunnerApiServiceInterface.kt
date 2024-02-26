// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.runner

import com.cosmotech.runner.api.RunnerApiService
import com.cosmotech.runner.service.RunnerService

interface RunnerApiServiceInterface : RunnerApiService {
  fun getRunnerService(): RunnerService
}
