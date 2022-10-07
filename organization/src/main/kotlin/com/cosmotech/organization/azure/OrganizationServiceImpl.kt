// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.organization.azure

import com.azure.cosmos.models.CosmosContainerProperties
import com.azure.cosmos.models.CosmosQueryRequestOptions
import com.azure.cosmos.models.SqlParameter
import com.azure.cosmos.models.SqlQuerySpec
import com.cosmotech.api.azure.CsmAzureService
import com.cosmotech.api.azure.findByIdOrThrow
import com.cosmotech.api.events.OrganizationRegistered
import com.cosmotech.api.events.OrganizationUnregistered
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.rbac.CsmRbac
import com.cosmotech.api.rbac.PERMISSION_WRITE
import com.cosmotech.api.rbac.PERMISSION_WRITE_SECURITY
import com.cosmotech.api.rbac.PERMISSION_READ
import com.cosmotech.api.rbac.PERMISSION_READ_SECURITY
import com.cosmotech.api.rbac.ROLE_ADMIN
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.rbac.getAllRolesDefinition
import com.cosmotech.api.rbac.getScenarioRolesDefinition
import com.cosmotech.api.rbac.model.RbacAccessControl
import com.cosmotech.api.utils.changed
import com.cosmotech.api.utils.compareToAndMutateIfNeeded
import com.cosmotech.api.utils.getCurrentAuthenticatedMail
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.api.utils.toDomain
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.domain.ComponentRolePermissions
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
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
internal class OrganizationServiceImpl(private val csmRbac: CsmRbac) :
    CsmAzureService(), OrganizationApiService {

  private lateinit var coreOrganizationContainer: String

  @PostConstruct
  fun initService() {
    this.coreOrganizationContainer =
        csmPlatformProperties.azure!!.cosmos.coreDatabase.organizations.container
    cosmosCoreDatabase.createContainerIfNotExists(
        CosmosContainerProperties(coreOrganizationContainer, "/id"))
  }

  override fun findAllOrganizations(): List<Organization> {
    val currentUser = getCurrentAuthenticatedMail(this.csmPlatformProperties)
    return cosmosCoreDatabase
        .getContainer(this.coreOrganizationContainer)
        .queryItems(
            SqlQuerySpec(
                "SELECT * FROM c " +
                    "WHERE ARRAY_CONTAINS(c.security.accessControlList, { id: @ACL_USER}, true)" +
                    " OR NOT IS_DEFINED(c.security)" +
                    " OR ARRAY_LENGTH(c.security.default) > 0",
                listOf(SqlParameter("@ACL_USER", currentUser))),
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
    csmRbac.verify(organization.security, PERMISSION_READ)
    return organization
  }

  override fun registerOrganization(organization: Organization): Organization {
    logger.trace("Registering organization : $organization")

    if (organization.name.isNullOrBlank()) {
      throw IllegalArgumentException("Organization name must not be null or blank")
    }

    val newOrganizationId = idGenerator.generate("organization")
    val currentUser = getCurrentAuthenticatedMail(this.csmPlatformProperties)

    val organizationRegistered =
        cosmosTemplate.insert(
            coreOrganizationContainer,
            organization.copy(
                id = newOrganizationId,
                ownerId = getCurrentAuthenticatedUserName(),
                security = organization.security ?: initSecurity(currentUser)))

    val organizationId =
        organizationRegistered.id
            ?: throw IllegalStateException(
                "No ID returned for organization registered: $organizationRegistered")

    this.eventPublisher.publishEvent(OrganizationRegistered(this, organizationId))

    return organizationRegistered
  }

  override fun unregisterOrganization(organizationId: String) {
    val organization = findOrganizationById(organizationId)
    csmRbac.verify(organization.security, PERMISSION_WRITE)
    if (organization.ownerId != getCurrentAuthenticatedUserName()) {
      // TODO Only the owner or an admin should be able to perform this operation
      throw CsmAccessForbiddenException("You are not allowed to delete this Resource")
    }

    cosmosTemplate.deleteEntity(coreOrganizationContainer, organization)

    this.eventPublisher.publishEvent(OrganizationUnregistered(this, organizationId))

    // TODO Handle rollbacks in case of errors
  }

  override fun updateOrganization(
      organizationId: String,
      organization: Organization
  ): Organization {
    val existingOrganization = findOrganizationById(organizationId)
    csmRbac.verify(existingOrganization.security, PERMISSION_WRITE)
    var hasChanged = false

    if (organization.name != null && organization.changed(existingOrganization) { name }) {
      existingOrganization.name = organization.name
      hasChanged = true
    }
    if (organization.services != null && organization.changed(existingOrganization) { services }) {
      existingOrganization.services = organization.services
      hasChanged = true
    }
    if (organization.security != null && organization.changed(existingOrganization) { security }) {
      logger.warn("Security cannot be changed in updateOrganization for $organizationId")
    }
    val responseEntity: Organization
    responseEntity =
        if (hasChanged) {
          cosmosTemplate.upsertAndReturnEntity(coreOrganizationContainer, existingOrganization)
        } else {
          existingOrganization
        }

    return responseEntity
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
    csmRbac.verify(existingOrganization.security, PERMISSION_WRITE)
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
    csmRbac.verify(existingOrganization.security, PERMISSION_WRITE)
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
    csmRbac.verify(organization.security, PERMISSION_READ_SECURITY)
    return organization.security as OrganizationSecurity
  }

  override fun setOrganizationDefaultSecurity(
      organizationId: String,
      organizationRole: String
  ): OrganizationSecurity {
    val organization = findOrganizationById(organizationId)
    csmRbac.verify(organization.security, PERMISSION_WRITE_SECURITY)
    csmRbac.setDefault(organization.security, organizationRole)
    this.updateOrganization(organizationId, organization)
    return organization.security as OrganizationSecurity
  }

  override fun getOrganizationAccessControl(
      organizationId: String,
      identityId: String
  ): OrganizationAccessControl {
    val organization = findOrganizationById(organizationId)
    csmRbac.verify(organization.security, PERMISSION_READ_SECURITY)
    return csmRbac.getAccessControl(organization.security, identityId) as OrganizationAccessControl
  }

  override fun addOrganizationAccessControl(
      organizationId: String,
      organizationAccessControl: OrganizationAccessControl
  ): OrganizationAccessControl {
    val organization = findOrganizationById(organizationId)
    csmRbac.verify(organization.security, PERMISSION_WRITE_SECURITY)
    csmRbac.setUserRole(
        organization.security, organizationAccessControl.id, organizationAccessControl.role)
    this.updateOrganization(organizationId, organization)
    return csmRbac.getAccessControl(organization.security, organizationAccessControl.id) as
        OrganizationAccessControl
  }

  override fun removeOrganizationAccessControl(organizationId: String, identityId: String) {
    val organization = findOrganizationById(organizationId)
    csmRbac.verify(organization.security, PERMISSION_WRITE_SECURITY)
    csmRbac.removeUser(organization.security, identityId)
    this.updateOrganization(organizationId, organization)
  }

  override fun getOrganizationSecurityUsers(organizationId: String): List<String> {
    val organization = findOrganizationById(organizationId)
    csmRbac.verify(organization.security, PERMISSION_READ_SECURITY)
    return csmRbac.getUsers(organization.security)
  }

  private fun initSecurity(userId: String): OrganizationSecurity {
    return OrganizationSecurity(
        default = ROLE_NONE,
        accessControlList = mutableListOf(RbacAccessControl(userId, ROLE_ADMIN)))
  }
}
