// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.config.CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureCredentials
import com.cosmotech.api.config.CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureCredentials.CsmPlatformAzureCredentialsCore
import com.cosmotech.api.config.CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus
import com.cosmotech.api.containerregistry.ContainerRegistryService
import com.cosmotech.api.utils.SecretManager
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.domain.Organization
import com.cosmotech.run.domain.ContainerResourceSizeInfo
import com.cosmotech.run.domain.ContainerResourceSizing
import com.cosmotech.run.domain.RunContainer
import com.cosmotech.run.service.WORKFLOW_TYPE_RUN
import com.cosmotech.runner.api.RunnerApiService
import com.cosmotech.runner.domain.Runner
import com.cosmotech.runner.domain.RunnerRunTemplateParameterValue
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.solution.domain.RunTemplate
import com.cosmotech.solution.domain.RunTemplateOrchestrator
import com.cosmotech.solution.domain.Solution
import com.cosmotech.workspace.api.WorkspaceApiService
import com.cosmotech.workspace.azure.IWorkspaceEventHubService
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
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.extension.ExtendWith

private const val CSM_SIMULATION_ID = "simulationrunid"
private const val CSM_RUN_TEMPLATE_ID = "testruntemplate"

@ExtendWith(MockKExtension::class)
class ContainerFactoryTests {

  @MockK(relaxed = true) private lateinit var azure: CsmPlatformProperties.CsmPlatformAzure
  @MockK(relaxed = true) private lateinit var csmPlatformProperties: CsmPlatformProperties

  @MockK private lateinit var runnerService: RunnerApiService
  @MockK private lateinit var workspaceService: WorkspaceApiService
  @MockK private lateinit var solutionService: SolutionApiService
  @MockK private lateinit var organizationService: OrganizationApiService
  @MockK private lateinit var secretManager: SecretManager
  @MockK private lateinit var containerRegistryService: ContainerRegistryService
  private lateinit var workspaceEventHubService: IWorkspaceEventHubService

  private lateinit var factory: RunContainerFactory

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
        RunContainerFactory(
            csmPlatformProperties,
            runnerService,
            workspaceService,
            solutionService,
            organizationService,
            workspaceEventHubService,
            containerRegistryService)
  }

  @Test
  fun `Container is created with correct content`() {
    val runner = getRunner()
    val organization = getOrganization()
    val workspace = getWorkspace()
    val solution = getSolution()
    val runTemplate = getRunTemplate()

    val containerStart =
        factory.buildContainersStart(
            runner = runner,
            workspace = workspace,
            organization = organization,
            solution = getSolution(),
            runId = CSM_SIMULATION_ID,
            csmSimulationId = CSM_SIMULATION_ID,
            workflowType = WORKFLOW_TYPE_RUN)

    val run_container =
        getRunContainer(solution, organization, workspace, runner, runTemplate, CSM_SIMULATION_ID)

    assertNotNull(containerStart)
    assertEquals(1, containerStart.containers.size)
    assertEquals(run_container, containerStart.containers[0])
  }

  private fun getRunContainer(
      solution: Solution,
      organization: Organization,
      workspace: Workspace,
      runner: Runner,
      runTemplate: RunTemplate,
      runId: String
  ): RunContainer {
    return RunContainer(
        name = "CSMOrchestrator",
        image = "twinengines.azurecr.io/" + solution.repository + ":" + solution.version,
        envVars =
            mapOf(
                "AZURE_TENANT_ID" to azure.credentials.core.tenantId,
                "AZURE_CLIENT_ID" to azure.credentials.core.clientId,
                "AZURE_CLIENT_SECRET" to azure.credentials.core.clientSecret,
                "IDENTITY_PROVIDER" to "azure",
                "CSM_API_URL" to csmPlatformProperties.api.baseUrl,
                "CSM_API_SCOPE" to azure.appIdUri + "/.default",
                "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
                "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
                "TWIN_CACHE_HOST" to csmPlatformProperties.twincache.host,
                "TWIN_CACHE_PORT" to csmPlatformProperties.twincache.port,
                "TWIN_CACHE_PASSWORD" to csmPlatformProperties.twincache.password,
                "TWIN_CACHE_USERNAME" to csmPlatformProperties.twincache.username,
                "CSM_SIMULATION_ID" to CSM_SIMULATION_ID,
                "AZURE_DATA_EXPLORER_RESOURCE_URI" to azure.dataWarehouseCluster.baseUri,
                "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI" to
                    azure.dataWarehouseCluster.options.ingestionUri,
                "AZURE_DATA_EXPLORER_DATABASE_NAME" to organization.id!!.lowercase() + "-test",
                "CSM_ORGANIZATION_ID" to organization.id!!,
                "CSM_WORKSPACE_ID" to workspace.id!!,
                "CSM_RUNNER_ID" to runner.id!!,
                "CSM_RUN_ID" to runId,
                "CSM_RUN_TEMPLATE_ID" to CSM_RUN_TEMPLATE_ID,
                "CSM_CONTAINER_MODE" to RunTemplateOrchestrator.csmOrc.value,
                "CSM_ENTRYPOINT_LEGACY" to "false",
                "CSM_PROBES_MEASURES_TOPIC" to
                    azure.eventBus.baseUri + "/" + organization.id!!.lowercase() + "-test",
                "CSM_CONTROL_PLANE_TOPIC" to
                    azure.eventBus.baseUri +
                        "/" +
                        organization.id!!.lowercase() +
                        "-test-scenariorun",
                "CSM_SIMULATION" to runTemplate.csmSimulation!!),
        entrypoint = "entrypoint.py",
        nodeLabel = runTemplate.computeSize!!.removeSuffix("pool"),
        runSizing =
            ContainerResourceSizing(
                requests = ContainerResourceSizeInfo(cpu = "70", memory = "130Gi"),
                limits = ContainerResourceSizeInfo(cpu = "70", memory = "130Gi")),
        solutionContainer = true)
  }

  private fun getRunner(): Runner {
    return Runner(
        id = "RunnerId",
        name = "TestRunner",
        runTemplateId = CSM_RUN_TEMPLATE_ID,
        datasetList = mutableListOf("1", "2"),
        parametersValues =
            mutableListOf(
                RunnerRunTemplateParameterValue(parameterId = "param1", value = "value1"),
                RunnerRunTemplateParameterValue(parameterId = "param2", value = "value2")))
  }

  private fun getRunTemplate(): RunTemplate {
    return RunTemplate(
        id = CSM_RUN_TEMPLATE_ID,
        name = "Test Run",
        csmSimulation = "TestSimulation",
        computeSize = "highcpupool",
        orchestratorType = RunTemplateOrchestrator.argoWorkflow,
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

  private fun getOrganization(): Organization {
    return Organization(
        id = "Organizationid",
        name = "Organization Test",
    )
  }
}
