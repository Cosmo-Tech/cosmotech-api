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
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.id.CsmIdGenerator
import com.cosmotech.api.rbac.*
import com.cosmotech.api.utils.getCurrentAccountIdentifier
import com.cosmotech.api.utils.getCurrentAuthenticatedRoles
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.api.utils.getCurrentAuthentication
import com.cosmotech.dataset.DatasetApiServiceInterface
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.dataset.domain.IngestionStatusEnum
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationSecurity
import com.cosmotech.scenario.domain.Scenario
import com.cosmotech.scenario.domain.ScenarioAccessControl
import com.cosmotech.scenario.domain.ScenarioJobState
import com.cosmotech.scenario.domain.ScenarioLastRun
import com.cosmotech.scenario.domain.ScenarioRunTemplateParameterValue
import com.cosmotech.scenario.domain.ScenarioSecurity
import com.cosmotech.scenario.repository.ScenarioRepository
import com.cosmotech.solution.SolutionApiServiceInterface
import com.cosmotech.solution.domain.RunTemplate
import com.cosmotech.solution.domain.RunTemplateParameter
import com.cosmotech.solution.domain.RunTemplateParameterGroup
import com.cosmotech.solution.domain.Solution
import com.cosmotech.workspace.WorkspaceApiServiceInterface
import com.cosmotech.workspace.azure.IWorkspaceEventHubService
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceAccessControl
import com.cosmotech.workspace.domain.WorkspaceSecurity
import com.cosmotech.workspace.domain.WorkspaceSolution
import com.cosmotech.workspace.service.getRbac
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import java.util.*
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
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

const val ORGANIZATION_ID = "O-AbCdEf123"
const val WORKSPACE_ID = "W-AbCdEf123"
const val SOLUTION_ID = "SOL-AbCdEf123"
const val AUTHENTICATED_USERNAME = "authenticated-user"
const val CONNECTED_ADMIN_USER = "test.admin@cosmotech.com"
const val CONNECTED_DEFAULT_USER = "test.user@cosmotech.com"

@ExtendWith(MockKExtension::class)
@Suppress("LongMethod", "LargeClass")
class ScenarioServiceImplTests {

  @Suppress("unused") @MockK private lateinit var datasetService: DatasetApiServiceInterface
  @MockK private lateinit var solutionService: SolutionApiServiceInterface
  @MockK private lateinit var workspaceService: WorkspaceApiServiceInterface

  @Suppress("unused")
  @MockK(relaxed = true)
  private lateinit var azureEventHubsClient: AzureEventHubsClient
  @Suppress("unused")
  @MockK(relaxed = true)
  private lateinit var azureDataExplorerClient: AzureDataExplorerClient
  @Suppress("unused")
  @MockK(relaxed = true)
  private lateinit var workspaceEventHubService: IWorkspaceEventHubService

  @Suppress("unused")
  @MockK
  private var csmPlatformProperties: CsmPlatformProperties = mockk(relaxed = true)
  @Suppress("unused") @MockK private var csmAdmin: CsmAdmin = CsmAdmin(csmPlatformProperties)

  @Suppress("unused") @SpyK private var csmRbac: CsmRbac = CsmRbac(csmPlatformProperties, csmAdmin)

  @MockK(relaxed = true) private lateinit var scenarioRepository: ScenarioRepository

  @Suppress("unused") @MockK private var eventPublisher: CsmEventPublisher = mockk(relaxed = true)
  @Suppress("unused") @MockK private var idGenerator: CsmIdGenerator = mockk(relaxed = true)

  @SpyK @InjectMockKs private lateinit var scenarioServiceImpl: ScenarioServiceImpl

  @BeforeTest
  fun setUp() {
    MockKAnnotations.init(this)

    val csmPlatformPropertiesAzure = mockk<CsmPlatformProperties.CsmPlatformAzure>()
    every { csmPlatformProperties.azure } returns csmPlatformPropertiesAzure

    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_DEFAULT_USER
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns AUTHENTICATED_USERNAME
    every { getCurrentAuthenticatedRoles(any()) } returns listOf()

    every { csmPlatformProperties.rbac.enabled } returns true
  }

  @AfterTest
  fun tearDown() {
    unmockkStatic(::getCurrentAuthentication)
  }

  @Test
  fun `do not creates copy of dataset for new scenario when workspace datasetCopy is true`() {

    // mock solution return
    every { solutionService.getVerifiedSolution(any(), any()) } returns
        Solution().apply {
          this.runTemplates =
              mutableListOf(mockk<RunTemplate>(relaxed = true) { every { id } returns "rt-id" })
        }

    // mock workspace
    every {
      workspaceService.getVerifiedWorkspace(any(), any(), PERMISSION_CREATE_CHILDREN)
    } returns
        Workspace(
                key = "key",
                name = "w",
                solution = WorkspaceSolution().apply { this.solutionId = "sol-id" })
            .apply { this.datasetCopy = true }

    // mock scenario save
    every { scenarioRepository.save(any()) } returns mockk<Scenario>()

    // mock dataset
    every { datasetService.findDatasetById(any(), any()) } returns
        Dataset().apply {
          this.twingraphId = "1"
          this.ingestionStatus = IngestionStatusEnum.SUCCESS
        }
    // TODO replace by copy
    //  every { datasetService.createSubDataset(any(), any(), any()) } returns mockk(relaxed = true)

    val scenario =
        Scenario().apply {
          this.id = "s-id"
          this.runTemplateId = "rt-id"
          this.datasetList = mutableListOf("d-1", "d-2")
        }
    scenarioServiceImpl.createScenario("o-id", "w-id", scenario)

    // TODO replace by copy
    // verify(exactly = 2) { datasetService.createSubDataset("o-id", any(), any()) }
  }

  @Test
  fun `do not creates copy of dataset for new scenario when workspace datasetCopy is false`() {

    // mock solution return
    every { solutionService.getVerifiedSolution(any(), any()) } returns
        Solution().apply {
          this.runTemplates =
              mutableListOf(mockk<RunTemplate>(relaxed = true) { every { id } returns "rt-id" })
        }

    // mock workspace
    every {
      workspaceService.getVerifiedWorkspace(any(), any(), PERMISSION_CREATE_CHILDREN)
    } returns
        Workspace(
                key = "key",
                name = "w",
                solution = WorkspaceSolution().apply { this.solutionId = "sol-id" })
            .apply { this.datasetCopy = false }

    // mock scenario save
    every { scenarioRepository.save(any()) } returns mockk<Scenario>()

    // mock dataset
    every { datasetService.findDatasetById(any(), any()) } returns
        Dataset().apply {
          this.twingraphId = "1"
          this.ingestionStatus = IngestionStatusEnum.SUCCESS
        }
    // TODO replace by copy
    // every { datasetService.createSubDataset(any(), any(), any()) } returns mockk(relaxed = true)

    val scenario =
        Scenario().apply {
          this.id = "s-id"
          this.runTemplateId = "rt-id"
          this.datasetList = mutableListOf("d-1", "d-2")
        }
    scenarioServiceImpl.createScenario("o-id", "w-id", scenario)

    // TODO replace by copy
    // verify(exactly = 0) { datasetService.createSubDataset("o-id", any(), any()) }
  }

  @Test
  fun `do not creates copy of dataset for new scenario when workspace datasetCopy is null`() {

    // mock solution return
    every { solutionService.getVerifiedSolution(any(), any()) } returns
        Solution().apply {
          this.runTemplates =
              mutableListOf(mockk<RunTemplate>(relaxed = true) { every { id } returns "rt-id" })
        }

    // mock workspace
    every {
      workspaceService.getVerifiedWorkspace(any(), any(), PERMISSION_CREATE_CHILDREN)
    } returns
        Workspace(
                key = "key",
                name = "w",
                solution = WorkspaceSolution().apply { this.solutionId = "sol-id" })
            .apply { this.datasetCopy = null }

    // mock scenario save
    every { scenarioRepository.save(any()) } returns mockk<Scenario>()

    // mock dataset
    every { datasetService.findDatasetById(any(), any()) } returns
        Dataset().apply {
          this.twingraphId = "1"
          this.ingestionStatus = IngestionStatusEnum.SUCCESS
        }
    // TODO replace by copy
    // every { datasetService.createSubDataset(any(), any(), any()) } returns mockk(relaxed = true)

    val scenario =
        Scenario().apply {
          this.id = "s-id"
          this.runTemplateId = "rt-id"
          this.datasetList = mutableListOf("d-1", "d-2")
        }
    scenarioServiceImpl.createScenario("o-id", "w-id", scenario)

    // TODO replace by copy
    // verify(exactly = 0) { datasetService.createSubDataset("o-id", any(), any()) }
  }

  @Test
  fun `PROD-7687 - should initialize Child Scenario Parameters values with parent ones`() {
    val workspace = mockk<Workspace>()
    every {
      workspaceService.getVerifiedWorkspace(
          ORGANIZATION_ID, WORKSPACE_ID, PERMISSION_CREATE_CHILDREN)
    } returns workspace
    val workspaceSecurity = mockk<WorkspaceSecurity>()
    every { workspace.security } returns workspaceSecurity
    every { workspace.security?.default } returns String()
    every { workspace.security?.accessControlList } returns mutableListOf()
    justRun { csmRbac.verify(any(), PERMISSION_CREATE_CHILDREN) }
    val workspaceSolution = mockk<WorkspaceSolution>()
    every { workspaceSolution.solutionId } returns SOLUTION_ID
    every { workspace.solution } returns workspaceSolution
    every { workspace.datasetCopy } returns null

    val solution = mockk<Solution>()
    every { solution.id } returns SOLUTION_ID
    every { solution.name } returns "test solution"
    every { solutionService.getVerifiedSolution(ORGANIZATION_ID, SOLUTION_ID) } returns solution
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

    every {
      scenarioServiceImpl.getVerifiedScenario(ORGANIZATION_ID, WORKSPACE_ID, parentScenarioId)
    } returns parentScenario

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
    val newScenario = mockScenario()
    every { scenarioRepository.save(any()) } returns newScenario

    every { solution.parameters } returns
        mutableListOf(
            RunTemplateParameter(
                id = "parameter_group_11_parameter1",
                defaultValue = "parameter1_group_11_value",
            ),
            RunTemplateParameter(
                id = "parameter_group_11_parameter2",
                defaultValue = "parameter2_group_11_value",
            ),
            RunTemplateParameter(
                id = "parameter_group_12_parameter1",
                defaultValue = "parameter1_group_12_value",
            ),
        )

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
        3, parametersValues.size, "Child Scenario parameters values is not of the right size")
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
    every {
      workspaceService.getVerifiedWorkspace(
          ORGANIZATION_ID, WORKSPACE_ID, PERMISSION_CREATE_CHILDREN)
    } returns workspace
    val workspaceSecurity = mockk<WorkspaceSecurity>()
    every { workspace.security } returns workspaceSecurity
    every { workspace.security?.default } returns String()
    every { workspace.security?.accessControlList } returns mutableListOf()

    val workspaceSolution = mockk<WorkspaceSolution>()
    every { workspaceSolution.solutionId } returns SOLUTION_ID
    every { workspace.solution } returns workspaceSolution
    every { workspace.datasetCopy } returns null

    val solution = mockk<Solution>()
    every { solution.id } returns SOLUTION_ID
    every { solution.name } returns "test solution"
    every { solutionService.getVerifiedSolution(ORGANIZATION_ID, SOLUTION_ID) } returns solution
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
    val newScenario = mockScenario()
    every { scenarioRepository.save(any()) } returns newScenario
    every { solution.parameters } returns
        mutableListOf(
            RunTemplateParameter(
                id = "parameter_group_11_parameter1",
                defaultValue = "parameter1_group_11_value",
            ),
            RunTemplateParameter(
                id = "parameter_group_11_parameter2",
                defaultValue = "parameter2_group_11_value",
            ),
            RunTemplateParameter(
                id = "parameter_group_12_parameter1",
                defaultValue = "parameter1_group_12_value",
            ),
        )
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
        3, parametersValues.size, "Child Scenario parameters values is not of the right size")
    assertEquals("parameter_group_12_parameter1", parametersValues[2].parameterId)
    assertEquals("parameter_group_12_parameter1_value", parametersValues[2].value)
    assertNull(parametersValues[0].isInherited)
  }

  @Test
  fun `PROD-7687 - Child Scenario Parameters values take precedence over the parent ones`() {
    val workspace = mockk<Workspace>()
    every {
      workspaceService.getVerifiedWorkspace(
          ORGANIZATION_ID, WORKSPACE_ID, PERMISSION_CREATE_CHILDREN)
    } returns workspace

    val workspaceSecurity = mockk<WorkspaceSecurity>()
    every { workspace.security } returns workspaceSecurity
    every { workspace.security?.default } returns String()
    every { workspace.security?.accessControlList } returns mutableListOf()
    justRun { csmRbac.verify(any(), PERMISSION_CREATE_CHILDREN) }

    val workspaceSolution = mockk<WorkspaceSolution>()
    every { workspaceSolution.solutionId } returns SOLUTION_ID
    every { workspace.solution } returns workspaceSolution
    every { workspace.datasetCopy } returns null

    val solution = mockk<Solution>()
    every { solution.id } returns SOLUTION_ID
    every { solution.name } returns "test solution"
    every { solutionService.getVerifiedSolution(ORGANIZATION_ID, SOLUTION_ID) } returns solution

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
    every { solution.parameters } returns
        mutableListOf(
            RunTemplateParameter(
                id = "parameter_group_11_parameter1",
                defaultValue = "parameter1_group_11_value",
            ),
            RunTemplateParameter(
                id = "parameter_group_11_parameter2",
                defaultValue = "parameter2_group_11_value",
            ),
            RunTemplateParameter(
                id = "parameter_group_12_parameter1",
                defaultValue = "parameter1_group_12_value",
            ),
        )

    every {
      scenarioServiceImpl.getVerifiedScenario(ORGANIZATION_ID, WORKSPACE_ID, parentScenarioId)
    } returns parentScenario

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
    val newScenario = mockScenario()
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
        3, parametersValues.size, "Child Scenario parameters values is not of the right size")
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
      scenarioServiceImpl.findScenarioById(
          ORGANIZATION_ID, WORKSPACE_ID, scenarioId, withState = false)
    } returns scenario

    assertThrows<IllegalStateException> {
      scenarioServiceImpl.findScenarioById(ORGANIZATION_ID, WORKSPACE_ID, scenarioId)
    }
  }

  @Test
  fun `scenario state should be null if scenario has no last run`() {
    val scenarioId = "S-myScenarioId"
    val scenario = Scenario(id = scenarioId, lastRun = null)
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")
    every {
      scenarioServiceImpl.getVerifiedScenario(ORGANIZATION_ID, WORKSPACE_ID, scenarioId)
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
      every {
        scenarioServiceImpl.getVerifiedScenario(
            ORGANIZATION_ID, WORKSPACE_ID, it.id!!, PERMISSION_DELETE)
      } returns it
    }
    val eventBus = mockk<CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus>()
    every { csmPlatformProperties.azure?.eventBus!! } returns eventBus
    val workspace = mockk<Workspace>()
    every { workspaceService.getVerifiedWorkspace(ORGANIZATION_ID, WORKSPACE_ID) } returns workspace
    every { workspace.key } returns "my-workspace-key"
    every { workspace.id } returns "my-workspace-id"
    every { workspace.datasetCopy } returns true
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

    scenarioServiceImpl.deleteScenario(ORGANIZATION_ID, WORKSPACE_ID, c111.id!!)

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

    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")
    every { scenarioRepository.save(any()) } returns mockk()
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
      every {
        scenarioServiceImpl.getVerifiedScenario(
            ORGANIZATION_ID, WORKSPACE_ID, it.id!!, PERMISSION_DELETE)
      } returns it
    }
    val eventBus = mockk<CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus>()
    every { csmPlatformProperties.azure?.eventBus!! } returns eventBus
    val workspace = mockk<Workspace>()
    every { workspaceService.getVerifiedWorkspace(ORGANIZATION_ID, WORKSPACE_ID) } returns workspace
    every { workspace.key } returns "my-workspace-key"
    every { workspace.id } returns "my-workspace-id"
    every { workspace.datasetCopy } returns true
    val authentication =
        mockk<CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication>()
    every { eventBus.authentication } returns authentication
    every { eventBus.authentication.strategy } returns
        CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication.Strategy
            .TENANT_CLIENT_CREDENTIALS
    every { workspace.sendScenarioMetadataToEventHub } returns false
    every { csmPlatformProperties.twincache.scenario.defaultPageSize } returns 100
    every { scenarioServiceImpl.isRbacEnabled(ORGANIZATION_ID, WORKSPACE_ID) } returns false

    scenarioServiceImpl.deleteScenario(ORGANIZATION_ID, WORKSPACE_ID, m1.id!!)

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

    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")
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
      every {
        scenarioServiceImpl.getVerifiedScenario(
            ORGANIZATION_ID, WORKSPACE_ID, it.id!!, PERMISSION_DELETE)
      } returns it
    }
    val eventBus = mockk<CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus>()
    every { csmPlatformProperties.azure?.eventBus!! } returns eventBus
    val workspace = mockk<Workspace>()
    every { workspaceService.getVerifiedWorkspace(ORGANIZATION_ID, WORKSPACE_ID) } returns workspace
    every { workspace.key } returns "my-workspace-key"
    every { workspace.id } returns "my-workspace-id"
    every { workspace.datasetCopy } returns true
    val authentication =
        mockk<CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication>()
    every { eventBus.authentication } returns authentication
    every { eventBus.authentication.strategy } returns
        CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication.Strategy
            .TENANT_CLIENT_CREDENTIALS
    every { workspace.sendScenarioMetadataToEventHub } returns false
    every { scenarioRepository.save(any()) } returns mockk()

    every { csmPlatformProperties.twincache.scenario.defaultPageSize } returns 100
    every { scenarioServiceImpl.isRbacEnabled(ORGANIZATION_ID, WORKSPACE_ID) } returns false

    scenarioServiceImpl.deleteScenario(ORGANIZATION_ID, WORKSPACE_ID, p11.id!!)

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
          every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")
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
          val workspace =
              Workspace(
                  id = WORKSPACE_ID,
                  security = null,
                  key = "w-myWorkspaceKey",
                  name = "wonderful_workspace",
                  solution = WorkspaceSolution(solutionId = "w-sol-id"))
          every {
            scenarioServiceImpl.getVerifiedScenario(ORGANIZATION_ID, WORKSPACE_ID, scenarioId)
          } returns scenario
          every { workspaceService.getVerifiedWorkspace(ORGANIZATION_ID, WORKSPACE_ID) } returns
              workspace

          every { scenarioServiceImpl getProperty "eventPublisher" } returns
              object : CsmEventPublisher {
                override fun <T : CsmEvent> publishEvent(event: T) {
                  when (event) {
                    is WorkflowStatusRequest -> event.response = phase
                    is ScenarioRunEndToEndStateRequest ->
                        event.response =
                            when (phase) {
                              "Pending",
                              "Running" -> "Running"
                              "Succeeded" -> "Successful"
                              "Skipped",
                              "Failed",
                              "Error",
                              "Omitted" -> "Failed"
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

  @TestFactory
  fun `test RBAC create Scenario`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_ADMIN to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true)
          .map { (role, shouldThrow) ->
            rbacTest("Test RBAC create Scenario: $role", role, shouldThrow) {
              every {
                workspaceService.getVerifiedWorkspace(
                    it.organization.id!!, it.workspace.id!!, PERMISSION_CREATE_CHILDREN)
              } returns it.workspace
              // Just here to really check PERMISSION_CREATE_CHILDREN and avoid several useless if
              // in this function...
              csmRbac.verify(it.workspace.getRbac(), PERMISSION_CREATE_CHILDREN)
              every { solutionService.getVerifiedSolution(any(), any()) } returns it.solution
              every { scenarioRepository.save(any()) } returns it.scenario
              scenarioServiceImpl.createScenario(
                  it.organization.id!!, it.workspace.id!!, it.scenario)
            }
          }

  @TestFactory
  fun `test RBAC read scenario`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_ADMIN to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to true,
              ROLE_NONE to true)
          .map { (role, shouldThrow) ->
            rbacTest("Test RBAC read scenario: $role", role, shouldThrow) {
              every {
                workspaceService.getVerifiedWorkspace(it.organization.id!!, it.workspace.id!!)
              } returns it.workspace
              every { scenarioRepository.findBy(any(), any(), any()) } returns
                  Optional.of(it.scenario)
              scenarioServiceImpl.findScenarioById(
                  it.organization.id!!, it.workspace.id!!, it.scenario.id!!)
            }
          }

  @TestFactory
  fun `test RBAC find all scenarios`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_ADMIN to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to false)
          .map { (role, shouldThrow) ->
            rbacTest("Test RBAC find all scenarios: $role", role, shouldThrow) {
              every {
                workspaceService.getVerifiedWorkspace(it.organization.id!!, it.workspace.id!!)
              } returns it.workspace
              every { csmPlatformProperties.twincache.scenario.defaultPageSize } returns 10

              scenarioServiceImpl.findAllScenarios(
                  it.organization.id!!, it.workspace.id!!, null, 100)
            }
          }

  @TestFactory
  fun `test RBAC get scenario validation status`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_ADMIN to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to true,
              ROLE_NONE to true)
          .map { (role, shouldThrow) ->
            rbacTest("Test RBAC get scenario validation status: $role", role, shouldThrow) {
              every {
                workspaceService.getVerifiedWorkspace(it.organization.id!!, it.workspace.id!!)
              } returns it.workspace
              every { scenarioRepository.findBy(any(), any(), any()) } returns
                  Optional.of(it.scenario)
              scenarioServiceImpl.getScenarioValidationStatusById(
                  it.organization.id!!, it.workspace.id!!, it.scenario.id!!)
            }
          }

  @TestFactory
  fun `test RBAC get scenario data download job info`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_ADMIN to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to true,
              ROLE_NONE to true)
          .map { (role, shouldThrow) ->
            rbacTest("Test RBAC get scenario data job info: $role", role, shouldThrow) {
              every {
                workspaceService.getVerifiedWorkspace(it.organization.id!!, it.workspace.id!!)
              } returns it.workspace
              every { scenarioRepository.findBy(any(), any(), any()) } returns
                  Optional.of(it.scenario)
              @Suppress("SwallowedException")
              try {
                scenarioServiceImpl.getScenarioDataDownloadJobInfo(
                    it.organization.id!!, it.workspace.id!!, it.scenario.id!!, "downloadId")
              } catch (e: CsmResourceNotFoundException) {
                print("Ignoring exception")
              }
            }
          }

  private fun rbacTest(
      testName: String,
      role: String,
      shouldThrow: Boolean,
      testLambda: (ctx: ScenarioTestContext) -> Unit
  ): DynamicTest? {
    val organization = mockOrganization("o-org-id", CONNECTED_DEFAULT_USER, role)
    val solution = mockSolution(organization.id!!)
    val workspace =
        mockWorkspace(organization.id!!, solution.id!!, "Workspace", CONNECTED_DEFAULT_USER, role)
    val scenario = mockScenario(CONNECTED_DEFAULT_USER, role)
    return DynamicTest.dynamicTest(testName) {
      if (shouldThrow) {
        assertThrows<CsmAccessForbiddenException> {
          testLambda(ScenarioTestContext(organization, solution, workspace, scenario))
        }
      } else {
        assertDoesNotThrow {
          testLambda(ScenarioTestContext(organization, solution, workspace, scenario))
        }
      }
    }
  }

  data class ScenarioTestContext(
      val organization: Organization,
      val solution: Solution,
      val workspace: Workspace,
      val scenario: Scenario
  )

  private fun mockScenario(
      roleName: String = CONNECTED_ADMIN_USER,
      role: String = ROLE_ADMIN
  ): Scenario {
    return Scenario(
        id = UUID.randomUUID().toString(),
        ownerId = roleName,
        security =
            ScenarioSecurity(
                default = ROLE_NONE,
                accessControlList =
                    mutableListOf(
                        ScenarioAccessControl(id = roleName, role = role),
                        ScenarioAccessControl("2user", "admin"))))
  }

  private fun mockOrganization(
      id: String,
      roleName: String = CONNECTED_ADMIN_USER,
      role: String = ROLE_ADMIN
  ): Organization {
    return Organization(
        id = id,
        name = "Organization Name",
        ownerId = "my.account-tester@cosmotech.com",
        security =
            OrganizationSecurity(
                default = ROLE_NONE,
                accessControlList =
                    mutableListOf(
                        OrganizationAccessControl(id = roleName, role = role),
                        OrganizationAccessControl("userLambda", "viewer"))))
  }

  private fun mockSolution(organizationId: String): Solution {
    return Solution(
        id = "solutionId",
        key = UUID.randomUUID().toString(),
        name = "My solution",
        organizationId = organizationId,
        ownerId = "ownerId")
  }

  private fun mockWorkspace(
      organizationId: String,
      solutionId: String,
      workspaceName: String,
      roleName: String = CONNECTED_ADMIN_USER,
      role: String = ROLE_ADMIN
  ): Workspace {
    return Workspace(
        id = UUID.randomUUID().toString(),
        key = UUID.randomUUID().toString(),
        name = workspaceName,
        solution =
            WorkspaceSolution(
                solutionId = solutionId,
            ),
        organizationId = organizationId,
        ownerId = "ownerId",
        security =
            WorkspaceSecurity(
                default = ROLE_NONE,
                accessControlList =
                    mutableListOf(
                        WorkspaceAccessControl(id = roleName, role = role),
                        WorkspaceAccessControl("2$workspaceName", "viewer"))))
  }
}
