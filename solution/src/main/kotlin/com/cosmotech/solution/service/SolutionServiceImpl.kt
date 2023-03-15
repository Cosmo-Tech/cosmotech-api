// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.solution.service

import com.cosmotech.api.CsmPhoenixService
import com.cosmotech.api.events.OrganizationUnregistered
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.utils.ResourceScanner
import com.cosmotech.api.utils.changed
import com.cosmotech.api.utils.compareToAndMutateIfNeeded
import com.cosmotech.api.utils.constructPageRequest
import com.cosmotech.api.utils.findAllPaginated
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.api.utils.sanitizeForCloudStorage
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.solution.domain.RunTemplate
import com.cosmotech.solution.domain.RunTemplateHandlerId
import com.cosmotech.solution.domain.RunTemplateParameter
import com.cosmotech.solution.domain.RunTemplateParameterGroup
import com.cosmotech.solution.domain.Solution
import com.cosmotech.solution.repository.SolutionRepository
import org.apache.commons.compress.archivers.ArchiveException
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.springframework.context.event.EventListener
import org.springframework.core.io.Resource
import org.springframework.data.domain.Pageable
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
@Suppress("TooManyFunctions")
internal class SolutionServiceImpl(
    private val resourceScanner: ResourceScanner,
    private val solutionRepository: SolutionRepository,
) : CsmPhoenixService(), SolutionApiService {

  override fun findAllSolutions(organizationId: String, page: Int?, size: Int?): List<Solution> {
    val defaultPageSize = csmPlatformProperties.twincache.solution.defaultPageSize
    var pageable = constructPageRequest(page, size, defaultPageSize)
    if (pageable != null) {
      return solutionRepository.findByOrganizationId(organizationId, pageable).toList()
    }
    return findAllPaginated(defaultPageSize) {
      solutionRepository.findByOrganizationId(organizationId, it).toList()
    }
  }

  override fun findSolutionById(organizationId: String, solutionId: String): Solution =
      solutionRepository.findById(solutionId).orElseThrow {
        CsmResourceNotFoundException(
            "Solution $solutionId not found in organization $organizationId")
      }

  override fun removeAllRunTemplates(organizationId: String, solutionId: String) {
    val solution = findSolutionById(organizationId, solutionId)
    if (!solution.runTemplates.isNullOrEmpty()) {
      solution.runTemplates = mutableListOf()
      solutionRepository.save(solution)
    }
  }

  override fun removeAllSolutionParameterGroups(organizationId: String, solutionId: String) {
    val solution = findSolutionById(organizationId, solutionId)
    if (!solution.parameterGroups.isNullOrEmpty()) {
      solution.parameterGroups = mutableListOf()
      solutionRepository.save(solution)
    }
  }

  override fun removeAllSolutionParameters(organizationId: String, solutionId: String) {
    val solution = findSolutionById(organizationId, solutionId)
    if (!solution.parameters.isNullOrEmpty()) {
      solution.parameters = mutableListOf()
      solutionRepository.save(solution)
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
    existingSolution.parameterGroups = runTemplateParameterGroupMap.values.toMutableList()
    solutionRepository.save(existingSolution)

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
    existingSolution.parameters = runTemplateParameterMap.values.toMutableList()
    solutionRepository.save(existingSolution)

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
    existingSolution.runTemplates = runTemplateMap.values.toMutableList()
    solutionRepository.save(existingSolution)

    return runTemplate
  }

  override fun createSolution(organizationId: String, solution: Solution) =
      solutionRepository.save(
          solution.copy(
              id = idGenerator.generate("solution", prependPrefix = "sol-"),
              organizationId = organizationId,
              ownerId = getCurrentAuthenticatedUserName()))

  override fun deleteSolution(organizationId: String, solutionId: String) {
    val solution = findSolutionById(organizationId, solutionId)
    if (solution.ownerId != getCurrentAuthenticatedUserName()) {
      // TODO Only the owner or an admin should be able to perform this operation
      throw CsmAccessForbiddenException("You are not allowed to delete this Resource")
    }
    solutionRepository.delete(solution)
  }

  override fun deleteSolutionRunTemplate(
      organizationId: String,
      solutionId: String,
      runTemplateId: String
  ) {
    val existingSolution = findSolutionById(organizationId, solutionId)
    if (existingSolution.runTemplates?.removeIf { it.id == runTemplateId } == false) {
      throw CsmResourceNotFoundException("Run Template '$runTemplateId' *not* found")
    }
    solutionRepository.save(existingSolution)
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
      solutionRepository.save(existingSolution)
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
    val runTemplates =
        existingSolution.runTemplates?.filter { it.id == runTemplateId }?.toMutableList()
    if (runTemplates == null || runTemplates.isEmpty()) {
      throw CsmResourceNotFoundException("Run Template '$runTemplateId' *not* found")
    }
    var hasChanged = false
    for (existingRunTemplate in runTemplates) {
      hasChanged =
          hasChanged || existingRunTemplate.compareToAndMutateIfNeeded(runTemplate).isNotEmpty()
    }

    if (hasChanged) {
      existingSolution.runTemplates = runTemplates
      solutionRepository.save(existingSolution)
    }
    return runTemplates
  }

  @EventListener(OrganizationUnregistered::class)
  @Async("csm-in-process-event-executor")
  fun onOrganizationUnregistered(organizationUnregistered: OrganizationUnregistered) {
    val pageable: Pageable =
        Pageable.ofSize(csmPlatformProperties.twincache.solution.defaultPageSize)
    val solutions =
        solutionRepository
            .findByOrganizationId(organizationUnregistered.organizationId, pageable)
            .toList()
    solutionRepository.deleteAll(solutions)
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

    try {
      val archiverType = ArchiveStreamFactory.detect(body.inputStream.buffered())
      if (ArchiveStreamFactory.ZIP != archiverType) {
        throw IllegalArgumentException(
            "Invalid archive type: '$archiverType'. A Zip Archive is expected.")
      }
    } catch (ae: ArchiveException) {
      throw IllegalArgumentException("A Zip Archive is expected.", ae)
    }

    resourceScanner.scanMimeTypes(body, csmPlatformProperties.upload.authorizedMimeTypes.handlers)

    throw NotImplementedError("Not implemented yet")
  }

  override fun downloadRunTemplateHandler(
      organizationId: String,
      solutionId: String,
      runTemplateId: String,
      handlerId: RunTemplateHandlerId
  ): Resource {
    val solution = this.validateRunTemplate(organizationId, solutionId, runTemplateId)
    val blobPath =
        "${organizationId.sanitizeForCloudStorage()}/" +
            "${solutionId.sanitizeForCloudStorage()}/" +
            "${runTemplateId}/${handlerId.value}.zip"
    logger.debug(
        "Downloading run template handler resource for #{} ({}-{}) - {} - {}: {}",
        solution.id,
        solution.name,
        solution.version,
        runTemplateId,
        handlerId,
        blobPath)
    throw NotImplementedError("Not implemented yet")
  }

  private fun validateRunTemplate(
      organizationId: String,
      solutionId: String,
      runTemplateId: String,
  ): Solution {
    val solution = findSolutionById(organizationId, solutionId)
    val validRunTemplateIds = solution.runTemplates?.map { it.id }?.toSet()
    if (validRunTemplateIds == null || validRunTemplateIds.isEmpty()) {
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

  override fun importSolution(organizationId: String, solution: Solution): Solution {
    if (solution.id == null) {
      throw CsmResourceNotFoundException("Solution id is null")
    }
    return solutionRepository.save(solution)
  }
}
