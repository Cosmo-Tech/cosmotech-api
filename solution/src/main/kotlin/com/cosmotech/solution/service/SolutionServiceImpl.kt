// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.solution.service

import com.cosmotech.api.CsmPhoenixService
import com.cosmotech.api.events.OrganizationUnregistered
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.rbac.CsmAdmin
import com.cosmotech.api.rbac.CsmRbac
import com.cosmotech.api.rbac.PERMISSION_CREATE_CHILDREN
import com.cosmotech.api.rbac.PERMISSION_DELETE
import com.cosmotech.api.rbac.PERMISSION_READ_SECURITY
import com.cosmotech.api.rbac.PERMISSION_WRITE
import com.cosmotech.api.rbac.PERMISSION_WRITE_SECURITY
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.rbac.model.RbacAccessControl
import com.cosmotech.api.rbac.model.RbacSecurity
import com.cosmotech.api.security.ROLE_PLATFORM_ADMIN
import com.cosmotech.api.utils.changed
import com.cosmotech.api.utils.compareToAndMutateIfNeeded
import com.cosmotech.api.utils.constructPageRequest
import com.cosmotech.api.utils.convertToMap
import com.cosmotech.api.utils.findAllPaginated
import com.cosmotech.api.utils.getCurrentAccountIdentifier
import com.cosmotech.api.utils.getCurrentAuthenticatedRoles
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.organization.OrganizationApiServiceInterface
import com.cosmotech.organization.service.toGenericSecurity
import com.cosmotech.solution.SolutionApiServiceInterface
import com.cosmotech.solution.domain.RunTemplate
import com.cosmotech.solution.domain.RunTemplateParameter
import com.cosmotech.solution.domain.RunTemplateParameterGroup
import com.cosmotech.solution.domain.Solution
import com.cosmotech.solution.domain.SolutionAccessControl
import com.cosmotech.solution.domain.SolutionCreateRequest
import com.cosmotech.solution.domain.SolutionRole
import com.cosmotech.solution.domain.SolutionSecurity
import com.cosmotech.solution.domain.SolutionUpdateRequest
import com.cosmotech.solution.repository.SolutionRepository
import org.springframework.context.event.EventListener
import org.springframework.data.domain.Pageable
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
@Suppress("TooManyFunctions")
class SolutionServiceImpl(
    private val solutionRepository: SolutionRepository,
    private val organizationApiService: OrganizationApiServiceInterface,
    private val csmRbac: CsmRbac,
    private val csmAdmin: CsmAdmin
) : CsmPhoenixService(), SolutionApiServiceInterface {

  override fun listSolutions(organizationId: String, page: Int?, size: Int?): List<Solution> {
    organizationApiService.getVerifiedOrganization(organizationId)

    val defaultPageSize = csmPlatformProperties.twincache.solution.defaultPageSize
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
    result.forEach { it.security = updateSecurityVisibility(it).security }
    return result
  }

  override fun getSolution(organizationId: String, solutionId: String): Solution {
    return updateSecurityVisibility(getVerifiedSolution(organizationId, solutionId))
  }

  override fun deleteSolutionRunTemplates(organizationId: String, solutionId: String) {
    val solution = getVerifiedSolution(organizationId, solutionId, PERMISSION_DELETE)

    if (solution.runTemplates.isNotEmpty()) {
      solution.runTemplates = mutableListOf()
      solutionRepository.save(solution)
    }
  }

  override fun deleteSolutionParameterGroups(organizationId: String, solutionId: String) {
    val solution = getVerifiedSolution(organizationId, solutionId, PERMISSION_DELETE)

    if (!solution.parameterGroups.isNullOrEmpty()) {
      solution.parameterGroups = mutableListOf()
      solutionRepository.save(solution)
    }
  }

  override fun deleteSolutionParameters(organizationId: String, solutionId: String) {
    val solution = getVerifiedSolution(organizationId, solutionId, PERMISSION_DELETE)

    if (!solution.parameters.isNullOrEmpty()) {
      solution.parameters = mutableListOf()
      solutionRepository.save(solution)
    }
  }

  override fun isRunTemplateExist(
      organizationId: String,
      workspaceId: String,
      solutionId: String,
      runTemplateId: String
  ): Boolean {
    val solution = getSolution(organizationId, solutionId)

    return solution.runTemplates.any { runTemplateId == it.id }
  }

  override fun updateSolutionParameterGroups(
    organizationId: String,
    solutionId: String,
    runTemplateParameterGroup: List<RunTemplateParameterGroup>
  ): List<RunTemplateParameterGroup> {
    val existingSolution = getVerifiedSolution(organizationId, solutionId, PERMISSION_WRITE)

    if (runTemplateParameterGroup.isEmpty()) {
      return runTemplateParameterGroup
    }

    val runTemplateParameterGroupMap =
        existingSolution.parameterGroups.associateBy { it.id }?.toMutableMap() ?: mutableMapOf()
    runTemplateParameterGroupMap.putAll(
        runTemplateParameterGroup.filter { it.id.isNotBlank() }.associateBy { it.id })
    existingSolution.parameterGroups = runTemplateParameterGroupMap.values.toMutableList()
    solutionRepository.save(existingSolution)

    return runTemplateParameterGroup
  }

  override fun updateSolutionParameters(
    organizationId: String,
    solutionId: String,
    runTemplateParameter: List<RunTemplateParameter>
  ): List<RunTemplateParameter> {
    val existingSolution = getVerifiedSolution(organizationId, solutionId, PERMISSION_WRITE)

    if (runTemplateParameter.isEmpty()) {
      return runTemplateParameter
    }

    val runTemplateParameterMap =
        existingSolution.parameters.associateBy { it.id }?.toMutableMap() ?: mutableMapOf()
    runTemplateParameterMap.putAll(
        runTemplateParameter.filter { it.id.isNotBlank() }.associateBy { it.id })
    existingSolution.parameters = runTemplateParameterMap.values.toMutableList()
    solutionRepository.save(existingSolution)

    return runTemplateParameter
  }

  override fun updateSolutionRunTemplates(
    organizationId: String,
    solutionId: String,
    runTemplate: List<RunTemplate>
  ): List<RunTemplate> {
    val existingSolution = getVerifiedSolution(organizationId, solutionId, PERMISSION_WRITE)

    if (runTemplate.isEmpty()) {
      return runTemplate
    }

    val runTemplateMap = existingSolution.runTemplates.associateBy { it.id }.toMutableMap()
    runTemplateMap.putAll(runTemplate.filter { it.id.isNotBlank() }.associateBy { it.id })
    existingSolution.runTemplates = runTemplateMap.values.toMutableList()
    solutionRepository.save(existingSolution)

    return runTemplate
  }

  override fun createSolution(organizationId: String, solutionCreateRequest: SolutionCreateRequest): Solution {
    organizationApiService.getVerifiedOrganization(organizationId, PERMISSION_CREATE_CHILDREN)

    val solutionId = idGenerator.generate("solution", prependPrefix = "sol-")
    val security = csmRbac.initSecurity(
      solutionCreateRequest.security.toGenericSecurity(solutionId)).toResourceSecurity()
    val createdSolution =
      Solution(
        id = solutionId,
        key = solutionCreateRequest.key,
        name = solutionCreateRequest.name,
        ownerId = getCurrentAuthenticatedUserName(csmPlatformProperties),
        description = solutionCreateRequest.description,
        repository = solutionCreateRequest.repository,
        version = solutionCreateRequest.version,
        tags = solutionCreateRequest.tags,
        organizationId = organizationId,
        runTemplates = solutionCreateRequest.runTemplates,
        parameters = solutionCreateRequest.parameters,
        parameterGroups = solutionCreateRequest.parameterGroups,
        url = solutionCreateRequest.url,
        csmSimulator = solutionCreateRequest.csmSimulator,
        alwaysPull = solutionCreateRequest.alwaysPull,
        sdkVersion = solutionCreateRequest.sdkVersion,
        security = security
      )
    return solutionRepository.save(createdSolution)
  }

  override fun deleteSolution(organizationId: String, solutionId: String) {
    val solution = getVerifiedSolution(organizationId, solutionId, PERMISSION_DELETE)
    // TODO Only the owner or an admin should be able to perform this operation
    solutionRepository.delete(solution)
  }

  override fun deleteSolutionRunTemplate(
      organizationId: String,
      solutionId: String,
      runTemplateId: String
  ) {
    val existingSolution = getVerifiedSolution(organizationId, solutionId, PERMISSION_DELETE)

    if (!existingSolution.runTemplates.removeIf { it.id == runTemplateId }) {
      throw CsmResourceNotFoundException("Run Template '$runTemplateId' *not* found")
    }
    solutionRepository.save(existingSolution)
  }

  override fun updateSolution(
    organizationId: String,
    solutionId: String,
    solutionUpdateRequest: SolutionUpdateRequest
  ): Solution {
    val existingSolution = getVerifiedSolution(organizationId, solutionId, PERMISSION_WRITE)

    // solutionId update is allowed but must be done with care. Maybe limit to minor update?

    val updatedSolution = Solution(
      id = solutionId,
      name = solutionUpdateRequest.name ?: existingSolution.name,
      organizationId = existingSolution.organizationId,
      ownerId = existingSolution.ownerId,
      description = solutionUpdateRequest.description,
      tags = solutionUpdateRequest.tags,
      repository = solutionUpdateRequest.repository ?: existingSolution.repository,
      key = solutionUpdateRequest.key ?: existingSolution.key,
      version = solutionUpdateRequest.version ?: existingSolution.version,
      url = solutionUpdateRequest.url,
      csmSimulator = solutionUpdateRequest.csmSimulator ?: existingSolution.csmSimulator,
      alwaysPull = solutionUpdateRequest.alwaysPull,
      parameters = solutionUpdateRequest.parameters ?: existingSolution.parameters,
      sdkVersion = solutionUpdateRequest.sdkVersion,
      parameterGroups = solutionUpdateRequest.parameterGroups ?: existingSolution.parameterGroups,
      security = existingSolution.security
    )
    
    val hasChanged =
        existingSolution
            .compareToAndMutateIfNeeded(
              updatedSolution, excludedFields = arrayOf("ownerId", "runTemplates"))
            .isNotEmpty()

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
    val existingSolution = getVerifiedSolution(organizationId, solutionId, PERMISSION_WRITE)
    val runTemplateToChange =
        existingSolution.runTemplates.firstOrNull { it.id == runTemplateId }
            ?: throw CsmResourceNotFoundException("Run Template '$runTemplateId' *not* found")

    runTemplateToChange.compareToAndMutateIfNeeded(runTemplate).isNotEmpty()
    solutionRepository.save(existingSolution)

    return existingSolution.runTemplates.toList()
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

  override fun getSolutionSecurity(organizationId: String, solutionId: String): SolutionSecurity {
    val solution = getVerifiedSolution(organizationId, solutionId, PERMISSION_READ_SECURITY)
    return solution.security
  }

  override fun updateSolutionDefaultSecurity(
    organizationId: String,
    solutionId: String,
    solutionRole: SolutionRole
  ): SolutionSecurity {
    val solution = getVerifiedSolution(organizationId, solutionId, PERMISSION_WRITE_SECURITY)
    val rbacSecurity = csmRbac.setDefault(solution.security.toGenericSecurity(solutionId), solutionRole.role)
    solution.security = rbacSecurity.toResourceSecurity()
    solutionRepository.save(solution)
    return solution.security
  }

  override fun createSolutionAccessControl(
    organizationId: String,
    solutionId: String,
    solutionAccessControl: SolutionAccessControl
  ): SolutionAccessControl {
    val organization = organizationApiService.getVerifiedOrganization(organizationId)
    val solution = getVerifiedSolution(organizationId, solutionId, PERMISSION_WRITE_SECURITY)

    val users = listSolutionSecurityUsers(organizationId, solutionId)
    if (users.contains(solutionAccessControl.id)) {
      throw IllegalArgumentException("User is already in this Solution security")
    }

    val rbacSecurity =
        csmRbac.addUserRole(
            organization.security.toGenericSecurity(organizationId),
            solution.security.toGenericSecurity(solutionId),
            solutionAccessControl.id,
            solutionAccessControl.role)
    solution.security = rbacSecurity.toResourceSecurity()
    solutionRepository.save(solution)
    val rbacAccessControl = csmRbac.getAccessControl(
      solution.security.toGenericSecurity(solutionId), solutionAccessControl.id)
    return SolutionAccessControl(rbacAccessControl.id, rbacAccessControl.role)
  }

  override fun getSolutionAccessControl(
      organizationId: String,
      solutionId: String,
      identityId: String
  ): SolutionAccessControl {
    val solution = getVerifiedSolution(organizationId, solutionId, PERMISSION_READ_SECURITY)
    val rbacAccessControl = csmRbac.getAccessControl(solution.security.toGenericSecurity(solutionId), identityId)
    return SolutionAccessControl(rbacAccessControl.id, rbacAccessControl.role)
  }

  override fun updateSolutionAccessControl(
      organizationId: String,
      solutionId: String,
      identityId: String,
      solutionRole: SolutionRole
  ): SolutionAccessControl {
    val solution = getVerifiedSolution(organizationId, solutionId, PERMISSION_WRITE_SECURITY)
    csmRbac.checkUserExists(
        solution.security.toGenericSecurity(
          solutionId), identityId, "User '$identityId' not found in solution $solutionId")
    val rbacSecurity = csmRbac.setUserRole(
      solution.security.toGenericSecurity(solutionId), identityId, solutionRole.role)
    solution.security = rbacSecurity.toResourceSecurity()
    solutionRepository.save(solution)
    val rbacAccessControl = csmRbac.getAccessControl(solution.security.toGenericSecurity(solutionId), identityId)
    return SolutionAccessControl(rbacAccessControl.id, rbacAccessControl.role)
  }

  override fun deleteSolutionAccessControl(organizationId: String, solutionId: String, identityId: String) {
    val solution = getVerifiedSolution(organizationId, solutionId, PERMISSION_WRITE_SECURITY)
    val rbacSecurity = csmRbac.removeUser(solution.security.toGenericSecurity(solutionId), identityId)
    solution.security = rbacSecurity.toResourceSecurity()
    solutionRepository.save(solution)
  }

  override fun listSolutionSecurityUsers(organizationId: String, solutionId: String): List<String> {
    val solution = getVerifiedSolution(organizationId, solutionId, PERMISSION_READ_SECURITY)
    return csmRbac.getUsers(solution.security.toGenericSecurity(solutionId))
  }

  override fun getVerifiedSolution(
      organizationId: String,
      solutionId: String,
      requiredPermission: String
  ): Solution {
    organizationApiService.getVerifiedOrganization(organizationId)
    val solution =
        solutionRepository.findBy(organizationId, solutionId).orElseThrow {
          CsmResourceNotFoundException(
              "Solution $solutionId not found in organization $organizationId")
        }
    csmRbac.verify(solution.security.toGenericSecurity(solutionId), requiredPermission)
    return solution
  }

  fun updateSecurityVisibility(solution: Solution): Solution {
    if (csmRbac.check(solution.security.toGenericSecurity(solution.id), PERMISSION_READ_SECURITY).not()) {
      val username = getCurrentAccountIdentifier(csmPlatformProperties)
      val retrievedAC = solution.security.accessControlList.firstOrNull { it.id == username }
      if (retrievedAC != null) {
        return solution.copy(
            security =
                SolutionSecurity(
                    default = solution.security.default,
                    accessControlList = mutableListOf(retrievedAC)))
      } else {
        return solution.copy(
            security =
                SolutionSecurity(
                    default = solution.security.default, accessControlList = mutableListOf()))
      }
    }
    return solution
  }
}


fun SolutionSecurity?.toGenericSecurity(solutionId: String) = RbacSecurity(
  solutionId,
  this?.default ?: ROLE_NONE,
  this?.accessControlList?.map { RbacAccessControl(it.id, it.role) }?.toMutableList() ?: mutableListOf()
)

fun RbacSecurity.toResourceSecurity() =
  SolutionSecurity(
    this.default,
    this.accessControlList
      .map { SolutionAccessControl(it.id, it.role) }
      .toMutableList())
