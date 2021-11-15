// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun.azure

import com.azure.cosmos.CosmosClient
import com.azure.cosmos.CosmosContainer
import com.azure.cosmos.CosmosDatabase
import com.azure.cosmos.models.CosmosItemResponse
import com.azure.cosmos.models.PartitionKey
import com.azure.cosmos.models.SqlQuerySpec
import com.azure.cosmos.util.CosmosPagedIterable
import com.azure.spring.data.cosmos.core.CosmosTemplate
import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.events.CsmEventPublisher
import com.cosmotech.api.id.CsmIdGenerator
import com.cosmotech.api.utils.getCurrentAuthentication
import com.cosmotech.api.utils.objectMapper
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.scenariorun.ContainerFactory
import com.cosmotech.scenariorun.domain.ScenarioRun
import com.cosmotech.scenariorun.domain.ScenarioRunContainer
import com.cosmotech.scenariorun.domain.ScenarioRunSearch
import com.cosmotech.scenariorun.domain.ScenarioRunStartContainers
import com.cosmotech.scenariorun.workflow.WorkflowService
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.solution.domain.Solution
import com.cosmotech.workspace.api.WorkspaceApiService
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceSolution
import com.fasterxml.jackson.databind.JsonNode
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.security.core.Authentication

private const val ORGANIZATION_ID = "O-AbCdEf123"
private const val WORKSPACE_ID = "W-AbCdEf123"
private const val SOLUTION_ID = "SOL-AbCdEf123"
private const val AUTHENTICATED_USERNAME = "authenticated-user"

private val objectMapper = objectMapper()

@ExtendWith(MockKExtension::class)
class ScenarioRunServiceImplTests {

  @MockK private lateinit var organizationService: OrganizationApiService
  @MockK(relaxed = true) private lateinit var containerFactory: ContainerFactory
  @MockK private lateinit var solutionService: SolutionApiService
  @MockK private lateinit var workspaceService: WorkspaceApiService
  @MockK private lateinit var idGenerator: CsmIdGenerator
  @Suppress("unused") @MockK(relaxed = true) private lateinit var cosmosTemplate: CosmosTemplate
  @Suppress("unused") @MockK private lateinit var cosmosClient: CosmosClient
  @Suppress("unused") @MockK(relaxed = true) private lateinit var cosmosCoreDatabase: CosmosDatabase
  @Suppress("unused") @MockK private lateinit var csmPlatformProperties: CsmPlatformProperties

  @Suppress("unused")
  @MockK(relaxUnitFun = true)
  private lateinit var eventPublisher: CsmEventPublisher

  @MockK(relaxed = true) private lateinit var workflowService: WorkflowService

  private lateinit var scenarioRunServiceImpl: ScenarioRunServiceImpl

  @BeforeTest
  fun setUp() {
    MockKAnnotations.init(this, relaxUnitFun = true)

    this.scenarioRunServiceImpl = spyk(ScenarioRunServiceImpl(containerFactory, workflowService))

    every { scenarioRunServiceImpl getProperty "cosmosTemplate" } returns cosmosTemplate
    every { scenarioRunServiceImpl getProperty "cosmosClient" } returns cosmosClient
    every { scenarioRunServiceImpl getProperty "idGenerator" } returns idGenerator
    every { scenarioRunServiceImpl getProperty "eventPublisher" } returns eventPublisher

    val csmPlatformPropertiesAzure = mockk<CsmPlatformProperties.CsmPlatformAzure>()
    val csmPlatformPropertiesAzureCosmos =
        mockk<CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureCosmos>()
    val csmPlatformPropertiesAzureCosmosCoreDatabase =
        mockk<CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureCosmos.CoreDatabase>()
    every { csmPlatformPropertiesAzureCosmosCoreDatabase.name } returns "test-db"
    every { csmPlatformPropertiesAzureCosmos.coreDatabase } returns
        csmPlatformPropertiesAzureCosmosCoreDatabase
    every { csmPlatformPropertiesAzure.cosmos } returns csmPlatformPropertiesAzureCosmos
    every { csmPlatformProperties.azure } returns csmPlatformPropertiesAzure

    every { cosmosClient.getDatabase("test-db") } returns cosmosCoreDatabase

    every { scenarioRunServiceImpl getProperty "csmPlatformProperties" } returns
        csmPlatformProperties

    mockkStatic(::getCurrentAuthentication)
    val authentication = mockk<Authentication>()
    every { authentication.name } returns AUTHENTICATED_USERNAME
    every { getCurrentAuthentication() } returns authentication

    scenarioRunServiceImpl.init()
  }

  @AfterTest
  fun tearDown() {
    unmockkStatic(::getCurrentAuthentication)
  }

  @Test
  fun `PROD-8473 - findScenarioRunById does not leak sensitive container data`() {
    val cosmosContainer = mockk<CosmosContainer>(relaxed = true)
    every { cosmosCoreDatabase.getContainer("${ORGANIZATION_ID}_scenario_data") } returns
        cosmosContainer
    val myScenarioRun =
        ScenarioRun(
            id = "sr-myscenariorun1",
            workspaceKey = "my-workspaceKey",
            containers =
                listOf(
                    ScenarioRunContainer(
                        name = "my-container1",
                        envVars = mapOf("MY_SECRET_ENV_VAR" to "value"),
                        image = "my-image:latest")))
    val queryResponse = mockk<CosmosPagedIterable<JsonNode>>()
    every { queryResponse.iterator() } returns
        listOf(myScenarioRun)
            .map { objectMapper.valueToTree<JsonNode>(it) }
            .toMutableList()
            .iterator()
    every {
      cosmosContainer.queryItems(any<SqlQuerySpec>(), any(), eq(JsonNode::class.java))
    } returns queryResponse

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
    val cosmosContainer = mockk<CosmosContainer>(relaxed = true)
    every { cosmosCoreDatabase.getContainer("${ORGANIZATION_ID}_scenario_data") } returns
        cosmosContainer
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
    val queryResponse = mockk<CosmosPagedIterable<JsonNode>>()
    every { queryResponse.iterator() } returns
        listOf(myScenarioRun1, myScenarioRun2)
            .map { objectMapper.valueToTree<JsonNode>(it) }
            .toMutableList()
            .iterator()
    every {
      cosmosContainer.queryItems(any<SqlQuerySpec>(), any(), eq(JsonNode::class.java))
    } returns queryResponse

    val scenarioRuns =
        this.scenarioRunServiceImpl.getScenarioRuns(ORGANIZATION_ID, WORKSPACE_ID, "my-scenario-id")

    assertEquals(2, scenarioRuns.size)
    assertNotNull(scenarioRuns[0].id)
    for (scenarioRun in scenarioRuns) {
      assertNull(
          scenarioRun.containers, "List of containers must be NULL for scenarioRun $scenarioRun")
    }
  }

  @Test
  fun `PROD-8473 - getWorkspaceScenarioRuns does not leak sensitive container data`() {
    val cosmosContainer = mockk<CosmosContainer>(relaxed = true)
    every { cosmosCoreDatabase.getContainer("${ORGANIZATION_ID}_scenario_data") } returns
        cosmosContainer
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
    val queryResponse = mockk<CosmosPagedIterable<JsonNode>>()
    every { queryResponse.iterator() } returns
        listOf(myScenarioRun1, myScenarioRun2)
            .map { objectMapper.valueToTree<JsonNode>(it) }
            .toMutableList()
            .iterator()
    every {
      cosmosContainer.queryItems(any<SqlQuerySpec>(), any(), eq(JsonNode::class.java))
    } returns queryResponse

    val scenarioRuns =
        this.scenarioRunServiceImpl.getWorkspaceScenarioRuns(ORGANIZATION_ID, WORKSPACE_ID)

    assertEquals(2, scenarioRuns.size)
    assertNotNull(scenarioRuns[0].id)
    for (scenarioRun in scenarioRuns) {
      assertNull(
          scenarioRun.containers, "List of containers must be NULL for scenarioRun $scenarioRun")
    }
  }

  @Test
  fun `PROD-8473 - searchScenarioRuns does not leak sensitive container data`() {
    val cosmosContainer = mockk<CosmosContainer>(relaxed = true)
    every { cosmosCoreDatabase.getContainer("${ORGANIZATION_ID}_scenario_data") } returns
        cosmosContainer
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
    val queryResponse = mockk<CosmosPagedIterable<JsonNode>>()
    every { queryResponse.iterator() } returns
        listOf(myScenarioRun1, myScenarioRun2)
            .map { objectMapper.valueToTree<JsonNode>(it) }
            .toMutableList()
            .iterator()
    every {
      cosmosContainer.queryItems(any<SqlQuerySpec>(), any(), eq(JsonNode::class.java))
    } returns queryResponse

    val scenarioRuns =
        this.scenarioRunServiceImpl.searchScenarioRuns(ORGANIZATION_ID, ScenarioRunSearch())

    assertEquals(2, scenarioRuns.size)
    assertNotNull(scenarioRuns[0].id)
    for (scenarioRun in scenarioRuns) {
      assertNull(
          scenarioRun.containers, "List of containers must be NULL for scenarioRun $scenarioRun")
    }
  }

  @Test
  fun `PROD-8473 - startScenarioRunContainers does not leak sensitive container data`() {
    val cosmosContainer = mockk<CosmosContainer>(relaxed = true)
    every { cosmosCoreDatabase.getContainer("${ORGANIZATION_ID}_scenario_data") } returns
        cosmosContainer
    val cosmosItemResponse = mockk<CosmosItemResponse<Map<*, *>>>()
    every {
      cosmosContainer.createItem(ofType(Map::class), PartitionKey(AUTHENTICATED_USERNAME), any())
    } returns cosmosItemResponse
    // The implementation in ScenarioRunServiceImpl only needs to know if the response item is null
    // or
    // not
    every { cosmosItemResponse.item } returns mapOf<String, Any>()

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
    every { workflowService.launchScenarioRun(any()) } returns myScenarioRun
    every { idGenerator.generate("scenariorun", "sr-") } returns myScenarioRun.id!!

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

    val cosmosContainer = mockk<CosmosContainer>(relaxed = true)
    every { cosmosCoreDatabase.getContainer("${ORGANIZATION_ID}_scenario_data") } returns
        cosmosContainer
    val cosmosItemResponse = mockk<CosmosItemResponse<Map<*, *>>>()
    every {
      cosmosContainer.createItem(ofType(Map::class), PartitionKey(AUTHENTICATED_USERNAME), any())
    } returns cosmosItemResponse
    // The implementation in ScenarioRunServiceImpl only needs to know if the response item is null
    // or
    // not
    every { cosmosItemResponse.item } returns mapOf<String, Any>()

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
    every { workflowService.launchScenarioRun(any()) } returns myScenarioRun
    every { idGenerator.generate("scenariorun", "sr-") } returns myScenarioRun.id!!

    val scenarioRunById =
        this.scenarioRunServiceImpl.runScenario(ORGANIZATION_ID, WORKSPACE_ID, "s-myscenarioid")

    assertNotNull(scenarioRunById)
    assertNotNull(scenarioRunById.id)
    assertEquals(myScenarioRun.id, scenarioRunById.id)
    assertNull(
        scenarioRunById.containers,
        "List of containers must be NULL for scenarioRun $scenarioRunById")
  }
}
