// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun

import com.cosmotech.scenariorun.domain.ScenarioRunContainer
import com.cosmotech.scenariorun.domain.ScenarioRunStartContainers
import io.argoproj.workflow.models.DAGTask
import io.argoproj.workflow.models.DAGTemplate
import io.argoproj.workflow.models.Template
import io.argoproj.workflow.models.Workflow
import io.argoproj.workflow.models.WorkflowSpec
import io.kubernetes.client.custom.Quantity
import io.kubernetes.client.openapi.models.V1EnvVar
import io.kubernetes.client.openapi.models.V1ObjectMeta
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimSpec
import io.kubernetes.client.openapi.models.V1ResourceRequirements
import io.kubernetes.client.openapi.models.V1VolumeMount
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ArgoAdapter {
  private val logger = LoggerFactory.getLogger(ArgoAdapter::class.java)
  private val K8S_AGENT_POOL = "agentpool"
  private val CSM_DEFAULT_ACCOUNT = "workflow"
  private val CSM_DAG_ENTRYPOINT = "entrypoint"
  private val CSM_DEFAULT_WORKFLOW_NAME = "default-workflow-"
  private val VOLUME_CLAIM_DATASETS = "datasetsdir"
  private val VOLUME_CLAIM_PARAMETERS = "parametersdir"
  private val VOLUME_DATASETS_PATH = "/mnt/scenariorun-data"
  private val VOLUME_PARAMETERS_PATH = "/mnt/scenariorun-parameters"

  fun buildTemplate(scenarioRunContainer: ScenarioRunContainer): Template {
    var envVars: MutableList<V1EnvVar>? = null
    if (scenarioRunContainer.envVars != null) {
      envVars = mutableListOf()
      scenarioRunContainer.envVars.forEach { (key, value) ->
        val envVar = V1EnvVar().name(key).value(value)
        envVars.add(envVar)
      }
    }
    val volumeMounts =
        listOf(
            V1VolumeMount().name(VOLUME_CLAIM_DATASETS).mountPath(VOLUME_DATASETS_PATH),
            V1VolumeMount().name(VOLUME_CLAIM_PARAMETERS).mountPath(VOLUME_PARAMETERS_PATH))

    val container =
        io.kubernetes.client.openapi.models.V1Container()
            .image(scenarioRunContainer.image)
            .env(envVars)
            .args(scenarioRunContainer.runArgs)
            .volumeMounts(volumeMounts)
    if (scenarioRunContainer.entrypoint != null) {
      container.command(listOf(scenarioRunContainer.entrypoint))
    }

    return Template().name(scenarioRunContainer.name).container(container)
  }

  fun buildWorkflowSpec(startContainers: ScenarioRunStartContainers): WorkflowSpec {
    val nodeSelector = buildNodeSelector(startContainers)
    val templates = buildContainersTemplates(startContainers)
    val entrypointTemplate = buildEntrypointTemplate(startContainers)
    templates.add(entrypointTemplate)
    val volumeClaims = buildVolumeClaims()

    return WorkflowSpec()
        .nodeSelector(nodeSelector)
        .serviceAccountName(CSM_DEFAULT_ACCOUNT)
        .entrypoint(CSM_DAG_ENTRYPOINT)
        .templates(templates)
        .volumeClaimTemplates(volumeClaims)
  }

  fun buildWorkflow(startContainers: ScenarioRunStartContainers): Workflow {
    val spec = buildWorkflowSpec(startContainers)
    val metadata = buildMetadata(startContainers)
    return Workflow().metadata(metadata).spec(spec)
  }

  private fun buildNodeSelector(startContainers: ScenarioRunStartContainers): Map<String, String> {
    val nodeSelector = mutableMapOf("kubernetes.io/os" to "linux")

    if (startContainers.nodeLabel != null) {
      nodeSelector.put(K8S_AGENT_POOL, startContainers.nodeLabel)
    }

    return nodeSelector
  }

  private fun buildContainersTemplates(
      startContainers: ScenarioRunStartContainers
  ): MutableList<Template> {
    val list = startContainers.containers.map { container -> buildTemplate(container) }
    return list.toMutableList()
  }

  private fun buildEntrypointTemplate(startContainers: ScenarioRunStartContainers): Template {
    val dagTemplate = Template().name(CSM_DAG_ENTRYPOINT)
    val dagTasks: MutableList<DAGTask> = mutableListOf()
    var previousContainer: ScenarioRunContainer? = null
    for (container in startContainers.containers) {
      var dependencies: List<String>? = null
      if (container.dependencies != null) dependencies = container.dependencies
      else {
        if (previousContainer != null) dependencies = listOf(previousContainer.name)
      }
      val task = DAGTask().name(container.name).template(container.name).dependencies(dependencies)
      dagTasks.add(task)
      previousContainer = container
    }

    dagTemplate.dag(DAGTemplate().tasks(dagTasks))

    return dagTemplate
  }

  private fun buildVolumeClaims(): List<V1PersistentVolumeClaim> {
    val datasetsdir =
        V1PersistentVolumeClaim()
            .metadata(V1ObjectMeta().name(VOLUME_CLAIM_DATASETS))
            .spec(
                V1PersistentVolumeClaimSpec()
                    .accessModes(listOf("ReadWriteOnce"))
                    .resources(
                        V1ResourceRequirements().requests(mapOf("storage" to Quantity("1Gi")))))
    val parametersdir =
        V1PersistentVolumeClaim()
            .metadata(V1ObjectMeta().name(VOLUME_CLAIM_PARAMETERS))
            .spec(
                V1PersistentVolumeClaimSpec()
                    .accessModes(listOf("ReadWriteOnce"))
                    .resources(
                        V1ResourceRequirements().requests(mapOf("storage" to Quantity("1Gi")))))

    return listOf(datasetsdir, parametersdir)
  }

  private fun buildMetadata(startContainers: ScenarioRunStartContainers): V1ObjectMeta {
    val generateName = startContainers.generateName ?: CSM_DEFAULT_WORKFLOW_NAME
    return V1ObjectMeta().generateName(generateName)
  }
}
