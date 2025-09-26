// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run.repository

import com.cosmotech.common.redis.Sanitize
import com.cosmotech.run.domain.Run
import com.redis.om.spring.annotations.Query
import com.redis.om.spring.repository.RedisDocumentRepository
import java.util.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface RunRepository : RedisDocumentRepository<Run, String> {

  @Query(
      "@organizationId:{\$organizationId} @workspaceId:{\$workspaceId} @runnerId:{\$runnerId} @id:{\$runId}")
  fun findBy(
      @Sanitize @Param("organizationId") organizationId: String,
      @Sanitize @Param("workspaceId") workspaceId: String,
      @Sanitize @Param("runnerId") runnerId: String,
      @Sanitize @Param("runId") runId: String
  ): Optional<Run>

  @Query("@organizationId:{\$organizationId} @workspaceId:{\$workspaceId} @runnerId:{\$runnerId}")
  fun findByRunnerId(
      @Sanitize @Param("organizationId") organizationId: String,
      @Sanitize @Param("workspaceId") workspaceId: String,
      @Sanitize @Param("runnerId") runnerId: String,
      pageable: Pageable
  ): Page<Run>
}
