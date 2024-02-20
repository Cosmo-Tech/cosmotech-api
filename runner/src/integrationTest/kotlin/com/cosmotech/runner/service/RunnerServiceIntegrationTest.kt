// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.runner.service

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
import com.cosmotech.connector.api.ConnectorApiService
import com.cosmotech.connector.domain.Connector
import com.cosmotech.dataset.api.DatasetApiService
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.dataset.domain.DatasetAccessControl
import com.cosmotech.dataset.domain.DatasetConnector
import com.cosmotech.dataset.domain.DatasetSecurity
import com.cosmotech.dataset.repository.DatasetRepository
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationSecurity
import com.cosmotech.runner.domain.Runner
import com.cosmotech.runner.domain.RunnerAccessControl
import com.cosmotech.runner.domain.RunnerJobState
import com.cosmotech.runner.domain.RunnerRole
import com.cosmotech.runner.domain.RunnerSecurity
import com.cosmotech.runner.domain.RunnerValidationStatus
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.solution.domain.RunTemplate
import com.cosmotech.solution.domain.Solution
import com.cosmotech.solution.domain.SolutionAccessControl
import com.cosmotech.solution.domain.SolutionSecurity
import com.cosmotech.workspace.api.WorkspaceApiService
import com.cosmotech.workspace.azure.WorkspaceEventHubInfo
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceAccessControl
import com.cosmotech.workspace.domain.WorkspaceSecurity
import com.cosmotech.workspace.domain.WorkspaceSolution
import com.ninjasquad.springmockk.SpykBean
import com.redis.om.spring.RediSearchIndexer
import com.redis.testcontainers.RedisStackContainer
import com.redislabs.redisgraph.impl.api.RedisGraph
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockkStatic
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
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
import redis.clients.jedis.JedisPool

@ActiveProfiles(profiles = ["runner-test"])
@ExtendWith(MockKExtension::class)
@ExtendWith(SpringExtension::class)
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RunnerServiceIntegrationTest : CsmRedisTestBase() {

  val CONNECTED_ADMIN_USER = "test.admin@cosmotech.com"
  val CONNECTED_NONE_USER = "test.none@cosmotech.com"
  val CONNECTED_EDITOR_USER = "test.editor@cosmotech.com"
  val CONNECTED_VALIDATOR_USER = "test.validator@cosmotech.com"
  val CONNECTED_READER_USER = "test.reader@cosmotech.com"
  val CONNECTED_VIEWER_USER = "test.user@cosmotech.com"
  val TEST_USER_MAIL = "fake@mail.fr"
  val REDIS_PORT = 6379

  private val logger = LoggerFactory.getLogger(RunnerServiceIntegrationTest::class.java)
  private val defaultName = "my.account-tester@cosmotech.com"

  @MockK(relaxed = true) private lateinit var azureDataExplorerClient: AzureDataExplorerClient
  @MockK private lateinit var scenarioDataDownloadJobInfoRequest: ScenarioDataDownloadJobInfoRequest

  @Autowired lateinit var rediSearchIndexer: RediSearchIndexer
  @Autowired lateinit var connectorApiService: ConnectorApiService
  @Autowired lateinit var organizationApiService: OrganizationApiService
  @SpykBean @Autowired lateinit var datasetApiService: DatasetApiService
  @Autowired lateinit var datasetRepository: DatasetRepository
  @Autowired lateinit var solutionApiService: SolutionApiService
  @Autowired lateinit var workspaceApiService: WorkspaceApiService
  @Autowired lateinit var runnerApiService: RunnerApiServicePlus
  @Autowired lateinit var runnerApiServiceCopy: RunnerApiServicePlus
  @Autowired lateinit var csmPlatformProperties: CsmPlatformProperties

  lateinit var connector: Connector
  lateinit var dataset: Dataset
  lateinit var solution: Solution
  lateinit var organization: Organization
  lateinit var workspace: Workspace
  lateinit var runner: Runner

  lateinit var connectorSaved: Connector
  lateinit var datasetSaved: Dataset
  lateinit var solutionSaved: Solution
  lateinit var organizationSaved: Organization
  lateinit var workspaceSaved: Workspace
  lateinit var runnerSaved: Runner

  lateinit var jedisPool: JedisPool

  @BeforeAll
  fun beforeAll() {
    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
    mockkStatic("com.cosmotech.api.utils.RedisUtilsKt")
    mockkStatic("org.springframework.web.context.request.RequestContextHolder")
    val context = getContext(redisStackServer)
    val containerIp =
        (context.server as RedisStackContainer).containerInfo.networkSettings.ipAddress
    jedisPool = JedisPool(containerIp, REDIS_PORT)
    ReflectionTestUtils.setField(datasetApiService, "csmJedisPool", jedisPool)
  }

  @BeforeEach
  fun setUp() {
    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "test.user"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("user")

    rediSearchIndexer.createIndexFor(Organization::class.java)
    rediSearchIndexer.createIndexFor(Dataset::class.java)
    rediSearchIndexer.createIndexFor(Solution::class.java)
    rediSearchIndexer.createIndexFor(Workspace::class.java)
    rediSearchIndexer.createIndexFor(Runner::class.java)

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

    runner =
        makeRunner(
            organizationSaved.id!!,
            workspaceSaved.id!!,
            solutionSaved.id!!,
            "Runner",
            mutableListOf(datasetSaved.id!!))

    runnerSaved = runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
  }

  @Test
  fun `test prototype injection runnerService in runnerApiService`() {

    val first = runnerApiService
    val second = runnerApiServiceCopy
    assertEquals(first, second)
    assertNotEquals(first.getRunnerService(), second.getRunnerService())
  }

  @Test
  fun `test CRUD operations on Runner as User Admin`() {
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")

    logger.info("should create a second Runner")
    val runner2 =
        makeRunner(
            organizationSaved.id!!,
            workspaceSaved.id!!,
            solutionSaved.id!!,
            "Runner2",
            mutableListOf(datasetSaved.id!!))
    val runnerSaved2 =
        runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner2)

    logger.info("should find all Runners and assert there are 2")
    var runnerList =
        runnerApiService.listRunners(organizationSaved.id!!, workspaceSaved.id!!, null, null)
    assertTrue(runnerList.size == 2)

    logger.info("should find a Runner by Id and assert it is the one created")
    val runnerRetrieved =
        runnerApiService.getRunner(organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
    assertEquals(runnerSaved, runnerRetrieved)

    logger.info("should update the Runner and assert the name has been updated")
    val runnerUpdated =
        runnerApiService.updateRunner(
            organizationSaved.id!!,
            workspaceSaved.id!!,
            runnerRetrieved.id!!,
            runnerRetrieved.copy(name = "Runner Updated"))
    assertEquals(
        runnerRetrieved.copy(name = "Runner Updated", creationDate = null, lastUpdate = null),
        runnerUpdated.copy(creationDate = null, lastUpdate = null))

    logger.info("should delete the Runner and assert there is only one Runner left")
    runnerApiService.deleteRunner(organizationSaved.id!!, workspaceSaved.id!!, runnerSaved2.id!!)
    val runnerListAfterDelete =
        runnerApiService.listRunners(organizationSaved.id!!, workspaceSaved.id!!, null, null)
    assertTrue(runnerListAfterDelete.size == 1)

    // We create more runner than there can be on one page of default size to assert
    // deleteAllRunners still works with high quantities of runners
    repeat(csmPlatformProperties.twincache.scenario.defaultPageSize + 1) {
      runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, makeRunner())
    }
  }

  @Test
  fun `test find All Runners with different pagination params`() {
    val numberOfRunners = 20
    val defaultPageSize = csmPlatformProperties.twincache.scenario.defaultPageSize
    val expectedSize = 15
    datasetSaved = materializeTwingraph()
    IntRange(1, numberOfRunners - 1).forEach {
      val runner =
          makeRunner(
              organizationSaved.id!!,
              workspaceSaved.id!!,
              solutionSaved.id!!,
              "Runner$it",
              mutableListOf(datasetSaved.id!!))
      runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
    }

    logger.info("should find all Runners and assert there are $numberOfRunners")
    var runnerList =
        runnerApiService.listRunners(organizationSaved.id!!, workspaceSaved.id!!, null, null)
    assertEquals(numberOfRunners, runnerList.size)

    logger.info("should find all Runners and assert it equals defaultPageSize: $defaultPageSize")
    runnerList = runnerApiService.listRunners(organizationSaved.id!!, workspaceSaved.id!!, 0, null)
    assertEquals(runnerList.size, defaultPageSize)

    logger.info("should find all Runners and assert there are expected size: $expectedSize")
    runnerList =
        runnerApiService.listRunners(organizationSaved.id!!, workspaceSaved.id!!, 0, expectedSize)
    assertEquals(runnerList.size, expectedSize)

    logger.info("should find all Runners and assert it returns the second / last page")
    runnerList =
        runnerApiService.listRunners(
            organizationSaved.id!!, workspaceSaved.id!!, 1, defaultPageSize)
    assertEquals(numberOfRunners - defaultPageSize, runnerList.size)
  }

  @Test
  fun `test find All Runners with wrong pagination params`() {
    logger.info("Should throw IllegalArgumentException when page and size are zeros")
    assertThrows<IllegalArgumentException> {
      runnerApiService.listRunners(organizationSaved.id!!, workspaceSaved.id!!, 0, 0)
    }

    logger.info("Should throw IllegalArgumentException when page is negative")
    assertThrows<IllegalArgumentException> {
      runnerApiService.listRunners(organizationSaved.id!!, workspaceSaved.id!!, -1, 10)
    }

    logger.info("Should throw IllegalArgumentException when size is negative")
    assertThrows<IllegalArgumentException> {
      runnerApiService.listRunners(organizationSaved.id!!, workspaceSaved.id!!, 0, -1)
    }
  }

  @Test
  fun `test parent runner operations as User Admin`() {

    logger.info("should create a child Runner with dataset different from parent")
    logger.info("Create a tree of Runners")
    val idMap = mutableMapOf<Int, String>()
    IntRange(1, 5).forEach {
      var runner =
          makeRunner(
              organizationSaved.id!!,
              workspaceSaved.id!!,
              solutionSaved.id!!,
              "Runner$it",
              mutableListOf(datasetSaved.id!!),
              if (it == 1) null else idMap[it - 1],
          )
      runner = runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
      idMap[it] = runner.id!!
    }
    var runners =
        runnerApiService.listRunners(organizationSaved.id!!, workspaceSaved.id!!, null, null)
    assertEquals(6, runners.size)

    logger.info("should delete last child (element 5) and assert there are 5 Runners left")
    runnerApiService.deleteRunner(organizationSaved.id!!, workspaceSaved.id!!, idMap[5]!!)
    runners = runnerApiService.listRunners(organizationSaved.id!!, workspaceSaved.id!!, null, null)
    assertEquals(5, runners.size)

    logger.info("should insure that the parent of element 4 is element 3")
    runner = runnerApiService.getRunner(organizationSaved.id!!, workspaceSaved.id!!, idMap[4]!!)
    assertEquals(idMap[3], runner.parentId)

    logger.info("should delete element 3 (in the middle) and assert there are 4 Runners left")
    runnerApiService.deleteRunner(organizationSaved.id!!, workspaceSaved.id!!, idMap[3]!!)
    runners = runnerApiService.listRunners(organizationSaved.id!!, workspaceSaved.id!!, null, null)
    assertEquals(4, runners.size)

    logger.info("should insure that the parent of element 4 is element 2")
    runner = runnerApiService.getRunner(organizationSaved.id!!, workspaceSaved.id!!, idMap[4]!!)
    assertEquals(idMap[2], runner.parentId)

    logger.info("should delete root element (element 1) and assert there are 3 Runners left")
    runnerApiService.deleteRunner(organizationSaved.id!!, workspaceSaved.id!!, idMap[1]!!)
    runners = runnerApiService.listRunners(organizationSaved.id!!, workspaceSaved.id!!, null, null)
    assertEquals(3, runners.size)

    val rootRunner =
        runnerApiService.getRunner(organizationSaved.id!!, workspaceSaved.id!!, idMap[2]!!)
    logger.info("rootId for the new root runner should be null")
    assertEquals(null, rootRunner.rootId)

    logger.info("rootId for the new root runner should be null")
    assertEquals(null, rootRunner.parentId)

    val childRunner =
        runnerApiService.getRunner(organizationSaved.id!!, workspaceSaved.id!!, idMap[4]!!)
    logger.info("rootId for element 4 should be element 2 id")
    assertEquals(rootRunner.id, childRunner.rootId)
  }

  @Test
  fun `test RBAC RunnerSecurity as User Admin`() {
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")

    logger.info("should test default security is set to ROLE_NONE")
    val runnerSecurity =
        runnerApiService.getRunnerSecurity(
            organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
    assertEquals(ROLE_NONE, runnerSecurity.default)

    logger.info("should set default security to ROLE_VIEWER and assert it has been set")
    val runnerRole = RunnerRole(ROLE_VIEWER)
    val runnerSecurityRegistered =
        runnerApiService.setRunnerDefaultSecurity(
            organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, runnerRole)
    assertEquals(runnerRole.role, runnerSecurityRegistered.default)
  }

  @Test
  fun `test RBAC RunnerSecurity as User Unauthorized`() {
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_READER_USER

    logger.info("should throw CsmAccessForbiddenException when trying to access RunnerSecurity")
    // Test default security
    assertThrows<CsmAccessForbiddenException> {
      runnerApiService.getRunnerSecurity(
          organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
    }

    logger.info("should throw CsmAccessForbiddenException when trying to set default security")
    val runnerRole = RunnerRole(ROLE_VIEWER)
    assertThrows<CsmAccessForbiddenException> {
      runnerApiService.setRunnerDefaultSecurity(
          organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, runnerRole)
    }
  }

  @TestFactory
  fun `test RBAC for deleteRunner`() =
      mapOf(
              CONNECTED_VIEWER_USER to true,
              CONNECTED_EDITOR_USER to true,
              CONNECTED_VALIDATOR_USER to true,
              CONNECTED_READER_USER to true,
              CONNECTED_NONE_USER to true,
              CONNECTED_ADMIN_USER to false)
          .map { (mail, shouldThrow) ->
            dynamicTest("Test RBAC findAllRunnerByValidationStatus : $mail") {
              every { getCurrentAccountIdentifier(any()) } returns mail
              if (shouldThrow)
                  assertThrows<Exception> {
                    runnerApiService.deleteRunner(
                        organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
                  }
              else
                  assertDoesNotThrow {
                    runnerApiService.deleteRunner(
                        organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
                  }
            }
          }

  @Test
  fun `test RBAC AccessControls on Runner as User Admin`() {

    logger.info("should add an Access Control and assert it has been added")
    val runnerAccessControl = RunnerAccessControl(TEST_USER_MAIL, ROLE_VIEWER)
    var runnerAccessControlRegistered =
        runnerApiService.addRunnerAccessControl(
            organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, runnerAccessControl)
    assertEquals(runnerAccessControl, runnerAccessControlRegistered)

    logger.info("should get the Access Control and assert it is the one created")
    runnerAccessControlRegistered =
        runnerApiService.getRunnerAccessControl(
            organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, TEST_USER_MAIL)
    assertEquals(runnerAccessControl, runnerAccessControlRegistered)

    logger.info(
        "should add an Access Control and assert it is the one created in the linked datasets")
    runnerSaved.datasetList!!.forEach {
      assertDoesNotThrow {
        datasetApiService.getDatasetAccessControl(organizationSaved.id!!, it, TEST_USER_MAIL)
      }
    }

    logger.info("should update the Access Control and assert it has been updated")
    runnerAccessControlRegistered =
        runnerApiService.updateRunnerAccessControl(
            organizationSaved.id!!,
            workspaceSaved.id!!,
            runnerSaved.id!!,
            TEST_USER_MAIL,
            RunnerRole(ROLE_EDITOR))
    assertEquals(ROLE_EDITOR, runnerAccessControlRegistered.role)

    logger.info(
        "should update the Access Control and assert it has been updated in the linked datasets")
    runnerSaved.datasetList!!.forEach {
      assertEquals(
          ROLE_EDITOR,
          datasetApiService
              .getDatasetAccessControl(organizationSaved.id!!, it, TEST_USER_MAIL)
              .role)
    }

    logger.info("should get the list of users and assert there are 2")
    var userList =
        runnerApiService.getRunnerSecurityUsers(
            organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
    assertEquals(3, userList.size)

    logger.info("should remove the Access Control and assert it has been removed")
    runnerApiService.removeRunnerAccessControl(
        organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, TEST_USER_MAIL)
    assertThrows<CsmResourceNotFoundException> {
      runnerAccessControlRegistered =
          runnerApiService.getRunnerAccessControl(
              organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, TEST_USER_MAIL)
    }

    logger.info(
        "should remove the Access Control and assert it has been removed in the linked datasets")
    runnerSaved.datasetList!!.forEach {
      assertThrows<CsmResourceNotFoundException> {
        datasetApiService.getDatasetAccessControl(organizationSaved.id!!, it, TEST_USER_MAIL)
      }
    }
  }

  @Test
  fun `test RBAC AccessControls on Runner as User Unauthorized`() {

    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_READER_USER

    logger.info("should throw CsmAccessForbiddenException when trying to add RunnerAccessControl")
    val runnerAccessControl = RunnerAccessControl(TEST_USER_MAIL, ROLE_VIEWER)
    assertThrows<CsmAccessForbiddenException> {
      runnerApiService.addRunnerAccessControl(
          organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, runnerAccessControl)
    }

    logger.info("should throw CsmAccessForbiddenException when trying to get RunnerAccessControl")
    assertThrows<CsmAccessForbiddenException> {
      runnerApiService.getRunnerAccessControl(
          organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, TEST_USER_MAIL)
    }

    logger.info(
        "should throw CsmAccessForbiddenException when trying to update RunnerAccessControl")
    assertThrows<CsmAccessForbiddenException> {
      runnerApiService.updateRunnerAccessControl(
          organizationSaved.id!!,
          workspaceSaved.id!!,
          runnerSaved.id!!,
          TEST_USER_MAIL,
          RunnerRole(ROLE_VIEWER))
    }

    logger.info("should throw CsmAccessForbiddenException when getting the list of users")
    assertThrows<CsmAccessForbiddenException> {
      runnerApiService.getRunnerSecurityUsers(
          organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
    }

    logger.info(
        "should throw CsmAccessForbiddenException when trying to remove RunnerAccessControl")
    assertThrows<CsmAccessForbiddenException> {
      runnerApiService.removeRunnerAccessControl(
          organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, TEST_USER_MAIL)
    }
  }

  @Test
  fun `test deleting a running runner`() {
    runnerSaved.state = RunnerJobState.Running
    runnerApiService.updateRunner(
        organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, runnerSaved)
    assertThrows<Exception> {
      runnerApiService.deleteRunner(organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
    }
  }

  @Test
  fun `test deleting dataset with runner`() {
    runnerApiService.deleteRunner(organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)

    assertDoesNotThrow {
      datasetApiService.findDatasetById(organizationSaved.id!!, datasetSaved.id!!)
    }
    runnerSaved.datasetList!!.forEach { dataset ->
      assertThrows<CsmResourceNotFoundException> {
        datasetApiService.findDatasetById(organizationSaved.id!!, dataset)
      }
    }
  }

  @Test
  fun `test updating (adding) runner's datasetList add runner users to new dataset`() {
    val newDataset = datasetApiService.createDataset(organizationSaved.id!!, makeDataset())
    runnerSaved =
        runnerApiService.updateRunner(
            organizationSaved.id!!,
            workspaceSaved.id!!,
            runnerSaved.id!!,
            runner.copy(datasetList = mutableListOf(datasetSaved.id!!, newDataset.id!!)))

    val runnerUserList =
        runnerApiService.getRunnerSecurityUsers(
            organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)

    runnerSaved.datasetList!!.forEach { thisDataset ->
      val datasetUserList =
          datasetApiService.getDatasetSecurityUsers(organizationSaved.id!!, thisDataset)
      runnerUserList.forEach { user -> assertTrue(datasetUserList.contains(user)) }
    }
  }

  @Test
  fun `access control list shouldn't contain more than one time each user on creation`() {
    organizationSaved =
        organizationApiService.registerOrganization(makeOrganization("organization"))
    solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, makeSolution())
    workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id!!, makeWorkspace())
    val brokenRunner =
        Runner(
            name = "runner",
            security =
                RunnerSecurity(
                    default = ROLE_NONE,
                    accessControlList =
                        mutableListOf(
                            RunnerAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                            RunnerAccessControl(CONNECTED_ADMIN_USER, ROLE_EDITOR))))
    assertThrows<IllegalArgumentException> {
      runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, brokenRunner)
    }
  }

  @Test
  fun `access control list shouldn't contain more than one time each user on ACL addition`() {
    organizationSaved =
        organizationApiService.registerOrganization(makeOrganization("organization"))
    solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, makeSolution())
    workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id!!, makeWorkspace())
    val workingRunner = makeRunner()
    runnerSaved =
        runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, workingRunner)

    assertThrows<IllegalArgumentException> {
      runnerApiService.addRunnerAccessControl(
          organizationSaved.id!!,
          workspaceSaved.id!!,
          runnerSaved.id!!,
          RunnerAccessControl(CONNECTED_ADMIN_USER, ROLE_EDITOR))
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
    runner = makeRunner(datasetList = mutableListOf(datasetSaved.id!!))
    runnerSaved = runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)

    datasetSaved =
        datasetApiService.findDatasetById(organizationSaved.id!!, runnerSaved.datasetList!![0])
    runnerApiService.deleteRunner(organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)

    assertThrows<CsmResourceNotFoundException> {
      datasetApiService.findDatasetById(organizationSaved.id!!, datasetSaved.id!!)
    }
  }

  @Test
  fun `on Runner delete linked datasets should not be deleted`() {
    workspace =
        Workspace(
            key = "key",
            name = "workspace",
            solution = WorkspaceSolution(solutionSaved.id!!),
            id = "id",
            datasetCopy = false)
    workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
    runner = makeRunner(datasetList = mutableListOf(datasetSaved.id!!))
    runnerSaved = runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)

    datasetSaved =
        datasetApiService.findDatasetById(organizationSaved.id!!, runnerSaved.datasetList!![0])
    runnerApiService.deleteRunner(organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)

    assertDoesNotThrow {
      datasetApiService.findDatasetById(organizationSaved.id!!, datasetSaved.id!!)
    }
  }

  @Test
  fun `users added to runner RBAC should have the correspondinf role set in dataset`() {
    workspace =
        Workspace(
            key = "key",
            name = "workspace",
            solution = WorkspaceSolution(solutionSaved.id!!),
            id = "id",
            datasetCopy = true)
    workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id!!, workspace)
    runner = makeRunner(datasetList = mutableListOf(datasetSaved.id!!))
    runnerSaved = runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)

    datasetSaved =
        datasetApiService.findDatasetById(organizationSaved.id!!, runnerSaved.datasetList!![0])
    runnerApiService.addRunnerAccessControl(
        organizationSaved.id!!,
        workspaceSaved.id!!,
        runnerSaved.id!!,
        RunnerAccessControl(id = "id", role = ROLE_EDITOR))

    val datasetAC =
        datasetApiService.getDatasetAccessControl(organizationSaved.id!!, datasetSaved.id!!, "id")
    assertEquals(ROLE_EDITOR, datasetAC.role)
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
        ioTypes = listOf(Connector.IoTypes.read))
  }

  fun makeDataset(
      organizationId: String = organizationSaved.id!!,
      name: String = "name",
      connector: Connector = connectorSaved
  ): Dataset {
    return Dataset(
        name = name,
        organizationId = organizationId,
        creationDate = Instant.now().toEpochMilli(),
        ownerId = "ownerId",
        connector =
            DatasetConnector(
                id = connector.id,
                name = connector.name,
                version = connector.version,
            ),
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
        runTemplates = mutableListOf(RunTemplate("runTemplateId")),
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

  fun makeRunner(
      organizationId: String = organizationSaved.id!!,
      workspaceId: String = workspaceSaved.id!!,
      solutionId: String = solutionSaved.id!!,
      name: String = "name",
      datasetList: MutableList<String> = mutableListOf<String>(),
      parentId: String? = null,
      userName: String = "roleName",
      role: String = ROLE_USER,
      validationStatus: RunnerValidationStatus = RunnerValidationStatus.Draft
  ): Runner {
    return Runner(
        id = UUID.randomUUID().toString(),
        name = name,
        organizationId = organizationId,
        workspaceId = workspaceId,
        solutionId = solutionId,
        runTemplateId = "runTemplateId",
        ownerId = "ownerId",
        datasetList = datasetList,
        parentId = parentId,
        validationStatus = validationStatus,
        security =
            RunnerSecurity(
                ROLE_NONE,
                mutableListOf(
                    RunnerAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                    RunnerAccessControl(userName, role))))
  }

  private fun materializeTwingraph(
      dataset: Dataset = datasetSaved,
      createTwingraph: Boolean = true
  ): Dataset {
    dataset.apply {
      if (createTwingraph) {
        RedisGraph(jedisPool).query(this.twingraphId, "MATCH (n:labelrouge) return 1")
      }
      this.ingestionStatus = Dataset.IngestionStatus.SUCCESS
      this.twincacheStatus = Dataset.TwincacheStatus.FULL
    }
    return datasetRepository.save(dataset)
  }
}
