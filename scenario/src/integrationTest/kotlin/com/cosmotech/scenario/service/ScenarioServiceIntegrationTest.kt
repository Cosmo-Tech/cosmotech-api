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
  @Autowired lateinit var csmPlatformProperties: CsmPlatformProperties

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

    dataset = makeDataset(organizationSaved.id!!, "Dataset", connectorSaved)
    datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)

    solution = mockSolution(organizationSaved.id!!)
    solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

    workspace = mockWorkspace(organizationSaved.id!!, solutionSaved.id!!, "Workspace")
    workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)

    scenario =
        makeScenario(
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

    logger.info("should create a second Scenario")
    val scenario2 =
        makeScenario(
            organizationSaved.id!!,
            workspaceSaved.id!!,
            solutionSaved.id!!,
            "Scenario2",
            mutableListOf(datasetSaved.id!!))
    val scenarioSaved2 =
        scenarioApiService.createScenario(organizationSaved.id!!, workspaceSaved.id!!, scenario2)

    logger.info("should find all Scenarios and assert there are 2")
    var scenarioList =
        scenarioApiService.findAllScenarios(organizationSaved.id!!, workspaceSaved.id!!, null, null)
    assertTrue(scenarioList.size == 2)

    logger.info("should find all Scenarios by Validation Status Draft and assert there are 2")
    scenarioList =
        scenarioApiService.findAllScenariosByValidationStatus(
            organizationSaved.id!!, workspaceSaved.id!!, ScenarioValidationStatus.Draft, null, null)
    assertTrue(scenarioList.size == 2)

    logger.info("should find a Scenario by Id and assert it is the one created")
    val scenarioRetrieved =
        scenarioApiService.findScenarioById(
            organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
    assertEquals(scenarioSaved, scenarioRetrieved)

    logger.info("should get the Validation Status of the Scenario and assert it is Draft")
    val scenarioValidationStatus =
        scenarioApiService.getScenarioValidationStatusById(
            organizationSaved.id!!, workspaceSaved.id!!, scenarioRetrieved.id!!)
    assertEquals(ScenarioValidationStatus.Draft, scenarioValidationStatus)

    logger.info("should update the Scenario and assert the name has been updated")
    val scenarioUpdated =
        scenarioApiService.updateScenario(
            organizationSaved.id!!,
            workspaceSaved.id!!,
            scenarioRetrieved.id!!,
            scenarioRetrieved.copy(name = "Scenario Updated"))
    assertEquals("Scenario Updated", scenarioUpdated.name)

    logger.info("should delete the Scenario and assert there is only one Scenario left")
    scenarioApiService.deleteScenario(
        organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved2.id!!, false)
    val scenarioListAfterDelete =
        scenarioApiService.findAllScenarios(organizationSaved.id!!, workspaceSaved.id!!, null, null)
    assertTrue(scenarioListAfterDelete.size == 1)

    logger.info("should delete all Scenarios and assert there is no Scenario left")
    scenarioApiService.deleteAllScenarios(organizationSaved.id!!, workspaceSaved.id!!)
    val scenarioListAfterDeleteAll =
        scenarioApiService.findAllScenarios(organizationSaved.id!!, workspaceSaved.id!!, null, null)
    assertTrue(scenarioListAfterDeleteAll.isEmpty())
  }

  @Test
  fun `test find All Scenarios with different pagination params`() {
    val numberOfScenarios = 20
    val defaultPageSize = csmPlatformProperties.twincache.scenario.defaultPageSize
    val expectedSize = 15
    IntRange(1, numberOfScenarios - 1).forEach {
      val scenario =
          makeScenario(
              organizationSaved.id!!,
              workspaceSaved.id!!,
              solutionSaved.id!!,
              "Scenario$it",
              mutableListOf(datasetSaved.id!!))
      scenarioApiService.createScenario(organizationSaved.id!!, workspaceSaved.id!!, scenario)
    }

    logger.info("should find all Scenarios and assert there are $numberOfScenarios")
    var scenarioList =
        scenarioApiService.findAllScenarios(organizationSaved.id!!, workspaceSaved.id!!, null, null)
    assertEquals(numberOfScenarios, scenarioList.size)

    logger.info("should find all Scenarios and assert it equals defaultPageSize: $defaultPageSize")
    scenarioList =
        scenarioApiService.findAllScenarios(organizationSaved.id!!, workspaceSaved.id!!, 0, null)
    assertEquals(scenarioList.size, defaultPageSize)

    logger.info("should find all Scenarios and assert there are expected size: $expectedSize")
    scenarioList =
        scenarioApiService.findAllScenarios(
            organizationSaved.id!!, workspaceSaved.id!!, 0, expectedSize)
    assertEquals(scenarioList.size, expectedSize)

    logger.info("should find all Scenarios and assert it returns the second / last page")
    scenarioList =
        scenarioApiService.findAllScenarios(
            organizationSaved.id!!, workspaceSaved.id!!, 1, defaultPageSize)
    assertEquals(numberOfScenarios - defaultPageSize, scenarioList.size)
  }

  @Test
  fun `test find All Scenarios with wrong pagination params`() {
    logger.info("Should throw IllegalArgumentException when page and size are zeros")
    assertThrows<IllegalArgumentException> {
      scenarioApiService.findAllScenarios(organizationSaved.id!!, workspaceSaved.id!!, 0, 0)
    }

    logger.info("Should throw IllegalArgumentException when page is negative")
    assertThrows<IllegalArgumentException> {
      scenarioApiService.findAllScenarios(organizationSaved.id!!, workspaceSaved.id!!, -1, 10)
    }

    logger.info("Should throw IllegalArgumentException when size is negative")
    assertThrows<IllegalArgumentException> {
      scenarioApiService.findAllScenarios(organizationSaved.id!!, workspaceSaved.id!!, 0, -1)
    }
  }

  @Test
  fun `test parent scenario operations as User Admin`() {

    logger.info(
        "should create a child Scenario with dataset different from parent and " +
            "assert the dataset list is the same as parent")
    val dataset = makeDataset(organizationSaved.id!!, "Dataset", connectorSaved)
    val datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)

    logger.info("should create a tree of Scenarios")
    val idMap = mutableMapOf<Int, String>()
    IntRange(1, 5).forEach {
      var scenario =
          makeScenario(
              organizationSaved.id!!,
              workspaceSaved.id!!,
              solutionSaved.id!!,
              "Scenario$it",
              mutableListOf(datasetSaved.id!!),
              if (it == 1) null else idMap[it - 1],
          )
      scenario =
          scenarioApiService.createScenario(organizationSaved.id!!, workspaceSaved.id!!, scenario)
      idMap[it] = scenario.id!!
    }
    var scenarios =
        scenarioApiService.findAllScenarios(organizationSaved.id!!, workspaceSaved.id!!, null, null)
    assertEquals(6, scenarios.size)

    logger.info("should delete last child (element 5) and assert there are 5 Scenarios left")
    scenarioApiService.deleteScenario(
        organizationSaved.id!!, workspaceSaved.id!!, idMap[5]!!, false)
    scenarios =
        scenarioApiService.findAllScenarios(organizationSaved.id!!, workspaceSaved.id!!, null, null)
    assertEquals(5, scenarios.size)

    logger.info("should insure that the parent of element 4 is element 3")
    scenario =
        scenarioApiService.findScenarioById(organizationSaved.id!!, workspaceSaved.id!!, idMap[4]!!)
    assertEquals(idMap[3], scenario.parentId)

    logger.info("should delete element 3 (in the middle) and assert there are 4 Scenarios left")
    scenarioApiService.deleteScenario(
        organizationSaved.id!!, workspaceSaved.id!!, idMap[3]!!, false)
    scenarios =
        scenarioApiService.findAllScenarios(organizationSaved.id!!, workspaceSaved.id!!, null, null)
    assertEquals(4, scenarios.size)

    logger.info("should insure that the parent of element 4 is element 2")
    scenario =
        scenarioApiService.findScenarioById(organizationSaved.id!!, workspaceSaved.id!!, idMap[4]!!)
    assertEquals(idMap[2], scenario.parentId)

    logger.info("should delete root element (element 1) and assert there are 3 Scenarios left")
    scenarioApiService.deleteScenario(
        organizationSaved.id!!, workspaceSaved.id!!, idMap[1]!!, false)
    scenarios =
        scenarioApiService.findAllScenarios(organizationSaved.id!!, workspaceSaved.id!!, null, null)
    assertEquals(3, scenarios.size)
  }

  @Test
  fun `test Scenario Parameter Values as User Admin`() {
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")

    logger.info("should assert that the Scenario has no Parameter Values")
    assertTrue(scenarioSaved.parametersValues!!.isEmpty())

    logger.info("should add a Parameter Value and assert it has been added")
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

    logger.info("should remove all Parameter Values and assert there is none left")
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

    logger.info("should test default security is set to ROLE_NONE")
    val scenarioSecurity =
        scenarioApiService.getScenarioSecurity(
            organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
    assertEquals(ROLE_NONE, scenarioSecurity.default)

    logger.info("should set default security to ROLE_VIEWER and assert it has been set")
    val scenarioRole = ScenarioRole(ROLE_VIEWER)
    val scenarioSecurityRegistered =
        scenarioApiService.setScenarioDefaultSecurity(
            organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, scenarioRole)
    assertEquals(scenarioRole.role, scenarioSecurityRegistered.default)
  }

  @Test
  fun `test RBAC ScenarioSecurity as User Unauthorized`() {
    every { getCurrentAuthenticatedMail(any()) } returns CONNECTED_READER_USER

    logger.info("should throw CsmAccessForbiddenException when trying to access ScenarioSecurity")
    // Test default security
    assertThrows<CsmAccessForbiddenException> {
      scenarioApiService.getScenarioSecurity(
          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
    }

    logger.info("should throw CsmAccessForbiddenException when trying to set default security")
    val scenarioRole = ScenarioRole(ROLE_VIEWER)
    assertThrows<CsmAccessForbiddenException> {
      scenarioApiService.setScenarioDefaultSecurity(
          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, scenarioRole)
    }
  }

  @Test
  fun `test RBAC AccessControls on Scenario as User Admin`() {

    logger.info("should add an Access Control and assert it has been added")
    val scenarioAccessControl = ScenarioAccessControl(FAKE_MAIL, ROLE_VIEWER)
    var scenarioAccessControlRegistered =
        scenarioApiService.addScenarioAccessControl(
            organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, scenarioAccessControl)
    assertEquals(scenarioAccessControl, scenarioAccessControlRegistered)

    logger.info("should get the Access Control and assert it is the one created")
    scenarioAccessControlRegistered =
        scenarioApiService.getScenarioAccessControl(
            organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, FAKE_MAIL)
    assertEquals(scenarioAccessControl, scenarioAccessControlRegistered)

    logger.info("should update the Access Control and assert it has been updated")
    scenarioAccessControlRegistered =
        scenarioApiService.updateScenarioAccessControl(
            organizationSaved.id!!,
            workspaceSaved.id!!,
            scenarioSaved.id!!,
            FAKE_MAIL,
            ScenarioRole(ROLE_EDITOR))
    assertEquals(ROLE_EDITOR, scenarioAccessControlRegistered.role)

    logger.info("should get the list of users and assert there are 2")
    var userList =
        scenarioApiService.getScenarioSecurityUsers(
            organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
    assertTrue(userList.size == 2)

    logger.info("should remove the Access Control and assert it has been removed")
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

    logger.info("should throw CsmAccessForbiddenException when trying to add ScenarioAccessControl")
    val scenarioAccessControl = ScenarioAccessControl(FAKE_MAIL, ROLE_VIEWER)
    assertThrows<CsmAccessForbiddenException> {
      scenarioApiService.addScenarioAccessControl(
          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, scenarioAccessControl)
    }

    logger.info("should throw CsmAccessForbiddenException when trying to get ScenarioAccessControl")
    assertThrows<CsmAccessForbiddenException> {
      scenarioApiService.getScenarioAccessControl(
          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, FAKE_MAIL)
    }

    logger.info(
        "should throw CsmAccessForbiddenException when trying to update ScenarioAccessControl")
    assertThrows<CsmAccessForbiddenException> {
      scenarioApiService.updateScenarioAccessControl(
          organizationSaved.id!!,
          workspaceSaved.id!!,
          scenarioSaved.id!!,
          FAKE_MAIL,
          ScenarioRole(ROLE_VIEWER))
    }

    logger.info("should throw CsmAccessForbiddenException when getting the list of users")
    assertThrows<CsmAccessForbiddenException> {
      scenarioApiService.getScenarioSecurityUsers(
          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
    }

    logger.info(
        "should throw CsmAccessForbiddenException when trying to remove ScenarioAccessControl")
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

  fun makeDataset(organizationId: String, name: String, connector: Connector): Dataset {
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

  fun makeScenario(
      organizationId: String,
      workspaceId: String,
      solutionId: String,
      name: String,
      datasetList: MutableList<String>,
      parentId: String? = null
  ): Scenario {
    return Scenario(
        name = name,
        organizationId = organizationId,
        workspaceId = workspaceId,
        solutionId = solutionId,
        ownerId = "ownerId",
        datasetList = datasetList,
        parentId = parentId,
    )
  }
}
