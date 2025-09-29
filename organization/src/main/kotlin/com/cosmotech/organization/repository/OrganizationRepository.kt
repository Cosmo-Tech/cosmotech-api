// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.organization.repository

import com.cosmotech.common.redis.SecurityConstraint
import com.cosmotech.organization.domain.Organization
import com.redis.om.spring.annotations.Query
import com.redis.om.spring.repository.RedisDocumentRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.query.Param

interface OrganizationRepository : RedisDocumentRepository<Organization, String> {
  @Query("\$securityConstraint")
  fun findOrganizationsBySecurity(
      @SecurityConstraint @Param("securityConstraint") securityConstraint: String,
      pageable: Pageable
  ): Page<Organization>
}
