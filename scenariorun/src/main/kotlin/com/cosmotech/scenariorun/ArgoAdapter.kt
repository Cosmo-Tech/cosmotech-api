// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun

import com.cosmotech.scenariorun.domain.ScenarioRunContainer
import io.argoproj.workflow.models.Template
import org.slf4j.LoggerFactory


class ArgoAdapter{
  private val logger = LoggerFactory.getLogger(ArgoAdapter::class.java)

  fun buildTemplate(scenarioRunContainer: ScenarioRunContainer): Template {
    return Template()
  }
}
