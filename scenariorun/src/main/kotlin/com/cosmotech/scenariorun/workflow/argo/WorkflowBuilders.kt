// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun.workflow.argo

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.scenariorun.CSM_DAG_ROOT
import com.cosmotech.scenariorun.domain.ScenarioRunContainer
import com.cosmotech.scenariorun.domain.ScenarioRunStartContainers
import io.argoproj.workflow.models.ArchiveStrategy
import io.argoproj.workflow.models.Artifact
import io.argoproj.workflow.models.DAGTask
import io.argoproj.workflow.models.DAGTemplate
import io.argoproj.workflow.models.Metadata
import io.argoproj.workflow.models.Outputs
import io.argoproj.workflow.models.Template
import io.argoproj.workflow.models.Workflow
import io.argoproj.workflow.models.WorkflowSpec
import io.kubernetes.client.custom.Quantity
import io.kubernetes.client.openapi.models.V1Container
import io.kubernetes.client.openapi.models.V1EmptyDirVolumeSource
import io.kubernetes.client.openapi.models.V1EnvVar
import io.kubernetes.client.openapi.models.V1LocalObjectReference
import io.kubernetes.client.openapi.models.V1ObjectMeta
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimSpec
import io.kubernetes.client.openapi.models.V1ResourceRequirements
import io.kubernetes.client.openapi.models.V1Volume
import io.kubernetes.client.openapi.models.V1VolumeMount

private const val CSM_DAG_ENTRYPOINT = "entrypoint"
private const val CSM_DEFAULT_WORKFLOW_NAME = "default-workflow-"
internal const val VOLUME_CLAIM = "datadir"
internal const val VOLUME_CLAIM_DATASETS_SUBPATH = "datasetsdir"
internal const val VOLUME_CLAIM_PARAMETERS_SUBPATH = "parametersdir"
private const val VOLUME_DATASETS_PATH = "/mnt/scenariorun-data"
private const val VOLUME_PARAMETERS_PATH = "/mnt/scenariorun-parameters"
internal const val CSM_ARGO_WORKFLOWS_TIMEOUT = 28800

internal fun buildTemplate(
    scenarioRunContainer: ScenarioRunContainer,
    csmPlatformProperties: CsmPlatformProperties
): Template {
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
          V1VolumeMount()
              .name(VOLUME_CLAIM)
              .mountPath(VOLUME_DATASETS_PATH)
              .subPath(VOLUME_CLAIM_DATASETS_SUBPATH),
          V1VolumeMount()
              .name(VOLUME_CLAIM)
              .mountPath(VOLUME_PARAMETERS_PATH)
              .subPath(VOLUME_CLAIM_PARAMETERS_SUBPATH),
          V1VolumeMount().name("out").mountPath("/var/csmoutput"))

  val container =
      V1Container()
          .image(scenarioRunContainer.image)
          .imagePullPolicy(csmPlatformProperties.argo.imagePullPolicy)
          .env(envVars)
          .args(scenarioRunContainer.runArgs)
          .volumeMounts(volumeMounts)
  if (scenarioRunContainer.entrypoint != null) {
    container.command(listOf(scenarioRunContainer.entrypoint))
  }

  val template =
      Template()
          .name(scenarioRunContainer.name)
          .metadata(Metadata().labels(scenarioRunContainer.labels))
          .container(container)
          .addVolumesItem(V1Volume().emptyDir(V1EmptyDirVolumeSource()).name("out"))

  val artifacts =
      scenarioRunContainer.artifacts?.map {
        Artifact()
            .name(it.name)
            .path("/var/csmoutput/${it.path}")
            .archive(ArchiveStrategy().none(Object()))
      }
  if (!artifacts.isNullOrEmpty()) {
    template.outputs(Outputs().artifacts(artifacts))
  }

  return template
}

internal fun buildWorkflowSpec(
    csmPlatformProperties: CsmPlatformProperties,
    startContainers: ScenarioRunStartContainers,
    executionTimeout: Int?
): WorkflowSpec {
  val nodeSelector = mutableMapOf("kubernetes.io/os" to "linux")
  if (startContainers.nodeLabel != null) {
    nodeSelector[csmPlatformProperties.argo.workflows.nodePoolLabel] = startContainers.nodeLabel
  }
  val templates =
      startContainers
          .containers
          .map { container -> buildTemplate(container, csmPlatformProperties) }
          .toMutableList()
  val entrypointTemplate = buildEntrypointTemplate(startContainers)
  templates.add(entrypointTemplate)

  var workflowSpec =
      WorkflowSpec()
          .imagePullSecrets(
              csmPlatformProperties
                  .argo
                  .imagePullSecrets
                  ?.filterNot(String::isBlank)
                  ?.map(V1LocalObjectReference()::name)
                  ?.ifEmpty { null })
          .nodeSelector(nodeSelector)
          .serviceAccountName(csmPlatformProperties.argo.workflows.serviceAccountName)
          .entrypoint(CSM_DAG_ENTRYPOINT)
          .templates(templates)
          .volumeClaimTemplates(buildVolumeClaims(csmPlatformProperties))

  workflowSpec.activeDeadlineSeconds = executionTimeout ?: CSM_ARGO_WORKFLOWS_TIMEOUT

  return workflowSpec
}

internal fun buildWorkflow(
    csmPlatformProperties: CsmPlatformProperties,
    startContainers: ScenarioRunStartContainers,
    executionTimeout: Int?
) =
    Workflow()
        .metadata(
            V1ObjectMeta()
                .generateName(startContainers.generateName ?: CSM_DEFAULT_WORKFLOW_NAME)
                .labels(startContainers.labels))
        .spec(buildWorkflowSpec(csmPlatformProperties, startContainers, executionTimeout))

private fun buildEntrypointTemplate(startContainers: ScenarioRunStartContainers): Template {
  val dagTemplate = Template().name(CSM_DAG_ENTRYPOINT)
  val dagTasks: MutableList<DAGTask> = mutableListOf()
  var previousContainer: ScenarioRunContainer? = null
  for (container in startContainers.containers) {
    var dependencies: List<String>? = null
    if (container.dependencies != null) {
      if (CSM_DAG_ROOT !in container.dependencies) {
        dependencies = container.dependencies
      }
    } else {
      if (previousContainer != null) dependencies = listOf(previousContainer.name)
    }
    val task = DAGTask().name(container.name).template(container.name).dependencies(dependencies)
    dagTasks.add(task)
    previousContainer = container
  }

  dagTemplate.dag(DAGTemplate().tasks(dagTasks))

  return dagTemplate
}

private fun buildVolumeClaims(
    csmPlatformProperties: CsmPlatformProperties
): List<V1PersistentVolumeClaim> {
  val workflowsConfig = csmPlatformProperties.argo.workflows
  val dataDir =
      V1PersistentVolumeClaim()
          .metadata(V1ObjectMeta().name(VOLUME_CLAIM))
          .spec(
              V1PersistentVolumeClaimSpec()
                  .accessModes(workflowsConfig.accessModes)
                  .storageClassName(
                      if (workflowsConfig.storageClass.isNullOrBlank()) null
                      else workflowsConfig.storageClass)
                  .resources(
                      V1ResourceRequirements()
                          .requests(workflowsConfig.requests.mapValues { Quantity(it.value) })))
  return listOf(dataDir)
}
