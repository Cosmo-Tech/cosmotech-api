package com.cosmotech.organization.service

import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.rbac.model.RbacAccessControl
import com.cosmotech.api.rbac.model.RbacSecurity
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationSecurity

/**
 * Extension function to convert Organization security to RbacSecurity
 * @return RbacSecurity object representing the organization's security settings
 */
fun Organization.getRbac(): RbacSecurity {
    return RbacSecurity(
        this.id,
        this.security?.default ?: ROLE_NONE,
        this.security?.accessControlList?.map { RbacAccessControl(it.id, it.role) }?.toMutableList()
            ?: mutableListOf()
    )
}

/**
 * Extension function to set organization security from RbacSecurity
 * @param rbacSecurity The RbacSecurity object to set
 */
fun Organization.setRbac(rbacSecurity: RbacSecurity) {
    this.security =
        OrganizationSecurity(
            rbacSecurity.default,
            rbacSecurity.accessControlList
                .map { OrganizationAccessControl(it.id, it.role) }
                .toMutableList()
        )
}

/**
 * Extension function to create a copy of Organization with filtered security visibility
 * @param username The username to filter security visibility for
 * @param hasReadSecurityPermission Whether the user has permission to read security settings
 * @return Organization with filtered security visibility
 */
fun Organization.withFilteredSecurityVisibility(username: String, hasReadSecurityPermission: Boolean): Organization {
    if (!hasReadSecurityPermission) {
        val retrievedAC = this.security?.accessControlList?.firstOrNull { it.id == username }
        return this.copy(
            security = OrganizationSecurity(
                default = this.security?.default ?: ROLE_NONE,
                accessControlList = retrievedAC?.let { mutableListOf(it) } ?: mutableListOf()
            )
        )
    }
    return this
}
