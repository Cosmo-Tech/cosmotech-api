package com.cosmotech.organization

import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.domain.Organization

interface EnhancedOrganizationApiService: OrganizationApiService {

    fun verifyPermissionsAndReturnOrganization(
        organizationId: String,
        requiredPermissions: List<String>
    ): Organization

}