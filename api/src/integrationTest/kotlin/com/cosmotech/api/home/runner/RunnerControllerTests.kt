// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.home.runner

import com.cosmotech.api.home.Constants.PLATFORM_ADMIN_EMAIL
import com.cosmotech.api.home.ControllerTestBase
import com.cosmotech.api.home.ControllerTestUtils.DatasetUtils.constructDatasetCreateRequest
import com.cosmotech.api.home.ControllerTestUtils.DatasetUtils.createDatasetAndReturnId
import com.cosmotech.api.home.ControllerTestUtils.DatasetUtils.getDatasetPartsId
import com.cosmotech.api.home.ControllerTestUtils.OrganizationUtils.constructOrganizationCreateRequest
import com.cosmotech.api.home.ControllerTestUtils.OrganizationUtils.createOrganizationAndReturnId
import com.cosmotech.api.home.ControllerTestUtils.RunnerUtils.constructRunnerObject
import com.cosmotech.api.home.ControllerTestUtils.RunnerUtils.constructUpdateRunnerObject
import com.cosmotech.api.home.ControllerTestUtils.RunnerUtils.createRunnerAndReturnId
import com.cosmotech.api.home.ControllerTestUtils.SolutionUtils.constructSolutionCreateRequest
import com.cosmotech.api.home.ControllerTestUtils.SolutionUtils.createSolutionAndReturnId
import com.cosmotech.api.home.ControllerTestUtils.WorkspaceUtils.constructWorkspaceCreateRequest
import com.cosmotech.api.home.ControllerTestUtils.WorkspaceUtils.createWorkspaceAndReturnId
import com.cosmotech.api.home.dataset.DatasetConstants.TEST_FILE_NAME
import com.cosmotech.api.home.organization.OrganizationConstants
import com.cosmotech.api.home.runner.RunnerConstants.NEW_USER_ID
import com.cosmotech.api.home.runner.RunnerConstants.NEW_USER_ROLE
import com.cosmotech.api.home.runner.RunnerConstants.RUNNER_NAME
import com.cosmotech.api.home.runner.RunnerConstants.RUNNER_RUN_TEMPLATE
import com.cosmotech.api.home.withPlatformAdminHeader
import com.cosmotech.common.events.CsmEventPublisher
import com.cosmotech.common.events.RunStart
import com.cosmotech.common.events.UpdateRunnerStatus
import com.cosmotech.common.rbac.ROLE_ADMIN
import com.cosmotech.common.rbac.ROLE_NONE
import com.cosmotech.common.rbac.ROLE_VIEWER
import com.cosmotech.dataset.domain.DatasetPartTypeEnum
import com.cosmotech.runner.domain.*
import com.cosmotech.runner.domain.ResourceSizeInfo
import com.cosmotech.runner.service.DATASET_PART_VARTYPE_FILE
import com.cosmotech.solution.domain.RunTemplateCreateRequest
import com.cosmotech.solution.domain.RunTemplateParameterCreateRequest
import com.cosmotech.solution.domain.RunTemplateParameterGroupCreateRequest
import com.cosmotech.solution.domain.RunTemplateResourceSizing
import com.cosmotech.workspace.domain.WorkspaceSolution
import com.cosmotech.workspace.domain.WorkspaceUpdateRequest
import com.ninjasquad.springmockk.MockkSpyBean
import io.mockk.every
import org.hamcrest.core.StringContains.containsString
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@ActiveProfiles(profiles = ["test"])
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RunnerControllerTests : ControllerTestBase() {

  private lateinit var organizationId: String
  private lateinit var workspaceId: String
  private lateinit var solutionId: String
  private lateinit var datasetId: String
  private val logger = LoggerFactory.getLogger(RunnerControllerTests::class.java)

  @MockkSpyBean @Autowired private lateinit var eventPublisher: CsmEventPublisher

  private val solutionParameterId1 = "param1"
  private val solutionParameterDefaultValue1 = "this_is_a_default_value"
  private val solutionParameterVarType1 = "string"
  private val solutionParameterId2 = "param2"
  private val solutionParameterDefaultValue2 = "ignored_value_with_this_varType"

  @BeforeEach
  fun beforeEach() {

    val tags = mutableListOf("tag1", "tag2")
    val description = "this_is_a_description"
    val parameterGroupId = "parameterGroup1"
    val runTemplateName = "this_is_a_name"
    val runTemplateComputeSize = "this_is_a_compute_size"
    val parameterLabels = mutableMapOf("fr" to "this_is_a_label")
    val runTemplateRunSizing =
        RunTemplateResourceSizing(
            com.cosmotech.solution.domain.ResourceSizeInfo("1", "2G"),
            com.cosmotech.solution.domain.ResourceSizeInfo("1", "2G"),
        )

    val parametersList =
        mutableListOf(
            RunTemplateParameterCreateRequest(
                id = solutionParameterId1,
                varType = solutionParameterVarType1,
                defaultValue = solutionParameterDefaultValue1,
            ),
            RunTemplateParameterCreateRequest(
                id = solutionParameterId2,
                varType = DATASET_PART_VARTYPE_FILE,
                defaultValue = solutionParameterDefaultValue2,
            ),
        )

    val parameterGroups =
        mutableListOf(
            RunTemplateParameterGroupCreateRequest(
                id = parameterGroupId,
                parameters = mutableListOf(solutionParameterId1, solutionParameterId2),
            )
        )
    val runTemplates =
        mutableListOf(
            RunTemplateCreateRequest(
                RUNNER_RUN_TEMPLATE,
                runTemplateName,
                parameterLabels,
                description,
                tags,
                runTemplateComputeSize,
                runTemplateRunSizing,
                mutableListOf(parameterGroupId),
                10,
            )
        )

    organizationId = createOrganizationAndReturnId(mvc, constructOrganizationCreateRequest())
    solutionId =
        createSolutionAndReturnId(
            mvc,
            organizationId,
            constructSolutionCreateRequest(
                parameters = parametersList,
                parameterGroups = parameterGroups,
                runTemplates = runTemplates,
            ),
        )
    workspaceId =
        createWorkspaceAndReturnId(
            mvc,
            organizationId,
            constructWorkspaceCreateRequest(solutionId = solutionId),
        )

    datasetId =
        createDatasetAndReturnId(
            mvc,
            organizationId,
            workspaceId,
            constructDatasetCreateRequest(datasetPartName = solutionParameterId2),
        )

    val datasetParts = getDatasetPartsId(mvc, organizationId, workspaceId, datasetId)

    mvc.perform(
            patch("/organizations/$organizationId/workspaces/$workspaceId")
                .withPlatformAdminHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    JSONObject(
                            WorkspaceUpdateRequest(
                                solution =
                                    WorkspaceSolution(
                                        solutionId = solutionId,
                                        datasetId = datasetId,
                                        defaultParameterValues =
                                            mutableMapOf(solutionParameterId2 to datasetParts[0]),
                                    )
                            )
                        )
                        .toString()
                )
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf())
        )
        .andExpect(status().is2xxSuccessful)
  }

  @Test
  fun get_runner_with_wrong_ids_format() {
    mvc.perform(
            get("/organizations/wrong-orgId/workspaces/wrong-workspaceId/runners/wrong-runnerId")
                .withPlatformAdminHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isBadRequest)
        .andExpect(jsonPath("$.detail", containsString("wrong-orgId:must match \"^o-\\w{10,20}\"")))
        .andExpect(
            jsonPath("$.detail", containsString("wrong-workspaceId:must match \"^w-\\w{10,20}\""))
        )
        .andExpect(
            jsonPath("$.detail", containsString("wrong-runnerId:must match \"^(r|s)-\\w{10,20}\""))
        )

    mvc.perform(
            get("/organizations/wrong-orgId/workspaces/w-123456abcdef/runners/r-123456azerty")
                .withPlatformAdminHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isBadRequest)
        .andExpect(jsonPath("$.detail", containsString("wrong-orgId:must match \"^o-\\w{10,20}\"")))

    mvc.perform(
            get("/organizations/wrong-orgId/workspaces/w-123456abcdef/runners/s-123456azerty")
                .withPlatformAdminHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isBadRequest)
        .andExpect(jsonPath("$.detail", containsString("wrong-orgId:must match \"^o-\\w{10,20}\"")))

    mvc.perform(
            get("/organizations/o-1233456azer/workspaces/wrong-workspaceId/runners/r-123456azerty")
                .withPlatformAdminHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isBadRequest)
        .andExpect(
            jsonPath("$.detail", containsString("wrong-workspaceId:must match \"^w-\\w{10,20}\""))
        )

    mvc.perform(
            get("/organizations/o-1233456azer/workspaces/wrong-workspaceId/runners/s-123456azerty")
                .withPlatformAdminHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isBadRequest)
        .andExpect(
            jsonPath("$.detail", containsString("wrong-workspaceId:must match \"^w-\\w{10,20}\""))
        )

    mvc.perform(
            get("/organizations/o-1233456azer/workspaces/w-123456abcdef/runners/wrong-runnerId")
                .withPlatformAdminHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isBadRequest)
        .andExpect(
            jsonPath("$.detail", containsString("wrong-runnerId:must match \"^(r|s)-\\w{10,20}\""))
        )
  }

  @Test
  fun create_runner() {

    val solutionName = "solution_name"
    val runTemplateName = "run_template_name"
    val description = "this_is_a_description"
    val tags = mutableListOf("tags1", "tags2")
    val datasetList = mutableListOf(datasetId)
    val runnerParameterValue = "parameter_value"
    val additionalData =
        mutableMapOf(
            "you_can_put" to "whatever_you_want_here",
            "even" to mapOf("object" to "if_you_want"),
        )

    val parentId =
        createRunnerAndReturnId(
            mvc,
            organizationId,
            workspaceId,
            constructRunnerObject(solutionId = solutionId, runTemplateId = RUNNER_RUN_TEMPLATE),
        )

    val constructRunnerRequest =
        constructRunnerObject(
            name = RUNNER_NAME,
            solutionId = solutionId,
            runTemplateId = RUNNER_RUN_TEMPLATE,
            parentId = parentId,
            solutionName = solutionName,
            runTemplateName = runTemplateName,
            security =
                RunnerSecurity(
                    default = ROLE_NONE,
                    accessControlList =
                        mutableListOf(RunnerAccessControl(id = NEW_USER_ID, role = NEW_USER_ROLE)),
                ),
            runSizing =
                RunnerResourceSizing(
                    requests =
                        ResourceSizeInfo(
                            cpu = "1",
                            memory = "2G",
                        ),
                    limits =
                        ResourceSizeInfo(
                            cpu = "1",
                            memory = "2G",
                        ),
                ),
            additionalData = additionalData,
            description = description,
            tags = tags,
            datasetList = datasetList,
            parametersValues =
                mutableListOf(
                    RunnerRunTemplateParameterValue(
                        parameterId = solutionParameterId1,
                        value = runnerParameterValue,
                        varType = solutionParameterVarType1,
                        isInherited = false,
                    )
                ),
        )

    mvc.perform(
            post("/organizations/$organizationId/workspaces/$workspaceId/runners")
                .withPlatformAdminHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSONObject(constructRunnerRequest).toString())
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf())
        )
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.name").value(RUNNER_NAME))
        .andExpect(jsonPath("$.createInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.updateInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.additionalData").value(additionalData))
        .andExpect(jsonPath("$.description").value(description))
        .andExpect(jsonPath("$.parentId").value(parentId))
        .andExpect(jsonPath("$.solutionName").value(solutionName))
        .andExpect(jsonPath("$.runTemplateName").value(runTemplateName))
        .andExpect(jsonPath("$.tags").value(tags))
        .andExpect(jsonPath("$.datasets.bases").value(datasetList))
        .andExpect(jsonPath("$.datasets.parameters[0].name").value(solutionParameterId2))
        .andExpect(jsonPath("$.datasets.parameters[0].sourceName").value(TEST_FILE_NAME))
        .andExpect(jsonPath("$.datasets.parameters[0].type").value(DatasetPartTypeEnum.File.name))
        .andExpect(jsonPath("$.security.default").value(ROLE_NONE))
        .andExpect(jsonPath("$.runSizing.requests.cpu").value("1"))
        .andExpect(jsonPath("$.runSizing.requests.memory").value("2G"))
        .andExpect(jsonPath("$.runSizing.limits.cpu").value("1"))
        .andExpect(jsonPath("$.runSizing.limits.memory").value("2G"))
        .andExpect(jsonPath("$.parametersValues[0].parameterId").value(solutionParameterId1))
        .andExpect(jsonPath("$.parametersValues[0].value").value(runnerParameterValue))
        .andExpect(jsonPath("$.parametersValues[0].varType").value(solutionParameterVarType1))
        .andExpect(jsonPath("$.parametersValues[0].isInherited").value(false))
        .andExpect(jsonPath("$.security.accessControlList[0].role").value(NEW_USER_ROLE))
        .andExpect(jsonPath("$.security.accessControlList[0].id").value(NEW_USER_ID))
        .andDo(MockMvcResultHandlers.print())
        .andDo(document("organizations/{organization_id}/workspaces/{workspace_id}/runners/POST"))
  }

  @Test
  fun get_runner() {

    val solutionName = "solution_name"
    val runTemplateName = "run_template_name"
    val description = "this_is_a_description"
    val tags = mutableListOf("tags1", "tags2")
    val datasetList = mutableListOf(datasetId)
    val runnerParameterId = "parameterId1"
    val runnerParameterValue = "parameter_value"
    val runnerParameterVarType = "this_is_a_vartype"

    val additionalData =
        mutableMapOf(
            "you_can_put" to "whatever_you_want_here",
            "even" to mapOf("object" to "if_you_want"),
        )

    val parentId =
        createRunnerAndReturnId(
            mvc,
            organizationId,
            workspaceId,
            constructRunnerObject(solutionId = solutionId, runTemplateId = RUNNER_RUN_TEMPLATE),
        )

    val runnerId =
        createRunnerAndReturnId(
            mvc,
            organizationId,
            workspaceId,
            constructRunnerObject(
                name = RUNNER_NAME,
                solutionId = solutionId,
                runTemplateId = RUNNER_RUN_TEMPLATE,
                parentId = parentId,
                solutionName = solutionName,
                runTemplateName = runTemplateName,
                security =
                    RunnerSecurity(
                        default = ROLE_NONE,
                        accessControlList =
                            mutableListOf(
                                RunnerAccessControl(id = NEW_USER_ID, role = NEW_USER_ROLE)
                            ),
                    ),
                runSizing =
                    RunnerResourceSizing(
                        requests =
                            ResourceSizeInfo(
                                cpu = "1",
                                memory = "2G",
                            ),
                        limits =
                            ResourceSizeInfo(
                                cpu = "1",
                                memory = "2G",
                            ),
                    ),
                additionalData = additionalData,
                description = description,
                tags = tags,
                datasetList = datasetList,
                parametersValues =
                    mutableListOf(
                        RunnerRunTemplateParameterValue(
                            parameterId = runnerParameterId,
                            value = runnerParameterValue,
                            varType = runnerParameterVarType,
                            isInherited = false,
                        )
                    ),
            ),
        )

    mvc.perform(
            get("/organizations/$organizationId/workspaces/$workspaceId/runners/$runnerId")
                .withPlatformAdminHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.id").value(runnerId))
        .andExpect(jsonPath("$.name").value(RUNNER_NAME))
        .andExpect(jsonPath("$.createInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.updateInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.additionalData").value(additionalData))
        .andExpect(jsonPath("$.description").value(description))
        .andExpect(jsonPath("$.parentId").value(parentId))
        .andExpect(jsonPath("$.solutionName").value(solutionName))
        .andExpect(jsonPath("$.runTemplateName").value(runTemplateName))
        .andExpect(jsonPath("$.tags").value(tags))
        .andExpect(jsonPath("$.datasets.bases").value(datasetList))
        .andExpect(jsonPath("$.datasets.parameters[0].name").value(solutionParameterId2))
        .andExpect(jsonPath("$.datasets.parameters[0].type").value(DatasetPartTypeEnum.File.name))
        .andExpect(jsonPath("$.datasets.parameters[0].sourceName").value(TEST_FILE_NAME))
        .andExpect(jsonPath("$.security.default").value(ROLE_NONE))
        .andExpect(jsonPath("$.runSizing.requests.cpu").value("1"))
        .andExpect(jsonPath("$.runSizing.requests.memory").value("2G"))
        .andExpect(jsonPath("$.runSizing.limits.cpu").value("1"))
        .andExpect(jsonPath("$.runSizing.limits.memory").value("2G"))
        .andExpect(jsonPath("$.parametersValues[0].parameterId").value(solutionParameterId1))
        .andExpect(jsonPath("$.parametersValues[0].value").value(solutionParameterDefaultValue1))
        .andExpect(jsonPath("$.parametersValues[0].varType").value(solutionParameterVarType1))
        .andExpect(jsonPath("$.parametersValues[0].isInherited").value(true))
        .andExpect(jsonPath("$.security.accessControlList[0].role").value(NEW_USER_ROLE))
        .andExpect(jsonPath("$.security.accessControlList[0].id").value(NEW_USER_ID))
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/GET"
            )
        )
  }

  @Test
  fun update_runner() {

    val solutionName = "solution_name"
    val runTemplateName = "run_template_name"
    val description = "this_is_a_description"
    val tags = mutableListOf("tags1", "tags2")
    val datasetList = mutableListOf(datasetId)
    val runnerParameterValue = "parameter_value"
    val runnerParameterVarType = "this_is_a_vartype"

    val additionalData =
        mutableMapOf(
            "you_can_put_also" to "whatever_you_want_here",
            "even" to mapOf("object" to "if_you_want_too"),
        )

    val baseRunnerId =
        createRunnerAndReturnId(
            mvc,
            organizationId,
            workspaceId,
            constructRunnerObject(solutionId = solutionId, runTemplateId = RUNNER_RUN_TEMPLATE),
        )

    val updateRunnerObject =
        constructUpdateRunnerObject(
            name = RUNNER_NAME,
            runTemplateId = RUNNER_RUN_TEMPLATE,
            solutionName = solutionName,
            runTemplateName = runTemplateName,
            runSizing =
                RunnerResourceSizing(
                    requests =
                        ResourceSizeInfo(
                            cpu = "1",
                            memory = "2G",
                        ),
                    limits =
                        ResourceSizeInfo(
                            cpu = "1",
                            memory = "2G",
                        ),
                ),
            additionalData = additionalData,
            description = description,
            tags = tags,
            datasetList = datasetList,
            parametersValues =
                mutableListOf(
                    RunnerRunTemplateParameterValue(
                        parameterId = solutionParameterId1,
                        value = runnerParameterValue,
                        varType = runnerParameterVarType,
                        isInherited = false,
                    )
                ),
        )

    mvc.perform(
            patch("/organizations/$organizationId/workspaces/$workspaceId/runners/$baseRunnerId")
                .withPlatformAdminHeader()
                .content(JSONObject(updateRunnerObject).toString())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf())
        )
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.id").value(baseRunnerId))
        .andExpect(jsonPath("$.name").value(RUNNER_NAME))
        .andExpect(jsonPath("$.createInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.updateInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.additionalData").value(additionalData))
        .andExpect(jsonPath("$.description").value(description))
        .andExpect(jsonPath("$.parentId").value(null))
        .andExpect(jsonPath("$.solutionName").value(solutionName))
        .andExpect(jsonPath("$.runTemplateName").value(runTemplateName))
        .andExpect(jsonPath("$.tags").value(tags))
        .andExpect(jsonPath("$.datasets.bases").value(datasetList))
        .andExpect(jsonPath("$.datasets.parameters[0].name").value(solutionParameterId2))
        .andExpect(jsonPath("$.datasets.parameters[0].sourceName").value(TEST_FILE_NAME))
        .andExpect(jsonPath("$.datasets.parameters[0].type").value(DatasetPartTypeEnum.File.name))
        .andExpect(jsonPath("$.security.default").value(ROLE_NONE))
        .andExpect(jsonPath("$.runSizing.requests.cpu").value("1"))
        .andExpect(jsonPath("$.runSizing.requests.memory").value("2G"))
        .andExpect(jsonPath("$.runSizing.limits.cpu").value("1"))
        .andExpect(jsonPath("$.runSizing.limits.memory").value("2G"))
        .andExpect(jsonPath("$.parametersValues[0].parameterId").value(solutionParameterId1))
        .andExpect(jsonPath("$.parametersValues[0].value").value(runnerParameterValue))
        .andExpect(jsonPath("$.parametersValues[0].varType").value(solutionParameterVarType1))
        .andExpect(jsonPath("$.parametersValues[0].isInherited").value(false))
        .andExpect(jsonPath("$.security.accessControlList[0].role").value(ROLE_ADMIN))
        .andExpect(jsonPath("$.security.accessControlList[0].id").value(PLATFORM_ADMIN_EMAIL))
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/PATCH"
            )
        )
  }

  @Test
  fun list_runners() {

    val firstRunnerName = "my_first_runner"
    val firstRunnerId =
        createRunnerAndReturnId(
            mvc,
            organizationId,
            workspaceId,
            constructRunnerObject(
                name = firstRunnerName,
                solutionId = solutionId,
                runTemplateId = RUNNER_RUN_TEMPLATE,
            ),
        )

    val secondRunnerName = "my_second_runner"
    val secondRunnerId =
        createRunnerAndReturnId(
            mvc,
            organizationId,
            workspaceId,
            constructRunnerObject(
                name = secondRunnerName,
                solutionId = solutionId,
                runTemplateId = RUNNER_RUN_TEMPLATE,
            ),
        )

    mvc.perform(
            get("/organizations/$organizationId/workspaces/$workspaceId/runners")
                .withPlatformAdminHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$[0].id").value(firstRunnerId))
        .andExpect(jsonPath("$[0].name").value(firstRunnerName))
        .andExpect(jsonPath("$[0].createInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$[0].updateInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$[0].parentId").value(null))
        .andExpect(jsonPath("$[0].security.default").value(ROLE_NONE))
        .andExpect(jsonPath("$[0].security.accessControlList[0].role").value(ROLE_ADMIN))
        .andExpect(jsonPath("$[0].security.accessControlList[0].id").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$[0].datasets.parameters[0].name").value(solutionParameterId2))
        .andExpect(jsonPath("$[0].datasets.parameters[0].sourceName").value(TEST_FILE_NAME))
        .andExpect(
            jsonPath("$[0].datasets.parameters[0].type").value(DatasetPartTypeEnum.File.name)
        )
        .andExpect(jsonPath("$[1].id").value(secondRunnerId))
        .andExpect(jsonPath("$[1].name").value(secondRunnerName))
        .andExpect(jsonPath("$[1].createInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$[1].updateInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$[1].parentId").value(null))
        .andExpect(jsonPath("$[1].security.default").value(ROLE_NONE))
        .andExpect(jsonPath("$[1].security.accessControlList[0].role").value(ROLE_ADMIN))
        .andExpect(jsonPath("$[1].security.accessControlList[0].id").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$[1].datasets.parameters[0].name").value(solutionParameterId2))
        .andExpect(jsonPath("$[1].datasets.parameters[0].sourceName").value(TEST_FILE_NAME))
        .andExpect(
            jsonPath("$[1].datasets.parameters[0].type").value(DatasetPartTypeEnum.File.name)
        )
        .andDo(MockMvcResultHandlers.print())
        .andDo(document("organizations/{organization_id}/workspaces/{workspace_id}/runners/GET"))
  }

  @Test
  fun delete_runner() {

    val runnerId =
        createRunnerAndReturnId(
            mvc,
            organizationId,
            workspaceId,
            constructRunnerObject(solutionId = solutionId, runTemplateId = RUNNER_RUN_TEMPLATE),
        )

    mvc.perform(
            delete("/organizations/$organizationId/workspaces/$workspaceId/runners/$runnerId")
                .withPlatformAdminHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf())
        )
        .andExpect(status().is2xxSuccessful)
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/DELETE"
            )
        )
  }

  @Test
  fun get_runner_security() {

    val runnerId =
        createRunnerAndReturnId(
            mvc,
            organizationId,
            workspaceId,
            constructRunnerObject(solutionId = solutionId, runTemplateId = RUNNER_RUN_TEMPLATE),
        )

    mvc.perform(
            get("/organizations/$organizationId/workspaces/$workspaceId/runners/$runnerId/security")
                .withPlatformAdminHeader()
        )
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.default").value(ROLE_NONE))
        .andExpect(jsonPath("$.accessControlList[0].role").value(ROLE_ADMIN))
        .andExpect(jsonPath("$.accessControlList[0].id").value(PLATFORM_ADMIN_EMAIL))
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security/GET"
            )
        )
  }

  @Test
  fun add_runner_security_access() {

    val runnerId =
        createRunnerAndReturnId(
            mvc,
            organizationId,
            workspaceId,
            constructRunnerObject(solutionId = solutionId, runTemplateId = RUNNER_RUN_TEMPLATE),
        )

    mvc.perform(
            post(
                    "/organizations/$organizationId/workspaces/$workspaceId/runners/$runnerId/security/access"
                )
                .withPlatformAdminHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                        "id": "${OrganizationConstants.NEW_USER_ID}",
                        "role": "${OrganizationConstants.NEW_USER_ROLE}"
                        }
                    """
                        .trimMargin()
                )
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf())
        )
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.role").value(OrganizationConstants.NEW_USER_ROLE))
        .andExpect(jsonPath("$.id").value(OrganizationConstants.NEW_USER_ID))
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security/access/POST"
            )
        )
  }

  @Test
  fun get_runner_security_access() {

    val runnerId =
        createRunnerAndReturnId(
            mvc,
            organizationId,
            workspaceId,
            constructRunnerObject(solutionId = solutionId, runTemplateId = RUNNER_RUN_TEMPLATE),
        )

    mvc.perform(
            get(
                    "/organizations/$organizationId/workspaces/$workspaceId/runners/$runnerId/security/access/$PLATFORM_ADMIN_EMAIL"
                )
                .withPlatformAdminHeader()
        )
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.role").value(ROLE_ADMIN))
        .andExpect(jsonPath("$.id").value(PLATFORM_ADMIN_EMAIL))
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security/access/{identity_id}/GET"
            )
        )
  }

  @Test
  fun update_runner_security_access() {

    val runnerSecurity =
        RunnerSecurity(
            default = ROLE_NONE,
            accessControlList =
                mutableListOf(
                    RunnerAccessControl(id = PLATFORM_ADMIN_EMAIL, role = ROLE_ADMIN),
                    RunnerAccessControl(
                        id = OrganizationConstants.NEW_USER_ID,
                        role = OrganizationConstants.NEW_USER_ROLE,
                    ),
                ),
        )
    val runnerId =
        createRunnerAndReturnId(
            mvc,
            organizationId,
            workspaceId,
            constructRunnerObject(
                solutionId = solutionId,
                runTemplateId = RUNNER_RUN_TEMPLATE,
                security = runnerSecurity,
            ),
        )

    mvc.perform(
            patch(
                    "/organizations/$organizationId/workspaces/$workspaceId/runners/$runnerId/security/access/${OrganizationConstants.NEW_USER_ID}"
                )
                .withPlatformAdminHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"role":"$ROLE_VIEWER"}""")
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf())
        )
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.role").value(ROLE_VIEWER))
        .andExpect(jsonPath("$.id").value(OrganizationConstants.NEW_USER_ID))
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security/access/{identity_id}/PATCH"
            )
        )
  }

  @Test
  fun delete_runner_security_access() {

    val runnerSecurity =
        RunnerSecurity(
            default = ROLE_NONE,
            accessControlList =
                mutableListOf(
                    RunnerAccessControl(id = PLATFORM_ADMIN_EMAIL, role = ROLE_ADMIN),
                    RunnerAccessControl(
                        id = OrganizationConstants.NEW_USER_ID,
                        role = OrganizationConstants.NEW_USER_ROLE,
                    ),
                ),
        )
    val runnerId =
        createRunnerAndReturnId(
            mvc,
            organizationId,
            workspaceId,
            constructRunnerObject(
                solutionId = solutionId,
                runTemplateId = RUNNER_RUN_TEMPLATE,
                security = runnerSecurity,
            ),
        )

    mvc.perform(
            delete(
                    "/organizations/$organizationId/workspaces/$workspaceId/runners/$runnerId/security/access/${OrganizationConstants.NEW_USER_ID}"
                )
                .withPlatformAdminHeader()
                .with(csrf())
        )
        .andExpect(status().is2xxSuccessful)
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security/access/{identity_id}/DELETE"
            )
        )
  }

  @Test
  fun update_runner_security_default() {

    val runnerId =
        createRunnerAndReturnId(
            mvc,
            organizationId,
            workspaceId,
            constructRunnerObject(solutionId = solutionId, runTemplateId = RUNNER_RUN_TEMPLATE),
        )

    mvc.perform(
            patch(
                    "/organizations/$organizationId/workspaces/$workspaceId/runners/$runnerId/security/default"
                )
                .withPlatformAdminHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"role":"$ROLE_VIEWER"}""")
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf())
        )
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.default").value(ROLE_VIEWER))
        .andExpect(jsonPath("$.accessControlList[0].id").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.accessControlList[0].role").value(ROLE_ADMIN))
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security/default/POST"
            )
        )
  }

  @Test
  fun list_runner_users() {

    val runnerSecurity =
        RunnerSecurity(
            default = ROLE_NONE,
            accessControlList =
                mutableListOf(
                    RunnerAccessControl(id = PLATFORM_ADMIN_EMAIL, role = ROLE_ADMIN),
                    RunnerAccessControl(
                        id = OrganizationConstants.NEW_USER_ID,
                        role = OrganizationConstants.NEW_USER_ROLE,
                    ),
                ),
        )
    val runnerId =
        createRunnerAndReturnId(
            mvc,
            organizationId,
            workspaceId,
            constructRunnerObject(
                solutionId = solutionId,
                runTemplateId = RUNNER_RUN_TEMPLATE,
                security = runnerSecurity,
            ),
        )

    mvc.perform(
            get(
                    "/organizations/$organizationId/workspaces/$workspaceId/runners/$runnerId/security/users"
                )
                .withPlatformAdminHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$[0]").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$[1]").value(OrganizationConstants.NEW_USER_ID))
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security/users/GET"
            )
        )
  }

  @Test
  fun start_runner() {
    val expectedRunId = "run-genid12345"
    every { eventPublisher.publishEvent(any<RunStart>()) } answers
        {
          firstArg<RunStart>().response = expectedRunId
        }

    val runnerId =
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
                    ),
            ),
        )

    mvc.perform(
            post("/organizations/$organizationId/workspaces/$workspaceId/runners/$runnerId/start")
                .withPlatformAdminHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf())
        )
        .andExpect(status().is2xxSuccessful)
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/start/POST"
            )
        )
  }

  @Test
  fun stop_runner() {
    val expectedRunId = "run-genid12345"

    val runnerId =
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
                    ),
            ),
        )
    every { eventPublisher.publishEvent(any()) } answers
        {
          firstArg<RunStart>().response = expectedRunId
        } andThenAnswer
        {
          firstArg<UpdateRunnerStatus>().response = "Running"
        } andThenAnswer
        {}

    mvc.perform(
            post("/organizations/$organizationId/workspaces/$workspaceId/runners/$runnerId/start")
                .withPlatformAdminHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf())
        )
        .andExpect(status().is2xxSuccessful)

    mvc.perform(
            post("/organizations/$organizationId/workspaces/$workspaceId/runners/$runnerId/stop")
                .withPlatformAdminHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf())
        )
        .andExpect(status().is2xxSuccessful)
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/stop/POST"
            )
        )
  }
}
