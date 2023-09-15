// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorunresult.service

import com.cosmotech.api.azure.adx.AzureDataExplorerClient
import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.rbac.*
import com.cosmotech.api.tests.CsmRedisTestBase
import com.cosmotech.api.utils.getCurrentAccountIdentifier
import com.cosmotech.api.utils.getCurrentAuthenticatedRoles
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.connector.api.ConnectorApiService
import com.cosmotech.dataset.api.DatasetApiService
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.scenario.api.ScenarioApiService
import com.cosmotech.scenario.domain.Scenario
import com.cosmotech.scenario.domain.ScenarioAccessControl
import com.cosmotech.scenariorunresult.api.ScenariorunresultApiService
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.solution.domain.Solution
import com.cosmotech.workspace.api.WorkspaceApiService
import com.cosmotech.workspace.azure.IWorkspaceEventHubService
import com.cosmotech.workspace.azure.WorkspaceEventHubInfo
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceAccessControl
import com.ninjasquad.springmockk.MockkBean
import com.redis.om.spring.RediSearchIndexer
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockkStatic
import java.util.*
import kotlin.test.assertEquals
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.runner.RunWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.util.ReflectionTestUtils

const val TEST_USER = "TEST_USER.mail@cosmotech.com"

@ActiveProfiles(profiles = ["scenariorunresult-test"])
@ExtendWith(MockKExtension::class)
@ExtendWith(SpringExtension::class)
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ScenarioRunResultServiceRBACTest : CsmRedisTestBase() {
  private val logger = LoggerFactory.getLogger(ScenarioRunResultServiceRBACTest::class.java)

  @MockkBean lateinit var csmADX: AzureDataExplorerClient
  @MockK(relaxed = true) private lateinit var workspaceEventHubService: IWorkspaceEventHubService

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
  fun setUp() {
    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "test.user"
    every { getCurrentAuthenticatedRoles(any()) } returns
        listOf("user") // use for diferenciate admin

    ReflectionTestUtils.setField(
        scenarioApiService, "workspaceEventHubService", workspaceEventHubService)
    every { workspaceEventHubService.getWorkspaceEventHubInfo(any(), any(), any()) } returns
        makeWorkspaceEventHubInfo(false)
  }

  @BeforeEach
  fun setIndexes() {
    // NEEDED: recreate indexes in redis
    rediSearchIndexer.createIndexFor(Organization::class.java)
    rediSearchIndexer.createIndexFor(Solution::class.java)
    rediSearchIndexer.createIndexFor(Workspace::class.java)
    rediSearchIndexer.createIndexFor(Scenario::class.java)

    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
    cosmoArbo = createCosmoArbo()
  }

  @TestFactory
  fun `test organization PERMISSION_READ access on send scenarioRunResults`(): List<DynamicTest> {
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
    workspaceApiService.addWorkspaceAccessControl(
        cosmoArbo.organizationId,
        cosmoArbo.workspaceId,
        WorkspaceAccessControl(TEST_USER, ROLE_ADMIN))
    scenarioApiService.addScenarioAccessControl(
        cosmoArbo.organizationId,
        cosmoArbo.workspaceId,
        cosmoArbo.scenarioId,
        ScenarioAccessControl(TEST_USER, ROLE_ADMIN))

    return listOf(
            ROLE_VIEWER to true,
            ROLE_USER to true,
            ROLE_EDITOR to true,
            ROLE_ADMIN to true,
        )
        .map { (role, shouldPass) ->
          DynamicTest.dynamicTest("Access with role $role on organization") {
            every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
            organizationApiService.addOrganizationAccessControl(
                cosmoArbo.organizationId, OrganizationAccessControl(TEST_USER, role))
            every { getCurrentAccountIdentifier(any()) } returns TEST_USER

            // ACT & ASSERT
            if (shouldPass) {
              assertDoesNotThrow {
                scenarioRunResultApiService.sendScenarioRunResult(
                    cosmoArbo.organizationId,
                    cosmoArbo.workspaceId,
                    cosmoArbo.scenarioId,
                    SCENARIORUN_ID,
                    PROBE_ID,
                    mutableMapOf("var1" to "var", "value1" to "value"))
              }
            } else {
              val exception =
                  assertThrows<CsmAccessForbiddenException> {
                    scenarioRunResultApiService.sendScenarioRunResult(
                        cosmoArbo.organizationId,
                        cosmoArbo.workspaceId,
                        cosmoArbo.scenarioId,
                        SCENARIORUN_ID,
                        PROBE_ID,
                        mutableMapOf("var1" to "var", "value1" to "value"))
                  }
              assertEquals(
                  "RBAC ${cosmoArbo.organizationId} - User does not have permission $PERMISSION_READ",
                  exception.message)
            }
          }
        }
  }

  @TestFactory
  fun `test workspace PERMISSION_READ access on send scenarioRunResults`(): List<DynamicTest> {
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
    organizationApiService.addOrganizationAccessControl(
        cosmoArbo.organizationId, OrganizationAccessControl(TEST_USER, ROLE_ADMIN))
    scenarioApiService.addScenarioAccessControl(
        cosmoArbo.organizationId,
        cosmoArbo.workspaceId,
        cosmoArbo.scenarioId,
        ScenarioAccessControl(TEST_USER, ROLE_ADMIN))

    return listOf(
            ROLE_VIEWER to true,
            ROLE_USER to true,
            ROLE_EDITOR to true,
            ROLE_ADMIN to true,
        )
        .map { (role, shouldPass) ->
          DynamicTest.dynamicTest("Access with role $role on organization") {
            every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
            workspaceApiService.addWorkspaceAccessControl(
                cosmoArbo.organizationId,
                cosmoArbo.workspaceId,
                WorkspaceAccessControl(TEST_USER, role))
            every { getCurrentAccountIdentifier(any()) } returns TEST_USER

            // ACT & ASSERT
            if (shouldPass) {
              assertDoesNotThrow {
                scenarioRunResultApiService.sendScenarioRunResult(
                    cosmoArbo.organizationId,
                    cosmoArbo.workspaceId,
                    cosmoArbo.scenarioId,
                    SCENARIORUN_ID,
                    PROBE_ID,
                    mutableMapOf("var1" to "var", "value1" to "value"))
              }
            } else {
              val exception =
                  assertThrows<CsmAccessForbiddenException> {
                    scenarioRunResultApiService.sendScenarioRunResult(
                        cosmoArbo.organizationId,
                        cosmoArbo.workspaceId,
                        cosmoArbo.scenarioId,
                        SCENARIORUN_ID,
                        PROBE_ID,
                        mutableMapOf("var1" to "var", "value1" to "value"))
                  }
              assertEquals(
                  "RBAC ${cosmoArbo.workspaceId} - User does not have permission $PERMISSION_READ",
                  exception.message)
            }
          }
        }
  }

  @TestFactory
  fun `test scenario PERMISSION_WRITE access on send scenarioRunResults`(): List<DynamicTest> {
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
    organizationApiService.addOrganizationAccessControl(
        cosmoArbo.organizationId, OrganizationAccessControl(TEST_USER, ROLE_ADMIN))
    workspaceApiService.addWorkspaceAccessControl(
        cosmoArbo.organizationId,
        cosmoArbo.workspaceId,
        WorkspaceAccessControl(TEST_USER, ROLE_ADMIN))

    return listOf(
            ROLE_VIEWER to false,
            ROLE_EDITOR to true,
            ROLE_VALIDATOR to true,
            ROLE_ADMIN to true,
        )
        .map { (role, shouldPass) ->
          DynamicTest.dynamicTest("Access with role $role on organization") {
            every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
            scenarioApiService.addScenarioAccessControl(
                cosmoArbo.organizationId,
                cosmoArbo.workspaceId,
                cosmoArbo.scenarioId,
                ScenarioAccessControl(TEST_USER, role))
            every { getCurrentAccountIdentifier(any()) } returns TEST_USER

            // ACT & ASSERT
            if (shouldPass) {
              assertDoesNotThrow {
                scenarioRunResultApiService.sendScenarioRunResult(
                    cosmoArbo.organizationId,
                    cosmoArbo.workspaceId,
                    cosmoArbo.scenarioId,
                    SCENARIORUN_ID,
                    PROBE_ID,
                    mutableMapOf("var1" to "var", "value1" to "value"))
              }
            } else {
              val exception =
                  assertThrows<CsmAccessForbiddenException> {
                    scenarioRunResultApiService.sendScenarioRunResult(
                        cosmoArbo.organizationId,
                        cosmoArbo.workspaceId,
                        cosmoArbo.scenarioId,
                        SCENARIORUN_ID,
                        PROBE_ID,
                        mutableMapOf("var1" to "var", "value1" to "value"))
                  }
              assertEquals(
                  "RBAC ${cosmoArbo.scenarioId} - User does not have permission $PERMISSION_WRITE",
                  exception.message)
            }
          }
        }
  }

  @TestFactory
  fun `test organization PERMISSION_READ access on get scenarioRunResults`(): List<DynamicTest> {
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
    workspaceApiService.addWorkspaceAccessControl(
        cosmoArbo.organizationId,
        cosmoArbo.workspaceId,
        WorkspaceAccessControl(TEST_USER, ROLE_ADMIN))
    scenarioApiService.addScenarioAccessControl(
        cosmoArbo.organizationId,
        cosmoArbo.workspaceId,
        cosmoArbo.scenarioId,
        ScenarioAccessControl(TEST_USER, ROLE_ADMIN))
    scenarioRunResultApiService.sendScenarioRunResult(
        cosmoArbo.organizationId,
        cosmoArbo.workspaceId,
        cosmoArbo.scenarioId,
        SCENARIORUN_ID,
        PROBE_ID,
        mutableMapOf("var1" to "var", "value1" to "value"))

    return listOf(
            ROLE_VIEWER to true,
            ROLE_USER to true,
            ROLE_EDITOR to true,
            ROLE_ADMIN to true,
        )
        .map { (role, shouldPass) ->
          DynamicTest.dynamicTest("Access with role $role on organization") {
            every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
            organizationApiService.addOrganizationAccessControl(
                cosmoArbo.organizationId, OrganizationAccessControl(TEST_USER, role))
            every { getCurrentAccountIdentifier(any()) } returns TEST_USER

            // ACT & ASSERT
            if (shouldPass) {
              assertDoesNotThrow {
                scenarioRunResultApiService.getScenarioRunResult(
                    cosmoArbo.organizationId,
                    cosmoArbo.workspaceId,
                    cosmoArbo.scenarioId,
                    SCENARIORUN_ID,
                    PROBE_ID)
              }
            } else {
              val exception =
                  assertThrows<CsmAccessForbiddenException> {
                    scenarioRunResultApiService.getScenarioRunResult(
                        cosmoArbo.organizationId,
                        cosmoArbo.workspaceId,
                        cosmoArbo.scenarioId,
                        SCENARIORUN_ID,
                        PROBE_ID)
                  }
              assertEquals(
                  "RBAC ${cosmoArbo.organizationId} - User does not have permission $PERMISSION_READ",
                  exception.message)
            }
          }
        }
  }

  @TestFactory
  fun `test workspace PERMISSION_READ access on get scenarioRunResults`(): List<DynamicTest> {
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
    organizationApiService.addOrganizationAccessControl(
        cosmoArbo.organizationId, OrganizationAccessControl(TEST_USER, ROLE_ADMIN))
    scenarioApiService.addScenarioAccessControl(
        cosmoArbo.organizationId,
        cosmoArbo.workspaceId,
        cosmoArbo.scenarioId,
        ScenarioAccessControl(TEST_USER, ROLE_ADMIN))
    scenarioRunResultApiService.sendScenarioRunResult(
        cosmoArbo.organizationId,
        cosmoArbo.workspaceId,
        cosmoArbo.scenarioId,
        SCENARIORUN_ID,
        PROBE_ID,
        mutableMapOf("var1" to "var", "value1" to "value"))

    return listOf(
            ROLE_VIEWER to true,
            ROLE_USER to true,
            ROLE_EDITOR to true,
            ROLE_ADMIN to true,
        )
        .map { (role, shouldPass) ->
          DynamicTest.dynamicTest("Access with role $role on organization") {
            every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
            workspaceApiService.addWorkspaceAccessControl(
                cosmoArbo.organizationId,
                cosmoArbo.workspaceId,
                WorkspaceAccessControl(TEST_USER, role))
            every { getCurrentAccountIdentifier(any()) } returns TEST_USER

            // ACT & ASSERT
            if (shouldPass) {
              assertDoesNotThrow {
                scenarioRunResultApiService.getScenarioRunResult(
                    cosmoArbo.organizationId,
                    cosmoArbo.workspaceId,
                    cosmoArbo.scenarioId,
                    SCENARIORUN_ID,
                    PROBE_ID)
              }
            } else {
              val exception =
                  assertThrows<CsmAccessForbiddenException> {
                    scenarioRunResultApiService.getScenarioRunResult(
                        cosmoArbo.organizationId,
                        cosmoArbo.workspaceId,
                        cosmoArbo.scenarioId,
                        SCENARIORUN_ID,
                        PROBE_ID)
                  }
              assertEquals(
                  "RBAC ${cosmoArbo.workspaceId} - User does not have permission $PERMISSION_READ",
                  exception.message)
            }
          }
        }
  }

  @TestFactory
  fun `test scenario PERMISSION_READ access on get scenarioRunResults`(): List<DynamicTest> {
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
    organizationApiService.addOrganizationAccessControl(
        cosmoArbo.organizationId, OrganizationAccessControl(TEST_USER, ROLE_ADMIN))
    workspaceApiService.addWorkspaceAccessControl(
        cosmoArbo.organizationId,
        cosmoArbo.workspaceId,
        WorkspaceAccessControl(TEST_USER, ROLE_ADMIN))
    scenarioRunResultApiService.sendScenarioRunResult(
        cosmoArbo.organizationId,
        cosmoArbo.workspaceId,
        cosmoArbo.scenarioId,
        SCENARIORUN_ID,
        PROBE_ID,
        mutableMapOf("var1" to "var", "value1" to "value"))

    return listOf(
            ROLE_VIEWER to true,
            ROLE_EDITOR to true,
            ROLE_VALIDATOR to true,
            ROLE_ADMIN to true,
        )
        .map { (role, shouldPass) ->
          DynamicTest.dynamicTest("Access with role $role on organization") {
            every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
            scenarioApiService.addScenarioAccessControl(
                cosmoArbo.organizationId,
                cosmoArbo.workspaceId,
                cosmoArbo.scenarioId,
                ScenarioAccessControl(TEST_USER, role))
            every { getCurrentAccountIdentifier(any()) } returns TEST_USER

            // ACT & ASSERT
            if (shouldPass) {
              assertDoesNotThrow {
                scenarioRunResultApiService.getScenarioRunResult(
                    cosmoArbo.organizationId,
                    cosmoArbo.workspaceId,
                    cosmoArbo.scenarioId,
                    SCENARIORUN_ID,
                    PROBE_ID)
              }
            } else {
              val exception =
                  assertThrows<CsmAccessForbiddenException> {
                    scenarioRunResultApiService.getScenarioRunResult(
                        cosmoArbo.organizationId,
                        cosmoArbo.workspaceId,
                        cosmoArbo.scenarioId,
                        SCENARIORUN_ID,
                        PROBE_ID)
                  }
              assertEquals(
                  "RBAC ${cosmoArbo.scenarioId} - User does not have permission $PERMISSION_READ",
                  exception.message)
            }
          }
        }
  }

  data class CosmoArbo(val organizationId: String, val workspaceId: String, val scenarioId: String)

  private fun createCosmoArbo(): CosmoArbo {
    val organization =
        makeOrganization(
            security =
                CsmSecurity(defaultRole = ROLE_NONE).addClassicRole().toOrganisationSecurity())
    val organizationSavedId = organizationApiService.registerOrganization(organization).id!!

    val solution = makeSolution(organizationSavedId)
    val solutionSavedId = solutionApiService.createSolution(organizationSavedId, solution).id!!

    val workspace =
        makeWorkspace(
            organizationSavedId,
            solutionSavedId,
            security = CsmSecurity(defaultRole = ROLE_NONE).addClassicRole().toWorkspaceSecurity())
    val workspaceSavedId = workspaceApiService.createWorkspace(organizationSavedId, workspace).id!!

    val connector = makeConnector()
    val connectorSavedId = connectorApiService.registerConnector(connector)

    val dataset = makeDataset(organizationSavedId, connectorSavedId)
    val datasetSavedId = datasetApiService.createDataset(organizationSavedId, dataset).id!!

    val scenario =
        makeScenario(
            organizationSavedId,
            workspaceSavedId,
            solutionSavedId,
            datasetList = mutableListOf(datasetSavedId),
            security = CsmSecurity(defaultRole = ROLE_NONE).addClassicRole().toScenarioSecurity())
    val scenarioSavedId =
        scenarioApiService.createScenario(organizationSavedId, workspaceSavedId, scenario).id!!

    return CosmoArbo(organizationSavedId, workspaceSavedId, scenarioSavedId)
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
