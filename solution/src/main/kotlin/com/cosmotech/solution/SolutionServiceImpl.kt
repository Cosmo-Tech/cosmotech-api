// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.solution

import com.azure.cosmos.models.CosmosContainerProperties
import com.cosmotech.api.AbstractCosmosBackedService
import com.cosmotech.api.events.OrganizationRegistered
import com.cosmotech.api.events.OrganizationUnregistered
import com.cosmotech.api.utils.findAll
import com.cosmotech.api.utils.findByIdOrThrow
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.solution.domain.RunTemplate
import com.cosmotech.solution.domain.RunTemplateParameter
import com.cosmotech.solution.domain.RunTemplateParameterGroup
import com.cosmotech.solution.domain.Solution
import java.util.*
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["csm.platform.vendor"], havingValue = "azure", matchIfMissing = true)
class SolutionServiceImpl : AbstractCosmosBackedService(), SolutionApiService {

  override fun findAllSolutions(organizationId: String) =
      cosmosTemplate.findAll<Solution>("${organizationId}_solutions")

  override fun findSolutionById(organizationId: String, solutionId: String): Solution =
      cosmosTemplate.findByIdOrThrow(
          "${organizationId}_solutions",
          solutionId,
          "Solution $solutionId not found in organization $organizationId")

  override fun removeAllRunTemplates(organizationId: String, solutionId: String) {
    TODO("Not yet implemented")
  }

  override fun removeAllSolutionParameterGroups(organizationId: String, solutionId: String) {
    TODO("Not yet implemented")
  }

  override fun removeAllSolutionParameters(organizationId: String, solutionId: String) {
    TODO("Not yet implemented")
  }

  override fun addOrReplaceParameters(
      organizationId: String,
      solutionId: String,
      runTemplateParameter: List<RunTemplateParameter>
  ): List<RunTemplateParameter> {
    TODO("Not yet implemented")
  }

  override fun addOrReplaceRunTemplates(
      organizationId: String,
      solutionId: String,
      runTemplate: List<RunTemplate>
  ): List<RunTemplate> {
    TODO("Not yet implemented")
  }

  override fun addParameterGroups(
      organizationId: String,
      solutionId: String,
      runTemplateParameterGroup: List<RunTemplateParameterGroup>
  ): List<RunTemplateParameterGroup> {
    TODO("Not yet implemented")
  }

  override fun createSolution(organizationId: String, solution: Solution) =
      cosmosTemplate.insert(
          "${organizationId}_solutions",
          solution.copy(id = idGenerator.generate("solution", prependPrefix = "SOL-")))
          ?: throw IllegalArgumentException("No solution returned in response: $solution")

  override fun deleteSolution(organizationId: String, solutionId: String): Solution {
    val solution = findSolutionById(organizationId, solutionId)
    cosmosTemplate.deleteEntity("${organizationId}_solutions", solution)
    return solution
  }

  override fun updateSolution(
      organizationId: String,
      solutionId: String,
      solution: Solution
  ): Solution {
    TODO("Not yet implemented")
  }

  @EventListener(OrganizationRegistered::class)
  fun onOrganizationRegistered(organizationRegistered: OrganizationRegistered) {
    cosmosCoreDatabase.createContainerIfNotExists(
        CosmosContainerProperties("${organizationRegistered.organizationId}_solutions", "/id"))
  }

  @EventListener(OrganizationUnregistered::class)
  @Async("csm-in-process-event-executor")
  fun onOrganizationUnregistered(organizationUnregistered: OrganizationUnregistered) {
    cosmosTemplate.deleteContainer("${organizationUnregistered.organizationId}_solutions")
  }

  override fun uploadRunTemplateHandler(
      organizationId: kotlin.String,
      solutionId: kotlin.String,
      runTemplateId: kotlin.String,
      handlerId: kotlin.String,
      body: org.springframework.core.io.Resource?
  ): Unit {
    TODO("Not yet implemented")
  }
}
