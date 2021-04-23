// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.solution

import com.azure.cosmos.models.CosmosContainerProperties
import com.cosmotech.api.AbstractCosmosBackedService
import com.cosmotech.api.events.OrganizationRegistered
import com.cosmotech.api.events.OrganizationUnregistered
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.solution.domain.Solution
import java.util.*
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["csm.platform.vendor"], havingValue = "azure", matchIfMissing = true)
class SolutionServiceImpl : AbstractCosmosBackedService(), SolutionApiService {

  override fun findAllSolutions(organizationId: String) =
      cosmosTemplate.findAll("${organizationId}_solutions", Solution::class.java).toList()

  override fun findSolutionById(organizationId: String, solutionId: String) =
      cosmosTemplate.findById("${organizationId}_solutions", solutionId, Solution::class.java)
          ?: throw IllegalArgumentException(
              "Solution $solutionId not found in organization $organizationId")

  override fun createSolution(organizationId: String, solution: Solution) =
      cosmosTemplate.insert(
          "${organizationId}_solutions", solution.copy(id = UUID.randomUUID().toString()))
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

  override fun upload(organizationId: String, body: org.springframework.core.io.Resource) =
      createSolution(organizationId, readYaml(body.inputStream))

  @EventListener(OrganizationRegistered::class)
  fun onOrganizationRegistered(organizationRegistered: OrganizationRegistered) {
    cosmosClient
        .getDatabase(databaseName)
        .createContainerIfNotExists(
            CosmosContainerProperties("${organizationRegistered.organizationId}_solutions", "/id"))
  }

  @EventListener(OrganizationUnregistered::class)
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
