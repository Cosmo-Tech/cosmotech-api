// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.connector.api.ConnectorApiService
import com.cosmotech.connector.domain.Connector
import com.cosmotech.connector.domain.Connector.IoTypes
import com.cosmotech.connector.domain.ConnectorParameter
import com.cosmotech.connector.domain.ConnectorParameterGroup
import com.cosmotech.dataset.api.DatasetApiService
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.dataset.domain.DatasetConnector
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.domain.Organization
import com.cosmotech.scenario.api.ScenarioApiService
import com.cosmotech.scenario.domain.Scenario
import com.cosmotech.scenario.domain.ScenarioRunTemplateParameterValue
import com.cosmotech.scenariorun.ContainerFactory.StartInfo
import com.cosmotech.scenariorun.api.ScenariorunApiService
import com.cosmotech.scenariorun.domain.ScenarioRunContainer
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.solution.domain.RunTemplate
import com.cosmotech.solution.domain.RunTemplateParameter
import com.cosmotech.solution.domain.RunTemplateParameterGroup
import com.cosmotech.solution.domain.RunTemplateStepSource
import com.cosmotech.solution.domain.Solution
import com.cosmotech.workspace.api.WorkspaceApiService
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceSolution
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class ContainerFactoryTests {
  private val logger = LoggerFactory.getLogger(ContainerFactoryTests::class.java)
  private val csmSimulationId = "simulationrunid"
  private val factory =
      ContainerFactory(
          CsmPlatformProperties(
              azure =
                  CsmPlatformProperties.CsmPlatformAzure(
                      credentials =
                          CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureCredentials(
                              tenantId = "12345678",
                              clientId = "98765432",
                              clientSecret = "azertyuiop",
                          ),
                      eventBus =
                          CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus(
                              baseUri = "amqps://csm-phoenix.servicebus.windows.net",
                          ),
                      dataWarehouseCluster =
                          CsmPlatformProperties.CsmPlatformAzure
                              .CsmPlatformAzureDataWarehouseCluster(
                                  options =
                                      CsmPlatformProperties.CsmPlatformAzure
                                          .CsmPlatformAzureDataWarehouseCluster.Options(
                                          ingestionUri =
                                              "https://ingest-phoenix.westeurope.kusto.windows.net",
                                      ),
                                  baseUri = "Not Used",
                              ),
                      storage =
                          CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureStorage(
                              connectionString =
                                  "DefaultEndpointsProtocol=https;AccountName=csmphoenix;AccountKey=42rmlBQ2IrxdIByLj79AecdIyYifSR04ZnGsBYt82tbM2clcP0QwJ9N+l/fLvyCzu9VZ8HPsQyM7jHe6CVSUig==;EndpointSuffix=core.windows.net",
                              baseUri = "Not Used",
                              resourceUri = "Not Used",
                          ),
                      containerRegistries =
                          CsmPlatformProperties.CsmPlatformAzure
                              .CsmPlatformAzureContainerRegistries(
                                  core = "ghcr.io", solutions = "twinengines.azurecr.io"),
                      keyVault = "Not Used",
                      analytics =
                          CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureAnalytics(
                              resourceUri = "Not Used",
                              instrumentationKey = "Not Used",
                              connectionString = "Not Used",
                          ),
                      cosmos =
                          CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureCosmos(
                              uri = "Not Used",
                              coreDatabase =
                                  CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureCosmos
                                      .CoreDatabase(
                                          name = "Not Used",
                                          connectors =
                                              CsmPlatformProperties.CsmPlatformAzure
                                                  .CsmPlatformAzureCosmos.CoreDatabase.Connectors(
                                                  container = "Not Used"),
                                          organizations =
                                              CsmPlatformProperties.CsmPlatformAzure
                                                  .CsmPlatformAzureCosmos.CoreDatabase
                                                  .Organizations(container = "Not Used"),
                                          users =
                                              CsmPlatformProperties.CsmPlatformAzure
                                                  .CsmPlatformAzureCosmos.CoreDatabase.Users(
                                                  container = "Not Used")),
                              key = "Not Used",
                              consistencyLevel = null,
                              populateQueryMetrics = false,
                              allowTelemetry = false,
                              connectionMode = null,
                          )),
              api =
                  CsmPlatformProperties.Api(
                      baseUrl = "https://api.cosmotech.com",
                      version = "v1",
                      basePath = "basepath",
                  ),
              images =
                  CsmPlatformProperties.CsmImages(
                      scenarioFetchParameters = "cosmotech/scenariofetchparameters:1.0.0",
                      sendDataWarehouse = "cosmotech/senddatawarehouse:1.0.0",
                  ),
              version = "Not Used",
              vendor = CsmPlatformProperties.Vendor.AZURE,
              idGenerator =
                  CsmPlatformProperties.IdGenerator(CsmPlatformProperties.IdGenerator.Type.HASHID),
              argo =
                  CsmPlatformProperties.Argo(
                      baseUri = "Not Used",
                  ),
              summary = "Not Used",
              description = "Not Used",
              eventPublisher =
                  CsmPlatformProperties.EventPublisher(
                      type = CsmPlatformProperties.EventPublisher.Type.IN_PROCESS),
          ))

  @Test
  fun `Dataset Container not null`() {
    val container =
        factory.buildFromDataset(
            getDataset(),
            getConnector(),
            1,
            false,
            "1",
            "Organizationid",
            "workspaceid",
            csmSimulationId)
    assertNotNull(container)
  }

  @Test
  fun `Dataset Container name valid`() {
    val container =
        factory.buildFromDataset(
            getDataset(),
            getConnector(),
            1,
            false,
            "1",
            "Organizationid",
            "workspaceid",
            csmSimulationId)
    assertEquals("fetchDatasetContainer-1", container.name)
  }

  @Test
  fun `Dataset Container image is valid`() {
    val container =
        factory.buildFromDataset(
            getDataset(),
            getConnector(),
            1,
            false,
            "1",
            "Organizationid",
            "workspaceid",
            csmSimulationId)
    assertEquals("ghcr.io/cosmotech/test_connector:1.0.0", container.image)
  }

  @Test
  fun `Dataset Container connector is valid`() {
    assertThrows(IllegalStateException::class.java) {
      factory.buildFromDataset(
          getDataset(),
          getConnector("BadId"),
          1,
          false,
          "1",
          "Organizationid",
          "workspaceid",
          csmSimulationId)
    }
  }

  @Test
  fun `Dataset env vars valid`() {
    val container =
        factory.buildFromDataset(
            getDataset(),
            getConnector(),
            1,
            false,
            null,
            "Organizationid",
            "workspaceid",
            csmSimulationId)
    val expected =
        mapOf(
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to "simulationrunid",
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "CSM_FETCH_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "ENV_PARAM_1" to "env_param1_value",
            "ENV_PARAM_2" to "env_param2_value",
            "ENV_PARAM_3" to "env_param3_value")
    assertEquals(expected, container.envVars)
  }

  @Test
  fun `Dataset env vars Workspace File valid`() {
    val container =
        factory.buildFromDataset(
            getDatasetWorkspaceFile(),
            getConnectorWorkspaceFile(),
            1,
            false,
            null,
            "Organizationid",
            "workspaceid",
            csmSimulationId)
    val expected =
        mapOf(
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to "simulationrunid",
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "CSM_FETCH_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "ENV_PARAM_1" to "organizationid/workspaceid/workspace.env",
        )
    assertEquals(expected, container.envVars)
  }

  @Test
  fun `Dataset env vars Workspace Storage valid`() {
    val container =
        factory.buildFromDataset(
            getDatasetWorkspaceStorage(),
            getConnectorWorkspaceStorage(),
            1,
            false,
            null,
            "Organizationid",
            "workspaceid",
            csmSimulationId)
    val expected =
        mapOf(
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to "simulationrunid",
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "CSM_FETCH_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "AZURE_STORAGE_CONNECTION_STRING" to
                "DefaultEndpointsProtocol=https;AccountName=csmphoenix;AccountKey=42rmlBQ2IrxdIByLj79AecdIyYifSR04ZnGsBYt82tbM2clcP0QwJ9N+l/fLvyCzu9VZ8HPsQyM7jHe6CVSUig==;EndpointSuffix=core.windows.net",
        )
    assertEquals(expected, container.envVars)
  }

  @Test
  fun `Dataset no env vars valid`() {
    val container =
        factory.buildFromDataset(
            getDatasetNoVars(),
            getConnectorNoVars(),
            1,
            false,
            null,
            "Organizationid",
            "workspaceid",
            csmSimulationId)
    val expected =
        mapOf(
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to "simulationrunid",
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "CSM_FETCH_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
        )
    assertEquals(expected, container.envVars)
  }

  @Test
  fun `Dataset args Workspace File valid`() {
    val container =
        factory.buildFromDataset(
            getDatasetWorkspaceFile(),
            getConnectorWorkspaceFile(),
            1,
            false,
            "1",
            "Organizationid",
            "workspaceid",
            csmSimulationId)
    val expected = listOf("organizationid/workspaceid/workspace.param")
    assertEquals(expected, container.runArgs)
  }

  @Test
  fun `Dataset args valid`() {
    val container =
        factory.buildFromDataset(
            getDataset(),
            getConnector(),
            1,
            false,
            "1",
            "Organizationid",
            "workspaceid",
            csmSimulationId)
    val expected = listOf("param1_value", "param2_value", "param3_value")
    assertEquals(expected, container.runArgs)
  }

  @Test
  fun `Fetch Scenario Parameters Container is not null`() {
    val container = factory.buildScenarioParametersFetchContainer("1", "2", "3", csmSimulationId)
    assertNotNull(container)
  }

  @Test
  fun `Fetch Scenario Parameters Container name valid`() {
    val container = factory.buildScenarioParametersFetchContainer("1", "2", "3", csmSimulationId)
    assertEquals("fetchScenarioParametersContainer", container.name)
  }

  @Test
  fun `Fetch Scenario Parameters Container image valid`() {
    val container = factory.buildScenarioParametersFetchContainer("1", "2", "3", csmSimulationId)
    assertEquals("ghcr.io/cosmotech/scenariofetchparameters:1.0.0", container.image)
  }

  @Test
  fun `Fetch Scenario Parameters Container env vars valid`() {
    val container = factory.buildScenarioParametersFetchContainer("1", "2", "3", csmSimulationId)
    val expected =
        mapOf(
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to "simulationrunid",
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "CSM_FETCH_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "CSM_ORGANIZATION_ID" to "1",
            "CSM_WORKSPACE_ID" to "2",
            "CSM_SCENARIO_ID" to "3")
    assertEquals(expected, container.envVars)
  }

  @Test
  fun `Fetch Scenario Parameters Container env vars valid json`() {
    val container =
        factory.buildScenarioParametersFetchContainer("1", "2", "3", csmSimulationId, true)
    val expected =
        mapOf(
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to "simulationrunid",
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "CSM_FETCH_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "CSM_ORGANIZATION_ID" to "1",
            "CSM_WORKSPACE_ID" to "2",
            "CSM_SCENARIO_ID" to "3",
            "WRITE_CSV" to "false",
            "WRITE_JSON" to "true")
    assertEquals(expected, container.envVars)
  }

  @Test
  fun `Send DataWarehouse Container is not null`() {
    val container =
        factory.buildSendDataWarehouseContainer(
            "Organizationid", getWorkspace(), getRunTemplate(), csmSimulationId)
    assertNotNull(container)
  }

  @Test
  fun `Send DataWarehouseContainer name valid`() {
    val container =
        factory.buildSendDataWarehouseContainer(
            "Organizationid", getWorkspace(), getRunTemplate(), csmSimulationId)
    assertEquals("sendDataWarehouseContainer", container.name)
  }

  @Test
  fun `Send DataWarehouse Container image valid`() {
    val container =
        factory.buildSendDataWarehouseContainer(
            "Organizationid", getWorkspace(), getRunTemplate(), csmSimulationId)
    assertEquals("ghcr.io/cosmotech/senddatawarehouse:1.0.0", container.image)
  }

  @Test
  fun `Send DataWarehouse Container env vars valid`() {
    val container =
        factory.buildSendDataWarehouseContainer(
            "Organizationid", getWorkspace(), getRunTemplate(), csmSimulationId)
    val expected =
        mapOf(
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to "simulationrunid",
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "CSM_SEND_DATAWAREHOUSE_PARAMETERS" to "true",
            "CSM_SEND_DATAWAREHOUSE_DATASETS" to "true",
            "ADX_DATA_INGESTION_URI" to "https://ingest-phoenix.westeurope.kusto.windows.net",
            "ADX_DATABASE" to "Organizationid-Test",
        )
    assertEquals(expected, container.envVars)
  }

  @Test
  fun `Send DataWarehouse Container no send env vars`() {
    val container =
        factory.buildSendDataWarehouseContainer(
            "Organizationid", getWorkspaceNoSend(), getRunTemplate(), csmSimulationId)
    val expected =
        mapOf(
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to "simulationrunid",
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "CSM_SEND_DATAWAREHOUSE_PARAMETERS" to "false",
            "CSM_SEND_DATAWAREHOUSE_DATASETS" to "false",
            "ADX_DATA_INGESTION_URI" to "https://ingest-phoenix.westeurope.kusto.windows.net",
            "ADX_DATABASE" to "Organizationid-Test",
        )
    assertEquals(expected, container.envVars)
  }

  @Test
  fun `Send DataWarehouse Container env vars send override dataset template`() {
    val container =
        factory.buildSendDataWarehouseContainer(
            "Organizationid", getWorkspace(), getRunTemplateNoDatasetsSend(), csmSimulationId)
    val expected =
        mapOf(
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to "simulationrunid",
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "CSM_SEND_DATAWAREHOUSE_PARAMETERS" to "true",
            "CSM_SEND_DATAWAREHOUSE_DATASETS" to "false",
            "ADX_DATA_INGESTION_URI" to "https://ingest-phoenix.westeurope.kusto.windows.net",
            "ADX_DATABASE" to "Organizationid-Test",
        )
    assertEquals(expected, container.envVars)
  }

  @Test
  fun `Send DataWarehouse Container env vars send override parameters template`() {
    val container =
        factory.buildSendDataWarehouseContainer(
            "Organizationid", getWorkspace(), getRunTemplateNoParametersSend(), csmSimulationId)
    val expected =
        mapOf(
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to "simulationrunid",
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "CSM_SEND_DATAWAREHOUSE_PARAMETERS" to "false",
            "CSM_SEND_DATAWAREHOUSE_DATASETS" to "true",
            "ADX_DATA_INGESTION_URI" to "https://ingest-phoenix.westeurope.kusto.windows.net",
            "ADX_DATABASE" to "Organizationid-Test",
        )
    assertEquals(expected, container.envVars)
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
    assertEquals("twinengines.azurecr.io/cosmotech/testsolution_simulator:1.0.0", container.image)
  }

  @Test
  fun `Parameters Handler Container entrypoint valid`() {
    val container = this.buildApplyParametersContainer()
    assertEquals("entrypoint.py", container.entrypoint)
  }

  @Test
  fun `Parameters Handler Container bad template exception`() {
    assertThrows(IllegalStateException::class.java) {
      factory.buildApplyParametersContainer(
          getOrganization(), getWorkspace(), getSolution(), "badTemplate", csmSimulationId)
    }
  }

  @Test
  fun `Parameters Handler Container env vars valid`() {
    val container = this.buildApplyParametersContainer()
    this.validateEnvVarsSolutionContainer(container, "handle-parameters")
  }

  @Test
  fun `Parameters Handler Container env vars source local defined valid`() {
    val container =
        factory.buildApplyParametersContainer(
            getOrganization(),
            getWorkspace(),
            getSolutionLocalSources(),
            "testruntemplate",
            csmSimulationId)
    envVarsWithSourceLocalValid(
        container, "handle-parameters", "CSM_PARAMETERS_HANDLER_PROVIDER", "parameters_handler")
  }

  @Test
  fun `Parameters Handler Container env vars source defined valid`() {
    val container =
        factory.buildApplyParametersContainer(
            getOrganization(),
            getWorkspace(),
            getSolutionCloudSources(),
            "testruntemplate",
            csmSimulationId)
    envVarsWithSourceValid(
        container,
        "handle-parameters",
        "CSM_PARAMETERS_HANDLER_PROVIDER",
        "CSM_PARAMETERS_HANDLER_PATH",
        "parameters_handler")
  }

  @Test
  fun `Validate Container env vars source defined valid`() {
    val container =
        factory.buildValidateDataContainer(
            getOrganization(),
            getWorkspace(),
            getSolutionCloudSources(),
            "testruntemplate",
            csmSimulationId)
    envVarsWithSourceValid(
        container,
        "validate",
        "CSM_DATASET_VALIDATOR_PROVIDER",
        "CSM_DATASET_VALIDATOR_PATH",
        "validator")
  }

  @Test
  fun `prerun Container env vars source defined valid`() {
    val container =
        factory.buildPreRunContainer(
            getOrganization(),
            getWorkspace(),
            getSolutionCloudSources(),
            "testruntemplate",
            csmSimulationId)
    envVarsWithSourceValid(container, "prerun", "CSM_PRERUN_PROVIDER", "CSM_PRERUN_PATH", "prerun")
  }

  @Test
  fun `run Container env vars source defined valid`() {
    val container =
        factory.buildRunContainer(
            getOrganization(),
            getWorkspace(),
            getSolutionCloudSources(),
            "testruntemplate",
            csmSimulationId)
    envVarsWithSourceValid(container, "engine", "CSM_ENGINE_PROVIDER", "CSM_ENGINE_PATH", "engine")
  }

  @Test
  fun `postrun Container env vars source defined valid`() {
    val container =
        factory.buildPostRunContainer(
            getOrganization(),
            getWorkspace(),
            getSolutionCloudSources(),
            "testruntemplate",
            csmSimulationId)
    envVarsWithSourceValid(
        container, "postrun", "CSM_POSTRUN_PROVIDER", "CSM_POSTRUN_PATH", "postrun")
  }

  private fun envVarsWithSourceValid(
      container: ScenarioRunContainer,
      mode: String,
      providerEnvVar: String,
      resourceEnvVar: String,
      resource: String
  ) {
    val expected =
        mapOf(
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to "simulationrunid",
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "CSM_RUN_TEMPLATE_ID" to "testruntemplate",
            "CSM_CONTAINER_MODE" to mode,
            "CSM_CONTROL_PLANE_TOPIC" to
                "amqps://csm-phoenix.servicebus.windows.net/organizationid-test-scenariorun",
            "CSM_PROBES_MEASURES_TOPIC" to
                "amqps://csm-phoenix.servicebus.windows.net/organizationid-test",
            "CSM_SIMULATION" to "TestSimulation",
            providerEnvVar to "azureStorage",
            "AZURE_STORAGE_CONNECTION_STRING" to
                "DefaultEndpointsProtocol=https;AccountName=csmphoenix;AccountKey=42rmlBQ2IrxdIByLj79AecdIyYifSR04ZnGsBYt82tbM2clcP0QwJ9N+l/fLvyCzu9VZ8HPsQyM7jHe6CVSUig==;EndpointSuffix=core.windows.net",
            resourceEnvVar to "Organizationid/1/${resource}.zip",
        )
    assertEquals(expected, container.envVars)
  }

  private fun envVarsWithSourceLocalValid(
      container: ScenarioRunContainer,
      mode: String,
      providerEnvVar: String,
      resource: String
  ) {
    val expected =
        mapOf(
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to "simulationrunid",
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "CSM_RUN_TEMPLATE_ID" to "testruntemplate",
            "CSM_CONTAINER_MODE" to mode,
            "CSM_CONTROL_PLANE_TOPIC" to
                "amqps://csm-phoenix.servicebus.windows.net/organizationid-test-scenariorun",
            "CSM_PROBES_MEASURES_TOPIC" to
                "amqps://csm-phoenix.servicebus.windows.net/organizationid-test",
            "CSM_SIMULATION" to "TestSimulation",
            providerEnvVar to "local",
            "AZURE_STORAGE_CONNECTION_STRING" to
                "DefaultEndpointsProtocol=https;AccountName=csmphoenix;AccountKey=42rmlBQ2IrxdIByLj79AecdIyYifSR04ZnGsBYt82tbM2clcP0QwJ9N+l/fLvyCzu9VZ8HPsQyM7jHe6CVSUig==;EndpointSuffix=core.windows.net",
        )
    assertEquals(expected, container.envVars)
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
    val containers =
        factory.buildContainersPipeline(
            scenario, datasets, connectors, workspace, getOrganization(), solution, csmSimulationId)
    assertEquals(containers.size, 8)
  }

  @Test
  fun `Build all containers for a Scenario containers name list`() {
    val scenario = getScenario()
    val datasets = listOf(getDataset())
    val connectors = listOf(getConnector())
    val workspace = getWorkspace()
    val solution = getSolution()
    val containers =
        factory.buildContainersPipeline(
            scenario, datasets, connectors, workspace, getOrganization(), solution, csmSimulationId)
    val expected =
        listOf(
            "fetchDatasetContainer-1",
            "fetchScenarioParametersContainer",
            "applyParametersContainer",
            "validateDataContainer",
            "sendDataWarehouseContainer",
            "preRunContainer",
            "runContainer",
            "postRunContainer",
        )
    assertEquals(expected, containers.map { container -> container.name })
  }

  @Test
  fun `Build all containers Only Run`() {
    val scenario = getScenario()
    val datasets = listOf(getDataset())
    val connectors = listOf(getConnector())
    val workspace = getWorkspace()
    val solution = getSolutionOnlyRun()
    val containers =
        factory.buildContainersPipeline(
            scenario, datasets, connectors, workspace, getOrganization(), solution, csmSimulationId)
    assertEquals(containers.size, 1)
  }

  @Test
  fun `Build all containers for a Scenario with 3 Datasets count`() {
    val scenario = getScenarioThreeDatasets()
    val datasets = listOf(getDataset(), getDataset2(), getDataset3())
    val connectors = listOf(getConnector(), getConnector2(), getConnector3())
    val workspace = getWorkspace()
    val solution = getSolution()
    val containers =
        factory.buildContainersPipeline(
            scenario, datasets, connectors, workspace, getOrganization(), solution, csmSimulationId)
    assertEquals(containers.size, 10)
  }

  @Test
  fun `Build all containers for a Scenario 3 Datasets containers name list`() {
    val scenario = getScenarioThreeDatasets()
    val datasets = listOf(getDataset(), getDataset2(), getDataset3())
    val connectors = listOf(getConnector(), getConnector2(), getConnector3())
    val workspace = getWorkspace()
    val solution = getSolution()
    val containers =
        factory.buildContainersPipeline(
            scenario, datasets, connectors, workspace, getOrganization(), solution, csmSimulationId)
    val expected =
        listOf(
            "fetchDatasetContainer-1",
            "fetchDatasetContainer-2",
            "fetchDatasetContainer-3",
            "fetchScenarioParametersContainer",
            "applyParametersContainer",
            "validateDataContainer",
            "sendDataWarehouseContainer",
            "preRunContainer",
            "runContainer",
            "postRunContainer",
        )
    assertEquals(expected, containers.map { container -> container.name })
  }

  @Test
  fun `Build all containers for a Scenario 3 Datasets containers image list`() {
    val scenario = getScenarioThreeDatasets()
    val datasets = listOf(getDataset(), getDataset2(), getDataset3())
    val connectors = listOf(getConnector(), getConnector2(), getConnector3())
    val workspace = getWorkspace()
    val solution = getSolution()
    val containers =
        factory.buildContainersPipeline(
            scenario, datasets, connectors, workspace, getOrganization(), solution, csmSimulationId)
    val expected =
        listOf(
            "ghcr.io/cosmotech/test_connector:1.0.0",
            "ghcr.io/cosmotech/test_connector2:1.0.0",
            "ghcr.io/cosmotech/test_connector3:1.0.0",
            "ghcr.io/cosmotech/scenariofetchparameters:1.0.0",
            "twinengines.azurecr.io/cosmotech/testsolution_simulator:1.0.0",
            "twinengines.azurecr.io/cosmotech/testsolution_simulator:1.0.0",
            "ghcr.io/cosmotech/senddatawarehouse:1.0.0",
            "twinengines.azurecr.io/cosmotech/testsolution_simulator:1.0.0",
            "twinengines.azurecr.io/cosmotech/testsolution_simulator:1.0.0",
            "twinengines.azurecr.io/cosmotech/testsolution_simulator:1.0.0",
        )
    assertEquals(expected, containers.map { container -> container.image })
  }

  @Test
  fun `Build all containers for a Scenario 3 Datasets containers env vars fetch`() {
    val scenario = getScenarioThreeDatasets()
    val datasets = listOf(getDataset(), getDataset2(), getDataset3())
    val connectors = listOf(getConnector(), getConnector2(), getConnector3())
    val workspace = getWorkspace()
    val solution = getSolution()
    val containers =
        factory.buildContainersPipeline(
            scenario, datasets, connectors, workspace, getOrganization(), solution, csmSimulationId)
    val container = containers.find { container -> container.name == "fetchDatasetContainer-2" }
    val expected =
        mapOf(
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to "simulationrunid",
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "CSM_FETCH_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "ENV_PARAM_1" to "env_param1_value",
            "ENV_PARAM_2" to "env_param2_value",
            "ENV_PARAM_3" to "env_param3_value")
    assertEquals(expected, container?.envVars)
  }

  @Test
  fun `Build all containers for a Scenario containers name list Only Run`() {
    val scenario = getScenario()
    val datasets = listOf(getDataset())
    val connectors = listOf(getConnector())
    val workspace = getWorkspace()
    val solution = getSolutionOnlyRun()
    val containers =
        factory.buildContainersPipeline(
            scenario, datasets, connectors, workspace, getOrganization(), solution, csmSimulationId)
    val expected =
        listOf(
            "runContainer",
        )
    assertEquals(expected, containers.map { container -> container.name })
  }

  @Test
  fun `Build start containers node Label`() {
    val scenario = getScenario()
    val datasets = listOf(getDataset())
    val connectors = listOf(getConnector())
    val workspace = getWorkspace()
    val solution = getSolution()
    val startContainers =
        factory.buildContainersStart(
            scenario, datasets, connectors, workspace, getOrganization(), solution, csmSimulationId)
    assertEquals("highcpupool", startContainers.nodeLabel)
  }

  @Test
  fun `Build start containers node Label %NONE%`() {
    val scenario = getScenario()
    val datasets = listOf(getDataset())
    val connectors = listOf(getConnector())
    val workspace = getWorkspace()
    val solution = getSolutionNoPool()
    val startContainers =
        factory.buildContainersStart(
            scenario, datasets, connectors, workspace, getOrganization(), solution, csmSimulationId)
    assertEquals("basicpool", startContainers.nodeLabel)
  }

  @Test
  fun `Build start containers node Label default`() {
    val scenario = getScenario()
    val datasets = listOf(getDataset())
    val connectors = listOf(getConnector())
    val workspace = getWorkspace()
    val solution = getSolutionNonePool()
    val startContainers =
        factory.buildContainersStart(
            scenario, datasets, connectors, workspace, getOrganization(), solution, csmSimulationId)
    assertNull(startContainers.nodeLabel)
  }

  @Test
  fun `Build start containers generate name`() {
    val scenario = getScenario()
    val datasets = listOf(getDataset())
    val connectors = listOf(getConnector())
    val workspace = getWorkspace()
    val solution = getSolution()
    val startContainers =
        factory.buildContainersStart(
            scenario, datasets, connectors, workspace, getOrganization(), solution, csmSimulationId)
    assertEquals("workflow-aqwxsz-", startContainers.generateName)
  }

  @Test
  fun `Build all containers for a Scenario DATASETID containers name list`() {
    val scenario = getScenarioDatasetIds()
    val datasets = listOf(getDataset(), getDataset2(), getDataset3())
    val connectors = listOf(getConnector(), getConnector2(), getConnector3())
    val workspace = getWorkspace()
    val solution = getSolutionDatasetIds()
    val containers =
        factory.buildContainersPipeline(
            scenario, datasets, connectors, workspace, getOrganization(), solution, csmSimulationId)
    val expected =
        listOf(
            "fetchDatasetContainer-1",
            "fetchScenarioParametersContainer",
            "fetchScenarioDatasetParametersContainer-1",
            "fetchScenarioDatasetParametersContainer-2",
            "fetchScenarioDatasetParametersContainer-3",
            "applyParametersContainer",
            "validateDataContainer",
            "sendDataWarehouseContainer",
            "preRunContainer",
            "runContainer",
            "postRunContainer",
        )
    assertEquals(expected, containers.map { container -> container.name })
  }

  @Test
  fun `Build all containers for a Scenario DATASETID containers image list`() {
    val scenario = getScenarioDatasetIds()
    val datasets = listOf(getDataset(), getDataset2(), getDataset3())
    val connectors = listOf(getConnector(), getConnector2(), getConnector3())
    val workspace = getWorkspace()
    val solution = getSolutionDatasetIds()
    val containers =
        factory.buildContainersPipeline(
            scenario, datasets, connectors, workspace, getOrganization(), solution, csmSimulationId)
    val expected =
        listOf(
            "ghcr.io/cosmotech/test_connector:1.0.0",
            "ghcr.io/cosmotech/scenariofetchparameters:1.0.0",
            "ghcr.io/cosmotech/test_connector:1.0.0",
            "ghcr.io/cosmotech/test_connector2:1.0.0",
            "ghcr.io/cosmotech/test_connector3:1.0.0",
            "twinengines.azurecr.io/cosmotech/testsolution_simulator:1.0.0",
            "twinengines.azurecr.io/cosmotech/testsolution_simulator:1.0.0",
            "ghcr.io/cosmotech/senddatawarehouse:1.0.0",
            "twinengines.azurecr.io/cosmotech/testsolution_simulator:1.0.0",
            "twinengines.azurecr.io/cosmotech/testsolution_simulator:1.0.0",
            "twinengines.azurecr.io/cosmotech/testsolution_simulator:1.0.0",
        )
    assertEquals(expected, containers.map { container -> container.image })
  }

  @Test
  fun `Build all containers for a Scenario DATASETID containers env vars`() {
    parametersDatasetEnvTest("1", "param2")
  }

  @Test
  fun `Build all containers for a Scenario DATASETID containers env vars 2`() {
    parametersDatasetEnvTest("2", "param3")
  }

  @Test
  fun `Full get Start Info csmSimulationId`() {
    val startInfo = getStartInfoFromIds()
    assertNotEquals("", startInfo.csmSimulationId)
  }

  @Test
  fun `Full get Start Info name`() {
    val startInfo = getStartInfoFromIds()
    assertEquals("workflow-aqwxsz-", startInfo.startContainers.generateName)
  }

  @Test
  fun `Full get Start Info pool`() {
    val startInfo = getStartInfoFromIds()
    assertEquals("highcpupool", startInfo.startContainers.nodeLabel)
  }

  @Test
  fun `Full get Start Info containers name`() {
    val startInfo = getStartInfoFromIds()
    val expected =
        listOf(
            "fetchDatasetContainer-1",
            "fetchDatasetContainer-2",
            "fetchScenarioParametersContainer",
            "fetchScenarioDatasetParametersContainer-1",
            "fetchScenarioDatasetParametersContainer-2",
            "fetchScenarioDatasetParametersContainer-3",
            "applyParametersContainer",
            "validateDataContainer",
            "sendDataWarehouseContainer",
            "preRunContainer",
            "runContainer",
            "postRunContainer",
        )
    assertEquals(expected, startInfo.startContainers.containers.map { container -> container.name })
  }

  @Test
  fun `Full get Start Info images`() {
    val startInfo = getStartInfoFromIds()
    val expected =
        listOf(
            "ghcr.io/cosmotech/test_connector:1.0.0",
            "ghcr.io/cosmotech/test_connector2:1.0.0",
            "ghcr.io/cosmotech/scenariofetchparameters:1.0.0",
            "ghcr.io/cosmotech/test_connector:1.0.0",
            "ghcr.io/cosmotech/test_connector2:1.0.0",
            "ghcr.io/cosmotech/test_connector3:1.0.0",
            "twinengines.azurecr.io/cosmotech/testsolution_simulator:1.0.0",
            "twinengines.azurecr.io/cosmotech/testsolution_simulator:1.0.0",
            "ghcr.io/cosmotech/senddatawarehouse:1.0.0",
            "twinengines.azurecr.io/cosmotech/testsolution_simulator:1.0.0",
            "twinengines.azurecr.io/cosmotech/testsolution_simulator:1.0.0",
            "twinengines.azurecr.io/cosmotech/testsolution_simulator:1.0.0",
        )
    assertEquals(
        expected, startInfo.startContainers.containers.map { container -> container.image })
  }

  private fun getStartInfoFromIds(): StartInfo {
    val organizationId = "Organizationid"
    val workspaceId = "workspaceid"
    val scenarioId = "AQWXSZ"

    val scenarioRunService = mockk<ScenariorunApiService>()
    val scenarioService = mockk<ScenarioApiService>()
    val workspaceService = mockk<WorkspaceApiService>()
    val solutionService = mockk<SolutionApiService>()
    val organizationService = mockk<OrganizationApiService>()
    val connectorService = mockk<ConnectorApiService>()
    val datasetService = mockk<DatasetApiService>()

    every { organizationService.findOrganizationById(organizationId) } returns getOrganization()
    every { workspaceService.findWorkspaceById(organizationId, workspaceId) } returns getWorkspace()
    every { solutionService.findSolutionById(organizationId, "1") } returns getSolutionDatasetIds()
    every { scenarioService.findScenarioById(organizationId, workspaceId, scenarioId) } returns
        getScenarioTwoDatasetsAndDatasetIds()
    every { datasetService.findDatasetById(organizationId, "1") } returns getDataset()
    every { datasetService.findDatasetById(organizationId, "2") } returns getDataset2()
    every { datasetService.findDatasetById(organizationId, "3") } returns getDataset3()
    every { connectorService.findConnectorById("AzErTyUiOp") } returns getConnector()
    every { connectorService.findConnectorById("AzErTyUiOp2") } returns getConnector2()
    every { connectorService.findConnectorById("AzErTyUiOp3") } returns getConnector3()

    return factory.getStartInfo(
        organizationId,
        workspaceId,
        scenarioId,
        scenarioRunService,
        scenarioService,
        workspaceService,
        solutionService,
        organizationService,
        connectorService,
        datasetService)
  }

  private fun parametersDatasetEnvTest(nameId: String, param: String) {
    val scenario = getScenarioDatasetIds()
    val datasets = listOf(getDataset(), getDataset2(), getDataset3())
    val connectors = listOf(getConnector(), getConnector2(), getConnector3())
    val workspace = getWorkspace()
    val solution = getSolutionDatasetIds()
    val containers =
        factory.buildContainersPipeline(
            scenario, datasets, connectors, workspace, getOrganization(), solution, csmSimulationId)
    val container =
        containers.find { container ->
          container.name == "fetchScenarioDatasetParametersContainer-${nameId}"
        }
    val expected =
        mapOf(
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to "simulationrunid",
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "CSM_FETCH_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters/${param}",
            "ENV_PARAM_1" to "env_param1_value",
            "ENV_PARAM_2" to "env_param2_value",
            "ENV_PARAM_3" to "env_param3_value")
    assertEquals(expected, container?.envVars)
  }

  private fun buildApplyParametersContainer(): ScenarioRunContainer {
    return factory.buildApplyParametersContainer(
        getOrganization(), getWorkspace(), getSolution(), "testruntemplate", csmSimulationId)
  }

  private fun buildValidateDataContainer(): ScenarioRunContainer {
    return factory.buildValidateDataContainer(
        getOrganization(), getWorkspace(), getSolution(), "testruntemplate", csmSimulationId)
  }

  private fun buildPreRunContainer(): ScenarioRunContainer {
    return factory.buildPreRunContainer(
        getOrganization(), getWorkspace(), getSolution(), "testruntemplate", csmSimulationId)
  }

  private fun buildRunContainer(): ScenarioRunContainer {
    return factory.buildRunContainer(
        getOrganization(), getWorkspace(), getSolution(), "testruntemplate", csmSimulationId)
  }

  private fun buildPostRunContainer(): ScenarioRunContainer {
    return factory.buildPostRunContainer(
        getOrganization(), getWorkspace(), getSolution(), "testruntemplate", csmSimulationId)
  }

  private fun validateEnvVarsSolutionContainer(container: ScenarioRunContainer, mode: String) {
    val expected =
        mapOf(
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to "simulationrunid",
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "CSM_RUN_TEMPLATE_ID" to "testruntemplate",
            "CSM_CONTAINER_MODE" to mode,
            "CSM_CONTROL_PLANE_TOPIC" to
                "amqps://csm-phoenix.servicebus.windows.net/organizationid-test-scenariorun",
            "CSM_PROBES_MEASURES_TOPIC" to
                "amqps://csm-phoenix.servicebus.windows.net/organizationid-test",
            "CSM_SIMULATION" to "TestSimulation")
    assertEquals(expected, container.envVars)
  }

  private fun getDatasetWorkspaceFile(): Dataset {
    val connector = getDatasetConnectorWorkspaceFile()
    return Dataset(id = "1", name = "Test Dataset", connector = connector)
  }

  private fun getDatasetWorkspaceStorage(): Dataset {
    val connector = getDatasetConnectorWorkspaceStorage()
    return Dataset(id = "1", name = "Test Dataset", connector = connector)
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

  private fun getDatasetConnectorWorkspaceFile(): DatasetConnector {
    return DatasetConnector(
        id = "AzErTyUiOp",
        parametersValues =
            mapOf(
                "EnvParam1" to "%WORKSPACE_FILE%/workspace.env",
                "Param1" to "%WORKSPACE_FILE%/workspace.param",
            ))
  }

  private fun getDatasetConnectorWorkspaceStorage(): DatasetConnector {
    return DatasetConnector(
        id = "AzErTyUiOp",
        parametersValues =
            mapOf(
                "EnvParam1" to "%STORAGE_CONNECTION_STRING%",
            ))
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

  private fun getConnector(
      id: String,
      key: String = "TestConnector",
      repository: String = "cosmotech/test_connector"
  ): Connector {
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

  private fun getConnectorWorkspaceFile(
      id: String = "AzErTyUiOp",
      key: String = "TestConnector",
      repository: String = "cosmotech/test_connector"
  ): Connector {
    val envparam1 =
        ConnectorParameter(id = "EnvParam1", label = "Env param 1", envVar = "ENV_PARAM_1")
    val param1 = ConnectorParameter(id = "Param1", label = "Param 1")
    val parametersList = listOf(envparam1, param1)
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

  private fun getConnectorWorkspaceStorage(
      id: String = "AzErTyUiOp",
      key: String = "TestConnector",
      repository: String = "cosmotech/test_connector"
  ): Connector {
    val envparam1 =
        ConnectorParameter(
            id = "EnvParam1", label = "Env param 1", envVar = "AZURE_STORAGE_CONNECTION_STRING")
    val parametersList = listOf(envparam1)
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
        id = "workspaceid",
        key = "Test",
        name = "Test Workspace",
        description = "Test Workspace Description",
        version = "1.0.0",
        solution =
            WorkspaceSolution(
                solutionId = "1",
            ),
    )
  }

  private fun getWorkspaceNoSend(): Workspace {
    return Workspace(
        id = "workspaceid",
        key = "Test",
        name = "Test Workspace",
        description = "Test Workspace Description",
        version = "1.0.0",
        solution =
            WorkspaceSolution(
                solutionId = "1",
            ),
        sendInputToDataWarehouse = false,
    )
  }

  private fun getWorkspaceNoDB(): Workspace {
    return Workspace(
        id = "workspaceid",
        key = "Test",
        name = "Test Workspace",
        solution =
            WorkspaceSolution(
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

  private fun getSolutionDatasetIds(): Solution {
    return Solution(
        id = "1",
        key = "TestSolution",
        name = "Test Solution",
        repository = "cosmotech/testsolution_simulator",
        version = "1.0.0",
        runTemplates = listOf(getRunTemplateDatasetIds()),
        parameters =
            listOf(
                RunTemplateParameter(
                    id = "param1",
                    labels = mapOf("en" to "Parameter 1"),
                    varType = "string",
                ),
                RunTemplateParameter(
                    id = "param2",
                    labels = mapOf("en" to "Parameter Dataset 1"),
                    varType = "%DATASETID%"),
                RunTemplateParameter(
                    id = "param3",
                    labels = mapOf("en" to "Parameter Dataset 2"),
                    varType = "%DATASETID%"),
                RunTemplateParameter(
                    id = "param4",
                    labels = mapOf("en" to "Parameter Dataset 3"),
                    varType = "%DATASETID%"),
                RunTemplateParameter(
                    id = "param5",
                    labels = mapOf("en" to "Parameter 5"),
                    varType = "int",
                ),
            ),
        parameterGroups =
            listOf(
                RunTemplateParameterGroup(
                    id = "group1",
                    labels = mapOf("en" to "Parameter Group 1"),
                    parameters =
                        listOf(
                            "param1",
                            "param2",
                            "param3",
                            "param4",
                            "param5",
                        ))))
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

  private fun getSolutionNonePool(): Solution {
    return Solution(
        id = "1",
        key = "TestSolution",
        name = "Test Solution",
        repository = "cosmotech/testsolution_simulator",
        version = "1.0.0",
        runTemplates = listOf(getRunTemplateNonePool()),
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

  private fun getSolutionCloudSources(): Solution {
    return Solution(
        id = "1",
        key = "TestSolution",
        name = "Test Solution",
        repository = "cosmotech/testsolution_simulator",
        version = "1.0.0",
        runTemplates = listOf(getRunTemplateCloudSources()),
    )
  }

  private fun getSolutionLocalSources(): Solution {
    return Solution(
        id = "1",
        key = "TestSolution",
        name = "Test Solution",
        repository = "cosmotech/testsolution_simulator",
        version = "1.0.0",
        runTemplates = listOf(getRunTemplateLocalSources()),
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

  private fun getRunTemplateDatasetIds(): RunTemplate {
    return RunTemplate(
        id = "testruntemplate",
        name = "Test Run",
        csmSimulation = "TestSimulation",
        computeSize = "highcpu",
        parameterGroups = listOf("group1"),
    )
  }

  private fun getRunTemplateNoPool(): RunTemplate {
    return RunTemplate(
        id = "testruntemplate",
        name = "Test Run",
        csmSimulation = "TestSimulation",
    )
  }

  private fun getRunTemplateNonePool(): RunTemplate {
    return RunTemplate(
        id = "testruntemplate",
        name = "Test Run",
        csmSimulation = "TestSimulation",
        computeSize = "%NONE%",
    )
  }

  private fun getRunTemplateNoDatasetsSend(): RunTemplate {

    return RunTemplate(
        id = "testruntemplate",
        name = "Test Run",
        csmSimulation = "TestSimulation",
        sendDatasetsToDataWarehouse = false)
  }

  private fun getRunTemplateNoParametersSend(): RunTemplate {

    return RunTemplate(
        id = "testruntemplate",
        name = "Test Run",
        csmSimulation = "TestSimulation",
        sendInputParametersToDataWarehouse = false)
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

  private fun getRunTemplateCloudSources(): RunTemplate {

    return RunTemplate(
        id = "testruntemplate",
        name = "Test Run",
        csmSimulation = "TestSimulation",
        parametersHandlerSource = RunTemplateStepSource.cloud,
        datasetValidatorSource = RunTemplateStepSource.cloud,
        preRunSource = RunTemplateStepSource.cloud,
        runSource = RunTemplateStepSource.cloud,
        postRunSource = RunTemplateStepSource.cloud,
    )
  }

  private fun getRunTemplateLocalSources(): RunTemplate {

    return RunTemplate(
        id = "testruntemplate",
        name = "Test Run",
        csmSimulation = "TestSimulation",
        parametersHandlerSource = RunTemplateStepSource.local,
        datasetValidatorSource = RunTemplateStepSource.local,
        preRunSource = RunTemplateStepSource.local,
        runSource = RunTemplateStepSource.local,
        postRunSource = RunTemplateStepSource.local,
    )
  }

  private fun getScenario(): Scenario {
    return Scenario(
        id = "AQWXSZ",
        name = "Test Scenario",
        runTemplateId = "testruntemplate",
        datasetList = listOf("1"),
    )
  }

  private fun getScenarioDatasetIds(): Scenario {
    return Scenario(
        id = "AQWXSZ",
        name = "Test Scenario",
        runTemplateId = "testruntemplate",
        datasetList = listOf("1"),
        parametersValues =
            listOf(
                ScenarioRunTemplateParameterValue(
                    parameterId = "param1",
                    value = "valParam1",
                ),
                ScenarioRunTemplateParameterValue(
                    parameterId = "param2",
                    value = "1",
                ),
                ScenarioRunTemplateParameterValue(
                    parameterId = "param3",
                    value = "2",
                ),
                ScenarioRunTemplateParameterValue(
                    parameterId = "param4",
                    value = "3",
                ),
                ScenarioRunTemplateParameterValue(
                    parameterId = "param5",
                    value = "999",
                ),
            ))
  }

  private fun getScenarioTwoDatasetsAndDatasetIds(): Scenario {
    return Scenario(
        id = "AQWXSZ",
        name = "Test Scenario",
        runTemplateId = "testruntemplate",
        datasetList = listOf("1", "2"),
        parametersValues =
            listOf(
                ScenarioRunTemplateParameterValue(
                    parameterId = "param1",
                    value = "valParam1",
                ),
                ScenarioRunTemplateParameterValue(
                    parameterId = "param2",
                    value = "1",
                ),
                ScenarioRunTemplateParameterValue(
                    parameterId = "param3",
                    value = "2",
                ),
                ScenarioRunTemplateParameterValue(
                    parameterId = "param4",
                    value = "3",
                ),
                ScenarioRunTemplateParameterValue(
                    parameterId = "param5",
                    value = "999",
                ),
            ))
  }

  private fun getScenarioThreeDatasets(): Scenario {
    return Scenario(
        id = "AQWXSZ",
        name = "Test Scenario",
        runTemplateId = "testruntemplate",
        datasetList = listOf("1", "2", "3"),
    )
  }

  private fun getOrganization(): Organization {
    return Organization(
        id = "Organizationid",
        name = "Organization Test",
    )
  }
}
