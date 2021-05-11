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
import com.cosmotech.workspace.domain.WorkspaceSolution
import com.cosmotech.scenariorun.domain.ScenarioRunContainer
import com.cosmotech.scenario.domain.Scenario
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
          scenarioFetchParametersImage = "cosmotech/scenariofetchparameters:1.0.0",
          sendDataWarehouseImage = "cosmotech/senddatawarehouse:1.0.0",
          adxDataIngestionUri = "https://ingest-phoenix.westeurope.kusto.windows.net",
          eventHubClusterUri = "amqps://csm-phoenix.servicebus.windows.net",
      )

  @Test
  fun `Dataset Container not null`() {
    val container = factory.buildFromDataset(getDataset(), getConnector(), 1)
    assertNotNull(container)
  }

  @Test
  fun `Dataset Container name valid`() {
    val container = factory.buildFromDataset(getDataset(), getConnector(), 1)
    assertEquals("fetchDatasetContainers-1", container.name)
  }

  @Test
  fun `Dataset Container image is valid`() {
    val container = factory.buildFromDataset(getDataset(), getConnector(), 1)
    assertEquals("cosmotech/test_connector:1.0.0", container.image)
  }

  @Test
  fun `Dataset Container connector is valid`() {
    assertThrows(IllegalStateException::class.java) {
      factory.buildFromDataset(getDataset(), getConnector("BadId"), 1)
    }
  }

  @Test
  fun `Dataset env vars valid`() {
    val container = factory.buildFromDataset(getDataset(), getConnector(), 1)
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
    val container = factory.buildFromDataset(getDatasetNoVars(), getConnectorNoVars(), 1)
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
    val container = factory.buildFromDataset(getDataset(), getConnector(), 1)
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
    assertEquals("cosmotech/scenariofetchparameters:1.0.0", container.image)
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
    val container = factory.buildSendDataWarehouseContainer(getWorkspace(), getRunTemplate())
    assertNotNull(container)
  }

  @Test
  fun `Send DataWarehouseContainer name valid`() {
    val container = factory.buildSendDataWarehouseContainer(getWorkspace(), getRunTemplate())
    assertEquals("sendDataWarehouseContainer", container.name)
  }

  @Test
  fun `Send DataWarehouse Container image valid`() {
    val container = factory.buildSendDataWarehouseContainer(getWorkspace(), getRunTemplate())
    assertEquals("cosmotech/senddatawarehouse:1.0.0", container.image)
  }

  @Test
  fun `Send DataWarehouse Container env vars valid`() {
    val container = factory.buildSendDataWarehouseContainer(getWorkspace(), getRunTemplate())
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
  fun `Send DataWarehouse Container no send env vars`() {
    val container = factory.buildSendDataWarehouseContainer(getWorkspaceNoSend(), getRunTemplate())
    val expected =
        mapOf(
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_API_URL" to "https://api.comostech.com",
            "CSM_API_TOKEN" to "azertyuiopqsdfghjklm",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "CSM_SEND_DATAWAREHOUSE_PARAMETERS" to "false",
            "CSM_SEND_DATAWAREHOUSE_DATASETS" to "false",
            "ADX_DATA_INGESTION_URI" to "https://ingest-phoenix.westeurope.kusto.windows.net",
            "ADX_DATABASE" to "test",
        )
    assertTrue(expected.equals(container.envVars))
  }

  @Test
  fun `Send DataWarehouse Container env vars send override dataset template`() {
    val container = factory.buildSendDataWarehouseContainer(getWorkspace(), getRunTemplateNoDatasetsSend())
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
            "CSM_SEND_DATAWAREHOUSE_DATASETS" to "false",
            "ADX_DATA_INGESTION_URI" to "https://ingest-phoenix.westeurope.kusto.windows.net",
            "ADX_DATABASE" to "test",
        )
    assertTrue(expected.equals(container.envVars))
  }

  @Test
  fun `Send DataWarehouse Container env vars send override parameters template`() {
    val container = factory.buildSendDataWarehouseContainer(getWorkspace(), getRunTemplateNoParametersSend())
    val expected =
        mapOf(
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_API_URL" to "https://api.comostech.com",
            "CSM_API_TOKEN" to "azertyuiopqsdfghjklm",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "CSM_SEND_DATAWAREHOUSE_PARAMETERS" to "false",
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

  @Test
  fun `Parameters Handler Container env vars valid`() {
    val container = this.buildApplyParametersContainer()
    this.validateEnvVarsSolutionContainer(container, "handle-parameters")
  }

  @Test
  fun `Validate Container is not null`() {
    val container = this.buildValidateDataContainer()
    assertNotNull(container)
  }

  @Test
  fun `Validate Container name valid`() {
    val container = this.buildValidateDataContainer()
    assertEquals("validateDataContainer", container.name)
  }

  @Test
  fun `Validate Container env vars valid`() {
    val container = this.buildValidateDataContainer()
    this.validateEnvVarsSolutionContainer(container, "validate")
  }

  @Test
  fun `Prerun Container is not null`() {
    val container = this.buildPreRunContainer()
    assertNotNull(container)
  }

  @Test
  fun `Prerun Container name valid`() {
    val container = this.buildPreRunContainer()
    assertEquals("preRunContainer", container.name)
  }

  @Test
  fun `PreRun Container env vars valid`() {
    val container = this.buildPreRunContainer()
    this.validateEnvVarsSolutionContainer(container, "prerun")
  }

  @Test
  fun `Run Container is not null`() {
    val container = this.buildRunContainer()
    assertNotNull(container)
  }

  @Test
  fun `Run Container name valid`() {
    val container = this.buildRunContainer()
    assertEquals("runContainer", container.name)
  }

  @Test
  fun `Run Container env vars valid`() {
    val container = this.buildRunContainer()
    this.validateEnvVarsSolutionContainer(container, "engine")
  }

  @Test
  fun `Post Run Container is not null`() {
    val container = this.buildPostRunContainer()
    assertNotNull(container)
  }

  @Test
  fun `Post Run Container name valid`() {
    val container = this.buildPostRunContainer()
    assertEquals("postRunContainer", container.name)
  }

  @Test
  fun `Post Run Container env vars valid`() {
    val container = this.buildPostRunContainer()
    this.validateEnvVarsSolutionContainer(container, "postrun")
  }

  @Test
  fun `Build all containers for a Scenario count`() {
    val scenario = getScenario()
    val datasets = listOf(getDataset())
    val connectors = listOf(getConnector())
    val workspace = getWorkspace()
    val solution = getSolution()
    val containers = factory.buildContainersPipeline(scenario, datasets, connectors, workspace, solution)
    assertEquals(containers.size, 8)
  }
  @Test
  fun `Build all containers for a Scenario containers name list`() {
    val scenario = getScenario()
    val datasets = listOf(getDataset())
    val connectors = listOf(getConnector())
    val workspace = getWorkspace()
    val solution = getSolution()
    val containers = factory.buildContainersPipeline(scenario, datasets, connectors, workspace, solution)
    val expected = listOf(
      "fetchDatasetContainers-1",
      "fetchScenarioParametersContainer",
      "applyParametersContainer",
      "validateDataContainer",
      "sendDataWarehouseContainer",
      "preRunContainer",
      "runContainer",
      "postRunContainer",
    )
    assertEquals(expected, containers.map{container -> container.name})
  }

  @Test
  fun `Build all containers Only Run`() {
    val scenario = getScenario()
    val datasets = listOf(getDataset())
    val connectors = listOf(getConnector())
    val workspace = getWorkspace()
    val solution = getSolutionOnlyRun()
    val containers = factory.buildContainersPipeline(scenario, datasets, connectors, workspace, solution)
    assertEquals(containers.size, 1)
  }

  @Test
  fun `Build all containers for a Scenario with 3 Datasets count`() {
    val scenario = getScenarioThreeDatasets()
    val datasets = listOf(getDataset(), getDataset2(), getDataset3())
    val connectors = listOf(getConnector(), getConnector2(), getConnector3())
    val workspace = getWorkspace()
    val solution = getSolution()
    val containers = factory.buildContainersPipeline(scenario, datasets, connectors, workspace, solution)
    assertEquals(containers.size, 10)
  }

  @Test
  fun `Build all containers for a Scenario 3 Datasets containers name list`() {
    val scenario = getScenarioThreeDatasets()
    val datasets = listOf(getDataset(), getDataset2(), getDataset3())
    val connectors = listOf(getConnector(), getConnector2(), getConnector3())
    val workspace = getWorkspace()
    val solution = getSolution()
    val containers = factory.buildContainersPipeline(scenario, datasets, connectors, workspace, solution)
    val expected = listOf(
      "fetchDatasetContainers-1",
      "fetchDatasetContainers-2",
      "fetchDatasetContainers-3",
      "fetchScenarioParametersContainer",
      "applyParametersContainer",
      "validateDataContainer",
      "sendDataWarehouseContainer",
      "preRunContainer",
      "runContainer",
      "postRunContainer",
    )
    assertEquals(expected, containers.map{container -> container.name})
  }

  @Test
  fun `Build all containers for a Scenario 3 Datasets containers image list`() {
    val scenario = getScenarioThreeDatasets()
    val datasets = listOf(getDataset(), getDataset2(), getDataset3())
    val connectors = listOf(getConnector(), getConnector2(), getConnector3())
    val workspace = getWorkspace()
    val solution = getSolution()
    val containers = factory.buildContainersPipeline(scenario, datasets, connectors, workspace, solution)
    val expected = listOf(
      "cosmotech/test_connector:1.0.0",
      "cosmotech/test_connector2:1.0.0",
      "cosmotech/test_connector3:1.0.0",
      "cosmotech/scenariofetchparameters:1.0.0",
      "cosmotech/testsolution_simulator:1.0.0",
      "cosmotech/testsolution_simulator:1.0.0",
      "cosmotech/senddatawarehouse:1.0.0",
      "cosmotech/testsolution_simulator:1.0.0",
      "cosmotech/testsolution_simulator:1.0.0",
      "cosmotech/testsolution_simulator:1.0.0",
    )
    assertEquals(expected, containers.map{container -> container.image})
  }

  @Test
  fun `Build all containers for a Scenario containers name list Only Run`() {
    val scenario = getScenario()
    val datasets = listOf(getDataset())
    val connectors = listOf(getConnector())
    val workspace = getWorkspace()
    val solution = getSolutionOnlyRun()
    val containers = factory.buildContainersPipeline(scenario, datasets, connectors, workspace, solution)
    val expected = listOf(
      "runContainer",
    )
    assertEquals(expected, containers.map{container -> container.name})
  }

  @Test
  fun `Build start containers node Label`() {
    val scenario = getScenario()
    val datasets = listOf(getDataset())
    val connectors = listOf(getConnector())
    val workspace = getWorkspace()
    val solution = getSolution()
    val startContainers = factory.buildContainersStart(scenario, datasets, connectors, workspace, solution)
    assertEquals("highcpupool", startContainers.nodeLabel)
  }

  @Test
  fun `Build start containers node Label default`() {
    val scenario = getScenario()
    val datasets = listOf(getDataset())
    val connectors = listOf(getConnector())
    val workspace = getWorkspace()
    val solution = getSolutionNoPool()
    val startContainers = factory.buildContainersStart(scenario, datasets, connectors, workspace, solution)
    assertEquals("basicpool", startContainers.nodeLabel)
  }

  @Test
  fun `Build start containers generate name`() {
    val scenario = getScenario()
    val datasets = listOf(getDataset())
    val connectors = listOf(getConnector())
    val workspace = getWorkspace()
    val solution = getSolution()
    val startContainers = factory.buildContainersStart(scenario, datasets, connectors, workspace, solution)
    logger.info(startContainers.toString())
    assertEquals("workflow-aqwxsz-", startContainers.generateName)
  }

  private fun buildApplyParametersContainer(): ScenarioRunContainer {
    return factory.buildApplyParametersContainer("test", getSolution(), "testruntemplate")
  }

  private fun buildValidateDataContainer(): ScenarioRunContainer {
    return factory.buildValidateDataContainer("test", getSolution(), "testruntemplate")
  }

  private fun buildPreRunContainer(): ScenarioRunContainer {
    return factory.buildPreRunContainer("test", getSolution(), "testruntemplate")
  }

  private fun buildRunContainer(): ScenarioRunContainer {
    return factory.buildRunContainer("test", getSolution(), "testruntemplate")
  }

  private fun buildPostRunContainer(): ScenarioRunContainer {
    return factory.buildPostRunContainer("test", getSolution(), "testruntemplate")
  }

  private fun validateEnvVarsSolutionContainer(container: ScenarioRunContainer, mode: String) {
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
            "CSM_CONTAINER_MODE" to mode,
            "CSM_CONTROL_PLANE_TOPIC" to "amqps://csm-phoenix.servicebus.windows.net/test-scenariorun",
            "CSM_PROBES_MEASURES_TOPIC" to "amqps://csm-phoenix.servicebus.windows.net/test",
            "CSM_SIMULATION" to "TestSimulation"
        )
    assertTrue(expected.equals(container.envVars))
  }

  private fun getDataset(): Dataset {
    val connector = getDatasetConnector()
    return Dataset(id = "1", name = "Test Dataset", connector = connector)
  }

  private fun getDataset2(): Dataset {
    val connector = getDatasetConnector2()
    return Dataset(id = "2", name = "Test Dataset 2", connector = connector)
  }

  private fun getDataset3(): Dataset {
    val connector = getDatasetConnector3()
    return Dataset(id = "3", name = "Test Dataset 3", connector = connector)
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

  private fun getDatasetConnector2(): DatasetConnector {
    return DatasetConnector(
        id = "AzErTyUiOp2",
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

  private fun getDatasetConnector3(): DatasetConnector {
    return DatasetConnector(
        id = "AzErTyUiOp3",
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

  private fun getConnector2(): Connector {
    return getConnector("AzErTyUiOp2", "TestConnector2", "cosmotech/test_connector2")
  }

  private fun getConnector3(): Connector {
    return getConnector("AzErTyUiOp3", "TestConnector3", "cosmotech/test_connector3")
  }

  private fun getConnector(id: String, key: String = "TestConnector", repository: String = "cosmotech/test_connector"): Connector {
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
        key = id,
        name = "Test Connector",
        repository = repository,
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
    return Dataset(id = "1", name = "Test Dataset No Vars", connector = connector)
  }

  private fun getWorkspace(): Workspace {
    return Workspace(
      id = "1",
      key = "test",
      name = "Test Workspace",
      description = "Test Workspace Description",
      version = "1.0.0",
      solution = WorkspaceSolution(
        solutionId = "1",
      ),
    )
  }

  private fun getWorkspaceNoSend(): Workspace {
    return Workspace(
      id = "1",
      key = "test",
      name = "Test Workspace",
      description = "Test Workspace Description",
      version = "1.0.0",
      solution = WorkspaceSolution(
        solutionId = "1",
      ),
      sendInputToDataWarehouse = false,
    )
  }

  private fun getWorkspaceNoDB(): Workspace {
    return Workspace(
      id = "1",
      key = "test",
      name = "Test Workspace",
      solution = WorkspaceSolution(
        solutionId = "1",
      ),
    )
  }

  private fun getSolution(): Solution {
    return Solution(
      id = "1",
      key = "TestSolution",
      name = "Test Solution",
      repository = "cosmotech/testsolution_simulator",
      version = "1.0.0",
      runTemplates = listOf(getRunTemplate()),
    )
  }

  private fun getSolutionNoPool(): Solution {
    return Solution(
      id = "1",
      key = "TestSolution",
      name = "Test Solution",
      repository = "cosmotech/testsolution_simulator",
      version = "1.0.0",
      runTemplates = listOf(getRunTemplateNoPool()),
    )
  }

  private fun getSolutionOnlyRun(): Solution {
    return Solution(
      id = "1",
      key = "TestSolution",
      name = "Test Solution",
      repository = "cosmotech/testsolution_simulator",
      version = "1.0.0",
      runTemplates = listOf(getRunTemplateOnlyRun()),
    )
  }

  private fun getRunTemplate(): RunTemplate {
    return RunTemplate(
      id = "testruntemplate",
      name = "Test Run",
      csmSimulation = "TestSimulation",
      computeSize = "highcpu",
    )
  }

  private fun getRunTemplateNoPool(): RunTemplate {
    return RunTemplate(
      id = "testruntemplate",
      name = "Test Run",
      csmSimulation = "TestSimulation",
    )
  }

  private fun getRunTemplateNoDatasetsSend(): RunTemplate {

    return RunTemplate(
      id = "testruntemplate",
      name = "Test Run",
      csmSimulation = "TestSimulation",
      sendDatasetsToDataWarehouse = false
    )
  }

  private fun getRunTemplateNoParametersSend(): RunTemplate {

    return RunTemplate(
      id = "testruntemplate",
      name = "Test Run",
      csmSimulation = "TestSimulation",
      sendInputParametersToDataWarehouse = false
    )
  }

  private fun getRunTemplateOnlyRun(): RunTemplate {

    return RunTemplate(
      id = "testruntemplate",
      name = "Test Run",
      csmSimulation = "TestSimulation",
        fetchDatasets = false,
        fetchScenarioParameters = false,
        applyParameters = false,
        validateData = false,
        sendDatasetsToDataWarehouse = false,
        sendInputParametersToDataWarehouse = false,
        preRun = false,
        postRun = false,
    )
  }

  private fun getScenario(): Scenario {
    return Scenario(
      id = "aqwxsz",
      name = "Test Scenario",
      runTemplateId = "testruntemplate",
      datasetList = listOf("1"),
    )
  }

  private fun getScenarioThreeDatasets(): Scenario {
    return Scenario(
      id = "aqwxsz",
      name = "Test Scenario",
      runTemplateId = "testruntemplate",
      datasetList = listOf("1","2","3"),
    )
  }
}
