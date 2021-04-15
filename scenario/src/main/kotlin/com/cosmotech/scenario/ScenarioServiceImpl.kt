// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenario

import com.cosmotech.api.AbstractPhoenixService
import com.cosmotech.scenario.api.ScenarioApiService
import com.cosmotech.scenario.domain.Scenario
import com.cosmotech.scenario.domain.ScenarioBase
import com.cosmotech.scenario.domain.ScenarioComparisonResult
import org.springframework.stereotype.Service

@Service
class ScenarioServiceImpl : AbstractPhoenixService(), ScenarioApiService {
  override fun compareScenarios(
      organizationId: kotlin.String,
      workspaceId: kotlin.String,
      scenarioId: kotlin.String,
      comparedScenarioId: kotlin.String
  ): ScenarioComparisonResult {
    TODO("Not yet implemented")
  }

  override fun createScenario(
      organizationId: kotlin.String,
      workspaceId: kotlin.String,
      scenario: Scenario
  ): Scenario {
    TODO("Not yet implemented")
  }

  override fun deleteScenario(
      organizationId: kotlin.String,
      workspaceId: kotlin.String,
      scenarioId: kotlin.String
  ): Scenario {
    TODO("Not yet implemented")
  }

  override fun findAllScenarios(
      organizationId: kotlin.String,
      workspaceId: kotlin.String
  ): List<Scenario> {
    TODO("Not yet implemented")
  }

  override fun findScenarioById(
      organizationId: kotlin.String,
      workspaceId: kotlin.String,
      scenarioId: kotlin.String
  ): Scenario {
    TODO("Not yet implemented")
  }

  override fun getScenariosTree(
      organizationId: kotlin.String,
      workspaceId: kotlin.String
  ): List<ScenarioBase> {
    TODO("Not yet implemented")
  }

  override fun updateScenario(
      organizationId: kotlin.String,
      workspaceId: kotlin.String,
      scenarioId: kotlin.String,
      scenario: Scenario
  ): Scenario {
    TODO("Not yet implemented")
  }
}
