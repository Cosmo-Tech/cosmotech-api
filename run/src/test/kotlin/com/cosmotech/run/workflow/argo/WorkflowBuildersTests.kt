// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run.workflow.argo

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.run.domain.RunContainer
import com.cosmotech.run.domain.RunStartContainers
import io.argoproj.workflow.models.IoArgoprojWorkflowV1alpha1DAGTask
import io.kubernetes.client.custom.Quantity
import io.kubernetes.client.openapi.models.V1EnvVar
import io.kubernetes.client.openapi.models.V1ObjectMeta
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimSpec
import io.kubernetes.client.openapi.models.V1VolumeMount
import io.kubernetes.client.openapi.models.V1VolumeResourceRequirements
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
    val src = getRunContainer()
    val template = buildTemplate(src, csmPlatformProperties)
    assertNotNull(template)
  }

  @Test
  fun `Template has image`() {
    val src = getRunContainer()
    val template = buildTemplate(src, csmPlatformProperties)
    assertEquals(src.image, template.container?.image)
  }

  @Test
  fun `Template has image pull policy set to IfNotPresent`() {
    every { csmPlatformProperties.argo.imagePullPolicy } returns "IfNotPresent"
    val src = getRunContainer()
    val template = buildTemplate(src, csmPlatformProperties)
    assertNotNull(template.container)
    assertEquals("IfNotPresent", template.container!!.imagePullPolicy)
  }

  @Test
  fun `Template has image pull policy set to Always`() {
    every { csmPlatformProperties.argo.imagePullPolicy } returns "Always"
    val src = getRunContainer()
    val template = buildTemplate(src, csmPlatformProperties)
    assertNotNull(template.container)
    assertEquals("Always", template.container!!.imagePullPolicy)
  }

  @Test
  fun `Solution has alwaysPull set to true`() {
    val src = getRunContainer()
    val template = buildTemplate(src, csmPlatformProperties, true)
    assertNotNull(template.container)
    assertEquals("Always", template.container!!.imagePullPolicy)
  }

  @Test
  fun `Template has name`() {
    val name = "template name"
    val src = getRunContainer(name)
    val template = buildTemplate(src, csmPlatformProperties)
    assertEquals(name, template.name)
  }

  @Test
  fun `Template has args`() {
    val src = getRunContainerArgs()
    val template = buildTemplate(src, csmPlatformProperties)
    assertEquals(src.runArgs, template.container?.args)
  }

  @Test
  fun `Template has no args`() {
    val src = getRunContainer()
    val template = buildTemplate(src, csmPlatformProperties)
    assertEquals(src.runArgs, template.container?.args)
  }

  @Test
  fun `Template has simulator default entrypoint`() {
    val src = getRunContainerEntrypoint()
    val template = buildTemplate(src, csmPlatformProperties)
    assertEquals(listOf(DEFAULT_ENTRY_POINT), template.container?.command)
  }

  @Test
  fun `Template has simulator no entrypoint`() {
    val src = getRunContainer()
    val template = buildTemplate(src, csmPlatformProperties)
    assertNull(template.container?.command)
  }

  @Test
  fun `Template has default entrypoint if not defined`() {
    val src = getRunContainer()
    val template = buildTemplate(src, csmPlatformProperties)
    val expected: String? = null
    assertEquals(expected, template.container?.command)
  }

  @Test
  fun `Template has default env var`() {
    val src = getRunContainer()
    val template = buildTemplate(src, csmPlatformProperties)
    assertNull(template.container?.env)
  }

  @Test
  fun `Template has env var`() {
    val src = getRunContainerEnv()
    val template = buildTemplate(src, csmPlatformProperties)
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
    val workflowSpec = buildWorkflowSpec(csmPlatformProperties, sc, null)
    assertNotNull(workflowSpec)
  }

  @Test
  fun `Create Workflow Spec with StartContainers agent pool`() {
    val workflows = mockk<CsmPlatformProperties.Argo.Workflows>(relaxed = true)
    every { workflows.nodePoolLabel } returns ""
    val argo = mockk<CsmPlatformProperties.Argo>(relaxed = true)
    every { argo.workflows } returns workflows
    every { csmPlatformProperties.argo } returns argo

    val sc = getStartContainersRun()
    val workflowSpec = buildWorkflowSpec(csmPlatformProperties, sc, null)
    val expected = mapOf("kubernetes.io/os" to "linux", "cosmotech.com/tier" to "compute")

    assertEquals(expected, workflowSpec.nodeSelector)
  }

  @Test
  fun `Create Workflow Spec with StartContainers basic agent pool`() {
    val workflows = mockk<CsmPlatformProperties.Argo.Workflows>(relaxed = true)
    every { workflows.nodePoolLabel } returns ""
    val argo = mockk<CsmPlatformProperties.Argo>(relaxed = true)
    every { argo.workflows } returns workflows
    every { csmPlatformProperties.argo } returns argo

    val sc = getStartContainersRunDefaultPool()
    val workflowSpec = buildWorkflowSpec(csmPlatformProperties, sc, null)
    val expected = mapOf("kubernetes.io/os" to "linux", "cosmotech.com/tier" to "compute")

    assertEquals(expected, workflowSpec.nodeSelector)
  }

  @Test
  fun `Create Workflow Spec with StartContainers no pool`() {
    val sc = getStartContainersRunNoPool()
    val workflowSpec = buildWorkflowSpec(csmPlatformProperties, sc, null)
    val expected = mapOf("kubernetes.io/os" to "linux", "cosmotech.com/tier" to "compute")

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
    val workflowSpec = buildWorkflowSpec(csmPlatformProperties, sc, null)

    assertEquals("workflow", workflowSpec.serviceAccountName)
  }

  @Test
  fun `Create Workflow Spec with StartContainers Run name`() {
    val sc = getStartContainersRun()
    val workflowSpec = buildWorkflowSpec(csmPlatformProperties, sc, null)

    assertEquals("runcontainer", workflowSpec.templates?.getOrNull(0)?.name)
  }

  @Test
  fun `Create Workflow Spec with StartContainers Entrypoint FetchDataset`() {
    val sc = getStartContainers()
    val workflowSpec = buildWorkflowSpec(csmPlatformProperties, sc, null)

    assertEquals("entrypoint", workflowSpec.entrypoint)
  }

  @Test
  fun `Create Workflow Spec with StartContainers entrypoint template not null`() {
    val sc = getStartContainers()
    val workflowSpec = buildWorkflowSpec(csmPlatformProperties, sc, null)

    val entrypointTemplate =
        workflowSpec.templates?.find { template -> template.name.equals("entrypoint") }
    assertNotNull(entrypointTemplate)
  }

  @Test
  fun `Create Workflow Spec with StartContainers entrypoint dag not null`() {
    val sc = getStartContainers()
    val workflowSpec = buildWorkflowSpec(csmPlatformProperties, sc, null)

    val entrypointTemplate =
        workflowSpec.templates?.find { template -> template.name.equals("entrypoint") }
    val dag = entrypointTemplate?.dag
    assertNotNull(dag)
  }

  @Test
  fun `Create Workflow Spec with StartContainers entrypoint with dependencies dag valid`() {
    val sc = getStartContainersWithDependencies()
    val workflowSpec = buildWorkflowSpec(csmPlatformProperties, sc, null)

    val entrypointTemplate =
        workflowSpec.templates?.find { template -> template.name.equals("entrypoint") }
    val expected =
        listOf(
            IoArgoprojWorkflowV1alpha1DAGTask()
                .name("fetchdatasetcontainer-1")
                .template("fetchdatasetcontainer-1"),
            IoArgoprojWorkflowV1alpha1DAGTask()
                .name("fetchscenarioparameterscontainer")
                .template("fetchscenarioparameterscontainer"),
            IoArgoprojWorkflowV1alpha1DAGTask()
                .name("applyparameterscontainer")
                .template("applyparameterscontainer")
                .dependencies(
                    listOf("fetchdatasetcontainer-1", "fetchscenarioparameterscontainer")),
            IoArgoprojWorkflowV1alpha1DAGTask()
                .name("validatedatacontainer")
                .template("validatedatacontainer")
                .dependencies(listOf("applyparameterscontainer")),
            IoArgoprojWorkflowV1alpha1DAGTask()
                .name("senddatawarehousecontainer")
                .template("senddatawarehousecontainer")
                .dependencies(listOf("validatedatacontainer")),
            IoArgoprojWorkflowV1alpha1DAGTask()
                .name("preruncontainer")
                .template("preruncontainer")
                .dependencies(listOf("validatedatacontainer")),
            IoArgoprojWorkflowV1alpha1DAGTask()
                .name("runcontainer")
                .template("runcontainer")
                .dependencies(listOf("preruncontainer")),
            IoArgoprojWorkflowV1alpha1DAGTask()
                .name("postruncontainer")
                .template("postruncontainer")
                .dependencies(listOf("runcontainer")),
        )

    assertEquals(expected, entrypointTemplate?.dag?.tasks)
  }

  @Test
  fun `Create Workflow Spec with StartContainers entrypoint dag valid`() {
    val sc = getStartContainers()
    val workflowSpec = buildWorkflowSpec(csmPlatformProperties, sc, null)

    val entrypointTemplate =
        workflowSpec.templates?.find { template -> template.name.equals("entrypoint") }
    val expected =
        listOf(
            IoArgoprojWorkflowV1alpha1DAGTask()
                .name("fetchdatasetcontainer-1")
                .template("fetchdatasetcontainer-1"),
            IoArgoprojWorkflowV1alpha1DAGTask()
                .name("fetchscenarioparameterscontainer")
                .template("fetchscenarioparameterscontainer")
                .dependencies(listOf("fetchdatasetcontainer-1")),
            IoArgoprojWorkflowV1alpha1DAGTask()
                .name("applyparameterscontainer")
                .template("applyparameterscontainer")
                .dependencies(listOf("fetchscenarioparameterscontainer")),
            IoArgoprojWorkflowV1alpha1DAGTask()
                .name("validatedatacontainer")
                .template("validatedatacontainer")
                .dependencies(listOf("applyparameterscontainer")),
            IoArgoprojWorkflowV1alpha1DAGTask()
                .name("senddatawarehousecontainer")
                .template("senddatawarehousecontainer")
                .dependencies(listOf("validatedatacontainer")),
            IoArgoprojWorkflowV1alpha1DAGTask()
                .name("preruncontainer")
                .template("preruncontainer")
                .dependencies(listOf("senddatawarehousecontainer")),
            IoArgoprojWorkflowV1alpha1DAGTask()
                .name("runcontainer")
                .template("runcontainer")
                .dependencies(listOf("preruncontainer")),
            IoArgoprojWorkflowV1alpha1DAGTask()
                .name("postruncontainer")
                .template("postruncontainer")
                .dependencies(listOf("runcontainer")),
        )

    assertEquals(expected, entrypointTemplate?.dag?.tasks)
  }

  @Test
  fun `Create Workflow Spec with StartContainers entrypoint dag dependencies valid`() {
    val sc = getStartContainers()
    val workflowSpec = buildWorkflowSpec(csmPlatformProperties, sc, null)

    val entrypointTemplate =
        workflowSpec.templates?.find { template -> template.name.equals("entrypoint") }
    val expected =
        listOf(
            null,
            "fetchdatasetcontainer-1",
            "fetchscenarioparameterscontainer",
            "applyparameterscontainer",
            "validatedatacontainer",
            "senddatawarehousecontainer",
            "preruncontainer",
            "runcontainer",
        )

    val dependencies =
        entrypointTemplate?.dag?.tasks?.map { task -> task.dependencies?.getOrNull(0) }

    assertEquals(expected, dependencies)
  }

  @Test
  fun `Create Workflow Spec with StartContainers diamond`() {
    val sc = getStartContainersDiamond()
    val workflowSpec = buildWorkflowSpec(csmPlatformProperties, sc, null)

    val entrypointTemplate =
        workflowSpec.templates?.find { template -> template.name.equals("entrypoint") }
    val expected =
        listOf(
            IoArgoprojWorkflowV1alpha1DAGTask().name("diamond-a").template("diamond-a"),
            IoArgoprojWorkflowV1alpha1DAGTask()
                .name("diamond-b")
                .template("diamond-b")
                .dependencies(listOf("diamond-a")),
            IoArgoprojWorkflowV1alpha1DAGTask()
                .name("diamond-c")
                .template("diamond-c")
                .dependencies(listOf("diamond-a")),
            IoArgoprojWorkflowV1alpha1DAGTask()
                .name("diamond-d")
                .template("diamond-d")
                .dependencies(listOf("diamond-b", "diamond-c")),
        )

    assertEquals(expected, entrypointTemplate?.dag?.tasks)
  }

  @Test
  fun `Create Workflow with StartContainers not null`() {
    val sc = getStartContainers()
    val workflow = buildWorkflow(csmPlatformProperties, sc, null)
    assertNotNull(workflow)
  }

  @Test
  fun `Create Workflow with StartContainers generate name default`() {
    val sc = getStartContainers()
    val workflow = buildWorkflow(csmPlatformProperties, sc, null)
    val expected = V1ObjectMeta().generateName("default-workflow-").labels(null)
    assertEquals(expected, workflow.metadata)
  }

  @Test
  fun `Create Workflow with StartContainers generate name Scenario`() {
    val sc = getStartContainersNamed()
    val workflow = buildWorkflow(csmPlatformProperties, sc, null)
    val expected = V1ObjectMeta().generateName("Scenario-1-").labels(null)
    assertEquals(expected, workflow.metadata)
  }

  @Test
  fun `Create Workflow spec with StartContainers volume claim default values`() {
    val sc = getStartContainersDiamond()
    val workflowSpec = buildWorkflowSpec(csmPlatformProperties, sc, null)
    val dataDir =
        V1PersistentVolumeClaim()
            .metadata(V1ObjectMeta().name(VOLUME_CLAIM))
            .spec(
                V1PersistentVolumeClaimSpec()
                    .accessModes(emptyList())
                    .storageClassName(null)
                    .resources(V1VolumeResourceRequirements().requests(emptyMap())))
    val expected = listOf(dataDir)
    assertEquals(expected, workflowSpec.volumeClaimTemplates)
  }

  @Test
  fun `Create Workflow spec with StartContainers volume claim`() {
    val sc = getStartContainersDiamond()
    every { csmPlatformProperties.argo.workflows.storageClass } returns "cosmotech-api-test-phoenix"
    every { csmPlatformProperties.argo.workflows.accessModes } returns listOf("ReadWriteMany")
    every { csmPlatformProperties.argo.workflows.requests } returns mapOf("storage" to "300Gi")
    val workflowSpec = buildWorkflowSpec(csmPlatformProperties, sc, null)
    val dataDir =
        V1PersistentVolumeClaim()
            .metadata(V1ObjectMeta().name(VOLUME_CLAIM))
            .spec(
                V1PersistentVolumeClaimSpec()
                    .accessModes(listOf("ReadWriteMany"))
                    .storageClassName("cosmotech-api-test-phoenix")
                    .resources(
                        V1VolumeResourceRequirements()
                            .requests(mapOf("storage" to Quantity("300Gi")))))
    val expected = listOf(dataDir)
    assertEquals(expected, workflowSpec.volumeClaimTemplates)
  }

  @Test
  fun `Create Template with StartContainers volume mount`() {
    val src = getRunContainer()
    val template = buildTemplate(src, csmPlatformProperties)
    val expected =
        listOf(
            V1VolumeMount()
                .name(VOLUME_CLAIM)
                .mountPath("/mnt/scenariorun-data")
                .subPath(VOLUME_CLAIM_DATASETS_SUBPATH),
            V1VolumeMount()
                .name(VOLUME_CLAIM)
                .mountPath("/mnt/scenariorun-parameters")
                .subPath(VOLUME_CLAIM_PARAMETERS_SUBPATH))
    assertEquals(expected, template.container?.volumeMounts)
  }

  @Test
  fun `Create Workflow with metadata labels`() {
    val sc = getStartContainersWithLabels()
    val workflow = buildWorkflowSpec(csmPlatformProperties, sc, null)
    val labeledTemplate =
        workflow.templates?.find { template -> template.name.equals("fetchdatasetcontainer-1") }

    val expected =
        mapOf(
            "label1" to "valLabel1",
            "label2" to "valLabel2",
        )
    assertEquals(expected, labeledTemplate?.metadata?.labels)
  }

  private fun getRunContainer(name: String = "default"): RunContainer {
    val src =
        RunContainer(
            name = name,
            image = "cosmotech/testcontainer",
        )
    return src
  }

  private fun getRunContainerWithLabels(name: String = "default"): RunContainer {
    val src =
        RunContainer(
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

  private fun getRunContainerDependencies(
      name: String = "default",
      dependencies: List<String>? = null
  ): RunContainer {
    val src =
        RunContainer(
            name = name,
            image = "cosmotech/testcontainer",
            dependencies = dependencies,
        )
    return src
  }

  private fun getRunContainerArgs(name: String = "default"): RunContainer {
    val src =
        RunContainer(
            name = name,
            image = "cosmotech/testcontainer",
            runArgs = listOf("arg1", "arg2", "arg3"))
    return src
  }

  private fun getRunContainerEntrypoint(name: String = "default"): RunContainer {
    val src =
        RunContainer(
            name = name, image = "cosmotech/testcontainer", entrypoint = DEFAULT_ENTRY_POINT)
    return src
  }

  private fun getRunContainerEntrypointDependencies(
      name: String = "default",
      dependencies: List<String>? = null
  ): RunContainer {
    val src =
        RunContainer(
            name = name,
            image = "cosmotech/testcontainer",
            entrypoint = DEFAULT_ENTRY_POINT,
            dependencies = dependencies)
    return src
  }

  private fun getRunContainerEnv(name: String = "default"): RunContainer {
    val src =
        RunContainer(
            name = name,
            image = "cosmotech/testcontainer",
            envVars = mapOf("env1" to "envvar1", "env2" to "envvar2", "env3" to "envvar3"))

    return src
  }

  private fun getStartContainersRunDefaultPool(): RunStartContainers {
    val sc =
        RunStartContainers(
            nodeLabel = "basicpool",
            containers = listOf(getRunContainerEntrypoint()),
            csmSimulationId = csmSimulationId)
    return sc
  }

  private fun getStartContainersRun(): RunStartContainers {
    val sc =
        RunStartContainers(
            nodeLabel = "highcpupool",
            containers = listOf(getRunContainerEntrypoint("runcontainer")),
            csmSimulationId = csmSimulationId)
    return sc
  }

  private fun getStartContainersRunNoPool(): RunStartContainers {
    val sc =
        RunStartContainers(
            containers = listOf(getRunContainerEntrypoint("runcontainer")),
            csmSimulationId = csmSimulationId)
    return sc
  }

  private fun getStartContainers(): RunStartContainers {
    val sc =
        RunStartContainers(
            nodeLabel = "highcpupool",
            containers =
                listOf(
                    getRunContainer("fetchdatasetcontainer-1"),
                    getRunContainer("fetchscenarioparameterscontainer"),
                    getRunContainerEntrypoint("applyparameterscontainer"),
                    getRunContainerEntrypoint("validatedatacontainer"),
                    getRunContainer("senddatawarehousecontainer"),
                    getRunContainerEntrypoint("preruncontainer"),
                    getRunContainerEntrypoint("runcontainer"),
                    getRunContainerEntrypoint("postruncontainer"),
                ),
            csmSimulationId = csmSimulationId)
    return sc
  }

  private fun getStartContainersWithDependencies(): RunStartContainers {
    val sc =
        RunStartContainers(
            nodeLabel = "highcpupool",
            containers =
                listOf(
                    getRunContainerDependencies("fetchdatasetcontainer-1", listOf("DAG_ROOT")),
                    getRunContainerDependencies(
                        "fetchscenarioparameterscontainer", listOf("DAG_ROOT")),
                    getRunContainerEntrypointDependencies(
                        "applyparameterscontainer",
                        listOf("fetchdatasetcontainer-1", "fetchscenarioparameterscontainer")),
                    getRunContainerEntrypointDependencies(
                        "validatedatacontainer", listOf("applyparameterscontainer")),
                    getRunContainerDependencies(
                        "senddatawarehousecontainer", listOf("validatedatacontainer")),
                    getRunContainerEntrypointDependencies(
                        "preruncontainer", listOf("validatedatacontainer")),
                    getRunContainerEntrypoint("runcontainer"),
                    getRunContainerEntrypoint("postruncontainer"),
                ),
            csmSimulationId = csmSimulationId)
    return sc
  }

  private fun getStartContainersWithLabels(): RunStartContainers {
    val sc =
        RunStartContainers(
            nodeLabel = "highcpupool",
            containers =
                listOf(
                    getRunContainerWithLabels("fetchdatasetcontainer-1"),
                    getRunContainer("fetchscenarioparameterscontainer"),
                    getRunContainerEntrypoint("applyparameterscontainer"),
                    getRunContainerEntrypoint("validatedatacontainer"),
                    getRunContainer("senddatawarehousecontainer"),
                    getRunContainerEntrypoint("preruncontainer"),
                    getRunContainerEntrypoint("runcontainer"),
                    getRunContainerEntrypoint("postruncontainer"),
                ),
            csmSimulationId = csmSimulationId)
    return sc
  }

  private fun getStartContainersNamed(): RunStartContainers {
    val sc =
        RunStartContainers(
            generateName = "Scenario-1-",
            nodeLabel = "highcpupool",
            containers =
                listOf(
                    getRunContainer("fetchdatasetcontainer-1"),
                    getRunContainer("fetchscenarioparameterscontainer"),
                    getRunContainerEntrypoint("applyparameterscontainer"),
                    getRunContainerEntrypoint("validatedatacontainer"),
                    getRunContainer("senddatawarehousecontainer"),
                    getRunContainerEntrypoint("preruncontainer"),
                    getRunContainerEntrypoint("runcontainer"),
                    getRunContainerEntrypoint("postruncontainer"),
                ),
            csmSimulationId = csmSimulationId)
    return sc
  }

  private fun getStartContainersDiamond(): RunStartContainers {
    val sc =
        RunStartContainers(
            containers =
                listOf(
                    getRunContainerDependencies("Diamond-A"),
                    getRunContainerDependencies("Diamond-B", dependencies = listOf("Diamond-A")),
                    getRunContainerDependencies("Diamond-C", dependencies = listOf("Diamond-A")),
                    getRunContainerDependencies(
                        "Diamond-D", dependencies = listOf("Diamond-B", "Diamond-C")),
                ),
            csmSimulationId = csmSimulationId)
    return sc
  }
}
