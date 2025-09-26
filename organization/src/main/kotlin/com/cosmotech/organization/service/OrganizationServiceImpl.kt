// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.organization.service

import com.cosmotech.common.CsmPhoenixService
import com.cosmotech.common.events.OrganizationUnregistered
import com.cosmotech.common.exceptions.CsmResourceNotFoundException
import com.cosmotech.common.rbac.CsmAdmin
import com.cosmotech.common.rbac.CsmRbac
import com.cosmotech.common.rbac.PERMISSION_DELETE
import com.cosmotech.common.rbac.PERMISSION_READ
import com.cosmotech.common.rbac.PERMISSION_READ_SECURITY
import com.cosmotech.common.rbac.PERMISSION_WRITE
import com.cosmotech.common.rbac.PERMISSION_WRITE_SECURITY
import com.cosmotech.common.rbac.ROLE_NONE
import com.cosmotech.common.rbac.getAllRolesDefinition
import com.cosmotech.common.rbac.getCommonRolesDefinition
import com.cosmotech.common.rbac.model.RbacAccessControl
import com.cosmotech.common.rbac.model.RbacSecurity
import com.cosmotech.common.utils.constructPageRequest
import com.cosmotech.common.utils.findAllPaginated
import com.cosmotech.common.utils.getCurrentAccountIdentifier
import com.cosmotech.organization.OrganizationApiServiceInterface
import com.cosmotech.organization.domain.ComponentRolePermissions
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationCreateRequest
import com.cosmotech.organization.domain.OrganizationEditInfo
import com.cosmotech.organization.domain.OrganizationRole
import com.cosmotech.organization.domain.OrganizationSecurity
import com.cosmotech.organization.domain.OrganizationUpdateRequest
import com.cosmotech.organization.repository.OrganizationRepository
import java.time.Instant
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

    require(organizationCreateRequest.name.isNotBlank()) {
      "Organization name must not be null or blank"
    }

    val organizationId = idGenerator.generate("organization")
    val now = Instant.now().toEpochMilli()
    val security =
        csmRbac
            .initSecurity(organizationCreateRequest.security.toGenericSecurity(organizationId))
            .toResourceSecurity()
    val createdOrganization =
        Organization(
            id = organizationId,
            name = organizationCreateRequest.name,
            createInfo =
                OrganizationEditInfo(
                    timestamp = now, userId = getCurrentAccountIdentifier(csmPlatformProperties)),
            updateInfo =
                OrganizationEditInfo(
                    timestamp = now, userId = getCurrentAccountIdentifier(csmPlatformProperties)),
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
      existingOrganization.name = organizationUpdateRequest.name!!
      hasChanged = true
    }

    return if (hasChanged) {
      save(existingOrganization)
    } else {
      existingOrganization
    }
  }

  override fun listPermissions(): List<ComponentRolePermissions> {
    return getAllRolesDefinition().mapNotNull { ComponentRolePermissions(it.key, it.value) }
  }

  override fun getOrganizationPermissions(organizationId: String, role: String): List<String> {
    getVerifiedOrganization(organizationId, PERMISSION_READ_SECURITY)
    return com.cosmotech.common.rbac.getPermissions(role, getCommonRolesDefinition())
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
    val rbacSecurity =
        csmRbac.setDefault(
            organization.security.toGenericSecurity(organizationId), organizationRole.role)
    organization.security = rbacSecurity.toResourceSecurity()
    save(organization)
    return organization.security
  }

  override fun getOrganizationAccessControl(
      organizationId: String,
      identityId: String
  ): OrganizationAccessControl {
    val organization = getVerifiedOrganization(organizationId, PERMISSION_READ_SECURITY)
    val rbacAccessControl =
        csmRbac.getAccessControl(
            organization.security.toGenericSecurity(organizationId), identityId)
    return OrganizationAccessControl(id = rbacAccessControl.id, role = rbacAccessControl.role)
  }

  override fun createOrganizationAccessControl(
      organizationId: String,
      organizationAccessControl: OrganizationAccessControl
  ): OrganizationAccessControl {
    val organization = getVerifiedOrganization(organizationId, PERMISSION_WRITE_SECURITY)

    val users = listOrganizationSecurityUsers(organizationId)
    require(!users.contains(organizationAccessControl.id)) {
      "User is already in this Organization security"
    }

    val rbacSecurity =
        csmRbac.setUserRole(
            organization.security.toGenericSecurity(organizationId),
            organizationAccessControl.id,
            organizationAccessControl.role)
    organization.security = rbacSecurity.toResourceSecurity()
    save(organization)
    val rbacAccessControl =
        csmRbac.getAccessControl(
            organization.security.toGenericSecurity(organizationId), organizationAccessControl.id)
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
        csmRbac.setUserRole(
            organization.security.toGenericSecurity(organizationId),
            identityId,
            organizationRole.role)
    organization.security = rbacSecurity.toResourceSecurity()
    save(organization)
    val rbacAccessControl =
        csmRbac.getAccessControl(
            organization.security.toGenericSecurity(organizationId), identityId)
    return OrganizationAccessControl(id = rbacAccessControl.id, role = rbacAccessControl.role)
  }

  override fun deleteOrganizationAccessControl(organizationId: String, identityId: String) {
    val organization = getVerifiedOrganization(organizationId, PERMISSION_WRITE_SECURITY)
    val rbacSecurity =
        csmRbac.removeUser(organization.security.toGenericSecurity(organizationId), identityId)
    organization.security = rbacSecurity.toResourceSecurity()
    save(organization)
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
    requiredPermissions.forEach {
      csmRbac.verify(organization.security.toGenericSecurity(organizationId), it)
    }
    return organization
  }

  fun updateSecurityVisibility(organization: Organization): Organization {
    if (csmRbac
        .check(organization.security.toGenericSecurity(organization.id), PERMISSION_READ_SECURITY)
        .not()) {
      val username = getCurrentAccountIdentifier(csmPlatformProperties)
      val retrievedAC = organization.security.accessControlList.firstOrNull { it.id == username }

      val accessControlList =
          if (retrievedAC != null) {
            mutableListOf(retrievedAC)
          } else {
            mutableListOf()
          }
      return organization.copy(
          security =
              OrganizationSecurity(
                  default = organization.security.default, accessControlList = accessControlList))
    }
    return organization
  }

  fun save(organization: Organization): Organization {
    organization.updateInfo =
        OrganizationEditInfo(
            timestamp = Instant.now().toEpochMilli(),
            userId = getCurrentAccountIdentifier(csmPlatformProperties))
    return organizationRepository.save(organization)
  }
}

fun OrganizationSecurity?.toGenericSecurity(organizationId: String) =
    RbacSecurity(
        organizationId,
        this?.default ?: ROLE_NONE,
        this?.accessControlList?.map { RbacAccessControl(it.id, it.role) }?.toMutableList()
            ?: mutableListOf())

fun RbacSecurity.toResourceSecurity() =
    OrganizationSecurity(
        this.default,
        this.accessControlList.map { OrganizationAccessControl(it.id, it.role) }.toMutableList())
