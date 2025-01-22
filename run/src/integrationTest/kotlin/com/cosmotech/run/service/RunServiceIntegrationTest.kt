// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run.service

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.events.RunDeleted
import com.cosmotech.api.events.RunStart
import com.cosmotech.api.rbac.ROLE_ADMIN
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.utils.getCurrentAccountIdentifier
import com.cosmotech.api.utils.getCurrentAuthenticatedRoles
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.connector.ConnectorApiServiceInterface
import com.cosmotech.connector.domain.Connector
import com.cosmotech.connector.domain.IoTypesEnum
import com.cosmotech.dataset.DatasetApiServiceInterface
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.dataset.domain.DatasetConnector
import com.cosmotech.dataset.domain.IngestionStatusEnum
import com.cosmotech.organization.OrganizationApiServiceInterface
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationCreationRequest
import com.cosmotech.organization.domain.OrganizationSecurity
import com.cosmotech.run.RunApiServiceInterface
import com.cosmotech.run.RunContainerFactory
import com.cosmotech.run.config.existDB
import com.cosmotech.run.config.existTable
import com.cosmotech.run.config.toDataTableName
import com.cosmotech.run.domain.Run
import com.cosmotech.run.domain.RunDataQuery
import com.cosmotech.run.domain.SendRunDataRequest
import com.cosmotech.run.workflow.WorkflowService
import com.cosmotech.runner.RunnerApiServiceInterface
import com.cosmotech.runner.domain.Runner
import com.cosmotech.runner.domain.RunnerAccessControl
import com.cosmotech.runner.domain.RunnerSecurity
import com.cosmotech.solution.SolutionApiServiceInterface
import com.cosmotech.solution.domain.RunTemplate
import com.cosmotech.solution.domain.Solution
import com.cosmotech.solution.domain.SolutionAccessControl
import com.cosmotech.solution.domain.SolutionSecurity
import com.cosmotech.workspace.WorkspaceApiServiceInterface
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceAccessControl
import com.cosmotech.workspace.domain.WorkspaceSecurity
import com.cosmotech.workspace.domain.WorkspaceSolution
import com.ninjasquad.springmockk.SpykBean
import com.redis.om.spring.RediSearchIndexer
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.mockkStatic
import java.math.BigDecimal
import java.sql.SQLException
import java.util.*
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.runner.RunWith
import org.postgresql.util.PSQLException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.util.ReflectionTestUtils

@ActiveProfiles(profiles = ["run-test"])
@ExtendWith(MockKExtension::class)
@ExtendWith(SpringExtension::class)
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RunServiceIntegrationTest : CsmRunTestBase() {

  @Autowired lateinit var csmPlatformProperties: CsmPlatformProperties

  val CONNECTED_ADMIN_USER = "test.admin@cosmotech.com"
  val CONNECTED_READER_USER = "test.user@cosmotech.com"
  private val logger = LoggerFactory.getLogger(RunServiceIntegrationTest::class.java)
  private val defaultName = "my.account-tester@cosmotech.com"

  @MockK(relaxed = true) private lateinit var containerFactory: RunContainerFactory
  @MockK(relaxed = true) private lateinit var workflowService: WorkflowService

  @Autowired lateinit var rediSearchIndexer: RediSearchIndexer
  @Autowired lateinit var connectorApiService: ConnectorApiServiceInterface
  @Autowired lateinit var organizationApiService: OrganizationApiServiceInterface
  @SpykBean @Autowired lateinit var datasetApiService: DatasetApiServiceInterface
  @Autowired lateinit var solutionApiService: SolutionApiServiceInterface
  @Autowired lateinit var workspaceApiService: WorkspaceApiServiceInterface
  @SpykBean @Autowired lateinit var runnerApiService: RunnerApiServiceInterface
  @Autowired lateinit var runApiService: RunApiServiceInterface
  @Autowired lateinit var eventPublisher: com.cosmotech.api.events.CsmEventPublisher

  @Autowired lateinit var adminRunStorageTemplate: JdbcTemplate

  lateinit var connector: Connector
  lateinit var dataset: Dataset
  lateinit var solution: Solution
  lateinit var organization: OrganizationCreationRequest
  lateinit var workspace: Workspace

  lateinit var connectorSaved: Connector
  lateinit var datasetSaved: Dataset
  lateinit var solutionSaved: Solution
  lateinit var organizationSaved: Organization
  lateinit var workspaceSaved: Workspace
  lateinit var runnerSaved: Runner
  lateinit var runSavedId: String

  @BeforeEach
  fun setUp() {
    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "test.user"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("user")

    ReflectionTestUtils.setField(runApiService, "containerFactory", containerFactory)
    ReflectionTestUtils.setField(runApiService, "workflowService", workflowService)
    ReflectionTestUtils.setField(runApiService, "eventPublisher", eventPublisher)

    rediSearchIndexer.createIndexFor(Solution::class.java)
    rediSearchIndexer.createIndexFor(Workspace::class.java)
    rediSearchIndexer.createIndexFor(Runner::class.java)
    rediSearchIndexer.createIndexFor(Run::class.java)

    connector = mockConnector("Connector")
    connectorSaved = connectorApiService.registerConnector(connector)

    organization = makeOrganizationRequest("Organization")
    organizationSaved = organizationApiService.createOrganization(organization)

    dataset = mockDataset(organizationSaved.id, "Dataset", connectorSaved)
    datasetSaved = datasetApiService.createDataset(organizationSaved.id, dataset)

    solution = mockSolution(organizationSaved.id)
    solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

    workspace = mockWorkspace(organizationSaved.id, solutionSaved.id!!, "Workspace")
    workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id, workspace)

    runnerSaved =
        mockRunner(
            organizationSaved.id,
            workspaceSaved.id!!,
            solutionSaved.id!!,
            solutionSaved.runTemplates[0].id,
            "Runner",
            mutableListOf(datasetSaved.id!!))

    every { workflowService.launchRun(any(), any(), any(), any()) } returns
        mockWorkflowRun(organizationSaved.id, workspaceSaved.id!!, runnerSaved.id!!)
    every { datasetApiService.findDatasetById(any(), any()) } returns
        datasetSaved.apply { ingestionStatus = IngestionStatusEnum.SUCCESS }
    every { datasetApiService.createSubDataset(any(), any(), any()) } returns mockk(relaxed = true)

    every { runnerApiService.getRunner(any(), any(), any()) } returns runnerSaved
    every { eventPublisher.publishEvent(any<RunDeleted>()) } returns Unit
  }

  private fun mockConnector(name: String = "connector"): Connector {
    return Connector(
        key = UUID.randomUUID().toString(),
        name = name,
        repository = "/repository",
        version = "1.0",
        ioTypes = listOf(IoTypesEnum.read))
  }

  fun mockDataset(
      organizationId: String = organizationSaved.id,
      name: String = "Dataset",
      connector: Connector = connectorSaved,
      roleName: String = defaultName,
      role: String = ROLE_ADMIN
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

  fun mockSolution(organizationId: String = organizationSaved.id): Solution {
    return Solution(
        id = "solutionId",
        key = UUID.randomUUID().toString(),
        name = "My solution",
        organizationId = organizationId,
        ownerId = "ownerId",
        runTemplates =
            mutableListOf(
                RunTemplate(
                    id = UUID.randomUUID().toString(),
                    name = "RunTemplate1",
                    description = "RunTemplate1 description")),
        security =
            SolutionSecurity(
                default = ROLE_NONE,
                accessControlList =
                    mutableListOf(
                        SolutionAccessControl(id = CONNECTED_ADMIN_USER, role = ROLE_ADMIN),
                        SolutionAccessControl(id = CONNECTED_READER_USER, role = ROLE_ADMIN))))
  }

  fun makeOrganizationRequest(id: String = "organizationId"): OrganizationCreationRequest {
    return OrganizationCreationRequest(
        name = "Organization Name",
        security =
            OrganizationSecurity(
                default = ROLE_NONE,
                accessControlList =
                    mutableListOf(
                        OrganizationAccessControl(id = CONNECTED_READER_USER, role = "reader"),
                        OrganizationAccessControl(id = CONNECTED_ADMIN_USER, role = "admin"))))
  }

  fun mockWorkspace(
      organizationId: String = organizationSaved.id,
      solutionId: String = solutionSaved.id!!,
      name: String = "workspace"
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
                ROLE_NONE, mutableListOf(WorkspaceAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN))))
  }

  fun mockRunner(
      organizationId: String = organizationSaved.id,
      workspaceId: String = workspaceSaved.id!!,
      solutionId: String = solutionSaved.id!!,
      runTemplateId: String = solutionSaved.runTemplates[0].id,
      name: String = "runner",
      datasetList: MutableList<String> = mutableListOf(datasetSaved.id!!)
  ): Runner {
    return Runner(
        id = "RunnerId",
        name = name,
        organizationId = organizationId,
        workspaceId = workspaceId,
        solutionId = solutionId,
        runTemplateId = runTemplateId,
        ownerId = "ownerId",
        datasetList = datasetList,
        security =
            RunnerSecurity(
                ROLE_NONE, mutableListOf(RunnerAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN))))
  }

  fun mockStartRun(
      organizationId: String,
      workspaceId: String,
      runnerId: String,
      solutionId: String
  ): String {
    val runner =
        Runner(
            id = runnerId,
            solutionId = solutionId,
            organizationId = organizationId,
            workspaceId = workspaceId)
    val runStart = RunStart(this, runner)
    eventPublisher.publishEvent(runStart)
    return runStart.response!!
  }

  fun mockWorkflowRun(organizationId: String, workspaceId: String, runnerId: String): Run {
    return Run(organizationId = organizationId, workspaceId = workspaceId, runnerId = runnerId)
  }

  @Test
  fun `test CRUD operations on Run`() {
    logger.info("test CRUD operations on Run")
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")

    logger.info("should create 1 run")

    runSavedId =
        mockStartRun(
            organizationSaved.id, workspaceSaved.id!!, runnerSaved.id!!, solutionSaved.id!!)
    assertNotEquals("", runSavedId)

    logger.info("should find 1 Run")
    var runs =
        runApiService.listRuns(
            organizationSaved.id, workspaceSaved.id!!, runnerSaved.id!!, null, null)
    assertEquals(1, runs.size)

    logger.info("should find Run by id")
    val foundRun =
        runApiService.getRun(
            organizationSaved.id, workspaceSaved.id!!, runnerSaved.id!!, runSavedId)
    assertEquals(runSavedId, foundRun.id)
    assertEquals(organizationSaved.id, foundRun.organizationId)
    assertEquals(workspaceSaved.id!!, foundRun.workspaceId)
    assertEquals(runnerSaved.id!!, foundRun.runnerId)

    logger.info("should create second Run")
    val runSaved2id =
        mockStartRun(
            organizationSaved.id, workspaceSaved.id!!, runnerSaved.id!!, solutionSaved.id!!)

    logger.info("should find all Runs by Runner id and assert size is 2")
    runs =
        runApiService.listRuns(
            organizationSaved.id, workspaceSaved.id!!, runnerSaved.id!!, null, null)
    assertEquals(2, runs.size)

    logger.info("should delete second Run and assert size is 1")
    runApiService.deleteRun(
        organizationSaved.id, workspaceSaved.id!!, runnerSaved.id!!, runSaved2id)
    runs =
        runApiService.listRuns(
            organizationSaved.id, workspaceSaved.id!!, runnerSaved.id!!, null, null)
    assertEquals(1, runs.size)
  }

  @Test
  fun `test find All Runs with different pagination params`() {
    val numberOfRuns = 20
    val defaultPageSize = csmPlatformProperties.twincache.run.defaultPageSize
    val expectedSize = 15

    IntRange(1, numberOfRuns).forEach {
      mockStartRun(organizationSaved.id, workspaceSaved.id!!, runnerSaved.id!!, solutionSaved.id!!)
    }

    logger.info("should find all Runs and assert there are $numberOfRuns")
    var runs =
        runApiService.listRuns(
            organizationSaved.id, workspaceSaved.id!!, runnerSaved.id!!, null, numberOfRuns * 2)
    assertEquals(numberOfRuns, runs.size)

    logger.info("should find all Runs and assert it equals defaultPageSize: $defaultPageSize")
    runs =
        runApiService.listRuns(organizationSaved.id, workspaceSaved.id!!, runnerSaved.id!!, 0, null)
    assertEquals(defaultPageSize, runs.size)

    logger.info("should find all Runs and assert there are expected size: $expectedSize")
    runs =
        runApiService.listRuns(
            organizationSaved.id, workspaceSaved.id!!, runnerSaved.id!!, 0, expectedSize)
    assertEquals(expectedSize, runs.size)

    logger.info("should find all Runs and assert it returns the second / last page")
    runs =
        runApiService.listRuns(
            organizationSaved.id, workspaceSaved.id!!, runnerSaved.id!!, 1, expectedSize)
    assertEquals(numberOfRuns - expectedSize, runs.size)
  }

  @Test
  fun `test find All Runs with wrong pagination params`() {
    logger.info("Should throw IllegalArgumentException when page and size are zeros")
    assertThrows<IllegalArgumentException> {
      runApiService.listRuns(organizationSaved.id, workspaceSaved.id!!, runnerSaved.id!!, 0, 0)
    }

    logger.info("Should throw IllegalArgumentException when page is negative")
    assertThrows<IllegalArgumentException> {
      runApiService.listRuns(organizationSaved.id, workspaceSaved.id!!, runnerSaved.id!!, -1, 10)
    }

    logger.info("Should throw IllegalArgumentException when size is negative")
    assertThrows<IllegalArgumentException> {
      runApiService.listRuns(organizationSaved.id, workspaceSaved.id!!, runnerSaved.id!!, 0, -1)
    }
  }

  @Nested
  inner class RunServicePostgresIntegrationTest {

    lateinit var readerRunStorageTemplate: JdbcTemplate

    @BeforeEach
    fun setUp() {
      runSavedId =
          mockStartRun(
              organizationSaved.id, workspaceSaved.id!!, runnerSaved.id!!, solutionSaved.id!!)
      assertTrue(adminRunStorageTemplate.existDB(runSavedId))

      val internalResultServices = csmPlatformProperties.internalResultServices!!
      val runtimeDS =
          DriverManagerDataSource(
              "jdbc:postgresql://${internalResultServices.storage.host}:${internalResultServices.storage.port}" +
                  "/$runSavedId",
              internalResultServices.storage.reader.username,
              internalResultServices.storage.reader.password)
      runtimeDS.setDriverClassName("org.postgresql.Driver")
      readerRunStorageTemplate = JdbcTemplate(runtimeDS)
    }

    @Test
    fun `test deleteRun should remove the database`() {
      runApiService.deleteRun(
          organizationSaved.id, workspaceSaved.id!!, runnerSaved.id!!, runSavedId)
      assertFalse(adminRunStorageTemplate.existDB(runSavedId))
    }

    @Test
    fun `test sendRunData must create table available to reader user`() {
      val tableName = "MyCustomData"
      val data =
          listOf(
              mapOf("param1" to "value1"),
              mapOf("param2" to 2),
              mapOf("param3" to JSONObject(mapOf("param4" to "value4"))))
      val requestBody = SendRunDataRequest(id = tableName, data = data)
      val runDataResult =
          runApiService.sendRunData(
              organizationSaved.id, workspaceSaved.id!!, runnerSaved.id!!, runSavedId, requestBody)

      assertEquals(tableName.toDataTableName(false), runDataResult.tableName)

      assertTrue(readerRunStorageTemplate.existTable(tableName.toDataTableName(false)))

      val rows =
          readerRunStorageTemplate.queryForList(
              "SELECT * FROM \"${tableName.toDataTableName(false)}\"")

      assertEquals(data.size, rows.size)
    }

    @Test
    fun `test sendRunData must create different tables when id change`() {
      val tableName = "MyCustomData"
      val data =
          listOf(
              mapOf("param1" to "value1"),
              mapOf("param2" to 2),
              mapOf("param3" to JSONObject(mapOf("param4" to "value4"))))
      val requestBody = SendRunDataRequest(id = tableName, data = data)
      runApiService.sendRunData(
          organizationSaved.id, workspaceSaved.id!!, runnerSaved.id!!, runSavedId, requestBody)

      assertTrue(readerRunStorageTemplate.existTable(tableName.toDataTableName(false)))

      val rows =
          readerRunStorageTemplate.queryForList(
              "SELECT * FROM \"${tableName.toDataTableName(false)}\"")

      assertEquals(data.size, rows.size)

      val tableName2 = "MyCustomData2"
      val data2 = listOf(mapOf("param1" to "value1"), mapOf("param2" to 2))
      val requestBody2 = SendRunDataRequest(id = tableName2, data = data2)
      runApiService.sendRunData(
          organizationSaved.id, workspaceSaved.id!!, runnerSaved.id!!, runSavedId, requestBody2)

      assertTrue(readerRunStorageTemplate.existTable(tableName2.toDataTableName(false)))

      val rows2 =
          readerRunStorageTemplate.queryForList(
              "SELECT * FROM \"${tableName2.toDataTableName(false)}\"")

      assertEquals(data2.size, rows2.size)
    }

    @Test
    fun `test multiple sendRunData with incompatible schema does not work`() {
      val tableName = "MyCustomData"
      val data = listOf(mapOf("parameter" to "stringValue"))
      val data2 = listOf(mapOf("parameter" to JSONObject(mapOf("key" to "value"))))
      val requestBody = SendRunDataRequest(id = tableName, data = data)
      val requestBody2 = SendRunDataRequest(id = tableName, data = data2)
      runApiService.sendRunData(
          organizationSaved.id, workspaceSaved.id!!, runnerSaved.id!!, runSavedId, requestBody)

      assertFailsWith(SQLException::class, "Schema should have been rejected") {
        runApiService.sendRunData(
            organizationSaved.id, workspaceSaved.id!!, runnerSaved.id!!, runSavedId, requestBody2)
      }
    }

    @Test
    fun `test multiple sendRunData with new columns works`() {
      val tableName = "MyCustomData"
      val data = listOf(mapOf("parameter" to "stringValue"))
      val data2 = listOf(mapOf("parameter2" to JSONObject(mapOf("key" to "value"))))
      val requestBody = SendRunDataRequest(id = tableName, data = data)
      val requestBody2 = SendRunDataRequest(id = tableName, data = data2)
      runApiService.sendRunData(
          organizationSaved.id, workspaceSaved.id!!, runnerSaved.id!!, runSavedId, requestBody)

      runApiService.sendRunData(
          organizationSaved.id, workspaceSaved.id!!, runnerSaved.id!!, runSavedId, requestBody2)

      val rows =
          readerRunStorageTemplate.queryForList(
              "SELECT * FROM \"${tableName.toDataTableName(false)}\"")

      assertEquals(
          data.size + data2.size,
          rows.size,
          "Multiple send of data with new columns should add new rows in table")
    }

    @Test
    fun `test sendRunData with no data fails`() {
      val tableName = "MyCustomData"
      val data = listOf<Map<String, Any>>()
      val requestBody = SendRunDataRequest(id = tableName, data = data)
      assertFailsWith(
          IllegalArgumentException::class, "sendRunData must fail if data is an empty list") {
            runApiService.sendRunData(
                organizationSaved.id,
                workspaceSaved.id!!,
                runnerSaved.id!!,
                runSavedId,
                requestBody)
          }
    }

    @Test
    fun `should get all entries`() {
      val data =
          listOf(
              mapOf("param1" to "value1"),
              mapOf("param2" to 2),
              mapOf("param3" to mapOf("param4" to "value4")))
      val customDataId = "CustomData"
      val requestBody = SendRunDataRequest(id = customDataId, data = data)
      runApiService.sendRunData(
          organizationSaved.id, workspaceSaved.id!!, runnerSaved.id!!, runSavedId, requestBody)
      val queryResult =
          runApiService.queryRunData(
              organizationSaved.id,
              workspaceSaved.id!!,
              runnerSaved.id!!,
              runSavedId,
              RunDataQuery("SELECT * FROM ${customDataId.toDataTableName(false)}"))
      val expectedResult =
          listOf(
              mapOf("param1" to "value1"),
              mapOf("param2" to BigDecimal(2)),
              mapOf("param3" to mapOf("param4" to "value4")))
      assertContentEquals(expectedResult, queryResult.result!!)
    }

    @Test
    fun `should throw table do not exist`() {
      val data =
          listOf(
              mapOf("param1" to "value1"),
              mapOf("param2" to 2),
              mapOf("param3" to mapOf("param4" to "value4")))
      val customDataId = "CustomData"
      val requestBody = SendRunDataRequest(id = customDataId, data = data)
      runApiService.sendRunData(
          organizationSaved.id, workspaceSaved.id!!, runnerSaved.id!!, runSavedId, requestBody)
      val exception =
          assertThrows<PSQLException> {
            runApiService.queryRunData(
                organizationSaved.id,
                workspaceSaved.id!!,
                runnerSaved.id!!,
                runSavedId,
                RunDataQuery("SELECT * FROM ${customDataId.toDataTableName(false)}2"))
          }
      assertEquals(
          "ERROR: relation \"${customDataId.toDataTableName(false)}2\" does not exist\n  Position: 15",
          exception.message)
    }

    @Test
    fun `should not allow command other than select`() {
      val data =
          listOf(
              mapOf("param1" to "value1"),
              mapOf("param2" to 2),
              mapOf("param3" to mapOf("param4" to "value4")))
      val customDataId = "CustomData"
      val requestBody = SendRunDataRequest(id = customDataId, data = data)
      runApiService.sendRunData(
          organizationSaved.id, workspaceSaved.id!!, runnerSaved.id!!, runSavedId, requestBody)
      var e =
          assertThrows<PSQLException> {
            runApiService.queryRunData(
                organizationSaved.id,
                workspaceSaved.id!!,
                runnerSaved.id!!,
                runSavedId,
                RunDataQuery("DROP TABLE ${customDataId.toDataTableName(false)}"))
          }
      assertEquals(
          "ERROR: must be owner of table ${customDataId.toDataTableName(false)}", e.message)
      e =
          assertThrows<PSQLException> {
            runApiService.queryRunData(
                organizationSaved.id,
                workspaceSaved.id!!,
                runnerSaved.id!!,
                runSavedId,
                RunDataQuery(
                    "CREATE TABLE ${customDataId.toDataTableName(false)} (id VARCHAR(100))"))
          }
      assertEquals("ERROR: permission denied for schema public\n" + "  Position: 14", e.message)
    }

    @Test
    fun `should get all tables in dB`() {
      val data =
          listOf(
              mapOf("param1" to "value1"),
              mapOf("param2" to 2),
              mapOf("param3" to mapOf("param4" to "value4")))
      var requestBody = SendRunDataRequest(id = "table1", data = data)
      runApiService.sendRunData(
          organizationSaved.id, workspaceSaved.id!!, runnerSaved.id!!, runSavedId, requestBody)
      requestBody = SendRunDataRequest(id = "table2", data = data)
      runApiService.sendRunData(
          organizationSaved.id, workspaceSaved.id!!, runnerSaved.id!!, runSavedId, requestBody)
      requestBody = SendRunDataRequest(id = "table3", data = data)
      runApiService.sendRunData(
          organizationSaved.id, workspaceSaved.id!!, runnerSaved.id!!, runSavedId, requestBody)
      val queryResult =
          runApiService.queryRunData(
              organizationSaved.id,
              workspaceSaved.id!!,
              runnerSaved.id!!,
              runSavedId,
              RunDataQuery(
                  "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public';"))
      val expectedResult =
          listOf(
              mapOf("table_name" to ("table1").toDataTableName(false)),
              mapOf("table_name" to ("table2").toDataTableName(false)),
              mapOf("table_name" to ("table3").toDataTableName(false)))
      assertEquals(expectedResult, queryResult.result)
    }
  }
}
