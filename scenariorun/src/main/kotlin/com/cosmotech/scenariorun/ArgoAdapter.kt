// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun

import com.cosmotech.scenariorun.domain.ScenarioRunContainer
import io.argoproj.workflow.models.Template
import io.kubernetes.client.openapi.models.V1EnvVar
import org.slf4j.LoggerFactory

class ArgoAdapter {
  private val logger = LoggerFactory.getLogger(ArgoAdapter::class.java)

  fun buildTemplate(
      scenarioRunContainer: ScenarioRunContainer,
      name: String = "default"
  ): Template {
    var envVars: MutableList<V1EnvVar>? = null
    if (scenarioRunContainer.envVars != null) {
      envVars = mutableListOf()
      scenarioRunContainer.envVars.forEach { (key, value) ->
        val envVar = V1EnvVar().name(key).value(value.toString())
        envVars.add(envVar)
      }
    }

    return Template()
        .name(name)
        .container(
            io.kubernetes.client.openapi.models.V1Container()
                .image(scenarioRunContainer.image)
                .command(listOf(scenarioRunContainer.entrypoint))
                .env(envVars)
                .args(scenarioRunContainer.runArgs))
  }
}
