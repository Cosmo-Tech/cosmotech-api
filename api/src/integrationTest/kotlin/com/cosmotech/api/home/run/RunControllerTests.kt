// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.home.run

import com.cosmotech.api.events.CsmEventPublisher
import com.cosmotech.api.home.Constants.PLATFORM_ADMIN_EMAIL
import com.cosmotech.api.home.ControllerTestBase
import com.cosmotech.api.home.ControllerTestUtils.OrganizationUtils.constructOrganizationCreateRequest
import com.cosmotech.api.home.ControllerTestUtils.OrganizationUtils.createOrganizationAndReturnId
import com.cosmotech.api.home.ControllerTestUtils.RunnerUtils.constructRunnerObject
import com.cosmotech.api.home.ControllerTestUtils.RunnerUtils.createRunnerAndReturnId
import com.cosmotech.api.home.ControllerTestUtils.SolutionUtils.constructSolutionCreateRequest
import com.cosmotech.api.home.ControllerTestUtils.SolutionUtils.createSolutionAndReturnId
import com.cosmotech.api.home.ControllerTestUtils.WorkspaceUtils.constructWorkspaceCreateRequest
import com.cosmotech.api.home.ControllerTestUtils.WorkspaceUtils.createWorkspaceAndReturnId
import com.cosmotech.api.home.annotations.WithMockOauth2User
import com.cosmotech.api.home.runner.RunnerConstants.RUNNER_RUN_TEMPLATE
import com.cosmotech.run.RunApiServiceInterface
import com.cosmotech.run.RunContainerFactory
import com.cosmotech.run.domain.*
import com.cosmotech.run.workflow.WorkflowService
import com.cosmotech.runner.domain.*
import com.cosmotech.runner.domain.ResourceSizeInfo
import com.cosmotech.solution.domain.RunTemplate
import com.cosmotech.solution.domain.RunTemplateResourceSizing
import com.ninjasquad.springmockk.SpykBean
import io.mockk.every
import io.mockk.impl.annotations.MockK
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.*


@ActiveProfiles(profiles = ["test"])
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RunControllerTests: ControllerTestBase() {

    private lateinit var organizationId: String
    private lateinit var workspaceId: String
    private lateinit var solutionId: String
    private lateinit var runnerId: String
    private lateinit var runId: String
    private val logger = LoggerFactory.getLogger(RunControllerTests::class.java)

    @Autowired lateinit var runApiService: RunApiServiceInterface

    @SpykBean
    @Autowired
    private lateinit var eventPublisher: CsmEventPublisher

    @MockK(relaxed = true) private lateinit var containerFactory: RunContainerFactory
    @MockK(relaxed = true) private lateinit var workflowService: WorkflowService

    @BeforeEach
    fun beforeEach() {


        ReflectionTestUtils.setField(runApiService, "containerFactory", containerFactory)
        ReflectionTestUtils.setField(runApiService, "workflowService", workflowService)
        ReflectionTestUtils.setField(runApiService, "eventPublisher", eventPublisher)

        val tags = mutableListOf("tag1", "tag2")
        val description = "this_is_a_description"
        val parameterGroupId = "parameterGroup1"
        val runTemplateName = "this_is_a_name"
        val runTemplateComputeSize = "this_is_a_compute_size"
        val parameterLabels = mutableMapOf("fr" to "this_is_a_label")
        val runTemplateRunSizing = RunTemplateResourceSizing(
            com.cosmotech.solution.domain.ResourceSizeInfo(
                "cpu_requests",
                "memory_requests"
            ),
            com.cosmotech.solution.domain.ResourceSizeInfo(
                "cpu_limits",
                "memory_limits"
            )
        )
        val runTemplates = mutableListOf(
            RunTemplate(
                RUNNER_RUN_TEMPLATE,
                runTemplateName,
                parameterLabels,
                description,
                tags,
                runTemplateComputeSize,
                runTemplateRunSizing,
                mutableListOf(parameterGroupId),
                10
            )
        )


        organizationId = createOrganizationAndReturnId(mvc, constructOrganizationCreateRequest())
        solutionId = createSolutionAndReturnId(
            mvc, organizationId,
            constructSolutionCreateRequest(
                runTemplates = runTemplates
            )
        )
        workspaceId = createWorkspaceAndReturnId(
            mvc, organizationId,
            constructWorkspaceCreateRequest(solutionId = solutionId)
        )

        runnerId = createRunnerAndReturnId(
            mvc,
            organizationId,
            workspaceId,
            constructRunnerObject(
                solutionId = solutionId,
                runTemplateId = RUNNER_RUN_TEMPLATE,
                runSizing = RunnerResourceSizing(
                    requests = ResourceSizeInfo(
                        cpu = "1Gi",
                        memory = "1Gi",
                    ),
                    limits = ResourceSizeInfo(
                        cpu = "1Gi",
                        memory = "1Gi",
                    ),
                )
            )
        )


        every { workflowService.launchRun(any(), any(), any(), any()) } returns
                mockRun(
                    organizationId,
                    workspaceId,
                    solutionId)

        runId = JSONObject(mvc
            .perform(
                post("/organizations/$organizationId/workspaces/$workspaceId/runners/$runnerId/start")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf())
            )
            .andExpect(status().is2xxSuccessful).andReturn().response.contentAsString).getString("id")

    }

    @Test
    @WithMockOauth2User
    fun list_runners() {

            mvc
                .perform(
                    get("/organizations/$organizationId/workspaces/$workspaceId/runners/$runnerId}/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf())
                )
            .andExpect(status().is2xxSuccessful)
            .andExpect(jsonPath("$[0].id").value(runId))
            .andExpect(jsonPath("$[0].state").value(RunState.Successful.toString()))
            .andExpect(jsonPath("$[0].organizationId").value(organizationId))
            .andExpect(jsonPath("$[0].workflowId").value("a_workflow_id"))
            .andExpect(jsonPath("$[0].csmSimulationRun").value("a_csm_simulation_run"))
            .andExpect(jsonPath("$[0].generateName").value(""))
            .andExpect(jsonPath("$[0].workflowName").value("a_workflow_name"))
            .andExpect(jsonPath("$[0].ownerId").value(PLATFORM_ADMIN_EMAIL))
            .andExpect(jsonPath("$[0].workspaceId").value(workspaceId))
            .andExpect(jsonPath("$[0].workspaceKey").value("workspaceKey"))
            .andExpect(jsonPath("$[0].solutionId").value(""))
            .andExpect(jsonPath("$[0].runnerId").value(runnerId))
            .andExpect(jsonPath("$[0].runTemplateId").value(""))
            .andExpect(jsonPath("$[0].computeSize").value(""))
            .andExpect(jsonPath("$[0].nodeLabel").value(""))
            .andExpect(jsonPath("$[0].containers").value(null))
            .andDo(MockMvcResultHandlers.print())
            .andDo(document("organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/runs/GET"))
    }

    @Test
    @WithMockOauth2User
    fun get_runner() {

        mvc
            .perform(
                get("/organizations/$organizationId/workspaces/$workspaceId/runners/$runnerId}/runs/$runId")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().is2xxSuccessful)
            .andExpect(jsonPath("$.state").value(RunState.Successful.toString()))
            .andExpect(jsonPath("$.organizationId").value(organizationId))
            .andExpect(jsonPath("$.workflowId").value("a_workflow_id"))
            .andExpect(jsonPath("$.csmSimulationRun").value("a_csm_simulation_run"))
            .andExpect(jsonPath("$.generateName").value(""))
            .andExpect(jsonPath("$.workflowName").value("a_workflow_name"))
            .andExpect(jsonPath("$.ownerId").value(PLATFORM_ADMIN_EMAIL))
            .andExpect(jsonPath("$.workspaceId").value(workspaceId))
            .andExpect(jsonPath("$.workspaceKey").value("workspaceKey"))
            .andExpect(jsonPath("$.solutionId").value(""))
            .andExpect(jsonPath("$.runnerId").value(runnerId))
            .andExpect(jsonPath("$.runTemplateId").value(""))
            .andExpect(jsonPath("$.computeSize").value(""))
            .andExpect(jsonPath("$.nodeLabel").value(""))
            .andExpect(jsonPath("$.containers").value(null))
            .andDo(MockMvcResultHandlers.print())
            .andDo(document("organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/runs/{run_id}/GET"))
    }


    @Test
    @WithMockOauth2User
    fun delete_runner() {

        mvc
            .perform(
                delete("/organizations/$organizationId/workspaces/$workspaceId/runners/$runnerId}/runs/$runId")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf())
            )
            .andExpect(status().is2xxSuccessful)
            .andDo(MockMvcResultHandlers.print())
            .andDo(document("organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/runs/{run_id}/DELETE"))
    }

    @Test
    @WithMockOauth2User
    fun send_data_runner() {

        val dataToSend = """{
                      "id": "my_table",
                      "data": [
                        {
                          "additionalProp1": {},
                          "additionalProp2": "test",
                          "additionalProp3": 100
                        },
                        {
                          "additionalProp1": {},
                          "additionalProp2": "test",
                          "additionalProp4": 1000
                        }
                      ]
                    }"""
            mvc
                .perform(
                    post("/organizations/$organizationId/workspaces/$workspaceId/runners/$runnerId}/runs/$runId/data/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dataToSend)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf())
                )
                .andExpect(status().is2xxSuccessful)
                .andExpect(jsonPath("$.database_name").value(runId))
                .andExpect(jsonPath("$.table_name").value("cd_my_table"))
                .andExpect(jsonPath("$.data[0].additionalProp1").value(""))
                .andExpect(jsonPath("$.data[0].additionalProp2").value("test"))
                .andExpect(jsonPath("$.data[0].additionalProp3").value(100))
                .andExpect(jsonPath("$.data[1].additionalProp1").value(""))
                .andExpect(jsonPath("$.data[1].additionalProp2").value("test"))
                .andExpect(jsonPath("$.data[1].additionalProp4").value(1000))
                .andDo(MockMvcResultHandlers.print())
                .andDo(document("organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/runs/{run_id}/data/send/POST"))

    }


    @Test
    @WithMockOauth2User
    fun query_data_runner() {

        val dataToSend = """{
                      "id": "my_table",
                      "data": [
                        {
                          "additionalProp1": {},
                          "additionalProp2": "test",
                          "additionalProp3": 100
                        },
                        {
                          "additionalProp1": {},
                          "additionalProp2": "test",
                          "additionalProp4": 1000
                        }
                      ]
                    }"""
        mvc
            .perform(
                post("/organizations/$organizationId/workspaces/$workspaceId/runners/$runnerId}/runs/$runId/data/send")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(dataToSend)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf())
            )
            .andExpect(status().is2xxSuccessful)

        mvc
            .perform(
                post("/organizations/$organizationId/workspaces/$workspaceId/runners/$runnerId}/runs/$runId/data/query")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{
                              "query": "SELECT * FROM cd_my_table"
                            }"""
                    )
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf())
            )
            .andExpect(status().is2xxSuccessful)
            .andExpect(jsonPath("$.result[0].additionalprop1").value(""))
            .andExpect(jsonPath("$.result[0].additionalprop2").value("test"))
            .andExpect(jsonPath("$.result[0].additionalprop3").value(100))
            .andExpect(jsonPath("$.result[1].additionalprop1").value(""))
            .andExpect(jsonPath("$.result[1].additionalprop2").value("test"))
            .andExpect(jsonPath("$.result[1].additionalprop4").value(1000))
            .andDo(MockMvcResultHandlers.print())
            .andDo(document("organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/runs/{run_id}/data/query/POST"))


    }


    @Test
    @WithMockOauth2User
    fun get_run_logs() {

        every{ workflowService.getRunLogs(any())} returns
                RunLogs(
                    runId = runId,
                    logs = mutableListOf(
                        RunLogsEntry("This is a log entry"),
                        RunLogsEntry("This is another log entry"),
                        RunLogsEntry("This is the last log entry"),
                        )
                )


        mvc
            .perform(
                get("/organizations/$organizationId/workspaces/$workspaceId/runners/$runnerId}/runs/$runId/logs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
            ).andExpect(status().is2xxSuccessful)
            .andExpect(jsonPath("$.runId").value(runId))
            .andExpect(jsonPath("$.logs[0].line").value("This is a log entry"))
            .andExpect(jsonPath("$.logs[1].line").value("This is another log entry"))
            .andExpect(jsonPath("$.logs[2].line").value("This is the last log entry"))
            .andDo(MockMvcResultHandlers.print())
            .andDo(document("organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/runs/{run_id}/logs/GET"))
    }


    @Test
    @WithMockOauth2User
    fun get_run_status() {

        val outboundNodes = mutableListOf("nodeId2", "nodeId3")
        every{ workflowService.getRunStatus(any())} returns
                RunStatus(
                    id = runId,
                    organizationId = organizationId,
                    workspaceId = workspaceId,
                    runnerId = runnerId,
                    workflowId = "a_workflow_id",
                    workflowName = "a_workflow_name",
                    startTime = Date.from(Instant.now()).toString(),
                    endTime = Date.from(Instant.now()).toString(),
                    phase = "Succeeded",
                    progress = "progress",
                    message = "this_is_a_message",
                    estimatedDuration = 20,
                    nodes = mutableListOf(
                        RunStatusNode(
                            id = "nodeId1",
                            name = "nodeName",
                            containerName = "containerName",
                            outboundNodes = outboundNodes,
                            resourcesDuration = RunResourceRequested( cpu = 1024, memory = 2048),
                            hostNodeName = "hostNodeName",
                            message = "this_is_a_message",
                            phase = "Succeeded",
                            progress = "progress",
                            startTime = Date.from(Instant.now()).toString(),
                            endTime = Date.from(Instant.now()).toString(),
                        )),
                    state = RunState.Successful
                )


        mvc
            .perform(
                get("/organizations/$organizationId/workspaces/$workspaceId/runners/$runnerId}/runs/$runId/status")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
            ).andExpect(status().is2xxSuccessful)
            .andExpect(jsonPath("$.id").value(runId))
            .andExpect(jsonPath("$.organizationId").value(organizationId))
            .andExpect(jsonPath("$.workspaceId").value(workspaceId))
            .andExpect(jsonPath("$.runnerId").value(runnerId))
            .andExpect(jsonPath("$.workflowId").value("a_workflow_id"))
            .andExpect(jsonPath("$.workflowName").value("a_workflow_name"))
            .andExpect(jsonPath("$.phase").value("Succeeded"))
            .andExpect(jsonPath("$.progress").value("progress"))
            .andExpect(jsonPath("$.message").value("this_is_a_message"))
            .andExpect(jsonPath("$.estimatedDuration").value(20))
            .andExpect(jsonPath("$.nodes[0].id").value("nodeId1"))
            .andExpect(jsonPath("$.nodes[0].name").value("nodeName"))
            .andExpect(jsonPath("$.nodes[0].containerName").value("containerName"))
            .andExpect(jsonPath("$.nodes[0].outboundNodes").value(outboundNodes))
            .andExpect(jsonPath("$.nodes[0].resourcesDuration.cpu").value(1024))
            .andExpect(jsonPath("$.nodes[0].resourcesDuration.memory").value(2048))
            .andExpect(jsonPath("$.nodes[0].hostNodeName").value("hostNodeName"))
            .andExpect(jsonPath("$.nodes[0].message").value("this_is_a_message"))
            .andExpect(jsonPath("$.nodes[0].phase").value("Succeeded"))
            .andExpect(jsonPath("$.nodes[0].progress").value("progress"))
            .andExpect(jsonPath("$.state").value("Successful"))
            .andDo(MockMvcResultHandlers.print())
            .andDo(document("organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/runs/{run_id}/status/GET"))
    }



    private fun mockRun(
        organizationId: String,
        workspaceId: String,
        solutionId: String
    ) = Run(
        id = "r-idgenerated",
        state = RunState.Successful,
        workflowId = "a_workflow_id",
        workflowName = "a_workflow_name",
        ownerId = PLATFORM_ADMIN_EMAIL,
        csmSimulationRun = "a_csm_simulation_run",
        generateName = "generated_name",
        organizationId = organizationId,
        workspaceId = workspaceId,
        workspaceKey = "workspaceKey",
        solutionId = solutionId,
        runTemplateId = RUNNER_RUN_TEMPLATE,
        computeSize = "this_is_a_compute_size",
        createdAt = Date.from(Instant.now()).toString(),
        datasetList = mutableListOf("datasetId1"),
        parametersValues = mutableListOf(
            RunTemplateParameterValue(
                parameterId = "parameterId1",
                value = "this_is_a_value",
                varType = "this_is_a_vartype",
            )
        ),
        nodeLabel = "this_is_a_nodeLabel",
        containers = mutableListOf(
            RunContainer(
                id = "containerId",
                name = "containerName",
                image = "containerImage",
                labels = mutableMapOf("fr" to "this_is_a_label"),
                envVars = mutableMapOf("envvar1" to "envvar1_value"),
                entrypoint = "this_is_an_entrypoint",
                runArgs = mutableListOf("runArgs1", "runArgs2"),
                dependencies = mutableListOf("dependency1", "dependency2"),
                solutionContainer = true,
                nodeLabel = "this_is_a_nodeLabel_too",
                runSizing = ContainerResourceSizing(
                    requests = ContainerResourceSizeInfo(
                        cpu = "1Gi",
                        memory = "1Gi",
                    ),
                    limits = ContainerResourceSizeInfo(
                        cpu = "1Gi",
                        memory = "1Gi",
                    )
                )
            )
        ),
        runnerId = runnerId
    )


}