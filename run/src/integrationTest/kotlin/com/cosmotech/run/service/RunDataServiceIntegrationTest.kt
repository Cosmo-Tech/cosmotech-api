// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run.service

import com.cosmotech.run.RunApiServiceInterface
import com.cosmotech.run.config.existDB
import com.cosmotech.run.config.toDataTableName
import com.cosmotech.run.domain.SendRunDataRequest
import com.redis.om.spring.annotations.EnableRedisDocumentRepositories
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles(profiles = ["run-test"])
@EnableRedisDocumentRepositories(basePackages = ["com.cosmotech"])
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RunDataServiceIntegrationTest : CsmPostgresTestBase() {

  @Autowired lateinit var runApiService: RunApiServiceInterface
  @Autowired lateinit var readerRunStorageTemplate: JdbcTemplate

  @Test
  fun shouldCreateDatabase() {
    val databaseName = "runTable_123564"
    val tableName = "MyCustomData"
    val data =
        listOf(
            mapOf("param1" to "value1"),
            mapOf("param2" to "2"),
            mapOf("param3" to JSONObject(mapOf("param4" to "value4")).toString()))
    val requestBody = SendRunDataRequest(id = tableName, data = data)
    assertFalse(readerRunStorageTemplate.existDB(databaseName))
    val runDataResult =
        runApiService.sendRunData("orgId", "workId", "runnerId", databaseName, requestBody)
    assertNotNull(runDataResult.databaseName)
    assertEquals(databaseName, runDataResult.databaseName)
    assertTrue(readerRunStorageTemplate.existDB(runDataResult.databaseName!!))
    assertEquals(tableName.toDataTableName(false), runDataResult.tableName)
    assertEquals(data, runDataResult.data)
  }
}
