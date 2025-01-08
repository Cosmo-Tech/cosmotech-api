package com.cosmotech.organization.service

/**
 * Constants used in Organization service operations
 */
object OrganizationConstants {
    const val ORGANIZATION_NAME_BLANK_ERROR = "Organization name must not be null or blank"
    const val ORGANIZATION_NOT_FOUND = "Organization %s does not exist!"
    const val USER_ALREADY_EXISTS = "User is already in this Organization security"
    const val USER_NOT_FOUND = "User '%s' not found in organization %s"
    const val RBAC_NOT_DEFINED = "RBAC not defined for %s"
    const val SECURITY_MODIFICATION_WARNING = 
        "Security modification has not been applied to organization %s, please refer to the appropriate security endpoints to perform this maneuver"
}
