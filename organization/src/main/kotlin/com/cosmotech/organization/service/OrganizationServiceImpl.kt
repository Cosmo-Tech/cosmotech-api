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
import com.cosmotech.organization.domain.OrganizationCreateRequest
import com.cosmotech.organization.domain.OrganizationRole
import com.cosmotech.organization.domain.OrganizationSecurity
import com.cosmotech.organization.domain.OrganizationUpdateRequest
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
    result.forEach { it.security = updateSecurityVisibility(it).security }
    return result
  }

  override fun getOrganization(organizationId: String): Organization {
    return updateSecurityVisibility(getVerifiedOrganization(organizationId, PERMISSION_READ))
  }

  override fun createOrganization(
      organizationCreateRequest: OrganizationCreateRequest
  ): Organization {
    logger.trace("Registering organization: {}", organizationCreateRequest)

    if (organizationCreateRequest.name.isBlank()) {
      throw IllegalArgumentException("Organization name must not be null or blank")
    }

    val organizationId = idGenerator.generate("organization")
    val security = csmRbac.initSecurity(
      organizationCreateRequest.security.toGenericSecurity(organizationId)).toResourceSecurity()
    val createdOrganization =
        Organization(
            id = organizationId,
            name = organizationCreateRequest.name,
            ownerId = getCurrentAuthenticatedUserName(csmPlatformProperties),
            security = security)

    return organizationRepository.save(createdOrganization)
  }

  override fun deleteOrganization(organizationId: String) {
    val organization = getVerifiedOrganization(organizationId, PERMISSION_DELETE)
    organizationRepository.delete(organization)
    this.eventPublisher.publishEvent(OrganizationUnregistered(this, organizationId))
  }

  override fun updateOrganization(
      organizationId: String,
      organizationUpdateRequest: OrganizationUpdateRequest
  ): Organization {
    val existingOrganization = getVerifiedOrganization(organizationId, PERMISSION_WRITE)
    var hasChanged = false

    if (organizationUpdateRequest.name != null &&
        organizationUpdateRequest.name != existingOrganization.name) {
      existingOrganization.name = organizationUpdateRequest.name
      hasChanged = true
    }

    return if (hasChanged) {
      organizationRepository.save(existingOrganization)
    } else {
      existingOrganization
    }
  }

  override fun listPermissions(): List<ComponentRolePermissions> {
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

  override fun updateOrganizationDefaultSecurity(
      organizationId: String,
      organizationRole: OrganizationRole
  ): OrganizationSecurity {
    val organization = getVerifiedOrganization(organizationId, PERMISSION_WRITE_SECURITY)
    val rbacSecurity = csmRbac.setDefault(
      organization.security.toGenericSecurity(organizationId), organizationRole.role)
    organization.security = rbacSecurity.toResourceSecurity()
    organizationRepository.save(organization)
    return organization.security
  }

  override fun getOrganizationAccessControl(
      organizationId: String,
      identityId: String
  ): OrganizationAccessControl {
    val organization = getVerifiedOrganization(organizationId, PERMISSION_READ_SECURITY)
    val rbacAccessControl = csmRbac.getAccessControl(
      organization.security.toGenericSecurity(organizationId), identityId)
    return OrganizationAccessControl(id = rbacAccessControl.id, role = rbacAccessControl.role)
  }

  override fun createOrganizationAccessControl(
      organizationId: String,
      organizationAccessControl: OrganizationAccessControl
  ): OrganizationAccessControl {
    val organization = getVerifiedOrganization(organizationId, PERMISSION_WRITE_SECURITY)

    val users = listOrganizationSecurityUsers(organizationId)
    if (users.contains(organizationAccessControl.id)) {
      throw IllegalArgumentException("User is already in this Organization security")
    }

    val rbacSecurity =
        csmRbac.setUserRole(
            organization.security.toGenericSecurity(
              organizationId), organizationAccessControl.id, organizationAccessControl.role)
    organization.security = rbacSecurity.toResourceSecurity()
    organizationRepository.save(organization)
    val rbacAccessControl =
        csmRbac.getAccessControl(organization.security.toGenericSecurity(
          organizationId), organizationAccessControl.id)
    return OrganizationAccessControl(id = rbacAccessControl.id, role = rbacAccessControl.role)
  }

  override fun updateOrganizationAccessControl(
      organizationId: String,
      identityId: String,
      organizationRole: OrganizationRole
  ): OrganizationAccessControl {
    val organization = getVerifiedOrganization(organizationId, PERMISSION_WRITE_SECURITY)
    csmRbac.checkUserExists(
        organization.security.toGenericSecurity(organizationId),
        identityId,
        "User '$identityId' not found in organization $organizationId")
    val rbacSecurity =
        csmRbac.setUserRole(organization.security.toGenericSecurity(
          organizationId), identityId, organizationRole.role)
    organization.security = rbacSecurity.toResourceSecurity()
    organizationRepository.save(organization)
    val rbacAccessControl = csmRbac.getAccessControl(
      organization.security.toGenericSecurity(organizationId), identityId)
    return OrganizationAccessControl(id = rbacAccessControl.id, role = rbacAccessControl.role)
  }

  override fun deleteOrganizationAccessControl(organizationId: String, identityId: String) {
    val organization = getVerifiedOrganization(organizationId, PERMISSION_WRITE_SECURITY)
    val rbacSecurity = csmRbac.removeUser(organization.security.toGenericSecurity(organizationId), identityId)
    organization.security = rbacSecurity.toResourceSecurity()
    organizationRepository.save(organization)
  }

  override fun listOrganizationSecurityUsers(organizationId: String): List<String> {
    val organization = getVerifiedOrganization(organizationId, PERMISSION_READ_SECURITY)
    return csmRbac.getUsers(organization.security.toGenericSecurity(organizationId))
  }

  override fun getVerifiedOrganization(
      organizationId: String,
      requiredPermission: String
  ): Organization {
    val organization =
        organizationRepository.findByIdOrNull(organizationId)
            ?: throw CsmResourceNotFoundException("Organization $organizationId does not exist!")
    csmRbac.verify(organization.security.toGenericSecurity(organizationId), requiredPermission)
    return organization
  }

  override fun getVerifiedOrganization(
      organizationId: String,
      requiredPermissions: List<String>
  ): Organization {
    val organization = getVerifiedOrganization(organizationId)
    requiredPermissions.forEach { csmRbac.verify(organization.security.toGenericSecurity(organizationId), it) }
    return organization
  }

  fun updateSecurityVisibility(organization: Organization): Organization {
    if (csmRbac.check(organization.security.toGenericSecurity(organization.id), PERMISSION_READ_SECURITY).not()) {
      val username = getCurrentAccountIdentifier(csmPlatformProperties)
      val retrievedAC = organization.security.accessControlList.firstOrNull { it.id == username }
      return if (retrievedAC != null) {
        organization.copy(
            security =
                OrganizationSecurity(
                    default = organization.security.default,
                    accessControlList = mutableListOf(retrievedAC)))
      } else {
        organization.copy(
            security =
                OrganizationSecurity(
                    default = organization.security.default, accessControlList = mutableListOf()))
      }
    }
    return organization
  }
}

fun OrganizationSecurity?.toGenericSecurity(organizationId: String) = RbacSecurity(
      organizationId,
    this?.default ?: ROLE_NONE,
      this?.accessControlList?.map { RbacAccessControl(it.id, it.role) }?.toMutableList() ?: mutableListOf()
  )

fun RbacSecurity.toResourceSecurity() =
      OrganizationSecurity(
          this.default,
          this.accessControlList
              .map { OrganizationAccessControl(it.id, it.role) }
              .toMutableList())
