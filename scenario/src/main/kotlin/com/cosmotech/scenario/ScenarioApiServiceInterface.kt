// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenario

import com.cosmotech.api.rbac.PERMISSION_READ
import com.cosmotech.scenario.api.ScenarioApiService
import com.cosmotech.scenario.domain.Scenario

interface ScenarioApiServiceInterface : ScenarioApiService {
  fun getVerifiedScenario(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      requiredPermission: String = PERMISSION_READ
  ): Scenario

  fun findScenarioById(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      withState: Boolean = false
  ): Scenario

  fun findScenarioChildrenById(
      organizationId: String,
      workspaceId: String,
      parentId: String
  ): List<Scenario>
}
