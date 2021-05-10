// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun

import com.cosmotech.connector.domain.Connector
import com.cosmotech.connector.domain.Connector.IoTypes
import com.cosmotech.connector.domain.ConnectorParameter
import com.cosmotech.connector.domain.ConnectorParameterGroup
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.dataset.domain.DatasetConnector
import com.cosmotech.solution.domain.Solution
import com.cosmotech.solution.domain.RunTemplate
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceService
import com.cosmotech.workspace.domain.WorkspaceServices
import com.cosmotech.workspace.domain.WorkspaceSolution
import com.cosmotech.scenariorun.domain.ScenarioRunContainer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class ContainerFactoryTests {
  private val logger = LoggerFactory.getLogger(ContainerFactoryTests::class.java)
  private val factory =
      ContainerFactory(
          azureTenantId = "12345678",
          azureClientId = "98765432",
          azureClientSecret = "azertyuiop",
          apiBaseUrl = "https://api.comostech.com",
          apiToken = "azertyuiopqsdfghjklm",
          scenarioFetchParametersImage = "cosmotech/scenariofetchparameters",
          sendDataWarehouseImage = "cosmotech/senddatawarehouse",
          adxDataIngestionUri = "https://ingest-phoenix.westeurope.kusto.windows.net",
          eventHubClusterUri = "amqps://csm-phoenix.servicebus.windows.net",
      )

  @Test
  fun `Dataset Container not null`() {
    val container = factory.buildFromDataset(getDataset(), getConnector())
    assertNotNull(container)
  }

  @Test
  fun `Dataset Container name valid`() {
    val container = factory.buildFromDataset(getDataset(), getConnector())
    assertEquals("fetchDatasetContainers", container.name)
  }

  @Test
  fun `Dataset Container image is valid`() {
    val container = factory.buildFromDataset(getDataset(), getConnector())
    assertEquals("cosmotech/test_connector:1.0.0", container.image)
  }

  @Test
  fun `Dataset Container connector is valid`() {
    assertThrows(IllegalStateException::class.java) {
      factory.buildFromDataset(getDataset(), getConnector("BadId"))
    }
  }

  @Test
  fun `Dataset env vars valid`() {
    val container = factory.buildFromDataset(getDataset(), getConnector())
    val expected =
        mapOf(
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_API_URL" to "https://api.comostech.com",
            "CSM_API_TOKEN" to "azertyuiopqsdfghjklm",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "ENV_PARAM_1" to "env_param1_value",
            "ENV_PARAM_2" to "env_param2_value",
            "ENV_PARAM_3" to "env_param3_value")
    assertTrue(expected.equals(container.envVars))
  }

  @Test
  fun `Dataset no env vars valid`() {
    val container = factory.buildFromDataset(getDatasetNoVars(), getConnectorNoVars())
    val expected =
        mapOf(
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_API_URL" to "https://api.comostech.com",
            "CSM_API_TOKEN" to "azertyuiopqsdfghjklm",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters"
        )
    assertTrue(expected.equals(container.envVars))
  }

  @Test
  fun `Dataset args valid`() {
    val container = factory.buildFromDataset(getDataset(), getConnector())
    val expected = listOf("param1_value", "param2_value", "param3_value")
    assertEquals(expected, container.runArgs)
  }

  @Test
  fun `Fetch Scenario Parameters Container is not null`() {
    val container = factory.buildScenarioParametersFetchContainer("1")
    assertNotNull(container)
  }

  @Test
  fun `Fetch Scenario Parameters Container name valid`() {
    val container = factory.buildScenarioParametersFetchContainer("1")
    assertEquals("fetchScenarioParametersContainer", container.name)
  }

  @Test
  fun `Fetch Scenario Parameters Container image valid`() {
    val container = factory.buildScenarioParametersFetchContainer("1")
    assertEquals("cosmotech/scenariofetchparameters", container.image)
  }

  @Test
  fun `Fetch Scenario Parameters Container env vars valid`() {
    val container = factory.buildScenarioParametersFetchContainer("1")
    val expected =
        mapOf(
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_API_URL" to "https://api.comostech.com",
            "CSM_API_TOKEN" to "azertyuiopqsdfghjklm",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "CSM_SCENARIO_ID" to "1"
        )
    assertTrue(expected.equals(container.envVars))
  }

  @Test
  fun `Send DataWarehouse Container is not null`() {
    val container = factory.buildSendDataWarehouseContainer(getWorkspace())
    assertNotNull(container)
  }

  @Test
  fun `Send DataWarehouseContainer name valid`() {
    val container = factory.buildSendDataWarehouseContainer(getWorkspace())
    assertEquals("sendDataWarehouseContainer", container.name)
  }

  @Test
  fun `Send DataWarehouse Container image valid`() {
    val container = factory.buildSendDataWarehouseContainer(getWorkspace())
    assertEquals("cosmotech/senddatawarehouse", container.image)
  }

  @Test
  fun `Send DataWarehouse Container env vars valid`() {
    val container = factory.buildSendDataWarehouseContainer(getWorkspace())
    val expected =
        mapOf(
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_API_URL" to "https://api.comostech.com",
            "CSM_API_TOKEN" to "azertyuiopqsdfghjklm",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "CSM_SEND_DATAWAREHOUSE_PARAMETERS" to "true",
            "CSM_SEND_DATAWAREHOUSE_DATASETS" to "true",
            "ADX_DATA_INGESTION_URI" to "https://ingest-phoenix.westeurope.kusto.windows.net",
            "ADX_DATABASE" to "test",
        )
    assertTrue(expected.equals(container.envVars))
  }

  @Test
  fun `Parameters Handler Container is not null`() {
    val container = this.buildApplyParametersContainer()
    assertNotNull(container)
  }

  @Test
  fun `Parameters Handler Container name valid`() {
    val container = this.buildApplyParametersContainer()
    assertEquals("applyParametersContainer", container.name)
  }

  @Test
  fun `Parameters Handler Container image valid`() {
    val container = this.buildApplyParametersContainer()
    assertEquals("cosmotech/testsolution_simulator:1.0.0", container.image)
  }

  @Test
  fun `Parameters Handler Container env vars valid`() {
    val container = this.buildApplyParametersContainer()
    val expected =
        mapOf(
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_API_URL" to "https://api.comostech.com",
            "CSM_API_TOKEN" to "azertyuiopqsdfghjklm",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "CSM_RUN_TEMPLATE_ID" to "testruntemplate",
            "CSM_CONTAINER_MODE" to "handle-parameters",
            "CSM_CONTROL_PLANE_TOPIC" to "amqps://csm-phoenix.servicebus.windows.net/test-scenariorun",
            "CSM_PROBES_MEASURES_TOPIC" to "amqps://csm-phoenix.servicebus.windows.net/test",
            "CSM_SIMULATION" to "TestSimulation"
        )
    logger.info(container.envVars.toString())
    assertTrue(expected.equals(container.envVars))
  }

  @Test
  fun `Parameters Handler Container entrypoint valid`() {
    val container = this.buildApplyParametersContainer()
    assertEquals("entrypoint.py", container.entrypoint)
  }

  @Test
  fun `Parameters Handler Container bad template exception`() {
    assertThrows(IllegalStateException::class.java) {
      factory.buildApplyParametersContainer("test", getSolution(), "badTemplate")
    }
  }

  private fun buildApplyParametersContainer(): ScenarioRunContainer {
    return factory.buildApplyParametersContainer("test", getSolution(), "testruntemplate")
  }


  private fun getDataset(): Dataset {
    val connector = getDatasetConnector()
    return Dataset(name = "Test Dataset", connector = connector)
  }

  private fun getDatasetConnector(): DatasetConnector {
    return DatasetConnector(
        id = "AzErTyUiOp",
        parametersValues =
            mapOf(
                "EnvParam1" to "env_param1_value",
                "EnvParam2" to "env_param2_value",
                "EnvParam3" to "env_param3_value",
                "Param1" to "param1_value",
                "Param2" to "param2_value",
                "Param3" to "param3_value",
            ))
  }

  private fun getConnector(): Connector {
    return getConnector("AzErTyUiOp")
  }

  private fun getConnector(id: String): Connector {
    val envparam1 =
        ConnectorParameter(id = "EnvParam1", label = "Env param 1", envVar = "ENV_PARAM_1")
    val envparam2 =
        ConnectorParameter(id = "EnvParam2", label = "Env param 2", envVar = "ENV_PARAM_2")
    val envparam3 =
        ConnectorParameter(id = "EnvParam3", label = "Env param 3", envVar = "ENV_PARAM_3")
    val param1 = ConnectorParameter(id = "Param1", label = "Param 1")
    val param2 = ConnectorParameter(id = "Param2", label = "Param 2")
    val param3 = ConnectorParameter(id = "Param3", label = "Param 3")
    val parametersList = listOf(envparam1, envparam2, envparam3, param1, param2, param3)
    val parameterGroup =
        ConnectorParameterGroup(
            id = "ParamGroup1", label = "Parameter Group 1", parameters = parametersList)
    return Connector(
        id = id,
        key = "TestConnector",
        name = "Test Connector",
        repository = "cosmotech/test_connector",
        version = "1.0.0",
        ioTypes = listOf(IoTypes.read),
        parameterGroups = listOf(parameterGroup))
  }

  private fun getConnectorNoVars(): Connector {
    return Connector(
        id = "QsDfGhJk",
        key = "TestConnector",
        name = "Test Connector",
        repository = "cosmotech/test_connector",
        version = "1.0.0",
        ioTypes = listOf(IoTypes.read))
  }

  private fun getDatasetConnectorNoVars(): DatasetConnector {
    return DatasetConnector(id = "QsDfGhJk")
  }

  private fun getDatasetNoVars(): Dataset {
    val connector = getDatasetConnectorNoVars()
    return Dataset(name = "Test Dataset No Vars", connector = connector)
  }

  private fun getWorkspace(): Workspace {
    return Workspace(
      key = "test",
      name = "Test Workspace",
      description = "Test Workspace Description",
      version = "1.0.0",
      solution = WorkspaceSolution(
        solutionId = "1",
      ),
    )
  }

  private fun getWorkspaceNoDB(): Workspace {
    return Workspace(
      key = "test",
      name = "Test Workspace",
      solution = WorkspaceSolution(
        solutionId = "1",
      ),
    )
  }

  private fun getSolution(): Solution {
    return Solution(
      key = "TestSolution",
      name = "Test Solution",
      repository = "cosmotech/testsolution_simulator",
      version = "1.0.0",
      runTemplates = listOf(getRunTemplate()),
    )
  }

  private fun getRunTemplate(): RunTemplate {
    return RunTemplate(
      id = "testruntemplate",
      name = "Test Run",
      csmSimulation = "TestSimulation",
    )
  }
}
