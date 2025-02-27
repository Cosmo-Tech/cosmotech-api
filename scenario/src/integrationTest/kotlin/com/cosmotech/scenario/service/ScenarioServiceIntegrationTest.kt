// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
@file:Suppress("DEPRECATION")

package com.cosmotech.scenario.service

import com.cosmotech.api.azure.adx.AzureDataExplorerClient
import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.events.ScenarioDataDownloadJobInfoRequest
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.rbac.ROLE_ADMIN
import com.cosmotech.api.rbac.ROLE_EDITOR
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.rbac.ROLE_USER
import com.cosmotech.api.rbac.ROLE_VIEWER
import com.cosmotech.api.tests.CsmRedisTestBase
import com.cosmotech.api.utils.getCurrentAccountIdentifier
import com.cosmotech.api.utils.getCurrentAuthenticatedRoles
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.connector.ConnectorApiServiceInterface
import com.cosmotech.connector.domain.Connector
import com.cosmotech.connector.domain.IoTypesEnum
import com.cosmotech.dataset.DatasetApiServiceInterface
import com.cosmotech.dataset.domain.*
import com.cosmotech.dataset.repository.DatasetRepository
import com.cosmotech.dataset.service.getRbac
import com.cosmotech.organization.OrganizationApiServiceInterface
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationSecurity
import com.cosmotech.scenario.ScenarioApiServiceInterface
import com.cosmotech.scenario.domain.Scenario
import com.cosmotech.scenario.domain.ScenarioAccessControl
import com.cosmotech.scenario.domain.ScenarioJobState
import com.cosmotech.scenario.domain.ScenarioRole
import com.cosmotech.scenario.domain.ScenarioRunTemplateParameterValue
import com.cosmotech.scenario.domain.ScenarioSecurity
import com.cosmotech.scenario.domain.ScenarioValidationStatus
import com.cosmotech.solution.SolutionApiServiceInterface
import com.cosmotech.solution.domain.*
import com.cosmotech.workspace.WorkspaceApiServiceInterface
import com.cosmotech.workspace.azure.IWorkspaceEventHubService
import com.cosmotech.workspace.azure.WorkspaceEventHubInfo
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceAccessControl
import com.cosmotech.workspace.domain.WorkspaceSecurity
import com.cosmotech.workspace.domain.WorkspaceSolution
import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.SpykBean
import com.redis.om.spring.RediSearchIndexer
import com.redis.testcontainers.RedisStackContainer
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockkStatic
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest.dynamicTest
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
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.Protocol
import redis.clients.jedis.UnifiedJedis

@ActiveProfiles(profiles = ["scenario-test"])
@ExtendWith(MockKExtension::class)
@ExtendWith(SpringExtension::class)
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ScenarioServiceIntegrationTest : CsmRedisTestBase() {

  val CONNECTED_ADMIN_USER = "test.admin@cosmotech.com"
  val CONNECTED_NONE_USER = "test.none@cosmotech.com"
  val CONNECTED_EDITOR_USER = "test.editor@cosmotech.com"
  val CONNECTED_VALIDATOR_USER = "test.validator@cosmotech.com"
  val CONNECTED_READER_USER = "test.reader@cosmotech.com"
  val CONNECTED_VIEWER_USER = "test.user@cosmotech.com"
  val TEST_USER_MAIL = "fake@mail.fr"

  private val logger = LoggerFactory.getLogger(ScenarioServiceIntegrationTest::class.java)
  private val defaultName = "my.account-tester@cosmotech.com"

  @MockkBean lateinit var csmADX: AzureDataExplorerClient
  @MockK(relaxed = true) private lateinit var workspaceEventHubService: IWorkspaceEventHubService
  @MockK(relaxed = true) private lateinit var azureDataExplorerClient: AzureDataExplorerClient
  @MockK private lateinit var scenarioDataDownloadJobInfoRequest: ScenarioDataDownloadJobInfoRequest

  @Autowired lateinit var rediSearchIndexer: RediSearchIndexer
  @Autowired lateinit var connectorApiService: ConnectorApiServiceInterface
  @Autowired lateinit var organizationApiService: OrganizationApiServiceInterface
  @SpykBean @Autowired lateinit var datasetApiService: DatasetApiServiceInterface
  @Autowired lateinit var datasetRepository: DatasetRepository
  @Autowired lateinit var solutionApiService: SolutionApiServiceInterface
  @Autowired lateinit var workspaceApiService: WorkspaceApiServiceInterface
  @Autowired lateinit var scenarioApiService: ScenarioApiServiceInterface
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

  lateinit var unifiedJedis: UnifiedJedis

  @BeforeAll
  fun beforeAll() {
    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
    mockkStatic("com.cosmotech.api.utils.RedisUtilsKt")
    mockkStatic("org.springframework.web.context.request.RequestContextHolder")
    val context = getContext(redisStackServer)
    val containerIp =
        (context.server as RedisStackContainer).containerInfo.networkSettings.ipAddress
    unifiedJedis = UnifiedJedis(HostAndPort(containerIp, Protocol.DEFAULT_PORT))
    ReflectionTestUtils.setField(datasetApiService, "unifiedJedis", unifiedJedis)
  }

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
        makeWorkspaceEventHubInfo(false)

    rediSearchIndexer.createIndexFor(Organization::class.java)
    rediSearchIndexer.createIndexFor(Connector::class.java)
    rediSearchIndexer.createIndexFor(Dataset::class.java)
    rediSearchIndexer.createIndexFor(Solution::class.java)
    rediSearchIndexer.createIndexFor(Workspace::class.java)
    rediSearchIndexer.createIndexFor(Scenario::class.java)

    connector = makeConnector("Connector")
    connectorSaved = connectorApiService.registerConnector(connector)

    organization = makeOrganization("Organization")
    organizationSaved = organizationApiService.registerOrganization(organization)

    dataset = makeDataset(organizationSaved.id!!, "Dataset", connectorSaved)
    datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
    materializeTwingraph()

    solution = makeSolution(organizationSaved.id!!)
    solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

    workspace = makeWorkspace(organizationSaved.id!!, solutionSaved.id!!, "Workspace")
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
        organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved2.id!!)
    val scenarioListAfterDelete =
        scenarioApiService.findAllScenarios(organizationSaved.id!!, workspaceSaved.id!!, null, null)
    assertTrue(scenarioListAfterDelete.size == 1)

    // We create more scenario than there can be on one page of default size to assert
    // deleteAllScenarios still works with high quantities of scenarios
    repeat(csmPlatformProperties.twincache.scenario.defaultPageSize + 1) {
      scenarioApiService.createScenario(organizationSaved.id!!, workspaceSaved.id!!, makeScenario())
    }

    logger.info("should delete all Scenarios and assert there is no Scenario left")
    scenarioApiService.deleteAllScenarios(organizationSaved.id!!, workspaceSaved.id!!)
    val scenarioListAfterDeleteAll =
        scenarioApiService.findAllScenarios(organizationSaved.id!!, workspaceSaved.id!!, null, null)
    assertTrue(scenarioListAfterDeleteAll.isEmpty())
  }

  @Test
  fun `test createScenario without parameters`() {
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")

    val scenarioWithoutParameters =
        makeScenario(
            organizationSaved.id!!,
            workspaceSaved.id!!,
            solutionSaved.id!!,
            "ScenarioWithoutParameters",
            mutableListOf(datasetSaved.id!!))

    val scenarioWithoutParametersSaved =
        scenarioApiService.createScenario(
            organizationSaved.id!!, workspaceSaved.id!!, scenarioWithoutParameters)

    assertNotNull(scenarioWithoutParametersSaved.parametersValues)
    assertTrue(scenarioWithoutParametersSaved.parametersValues!!.size == 2)
    assertEquals("param1", scenarioWithoutParametersSaved.parametersValues!![0].parameterId)
    assertEquals("param1VarType", scenarioWithoutParametersSaved.parametersValues!![0].varType)
    assertEquals("110", scenarioWithoutParametersSaved.parametersValues!![0].value)
    assertEquals("param2", scenarioWithoutParametersSaved.parametersValues!![1].parameterId)
    assertEquals("param2VarType", scenarioWithoutParametersSaved.parametersValues!![1].varType)
    assertEquals("", scenarioWithoutParametersSaved.parametersValues!![1].value)
  }

  @Test
  fun `test createScenario with parameters`() {
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")

    val scenarioWithParameters =
        makeScenario(
            organizationSaved.id!!,
            workspaceSaved.id!!,
            solutionSaved.id!!,
            "ScenarioWithParameters",
            mutableListOf(datasetSaved.id!!),
            parametersValues =
                mutableListOf(
                    ScenarioRunTemplateParameterValue(
                        parameterId = "param1", value = "105", varType = "exclude_read_only_value"),
                    ScenarioRunTemplateParameterValue(
                        parameterId = "not_defined_parameter",
                        value = "105",
                        varType = "exclude_read_only_value")))

    val scenarioWithParametersSaved =
        scenarioApiService.createScenario(
            organizationSaved.id!!, workspaceSaved.id!!, scenarioWithParameters)

    assertNotNull(scenarioWithParametersSaved.parametersValues)
    assertTrue(scenarioWithParametersSaved.parametersValues!!.size == 2)
    assertEquals("param1", scenarioWithParametersSaved.parametersValues!![0].parameterId)
    assertEquals("param1VarType", scenarioWithParametersSaved.parametersValues!![0].varType)
    assertEquals("105", scenarioWithParametersSaved.parametersValues!![0].value)
    assertEquals("param2", scenarioWithParametersSaved.parametersValues!![1].parameterId)
    assertEquals("param2VarType", scenarioWithParametersSaved.parametersValues!![1].varType)
    assertEquals("", scenarioWithParametersSaved.parametersValues!![1].value)
  }

  @Test
  fun `test updateScenario with empty parameters`() {
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")

    val scenarioWithoutParameters =
        makeScenario(
            organizationSaved.id!!,
            workspaceSaved.id!!,
            solutionSaved.id!!,
            "ScenarioWithoutParameters",
            mutableListOf(datasetSaved.id!!))

    val scenarioWithoutParametersSaved =
        scenarioApiService.createScenario(
            organizationSaved.id!!, workspaceSaved.id!!, scenarioWithoutParameters)

    val updatedScenarioWithoutParametersSaved =
        scenarioApiService.updateScenario(
            organizationSaved.id!!,
            workspaceSaved.id!!,
            scenarioWithoutParametersSaved.id!!,
            scenarioWithoutParametersSaved.apply {
              parametersValues =
                  mutableListOf(
                      ScenarioRunTemplateParameterValue(
                          parameterId = "param1",
                          value = "107",
                          varType = "exclude_read_only_value"),
                      ScenarioRunTemplateParameterValue(
                          parameterId = "not_defined_parameter",
                          value = "105",
                          varType = "exclude_read_only_value"))
            })

    assertNotNull(updatedScenarioWithoutParametersSaved.parametersValues)
    assertTrue(updatedScenarioWithoutParametersSaved.parametersValues!!.size == 2)
    assertEquals("param1", updatedScenarioWithoutParametersSaved.parametersValues!![0].parameterId)
    assertEquals(
        "param1VarType", updatedScenarioWithoutParametersSaved.parametersValues!![0].varType)
    assertEquals("107", updatedScenarioWithoutParametersSaved.parametersValues!![0].value)
    assertEquals("param2", updatedScenarioWithoutParametersSaved.parametersValues!![1].parameterId)
    assertEquals(
        "param2VarType", updatedScenarioWithoutParametersSaved.parametersValues!![1].varType)
    assertEquals("", updatedScenarioWithoutParametersSaved.parametersValues!![1].value)
  }

  @Test
  fun `test updateScenario with non-empty parameters`() {
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")

    val scenarioWithParameters =
        makeScenario(
            organizationSaved.id!!,
            workspaceSaved.id!!,
            solutionSaved.id!!,
            "ScenarioWithParameters",
            mutableListOf(datasetSaved.id!!),
            parametersValues =
                mutableListOf(
                    ScenarioRunTemplateParameterValue(
                        parameterId = "param1", value = "100", varType = "exclude_read_only_value"),
                    ScenarioRunTemplateParameterValue(
                        parameterId = "param2",
                        value = "this_is_a_value",
                        varType = "exclude_read_only_value")))

    val scenarioWithParametersSaved =
        scenarioApiService.createScenario(
            organizationSaved.id!!, workspaceSaved.id!!, scenarioWithParameters)

    val updatedScenarioWithParametersSaved =
        scenarioApiService.updateScenario(
            organizationSaved.id!!,
            workspaceSaved.id!!,
            scenarioWithParametersSaved.id!!,
            scenarioWithParametersSaved.apply {
              parametersValues =
                  mutableListOf(
                      ScenarioRunTemplateParameterValue(
                          parameterId = "param1",
                          value = "107",
                          varType = "exclude_read_only_value"),
                      ScenarioRunTemplateParameterValue(
                          parameterId = "not_defined_parameter",
                          value = "105",
                          varType = "exclude_read_only_value"))
            })

    assertNotNull(updatedScenarioWithParametersSaved.parametersValues)
    assertTrue(updatedScenarioWithParametersSaved.parametersValues!!.size == 2)
    assertEquals("param1", updatedScenarioWithParametersSaved.parametersValues!![0].parameterId)
    assertEquals("param1VarType", updatedScenarioWithParametersSaved.parametersValues!![0].varType)
    assertEquals("107", updatedScenarioWithParametersSaved.parametersValues!![0].value)
    assertEquals("param2", updatedScenarioWithParametersSaved.parametersValues!![1].parameterId)
    assertEquals("param2VarType", updatedScenarioWithParametersSaved.parametersValues!![1].varType)
    assertEquals("", updatedScenarioWithParametersSaved.parametersValues!![1].value)
  }

  @Test
  fun `test find All Scenarios with different pagination params`() {
    val numberOfScenarios = 20
    val defaultPageSize = csmPlatformProperties.twincache.scenario.defaultPageSize
    val expectedSize = 15
    datasetSaved = materializeTwingraph()
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
    assertEquals(expectedSize, scenarioList.size)

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

    logger.info("should create a child Scenario with dataset different from parent")
    logger.info("Create a tree of Scenarios")
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
    val scenarioAccessControl = ScenarioAccessControl(TEST_USER_MAIL, ROLE_VIEWER)
    var scenarioAccessControlRegistered =
        scenarioApiService.addScenarioAccessControl(
            organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, scenarioAccessControl)
    assertEquals(scenarioAccessControl, scenarioAccessControlRegistered)

    logger.info("should get the Access Control and assert it is the one created")
    scenarioAccessControlRegistered =
        scenarioApiService.getScenarioAccessControl(
            organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, TEST_USER_MAIL)
    assertEquals(scenarioAccessControl, scenarioAccessControlRegistered)

    logger.info(
        "should add an Access Control and assert it is the one created in the linked datasets")
    scenarioSaved.datasetList!!.forEach {
      assertDoesNotThrow {
        datasetApiService.getDatasetAccessControl(organizationSaved.id!!, it, TEST_USER_MAIL)
      }
    }

    logger.info("should update the Access Control and assert it has been updated")
    scenarioAccessControlRegistered =
        scenarioApiService.updateScenarioAccessControl(
            organizationSaved.id!!,
            workspaceSaved.id!!,
            scenarioSaved.id!!,
            TEST_USER_MAIL,
            ScenarioRole(ROLE_EDITOR))
    assertEquals(ROLE_EDITOR, scenarioAccessControlRegistered.role)

    logger.info(
        "should update the Access Control and assert it has been updated in the linked datasets")
    scenarioSaved.datasetList!!.forEach {
      assertEquals(
          ROLE_EDITOR,
          datasetApiService
              .getDatasetAccessControl(organizationSaved.id!!, it, TEST_USER_MAIL)
              .role)
    }

    logger.info("should get the list of users and assert there are 2")
    var userList =
        scenarioApiService.getScenarioSecurityUsers(
            organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
    assertEquals(3, userList.size)

    logger.info("should remove the Access Control and assert it has been removed")
    scenarioApiService.removeScenarioAccessControl(
        organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, TEST_USER_MAIL)
    assertThrows<CsmResourceNotFoundException> {
      scenarioAccessControlRegistered =
          scenarioApiService.getScenarioAccessControl(
              organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, TEST_USER_MAIL)
    }

    logger.info(
        "should remove the Access Control and assert it has been removed in the linked datasets")
    scenarioSaved.datasetList!!.forEach {
      assertThrows<CsmResourceNotFoundException> {
        datasetApiService.getDatasetAccessControl(organizationSaved.id!!, it, TEST_USER_MAIL)
      }
    }
  }

  @Test
  fun `test RBAC AccessControls on Scenario as User Unauthorized`() {

    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_READER_USER

    logger.info("should throw CsmAccessForbiddenException when trying to add ScenarioAccessControl")
    val scenarioAccessControl = ScenarioAccessControl(TEST_USER_MAIL, ROLE_VIEWER)
    assertThrows<CsmAccessForbiddenException> {
      scenarioApiService.addScenarioAccessControl(
          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, scenarioAccessControl)
    }

    logger.info("should throw CsmAccessForbiddenException when trying to get ScenarioAccessControl")
    assertThrows<CsmAccessForbiddenException> {
      scenarioApiService.getScenarioAccessControl(
          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, TEST_USER_MAIL)
    }

    logger.info(
        "should throw CsmAccessForbiddenException when trying to update ScenarioAccessControl")
    assertThrows<CsmAccessForbiddenException> {
      scenarioApiService.updateScenarioAccessControl(
          organizationSaved.id!!,
          workspaceSaved.id!!,
          scenarioSaved.id!!,
          TEST_USER_MAIL,
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
          organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, TEST_USER_MAIL)
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

  @Test
  fun `test deleting dataset with scenario`() {
    scenarioApiService.deleteScenario(
        organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)

    assertDoesNotThrow {
      datasetApiService.findDatasetById(organizationSaved.id!!, datasetSaved.id!!)
    }
    scenarioSaved.datasetList!!.forEach { dataset ->
      assertThrows<CsmResourceNotFoundException> {
        datasetApiService.findDatasetById(organizationSaved.id!!, dataset)
      }
    }
  }

  @Test
  fun `test do not delete old style dataset with scenario`() {
    // old style dataset doesn't have the creationDate property set
    // set all dataset in scenario datasetList to old style
    scenarioSaved.datasetList!!.forEach { datasetId ->
      val scenarioDataset = datasetApiService.findDatasetById(organizationSaved.id!!, datasetId)
      datasetRepository.save(scenarioDataset.apply { creationDate = null })
    }

    scenarioApiService.deleteScenario(
        organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)

    assertDoesNotThrow {
      datasetApiService.findDatasetById(organizationSaved.id!!, datasetSaved.id!!)
    }
    scenarioSaved.datasetList!!.forEach { dataset ->
      assertDoesNotThrow { datasetApiService.findDatasetById(organizationSaved.id!!, dataset) }
    }
  }

  @Test
  fun `test updating datasetList and assert the accessControlList has the correct users`() {
    val newDataset =
        datasetApiService.createSubDataset(
            organizationSaved.id!!,
            datasetSaved.id!!,
            SubDatasetGraphQuery(
                name = "Copy of datasetSaved", queries = mutableListOf("FAKE Query"), main = false))
    scenarioSaved =
        scenarioApiService.updateScenario(
            organizationSaved.id!!,
            workspaceSaved.id!!,
            scenarioSaved.id!!,
            scenario.copy(datasetList = mutableListOf(datasetSaved.id!!, newDataset.id!!)))

    val scenarioUserList =
        scenarioApiService.getScenarioSecurityUsers(
            organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)

    scenarioSaved.datasetList!!.forEach { thisDataset ->
      val dataset =
          datasetApiService.findByOrganizationIdAndDatasetId(organizationSaved.id!!, thisDataset)
      assertNotNull(dataset)
      val datasetUserList = dataset.getRbac().accessControlList
      if (dataset.main == null || dataset.main == true) {
        datasetUserList.map { it.id }.forEach { assertTrue(scenarioUserList.contains(it)) }
      } else {
        assertTrue(datasetUserList.map { it.id }.containsAll(scenarioUserList))
      }
    }
  }

  @Test
  fun `access control list shouldn't contain more than one time each user on creation`() {
    organizationSaved =
        organizationApiService.registerOrganization(makeOrganization("organization"))
    solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, makeSolution())
    workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id!!, makeWorkspace())
    val brokenScenario =
        Scenario(
            name = "scenario",
            security =
                ScenarioSecurity(
                    default = ROLE_NONE,
                    accessControlList =
                        mutableListOf(
                            ScenarioAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                            ScenarioAccessControl(CONNECTED_ADMIN_USER, ROLE_EDITOR))))
    assertThrows<IllegalArgumentException> {
      scenarioApiService.createScenario(organizationSaved.id!!, workspaceSaved.id!!, brokenScenario)
    }
  }

  @Test
  fun `access control list shouldn't contain more than one time each user on ACL addition`() {
    organizationSaved =
        organizationApiService.registerOrganization(makeOrganization("organization"))
    solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, makeSolution())
    workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id!!, makeWorkspace())
    val workingScenario = makeScenario()
    scenarioSaved =
        scenarioApiService.createScenario(
            organizationSaved.id!!, workspaceSaved.id!!, workingScenario)

    assertThrows<IllegalArgumentException> {
      scenarioApiService.addScenarioAccessControl(
          organizationSaved.id!!,
          workspaceSaved.id!!,
          scenarioSaved.id!!,
          ScenarioAccessControl(CONNECTED_ADMIN_USER, ROLE_EDITOR))
    }
  }

  @Test
  fun `when workspace datasetCopy is true, linked datasets should be deleted`() {
    workspace =
        Workspace(
            key = "key",
            name = "workspace",
            solution = WorkspaceSolution(solutionSaved.id!!),
            id = "id",
            datasetCopy = true)
    workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
    scenario = makeScenario(datasetList = mutableListOf(datasetSaved.id!!))
    scenarioSaved =
        scenarioApiService.createScenario(organizationSaved.id!!, workspaceSaved.id!!, scenario)

    datasetSaved =
        datasetApiService.findDatasetById(organizationSaved.id!!, scenarioSaved.datasetList!![0])
    scenarioApiService.deleteScenario(
        organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)

    assertThrows<CsmResourceNotFoundException> {
      datasetApiService.findDatasetById(organizationSaved.id!!, datasetSaved.id!!)
    }
  }

  @Test
  fun `test scenario creation with null datasetList`() {
    val scenarioWithNullDatasetList = makeScenario(datasetList = null)
    val scenarioWithNullDatasetListSaved =
        scenarioApiService.createScenario(
            organizationSaved.id!!, workspaceSaved.id!!, scenarioWithNullDatasetList)

    assertNotNull(scenarioWithNullDatasetListSaved)
    assertNotNull(scenarioWithNullDatasetListSaved.datasetList)
    assertEquals(0, scenarioWithNullDatasetListSaved.datasetList!!.size)
  }

  @Test
  fun `when workspace datasetCopy is false, linked datasets should not be deleted`() {
    workspace =
        Workspace(
            key = "key",
            name = "workspace",
            solution = WorkspaceSolution(solutionSaved.id!!),
            id = "id",
            datasetCopy = false)
    workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
    scenario = makeScenario(datasetList = mutableListOf(datasetSaved.id!!))
    scenarioSaved =
        scenarioApiService.createScenario(organizationSaved.id!!, workspaceSaved.id!!, scenario)

    datasetSaved =
        datasetApiService.findDatasetById(organizationSaved.id!!, scenarioSaved.datasetList!![0])
    scenarioApiService.deleteScenario(
        organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)

    assertDoesNotThrow {
      datasetApiService.findDatasetById(organizationSaved.id!!, datasetSaved.id!!)
    }
  }

  @Test
  fun `when workspace datasetCopy is true, users added to scenario RBAC should have the same role in dataset`() {
    workspace =
        Workspace(
            key = "key",
            name = "workspace",
            solution = WorkspaceSolution(solutionSaved.id!!),
            id = "id",
            datasetCopy = true)
    workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
    scenario = makeScenario(datasetList = mutableListOf(datasetSaved.id!!))
    scenarioSaved =
        scenarioApiService.createScenario(organizationSaved.id!!, workspaceSaved.id!!, scenario)

    datasetSaved =
        datasetApiService.findDatasetById(organizationSaved.id!!, scenarioSaved.datasetList!![0])
    scenarioApiService.addScenarioAccessControl(
        organizationSaved.id!!,
        workspaceSaved.id!!,
        scenarioSaved.id!!,
        ScenarioAccessControl(id = "id", role = ROLE_EDITOR))

    val datasetAC =
        datasetApiService.getDatasetAccessControl(organizationSaved.id!!, datasetSaved.id!!, "id")
    assertEquals(ROLE_EDITOR, datasetAC.role)
  }

  @Test
  fun `when workspace datasetCopy is false, users added to scenario RBAC should not have the same role in dataset copy`() {
    val userId = "new_id"
    workspace =
        Workspace(
            key = "key",
            name = "workspace",
            solution = WorkspaceSolution(solutionSaved.id!!),
            id = userId,
            datasetCopy = false)
    workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)

    val datasetCopy =
        makeDataset(organizationSaved.id!!, "DatasetCopy", connectorSaved, isMain = false)
    var datasetCopySaved = datasetApiService.createDataset(organizationSaved.id!!, datasetCopy)

    scenario = makeScenario(datasetList = mutableListOf(datasetSaved.id!!, datasetCopySaved.id!!))
    scenarioSaved =
        scenarioApiService.createScenario(organizationSaved.id!!, workspaceSaved.id!!, scenario)

    datasetSaved =
        datasetApiService.findDatasetById(organizationSaved.id!!, scenarioSaved.datasetList!![0])
    datasetCopySaved =
        datasetApiService.findDatasetById(organizationSaved.id!!, scenarioSaved.datasetList!![1])

    scenarioApiService.addScenarioAccessControl(
        organizationSaved.id!!,
        workspaceSaved.id!!,
        scenarioSaved.id!!,
        ScenarioAccessControl(id = userId, role = ROLE_EDITOR))

    val datasetCopyAC =
        datasetApiService.getDatasetAccessControl(
            organizationSaved.id!!, datasetCopySaved.id!!, userId)
    assertEquals(ROLE_VIEWER, datasetCopyAC.role)

    val datasetAC =
        datasetApiService.getDatasetAccessControl(organizationSaved.id!!, datasetSaved.id!!, userId)
    assertEquals(ROLE_VIEWER, datasetAC.role)
  }

  @Test
  fun `when a user is removed from ACL on a scenario, only dataset copy should be updated`() {
    val userId = "new_id"
    workspace =
        Workspace(
            key = "key",
            name = "workspace",
            solution = WorkspaceSolution(solutionSaved.id!!),
            id = userId,
            datasetCopy = false)
    workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)

    val datasetCopy =
        makeDataset(organizationSaved.id!!, "DatasetCopy", connectorSaved, isMain = false)
    var datasetCopySaved = datasetApiService.createDataset(organizationSaved.id!!, datasetCopy)

    scenario = makeScenario(datasetList = mutableListOf(datasetSaved.id!!, datasetCopySaved.id!!))
    scenarioSaved =
        scenarioApiService.createScenario(organizationSaved.id!!, workspaceSaved.id!!, scenario)

    datasetSaved =
        datasetApiService.findDatasetById(organizationSaved.id!!, scenarioSaved.datasetList!![0])
    datasetCopySaved =
        datasetApiService.findDatasetById(organizationSaved.id!!, scenarioSaved.datasetList!![1])

    scenarioApiService.addScenarioAccessControl(
        organizationSaved.id!!,
        workspaceSaved.id!!,
        scenarioSaved.id!!,
        ScenarioAccessControl(id = userId, role = ROLE_EDITOR))

    scenarioApiService.removeScenarioAccessControl(
        organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, userId)

    val exception =
        assertThrows<CsmResourceNotFoundException> {
          datasetApiService.getDatasetAccessControl(
              organizationSaved.id!!, datasetCopySaved.id!!, userId)
        }

    assertEquals("User $userId not found in ${datasetCopySaved.id!!} component", exception.message)

    val datasetAC =
        datasetApiService.getDatasetAccessControl(organizationSaved.id!!, datasetSaved.id!!, userId)

    // Check that the user access is still defined and equals to default role when datasetCopy =
    // false
    assertEquals(ROLE_VIEWER, datasetAC.role)
  }

  @Test
  fun `when a user is updated from ACL on a scenario,and workspace datasetCopy to true all datasets should be updated`() {
    val userId = "new_id"
    workspace =
        Workspace(
            key = "key",
            name = "workspace",
            solution = WorkspaceSolution(solutionSaved.id!!),
            id = userId,
            datasetCopy = true)
    workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)

    val datasetCopy =
        makeDataset(organizationSaved.id!!, "DatasetCopy", connectorSaved, isMain = false)
    var datasetCopySaved = datasetApiService.createDataset(organizationSaved.id!!, datasetCopy)
    materializeTwingraph(datasetCopySaved)

    scenario = makeScenario(datasetList = mutableListOf(datasetSaved.id!!, datasetCopySaved.id!!))
    scenarioSaved =
        scenarioApiService.createScenario(organizationSaved.id!!, workspaceSaved.id!!, scenario)

    datasetSaved =
        datasetApiService.findDatasetById(organizationSaved.id!!, scenarioSaved.datasetList!![0])
    datasetCopySaved =
        datasetApiService.findDatasetById(organizationSaved.id!!, scenarioSaved.datasetList!![1])

    scenarioApiService.addScenarioAccessControl(
        organizationSaved.id!!,
        workspaceSaved.id!!,
        scenarioSaved.id!!,
        ScenarioAccessControl(id = userId, role = ROLE_EDITOR))

    scenarioApiService.removeScenarioAccessControl(
        organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!, userId)

    var exception =
        assertThrows<CsmResourceNotFoundException> {
          datasetApiService.getDatasetAccessControl(
              organizationSaved.id!!, datasetSaved.id!!, userId)
        }

    assertEquals("User $userId not found in ${datasetSaved.id!!} component", exception.message)

    exception =
        assertThrows<CsmResourceNotFoundException> {
          datasetApiService.getDatasetAccessControl(
              organizationSaved.id!!, datasetCopySaved.id!!, userId)
        }

    assertEquals("User $userId not found in ${datasetCopySaved.id!!} component", exception.message)
  }

  @Test
  fun `As a viewer, I can only see my information in security property for findScenarioById`() {
    organization = makeOrganization(userName = TEST_USER_MAIL)
    organizationSaved = organizationApiService.registerOrganization(organization)
    solution = makeSolution(organizationId = organizationSaved.id!!)
    solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)
    workspace = makeWorkspace(organizationId = organizationSaved.id!!, userName = TEST_USER_MAIL)
    workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
    dataset = makeDataset(organizationId = organizationSaved.id!!)
    datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
    scenario
    scenario =
        makeScenario(
            organizationId = organizationSaved.id!!, userName = TEST_USER_MAIL, role = ROLE_VIEWER)
    scenarioSaved =
        scenarioApiService.createScenario(organizationSaved.id!!, workspaceSaved.id!!, scenario)
    every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

    scenarioSaved =
        scenarioApiService.findScenarioById(
            organizationSaved.id!!, workspaceSaved.id!!, scenarioSaved.id!!)
    assertEquals(
        ScenarioSecurity(
            default = ROLE_NONE, mutableListOf(ScenarioAccessControl(TEST_USER_MAIL, ROLE_VIEWER))),
        scenarioSaved.security)
    assertEquals(1, scenarioSaved.security!!.accessControlList.size)
  }

  @Test
  fun `As a viewer, I can only see my information in security property for findAllScenarios`() {
    organization = makeOrganization(userName = TEST_USER_MAIL)
    organizationSaved = organizationApiService.registerOrganization(organization)
    solution = makeSolution(organizationId = organizationSaved.id!!)
    solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)
    workspace = makeWorkspace(organizationId = organizationSaved.id!!, userName = TEST_USER_MAIL)
    workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
    dataset = makeDataset(organizationId = organizationSaved.id!!)
    datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
    scenario
    scenario =
        makeScenario(
            organizationId = organizationSaved.id!!, userName = TEST_USER_MAIL, role = ROLE_VIEWER)
    scenarioSaved =
        scenarioApiService.createScenario(organizationSaved.id!!, workspaceSaved.id!!, scenario)
    every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

    val scenarios =
        scenarioApiService.findAllScenarios(organizationSaved.id!!, workspaceSaved.id!!, null, null)
    scenarios.forEach {
      assertEquals(
          ScenarioSecurity(
              default = ROLE_NONE,
              mutableListOf(ScenarioAccessControl(TEST_USER_MAIL, ROLE_VIEWER))),
          it.security)
      assertEquals(1, it.security!!.accessControlList.size)
    }
  }

  private fun makeWorkspaceEventHubInfo(eventHubAvailable: Boolean): WorkspaceEventHubInfo {
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

  private fun makeConnector(name: String = "name"): Connector {
    return Connector(
        key = UUID.randomUUID().toString(),
        name = name,
        repository = "/repository",
        version = "1.0",
        ioTypes = listOf(IoTypesEnum.read))
  }

  fun makeDataset(
      organizationId: String = organizationSaved.id!!,
      name: String = "name",
      connector: Connector = connectorSaved,
      sourceType: DatasetSourceType = DatasetSourceType.Twincache,
      isMain: Boolean = true
  ): Dataset {
    return Dataset(
        name = name,
        organizationId = organizationId,
        creationDate = Instant.now().toEpochMilli(),
        ownerId = "ownerId",
        sourceType = sourceType,
        connector =
            DatasetConnector(
                id = connector.id,
                name = connector.name,
                version = connector.version,
            ),
        main = isMain,
        security =
            DatasetSecurity(
                default = ROLE_NONE,
                accessControlList =
                    mutableListOf(
                        DatasetAccessControl(id = CONNECTED_ADMIN_USER, role = ROLE_ADMIN))))
  }

  fun makeSolution(organizationId: String = organizationSaved.id!!): Solution {
    return Solution(
        id = "solutionId",
        key = UUID.randomUUID().toString(),
        name = "My solution",
        organizationId = organizationId,
        ownerId = "ownerId",
        runTemplates =
            mutableListOf(
                RunTemplate(
                    id = "runtemplateTest",
                    name = "runtemplateTest",
                    parameterGroups = mutableListOf("RTparametersGroup1"),
                )),
        parameterGroups =
            mutableListOf(
                RunTemplateParameterGroup(
                    id = "RTparametersGroup1", parameters = mutableListOf("param1", "param2"))),
        parameters =
            mutableListOf(
                RunTemplateParameter(
                    id = "param1",
                    varType = "param1VarType",
                    defaultValue = "110",
                    maxValue = "120",
                    minValue = "100",
                ),
                RunTemplateParameter(
                    id = "param2",
                    varType = "param2VarType",
                ),
            ),
        security =
            SolutionSecurity(
                default = ROLE_NONE,
                accessControlList =
                    mutableListOf(
                        SolutionAccessControl(id = CONNECTED_ADMIN_USER, role = ROLE_ADMIN))))
  }

  fun makeOrganization(
      id: String = "id",
      userName: String = defaultName,
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
                        OrganizationAccessControl(id = userName, role = role))))
  }

  fun makeWorkspace(
      organizationId: String = organizationSaved.id!!,
      solutionId: String = solutionSaved.id!!,
      name: String = "name",
      userName: String = defaultName,
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
                    WorkspaceAccessControl(id = userName, role = role),
                    WorkspaceAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN))))
  }

  fun makeScenario(
      organizationId: String = organizationSaved.id!!,
      workspaceId: String = workspaceSaved.id!!,
      solutionId: String = solutionSaved.id!!,
      name: String = "name",
      datasetList: MutableList<String>? = mutableListOf(),
      parentId: String? = null,
      userName: String = "roleName",
      role: String = ROLE_USER,
      validationStatus: ScenarioValidationStatus = ScenarioValidationStatus.Draft,
      parametersValues: MutableList<ScenarioRunTemplateParameterValue>? = null
  ): Scenario {
    return Scenario(
        id = UUID.randomUUID().toString(),
        name = name,
        organizationId = organizationId,
        workspaceId = workspaceId,
        solutionId = solutionId,
        runTemplateId = "runtemplateTest",
        ownerId = "ownerId",
        datasetList = datasetList,
        parentId = parentId,
        validationStatus = validationStatus,
        parametersValues = parametersValues,
        security =
            ScenarioSecurity(
                ROLE_NONE,
                mutableListOf(
                    ScenarioAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                    ScenarioAccessControl(userName, role))))
  }

  private fun materializeTwingraph(
      dataset: Dataset = datasetSaved,
      createTwingraph: Boolean = true
  ): Dataset {
    dataset.apply {
      if (createTwingraph) {
        unifiedJedis.graphQuery(this.twingraphId, "MATCH (n:labelrouge) return 1")
      }
      this.ingestionStatus = IngestionStatusEnum.SUCCESS
      this.twincacheStatus = TwincacheStatusEnum.FULL
    }
    return datasetRepository.save(dataset)
  }
}
