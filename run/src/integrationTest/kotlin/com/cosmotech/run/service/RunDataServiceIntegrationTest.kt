// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run.service

import com.cosmotech.run.RunApiServiceInterface
import com.redis.om.spring.annotations.EnableRedisDocumentRepositories
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForObject
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals

@ActiveProfiles(profiles = ["run-test"])
@EnableRedisDocumentRepositories(basePackages = ["com.cosmotech"])
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RunDataServiceIntegrationTest : CsmPostgresTestBase() {

  @Autowired lateinit var runApiService: RunApiServiceInterface
  @Autowired lateinit var readerRunStorageTemplate: JdbcTemplate

  @Test
  fun shouldCreateTable() {
    val runId = "runTable_123564"
    val runData = runApiService.sendRunData("orgId","workId","runnerId", runId)
    val createdDBName = readerRunStorageTemplate
      .queryForObject<String>("select datname FROM pg_catalog.pg_database where datname= ?",
        arrayOf(runId.lowercase()))
    assertEquals(runData.name!!.lowercase(), createdDBName)
  }
}
