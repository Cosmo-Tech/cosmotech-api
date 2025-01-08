package com.cosmotech.organization.service

import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.rbac.CsmRbac
import com.cosmotech.api.rbac.PERMISSION_READ_SECURITY
import com.cosmotech.api.rbac.PERMISSION_WRITE_SECURITY
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationRole
import com.cosmotech.organization.domain.OrganizationSecurity
import com.cosmotech.organization.repository.OrganizationRepository
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service

/**
 * Service handling organization security operations
 */
@Service
class OrganizationSecurityService(
    private val csmRbac: CsmRbac,
    private val organizationRepository: OrganizationRepository,
    private val organizationVerificationService: OrganizationVerificationService
) {

    /**
     * Get organization security settings
     * @param organizationId The organization ID
     * @return OrganizationSecurity The security settings
     * @throws CsmResourceNotFoundException if RBAC is not defined
     */
    fun getOrganizationSecurity(organizationId: String): OrganizationSecurity {
        val organization = organizationVerificationService.getVerifiedOrganization(organizationId, PERMISSION_READ_SECURITY)
        return organization.security
            ?: throw CsmResourceNotFoundException(OrganizationConstants.RBAC_NOT_DEFINED.format(organization.id))
    }

    /**
     * Set default security role for organization
     * @param organizationId The organization ID
     * @param organizationRole The default role to set
     * @return OrganizationSecurity The updated security settings
     */
    fun setOrganizationDefaultSecurity(
        organizationId: String,
        organizationRole: OrganizationRole
    ): OrganizationSecurity {
        val organization = organizationVerificationService.getVerifiedOrganization(organizationId, PERMISSION_WRITE_SECURITY)
        val rbacSecurity = csmRbac.setDefault(organization.getRbac(), organizationRole.role)
        organization.setRbac(rbacSecurity)
        organizationRepository.save(organization)
        return organization.security as OrganizationSecurity
    }

    /**
     * Get access control for a specific user
     * @param organizationId The organization ID
     * @param identityId The user identity ID
     * @return OrganizationAccessControl The user's access control settings
     */
    fun getOrganizationAccessControl(
        organizationId: String,
        identityId: String
    ): OrganizationAccessControl {
        val organization = organizationVerificationService.getVerifiedOrganization(organizationId, PERMISSION_READ_SECURITY)
        val rbacAccessControl = csmRbac.getAccessControl(organization.getRbac(), identityId)
        return OrganizationAccessControl(rbacAccessControl.id, rbacAccessControl.role)
    }

    /**
     * Add new access control for a user
     * @param organizationId The organization ID
     * @param organizationAccessControl The access control to add
     * @return OrganizationAccessControl The added access control
     * @throws IllegalArgumentException if user already exists in security
     */
    fun addOrganizationAccessControl(
        organizationId: String,
        organizationAccessControl: OrganizationAccessControl
    ): OrganizationAccessControl {
        val organization = organizationVerificationService.getVerifiedOrganization(organizationId, PERMISSION_WRITE_SECURITY)

        val users = getOrganizationSecurityUsers(organizationId)
        if (users.contains(organizationAccessControl.id)) {
            throw IllegalArgumentException(OrganizationConstants.USER_ALREADY_EXISTS)
        }

        val rbacSecurity =
            csmRbac.setUserRole(
                organization.getRbac(),
                organizationAccessControl.id,
                organizationAccessControl.role
            )
        organization.setRbac(rbacSecurity)
        organizationRepository.save(organization)
        val rbacAccessControl =
            csmRbac.getAccessControl(organization.getRbac(), organizationAccessControl.id)
        return OrganizationAccessControl(rbacAccessControl.id, rbacAccessControl.role)
    }

    /**
     * Update access control for a user
     * @param organizationId The organization ID
     * @param identityId The user identity ID
     * @param organizationRole The new role to set
     * @return OrganizationAccessControl The updated access control
     */
    fun updateOrganizationAccessControl(
        organizationId: String,
        identityId: String,
        organizationRole: OrganizationRole
    ): OrganizationAccessControl {
        val organization = organizationVerificationService.getVerifiedOrganization(organizationId, PERMISSION_WRITE_SECURITY)
        csmRbac.checkUserExists(
            organization.getRbac(),
            identityId,
            OrganizationConstants.USER_NOT_FOUND.format(identityId, organizationId)
        )
        val rbacSecurity =
            csmRbac.setUserRole(organization.getRbac(), identityId, organizationRole.role)
        organization.setRbac(rbacSecurity)
        organizationRepository.save(organization)
        val rbacAccessControl = csmRbac.getAccessControl(organization.getRbac(), identityId)
        return OrganizationAccessControl(rbacAccessControl.id, rbacAccessControl.role)
    }

    /**
     * Remove access control for a user
     * @param organizationId The organization ID
     * @param identityId The user identity ID to remove
     */
    fun removeOrganizationAccessControl(organizationId: String, identityId: String) {
        val organization = organizationVerificationService.getVerifiedOrganization(organizationId, PERMISSION_WRITE_SECURITY)
        val rbacSecurity = csmRbac.removeUser(organization.getRbac(), identityId)
        organization.setRbac(rbacSecurity)
        organizationRepository.save(organization)
    }

    /**
     * Get list of users with security settings in organization
     * @param organizationId The organization ID
     * @return List<String> List of user IDs
     */
    fun getOrganizationSecurityUsers(organizationId: String): List<String> {
        val organization = organizationVerificationService.getVerifiedOrganization(organizationId, PERMISSION_READ_SECURITY)
        return csmRbac.getUsers(organization.getRbac())
    }
}
