// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorunresults

import com.cosmotech.scenariorunresults.api.ScenariorunresultsApiService
import org.springframework.stereotype.Service

@Service
class ScenarioRunResultsServiceImpl : ScenariorunresultsApiService {
  override fun createScenarioRunResults(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      scenariorunId: String,
      scenariorunresultsId: String
  ) {
    TODO("Not yet implemented")
  }

  override fun getScenarioRunResults(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      scenariorunId: String,
      scenariorunresultsId: String
  ) {
    TODO("Not yet implemented")
  }
}
