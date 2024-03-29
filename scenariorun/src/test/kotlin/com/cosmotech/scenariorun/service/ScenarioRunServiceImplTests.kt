// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun.service

import com.cosmotech.api.azure.adx.AzureDataExplorerClient
import com.cosmotech.api.azure.eventhubs.AzureEventHubsClient
import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.events.CsmEventPublisher
import com.cosmotech.api.id.CsmIdGenerator
import com.cosmotech.api.rbac.CsmRbac
import com.cosmotech.api.utils.getCurrentAuthenticatedRoles
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.api.utils.getCurrentAuthentication
import com.cosmotech.organization.OrganizationApiServiceInterface
import com.cosmotech.organization.domain.Organization
import com.cosmotech.scenario.ScenarioApiServiceInterface
import com.cosmotech.scenariorun.ContainerFactory
import com.cosmotech.scenariorun.domain.ScenarioRun
import com.cosmotech.scenariorun.domain.ScenarioRunContainer
import com.cosmotech.scenariorun.domain.ScenarioRunSearch
import com.cosmotech.scenariorun.domain.ScenarioRunStartContainers
import com.cosmotech.scenariorun.repository.ScenarioRunRepository
import com.cosmotech.scenariorun.workflow.WorkflowService
import com.cosmotech.solution.SolutionApiServiceInterface
import com.cosmotech.solution.domain.Solution
import com.cosmotech.workspace.WorkspaceApiServiceInterface
import com.cosmotech.workspace.azure.IWorkspaceEventHubService
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceSolution
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import io.mockk.verify
import java.util.*
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication

private const val ORGANIZATION_ID = "O-AbCdEf123"
private const val WORKSPACE_ID = "W-AbCdEf123"
private const val SOLUTION_ID = "SOL-AbCdEf123"
private const val AUTHENTICATED_USERNAME = "authenticated-user"

@ExtendWith(MockKExtension::class)
class ScenarioRunServiceImplTests {

  @MockK private lateinit var organizationService: OrganizationApiServiceInterface
  @MockK(relaxed = true) private lateinit var containerFactory: ContainerFactory
  @MockK private lateinit var solutionService: SolutionApiServiceInterface
  @MockK private lateinit var workspaceService: WorkspaceApiServiceInterface
  @MockK private lateinit var idGenerator: CsmIdGenerator
  @Suppress("unused") @MockK private lateinit var csmPlatformProperties: CsmPlatformProperties

  @Suppress("unused")
  @MockK(relaxUnitFun = true)
  private lateinit var eventPublisher: CsmEventPublisher

  @MockK(relaxed = true) private lateinit var workflowService: WorkflowService

  @MockK(relaxed = true) private lateinit var scenarioApiService: ScenarioApiServiceInterface
  @MockK private lateinit var csmRbac: CsmRbac

  @MockK(relaxed = true) private lateinit var azureDataExplorerClient: AzureDataExplorerClient
  @MockK(relaxed = true) private lateinit var azureEventHubsClient: AzureEventHubsClient
  @MockK(relaxed = true) private lateinit var workspaceEventHubService: IWorkspaceEventHubService
  @MockK(relaxed = true) private lateinit var scenarioRunRepository: ScenarioRunRepository
  private lateinit var scenarioRunServiceImpl: ScenarioRunServiceImpl

  @BeforeTest
  fun setUp() {
    MockKAnnotations.init(this, relaxUnitFun = true)

    this.scenarioRunServiceImpl =
        spyk(
            ScenarioRunServiceImpl(
                containerFactory,
                workflowService,
                organizationService,
                workspaceService,
                scenarioApiService,
                azureDataExplorerClient,
                azureEventHubsClient,
                workspaceEventHubService,
                scenarioRunRepository,
                csmRbac))

    every { scenarioRunServiceImpl getProperty "idGenerator" } returns idGenerator
    every { scenarioRunServiceImpl getProperty "eventPublisher" } returns eventPublisher

    val csmPlatformPropertiesAzure = mockk<CsmPlatformProperties.CsmPlatformAzure>()
    every { csmPlatformProperties.azure } returns csmPlatformPropertiesAzure

    every { scenarioRunServiceImpl getProperty "csmPlatformProperties" } returns
        csmPlatformProperties

    val csmPlatformPropertiesTwincache = mockk<CsmPlatformProperties.CsmTwinCacheProperties>()
    every { csmPlatformProperties.twincache } returns csmPlatformPropertiesTwincache
    mockkStatic(::getCurrentAuthentication)
    val authentication = mockk<BearerTokenAuthentication>()

    every { getCurrentAuthentication() } returns authentication
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns AUTHENTICATED_USERNAME
    every { getCurrentAuthenticatedRoles(any()) } returns listOf()
  }

  @AfterTest
  fun tearDown() {
    unmockkStatic(::getCurrentAuthentication)
  }

  @Test
  fun `PROD-8473 - findScenarioRunById does not leak sensitive container data`() {
    val myScenarioRun =
        ScenarioRun(
            id = "sr-myscenariorun1",
            organizationId = ORGANIZATION_ID,
            workspaceId = WORKSPACE_ID,
            scenarioId = "scenario",
            workspaceKey = "my-workspaceKey",
            containers =
                listOf(
                    ScenarioRunContainer(
                        name = "my-container1",
                        envVars = mapOf("MY_SECRET_ENV_VAR" to "value"),
                        image = "my-image:latest")))

    every { scenarioRunRepository.findBy(any(), any()) } returns Optional.of(myScenarioRun)

    val scenarioRunById =
        this.scenarioRunServiceImpl.findScenarioRunById(ORGANIZATION_ID, myScenarioRun.id!!)
    assertNotNull(scenarioRunById)
    assertNotNull(scenarioRunById.id)
    assertEquals(myScenarioRun.id, scenarioRunById.id)
    assertNull(
        scenarioRunById.containers,
        "List of containers must be NULL for scenarioRun $scenarioRunById")
  }

  @Test
  fun `PROD-8473 - getScenarioRuns does not leak sensitive container data`() {
    val myScenarioRun1 =
        ScenarioRun(
            id = "sr-myscenariorun1",
            workspaceKey = "my-workspaceKey",
            containers =
                listOf(
                    ScenarioRunContainer(
                        name = "my-container11",
                        envVars = mapOf("MY_SECRET_ENV_VAR" to "value"),
                        image = "my-image:latest")))
    val myScenarioRun2 =
        ScenarioRun(
            id = "sr-myscenariorun2",
            workspaceKey = "my-workspaceKey",
            containers =
                listOf(
                    ScenarioRunContainer(
                        name = "my-container21",
                        envVars = mapOf("MY_SECRET_ENV_VAR" to "value", "DEBUG" to "true"),
                        image = "debian:11"),
                    ScenarioRunContainer(
                        name = "my-container22",
                        envVars = mapOf("KEY" to "value"),
                        image = "rhel:7")))

    every { scenarioRunRepository.findByScenarioId(any(), any(), any(), any()).toList() } returns
        listOf(myScenarioRun1, myScenarioRun2).toMutableList()
    every { csmPlatformProperties.twincache.scenariorun.defaultPageSize } returns 5

    val scenarioRuns =
        this.scenarioRunServiceImpl.getScenarioRuns(
            ORGANIZATION_ID, WORKSPACE_ID, "my-scenario-id", 0, 10)

    assertEquals(2, scenarioRuns.size)
    assertNotNull(scenarioRuns[0].id)
    for (scenarioRun in scenarioRuns) {
      assertNull(
          scenarioRun.containers, "List of containers must be NULL for scenarioRun $scenarioRun")
    }
  }

  @Test
  fun `PROD-8473 - getWorkspaceScenarioRuns does not leak sensitive container data`() {
    val myScenarioRun1 =
        ScenarioRun(
            id = "sr-myscenariorun1",
            workspaceKey = "my-workspaceKey",
            containers =
                listOf(
                    ScenarioRunContainer(
                        name = "my-container11",
                        envVars = mapOf("MY_SECRET_ENV_VAR" to "value"),
                        image = "my-image:latest")))
    val myScenarioRun2 =
        ScenarioRun(
            id = "sr-myscenariorun2",
            workspaceKey = "my-workspaceKey",
            containers =
                listOf(
                    ScenarioRunContainer(
                        name = "my-container21",
                        envVars = mapOf("MY_SECRET_ENV_VAR" to "value", "DEBUG" to "true"),
                        image = "debian:11"),
                    ScenarioRunContainer(
                        name = "my-container22",
                        envVars = mapOf("KEY" to "value"),
                        image = "rhel:7")))

    every { scenarioRunRepository.findByWorkspaceId(any(), any(), any()).toList() } returns
        listOf(myScenarioRun1, myScenarioRun2).toMutableList()
    every { csmPlatformProperties.twincache.scenariorun.defaultPageSize } returns 5
    every { workspaceService.findWorkspaceById(ORGANIZATION_ID, WORKSPACE_ID) } returns mockk()
    every { csmRbac.verify(any(), any(), any()) } returns Unit
    val scenarioRuns =
        this.scenarioRunServiceImpl.getWorkspaceScenarioRuns(ORGANIZATION_ID, WORKSPACE_ID, 0, 10)

    assertEquals(2, scenarioRuns.size)
    assertNotNull(scenarioRuns[0].id)
    for (scenarioRun in scenarioRuns) {
      assertNull(
          scenarioRun.containers, "List of containers must be NULL for scenarioRun $scenarioRun")
    }
  }

  @Test
  fun `PROD-8473 - searchScenarioRuns does not leak sensitive container data`() {
    val myScenarioRun1 =
        ScenarioRun(
            id = "sr-myscenariorun1",
            workspaceKey = "my-workspaceKey",
            containers =
                listOf(
                    ScenarioRunContainer(
                        name = "my-container11",
                        envVars = mapOf("MY_SECRET_ENV_VAR" to "value"),
                        image = "my-image:latest")))
    val myScenarioRun2 =
        ScenarioRun(
            id = "sr-myscenariorun2",
            workspaceKey = "my-workspaceKey",
            containers =
                listOf(
                    ScenarioRunContainer(
                        name = "my-container21",
                        envVars = mapOf("MY_SECRET_ENV_VAR" to "value", "DEBUG" to "true"),
                        image = "debian:11"),
                    ScenarioRunContainer(
                        name = "my-container22",
                        envVars = mapOf("KEY" to "value"),
                        image = "rhel:7")))
    every { organizationService.findOrganizationById(ORGANIZATION_ID) } returns
        Organization(id = ORGANIZATION_ID)
    every { csmPlatformProperties.twincache.scenariorun.defaultPageSize } returns 5
    every { scenarioRunRepository.findByPredicate(any(), any(), any()).toList() } returns
        listOf(myScenarioRun1, myScenarioRun2)

    var scenarioRunSearch = ScenarioRunSearch(ownerId = AUTHENTICATED_USERNAME)
    val scenarioRuns =
        this.scenarioRunServiceImpl.searchScenarioRuns(
            ORGANIZATION_ID, scenarioRunSearch, null, 100)

    assertEquals(2, scenarioRuns.size)
    assertNotNull(scenarioRuns[0].id)
    for (scenarioRun in scenarioRuns) {
      assertNull(
          scenarioRun.containers, "List of containers must be NULL for scenarioRun $scenarioRun")
    }
  }

  @Test
  fun `PROD-8473 - startScenarioRunContainers does not leak sensitive container data`() {

    val containers =
        listOf(
            ScenarioRunContainer(
                name = "my-container1",
                envVars = mapOf("MY_SECRET_ENV_VAR" to "value"),
                image = "my-image:latest"))
    val myScenarioRun =
        ScenarioRun(
            id = "sr-myscenariorun1",
            organizationId = ORGANIZATION_ID,
            workspaceId = WORKSPACE_ID,
            scenarioId = "s-myscenarioid",
            csmSimulationRun = "my-csm-simulationrun",
            workflowId = "my-workflow-id",
            workflowName = "my-workflow-name",
            containers = containers)
    every { workflowService.launchScenarioRun(any(), null) } returns myScenarioRun
    every { idGenerator.generate("scenariorun", "sr-") } returns myScenarioRun.id!!
    every { organizationService.findOrganizationById(ORGANIZATION_ID) } returns
        Organization(id = ORGANIZATION_ID)
    every { csmRbac.verify(any(), any(), any()) } returns Unit
    every { scenarioRunRepository.save(any()) } returns myScenarioRun

    val scenarioRun =
        this.scenarioRunServiceImpl.startScenarioRunContainers(
            ORGANIZATION_ID,
            ScenarioRunStartContainers(
                csmSimulationId = "my-csm-simulation-id", containers = containers))

    assertNotNull(scenarioRun)
    assertNotNull(scenarioRun.id)
    assertEquals(myScenarioRun.id, scenarioRun.id)
    assertNull(
        scenarioRun.containers, "List of containers must be NULL for scenarioRun $scenarioRun")
  }

  @Test
  fun `PROD-8473 - runScenario does not leak sensitive container data`() {
    every { organizationService.findOrganizationById(ORGANIZATION_ID) } returns mockk()
    val workspace = mockk<Workspace>()
    every { workspaceService.findWorkspaceById(ORGANIZATION_ID, WORKSPACE_ID) } returns workspace
    val workspaceSolution = mockk<WorkspaceSolution>()
    every { workspaceSolution.solutionId } returns SOLUTION_ID
    every { workspace.solution } returns workspaceSolution

    val solution = mockk<Solution>()
    every { solution.id } returns SOLUTION_ID
    every { solution.name } returns "test solution"
    every { solutionService.findSolutionById(ORGANIZATION_ID, SOLUTION_ID) } returns solution
    every { csmRbac.verify(any(), any(), any()) } returns Unit
    val myScenarioRun =
        ScenarioRun(
            id = "sr-myscenariorun1",
            organizationId = ORGANIZATION_ID,
            workspaceId = WORKSPACE_ID,
            scenarioId = "s-myscenarioid",
            csmSimulationRun = "my-csm-simulationrun",
            workflowId = "my-workflow-id",
            workflowName = "my-workflow-name",
            containers =
                listOf(
                    ScenarioRunContainer(
                        name = "my-container1",
                        envVars = mapOf("MY_SECRET_ENV_VAR" to "value"),
                        image = "my-image:latest")))
    every { workflowService.launchScenarioRun(any(), any()) } returns myScenarioRun
    every { idGenerator.generate("scenariorun", "sr-") } returns myScenarioRun.id!!
    val eventBus = mockk<CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus>()
    every { csmPlatformProperties.azure?.eventBus!! } returns eventBus
    every { workspace.key } returns "my-workspace-key"
    val authentication =
        mockk<CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication>()
    every { eventBus.authentication } returns authentication
    every { eventBus.authentication.strategy } returns
        CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication.Strategy
            .TENANT_CLIENT_CREDENTIALS
    every { workspace.sendScenarioMetadataToEventHub } returns false

    every { scenarioRunRepository.save(any()) } returns myScenarioRun

    val scenarioRunById =
        this.scenarioRunServiceImpl.runScenario(ORGANIZATION_ID, WORKSPACE_ID, "s-myscenarioid")

    assertNotNull(scenarioRunById)
    assertNotNull(scenarioRunById.id)
    assertEquals(myScenarioRun.id, scenarioRunById.id)
    assertNull(
        scenarioRunById.containers,
        "List of containers must be NULL for scenarioRun $scenarioRunById")
  }

  @Test
  fun `PROD-8148 - deleteDataFromADXbyExtentShard is called once`() {
    val scenarioRun = mockk<ScenarioRun>()
    val organizationId = "orgId"
    val workspaceKey = "wk"
    val simulationRunId = "csmSimulationRun"
    every { scenarioRunServiceImpl.findScenarioRunById(organizationId, "scenariorunId") } returns
        scenarioRun
    every { scenarioRun.id } returns "scenariorunId"
    every { scenarioRun.ownerId } returns "ownerId"
    every { scenarioRun.organizationId } returns organizationId
    every { scenarioRun.workspaceKey } returns workspaceKey
    every { scenarioRun.workspaceId } returns "workspaceId"
    every { scenarioRun.csmSimulationRun } returns simulationRunId
    every { scenarioRun.scenarioId } returns "scenarioId"
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "ownerId"
    every { csmRbac.verify(any(), any(), any()) } returns Unit
    scenarioRunServiceImpl.deleteScenarioRun("orgId", "scenariorunId")
    verify(exactly = 1) {
      azureDataExplorerClient.deleteDataFromADXbyExtentShard(
          organizationId, workspaceKey, simulationRunId)
    }
  }
}
