// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.organization.azure

import com.azure.cosmos.models.CosmosContainerProperties
import com.azure.cosmos.models.CosmosQueryRequestOptions
import com.azure.cosmos.models.SqlParameter
import com.azure.cosmos.models.SqlQuerySpec
import com.cosmotech.api.azure.cosmosdb.ext.findAll
import com.cosmotech.api.azure.cosmosdb.ext.findByIdOrThrow
import com.cosmotech.api.azure.cosmosdb.service.CsmCosmosDBService
import com.cosmotech.api.events.OrganizationRegistered
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
import com.cosmotech.api.rbac.getScenarioRolesDefinition
import com.cosmotech.api.rbac.model.RbacAccessControl
import com.cosmotech.api.rbac.model.RbacSecurity
import com.cosmotech.api.utils.changed
import com.cosmotech.api.utils.compareToAndMutateIfNeeded
import com.cosmotech.api.utils.getCurrentAccountIdentifier
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.api.utils.toDomain
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.domain.ComponentRolePermissions
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationRole
import com.cosmotech.organization.domain.OrganizationSecurity
import com.cosmotech.organization.domain.OrganizationService
import com.cosmotech.organization.domain.OrganizationServices
import com.fasterxml.jackson.databind.JsonNode
import javax.annotation.PostConstruct
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["csm.platform.vendor"], havingValue = "azure", matchIfMissing = true)
@Suppress("TooManyFunctions")
class OrganizationServiceImpl(private val csmRbac: CsmRbac, private val csmAdmin: CsmAdmin) :
    CsmCosmosDBService(), OrganizationApiService {

  private lateinit var coreOrganizationContainer: String

  @PostConstruct
  fun initService() {
    this.coreOrganizationContainer =
        csmPlatformProperties.azure!!.cosmos.coreDatabase.organizations.container
    cosmosCoreDatabase.createContainerIfNotExists(
        CosmosContainerProperties(coreOrganizationContainer, "/id"))
  }

  override fun findAllOrganizations(): List<Organization> {
    val isAdmin = csmAdmin.verifyCurrentRolesAdmin()
    if (isAdmin || !this.csmPlatformProperties.rbac.enabled) {
      return cosmosTemplate.findAll(coreOrganizationContainer)
    }
    val currentUser = getCurrentAccountIdentifier(this.csmPlatformProperties)
    return cosmosCoreDatabase
        .getContainer(this.coreOrganizationContainer)
        .queryItems(
            SqlQuerySpec(
                "SELECT * FROM c WHERE ARRAY_CONTAINS(c.security.accessControlList, {id: @ACL_USER}, true) " +
                    "OR c.security.default NOT LIKE 'none'",
                SqlParameter("@ACL_USER", currentUser)),
            CosmosQueryRequestOptions(),
            // It would be much better to specify the Domain Type right away and
            // avoid the map operation, but we can't due
            // to the lack of customization of the Cosmos Client Object Mapper, as reported here :
            // https://github.com/Azure/azure-sdk-for-java/issues/12269
            JsonNode::class.java)
        .mapNotNull { it.toDomain<Organization>() }
        .toList()
  }

  override fun findOrganizationById(organizationId: String): Organization {
    val organization: Organization =
        cosmosTemplate.findByIdOrThrow(coreOrganizationContainer, organizationId)
    csmRbac.verify(organization.getRbac(), PERMISSION_READ)
    return organization
  }

  override fun registerOrganization(organization: Organization): Organization {
    logger.trace("Registering organization : $organization")

    if (organization.name.isNullOrBlank()) {
      throw IllegalArgumentException("Organization name must not be null or blank")
    }

    val newOrganizationId = idGenerator.generate("organization")

    var organizationSecurity = organization.security
    if (organizationSecurity == null) {
      organizationSecurity = initSecurity(getCurrentAccountIdentifier(this.csmPlatformProperties))
    }

    val organizationRegistered =
        cosmosTemplate.insert(
            coreOrganizationContainer,
            organization.copy(
                id = newOrganizationId,
                ownerId = getCurrentAuthenticatedUserName(csmPlatformProperties),
                security = organizationSecurity))

    val organizationId =
        organizationRegistered.id
            ?: throw IllegalStateException(
                "No ID returned for organization registered: $organizationRegistered")

    this.eventPublisher.publishEvent(OrganizationRegistered(this, organizationId))

    return organizationRegistered
  }

  override fun unregisterOrganization(organizationId: String) {
    val organization = findOrganizationById(organizationId)
    csmRbac.verify(organization.getRbac(), PERMISSION_DELETE)

    cosmosTemplate.deleteEntity(coreOrganizationContainer, organization)

    this.eventPublisher.publishEvent(OrganizationUnregistered(this, organizationId))
  }

  override fun updateOrganization(
      organizationId: String,
      organization: Organization
  ): Organization {
    val existingOrganization = findOrganizationById(organizationId)
    csmRbac.verify(existingOrganization.getRbac(), PERMISSION_WRITE)
    var hasChanged = false

    if (organization.name != null && organization.changed(existingOrganization) { name }) {
      existingOrganization.name = organization.name
      hasChanged = true
    }
    if (organization.services != null && organization.changed(existingOrganization) { services }) {
      existingOrganization.services = organization.services
      hasChanged = true
    }
    if (organization.security != null && existingOrganization.security == null) {
      if (csmRbac.isAdmin(organization.getRbac(), getCommonRolesDefinition())) {
        existingOrganization.security = organization.security
        hasChanged = true
      } else {
        logger.warn(
            "Security cannot by updated directly without admin permissions for ${organization.id}")
      }
    }
    return if (hasChanged) {
      cosmosTemplate.upsertAndReturnEntity(coreOrganizationContainer, existingOrganization)
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

  private fun updateOrganizationServiceByOrganizationId(
      organizationId: String,
      organizationService: OrganizationService,
      memberAccessBlock: OrganizationServices.() -> OrganizationService?
  ): OrganizationService {
    val existingOrganization = findOrganizationById(organizationId)
    csmRbac.verify(existingOrganization.getRbac(), PERMISSION_WRITE)
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
    return if (hasChanged) {
      //      existingServices.existingOrganizationService = existingOrganizationService
      existingOrganization.services = existingServices
      cosmosTemplate.upsert(coreOrganizationContainer, existingOrganization)
      existingOrganizationService
    } else {
      existingOrganizationService
    }
  }

  override fun updateTenantCredentialsByOrganizationId(
      organizationId: String,
      requestBody: Map<String, Any>
  ): Map<String, Any> {
    val existingOrganization = findOrganizationById(organizationId)
    csmRbac.verify(existingOrganization.getRbac(), PERMISSION_WRITE)
    if (requestBody.isEmpty()) {
      return requestBody
    }
    val existingServices = existingOrganization.services ?: OrganizationServices()
    val existingTenantCredentials = existingServices.tenantCredentials?.toMutableMap()
    existingTenantCredentials?.putAll(requestBody)

    existingServices.tenantCredentials = existingTenantCredentials
    existingOrganization.services = existingServices

    cosmosTemplate.upsert(coreOrganizationContainer, existingOrganization)
    return existingTenantCredentials?.toMap() ?: mapOf()
  }

  override fun getAllPermissions(): List<ComponentRolePermissions> {
    return getAllRolesDefinition().mapNotNull { ComponentRolePermissions(it.key, it.value) }
  }

  override fun getOrganizationPermissions(organizationId: String, role: String): List<String> {
    return com.cosmotech.api.rbac.getPermissions(role, getScenarioRolesDefinition())
  }

  override fun getOrganizationSecurity(organizationId: String): OrganizationSecurity {
    val organization = findOrganizationById(organizationId)
    csmRbac.verify(organization.getRbac(), PERMISSION_READ_SECURITY)
    return organization.security
        ?: throw CsmResourceNotFoundException("RBAC not defined for ${organization.id}")
  }

  override fun setOrganizationDefaultSecurity(
      organizationId: String,
      organizationRole: OrganizationRole
  ): OrganizationSecurity {
    val organization = findOrganizationById(organizationId)
    csmRbac.verify(organization.getRbac(), PERMISSION_WRITE_SECURITY)
    val rbacSecurity = csmRbac.setDefault(organization.getRbac(), organizationRole.role)
    organization.setRbac(rbacSecurity)
    cosmosTemplate.upsertAndReturnEntity(coreOrganizationContainer, organization)
    return organization.security as OrganizationSecurity
  }

  override fun getOrganizationAccessControl(
      organizationId: String,
      identityId: String
  ): OrganizationAccessControl {
    val organization = findOrganizationById(organizationId)
    csmRbac.verify(organization.getRbac(), PERMISSION_READ_SECURITY)
    val rbacAccessControl = csmRbac.getAccessControl(organization.getRbac(), identityId)
    return OrganizationAccessControl(rbacAccessControl.id, rbacAccessControl.role)
  }

  override fun addOrganizationAccessControl(
      organizationId: String,
      organizationAccessControl: OrganizationAccessControl
  ): OrganizationAccessControl {
    val organization = findOrganizationById(organizationId)
    csmRbac.verify(organization.getRbac(), PERMISSION_WRITE_SECURITY)
    val rbacSecurity =
        csmRbac.setUserRole(
            organization.getRbac(), organizationAccessControl.id, organizationAccessControl.role)
    organization.setRbac(rbacSecurity)
    cosmosTemplate.upsertAndReturnEntity(coreOrganizationContainer, organization)
    val rbacAccessControl =
        csmRbac.getAccessControl(organization.getRbac(), organizationAccessControl.id)
    return OrganizationAccessControl(rbacAccessControl.id, rbacAccessControl.role)
  }

  override fun updateOrganizationAccessControl(
      organizationId: String,
      identityId: String,
      organizationRole: OrganizationRole
  ): OrganizationAccessControl {
    val organization = findOrganizationById(organizationId)
    csmRbac.verify(organization.getRbac(), PERMISSION_WRITE_SECURITY)
    csmRbac.checkUserExists(
        organization.getRbac(),
        identityId,
        "User '$identityId' not found in workspace $organizationId")
    val rbacSecurity =
        csmRbac.setUserRole(organization.getRbac(), identityId, organizationRole.role)
    organization.setRbac(rbacSecurity)
    cosmosTemplate.upsertAndReturnEntity(coreOrganizationContainer, organization)
    val rbacAccessControl = csmRbac.getAccessControl(organization.getRbac(), identityId)
    return OrganizationAccessControl(rbacAccessControl.id, rbacAccessControl.role)
  }

  override fun removeOrganizationAccessControl(organizationId: String, identityId: String) {
    val organization = findOrganizationById(organizationId)
    csmRbac.verify(organization.getRbac(), PERMISSION_WRITE_SECURITY)
    val rbacSecurity = csmRbac.removeUser(organization.getRbac(), identityId)
    organization.setRbac(rbacSecurity)
    cosmosTemplate.upsertAndReturnEntity(coreOrganizationContainer, organization)
  }

  override fun getOrganizationSecurityUsers(organizationId: String): List<String> {
    val organization = findOrganizationById(organizationId)
    csmRbac.verify(organization.getRbac(), PERMISSION_READ_SECURITY)
    return csmRbac.getUsers(organization.getRbac())
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
          rbacSecurity
              .accessControlList
              .map { OrganizationAccessControl(it.id, it.role) }
              .toMutableList())
}
