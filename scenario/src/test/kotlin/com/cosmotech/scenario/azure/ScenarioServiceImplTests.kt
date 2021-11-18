// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenario.azure

import com.azure.cosmos.CosmosClient
import com.azure.cosmos.CosmosContainer
import com.azure.cosmos.CosmosDatabase
import com.azure.cosmos.models.CosmosItemResponse
import com.azure.cosmos.models.PartitionKey
import com.azure.spring.data.cosmos.core.CosmosTemplate
import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.events.CsmEvent
import com.cosmotech.api.events.CsmEventPublisher
import com.cosmotech.api.events.ScenarioRunEndToEndStateRequest
import com.cosmotech.api.events.WorkflowStatusRequest
import com.cosmotech.api.id.CsmIdGenerator
import com.cosmotech.api.utils.getCurrentAuthentication
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.scenario.domain.Scenario
import com.cosmotech.scenario.domain.ScenarioJobState
import com.cosmotech.scenario.domain.ScenarioLastRun
import com.cosmotech.scenario.domain.ScenarioRunTemplateParameterValue
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.solution.domain.RunTemplate
import com.cosmotech.solution.domain.RunTemplateParameterGroup
import com.cosmotech.solution.domain.Solution
import com.cosmotech.user.api.UserApiService
import com.cosmotech.workspace.api.WorkspaceApiService
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceSolution
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import java.time.Duration
import java.util.stream.Stream
import kotlin.test.*
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import org.springframework.security.core.Authentication

const val ORGANIZATION_ID = "O-AbCdEf123"
const val WORKSPACE_ID = "W-AbCdEf123"
const val SOLUTION_ID = "SOL-AbCdEf123"
const val AUTHENTICATED_USERNAME = "authenticated-user"

@ExtendWith(MockKExtension::class)
@Suppress("LongMethod", "LargeClass")
class ScenarioServiceImplTests {

  @MockK private lateinit var userService: UserApiService
  @MockK private lateinit var organizationService: OrganizationApiService
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

  private lateinit var scenarioServiceImpl: ScenarioServiceImpl

  @BeforeTest
  fun setUp() {
    MockKAnnotations.init(this, relaxUnitFun = true)

    this.scenarioServiceImpl =
        spyk(
            ScenarioServiceImpl(
                userService, solutionService, organizationService, workspaceService),
            recordPrivateCalls = true)

    every { scenarioServiceImpl getProperty "cosmosTemplate" } returns cosmosTemplate
    every { scenarioServiceImpl getProperty "cosmosClient" } returns cosmosClient
    every { scenarioServiceImpl getProperty "idGenerator" } returns idGenerator
    every { scenarioServiceImpl getProperty "eventPublisher" } returns eventPublisher

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

    every { scenarioServiceImpl getProperty "csmPlatformProperties" } returns csmPlatformProperties

    mockkStatic(::getCurrentAuthentication)
    val authentication = mockk<Authentication>()
    every { authentication.name } returns AUTHENTICATED_USERNAME
    every { getCurrentAuthentication() } returns authentication

    scenarioServiceImpl.init()
  }

  @AfterTest
  fun tearDown() {
    unmockkStatic(::getCurrentAuthentication)
  }

  @Test
  fun `PROD-7687 - should initialize Child Scenario Parameters values with parent ones`() {
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
    val runTemplate1 = mockk<RunTemplate>()
    every { runTemplate1.id } returns "runTemplate1_id"
    every { runTemplate1.name } returns "runTemplate1 name"
    every { runTemplate1.parameterGroups } returns
        listOf("parameter_group_11", "parameter_group_12")

    val runTemplate2 = mockk<RunTemplate>()
    every { runTemplate2.id } returns "runTemplate2_id"
    every { runTemplate2.id } returns "runTemplate2 name"
    every { runTemplate2.parameterGroups } returns listOf("parameter_group_21")

    every { solution.runTemplates } returns listOf(runTemplate1, runTemplate2)

    val parameterGroup11 = mockk<RunTemplateParameterGroup>()
    every { parameterGroup11.id } returns "parameter_group_11"
    every { parameterGroup11.parameters } returns
        listOf("parameter_group_11_parameter1", "parameter_group_11_parameter2")
    val parameterGroup12 = mockk<RunTemplateParameterGroup>()
    every { parameterGroup12.id } returns "parameter_group_12"
    every { parameterGroup12.parameters } returns listOf("parameter_group_12_parameter1")
    val parameterGroup21 = mockk<RunTemplateParameterGroup>()
    every { parameterGroup21.id } returns "parameter_group_21"
    every { parameterGroup21.parameters } returns emptyList()

    every { solution.parameterGroups } returns
        listOf(parameterGroup11, parameterGroup12, parameterGroup21)

    val parentScenarioId = "S-parent"
    val parentScenario = mockk<Scenario>(relaxed = true)
    every { parentScenario.id } returns parentScenarioId
    every { parentScenario.parametersValues } returns
        listOf(
            ScenarioRunTemplateParameterValue(
                parameterId = "parameter_group_11_parameter1",
                value = "parameter_group_11_parameter1_value"))

    every {
      scenarioServiceImpl.findScenarioByIdNoState(ORGANIZATION_ID, WORKSPACE_ID, parentScenarioId)
    } returns parentScenario

    every { idGenerator.generate("scenario") } returns "S-myScenarioId"

    val cosmosContainer = mockk<CosmosContainer>()
    every { cosmosCoreDatabase.getContainer("${ORGANIZATION_ID}_scenario_data") } returns
        cosmosContainer
    val cosmosItemResponse = mockk<CosmosItemResponse<Map<*, *>>>()
    every {
      cosmosContainer.createItem(ofType(Map::class), PartitionKey(AUTHENTICATED_USERNAME), any())
    } returns cosmosItemResponse
    // The implementation in ScenarioServiceImpl only needs to know if the response item is null or
    // not
    every { cosmosItemResponse.item } returns mapOf<String, Any>()

    val scenarioCreated =
        scenarioServiceImpl.createScenario(
            ORGANIZATION_ID,
            WORKSPACE_ID,
            Scenario(
                solutionId = SOLUTION_ID,
                parentId = parentScenarioId,
                runTemplateId = "runTemplate1_id",
                parametersValues =
                    listOf(
                        ScenarioRunTemplateParameterValue(
                            parameterId = "parameter_group_12_parameter1",
                            value = "parameter_group_12_parameter1_value"))))

    assertEquals("S-myScenarioId", scenarioCreated.id)
    assertEquals(AUTHENTICATED_USERNAME, scenarioCreated.ownerId)
    // PROD-7687 : child scenario parameters values must be initialized with those of the parent
    val parametersValues = scenarioCreated.parametersValues
    assertNotNull(parametersValues)
    assertEquals(
        2, parametersValues.size, "Child Scenario parameters values is not of the right size")
    val paramGrp21 = parametersValues.filter { it.parameterId == "parameter_group_12_parameter1" }
    assertEquals(1, paramGrp21.size)
    assertEquals("parameter_group_12_parameter1_value", paramGrp21[0].value)
    assertNull(paramGrp21[0].isInherited)

    val paramGrp11 = parametersValues.filter { it.parameterId == "parameter_group_11_parameter1" }
    assertEquals(1, paramGrp11.size)
    assertEquals("parameter_group_11_parameter1_value", paramGrp11[0].value)
    assertNotNull(paramGrp11[0].isInherited)
    assertTrue { paramGrp11[0].isInherited!! }
  }

  @Test
  fun `PROD-7687 - No Parameters Values inherited if no parentId defined`() {
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
    val runTemplate1 = mockk<RunTemplate>()
    every { runTemplate1.id } returns "runTemplate1_id"
    every { runTemplate1.name } returns "runTemplate1 name"
    every { runTemplate1.parameterGroups } returns
        listOf("parameter_group_11", "parameter_group_12")

    val runTemplate2 = mockk<RunTemplate>()
    every { runTemplate2.id } returns "runTemplate2_id"
    every { runTemplate2.id } returns "runTemplate2 name"
    every { runTemplate2.parameterGroups } returns listOf("parameter_group_21")

    every { solution.runTemplates } returns listOf(runTemplate1, runTemplate2)

    val parameterGroup11 = mockk<RunTemplateParameterGroup>()
    every { parameterGroup11.id } returns "parameter_group_11"
    every { parameterGroup11.parameters } returns
        listOf("parameter_group_11_parameter1", "parameter_group_11_parameter2")
    val parameterGroup12 = mockk<RunTemplateParameterGroup>()
    every { parameterGroup12.id } returns "parameter_group_12"
    every { parameterGroup12.parameters } returns listOf("parameter_group_12_parameter1")
    val parameterGroup21 = mockk<RunTemplateParameterGroup>()
    every { parameterGroup21.id } returns "parameter_group_21"
    every { parameterGroup21.parameters } returns emptyList()

    every { solution.parameterGroups } returns
        listOf(parameterGroup11, parameterGroup12, parameterGroup21)

    every { idGenerator.generate("scenario") } returns "S-myScenarioId"

    val cosmosContainer = mockk<CosmosContainer>()
    every { cosmosCoreDatabase.getContainer("${ORGANIZATION_ID}_scenario_data") } returns
        cosmosContainer
    val cosmosItemResponse = mockk<CosmosItemResponse<Map<*, *>>>()
    every {
      cosmosContainer.createItem(ofType(Map::class), PartitionKey(AUTHENTICATED_USERNAME), any())
    } returns cosmosItemResponse
    // The implementation in ScenarioServiceImpl only needs to know if the response item is null or
    // not
    every { cosmosItemResponse.item } returns mapOf<String, Any>()

    val scenarioCreated =
        scenarioServiceImpl.createScenario(
            ORGANIZATION_ID,
            WORKSPACE_ID,
            Scenario(
                solutionId = SOLUTION_ID,
                runTemplateId = "runTemplate1_id",
                parametersValues =
                    listOf(
                        ScenarioRunTemplateParameterValue(
                            parameterId = "parameter_group_12_parameter1",
                            value = "parameter_group_12_parameter1_value"))))

    assertEquals("S-myScenarioId", scenarioCreated.id)
    assertEquals(AUTHENTICATED_USERNAME, scenarioCreated.ownerId)
    // PROD-7687 : no parentId => child scenario parameters values kept as is
    val parametersValues = scenarioCreated.parametersValues
    assertNotNull(parametersValues)
    assertEquals(
        1, parametersValues.size, "Child Scenario parameters values is not of the right size")
    assertEquals("parameter_group_12_parameter1", parametersValues[0].parameterId)
    assertEquals("parameter_group_12_parameter1_value", parametersValues[0].value)
    assertNull(parametersValues[0].isInherited)
  }

  @Test
  fun `PROD-7687 - Child Scenario Parameters values take precedence over the parent ones`() {
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
    val runTemplate1 = mockk<RunTemplate>()
    every { runTemplate1.id } returns "runTemplate1_id"
    every { runTemplate1.name } returns "runTemplate1 name"
    every { runTemplate1.parameterGroups } returns
        listOf("parameter_group_11", "parameter_group_12")

    val runTemplate2 = mockk<RunTemplate>()
    every { runTemplate2.id } returns "runTemplate2_id"
    every { runTemplate2.id } returns "runTemplate2 name"
    every { runTemplate2.parameterGroups } returns listOf("parameter_group_21")

    every { solution.runTemplates } returns listOf(runTemplate1, runTemplate2)

    val parameterGroup11 = mockk<RunTemplateParameterGroup>()
    every { parameterGroup11.id } returns "parameter_group_11"
    every { parameterGroup11.parameters } returns
        listOf("parameter_group_11_parameter1", "parameter_group_11_parameter2")
    val parameterGroup12 = mockk<RunTemplateParameterGroup>()
    every { parameterGroup12.id } returns "parameter_group_12"
    every { parameterGroup12.parameters } returns listOf("parameter_group_12_parameter1")
    val parameterGroup21 = mockk<RunTemplateParameterGroup>()
    every { parameterGroup21.id } returns "parameter_group_21"
    every { parameterGroup21.parameters } returns emptyList()

    every { solution.parameterGroups } returns
        listOf(parameterGroup11, parameterGroup12, parameterGroup21)

    val parentScenarioId = "S-parent"
    val parentScenario = mockk<Scenario>(relaxed = true)
    every { parentScenario.id } returns parentScenarioId
    every { parentScenario.parametersValues } returns
        listOf(
            ScenarioRunTemplateParameterValue(
                parameterId = "parameter_group_11_parameter1",
                value = "parameter_group_11_parameter1_value_from_parent"))

    every {
      scenarioServiceImpl.findScenarioByIdNoState(ORGANIZATION_ID, WORKSPACE_ID, parentScenarioId)
    } returns parentScenario

    every { idGenerator.generate("scenario") } returns "S-myScenarioId"

    val cosmosContainer = mockk<CosmosContainer>()
    every { cosmosCoreDatabase.getContainer("${ORGANIZATION_ID}_scenario_data") } returns
        cosmosContainer
    val cosmosItemResponse = mockk<CosmosItemResponse<Map<*, *>>>()
    every {
      cosmosContainer.createItem(ofType(Map::class), PartitionKey(AUTHENTICATED_USERNAME), any())
    } returns cosmosItemResponse
    // The implementation in ScenarioServiceImpl only needs to know if the response item is null or
    // not
    every { cosmosItemResponse.item } returns mapOf<String, Any>()

    val scenarioCreated =
        scenarioServiceImpl.createScenario(
            ORGANIZATION_ID,
            WORKSPACE_ID,
            Scenario(
                solutionId = SOLUTION_ID,
                parentId = parentScenarioId,
                runTemplateId = "runTemplate1_id",
                parametersValues =
                    listOf(
                        ScenarioRunTemplateParameterValue(
                            parameterId = "parameter_group_11_parameter1",
                            value = "parameter_group_11_parameter1_value_from_child"))))

    assertEquals("S-myScenarioId", scenarioCreated.id)
    assertEquals(AUTHENTICATED_USERNAME, scenarioCreated.ownerId)
    // PROD-7687 : both parent and child have the same parameters ID in their parameters values =>
    // child values should take precedence
    val parametersValues = scenarioCreated.parametersValues
    assertNotNull(parametersValues)
    assertEquals(
        1, parametersValues.size, "Child Scenario parameters values is not of the right size")
    assertEquals("parameter_group_11_parameter1", parametersValues[0].parameterId)
    assertEquals("parameter_group_11_parameter1_value_from_child", parametersValues[0].value)
    assertNull(parametersValues[0].isInherited)
  }

  @Test
  @Ignore("The logic has been moved to ScenarioRunServiceImpl")
  fun `findScenarioById should throw an error if scenario has a last run but no workflow `() {
    val scenarioId = "S-myScenarioId"
    val scenario =
        Scenario(
            id = scenarioId,
            lastRun =
                ScenarioLastRun(
                    scenarioRunId = "SR-myScenarioRunId", workflowId = null, workflowName = null))
    every {
      scenarioServiceImpl.findScenarioByIdNoState(ORGANIZATION_ID, WORKSPACE_ID, scenarioId)
    } returns scenario

    assertThrows<IllegalStateException> {
      scenarioServiceImpl.findScenarioById(ORGANIZATION_ID, WORKSPACE_ID, scenarioId)
    }
  }

  @Test
  fun `scenario state should be null if scenario has no last run`() {
    val scenarioId = "S-myScenarioId"
    val scenario = Scenario(id = scenarioId, lastRun = null)
    every {
      scenarioServiceImpl.findScenarioByIdNoState(ORGANIZATION_ID, WORKSPACE_ID, scenarioId)
    } returns scenario

    scenarioServiceImpl.findScenarioById(ORGANIZATION_ID, WORKSPACE_ID, scenarioId)

    assertNull(scenario.state)
  }

  @Test
  fun `PROD-7939 - deleting a child has no effect on siblings nor its parent`() {
    /* Given the following tree: M1 -- (P11 -- (C111, C112) | P12 -- C121) and M2 -- C21 ,
     * deleting C111 has no effect on the other scenarios */
    val m1 = Scenario(id = "M1", parentId = null, ownerId = AUTHENTICATED_USERNAME)
    val p11 = Scenario(id = "P11", parentId = m1.id, ownerId = AUTHENTICATED_USERNAME)
    val c111 = Scenario(id = "C111", parentId = p11.id, ownerId = AUTHENTICATED_USERNAME)
    val c112 = Scenario(id = "C112", parentId = p11.id, ownerId = AUTHENTICATED_USERNAME)
    val p12 = Scenario(id = "P12", parentId = m1.id, ownerId = AUTHENTICATED_USERNAME)
    val c121 = Scenario(id = "C121", parentId = p12.id, ownerId = AUTHENTICATED_USERNAME)
    val m2 = Scenario(id = "M2", parentId = null, ownerId = AUTHENTICATED_USERNAME)
    val c21 = Scenario(id = "C21", parentId = m2.id, ownerId = AUTHENTICATED_USERNAME)

    every {
      scenarioServiceImpl.findScenarioChildrenById(ORGANIZATION_ID, WORKSPACE_ID, m1.id!!)
    } returns listOf(p11, p12)
    every {
      scenarioServiceImpl.findScenarioChildrenById(ORGANIZATION_ID, WORKSPACE_ID, p11.id!!)
    } returns listOf(c111, c112)
    every {
      scenarioServiceImpl.findScenarioChildrenById(ORGANIZATION_ID, WORKSPACE_ID, p12.id!!)
    } returns listOf(c121)
    every {
      scenarioServiceImpl.findScenarioChildrenById(ORGANIZATION_ID, WORKSPACE_ID, m2.id!!)
    } returns listOf(c21)
    sequenceOf(m1, p11, p12, c111, c112, p12, c121, m2, c21).forEach {
      every { scenarioServiceImpl.findScenarioById(ORGANIZATION_ID, WORKSPACE_ID, it.id!!) } returns
          it
    }
    val cosmosContainer = mockk<CosmosContainer>(relaxed = true)
    every { cosmosCoreDatabase.getContainer("${ORGANIZATION_ID}_scenario_data") } returns
        cosmosContainer

    this.scenarioServiceImpl.deleteScenario(ORGANIZATION_ID, WORKSPACE_ID, c111.id!!, false)

    verify(exactly = 1) { cosmosTemplate.deleteEntity("${ORGANIZATION_ID}_scenario_data", c111) }
    sequenceOf(m1, p11, c112, p12, c121, m2, c21).forEach {
      verify(exactly = 0) { cosmosTemplate.deleteEntity("${ORGANIZATION_ID}_scenario_data", it) }
    }
    verify(exactly = 0) {
      cosmosContainer.upsertItem(ofType(Map::class), PartitionKey(AUTHENTICATED_USERNAME), any())
    }
    confirmVerified(cosmosTemplate)

    assertNull(m1.parentId)
    assertNotNull(m1.id)
    assertEquals(m1.id, p11.parentId)
    assertEquals(m1.id, p12.parentId)
    assertNotNull(p11.id)
    assertEquals(p11.id, c112.parentId)
    assertNotNull(p12.id)
    assertEquals(p12.id, c121.parentId)
    assertNull(m2.parentId)
    assertNotNull(m2.id)
    assertEquals(m2.id, c21.parentId)
  }

  @Test
  fun `PROD-7939 - deleting a master parent makes direct children masters`() {
    /* Given the following tree: M1 -- (P11 -- (C111, C112) | P12 -- C121) and M2 -- C21 ,
     * deleting M1 makes P11 and P12 become master scenarios */
    val m1 = Scenario(id = "M1", parentId = null, ownerId = AUTHENTICATED_USERNAME)
    val p11 = Scenario(id = "P11", parentId = m1.id, ownerId = AUTHENTICATED_USERNAME)
    val c111 = Scenario(id = "C111", parentId = p11.id, ownerId = AUTHENTICATED_USERNAME)
    val c112 = Scenario(id = "C112", parentId = p11.id, ownerId = AUTHENTICATED_USERNAME)
    val p12 = Scenario(id = "P12", parentId = m1.id, ownerId = AUTHENTICATED_USERNAME)
    val c121 = Scenario(id = "C121", parentId = p12.id, ownerId = AUTHENTICATED_USERNAME)
    val m2 = Scenario(id = "M2", parentId = null, ownerId = AUTHENTICATED_USERNAME)
    val c21 = Scenario(id = "C21", parentId = m2.id, ownerId = AUTHENTICATED_USERNAME)

    every {
      scenarioServiceImpl.findScenarioChildrenById(ORGANIZATION_ID, WORKSPACE_ID, m1.id!!)
    } returns listOf(p11, p12)
    every {
      scenarioServiceImpl.findScenarioChildrenById(ORGANIZATION_ID, WORKSPACE_ID, p11.id!!)
    } returns listOf(c111, c112)
    every {
      scenarioServiceImpl.findScenarioChildrenById(ORGANIZATION_ID, WORKSPACE_ID, p12.id!!)
    } returns listOf(c121)
    every {
      scenarioServiceImpl.findScenarioChildrenById(ORGANIZATION_ID, WORKSPACE_ID, m2.id!!)
    } returns listOf(c21)
    sequenceOf(m1, p11, p12, c111, c112, p12, c121, m2, c21).forEach {
      every { scenarioServiceImpl.findScenarioById(ORGANIZATION_ID, WORKSPACE_ID, it.id!!) } returns
          it
    }
    val cosmosContainer = mockk<CosmosContainer>(relaxed = true)
    every { cosmosCoreDatabase.getContainer("${ORGANIZATION_ID}_scenario_data") } returns
        cosmosContainer

    this.scenarioServiceImpl.deleteScenario(ORGANIZATION_ID, WORKSPACE_ID, m1.id!!, true)

    verify(exactly = 1) { cosmosTemplate.deleteEntity("${ORGANIZATION_ID}_scenario_data", m1) }
    sequenceOf(p11, c111, c112, p12, c121, m2, c21).forEach {
      verify(exactly = 0) { cosmosTemplate.deleteEntity("${ORGANIZATION_ID}_scenario_data", it) }
    }
    sequenceOf(p11, p12).forEach {
      verify(exactly = 1) {
        scenarioServiceImpl.upsertScenarioData(ORGANIZATION_ID, it, WORKSPACE_ID)
      }
    }
    verify(exactly = 2) {
      cosmosContainer.upsertItem(ofType(Map::class), PartitionKey(AUTHENTICATED_USERNAME), any())
    }
    confirmVerified(cosmosTemplate)

    assertNull(p11.parentId)
    assertNull(p12.parentId)
    assertNotNull(p11.id)
    assertEquals(p11.id, c112.parentId)
    assertNotNull(p12.id)
    assertEquals(p12.id, c121.parentId)
    assertNull(m2.parentId)
    assertNotNull(m2.id)
    assertEquals(m2.id, c21.parentId)
  }

  @Test
  fun `PROD-7939 - deleting a non-master parent assigns the parent's parent as new parent`() {
    /* Given the following tree: M1 -- (P11 -- (C111, C112) | P12 -- C121) and M2 -- C21 ,
     * deleting P11 results in assigning M1 as the new parent for C111 qnd C112  */
    val m1 = Scenario(id = "M1", parentId = null, ownerId = AUTHENTICATED_USERNAME)
    val p11 = Scenario(id = "P11", parentId = m1.id, ownerId = AUTHENTICATED_USERNAME)
    val c111 = Scenario(id = "C111", parentId = p11.id, ownerId = AUTHENTICATED_USERNAME)
    val c112 = Scenario(id = "C112", parentId = p11.id, ownerId = AUTHENTICATED_USERNAME)
    val p12 = Scenario(id = "P12", parentId = m1.id, ownerId = AUTHENTICATED_USERNAME)
    val c121 = Scenario(id = "C121", parentId = p12.id, ownerId = AUTHENTICATED_USERNAME)
    val m2 = Scenario(id = "M2", parentId = null, ownerId = AUTHENTICATED_USERNAME)
    val c21 = Scenario(id = "C21", parentId = m2.id, ownerId = AUTHENTICATED_USERNAME)

    every {
      scenarioServiceImpl.findScenarioChildrenById(ORGANIZATION_ID, WORKSPACE_ID, m1.id!!)
    } returns listOf(p11, p12)
    every {
      scenarioServiceImpl.findScenarioChildrenById(ORGANIZATION_ID, WORKSPACE_ID, p11.id!!)
    } returns listOf(c111, c112)
    every {
      scenarioServiceImpl.findScenarioChildrenById(ORGANIZATION_ID, WORKSPACE_ID, p12.id!!)
    } returns listOf(c121)
    every {
      scenarioServiceImpl.findScenarioChildrenById(ORGANIZATION_ID, WORKSPACE_ID, m2.id!!)
    } returns listOf(c21)
    sequenceOf(m1, p11, p12, c111, c112, p12, c121, m2, c21).forEach {
      every { scenarioServiceImpl.findScenarioById(ORGANIZATION_ID, WORKSPACE_ID, it.id!!) } returns
          it
    }
    val cosmosContainer = mockk<CosmosContainer>(relaxed = true)
    every { cosmosCoreDatabase.getContainer("${ORGANIZATION_ID}_scenario_data") } returns
        cosmosContainer

    this.scenarioServiceImpl.deleteScenario(ORGANIZATION_ID, WORKSPACE_ID, p11.id!!, false)

    verify(exactly = 1) { cosmosTemplate.deleteEntity("${ORGANIZATION_ID}_scenario_data", p11) }

    await atMost
        Duration.ofSeconds(5) untilAsserted
        {
          verify(exactly = 2) {
            cosmosContainer.upsertItem(
                ofType(Map::class), PartitionKey(AUTHENTICATED_USERNAME), any())
          }
        }

    sequenceOf(c111, c112).forEach {
      verify(exactly = 1) {
        scenarioServiceImpl.upsertScenarioData(ORGANIZATION_ID, it, WORKSPACE_ID)
      }
    }
    sequenceOf(m1, c111, c112, p12, c121, m2, c21).forEach {
      verify(exactly = 0) { cosmosTemplate.deleteEntity("${ORGANIZATION_ID}_scenario_data", it) }
    }
    confirmVerified(cosmosTemplate)

    assertNull(m1.parentId)
    assertNotNull(m1.id)
    assertEquals(m1.id, c111.parentId)
    assertEquals(m1.id, c112.parentId)
    assertEquals(m1.id, p12.parentId)
    assertNotNull(p12.id)
    assertEquals(p12.id, c121.parentId)
    assertNull(m2.parentId)
    assertNotNull(m2.id)
    assertEquals(m2.id, c21.parentId)
  }

  @TestFactory
  fun `scenario state should be set to Running when needed`() =
      buildDynamicTestsForWorkflowPhases(ScenarioJobState.Running, "Pending", "Running")

  @TestFactory
  fun `scenario state should be set to Successful when needed`() =
      buildDynamicTestsForWorkflowPhases(ScenarioJobState.Successful, "Succeeded")

  @TestFactory
  fun `scenario state should be set to Failed when needed`() =
      buildDynamicTestsForWorkflowPhases(
          ScenarioJobState.Failed, "Skipped", "Failed", "Error", "Omitted")

  @TestFactory
  fun `PROD-7888 - scenario state should be Unknown if workflow phase is null or not known to us`() =
      buildDynamicTestsForWorkflowPhases(ScenarioJobState.Unknown, null, "an-unknown-status")

  private fun buildDynamicTestsForWorkflowPhases(
      expectedState: ScenarioJobState?,
      vararg phases: String?
  ) =
      phases.toList().map { phase ->
        DynamicTest.dynamicTest("Workflow Phase: $phase") {
          val scenarioId = "S-myScenarioId"
          val scenario =
              Scenario(
                  id = scenarioId,
                  workspaceId = "w-myWorkspaceId",
                  lastRun =
                      ScenarioLastRun(
                          scenarioRunId = "SR-myScenarioRunId",
                          workflowId = "workflowId",
                          workflowName = "workflowName"))
          every {
            scenarioServiceImpl.findScenarioByIdNoState(ORGANIZATION_ID, WORKSPACE_ID, scenarioId)
          } returns scenario

          every { scenarioServiceImpl getProperty "eventPublisher" } returns
              object : CsmEventPublisher {
                override fun <T : CsmEvent> publishEvent(event: T) {
                  when (event) {
                    is WorkflowStatusRequest -> event.response = phase
                    is ScenarioRunEndToEndStateRequest ->
                        event.response =
                            when (phase) {
                              "Pending", "Running" -> "Running"
                              "Succeeded" -> "Successful"
                              "Skipped", "Failed", "Error", "Omitted" -> "Failed"
                              else -> "Unknown"
                            }
                    else ->
                        throw UnsupportedOperationException(
                            "This test event publisher purposely supports events of type WorkflowStatusRequest" +
                                " and ScenarioRunEndToEndStateRequest only")
                  }
                }
              }

          scenarioServiceImpl.findScenarioById(ORGANIZATION_ID, WORKSPACE_ID, scenarioId)

          assertEquals(expectedState, scenario.state)
        }
      }

  @Test
  fun `PROD-8051 - findAllScenarios add info about parent and master lastRuns`() {
    /* Scenario tree: M1 (run) -- (P11 (never run) -- C111 (run) */
    val m1 =
        Scenario(
            id = "M1",
            parentId = null,
            rootId = null,
            lastRun =
                ScenarioLastRun(
                    scenarioRunId = "sr-m1",
                    workflowName = "m1-workflowName",
                    workflowId = "m1-workflowId",
                    csmSimulationRun = "m1-csmSimulationRun"))
    val p11 = Scenario(id = "P11", parentId = m1.id, rootId = m1.id, lastRun = null)
    val c111 =
        Scenario(
            id = "C111",
            parentId = p11.id,
            rootId = m1.id,
            lastRun =
                ScenarioLastRun(
                    scenarioRunId = "sr-c111",
                    workflowName = "c111-workflowName",
                    workflowId = "c111-workflowId",
                    csmSimulationRun = "c111-csmSimulationRun"))

    every {
      scenarioServiceImpl.findScenarioByIdNoState(ORGANIZATION_ID, WORKSPACE_ID, m1.id!!)
    } returns m1
    every {
      scenarioServiceImpl.findScenarioByIdNoState(ORGANIZATION_ID, WORKSPACE_ID, p11.id!!)
    } returns p11
    every {
      scenarioServiceImpl.findScenarioByIdNoState(ORGANIZATION_ID, WORKSPACE_ID, c111.id!!)
    } returns c111
    every {
      scenarioServiceImpl.findAllScenariosStateOption(ORGANIZATION_ID, WORKSPACE_ID, true)
    } returns listOf(m1, p11, c111)

    val allScenariosById =
        scenarioServiceImpl
            .findAllScenarios(ORGANIZATION_ID, WORKSPACE_ID)
            .associateBy(Scenario::id)
    assertEquals(3, allScenariosById.size)
    assertTrue { allScenariosById.containsKey(m1.id) }
    assertTrue { allScenariosById.containsKey(p11.id) }
    assertTrue { allScenariosById.containsKey(c111.id) }

    val m1Found = allScenariosById[m1.id]!!
    assertNull(m1Found.parentLastRun)
    assertNull(m1Found.rootLastRun)

    val p11Found = allScenariosById[p11.id]!!
    assertEquals(m1Found.lastRun, p11Found.parentLastRun)
    assertEquals(m1Found.lastRun, p11Found.rootLastRun)

    val c111Found = allScenariosById[c111.id]!!
    assertNull(c111Found.parentLastRun)
    assertEquals(m1Found.lastRun, c111Found.rootLastRun)
  }

  @Test
  fun `PROD-8051 - findScenarioById adds null parent and master lastRuns if they don't exist`() {
    val parentId = "s-no-longer-existing-parent"
    val rootId = "s-no-longer-existing-root"
    every {
      scenarioServiceImpl.findScenarioByIdNoState(ORGANIZATION_ID, WORKSPACE_ID, parentId)
    } throws IllegalArgumentException()
    every {
      scenarioServiceImpl.findScenarioByIdNoState(ORGANIZATION_ID, WORKSPACE_ID, rootId)
    } throws IllegalArgumentException()

    val scenarioId = "s-c1"
    Scenario(id = scenarioId, parentId = parentId, rootId = rootId)
    val scenario = Scenario(id = scenarioId, parentId = parentId, rootId = rootId)
    every {
      scenarioServiceImpl.findScenarioByIdNoState(ORGANIZATION_ID, WORKSPACE_ID, scenarioId)
    } returns scenario

    val scenarioReturned =
        scenarioServiceImpl.findScenarioById(ORGANIZATION_ID, WORKSPACE_ID, scenarioId)

    assertNull(scenarioReturned.parentLastRun)
    assertNull(scenarioReturned.rootLastRun)
  }

  @ParameterizedTest(name = "(parentId, rootId) = ({0}, {1})")
  @ArgumentsSource(ParentRootScenarioIdsCartesianProductArgumentsProvider::class)
  fun `PROD-8051 - findScenarioById adds info about parent and master lastRuns`(
      parentScenarioId: String?,
      rootScenarioId: String?
  ) {
    val parentLastRun: ScenarioLastRun?
    if (!parentScenarioId.isNullOrBlank()) {
      parentLastRun =
          ScenarioLastRun(
              scenarioRunId = "sr-parentScenarioRunId",
              workflowName = "parent-workflowName",
              workflowId = "parent-workflowId",
              csmSimulationRun = "parent-csmSimulationRun")
      val parent = Scenario(id = parentScenarioId, lastRun = parentLastRun)
      every {
        scenarioServiceImpl.findScenarioByIdNoState(ORGANIZATION_ID, WORKSPACE_ID, parentScenarioId)
      } returns parent
    } else {
      parentLastRun = null
    }

    val rootLastRun: ScenarioLastRun?
    if (!rootScenarioId.isNullOrBlank()) {
      rootLastRun =
          ScenarioLastRun(
              scenarioRunId = "sr-rootScenarioRunId",
              workflowName = "root-workflowName",
              workflowId = "root-workflowId",
              csmSimulationRun = "root-csmSimulationRun")
      val root = Scenario(id = rootScenarioId, lastRun = rootLastRun)
      every {
        scenarioServiceImpl.findScenarioByIdNoState(ORGANIZATION_ID, WORKSPACE_ID, rootScenarioId)
      } returns root
    } else {
      rootLastRun = null
    }

    val scenarioId = "S-myScenarioId"
    val scenario = Scenario(id = scenarioId, parentId = parentScenarioId, rootId = rootScenarioId)
    every {
      scenarioServiceImpl.findScenarioByIdNoState(ORGANIZATION_ID, WORKSPACE_ID, scenarioId)
    } returns scenario

    val scenarioReturned =
        scenarioServiceImpl.findScenarioById(ORGANIZATION_ID, WORKSPACE_ID, scenarioId)

    assertEquals(parentLastRun, scenarioReturned.parentLastRun)
    assertEquals(rootLastRun, scenarioReturned.rootLastRun)
  }
}

private fun <T, U> List<T>.cartesianProduct(otherList: List<U>): List<Pair<T, U>> =
    this.flatMap { elementInThisList ->
      otherList.map { elementInOtherList -> elementInThisList to elementInOtherList }
    }

private class ParentRootScenarioIdsCartesianProductArgumentsProvider : ArgumentsProvider {
  override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> =
      listOf(null, "", "s-parentId")
          .cartesianProduct(listOf(null, "", "s-rootId"))
          .map { (parentId, rootId) -> Arguments.of(parentId, rootId) }
          .stream()
}
