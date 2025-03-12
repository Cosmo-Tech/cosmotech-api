// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.home.runner

import com.cosmotech.api.home.Constants.PLATFORM_ADMIN_EMAIL
import com.cosmotech.api.home.ControllerTestBase
import com.cosmotech.api.home.ControllerTestUtils.OrganizationUtils.constructOrganizationCreateRequest
import com.cosmotech.api.home.ControllerTestUtils.OrganizationUtils.createOrganizationAndReturnId
import com.cosmotech.api.home.ControllerTestUtils.RunnerUtils.constructRunnerObject
import com.cosmotech.api.home.ControllerTestUtils.RunnerUtils.constructUpdateRunnerObject
import com.cosmotech.api.home.ControllerTestUtils.RunnerUtils.createRunnerAndReturnId
import com.cosmotech.api.home.ControllerTestUtils.SolutionUtils.constructSolutionCreateRequest
import com.cosmotech.api.home.ControllerTestUtils.SolutionUtils.createSolutionAndReturnId
import com.cosmotech.api.home.ControllerTestUtils.WorkspaceUtils.constructWorkspaceCreateRequest
import com.cosmotech.api.home.ControllerTestUtils.WorkspaceUtils.createWorkspaceAndReturnId
import com.cosmotech.api.home.annotations.WithMockOauth2User
import com.cosmotech.api.home.organization.OrganizationConstants
import com.cosmotech.api.home.runner.RunnerConstants.NEW_USER_ID
import com.cosmotech.api.home.runner.RunnerConstants.NEW_USER_ROLE
import com.cosmotech.api.home.runner.RunnerConstants.RUNNER_NAME
import com.cosmotech.api.home.runner.RunnerConstants.RUNNER_OWNER_NAME
import com.cosmotech.api.home.runner.RunnerConstants.RUNNER_RUN_TEMPLATE
import com.cosmotech.api.rbac.ROLE_ADMIN
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.rbac.ROLE_VIEWER
import com.cosmotech.runner.domain.*
import com.cosmotech.runner.domain.ResourceSizeInfo
import com.cosmotech.solution.domain.RunTemplate
import com.cosmotech.solution.domain.RunTemplateResourceSizing
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
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
class RunnerControllerTests: ControllerTestBase() {

    private lateinit var organizationId: String
    private lateinit var workspaceId: String
    private lateinit var solutionId: String
    private val logger = LoggerFactory.getLogger(RunnerControllerTests::class.java)


    @BeforeEach
    fun beforeEach() {

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
    }

    @Test
    @WithMockOauth2User
    fun create_runner() {

        val solutionName = "solution_name"
        val runTemplateName = "run_template_name"
        val description = "this_is_a_description"
        val tags = mutableListOf("tags1", "tags2")
        val datasetList = mutableListOf("datasetId1", "datasetId2")
        val runnerParameterId = "parameterId1"
        val runnerParameterValue = "parameter_value"
        val runnerParameterVarType = "this_is_a_vartype"

        val parentId = createRunnerAndReturnId(
            mvc,
            organizationId,
            workspaceId,
            constructRunnerObject(
                solutionId = solutionId,
                runTemplateId = RUNNER_RUN_TEMPLATE
            )
        )

        val constructRunnerRequest = constructRunnerObject(
            name = RUNNER_NAME,
            solutionId = solutionId,
            runTemplateId = RUNNER_RUN_TEMPLATE,
            parentId = parentId,
            solutionName = solutionName,
            runTemplateName = runTemplateName,
            security = RunnerSecurity(
                default = ROLE_NONE,
                accessControlList =
                    mutableListOf(
                        RunnerAccessControl(
                            id = NEW_USER_ID,
                            role = NEW_USER_ROLE
                        )
                    )
            ),
            runSizing = RunnerResourceSizing(
                requests =
                    ResourceSizeInfo(
                        cpu = "cpu_requests",
                        memory = "memory_requests",
                    ),
                limits =
                    ResourceSizeInfo(
                        cpu = "cpu_limits",
                        memory = "memory_limits",
                    )
            ),
            ownerName = RUNNER_OWNER_NAME,
            description = description,
            tags = tags,
            datasetList = datasetList,
            parametersValues = mutableListOf(
                RunnerRunTemplateParameterValue(
                    parameterId = runnerParameterId,
                    value = runnerParameterValue,
                    varType = runnerParameterVarType,
                    isInherited = false

                )
            )
        )

        mvc
            .perform(
                post("/organizations/$organizationId/workspaces/$workspaceId/runners")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        JSONObject(
                            constructRunnerRequest
                        ).toString()
                    )
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf())
            ).andExpect(status().is2xxSuccessful)
            .andExpect(jsonPath("$.name").value(RUNNER_NAME))
            .andExpect(jsonPath("$.ownerId").value(PLATFORM_ADMIN_EMAIL))
            .andExpect(jsonPath("$.ownerName").value(RUNNER_OWNER_NAME))
            .andExpect(jsonPath("$.description").value(description))
            .andExpect(jsonPath("$.parentId").value(parentId))
            .andExpect(jsonPath("$.solutionName").value(solutionName))
            .andExpect(jsonPath("$.runTemplateName").value(runTemplateName))
            .andExpect(jsonPath("$.tags").value(tags))
            .andExpect(jsonPath("$.datasetList").value(datasetList))
            .andExpect(jsonPath("$.security.default").value(ROLE_NONE))
            .andExpect(jsonPath("$.runSizing.requests.cpu").value("cpu_requests"))
            .andExpect(jsonPath("$.runSizing.requests.memory").value("memory_requests"))
            .andExpect(jsonPath("$.runSizing.limits.cpu").value("cpu_limits"))
            .andExpect(jsonPath("$.runSizing.limits.memory").value("memory_limits"))
            .andExpect(jsonPath("$.parametersValues[0].parameterId").value(runnerParameterId))
            .andExpect(jsonPath("$.parametersValues[0].value").value(runnerParameterValue))
            .andExpect(jsonPath("$.parametersValues[0].varType").value(runnerParameterVarType))
            .andExpect(jsonPath("$.parametersValues[0].isInherited").value(false))
            .andExpect(jsonPath("$.security.accessControlList[0].role").value(NEW_USER_ROLE))
            .andExpect(jsonPath("$.security.accessControlList[0].id").value(NEW_USER_ID))
            .andDo(MockMvcResultHandlers.print())
            .andDo(document("organizations/{organization_id}/workspaces/{workspace_id}/runners/POST"))
    }


    @Test
    @WithMockOauth2User
    fun get_runner() {

        val solutionName = "solution_name"
        val runTemplateName = "run_template_name"
        val description = "this_is_a_description"
        val tags = mutableListOf("tags1", "tags2")
        val datasetList = mutableListOf("datasetId1", "datasetId2")
        val runnerParameterId = "parameterId1"
        val runnerParameterValue = "parameter_value"
        val runnerParameterVarType = "this_is_a_vartype"

        val parentId = createRunnerAndReturnId(
            mvc,
            organizationId,
            workspaceId,
            constructRunnerObject(
                solutionId = solutionId,
                runTemplateId = RUNNER_RUN_TEMPLATE
            )
        )

        val runnerId = createRunnerAndReturnId(
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
                security = RunnerSecurity(
                    default = ROLE_NONE,
                    accessControlList =
                        mutableListOf(
                            RunnerAccessControl(
                                id = NEW_USER_ID,
                                role = NEW_USER_ROLE
                            )
                        )
                ),
                runSizing = RunnerResourceSizing(
                    requests =
                        ResourceSizeInfo(
                            cpu = "cpu_requests",
                            memory = "memory_requests",
                        ),
                    limits =
                        ResourceSizeInfo(
                            cpu = "cpu_limits",
                            memory = "memory_limits",
                        )
                ),
                ownerName = RUNNER_OWNER_NAME,
                description = description,
                tags = tags,
                datasetList = datasetList,
                parametersValues = mutableListOf(
                    RunnerRunTemplateParameterValue(
                        parameterId = runnerParameterId,
                        value = runnerParameterValue,
                        varType = runnerParameterVarType,
                        isInherited = false

                    )
                )
            )
        )

        mvc
            .perform(
                get("/organizations/$organizationId/workspaces/$workspaceId/runners/$runnerId")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
            ).andExpect(status().is2xxSuccessful)
            .andExpect(jsonPath("$.id").value(runnerId))
            .andExpect(jsonPath("$.name").value(RUNNER_NAME))
            .andExpect(jsonPath("$.ownerId").value(PLATFORM_ADMIN_EMAIL))
            .andExpect(jsonPath("$.ownerName").value(RUNNER_OWNER_NAME))
            .andExpect(jsonPath("$.description").value(description))
            .andExpect(jsonPath("$.parentId").value(parentId))
            .andExpect(jsonPath("$.solutionName").value(solutionName))
            .andExpect(jsonPath("$.runTemplateName").value(runTemplateName))
            .andExpect(jsonPath("$.tags").value(tags))
            .andExpect(jsonPath("$.datasetList").value(datasetList))
            .andExpect(jsonPath("$.security.default").value(ROLE_NONE))
            .andExpect(jsonPath("$.runSizing.requests.cpu").value("cpu_requests"))
            .andExpect(jsonPath("$.runSizing.requests.memory").value("memory_requests"))
            .andExpect(jsonPath("$.runSizing.limits.cpu").value("cpu_limits"))
            .andExpect(jsonPath("$.runSizing.limits.memory").value("memory_limits"))
            .andExpect(jsonPath("$.parametersValues[0].parameterId").value(runnerParameterId))
            .andExpect(jsonPath("$.parametersValues[0].value").value(runnerParameterValue))
            .andExpect(jsonPath("$.parametersValues[0].varType").value(runnerParameterVarType))
            .andExpect(jsonPath("$.parametersValues[0].isInherited").value(false))
            .andExpect(jsonPath("$.security.accessControlList[0].role").value(NEW_USER_ROLE))
            .andExpect(jsonPath("$.security.accessControlList[0].id").value(NEW_USER_ID))
            .andDo(MockMvcResultHandlers.print())
            .andDo(document("organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/GET"))

    }

    @Test
    @WithMockOauth2User
    fun update_runner() {

        val solutionName = "solution_name"
        val runTemplateName = "run_template_name"
        val description = "this_is_a_description"
        val tags = mutableListOf("tags1", "tags2")
        val datasetList = mutableListOf("datasetId1", "datasetId2")
        val runnerParameterId = "parameterId1"
        val runnerParameterValue = "parameter_value"
        val runnerParameterVarType = "this_is_a_vartype"

        val baseRunnerId = createRunnerAndReturnId(
            mvc,
            organizationId,
            workspaceId,
            constructRunnerObject(
                solutionId = solutionId,
                runTemplateId = RUNNER_RUN_TEMPLATE
            )
        )

        val updateRunnerObject = constructUpdateRunnerObject(
            name = RUNNER_NAME,
            runTemplateId = RUNNER_RUN_TEMPLATE,
            solutionName = solutionName,
            runTemplateName = runTemplateName,
            runSizing = RunnerResourceSizing(
                requests =
                    ResourceSizeInfo(
                        cpu = "cpu_requests",
                        memory = "memory_requests",
                    ),
                limits =
                    ResourceSizeInfo(
                        cpu = "cpu_limits",
                        memory = "memory_limits",
                    )
            ),
            ownerName = RUNNER_OWNER_NAME,
            description = description,
            tags = tags,
            datasetList = datasetList,
            parametersValues = mutableListOf(
                RunnerRunTemplateParameterValue(
                    parameterId = runnerParameterId,
                    value = runnerParameterValue,
                    varType = runnerParameterVarType,
                    isInherited = false

                )
            )
        )



        mvc
            .perform(
                patch("/organizations/$organizationId/workspaces/$workspaceId/runners/$baseRunnerId")
                    .content(
                        JSONObject(updateRunnerObject).toString()
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf())
            ).andExpect(status().is2xxSuccessful)
            .andExpect(jsonPath("$.id").value(baseRunnerId))
            .andExpect(jsonPath("$.name").value(RUNNER_NAME))
            .andExpect(jsonPath("$.ownerId").value(PLATFORM_ADMIN_EMAIL))
            .andExpect(jsonPath("$.ownerName").value(RUNNER_OWNER_NAME))
            .andExpect(jsonPath("$.description").value(description))
            .andExpect(jsonPath("$.parentId").value(null))
            .andExpect(jsonPath("$.solutionName").value(solutionName))
            .andExpect(jsonPath("$.runTemplateName").value(runTemplateName))
            .andExpect(jsonPath("$.tags").value(tags))
            .andExpect(jsonPath("$.datasetList").value(datasetList))
            .andExpect(jsonPath("$.security.default").value(ROLE_NONE))
            .andExpect(jsonPath("$.runSizing.requests.cpu").value("cpu_requests"))
            .andExpect(jsonPath("$.runSizing.requests.memory").value("memory_requests"))
            .andExpect(jsonPath("$.runSizing.limits.cpu").value("cpu_limits"))
            .andExpect(jsonPath("$.runSizing.limits.memory").value("memory_limits"))
            .andExpect(jsonPath("$.parametersValues[0].parameterId").value(runnerParameterId))
            .andExpect(jsonPath("$.parametersValues[0].value").value(runnerParameterValue))
            .andExpect(jsonPath("$.parametersValues[0].varType").value(runnerParameterVarType))
            .andExpect(jsonPath("$.parametersValues[0].isInherited").value(false))
            .andExpect(jsonPath("$.security.accessControlList[0].role").value(ROLE_ADMIN))
            .andExpect(jsonPath("$.security.accessControlList[0].id").value(PLATFORM_ADMIN_EMAIL))
            .andDo(MockMvcResultHandlers.print())
            .andDo(document("organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/PATCH"))
    }


    @Test
    @WithMockOauth2User
    fun list_runners() {

        val firstRunnerName = "my_first_runner"
        val firstOwnerName = "firstRunnerOwner"
        val firstRunnerId = createRunnerAndReturnId(
            mvc,
            organizationId,
            workspaceId,
            constructRunnerObject(
                name = firstRunnerName,
                solutionId = solutionId,
                runTemplateId = RUNNER_RUN_TEMPLATE,
                ownerName = firstOwnerName
            )
        )

        val secondRunnerName = "my_second_runner"
        val secondOwnerName = "secondRunnerOwner"
        val secondRunnerId = createRunnerAndReturnId(
            mvc,
            organizationId,
            workspaceId,
            constructRunnerObject(
                name = secondRunnerName,
                solutionId = solutionId,
                runTemplateId = RUNNER_RUN_TEMPLATE,
                ownerName = secondOwnerName
            )
        )


        mvc
            .perform(
                get("/organizations/$organizationId/workspaces/$workspaceId/runners")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
            ).andExpect(status().is2xxSuccessful)
            .andExpect(jsonPath("$[0].id").value(firstRunnerId))
            .andExpect(jsonPath("$[0].name").value(firstRunnerName))
            .andExpect(jsonPath("$[0].ownerId").value(PLATFORM_ADMIN_EMAIL))
            .andExpect(jsonPath("$[0].ownerName").value(firstOwnerName))
            .andExpect(jsonPath("$[0].parentId").value(null))
            .andExpect(jsonPath("$[0].security.default").value(ROLE_NONE))
            .andExpect(jsonPath("$[0].security.accessControlList[0].role").value(ROLE_ADMIN))
            .andExpect(jsonPath("$[0].security.accessControlList[0].id").value(PLATFORM_ADMIN_EMAIL))
            .andExpect(jsonPath("$[1].id").value(secondRunnerId))
            .andExpect(jsonPath("$[1].name").value(secondRunnerName))
            .andExpect(jsonPath("$[1].ownerId").value(PLATFORM_ADMIN_EMAIL))
            .andExpect(jsonPath("$[1].ownerName").value(secondOwnerName))
            .andExpect(jsonPath("$[1].parentId").value(null))
            .andExpect(jsonPath("$[1].security.default").value(ROLE_NONE))
            .andExpect(jsonPath("$[1].security.accessControlList[0].role").value(ROLE_ADMIN))
            .andExpect(jsonPath("$[1].security.accessControlList[0].id").value(PLATFORM_ADMIN_EMAIL))
            .andDo(MockMvcResultHandlers.print())
            .andDo(document("organizations/{organization_id}/workspaces/{workspace_id}/runners/GET"))

    }

    @Test
    @WithMockOauth2User
    fun delete_runner() {

        val runnerId = createRunnerAndReturnId(
            mvc,
            organizationId,
            workspaceId,
            constructRunnerObject(
                solutionId = solutionId,
                runTemplateId = RUNNER_RUN_TEMPLATE
            )
        )

        mvc
            .perform(
                delete("/organizations/$organizationId/workspaces/$workspaceId/runners/$runnerId")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf())
            ).andExpect(status().is2xxSuccessful)
            .andDo(MockMvcResultHandlers.print())
            .andDo(document("organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/DELETE"))

    }


    @Test
    @WithMockOauth2User
    fun get_runner_security() {

        val runnerId = createRunnerAndReturnId(mvc,
            organizationId,
            workspaceId,
            constructRunnerObject(
                solutionId = solutionId,
                runTemplateId = RUNNER_RUN_TEMPLATE
            )
        )

        mvc
            .perform(
                get("/organizations/$organizationId/workspaces/$workspaceId/runners/$runnerId/security")
            )
            .andExpect(status().is2xxSuccessful)
            .andExpect(jsonPath("$.default").value(ROLE_NONE))
            .andExpect(jsonPath("$.accessControlList[0].role").value(ROLE_ADMIN))
            .andExpect(jsonPath("$.accessControlList[0].id").value(PLATFORM_ADMIN_EMAIL))
            .andDo(MockMvcResultHandlers.print())
            .andDo(document("organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security/GET"))
    }

    @Test
    @WithMockOauth2User
    fun add_runner_security_access() {

        val runnerId = createRunnerAndReturnId(mvc,
            organizationId,
            workspaceId,
            constructRunnerObject(
                solutionId = solutionId,
                runTemplateId = RUNNER_RUN_TEMPLATE
            )
        )


        mvc
            .perform(
                post("/organizations/$organizationId/workspaces/$workspaceId/runners/$runnerId/security/access")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                        "id": "${OrganizationConstants.NEW_USER_ID}",
                        "role": "${OrganizationConstants.NEW_USER_ROLE}"
                        }
                    """.trimMargin())
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf())
            )
            .andExpect(status().is2xxSuccessful)
            .andExpect(jsonPath("$.role").value(OrganizationConstants.NEW_USER_ROLE))
            .andExpect(jsonPath("$.id").value(OrganizationConstants.NEW_USER_ID))
            .andDo(MockMvcResultHandlers.print())
            .andDo(document("organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security/access/POST"))
    }

    @Test
    @WithMockOauth2User
    fun get_runner_security_access() {

        val runnerId = createRunnerAndReturnId(mvc,
            organizationId,
            workspaceId,
            constructRunnerObject(
                solutionId = solutionId,
                runTemplateId = RUNNER_RUN_TEMPLATE
            )
        )

        mvc
            .perform(
                get("/organizations/$organizationId/workspaces/$workspaceId/runners/$runnerId/security/access/$PLATFORM_ADMIN_EMAIL")
            )
            .andExpect(status().is2xxSuccessful)
            .andExpect(jsonPath("$.role").value(ROLE_ADMIN))
            .andExpect(jsonPath("$.id").value(PLATFORM_ADMIN_EMAIL))
            .andDo(MockMvcResultHandlers.print())
            .andDo(document("organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security/access/{identity_id}/GET"))
    }

    @Test
    @WithMockOauth2User
    fun update_runner_security_access() {

        val runnerSecurity = RunnerSecurity(default = ROLE_NONE,
            accessControlList = mutableListOf(
                RunnerAccessControl(id = PLATFORM_ADMIN_EMAIL, role = ROLE_ADMIN),
                RunnerAccessControl(id = OrganizationConstants.NEW_USER_ID, role = OrganizationConstants.NEW_USER_ROLE)
            ))
        val runnerId = createRunnerAndReturnId(mvc,
            organizationId,
            workspaceId,
            constructRunnerObject(
                solutionId = solutionId,
                runTemplateId = RUNNER_RUN_TEMPLATE,
                security = runnerSecurity
            )
        )

        mvc
            .perform(
                patch("/organizations/$organizationId/workspaces/$workspaceId/runners/$runnerId/security/access/${OrganizationConstants.NEW_USER_ID}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"role":"$ROLE_VIEWER"}""")
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf())
            )
            .andExpect(status().is2xxSuccessful)
            .andExpect(jsonPath("$.role").value(ROLE_VIEWER))
            .andExpect(jsonPath("$.id").value(OrganizationConstants.NEW_USER_ID))
            .andDo(MockMvcResultHandlers.print())
            .andDo(document("organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security/access/{identity_id}/PATCH"))
    }



    @Test
    @WithMockOauth2User
    fun delete_runner_security_access() {

        val runnerSecurity = RunnerSecurity(default = ROLE_NONE,
            accessControlList = mutableListOf(
                RunnerAccessControl(id = PLATFORM_ADMIN_EMAIL, role = ROLE_ADMIN),
                RunnerAccessControl(id = OrganizationConstants.NEW_USER_ID, role = OrganizationConstants.NEW_USER_ROLE)
            ))
        val runnerId = createRunnerAndReturnId(mvc,
            organizationId,
            workspaceId,
            constructRunnerObject(
                solutionId = solutionId,
                runTemplateId = RUNNER_RUN_TEMPLATE,
                security = runnerSecurity
            )
        )


        mvc
            .perform(
                delete("/organizations/$organizationId/workspaces/$workspaceId/runners/$runnerId/security/access/${OrganizationConstants.NEW_USER_ID}")
                    .with(csrf())
            )
            .andExpect(status().is2xxSuccessful)
            .andDo(MockMvcResultHandlers.print())
            .andDo(document("organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security/access/{identity_id}/DELETE"))
    }

    @Test
    @WithMockOauth2User
    fun update_runner_security_default() {

        val runnerId = createRunnerAndReturnId(mvc,
            organizationId,
            workspaceId,
            constructRunnerObject(
                solutionId = solutionId,
                runTemplateId = RUNNER_RUN_TEMPLATE
            )
        )

        mvc
            .perform(
                patch("/organizations/$organizationId/workspaces/$workspaceId/runners/$runnerId/security/default")
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
            .andDo(document("organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security/default/POST"))
    }

    @Test
    @WithMockOauth2User
    fun list_runner_users() {

        val runnerSecurity = RunnerSecurity(default = ROLE_NONE,
            accessControlList = mutableListOf(
                RunnerAccessControl(id = PLATFORM_ADMIN_EMAIL, role = ROLE_ADMIN),
                RunnerAccessControl(id = OrganizationConstants.NEW_USER_ID, role = OrganizationConstants.NEW_USER_ROLE)
            ))
        val runnerId = createRunnerAndReturnId(mvc,
            organizationId,
            workspaceId,
            constructRunnerObject(
                solutionId = solutionId,
                runTemplateId = RUNNER_RUN_TEMPLATE,
                security = runnerSecurity
            )
        )

        mvc
            .perform(
                get("/organizations/$organizationId/workspaces/$workspaceId/runners/$runnerId/security/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().is2xxSuccessful)
            .andExpect(jsonPath("$[0]").value(PLATFORM_ADMIN_EMAIL))
            .andExpect(jsonPath("$[1]").value(OrganizationConstants.NEW_USER_ID))
            .andDo(MockMvcResultHandlers.print())
            .andDo(document("organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/security/users/GET"))
    }


/*
    @Test
    @WithMockOauth2User
    fun start_runner() {

        val runnerId = createRunnerAndReturnId(
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

        mvc
            .perform(
                post("/organizations/$organizationId/workspaces/$workspaceId/runners/$runnerId/start")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf())
            )
            .andExpect(status().is2xxSuccessful)
            .andDo(MockMvcResultHandlers.print())
            .andDo(document("organizations/{organization_id}/workspaces/{workspace_id}/runners/{runner_id}/start/POST"))
    }

*/

}