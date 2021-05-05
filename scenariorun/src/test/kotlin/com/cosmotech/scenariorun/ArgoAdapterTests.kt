// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun

import com.cosmotech.scenariorun.domain.ScenarioRunContainer
import com.cosmotech.scenariorun.ArgoAdapter
import io.argoproj.workflow.models.Template
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.function.Executable
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory


class ArgoAdapterTests {
  private val logger = LoggerFactory.getLogger(ArgoAdapterTests::class.java)
  private val argoAdapter = ArgoAdapter()

  @Test
  fun `Template not null`() {
    val src = getScenarioRunContainer()
    val template = argoAdapter.buildTemplate(src)
    assertNotNull(template)
  }

  fun getScenarioRunContainer(): ScenarioRunContainer {
    val src = ScenarioRunContainer()
    return src
  }
}
