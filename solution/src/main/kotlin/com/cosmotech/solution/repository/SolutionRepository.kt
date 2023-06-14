// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.solution.repository

import com.cosmotech.api.redis.Sanitize
import com.cosmotech.solution.domain.Solution
import com.redis.om.spring.annotations.Query
import com.redis.om.spring.repository.RedisDocumentRepository
import java.util.Optional
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface SolutionRepository : RedisDocumentRepository<Solution, String> {

  @Query("@organizationId:{\$organizationId} @id:{\$solutionId}")
  fun findBy(
      @Sanitize @Param("organizationId") organizationId: String,
      @Sanitize @Param("solutionId") solutionId: String
  ): Optional<Solution>

  fun findByOrganizationId(organizationId: String, pageable: Pageable): Page<Solution>
}
