// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun

import com.cosmotech.scenariorun.domain.ScenarioRunContainer
import com.cosmotech.scenariorun.domain.ScenarioRunStartContainers
import io.kubernetes.client.openapi.models.V1EnvVar
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
  fun `Create Workflow Spec with StartContainers default agent pool`() {
    var sc = getStartContainersRunDefaultPool()
    val workflowSpec = argoAdapter.buildWorkflowSpec(sc)
    val expected = mapOf("kubernetes.io/os" to "linux", "agentpool" to "basicpool")

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
  fun `Create Workflow Spec with StartContainers entrypoint step not null`() {
    var sc = getStartContainers()
    val workflowSpec = argoAdapter.buildWorkflowSpec(sc)

    val entrypointTemplate = workflowSpec.templates?.find {
      template -> template.name.equals("entrypoint")
    }
    assertNotNull(entrypointTemplate)
  }

  fun getScenarioRunContainer(name: String = "default"): ScenarioRunContainer {
    var src =
        ScenarioRunContainer(
           name = name, 
            image = "cosmotech/testcontainer",
        )
    return src
  }

  fun getScenarioRunContainerArgs(name: String = "default"): ScenarioRunContainer {
    var src =
        ScenarioRunContainer(
           name = name, 
            image = "cosmotech/testcontainer", runArgs = listOf("arg1", "arg2", "arg3"))
    return src
  }

  fun getScenarioRunContainerEntrypoint(name: String = "default"): ScenarioRunContainer {
    var src =
        ScenarioRunContainer(
           name = name, 
          image = "cosmotech/testcontainer", entrypoint = DEFAULT_ENTRY_POINT)
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
    val sc = ScenarioRunStartContainers(containers = listOf(getScenarioRunContainerEntrypoint()))
    return sc
  }

  fun getStartContainersRun(): ScenarioRunStartContainers {
    val sc =
        ScenarioRunStartContainers(
            nodeLabel = "highcpupool",
            containers = listOf(getScenarioRunContainerEntrypoint("runContainer")))
    return sc
  }

  fun getStartContainers(): ScenarioRunStartContainers {
    val sc =
        ScenarioRunStartContainers(
            nodeLabel = "highcpupool",
            containers = listOf(
            getScenarioRunContainer("fetchDatasetContainer-1"),
            getScenarioRunContainer("fetchScenarioParametersContainer"),
            getScenarioRunContainerEntrypoint("applyParametersContainer"),
            getScenarioRunContainerEntrypoint("validateDataContainer"),
            getScenarioRunContainer("sendDataWarehouseContainer"),
            getScenarioRunContainerEntrypoint("preRunContainer"),
            getScenarioRunContainerEntrypoint("runContainer"),
            getScenarioRunContainerEntrypoint("postRunContainer"),
          )
        )
    return sc
  }

}
