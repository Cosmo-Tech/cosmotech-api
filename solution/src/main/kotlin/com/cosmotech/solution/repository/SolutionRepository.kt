// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.solution.repository

import com.cosmotech.common.redis.Sanitize
import com.cosmotech.common.redis.SecurityConstraint
import com.cosmotech.solution.domain.Solution
import com.redis.om.spring.annotations.Query
import com.redis.om.spring.repository.RedisDocumentRepository
import java.util.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.query.Param

interface SolutionRepository : RedisDocumentRepository<Solution, String> {

  @Query("@organizationId:{\$organizationId} @id:{\$solutionId}")
  fun findBy(
      @Sanitize @Param("organizationId") organizationId: String,
      @Sanitize @Param("solutionId") solutionId: String,
  ): Optional<Solution>

  @Query("(@organizationId:{\$organizationId})  \$securityConstraint")
  fun findByOrganizationIdAndSecurity(
      @Sanitize @Param("organizationId") organizationId: String,
      @SecurityConstraint @Param("securityConstraint") securityConstraint: String,
      pageable: Pageable,
  ): Page<Solution>

  @Query("@organizationId:{\$organizationId}")
  fun findByOrganizationId(
      @Sanitize @Param("organizationId") organizationId: String,
      pageable: Pageable,
  ): Page<Solution>
}
