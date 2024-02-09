// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun

import com.cosmotech.api.rbac.PERMISSION_READ
import com.cosmotech.scenariorun.api.ScenariorunApiService
import com.cosmotech.scenariorun.domain.ScenarioRun

interface ScenarioRunApiServiceInterface : ScenariorunApiService {
  fun getVerifiedScenarioRun(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      requiredPermission: String = PERMISSION_READ
  ): ScenarioRun
}
