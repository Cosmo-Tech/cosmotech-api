// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun.repository

import com.cosmotech.scenariorun.domain.ScenarioRun
import com.redis.om.spring.annotations.Query
import com.redis.om.spring.repository.RedisDocumentRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ScenarioRunRepository : RedisDocumentRepository<ScenarioRun, String> {

  fun findByScenarioId(scenarioId: String): List<ScenarioRun>

  fun findByWorkspaceId(workspaceId: String): List<ScenarioRun>

  @Query("\$predicate")
  fun findByPredicate(@Param("predicate") criteriaList: List<String>): List<ScenarioRun>
}
