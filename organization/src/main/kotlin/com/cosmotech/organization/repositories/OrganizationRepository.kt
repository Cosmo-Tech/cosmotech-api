// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.organization.repositories

import com.cosmotech.organization.domain.Organization
import com.redis.om.spring.repository.RedisDocumentRepository
import org.springframework.stereotype.Repository

@Repository interface OrganizationRepository : RedisDocumentRepository<Organization, String> {
  fun findFirstByName(name: String): Organization?
}
