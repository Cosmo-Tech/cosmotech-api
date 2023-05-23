// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorunresult.repository

import com.cosmotech.scenariorunresults.domain.ScenarioRunResult
import com.redis.om.spring.repository.RedisDocumentRepository
import org.springframework.stereotype.Repository

@Repository
interface ScenarioRunResultRepository : RedisDocumentRepository<ScenarioRunResult, String> {}
