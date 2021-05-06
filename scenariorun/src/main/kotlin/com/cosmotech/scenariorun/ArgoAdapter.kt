// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun

import com.cosmotech.scenariorun.domain.ScenarioRunContainer
import com.cosmotech.scenariorun.domain.ScenarioRunStartContainers
import io.argoproj.workflow.models.Template
import io.argoproj.workflow.models.WorkflowSpec
import io.kubernetes.client.openapi.models.V1EnvVar
import org.slf4j.LoggerFactory

class ArgoAdapter {
  private val logger = LoggerFactory.getLogger(ArgoAdapter::class.java)
  private val K8S_AGENT_POOL = "agentpool"
  private val CSM_DEFAULT_POOL = "basicpool"
  private val CSM_DEFAULT_ACCOUNT = "workflow"
  private val CSM_DAG_ENTRYPOINT = "entrypoint"

  fun buildTemplate(
      scenarioRunContainer: ScenarioRunContainer
  ): Template {
    var envVars: MutableList<V1EnvVar>? = null
    if (scenarioRunContainer.envVars != null) {
      envVars = mutableListOf()
      scenarioRunContainer.envVars.forEach { (key, value) ->
        val envVar = V1EnvVar().name(key).value(value)
        envVars.add(envVar)
      }
    }

    return Template()
        .name(scenarioRunContainer.name)
        .container(
            io.kubernetes.client.openapi.models.V1Container()
                .image(scenarioRunContainer.image)
                .command(listOf(scenarioRunContainer.entrypoint))
                .env(envVars)
                .args(scenarioRunContainer.runArgs))
  }

  fun buildWorkflowSpec(startContainers: ScenarioRunStartContainers): WorkflowSpec {
    val nodeSelector = buildNodeSelector(startContainers)
    val templates = buildContainersTemplates(startContainers)
    val entrypointTemplate = buildEntrypointTemplate(startContainers)
    templates.add(entrypointTemplate)

    return WorkflowSpec()
        .nodeSelector(nodeSelector)
        .serviceAccountName(CSM_DEFAULT_ACCOUNT)
        .entrypoint(CSM_DAG_ENTRYPOINT)
        .templates(templates)
  }

  private fun buildNodeSelector(startContainers: ScenarioRunStartContainers): Map<String, String> {
    val nodeSelector = mutableMapOf("kubernetes.io/os" to "linux")

    if (startContainers.nodeLabel != null)
        nodeSelector.put(K8S_AGENT_POOL, startContainers.nodeLabel)
    else nodeSelector.put(K8S_AGENT_POOL, CSM_DEFAULT_POOL)

    return nodeSelector
  }

  private fun buildContainersTemplates(startContainers: ScenarioRunStartContainers): MutableList<Template> {
    val list = startContainers.containers.map {
      container -> buildTemplate(container)
    }
    return list.toMutableList()
  }
  private fun buildEntrypointTemplate(startContainers: ScenarioRunStartContainers): Template {
    return Template().name(CSM_DAG_ENTRYPOINT)
  }
}
