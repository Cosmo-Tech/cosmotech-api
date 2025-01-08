// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.organization.service

import com.cosmotech.api.CsmPhoenixService
import com.cosmotech.api.events.OrganizationUnregistered
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.rbac.CsmAdmin
import com.cosmotech.api.rbac.CsmRbac
import com.cosmotech.api.rbac.PERMISSION_DELETE
import com.cosmotech.api.rbac.PERMISSION_READ
import com.cosmotech.api.rbac.PERMISSION_READ_SECURITY
import com.cosmotech.api.rbac.PERMISSION_WRITE
import com.cosmotech.api.rbac.PERMISSION_WRITE_SECURITY
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.rbac.getAllRolesDefinition
import com.cosmotech.api.rbac.getCommonRolesDefinition
import com.cosmotech.api.rbac.model.RbacAccessControl
import com.cosmotech.api.rbac.model.RbacSecurity
import com.cosmotech.api.utils.changed
import com.cosmotech.api.utils.constructPageRequest
import com.cosmotech.api.utils.findAllPaginated
import com.cosmotech.api.utils.getCurrentAccountIdentifier
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.organization.OrganizationApiServiceInterface
import com.cosmotech.organization.domain.ComponentRolePermissions
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationRole
import com.cosmotech.organization.domain.OrganizationSecurity
import com.cosmotech.organization.repository.OrganizationRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
@Suppress("TooManyFunctions")
class OrganizationServiceImpl(
    private val csmRbac: CsmRbac,
    private val csmAdmin: CsmAdmin,
    private val organizationRepository: OrganizationRepository,
    private val organizationSecurityService: OrganizationSecurityService,
    private val organizationVerificationService: OrganizationVerificationService
) : CsmPhoenixService(), OrganizationApiServiceInterface, IOrganizationService {

  override fun findAllOrganizations(page: Int?, size: Int?): List<Organization> {
    val defaultPageSize = csmPlatformProperties.twincache.organization.defaultPageSize
    val pageable = constructPageRequest(page, size, defaultPageSize)
    val isAdmin = csmAdmin.verifyCurrentRolesAdmin()
    val result: MutableList<Organization>

    val rbacEnabled = !isAdmin && this.csmPlatformProperties.rbac.enabled

    if (pageable == null) {
      result =
          findAllPaginated(defaultPageSize) {
            if (rbacEnabled) {
              val currentUser = getCurrentAccountIdentifier(this.csmPlatformProperties)
              organizationRepository.findOrganizationsBySecurity(currentUser, it).toList()
            } else {
              organizationRepository.findAll(it).toList()
            }
          }
    } else {
      result =
          if (rbacEnabled) {
            val currentUser = getCurrentAccountIdentifier(this.csmPlatformProperties)
            organizationRepository.findOrganizationsBySecurity(currentUser, pageable).toList()
          } else {
            organizationRepository.findAll(pageable).toList()
          }
    }
    result.forEach { it.security = updateSecurityVisibility(it).security }
    return result
  }

  override fun findOrganizationById(organizationId: String): Organization {
    return updateSecurityVisibility(getVerifiedOrganization(organizationId, PERMISSION_READ))
  }

  override fun registerOrganization(organization: Organization): Organization {
    logger.trace("Registering organization: {}", organization)

    if (organization.name.isNullOrBlank()) {
      throw IllegalArgumentException("Organization name must not be null or blank")
    }

    val createdOrganization =
        organization.copy(
            id = idGenerator.generate("organization"),
            ownerId = getCurrentAuthenticatedUserName(csmPlatformProperties))
    createdOrganization.setRbac(csmRbac.initSecurity(organization.getRbac()))

    return organizationRepository.save(createdOrganization)
  }

  override fun unregisterOrganization(organizationId: String) {
    val organization = getVerifiedOrganization(organizationId, PERMISSION_DELETE)
    organizationRepository.delete(organization)
    this.eventPublisher.publishEvent(OrganizationUnregistered(this, organizationId))
  }

  override fun updateOrganization(
      organizationId: String,
      organization: Organization
  ): Organization {
    val existingOrganization = getVerifiedOrganization(organizationId, PERMISSION_WRITE)
    var hasChanged = false

    if (organization.name != null && organization.changed(existingOrganization) { name }) {
      existingOrganization.name = organization.name
      hasChanged = true
    }

    if (organization.security != existingOrganization.security) {
      logger.warn(
          "Security modification has not been applied to organization $organizationId," +
              " please refer to the appropriate security endpoints to perform this maneuver")
    }

    return if (hasChanged) {
      organizationRepository.save(existingOrganization)
    } else {
      existingOrganization
    }
  }

  override fun getAllPermissions(): List<ComponentRolePermissions> {
    return getAllRolesDefinition().mapNotNull { ComponentRolePermissions(it.key, it.value) }
  }

  override fun getOrganizationPermissions(organizationId: String, role: String): List<String> {
    getVerifiedOrganization(organizationId, PERMISSION_READ_SECURITY)
    return com.cosmotech.api.rbac.getPermissions(role, getCommonRolesDefinition())
  }

  override fun getOrganizationSecurity(organizationId: String): OrganizationSecurity {
    return organizationSecurityService.getOrganizationSecurity(organizationId)
  }

  override fun setOrganizationDefaultSecurity(
      organizationId: String,
      organizationRole: OrganizationRole
  ): OrganizationSecurity {
    return organizationSecurityService.setOrganizationDefaultSecurity(organizationId, organizationRole)
  }

  override fun getOrganizationAccessControl(
      organizationId: String,
      identityId: String
  ): OrganizationAccessControl {
    return organizationSecurityService.getOrganizationAccessControl(organizationId, identityId)
  }

  override fun addOrganizationAccessControl(
      organizationId: String,
      organizationAccessControl: OrganizationAccessControl
  ): OrganizationAccessControl {
    return organizationSecurityService.addOrganizationAccessControl(organizationId, organizationAccessControl)
  }

  override fun updateOrganizationAccessControl(
      organizationId: String,
      identityId: String,
      organizationRole: OrganizationRole
  ): OrganizationAccessControl {
    return organizationSecurityService.updateOrganizationAccessControl(organizationId, identityId, organizationRole)
  }

  override fun removeOrganizationAccessControl(organizationId: String, identityId: String) {
    organizationSecurityService.removeOrganizationAccessControl(organizationId, identityId)
  }

  override fun getOrganizationSecurityUsers(organizationId: String): List<String> {
    return organizationSecurityService.getOrganizationSecurityUsers(organizationId)
  }

  override fun getVerifiedOrganization(organizationId: String, requiredPermission: String): Organization {
    return organizationVerificationService.getVerifiedOrganization(organizationId, requiredPermission)
  }

  override fun getVerifiedOrganization(organizationId: String, requiredPermissions: List<String>): Organization {
    return organizationVerificationService.getVerifiedOrganization(organizationId, requiredPermissions)
  }

  fun updateSecurityVisibility(organization: Organization): Organization {
    if (csmRbac.check(organization.getRbac(), PERMISSION_READ_SECURITY).not()) {
      val username = getCurrentAccountIdentifier(csmPlatformProperties)
      val retrievedAC = organization.security!!.accessControlList.firstOrNull { it.id == username }
      return if (retrievedAC != null) {
        organization.copy(
            security =
                OrganizationSecurity(
                    default = organization.security!!.default,
                    accessControlList = mutableListOf(retrievedAC)))
      } else {
        organization.copy(
            security =
                OrganizationSecurity(
                    default = organization.security!!.default, accessControlList = mutableListOf()))
      }
    }
    return organization
  }
}
