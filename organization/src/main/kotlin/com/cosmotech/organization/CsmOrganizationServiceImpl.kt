package com.cosmotech.organization

import com.cosmotech.api.AbstractPhoenixService
import com.cosmotech.organization.api.OrganizationsApiService
import org.springframework.stereotype.Service

@Service
class CsmOrganizationServiceImpl : AbstractPhoenixService(), OrganizationsApiService {
    override fun organizationsOrganizationIdGet(organizationId: String) {
        TODO("Not yet implemented")
    }
}