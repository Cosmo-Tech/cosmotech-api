// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun.workflow.argo

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.scenariorun.domain.ScenarioRunContainer
import com.cosmotech.scenariorun.domain.ScenarioRunStartContainers
import io.argoproj.workflow.models.DAGTask
import io.kubernetes.client.custom.Quantity
import io.kubernetes.client.openapi.models.*
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.junit.jupiter.api.Assertions

private const val API_VERSION = "v1"
private const val DEFAULT_ENTRY_POINT = "entrypoint.py"
private const val csmSimulationId = "simulationrunid"

class ArgoWorkflowServiceTests {

  private lateinit var csmPlatformProperties: CsmPlatformProperties

  private lateinit var argoWorkflowService: ArgoWorkflowService

  @BeforeTest
  fun setUp() {
    this.csmPlatformProperties = mockk(relaxed = true)
    this.argoWorkflowService = ArgoWorkflowService(API_VERSION, csmPlatformProperties)
  }

  @Test
  fun `Template not null`() {
    val src = getScenarioRunContainer()
    val template = argoWorkflowService.buildTemplate(src)
    Assertions.assertNotNull(template)
  }

  @Test
  fun `Template has image`() {
    val src = getScenarioRunContainer()
    val template = argoWorkflowService.buildTemplate(src)
    Assertions.assertEquals(src.image, template.container?.image)
  }

  @Test
  fun `Template has name`() {
    val name = "template name"
    val src = getScenarioRunContainer(name)
    val template = argoWorkflowService.buildTemplate(src)
    Assertions.assertEquals(name, template.name)
  }

  @Test
  fun `Template has args`() {
    val src = getScenarioRunContainerArgs()
    val template = argoWorkflowService.buildTemplate(src)
    Assertions.assertEquals(src.runArgs, template.container?.args)
  }

  @Test
  fun `Template has no args`() {
    val src = getScenarioRunContainer()
    val template = argoWorkflowService.buildTemplate(src)
    Assertions.assertEquals(src.runArgs, template.container?.args)
  }

  @Test
  fun `Template has simulator default entrypoint`() {
    val src = getScenarioRunContainerEntrypoint()
    val template = argoWorkflowService.buildTemplate(src)
    Assertions.assertEquals(listOf(DEFAULT_ENTRY_POINT), template.container?.command)
  }

  @Test
  fun `Template has simulator no entrypoint`() {
    val src = getScenarioRunContainer()
    val template = argoWorkflowService.buildTemplate(src)
    Assertions.assertNull(template.container?.command)
  }

  @Test
  fun `Template has default entrypoint if not defined`() {
    val src = getScenarioRunContainer()
    val template = argoWorkflowService.buildTemplate(src)
    val expected: String? = null
    Assertions.assertEquals(expected, template.container?.command)
  }

  @Test
  fun `Template has default env var`() {
    val src = getScenarioRunContainer()
    val template = argoWorkflowService.buildTemplate(src)
    Assertions.assertNull(template.container?.env)
  }

  @Test
  fun `Template has env var`() {
    val src = getScenarioRunContainerEnv()
    val template = argoWorkflowService.buildTemplate(src)
    val expected =
        listOf(
            V1EnvVar().name("env1").value("envvar1"),
            V1EnvVar().name("env2").value("envvar2"),
            V1EnvVar().name("env3").value("envvar3"),
        )

    Assertions.assertEquals(expected, template.container?.env)
  }

  @Test
  fun `Create Workflow Spec with StartContainers not null`() {
    val sc = getStartContainersRun()
    val workflowSpec = argoWorkflowService.buildWorkflowSpec(sc)
    Assertions.assertNotNull(workflowSpec)
  }

  @Test
  fun `Create Workflow Spec with StartContainers agent pool`() {
    val sc = getStartContainersRun()
    val workflowSpec = argoWorkflowService.buildWorkflowSpec(sc)
    val expected = mapOf("kubernetes.io/os" to "linux", "agentpool" to "highcpupool")

    Assertions.assertTrue(expected.equals(workflowSpec.nodeSelector))
  }

  @Test
  fun `Create Workflow Spec with StartContainers basic agent pool`() {
    val sc = getStartContainersRunDefaultPool()
    val workflowSpec = argoWorkflowService.buildWorkflowSpec(sc)
    val expected = mapOf("kubernetes.io/os" to "linux", "agentpool" to "basicpool")

    Assertions.assertTrue(expected.equals(workflowSpec.nodeSelector))
  }

  @Test
  fun `Create Workflow Spec with StartContainers no pool`() {
    val sc = getStartContainersRunNoPool()
    val workflowSpec = argoWorkflowService.buildWorkflowSpec(sc)
    val expected = mapOf("kubernetes.io/os" to "linux")

    Assertions.assertTrue(expected.equals(workflowSpec.nodeSelector))
  }

  @Test
  fun `Create Workflow Spec with StartContainers Service Account`() {
    val sc = getStartContainersRun()
    val workflowSpec = argoWorkflowService.buildWorkflowSpec(sc)

    Assertions.assertEquals("workflow", workflowSpec.serviceAccountName)
  }

  @Test
  fun `Create Workflow Spec with StartContainers Run name`() {
    val sc = getStartContainersRun()
    val workflowSpec = argoWorkflowService.buildWorkflowSpec(sc)

    Assertions.assertEquals("runContainer", workflowSpec.templates?.getOrNull(0)?.name)
  }

  @Test
  fun `Create Workflow Spec with StartContainers Entrypoint FetchDataset`() {
    val sc = getStartContainers()
    val workflowSpec = argoWorkflowService.buildWorkflowSpec(sc)

    Assertions.assertEquals("entrypoint", workflowSpec.entrypoint)
  }

  @Test
  fun `Create Workflow Spec with StartContainers entrypoint template not null`() {
    val sc = getStartContainers()
    val workflowSpec = argoWorkflowService.buildWorkflowSpec(sc)

    val entrypointTemplate =
        workflowSpec.templates?.find { template -> template.name.equals("entrypoint") }
    Assertions.assertNotNull(entrypointTemplate)
  }

  @Test
  fun `Create Workflow Spec with StartContainers entrypoint dag not null`() {
    val sc = getStartContainers()
    val workflowSpec = argoWorkflowService.buildWorkflowSpec(sc)

    val entrypointTemplate =
        workflowSpec.templates?.find { template -> template.name.equals("entrypoint") }
    val dag = entrypointTemplate?.dag
    Assertions.assertNotNull(dag)
  }

  @Test
  fun `Create Workflow Spec with StartContainers entrypoint dag valid`() {
    val sc = getStartContainers()
    val workflowSpec = argoWorkflowService.buildWorkflowSpec(sc)

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

    Assertions.assertEquals(expected, entrypointTemplate?.dag?.tasks)
  }

  @Test
  fun `Create Workflow Spec with StartContainers entrypoint dag dependencies valid`() {
    val sc = getStartContainers()
    val workflowSpec = argoWorkflowService.buildWorkflowSpec(sc)

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

    Assertions.assertEquals(expected, dependencies)
  }

  @Test
  fun `Create Workflow Spec with StartContainers diamond`() {
    val sc = getStartContainersDiamond()
    val workflowSpec = argoWorkflowService.buildWorkflowSpec(sc)

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

    Assertions.assertEquals(expected, entrypointTemplate?.dag?.tasks)
  }

  @Test
  fun `Create Workflow with StartContainers not null`() {
    val sc = getStartContainers()
    val workflow = argoWorkflowService.buildWorkflow(sc)
    Assertions.assertNotNull(workflow)
  }

  @Test
  fun `Create Workflow with StartContainers generate name default`() {
    val sc = getStartContainers()
    val workflow = argoWorkflowService.buildWorkflow(sc)
    val expected = V1ObjectMeta().generateName("default-workflow-")
    Assertions.assertEquals(expected, workflow.metadata)
  }

  @Test
  fun `Create Workflow with StartContainers generate name Scenario`() {
    val sc = getStartContainersNamed()
    val workflow = argoWorkflowService.buildWorkflow(sc)
    val expected = V1ObjectMeta().generateName("Scenario-1-")
    Assertions.assertEquals(expected, workflow.metadata)
  }

  @Test
  fun `Create Workflow spec with StartContainers volume claim`() {
    val sc = getStartContainersDiamond()
    val workflowSpec = argoWorkflowService.buildWorkflowSpec(sc)
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
    Assertions.assertEquals(expected, workflowSpec.volumeClaimTemplates)
  }

  @Test
  fun `Create Template with StartContainers volume mount`() {
    val src = getScenarioRunContainer()
    val template = argoWorkflowService.buildTemplate(src)
    val expected =
        listOf(
            V1VolumeMount().name("datasetsdir").mountPath("/mnt/scenariorun-data"),
            V1VolumeMount().name("parametersdir").mountPath("/mnt/scenariorun-parameters"))
    Assertions.assertEquals(expected, template.container?.volumeMounts)
  }

  fun getScenarioRunContainer(name: String = "default"): ScenarioRunContainer {
    val src =
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
    val src =
        ScenarioRunContainer(
            name = name,
            image = "cosmotech/testcontainer",
            dependencies = dependencies,
        )
    return src
  }

  fun getScenarioRunContainerArgs(name: String = "default"): ScenarioRunContainer {
    val src =
        ScenarioRunContainer(
            name = name,
            image = "cosmotech/testcontainer",
            runArgs = listOf("arg1", "arg2", "arg3"))
    return src
  }

  fun getScenarioRunContainerEntrypoint(name: String = "default"): ScenarioRunContainer {
    val src =
        ScenarioRunContainer(
            name = name, image = "cosmotech/testcontainer", entrypoint = DEFAULT_ENTRY_POINT)
    return src
  }

  fun getScenarioRunContainerEnv(name: String = "default"): ScenarioRunContainer {
    val src =
        ScenarioRunContainer(
            name = name,
            image = "cosmotech/testcontainer",
            envVars = mapOf("env1" to "envvar1", "env2" to "envvar2", "env3" to "envvar3"))

    return src
  }

  fun getStartContainersRunDefaultPool(): ScenarioRunStartContainers {
    val sc =
        ScenarioRunStartContainers(
            nodeLabel = "basicpool",
            containers = listOf(getScenarioRunContainerEntrypoint()),
            csmSimulationId = csmSimulationId)
    return sc
  }

  fun getStartContainersRun(): ScenarioRunStartContainers {
    val sc =
        ScenarioRunStartContainers(
            nodeLabel = "highcpupool",
            containers = listOf(getScenarioRunContainerEntrypoint("runContainer")),
            csmSimulationId = csmSimulationId)
    return sc
  }

  fun getStartContainersRunNoPool(): ScenarioRunStartContainers {
    val sc =
        ScenarioRunStartContainers(
            containers = listOf(getScenarioRunContainerEntrypoint("runContainer")),
            csmSimulationId = csmSimulationId)
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
                ),
            csmSimulationId = csmSimulationId)
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
                ),
            csmSimulationId = csmSimulationId)
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
                ),
            csmSimulationId = csmSimulationId)
    return sc
  }
}
