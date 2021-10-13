// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun.workflow.argo

import com.cosmotech.api.config.CsmPlatformProperties
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
import io.mockk.every
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull

private const val DEFAULT_ENTRY_POINT = "entrypoint.py"
private const val csmSimulationId = "simulationrunid"

class WorkflowBuildersTests {

  private lateinit var csmPlatformProperties: CsmPlatformProperties

  @BeforeTest
  fun setUp() {
    this.csmPlatformProperties = mockk(relaxed = true)
  }

  @Test
  fun `Template not null`() {
    val src = getScenarioRunContainer()
    val template = buildTemplate(src)
    assertNotNull(template)
  }

  @Test
  fun `Template has image`() {
    val src = getScenarioRunContainer()
    val template = buildTemplate(src)
    assertEquals(src.image, template.container?.image)
  }

  @Test
  fun `Template has image pull policy set to Always`() {
    val src = getScenarioRunContainer()
    val template = buildTemplate(src)
    assertNotNull(template.container)
    assertEquals("Always", template.container!!.imagePullPolicy)
  }

  @Test
  fun `Template has name`() {
    val name = "template name"
    val src = getScenarioRunContainer(name)
    val template = buildTemplate(src)
    assertEquals(name, template.name)
  }

  @Test
  fun `Template has args`() {
    val src = getScenarioRunContainerArgs()
    val template = buildTemplate(src)
    assertEquals(src.runArgs, template.container?.args)
  }

  @Test
  fun `Template has no args`() {
    val src = getScenarioRunContainer()
    val template = buildTemplate(src)
    assertEquals(src.runArgs, template.container?.args)
  }

  @Test
  fun `Template has simulator default entrypoint`() {
    val src = getScenarioRunContainerEntrypoint()
    val template = buildTemplate(src)
    assertEquals(listOf(DEFAULT_ENTRY_POINT), template.container?.command)
  }

  @Test
  fun `Template has simulator no entrypoint`() {
    val src = getScenarioRunContainer()
    val template = buildTemplate(src)
    assertNull(template.container?.command)
  }

  @Test
  fun `Template has default entrypoint if not defined`() {
    val src = getScenarioRunContainer()
    val template = buildTemplate(src)
    val expected: String? = null
    assertEquals(expected, template.container?.command)
  }

  @Test
  fun `Template has default env var`() {
    val src = getScenarioRunContainer()
    val template = buildTemplate(src)
    assertNull(template.container?.env)
  }

  @Test
  fun `Template has env var`() {
    val src = getScenarioRunContainerEnv()
    val template = buildTemplate(src)
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
    val sc = getStartContainersRun()
    val workflowSpec = buildWorkflowSpec(csmPlatformProperties, sc)
    assertNotNull(workflowSpec)
  }

  @Test
  fun `Create Workflow Spec with StartContainers agent pool`() {
    val workflows = mockk<CsmPlatformProperties.Argo.Workflows>(relaxed = true)
    every { workflows.nodePoolLabel } returns "agentpool"
    val argo = mockk<CsmPlatformProperties.Argo>(relaxed = true)
    every { argo.workflows } returns workflows
    every { csmPlatformProperties.argo } returns argo

    val sc = getStartContainersRun()
    val workflowSpec = buildWorkflowSpec(csmPlatformProperties, sc)
    val expected = mapOf("kubernetes.io/os" to "linux", "agentpool" to "highcpupool")

    assertEquals(expected, workflowSpec.nodeSelector)
  }

  @Test
  fun `Create Workflow Spec with StartContainers basic agent pool`() {
    val workflows = mockk<CsmPlatformProperties.Argo.Workflows>(relaxed = true)
    every { workflows.nodePoolLabel } returns "agentpool"
    val argo = mockk<CsmPlatformProperties.Argo>(relaxed = true)
    every { argo.workflows } returns workflows
    every { csmPlatformProperties.argo } returns argo

    val sc = getStartContainersRunDefaultPool()
    val workflowSpec = buildWorkflowSpec(csmPlatformProperties, sc)
    val expected = mapOf("kubernetes.io/os" to "linux", "agentpool" to "basicpool")

    assertEquals(expected, workflowSpec.nodeSelector)
  }

  @Test
  fun `Create Workflow Spec with StartContainers no pool`() {
    val sc = getStartContainersRunNoPool()
    val workflowSpec = buildWorkflowSpec(csmPlatformProperties, sc)
    val expected = mapOf("kubernetes.io/os" to "linux")

    assertEquals(expected, workflowSpec.nodeSelector)
  }

  @Test
  fun `Create Workflow Spec with StartContainers Service Account`() {
    val workflows = mockk<CsmPlatformProperties.Argo.Workflows>(relaxed = true)
    every { workflows.serviceAccountName } returns "workflow"
    val argo = mockk<CsmPlatformProperties.Argo>(relaxed = true)
    every { argo.workflows } returns workflows
    every { csmPlatformProperties.argo } returns argo

    val sc = getStartContainersRun()
    val workflowSpec = buildWorkflowSpec(csmPlatformProperties, sc)

    assertEquals("workflow", workflowSpec.serviceAccountName)
  }

  @Test
  fun `Create Workflow Spec with StartContainers Run name`() {
    val sc = getStartContainersRun()
    val workflowSpec = buildWorkflowSpec(csmPlatformProperties, sc)

    assertEquals("runContainer", workflowSpec.templates?.getOrNull(0)?.name)
  }

  @Test
  fun `Create Workflow Spec with StartContainers Entrypoint FetchDataset`() {
    val sc = getStartContainers()
    val workflowSpec = buildWorkflowSpec(csmPlatformProperties, sc)

    assertEquals("entrypoint", workflowSpec.entrypoint)
  }

  @Test
  fun `Create Workflow Spec with StartContainers entrypoint template not null`() {
    val sc = getStartContainers()
    val workflowSpec = buildWorkflowSpec(csmPlatformProperties, sc)

    val entrypointTemplate =
        workflowSpec.templates?.find { template -> template.name.equals("entrypoint") }
    assertNotNull(entrypointTemplate)
  }

  @Test
  fun `Create Workflow Spec with StartContainers entrypoint dag not null`() {
    val sc = getStartContainers()
    val workflowSpec = buildWorkflowSpec(csmPlatformProperties, sc)

    val entrypointTemplate =
        workflowSpec.templates?.find { template -> template.name.equals("entrypoint") }
    val dag = entrypointTemplate?.dag
    assertNotNull(dag)
  }

  @Test
  fun `Create Workflow Spec with StartContainers entrypoint with dependencies dag valid`() {
    val sc = getStartContainersWithDependencies()
    val workflowSpec = buildWorkflowSpec(csmPlatformProperties, sc)

    val entrypointTemplate =
        workflowSpec.templates?.find { template -> template.name.equals("entrypoint") }
    val expected =
        listOf(
            DAGTask().name("fetchDatasetContainer-1").template("fetchDatasetContainer-1"),
            DAGTask()
                .name("fetchScenarioParametersContainer")
                .template("fetchScenarioParametersContainer"),
            DAGTask()
                .name("applyParametersContainer")
                .template("applyParametersContainer")
                .dependencies(
                    listOf("fetchDatasetContainer-1", "fetchScenarioParametersContainer")),
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
                .dependencies(listOf("validateDataContainer")),
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
  fun `Create Workflow Spec with StartContainers entrypoint dag valid`() {
    val sc = getStartContainers()
    val workflowSpec = buildWorkflowSpec(csmPlatformProperties, sc)

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
    val sc = getStartContainers()
    val workflowSpec = buildWorkflowSpec(csmPlatformProperties, sc)

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
    val sc = getStartContainersDiamond()
    val workflowSpec = buildWorkflowSpec(csmPlatformProperties, sc)

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
    val sc = getStartContainers()
    val workflow = buildWorkflow(csmPlatformProperties, sc)
    assertNotNull(workflow)
  }

  @Test
  fun `Create Workflow with StartContainers generate name default`() {
    val sc = getStartContainers()
    val workflow = buildWorkflow(csmPlatformProperties, sc)
    val expected = V1ObjectMeta().generateName("default-workflow-")
    assertEquals(expected, workflow.metadata)
  }

  @Test
  fun `Create Workflow with StartContainers generate name Scenario`() {
    val sc = getStartContainersNamed()
    val workflow = buildWorkflow(csmPlatformProperties, sc)
    val expected = V1ObjectMeta().generateName("Scenario-1-")
    assertEquals(expected, workflow.metadata)
  }

  @Test
  fun `Create Workflow spec with StartContainers volume claim`() {
    val sc = getStartContainersDiamond()
    every { csmPlatformProperties.argo.workflows.storageClass } returns "cosmotech-api-test-phoenix"
    val workflowSpec = buildWorkflowSpec(csmPlatformProperties, sc)
    val dataDir =
        V1PersistentVolumeClaim()
            .metadata(V1ObjectMeta().name(VOLUME_CLAIM))
            .spec(
                V1PersistentVolumeClaimSpec()
                    .accessModes(listOf("ReadWriteMany"))
                    .storageClassName("cosmotech-api-test-phoenix")
                    .resources(
                        V1ResourceRequirements().requests(mapOf("storage" to Quantity("100Gi")))))
    val expected = listOf(dataDir)
    assertEquals(expected, workflowSpec.volumeClaimTemplates)
  }

  @Test
  fun `Create Template with StartContainers volume mount`() {
    val src = getScenarioRunContainer()
    val template = buildTemplate(src)
    val expected =
        listOf(
            V1VolumeMount()
                .name(VOLUME_CLAIM)
                .mountPath("/mnt/scenariorun-data")
                .subPath(VOLUME_CLAIM_DATASETS_SUBPATH),
            V1VolumeMount()
                .name(VOLUME_CLAIM)
                .mountPath("/mnt/scenariorun-parameters")
                .subPath(VOLUME_CLAIM_PARAMETERS_SUBPATH),
            V1VolumeMount().name("out").mountPath("/var/csmoutput"))
    assertEquals(expected, template.container?.volumeMounts)
  }

  @Test
  fun `Create Workflow with metadata labels`() {
    val sc = getStartContainersWithLabels()
    val workflow = buildWorkflowSpec(csmPlatformProperties, sc)
    val labeledTemplate =
        workflow.templates?.find { template -> template.name.equals("fetchDatasetContainer-1") }

    val expected =
        mapOf(
            "label1" to "valLabel1",
            "label2" to "valLabel2",
        )
    assertEquals(expected, labeledTemplate?.metadata?.labels)
  }

  private fun getScenarioRunContainer(name: String = "default"): ScenarioRunContainer {
    val src =
        ScenarioRunContainer(
            name = name,
            image = "cosmotech/testcontainer",
        )
    return src
  }

  private fun getScenarioRunContainerWithLabels(name: String = "default"): ScenarioRunContainer {
    val src =
        ScenarioRunContainer(
            name = name,
            image = "cosmotech/testcontainer",
            labels =
                mapOf(
                    "label1" to "valLabel1",
                    "label2" to "valLabel2",
                ),
        )
    return src
  }

  private fun getScenarioRunContainerDependencies(
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

  private fun getScenarioRunContainerArgs(name: String = "default"): ScenarioRunContainer {
    val src =
        ScenarioRunContainer(
            name = name,
            image = "cosmotech/testcontainer",
            runArgs = listOf("arg1", "arg2", "arg3"))
    return src
  }

  private fun getScenarioRunContainerEntrypoint(name: String = "default"): ScenarioRunContainer {
    val src =
        ScenarioRunContainer(
            name = name, image = "cosmotech/testcontainer", entrypoint = DEFAULT_ENTRY_POINT)
    return src
  }

  private fun getScenarioRunContainerEntrypointDependencies(
      name: String = "default",
      dependencies: List<String>? = null
  ): ScenarioRunContainer {
    val src =
        ScenarioRunContainer(
            name = name,
            image = "cosmotech/testcontainer",
            entrypoint = DEFAULT_ENTRY_POINT,
            dependencies = dependencies)
    return src
  }

  private fun getScenarioRunContainerEnv(name: String = "default"): ScenarioRunContainer {
    val src =
        ScenarioRunContainer(
            name = name,
            image = "cosmotech/testcontainer",
            envVars = mapOf("env1" to "envvar1", "env2" to "envvar2", "env3" to "envvar3"))

    return src
  }

  private fun getStartContainersRunDefaultPool(): ScenarioRunStartContainers {
    val sc =
        ScenarioRunStartContainers(
            nodeLabel = "basicpool",
            containers = listOf(getScenarioRunContainerEntrypoint()),
            csmSimulationId = csmSimulationId)
    return sc
  }

  private fun getStartContainersRun(): ScenarioRunStartContainers {
    val sc =
        ScenarioRunStartContainers(
            nodeLabel = "highcpupool",
            containers = listOf(getScenarioRunContainerEntrypoint("runContainer")),
            csmSimulationId = csmSimulationId)
    return sc
  }

  private fun getStartContainersRunNoPool(): ScenarioRunStartContainers {
    val sc =
        ScenarioRunStartContainers(
            containers = listOf(getScenarioRunContainerEntrypoint("runContainer")),
            csmSimulationId = csmSimulationId)
    return sc
  }

  private fun getStartContainers(): ScenarioRunStartContainers {
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

  private fun getStartContainersWithDependencies(): ScenarioRunStartContainers {
    val sc =
        ScenarioRunStartContainers(
            nodeLabel = "highcpupool",
            containers =
                listOf(
                    getScenarioRunContainerDependencies(
                        "fetchDatasetContainer-1", listOf("DAG_ROOT")),
                    getScenarioRunContainerDependencies(
                        "fetchScenarioParametersContainer", listOf("DAG_ROOT")),
                    getScenarioRunContainerEntrypointDependencies(
                        "applyParametersContainer",
                        listOf("fetchDatasetContainer-1", "fetchScenarioParametersContainer")),
                    getScenarioRunContainerEntrypointDependencies(
                        "validateDataContainer", listOf("applyParametersContainer")),
                    getScenarioRunContainerDependencies(
                        "sendDataWarehouseContainer", listOf("validateDataContainer")),
                    getScenarioRunContainerEntrypointDependencies(
                        "preRunContainer", listOf("validateDataContainer")),
                    getScenarioRunContainerEntrypoint("runContainer"),
                    getScenarioRunContainerEntrypoint("postRunContainer"),
                ),
            csmSimulationId = csmSimulationId)
    return sc
  }

  private fun getStartContainersWithLabels(): ScenarioRunStartContainers {
    val sc =
        ScenarioRunStartContainers(
            nodeLabel = "highcpupool",
            containers =
                listOf(
                    getScenarioRunContainerWithLabels("fetchDatasetContainer-1"),
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

  private fun getStartContainersNamed(): ScenarioRunStartContainers {
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

  private fun getStartContainersDiamond(): ScenarioRunStartContainers {
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
