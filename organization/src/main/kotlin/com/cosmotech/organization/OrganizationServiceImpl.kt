// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.

package com.cosmotech.organization

import com.cosmotech.api.AbstractPhoenixService
import com.cosmotech.organization.api.OrganizationsApiService
import com.cosmotech.organization.domain.Organization
import org.springframework.stereotype.Service

@Service
class OrganizationServiceImpl : AbstractPhoenixService(), OrganizationsApiService {
  override fun findAllOrganizations(): List<Organization> {
    TODO("Not yet implemented")
  }

  override fun findOrganizationById(organizationId: String): Organization {
    TODO("Not yet implemented")
  }

  override fun registerOrganization(organization: Organization): Organization {
    TODO("Not yet implemented")
  }

  override fun unregisterOrganization(organizationId: String): Organization {
    TODO("Not yet implemented")
  }

  override fun updateOrganization(
      organizationId: String,
      organization: Organization
  ): Organization {
    TODO("Not yet implemented")
  }
}
