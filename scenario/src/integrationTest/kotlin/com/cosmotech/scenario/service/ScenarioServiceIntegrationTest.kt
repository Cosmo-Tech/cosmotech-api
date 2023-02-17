// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenario.service

import com.cosmotech.api.azure.adx.AzureDataExplorerClient
import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.rbac.ROLE_EDITOR
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.rbac.ROLE_VIEWER
import com.cosmotech.api.tests.CsmRedisTestBase
import com.cosmotech.api.utils.getCurrentAuthenticatedMail
import com.cosmotech.api.utils.getCurrentAuthenticatedRoles
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.connector.api.ConnectorApiService
import com.cosmotech.connector.domain.Connector
import com.cosmotech.dataset.api.DatasetApiService
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.dataset.domain.DatasetConnector
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationSecurity
import com.cosmotech.scenario.api.ScenarioApiService
import com.cosmotech.scenario.domain.Scenario
import com.cosmotech.scenario.domain.ScenarioAccessControl
import com.cosmotech.scenario.domain.ScenarioRole
import com.cosmotech.scenario.domain.ScenarioRunTemplateParameterValue
import com.cosmotech.scenario.domain.ScenarioValidationStatus
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.solution.domain.Solution
import com.cosmotech.workspace.api.WorkspaceApiService
import com.cosmotech.workspace.azure.IWorkspaceEventHubService
import com.cosmotech.workspace.azure.WorkspaceEventHubInfo
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceSolution
import com.ninjasquad.springmockk.MockkBean
import com.redis.om.spring.RediSearchIndexer
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockkStatic
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.runner.RunWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.util.ReflectionTestUtils

const val CONNECTED_ADMIN_USER = "test.admin@cosmotech.com"
const val CONNECTED_READER_USER = "test.user@cosmotech.com"
const val FAKE_MAIL = "fake@mail.fr"

@ActiveProfiles(profiles = ["scenario-test"])
@ExtendWith(MockKExtension::class)
@ExtendWith(SpringExtension::class)
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ScenarioServiceIntegrationTest : CsmRedisTestBase() {

  private val logger = LoggerFactory.getLogger(ScenarioServiceIntegrationTest::class.java)

  @MockkBean lateinit var csmADX: AzureDataExplorerClient
  @MockK(relaxed = true) private lateinit var workspaceEventHubService: IWorkspaceEventHubService
  @MockK(relaxed = true) private lateinit var azureDataExplorerClient: AzureDataExplorerClient

  @Autowired lateinit var rediSearchIndexer: RediSearchIndexer
  @Autowired lateinit var connectorApiService: ConnectorApiService
  @Autowired lateinit var organizationApiService: OrganizationApiService
  @Autowired lateinit var datasetApiService: DatasetApiService
  @Autowired lateinit var solutionApiService: SolutionApiService
  @Autowired lateinit var workspaceApiService: WorkspaceApiService
  @Autowired lateinit var scenarioApiService: ScenarioApiService

  lateinit var connector: Connector
  lateinit var dataset: Dataset
  lateinit var solution: Solution
  lateinit var organization: Organization
  lateinit var workspace: Workspace
  lateinit var scenario: Scenario

  lateinit var connectorSaved: Connector
  lateinit var datasetSaved: Dataset
  lateinit var solutionSaved: Solution
  lateinit var organizationSaved: Organization
  lateinit var workspaceSaved: Workspace
  lateinit var scenarioSaved: Scenario

  @BeforeEach
  fun setUp() {
    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
    every { getCurrentAuthenticatedMail(any()) } returns CONNECTED_ADMIN_USER
    every { getCurrentAuthenticatedUserName() } returns "test.user"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("user")

    ReflectionTestUtils.setField(
        scenarioApiService, "workspaceEventHubService", workspaceEventHubService)
    ReflectionTestUtils.setField(
        scenarioApiService, "azureDataExplorerClient", azureDataExplorerClient)
    every { workspaceEventHubService.getWorkspaceEventHubInfo(any(), any(), any()) } returns
        mockWorkspaceEventHubInfo(false)

    rediSearchIndexer.createIndexFor(Scenario::class.java)

    connector = mockConnector("Connector")
    connectorSaved = connectorApiService.registerConnector(connector)

    organization = mockOrganization("Organization")
    organizationSaved = organizationApiService.registerOrganization(organization)

    dataset = mockDataset(organizationSaved.id!!, "Dataset", connectorSaved)
    datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)

    solution = mockSolution(organizationSaved.id!!)
    solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

    workspace = mockWorkspace(organizationSaved.id!!, solutionSaved.id!!, "Workspace")
    workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)

    scenario =
        mockScenario(
            organizationSaved.id!!,
            workspaceSaved.id!!,
            solutionSaved.id!!,
            "Scenario",
            mutableListOf(datasetSaved.id!!))

    scenarioSaved =
        scenarioApiService.createScenario(organizationSaved.id!!, workspaceSaved.id!!, scenario)
  }

  @Test
  fun `test CRUD operations on Scenario as User Admin`() {

    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")

    val scenario2 =
        mockScenario(
            organizationSaved.id!!,
            workspaceSaved.id!!,
            solutionSaved.id!!,
            "Scenario2",
            mutableListOf(datasetSaved.id!!))
    val scenarioSaved2 =
        scenarioApiService.createScenario(organizationSaved.id!!, workspaceSaved.id!!, scenario2)

    var scenarioList =
        scenarioApiService.findAllScenarios(organizationSaved.id!!, workspaceSaved.id!!)
    assertEquals(2, scenarioList.size)

    scenarioList =
        scenarioApiService.findAllScenariosByValidationStatus(
            organizationSaved.id!!, workspaceSaved.id!!, ScenarioValidationStatus.Draft)
    assertEquals(2, scenarioList.size)

    val scenarioRetrieved =
        scenarioApiService.findScenarioById(
            organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
    assertEquals(scenarioSaved, scenarioRetrieved)

    val scenarioValidationStatus =
        scenarioApiService.getScenarioValidationStatusById(
            organizationSaved.id!!, workspaceSaved.id!!, scenarioRetrieved.id!!)
    assertEquals(ScenarioValidationStatus.Draft, scenarioValidationStatus)

    val scenarioUpdated =
        scenarioApiService.updateScenario(
            organizationSaved.id!!,
            workspaceSaved.id!!,
            scenarioRetrieved.id!!,
            scenarioRetrieved.copy(name = "Scenario Updated"))
    assertEquals("Scenario Updated", scenarioUpdated.name)

    scenarioApiService.deleteScenario(
        organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved2.id!!, false)
    val scenarioListAfterDelete =
        scenarioApiService.findAllScenarios(organizationSaved.id!!, workspaceSaved.id!!)
    assertEquals(1, scenarioListAfterDelete.size)

    scenarioApiService.deleteAllScenarios(organizationSaved.id!!, workspaceSaved.id!!)
    val scenarioListAfterDeleteAll =
        scenarioApiService.findAllScenarios(organizationSaved.id!!, workspaceSaved.id!!)
    assertEquals(0, scenarioListAfterDeleteAll.size)
  }

  @Test
  fun `test Scenario Parameter Values as User Admin`() {

    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")

    assertTrue(scenarioSaved.parametersValues!!.isEmpty())
    val params =
        mutableListOf(
            ScenarioRunTemplateParameterValue("param1", "value1", "description1", false),
            ScenarioRunTemplateParameterValue("param2", "value2", "description2", false))
    scenarioApiService.addOrReplaceScenarioParameterValues(
        organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, params)
    val scenarioRetrievedAfterUpdate =
        scenarioApiService.findScenarioById(
            organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
    assertEquals(params, scenarioRetrievedAfterUpdate.parametersValues)

    scenarioApiService.removeAllScenarioParameterValues(
        organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
    val scenarioRetrievedAfterRemove =
        scenarioApiService.findScenarioById(
            organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
    assertTrue(scenarioRetrievedAfterRemove.parametersValues!!.isEmpty())
  }

  @Test
  fun `test RBAC ScenarioSecurity as User Admin`() {

    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")

    // Test default security
    val scenarioSecurity =
        scenarioApiService.getScenarioSecurity(
            organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
    assertEquals(ROLE_NONE, scenarioSecurity.default)

    val scenarioRole = ScenarioRole(ROLE_VIEWER)
    val scenarioSecurityRegistered =
        scenarioApiService.setScenarioDefaultSecurity(
            organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, scenarioRole)
    assertEquals(scenarioRole.role, scenarioSecurityRegistered.default)
  }

  @Test
  fun `test RBAC ScenarioSecurity as User Unauthorized`() {

    every { getCurrentAuthenticatedMail(any()) } returns CONNECTED_READER_USER

    // Test default security
    assertThrows<CsmAccessForbiddenException> {
      scenarioApiService.getScenarioSecurity(
          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
    }

    val scenarioRole = ScenarioRole(ROLE_VIEWER)
    assertThrows<CsmAccessForbiddenException> {
      scenarioApiService.setScenarioDefaultSecurity(
          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, scenarioRole)
    }
  }

  @Test
  fun `test RBAC AccessControls on Scenario as User Admin`() {

    val scenarioAccessControl = ScenarioAccessControl(FAKE_MAIL, ROLE_VIEWER)
    var scenarioAccessControlRegistered =
        scenarioApiService.addScenarioAccessControl(
            organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, scenarioAccessControl)
    assertEquals(scenarioAccessControl, scenarioAccessControlRegistered)

    scenarioAccessControlRegistered =
        scenarioApiService.getScenarioAccessControl(
            organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, FAKE_MAIL)
    assertEquals(scenarioAccessControl, scenarioAccessControlRegistered)

    scenarioAccessControlRegistered =
        scenarioApiService.updateScenarioAccessControl(
            organizationSaved.id!!,
            workspaceSaved.id!!,
            scenarioSaved.id!!,
            FAKE_MAIL,
            ScenarioRole(ROLE_EDITOR))
    assertEquals(ROLE_EDITOR, scenarioAccessControlRegistered.role)

    scenarioApiService.removeScenarioAccessControl(
        organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, FAKE_MAIL)
    assertThrows<CsmResourceNotFoundException> {
      scenarioAccessControlRegistered =
          scenarioApiService.getScenarioAccessControl(
              organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, FAKE_MAIL)
    }
  }

  @Test
  fun `test RBAC AccessControls on Scenario as User Unauthorized`() {

    every { getCurrentAuthenticatedMail(any()) } returns CONNECTED_READER_USER

    val scenarioAccessControl = ScenarioAccessControl(FAKE_MAIL, ROLE_VIEWER)
    assertThrows<CsmAccessForbiddenException> {
      scenarioApiService.addScenarioAccessControl(
          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, scenarioAccessControl)
    }
    assertThrows<CsmAccessForbiddenException> {
      scenarioApiService.getScenarioAccessControl(
          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, FAKE_MAIL)
    }
    assertThrows<CsmAccessForbiddenException> {
      scenarioApiService.updateScenarioAccessControl(
          organizationSaved.id!!,
          workspaceSaved.id!!,
          scenarioSaved.id!!,
          FAKE_MAIL,
          ScenarioRole(ROLE_VIEWER))
    }
    assertThrows<CsmAccessForbiddenException> {
      scenarioApiService.removeScenarioAccessControl(
          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, FAKE_MAIL)
    }
  }

  private fun mockWorkspaceEventHubInfo(eventHubAvailable: Boolean): WorkspaceEventHubInfo {
    return WorkspaceEventHubInfo(
        eventHubNamespace = "eventHubNamespace",
        eventHubAvailable = eventHubAvailable,
        eventHubName = "eventHubName",
        eventHubUri = "eventHubUri",
        eventHubSasKeyName = "eventHubSasKeyName",
        eventHubSasKey = "eventHubSasKey",
        eventHubCredentialType =
            CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication.Strategy
                .SHARED_ACCESS_POLICY)
  }

  private fun mockConnector(name: String): Connector {
    return Connector(
        key = UUID.randomUUID().toString(),
        name = name,
        repository = "/repository",
        version = "1.0",
        ioTypes = listOf(Connector.IoTypes.read))
  }

  fun mockDataset(organizationId: String, name: String, connector: Connector): Dataset {
    return Dataset(
        name = name,
        organizationId = organizationId,
        ownerId = "ownerId",
        connector =
            DatasetConnector(
                id = connector.id,
                name = connector.name,
                version = connector.version,
            ),
    )
  }

  fun mockSolution(organizationId: String): Solution {
    return Solution(
        id = "solutionId",
        key = UUID.randomUUID().toString(),
        name = "My solution",
        organizationId = organizationId,
        ownerId = "ownerId")
  }

  fun mockOrganization(id: String): Organization {
    return Organization(
        id = id,
        name = "Organization Name",
        ownerId = "my.account-tester@cosmotech.com",
        security =
            OrganizationSecurity(
                default = ROLE_NONE,
                accessControlList =
                    mutableListOf(
                        OrganizationAccessControl(id = CONNECTED_READER_USER, role = "reader"),
                        OrganizationAccessControl(id = CONNECTED_ADMIN_USER, role = "admin"))))
  }

  fun mockWorkspace(organizationId: String, solutionId: String, name: String): Workspace {
    return Workspace(
        key = UUID.randomUUID().toString(),
        name = name,
        solution =
            WorkspaceSolution(
                solutionId = solutionId,
            ),
        organizationId = organizationId,
        ownerId = "ownerId",
    )
  }

  fun mockScenario(
      organizationId: String,
      workspaceId: String,
      solutionId: String,
      name: String,
      datasetList: MutableList<String>
  ): Scenario {
    return Scenario(
        name = name,
        organizationId = organizationId,
        workspaceId = workspaceId,
        solutionId = solutionId,
        ownerId = "ownerId",
        datasetList = datasetList)
  }
}
