// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.organization.repository

import com.cosmotech.organization.domain.Organization
import com.redis.om.spring.annotations.Query
import com.redis.om.spring.repository.RedisDocumentRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface OrganizationRepository : RedisDocumentRepository<Organization, String> {
  @Query("(-@security_default:{none}) | (@security_accessControlList_id:{\$userId})")
  fun findOrganizationsBySecurity(@Param("userId") userId: String): List<Organization>
}
