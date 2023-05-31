// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorunresult.service

import com.cosmotech.api.tests.CsmRedisTestBase
import com.cosmotech.scenariorunresult.api.ScenariorunresultApiService
import com.cosmotech.scenariorunresult.domain.ScenarioRunResult
import io.mockk.junit5.MockKExtension
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.runner.RunWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.junit4.SpringRunner

const val ORGANIZATION_ID = "Organization"
const val WORKSPACE_ID = "Workspace"
const val SCENARIORUN_ID = "ScenarioRun"
const val PROBE_ID = "Probe"

@ActiveProfiles(profiles = ["scenariorunresult-test"])
@ExtendWith(MockKExtension::class)
@ExtendWith(SpringExtension::class)
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ScenarioRunResultServiceIntegrationTest : CsmRedisTestBase() {

  private val logger = LoggerFactory.getLogger(ScenarioRunResultServiceIntegrationTest::class.java)
  @Autowired lateinit var scenariorunresultApiService: ScenariorunresultApiService

  @Test
  fun `test CRUD for scenarioRunResult service`() {
    logger.info("should create a scenarioRunResult")
    val result =
        scenariorunresultApiService.createScenarioRunResult(
            ORGANIZATION_ID,
            WORKSPACE_ID,
            SCENARIORUN_ID,
            PROBE_ID,
            ScenarioRunResult(
                "id",
                "{\"results\": {" +
                    "\"probe1\": {" +
                    "\"var1\": \"var\",\"value1\": \"value\"}," +
                    "\"probe2\": {" +
                    "\"var1\": \"var\",\"value1\": \"value\"}}}"))
    assertNotNull(result)

    logger.info("should get a scenarioRunResult")
    val getResult =
        scenariorunresultApiService.getScenarioRunResult(
            ORGANIZATION_ID, WORKSPACE_ID, SCENARIORUN_ID, PROBE_ID)
    assertEquals(result, getResult)
  }
}
