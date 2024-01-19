// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorunresult.service

import com.cosmotech.api.exceptions.CsmClientException
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.rbac.CsmRbac
import com.cosmotech.api.rbac.PERMISSION_READ
import com.cosmotech.api.rbac.PERMISSION_WRITE
import com.cosmotech.api.rbac.getScenarioRolesDefinition
import com.cosmotech.scenario.api.ScenarioApiService
import com.cosmotech.scenario.service.getRbac
import com.cosmotech.scenariorunresult.api.ScenariorunresultApiService
import com.cosmotech.scenariorunresult.domain.ScenarioRunResult
import com.cosmotech.scenariorunresult.repository.ScenarioRunResultRepository
import org.springframework.stereotype.Service

@Service
class ScenarioRunResultServiceImpl(
    private val scenarioRunResultRepository: ScenarioRunResultRepository,
    private val scenarioService: ScenarioApiService,
    private val csmRbac: CsmRbac,
) : ScenariorunresultApiService {

  val scenarioPermissions = getScenarioRolesDefinition()
  override fun sendScenarioRunResult(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      scenariorunId: String,
      probeId: String,
      requestBody: Map<String, String>
  ): ScenarioRunResult {
    val scenario = scenarioService.findScenarioById(organizationId, workspaceId, scenarioId)
    csmRbac.verify(scenario.getRbac(), PERMISSION_WRITE, scenarioPermissions)

    if (requestBody.isNotEmpty()) {
      val resultId = createResultId(scenariorunId, probeId)
      val scenarioRunResult =
          scenarioRunResultRepository
              .findById(resultId)
              .orElse(ScenarioRunResult(resultId, mutableListOf()))
      scenarioRunResult.results!!.add(requestBody.toMutableMap())
      return scenarioRunResultRepository.save(scenarioRunResult)
    } else {
      throw CsmClientException("no data sent")
    }
  }

  override fun getScenarioRunResult(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      scenariorunId: String,
      probeId: String
  ): ScenarioRunResult {
    val scenario = scenarioService.findScenarioById(organizationId, workspaceId, scenarioId)
    csmRbac.verify(scenario.getRbac(), PERMISSION_READ, scenarioPermissions)

    val resultId = createResultId(scenariorunId, probeId)
    return scenarioRunResultRepository.findById(resultId).orElseThrow {
      CsmResourceNotFoundException("no probe $resultId found")
    }
  }

  internal fun createResultId(scenariorunId: String, probeId: String): String {
    return "${scenariorunId}_${probeId}"
  }
}
