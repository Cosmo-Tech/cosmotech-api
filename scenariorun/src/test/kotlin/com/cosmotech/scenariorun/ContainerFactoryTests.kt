// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.config.CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureCredentials
import com.cosmotech.api.config.CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureCredentials.CsmPlatformAzureCredentialsCore
import com.cosmotech.api.config.CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus
import com.cosmotech.api.config.CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication
import com.cosmotech.api.config.CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication.SharedAccessPolicyCredentials
import com.cosmotech.api.config.CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication.SharedAccessPolicyDetails
import com.cosmotech.api.config.CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication.Strategy.SHARED_ACCESS_POLICY
import com.cosmotech.api.config.CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication.Strategy.TENANT_CLIENT_CREDENTIALS
import com.cosmotech.api.containerregistry.ContainerRegistryService
import com.cosmotech.api.exceptions.CsmClientException
import com.cosmotech.api.utils.SecretManager
import com.cosmotech.connector.api.ConnectorApiService
import com.cosmotech.connector.domain.Connector
import com.cosmotech.connector.domain.ConnectorParameter
import com.cosmotech.connector.domain.ConnectorParameterGroup
import com.cosmotech.connector.domain.IoTypesEnum
import com.cosmotech.dataset.api.DatasetApiService
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.dataset.domain.DatasetConnector
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.domain.Organization
import com.cosmotech.scenario.api.ScenarioApiService
import com.cosmotech.scenario.domain.Scenario
import com.cosmotech.scenario.domain.ScenarioRunTemplateParameterValue
import com.cosmotech.scenariorun.container.BASIC_SIZING
import com.cosmotech.scenariorun.container.StartInfo
import com.cosmotech.scenariorun.domain.ScenarioRunContainer
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.solution.domain.RunTemplate
import com.cosmotech.solution.domain.RunTemplateOrchestrator
import com.cosmotech.solution.domain.RunTemplateParameter
import com.cosmotech.solution.domain.RunTemplateParameterGroup
import com.cosmotech.solution.domain.RunTemplateStepSource
import com.cosmotech.solution.domain.Solution
import com.cosmotech.workspace.api.WorkspaceApiService
import com.cosmotech.workspace.azure.IWorkspaceEventHubService
import com.cosmotech.workspace.azure.WORKSPACE_EVENTHUB_ACCESSKEY_SECRET
import com.cosmotech.workspace.azure.WorkspaceEventHubService
import com.cosmotech.workspace.azure.strategy.IWorkspaceEventHubStrategy
import com.cosmotech.workspace.azure.strategy.WorkspaceEventHubStrategyDedicated
import com.cosmotech.workspace.azure.strategy.WorkspaceEventHubStrategyShared
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceSolution
import io.mockk.MockKAnnotations
import io.mockk.every
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

private const val CSM_SIMULATION_ID = "ScenarioRunId"

private const val DEFAULT_WORKFLOW_TYPE = "testruntype"

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
  @MockK private lateinit var secretManager: SecretManager
  @MockK private lateinit var containerRegistryService: ContainerRegistryService
  private lateinit var workspaceEventHubService: IWorkspaceEventHubService

  private lateinit var factory: ContainerFactory

  @Suppress("LongMethod")
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
    every { csmPlatformProperties.namespace } returns "csm-phoenix"
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
    every { csmPlatformProperties.identityProvider } returns
        CsmPlatformProperties.CsmIdentityProvider(
            code = "azure",
            defaultScopes = mapOf("This is a fake scope id" to "This is a fake scope name"),
            authorizationUrl = "http://this_is_a_fake_url.com",
            tokenUrl = "http://this_is_a_fake_token_url.com",
        )
    every { csmPlatformProperties.twincache } returns
        CsmPlatformProperties.CsmTwinCacheProperties(
            host = "this_is_a_host",
            port = "6973",
            password = "this_is_a_password",
        )

    val dedicatedStrategy: IWorkspaceEventHubStrategy =
        WorkspaceEventHubStrategyDedicated(csmPlatformProperties, secretManager)
    val sharedStrategy: IWorkspaceEventHubStrategy =
        WorkspaceEventHubStrategyShared(csmPlatformProperties)
    workspaceEventHubService =
        WorkspaceEventHubService(
            mapOf("Dedicated" to dedicatedStrategy, "Shared" to sharedStrategy))

    factory =
        ContainerFactory(
            csmPlatformProperties,
            scenarioService,
            workspaceService,
            solutionService,
            organizationService,
            connectorService,
            datasetService,
            workspaceEventHubService,
            containerRegistryService)
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
            "Workspaceid",
            "Scenarioid",
            CSM_SIMULATION_ID,
            "Test",
            CSM_SIMULATION_ID,
            nodeSizingLabel = NODE_LABEL_DEFAULT,
            customSizing = BASIC_SIZING)
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
            "Workspaceid",
            "Scenarioid",
            CSM_SIMULATION_ID,
            "Test",
            CSM_SIMULATION_ID,
            nodeSizingLabel = NODE_LABEL_DEFAULT,
            customSizing = BASIC_SIZING)
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
            "Workspaceid",
            "Scenarioid",
            CSM_SIMULATION_ID,
            "Test",
            CSM_SIMULATION_ID,
            nodeSizingLabel = NODE_LABEL_DEFAULT,
            customSizing = BASIC_SIZING)
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
          "Workspaceid",
          "Scenarioid",
          CSM_SIMULATION_ID,
          "Test",
          CSM_SIMULATION_ID,
          nodeSizingLabel = NODE_LABEL_DEFAULT,
          customSizing = BASIC_SIZING)
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
            "Workspaceid",
            "Scenarioid",
            CSM_SIMULATION_ID,
            "Test",
            CSM_SIMULATION_ID,
            nodeSizingLabel = NODE_LABEL_DEFAULT,
            customSizing = BASIC_SIZING)
    val expected =
        mapOf(
            "IDENTITY_PROVIDER" to "azure",
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to CSM_SIMULATION_ID,
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "organizationid-test",
            "CSM_ORGANIZATION_ID" to "Organizationid",
            "CSM_WORKSPACE_ID" to "Workspaceid",
            "CSM_SCENARIO_ID" to "Scenarioid",
            "CSM_SCENARIO_RUN_ID" to CSM_SIMULATION_ID,
            "CSM_FETCH_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "ENV_PARAM_1" to "env_param1_value",
            "ENV_PARAM_2" to "env_param2_value",
            "ENV_PARAM_3" to "env_param3_value",
            "TWIN_CACHE_HOST" to "this_is_a_host",
            "TWIN_CACHE_PORT" to "6973",
            "TWIN_CACHE_PASSWORD" to "this_is_a_password",
            "TWIN_CACHE_USERNAME" to "default")
    assertEquals(expected.toSortedMap(), container.envVars?.toSortedMap())
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
            "Workspaceid",
            "Scenarioid",
            CSM_SIMULATION_ID,
            "Test",
            CSM_SIMULATION_ID,
            nodeSizingLabel = NODE_LABEL_DEFAULT,
            customSizing = BASIC_SIZING)
    val expected =
        mapOf(
            "IDENTITY_PROVIDER" to "azure",
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to CSM_SIMULATION_ID,
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "organizationid-test",
            "CSM_ORGANIZATION_ID" to "Organizationid",
            "CSM_WORKSPACE_ID" to "Workspaceid",
            "CSM_SCENARIO_ID" to "Scenarioid",
            "CSM_SCENARIO_RUN_ID" to CSM_SIMULATION_ID,
            "CSM_FETCH_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "ENV_PARAM_1" to "organizationid/workspaceid/workspace.env",
            "TWIN_CACHE_HOST" to "this_is_a_host",
            "TWIN_CACHE_PORT" to "6973",
            "TWIN_CACHE_PASSWORD" to "this_is_a_password",
            "TWIN_CACHE_USERNAME" to "default")
    assertEquals(expected.toSortedMap(), container.envVars?.toSortedMap())
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
            "Workspaceid",
            "Scenarioid",
            CSM_SIMULATION_ID,
            "Test",
            CSM_SIMULATION_ID,
            nodeSizingLabel = NODE_LABEL_DEFAULT,
            customSizing = BASIC_SIZING)
    val expected =
        mapOf(
            "IDENTITY_PROVIDER" to "azure",
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to CSM_SIMULATION_ID,
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "organizationid-test",
            "CSM_ORGANIZATION_ID" to "Organizationid",
            "CSM_WORKSPACE_ID" to "Workspaceid",
            "CSM_SCENARIO_ID" to "Scenarioid",
            "CSM_SCENARIO_RUN_ID" to CSM_SIMULATION_ID,
            "CSM_FETCH_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "AZURE_STORAGE_CONNECTION_STRING" to "csmphoenix_storage_connection_string",
            "TWIN_CACHE_HOST" to "this_is_a_host",
            "TWIN_CACHE_PORT" to "6973",
            "TWIN_CACHE_PASSWORD" to "this_is_a_password",
            "TWIN_CACHE_USERNAME" to "default")
    assertEquals(expected.toSortedMap(), container.envVars?.toSortedMap())
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
            "Workspaceid",
            "Scenarioid",
            CSM_SIMULATION_ID,
            "Test",
            CSM_SIMULATION_ID,
            nodeSizingLabel = NODE_LABEL_DEFAULT,
            customSizing = BASIC_SIZING)
    val expected =
        mapOf(
            "IDENTITY_PROVIDER" to "azure",
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to CSM_SIMULATION_ID,
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "organizationid-test",
            "CSM_ORGANIZATION_ID" to "Organizationid",
            "CSM_WORKSPACE_ID" to "Workspaceid",
            "CSM_SCENARIO_ID" to "Scenarioid",
            "CSM_SCENARIO_RUN_ID" to CSM_SIMULATION_ID,
            "CSM_FETCH_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "TWIN_CACHE_HOST" to "this_is_a_host",
            "TWIN_CACHE_PORT" to "6973",
            "TWIN_CACHE_PASSWORD" to "this_is_a_password",
            "TWIN_CACHE_USERNAME" to "default")
    assertEquals(expected.toSortedMap(), container.envVars?.toSortedMap())
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
            "Workspaceid",
            "Scenarioid",
            CSM_SIMULATION_ID,
            "Test",
            CSM_SIMULATION_ID,
            nodeSizingLabel = NODE_LABEL_DEFAULT,
            customSizing = BASIC_SIZING)
    val expected =
        mapOf(
            "IDENTITY_PROVIDER" to "azure",
            "CSM_AZURE_MANAGED_IDENTITY" to "true",
            "CSM_SIMULATION_ID" to CSM_SIMULATION_ID,
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "organizationid-test",
            "CSM_ORGANIZATION_ID" to "Organizationid",
            "CSM_WORKSPACE_ID" to "Workspaceid",
            "CSM_SCENARIO_ID" to "Scenarioid",
            "CSM_SCENARIO_RUN_ID" to CSM_SIMULATION_ID,
            "CSM_FETCH_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "TWIN_CACHE_HOST" to "this_is_a_host",
            "TWIN_CACHE_PORT" to "6973",
            "TWIN_CACHE_PASSWORD" to "this_is_a_password",
            "TWIN_CACHE_USERNAME" to "default")
    assertEquals(expected.toSortedMap(), container.envVars?.toSortedMap())
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
            "Workspaceid",
            "Scenarioid",
            CSM_SIMULATION_ID,
            "Test",
            CSM_SIMULATION_ID,
            nodeSizingLabel = NODE_LABEL_DEFAULT,
            customSizing = BASIC_SIZING)
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
            "Workspaceid",
            "Scenarioid",
            CSM_SIMULATION_ID,
            "Test",
            CSM_SIMULATION_ID,
            nodeSizingLabel = NODE_LABEL_DEFAULT,
            customSizing = BASIC_SIZING)
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
            "Workspaceid",
            "Scenarioid",
            CSM_SIMULATION_ID,
            "Test",
            CSM_SIMULATION_ID,
            nodeSizingLabel = NODE_LABEL_DEFAULT,
            customSizing = BASIC_SIZING)
    val expected = listOf("param1_value", "param2_value", "param3_value")
    assertEquals(expected, container.runArgs)
  }

  @Test
  fun `Fetch Scenario Parameters Container is not null`() {
    val container =
        factory.buildScenarioParametersFetchContainer(
            "1",
            "2",
            "3",
            "4",
            "Test",
            CSM_SIMULATION_ID,
            nodeSizingLabel = NODE_LABEL_DEFAULT,
            customSizing = BASIC_SIZING)
    assertNotNull(container)
  }

  @Test
  fun `Fetch Scenario Parameters Container name valid`() {
    val container =
        factory.buildScenarioParametersFetchContainer(
            "1",
            "2",
            "3",
            "4",
            "Test",
            CSM_SIMULATION_ID,
            nodeSizingLabel = NODE_LABEL_DEFAULT,
            customSizing = BASIC_SIZING)
    assertEquals("fetchScenarioParametersContainer", container.name)
  }

  @Test
  fun `Fetch Scenario Parameters Container image valid`() {
    val container =
        factory.buildScenarioParametersFetchContainer(
            "1",
            "2",
            "3",
            "4",
            "Test",
            CSM_SIMULATION_ID,
            nodeSizingLabel = NODE_LABEL_DEFAULT,
            customSizing = BASIC_SIZING)
    assertEquals("ghcr.io/cosmotech/scenariofetchparameters:1.0.0", container.image)
  }

  @Test
  fun `Fetch Scenario Parameters Container env vars valid`() {
    val container =
        factory.buildScenarioParametersFetchContainer(
            "1",
            "2",
            "3",
            "4",
            "Test",
            CSM_SIMULATION_ID,
            nodeSizingLabel = NODE_LABEL_DEFAULT,
            customSizing = BASIC_SIZING)
    val expected =
        mapOf(
            "IDENTITY_PROVIDER" to "azure",
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to CSM_SIMULATION_ID,
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "1-test",
            "CSM_ORGANIZATION_ID" to "1",
            "CSM_WORKSPACE_ID" to "2",
            "CSM_SCENARIO_ID" to "3",
            "CSM_SCENARIO_RUN_ID" to "4",
            "CSM_FETCH_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "TWIN_CACHE_HOST" to "this_is_a_host",
            "TWIN_CACHE_PORT" to "6973",
            "TWIN_CACHE_PASSWORD" to "this_is_a_password",
            "TWIN_CACHE_USERNAME" to "default")
    assertEquals(expected.toSortedMap(), container.envVars?.toSortedMap())
  }

  @Test
  fun `Fetch Scenario Parameters Container env vars valid with containerScopes defined`() {

    every { csmPlatformProperties.identityProvider } returns
        CsmPlatformProperties.CsmIdentityProvider(
            code = "azure",
            defaultScopes = mapOf("This is a fake scope id" to "This is a fake scope name"),
            authorizationUrl = "http://this_is_a_fake_url.com",
            containerScopes =
                mapOf("scope1" to "This is a scope", "scope2" to "This is another scope"),
            tokenUrl = "http://this_is_a_fake_token_url.com",
        )

    val container =
        factory.buildScenarioParametersFetchContainer(
            "1",
            "2",
            "3",
            "4",
            "Test",
            CSM_SIMULATION_ID,
            nodeSizingLabel = NODE_LABEL_DEFAULT,
            customSizing = BASIC_SIZING)
    val expected =
        mapOf(
            "IDENTITY_PROVIDER" to "azure",
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to CSM_SIMULATION_ID,
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_API_SCOPE" to "scope1 scope2",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "1-test",
            "CSM_ORGANIZATION_ID" to "1",
            "CSM_WORKSPACE_ID" to "2",
            "CSM_SCENARIO_ID" to "3",
            "CSM_SCENARIO_RUN_ID" to "4",
            "CSM_FETCH_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "TWIN_CACHE_HOST" to "this_is_a_host",
            "TWIN_CACHE_PORT" to "6973",
            "TWIN_CACHE_PASSWORD" to "this_is_a_password",
            "TWIN_CACHE_USERNAME" to "default")
    assertEquals(expected.toSortedMap(), container.envVars?.toSortedMap())
  }

  @Test
  fun `Fetch Scenario Parameters Container env vars valid json`() {
    val container =
        factory.buildScenarioParametersFetchContainer(
            "1",
            "2",
            "3",
            "4",
            "Test",
            CSM_SIMULATION_ID,
            true,
            nodeSizingLabel = NODE_LABEL_DEFAULT,
            customSizing = BASIC_SIZING)
    val expected =
        mapOf(
            "IDENTITY_PROVIDER" to "azure",
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to CSM_SIMULATION_ID,
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "1-test",
            "CSM_ORGANIZATION_ID" to "1",
            "CSM_WORKSPACE_ID" to "2",
            "CSM_SCENARIO_ID" to "3",
            "CSM_SCENARIO_RUN_ID" to "4",
            "CSM_FETCH_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "WRITE_CSV" to "false",
            "WRITE_JSON" to "true",
            "TWIN_CACHE_HOST" to "this_is_a_host",
            "TWIN_CACHE_PORT" to "6973",
            "TWIN_CACHE_PASSWORD" to "this_is_a_password",
            "TWIN_CACHE_USERNAME" to "default")
    assertEquals(expected.toSortedMap(), container.envVars?.toSortedMap())
  }

  @Test
  fun `Send DataWarehouse Container is not null`() {
    val container =
        factory.buildSendDataWarehouseContainer(
            "Organizationid",
            getWorkspace(),
            "Scenarioid",
            CSM_SIMULATION_ID,
            getRunTemplate(),
            CSM_SIMULATION_ID,
            nodeSizingLabel = NODE_LABEL_DEFAULT,
            customSizing = BASIC_SIZING)
    assertNotNull(container)
  }

  @Test
  fun `Send DataWarehouseContainer name valid`() {
    val container =
        factory.buildSendDataWarehouseContainer(
            "Organizationid",
            getWorkspace(),
            "Scenarioid",
            CSM_SIMULATION_ID,
            getRunTemplate(),
            CSM_SIMULATION_ID,
            nodeSizingLabel = NODE_LABEL_DEFAULT,
            customSizing = BASIC_SIZING)
    assertEquals("sendDataWarehouseContainer", container.name)
  }

  @Test
  fun `Send DataWarehouse Container image valid`() {
    val container =
        factory.buildSendDataWarehouseContainer(
            "Organizationid",
            getWorkspace(),
            "Scenarioid",
            CSM_SIMULATION_ID,
            getRunTemplate(),
            CSM_SIMULATION_ID,
            nodeSizingLabel = NODE_LABEL_DEFAULT,
            customSizing = BASIC_SIZING)
    assertEquals("ghcr.io/cosmotech/senddatawarehouse:1.0.0", container.image)
  }

  @Test
  fun `Send DataWarehouse Container env vars valid`() {
    val container =
        factory.buildSendDataWarehouseContainer(
            "Organizationid",
            getWorkspace(),
            "Scenarioid",
            CSM_SIMULATION_ID,
            getRunTemplate(),
            CSM_SIMULATION_ID,
            nodeSizingLabel = NODE_LABEL_DEFAULT,
            customSizing = BASIC_SIZING)
    val expected =
        mapOf(
            "IDENTITY_PROVIDER" to "azure",
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to CSM_SIMULATION_ID,
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "organizationid-test",
            "CSM_ORGANIZATION_ID" to "Organizationid",
            "CSM_WORKSPACE_ID" to "Workspaceid",
            "CSM_SCENARIO_ID" to "Scenarioid",
            "CSM_SCENARIO_RUN_ID" to CSM_SIMULATION_ID,
            "CSM_SEND_DATAWAREHOUSE_PARAMETERS" to "true",
            "CSM_SEND_DATAWAREHOUSE_DATASETS" to "true",
            "TWIN_CACHE_HOST" to "this_is_a_host",
            "TWIN_CACHE_PORT" to "6973",
            "TWIN_CACHE_PASSWORD" to "this_is_a_password",
            "TWIN_CACHE_USERNAME" to "default")
    assertEquals(expected.toSortedMap(), container.envVars?.toSortedMap())
  }

  @Test
  fun `Send DataWarehouse Container no send env vars`() {
    val container =
        factory.buildSendDataWarehouseContainer(
            "Organizationid",
            getWorkspaceNoSend(),
            "Scenarioid",
            CSM_SIMULATION_ID,
            getRunTemplate(),
            CSM_SIMULATION_ID,
            nodeSizingLabel = NODE_LABEL_DEFAULT,
            customSizing = BASIC_SIZING)
    val expected =
        mapOf(
            "IDENTITY_PROVIDER" to "azure",
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to CSM_SIMULATION_ID,
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "organizationid-test",
            "CSM_ORGANIZATION_ID" to "Organizationid",
            "CSM_WORKSPACE_ID" to "Workspaceid",
            "CSM_SCENARIO_ID" to "Scenarioid",
            "CSM_SCENARIO_RUN_ID" to CSM_SIMULATION_ID,
            "CSM_SEND_DATAWAREHOUSE_PARAMETERS" to "true",
            "CSM_SEND_DATAWAREHOUSE_DATASETS" to "true",
            "TWIN_CACHE_HOST" to "this_is_a_host",
            "TWIN_CACHE_PORT" to "6973",
            "TWIN_CACHE_PASSWORD" to "this_is_a_password",
            "TWIN_CACHE_USERNAME" to "default")
    assertEquals(expected.toSortedMap(), container.envVars?.toSortedMap())
  }

  @Test
  fun `Send DataWarehouse Container env vars send override dataset template`() {
    val container =
        factory.buildSendDataWarehouseContainer(
            "Organizationid",
            getWorkspace(),
            "Scenarioid",
            CSM_SIMULATION_ID,
            getRunTemplateNoDatasetsSend(),
            CSM_SIMULATION_ID,
            nodeSizingLabel = NODE_LABEL_DEFAULT,
            customSizing = BASIC_SIZING)
    val expected =
        mapOf(
            "IDENTITY_PROVIDER" to "azure",
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to CSM_SIMULATION_ID,
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "organizationid-test",
            "CSM_ORGANIZATION_ID" to "Organizationid",
            "CSM_WORKSPACE_ID" to "Workspaceid",
            "CSM_SCENARIO_ID" to "Scenarioid",
            "CSM_SCENARIO_RUN_ID" to CSM_SIMULATION_ID,
            "CSM_SEND_DATAWAREHOUSE_PARAMETERS" to "true",
            "CSM_SEND_DATAWAREHOUSE_DATASETS" to "false",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "organizationid-test",
            "TWIN_CACHE_HOST" to "this_is_a_host",
            "TWIN_CACHE_PORT" to "6973",
            "TWIN_CACHE_PASSWORD" to "this_is_a_password",
            "TWIN_CACHE_USERNAME" to "default")
    assertEquals(expected.toSortedMap(), container.envVars?.toSortedMap())
  }

  @Test
  fun `Send DataWarehouse Container env vars send override parameters template`() {
    val container =
        factory.buildSendDataWarehouseContainer(
            "Organizationid",
            getWorkspace(),
            "Scenarioid",
            CSM_SIMULATION_ID,
            getRunTemplateNoParametersSend(),
            CSM_SIMULATION_ID,
            nodeSizingLabel = NODE_LABEL_DEFAULT,
            customSizing = BASIC_SIZING)
    val expected =
        mapOf(
            "IDENTITY_PROVIDER" to "azure",
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to CSM_SIMULATION_ID,
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "organizationid-test",
            "CSM_ORGANIZATION_ID" to "Organizationid",
            "CSM_WORKSPACE_ID" to "Workspaceid",
            "CSM_SCENARIO_ID" to "Scenarioid",
            "CSM_SCENARIO_RUN_ID" to CSM_SIMULATION_ID,
            "CSM_SEND_DATAWAREHOUSE_PARAMETERS" to "false",
            "CSM_SEND_DATAWAREHOUSE_DATASETS" to "true",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "organizationid-test",
            "TWIN_CACHE_HOST" to "this_is_a_host",
            "TWIN_CACHE_PORT" to "6973",
            "TWIN_CACHE_PASSWORD" to "this_is_a_password",
            "TWIN_CACHE_USERNAME" to "default")
    assertEquals(expected.toSortedMap(), container.envVars?.toSortedMap())
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
          getOrganization(),
          getWorkspace(),
          getScenario(),
          getSolution(),
          CSM_SIMULATION_ID,
          "badTemplate",
          CSM_SIMULATION_ID,
          nodeSizingLabel = NODE_LABEL_DEFAULT,
          customSizing = BASIC_SIZING)
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
            getScenario(),
            getSolutionLocalSources(),
            CSM_SIMULATION_ID,
            "testruntemplate",
            CSM_SIMULATION_ID,
            nodeSizingLabel = NODE_LABEL_DEFAULT,
            customSizing = BASIC_SIZING)
    envVarsWithSourceLocalValid(container, "handle-parameters", "CSM_PARAMETERS_HANDLER_PROVIDER")
  }

  @Test
  fun `Parameters Handler Container env vars source defined valid`() {
    val container =
        factory.buildApplyParametersContainer(
            getOrganization(),
            getWorkspace(),
            getScenario(),
            getSolutionCloudSources(),
            CSM_SIMULATION_ID,
            "testruntemplate",
            CSM_SIMULATION_ID,
            nodeSizingLabel = NODE_LABEL_DEFAULT,
            customSizing = BASIC_SIZING)
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
            getScenario(),
            getSolutionCloudSources(),
            CSM_SIMULATION_ID,
            "testruntemplate",
            CSM_SIMULATION_ID,
            nodeSizingLabel = NODE_LABEL_DEFAULT,
            customSizing = BASIC_SIZING)
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
            getScenario(),
            getSolutionCloudSources(),
            CSM_SIMULATION_ID,
            "testruntemplate",
            CSM_SIMULATION_ID,
            nodeSizingLabel = NODE_LABEL_DEFAULT,
            customSizing = BASIC_SIZING)
    envVarsWithSourceValid(
        container, "prerun", "CSM_PRERUN_PROVIDER", "CSM_PRERUN_PATH", "prerun", "testruntemplate")
  }

  @Test
  fun `run Container env vars source defined valid`() {
    val container =
        factory.buildRunContainer(
            getOrganization(),
            getWorkspace(),
            getScenario(),
            getSolutionCloudSources(),
            CSM_SIMULATION_ID,
            "testruntemplate",
            CSM_SIMULATION_ID,
            nodeSizingLabel = NODE_LABEL_DEFAULT,
            customSizing = BASIC_SIZING)
    envVarsWithSourceValid(
        container, "engine", "CSM_ENGINE_PROVIDER", "CSM_ENGINE_PATH", "engine", "testruntemplate")
  }

  @Test
  fun `postrun Container env vars source defined valid`() {
    val container =
        factory.buildPostRunContainer(
            getOrganization(),
            getWorkspace(),
            getScenario(),
            getSolutionCloudSources(),
            CSM_SIMULATION_ID,
            "testruntemplate",
            CSM_SIMULATION_ID,
            nodeSizingLabel = NODE_LABEL_DEFAULT,
            customSizing = BASIC_SIZING)
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
            "IDENTITY_PROVIDER" to "azure",
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to CSM_SIMULATION_ID,
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "organizationid-test",
            "CSM_ORGANIZATION_ID" to "Organizationid",
            "CSM_WORKSPACE_ID" to "Workspaceid",
            "CSM_SCENARIO_ID" to "Scenarioid",
            "CSM_SCENARIO_RUN_ID" to CSM_SIMULATION_ID,
            "CSM_RUN_TEMPLATE_ID" to "testruntemplate",
            "CSM_CONTAINER_MODE" to mode,
            "CSM_PROBES_MEASURES_TOPIC" to
                "amqps://csm-phoenix.servicebus.windows.net/organizationid-test",
            "CSM_CONTROL_PLANE_TOPIC" to
                "amqps://csm-phoenix.servicebus.windows.net/organizationid-test-scenariorun",
            "CSM_SIMULATION" to "TestSimulation",
            providerEnvVar to "azureStorage",
            "AZURE_STORAGE_CONNECTION_STRING" to "csmphoenix_storage_connection_string",
            resourceEnvVar to "organizationid/1/$runTemplate/$resource.zip",
            "TWIN_CACHE_HOST" to "this_is_a_host",
            "TWIN_CACHE_PORT" to "6973",
            "TWIN_CACHE_PASSWORD" to "this_is_a_password",
            "TWIN_CACHE_USERNAME" to "default",
            "CSM_ENTRYPOINT_LEGACY" to "true")
    assertEquals(expected.toSortedMap(), container.envVars?.toSortedMap())
  }

  private fun envVarsWithSourceLocalValid(
      container: ScenarioRunContainer,
      mode: String,
      providerEnvVar: String
  ) {
    val expected =
        mapOf(
            "IDENTITY_PROVIDER" to "azure",
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to CSM_SIMULATION_ID,
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "organizationid-test",
            "CSM_ORGANIZATION_ID" to "Organizationid",
            "CSM_WORKSPACE_ID" to "Workspaceid",
            "CSM_SCENARIO_ID" to "Scenarioid",
            "CSM_SCENARIO_RUN_ID" to CSM_SIMULATION_ID,
            "CSM_RUN_TEMPLATE_ID" to "testruntemplate",
            "CSM_CONTAINER_MODE" to mode,
            "CSM_PROBES_MEASURES_TOPIC" to
                "amqps://csm-phoenix.servicebus.windows.net/organizationid-test",
            "CSM_CONTROL_PLANE_TOPIC" to
                "amqps://csm-phoenix.servicebus.windows.net/organizationid-test-scenariorun",
            "CSM_SIMULATION" to "TestSimulation",
            providerEnvVar to "local",
            "AZURE_STORAGE_CONNECTION_STRING" to "csmphoenix_storage_connection_string",
            "TWIN_CACHE_HOST" to "this_is_a_host",
            "TWIN_CACHE_PORT" to "6973",
            "TWIN_CACHE_PASSWORD" to "this_is_a_password",
            "TWIN_CACHE_USERNAME" to "default",
            "CSM_ENTRYPOINT_LEGACY" to "true")
    assertEquals(expected.toSortedMap(), container.envVars?.toSortedMap())
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
            CSM_SIMULATION_ID,
            CSM_SIMULATION_ID,
            scenarioRunLabel = NODE_LABEL_DEFAULT,
            scenarioRunSizing = BASIC_SIZING)
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
            CSM_SIMULATION_ID,
            CSM_SIMULATION_ID,
            scenarioRunLabel = NODE_LABEL_DEFAULT,
            scenarioRunSizing = BASIC_SIZING)
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
            CSM_SIMULATION_ID,
            CSM_SIMULATION_ID,
            scenarioRunLabel = NODE_LABEL_DEFAULT,
            scenarioRunSizing = BASIC_SIZING)
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
            CSM_SIMULATION_ID,
            CSM_SIMULATION_ID,
            scenarioRunLabel = NODE_LABEL_DEFAULT,
            scenarioRunSizing = BASIC_SIZING)
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
            CSM_SIMULATION_ID,
            CSM_SIMULATION_ID,
            scenarioRunLabel = NODE_LABEL_DEFAULT,
            scenarioRunSizing = BASIC_SIZING)
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
            CSM_SIMULATION_ID,
            CSM_SIMULATION_ID,
            scenarioRunLabel = NODE_LABEL_DEFAULT,
            scenarioRunSizing = BASIC_SIZING)
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
            CSM_SIMULATION_ID,
            CSM_SIMULATION_ID,
            scenarioRunLabel = NODE_LABEL_DEFAULT,
            scenarioRunSizing = BASIC_SIZING)
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
            CSM_SIMULATION_ID,
            CSM_SIMULATION_ID,
            scenarioRunLabel = NODE_LABEL_DEFAULT,
            scenarioRunSizing = BASIC_SIZING)
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
            CSM_SIMULATION_ID,
            CSM_SIMULATION_ID,
            scenarioRunLabel = NODE_LABEL_DEFAULT,
            scenarioRunSizing = BASIC_SIZING)
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
            CSM_SIMULATION_ID,
            CSM_SIMULATION_ID,
            scenarioRunLabel = NODE_LABEL_DEFAULT,
            scenarioRunSizing = BASIC_SIZING)
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
            CSM_SIMULATION_ID,
            CSM_SIMULATION_ID,
            scenarioRunLabel = NODE_LABEL_DEFAULT,
            scenarioRunSizing = BASIC_SIZING)
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
            CSM_SIMULATION_ID,
            CSM_SIMULATION_ID,
            scenarioRunLabel = NODE_LABEL_DEFAULT,
            scenarioRunSizing = BASIC_SIZING)
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
            CSM_SIMULATION_ID,
            CSM_SIMULATION_ID,
            scenarioRunLabel = NODE_LABEL_DEFAULT,
            scenarioRunSizing = BASIC_SIZING)
    val container = containers.find { container -> container.name == "fetchDatasetContainer-2" }
    val expected =
        mapOf(
            "IDENTITY_PROVIDER" to "azure",
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to CSM_SIMULATION_ID,
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "organizationid-test",
            "CSM_ORGANIZATION_ID" to "Organizationid",
            "CSM_WORKSPACE_ID" to "Workspaceid",
            "CSM_SCENARIO_ID" to "Scenarioid",
            "CSM_SCENARIO_RUN_ID" to CSM_SIMULATION_ID,
            "CSM_FETCH_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "ENV_PARAM_1" to "env_param1_value",
            "ENV_PARAM_2" to "env_param2_value",
            "ENV_PARAM_3" to "env_param3_value",
            "TWIN_CACHE_HOST" to "this_is_a_host",
            "TWIN_CACHE_PORT" to "6973",
            "TWIN_CACHE_PASSWORD" to "this_is_a_password",
            "TWIN_CACHE_USERNAME" to "default")
    assertEquals(expected.toSortedMap(), container?.envVars?.toSortedMap())
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
            CSM_SIMULATION_ID,
            CSM_SIMULATION_ID,
            scenarioRunLabel = NODE_LABEL_DEFAULT,
            scenarioRunSizing = BASIC_SIZING)
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
            CSM_SIMULATION_ID,
            DEFAULT_WORKFLOW_TYPE,
        )
    assertEquals("highcpupool", startContainers.nodeLabel)
  }

  @Test
  fun `Build start containers node Label NONE`() {
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
            CSM_SIMULATION_ID,
            DEFAULT_WORKFLOW_TYPE,
        )
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
            CSM_SIMULATION_ID,
            DEFAULT_WORKFLOW_TYPE,
        )
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
            CSM_SIMULATION_ID,
            DEFAULT_WORKFLOW_TYPE,
        )
    assertEquals("workflow-scenariorunid-", startContainers.generateName)
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
            CSM_SIMULATION_ID,
            CSM_SIMULATION_ID,
            scenarioRunLabel = NODE_LABEL_DEFAULT,
            scenarioRunSizing = BASIC_SIZING)
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
            CSM_SIMULATION_ID,
            CSM_SIMULATION_ID,
            scenarioRunLabel = NODE_LABEL_DEFAULT,
            scenarioRunSizing = BASIC_SIZING)
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
            CSM_SIMULATION_ID,
            CSM_SIMULATION_ID,
            scenarioRunLabel = NODE_LABEL_DEFAULT,
            scenarioRunSizing = BASIC_SIZING)
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
            CSM_SIMULATION_ID,
            CSM_SIMULATION_ID,
            scenarioRunLabel = NODE_LABEL_DEFAULT,
            scenarioRunSizing = BASIC_SIZING)
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
          scenario,
          datasets,
          connectors,
          workspace,
          getOrganization(),
          solution,
          CSM_SIMULATION_ID,
          CSM_SIMULATION_ID,
          scenarioRunLabel = NODE_LABEL_DEFAULT,
          scenarioRunSizing = BASIC_SIZING)
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
    assertEquals("workflow-scenariorunid-", startInfo.startContainers.generateName)
  }

  @Test
  fun `Full get Start Info pool`() {
    val startInfo = getStartInfoFromIds()
    assertEquals("highcpupool", startInfo.startContainers.nodeLabel)
  }

  @Test
  fun `Full get Start node labels`() {
    val startInfo = getStartInfoFromIds()
    assertEquals(
        mapOf(
            "cosmotech.com/job_id" to CSM_SIMULATION_ID,
            "cosmotech.com/workflowtype" to DEFAULT_WORKFLOW_TYPE,
            "cosmotech.com/organizationId" to "Organizationid",
            "cosmotech.com/workspaceId" to "Workspaceid",
            "cosmotech.com/scenarioId" to "Scenarioid",
        ),
        startInfo.startContainers.labels,
    )
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
          dataset,
          connector,
          1,
          true,
          "fetchId",
          "O-id",
          "W-id",
          "S-id",
          "Sr-id",
          "W-key",
          "csmSimulationId",
          nodeSizingLabel = NODE_LABEL_DEFAULT,
          customSizing = BASIC_SIZING)
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
            dataset,
            connector,
            1,
            true,
            "fetchId",
            "O-id",
            "W-id",
            "S-id",
            "Sr-id",
            "W-key",
            "csmSimulationId",
            nodeSizingLabel = NODE_LABEL_DEFAULT,
            customSizing = BASIC_SIZING)

    assertEquals("$CONTAINER_FETCH_DATASET_PARAMETERS-1", scenarioRunContainer.name)
    assertNull(scenarioRunContainer.labels)
    assertEquals(
        mapOf(
                "IDENTITY_PROVIDER" to "azure",
                "AZURE_TENANT_ID" to "12345678",
                "AZURE_CLIENT_ID" to "98765432",
                "AZURE_CLIENT_SECRET" to "azertyuiop",
                "CSM_SIMULATION_ID" to "csmSimulationId",
                "CSM_API_URL" to "https://api.cosmotech.com",
                "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
                "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
                "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
                "AZURE_DATA_EXPLORER_RESOURCE_URI" to
                    "https://phoenix.westeurope.kusto.windows.net",
                "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                    "https://ingest-phoenix.westeurope.kusto.windows.net",
                "AZURE_DATA_EXPLORER_DATABASE_NAME" to "o-id-w-key",
                "CSM_ORGANIZATION_ID" to "O-id",
                "CSM_WORKSPACE_ID" to "W-id",
                "CSM_SCENARIO_ID" to "S-id",
                "CSM_SCENARIO_RUN_ID" to "Sr-id",
                "CSM_FETCH_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters/fetchId",
                "TWIN_CACHE_HOST" to "this_is_a_host",
                "TWIN_CACHE_PORT" to "6973",
                "TWIN_CACHE_PASSWORD" to "this_is_a_password",
                "TWIN_CACHE_USERNAME" to "default")
            .toSortedMap(),
        scenarioRunContainer.envVars?.toSortedMap())
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
            dataset,
            connector,
            1,
            true,
            "fetchId",
            "O-id",
            "W-id",
            "S-id",
            "Sr-id",
            "W-key",
            "csmSimulationId",
            nodeSizingLabel = NODE_LABEL_DEFAULT,
            customSizing = BASIC_SIZING)

    assertEquals("$CONTAINER_FETCH_DATASET_PARAMETERS-1", scenarioRunContainer.name)
    assertNull(scenarioRunContainer.labels)
    assertEquals(
        mapOf(
                "IDENTITY_PROVIDER" to "azure",
                "AZURE_TENANT_ID" to "12345678",
                "AZURE_CLIENT_ID" to "98765432",
                "AZURE_CLIENT_SECRET" to "azertyuiop",
                "CSM_SIMULATION_ID" to "csmSimulationId",
                "CSM_API_URL" to "https://api.cosmotech.com",
                "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
                "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
                "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
                "AZURE_DATA_EXPLORER_RESOURCE_URI" to
                    "https://phoenix.westeurope.kusto.windows.net",
                "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                    "https://ingest-phoenix.westeurope.kusto.windows.net",
                "AZURE_DATA_EXPLORER_DATABASE_NAME" to "o-id-w-key",
                "CSM_ORGANIZATION_ID" to "O-id",
                "CSM_WORKSPACE_ID" to "W-id",
                "CSM_SCENARIO_ID" to "S-id",
                "CSM_SCENARIO_RUN_ID" to "Sr-id",
                "CSM_FETCH_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters/fetchId",
                "TWIN_CACHE_HOST" to "this_is_a_host",
                "TWIN_CACHE_PORT" to "6973",
                "TWIN_CACHE_PASSWORD" to "this_is_a_password",
                "TWIN_CACHE_USERNAME" to "default")
            .toSortedMap(),
        scenarioRunContainer.envVars?.toSortedMap())
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
            dataset,
            connector,
            1,
            true,
            "fetchId",
            "O-id",
            "W-id",
            "S-id",
            "Sr-id",
            "W-key",
            "csmSimulationId",
            nodeSizingLabel = NODE_LABEL_DEFAULT,
            customSizing = BASIC_SIZING)

    assertEquals("$CONTAINER_FETCH_DATASET_PARAMETERS-1", scenarioRunContainer.name)
    assertEquals(
        mapOf(AZURE_AAD_POD_ID_BINDING_LABEL to "phoenixdev-pod-identity"),
        scenarioRunContainer.labels)
    assertEquals(
        mapOf(
                "IDENTITY_PROVIDER" to "azure",
                "CSM_AZURE_MANAGED_IDENTITY" to "true",
                "CSM_SIMULATION_ID" to "csmSimulationId",
                "CSM_API_URL" to "https://api.cosmotech.com",
                "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
                "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
                "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
                "AZURE_DATA_EXPLORER_RESOURCE_URI" to
                    "https://phoenix.westeurope.kusto.windows.net",
                "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                    "https://ingest-phoenix.westeurope.kusto.windows.net",
                "AZURE_DATA_EXPLORER_DATABASE_NAME" to "o-id-w-key",
                "CSM_ORGANIZATION_ID" to "O-id",
                "CSM_WORKSPACE_ID" to "W-id",
                "CSM_SCENARIO_ID" to "S-id",
                "CSM_SCENARIO_RUN_ID" to "Sr-id",
                "CSM_FETCH_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters/fetchId",
                "TWIN_CACHE_HOST" to "this_is_a_host",
                "TWIN_CACHE_PORT" to "6973",
                "TWIN_CACHE_PASSWORD" to "this_is_a_password",
                "TWIN_CACHE_USERNAME" to "default")
            .toSortedMap(),
        scenarioRunContainer.envVars?.toSortedMap())
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
            dataset,
            connector,
            1,
            true,
            "fetchId",
            "O-id",
            "W-id",
            "S-id",
            "Sr-id",
            "W-key",
            "csmSimulationId",
            nodeSizingLabel = NODE_LABEL_DEFAULT,
            customSizing = BASIC_SIZING)

    assertEquals("$CONTAINER_FETCH_DATASET_PARAMETERS-1", scenarioRunContainer.name)
    assertNull(scenarioRunContainer.labels)
    assertEquals(
        mapOf(
                "IDENTITY_PROVIDER" to "azure",
                "AZURE_TENANT_ID" to "12345678",
                "AZURE_CLIENT_ID" to "98765432",
                "AZURE_CLIENT_SECRET" to "azertyuiop",
                "CSM_SIMULATION_ID" to "csmSimulationId",
                "CSM_API_URL" to "https://api.cosmotech.com",
                "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
                "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
                "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
                "AZURE_DATA_EXPLORER_RESOURCE_URI" to
                    "https://phoenix.westeurope.kusto.windows.net",
                "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                    "https://ingest-phoenix.westeurope.kusto.windows.net",
                "AZURE_DATA_EXPLORER_DATABASE_NAME" to "o-id-w-key",
                "CSM_ORGANIZATION_ID" to "O-id",
                "CSM_WORKSPACE_ID" to "W-id",
                "CSM_SCENARIO_ID" to "S-id",
                "CSM_SCENARIO_RUN_ID" to "Sr-id",
                "CSM_FETCH_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters/fetchId",
                "TWIN_CACHE_HOST" to "this_is_a_host",
                "TWIN_CACHE_PORT" to "6973",
                "TWIN_CACHE_PASSWORD" to "this_is_a_password",
                "TWIN_CACHE_USERNAME" to "default")
            .toSortedMap(),
        scenarioRunContainer.envVars?.toSortedMap())
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
            dataset,
            connector,
            1,
            true,
            "fetchId",
            "O-id",
            "W-id",
            "S-id",
            "Sr-id",
            "W-key",
            "csmSimulationId",
            nodeSizingLabel = NODE_LABEL_DEFAULT,
            customSizing = BASIC_SIZING)

    assertEquals("$CONTAINER_FETCH_DATASET_PARAMETERS-1", scenarioRunContainer.name)
    assertNull(scenarioRunContainer.labels)
    assertEquals(
        mapOf(
                "IDENTITY_PROVIDER" to "azure",
                "AZURE_TENANT_ID" to "12345678",
                "AZURE_CLIENT_ID" to "98765432",
                "AZURE_CLIENT_SECRET" to "azertyuiop",
                "CSM_SIMULATION_ID" to "csmSimulationId",
                "CSM_API_URL" to "https://api.cosmotech.com",
                "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
                "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
                "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
                "AZURE_DATA_EXPLORER_RESOURCE_URI" to
                    "https://phoenix.westeurope.kusto.windows.net",
                "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                    "https://ingest-phoenix.westeurope.kusto.windows.net",
                "AZURE_DATA_EXPLORER_DATABASE_NAME" to "o-id-w-key",
                "CSM_ORGANIZATION_ID" to "O-id",
                "CSM_WORKSPACE_ID" to "W-id",
                "CSM_SCENARIO_ID" to "S-id",
                "CSM_SCENARIO_RUN_ID" to "Sr-id",
                "CSM_FETCH_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters/fetchId",
                "TWIN_CACHE_HOST" to "this_is_a_host",
                "TWIN_CACHE_PORT" to "6973",
                "TWIN_CACHE_PASSWORD" to "this_is_a_password",
                "TWIN_CACHE_USERNAME" to "default")
            .toSortedMap(),
        scenarioRunContainer.envVars?.toSortedMap())
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
            dataset,
            connector,
            1,
            true,
            "fetchId",
            "O-id",
            "W-id",
            "S-id",
            "Sr-id",
            "W-key",
            "csmSimulationId",
            nodeSizingLabel = NODE_LABEL_DEFAULT,
            customSizing = BASIC_SIZING)

    assertEquals("$CONTAINER_FETCH_DATASET_PARAMETERS-1", scenarioRunContainer.name)
    assertNull(scenarioRunContainer.labels)
    assertEquals(
        mapOf(
                "IDENTITY_PROVIDER" to "azure",
                "AZURE_TENANT_ID" to "customer-app-registration-tenantId",
                "AZURE_CLIENT_ID" to "customer-app-registration-clientId",
                "AZURE_CLIENT_SECRET" to "customer-app-registration-clientSecret",
                "CSM_SIMULATION_ID" to "csmSimulationId",
                "CSM_API_URL" to "https://api.cosmotech.com",
                "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
                "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
                "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
                "AZURE_DATA_EXPLORER_RESOURCE_URI" to
                    "https://phoenix.westeurope.kusto.windows.net",
                "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                    "https://ingest-phoenix.westeurope.kusto.windows.net",
                "AZURE_DATA_EXPLORER_DATABASE_NAME" to "o-id-w-key",
                "CSM_ORGANIZATION_ID" to "O-id",
                "CSM_WORKSPACE_ID" to "W-id",
                "CSM_SCENARIO_ID" to "S-id",
                "CSM_SCENARIO_RUN_ID" to "Sr-id",
                "CSM_FETCH_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters/fetchId",
                "TWIN_CACHE_HOST" to "this_is_a_host",
                "TWIN_CACHE_PORT" to "6973",
                "TWIN_CACHE_PASSWORD" to "this_is_a_password",
                "TWIN_CACHE_USERNAME" to "default")
            .toSortedMap(),
        scenarioRunContainer.envVars?.toSortedMap())
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
                "IDENTITY_PROVIDER" to "azure",
                "AZURE_TENANT_ID" to "12345678",
                "AZURE_CLIENT_ID" to "98765432",
                "AZURE_CLIENT_SECRET" to "azertyuiop",
                "CSM_SIMULATION_ID" to CSM_SIMULATION_ID,
                "CSM_API_URL" to "https://api.cosmotech.com",
                "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
                "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
                "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
                "AZURE_DATA_EXPLORER_RESOURCE_URI" to
                    "https://phoenix.westeurope.kusto.windows.net",
                "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                    "https://ingest-phoenix.westeurope.kusto.windows.net",
                "AZURE_DATA_EXPLORER_DATABASE_NAME" to "organizationid-test",
                "CSM_ORGANIZATION_ID" to "Organizationid",
                "CSM_WORKSPACE_ID" to "Workspaceid",
                "CSM_SCENARIO_ID" to "Scenarioid",
                "CSM_SCENARIO_RUN_ID" to CSM_SIMULATION_ID,
                "CSM_RUN_TEMPLATE_ID" to "testruntemplate",
                "CSM_CONTAINER_MODE" to "handle-parameters",
                "CSM_PROBES_MEASURES_TOPIC" to
                    "amqps://csm-phoenix.servicebus.windows.net/organizationid-test",
                "CSM_CONTROL_PLANE_TOPIC" to
                    "amqps://csm-phoenix.servicebus.windows.net/organizationid-test-scenariorun",
                "CSM_SIMULATION" to "TestSimulation",
                "TWIN_CACHE_HOST" to "this_is_a_host",
                "TWIN_CACHE_PORT" to "6973",
                "TWIN_CACHE_PASSWORD" to "this_is_a_password",
                "TWIN_CACHE_USERNAME" to "default",
                "CSM_ENTRYPOINT_LEGACY" to "true")
            .toSortedMap(),
        container.envVars?.toSortedMap())
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
                "IDENTITY_PROVIDER" to "azure",
                "AZURE_TENANT_ID" to "12345678",
                "AZURE_CLIENT_ID" to "98765432",
                "AZURE_CLIENT_SECRET" to "azertyuiop",
                "CSM_SIMULATION_ID" to CSM_SIMULATION_ID,
                "CSM_API_URL" to "https://api.cosmotech.com",
                "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
                "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
                "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
                "AZURE_DATA_EXPLORER_RESOURCE_URI" to
                    "https://phoenix.westeurope.kusto.windows.net",
                "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                    "https://ingest-phoenix.westeurope.kusto.windows.net",
                "AZURE_DATA_EXPLORER_DATABASE_NAME" to "organizationid-test",
                "CSM_ORGANIZATION_ID" to "Organizationid",
                "CSM_WORKSPACE_ID" to "Workspaceid",
                "CSM_SCENARIO_ID" to "Scenarioid",
                "CSM_SCENARIO_RUN_ID" to CSM_SIMULATION_ID,
                "CSM_RUN_TEMPLATE_ID" to "testruntemplate",
                "CSM_CONTAINER_MODE" to "prerun",
                "CSM_PROBES_MEASURES_TOPIC" to
                    "amqps://csm-phoenix.servicebus.windows.net/organizationid-test",
                "CSM_CONTROL_PLANE_TOPIC" to
                    "amqps://csm-phoenix.servicebus.windows.net/organizationid-test-scenariorun",
                "CSM_SIMULATION" to "TestSimulation",
                "TWIN_CACHE_HOST" to "this_is_a_host",
                "TWIN_CACHE_PORT" to "6973",
                "TWIN_CACHE_PASSWORD" to "this_is_a_password",
                "TWIN_CACHE_USERNAME" to "default",
                "CSM_ENTRYPOINT_LEGACY" to "true")
            .toSortedMap(),
        container.envVars?.toSortedMap())
  }
  @Test
  fun `PROD-8072- Tenant credentials are set as env vars to solution container without control plane`() {
    every { azure.eventBus } returns
        CsmPlatformAzureEventBus(
            baseUri = "amqps://csm-phoenix.servicebus.windows.net",
            authentication = Authentication(strategy = TENANT_CLIENT_CREDENTIALS))

    val container = buildRunContainer(false, false)

    assertNotNull(container.envVars)
    assertFalse { container.envVars!!.containsKey(AZURE_EVENT_HUB_SHARED_ACCESS_POLICY_ENV_VAR) }
    assertFalse { container.envVars!!.containsKey(AZURE_EVENT_HUB_SHARED_ACCESS_KEY_ENV_VAR) }

    assertEquals(
        mapOf(
            "IDENTITY_PROVIDER" to "azure",
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to CSM_SIMULATION_ID,
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "organizationid-test",
            "CSM_ORGANIZATION_ID" to "Organizationid",
            "CSM_WORKSPACE_ID" to "Workspaceid",
            "CSM_SCENARIO_ID" to "Scenarioid",
            "CSM_SCENARIO_RUN_ID" to CSM_SIMULATION_ID,
            "CSM_RUN_TEMPLATE_ID" to "testruntemplate",
            "CSM_CONTAINER_MODE" to "engine",
            "CSM_PROBES_MEASURES_TOPIC" to
                "amqps://csm-phoenix.servicebus.windows.net/organizationid-test",
            "CSM_SIMULATION" to "TestSimulation",
            "TWIN_CACHE_HOST" to "this_is_a_host",
            "TWIN_CACHE_PORT" to "6973",
            "TWIN_CACHE_PASSWORD" to "this_is_a_password",
            "TWIN_CACHE_USERNAME" to "default",
            "CSM_ENTRYPOINT_LEGACY" to "true"),
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
                "IDENTITY_PROVIDER" to "azure",
                "AZURE_TENANT_ID" to "12345678",
                "AZURE_CLIENT_ID" to "98765432",
                "AZURE_CLIENT_SECRET" to "azertyuiop",
                "CSM_SIMULATION_ID" to CSM_SIMULATION_ID,
                "CSM_API_URL" to "https://api.cosmotech.com",
                "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
                "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
                "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
                "AZURE_DATA_EXPLORER_RESOURCE_URI" to
                    "https://phoenix.westeurope.kusto.windows.net",
                "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                    "https://ingest-phoenix.westeurope.kusto.windows.net",
                "AZURE_DATA_EXPLORER_DATABASE_NAME" to "organizationid-test",
                "CSM_ORGANIZATION_ID" to "Organizationid",
                "CSM_WORKSPACE_ID" to "Workspaceid",
                "CSM_SCENARIO_ID" to "Scenarioid",
                "CSM_SCENARIO_RUN_ID" to CSM_SIMULATION_ID,
                "CSM_RUN_TEMPLATE_ID" to "testruntemplate",
                "CSM_CONTAINER_MODE" to "engine",
                "CSM_PROBES_MEASURES_TOPIC" to
                    "amqps://csm-phoenix.servicebus.windows.net/organizationid-test",
                "CSM_CONTROL_PLANE_TOPIC" to
                    "amqps://csm-phoenix.servicebus.windows.net/organizationid-test-scenariorun",
                "AZURE_EVENT_HUB_SHARED_ACCESS_POLICY" to "my-eventhub-access-policy",
                "AZURE_EVENT_HUB_SHARED_ACCESS_KEY" to "a1b2c3d4e5==",
                "CSM_AMQPCONSUMER_USER" to "my-eventhub-access-policy",
                "CSM_AMQPCONSUMER_PASSWORD" to "a1b2c3d4e5==",
                "CSM_CONTROL_PLANE_USER" to "my-eventhub-access-policy",
                "CSM_CONTROL_PLANE_PASSWORD" to "a1b2c3d4e5==",
                "CSM_SIMULATION" to "TestSimulation",
                "TWIN_CACHE_HOST" to "this_is_a_host",
                "TWIN_CACHE_PORT" to "6973",
                "TWIN_CACHE_PASSWORD" to "this_is_a_password",
                "TWIN_CACHE_USERNAME" to "default",
                "CSM_ENTRYPOINT_LEGACY" to "true")
            .toSortedMap(),
        container.envVars?.toSortedMap())
  }

  @Test
  fun `PROD-8072- Shared Access key set as env vars to solution container without control plane`() {
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

    val container = buildRunContainer(false, false)

    assertNotNull(container.envVars)

    assertEquals(
        mapOf(
                "IDENTITY_PROVIDER" to "azure",
                "AZURE_TENANT_ID" to "12345678",
                "AZURE_CLIENT_ID" to "98765432",
                "AZURE_CLIENT_SECRET" to "azertyuiop",
                "CSM_SIMULATION_ID" to CSM_SIMULATION_ID,
                "CSM_API_URL" to "https://api.cosmotech.com",
                "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
                "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
                "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
                "AZURE_DATA_EXPLORER_RESOURCE_URI" to
                    "https://phoenix.westeurope.kusto.windows.net",
                "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                    "https://ingest-phoenix.westeurope.kusto.windows.net",
                "AZURE_DATA_EXPLORER_DATABASE_NAME" to "organizationid-test",
                "CSM_ORGANIZATION_ID" to "Organizationid",
                "CSM_WORKSPACE_ID" to "Workspaceid",
                "CSM_SCENARIO_ID" to "Scenarioid",
                "CSM_SCENARIO_RUN_ID" to CSM_SIMULATION_ID,
                "CSM_RUN_TEMPLATE_ID" to "testruntemplate",
                "CSM_CONTAINER_MODE" to "engine",
                "CSM_PROBES_MEASURES_TOPIC" to
                    "amqps://csm-phoenix.servicebus.windows.net/organizationid-test",
                "AZURE_EVENT_HUB_SHARED_ACCESS_POLICY" to "my-eventhub-access-policy",
                "AZURE_EVENT_HUB_SHARED_ACCESS_KEY" to "a1b2c3d4e5==",
                "CSM_AMQPCONSUMER_USER" to "my-eventhub-access-policy",
                "CSM_AMQPCONSUMER_PASSWORD" to "a1b2c3d4e5==",
                "CSM_SIMULATION" to "TestSimulation",
                "TWIN_CACHE_HOST" to "this_is_a_host",
                "TWIN_CACHE_PORT" to "6973",
                "TWIN_CACHE_PASSWORD" to "this_is_a_password",
                "TWIN_CACHE_USERNAME" to "default",
                "CSM_ENTRYPOINT_LEGACY" to "true")
            .toSortedMap(),
        container.envVars?.toSortedMap())
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
    val workspaceId = "Workspaceid"
    val scenarioId = "Scenarioid"
    val scenarioRunId = CSM_SIMULATION_ID

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
    every { containerRegistryService.checkSolutionImage(any(), any()) } returns Unit

    return factory.getStartInfo(
        organizationId,
        workspaceId,
        scenarioId,
        DEFAULT_WORKFLOW_TYPE,
        scenarioRunId,
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
            CSM_SIMULATION_ID,
            CSM_SIMULATION_ID,
            scenarioRunLabel = NODE_LABEL_DEFAULT,
            scenarioRunSizing = BASIC_SIZING)
    val container =
        containers.find { container ->
          container.name == "fetchScenarioDatasetParametersContainer-$nameId"
        }
    val expected =
        mapOf(
            "IDENTITY_PROVIDER" to "azure",
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to CSM_SIMULATION_ID,
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "organizationid-test",
            "CSM_ORGANIZATION_ID" to "Organizationid",
            "CSM_WORKSPACE_ID" to "Workspaceid",
            "CSM_SCENARIO_ID" to "Scenarioid",
            "CSM_SCENARIO_RUN_ID" to CSM_SIMULATION_ID,
            "CSM_FETCH_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters/$param",
            "ENV_PARAM_1" to "env_param1_value",
            "ENV_PARAM_2" to "env_param2_value",
            "ENV_PARAM_3" to "env_param3_value",
            "TWIN_CACHE_HOST" to "this_is_a_host",
            "TWIN_CACHE_PORT" to "6973",
            "TWIN_CACHE_PASSWORD" to "this_is_a_password",
            "TWIN_CACHE_USERNAME" to "default")
    assertEquals(expected.toSortedMap(), container?.envVars?.toSortedMap())
  }

  @Test
  fun `PROD-7623- Dedicated EventHub by namespace set to true and send control plane to true`() {
    val container = buildRunContainer(true, true)

    assertNotNull(container.envVars)
    assertEquals(
        mapOf(
                "IDENTITY_PROVIDER" to "azure",
                "AZURE_TENANT_ID" to "12345678",
                "AZURE_CLIENT_ID" to "98765432",
                "AZURE_CLIENT_SECRET" to "azertyuiop",
                "CSM_SIMULATION_ID" to CSM_SIMULATION_ID,
                "CSM_API_URL" to "https://api.cosmotech.com",
                "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
                "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
                "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
                "AZURE_DATA_EXPLORER_RESOURCE_URI" to
                    "https://phoenix.westeurope.kusto.windows.net",
                "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                    "https://ingest-phoenix.westeurope.kusto.windows.net",
                "AZURE_DATA_EXPLORER_DATABASE_NAME" to "organizationid-test",
                "CSM_ORGANIZATION_ID" to "Organizationid",
                "CSM_WORKSPACE_ID" to "Workspaceid",
                "CSM_SCENARIO_ID" to "Scenarioid",
                "CSM_SCENARIO_RUN_ID" to CSM_SIMULATION_ID,
                "CSM_RUN_TEMPLATE_ID" to "testruntemplate",
                "CSM_CONTAINER_MODE" to "engine",
                "CSM_PROBES_MEASURES_TOPIC" to
                    "amqps://organizationid-test.servicebus.windows.net/probesmeasures",
                "CSM_CONTROL_PLANE_TOPIC" to
                    "amqps://organizationid-test.servicebus.windows.net/scenariorun",
                "CSM_SIMULATION" to "TestSimulation",
                "TWIN_CACHE_HOST" to "this_is_a_host",
                "TWIN_CACHE_PORT" to "6973",
                "TWIN_CACHE_PASSWORD" to "this_is_a_password",
                "TWIN_CACHE_USERNAME" to "default",
                "CSM_ENTRYPOINT_LEGACY" to "true")
            .toSortedMap(),
        container.envVars?.toSortedMap())
  }

  @Test
  fun `PROD-7623- Dedicated EventHub by namespace set to true and send control plane to false`() {
    val container = buildRunContainer(true, false)

    assertNotNull(container.envVars)
    assertEquals(
        mapOf(
                "IDENTITY_PROVIDER" to "azure",
                "AZURE_TENANT_ID" to "12345678",
                "AZURE_CLIENT_ID" to "98765432",
                "AZURE_CLIENT_SECRET" to "azertyuiop",
                "CSM_SIMULATION_ID" to CSM_SIMULATION_ID,
                "CSM_API_URL" to "https://api.cosmotech.com",
                "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
                "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
                "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
                "AZURE_DATA_EXPLORER_RESOURCE_URI" to
                    "https://phoenix.westeurope.kusto.windows.net",
                "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                    "https://ingest-phoenix.westeurope.kusto.windows.net",
                "AZURE_DATA_EXPLORER_DATABASE_NAME" to "organizationid-test",
                "CSM_ORGANIZATION_ID" to "Organizationid",
                "CSM_WORKSPACE_ID" to "Workspaceid",
                "CSM_SCENARIO_ID" to "Scenarioid",
                "CSM_SCENARIO_RUN_ID" to CSM_SIMULATION_ID,
                "CSM_RUN_TEMPLATE_ID" to "testruntemplate",
                "CSM_CONTAINER_MODE" to "engine",
                "CSM_PROBES_MEASURES_TOPIC" to
                    "amqps://organizationid-test.servicebus.windows.net/probesmeasures",
                "CSM_SIMULATION" to "TestSimulation",
                "TWIN_CACHE_HOST" to "this_is_a_host",
                "TWIN_CACHE_PORT" to "6973",
                "TWIN_CACHE_PASSWORD" to "this_is_a_password",
                "TWIN_CACHE_USERNAME" to "default",
                "CSM_ENTRYPOINT_LEGACY" to "true")
            .toSortedMap(),
        container.envVars?.toSortedMap())
  }

  @Test
  fun `PROD-7623- Dedicated EventHub by namespace set to true`() {
    val container = buildRunContainer(true)

    assertNotNull(container.envVars)
    assertEquals(
        mapOf(
                "IDENTITY_PROVIDER" to "azure",
                "AZURE_TENANT_ID" to "12345678",
                "AZURE_CLIENT_ID" to "98765432",
                "AZURE_CLIENT_SECRET" to "azertyuiop",
                "CSM_SIMULATION_ID" to CSM_SIMULATION_ID,
                "CSM_API_URL" to "https://api.cosmotech.com",
                "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
                "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
                "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
                "AZURE_DATA_EXPLORER_RESOURCE_URI" to
                    "https://phoenix.westeurope.kusto.windows.net",
                "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                    "https://ingest-phoenix.westeurope.kusto.windows.net",
                "AZURE_DATA_EXPLORER_DATABASE_NAME" to "organizationid-test",
                "CSM_ORGANIZATION_ID" to "Organizationid",
                "CSM_WORKSPACE_ID" to "Workspaceid",
                "CSM_SCENARIO_ID" to "Scenarioid",
                "CSM_SCENARIO_RUN_ID" to CSM_SIMULATION_ID,
                "CSM_RUN_TEMPLATE_ID" to "testruntemplate",
                "CSM_CONTAINER_MODE" to "engine",
                "CSM_PROBES_MEASURES_TOPIC" to
                    "amqps://organizationid-test.servicebus.windows.net/probesmeasures",
                "CSM_CONTROL_PLANE_TOPIC" to
                    "amqps://organizationid-test.servicebus.windows.net/scenariorun",
                "CSM_SIMULATION" to "TestSimulation",
                "TWIN_CACHE_HOST" to "this_is_a_host",
                "TWIN_CACHE_PORT" to "6973",
                "TWIN_CACHE_PASSWORD" to "this_is_a_password",
                "TWIN_CACHE_USERNAME" to "default",
                "CSM_ENTRYPOINT_LEGACY" to "true")
            .toSortedMap(),
        container.envVars?.toSortedMap())
  }

  @Test
  fun `Dedicated EventHub by namespace set to true with default strategy SAS and secret key`() {
    every { azure.eventBus } returns
        CsmPlatformAzureEventBus(
            baseUri = "amqps://csm-phoenix.servicebus.windows.net",
            authentication = Authentication(strategy = SHARED_ACCESS_POLICY))
    val name = "RootManageSharedAccessKey"
    val key = "key_in_secret_manager"
    every { secretManager.readSecret("csm-phoenix", "organizationid-test") } returns
        mapOf(WORKSPACE_EVENTHUB_ACCESSKEY_SECRET to key)
    val container = buildRunContainer(true)

    assertNotNull(container.envVars)
    assertEquals(
        mapOf(
                "IDENTITY_PROVIDER" to "azure",
                "AZURE_TENANT_ID" to "12345678",
                "AZURE_CLIENT_ID" to "98765432",
                "AZURE_CLIENT_SECRET" to "azertyuiop",
                "CSM_SIMULATION_ID" to CSM_SIMULATION_ID,
                "CSM_API_URL" to "https://api.cosmotech.com",
                "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
                "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
                "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
                "AZURE_DATA_EXPLORER_RESOURCE_URI" to
                    "https://phoenix.westeurope.kusto.windows.net",
                "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                    "https://ingest-phoenix.westeurope.kusto.windows.net",
                "AZURE_DATA_EXPLORER_DATABASE_NAME" to "organizationid-test",
                "CSM_ORGANIZATION_ID" to "Organizationid",
                "CSM_WORKSPACE_ID" to "Workspaceid",
                "CSM_SCENARIO_ID" to "Scenarioid",
                "CSM_SCENARIO_RUN_ID" to CSM_SIMULATION_ID,
                "CSM_RUN_TEMPLATE_ID" to "testruntemplate",
                "CSM_CONTAINER_MODE" to "engine",
                "CSM_PROBES_MEASURES_TOPIC" to
                    "amqps://organizationid-test.servicebus.windows.net/probesmeasures",
                "AZURE_EVENT_HUB_SHARED_ACCESS_POLICY" to name,
                "AZURE_EVENT_HUB_SHARED_ACCESS_KEY" to key,
                "CSM_AMQPCONSUMER_USER" to name,
                "CSM_AMQPCONSUMER_PASSWORD" to key,
                "CSM_CONTROL_PLANE_TOPIC" to
                    "amqps://organizationid-test.servicebus.windows.net/scenariorun",
                "CSM_CONTROL_PLANE_USER" to name,
                "CSM_CONTROL_PLANE_PASSWORD" to key,
                "CSM_SIMULATION" to "TestSimulation",
                "TWIN_CACHE_HOST" to "this_is_a_host",
                "TWIN_CACHE_PORT" to "6973",
                "TWIN_CACHE_PASSWORD" to "this_is_a_password",
                "TWIN_CACHE_USERNAME" to "default",
                "CSM_ENTRYPOINT_LEGACY" to "true")
            .toSortedMap(),
        container.envVars?.toSortedMap())
  }

  @Test
  fun `Dedicated EventHub by namespace set to true with workspace strategy SAS and secret key`() {
    val name = "RootManageSharedAccessKey"
    val key = "key_in_secret_manager"
    every { secretManager.readSecret("csm-phoenix", "organizationid-test") } returns
        mapOf(WORKSPACE_EVENTHUB_ACCESSKEY_SECRET to key)
    val container = buildRunContainer(true, true, "SHARED_ACCESS_POLICY")

    assertNotNull(container.envVars)
    assertEquals(
        mapOf(
                "IDENTITY_PROVIDER" to "azure",
                "AZURE_TENANT_ID" to "12345678",
                "AZURE_CLIENT_ID" to "98765432",
                "AZURE_CLIENT_SECRET" to "azertyuiop",
                "CSM_SIMULATION_ID" to CSM_SIMULATION_ID,
                "CSM_API_URL" to "https://api.cosmotech.com",
                "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
                "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
                "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
                "AZURE_DATA_EXPLORER_RESOURCE_URI" to
                    "https://phoenix.westeurope.kusto.windows.net",
                "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                    "https://ingest-phoenix.westeurope.kusto.windows.net",
                "AZURE_DATA_EXPLORER_DATABASE_NAME" to "organizationid-test",
                "CSM_ORGANIZATION_ID" to "Organizationid",
                "CSM_WORKSPACE_ID" to "Workspaceid",
                "CSM_SCENARIO_ID" to "Scenarioid",
                "CSM_SCENARIO_RUN_ID" to CSM_SIMULATION_ID,
                "CSM_RUN_TEMPLATE_ID" to "testruntemplate",
                "CSM_CONTAINER_MODE" to "engine",
                "CSM_PROBES_MEASURES_TOPIC" to
                    "amqps://organizationid-test.servicebus.windows.net/probesmeasures",
                "AZURE_EVENT_HUB_SHARED_ACCESS_POLICY" to name,
                "AZURE_EVENT_HUB_SHARED_ACCESS_KEY" to key,
                "CSM_AMQPCONSUMER_USER" to name,
                "CSM_AMQPCONSUMER_PASSWORD" to key,
                "CSM_CONTROL_PLANE_TOPIC" to
                    "amqps://organizationid-test.servicebus.windows.net/scenariorun",
                "CSM_CONTROL_PLANE_USER" to name,
                "CSM_CONTROL_PLANE_PASSWORD" to key,
                "CSM_SIMULATION" to "TestSimulation",
                "TWIN_CACHE_HOST" to "this_is_a_host",
                "TWIN_CACHE_PORT" to "6973",
                "TWIN_CACHE_PASSWORD" to "this_is_a_password",
                "TWIN_CACHE_USERNAME" to "default",
                "CSM_ENTRYPOINT_LEGACY" to "true")
            .toSortedMap(),
        container.envVars?.toSortedMap())
  }

  @Test
  fun `Dedicated EventHub by namespace set to true with workspace strategy tenant credential and secret key`() {
    every { azure.eventBus } returns
        CsmPlatformAzureEventBus(
            baseUri = "amqps://csm-phoenix.servicebus.windows.net",
            authentication = Authentication(strategy = SHARED_ACCESS_POLICY))
    val container = buildRunContainer(true, true, "TENANT_CLIENT_CREDENTIALS")

    assertNotNull(container.envVars)
    assertEquals(
        mapOf(
                "IDENTITY_PROVIDER" to "azure",
                "AZURE_TENANT_ID" to "12345678",
                "AZURE_CLIENT_ID" to "98765432",
                "AZURE_CLIENT_SECRET" to "azertyuiop",
                "CSM_SIMULATION_ID" to CSM_SIMULATION_ID,
                "CSM_API_URL" to "https://api.cosmotech.com",
                "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
                "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
                "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
                "AZURE_DATA_EXPLORER_RESOURCE_URI" to
                    "https://phoenix.westeurope.kusto.windows.net",
                "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                    "https://ingest-phoenix.westeurope.kusto.windows.net",
                "AZURE_DATA_EXPLORER_DATABASE_NAME" to "organizationid-test",
                "CSM_ORGANIZATION_ID" to "Organizationid",
                "CSM_WORKSPACE_ID" to "Workspaceid",
                "CSM_SCENARIO_ID" to "Scenarioid",
                "CSM_SCENARIO_RUN_ID" to CSM_SIMULATION_ID,
                "CSM_RUN_TEMPLATE_ID" to "testruntemplate",
                "CSM_CONTAINER_MODE" to "engine",
                "CSM_PROBES_MEASURES_TOPIC" to
                    "amqps://organizationid-test.servicebus.windows.net/probesmeasures",
                "CSM_CONTROL_PLANE_TOPIC" to
                    "amqps://organizationid-test.servicebus.windows.net/scenariorun",
                "CSM_SIMULATION" to "TestSimulation",
                "TWIN_CACHE_HOST" to "this_is_a_host",
                "TWIN_CACHE_PORT" to "6973",
                "TWIN_CACHE_PASSWORD" to "this_is_a_password",
                "TWIN_CACHE_USERNAME" to "default",
                "CSM_ENTRYPOINT_LEGACY" to "true")
            .toSortedMap(),
        container.envVars?.toSortedMap())
  }

  @Test
  fun `Dedicated EventHub by namespace set to true with workspace strategy SAS, custom name and secret key`() {
    val name = "CustomKeyName"
    val key = "key_in_secret_manager"
    every { secretManager.readSecret("csm-phoenix", "organizationid-test") } returns
        mapOf(WORKSPACE_EVENTHUB_ACCESSKEY_SECRET to key)
    val container = buildRunContainer(true, true, "SHARED_ACCESS_POLICY", name)

    assertNotNull(container.envVars)
    assertEquals(
        mapOf(
                "IDENTITY_PROVIDER" to "azure",
                "AZURE_TENANT_ID" to "12345678",
                "AZURE_CLIENT_ID" to "98765432",
                "AZURE_CLIENT_SECRET" to "azertyuiop",
                "CSM_SIMULATION_ID" to CSM_SIMULATION_ID,
                "CSM_API_URL" to "https://api.cosmotech.com",
                "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
                "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
                "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
                "AZURE_DATA_EXPLORER_RESOURCE_URI" to
                    "https://phoenix.westeurope.kusto.windows.net",
                "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                    "https://ingest-phoenix.westeurope.kusto.windows.net",
                "AZURE_DATA_EXPLORER_DATABASE_NAME" to "organizationid-test",
                "CSM_ORGANIZATION_ID" to "Organizationid",
                "CSM_WORKSPACE_ID" to "Workspaceid",
                "CSM_SCENARIO_ID" to "Scenarioid",
                "CSM_SCENARIO_RUN_ID" to CSM_SIMULATION_ID,
                "CSM_RUN_TEMPLATE_ID" to "testruntemplate",
                "CSM_CONTAINER_MODE" to "engine",
                "CSM_PROBES_MEASURES_TOPIC" to
                    "amqps://organizationid-test.servicebus.windows.net/probesmeasures",
                "AZURE_EVENT_HUB_SHARED_ACCESS_POLICY" to name,
                "AZURE_EVENT_HUB_SHARED_ACCESS_KEY" to key,
                "CSM_AMQPCONSUMER_USER" to name,
                "CSM_AMQPCONSUMER_PASSWORD" to key,
                "CSM_CONTROL_PLANE_TOPIC" to
                    "amqps://organizationid-test.servicebus.windows.net/scenariorun",
                "CSM_CONTROL_PLANE_USER" to name,
                "CSM_CONTROL_PLANE_PASSWORD" to key,
                "CSM_SIMULATION" to "TestSimulation",
                "TWIN_CACHE_HOST" to "this_is_a_host",
                "TWIN_CACHE_PORT" to "6973",
                "TWIN_CACHE_PASSWORD" to "this_is_a_password",
                "TWIN_CACHE_USERNAME" to "default",
                "CSM_ENTRYPOINT_LEGACY" to "true")
            .toSortedMap(),
        container.envVars?.toSortedMap())
  }

  @Test
  fun `Exception if dedicated Event Hub Shared Access Policy strategy but no secret configured`() {
    every { secretManager.readSecret("csm-phoenix", "organizationid-test") } returns mapOf()
    every { azure.eventBus } returns
        CsmPlatformAzureEventBus(
            baseUri = "amqps://csm-phoenix.servicebus.windows.net",
            authentication = Authentication(strategy = SHARED_ACCESS_POLICY))

    assertThrows(IllegalStateException::class.java) { buildRunContainer(true) }
  }

  @Test
  fun `Dedicated EventHub by namespace set to true with default strategy SAS and secret key no control plane`() {
    every { azure.eventBus } returns
        CsmPlatformAzureEventBus(
            baseUri = "amqps://csm-phoenix.servicebus.windows.net",
            authentication = Authentication(strategy = SHARED_ACCESS_POLICY))
    val name = "RootManageSharedAccessKey"
    val key = "key_in_secret_manager"
    every { secretManager.readSecret("csm-phoenix", "organizationid-test") } returns
        mapOf(WORKSPACE_EVENTHUB_ACCESSKEY_SECRET to key)
    val container = buildRunContainer(true, false)

    assertNotNull(container.envVars)
    assertEquals(
        mapOf(
                "IDENTITY_PROVIDER" to "azure",
                "AZURE_TENANT_ID" to "12345678",
                "AZURE_CLIENT_ID" to "98765432",
                "AZURE_CLIENT_SECRET" to "azertyuiop",
                "CSM_SIMULATION_ID" to CSM_SIMULATION_ID,
                "CSM_API_URL" to "https://api.cosmotech.com",
                "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
                "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
                "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
                "AZURE_DATA_EXPLORER_RESOURCE_URI" to
                    "https://phoenix.westeurope.kusto.windows.net",
                "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                    "https://ingest-phoenix.westeurope.kusto.windows.net",
                "AZURE_DATA_EXPLORER_DATABASE_NAME" to "organizationid-test",
                "CSM_ORGANIZATION_ID" to "Organizationid",
                "CSM_WORKSPACE_ID" to "Workspaceid",
                "CSM_SCENARIO_ID" to "Scenarioid",
                "CSM_SCENARIO_RUN_ID" to CSM_SIMULATION_ID,
                "CSM_RUN_TEMPLATE_ID" to "testruntemplate",
                "CSM_CONTAINER_MODE" to "engine",
                "CSM_PROBES_MEASURES_TOPIC" to
                    "amqps://organizationid-test.servicebus.windows.net/probesmeasures",
                "AZURE_EVENT_HUB_SHARED_ACCESS_POLICY" to name,
                "AZURE_EVENT_HUB_SHARED_ACCESS_KEY" to key,
                "CSM_AMQPCONSUMER_USER" to name,
                "CSM_AMQPCONSUMER_PASSWORD" to key,
                "CSM_SIMULATION" to "TestSimulation",
                "TWIN_CACHE_HOST" to "this_is_a_host",
                "TWIN_CACHE_PORT" to "6973",
                "TWIN_CACHE_PASSWORD" to "this_is_a_password",
                "TWIN_CACHE_USERNAME" to "default",
                "CSM_ENTRYPOINT_LEGACY" to "true")
            .toSortedMap(),
        container.envVars?.toSortedMap())
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
                "IDENTITY_PROVIDER" to "azure",
                "AZURE_TENANT_ID" to "12345678",
                "AZURE_CLIENT_ID" to "98765432",
                "AZURE_CLIENT_SECRET" to "azertyuiop",
                "CSM_SIMULATION_ID" to CSM_SIMULATION_ID,
                "CSM_API_URL" to "https://api.cosmotech.com",
                "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
                "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
                "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
                "AZURE_DATA_EXPLORER_RESOURCE_URI" to
                    "https://phoenix.westeurope.kusto.windows.net",
                "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                    "https://ingest-phoenix.westeurope.kusto.windows.net",
                "AZURE_DATA_EXPLORER_DATABASE_NAME" to "organizationid-test",
                "CSM_ORGANIZATION_ID" to "Organizationid",
                "CSM_WORKSPACE_ID" to "Workspaceid",
                "CSM_SCENARIO_ID" to "Scenarioid",
                "CSM_SCENARIO_RUN_ID" to CSM_SIMULATION_ID,
                "CSM_RUN_TEMPLATE_ID" to "testruntemplate",
                "CSM_CONTAINER_MODE" to "engine",
                "CSM_PROBES_MEASURES_TOPIC" to
                    "amqps://csm-phoenix.servicebus.windows.net/organizationid-test",
                "CSM_CONTROL_PLANE_TOPIC" to
                    "amqps://csm-phoenix.servicebus.windows.net/organizationid-test-scenariorun",
                "CSM_SIMULATION" to "TestSimulation",
                "TWIN_CACHE_HOST" to "this_is_a_host",
                "TWIN_CACHE_PORT" to "6973",
                "TWIN_CACHE_PASSWORD" to "this_is_a_password",
                "TWIN_CACHE_USERNAME" to "default",
                "CSM_ENTRYPOINT_LEGACY" to "true")
            .toSortedMap(),
        container.envVars?.toSortedMap())
  }
  @Test
  fun `CSMOrchestrator Container is not null`() {
    val container = this.buildCSMOrchestratorContainer()
    assertNotNull(container)
  }

  @Test
  fun `CSMOrchestrator Container name valid`() {
    val container = this.buildCSMOrchestratorContainer()
    assertEquals("CSMOrchestrator", container.name)
  }

  @Test
  fun `CSMOrchestrator Container env vars valid`() {
    val container = this.buildCSMOrchestratorContainer()
    this.validateCSMOrchestratorContainer(container)
  }

  @Test
  fun `Build all containers for a Scenario with csm-orc count`() {
    val scenario = getScenario()
    val datasets = listOf(getDataset())
    val connectors = listOf(getConnector())
    val workspace = getWorkspace()
    val solution = getCSMOrcSolution()
    val containers =
        factory.buildContainersPipeline(
            scenario,
            datasets,
            connectors,
            workspace,
            getOrganization(),
            solution,
            CSM_SIMULATION_ID,
            CSM_SIMULATION_ID,
            scenarioRunLabel = NODE_LABEL_DEFAULT,
            scenarioRunSizing = BASIC_SIZING)
    assertEquals(containers.size, 1)
  }
  @Test
  fun `Build all containers for a Scenario with csm-orc list`() {
    val scenario = getScenario()
    val datasets = listOf(getDataset())
    val connectors = listOf(getConnector())
    val workspace = getWorkspace()
    val solution = getCSMOrcSolution()
    val containers =
        factory.buildContainersPipeline(
            scenario,
            datasets,
            connectors,
            workspace,
            getOrganization(),
            solution,
            CSM_SIMULATION_ID,
            CSM_SIMULATION_ID,
            scenarioRunLabel = NODE_LABEL_DEFAULT,
            scenarioRunSizing = BASIC_SIZING)
    val expected =
        listOf(
            "CSMOrchestrator",
        )
    assertEquals(expected, containers.map { container -> container.name })
  }
  private fun buildApplyParametersContainer(): ScenarioRunContainer {
    return factory.buildApplyParametersContainer(
        getOrganization(),
        getWorkspace(),
        getScenario(),
        getSolution(),
        CSM_SIMULATION_ID,
        "testruntemplate",
        CSM_SIMULATION_ID,
        nodeSizingLabel = NODE_LABEL_DEFAULT,
        customSizing = BASIC_SIZING)
  }

  private fun buildValidateDataContainer(): ScenarioRunContainer {
    return factory.buildValidateDataContainer(
        getOrganization(),
        getWorkspace(),
        getScenario(),
        getSolution(),
        CSM_SIMULATION_ID,
        "testruntemplate",
        CSM_SIMULATION_ID,
        nodeSizingLabel = NODE_LABEL_DEFAULT,
        customSizing = BASIC_SIZING)
  }

  private fun buildPreRunContainer(): ScenarioRunContainer {
    return factory.buildPreRunContainer(
        getOrganization(),
        getWorkspace(),
        getScenario(),
        getSolution(),
        CSM_SIMULATION_ID,
        "testruntemplate",
        CSM_SIMULATION_ID,
        nodeSizingLabel = NODE_LABEL_DEFAULT,
        customSizing = BASIC_SIZING)
  }

  private fun buildRunContainer(
      dedicatedEventHubNamespace: Boolean? = null,
      sendToScenarioRun: Boolean? = null,
      sasAuthentication: String? = null,
      sasName: String? = null
  ): ScenarioRunContainer {
    return factory.buildRunContainer(
        getOrganization(),
        getWorkspace(dedicatedEventHubNamespace, sendToScenarioRun, sasAuthentication, sasName),
        getScenario(),
        getSolution(),
        CSM_SIMULATION_ID,
        "testruntemplate",
        CSM_SIMULATION_ID,
        nodeSizingLabel = NODE_LABEL_DEFAULT,
        customSizing = BASIC_SIZING)
  }

  private fun buildPostRunContainer(): ScenarioRunContainer {
    return factory.buildPostRunContainer(
        getOrganization(),
        getWorkspace(),
        getScenario(),
        getSolution(),
        CSM_SIMULATION_ID,
        "testruntemplate",
        CSM_SIMULATION_ID,
        nodeSizingLabel = NODE_LABEL_DEFAULT,
        customSizing = BASIC_SIZING)
  }

  private fun buildCSMOrchestratorContainer(): ScenarioRunContainer {
    return factory.buildCSMOrchestratorContainer(
        getOrganization(),
        getWorkspace(),
        getScenario(),
        getSolution(),
        CSM_SIMULATION_ID,
        "testruntemplate",
        CSM_SIMULATION_ID,
        nodeSizingLabel = NODE_LABEL_DEFAULT,
        customSizing = BASIC_SIZING)
  }

  private fun validateEnvVarsSolutionContainer(container: ScenarioRunContainer?, mode: String) {
    val expected =
        mapOf(
            "IDENTITY_PROVIDER" to "azure",
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to CSM_SIMULATION_ID,
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "organizationid-test",
            "CSM_ORGANIZATION_ID" to "Organizationid",
            "CSM_WORKSPACE_ID" to "Workspaceid",
            "CSM_SCENARIO_ID" to "Scenarioid",
            "CSM_SCENARIO_RUN_ID" to CSM_SIMULATION_ID,
            "CSM_RUN_TEMPLATE_ID" to "testruntemplate",
            "CSM_CONTAINER_MODE" to mode,
            "CSM_PROBES_MEASURES_TOPIC" to
                "amqps://csm-phoenix.servicebus.windows.net/organizationid-test",
            "CSM_CONTROL_PLANE_TOPIC" to
                "amqps://csm-phoenix.servicebus.windows.net/organizationid-test-scenariorun",
            "CSM_SIMULATION" to "TestSimulation",
            "TWIN_CACHE_HOST" to "this_is_a_host",
            "TWIN_CACHE_PORT" to "6973",
            "TWIN_CACHE_PASSWORD" to "this_is_a_password",
            "TWIN_CACHE_USERNAME" to "default",
            "CSM_ENTRYPOINT_LEGACY" to "true")
    assertEquals(expected.toSortedMap(), container?.envVars?.toSortedMap())
  }
  private fun validateCSMOrchestratorContainer(container: ScenarioRunContainer?) {
    val expected =
        mapOf(
            "IDENTITY_PROVIDER" to "azure",
            "AZURE_TENANT_ID" to "12345678",
            "AZURE_CLIENT_ID" to "98765432",
            "AZURE_CLIENT_SECRET" to "azertyuiop",
            "CSM_SIMULATION_ID" to CSM_SIMULATION_ID,
            "CSM_API_URL" to "https://api.cosmotech.com",
            "CSM_API_SCOPE" to "http://dev.api.cosmotech.com/.default",
            "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
            "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
            "AZURE_DATA_EXPLORER_RESOURCE_URI" to "https://phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                "https://ingest-phoenix.westeurope.kusto.windows.net",
            "AZURE_DATA_EXPLORER_DATABASE_NAME" to "organizationid-test",
            "CSM_ORGANIZATION_ID" to "Organizationid",
            "CSM_WORKSPACE_ID" to "Workspaceid",
            "CSM_SCENARIO_ID" to "Scenarioid",
            "CSM_SCENARIO_RUN_ID" to CSM_SIMULATION_ID,
            "CSM_RUN_TEMPLATE_ID" to "testruntemplate",
            "CSM_CONTAINER_MODE" to "csmOrc",
            "CSM_PROBES_MEASURES_TOPIC" to
                "amqps://csm-phoenix.servicebus.windows.net/organizationid-test",
            "CSM_CONTROL_PLANE_TOPIC" to
                "amqps://csm-phoenix.servicebus.windows.net/organizationid-test-scenariorun",
            "CSM_SIMULATION" to "TestSimulation",
            "TWIN_CACHE_HOST" to "this_is_a_host",
            "TWIN_CACHE_PORT" to "6973",
            "TWIN_CACHE_PASSWORD" to "this_is_a_password",
            "TWIN_CACHE_USERNAME" to "default",
            "CSM_ENTRYPOINT_LEGACY" to "false")
    assertEquals(expected.toSortedMap(), container?.envVars?.toSortedMap())
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
            mutableMapOf(
                "EnvParam1" to "%WORKSPACE_FILE%/workspace.env",
                "Param1" to "%WORKSPACE_FILE%/workspace.param",
            ))
  }

  private fun getDatasetConnectorWorkspaceStorage(): DatasetConnector {
    return DatasetConnector(
        id = "AzErTyUiOp",
        parametersValues =
            mutableMapOf(
                "EnvParam1" to "%STORAGE_CONNECTION_STRING%",
            ))
  }

  private fun getDatasetConnector(): DatasetConnector {
    return DatasetConnector(
        id = "AzErTyUiOp",
        parametersValues =
            mutableMapOf(
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
            mutableMapOf(
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
            mutableMapOf(
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
        ioTypes = listOf(IoTypesEnum.read),
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
        ioTypes = listOf(IoTypesEnum.read),
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
        ioTypes = listOf(IoTypesEnum.read),
        parameterGroups = listOf(parameterGroup))
  }

  private fun getConnectorNoVars(): Connector {
    return Connector(
        id = "QsDfGhJk",
        key = "TestConnector",
        name = "Test Connector",
        repository = "cosmotech/test_connector",
        version = "1.0.0",
        ioTypes = listOf(IoTypesEnum.read))
  }

  private fun getConnectorNoVarsManagedIdentity(): Connector {
    return Connector(
        id = "QsDfGhJk",
        key = "TestConnector",
        name = "Test Connector",
        repository = "cosmotech/test_connector",
        version = "1.0.0",
        ioTypes = listOf(IoTypesEnum.read),
        azureManagedIdentity = true)
  }

  private fun getDatasetConnectorNoVars(): DatasetConnector {
    return DatasetConnector(id = "QsDfGhJk")
  }

  private fun getDatasetNoVars(): Dataset {
    val connector = getDatasetConnectorNoVars()
    return Dataset(id = "1", name = "Test Dataset No Vars", connector = connector)
  }

  private fun getWorkspace(
      dedicatedEventHubNamespace: Boolean? = null,
      sendToScenarioRun: Boolean? = true,
      sasAuthentication: String? = null,
      sasName: String? = null
  ): Workspace {
    return Workspace(
        id = "Workspaceid",
        key = "Test",
        name = "Test Workspace",
        description = "Test Workspace Description",
        version = "1.0.0",
        solution =
            WorkspaceSolution(
                solutionId = "1",
            ),
        useDedicatedEventHubNamespace = dedicatedEventHubNamespace,
        sendScenarioRunToEventHub = sendToScenarioRun,
        dedicatedEventHubAuthenticationStrategy = sasAuthentication,
        dedicatedEventHubSasKeyName = sasName,
    )
  }

  private fun getWorkspaceNoSend(): Workspace {
    return Workspace(
        id = "Workspaceid",
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
        runTemplates = mutableListOf(getRunTemplate()),
    )
  }

  private fun getSolutionStack(): Solution {
    return Solution(
        id = "1",
        key = "TestSolution",
        name = "Test Solution",
        repository = "cosmotech/testsolution_simulator",
        version = "1.0.0",
        runTemplates = mutableListOf(getRunTemplateStack()),
    )
  }

  private fun getSolutionStackNoDWH(): Solution {
    return Solution(
        id = "1",
        key = "TestSolution",
        name = "Test Solution",
        repository = "cosmotech/testsolution_simulator",
        version = "1.0.0",
        runTemplates = mutableListOf(getRunTemplateStackNoDWH()),
    )
  }

  private fun getSolutionDatasetIds(): Solution {
    return Solution(
        id = "1",
        key = "TestSolution",
        name = "Test Solution",
        repository = "cosmotech/testsolution_simulator",
        version = "1.0.0",
        runTemplates = mutableListOf(getRunTemplateDatasetIds()),
        parameters =
            mutableListOf(
                RunTemplateParameter(
                    id = "param1",
                    labels = mutableMapOf("en" to "Parameter 1"),
                    varType = "string",
                ),
                RunTemplateParameter(
                    id = "param2",
                    labels = mutableMapOf("en" to "Parameter Dataset 2"),
                    varType = "%DATASETID%"),
                RunTemplateParameter(
                    id = "param3",
                    labels = mutableMapOf("en" to "Parameter Dataset 3"),
                    varType = "%DATASETID%"),
                RunTemplateParameter(
                    id = "param4",
                    labels = mutableMapOf("en" to "Parameter Dataset 4"),
                    varType = "%DATASETID%"),
                RunTemplateParameter(
                    id = "param5",
                    labels = mutableMapOf("en" to "Parameter 5"),
                    varType = "int",
                ),
            ),
        parameterGroups =
            mutableListOf(
                RunTemplateParameterGroup(
                    id = "group1",
                    labels = mutableMapOf("en" to "Parameter Group 1"),
                    parameters =
                        mutableListOf(
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
        runTemplates = mutableListOf(getRunTemplateNoPool()),
    )
  }

  private fun getSolutionNonePool(): Solution {
    return Solution(
        id = "1",
        key = "TestSolution",
        name = "Test Solution",
        repository = "cosmotech/testsolution_simulator",
        version = "1.0.0",
        runTemplates = mutableListOf(getRunTemplateNonePool()),
    )
  }

  private fun getSolutionOnlyRun(): Solution {
    return Solution(
        id = "1",
        key = "TestSolution",
        name = "Test Solution",
        repository = "cosmotech/testsolution_simulator",
        version = "1.0.0",
        runTemplates = mutableListOf(getRunTemplateOnlyRun()),
    )
  }

  private fun getSolutionCloudSources(): Solution {
    return Solution(
        id = "1",
        key = "TestSolution",
        name = "Test Solution",
        repository = "cosmotech/testsolution_simulator",
        version = "1.0.0",
        runTemplates = mutableListOf(getRunTemplateCloudSources()),
    )
  }

  private fun getSolutionLocalSources(): Solution {
    return Solution(
        id = "1",
        key = "TestSolution",
        name = "Test Solution",
        repository = "cosmotech/testsolution_simulator",
        version = "1.0.0",
        runTemplates = mutableListOf(getRunTemplateLocalSources()),
    )
  }
  private fun getCSMOrcSolution(): Solution {
    return Solution(
        id = "1",
        key = "TestSolution",
        name = "Test Solution",
        repository = "cosmotech/testsolution_simulator",
        version = "1.0.0",
        runTemplates = mutableListOf(getCSMOrcRunTemplate()),
    )
  }
  private fun getRunTemplate(): RunTemplate {
    return RunTemplate(
        id = "testruntemplate",
        name = "Test Run",
        csmSimulation = "TestSimulation",
        computeSize = "highcpupool",
        fetchDatasets = true,
        fetchScenarioParameters = true,
        applyParameters = true,
        validateData = true,
        run = true,
        sendDatasetsToDataWarehouse = true,
        sendInputParametersToDataWarehouse = true,
        preRun = true,
        postRun = true,
        orchestratorType = RunTemplateOrchestrator.argoWorkflow,
    )
  }

  private fun getRunTemplateStack(): RunTemplate {
    return RunTemplate(
        id = "testruntemplate",
        name = "Test Run",
        csmSimulation = "TestSimulation",
        computeSize = "highcpu",
        stackSteps = true,
        fetchDatasets = true,
        fetchScenarioParameters = true,
        applyParameters = true,
        validateData = true,
        run = true,
        sendDatasetsToDataWarehouse = true,
        sendInputParametersToDataWarehouse = true,
        preRun = true,
        postRun = true,
        orchestratorType = RunTemplateOrchestrator.argoWorkflow,
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
        fetchDatasets = true,
        fetchScenarioParameters = true,
        applyParameters = true,
        validateData = true,
        run = true,
        preRun = true,
        postRun = true,
        orchestratorType = RunTemplateOrchestrator.argoWorkflow,
    )
  }

  private fun getRunTemplateDatasetIds(): RunTemplate {
    return RunTemplate(
        id = "testruntemplate",
        name = "Test Run",
        csmSimulation = "TestSimulation",
        computeSize = "highcpu",
        parameterGroups = mutableListOf("group1"),
        fetchDatasets = true,
        fetchScenarioParameters = true,
        applyParameters = true,
        validateData = true,
        run = true,
        sendDatasetsToDataWarehouse = true,
        sendInputParametersToDataWarehouse = true,
        preRun = true,
        postRun = true,
        orchestratorType = RunTemplateOrchestrator.argoWorkflow,
    )
  }

  private fun getRunTemplateNoPool(): RunTemplate {
    return RunTemplate(
        id = "testruntemplate",
        name = "Test Run",
        csmSimulation = "TestSimulation",
        fetchDatasets = true,
        fetchScenarioParameters = true,
        applyParameters = true,
        validateData = true,
        run = true,
        sendDatasetsToDataWarehouse = true,
        sendInputParametersToDataWarehouse = true,
        preRun = true,
        postRun = true,
        orchestratorType = RunTemplateOrchestrator.argoWorkflow,
    )
  }

  private fun getRunTemplateNonePool(): RunTemplate {
    return RunTemplate(
        id = "testruntemplate",
        name = "Test Run",
        csmSimulation = "TestSimulation",
        computeSize = "%NONE%",
        fetchDatasets = true,
        fetchScenarioParameters = true,
        applyParameters = true,
        validateData = true,
        run = true,
        sendDatasetsToDataWarehouse = true,
        sendInputParametersToDataWarehouse = true,
        preRun = true,
        postRun = true,
        orchestratorType = RunTemplateOrchestrator.argoWorkflow,
    )
  }

  private fun getRunTemplateNoDatasetsSend(): RunTemplate {

    return RunTemplate(
        id = "testruntemplate",
        name = "Test Run",
        csmSimulation = "TestSimulation",
        sendDatasetsToDataWarehouse = false,
        fetchDatasets = true,
        fetchScenarioParameters = true,
        applyParameters = true,
        validateData = true,
        run = true,
        sendInputParametersToDataWarehouse = true,
        preRun = true,
        postRun = true,
        orchestratorType = RunTemplateOrchestrator.argoWorkflow,
    )
  }

  private fun getRunTemplateNoParametersSend(): RunTemplate {

    return RunTemplate(
        id = "testruntemplate",
        name = "Test Run",
        csmSimulation = "TestSimulation",
        sendInputParametersToDataWarehouse = false,
        fetchDatasets = true,
        fetchScenarioParameters = true,
        applyParameters = true,
        validateData = true,
        run = true,
        sendDatasetsToDataWarehouse = true,
        preRun = true,
        postRun = true,
        orchestratorType = RunTemplateOrchestrator.argoWorkflow,
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
        run = true,
        sendDatasetsToDataWarehouse = false,
        sendInputParametersToDataWarehouse = false,
        preRun = false,
        postRun = false,
        orchestratorType = RunTemplateOrchestrator.argoWorkflow,
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
        fetchDatasets = true,
        fetchScenarioParameters = true,
        applyParameters = true,
        validateData = true,
        run = true,
        sendDatasetsToDataWarehouse = true,
        sendInputParametersToDataWarehouse = true,
        preRun = true,
        postRun = true,
        orchestratorType = RunTemplateOrchestrator.argoWorkflow,
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
        fetchDatasets = true,
        fetchScenarioParameters = true,
        applyParameters = true,
        validateData = true,
        run = true,
        sendDatasetsToDataWarehouse = true,
        sendInputParametersToDataWarehouse = true,
        preRun = true,
        postRun = true,
        orchestratorType = RunTemplateOrchestrator.argoWorkflow,
    )
  }
  private fun getCSMOrcRunTemplate(): RunTemplate {
    return RunTemplate(
        id = "testruntemplate",
        name = "Test Run",
        csmSimulation = "TestSimulation",
        computeSize = "highcpupool",
        orchestratorType = RunTemplateOrchestrator.csmOrc,
    )
  }

  private fun getScenario(): Scenario {
    return Scenario(
        id = "Scenarioid",
        name = "Test Scenario",
        runTemplateId = "testruntemplate",
        datasetList = mutableListOf("1"),
    )
  }

  private fun getScenarioDatasetIds(): Scenario {
    return Scenario(
        id = "Scenarioid",
        name = "Test Scenario",
        runTemplateId = "testruntemplate",
        datasetList = mutableListOf("1"),
        parametersValues =
            mutableListOf(
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
        id = "Scenarioid",
        name = "Test Scenario",
        runTemplateId = "testruntemplate",
        datasetList = mutableListOf("1"),
        parametersValues =
            mutableListOf(
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
        id = "Scenarioid",
        name = "Test Scenario",
        runTemplateId = "testruntemplate",
        datasetList = mutableListOf("1"),
        parametersValues =
            mutableListOf(
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
        id = "Scenarioid",
        name = "Test Scenario",
        runTemplateId = "testruntemplate",
        datasetList = mutableListOf("1", "2"),
        parametersValues =
            mutableListOf(
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
        id = "Scenarioid",
        name = "Test Scenario",
        runTemplateId = "testruntemplate",
        datasetList = mutableListOf("1", "2", "3"),
    )
  }

  private fun getOrganization(): Organization {
    return Organization(
        id = "Organizationid",
        name = "Organization Test",
    )
  }
}
