package com.cosmotech.scenariorun.utils

import com.cosmotech.scenariorun.domain.ScenarioRunSearch
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class ScenarioRunExtensionsTests {


  @Test
  fun `should convert ScenarioRunSearch to List of Redis query`() {
    var scenarioSearch = ScenarioRunSearch(
      scenarioId = "scenario-Id",
      state = ScenarioRunSearch.State.Running,
      runTemplateId = null,
      workspaceId = "workspace-Id",
    )

    val redisQuery = scenarioSearch.toRedisPredicate()
    assertContains(redisQuery, "@scenarioId:{*scenario\\-Id*}")
    assertContains(redisQuery, "@workspaceId:{*workspace\\-Id*}")
    assertContains(redisQuery, "@state:*Running*")
    assertTrue { redisQuery.size == 3 }
  }

}