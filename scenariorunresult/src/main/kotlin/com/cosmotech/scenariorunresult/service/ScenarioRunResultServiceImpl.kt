// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorunresult.service

import com.cosmotech.scenariorunresult.api.ScenariorunresultApiService
import com.cosmotech.scenariorunresult.domain.ScenarioRunResult
import com.cosmotech.scenariorunresult.repository.ScenarioRunResultRepository
import org.springframework.stereotype.Service

@Service
class ScenarioRunResultServiceImpl(
    private val scenarioRunResultRepository: ScenarioRunResultRepository
) : ScenariorunresultApiService {
  override fun createScenarioRunResult(
      organizationId: String,
      workspaceId: String,
      scenariorunId: String,
      probeId: String,
      scenarioRunResult: ScenarioRunResult
  ): ScenarioRunResult {
    scenarioRunResult.id = "${scenariorunId}_${probeId}"
    return scenarioRunResultRepository.save(scenarioRunResult)
  }

  override fun getScenarioRunResult(
      organizationId: String,
      workspaceId: String,
      scenariorunId: String,
      probeId: String
  ): ScenarioRunResult {
    return scenarioRunResultRepository.findById("${scenariorunId}_${probeId}").get()
  }
}
