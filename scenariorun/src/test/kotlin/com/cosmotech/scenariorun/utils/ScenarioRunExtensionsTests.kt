// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun.utils

import com.cosmotech.scenariorun.domain.ScenarioRunSearch
import kotlin.test.assertContains
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class ScenarioRunExtensionsTests {

  @Test
  fun `should convert ScenarioRunSearch to List of Redis query`() {
    var scenarioSearch =
        ScenarioRunSearch(
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
