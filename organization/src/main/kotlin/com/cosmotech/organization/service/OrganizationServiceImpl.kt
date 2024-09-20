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
import com.cosmotech.api.utils.constructPageRequest
import com.cosmotech.api.utils.findAllPaginated
import com.cosmotech.api.utils.getCurrentAccountIdentifier
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.organization.OrganizationApiServiceInterface
import com.cosmotech.organization.domain.ComponentRolePermissions
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationRequest
import com.cosmotech.organization.domain.OrganizationRole
import com.cosmotech.organization.domain.OrganizationSecurity
import com.cosmotech.organization.domain.OrganizationUpdate
import com.cosmotech.organization.repository.OrganizationRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
@Suppress("TooManyFunctions")
class OrganizationServiceImpl(
    private val csmRbac: CsmRbac,
    private val csmAdmin: CsmAdmin,
    private val organizationRepository: OrganizationRepository
) : CsmPhoenixService(), OrganizationApiServiceInterface {

  override fun listOrganizations(page: Int?, size: Int?): List<Organization> {
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

    return result
  }

  override fun getOrganization(organizationId: String): Organization {
    return getVerifiedOrganization(organizationId, PERMISSION_READ)
  }

  override fun createOrganization(organizationRequest: OrganizationRequest): Organization {
    logger.trace("Creating organization: {}", organizationRequest)

    if (organizationRequest.name.isNullOrBlank()) {
      throw IllegalArgumentException("Organization name must not be null or blank")
    }

    val organizationId = idGenerator.generate("organization")
    val createdOrganization =
        Organization(
            id = organizationId,
            ownerId = getCurrentAuthenticatedUserName(csmPlatformProperties),
            name = organizationRequest.name,
            security =
                csmRbac
                    .initSecurity(organizationRequest.security.toGenericSecurity(organizationId))
                    .toResourceSecurity())

    return organizationRepository.save(createdOrganization)
  }

  override fun deleteOrganization(organizationId: String) {
    val organization = getVerifiedOrganization(organizationId, PERMISSION_DELETE)
    organizationRepository.delete(organization)
    this.eventPublisher.publishEvent(OrganizationUnregistered(this, organizationId))
  }

  override fun updateOrganization(
      organizationId: String,
      organizationUpdate: OrganizationUpdate
  ): Organization {
    var organization = getVerifiedOrganization(organizationId, PERMISSION_WRITE)

    organizationUpdate.name?.let { organization.name = it }

    return organizationRepository.save(organization)
  }

  override fun getAllPermissions(): List<ComponentRolePermissions> {
    return getAllRolesDefinition().mapNotNull { ComponentRolePermissions(it.key, it.value) }
  }

  override fun getOrganizationPermissions(organizationId: String, role: String): List<String> {
    getVerifiedOrganization(organizationId, PERMISSION_READ_SECURITY)
    return com.cosmotech.api.rbac.getPermissions(role, getCommonRolesDefinition())
  }

  override fun getOrganizationSecurity(organizationId: String): OrganizationSecurity {
    val organization = getVerifiedOrganization(organizationId, PERMISSION_READ_SECURITY)
    return organization.security
  }

  override fun setOrganizationDefaultSecurity(
      organizationId: String,
      organizationRole: OrganizationRole
  ): OrganizationSecurity {
    val organization = getVerifiedOrganization(organizationId, PERMISSION_WRITE_SECURITY)
    val rbacSecurity =
        csmRbac.setDefault(
            organization.security.toGenericSecurity(organization.id), organizationRole.role)
    organization.security = rbacSecurity.toResourceSecurity()
    organizationRepository.save(organization)
    return organization.security
  }

  override fun getOrganizationAccessControl(
      organizationId: String,
      identityId: String
  ): OrganizationAccessControl {
    val organization = getVerifiedOrganization(organizationId, PERMISSION_READ_SECURITY)
    val rbacAccessControl =
        csmRbac.getAccessControl(
            organization.security.toGenericSecurity(organization.id), identityId)
    return OrganizationAccessControl(rbacAccessControl.id, rbacAccessControl.role)
  }

  override fun addOrganizationAccessControl(
      organizationId: String,
      organizationAccessControl: OrganizationAccessControl
  ): OrganizationAccessControl {
    val organization = getVerifiedOrganization(organizationId, PERMISSION_WRITE_SECURITY)

    val users = getOrganizationSecurityUsers(organizationId)
    if (users.contains(organizationAccessControl.id)) {
      throw IllegalArgumentException("User is already in this Organization security")
    }

    val rbacSecurity =
        csmRbac.setUserRole(
            organization.security.toGenericSecurity(organization.id),
            organizationAccessControl.id,
            organizationAccessControl.role)
    organization.security = rbacSecurity.toResourceSecurity()
    organizationRepository.save(organization)
    val rbacAccessControl =
        csmRbac.getAccessControl(
            organization.security.toGenericSecurity(organization.id), organizationAccessControl.id)
    return OrganizationAccessControl(rbacAccessControl.id, rbacAccessControl.role)
  }

  override fun updateOrganizationAccessControl(
      organizationId: String,
      identityId: String,
      organizationRole: OrganizationRole
  ): OrganizationAccessControl {
    val organization = getVerifiedOrganization(organizationId, PERMISSION_WRITE_SECURITY)
    csmRbac.checkUserExists(
        organization.security.toGenericSecurity(organization.id),
        identityId,
        "User '$identityId' not found in organization $organizationId")
    val rbacSecurity =
        csmRbac.setUserRole(
            organization.security.toGenericSecurity(organization.id),
            identityId,
            organizationRole.role)
    organization.security = rbacSecurity.toResourceSecurity()
    organizationRepository.save(organization)
    val rbacAccessControl =
        csmRbac.getAccessControl(
            organization.security.toGenericSecurity(organization.id), identityId)
    return OrganizationAccessControl(rbacAccessControl.id, rbacAccessControl.role)
  }

  override fun removeOrganizationAccessControl(organizationId: String, identityId: String) {
    val organization = getVerifiedOrganization(organizationId, PERMISSION_WRITE_SECURITY)
    val rbacSecurity =
        csmRbac.removeUser(organization.security.toGenericSecurity(organization.id), identityId)
    organization.security = rbacSecurity.toResourceSecurity()
    organizationRepository.save(organization)
  }

  override fun getOrganizationSecurityUsers(organizationId: String): List<String> {
    val organization = getVerifiedOrganization(organizationId, PERMISSION_READ_SECURITY)
    return csmRbac.getUsers(organization.security.toGenericSecurity(organization.id))
  }

  override fun getVerifiedOrganization(
      organizationId: String,
      requiredPermission: String
  ): Organization {
    val organization =
        organizationRepository.findByIdOrNull(organizationId)
            ?: throw CsmResourceNotFoundException("Organization $organizationId does not exist!")
    csmRbac.verify(organization.security.toGenericSecurity(organization.id), requiredPermission)
    return organization
  }

  override fun getVerifiedOrganization(
      organizationId: String,
      requiredPermissions: List<String>
  ): Organization {
    val organization = getVerifiedOrganization(organizationId)
    requiredPermissions.forEach {
      csmRbac.verify(organization.security.toGenericSecurity(organization.id), it)
    }
    return organization
  }
}

fun OrganizationSecurity?.toGenericSecurity(id: String?): RbacSecurity =
    RbacSecurity(
        id,
        this?.default ?: ROLE_NONE,
        this?.accessControlList?.map { RbacAccessControl(it.id, it.role) }?.toMutableList()
            ?: mutableListOf())

fun RbacSecurity.toResourceSecurity(): OrganizationSecurity =
    OrganizationSecurity(
        this.default,
        this.accessControlList.map { OrganizationAccessControl(it.id, it.role) }.toMutableList())
