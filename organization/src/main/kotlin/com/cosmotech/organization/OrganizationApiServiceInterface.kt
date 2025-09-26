// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.organization

import com.cosmotech.common.rbac.PERMISSION_READ
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.domain.Organization

interface OrganizationApiServiceInterface : OrganizationApiService {

  fun getVerifiedOrganization(
      organizationId: String,
      requiredPermission: String = PERMISSION_READ
  ): Organization

  fun getVerifiedOrganization(
      organizationId: String,
      requiredPermissions: List<String>
  ): Organization
}
