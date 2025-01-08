package com.cosmotech.organization.service

import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.organization.domain.Organization

/**
 * Interface for Organization service operations
 */
interface IOrganizationService {
    /**
     * Get a verified organization by ID
     * @param organizationId The organization ID
     * @param requiredPermission The required permission to verify
     * @return Organization The verified organization
     */
    fun getVerifiedOrganization(organizationId: String, requiredPermission: String): Organization

    /**
     * Get a verified organization by ID with multiple required permissions
     * @param organizationId The organization ID
     * @param requiredPermissions List of required permissions to verify
     * @return Organization The verified organization
     */
    fun getVerifiedOrganization(organizationId: String, requiredPermissions: List<String>): Organization
}
