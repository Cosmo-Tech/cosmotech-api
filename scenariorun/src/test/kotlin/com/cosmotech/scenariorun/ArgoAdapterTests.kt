// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun

import com.cosmotech.scenariorun.domain.ScenarioRunContainer
import com.cosmotech.scenariorun.domain.ScenarioRunStartContainers
import io.argoproj.workflow.models.DAGTask
import io.kubernetes.client.custom.Quantity
import io.kubernetes.client.openapi.models.V1EnvVar
import io.kubernetes.client.openapi.models.V1ObjectMeta
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimSpec
import io.kubernetes.client.openapi.models.V1ResourceRequirements
import io.kubernetes.client.openapi.models.V1VolumeMount
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class ArgoAdapterTests {
  private val logger = LoggerFactory.getLogger(ArgoAdapterTests::class.java)
  private val argoAdapter = ArgoAdapter()
  private val DEFAULT_ENTRY_POINT = "entrypoint.py"

  @Test
  fun `Template not null`() {
    val src = getScenarioRunContainer()
    val template = argoAdapter.buildTemplate(src)
    assertNotNull(template)
  }

  @Test
  fun `Template has image`() {
    val src = getScenarioRunContainer()
    val template = argoAdapter.buildTemplate(src)
    assertEquals(src.image, template.container?.image)
  }

  @Test
  fun `Template has name`() {
    val name = "template name"
    val src = getScenarioRunContainer(name)
    val template = argoAdapter.buildTemplate(src)
    assertEquals(name, template.name)
  }

  @Test
  fun `Template has args`() {
    val src = getScenarioRunContainerArgs()
    val template = argoAdapter.buildTemplate(src)
    assertEquals(src.runArgs, template.container?.args)
  }

  @Test
  fun `Template has no args`() {
    val src = getScenarioRunContainer()
    val template = argoAdapter.buildTemplate(src)
    assertEquals(src.runArgs, template.container?.args)
  }

  @Test
  fun `Template has simulator default entrypoint`() {
    val src = getScenarioRunContainerEntrypoint()
    val template = argoAdapter.buildTemplate(src)
    assertEquals(listOf(DEFAULT_ENTRY_POINT), template.container?.command)
  }

  @Test
  fun `Template has default entrypoint if not defined`() {
    val src = getScenarioRunContainer()
    val template = argoAdapter.buildTemplate(src)
    val expected: List<String?> = listOf(null)
    assertEquals(expected, template.container?.command)
  }

  @Test
  fun `Template has default env var`() {
    val src = getScenarioRunContainer()
    val template = argoAdapter.buildTemplate(src)
    assertNull(template.container?.env)
  }

  @Test
  fun `Template has env var`() {
    val src = getScenarioRunContainerEnv()
    val template = argoAdapter.buildTemplate(src)
    val expected =
        listOf(
            V1EnvVar().name("env1").value("envvar1"),
            V1EnvVar().name("env2").value("envvar2"),
            V1EnvVar().name("env3").value("envvar3"),
        )

    assertEquals(expected, template.container?.env)
  }

  @Test
  fun `Create Workflow Spec with StartContainers not null`() {
    var sc = getStartContainersRun()
    val workflowSpec = argoAdapter.buildWorkflowSpec(sc)
    assertNotNull(workflowSpec)
  }

  @Test
  fun `Create Workflow Spec with StartContainers agent pool`() {
    var sc = getStartContainersRun()
    val workflowSpec = argoAdapter.buildWorkflowSpec(sc)
    val expected = mapOf("kubernetes.io/os" to "linux", "agentpool" to "highcpupool")

    assertTrue(expected.equals(workflowSpec.nodeSelector))
  }

  @Test
  fun `Create Workflow Spec with StartContainers basic agent pool`() {
    var sc = getStartContainersRunDefaultPool()
    val workflowSpec = argoAdapter.buildWorkflowSpec(sc)
    val expected = mapOf("kubernetes.io/os" to "linux", "agentpool" to "basicpool")

    assertTrue(expected.equals(workflowSpec.nodeSelector))
  }

  @Test
  fun `Create Workflow Spec with StartContainers no pool`() {
    var sc = getStartContainersRunNoPool()
    val workflowSpec = argoAdapter.buildWorkflowSpec(sc)
    val expected = mapOf("kubernetes.io/os" to "linux")

    assertTrue(expected.equals(workflowSpec.nodeSelector))
  }

  @Test
  fun `Create Workflow Spec with StartContainers Service Account`() {
    var sc = getStartContainersRun()
    val workflowSpec = argoAdapter.buildWorkflowSpec(sc)

    assertEquals("workflow", workflowSpec.serviceAccountName)
  }

  @Test
  fun `Create Workflow Spec with StartContainers Run name`() {
    var sc = getStartContainersRun()
    val workflowSpec = argoAdapter.buildWorkflowSpec(sc)

    assertEquals("runContainer", workflowSpec.templates?.getOrNull(0)?.name)
  }

  @Test
  fun `Create Workflow Spec with StartContainers Entrypoint FetchDataset`() {
    var sc = getStartContainers()
    val workflowSpec = argoAdapter.buildWorkflowSpec(sc)

    assertEquals("entrypoint", workflowSpec.entrypoint)
  }

  @Test
  fun `Create Workflow Spec with StartContainers entrypoint template not null`() {
    var sc = getStartContainers()
    val workflowSpec = argoAdapter.buildWorkflowSpec(sc)

    val entrypointTemplate =
        workflowSpec.templates?.find { template -> template.name.equals("entrypoint") }
    assertNotNull(entrypointTemplate)
  }

  @Test
  fun `Create Workflow Spec with StartContainers entrypoint dag not null`() {
    var sc = getStartContainers()
    val workflowSpec = argoAdapter.buildWorkflowSpec(sc)

    val entrypointTemplate =
        workflowSpec.templates?.find { template -> template.name.equals("entrypoint") }
    val dag = entrypointTemplate?.dag
    assertNotNull(dag)
  }

  @Test
  fun `Create Workflow Spec with StartContainers entrypoint dag valid`() {
    var sc = getStartContainers()
    val workflowSpec = argoAdapter.buildWorkflowSpec(sc)

    val entrypointTemplate =
        workflowSpec.templates?.find { template -> template.name.equals("entrypoint") }
    val expected =
        listOf(
            DAGTask().name("fetchDatasetContainer-1").template("fetchDatasetContainer-1"),
            DAGTask()
                .name("fetchScenarioParametersContainer")
                .template("fetchScenarioParametersContainer")
                .dependencies(listOf("fetchDatasetContainer-1")),
            DAGTask()
                .name("applyParametersContainer")
                .template("applyParametersContainer")
                .dependencies(listOf("fetchScenarioParametersContainer")),
            DAGTask()
                .name("validateDataContainer")
                .template("validateDataContainer")
                .dependencies(listOf("applyParametersContainer")),
            DAGTask()
                .name("sendDataWarehouseContainer")
                .template("sendDataWarehouseContainer")
                .dependencies(listOf("validateDataContainer")),
            DAGTask()
                .name("preRunContainer")
                .template("preRunContainer")
                .dependencies(listOf("sendDataWarehouseContainer")),
            DAGTask()
                .name("runContainer")
                .template("runContainer")
                .dependencies(listOf("preRunContainer")),
            DAGTask()
                .name("postRunContainer")
                .template("postRunContainer")
                .dependencies(listOf("runContainer")),
        )

    assertEquals(expected, entrypointTemplate?.dag?.tasks)
  }

  @Test
  fun `Create Workflow Spec with StartContainers entrypoint dag dependencies valid`() {
    var sc = getStartContainers()
    val workflowSpec = argoAdapter.buildWorkflowSpec(sc)

    val entrypointTemplate =
        workflowSpec.templates?.find { template -> template.name.equals("entrypoint") }
    val expected =
        listOf(
            null,
            "fetchDatasetContainer-1",
            "fetchScenarioParametersContainer",
            "applyParametersContainer",
            "validateDataContainer",
            "sendDataWarehouseContainer",
            "preRunContainer",
            "runContainer",
        )

    val dependencies =
        entrypointTemplate?.dag?.tasks?.map { task -> task.dependencies?.getOrNull(0) }

    assertEquals(expected, dependencies)
  }

  @Test
  fun `Create Workflow Spec with StartContainers diamond`() {
    var sc = getStartContainersDiamond()
    val workflowSpec = argoAdapter.buildWorkflowSpec(sc)

    val entrypointTemplate =
        workflowSpec.templates?.find { template -> template.name.equals("entrypoint") }
    val expected =
        listOf(
            DAGTask().name("Diamond-A").template("Diamond-A"),
            DAGTask().name("Diamond-B").template("Diamond-B").dependencies(listOf("Diamond-A")),
            DAGTask().name("Diamond-C").template("Diamond-C").dependencies(listOf("Diamond-A")),
            DAGTask()
                .name("Diamond-D")
                .template("Diamond-D")
                .dependencies(listOf("Diamond-B", "Diamond-C")),
        )

    assertEquals(expected, entrypointTemplate?.dag?.tasks)
  }

  @Test
  fun `Create Workflow with StartContainers not null`() {
    var sc = getStartContainers()
    val workflow = argoAdapter.buildWorkflow(sc)
    assertNotNull(workflow)
  }

  @Test
  fun `Create Workflow with StartContainers generate name default`() {
    var sc = getStartContainers()
    val workflow = argoAdapter.buildWorkflow(sc)
    val expected = V1ObjectMeta().generateName("default-workflow-")
    assertEquals(expected, workflow.metadata)
  }

  @Test
  fun `Create Workflow with StartContainers generate name Scenario`() {
    var sc = getStartContainersNamed()
    val workflow = argoAdapter.buildWorkflow(sc)
    val expected = V1ObjectMeta().generateName("Scenario-1-")
    assertEquals(expected, workflow.metadata)
  }

  @Test
  fun `Create Workflow spec with StartContainers volume claim`() {
    var sc = getStartContainersDiamond()
    val workflowSpec = argoAdapter.buildWorkflowSpec(sc)
    val datasetsdir =
        V1PersistentVolumeClaim()
            .metadata(V1ObjectMeta().name("datasetsdir"))
            .spec(
                V1PersistentVolumeClaimSpec()
                    .accessModes(listOf("ReadWriteOnce"))
                    .resources(
                        V1ResourceRequirements().requests(mapOf("storage" to Quantity("1Gi")))))
    val parametersdir =
        V1PersistentVolumeClaim()
            .metadata(V1ObjectMeta().name("parametersdir"))
            .spec(
                V1PersistentVolumeClaimSpec()
                    .accessModes(listOf("ReadWriteOnce"))
                    .resources(
                        V1ResourceRequirements().requests(mapOf("storage" to Quantity("1Gi")))))
    val expected = listOf(datasetsdir, parametersdir)
    assertEquals(expected, workflowSpec.volumeClaimTemplates)
  }

  @Test
  fun `Create Template with StartContainers volume mount`() {
    val src = getScenarioRunContainer()
    val template = argoAdapter.buildTemplate(src)
    val expected =
        listOf(
            V1VolumeMount().name("datasetsdir").mountPath("/mnt/scenariorun-data"),
            V1VolumeMount().name("parametersdir").mountPath("/mnt/scenariorun-parameters"))
    assertEquals(expected, template.container?.volumeMounts)
  }

  fun getScenarioRunContainer(name: String = "default"): ScenarioRunContainer {
    var src =
        ScenarioRunContainer(
            name = name,
            image = "cosmotech/testcontainer",
        )
    return src
  }

  fun getScenarioRunContainerDependencies(
      name: String = "default",
      dependencies: List<String>? = null
  ): ScenarioRunContainer {
    var src =
        ScenarioRunContainer(
            name = name,
            image = "cosmotech/testcontainer",
            dependencies = dependencies,
        )
    return src
  }

  fun getScenarioRunContainerArgs(name: String = "default"): ScenarioRunContainer {
    var src =
        ScenarioRunContainer(
            name = name,
            image = "cosmotech/testcontainer",
            runArgs = listOf("arg1", "arg2", "arg3"))
    return src
  }

  fun getScenarioRunContainerEntrypoint(name: String = "default"): ScenarioRunContainer {
    var src =
        ScenarioRunContainer(
            name = name, image = "cosmotech/testcontainer", entrypoint = DEFAULT_ENTRY_POINT)
    return src
  }

  fun getScenarioRunContainerEnv(name: String = "default"): ScenarioRunContainer {
    var src =
        ScenarioRunContainer(
            name = name,
            image = "cosmotech/testcontainer",
            envVars = mapOf("env1" to "envvar1", "env2" to "envvar2", "env3" to "envvar3"))

    return src
  }

  fun getStartContainersRunDefaultPool(): ScenarioRunStartContainers {
    val sc =
        ScenarioRunStartContainers(
            nodeLabel = "basicpool", containers = listOf(getScenarioRunContainerEntrypoint()))
    return sc
  }

  fun getStartContainersRun(): ScenarioRunStartContainers {
    val sc =
        ScenarioRunStartContainers(
            nodeLabel = "highcpupool",
            containers = listOf(getScenarioRunContainerEntrypoint("runContainer")))
    return sc
  }

  fun getStartContainersRunNoPool(): ScenarioRunStartContainers {
    val sc =
        ScenarioRunStartContainers(
            containers = listOf(getScenarioRunContainerEntrypoint("runContainer")))
    return sc
  }

  fun getStartContainers(): ScenarioRunStartContainers {
    val sc =
        ScenarioRunStartContainers(
            nodeLabel = "highcpupool",
            containers =
                listOf(
                    getScenarioRunContainer("fetchDatasetContainer-1"),
                    getScenarioRunContainer("fetchScenarioParametersContainer"),
                    getScenarioRunContainerEntrypoint("applyParametersContainer"),
                    getScenarioRunContainerEntrypoint("validateDataContainer"),
                    getScenarioRunContainer("sendDataWarehouseContainer"),
                    getScenarioRunContainerEntrypoint("preRunContainer"),
                    getScenarioRunContainerEntrypoint("runContainer"),
                    getScenarioRunContainerEntrypoint("postRunContainer"),
                ))
    return sc
  }

  fun getStartContainersNamed(): ScenarioRunStartContainers {
    val sc =
        ScenarioRunStartContainers(
            generateName = "Scenario-1-",
            nodeLabel = "highcpupool",
            containers =
                listOf(
                    getScenarioRunContainer("fetchDatasetContainer-1"),
                    getScenarioRunContainer("fetchScenarioParametersContainer"),
                    getScenarioRunContainerEntrypoint("applyParametersContainer"),
                    getScenarioRunContainerEntrypoint("validateDataContainer"),
                    getScenarioRunContainer("sendDataWarehouseContainer"),
                    getScenarioRunContainerEntrypoint("preRunContainer"),
                    getScenarioRunContainerEntrypoint("runContainer"),
                    getScenarioRunContainerEntrypoint("postRunContainer"),
                ))
    return sc
  }

  fun getStartContainersDiamond(): ScenarioRunStartContainers {
    val sc =
        ScenarioRunStartContainers(
            containers =
                listOf(
                    getScenarioRunContainerDependencies("Diamond-A"),
                    getScenarioRunContainerDependencies(
                        "Diamond-B", dependencies = listOf("Diamond-A")),
                    getScenarioRunContainerDependencies(
                        "Diamond-C", dependencies = listOf("Diamond-A")),
                    getScenarioRunContainerDependencies(
                        "Diamond-D", dependencies = listOf("Diamond-B", "Diamond-C")),
                ))
    return sc
  }
}
