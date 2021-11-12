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
import com.cosmotech.api.utils.compareToAndMutateIfNeeded
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.solution.domain.RunTemplate
import com.cosmotech.solution.domain.RunTemplateHandlerId
import com.cosmotech.solution.domain.RunTemplateParameter
import com.cosmotech.solution.domain.RunTemplateParameterGroup
import com.cosmotech.solution.domain.RunTemplateStepSource
import com.cosmotech.solution.domain.Solution
import org.apache.commons.compress.archivers.ArchiveException
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.event.EventListener
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["csm.platform.vendor"], havingValue = "azure", matchIfMissing = true)
@Suppress("TooManyFunctions")
internal class SolutionServiceImpl(
    private val resourceLoader: ResourceLoader,
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
              id = idGenerator.generate("solution", prependPrefix = "sol-"),
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

    // solutionId update is allowed but must be done with care. Maybe limit to minor update?
    var hasChanged =
        existingSolution
            .compareToAndMutateIfNeeded(solution, excludedFields = arrayOf("ownerId"))
            .isNotEmpty()

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
      hasChanged =
          hasChanged || existingRunTemplate.compareToAndMutateIfNeeded(runTemplate).isNotEmpty()
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
    val solution = this.validateRunTemplate(organizationId, solutionId, runTemplateId)
    logger.debug(
        "Uploading run template handler to solution #{} ({} - {})",
        solution.id,
        solution.name,
        solution.version)

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
      RunTemplateHandlerId.scenariodata_transform ->
          runTemplate.scenariodataTransformSource = RunTemplateStepSource.cloud
    }.run {
      // This trick forces Kotlin to raise an error at compile time if the "when" statement is not
      // exhaustive
    }

    cosmosTemplate.upsert("${organizationId}_solutions", solution)
  }

  override fun downloadRunTemplateHandler(
      organizationId: String,
      solutionId: String,
      runTemplateId: String,
      handlerId: RunTemplateHandlerId
  ): Resource {
    val solution = this.validateRunTemplate(organizationId, solutionId, runTemplateId)
    val blobPath =
        "${organizationId.sanitizeForAzureStorage()}/" +
            "${solutionId.sanitizeForAzureStorage()}/" +
            "${runTemplateId}/${handlerId.value}.zip"
    logger.debug(
        "Downloading run template handler resource for #{} ({}-{}) - {} - {}: {}",
        solution.id,
        solution.name,
        solution.version,
        runTemplateId,
        handlerId,
        blobPath)
    return resourceLoader.getResource("azure-blob://${blobPath}")
  }

  private fun validateRunTemplate(
      organizationId: String,
      solutionId: String,
      runTemplateId: String,
  ): Solution {
    val solution = findSolutionById(organizationId, solutionId)
    val validRunTemplateIds = solution.runTemplates.map { it.id }.toSet()
    if (validRunTemplateIds.isEmpty()) {
      throw IllegalArgumentException(
          "Solution $solutionId does not declare any run templates. " +
              "It is therefore not possible to upload run template handlers. " +
              "Either update the Solution or upload a new one with run templates.")
    }
    if (runTemplateId !in validRunTemplateIds) {
      throw IllegalArgumentException(
          "Invalid runTemplateId: [$runTemplateId]. Must be one of: $validRunTemplateIds")
    }

    return solution
  }
}
