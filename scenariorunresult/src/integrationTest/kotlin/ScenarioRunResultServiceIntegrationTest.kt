// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorunresult.service

import com.cosmotech.api.azure.adx.AzureDataExplorerClient
import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.exceptions.CsmClientException
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.metrics.MonitorServiceAspect
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.tests.CsmRedisTestBase
import com.cosmotech.api.utils.getCurrentAccountIdentifier
import com.cosmotech.api.utils.getCurrentAuthenticatedRoles
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.connector.api.ConnectorApiService
import com.cosmotech.dataset.api.DatasetApiService
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.domain.Organization
import com.cosmotech.scenario.api.ScenarioApiService
import com.cosmotech.scenario.domain.Scenario
import com.cosmotech.scenariorunresult.api.ScenariorunresultApiService
import com.cosmotech.scenariorunresult.domain.ScenarioRunResult
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.solution.domain.Solution
import com.cosmotech.workspace.api.WorkspaceApiService
import com.cosmotech.workspace.azure.IWorkspaceEventHubService
import com.cosmotech.workspace.azure.WorkspaceEventHubInfo
import com.cosmotech.workspace.domain.Workspace
import com.ninjasquad.springmockk.MockkBean
import com.redis.om.spring.RediSearchIndexer
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockkStatic
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.BeforeAll
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

const val SCENARIORUN_ID = "ScenarioRun"
const val PROBE_ID = "Probe"

@ActiveProfiles(profiles = ["scenariorunresult-test"])
@ExtendWith(MockKExtension::class)
@ExtendWith(SpringExtension::class)
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ScenarioRunResultServiceIntegrationTest : CsmRedisTestBase() {
  private val logger = LoggerFactory.getLogger(ScenarioRunResultServiceIntegrationTest::class.java)

  @MockkBean lateinit var csmADX: AzureDataExplorerClient
  @MockK(relaxed = true) private lateinit var workspaceEventHubService: IWorkspaceEventHubService
  @MockK lateinit var monitorServiceAspect: MonitorServiceAspect

  // NEEDED: recreate indexes in redis
  @Autowired lateinit var rediSearchIndexer: RediSearchIndexer

  @Autowired lateinit var csmPlatformProperties: CsmPlatformProperties
  @Autowired lateinit var organizationApiService: OrganizationApiService
  @Autowired lateinit var solutionApiService: SolutionApiService
  @Autowired lateinit var workspaceApiService: WorkspaceApiService
  @Autowired lateinit var scenarioApiService: ScenarioApiService
  @Autowired lateinit var connectorApiService: ConnectorApiService
  @Autowired lateinit var datasetApiService: DatasetApiService
  @Autowired lateinit var scenarioRunResultApiService: ScenariorunresultApiService

  lateinit var cosmoArbo: CosmoArbo

  @BeforeAll
  fun isolateFromContext() {
    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns
        "test.user" // patch for monitoring
    every { getCurrentAuthenticatedRoles(any()) } returns
        listOf("user") // use for diferenciate admin

    ReflectionTestUtils.setField(
        scenarioApiService, "workspaceEventHubService", workspaceEventHubService)
    every { workspaceEventHubService.getWorkspaceEventHubInfo(any(), any(), any()) } returns
        makeWorkspaceEventHubInfo(false)
  }

  @BeforeEach
  fun setUp() {
    // NEEDED: recreate indexes in redis
    rediSearchIndexer.createIndexFor(Organization::class.java)
    rediSearchIndexer.createIndexFor(Solution::class.java)
    rediSearchIndexer.createIndexFor(Workspace::class.java)
    rediSearchIndexer.createIndexFor(Scenario::class.java)

    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
    cosmoArbo = createCosmoArbo()
  }

  @Test
  fun `test fail get no existing scenarioRunResult`() {
    val exception =
        assertThrows<CsmResourceNotFoundException> {
          scenarioRunResultApiService.getScenarioRunResult(
              cosmoArbo.organizationId,
              cosmoArbo.workspaceId,
              cosmoArbo.scenarioId,
              SCENARIORUN_ID,
              PROBE_ID)
        }
    val resultId = SCENARIORUN_ID + "_" + PROBE_ID
    assertEquals("no probe $resultId found", exception.message)
  }

  @Test
  fun `test fail create with empty body`() {
    val exception =
        assertThrows<CsmClientException> {
          scenarioRunResultApiService.sendScenarioRunResult(
              cosmoArbo.organizationId,
              cosmoArbo.workspaceId,
              cosmoArbo.scenarioId,
              SCENARIORUN_ID,
              PROBE_ID,
              mutableMapOf())
        }
    assertEquals("no data sent", exception.message)
  }

  @Test
  fun `test success Create scenarioRunResult`() {
    logger.info("create a scenarioRunResult with result")
    var result =
        scenarioRunResultApiService.sendScenarioRunResult(
            cosmoArbo.organizationId,
            cosmoArbo.workspaceId,
            cosmoArbo.scenarioId,
            SCENARIORUN_ID,
            PROBE_ID,
            mutableMapOf("var1" to "var", "value1" to "value"))

    assertNotNull(result)

    var scenarioRunResult =
        ScenarioRunResult(
            id = "${SCENARIORUN_ID}_${PROBE_ID}",
            results = mutableListOf(mutableMapOf("var1" to "var", "value1" to "value")))
    logger.info("get scenarioRunResult")
    var getResult =
        scenarioRunResultApiService.getScenarioRunResult(
            cosmoArbo.organizationId,
            cosmoArbo.workspaceId,
            cosmoArbo.scenarioId,
            SCENARIORUN_ID,
            PROBE_ID)

    assertEquals(scenarioRunResult, getResult)

    logger.info("create second result in scenarioRunResult")
    result =
        scenarioRunResultApiService.sendScenarioRunResult(
            cosmoArbo.organizationId,
            cosmoArbo.workspaceId,
            cosmoArbo.scenarioId,
            SCENARIORUN_ID,
            PROBE_ID,
            mutableMapOf("var2" to "var", "value2" to "value"))

    assertNotNull(result)

    logger.info("get scenarioRunResult")
    getResult =
        scenarioRunResultApiService.getScenarioRunResult(
            cosmoArbo.organizationId,
            cosmoArbo.workspaceId,
            cosmoArbo.scenarioId,
            SCENARIORUN_ID,
            PROBE_ID)
    scenarioRunResult =
        ScenarioRunResult(
            id = "${SCENARIORUN_ID}_${PROBE_ID}",
            results =
                mutableListOf(
                    mutableMapOf("var1" to "var", "value1" to "value"),
                    mutableMapOf("var2" to "var", "value2" to "value")))

    assertEquals(scenarioRunResult, getResult)
  }

  data class CosmoArbo(
      val organizationId: String,
      val workspaceId: String,
      val scenarioId: String,
      val solutionId: String,
      val connectorId: String,
      val datasetId: String
  )

  private fun createCosmoArbo(): CosmoArbo {
    val organization =
        makeOrganization(
            security = CsmSecurity(ROLE_NONE).addClassicRole().toOrganisationSecurity())
    val organizationSavedId = organizationApiService.registerOrganization(organization).id!!

    val solution = makeSolution(organizationSavedId)
    val solutionSavedId = solutionApiService.createSolution(organizationSavedId, solution).id!!

    val workspace =
        makeWorkspace(
            organizationSavedId,
            solutionSavedId,
            security = CsmSecurity(ROLE_NONE).addClassicRole().toWorkspaceSecurity())
    val workspaceSavedId = workspaceApiService.createWorkspace(organizationSavedId, workspace).id!!

    val connector = makeConnector()
    val connectorSaved = connectorApiService.registerConnector(connector)
    val connectorSavedId = connectorSaved.id!!

    val dataset = makeDataset(organizationSavedId, connectorSaved)
    val datasetSavedId = datasetApiService.createDataset(organizationSavedId, dataset).id!!

    val scenario =
        makeScenario(
            organizationSavedId,
            workspaceSavedId,
            solutionSavedId,
            datasetList = mutableListOf(datasetSavedId),
            security = CsmSecurity(ROLE_NONE).addClassicRole().toScenarioSecurity())
    val scenarioSavedId =
        scenarioApiService.createScenario(organizationSavedId, workspaceSavedId, scenario).id!!

    return CosmoArbo(
        organizationSavedId,
        workspaceSavedId,
        scenarioSavedId,
        solutionSavedId,
        connectorSavedId,
        datasetSavedId)
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
}
