// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorunresult.repository

import com.cosmotech.api.redis.Sanitize
import com.cosmotech.scenariorun.domain.ScenarioRun
import com.cosmotech.scenariorunresult.domain.ScenarioRunResult
import com.redis.om.spring.annotations.Query
import com.redis.om.spring.repository.RedisDocumentRepository
import java.util.*
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ScenarioRunResultRepository : RedisDocumentRepository<ScenarioRunResult, String> {

  @Query(
      "@organizationId:{\$organizationId} " +
          "@id:{\$workspaceId} " +
          "@id:{\$scenarioId} " +
          "@id:{\$scenarioRunId} " +
          "@id:{\$scenarioRunResultId}")
  fun findBy(
      @Sanitize @Param("organizationId") organizationId: String,
      @Sanitize @Param("workspaceId") workspaceId: String,
      @Sanitize @Param("scenarioId") scenarioId: String,
      @Sanitize @Param("scenarioRunId") scenarioRunId: String,
      @Sanitize @Param("scenarioRunResultId") scenarioRunResultId: String
  ): Optional<ScenarioRun>
}
