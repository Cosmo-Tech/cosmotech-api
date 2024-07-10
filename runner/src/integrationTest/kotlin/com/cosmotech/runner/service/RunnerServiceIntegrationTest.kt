// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
@file:Suppress("DEPRECATION")

package com.cosmotech.runner.service

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.events.AskRunStatusEvent
import com.cosmotech.api.events.CsmEventPublisher
import com.cosmotech.api.events.RunStart
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
import com.cosmotech.connector.domain.IoTypesEnum
import com.cosmotech.dataset.api.DatasetApiService
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.dataset.domain.DatasetAccessControl
import com.cosmotech.dataset.domain.DatasetConnector
import com.cosmotech.dataset.domain.DatasetSecurity
import com.cosmotech.dataset.domain.IngestionStatusEnum
import com.cosmotech.dataset.domain.TwincacheStatusEnum
import com.cosmotech.dataset.repository.DatasetRepository
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationSecurity
import com.cosmotech.runner.RunnerApiServiceInterface
import com.cosmotech.runner.domain.*
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.solution.domain.*
import com.cosmotech.workspace.api.WorkspaceApiService
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceAccessControl
import com.cosmotech.workspace.domain.WorkspaceSecurity
import com.cosmotech.workspace.domain.WorkspaceSolution
import com.ninjasquad.springmockk.SpykBean
import com.redis.om.spring.RediSearchIndexer
import com.redis.testcontainers.RedisStackContainer
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockkStatic
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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

@ActiveProfiles(profiles = ["runner-test"])
@ExtendWith(MockKExtension::class)
@ExtendWith(SpringExtension::class)
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RunnerServiceIntegrationTest : CsmRedisTestBase() {

  val CONNECTED_ADMIN_USER = "test.admin@cosmotech.com"
  val CONNECTED_READER_USER = "test.reader@cosmotech.com"
  val TEST_USER_MAIL = "fake@mail.fr"

  private val logger = LoggerFactory.getLogger(RunnerServiceIntegrationTest::class.java)
  private val defaultName = "my.account-tester@cosmotech.com"

  @SpykBean @Autowired private lateinit var eventPublisher: CsmEventPublisher

  @Autowired lateinit var rediSearchIndexer: RediSearchIndexer
  @Autowired lateinit var connectorApiService: ConnectorApiService
  @Autowired lateinit var organizationApiService: OrganizationApiService
  @SpykBean @Autowired lateinit var datasetApiService: DatasetApiService
  @Autowired lateinit var datasetRepository: DatasetRepository
  @Autowired lateinit var solutionApiService: SolutionApiService
  @Autowired lateinit var workspaceApiService: WorkspaceApiService
  @Autowired lateinit var runnerApiService: RunnerApiServiceInterface
  @Autowired lateinit var csmPlatformProperties: CsmPlatformProperties

  lateinit var connector: Connector
  lateinit var dataset: Dataset
  lateinit var solution: Solution
  lateinit var organization: Organization
  lateinit var workspace: Workspace
  lateinit var runner: Runner
  lateinit var parentRunner: Runner

  lateinit var connectorSaved: Connector
  lateinit var datasetSaved: Dataset
  lateinit var solutionSaved: Solution
  lateinit var organizationSaved: Organization
  lateinit var workspaceSaved: Workspace
  lateinit var runnerSaved: Runner
  lateinit var parentRunnerSaved: Runner

  lateinit var jedis: UnifiedJedis

  val runTemplateParameterValue1 =
      RunnerRunTemplateParameterValue(
          parameterId = "param1", value = "param1value", isInherited = true, varType = "String")

  val runTemplateParameterValue2 =
      RunnerRunTemplateParameterValue(
          parameterId = "param2", value = "param2value", varType = "String")

  @BeforeAll
  fun beforeAll() {
    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
    mockkStatic("com.cosmotech.api.utils.RedisUtilsKt")
    mockkStatic("org.springframework.web.context.request.RequestContextHolder")
    val context = getContext(redisStackServer)
    val containerIp =
        (context.server as RedisStackContainer).containerInfo.networkSettings.ipAddress
    jedis = UnifiedJedis(HostAndPort(containerIp, Protocol.DEFAULT_PORT))

    ReflectionTestUtils.setField(datasetApiService, "unifiedJedis", jedis)
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

    parentRunner =
        makeRunner(
            organizationSaved.id!!,
            workspaceSaved.id!!,
            solutionSaved.id!!,
            "Runner",
            mutableListOf(datasetSaved.id!!),
            parametersValues = mutableListOf(runTemplateParameterValue1))

    parentRunnerSaved =
        runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, parentRunner)

    runner =
        makeRunner(
            organizationSaved.id!!,
            workspaceSaved.id!!,
            solutionSaved.id!!,
            name = "Runner",
            parentId = parentRunnerSaved.id!!,
            datasetList = mutableListOf(datasetSaved.id!!),
            parametersValues = mutableListOf(runTemplateParameterValue2))

    runnerSaved = runnerApiService.createRunner(organizationSaved.id!!, workspaceSaved.id!!, runner)
  }

  @Test
  fun `test prototype injection runnerService in runnerApiService`() {
    assertNotEquals(runnerApiService.getRunnerService(), runnerApiService.getRunnerService())
  }

  @Test
  fun `test CRUD operations on Runner as Platform Admin`() {
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
    assertEquals(3, runnerList.size)

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
    assertEquals(2, runnerListAfterDelete.size)

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
    assertEquals(defaultPageSize, runnerList.size)

    logger.info("should find all Runners and assert it equals defaultPageSize: $defaultPageSize")
    runnerList = runnerApiService.listRunners(organizationSaved.id!!, workspaceSaved.id!!, 0, null)
    assertEquals(defaultPageSize, runnerList.size)

    logger.info("should find all Runners and assert there are expected size: $expectedSize")
    runnerList =
        runnerApiService.listRunners(organizationSaved.id!!, workspaceSaved.id!!, 0, expectedSize)
    assertEquals(expectedSize, runnerList.size)

    logger.info("should find all Runners and assert it returns the second / last page")
    runnerList =
        runnerApiService.listRunners(
            organizationSaved.id!!, workspaceSaved.id!!, 1, defaultPageSize)
    assertEquals(1, runnerList.size)
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

  fun `test RBAC RunnerSecurity as Platform Admin`() {
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
  fun `test RBAC RunnerSecurity as Unauthorized User`() {
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

  @Test
  fun `test AccessControls management on Runner as ressource Admin`() {
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
  fun `test AccessControls management on Runner as Unauthorized User`() {
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
    runnerSaved.lastRunId = "run-genid12345"
    runnerApiService.updateRunner(
        organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!, runnerSaved)

    every { eventPublisher.publishEvent(any<AskRunStatusEvent>()) } answers
        {
          firstArg<AskRunStatusEvent>().response = "Running"
        }

    val exception =
        assertThrows<Exception> {
          runnerApiService.deleteRunner(
              organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
        }
    assertEquals("Can't delete a running runner : ${runnerSaved.id}", exception.message)
  }

  @Test
  fun `test on runner delete keep datasets`() {
    runnerApiService.deleteRunner(organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)

    runnerSaved.datasetList!!.forEach { dataset ->
      assertDoesNotThrow { datasetApiService.findDatasetById(organizationSaved.id!!, dataset) }
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
            runner.copy(
                datasetList = mutableListOf(datasetSaved.id!!, newDataset.id!!),
                security =
                    runner.security
                        ?.accessControlList
                        ?.apply { this.add(RunnerAccessControl("newUser", ROLE_VIEWER)) }
                        ?.let { runner.security?.copy(accessControlList = it) }))

    val runnerUserList =
        runnerApiService.getRunnerSecurityUsers(
            organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)

    val datasetUserList =
        datasetApiService.getDatasetSecurityUsers(organizationSaved.id!!, newDataset.id!!)
    datasetUserList.containsAll(runnerUserList)
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

    val runnerSavedSecurityUsers =
        runnerApiService.getRunnerSecurityUsers(
            organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
    assertEquals(2, runnerSavedSecurityUsers.size)

    runnerApiService.addRunnerAccessControl(
        organizationSaved.id!!,
        workspaceSaved.id!!,
        runnerSaved.id!!,
        RunnerAccessControl(defaultName, ROLE_EDITOR))

    val runnerSecurityUsers =
        runnerApiService.getRunnerSecurityUsers(
            organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
    assertEquals(2, runnerSecurityUsers.size)
    assert(runnerSavedSecurityUsers == runnerSecurityUsers)
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
  fun `users added to runner RBAC should have the corresponding role set in dataset`() {
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

  @Test
  fun `test runner creation with unknown runtemplateId`() {
    val runnerWithWrongRunTemplateId =
        makeRunner(
            organizationSaved.id!!,
            workspaceSaved.id!!,
            solutionSaved.id!!,
            name = "Runner_With_unknown_runtemplate_id",
            parentId = "unknown_parent_id",
            runTemplateId = "unknown_runtemplate_id",
            datasetList = mutableListOf(datasetSaved.id!!),
            parametersValues = mutableListOf(runTemplateParameterValue2))

    val assertThrows =
        assertThrows<IllegalArgumentException> {
          runnerApiService.createRunner(
              organizationSaved.id!!, workspaceSaved.id!!, runnerWithWrongRunTemplateId)
        }
    assertEquals(
        "Run Template not found: ${runnerWithWrongRunTemplateId.runTemplateId}",
        assertThrows.message)
  }

  @Test
  fun `test runner creation with unknown parentId`() {
    val parentId = "unknown_parent_id"
    val runnerWithWrongParentId =
        makeRunner(
            organizationSaved.id!!,
            workspaceSaved.id!!,
            solutionSaved.id!!,
            name = "Runner_With_unknown_parent",
            parentId = parentId,
            datasetList = mutableListOf(datasetSaved.id!!),
            parametersValues = mutableListOf(runTemplateParameterValue2))

    val assertThrows =
        assertThrows<IllegalArgumentException> {
          runnerApiService.createRunner(
              organizationSaved.id!!, workspaceSaved.id!!, runnerWithWrongParentId)
        }
    assertTrue(assertThrows.message!!.startsWith("Parent Id $parentId define on"))
  }

  @Test
  fun `test inherited parameters values`() {
    val runnerSavedParametersValues = runnerSaved.parametersValues
    assertNotNull(runnerSavedParametersValues)
    assertEquals(2, runnerSavedParametersValues.size)
    assertEquals(
        mutableListOf(runTemplateParameterValue2, runTemplateParameterValue1),
        runnerSavedParametersValues)
  }

  @Test
  fun `startRun send event and save lastRun info`() {
    val expectedRunId = "run-genid12345"
    every { eventPublisher.publishEvent(any<RunStart>()) } answers
        {
          firstArg<RunStart>().response = expectedRunId
        }

    val runId =
        runnerApiService.startRun(organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
    assertEquals(expectedRunId, runId)

    val lastRunId =
        runnerApiService
            .getRunner(organizationSaved.id!!, workspaceSaved.id!!, runnerSaved.id!!)
            .lastRunId
    assertEquals(expectedRunId, lastRunId)
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
        parameterGroups =
            mutableListOf(
                RunTemplateParameterGroup(
                    id = "testParameterGroups", parameters = mutableListOf("param1", "param2"))),
        runTemplates =
            mutableListOf(
                RunTemplate(
                    id = "runTemplateId", parameterGroups = mutableListOf("testParameterGroups"))),
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
      datasetList: MutableList<String> = mutableListOf(),
      parentId: String? = null,
      userName: String = defaultName,
      role: String = ROLE_USER,
      runTemplateId: String = "runTemplateId",
      validationStatus: RunnerValidationStatus = RunnerValidationStatus.Draft,
      parametersValues: MutableList<RunnerRunTemplateParameterValue> = mutableListOf()
  ): Runner {
    return Runner(
        id = UUID.randomUUID().toString(),
        name = name,
        organizationId = organizationId,
        workspaceId = workspaceId,
        solutionId = solutionId,
        runTemplateId = runTemplateId,
        ownerId = "ownerId",
        datasetList = datasetList,
        parentId = parentId,
        parametersValues = parametersValues,
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
        jedis.graphQuery(this.twingraphId, "MATCH (n:labelrouge) return 1")
      }
      this.ingestionStatus = IngestionStatusEnum.SUCCESS
      this.twincacheStatus = TwincacheStatusEnum.FULL
    }
    return datasetRepository.save(dataset)
  }
}
