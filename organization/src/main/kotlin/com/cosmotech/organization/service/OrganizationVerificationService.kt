package com.cosmotech.organization.service

import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.rbac.CsmRbac
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.repository.OrganizationRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

/**
 * Service handling organization verification operations
 */
@Service
class OrganizationVerificationService(
    private val csmRbac: CsmRbac,
    private val organizationRepository: OrganizationRepository
) {
    /**
     * Get a verified organization by ID
     * @param organizationId The organization ID
     * @param requiredPermission The required permission to verify
     * @return Organization The verified organization
     * @throws CsmResourceNotFoundException if organization not found
     */
    fun getVerifiedOrganization(organizationId: String, requiredPermission: String): Organization {
        val organization = organizationRepository.findByIdOrNull(organizationId)
            ?: throw CsmResourceNotFoundException(OrganizationConstants.ORGANIZATION_NOT_FOUND.format(organizationId))
        csmRbac.verify(organization.getRbac(), requiredPermission)
        return organization
    }

    /**
     * Get a verified organization by ID with multiple required permissions
     * @param organizationId The organization ID
     * @param requiredPermissions List of required permissions to verify
     * @return Organization The verified organization
     * @throws CsmResourceNotFoundException if organization not found
     */
    fun getVerifiedOrganization(organizationId: String, requiredPermissions: List<String>): Organization {
        val organization = getVerifiedOrganization(organizationId, requiredPermissions.first())
        requiredPermissions.drop(1).forEach { csmRbac.verify(organization.getRbac(), it) }
        return organization
    }
}
