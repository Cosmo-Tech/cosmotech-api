// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenario.azure

import com.azure.cosmos.CosmosClient
import com.azure.cosmos.CosmosContainer
import com.azure.cosmos.CosmosDatabase
import com.azure.cosmos.models.CosmosItemResponse
import com.azure.cosmos.models.PartitionKey
import com.azure.spring.data.cosmos.core.CosmosTemplate
import com.cosmotech.api.argo.WorkflowUtils
import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.id.CsmIdGenerator
import com.cosmotech.api.utils.getCurrentAuthentication
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.scenario.domain.Scenario
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
import kotlin.test.*
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.security.core.Authentication

const val ORGANIZATION_ID = "O-AbCdEf123"
const val WORKSPACE_ID = "W-AbCdEf123"
const val SOLUTION_ID = "SOL-AbCdEf123"
const val AUTHENTICATED_USERNAME = "authenticated-user"

@ExtendWith(MockKExtension::class)
class ScenarioServiceImplTests {

  @MockK private lateinit var userService: UserApiService
  @MockK private lateinit var organizationService: OrganizationApiService
  @MockK private lateinit var solutionService: SolutionApiService
  @MockK private lateinit var workspaceService: WorkspaceApiService
  @MockK private lateinit var idGenerator: CsmIdGenerator

  @MockK private lateinit var workflowUtils: WorkflowUtils

  @Suppress("unused") @MockK private lateinit var cosmosTemplate: CosmosTemplate
  @Suppress("unused") @MockK private lateinit var cosmosClient: CosmosClient
  @Suppress("unused") @MockK private lateinit var cosmosCoreDatabase: CosmosDatabase
  @Suppress("unused") @MockK private lateinit var csmPlatformProperties: CsmPlatformProperties

  private lateinit var scenarioServiceImpl: ScenarioServiceImpl

  @BeforeTest
  fun setUp() {
    MockKAnnotations.init(this, relaxUnitFun = true)

    this.scenarioServiceImpl =
        spyk(
            ScenarioServiceImpl(
                userService, solutionService, organizationService, workspaceService, workflowUtils),
            recordPrivateCalls = true)

    every { scenarioServiceImpl getProperty "cosmosClient" } returns cosmosClient
    every { scenarioServiceImpl getProperty "idGenerator" } returns idGenerator

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
  fun `PROD-7687 - createScenario should initialize Child Scenario Parameters values with parent ones`() {
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
  fun `PROD-7687 - in createScenario, Child Scenario Parameters values take precedence over the parent ones`() {
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
}
