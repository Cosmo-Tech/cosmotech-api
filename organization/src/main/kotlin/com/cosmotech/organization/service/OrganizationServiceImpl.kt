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
import com.cosmotech.api.rbac.ROLE_ADMIN
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.rbac.getAllRolesDefinition
import com.cosmotech.api.rbac.getCommonRolesDefinition
import com.cosmotech.api.rbac.model.RbacAccessControl
import com.cosmotech.api.rbac.model.RbacSecurity
import com.cosmotech.api.utils.changed
import com.cosmotech.api.utils.compareToAndMutateIfNeeded
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
import com.cosmotech.organization.domain.OrganizationService
import com.cosmotech.organization.domain.OrganizationServices
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

    return result
  }

  override fun findOrganizationById(organizationId: String): Organization {
    return getVerifiedOrganization(organizationId, PERMISSION_READ)
  }

  override fun registerOrganization(organization: Organization): Organization {
    logger.trace("Registering organization : {}", organization)

    if (organization.name.isNullOrBlank()) {
      throw IllegalArgumentException("Organization name must not be null or blank")
    }

    val newOrganizationId = idGenerator.generate("organization")
    var organizationSecurity = organization.security
    if (organizationSecurity == null) {
      val currentUser = getCurrentAccountIdentifier(this.csmPlatformProperties)
      organizationSecurity = initSecurity(currentUser)
    } else {
      val accessControls = mutableListOf<String>()
      organizationSecurity.accessControlList.forEach {
        if (!accessControls.contains(it.id)) {
          accessControls.add(it.id)
        } else {
          throw IllegalArgumentException("User $it is referenced multiple times in the security")
        }
      }
    }

    return organizationRepository.save(
        organization.copy(
            id = newOrganizationId,
            ownerId = getCurrentAuthenticatedUserName(csmPlatformProperties),
            security = organizationSecurity))
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
    if (organization.services != null && organization.changed(existingOrganization) { services }) {
      existingOrganization.services = organization.services
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

  override fun updateSolutionsContainerRegistryByOrganizationId(
      organizationId: String,
      organizationService: OrganizationService
  ) =
      updateOrganizationServiceByOrganizationId(organizationId, organizationService) {
        solutionsContainerRegistry
      }

  override fun updateStorageByOrganizationId(
      organizationId: String,
      organizationService: OrganizationService
  ) = updateOrganizationServiceByOrganizationId(organizationId, organizationService) { storage }

  override fun updateTenantCredentialsByOrganizationId(
      organizationId: String,
      requestBody: Map<String, Any>
  ): Map<String, Any> {
    val existingOrganization = getVerifiedOrganization(organizationId, PERMISSION_WRITE)
    if (requestBody.isEmpty()) {
      return requestBody
    }
    val existingServices = existingOrganization.services ?: OrganizationServices()
    val existingTenantCredentials =
        existingServices.tenantCredentials?.toMutableMap() ?: mutableMapOf()
    existingTenantCredentials.putAll(requestBody)

    existingServices.tenantCredentials = existingTenantCredentials
    existingOrganization.services = existingServices

    val updatedOrganization = organizationRepository.save(existingOrganization)
    return updatedOrganization.services!!.tenantCredentials!!.toMap()
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
        ?: throw CsmResourceNotFoundException("RBAC not defined for ${organization.id}")
  }

  override fun setOrganizationDefaultSecurity(
      organizationId: String,
      organizationRole: OrganizationRole
  ): OrganizationSecurity {
    val organization = getVerifiedOrganization(organizationId, PERMISSION_WRITE_SECURITY)
    val rbacSecurity = csmRbac.setDefault(organization.getRbac(), organizationRole.role)
    organization.setRbac(rbacSecurity)
    organizationRepository.save(organization)
    return organization.security as OrganizationSecurity
  }

  override fun getOrganizationAccessControl(
      organizationId: String,
      identityId: String
  ): OrganizationAccessControl {
    val organization = getVerifiedOrganization(organizationId, PERMISSION_READ_SECURITY)
    val rbacAccessControl = csmRbac.getAccessControl(organization.getRbac(), identityId)
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
            organization.getRbac(), organizationAccessControl.id, organizationAccessControl.role)
    organization.setRbac(rbacSecurity)
    organizationRepository.save(organization)
    val rbacAccessControl =
        csmRbac.getAccessControl(organization.getRbac(), organizationAccessControl.id)
    return OrganizationAccessControl(rbacAccessControl.id, rbacAccessControl.role)
  }

  override fun updateOrganizationAccessControl(
      organizationId: String,
      identityId: String,
      organizationRole: OrganizationRole
  ): OrganizationAccessControl {
    val organization = getVerifiedOrganization(organizationId, PERMISSION_WRITE_SECURITY)
    csmRbac.checkUserExists(
        organization.getRbac(),
        identityId,
        "User '$identityId' not found in organization $organizationId")
    val rbacSecurity =
        csmRbac.setUserRole(organization.getRbac(), identityId, organizationRole.role)
    organization.setRbac(rbacSecurity)
    organizationRepository.save(organization)
    val rbacAccessControl = csmRbac.getAccessControl(organization.getRbac(), identityId)
    return OrganizationAccessControl(rbacAccessControl.id, rbacAccessControl.role)
  }

  override fun removeOrganizationAccessControl(organizationId: String, identityId: String) {
    val organization = getVerifiedOrganization(organizationId, PERMISSION_WRITE_SECURITY)
    val rbacSecurity = csmRbac.removeUser(organization.getRbac(), identityId)
    organization.setRbac(rbacSecurity)
    organizationRepository.save(organization)
  }

  override fun getOrganizationSecurityUsers(organizationId: String): List<String> {
    val organization = getVerifiedOrganization(organizationId, PERMISSION_READ_SECURITY)
    return csmRbac.getUsers(organization.getRbac())
  }

  override fun getVerifiedOrganization(
      organizationId: String,
      requiredPermission: String
  ): Organization {
    val organization =
        organizationRepository.findByIdOrNull(organizationId)
            ?: throw CsmResourceNotFoundException("Organization $organizationId does not exist!")
    csmRbac.verify(organization.getRbac(), requiredPermission)
    return organization
  }

  override fun getVerifiedOrganization(
      organizationId: String,
      requiredPermissions: List<String>
  ): Organization {
    val organization = getVerifiedOrganization(organizationId)
    requiredPermissions.forEach { csmRbac.verify(organization.getRbac(), it) }
    return organization
  }

  private fun updateOrganizationServiceByOrganizationId(
      organizationId: String,
      organizationService: OrganizationService,
      memberAccessBlock: OrganizationServices.() -> OrganizationService?
  ): OrganizationService {
    val existingOrganization = getVerifiedOrganization(organizationId, PERMISSION_WRITE)
    val existingServices = existingOrganization.services ?: OrganizationServices()
    val existingOrganizationService =
        with(existingServices, memberAccessBlock) ?: OrganizationService()

    var hasChanged =
        existingOrganizationService
            .compareToAndMutateIfNeeded(
                organizationService, excludedFields = arrayOf("credentials"))
            .isNotEmpty()
    if (organizationService.credentials != null) {
      val existingOrganizationServiceCredentials =
          existingOrganizationService.credentials?.toMutableMap() ?: mutableMapOf()
      existingOrganizationServiceCredentials.clear()
      existingOrganizationServiceCredentials.putAll(organizationService.credentials ?: emptyMap())
      hasChanged = true
    }

    if (hasChanged) {
      existingOrganization.services = existingServices
      organizationRepository.save(existingOrganization)
    }

    return existingOrganizationService
  }

  private fun initSecurity(userId: String): OrganizationSecurity {
    return OrganizationSecurity(
        default = ROLE_NONE,
        accessControlList = mutableListOf(OrganizationAccessControl(userId, ROLE_ADMIN)))
  }
}

fun Organization.getRbac(): RbacSecurity {
  return RbacSecurity(
      this.id,
      this.security?.default ?: ROLE_NONE,
      this.security?.accessControlList?.map { RbacAccessControl(it.id, it.role) }?.toMutableList()
          ?: mutableListOf())
}

fun Organization.setRbac(rbacSecurity: RbacSecurity) {
  this.security =
      OrganizationSecurity(
          rbacSecurity.default,
          rbacSecurity.accessControlList
              .map { OrganizationAccessControl(it.id, it.role) }
              .toMutableList())
}
