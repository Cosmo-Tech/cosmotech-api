// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun.repository

import com.cosmotech.api.redis.Sanitize
import com.cosmotech.scenariorun.domain.ScenarioRun
import com.redis.om.spring.annotations.Query
import com.redis.om.spring.repository.RedisDocumentRepository
import java.util.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ScenarioRunRepository : RedisDocumentRepository<ScenarioRun, String> {

  @Query("@organizationId:{\$organizationId} @id:{\$scenarioRunId}")
  fun findBy(
      @Sanitize @Param("organizationId") organizationId: String,
      @Sanitize @Param("scenarioRunId") scenarioRunId: String
  ): Optional<ScenarioRun>

  @Query(
      "@organizationId:{\$organizationId} @workspaceId:{\$workspaceId} @scenarioId:{\$scenarioId}")
  fun findByScenarioId(
      @Sanitize @Param("organizationId") organizationId: String,
      @Sanitize @Param("workspaceId") workspaceId: String,
      @Sanitize @Param("scenarioId") scenarioId: String,
      pageable: Pageable
  ): Page<ScenarioRun>

  @Query("@organizationId:{\$organizationId} @workspaceId:{\$workspaceId}")
  fun findByWorkspaceId(
      @Sanitize @Param("organizationId") organizationId: String,
      @Sanitize @Param("workspaceId") workspaceId: String,
      pageable: Pageable
  ): Page<ScenarioRun>

  @Query("(@organizationId:{\$organizationId}) \$predicate")
  fun findByPredicate(
      @Sanitize @Param("organizationId") organizationId: String,
      @Param("predicate") criteriaList: List<String>,
      pageable: Pageable
  ): Page<ScenarioRun>
}
