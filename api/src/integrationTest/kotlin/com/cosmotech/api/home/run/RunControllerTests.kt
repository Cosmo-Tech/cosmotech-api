// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.home.run

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
import com.cosmotech.api.home.run.RunConstants.RequestContent.CONTAINER_DEPENDENCIES
import com.cosmotech.api.home.run.RunConstants.RequestContent.CONTAINER_ENTRYPOINT
import com.cosmotech.api.home.run.RunConstants.RequestContent.CONTAINER_ENV_VARS
import com.cosmotech.api.home.run.RunConstants.RequestContent.CONTAINER_ID
import com.cosmotech.api.home.run.RunConstants.RequestContent.CONTAINER_IMAGE
import com.cosmotech.api.home.run.RunConstants.RequestContent.CONTAINER_LABELS
import com.cosmotech.api.home.run.RunConstants.RequestContent.CONTAINER_NAME
import com.cosmotech.api.home.run.RunConstants.RequestContent.CONTAINER_NODE_LABEL
import com.cosmotech.api.home.run.RunConstants.RequestContent.CONTAINER_RUN_ARGS
import com.cosmotech.api.home.run.RunConstants.RequestContent.CSM_SIMULATION_RUN
import com.cosmotech.api.home.run.RunConstants.RequestContent.DATASET_LIST
import com.cosmotech.api.home.run.RunConstants.RequestContent.DESCRIPTION
import com.cosmotech.api.home.run.RunConstants.RequestContent.HOST_NODE_NAME
import com.cosmotech.api.home.run.RunConstants.RequestContent.NODE_ID
import com.cosmotech.api.home.run.RunConstants.RequestContent.NODE_LABEL
import com.cosmotech.api.home.run.RunConstants.RequestContent.NODE_MESSAGE
import com.cosmotech.api.home.run.RunConstants.RequestContent.NODE_NAME
import com.cosmotech.api.home.run.RunConstants.RequestContent.NODE_PHASE
import com.cosmotech.api.home.run.RunConstants.RequestContent.NODE_PROGRESS
import com.cosmotech.api.home.run.RunConstants.RequestContent.PARAMETER_GROUP_ID
import com.cosmotech.api.home.run.RunConstants.RequestContent.PARAMETER_LABELS
import com.cosmotech.api.home.run.RunConstants.RequestContent.RUN_TEMPLATE_COMPUTE_SIZE
import com.cosmotech.api.home.run.RunConstants.RequestContent.RUN_TEMPLATE_NAME
import com.cosmotech.api.home.run.RunConstants.RequestContent.RUN_TEMPLATE_PARAMETER_ID
import com.cosmotech.api.home.run.RunConstants.RequestContent.RUN_TEMPLATE_VALUE
import com.cosmotech.api.home.run.RunConstants.RequestContent.RUN_TEMPLATE_VAR_TYPE
import com.cosmotech.api.home.run.RunConstants.RequestContent.TAGS
import com.cosmotech.api.home.run.RunConstants.RequestContent.WORKFLOW_ID
import com.cosmotech.api.home.run.RunConstants.RequestContent.WORKFLOW_MESSAGE
import com.cosmotech.api.home.run.RunConstants.RequestContent.WORKFLOW_NAME
import com.cosmotech.api.home.run.RunConstants.RequestContent.WORKFLOW_PHASE
import com.cosmotech.api.home.run.RunConstants.RequestContent.WORKFLOW_PROGRESS
import com.cosmotech.api.home.run.RunConstants.RequestContent.WORKSPACE_KEY
import com.cosmotech.api.home.runner.RunnerConstants.RUNNER_RUN_TEMPLATE
import com.cosmotech.common.events.CsmEventPublisher
import com.cosmotech.common.events.UpdateRunnerStatus
import com.cosmotech.run.RunApiServiceInterface
import com.cosmotech.run.RunContainerFactory
import com.cosmotech.run.domain.*
import com.cosmotech.run.workflow.WorkflowService
import com.cosmotech.runner.domain.*
import com.cosmotech.runner.domain.ResourceSizeInfo
import com.cosmotech.solution.domain.RunTemplateCreateRequest
import com.cosmotech.solution.domain.RunTemplateResourceSizing
import com.ninjasquad.springmockk.SpykBean
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import java.time.Instant
import java.util.*
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@ActiveProfiles(profiles = ["test"])
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RunControllerTests : ControllerTestBase() {

  private lateinit var organizationId: String
  private lateinit var workspaceId: String
  private lateinit var solutionId: String
  private lateinit var runnerId: String
  private lateinit var runId: String
  private val logger = LoggerFactory.getLogger(RunControllerTests::class.java)

  @Autowired lateinit var runApiService: RunApiServiceInterface

  @SpykBean @Autowired private lateinit var eventPublisher: CsmEventPublisher

  @MockK(relaxed = true) private lateinit var containerFactory: RunContainerFactory
  @MockK(relaxed = true) private lateinit var workflowService: WorkflowService

  @BeforeEach
  fun beforeEach() {

    ReflectionTestUtils.setField(runApiService, "containerFactory", containerFactory)
    ReflectionTestUtils.setField(runApiService, "workflowService", workflowService)
    ReflectionTestUtils.setField(runApiService, "eventPublisher", eventPublisher)

    val runTemplateRunSizing =
        RunTemplateResourceSizing(
            com.cosmotech.solution.domain.ResourceSizeInfo("cpu_requests", "memory_requests"),
            com.cosmotech.solution.domain.ResourceSizeInfo("cpu_limits", "memory_limits"))

    val runTemplates =
        mutableListOf(
            RunTemplateCreateRequest(
                RUNNER_RUN_TEMPLATE,
                RUN_TEMPLATE_NAME,
                PARAMETER_LABELS,
                DESCRIPTION,
                TAGS,
                RUN_TEMPLATE_COMPUTE_SIZE,
                runTemplateRunSizing,
                mutableListOf(PARAMETER_GROUP_ID),
                10))

    organizationId = createOrganizationAndReturnId(mvc, constructOrganizationCreateRequest())
    solutionId =
        createSolutionAndReturnId(
            mvc, organizationId, constructSolutionCreateRequest(runTemplates = runTemplates))
    workspaceId =
        createWorkspaceAndReturnId(
            mvc, organizationId, constructWorkspaceCreateRequest(solutionId = solutionId))

    runnerId =
        createRunnerAndReturnId(
            mvc,
            organizationId,
            workspaceId,
            constructRunnerObject(
                solutionId = solutionId,
                runTemplateId = RUNNER_RUN_TEMPLATE,
                runSizing =
                    RunnerResourceSizing(
                        requests =
                            ResourceSizeInfo(
                                cpu = "1Gi",
                                memory = "1Gi",
                            ),
                        limits =
                            ResourceSizeInfo(
                                cpu = "1Gi",
                                memory = "1Gi",
                            ),
                    )))

    every { workflowService.launchRun(any(), any(), any(), any()) } returns
        mockRun(organizationId, workspaceId, solutionId)

    runId =
        JSONObject(
                mvc.perform(
                        post(
                                "/organizations/$organizationId/workspaces/$workspaceId/runners/$runnerId/start")
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON)
                            .with(csrf()))
                    .andExpect(status().is2xxSuccessful)
                    .andReturn()
                    .response
                    .contentAsString)
            .getString("id")
  }

  @Test
  @WithMockOauth2User
  fun list_runs() {

    every { eventPublisher.publishEvent(any()) } answers
        {
          firstArg<UpdateRunnerStatus>().response = "Running"
        }

    mvc.perform(
            get("/organizations/$organizationId/workspaces/$workspaceId/runners/$runnerId}/runs")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$[0].id").value(runId))
        .andExpect(jsonPath("$[0].state").value(RunState.Successful.toString()))
        .andExpect(jsonPath("$[0].organizationId").value(organizationId))
        .andExpect(jsonPath("$[0].workflowId").value(WORKFLOW_ID))
        .andExpect(jsonPath("$[0].csmSimulationRun").value(CSM_SIMULATION_RUN))
        .andExpect(jsonPath("$[0].generateName").value(""))
        .andExpect(jsonPath("$[0].workflowName").value(WORKFLOW_NAME))
        .andExpect(jsonPath("$[0].createInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$[0].workspaceId").value(workspaceId))
        .andExpect(jsonPath("$[0].workspaceKey").value(WORKSPACE_KEY))
        .andExpect(jsonPath("$[0].solutionId").value(""))
        .andExpect(jsonPath("$[0].runnerId").value(runnerId))
        .andExpect(jsonPath("$[0].runTemplateId").value(""))
        .andExpect(jsonPath("$[0].computeSize").value(""))
        .andExpect(jsonPath("$[0].nodeLabel").value(""))
        .andExpect(jsonPath("$[0].containers").value(null))
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/runs/GET"))
  }

  @Test
  @WithMockOauth2User
  fun get_run() {

    every { eventPublisher.publishEvent(any()) } answers
        {
          firstArg<UpdateRunnerStatus>().response = "Running"
        }

    mvc.perform(
            get(
                    "/organizations/$organizationId/workspaces/$workspaceId/runners/$runnerId}/runs/$runId")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.state").value(RunState.Successful.toString()))
        .andExpect(jsonPath("$.organizationId").value(organizationId))
        .andExpect(jsonPath("$.workflowId").value(WORKFLOW_ID))
        .andExpect(jsonPath("$.csmSimulationRun").value(CSM_SIMULATION_RUN))
        .andExpect(jsonPath("$.generateName").value(""))
        .andExpect(jsonPath("$.workflowName").value(WORKFLOW_NAME))
        .andExpect(jsonPath("$.createInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.workspaceId").value(workspaceId))
        .andExpect(jsonPath("$.workspaceKey").value(WORKSPACE_KEY))
        .andExpect(jsonPath("$.solutionId").value(""))
        .andExpect(jsonPath("$.runnerId").value(runnerId))
        .andExpect(jsonPath("$.runTemplateId").value(""))
        .andExpect(jsonPath("$.computeSize").value(""))
        .andExpect(jsonPath("$.nodeLabel").value(""))
        .andExpect(jsonPath("$.containers").value(null))
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/runs/{run_id}/GET"))
  }

  @Test
  @WithMockOauth2User
  fun delete_run() {

    every { workflowService.stopWorkflow(any()) } returns mockk<RunStatus>(relaxed = true)

    every { eventPublisher.publishEvent(any()) } answers
        {
          firstArg<UpdateRunnerStatus>().response = "Successful"
        } andThenAnswer
        {
          firstArg<UpdateRunnerStatus>().response = "Successful"
        } andThenAnswer
        {
          firstArg<UpdateRunnerStatus>().response = "Successful"
        } andThenAnswer
        {}

    mvc.perform(
            delete(
                    "/organizations/$organizationId/workspaces/$workspaceId/runners/$runnerId}/runs/$runId")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/runs/{run_id}/DELETE"))
  }

  @Test
  @WithMockOauth2User
  fun get_run_logs() {

    val logs =
        """This is the first line of a log entry
      |This is the second line of a log entry
      |This is the third line of a log entry"""
            .trimMargin()

    every { workflowService.getRunningLogs(any()) } returns logs

    every { eventPublisher.publishEvent(any()) } answers
        {
          firstArg<UpdateRunnerStatus>().response = "Successful"
        }

    mvc.perform(
            get(
                    "/organizations/$organizationId/workspaces/$workspaceId/runners/$runnerId}/runs/$runId/logs")
                .accept(MediaType.TEXT_PLAIN))
        .andExpect(status().is2xxSuccessful)
        .andExpect(content().string(logs))
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/runs/{run_id}/logs/GET"))
  }

  @Test
  @WithMockOauth2User
  fun get_run_status() {

    val outboundNodes = mutableListOf("nodeId2", "nodeId3")
    every { workflowService.getRunStatus(any()) } returns
        RunStatus(
            id = runId,
            organizationId = organizationId,
            workspaceId = workspaceId,
            runnerId = runnerId,
            workflowId = WORKFLOW_ID,
            workflowName = WORKFLOW_NAME,
            startTime = Date.from(Instant.now()).toString(),
            endTime = Date.from(Instant.now()).toString(),
            phase = WORKFLOW_PHASE,
            progress = WORKFLOW_PROGRESS,
            message = WORKFLOW_MESSAGE,
            estimatedDuration = 20,
            nodes =
                mutableListOf(
                    RunStatusNode(
                        id = NODE_ID,
                        name = NODE_NAME,
                        containerName = CONTAINER_NAME,
                        outboundNodes = outboundNodes,
                        resourcesDuration = RunResourceRequested(cpu = 1024, memory = 2048),
                        hostNodeName = HOST_NODE_NAME,
                        message = NODE_MESSAGE,
                        phase = NODE_PHASE,
                        progress = NODE_PROGRESS,
                        startTime = Date.from(Instant.now()).toString(),
                        endTime = Date.from(Instant.now()).toString(),
                    )),
            state = RunState.Successful)

    mvc.perform(
            get(
                    "/organizations/$organizationId/workspaces/$workspaceId/runners/$runnerId}/runs/$runId/status")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.id").value(runId))
        .andExpect(jsonPath("$.organizationId").value(organizationId))
        .andExpect(jsonPath("$.workspaceId").value(workspaceId))
        .andExpect(jsonPath("$.runnerId").value(runnerId))
        .andExpect(jsonPath("$.workflowId").value(WORKFLOW_ID))
        .andExpect(jsonPath("$.workflowName").value(WORKFLOW_NAME))
        .andExpect(jsonPath("$.phase").value(WORKFLOW_PHASE))
        .andExpect(jsonPath("$.progress").value(WORKFLOW_PROGRESS))
        .andExpect(jsonPath("$.message").value(WORKFLOW_MESSAGE))
        .andExpect(jsonPath("$.estimatedDuration").value(20))
        .andExpect(jsonPath("$.nodes[0].id").value(NODE_ID))
        .andExpect(jsonPath("$.nodes[0].name").value(NODE_NAME))
        .andExpect(jsonPath("$.nodes[0].containerName").value(CONTAINER_NAME))
        .andExpect(jsonPath("$.nodes[0].outboundNodes").value(outboundNodes))
        .andExpect(jsonPath("$.nodes[0].resourcesDuration.cpu").value(1024))
        .andExpect(jsonPath("$.nodes[0].resourcesDuration.memory").value(2048))
        .andExpect(jsonPath("$.nodes[0].hostNodeName").value(HOST_NODE_NAME))
        .andExpect(jsonPath("$.nodes[0].message").value(NODE_MESSAGE))
        .andExpect(jsonPath("$.nodes[0].phase").value(NODE_PHASE))
        .andExpect(jsonPath("$.nodes[0].progress").value(NODE_PROGRESS))
        .andExpect(jsonPath("$.state").value("Successful"))
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/runs/{run_id}/status/GET"))
  }

  private fun mockRun(organizationId: String, workspaceId: String, solutionId: String) =
      Run(
          id = "r-idgenerated",
          state = RunState.Successful,
          workflowId = WORKFLOW_ID,
          workflowName = WORKFLOW_NAME,
          csmSimulationRun = CSM_SIMULATION_RUN,
          generateName = "generated_name",
          createInfo = RunEditInfo(timestamp = 123456789, userId = "user"),
          organizationId = organizationId,
          workspaceId = workspaceId,
          workspaceKey = WORKSPACE_KEY,
          solutionId = solutionId,
          runTemplateId = RUNNER_RUN_TEMPLATE,
          computeSize = RUN_TEMPLATE_COMPUTE_SIZE,
          datasetList = DATASET_LIST,
          parametersValues =
              mutableListOf(
                  RunTemplateParameterValue(
                      parameterId = RUN_TEMPLATE_PARAMETER_ID,
                      value = RUN_TEMPLATE_VALUE,
                      varType = RUN_TEMPLATE_VAR_TYPE,
                  )),
          nodeLabel = NODE_LABEL,
          containers =
              mutableListOf(
                  RunContainer(
                      id = CONTAINER_ID,
                      name = CONTAINER_NAME,
                      image = CONTAINER_IMAGE,
                      labels = CONTAINER_LABELS,
                      envVars = CONTAINER_ENV_VARS,
                      entrypoint = CONTAINER_ENTRYPOINT,
                      runArgs = CONTAINER_RUN_ARGS,
                      dependencies = CONTAINER_DEPENDENCIES,
                      solutionContainer = true,
                      nodeLabel = CONTAINER_NODE_LABEL,
                      runSizing =
                          ContainerResourceSizing(
                              requests =
                                  ContainerResourceSizeInfo(
                                      cpu = "1Gi",
                                      memory = "1Gi",
                                  ),
                              limits =
                                  ContainerResourceSizeInfo(
                                      cpu = "1Gi",
                                      memory = "1Gi",
                                  )))),
          runnerId = runnerId)
}
