// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.solution.repository

import com.cosmotech.solution.domain.Solution
import com.redis.om.spring.repository.RedisDocumentRepository
import org.springframework.stereotype.Repository

@Repository
interface SolutionRepository : RedisDocumentRepository<Solution, String> {

  fun findByOrganizationId(organizationId: String): List<Solution>
}
