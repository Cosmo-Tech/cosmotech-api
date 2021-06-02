// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.solution.azure

import com.azure.cosmos.models.CosmosContainerProperties
import com.azure.storage.blob.BlobServiceClient
import com.cosmotech.api.azure.AbstractCosmosBackedService
import com.cosmotech.api.azure.findAll
import com.cosmotech.api.azure.findByIdOrThrow
import com.cosmotech.api.azure.sanitizeForAzureStorage
import com.cosmotech.api.events.OrganizationRegistered
import com.cosmotech.api.events.OrganizationUnregistered
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.utils.changed
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.solution.domain.*
import org.apache.commons.compress.archivers.ArchiveException
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.event.EventListener
import org.springframework.core.io.Resource
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["csm.platform.vendor"], havingValue = "azure", matchIfMissing = true)
class SolutionServiceImpl(
    private val azureStorageBlobServiceClient: BlobServiceClient,
) : AbstractCosmosBackedService(), SolutionApiService {

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
          solution.copy(
              id = idGenerator.generate("solution", prependPrefix = "SOL-"),
              ownerId = getCurrentAuthenticatedUserName()))
          ?: throw IllegalArgumentException("No solution returned in response: $solution")

  override fun deleteSolution(organizationId: String, solutionId: String) {
    val solution = findSolutionById(organizationId, solutionId)
    if (solution.ownerId != getCurrentAuthenticatedUserName()) {
      // TODO Only the owner or an admin should be able to perform this operation
      throw CsmAccessForbiddenException("You are not allowed to delete this Resource")
    }
    cosmosTemplate.deleteEntity("${organizationId}_solutions", solution)
  }

  override fun deleteSolutionRunTemplate(
      organizationId: String,
      solutionId: String,
      runTemplateId: String
  ) {
    val existingSolution = findSolutionById(organizationId, solutionId)
    val runTemplatesMutableList = existingSolution.runTemplates.toMutableList()
    if (!runTemplatesMutableList.removeIf { it.id == runTemplateId }) {
      throw CsmResourceNotFoundException("Run Template '$runTemplateId' *not* found")
    }
    existingSolution.runTemplates = runTemplatesMutableList.toList()
    cosmosTemplate.upsert("${organizationId}_solutions", existingSolution)
  }

  override fun updateSolution(
      organizationId: String,
      solutionId: String,
      solution: Solution
  ): Solution {
    val existingSolution = findSolutionById(organizationId, solutionId)

    var hasChanged = false
    if (solution.ownerId != null && solution.changed(existingSolution) { ownerId }) {
      // Allow to change the ownerId as well, but only the owner can transfer the ownership
      if (existingSolution.ownerId != getCurrentAuthenticatedUserName()) {
        // TODO Only the owner or an admin should be able to perform this operation
        throw CsmAccessForbiddenException(
            "You are not allowed to change the ownership of this Resource")
      }
      existingSolution.ownerId = solution.ownerId
      hasChanged = true
    }
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
    // Solution update must be done with care. Maybe limit to minor update?
    if (solution.version != null && solution.changed(existingSolution) { version }) {
      existingSolution.version = solution.version
      hasChanged = true
    }

    if (solution.url != null && solution.changed(existingSolution) { url }) {
      existingSolution.url = solution.url
      hasChanged = true
    }

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

  override fun updateSolutionRunTemplate(
      organizationId: String,
      solutionId: String,
      runTemplateId: String,
      runTemplate: RunTemplate
  ): List<RunTemplate> {
    val existingSolution = findSolutionById(organizationId, solutionId)
    val runTemplates = existingSolution.runTemplates.filter { it.id == runTemplateId }
    if (runTemplates.isEmpty()) {
      throw CsmResourceNotFoundException("Run Template '$runTemplateId' *not* found")
    }
    var hasChanged = false
    for (existingRunTemplate in runTemplates) {
      if (runTemplate.name != null && runTemplate.changed(existingRunTemplate) { name }) {
        existingRunTemplate.name = runTemplate.name
        hasChanged = true
      }
      if (runTemplate.description != null &&
          runTemplate.changed(existingRunTemplate) { description }) {
        existingRunTemplate.description = runTemplate.description
        hasChanged = true
      }
      if (runTemplate.tags != null &&
          runTemplate.tags?.toSet() != existingRunTemplate.tags?.toSet()) {
        existingRunTemplate.tags = runTemplate.tags
        hasChanged = true
      }
      if (runTemplate.csmSimulation != null &&
          runTemplate.changed(existingRunTemplate) { csmSimulation }) {
        existingRunTemplate.csmSimulation = runTemplate.csmSimulation
        hasChanged = true
      }
      if (runTemplate.computeSize != null &&
          runTemplate.changed(existingRunTemplate) { computeSize }) {
        existingRunTemplate.computeSize = runTemplate.computeSize
        hasChanged = true
      }
      if (runTemplate.fetchDatasets != null &&
          runTemplate.changed(existingRunTemplate) { fetchDatasets }) {
        existingRunTemplate.fetchDatasets = runTemplate.fetchDatasets
        hasChanged = true
      }
      if (runTemplate.fetchScenarioParameters != null &&
          runTemplate.changed(existingRunTemplate) { fetchScenarioParameters }) {
        existingRunTemplate.fetchScenarioParameters = runTemplate.fetchScenarioParameters
        hasChanged = true
      }
      if (runTemplate.applyParameters != null &&
          runTemplate.changed(existingRunTemplate) { applyParameters }) {
        existingRunTemplate.applyParameters = runTemplate.applyParameters
        hasChanged = true
      }
      if (runTemplate.validateData != null &&
          runTemplate.changed(existingRunTemplate) { validateData }) {
        existingRunTemplate.validateData = runTemplate.validateData
        hasChanged = true
      }
      if (runTemplate.sendDatasetsToDataWarehouse != null &&
          runTemplate.changed(existingRunTemplate) { sendDatasetsToDataWarehouse }) {
        existingRunTemplate.sendDatasetsToDataWarehouse = runTemplate.sendDatasetsToDataWarehouse
        hasChanged = true
      }
      if (runTemplate.sendInputParametersToDataWarehouse != null &&
          runTemplate.changed(existingRunTemplate) { sendInputParametersToDataWarehouse }) {
        existingRunTemplate.sendInputParametersToDataWarehouse =
            runTemplate.sendInputParametersToDataWarehouse
        hasChanged = true
      }
      if (runTemplate.preRun != null && runTemplate.changed(existingRunTemplate) { preRun }) {
        existingRunTemplate.preRun = runTemplate.preRun
        hasChanged = true
      }
      if (runTemplate.run != null && runTemplate.changed(existingRunTemplate) { run }) {
        existingRunTemplate.run = runTemplate.run
        hasChanged = true
      }
      if (runTemplate.postRun != null && runTemplate.changed(existingRunTemplate) { postRun }) {
        existingRunTemplate.postRun = runTemplate.postRun
        hasChanged = true
      }
      if (runTemplate.parametersHandlerSource != null &&
          runTemplate.changed(existingRunTemplate) { parametersHandlerSource }) {
        existingRunTemplate.parametersHandlerSource = runTemplate.parametersHandlerSource
        hasChanged = true
      }
      if (runTemplate.datasetValidatorSource != null &&
          runTemplate.changed(existingRunTemplate) { datasetValidatorSource }) {
        existingRunTemplate.datasetValidatorSource = runTemplate.datasetValidatorSource
        hasChanged = true
      }
      if (runTemplate.preRunSource != null &&
          runTemplate.changed(existingRunTemplate) { preRunSource }) {
        existingRunTemplate.preRunSource = runTemplate.preRunSource
        hasChanged = true
      }
      if (runTemplate.runSource != null && runTemplate.changed(existingRunTemplate) { runSource }) {
        existingRunTemplate.runSource = runTemplate.runSource
        hasChanged = true
      }
      if (runTemplate.postRunSource != null && runTemplate.changed(existingRunTemplate) { name }) {
        existingRunTemplate.name = runTemplate.name
        hasChanged = true
      }
      if (runTemplate.parameterGroups != null &&
          runTemplate.parameterGroups?.toSet() != existingRunTemplate.parameterGroups?.toSet()) {
        existingRunTemplate.parameterGroups = runTemplate.parameterGroups
        hasChanged = true
      }
    }

    if (hasChanged) {
      existingSolution.runTemplates = runTemplates
      cosmosTemplate.upsert("${organizationId}_solutions", existingSolution)
    }
    return runTemplates
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
      handlerId: RunTemplateHandlerId,
      body: Resource,
      overwrite: Boolean
  ) {
    val solution = findSolutionById(organizationId, solutionId)
    logger.debug(
        "Uploading run template handler to solution #{} ({} - {})",
        solution.id,
        solution.name,
        solution.version)

    val validRunTemplateIds = solution.runTemplates.map { it.id }.toSet()
    if (validRunTemplateIds.isEmpty()) {
      throw IllegalArgumentException(
          "Solution $solutionId does not declare any run templates. " +
              "It is therefore not possible to upload run template handlers. " +
              "Either update the Solution or upload a new one with run templates.")
    }
    if (!validRunTemplateIds.contains(runTemplateId)) {
      throw IllegalArgumentException(
          "Invalid runTemplateId: [$runTemplateId]. Must be one of: $validRunTemplateIds")
    }

    // Security checks
    // TODO Security-wise, we should also check the content of the archive for potential attack
    // vectors, like upload of malicious files, zip bombing, path traversal attacks, ...
    try {
      val archiverType = ArchiveStreamFactory.detect(body.inputStream.buffered())
      if (ArchiveStreamFactory.ZIP != archiverType) {
        throw IllegalArgumentException(
            "Invalid archive type: '$archiverType'. A Zip Archive is expected.")
      }
    } catch (ae: ArchiveException) {
      throw IllegalArgumentException("A Zip Archive is expected.", ae)
    }

    azureStorageBlobServiceClient
        .getBlobContainerClient(organizationId.sanitizeForAzureStorage())
        .getBlobClient(
            "${solutionId.sanitizeForAzureStorage()}/$runTemplateId/${handlerId.value}.zip")
        .upload(body.inputStream, body.contentLength(), overwrite)

    val runTemplate = solution.runTemplates.findLast { it.id == runTemplateId }!!
    when (handlerId) {
      RunTemplateHandlerId.parameters_handler ->
          runTemplate.parametersHandlerSource = RunTemplateStepSource.cloud
      RunTemplateHandlerId.validator ->
          runTemplate.datasetValidatorSource = RunTemplateStepSource.cloud
      RunTemplateHandlerId.prerun -> runTemplate.preRunSource = RunTemplateStepSource.cloud
      RunTemplateHandlerId.engine -> runTemplate.runSource = RunTemplateStepSource.cloud
      RunTemplateHandlerId.postrun -> runTemplate.postRunSource = RunTemplateStepSource.cloud
    }
    cosmosTemplate.upsert("${organizationId}_solutions", solution)
  }
}
