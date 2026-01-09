// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.solution.service

import com.cosmotech.common.CsmPhoenixService
import com.cosmotech.common.containerregistry.ContainerRegistryService
import com.cosmotech.common.events.OrganizationUnregistered
import com.cosmotech.common.exceptions.CsmResourceNotFoundException
import com.cosmotech.common.id.generateId
import com.cosmotech.common.rbac.CsmAdmin
import com.cosmotech.common.rbac.CsmRbac
import com.cosmotech.common.rbac.PERMISSION_CREATE_CHILDREN
import com.cosmotech.common.rbac.PERMISSION_DELETE
import com.cosmotech.common.rbac.PERMISSION_READ_SECURITY
import com.cosmotech.common.rbac.PERMISSION_WRITE
import com.cosmotech.common.rbac.PERMISSION_WRITE_SECURITY
import com.cosmotech.common.rbac.ROLE_NONE
import com.cosmotech.common.rbac.model.RbacAccessControl
import com.cosmotech.common.rbac.model.RbacSecurity
import com.cosmotech.common.utils.compareToAndMutateIfNeeded
import com.cosmotech.common.utils.constructPageRequest
import com.cosmotech.common.utils.findAllPaginated
import com.cosmotech.common.utils.getCurrentAccountIdentifier
import com.cosmotech.common.utils.validateResourceSizing
import com.cosmotech.organization.OrganizationApiServiceInterface
import com.cosmotech.organization.service.toGenericSecurity
import com.cosmotech.solution.SolutionApiServiceInterface
import com.cosmotech.solution.domain.RunTemplate
import com.cosmotech.solution.domain.RunTemplateCreateRequest
import com.cosmotech.solution.domain.RunTemplateParameter
import com.cosmotech.solution.domain.RunTemplateParameterCreateRequest
import com.cosmotech.solution.domain.RunTemplateParameterGroup
import com.cosmotech.solution.domain.RunTemplateParameterGroupCreateRequest
import com.cosmotech.solution.domain.RunTemplateParameterGroupUpdateRequest
import com.cosmotech.solution.domain.RunTemplateParameterUpdateRequest
import com.cosmotech.solution.domain.RunTemplateUpdateRequest
import com.cosmotech.solution.domain.Solution
import com.cosmotech.solution.domain.SolutionAccessControl
import com.cosmotech.solution.domain.SolutionCreateRequest
import com.cosmotech.solution.domain.SolutionEditInfo
import com.cosmotech.solution.domain.SolutionRole
import com.cosmotech.solution.domain.SolutionSecurity
import com.cosmotech.solution.domain.SolutionUpdateRequest
import com.cosmotech.solution.repository.SolutionRepository
import java.time.Instant
import kotlin.collections.mutableListOf
import org.springframework.context.event.EventListener
import org.springframework.data.domain.Pageable
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
@Suppress("TooManyFunctions", "LargeClass")
class SolutionServiceImpl(
    private val solutionRepository: SolutionRepository,
    private val organizationApiService: OrganizationApiServiceInterface,
    private val csmRbac: CsmRbac,
    private val csmAdmin: CsmAdmin,
    private val containerRegistryService: ContainerRegistryService,
) : CsmPhoenixService(), SolutionApiServiceInterface {

  override fun listSolutions(organizationId: String, page: Int?, size: Int?): List<Solution> {
    organizationApiService.getVerifiedOrganization(organizationId)

    val defaultPageSize = csmPlatformProperties.databases.resources.solution.defaultPageSize
    val pageable = constructPageRequest(page, size, defaultPageSize)
    val isAdmin = csmAdmin.verifyCurrentRolesAdmin()
    val result: MutableList<Solution>

    val rbacEnabled = !isAdmin && this.csmPlatformProperties.rbac.enabled

    if (pageable == null) {
      result =
          findAllPaginated(defaultPageSize) {
            if (rbacEnabled) {
              val currentUser = getCurrentAccountIdentifier(this.csmPlatformProperties)
              solutionRepository
                  .findByOrganizationIdAndSecurity(organizationId, currentUser, it)
                  .toList()
            } else {
              solutionRepository.findByOrganizationId(organizationId, it).toList()
            }
          }
    } else {
      result =
          if (rbacEnabled) {
            val currentUser = getCurrentAccountIdentifier(this.csmPlatformProperties)
            solutionRepository
                .findByOrganizationIdAndSecurity(organizationId, currentUser, pageable)
                .toList()
          } else {
            solutionRepository.findByOrganizationId(organizationId, pageable).toList()
          }
    }
    return result.map { fillSdkVersion(updateSecurityVisibility(it)) }
  }

  override fun getSolution(organizationId: String, solutionId: String): Solution {
    return fillSdkVersion(updateSecurityVisibility(getVerifiedSolution(organizationId, solutionId)))
  }

  override fun isRunTemplateExist(
      organizationId: String,
      workspaceId: String,
      solutionId: String,
      runTemplateId: String,
  ): Boolean {
    val solution = getSolution(organizationId, solutionId)

    return solution.runTemplates.any { runTemplateId == it.id }
  }

  override fun createSolution(
      organizationId: String,
      solutionCreateRequest: SolutionCreateRequest,
  ): Solution {
    organizationApiService.getVerifiedOrganization(organizationId, PERMISSION_CREATE_CHILDREN)

    val solutionId = generateId("solution", prependPrefix = "sol-")
    val now = Instant.now().toEpochMilli()
    val security =
        csmRbac
            .initSecurity(solutionCreateRequest.security.toGenericSecurity(solutionId))
            .toResourceSecurity()

    checkParametersAndRunTemplateUnicity(
        solutionCreateRequest.parameters,
        solutionCreateRequest.parameterGroups,
        solutionCreateRequest.runTemplates,
    )

    val solutionRunTemplateParameters =
        solutionCreateRequest.parameters?.map { convertToRunTemplateParameter(it) }?.toMutableList()
            ?: mutableListOf()

    val solutionRunTemplateParameterGroups =
        solutionCreateRequest.parameterGroups
            ?.map { convertToRunTemplateParameterGroup(it) }
            ?.toMutableList() ?: mutableListOf()

    val solutionRunTemplates =
        solutionCreateRequest.runTemplates?.map { convertToRunTemplate(it) }?.toMutableList()
            ?: mutableListOf()

    val createdSolution =
        Solution(
            id = solutionId,
            key = solutionCreateRequest.key,
            name = solutionCreateRequest.name,
            createInfo =
                SolutionEditInfo(
                    timestamp = now,
                    userId = getCurrentAccountIdentifier(csmPlatformProperties),
                ),
            updateInfo =
                SolutionEditInfo(
                    timestamp = now,
                    userId = getCurrentAccountIdentifier(csmPlatformProperties),
                ),
            description = solutionCreateRequest.description,
            repository = solutionCreateRequest.repository,
            version = solutionCreateRequest.version,
            tags = solutionCreateRequest.tags,
            organizationId = organizationId,
            runTemplates = solutionRunTemplates,
            parameters = solutionRunTemplateParameters,
            parameterGroups = solutionRunTemplateParameterGroups,
            url = solutionCreateRequest.url,
            alwaysPull = solutionCreateRequest.alwaysPull,
            security = security,
        )

    return fillSdkVersion(solutionRepository.save(createdSolution))
  }

  override fun deleteSolution(organizationId: String, solutionId: String) {
    val solution = getVerifiedSolution(organizationId, solutionId, PERMISSION_DELETE)
    solutionRepository.delete(solution)
  }

  override fun listRunTemplates(organizationId: String, solutionId: String): List<RunTemplate> {
    val existingSolution = getVerifiedSolution(organizationId, solutionId)
    return existingSolution.runTemplates
  }

  override fun createSolutionRunTemplate(
      organizationId: String,
      solutionId: String,
      runTemplateCreateRequest: RunTemplateCreateRequest,
  ): RunTemplate {
    val existingSolution = getVerifiedSolution(organizationId, solutionId, PERMISSION_WRITE)
    val runTemplateIdAlreadyExist =
        existingSolution.runTemplates
            .map { it.id.lowercase() }
            .firstOrNull { it == runTemplateCreateRequest.id.lowercase() }

    require(runTemplateIdAlreadyExist == null) {
      "Run template with id '${runTemplateCreateRequest.id}' already exists"
    }
    val runTemplateToCreate = convertToRunTemplate(runTemplateCreateRequest)

    existingSolution.runTemplates.add(runTemplateToCreate)
    save(existingSolution)

    return runTemplateToCreate
  }

  override fun getRunTemplate(
      organizationId: String,
      solutionId: String,
      runTemplateId: String,
  ): RunTemplate {
    val solution = getVerifiedSolution(organizationId, solutionId)
    val solutionRunTemplate =
        solution.runTemplates.firstOrNull { it.id == runTemplateId }
            ?: throw CsmResourceNotFoundException(
                "Solution run template with id $runTemplateId does not exist"
            )
    return solutionRunTemplate
  }

  override fun updateSolutionRunTemplate(
      organizationId: String,
      solutionId: String,
      runTemplateId: String,
      runTemplateUpdateRequest: RunTemplateUpdateRequest,
  ): RunTemplate {
    val existingSolution = getVerifiedSolution(organizationId, solutionId, PERMISSION_WRITE)
    runTemplateUpdateRequest.runSizing?.let {
      validateResourceSizing(
          RunTemplateUpdateRequest::runSizing.name,
          it.requests.cpu,
          it.requests.memory,
          it.limits.cpu,
          it.limits.memory,
      )
    }

    existingSolution.runTemplates
        .find { it.id == runTemplateId }
        ?.apply {
          name = runTemplateUpdateRequest.name ?: this.name
          description = runTemplateUpdateRequest.description ?: this.description
          tags = runTemplateUpdateRequest.tags ?: this.tags
          labels = runTemplateUpdateRequest.labels ?: this.labels
          runSizing = runTemplateUpdateRequest.runSizing ?: this.runSizing
          computeSize = runTemplateUpdateRequest.computeSize ?: this.computeSize
          parameterGroups = runTemplateUpdateRequest.parameterGroups ?: this.parameterGroups
          executionTimeout = runTemplateUpdateRequest.executionTimeout ?: this.executionTimeout
        }
        ?: throw CsmResourceNotFoundException(
            "Solution run template with id $runTemplateId does not exist"
        )

    val solutionSaved = save(existingSolution)

    return solutionSaved.runTemplates.first { it.id == runTemplateId }
  }

  override fun deleteSolutionRunTemplate(
      organizationId: String,
      solutionId: String,
      runTemplateId: String,
  ) {
    val existingSolution = getVerifiedSolution(organizationId, solutionId, PERMISSION_WRITE)

    if (!existingSolution.runTemplates.removeIf { it.id == runTemplateId }) {
      throw CsmResourceNotFoundException(
          "Solution run template with id $runTemplateId does not exist"
      )
    }
    save(existingSolution)
  }

  override fun updateSolution(
      organizationId: String,
      solutionId: String,
      solutionUpdateRequest: SolutionUpdateRequest,
  ): Solution {
    val existingSolution = getVerifiedSolution(organizationId, solutionId, PERMISSION_WRITE)

    checkParametersAndRunTemplateUnicity(
        solutionUpdateRequest.parameters,
        solutionUpdateRequest.parameterGroups,
        solutionUpdateRequest.runTemplates,
    )

    val solutionRunTemplateParameters =
        solutionUpdateRequest.parameters?.map { convertToRunTemplateParameter(it) }?.toMutableList()
            ?: existingSolution.parameters

    val solutionRunTemplateParameterGroups =
        solutionUpdateRequest.parameterGroups
            ?.map { convertToRunTemplateParameterGroup(it) }
            ?.toMutableList() ?: existingSolution.parameterGroups

    val solutionRunTemplates =
        solutionUpdateRequest.runTemplates?.map { convertToRunTemplate(it) }?.toMutableList()
            ?: existingSolution.runTemplates

    val updatedSolution =
        Solution(
            id = solutionId,
            name = solutionUpdateRequest.name ?: existingSolution.name,
            organizationId = existingSolution.organizationId,
            createInfo = existingSolution.createInfo,
            updateInfo = existingSolution.updateInfo,
            description = solutionUpdateRequest.description ?: existingSolution.description,
            tags = solutionUpdateRequest.tags ?: existingSolution.tags,
            repository = solutionUpdateRequest.repository ?: existingSolution.repository,
            key = solutionUpdateRequest.key ?: existingSolution.key,
            version = solutionUpdateRequest.version ?: existingSolution.version,
            url = solutionUpdateRequest.url ?: existingSolution.url,
            alwaysPull = solutionUpdateRequest.alwaysPull ?: existingSolution.alwaysPull,
            parameters = solutionRunTemplateParameters,
            parameterGroups = solutionRunTemplateParameterGroups,
            runTemplates = solutionRunTemplates,
            security = existingSolution.security,
        )

    val hasChanged =
        existingSolution
            .compareToAndMutateIfNeeded(updatedSolution, excludedFields = arrayOf("ownerId"))
            .isNotEmpty()

    val returnedSolution = if (hasChanged) save(existingSolution) else existingSolution
    return fillSdkVersion(returnedSolution)
  }

  @EventListener(OrganizationUnregistered::class)
  @Async("csm-in-process-event-executor")
  fun onOrganizationUnregistered(organizationUnregistered: OrganizationUnregistered) {
    val pageable: Pageable =
        Pageable.ofSize(csmPlatformProperties.databases.resources.solution.defaultPageSize)
    val solutions =
        solutionRepository
            .findByOrganizationId(organizationUnregistered.organizationId, pageable)
            .toList()
    solutionRepository.deleteAll(solutions)
  }

  override fun getSolutionSecurity(organizationId: String, solutionId: String): SolutionSecurity {
    val solution = getVerifiedSolution(organizationId, solutionId, PERMISSION_READ_SECURITY)
    return solution.security
  }

  override fun listSolutionParameters(
      organizationId: String,
      solutionId: String,
  ): List<RunTemplateParameter> {
    val solution = getVerifiedSolution(organizationId, solutionId)
    return solution.parameters
  }

  override fun updateSolutionDefaultSecurity(
      organizationId: String,
      solutionId: String,
      solutionRole: SolutionRole,
  ): SolutionSecurity {
    val solution = getVerifiedSolution(organizationId, solutionId, PERMISSION_WRITE_SECURITY)
    val rbacSecurity =
        csmRbac.setDefault(solution.security.toGenericSecurity(solutionId), solutionRole.role)
    solution.security = rbacSecurity.toResourceSecurity()
    save(solution)
    return solution.security
  }

  override fun createSolutionAccessControl(
      organizationId: String,
      solutionId: String,
      solutionAccessControl: SolutionAccessControl,
  ): SolutionAccessControl {
    val organization = organizationApiService.getVerifiedOrganization(organizationId)
    val solution = getVerifiedSolution(organizationId, solutionId, PERMISSION_WRITE_SECURITY)

    val users = listSolutionSecurityUsers(organizationId, solutionId)
    require(!users.contains(solutionAccessControl.id)) {
      "User is already in this Solution security"
    }

    val rbacSecurity =
        csmRbac.addEntityRole(
            organization.security.toGenericSecurity(organizationId),
            solution.security.toGenericSecurity(solutionId),
            solutionAccessControl.id,
            solutionAccessControl.role,
        )
    solution.security = rbacSecurity.toResourceSecurity()
    save(solution)
    val rbacAccessControl =
        csmRbac.getAccessControl(
            solution.security.toGenericSecurity(solutionId),
            solutionAccessControl.id,
        )
    return SolutionAccessControl(rbacAccessControl.id, rbacAccessControl.role)
  }

  override fun listSolutionParameterGroups(
      organizationId: String,
      solutionId: String,
  ): List<RunTemplateParameterGroup> {
    val solution = getVerifiedSolution(organizationId, solutionId)
    return solution.parameterGroups
  }

  override fun createSolutionParameterGroup(
      organizationId: String,
      solutionId: String,
      runTemplateParameterGroupCreateRequest: RunTemplateParameterGroupCreateRequest,
  ): RunTemplateParameterGroup {
    val existingSolution = getVerifiedSolution(organizationId, solutionId, PERMISSION_WRITE)
    val parameterIdAlreadyExist =
        existingSolution.parameterGroups
            .map { it.id.lowercase() }
            .firstOrNull { it == runTemplateParameterGroupCreateRequest.id.lowercase() }

    require(parameterIdAlreadyExist == null) {
      "Parameter Group with id '${runTemplateParameterGroupCreateRequest.id}' already exists"
    }
    val parameterGroupToCreate =
        convertToRunTemplateParameterGroup(runTemplateParameterGroupCreateRequest)

    existingSolution.parameterGroups.add(parameterGroupToCreate)
    save(existingSolution)

    return parameterGroupToCreate
  }

  override fun getSolutionParameterGroup(
      organizationId: String,
      solutionId: String,
      parameterGroupId: String,
  ): RunTemplateParameterGroup {
    val solution = getVerifiedSolution(organizationId, solutionId)
    val solutionParameterGroup =
        solution.parameterGroups.firstOrNull { it.id == parameterGroupId }
            ?: throw CsmResourceNotFoundException(
                "Solution parameter group with id $parameterGroupId does not exist"
            )
    return solutionParameterGroup
  }

  override fun updateSolutionParameterGroup(
      organizationId: String,
      solutionId: String,
      parameterGroupId: String,
      runTemplateParameterGroupUpdateRequest: RunTemplateParameterGroupUpdateRequest,
  ): RunTemplateParameterGroup {

    val existingSolution = getVerifiedSolution(organizationId, solutionId, PERMISSION_WRITE)
    existingSolution.parameterGroups
        .find { it.id == parameterGroupId }
        ?.apply {
          description = runTemplateParameterGroupUpdateRequest.description ?: this.description
          labels = runTemplateParameterGroupUpdateRequest.labels ?: this.labels
          additionalData =
              runTemplateParameterGroupUpdateRequest.additionalData ?: this.additionalData
          parameters = runTemplateParameterGroupUpdateRequest.parameters ?: this.parameters
        }
        ?: throw CsmResourceNotFoundException(
            "Solution parameter group with id $parameterGroupId does not exist"
        )

    val solutionSaved = save(existingSolution)

    return solutionSaved.parameterGroups.first { it.id == parameterGroupId }
  }

  override fun deleteSolutionParameterGroup(
      organizationId: String,
      solutionId: String,
      parameterGroupId: String,
  ) {
    val solution = getVerifiedSolution(organizationId, solutionId, PERMISSION_WRITE)
    val solutionParameterGroup =
        solution.parameterGroups.firstOrNull { it.id == parameterGroupId }
            ?: throw CsmResourceNotFoundException(
                "Solution parameter group with id $parameterGroupId does not exist"
            )
    solution.parameterGroups.remove(solutionParameterGroup)
    save(solution)
  }

  override fun getSolutionAccessControl(
      organizationId: String,
      solutionId: String,
      identityId: String,
  ): SolutionAccessControl {
    val solution = getVerifiedSolution(organizationId, solutionId, PERMISSION_READ_SECURITY)
    val rbacAccessControl =
        csmRbac.getAccessControl(solution.security.toGenericSecurity(solutionId), identityId)
    return SolutionAccessControl(rbacAccessControl.id, rbacAccessControl.role)
  }

  override fun createSolutionParameter(
      organizationId: String,
      solutionId: String,
      runTemplateParameterCreateRequest: RunTemplateParameterCreateRequest,
  ): RunTemplateParameter {

    val existingSolution = getVerifiedSolution(organizationId, solutionId, PERMISSION_WRITE)

    val parameterIdAlreadyExist =
        existingSolution.parameters
            .map { it.id.lowercase() }
            .firstOrNull { it == runTemplateParameterCreateRequest.id.lowercase() }

    require(parameterIdAlreadyExist == null) {
      "Parameter with id '${runTemplateParameterCreateRequest.id}' already exists"
    }

    val parameterToCreate = convertToRunTemplateParameter(runTemplateParameterCreateRequest)

    existingSolution.parameters.add(parameterToCreate)
    save(existingSolution)

    return parameterToCreate
  }

  override fun updateSolutionParameter(
      organizationId: String,
      solutionId: String,
      parameterId: String,
      runTemplateParameterUpdateRequest: RunTemplateParameterUpdateRequest,
  ): RunTemplateParameter {

    val existingSolution = getVerifiedSolution(organizationId, solutionId, PERMISSION_WRITE)
    existingSolution.parameters
        .find { it.id == parameterId }
        ?.apply {
          description = runTemplateParameterUpdateRequest.description ?: this.description
          varType = runTemplateParameterUpdateRequest.varType ?: this.varType
          labels = runTemplateParameterUpdateRequest.labels ?: this.labels
          defaultValue = runTemplateParameterUpdateRequest.defaultValue ?: this.defaultValue
          minValue = runTemplateParameterUpdateRequest.minValue ?: this.minValue
          maxValue = runTemplateParameterUpdateRequest.maxValue ?: this.maxValue
          additionalData = runTemplateParameterUpdateRequest.additionalData ?: this.additionalData
        }
        ?: throw CsmResourceNotFoundException(
            "Solution parameter with id $parameterId does not exist"
        )

    val solutionSaved = save(existingSolution)

    return solutionSaved.parameters.first { it.id == parameterId }
  }

  override fun getSolutionParameter(
      organizationId: String,
      solutionId: String,
      parameterId: String,
  ): RunTemplateParameter {
    val solution = getVerifiedSolution(organizationId, solutionId)
    val solutionParameter =
        solution.parameters.firstOrNull { it.id == parameterId }
            ?: throw CsmResourceNotFoundException(
                "Solution parameter with id $parameterId does not exist"
            )
    return solutionParameter
  }

  override fun deleteSolutionParameter(
      organizationId: String,
      solutionId: String,
      parameterId: String,
  ) {
    val solution = getVerifiedSolution(organizationId, solutionId, PERMISSION_WRITE)
    val solutionParameter =
        solution.parameters.firstOrNull { it.id == parameterId }
            ?: throw CsmResourceNotFoundException(
                "Solution parameter with id $parameterId does not exist"
            )

    solution.parameters.remove(solutionParameter)
    save(solution)
  }

  override fun updateSolutionAccessControl(
      organizationId: String,
      solutionId: String,
      identityId: String,
      solutionRole: SolutionRole,
  ): SolutionAccessControl {
    val solution = getVerifiedSolution(organizationId, solutionId, PERMISSION_WRITE_SECURITY)
    csmRbac.checkEntityExists(
        solution.security.toGenericSecurity(solutionId),
        identityId,
        "User '$identityId' not found in solution $solutionId",
    )
    val rbacSecurity =
        csmRbac.setEntityRole(
            solution.security.toGenericSecurity(solutionId),
            identityId,
            solutionRole.role,
        )
    solution.security = rbacSecurity.toResourceSecurity()
    save(solution)
    val rbacAccessControl =
        csmRbac.getAccessControl(solution.security.toGenericSecurity(solutionId), identityId)
    return SolutionAccessControl(rbacAccessControl.id, rbacAccessControl.role)
  }

  override fun deleteSolutionAccessControl(
      organizationId: String,
      solutionId: String,
      identityId: String,
  ) {
    val solution = getVerifiedSolution(organizationId, solutionId, PERMISSION_WRITE_SECURITY)
    val rbacSecurity =
        csmRbac.removeEntity(solution.security.toGenericSecurity(solutionId), identityId)
    solution.security = rbacSecurity.toResourceSecurity()
    save(solution)
  }

  override fun listSolutionSecurityUsers(organizationId: String, solutionId: String): List<String> {
    val solution = getVerifiedSolution(organizationId, solutionId, PERMISSION_READ_SECURITY)
    return csmRbac.getEntities(solution.security.toGenericSecurity(solutionId))
  }

  override fun getVerifiedSolution(
      organizationId: String,
      solutionId: String,
      requiredPermission: String,
  ): Solution {
    organizationApiService.getVerifiedOrganization(organizationId)
    val solution =
        solutionRepository.findBy(organizationId, solutionId).orElseThrow {
          CsmResourceNotFoundException(
              "Solution $solutionId not found in organization $organizationId"
          )
        }
    csmRbac.verify(solution.security.toGenericSecurity(solutionId), requiredPermission)
    return solution
  }

  fun updateSecurityVisibility(solution: Solution): Solution {
    if (
        csmRbac
            .check(solution.security.toGenericSecurity(solution.id), PERMISSION_READ_SECURITY)
            .not()
    ) {
      val username = getCurrentAccountIdentifier(csmPlatformProperties)
      val retrievedAC = solution.security.accessControlList.firstOrNull { it.id == username }

      val accessControlList =
          if (retrievedAC != null) {
            mutableListOf(retrievedAC)
          } else {
            mutableListOf()
          }

      return solution.copy(
          security =
              SolutionSecurity(
                  default = solution.security.default,
                  accessControlList = accessControlList,
              )
      )
    }
    return solution
  }

  fun fillSdkVersion(solution: Solution) =
      solution.copy(
          sdkVersion =
              containerRegistryService.getImageLabel(
                  solution.repository,
                  solution.version,
                  "com.cosmotech.sdk-version",
              )
      )

  fun convertToRunTemplateParameter(
      runTemplateParameterCreateRequest: RunTemplateParameterCreateRequest
  ): RunTemplateParameter {
    return RunTemplateParameter(
        id = runTemplateParameterCreateRequest.id,
        description = runTemplateParameterCreateRequest.description,
        varType = runTemplateParameterCreateRequest.varType,
        labels = runTemplateParameterCreateRequest.labels,
        defaultValue = runTemplateParameterCreateRequest.defaultValue,
        minValue = runTemplateParameterCreateRequest.minValue,
        maxValue = runTemplateParameterCreateRequest.maxValue,
        additionalData = runTemplateParameterCreateRequest.additionalData,
    )
  }

  fun convertToRunTemplateParameterGroup(
      runTemplateParameterGroupCreateRequest: RunTemplateParameterGroupCreateRequest
  ): RunTemplateParameterGroup {
    return RunTemplateParameterGroup(
        id = runTemplateParameterGroupCreateRequest.id,
        description = runTemplateParameterGroupCreateRequest.description,
        labels = runTemplateParameterGroupCreateRequest.labels,
        additionalData = runTemplateParameterGroupCreateRequest.additionalData,
        parameters = runTemplateParameterGroupCreateRequest.parameters!!,
    )
  }

  fun convertToRunTemplate(runTemplateCreateRequest: RunTemplateCreateRequest): RunTemplate {
    runTemplateCreateRequest.runSizing?.let {
      validateResourceSizing(
          RunTemplateCreateRequest::runSizing.name,
          it.requests.cpu,
          it.requests.memory,
          it.limits.cpu,
          it.limits.memory,
      )
    }

    return RunTemplate(
        id = runTemplateCreateRequest.id,
        parameterGroups = runTemplateCreateRequest.parameterGroups!!,
        name = runTemplateCreateRequest.name,
        labels = runTemplateCreateRequest.labels,
        description = runTemplateCreateRequest.description,
        tags = runTemplateCreateRequest.tags,
        computeSize = runTemplateCreateRequest.computeSize,
        runSizing = runTemplateCreateRequest.runSizing,
        executionTimeout = runTemplateCreateRequest.executionTimeout,
    )
  }

  private fun checkParametersAndRunTemplateUnicity(
      parameters: MutableList<RunTemplateParameterCreateRequest>?,
      parameterGroups: MutableList<RunTemplateParameterGroupCreateRequest>?,
      runTemplates: MutableList<RunTemplateCreateRequest>?,
  ) {

    val duplicatedFieldIds = mutableListOf<String>()

    if (
        parameters?.groupBy { it.id.lowercase() }?.filterValues { it.size > 1 }?.isNotEmpty() ==
            true
    ) {
      duplicatedFieldIds.add("parameters")
    }

    if (
        parameterGroups
            ?.groupBy { it.id.lowercase() }
            ?.filterValues { it.size > 1 }
            ?.isNotEmpty() == true
    ) {
      duplicatedFieldIds.add("parameterGroups")
    }

    if (
        runTemplates?.groupBy { it.id.lowercase() }?.filterValues { it.size > 1 }?.isNotEmpty() ==
            true
    ) {
      duplicatedFieldIds.add("runTemplates")
    }

    require(duplicatedFieldIds.isEmpty()) {
      "One or several solution items have same id : ${duplicatedFieldIds.joinToString(",")}"
    }
  }

  fun save(solution: Solution): Solution {
    solution.updateInfo =
        SolutionEditInfo(
            timestamp = Instant.now().toEpochMilli(),
            userId = getCurrentAccountIdentifier(csmPlatformProperties),
        )

    return solutionRepository.save(solution)
  }
}

fun SolutionSecurity?.toGenericSecurity(solutionId: String) =
    RbacSecurity(
        solutionId,
        this?.default ?: ROLE_NONE,
        this?.accessControlList?.map { RbacAccessControl(it.id, it.role) }?.toMutableList()
            ?: mutableListOf(),
    )

fun RbacSecurity.toResourceSecurity() =
    SolutionSecurity(
        this.default,
        this.accessControlList.map { SolutionAccessControl(it.id, it.role) }.toMutableList(),
    )
