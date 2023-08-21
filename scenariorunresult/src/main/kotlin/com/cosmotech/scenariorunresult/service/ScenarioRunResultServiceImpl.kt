// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorunresult.service

import com.cosmotech.api.exceptions.CsmClientException
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.rbac.CsmRbac
import com.cosmotech.api.rbac.PERMISSION_READ
import com.cosmotech.api.rbac.PERMISSION_WRITE
import com.cosmotech.api.rbac.getScenarioRolesDefinition
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.service.getRbac
import com.cosmotech.scenario.api.ScenarioApiService
import com.cosmotech.scenario.service.getRbac
import com.cosmotech.scenariorunresult.api.ScenariorunresultApiService
import com.cosmotech.scenariorunresult.domain.ScenarioRunResult
import com.cosmotech.scenariorunresult.repository.ScenarioRunResultRepository
import com.cosmotech.workspace.api.WorkspaceApiService
import com.cosmotech.workspace.service.getRbac
import org.springframework.stereotype.Service

@Service
class ScenarioRunResultServiceImpl(
    private val scenarioRunResultRepository: ScenarioRunResultRepository,
    private val organizationService: OrganizationApiService,
    private val workspaceService: WorkspaceApiService,
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
    val organization = organizationService.findOrganizationById(organizationId)
    csmRbac.verify(organization.getRbac(), PERMISSION_READ)
    val workspace = workspaceService.findWorkspaceById(organizationId, workspaceId)
    csmRbac.verify(workspace.getRbac(), PERMISSION_READ)
    val scenario = scenarioService.findScenarioById(organizationId, workspaceId, scenarioId)
    csmRbac.verify(scenario.getRbac(), PERMISSION_WRITE, scenarioPermissions)

    if (requestBody.isNotEmpty()) {
      val redisId = createResultId(scenariorunId, probeId)
      val scenarioRunResult =
          scenarioRunResultRepository.findById(redisId).orElse(createNewScenarioRunResult(redisId))
      scenarioRunResult.results?.add(requestBody.toMutableMap())
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
    // TODO ADD RBAC
    val redisId = createResultId(scenariorunId, probeId)
    return scenarioRunResultRepository.findById(redisId).orElseThrow {
      CsmResourceNotFoundException("no probe $redisId found")
    }
  }
  internal fun createNewScenarioRunResult(id: String): ScenarioRunResult {
    return ScenarioRunResult(id, mutableListOf())
  }

  internal fun createResultId(scenariorunId: String, probeId: String): String {
    return "${scenariorunId}_${probeId}"
  }
}
