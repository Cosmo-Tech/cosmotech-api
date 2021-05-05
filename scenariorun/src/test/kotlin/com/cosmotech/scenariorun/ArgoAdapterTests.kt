// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun

import com.cosmotech.scenariorun.domain.ScenarioRunContainer
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
    val src = getScenarioRunContainer()
    val name = "template name"
    val template = argoAdapter.buildTemplate(src, name)
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

  fun getScenarioRunContainer(): ScenarioRunContainer {
    var src =
        ScenarioRunContainer(
            image = "cosmotech/testcontainer",
        )
    return src
  }

  fun getScenarioRunContainerArgs(): ScenarioRunContainer {
    var src =
        ScenarioRunContainer(
            image = "cosmotech/testcontainer", runArgs = listOf("arg1", "arg2", "arg3"))
    return src
  }

  fun getScenarioRunContainerEntrypoint(): ScenarioRunContainer {
    var src =
        ScenarioRunContainer(image = "cosmotech/testcontainer", entrypoint = DEFAULT_ENTRY_POINT)
    return src
  }

  fun getScenarioRunContainerEnv(): ScenarioRunContainer {
    var src =
        ScenarioRunContainer(
            image = "cosmotech/testcontainer",
            envVars = mapOf("env1" to "envvar1", "env2" to "envvar2", "env3" to "envvar3"))

    return src
  }
}
