// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun.workflow.argo

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.scenariorun.CSM_DAG_ROOT
import com.cosmotech.scenariorun.container.BASIC_SIZING
import com.cosmotech.scenariorun.container.getLimitsMap
import com.cosmotech.scenariorun.container.getRequestsMap
import com.cosmotech.scenariorun.container.toContainerResourceSizing
import com.cosmotech.scenariorun.domain.ScenarioRunContainer
import com.cosmotech.scenariorun.domain.ScenarioRunStartContainers
import com.cosmotech.scenariorun.utils.getNodeLabelSize
import io.argoproj.workflow.models.IoArgoprojWorkflowV1alpha1ArchiveStrategy
import io.argoproj.workflow.models.IoArgoprojWorkflowV1alpha1Artifact
import io.argoproj.workflow.models.IoArgoprojWorkflowV1alpha1DAGTask
import io.argoproj.workflow.models.IoArgoprojWorkflowV1alpha1DAGTemplate
import io.argoproj.workflow.models.IoArgoprojWorkflowV1alpha1Metadata
import io.argoproj.workflow.models.IoArgoprojWorkflowV1alpha1Outputs
import io.argoproj.workflow.models.IoArgoprojWorkflowV1alpha1Template
import io.argoproj.workflow.models.IoArgoprojWorkflowV1alpha1Workflow
import io.argoproj.workflow.models.IoArgoprojWorkflowV1alpha1WorkflowSpec
import io.kubernetes.client.custom.Quantity
import io.kubernetes.client.openapi.models.V1Container
import io.kubernetes.client.openapi.models.V1EmptyDirVolumeSource
import io.kubernetes.client.openapi.models.V1EnvVar
import io.kubernetes.client.openapi.models.V1LocalObjectReference
import io.kubernetes.client.openapi.models.V1ObjectMeta
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimSpec
import io.kubernetes.client.openapi.models.V1ResourceRequirements
import io.kubernetes.client.openapi.models.V1Toleration
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
): IoArgoprojWorkflowV1alpha1Template {
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

  val sizingInfo = scenarioRunContainer.runSizing ?: BASIC_SIZING.toContainerResourceSizing()

  scenarioRunContainer.nodeLabel
  val container =
      V1Container()
          .image(scenarioRunContainer.image)
          .imagePullPolicy(csmPlatformProperties.argo.imagePullPolicy)
          .env(envVars)
          .args(scenarioRunContainer.runArgs)
          .volumeMounts(volumeMounts)
          .resources(
              V1ResourceRequirements()
                  .requests(sizingInfo.getRequestsMap())
                  .limits(sizingInfo.getLimitsMap()))
  if (scenarioRunContainer.entrypoint != null) {
    container.command(listOf(scenarioRunContainer.entrypoint))
  }

  val template =
      IoArgoprojWorkflowV1alpha1Template()
          .name(scenarioRunContainer.name)
          .metadata(IoArgoprojWorkflowV1alpha1Metadata().labels(scenarioRunContainer.labels))
          .container(container)
          .nodeSelector(scenarioRunContainer.getNodeLabelSize())
          .addVolumesItem(V1Volume().emptyDir(V1EmptyDirVolumeSource()).name("out"))

  val artifacts =
      scenarioRunContainer.artifacts?.map {
        IoArgoprojWorkflowV1alpha1Artifact()
            .name(it.name)
            .path("/var/csmoutput/${it.path}")
            .archive(IoArgoprojWorkflowV1alpha1ArchiveStrategy().none(Object()))
      }
  if (!artifacts.isNullOrEmpty()) {
    template.outputs(IoArgoprojWorkflowV1alpha1Outputs().artifacts(artifacts))
  }

  return template
}

internal fun buildWorkflowSpec(
    csmPlatformProperties: CsmPlatformProperties,
    startContainers: ScenarioRunStartContainers,
    executionTimeout: Int?
): IoArgoprojWorkflowV1alpha1WorkflowSpec {
  val nodeSelector = mutableMapOf("kubernetes.io/os" to "linux", "cosmotech.com/tier" to "compute")
  val templates =
      startContainers
          .containers
          .map { container -> buildTemplate(container, csmPlatformProperties) }
          .toMutableList()
  val entrypointTemplate = buildEntrypointTemplate(startContainers)
  templates.add(entrypointTemplate)

  var workflowSpec =
      IoArgoprojWorkflowV1alpha1WorkflowSpec()
          .imagePullSecrets(
              csmPlatformProperties
                  .argo
                  .imagePullSecrets
                  ?.filterNot(String::isBlank)
                  ?.map(V1LocalObjectReference()::name)
                  ?.ifEmpty { null })
          .nodeSelector(nodeSelector)
          .tolerations(listOf(V1Toleration().key("vendor").value("cosmotech").effect("NoSchedule")))
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
    IoArgoprojWorkflowV1alpha1Workflow()
        .metadata(
            V1ObjectMeta()
                .generateName(startContainers.generateName ?: CSM_DEFAULT_WORKFLOW_NAME)
                .labels(startContainers.labels))
        .spec(buildWorkflowSpec(csmPlatformProperties, startContainers, executionTimeout))

private fun buildEntrypointTemplate(
    startContainers: ScenarioRunStartContainers
): IoArgoprojWorkflowV1alpha1Template {
  val dagTemplate = IoArgoprojWorkflowV1alpha1Template().name(CSM_DAG_ENTRYPOINT)
  val dagTasks: MutableList<IoArgoprojWorkflowV1alpha1DAGTask> = mutableListOf()
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
    val task =
        IoArgoprojWorkflowV1alpha1DAGTask()
            .name(container.name)
            .template(container.name)
            .dependencies(dependencies)
    dagTasks.add(task)
    previousContainer = container
  }

  dagTemplate.dag(IoArgoprojWorkflowV1alpha1DAGTemplate().tasks(dagTasks))

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
