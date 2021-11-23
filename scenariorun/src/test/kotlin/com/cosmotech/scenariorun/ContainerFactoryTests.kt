// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun

import com.cosmotech.api.azure.eventhubs.AzureEventHubsClient
import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.config.CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureCredentials
import com.cosmotech.api.config.CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureCredentials.CsmPlatformAzureCredentialsCore
import com.cosmotech.api.config.CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus
import com.cosmotech.api.config.CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication
import com.cosmotech.api.config.CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication.SharedAccessPolicyCredentials
import com.cosmotech.api.config.CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication.SharedAccessPolicyDetails
import com.cosmotech.api.config.CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication.Strategy.SHARED_ACCESS_POLICY
import com.cosmotech.api.config.CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication.Strategy.TENANT_CLIENT_CREDENTIALS
import com.cosmotech.api.exceptions.CsmClientException
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
import com.cosmotech.scenariorun.container.StartInfo
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
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

private const val CSM_SIMULATION_ID = "simulationrunid"

@Suppress("TooManyFunctions", "LargeClass")
@ExtendWith(MockKExtension::class)
class ContainerFactoryTests {

  @MockK(relaxed = true) private lateinit var azure: CsmPlatformProperties.CsmPlatformAzure
  @MockK(relaxed = true) private lateinit var csmPlatformProperties: CsmPlatformProperties

  @MockK private lateinit var scenarioService: ScenarioApiService
  @MockK private lateinit var workspaceService: WorkspaceApiService
  @MockK private lateinit var solutionService: SolutionApiService
  @MockK private lateinit var organizationService: OrganizationApiService
  @MockK private lateinit var connectorService: ConnectorApiService
  @MockK private lateinit var datasetService: DatasetApiService
  @MockK(relaxed = true) lateinit var azureEventHubsClient: AzureEventHubsClient

  @InjectMockKs private lateinit var factory: ContainerFactory

  @BeforeTest
  fun setUp() {
    MockKAnnotations.init(this)

    every { azure.appIdUri } returns "http://dev.api.cosmotech.com"
    every { azure.credentials } returns
        CsmPlatformAzureCredentials(
            core =
                CsmPlatformAzureCredentialsCore(
                    tenantId = "12345678",
                    clientId = "98765432",
                    clientSecret = "azertyuiop",
                    aadPodIdBinding = "phoenixdev-pod-identity",
                ),
            customer =
                CsmPlatformAzureCredentials.CsmPlatformAzureCredentialsCustomer(
                    tenantId = "customer-app-registration-tenantId",
                    clientId = "customer-app-registration-clientId",
                    clientSecret = "customer-app-registration-clientSecret"))
    every { azure.eventBus } returns
        CsmPlatformAzureEventBus(
            baseUri = "amqps://csm-phoenix.servicebus.windows.net",
        )
    every { azure.dataWarehouseCluster } returns
        CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureDataWarehouseCluster(
            options =
                CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureDataWarehouseCluster.Options(
                    ingestionUri = "https://ingest-phoenix.westeurope.kusto.windows.net",
                ),
            baseUri = "https://phoenix.westeurope.kusto.windows.net",
        )
    every { azure.storage } returns
        CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureStorage(
            connectionString = "csmphoenix_storage_connection_string",
            baseUri = "Not Used",
            resourceUri = "Not Used",
        )
    every { azure.containerRegistries } returns
        CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureContainerRegistries(
            core = "ghcr.io", solutions = "twinengines.azurecr.io")
    every { csmPlatformProperties.azure } returns azure
    every { csmPlatformProperties.api } returns
        CsmPlatformProperties.Api(
            baseUrl = "https://api.cosmotech.com",
            version = "v1",
            basePath = "basepath",
        )
    every { csmPlatformProperties.images } returns
        CsmPlatformProperties.CsmImages(
            scenarioFetchParameters = "cosmotech/scenariofetchparameters:1.0.0",
            sendDataWarehouse = "cosmotech/senddatawarehouse:1.0.0",
        )
  }

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
            "Test",
            CSM_SIMULATION_ID)
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
            "Test",
            CSM_SIMULATION_ID)
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
            "Test",
            "workspaceid",
            CSM_SIMULATION_ID)
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
          "Test",
          CSM_SIMULATION_ID)
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
            "Test",
            CSM_SIMULATION_ID)
    val expected =
        mapOf(
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to "simulationrunid",
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "Organizationid-Test",
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
            "Test",
            CSM_SIMULATION_ID)
    val expected =
        mapOf(
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to "simulationrunid",
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "Organizationid-Test",
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
            "Test",
            CSM_SIMULATION_ID)
    val expected =
        mapOf(
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to "simulationrunid",
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "Organizationid-Test",
            "CSM_FETCH_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "AZURE_STORAGE_CONNECTION_STRING" to "csmphoenix_storage_connection_string",
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
            "Test",
            CSM_SIMULATION_ID)
    val expected =
        mapOf(
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to "simulationrunid",
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "Organizationid-Test",
            "CSM_FETCH_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
        )
    assertEquals(expected, container.envVars)
  }

  @Test
  fun `Dataset managed identity env vars valid`() {
    val container =
        factory.buildFromDataset(
            getDatasetNoVars(),
            getConnectorNoVarsManagedIdentity(),
            1,
            false,
            null,
            "Organizationid",
            "workspaceid",
            "Test",
            CSM_SIMULATION_ID)
    val expected =
        mapOf(
            "CSM_AZURE_MANAGED_IDENTITY" to "true",
            "CSM_SIMULATION_ID" to "simulationrunid",
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "Organizationid-Test",
            "CSM_FETCH_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
        )
    assertEquals(expected, container.envVars)
  }

  @Test
  fun `Dataset managed identity labels valid`() {
    val container =
        factory.buildFromDataset(
            getDatasetNoVars(),
            getConnectorNoVarsManagedIdentity(),
            1,
            false,
            null,
            "Organizationid",
            "workspaceid",
            "Test",
            CSM_SIMULATION_ID)
    val expected = mapOf("aadpodidbinding" to "phoenixdev-pod-identity")
    assertEquals(expected, container.labels)
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
            "Test",
            CSM_SIMULATION_ID)
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
            "Test",
            CSM_SIMULATION_ID)
    val expected = listOf("param1_value", "param2_value", "param3_value")
    assertEquals(expected, container.runArgs)
  }

  @Test
  fun `Fetch Scenario Parameters Container is not null`() {
    val container =
        factory.buildScenarioParametersFetchContainer("1", "2", "3", "Test", CSM_SIMULATION_ID)
    assertNotNull(container)
  }

  @Test
  fun `Fetch Scenario Parameters Container name valid`() {
    val container =
        factory.buildScenarioParametersFetchContainer("1", "2", "3", "Test", CSM_SIMULATION_ID)
    assertEquals("fetchScenarioParametersContainer", container.name)
  }

  @Test
  fun `Fetch Scenario Parameters Container image valid`() {
    val container =
        factory.buildScenarioParametersFetchContainer("1", "2", "3", "Test", CSM_SIMULATION_ID)
    assertEquals("ghcr.io/cosmotech/scenariofetchparameters:1.0.0", container.image)
  }

  @Test
  fun `Fetch Scenario Parameters Container env vars valid`() {
    val container =
        factory.buildScenarioParametersFetchContainer("1", "2", "Test", "3", CSM_SIMULATION_ID)
    val expected =
        mapOf(
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to "simulationrunid",
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "1-Test",
            "CSM_FETCH_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "CSM_ORGANIZATION_ID" to "1",
            "CSM_WORKSPACE_ID" to "2",
            "CSM_SCENARIO_ID" to "3")
    assertEquals(expected, container.envVars)
  }

  @Test
  fun `Fetch Scenario Parameters Container env vars valid json`() {
    val container =
        factory.buildScenarioParametersFetchContainer(
            "1", "2", "Test", "3", CSM_SIMULATION_ID, true)
    val expected =
        mapOf(
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to "simulationrunid",
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "1-Test",
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
            "Organizationid", getWorkspace(), getRunTemplate(), CSM_SIMULATION_ID)
    assertNotNull(container)
  }

  @Test
  fun `Send DataWarehouseContainer name valid`() {
    val container =
        factory.buildSendDataWarehouseContainer(
            "Organizationid", getWorkspace(), getRunTemplate(), CSM_SIMULATION_ID)
    assertEquals("sendDataWarehouseContainer", container.name)
  }

  @Test
  fun `Send DataWarehouse Container image valid`() {
    val container =
        factory.buildSendDataWarehouseContainer(
            "Organizationid", getWorkspace(), getRunTemplate(), CSM_SIMULATION_ID)
    assertEquals("ghcr.io/cosmotech/senddatawarehouse:1.0.0", container.image)
  }

  @Test
  fun `Send DataWarehouse Container env vars valid`() {
    val container =
        factory.buildSendDataWarehouseContainer(
            "Organizationid", getWorkspace(), getRunTemplate(), CSM_SIMULATION_ID)
    val expected =
        mapOf(
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to "simulationrunid",
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "Organizationid-Test",
            "CSM_SEND_DATAWAREHOUSE_PARAMETERS" to "true",
            "CSM_SEND_DATAWAREHOUSE_DATASETS" to "true",
        )
    assertEquals(expected, container.envVars)
  }

  @Test
  fun `Send DataWarehouse Container no send env vars`() {
    val container =
        factory.buildSendDataWarehouseContainer(
            "Organizationid", getWorkspaceNoSend(), getRunTemplate(), CSM_SIMULATION_ID)
    val expected =
        mapOf(
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to "simulationrunid",
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "Organizationid-Test",
            "CSM_SEND_DATAWAREHOUSE_PARAMETERS" to "false",
            "CSM_SEND_DATAWAREHOUSE_DATASETS" to "false",
        )
    assertEquals(expected, container.envVars)
  }

  @Test
  fun `Send DataWarehouse Container env vars send override dataset template`() {
    val container =
        factory.buildSendDataWarehouseContainer(
            "Organizationid", getWorkspace(), getRunTemplateNoDatasetsSend(), CSM_SIMULATION_ID)
    val expected =
        mapOf(
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to "simulationrunid",
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "Organizationid-Test",
            "CSM_SEND_DATAWAREHOUSE_PARAMETERS" to "true",
            "CSM_SEND_DATAWAREHOUSE_DATASETS" to "false",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "Organizationid-Test",
        )
    assertEquals(expected, container.envVars)
  }

  @Test
  fun `Send DataWarehouse Container env vars send override parameters template`() {
    val container =
        factory.buildSendDataWarehouseContainer(
            "Organizationid", getWorkspace(), getRunTemplateNoParametersSend(), CSM_SIMULATION_ID)
    val expected =
        mapOf(
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to "simulationrunid",
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "Organizationid-Test",
            "CSM_SEND_DATAWAREHOUSE_PARAMETERS" to "false",
            "CSM_SEND_DATAWAREHOUSE_DATASETS" to "true",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "Organizationid-Test",
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
          getOrganization(), getWorkspace(), getSolution(), "badTemplate", CSM_SIMULATION_ID)
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
            CSM_SIMULATION_ID)
    envVarsWithSourceLocalValid(container, "handle-parameters", "CSM_PARAMETERS_HANDLER_PROVIDER")
  }

  @Test
  fun `Parameters Handler Container env vars source defined valid`() {
    val container =
        factory.buildApplyParametersContainer(
            getOrganization(),
            getWorkspace(),
            getSolutionCloudSources(),
            "testruntemplate",
            CSM_SIMULATION_ID)
    envVarsWithSourceValid(
        container,
        "handle-parameters",
        "CSM_PARAMETERS_HANDLER_PROVIDER",
        "CSM_PARAMETERS_HANDLER_PATH",
        "parameters_handler",
        "testruntemplate")
  }

  @Test
  fun `Validate Container env vars source defined valid`() {
    val container =
        factory.buildValidateDataContainer(
            getOrganization(),
            getWorkspace(),
            getSolutionCloudSources(),
            "testruntemplate",
            CSM_SIMULATION_ID)
    envVarsWithSourceValid(
        container,
        "validate",
        "CSM_DATASET_VALIDATOR_PROVIDER",
        "CSM_DATASET_VALIDATOR_PATH",
        "validator",
        "testruntemplate")
  }

  @Test
  fun `prerun Container env vars source defined valid`() {
    val container =
        factory.buildPreRunContainer(
            getOrganization(),
            getWorkspace(),
            getSolutionCloudSources(),
            "testruntemplate",
            CSM_SIMULATION_ID)
    envVarsWithSourceValid(
        container, "prerun", "CSM_PRERUN_PROVIDER", "CSM_PRERUN_PATH", "prerun", "testruntemplate")
  }

  @Test
  fun `run Container env vars source defined valid`() {
    val container =
        factory.buildRunContainer(
            getOrganization(),
            getWorkspace(),
            getSolutionCloudSources(),
            "testruntemplate",
            CSM_SIMULATION_ID)
    envVarsWithSourceValid(
        container, "engine", "CSM_ENGINE_PROVIDER", "CSM_ENGINE_PATH", "engine", "testruntemplate")
  }

  @Test
  fun `postrun Container env vars source defined valid`() {
    val container =
        factory.buildPostRunContainer(
            getOrganization(),
            getWorkspace(),
            getSolutionCloudSources(),
            "testruntemplate",
            CSM_SIMULATION_ID)
    envVarsWithSourceValid(
        container,
        "postrun",
        "CSM_POSTRUN_PROVIDER",
        "CSM_POSTRUN_PATH",
        "postrun",
        "testruntemplate")
  }

  private fun envVarsWithSourceValid(
      container: ScenarioRunContainer,
      mode: String,
      providerEnvVar: String,
      resourceEnvVar: String,
      resource: String,
      runTemplate: String,
  ) {
    val expected =
        mapOf(
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to "simulationrunid",
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "Organizationid-Test",
            "CSM_RUN_TEMPLATE_ID" to "testruntemplate",
            "CSM_CONTAINER_MODE" to mode,
            "CSM_PROBES_MEASURES_TOPIC" to
                "amqps://csm-phoenix.servicebus.windows.net/organizationid-test",
            "CSM_SIMULATION" to "TestSimulation",
            providerEnvVar to "azureStorage",
            "AZURE_STORAGE_CONNECTION_STRING" to "csmphoenix_storage_connection_string",
            resourceEnvVar to "organizationid/1/${runTemplate}/${resource}.zip",
        )
    assertEquals(expected, container.envVars)
  }

  private fun envVarsWithSourceLocalValid(
      container: ScenarioRunContainer,
      mode: String,
      providerEnvVar: String
  ) {
    val expected =
        mapOf(
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to "simulationrunid",
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "Organizationid-Test",
            "CSM_RUN_TEMPLATE_ID" to "testruntemplate",
            "CSM_CONTAINER_MODE" to mode,
            "CSM_PROBES_MEASURES_TOPIC" to
                "amqps://csm-phoenix.servicebus.windows.net/organizationid-test",
            "CSM_SIMULATION" to "TestSimulation",
            providerEnvVar to "local",
            "AZURE_STORAGE_CONNECTION_STRING" to "csmphoenix_storage_connection_string",
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
            scenario,
            datasets,
            connectors,
            workspace,
            getOrganization(),
            solution,
            CSM_SIMULATION_ID)
    assertEquals(containers.size, 8)
  }

  @Test
  fun `Build all containers for a Stacked Scenario no DWH containers name list`() {
    val scenario = getScenario()
    val datasets = listOf(getDataset())
    val connectors = listOf(getConnector())
    val workspace = getWorkspace()
    val solution = getSolutionStackNoDWH()
    val containers =
        factory.buildContainersPipeline(
            scenario,
            datasets,
            connectors,
            workspace,
            getOrganization(),
            solution,
            CSM_SIMULATION_ID)
    val expected =
        listOf(
            "fetchDatasetContainer-1",
            "fetchScenarioParametersContainer",
            "multipleStepsContainer-1",
        )
    assertEquals(expected, containers.map { container -> container.name })
  }

  @Test
  fun `Build all containers for a Stacked Scenario no DWH containers env var 1 list`() {
    val scenario = getScenario()
    val datasets = listOf(getDataset())
    val connectors = listOf(getConnector())
    val workspace = getWorkspace()
    val solution = getSolutionStackNoDWH()
    val containers =
        factory.buildContainersPipeline(
            scenario,
            datasets,
            connectors,
            workspace,
            getOrganization(),
            solution,
            CSM_SIMULATION_ID)
    val container = containers.find { container -> container.name == "multipleStepsContainer-1" }
    this.validateEnvVarsSolutionContainer(
        container, "handle-parameters,validate,prerun,engine,postrun")
  }

  @Test
  fun `Build all containers for a Stacked Scenario containers name list`() {
    val scenario = getScenario()
    val datasets = listOf(getDataset())
    val connectors = listOf(getConnector())
    val workspace = getWorkspace()
    val solution = getSolutionStack()
    val containers =
        factory.buildContainersPipeline(
            scenario,
            datasets,
            connectors,
            workspace,
            getOrganization(),
            solution,
            CSM_SIMULATION_ID)
    val expected =
        listOf(
            "fetchDatasetContainer-1",
            "fetchScenarioParametersContainer",
            "multipleStepsContainer-1",
            "sendDataWarehouseContainer",
            "multipleStepsContainer-2",
        )
    assertEquals(expected, containers.map { container -> container.name })
  }

  @Test
  fun `Build all containers for a Stacked Scenario containers env var 1 list`() {
    val scenario = getScenario()
    val datasets = listOf(getDataset())
    val connectors = listOf(getConnector())
    val workspace = getWorkspace()
    val solution = getSolutionStack()
    val containers =
        factory.buildContainersPipeline(
            scenario,
            datasets,
            connectors,
            workspace,
            getOrganization(),
            solution,
            CSM_SIMULATION_ID)
    val container = containers.find { container -> container.name == "multipleStepsContainer-1" }
    this.validateEnvVarsSolutionContainer(container, "handle-parameters,validate")
  }

  @Test
  fun `Build all containers for a Stacked Scenario containers env var 2 list`() {
    val scenario = getScenario()
    val datasets = listOf(getDataset())
    val connectors = listOf(getConnector())
    val workspace = getWorkspace()
    val solution = getSolutionStack()
    val containers =
        factory.buildContainersPipeline(
            scenario,
            datasets,
            connectors,
            workspace,
            getOrganization(),
            solution,
            CSM_SIMULATION_ID)
    val container = containers.find { container -> container.name == "multipleStepsContainer-2" }
    this.validateEnvVarsSolutionContainer(container, "prerun,engine,postrun")
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
            scenario,
            datasets,
            connectors,
            workspace,
            getOrganization(),
            solution,
            CSM_SIMULATION_ID)
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
            scenario,
            datasets,
            connectors,
            workspace,
            getOrganization(),
            solution,
            CSM_SIMULATION_ID)
    assertEquals(1, containers.size)
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
            scenario,
            datasets,
            connectors,
            workspace,
            getOrganization(),
            solution,
            CSM_SIMULATION_ID)
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
            scenario,
            datasets,
            connectors,
            workspace,
            getOrganization(),
            solution,
            CSM_SIMULATION_ID)
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
  fun `Build all containers for a Scenario 3 Datasets containers dag dependency list`() {
    val scenario = getScenarioThreeDatasets()
    val datasets = listOf(getDataset(), getDataset2(), getDataset3())
    val connectors = listOf(getConnector(), getConnector2(), getConnector3())
    val workspace = getWorkspace()
    val solution = getSolution()
    val containers =
        factory.buildContainersPipeline(
            scenario,
            datasets,
            connectors,
            workspace,
            getOrganization(),
            solution,
            CSM_SIMULATION_ID)
    val expected =
        listOf(
            listOf("DAG_ROOT"),
            listOf("DAG_ROOT"),
            listOf("DAG_ROOT"),
            listOf("DAG_ROOT"),
            listOf(
                "fetchDatasetContainer-1",
                "fetchDatasetContainer-2",
                "fetchDatasetContainer-3",
                "fetchScenarioParametersContainer"),
            listOf("applyParametersContainer"),
            listOf("validateDataContainer"),
            listOf("validateDataContainer"),
            listOf("preRunContainer"),
            listOf("runContainer"),
        )
    assertEquals(expected, containers.map { container -> container.dependencies })
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
            scenario,
            datasets,
            connectors,
            workspace,
            getOrganization(),
            solution,
            CSM_SIMULATION_ID)
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
            scenario,
            datasets,
            connectors,
            workspace,
            getOrganization(),
            solution,
            CSM_SIMULATION_ID)
    val container = containers.find { container -> container.name == "fetchDatasetContainer-2" }
    val expected =
        mapOf(
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to "simulationrunid",
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "Organizationid-Test",
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
            scenario,
            datasets,
            connectors,
            workspace,
            getOrganization(),
            solution,
            CSM_SIMULATION_ID)
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
            scenario,
            datasets,
            connectors,
            workspace,
            getOrganization(),
            solution,
            CSM_SIMULATION_ID)
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
            scenario,
            datasets,
            connectors,
            workspace,
            getOrganization(),
            solution,
            CSM_SIMULATION_ID)
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
            scenario,
            datasets,
            connectors,
            workspace,
            getOrganization(),
            solution,
            CSM_SIMULATION_ID)
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
            scenario,
            datasets,
            connectors,
            workspace,
            getOrganization(),
            solution,
            CSM_SIMULATION_ID)
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
            scenario,
            datasets,
            connectors,
            workspace,
            getOrganization(),
            solution,
            CSM_SIMULATION_ID)
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
            scenario,
            datasets,
            connectors,
            workspace,
            getOrganization(),
            solution,
            CSM_SIMULATION_ID)
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
    parametersDatasetEnvTest(getScenarioDatasetIds(), "1", "param2")
  }

  @Test
  fun `Build all containers for a Scenario DATASETID containers env vars 2`() {
    parametersDatasetEnvTest(getScenarioDatasetIds(), "2", "param3")
  }

  @Test
  fun `Build all containers for a Scenario Two DATASETID containers name list`() {
    val scenario = getScenarioTwoDatasetIds()
    val datasets = listOf(getDataset(), getDataset2(), getDataset3())
    val connectors = listOf(getConnector(), getConnector2(), getConnector3())
    val workspace = getWorkspace()
    val solution = getSolutionDatasetIds()
    val containers =
        factory.buildContainersPipeline(
            scenario,
            datasets,
            connectors,
            workspace,
            getOrganization(),
            solution,
            CSM_SIMULATION_ID)
    val expected =
        listOf(
            "fetchDatasetContainer-1",
            "fetchScenarioParametersContainer",
            "fetchScenarioDatasetParametersContainer-1",
            "fetchScenarioDatasetParametersContainer-2",
            "fetchScenarioDatasetParametersContainer-3",
            "fetchScenarioDatasetParametersContainer-4",
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
  fun `Build all containers for a Scenario Two DATASETID containers image list`() {
    val scenario = getScenarioTwoDatasetIds()
    val datasets = listOf(getDataset(), getDataset2(), getDataset3())
    val connectors = listOf(getConnector(), getConnector2(), getConnector3())
    val workspace = getWorkspace()
    val solution = getSolutionDatasetIds()
    val containers =
        factory.buildContainersPipeline(
            scenario,
            datasets,
            connectors,
            workspace,
            getOrganization(),
            solution,
            CSM_SIMULATION_ID)
    val expected =
        listOf(
            "ghcr.io/cosmotech/test_connector:1.0.0",
            "ghcr.io/cosmotech/scenariofetchparameters:1.0.0",
            "ghcr.io/cosmotech/test_connector:1.0.0",
            "ghcr.io/cosmotech/test_connector2:1.0.0",
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
  fun `Build all containers for a Scenario Two DATASETID containers env vars 2-1`() {
    parametersDatasetEnvTest(getScenarioTwoDatasetIds(), "1", "param2-0")
  }

  @Test
  fun `Build all containers for a Scenario Two DATASETID containers env vars 2-2`() {
    parametersDatasetEnvTest(getScenarioTwoDatasetIds(), "2", "param2-1")
  }

  @Test
  fun `Build all containers for a Scenario Two DATASETID containers env vars 3`() {
    parametersDatasetEnvTest(getScenarioTwoDatasetIds(), "3", "param3")
  }

  @Test
  fun `Build all containers for a Scenario DATASETID malformed list`() {
    val scenario = getScenarioMalformedDatasetIds()
    val datasets = listOf(getDataset(), getDataset2(), getDataset3())
    val connectors = listOf(getConnector(), getConnector2(), getConnector3())
    val workspace = getWorkspace()
    val solution = getSolutionDatasetIds()
    assertThrows(CsmClientException::class.java) {
      factory.buildContainersPipeline(
          scenario, datasets, connectors, workspace, getOrganization(), solution, CSM_SIMULATION_ID)
    }
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
  fun `buildFromDataset - error if activating both managed identity and customer credentials`() {
    val connector = mockk<Connector>(relaxed = true)
    every { connector.azureManagedIdentity } returns true
    every { connector.azureAuthenticationWithCustomerAppRegistration } returns true
    every { connector.id } returns "C-id"
    val datasetConnector = mockk<DatasetConnector>()
    every { datasetConnector.id } returns "C-id"
    val dataset = mockk<Dataset>()
    every { dataset.connector } returns datasetConnector

    assertThrows(IllegalArgumentException::class.java) {
      factory.buildFromDataset(
          dataset, connector, 1, true, "fetchId", "O-id", "W-id", "W-key", "csmSimulationId")
    }
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

  @Test
  fun `buildFromDataset - no labels and core credentials if azureManagedIdentity is null`() {
    val connector = mockk<Connector>(relaxed = true)
    every { connector.azureManagedIdentity } returns null
    every { connector.id } returns "C-id"
    val datasetConnector = mockk<DatasetConnector>()
    every { datasetConnector.id } returns "C-id"
    val dataset = mockk<Dataset>()
    every { dataset.connector } returns datasetConnector

    val scenarioRunContainer =
        factory.buildFromDataset(
            dataset, connector, 1, true, "fetchId", "O-id", "W-id", "W-key", "csmSimulationId")

    assertEquals("${CONTAINER_FETCH_DATASET_PARAMETERS}-1", scenarioRunContainer.name)
    assertNull(scenarioRunContainer.labels)
    assertEquals(
        mapOf(
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to "csmSimulationId",
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "O-id-W-key",
            "CSM_FETCH_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters/fetchId"),
        scenarioRunContainer.envVars)
  }

  @Test
  fun `buildFromDataset - no labels and core credentials if azureManagedIdentity is false`() {
    val connector = mockk<Connector>(relaxed = true)
    every { connector.azureManagedIdentity } returns false
    every { connector.id } returns "C-id"
    val datasetConnector = mockk<DatasetConnector>()
    every { datasetConnector.id } returns "C-id"
    val dataset = mockk<Dataset>()
    every { dataset.connector } returns datasetConnector

    val scenarioRunContainer =
        factory.buildFromDataset(
            dataset, connector, 1, true, "fetchId", "O-id", "W-id", "W-key", "csmSimulationId")

    assertEquals("${CONTAINER_FETCH_DATASET_PARAMETERS}-1", scenarioRunContainer.name)
    assertNull(scenarioRunContainer.labels)
    assertEquals(
        mapOf(
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to "csmSimulationId",
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "O-id-W-key",
            "CSM_FETCH_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters/fetchId"),
        scenarioRunContainer.envVars)
  }

  @Test
  fun `buildFromDataset - labels and managed identity env var if azureManagedIdentity is true`() {
    val connector = mockk<Connector>(relaxed = true)
    every { connector.azureManagedIdentity } returns true
    every { connector.id } returns "C-id"
    val datasetConnector = mockk<DatasetConnector>()
    every { datasetConnector.id } returns "C-id"
    val dataset = mockk<Dataset>()
    every { dataset.connector } returns datasetConnector

    val scenarioRunContainer =
        factory.buildFromDataset(
            dataset, connector, 1, true, "fetchId", "O-id", "W-id", "W-key", "csmSimulationId")

    assertEquals("${CONTAINER_FETCH_DATASET_PARAMETERS}-1", scenarioRunContainer.name)
    assertEquals(
        mapOf(AZURE_AAD_POD_ID_BINDING_LABEL to "phoenixdev-pod-identity"),
        scenarioRunContainer.labels)
    assertEquals(
        mapOf(
            "CSM_AZURE_MANAGED_IDENTITY" to "true",
            "CSM_SIMULATION_ID" to "csmSimulationId",
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "O-id-W-key",
            "CSM_FETCH_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters/fetchId"),
        scenarioRunContainer.envVars)
  }

  @Test
  fun `buildFromDataset - core creds if azureAuthenticationWithCustomerAppRegistration is null`() {
    val connector = mockk<Connector>(relaxed = true)
    every { connector.azureAuthenticationWithCustomerAppRegistration } returns false
    every { connector.id } returns "C-id"
    val datasetConnector = mockk<DatasetConnector>()
    every { datasetConnector.id } returns "C-id"
    val dataset = mockk<Dataset>()
    every { dataset.connector } returns datasetConnector

    val scenarioRunContainer =
        factory.buildFromDataset(
            dataset, connector, 1, true, "fetchId", "O-id", "W-id", "W-key", "csmSimulationId")

    assertEquals("${CONTAINER_FETCH_DATASET_PARAMETERS}-1", scenarioRunContainer.name)
    assertNull(scenarioRunContainer.labels)
    assertEquals(
        mapOf(
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to "csmSimulationId",
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "O-id-W-key",
            "CSM_FETCH_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters/fetchId"),
        scenarioRunContainer.envVars)
  }

  @Test
  fun `buildFromDataset - core creds if azureAuthenticationWithCustomerAppRegistration is false`() {
    val connector = mockk<Connector>(relaxed = true)
    every { connector.azureAuthenticationWithCustomerAppRegistration } returns false
    every { connector.id } returns "C-id"
    val datasetConnector = mockk<DatasetConnector>()
    every { datasetConnector.id } returns "C-id"
    val dataset = mockk<Dataset>()
    every { dataset.connector } returns datasetConnector

    val scenarioRunContainer =
        factory.buildFromDataset(
            dataset, connector, 1, true, "fetchId", "O-id", "W-id", "W-key", "csmSimulationId")

    assertEquals("${CONTAINER_FETCH_DATASET_PARAMETERS}-1", scenarioRunContainer.name)
    assertNull(scenarioRunContainer.labels)
    assertEquals(
        mapOf(
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to "csmSimulationId",
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "O-id-W-key",
            "CSM_FETCH_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters/fetchId"),
        scenarioRunContainer.envVars)
  }

  @Test
  fun `buildFromDataset - customer creds as envvar if azureAuthenticationWithCustomerAppRegistration is true`() {
    val connector = mockk<Connector>(relaxed = true)
    every { connector.azureAuthenticationWithCustomerAppRegistration } returns true
    every { connector.id } returns "C-id"
    val datasetConnector = mockk<DatasetConnector>()
    every { datasetConnector.id } returns "C-id"
    val dataset = mockk<Dataset>()
    every { dataset.connector } returns datasetConnector

    val scenarioRunContainer =
        factory.buildFromDataset(
            dataset, connector, 1, true, "fetchId", "O-id", "W-id", "W-key", "csmSimulationId")

    assertEquals("${CONTAINER_FETCH_DATASET_PARAMETERS}-1", scenarioRunContainer.name)
    assertNull(scenarioRunContainer.labels)
    assertEquals(
        mapOf(
            "AZURE_TENANT_ID" to "customer-app-registration-tenantId",
            "AZURE_CLIENT_ID" to "customer-app-registration-clientId",
            "AZURE_CLIENT_SECRET" to "customer-app-registration-clientSecret",
            "CSM_SIMULATION_ID" to "csmSimulationId",
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "O-id-W-key",
            "CSM_FETCH_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters/fetchId"),
        scenarioRunContainer.envVars)
  }

  @Test
  fun `PROD-8072- Tenant credentials are set as env vars to solution container by default`() {
    every { azure.eventBus } returns
        CsmPlatformAzureEventBus(
            baseUri = "amqps://csm-phoenix.servicebus.windows.net",
        )

    val container = buildApplyParametersContainer()

    assertNotNull(container.envVars)
    assertFalse { container.envVars!!.containsKey(AZURE_EVENT_HUB_SHARED_ACCESS_POLICY_ENV_VAR) }
    assertFalse { container.envVars!!.containsKey(AZURE_EVENT_HUB_SHARED_ACCESS_KEY_ENV_VAR) }
    assertEquals(
        mapOf(
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to "simulationrunid",
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "Organizationid-Test",
            "CSM_RUN_TEMPLATE_ID" to "testruntemplate",
            "CSM_CONTAINER_MODE" to "handle-parameters",
            "CSM_PROBES_MEASURES_TOPIC" to
                "amqps://csm-phoenix.servicebus.windows.net/organizationid-test",
            "CSM_SIMULATION" to "TestSimulation"),
        container.envVars)
  }

  @Test
  fun `PROD-8072- Tenant credentials are set as env vars to solution container if told so`() {
    every { azure.eventBus } returns
        CsmPlatformAzureEventBus(
            baseUri = "amqps://csm-phoenix.servicebus.windows.net",
            authentication = Authentication(strategy = TENANT_CLIENT_CREDENTIALS))

    val container = buildPreRunContainer()

    assertNotNull(container.envVars)
    assertFalse { container.envVars!!.containsKey(AZURE_EVENT_HUB_SHARED_ACCESS_POLICY_ENV_VAR) }
    assertFalse { container.envVars!!.containsKey(AZURE_EVENT_HUB_SHARED_ACCESS_KEY_ENV_VAR) }

    assertEquals(
        mapOf(
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to "simulationrunid",
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "Organizationid-Test",
            "CSM_RUN_TEMPLATE_ID" to "testruntemplate",
            "CSM_CONTAINER_MODE" to "prerun",
            "CSM_PROBES_MEASURES_TOPIC" to
                "amqps://csm-phoenix.servicebus.windows.net/organizationid-test",
            "CSM_SIMULATION" to "TestSimulation"),
        container.envVars)
  }

  @Test
  fun `PROD-8072- Shared Access key set as env vars to solution container if told so`() {
    every { azure.eventBus } returns
        CsmPlatformAzureEventBus(
            baseUri = "amqps://csm-phoenix.servicebus.windows.net",
            authentication =
                Authentication(
                    strategy = SHARED_ACCESS_POLICY,
                    sharedAccessPolicy =
                        SharedAccessPolicyDetails(
                            namespace =
                                SharedAccessPolicyCredentials(
                                    name = "my-eventhub-access-policy", key = "a1b2c3d4e5=="))))

    val container = buildRunContainer()

    assertNotNull(container.envVars)

    assertEquals(
        mapOf(
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to "simulationrunid",
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "Organizationid-Test",
            "CSM_RUN_TEMPLATE_ID" to "testruntemplate",
            "CSM_CONTAINER_MODE" to "engine",
            "CSM_PROBES_MEASURES_TOPIC" to
                "amqps://csm-phoenix.servicebus.windows.net/organizationid-test",
            "AZURE_EVENT_HUB_SHARED_ACCESS_POLICY" to "my-eventhub-access-policy",
            "AZURE_EVENT_HUB_SHARED_ACCESS_KEY" to "a1b2c3d4e5==",
            "CSM_AMQPCONSUMER_USER" to "my-eventhub-access-policy",
            "CSM_AMQPCONSUMER_PASSWORD" to "a1b2c3d4e5==",
            "CSM_CONTROL_PLANE_USER" to "my-eventhub-access-policy",
            "CSM_CONTROL_PLANE_PASSWORD" to "a1b2c3d4e5==",
            "CSM_SIMULATION" to "TestSimulation"),
        container.envVars)
  }

  @Test
  fun `PROD-8072- Exception if Shared Access Policy strategy but no credentials configured`() {
    every { azure.eventBus } returns
        CsmPlatformAzureEventBus(
            baseUri = "amqps://csm-phoenix.servicebus.windows.net",
            authentication = Authentication(strategy = SHARED_ACCESS_POLICY))

    assertThrows(IllegalStateException::class.java) { buildRunContainer() }
  }

  private fun getStartInfoFromIds(): StartInfo {
    val organizationId = "Organizationid"
    val workspaceId = "workspaceid"
    val scenarioId = "AQWXSZ"

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
    )
  }

  private fun parametersDatasetEnvTest(scenario: Scenario, nameId: String, param: String) {
    val datasets = listOf(getDataset(), getDataset2(), getDataset3())
    val connectors = listOf(getConnector(), getConnector2(), getConnector3())
    val workspace = getWorkspace()
    val solution = getSolutionDatasetIds()
    val containers =
        factory.buildContainersPipeline(
            scenario,
            datasets,
            connectors,
            workspace,
            getOrganization(),
            solution,
            CSM_SIMULATION_ID)
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
            "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "Organizationid-Test",
            "CSM_FETCH_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters/${param}",
            "ENV_PARAM_1" to "env_param1_value",
            "ENV_PARAM_2" to "env_param2_value",
            "ENV_PARAM_3" to "env_param3_value")
    assertEquals(expected, container?.envVars)
  }

  @Test
  fun `PROD-7623- Dedicated EventHub by namespace set to true`() {
    val container = buildRunContainer(true)

    assertNotNull(container.envVars)
    assertEquals(
        mapOf(
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to "simulationrunid",
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "Organizationid-Test",
            "CSM_RUN_TEMPLATE_ID" to "testruntemplate",
            "CSM_CONTAINER_MODE" to "engine",
            "CSM_PROBES_MEASURES_TOPIC" to
                "amqps://organizationid-test.servicebus.windows.net/probesmeasures",
            "CSM_SIMULATION" to "TestSimulation"),
        container.envVars)
  }

  @Test
  fun `PROD-7623- Dedicated EventHub by namespace set to false`() {
    every { azure.eventBus } returns
        CsmPlatformAzureEventBus(
            baseUri = "amqps://csm-phoenix.servicebus.windows.net",
        )

    val container = buildRunContainer(false)

    assertNotNull(container.envVars)
    assertEquals(
        mapOf(
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to "simulationrunid",
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "Organizationid-Test",
            "CSM_RUN_TEMPLATE_ID" to "testruntemplate",
            "CSM_CONTAINER_MODE" to "engine",
            "CSM_PROBES_MEASURES_TOPIC" to
                "amqps://csm-phoenix.servicebus.windows.net/organizationid-test",
            "CSM_SIMULATION" to "TestSimulation"),
        container.envVars)
  }

  private fun buildApplyParametersContainer(): ScenarioRunContainer {
    return factory.buildApplyParametersContainer(
        getOrganization(), getWorkspace(), getSolution(), "testruntemplate", CSM_SIMULATION_ID)
  }

  private fun buildValidateDataContainer(): ScenarioRunContainer {
    return factory.buildValidateDataContainer(
        getOrganization(), getWorkspace(), getSolution(), "testruntemplate", CSM_SIMULATION_ID)
  }

  private fun buildPreRunContainer(): ScenarioRunContainer {
    return factory.buildPreRunContainer(
        getOrganization(), getWorkspace(), getSolution(), "testruntemplate", CSM_SIMULATION_ID)
  }

  private fun buildRunContainer(dedicatedEventHubNamespace: Boolean? = null): ScenarioRunContainer {
    return factory.buildRunContainer(
        getOrganization(),
        getWorkspace(dedicatedEventHubNamespace),
        getSolution(),
        "testruntemplate",
        CSM_SIMULATION_ID)
  }

  private fun buildPostRunContainer(): ScenarioRunContainer {
    return factory.buildPostRunContainer(
        getOrganization(), getWorkspace(), getSolution(), "testruntemplate", CSM_SIMULATION_ID)
  }

  private fun validateEnvVarsSolutionContainer(container: ScenarioRunContainer?, mode: String) {
    val expected =
        mapOf(
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to "simulationrunid",
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "Organizationid-Test",
            "CSM_RUN_TEMPLATE_ID" to "testruntemplate",
            "CSM_CONTAINER_MODE" to mode,
            "CSM_PROBES_MEASURES_TOPIC" to
                "amqps://csm-phoenix.servicebus.windows.net/organizationid-test",
            "CSM_SIMULATION" to "TestSimulation")
    assertEquals(expected, container?.envVars)
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
        key = key,
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
        key = key,
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
        key = key,
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

  private fun getConnectorNoVarsManagedIdentity(): Connector {
    return Connector(
        id = "QsDfGhJk",
        key = "TestConnector",
        name = "Test Connector",
        repository = "cosmotech/test_connector",
        version = "1.0.0",
        ioTypes = listOf(IoTypes.read),
        azureManagedIdentity = true)
  }

  private fun getDatasetConnectorNoVars(): DatasetConnector {
    return DatasetConnector(id = "QsDfGhJk")
  }

  private fun getDatasetNoVars(): Dataset {
    val connector = getDatasetConnectorNoVars()
    return Dataset(id = "1", name = "Test Dataset No Vars", connector = connector)
  }

  private fun getWorkspace(dedicatedEventHubNamespace: Boolean? = null): Workspace {
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
        useDedicatedEventHubNamespace = dedicatedEventHubNamespace,
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

  private fun getSolutionStack(): Solution {
    return Solution(
        id = "1",
        key = "TestSolution",
        name = "Test Solution",
        repository = "cosmotech/testsolution_simulator",
        version = "1.0.0",
        runTemplates = listOf(getRunTemplateStack()),
    )
  }

  private fun getSolutionStackNoDWH(): Solution {
    return Solution(
        id = "1",
        key = "TestSolution",
        name = "Test Solution",
        repository = "cosmotech/testsolution_simulator",
        version = "1.0.0",
        runTemplates = listOf(getRunTemplateStackNoDWH()),
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
                    labels = mapOf("en" to "Parameter Dataset 2"),
                    varType = "%DATASETID%"),
                RunTemplateParameter(
                    id = "param3",
                    labels = mapOf("en" to "Parameter Dataset 3"),
                    varType = "%DATASETID%"),
                RunTemplateParameter(
                    id = "param4",
                    labels = mapOf("en" to "Parameter Dataset 4"),
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
        computeSize = "highcpupool",
    )
  }

  private fun getRunTemplateStack(): RunTemplate {
    return RunTemplate(
        id = "testruntemplate",
        name = "Test Run",
        csmSimulation = "TestSimulation",
        computeSize = "highcpu",
        stackSteps = true,
    )
  }

  private fun getRunTemplateStackNoDWH(): RunTemplate {
    return RunTemplate(
        id = "testruntemplate",
        name = "Test Run",
        csmSimulation = "TestSimulation",
        computeSize = "highcpu",
        stackSteps = true,
        sendDatasetsToDataWarehouse = false,
        sendInputParametersToDataWarehouse = false,
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

  private fun getScenarioTwoDatasetIds(): Scenario {
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
                    value = "[1,2]",
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

  private fun getScenarioMalformedDatasetIds(): Scenario {
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
                    value = "[",
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
