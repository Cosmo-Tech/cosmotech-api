package com.cosmotech.organization

import com.cosmotech.api.AbstractPhoenixService
import com.cosmotech.organization.api.OrganizationsApiService
import com.cosmotech.organization.domain.Organization
import org.springframework.stereotype.Service

@Service
class OrganizationServiceImpl : AbstractPhoenixService(), OrganizationsApiService {
    override fun findAll(): List<Organization> {
        TODO("Not yet implemented")
    }

    override fun findById(organizationId: String): Organization {
        TODO("Not yet implemented")
    }

    override fun register(organization: Organization): Organization {
        TODO("Not yet implemented")
    }

    override fun unregister(organizationId: String): Organization {
        TODO("Not yet implemented")
    }

    override fun update(organizationId: String, organization: Organization): Organization {
        TODO("Not yet implemented")
    }
}