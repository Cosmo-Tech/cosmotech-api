// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run

import com.cosmotech.common.config.CsmPlatformProperties
import com.cosmotech.common.containerregistry.ContainerRegistryService
import com.cosmotech.common.rbac.ROLE_ADMIN
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationEditInfo
import com.cosmotech.organization.domain.OrganizationSecurity
import com.cosmotech.run.domain.ContainerResourceSizeInfo
import com.cosmotech.run.domain.ContainerResourceSizing
import com.cosmotech.run.domain.RunContainer
import com.cosmotech.run.service.WORKFLOW_TYPE_RUN
import com.cosmotech.runner.api.RunnerApiService
import com.cosmotech.runner.domain.LastRunInfo
import com.cosmotech.runner.domain.LastRunInfo.LastRunStatus
import com.cosmotech.runner.domain.Runner
import com.cosmotech.runner.domain.RunnerAccessControl
import com.cosmotech.runner.domain.RunnerDatasets
import com.cosmotech.runner.domain.RunnerEditInfo
import com.cosmotech.runner.domain.RunnerRunTemplateParameterValue
import com.cosmotech.runner.domain.RunnerSecurity
import com.cosmotech.runner.domain.RunnerValidationStatus
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.solution.domain.RunTemplate
import com.cosmotech.solution.domain.RunTemplateParameter
import com.cosmotech.solution.domain.RunTemplateParameterGroup
import com.cosmotech.solution.domain.Solution
import com.cosmotech.solution.domain.SolutionEditInfo
import com.cosmotech.solution.domain.SolutionSecurity
import com.cosmotech.workspace.api.WorkspaceApiService
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceEditInfo
import com.cosmotech.workspace.domain.WorkspaceSecurity
import com.cosmotech.workspace.domain.WorkspaceSolution
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.extension.ExtendWith

private const val CSM_SIMULATION_ID = "simulationrunid"
private const val CSM_RUN_TEMPLATE_ID = "testruntemplate"

@ExtendWith(MockKExtension::class)
class ContainerFactoryTests {

  @MockK(relaxed = true) private lateinit var csmPlatformProperties: CsmPlatformProperties

  @MockK private lateinit var runnerService: RunnerApiService
  @MockK private lateinit var workspaceService: WorkspaceApiService
  @MockK private lateinit var solutionService: SolutionApiService
  @MockK private lateinit var organizationService: OrganizationApiService
  @MockK private lateinit var containerRegistryService: ContainerRegistryService

  private lateinit var factory: RunContainerFactory

  @Suppress("LongMethod")
  @BeforeTest
  fun setUp() {
    MockKAnnotations.init(this)

    every { csmPlatformProperties.namespace } returns "csm-phoenix"
    every { csmPlatformProperties.api } returns
        CsmPlatformProperties.Api(
            baseUrl = "https://api.cosmotech.com",
            version = "v1",
            basePath = "basepath",
        )
    every { csmPlatformProperties.identityProvider } returns
        CsmPlatformProperties.CsmIdentityProvider(
            defaultScopes = mapOf("This is a fake scope id" to "This is a fake scope name"),
            authorizationUrl = "http://this_is_a_fake_url.com",
            tokenUrl = "http://this_is_a_fake_token_url.com",
            containerScopes = mapOf("/.default" to "Default Scope"),
            serverBaseUrl = "http://localhost:8080",
            identity =
                CsmPlatformProperties.CsmIdentityProvider.CsmIdentity(
                    tenantId = "my_tenant_id",
                    clientId = "my_client_id",
                    clientSecret = "my_client_secret"))
    every { csmPlatformProperties.databases.resources } returns
        CsmPlatformProperties.CsmDatabasesProperties.CsmResourcesProperties(
            host = "this_is_a_host",
            port = "6973",
            password = "this_is_a_password",
        )
    every { csmPlatformProperties.containerRegistry } returns
        CsmPlatformProperties.CsmPlatformContainerRegistry(
            scheme = "https",
            host = "twinengines.azurecr.io",
            checkSolutionImage = true,
            password = "password",
            username = "username")

    every { csmPlatformProperties.databases.data } returns
        CsmPlatformProperties.CsmDatabasesProperties.CsmDataIOProperties(
            host = "localhost",
            port = 5432,
            database = "cosmotech",
            reader =
                CsmPlatformProperties.CsmDatabasesProperties.CsmDataIOProperties.CsmStorageUser(
                    username = "username", password = "password"),
            writer =
                CsmPlatformProperties.CsmDatabasesProperties.CsmDataIOProperties.CsmStorageUser(
                    username = "username", password = "password"))

    factory =
        RunContainerFactory(
            csmPlatformProperties,
            runnerService,
            workspaceService,
            solutionService,
            organizationService,
            containerRegistryService)
  }

  @Test
  fun `Container is created with correct content`() {
    val runner = getRunner()
    val organization = getOrganization()
    val workspace = getWorkspace()
    val solution = getSolution()
    val runTemplate = getRunTemplate()

    val containerStart =
        factory.buildContainersStart(
            runner = runner,
            workspace = workspace,
            organization = organization,
            solution = getSolution(),
            runId = CSM_SIMULATION_ID,
            csmSimulationId = CSM_SIMULATION_ID,
            workflowType = WORKFLOW_TYPE_RUN)

    val run_container =
        getRunContainer(solution, organization, workspace, runner, runTemplate, CSM_SIMULATION_ID)

    assertNotNull(containerStart)
    assertEquals(1, containerStart.containers.size)
    assertEquals(run_container, containerStart.containers[0])
  }

  private fun getRunContainer(
      solution: Solution,
      organization: Organization,
      workspace: Workspace,
      runner: Runner,
      runTemplate: RunTemplate,
      runId: String
  ): RunContainer {

    return RunContainer(
        name = CONTAINER_CSM_ORC,
        image = "twinengines.azurecr.io/" + solution.repository + ":" + solution.version,
        envVars =
            mapOf(
                "CSM_API_SCOPE" to "/.default",
                "CSM_API_URL" to csmPlatformProperties.api.baseUrl,
                "CSM_DATASET_ABSOLUTE_PATH" to "/mnt/scenariorun-data",
                "CSM_PARAMETERS_ABSOLUTE_PATH" to "/mnt/scenariorun-parameters",
                "CSM_OUTPUT_ABSOLUTE_PATH" to "/pkg/share/Simulation/Output",
                "CSM_TEMP_ABSOLUTE_PATH" to "/usr/tmp",
                "TWIN_CACHE_HOST" to csmPlatformProperties.databases.resources.host,
                "TWIN_CACHE_PORT" to csmPlatformProperties.databases.resources.port,
                "TWIN_CACHE_PASSWORD" to csmPlatformProperties.databases.resources.password,
                "TWIN_CACHE_USERNAME" to csmPlatformProperties.databases.resources.username,
                "IDP_CLIENT_ID" to csmPlatformProperties.identityProvider.identity.clientId,
                "IDP_CLIENT_SECRET" to csmPlatformProperties.identityProvider.identity.clientSecret,
                "IDP_BASE_URL" to csmPlatformProperties.identityProvider.serverBaseUrl,
                "IDP_TENANT_ID" to csmPlatformProperties.identityProvider.identity.tenantId,
                "CSM_SIMULATION_ID" to CSM_SIMULATION_ID,
                "CSM_ORGANIZATION_ID" to organization.id,
                "CSM_WORKSPACE_ID" to workspace.id,
                "CSM_RUNNER_ID" to runner.id,
                "CSM_RUN_ID" to runId,
                "CSM_RUN_TEMPLATE_ID" to CSM_RUN_TEMPLATE_ID),
        entrypoint = "entrypoint.py",
        nodeLabel = runTemplate.computeSize!!.removeSuffix("pool"),
        runSizing =
            ContainerResourceSizing(
                requests = ContainerResourceSizeInfo(cpu = "70", memory = "130Gi"),
                limits = ContainerResourceSizeInfo(cpu = "70", memory = "130Gi")),
        solutionContainer = true)
  }

  private fun getRunner(): Runner {
    return Runner(
        id = "RunnerId",
        name = "TestRunner",
        runTemplateId = CSM_RUN_TEMPLATE_ID,
        datasets = RunnerDatasets(bases = mutableListOf("1", "2"), parameter = "3"),
        solutionId = "solution",
        organizationId = "organization",
        workspaceId = "workspace",
        createInfo = RunnerEditInfo(timestamp = Instant.now().toEpochMilli(), userId = "user"),
        updateInfo = RunnerEditInfo(timestamp = Instant.now().toEpochMilli(), userId = "user"),
        ownerName = "owner",
        parametersValues =
            mutableListOf(
                RunnerRunTemplateParameterValue(parameterId = "param1", value = "value1"),
                RunnerRunTemplateParameterValue(parameterId = "param2", value = "value2")),
        validationStatus = RunnerValidationStatus.Draft,
        lastRunInfo = LastRunInfo(lastRunId = null, lastRunStatus = LastRunStatus.NotStarted),
        security =
            RunnerSecurity(ROLE_ADMIN, mutableListOf(RunnerAccessControl("user", ROLE_ADMIN))))
  }

  private fun getRunTemplate(): RunTemplate {
    return RunTemplate(
        id = CSM_RUN_TEMPLATE_ID,
        name = "Test Run",
        computeSize = "highcpupool",
        parameterGroups = mutableListOf())
  }

  private fun getSolution(): Solution {
    return Solution(
        id = "1",
        key = "TestSolution",
        name = "Test Solution",
        createInfo = SolutionEditInfo(0, ""),
        updateInfo = SolutionEditInfo(0, ""),
        repository = "cosmotech/testsolution_simulator",
        version = "1.0.0",
        runTemplates = mutableListOf(getRunTemplate()),
        parameters = mutableListOf(RunTemplateParameter("parameter", "string")),
        parameterGroups =
            mutableListOf(
                RunTemplateParameterGroup(
                    id = "parameter", isTable = false, parameters = mutableListOf())),
        organizationId = "Organizationid",
        security = SolutionSecurity(ROLE_ADMIN, mutableListOf()),
    )
  }

  private fun getWorkspace(): Workspace {
    return Workspace(
        id = "Workspaceid",
        key = "Test",
        organizationId = "organizationId",
        createInfo = WorkspaceEditInfo(0, ""),
        updateInfo = WorkspaceEditInfo(0, ""),
        name = "Test Workspace",
        description = "Test Workspace Description",
        version = "1.0.0",
        solution =
            WorkspaceSolution(
                solutionId = "1",
            ),
        security = WorkspaceSecurity(default = ROLE_ADMIN, accessControlList = mutableListOf()))
  }

  private fun getOrganization(): Organization {
    return Organization(
        id = "Organizationid",
        name = "Organization Test",
        createInfo = OrganizationEditInfo(0, ""),
        updateInfo = OrganizationEditInfo(0, ""),
        security =
            OrganizationSecurity(
                ROLE_ADMIN, mutableListOf(OrganizationAccessControl("user", ROLE_ADMIN))))
  }
}
