// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenario.service

import com.cosmotech.api.azure.adx.AzureDataExplorerClient
import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.events.CsmEventPublisher
import com.cosmotech.api.events.ScenarioDataDownloadJobInfoRequest
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.rbac.ROLE_ADMIN
import com.cosmotech.api.rbac.ROLE_EDITOR
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.rbac.ROLE_USER
import com.cosmotech.api.rbac.ROLE_VALIDATOR
import com.cosmotech.api.rbac.ROLE_VIEWER
import com.cosmotech.api.tests.CsmRedisTestBase
import com.cosmotech.api.utils.getCurrentAccountIdentifier
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
import com.cosmotech.scenario.domain.ScenarioJobState
import com.cosmotech.scenario.domain.ScenarioRole
import com.cosmotech.scenario.domain.ScenarioRunTemplateParameterValue
import com.cosmotech.scenario.domain.ScenarioSecurity
import com.cosmotech.scenario.domain.ScenarioValidationStatus
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.solution.domain.Solution
import com.cosmotech.workspace.api.WorkspaceApiService
import com.cosmotech.workspace.azure.IWorkspaceEventHubService
import com.cosmotech.workspace.azure.WorkspaceEventHubInfo
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceAccessControl
import com.cosmotech.workspace.domain.WorkspaceSecurity
import com.cosmotech.workspace.domain.WorkspaceSolution
import com.ninjasquad.springmockk.MockkBean
import com.redis.om.spring.RediSearchIndexer
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertDoesNotThrow
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
const val CONNECTED_NONE_USER = "test.none@cosmotech.com"
const val CONNECTED_EDITOR_USER = "test.editor@cosmotech.com"
const val CONNECTED_VALIDATOR_USER = "test.validator@cosmotech.com"
const val CONNECTED_READER_USER = "test.reader@cosmotech.com"
const val CONNECTED_VIEWER_USER = "test.user@cosmotech.com"
const val FAKE_MAIL = "fake@mail.fr"

@ActiveProfiles(profiles = ["scenario-test"])
@ExtendWith(MockKExtension::class)
@ExtendWith(SpringExtension::class)
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ScenarioServiceIntegrationTest : CsmRedisTestBase() {

  private val logger = LoggerFactory.getLogger(ScenarioServiceIntegrationTest::class.java)
  private val defaultName = "my.account-tester@cosmotech.com"

  @MockkBean lateinit var csmADX: AzureDataExplorerClient
  @MockK(relaxed = true) private lateinit var workspaceEventHubService: IWorkspaceEventHubService
  @MockK(relaxed = true) private lateinit var azureDataExplorerClient: AzureDataExplorerClient
  @MockK private lateinit var scenarioDataDownloadJobInfoRequest: ScenarioDataDownloadJobInfoRequest

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
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "test.user"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("user")

    ReflectionTestUtils.setField(
        scenarioApiService, "workspaceEventHubService", workspaceEventHubService)
    ReflectionTestUtils.setField(
        scenarioApiService, "azureDataExplorerClient", azureDataExplorerClient)
    every { workspaceEventHubService.getWorkspaceEventHubInfo(any(), any(), any()) } returns
        mockWorkspaceEventHubInfo(false)

    rediSearchIndexer.createIndexFor(Organization::class.java)
    rediSearchIndexer.createIndexFor(Solution::class.java)
    rediSearchIndexer.createIndexFor(Workspace::class.java)
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

    logger.info("should create a second Scenario")
    val scenario2 =
        mockScenario(
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
        organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved2.id!!)
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
          mockScenario(
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
    val dataset = mockDataset(organizationSaved.id!!, "Dataset", connectorSaved)
    val datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)

    logger.info("should create a tree of Scenarios")
    val idMap = mutableMapOf<Int, String>()
    IntRange(1, 5).forEach {
      var scenario =
          mockScenario(
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
    scenarioApiService.deleteScenario(organizationSaved.id!!, workspaceSaved.id!!, idMap[5]!!)
    scenarios =
        scenarioApiService.findAllScenarios(organizationSaved.id!!, workspaceSaved.id!!, null, null)
    assertEquals(5, scenarios.size)

    logger.info("should insure that the parent of element 4 is element 3")
    scenario =
        scenarioApiService.findScenarioById(organizationSaved.id!!, workspaceSaved.id!!, idMap[4]!!)
    assertEquals(idMap[3], scenario.parentId)

    logger.info("should delete element 3 (in the middle) and assert there are 4 Scenarios left")
    scenarioApiService.deleteScenario(organizationSaved.id!!, workspaceSaved.id!!, idMap[3]!!)
    scenarios =
        scenarioApiService.findAllScenarios(organizationSaved.id!!, workspaceSaved.id!!, null, null)
    assertEquals(4, scenarios.size)

    logger.info("should insure that the parent of element 4 is element 2")
    scenario =
        scenarioApiService.findScenarioById(organizationSaved.id!!, workspaceSaved.id!!, idMap[4]!!)
    assertEquals(idMap[2], scenario.parentId)

    logger.info("should delete root element (element 1) and assert there are 3 Scenarios left")
    scenarioApiService.deleteScenario(organizationSaved.id!!, workspaceSaved.id!!, idMap[1]!!)
    scenarios =
        scenarioApiService.findAllScenarios(organizationSaved.id!!, workspaceSaved.id!!, null, null)
    assertEquals(3, scenarios.size)

    val rootScenario =
        scenarioApiService.findScenarioById(organizationSaved.id!!, workspaceSaved.id!!, idMap[2]!!)
    logger.info("rootId for the new root scenario should be null")
    assertEquals(null, rootScenario.rootId)

    logger.info("rootId for the new root scenario should be null")
    assertEquals(null, rootScenario.parentId)

    val childScenario =
        scenarioApiService.findScenarioById(organizationSaved.id!!, workspaceSaved.id!!, idMap[4]!!)
    logger.info("rootId for element 4 should be element 2 id")
    assertEquals(rootScenario.id, childScenario.rootId)
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
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_READER_USER

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

  @TestFactory
  fun `test RBAC for findAllScenarioByValidationStatus`() =
      mapOf(
              CONNECTED_VIEWER_USER to true,
              CONNECTED_EDITOR_USER to true,
              CONNECTED_VALIDATOR_USER to true,
              CONNECTED_READER_USER to true,
              CONNECTED_NONE_USER to true,
              CONNECTED_ADMIN_USER to false)
          .map { (mail, shouldThrow) ->
            dynamicTest("Test RBAC findAllScenarioByValidationStatus : $mail") {
              every { getCurrentAccountIdentifier(any()) } returns mail
              if (shouldThrow)
                  assertThrows<Exception> {
                    scenarioApiService.findAllScenariosByValidationStatus(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        ScenarioValidationStatus.Validated,
                        null,
                        null)
                  }
              else
                  assertDoesNotThrow {
                    scenarioApiService.findAllScenariosByValidationStatus(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        ScenarioValidationStatus.Validated,
                        null,
                        null)
                  }
            }
          }

  @TestFactory
  fun `test RBAC for deleteScenario`() =
      mapOf(
              CONNECTED_VIEWER_USER to true,
              CONNECTED_EDITOR_USER to true,
              CONNECTED_VALIDATOR_USER to true,
              CONNECTED_READER_USER to true,
              CONNECTED_NONE_USER to true,
              CONNECTED_ADMIN_USER to false)
          .map { (mail, shouldThrow) ->
            dynamicTest("Test RBAC findAllScenarioByValidationStatus : $mail") {
              every { getCurrentAccountIdentifier(any()) } returns mail
              if (shouldThrow)
                  assertThrows<Exception> {
                    scenarioApiService.deleteScenario(
                        organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                  }
              else
                  assertDoesNotThrow {
                    scenarioApiService.deleteScenario(
                        organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                  }
            }
          }

  @TestFactory
  fun `test RBAC for deleteAllScenarios`() =
      mapOf(
              CONNECTED_VIEWER_USER to true,
              CONNECTED_EDITOR_USER to true,
              CONNECTED_VALIDATOR_USER to true,
              CONNECTED_READER_USER to true,
              CONNECTED_NONE_USER to true,
              CONNECTED_ADMIN_USER to false)
          .map { (mail, shouldThrow) ->
            dynamicTest("Test RBAC findAllScenarioByValidationStatus : $mail") {
              every { getCurrentAccountIdentifier(any()) } returns mail
              if (shouldThrow)
                  assertThrows<Exception> {
                    scenarioApiService.deleteAllScenarios(
                        organizationSaved.id!!, workspaceSaved.id!!)
                  }
              else
                  assertDoesNotThrow {
                    scenarioApiService.deleteAllScenarios(
                        organizationSaved.id!!, workspaceSaved.id!!)
                  }
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
    assertEquals(3, userList.size)

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

    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_READER_USER

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

  @Test
  fun `test propagation of scenario deletion in children`() {
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

    var firstChildScenario =
        scenarioApiService.createScenario(
            organizationSaved.id!!,
            workspaceSaved.id!!,
            Scenario(
                name = "firstChildScenario",
                organizationId = organizationSaved.id,
                workspaceId = workspaceSaved.id,
                solutionId = solutionSaved.id,
                ownerId = "ownerId",
                datasetList = mutableListOf(datasetSaved.id!!),
                parentId = scenarioSaved.id,
                rootId = scenarioSaved.id))

    var secondChildScenario =
        scenarioApiService.createScenario(
            organizationSaved.id!!,
            workspaceSaved.id!!,
            Scenario(
                name = "secondChildScenario",
                organizationId = organizationSaved.id,
                workspaceId = workspaceSaved.id,
                solutionId = solutionSaved.id,
                ownerId = "ownerId",
                datasetList = mutableListOf(datasetSaved.id!!),
                parentId = firstChildScenario.id,
                rootId = scenarioSaved.id))

    scenarioApiService.deleteScenario(
        organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)

    firstChildScenario =
        scenarioApiService.findScenarioById(
            organizationSaved.id!!, workspaceSaved.id!!, firstChildScenario.id!!)

    secondChildScenario =
        scenarioApiService.findScenarioById(
            organizationSaved.id!!, workspaceSaved.id!!, secondChildScenario.id!!)

    logger.info("parentId should be null")
    assertEquals(null, firstChildScenario.parentId)

    logger.info("rootId should be null")
    assertEquals(null, firstChildScenario.rootId)

    logger.info("parentId should be the firstChildScenario Id")
    assertEquals(firstChildScenario.id, secondChildScenario.parentId)

    logger.info("rootId should be the firstChildScenario Id")
    assertEquals(firstChildScenario.id, secondChildScenario.rootId)
  }

  @Test
  fun `test deleting a running scenario`() {
    scenarioSaved.state = ScenarioJobState.Running
    scenarioApiService.updateScenario(
        organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, scenarioSaved)
    assertThrows<Exception> {
      scenarioApiService.deleteScenario(
          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
    }
  }

  @Nested
  inner class RBACTests {

    @MockK private lateinit var csmEventPublisher: CsmEventPublisher

    @TestFactory
    fun `test RBAC findAllScenarios`() =
        mapOf(
                ROLE_VIEWER to false,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to false,
                ROLE_USER to false,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              dynamicTest("Test RBAC findAllScenarios : $role") {
                organizationSaved =
                    organizationApiService.registerOrganization(
                        mockOrganization(id = "id", roleName = defaultName))
                solutionSaved =
                    solutionApiService.createSolution(organizationSaved.id!!, mockSolution())

                workspaceSaved =
                    workspaceApiService.createWorkspace(
                        organizationSaved.id!!, mockWorkspace(roleName = defaultName))

                scenarioSaved =
                    scenarioApiService.createScenario(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        mockScenario(roleName = defaultName, role = role))

                every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
                every { getCurrentAccountIdentifier(any()) } returns defaultName

                val allScenarios =
                    scenarioApiService.findAllScenarios(
                        organizationSaved.id!!, workspace.id!!, null, null)

                if (shouldThrow) {
                  assertEquals(0, allScenarios.size)
                } else {
                  assertNotNull(allScenarios)
                }
              }
            }
    @TestFactory
    fun `test RBAC createScenario`() =
        mapOf(
                ROLE_VIEWER to true,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to true,
                ROLE_USER to false,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              dynamicTest("Test RBAC createScenario : $role") {
                organizationSaved =
                    organizationApiService.registerOrganization(
                        mockOrganization(id = "id", roleName = defaultName))
                solutionSaved =
                    solutionApiService.createSolution(organizationSaved.id!!, mockSolution())

                workspaceSaved =
                    workspaceApiService.createWorkspace(
                        organizationSaved.id!!, mockWorkspace(roleName = defaultName, role = role))

                every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
                every { getCurrentAccountIdentifier(any()) } returns defaultName

                scenario = mockScenario(roleName = defaultName, role = role)
                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    scenarioApiService.createScenario(
                        organizationSaved.id!!, workspaceSaved.id!!, scenario)
                  }
                } else {
                  assertDoesNotThrow {
                    scenarioApiService.createScenario(
                        organizationSaved.id!!, workspaceSaved.id!!, scenario)
                  }
                }
              }
            }
    @TestFactory
    fun `test RBAC deleteAllScenarios`() =
        mapOf(
                ROLE_VIEWER to true,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to true,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              dynamicTest("Test RBAC deleteAllScenarios : $role") {
                organizationSaved =
                    organizationApiService.registerOrganization(
                        mockOrganization(id = "id", roleName = defaultName))
                solutionSaved =
                    solutionApiService.createSolution(organizationSaved.id!!, mockSolution())

                every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
                every { getCurrentAccountIdentifier(any()) } returns defaultName

                workspaceSaved =
                    workspaceApiService.createWorkspace(
                        organizationSaved.id!!, mockWorkspace(roleName = defaultName, role = role))

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    scenarioApiService.deleteAllScenarios(
                        organizationSaved.id!!, workspaceSaved.id!!)
                  }
                } else {
                  assertDoesNotThrow {
                    scenarioApiService.deleteAllScenarios(
                        organizationSaved.id!!, workspaceSaved.id!!)
                  }
                }
              }
            }
    @TestFactory
    fun `test RBAC findAllScenariosByValidationStatus`() =
        mapOf(
                ROLE_VIEWER to false,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to false,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              dynamicTest("Test RBAC findAllScenariosByValidationStatus : $role") {
                organizationSaved =
                    organizationApiService.registerOrganization(
                        mockOrganization(id = "id", roleName = defaultName))
                solutionSaved =
                    solutionApiService.createSolution(organizationSaved.id!!, mockSolution())

                workspaceSaved =
                    workspaceApiService.createWorkspace(
                        organizationSaved.id!!, mockWorkspace(roleName = defaultName))

                scenarioSaved =
                    scenarioApiService.createScenario(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        mockScenario(roleName = defaultName, role = role))

                every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
                every { getCurrentAccountIdentifier(any()) } returns defaultName

                val allScenarios =
                    scenarioApiService.findAllScenariosByValidationStatus(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        ScenarioValidationStatus.Draft,
                        null,
                        null)

                assertTrue { allScenarios.size > 1 }
              }
            }
    @TestFactory
    fun `test RBAC getScenariosTree`() =
        mapOf(
                ROLE_VIEWER to false,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to true,
                ROLE_USER to false,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              dynamicTest("Test RBAC getScenariosTree : $role") {
                organizationSaved =
                    organizationApiService.registerOrganization(
                        mockOrganization(id = "id", roleName = defaultName))
                solutionSaved =
                    solutionApiService.createSolution(organizationSaved.id!!, mockSolution())

                workspaceSaved =
                    workspaceApiService.createWorkspace(
                        organizationSaved.id!!, mockWorkspace(roleName = defaultName, role = role))

                every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
                every { getCurrentAccountIdentifier(any()) } returns defaultName

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    scenarioApiService.getScenariosTree(organizationSaved.id!!, workspaceSaved.id!!)
                  }
                } else {
                  assertDoesNotThrow {
                    scenarioApiService.getScenariosTree(organizationSaved.id!!, workspaceSaved.id!!)
                  }
                }
              }
            }
    @TestFactory
    fun `test RBAC findScenarioById`() =
        mapOf(
                ROLE_VIEWER to false,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to false,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              dynamicTest("Test RBAC findScenarioById : $role") {
                organizationSaved =
                    organizationApiService.registerOrganization(
                        mockOrganization(id = "id", roleName = defaultName))
                solutionSaved =
                    solutionApiService.createSolution(organizationSaved.id!!, mockSolution())

                workspaceSaved =
                    workspaceApiService.createWorkspace(
                        organizationSaved.id!!, mockWorkspace(roleName = defaultName))

                every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
                every { getCurrentAccountIdentifier(any()) } returns defaultName

                scenarioSaved =
                    scenarioApiService.createScenario(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        mockScenario(roleName = defaultName, role = role))
                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    scenarioApiService.findScenarioById(
                        organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                  }
                } else {
                  assertDoesNotThrow {
                    scenarioApiService.findScenarioById(
                        organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                  }
                }
              }
            }
    @TestFactory
    fun `test RBAC deleteScenario`() =
        mapOf(
                ROLE_VIEWER to true,
                ROLE_EDITOR to true,
                ROLE_VALIDATOR to true,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              dynamicTest("Test RBAC deleteScenario : $role") {
                organizationSaved =
                    organizationApiService.registerOrganization(
                        mockOrganization(id = "id", roleName = defaultName))
                solutionSaved =
                    solutionApiService.createSolution(organizationSaved.id!!, mockSolution())

                workspaceSaved =
                    workspaceApiService.createWorkspace(
                        organizationSaved.id!!, mockWorkspace(roleName = defaultName))

                every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
                every { getCurrentAccountIdentifier(any()) } returns defaultName

                scenarioSaved =
                    scenarioApiService.createScenario(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        mockScenario(roleName = defaultName, role = role))
                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    scenarioApiService.deleteScenario(
                        organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                  }
                } else {
                  assertDoesNotThrow {
                    scenarioApiService.deleteScenario(
                        organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                  }
                }
              }
            }
    @TestFactory
    fun `test RBAC updateScenario`() =
        mapOf(
                ROLE_VIEWER to true,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to false,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              dynamicTest("Test RBAC updateScenario : $role") {
                organizationSaved =
                    organizationApiService.registerOrganization(
                        mockOrganization(id = "id", roleName = defaultName))
                solutionSaved =
                    solutionApiService.createSolution(organizationSaved.id!!, mockSolution())

                workspaceSaved =
                    workspaceApiService.createWorkspace(
                        organizationSaved.id!!, mockWorkspace(roleName = defaultName))

                every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
                every { getCurrentAccountIdentifier(any()) } returns defaultName

                scenarioSaved =
                    scenarioApiService.createScenario(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        mockScenario(roleName = defaultName, role = role))
                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    scenarioApiService.updateScenario(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        scenarioSaved.id!!,
                        mockScenario())
                  }
                } else {
                  assertDoesNotThrow {
                    scenarioApiService.updateScenario(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        scenarioSaved.id!!,
                        mockScenario())
                  }
                }
              }
            }
    @TestFactory
    fun `test RBAC getScenarioValidationStatusById`() =
        mapOf(
                ROLE_VIEWER to false,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to false,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              dynamicTest("Test RBAC getScenarioValidationStatusById : $role") {
                organizationSaved =
                    organizationApiService.registerOrganization(
                        mockOrganization(id = "id", roleName = defaultName))
                solutionSaved =
                    solutionApiService.createSolution(organizationSaved.id!!, mockSolution())

                workspaceSaved =
                    workspaceApiService.createWorkspace(
                        organizationSaved.id!!, mockWorkspace(roleName = defaultName))

                every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
                every { getCurrentAccountIdentifier(any()) } returns defaultName

                scenarioSaved =
                    scenarioApiService.createScenario(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        mockScenario(roleName = defaultName, role = role))
                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    scenarioApiService.getScenarioValidationStatusById(
                        organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                  }
                } else {
                  assertDoesNotThrow {
                    scenarioApiService.getScenarioValidationStatusById(
                        organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                  }
                }
              }
            }

    @TestFactory
    fun `test RBAC addOrReplaceScenarioParameterValues`() =
        mapOf(
                ROLE_VIEWER to true,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to false,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              dynamicTest("Test RBAC addOrReplaceScenarioParameterValues : $role") {
                organizationSaved =
                    organizationApiService.registerOrganization(
                        mockOrganization(id = "id", roleName = defaultName))
                solutionSaved =
                    solutionApiService.createSolution(organizationSaved.id!!, mockSolution())

                workspaceSaved =
                    workspaceApiService.createWorkspace(
                        organizationSaved.id!!, mockWorkspace(roleName = defaultName))

                every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
                every { getCurrentAccountIdentifier(any()) } returns defaultName

                scenarioSaved =
                    scenarioApiService.createScenario(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        mockScenario(roleName = defaultName, role = role))
                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    scenarioApiService.addOrReplaceScenarioParameterValues(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        scenarioSaved.id!!,
                        listOf(ScenarioRunTemplateParameterValue("id", "0")))
                  }
                } else {
                  assertDoesNotThrow {
                    scenarioApiService.addOrReplaceScenarioParameterValues(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        scenarioSaved.id!!,
                        listOf(ScenarioRunTemplateParameterValue("id", "0")))
                  }
                }
              }
            }
    @TestFactory
    fun `test RBAC removeAllScenarioParameterValues`() =
        mapOf(
                ROLE_VIEWER to true,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to false,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              dynamicTest("Test RBAC removeAllScenarioParameterValues : $role") {
                organizationSaved =
                    organizationApiService.registerOrganization(
                        mockOrganization(id = "id", roleName = defaultName))
                solutionSaved =
                    solutionApiService.createSolution(organizationSaved.id!!, mockSolution())

                workspaceSaved =
                    workspaceApiService.createWorkspace(
                        organizationSaved.id!!, mockWorkspace(roleName = defaultName))

                every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
                every { getCurrentAccountIdentifier(any()) } returns defaultName

                scenarioSaved =
                    scenarioApiService.createScenario(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        mockScenario(roleName = defaultName, role = role))
                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    scenarioApiService.removeAllScenarioParameterValues(
                        organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                  }
                } else {
                  assertDoesNotThrow {
                    scenarioApiService.removeAllScenarioParameterValues(
                        organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                  }
                }
              }
            }
    @TestFactory
    fun `test RBAC downloadScenarioData`() =
        mapOf(
                ROLE_VIEWER to false,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to false,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              dynamicTest("Test RBAC downloadScenarioData : $role") {
                organizationSaved =
                    organizationApiService.registerOrganization(
                        mockOrganization(id = "id", roleName = defaultName))
                solutionSaved =
                    solutionApiService.createSolution(organizationSaved.id!!, mockSolution())

                workspaceSaved =
                    workspaceApiService.createWorkspace(
                        organizationSaved.id!!, mockWorkspace(roleName = defaultName))

                every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
                every { getCurrentAccountIdentifier(any()) } returns defaultName

                scenarioSaved =
                    scenarioApiService.createScenario(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        mockScenario(roleName = defaultName, role = role))
                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    scenarioApiService.downloadScenarioData(
                        organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                  }
                } else {
                  assertDoesNotThrow {
                    scenarioApiService.downloadScenarioData(
                        organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                  }
                }
              }
            }
    @TestFactory
    fun `test RBAC getScenarioDataDownloadJobInfo`() =
        mapOf(
                ROLE_VIEWER to false,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to false,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              dynamicTest("Test RBAC getScenarioDataDownloadJobInfo : $role") {
                organizationSaved =
                    organizationApiService.registerOrganization(
                        mockOrganization(id = "id", roleName = defaultName))
                solutionSaved =
                    solutionApiService.createSolution(organizationSaved.id!!, mockSolution())

                workspaceSaved =
                    workspaceApiService.createWorkspace(
                        organizationSaved.id!!, mockWorkspace(roleName = defaultName))

                every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
                every { getCurrentAccountIdentifier(any()) } returns defaultName
                mockkConstructor(ScenarioDataDownloadJobInfoRequest::class)
                every { anyConstructed<ScenarioDataDownloadJobInfoRequest>().response } returns
                    ("" to "")

                scenarioSaved =
                    scenarioApiService.createScenario(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        mockScenario(roleName = defaultName, role = role))

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    scenarioApiService.getScenarioDataDownloadJobInfo(
                        organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, "jobId")
                  }
                } else {
                  assertDoesNotThrow {
                    scenarioApiService.getScenarioDataDownloadJobInfo(
                        organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, "jobid")
                  }
                }
              }
            }
    @TestFactory
    fun `test RBAC getScenarioPermissions`() =
        mapOf(
                ROLE_VIEWER to false,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to false,
                ROLE_USER to false,
                ROLE_NONE to false,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              dynamicTest("Test RBAC getScenarioPermissions : $role") {
                organizationSaved =
                    organizationApiService.registerOrganization(
                        mockOrganization(id = "id", roleName = defaultName))
                solutionSaved =
                    solutionApiService.createSolution(organizationSaved.id!!, mockSolution())

                workspaceSaved =
                    workspaceApiService.createWorkspace(
                        organizationSaved.id!!, mockWorkspace(roleName = defaultName))

                every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
                every { getCurrentAccountIdentifier(any()) } returns defaultName

                scenarioSaved =
                    scenarioApiService.createScenario(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        mockScenario(roleName = defaultName, role = role))

                assertDoesNotThrow {
                  scenarioApiService.getScenarioPermissions(
                      organizationSaved.id!!, workspaceSaved.id!!, role)
                }
              }
            }
    @TestFactory
    fun `test RBAC getScenarioSecurity`() =
        mapOf(
                ROLE_VIEWER to false,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to false,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              dynamicTest("Test RBAC getScenarioSecurity : $role") {
                organizationSaved =
                    organizationApiService.registerOrganization(
                        mockOrganization(id = "id", roleName = defaultName))
                solutionSaved =
                    solutionApiService.createSolution(organizationSaved.id!!, mockSolution())

                workspaceSaved =
                    workspaceApiService.createWorkspace(
                        organizationSaved.id!!, mockWorkspace(roleName = defaultName))

                every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
                every { getCurrentAccountIdentifier(any()) } returns defaultName

                scenarioSaved =
                    scenarioApiService.createScenario(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        mockScenario(roleName = defaultName, role = role))
                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    scenarioApiService.getScenarioSecurity(
                        organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                  }
                } else {
                  assertDoesNotThrow {
                    scenarioApiService.getScenarioSecurity(
                        organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                  }
                }
              }
            }
    @TestFactory
    fun `test RBAC setScenarioDefaultSecurity`() =
        mapOf(
                ROLE_VIEWER to true,
                ROLE_EDITOR to true,
                ROLE_VALIDATOR to true,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              dynamicTest("Test RBAC setScenarioDefaultSecurity : $role") {
                organizationSaved =
                    organizationApiService.registerOrganization(
                        mockOrganization(id = "id", roleName = defaultName))
                solutionSaved =
                    solutionApiService.createSolution(organizationSaved.id!!, mockSolution())

                workspaceSaved =
                    workspaceApiService.createWorkspace(
                        organizationSaved.id!!, mockWorkspace(roleName = defaultName))

                every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
                every { getCurrentAccountIdentifier(any()) } returns defaultName

                scenarioSaved =
                    scenarioApiService.createScenario(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        mockScenario(roleName = defaultName, role = role))
                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    scenarioApiService.setScenarioDefaultSecurity(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        scenarioSaved.id!!,
                        ScenarioRole(ROLE_ADMIN))
                  }
                } else {
                  assertDoesNotThrow {
                    scenarioApiService.setScenarioDefaultSecurity(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        scenarioSaved.id!!,
                        ScenarioRole(ROLE_ADMIN))
                  }
                }
              }
            }
    @TestFactory
    fun `test RBAC addScenarioAccessControl`() =
        mapOf(
                ROLE_VIEWER to true,
                ROLE_EDITOR to true,
                ROLE_VALIDATOR to true,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              dynamicTest("Test RBAC addScenarioAccessControl : $role") {
                organizationSaved =
                    organizationApiService.registerOrganization(
                        mockOrganization(id = "id", roleName = defaultName))
                solutionSaved =
                    solutionApiService.createSolution(organizationSaved.id!!, mockSolution())

                workspaceSaved =
                    workspaceApiService.createWorkspace(
                        organizationSaved.id!!, mockWorkspace(roleName = defaultName))

                every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
                every { getCurrentAccountIdentifier(any()) } returns defaultName

                scenarioSaved =
                    scenarioApiService.createScenario(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        mockScenario(roleName = defaultName, role = role))
                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    scenarioApiService.addScenarioAccessControl(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        scenarioSaved.id!!,
                        ScenarioAccessControl("id", ROLE_ADMIN))
                  }
                } else {
                  assertDoesNotThrow {
                    scenarioApiService.addScenarioAccessControl(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        scenarioSaved.id!!,
                        ScenarioAccessControl("id", ROLE_ADMIN))
                  }
                }
              }
            }
    @TestFactory
    fun `test RBAC getScenarioAccessControl`() =
        mapOf(
                ROLE_VIEWER to false,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to false,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              dynamicTest("Test RBAC getScenarioAccessControl : $role") {
                organizationSaved =
                    organizationApiService.registerOrganization(
                        mockOrganization(id = "id", roleName = defaultName))
                solutionSaved =
                    solutionApiService.createSolution(organizationSaved.id!!, mockSolution())

                workspaceSaved =
                    workspaceApiService.createWorkspace(
                        organizationSaved.id!!, mockWorkspace(roleName = defaultName))

                every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
                every { getCurrentAccountIdentifier(any()) } returns defaultName

                scenarioSaved =
                    scenarioApiService.createScenario(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        mockScenario(roleName = defaultName, role = role))
                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    scenarioApiService.getScenarioAccessControl(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        scenarioSaved.id!!,
                        defaultName)
                  }
                } else {
                  assertDoesNotThrow {
                    scenarioApiService.getScenarioAccessControl(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        scenarioSaved.id!!,
                        defaultName)
                  }
                }
              }
            }
    @TestFactory
    fun `test RBAC removeScenarioAccessControl`() =
        mapOf(
                ROLE_VIEWER to true,
                ROLE_EDITOR to true,
                ROLE_VALIDATOR to true,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              dynamicTest("Test RBAC removeScenarioAccessControl : $role") {
                organizationSaved =
                    organizationApiService.registerOrganization(
                        mockOrganization(id = "id", roleName = defaultName))
                solutionSaved =
                    solutionApiService.createSolution(organizationSaved.id!!, mockSolution())

                workspaceSaved =
                    workspaceApiService.createWorkspace(
                        organizationSaved.id!!, mockWorkspace(roleName = defaultName))

                every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
                every { getCurrentAccountIdentifier(any()) } returns defaultName

                scenarioSaved =
                    scenarioApiService.createScenario(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        mockScenario(roleName = defaultName, role = role))
                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    scenarioApiService.removeScenarioAccessControl(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        scenarioSaved.id!!,
                        defaultName)
                  }
                } else {
                  assertDoesNotThrow {
                    scenarioApiService.removeScenarioAccessControl(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        scenarioSaved.id!!,
                        defaultName)
                  }
                }
              }
            }
    @TestFactory
    fun `test RBAC updateScenarioAccessControl`() =
        mapOf(
                ROLE_VIEWER to true,
                ROLE_EDITOR to true,
                ROLE_VALIDATOR to true,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              dynamicTest("Test RBAC updateScenarioAccessControl : $role") {
                organizationSaved =
                    organizationApiService.registerOrganization(
                        mockOrganization(id = "id", roleName = defaultName))
                solutionSaved =
                    solutionApiService.createSolution(organizationSaved.id!!, mockSolution())

                workspaceSaved =
                    workspaceApiService.createWorkspace(
                        organizationSaved.id!!, mockWorkspace(roleName = defaultName))

                every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
                every { getCurrentAccountIdentifier(any()) } returns defaultName

                scenarioSaved =
                    scenarioApiService.createScenario(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        mockScenario(roleName = defaultName, role = role))
                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    scenarioApiService.updateScenarioAccessControl(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        scenarioSaved.id!!,
                        defaultName,
                        ScenarioRole(ROLE_VIEWER))
                  }
                } else {
                  assertDoesNotThrow {
                    scenarioApiService.updateScenarioAccessControl(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        scenarioSaved.id!!,
                        defaultName,
                        ScenarioRole(ROLE_VIEWER))
                  }
                }
              }
            }
    @TestFactory
    fun `test RBAC getScenarioSecurityUsers`() =
        mapOf(
                ROLE_VIEWER to false,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to false,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              dynamicTest("Test RBAC getScenarioSecurityUsers : $role") {
                organizationSaved =
                    organizationApiService.registerOrganization(
                        mockOrganization(id = "id", roleName = defaultName))
                solutionSaved =
                    solutionApiService.createSolution(organizationSaved.id!!, mockSolution())

                workspaceSaved =
                    workspaceApiService.createWorkspace(
                        organizationSaved.id!!, mockWorkspace(roleName = defaultName))

                every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
                every { getCurrentAccountIdentifier(any()) } returns defaultName

                scenarioSaved =
                    scenarioApiService.createScenario(
                        organizationSaved.id!!,
                        workspaceSaved.id!!,
                        mockScenario(roleName = defaultName, role = role))
                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    scenarioApiService.getScenarioSecurityUsers(
                        organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                  }
                } else {
                  assertDoesNotThrow {
                    scenarioApiService.getScenarioSecurityUsers(
                        organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
                  }
                }
              }
            }
    @TestFactory
    fun `test RBAC importScenario`() =
        mapOf(
                ROLE_VIEWER to false,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to false,
                ROLE_USER to false,
                ROLE_NONE to false,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              dynamicTest("Test RBAC importScenario : $role") {
                organizationSaved =
                    organizationApiService.registerOrganization(
                        mockOrganization(id = "id", roleName = defaultName))
                solutionSaved =
                    solutionApiService.createSolution(organizationSaved.id!!, mockSolution())

                workspaceSaved =
                    workspaceApiService.createWorkspace(
                        organizationSaved.id!!, mockWorkspace(roleName = defaultName))

                every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
                every { getCurrentAccountIdentifier(any()) } returns defaultName

                scenario = mockScenario(roleName = defaultName, role = role)

                assertDoesNotThrow {
                  scenarioApiService.importScenario(
                      organizationSaved.id!!, workspaceSaved.id!!, scenario)
                }
              }
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

  private fun mockConnector(name: String = "name"): Connector {
    return Connector(
        key = UUID.randomUUID().toString(),
        name = name,
        repository = "/repository",
        version = "1.0",
        ioTypes = listOf(Connector.IoTypes.read))
  }

  fun mockDataset(
      organizationId: String = organizationSaved.id!!,
      name: String = "name",
      connector: Connector = connectorSaved
  ): Dataset {
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

  fun mockSolution(organizationId: String = organizationSaved.id!!): Solution {
    return Solution(
        id = "solutionId",
        key = UUID.randomUUID().toString(),
        name = "My solution",
        organizationId = organizationId,
        ownerId = "ownerId")
  }

  fun mockOrganization(
      id: String = "id",
      roleName: String = "roleName",
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
                        OrganizationAccessControl(id = CONNECTED_READER_USER, role = "reader"),
                        OrganizationAccessControl(id = CONNECTED_ADMIN_USER, role = "admin"),
                        OrganizationAccessControl(id = roleName, role = role))))
  }

  fun mockWorkspace(
      organizationId: String = organizationSaved.id!!,
      solutionId: String = solutionSaved.id!!,
      name: String = "name",
      roleName: String = defaultName,
      role: String = ROLE_ADMIN
  ): Workspace {
    return Workspace(
        key = UUID.randomUUID().toString(),
        name = name,
        solution =
            WorkspaceSolution(
                solutionId = solutionId,
            ),
        organizationId = organizationId,
        ownerId = "ownerId",
        security =
            WorkspaceSecurity(
                default = ROLE_NONE,
                mutableListOf(
                    WorkspaceAccessControl(id = roleName, role = role),
                    WorkspaceAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN))))
  }

  fun mockScenario(
      organizationId: String = organizationSaved.id!!,
      workspaceId: String = workspaceSaved.id!!,
      solutionId: String = solutionSaved.id!!,
      name: String = "name",
      datasetList: MutableList<String> = mutableListOf<String>(),
      parentId: String? = null,
      roleName: String = "roleName",
      role: String = ROLE_USER,
      validationStatus: ScenarioValidationStatus = ScenarioValidationStatus.Draft
  ): Scenario {
    return Scenario(
        id = UUID.randomUUID().toString(),
        name = name,
        organizationId = organizationId,
        workspaceId = workspaceId,
        solutionId = solutionId,
        ownerId = "ownerId",
        datasetList = datasetList,
        parentId = parentId,
        validationStatus = validationStatus,
        security =
            ScenarioSecurity(
                ROLE_NONE,
                mutableListOf(
                    ScenarioAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                    ScenarioAccessControl(roleName, role))))
  }
}
