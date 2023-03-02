// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenario.service

import com.cosmotech.api.azure.adx.AzureDataExplorerClient
import com.cosmotech.api.azure.eventhubs.AzureEventHubsClient
import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.events.CsmEvent
import com.cosmotech.api.events.CsmEventPublisher
import com.cosmotech.api.events.ScenarioRunEndToEndStateRequest
import com.cosmotech.api.events.WorkflowStatusRequest
import com.cosmotech.api.id.CsmIdGenerator
import com.cosmotech.api.rbac.CsmRbac
import com.cosmotech.api.rbac.PERMISSION_CREATE_CHILDREN
import com.cosmotech.api.utils.getCurrentAuthenticatedMail
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.api.utils.getCurrentAuthentication
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.domain.Organization
import com.cosmotech.scenario.domain.Scenario
import com.cosmotech.scenario.domain.ScenarioJobState
import com.cosmotech.scenario.domain.ScenarioLastRun
import com.cosmotech.scenario.domain.ScenarioRunTemplateParameterValue
import com.cosmotech.scenario.repository.ScenarioRepository
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.solution.domain.RunTemplate
import com.cosmotech.solution.domain.RunTemplateParameterGroup
import com.cosmotech.solution.domain.Solution
import com.cosmotech.workspace.api.WorkspaceApiService
import com.cosmotech.workspace.azure.IWorkspaceEventHubService
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceSecurity
import com.cosmotech.workspace.domain.WorkspaceSolution
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import io.mockk.verify
import java.util.stream.Stream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource

const val ORGANIZATION_ID = "O-AbCdEf123"
const val WORKSPACE_ID = "W-AbCdEf123"
const val SOLUTION_ID = "SOL-AbCdEf123"
const val AUTHENTICATED_USERNAME = "authenticated-user"

@ExtendWith(MockKExtension::class)
@Suppress("LongMethod", "LargeClass")
class ScenarioServiceImplTests {

  @MockK private lateinit var organizationService: OrganizationApiService
  @MockK private lateinit var solutionService: SolutionApiService
  @MockK private lateinit var workspaceService: WorkspaceApiService
  @MockK private lateinit var idGenerator: CsmIdGenerator
  @MockK(relaxed = true) private lateinit var azureEventHubsClient: AzureEventHubsClient
  @MockK(relaxed = true) private lateinit var azureDataExplorerClient: AzureDataExplorerClient
  @MockK(relaxed = true) private lateinit var workspaceEventHubService: IWorkspaceEventHubService

  @RelaxedMockK private lateinit var csmRbac: CsmRbac

  @MockK(relaxed = true) private lateinit var scenarioRepository: ScenarioRepository
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
                solutionService,
                organizationService,
                workspaceService,
                azureDataExplorerClient,
                azureEventHubsClient,
                csmRbac,
                workspaceEventHubService,
                scenarioRepository),
            recordPrivateCalls = true)

    every { scenarioServiceImpl getProperty "idGenerator" } returns idGenerator
    every { scenarioServiceImpl getProperty "eventPublisher" } returns eventPublisher
    every { scenarioServiceImpl getProperty "eventPublisher" } returns eventPublisher

    val csmPlatformPropertiesAzure = mockk<CsmPlatformProperties.CsmPlatformAzure>()
    every { csmPlatformProperties.azure } returns csmPlatformPropertiesAzure

    every { scenarioServiceImpl getProperty "csmPlatformProperties" } returns csmPlatformProperties

    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
    every { getCurrentAuthenticatedUserName() } returns AUTHENTICATED_USERNAME
    every { getCurrentAuthenticatedMail(csmPlatformProperties) } returns "dummy@cosmotech.com"
  }

  @AfterTest
  fun tearDown() {
    unmockkStatic(::getCurrentAuthentication)
  }

  @Test
  fun `PROD-7687 - should initialize Child Scenario Parameters values with parent ones`() {
    val workspace = mockk<Workspace>()
    every { workspaceService.findWorkspaceById(ORGANIZATION_ID, WORKSPACE_ID) } returns workspace
    val workspaceSecurity = mockk<WorkspaceSecurity>()
    every { workspace.security } returns workspaceSecurity
    every { workspace.security?.default } returns String()
    every { workspace.security?.accessControlList } returns mutableListOf()
    justRun { csmRbac.verify(any(), PERMISSION_CREATE_CHILDREN) }
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
        mutableListOf("parameter_group_11", "parameter_group_12")

    val runTemplate2 = mockk<RunTemplate>()
    every { runTemplate2.id } returns "runTemplate2_id"
    every { runTemplate2.id } returns "runTemplate2 name"
    every { runTemplate2.parameterGroups } returns mutableListOf("parameter_group_21")

    every { solution.runTemplates } returns mutableListOf(runTemplate1, runTemplate2)

    val parameterGroup11 = mockk<RunTemplateParameterGroup>()
    every { parameterGroup11.id } returns "parameter_group_11"
    every { parameterGroup11.parameters } returns
        mutableListOf("parameter_group_11_parameter1", "parameter_group_11_parameter2")
    val parameterGroup12 = mockk<RunTemplateParameterGroup>()
    every { parameterGroup12.id } returns "parameter_group_12"
    every { parameterGroup12.parameters } returns mutableListOf("parameter_group_12_parameter1")
    val parameterGroup21 = mockk<RunTemplateParameterGroup>()
    every { parameterGroup21.id } returns "parameter_group_21"
    every { parameterGroup21.parameters } returns mutableListOf()

    every { solution.parameterGroups } returns
        mutableListOf(parameterGroup11, parameterGroup12, parameterGroup21)

    val parentScenarioId = "S-parent"
    val parentScenario = mockk<Scenario>(relaxed = true)
    every { parentScenario.id } returns parentScenarioId
    every { parentScenario.parametersValues } returns
        mutableListOf(
            ScenarioRunTemplateParameterValue(
                parameterId = "parameter_group_11_parameter1",
                value = "parameter_group_11_parameter1_value"))

    every { scenarioServiceImpl.findScenarioByIdNoState(parentScenarioId) } returns parentScenario

    every { idGenerator.generate("scenario") } returns "S-myScenarioId"

    // The implementation in ScenarioServiceImpl only needs to know if the response item is null or
    // not
    val eventBus = mockk<CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus>()
    every { csmPlatformProperties.azure?.eventBus!! } returns eventBus
    every { workspace.key } returns "my-workspace-key"
    every { workspace.id } returns "my-workspace-id"
    val authentication =
        mockk<CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication>()
    every { eventBus.authentication } returns authentication
    every { eventBus.authentication.strategy } returns
        CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication.Strategy
            .TENANT_CLIENT_CREDENTIALS
    every { workspace.sendScenarioMetadataToEventHub } returns false
    val newScenario = getMockScenario()
    every { scenarioRepository.save(any()) } returns newScenario

    val scenarioCreated =
        scenarioServiceImpl.createScenario(
            ORGANIZATION_ID,
            WORKSPACE_ID,
            Scenario(
                solutionId = SOLUTION_ID,
                parentId = parentScenarioId,
                runTemplateId = "runTemplate1_id",
                parametersValues =
                    mutableListOf(
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
    val workspace = mockk<Workspace>()
    every { workspaceService.findWorkspaceById(ORGANIZATION_ID, WORKSPACE_ID) } returns workspace
    val workspaceSecurity = mockk<WorkspaceSecurity>()
    every { workspace.security } returns workspaceSecurity
    every { workspace.security?.default } returns String()
    every { workspace.security?.accessControlList } returns mutableListOf()
    justRun { csmRbac.verify(any(), PERMISSION_CREATE_CHILDREN) }

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
        mutableListOf("parameter_group_11", "parameter_group_12")

    val runTemplate2 = mockk<RunTemplate>()
    every { runTemplate2.id } returns "runTemplate2_id"
    every { runTemplate2.id } returns "runTemplate2 name"
    every { runTemplate2.parameterGroups } returns mutableListOf("parameter_group_21")

    every { solution.runTemplates } returns mutableListOf(runTemplate1, runTemplate2)

    val parameterGroup11 = mockk<RunTemplateParameterGroup>()
    every { parameterGroup11.id } returns "parameter_group_11"
    every { parameterGroup11.parameters } returns
        mutableListOf("parameter_group_11_parameter1", "parameter_group_11_parameter2")
    val parameterGroup12 = mockk<RunTemplateParameterGroup>()
    every { parameterGroup12.id } returns "parameter_group_12"
    every { parameterGroup12.parameters } returns mutableListOf("parameter_group_12_parameter1")
    val parameterGroup21 = mockk<RunTemplateParameterGroup>()
    every { parameterGroup21.id } returns "parameter_group_21"
    every { parameterGroup21.parameters } returns mutableListOf()

    every { solution.parameterGroups } returns
        mutableListOf(parameterGroup11, parameterGroup12, parameterGroup21)

    every { idGenerator.generate("scenario") } returns "S-myScenarioId"

    // The implementation in ScenarioServiceImpl only needs to know if the response item is null or
    // not
    val eventBus = mockk<CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus>()
    every { csmPlatformProperties.azure?.eventBus!! } returns eventBus
    every { workspace.key } returns "my-workspace-key"
    every { workspace.id } returns "my-workspace-id"
    val authentication =
        mockk<CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication>()
    every { eventBus.authentication } returns authentication
    every { eventBus.authentication.strategy } returns
        CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication.Strategy
            .TENANT_CLIENT_CREDENTIALS
    every { workspace.sendScenarioMetadataToEventHub } returns false
    val newScenario = getMockScenario()
    every { scenarioRepository.save(any()) } returns newScenario

    val scenarioCreated =
        scenarioServiceImpl.createScenario(
            ORGANIZATION_ID,
            WORKSPACE_ID,
            Scenario(
                solutionId = SOLUTION_ID,
                runTemplateId = "runTemplate1_id",
                parametersValues =
                    mutableListOf(
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
    val workspace = mockk<Workspace>()
    every { workspaceService.findWorkspaceById(ORGANIZATION_ID, WORKSPACE_ID) } returns workspace
    val workspaceSecurity = mockk<WorkspaceSecurity>()
    every { workspace.security } returns workspaceSecurity
    every { workspace.security?.default } returns String()
    every { workspace.security?.accessControlList } returns mutableListOf()
    justRun { csmRbac.verify(any(), PERMISSION_CREATE_CHILDREN) }
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
        mutableListOf("parameter_group_11", "parameter_group_12")

    val runTemplate2 = mockk<RunTemplate>()
    every { runTemplate2.id } returns "runTemplate2_id"
    every { runTemplate2.id } returns "runTemplate2 name"
    every { runTemplate2.parameterGroups } returns mutableListOf("parameter_group_21")

    every { solution.runTemplates } returns mutableListOf(runTemplate1, runTemplate2)

    val parameterGroup11 = mockk<RunTemplateParameterGroup>()
    every { parameterGroup11.id } returns "parameter_group_11"
    every { parameterGroup11.parameters } returns
        mutableListOf("parameter_group_11_parameter1", "parameter_group_11_parameter2")
    val parameterGroup12 = mockk<RunTemplateParameterGroup>()
    every { parameterGroup12.id } returns "parameter_group_12"
    every { parameterGroup12.parameters } returns mutableListOf("parameter_group_12_parameter1")
    val parameterGroup21 = mockk<RunTemplateParameterGroup>()
    every { parameterGroup21.id } returns "parameter_group_21"
    every { parameterGroup21.parameters } returns mutableListOf()

    every { solution.parameterGroups } returns
        mutableListOf(parameterGroup11, parameterGroup12, parameterGroup21)

    val parentScenarioId = "S-parent"
    val parentScenario = mockk<Scenario>(relaxed = true)
    every { parentScenario.id } returns parentScenarioId
    every { parentScenario.parametersValues } returns
        mutableListOf(
            ScenarioRunTemplateParameterValue(
                parameterId = "parameter_group_11_parameter1",
                value = "parameter_group_11_parameter1_value_from_parent"))

    every { scenarioServiceImpl.findScenarioByIdNoState(parentScenarioId) } returns parentScenario

    every { idGenerator.generate("scenario") } returns "S-myScenarioId"

    val eventBus = mockk<CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus>()
    every { csmPlatformProperties.azure?.eventBus!! } returns eventBus
    every { workspace.key } returns "my-workspace-key"
    every { workspace.id } returns "my-workspace-id"
    val authentication =
        mockk<CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication>()
    every { eventBus.authentication } returns authentication
    every { eventBus.authentication.strategy } returns
        CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication.Strategy
            .TENANT_CLIENT_CREDENTIALS
    every { workspace.sendScenarioMetadataToEventHub } returns false
    val newScenario = getMockScenario()
    every { scenarioRepository.save(any()) } returns newScenario

    val scenarioCreated =
        scenarioServiceImpl.createScenario(
            ORGANIZATION_ID,
            WORKSPACE_ID,
            Scenario(
                solutionId = SOLUTION_ID,
                parentId = parentScenarioId,
                runTemplateId = "runTemplate1_id",
                parametersValues =
                    mutableListOf(
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
    every { scenarioServiceImpl.findScenarioByIdNoState(scenarioId) } returns scenario

    assertThrows<IllegalStateException> {
      scenarioServiceImpl.findScenarioById(ORGANIZATION_ID, WORKSPACE_ID, scenarioId)
    }
  }

  @Test
  fun `scenario state should be null if scenario has no last run`() {
    val scenarioId = "S-myScenarioId"
    val scenario = Scenario(id = scenarioId, lastRun = null)
    val organization = Organization(id = ORGANIZATION_ID, security = null)
    val workspace =
        Workspace(
            id = WORKSPACE_ID,
            security = null,
            key = "w-myWorkspaceKey",
            name = "wonderful_workspace",
            solution = WorkspaceSolution(solutionId = "w-sol-id"))
    every { organizationService.findOrganizationById(ORGANIZATION_ID) } returns organization
    every { workspaceService.findWorkspaceById(ORGANIZATION_ID, WORKSPACE_ID) } returns workspace
    every { scenarioServiceImpl.findScenarioByIdNoState(scenarioId) } returns scenario

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
    val eventBus = mockk<CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus>()
    every { csmPlatformProperties.azure?.eventBus!! } returns eventBus
    val workspace = mockk<Workspace>()
    every { workspaceService.findWorkspaceById(ORGANIZATION_ID, WORKSPACE_ID) } returns workspace
    every { workspace.key } returns "my-workspace-key"
    every { workspace.id } returns "my-workspace-id"

    val workspaceSecurity = mockk<WorkspaceSecurity>()
    every { workspace.security } returns workspaceSecurity
    every { workspace.security?.default } returns String()
    every { workspace.security?.accessControlList } returns mutableListOf()
    every { csmRbac.isAdmin(any(), any()) } returns true

    val authentication =
        mockk<CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication>()
    every { eventBus.authentication } returns authentication
    every { eventBus.authentication.strategy } returns
        CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication.Strategy
            .TENANT_CLIENT_CREDENTIALS
    every { workspace.sendScenarioMetadataToEventHub } returns false
    every { csmPlatformProperties.twincache.scenario.defaultPageSize } returns 5

    this.scenarioServiceImpl.deleteScenario(ORGANIZATION_ID, WORKSPACE_ID, c111.id!!, false)

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
    val eventBus = mockk<CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus>()
    every { csmPlatformProperties.azure?.eventBus!! } returns eventBus
    val workspace = mockk<Workspace>()
    every { workspaceService.findWorkspaceById(ORGANIZATION_ID, WORKSPACE_ID) } returns workspace
    every { workspace.key } returns "my-workspace-key"
    every { workspace.id } returns "my-workspace-id"
    val authentication =
        mockk<CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication>()
    every { eventBus.authentication } returns authentication
    every { eventBus.authentication.strategy } returns
        CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication.Strategy
            .TENANT_CLIENT_CREDENTIALS
    every { workspace.sendScenarioMetadataToEventHub } returns false

    this.scenarioServiceImpl.deleteScenario(ORGANIZATION_ID, WORKSPACE_ID, m1.id!!, true)

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
    val eventBus = mockk<CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus>()
    every { csmPlatformProperties.azure?.eventBus!! } returns eventBus
    val workspace = mockk<Workspace>()
    every { workspaceService.findWorkspaceById(ORGANIZATION_ID, WORKSPACE_ID) } returns workspace
    every { workspace.key } returns "my-workspace-key"
    every { workspace.id } returns "my-workspace-id"
    val authentication =
        mockk<CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication>()
    every { eventBus.authentication } returns authentication
    every { eventBus.authentication.strategy } returns
        CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication.Strategy
            .TENANT_CLIENT_CREDENTIALS
    every { workspace.sendScenarioMetadataToEventHub } returns false
    every { scenarioRepository.save(any()) } returns mockk()

    this.scenarioServiceImpl.deleteScenario(ORGANIZATION_ID, WORKSPACE_ID, p11.id!!, false)

    sequenceOf(c111, c112).forEach {
      verify(exactly = 1) { scenarioServiceImpl.upsertScenarioData(it) }
    }
    sequenceOf(m1, c111, c112, p12, c121, m2, c21).forEach {
      verify(exactly = 0) { scenarioRepository.delete(it) }
    }

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
          val organization = Organization(id = ORGANIZATION_ID, security = null)
          val workspace =
              Workspace(
                  id = WORKSPACE_ID,
                  security = null,
                  key = "w-myWorkspaceKey",
                  name = "wonderful_workspace",
                  solution = WorkspaceSolution(solutionId = "w-sol-id"))
          every { scenarioServiceImpl.findScenarioByIdNoState(scenarioId) } returns scenario
          every { organizationService.findOrganizationById(ORGANIZATION_ID) } returns organization
          every { workspaceService.findWorkspaceById(ORGANIZATION_ID, WORKSPACE_ID) } returns
              workspace

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
    val organization = Organization(id = ORGANIZATION_ID, security = null)
    val workspace =
        Workspace(
            id = WORKSPACE_ID,
            security = null,
            key = "w-myWorkspaceKey",
            name = "wonderful_workspace",
            solution = WorkspaceSolution(solutionId = "w-sol-id"))
    every { csmPlatformProperties.twincache.scenario.defaultPageSize } returns 100
    every { organizationService.findOrganizationById(ORGANIZATION_ID) } returns organization
    every { workspaceService.findWorkspaceById(ORGANIZATION_ID, WORKSPACE_ID) } returns workspace
    every { scenarioServiceImpl.findScenarioByIdNoState(m1.id!!) } returns m1
    every { scenarioServiceImpl.findScenarioByIdNoState(p11.id!!) } returns p11
    every { scenarioServiceImpl.findScenarioByIdNoState(c111.id!!) } returns c111
    every {
      scenarioServiceImpl.findPaginatedScenariosStateOption(
          ORGANIZATION_ID, WORKSPACE_ID, 0, 100, true)
    } returns listOf(m1, p11, c111)

    val allScenariosById =
        scenarioServiceImpl
            .findAllScenarios(ORGANIZATION_ID, WORKSPACE_ID, 0, 100)
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
    val organization = Organization(id = ORGANIZATION_ID, security = null)
    val workspace =
        Workspace(
            id = WORKSPACE_ID,
            security = null,
            key = "w-myWorkspaceKey",
            name = "wonderful_workspace",
            solution = WorkspaceSolution(solutionId = "w-sol-id"))
    every { organizationService.findOrganizationById(ORGANIZATION_ID) } returns organization
    every { workspaceService.findWorkspaceById(ORGANIZATION_ID, WORKSPACE_ID) } returns workspace
    every { scenarioServiceImpl.findScenarioByIdNoState(parentId) } throws
        IllegalArgumentException()
    every { scenarioServiceImpl.findScenarioByIdNoState(rootId) } throws IllegalArgumentException()

    val scenarioId = "s-c1"
    Scenario(id = scenarioId, parentId = parentId, rootId = rootId)
    val scenario = Scenario(id = scenarioId, parentId = parentId, rootId = rootId)
    every { scenarioServiceImpl.findScenarioByIdNoState(scenarioId) } returns scenario

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
      every { scenarioServiceImpl.findScenarioByIdNoState(parentScenarioId) } returns parent
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
      every { scenarioServiceImpl.findScenarioByIdNoState(rootScenarioId) } returns root
    } else {
      rootLastRun = null
    }

    val scenarioId = "S-myScenarioId"
    val scenario = Scenario(id = scenarioId, parentId = parentScenarioId, rootId = rootScenarioId)
    val organization = Organization(id = ORGANIZATION_ID, security = null)
    val workspace =
        Workspace(
            id = WORKSPACE_ID,
            security = null,
            key = "w-myWorkspaceKey",
            name = "wonderful_workspace",
            solution = WorkspaceSolution(solutionId = "w-sol-id"))
    every { organizationService.findOrganizationById(ORGANIZATION_ID) } returns organization
    every { workspaceService.findWorkspaceById(ORGANIZATION_ID, WORKSPACE_ID) } returns workspace
    every { scenarioServiceImpl.findScenarioByIdNoState(scenarioId) } returns scenario

    val scenarioReturned =
        scenarioServiceImpl.findScenarioById(ORGANIZATION_ID, WORKSPACE_ID, scenarioId)

    assertEquals(parentLastRun, scenarioReturned.parentLastRun)
    assertEquals(rootLastRun, scenarioReturned.rootLastRun)
  }

  private fun getMockScenario(): Scenario {
    val newScenario = mockk<Scenario>(relaxed = true)
    newScenario.id = "S-myScenarioId"
    newScenario.ownerId = AUTHENTICATED_USERNAME
    return newScenario
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
