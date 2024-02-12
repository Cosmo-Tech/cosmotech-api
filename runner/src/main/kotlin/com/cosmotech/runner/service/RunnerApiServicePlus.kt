// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.runner.service

import com.cosmotech.runner.api.RunnerApiService

interface RunnerApiServicePlus : RunnerApiService {
  fun getRunnerService(): RunnerService
}
