// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun.repository

import com.cosmotech.scenariorun.domain.ScenarioRun
import com.redis.om.spring.annotations.Query
import com.redis.om.spring.repository.RedisDocumentRepository
import java.util.Optional
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ScenarioRunRepository : RedisDocumentRepository<ScenarioRun, String> {

  @Query("@organizationId:{\$organizationId} @id:{\$scenarioRunId}")
  fun findBy(
      @Param("organizationId") organizationId: String,
      @Param("scenarioRunId") scenarioRunId: String
  ): Optional<ScenarioRun>

  fun findByScenarioId(scenarioId: String, pageable: Pageable): Page<ScenarioRun>

  fun findByWorkspaceId(workspaceId: String, pageable: Pageable): Page<ScenarioRun>

  @Query("\$predicate")
  fun findByPredicate(
      @Param("predicate") criteriaList: List<String>,
      pageable: Pageable
  ): Page<ScenarioRun>
}
