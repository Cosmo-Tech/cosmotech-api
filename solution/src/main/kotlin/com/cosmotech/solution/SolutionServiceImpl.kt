// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.solution

import com.azure.cosmos.models.CosmosContainerProperties
import com.cosmotech.api.AbstractCosmosBackedService
import com.cosmotech.api.events.OrganizationRegistered
import com.cosmotech.api.events.OrganizationUnregistered
import com.cosmotech.api.utils.changed
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
    val solution = findSolutionById(organizationId, solutionId)
    if (!solution.runTemplates.isNullOrEmpty()) {
      solution.runTemplates = listOf()
      cosmosTemplate.upsert("${organizationId}_solutions", solution)
    }
  }

  override fun removeAllSolutionParameterGroups(organizationId: String, solutionId: String) {
    val solution = findSolutionById(organizationId, solutionId)
    if (!solution.parameterGroups.isNullOrEmpty()) {
      solution.parameterGroups = listOf()
      cosmosTemplate.upsert("${organizationId}_solutions", solution)
    }
  }

  override fun removeAllSolutionParameters(organizationId: String, solutionId: String) {
    val solution = findSolutionById(organizationId, solutionId)
    if (!solution.parameters.isNullOrEmpty()) {
      solution.parameters = listOf()
      cosmosTemplate.upsert("${organizationId}_solutions", solution)
    }
  }

  override fun addOrReplaceParameterGroups(
      organizationId: String,
      solutionId: String,
      runTemplateParameterGroup: List<RunTemplateParameterGroup>
  ): List<RunTemplateParameterGroup> {
    if (runTemplateParameterGroup.isEmpty()) {
      return runTemplateParameterGroup
    }

    val existingSolution = findSolutionById(organizationId, solutionId)
    val runTemplateParameterGroupMap =
        existingSolution.parameterGroups?.associateBy { it.id }?.toMutableMap() ?: mutableMapOf()
    runTemplateParameterGroupMap.putAll(
        runTemplateParameterGroup.filter { it.id.isNotBlank() }.associateBy { it.id })
    existingSolution.parameterGroups = runTemplateParameterGroupMap.values.toList()
    cosmosTemplate.upsert("${organizationId}_solutions", existingSolution)

    return runTemplateParameterGroup
  }

  override fun addOrReplaceParameters(
      organizationId: String,
      solutionId: String,
      runTemplateParameter: List<RunTemplateParameter>
  ): List<RunTemplateParameter> {
    if (runTemplateParameter.isEmpty()) {
      return runTemplateParameter
    }

    val existingSolution = findSolutionById(organizationId, solutionId)
    val runTemplateParameterMap =
        existingSolution.parameters?.associateBy { it.id }?.toMutableMap() ?: mutableMapOf()
    runTemplateParameterMap.putAll(
        runTemplateParameter.filter { it.id.isNotBlank() }.associateBy { it.id })
    existingSolution.parameters = runTemplateParameterMap.values.toList()
    cosmosTemplate.upsert("${organizationId}_solutions", existingSolution)

    return runTemplateParameter
  }

  override fun addOrReplaceRunTemplates(
      organizationId: String,
      solutionId: String,
      runTemplate: List<RunTemplate>
  ): List<RunTemplate> {
    if (runTemplate.isEmpty()) {
      return runTemplate
    }

    val existingSolution = findSolutionById(organizationId, solutionId)
    val runTemplateMap =
        existingSolution.runTemplates?.associateBy { it.id }?.toMutableMap() ?: mutableMapOf()
    runTemplateMap.putAll(runTemplate.filter { it.id.isNotBlank() }.associateBy { it.id })
    existingSolution.runTemplates = runTemplateMap.values.toList()
    cosmosTemplate.upsert("${organizationId}_solutions", existingSolution)

    return runTemplate
  }

  override fun createSolution(organizationId: String, solution: Solution) =
      cosmosTemplate.insert(
          "${organizationId}_solutions",
          solution.copy(id = idGenerator.generate("solution", prependPrefix = "SOL-")))
          ?: throw IllegalArgumentException("No solution returned in response: $solution")

  override fun deleteSolution(organizationId: String, solutionId: String) {
    cosmosTemplate.deleteEntity(
        "${organizationId}_solutions", findSolutionById(organizationId, solutionId))
  }

  override fun updateSolution(
      organizationId: String,
      solutionId: String,
      solution: Solution
  ): Solution {
    val existingSolution = findSolutionById(organizationId, solutionId)

    var hasChanged = false
    if (solution.key != null && solution.changed(existingSolution) { key }) {
      existingSolution.key = solution.key
      hasChanged = true
    }
    if (solution.name != null && solution.changed(existingSolution) { name }) {
      existingSolution.name = solution.name
      hasChanged = true
    }
    if (solution.description != null && solution.changed(existingSolution) { description }) {
      existingSolution.description = solution.description
      hasChanged = true
    }
    if (solution.repository != null && solution.changed(existingSolution) { repository }) {
      existingSolution.repository = solution.repository
      hasChanged = true
    }
    // Version is not purposely not overridable

    if (solution.url != null && solution.changed(existingSolution) { url }) {
      existingSolution.url = solution.url
      hasChanged = true
    }

    // TODO Allow to change the ownerId as well, but only the owner can transfer the ownership

    if (solution.tags != null && solution.tags?.toSet() != existingSolution.tags?.toSet()) {
      existingSolution.tags = solution.tags
      hasChanged = true
    }

    if (solution.parameters != null) {
      existingSolution.parameters = solution.parameters
      hasChanged = true
    }

    if (solution.parameterGroups != null) {
      existingSolution.parameterGroups = solution.parameterGroups
      hasChanged = true
    }

    if (solution.runTemplates != null) {
      existingSolution.runTemplates = solution.runTemplates
      hasChanged = true
    }

    return if (hasChanged) {
      cosmosTemplate.upsertAndReturnEntity("${organizationId}_solutions", existingSolution)
    } else {
      existingSolution
    }
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
      organizationId: String,
      solutionId: String,
      runTemplateId: String,
      handlerId: String,
      body: org.springframework.core.io.Resource?
  ): Unit {
    TODO("Not yet implemented")
  }
}
