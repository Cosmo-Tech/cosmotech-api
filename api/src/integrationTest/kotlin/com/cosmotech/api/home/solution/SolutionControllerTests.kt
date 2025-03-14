// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.home.solution

import com.cosmotech.api.containerregistry.ContainerRegistryService
import com.cosmotech.api.home.Constants.PLATFORM_ADMIN_EMAIL
import com.cosmotech.api.home.ControllerTestBase
import com.cosmotech.api.home.ControllerTestUtils.OrganizationUtils.constructOrganizationCreateRequest
import com.cosmotech.api.home.ControllerTestUtils.OrganizationUtils.createOrganizationAndReturnId
import com.cosmotech.api.home.ControllerTestUtils.SolutionUtils.constructSolutionCreateRequest
import com.cosmotech.api.home.ControllerTestUtils.SolutionUtils.constructSolutionUpdateRequest
import com.cosmotech.api.home.ControllerTestUtils.SolutionUtils.createSolutionAndReturnId
import com.cosmotech.api.home.annotations.WithMockOauth2User
import com.cosmotech.api.home.organization.OrganizationConstants.NEW_USER_ID
import com.cosmotech.api.home.organization.OrganizationConstants.NEW_USER_ROLE
import com.cosmotech.api.home.solution.SolutionConstants.SOLUTION_KEY
import com.cosmotech.api.home.solution.SolutionConstants.SOLUTION_NAME
import com.cosmotech.api.home.solution.SolutionConstants.SOLUTION_REPOSITORY
import com.cosmotech.api.home.solution.SolutionConstants.SOLUTION_SDK_VERSION
import com.cosmotech.api.home.solution.SolutionConstants.SOLUTION_SIMULATOR
import com.cosmotech.api.home.solution.SolutionConstants.SOLUTION_VERSION
import com.cosmotech.api.rbac.ROLE_ADMIN
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.rbac.ROLE_VIEWER
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.solution.domain.*
import io.mockk.every
import io.mockk.mockk
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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

@ActiveProfiles(profiles = ["test"])
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SolutionControllerTests : ControllerTestBase() {

  @Autowired lateinit var solutionApiService: SolutionApiService
  private lateinit var organizationId: String

  private var containerRegistryService: ContainerRegistryService = mockk(relaxed = true)

  @BeforeEach
  fun beforeEach() {
    ReflectionTestUtils.setField(
        solutionApiService, "containerRegistryService", containerRegistryService)
    every { containerRegistryService.getImageLabel(any(), any(), any()) } returns
        SOLUTION_SDK_VERSION

    organizationId = createOrganizationAndReturnId(mvc, constructOrganizationCreateRequest())
  }

  @Test
  @WithMockOauth2User
  fun list_solutions() {
    val firstSolutionName = "firstSolutionName"
    val firstSolutionKey = "firstSolutionKey"
    val firstSolutionId =
        createSolutionAndReturnId(
            mvc,
            organizationId,
            constructSolutionCreateRequest(
                name = firstSolutionName,
                key = firstSolutionKey,
            ))
    val secondSolutionName = "secondSolutionName"
    val secondSolutionKey = "secondSolutionKey"
    val secondSolutionId =
        createSolutionAndReturnId(
            mvc,
            organizationId,
            constructSolutionCreateRequest(
                name = secondSolutionName,
                key = secondSolutionKey,
            ))

    mvc.perform(
            get("/organizations/$organizationId/solutions").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$[0].id").value(firstSolutionId))
        .andExpect(jsonPath("$[0].key").value(firstSolutionKey))
        .andExpect(jsonPath("$[0].name").value(firstSolutionName))
        .andExpect(jsonPath("$[0].ownerId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$[0].organizationId").value(organizationId))
        .andExpect(jsonPath("$[0].sdkVersion").value(SOLUTION_SDK_VERSION))
        .andExpect(jsonPath("$[0].security.default").value(ROLE_NONE))
        .andExpect(jsonPath("$[0].security.accessControlList[0].role").value(ROLE_ADMIN))
        .andExpect(jsonPath("$[0].security.accessControlList[0].id").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$[1].id").value(secondSolutionId))
        .andExpect(jsonPath("$[1].key").value(secondSolutionKey))
        .andExpect(jsonPath("$[1].name").value(secondSolutionName))
        .andExpect(jsonPath("$[1].ownerId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$[1].organizationId").value(organizationId))
        .andExpect(jsonPath("$[1].sdkVersion").value(SOLUTION_SDK_VERSION))
        .andExpect(jsonPath("$[1].security.default").value(ROLE_NONE))
        .andExpect(jsonPath("$[1].security.accessControlList[0].role").value(ROLE_ADMIN))
        .andExpect(jsonPath("$[1].security.accessControlList[0].id").value(PLATFORM_ADMIN_EMAIL))
        .andDo(MockMvcResultHandlers.print())
        .andDo(document("organizations/{organization_id}/solutions/GET"))
  }

  @Test
  @WithMockOauth2User
  fun create_solution() {

    val description = "this_is_a_description"
    val tags = mutableListOf("tag1", "tag2")
    val parameterId = "parameter1"
    val parameterName = "my_parameter_name"
    val parameterDesc = "my_parameter_desc"
    val parameterLabels = mutableMapOf("fr" to "this_is_a_label")
    val parameterVarType = "this_is_a_vartype"
    val parameterDefaultValue = "this_is_a_default_value"
    val parameterMinValue = "this_is_a_minimal_value"
    val parameterMaxValue = "this_is_a_maximal_value"
    val parameterRegexValidation = "this_is_a_regex_to_validate_value"
    val options =
        mutableMapOf(
            "you_can_put" to "whatever_you_want_here",
            "even" to JSONObject(mapOf("object" to "if_you_want")))
    val parameters =
        mutableListOf(
            RunTemplateParameter(
                parameterId,
                parameterVarType,
                parameterName,
                parameterDesc,
                parameterLabels,
                parameterDefaultValue,
                parameterMinValue,
                parameterMaxValue,
                parameterRegexValidation,
                options))
    val parameterGroupId = "parameterGroup1"
    val parameterGroupParentId = "this_is_a_parent_id"
    val parameterGroups =
        mutableListOf(
            RunTemplateParameterGroup(
                parameterGroupId,
                parameterLabels,
                false,
                options,
                parameterGroupParentId,
                mutableListOf(parameterId)))
    val runTemplateId = "runtemplate1"
    val runTemplateName = "this_is_a_name"
    val runTemplateComputeSize = "this_is_a_compute_size"
    val runTemplateRunSizing =
        RunTemplateResourceSizing(
            ResourceSizeInfo("cpu_requests", "memory_requests"),
            ResourceSizeInfo("cpu_limits", "memory_limits"))
    val runTemplates =
        mutableListOf(
            RunTemplate(
                runTemplateId,
                runTemplateName,
                parameterLabels,
                description,
                tags,
                runTemplateComputeSize,
                runTemplateRunSizing,
                mutableListOf(parameterGroupId),
                10))

    val url = "this_is_the_solution_url"
    val security =
        SolutionSecurity(
            default = ROLE_NONE,
            accessControlList =
                mutableListOf(
                    SolutionAccessControl(id = PLATFORM_ADMIN_EMAIL, role = ROLE_ADMIN),
                    SolutionAccessControl(id = NEW_USER_ID, role = NEW_USER_ROLE)))
    val solutionCreateRequest =
        constructSolutionCreateRequest(
            SOLUTION_KEY,
            SOLUTION_NAME,
            SOLUTION_REPOSITORY,
            SOLUTION_VERSION,
            SOLUTION_SIMULATOR,
            description,
            false,
            tags,
            parameters,
            parameterGroups,
            runTemplates,
            url,
            security)

    mvc.perform(
            post("/organizations/$organizationId/solutions")
                .content(JSONObject(solutionCreateRequest).toString())
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.name").value(SOLUTION_NAME))
        .andExpect(jsonPath("$.key").value(SOLUTION_KEY))
        .andExpect(jsonPath("$.ownerId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.version").value(SOLUTION_VERSION))
        .andExpect(jsonPath("$.description").value(description))
        .andExpect(jsonPath("$.alwaysPull").value(false))
        .andExpect(jsonPath("$.parameters[0].id").value(parameterId))
        .andExpect(jsonPath("$.parameters[0].labels").value(parameterLabels))
        .andExpect(jsonPath("$.parameters[0].name").value(parameterName))
        .andExpect(jsonPath("$.parameters[0].description").value(parameterDesc))
        .andExpect(jsonPath("$.parameters[0].varType").value(parameterVarType))
        .andExpect(jsonPath("$.parameters[0].defaultValue").value(parameterDefaultValue))
        .andExpect(jsonPath("$.parameters[0].minValue").value(parameterMinValue))
        .andExpect(jsonPath("$.parameters[0].maxValue").value(parameterMaxValue))
        .andExpect(jsonPath("$.parameters[0].regexValidation").value(parameterRegexValidation))
        .andExpect(jsonPath("$.parameterGroups[0].id").value(parameterGroupId))
        .andExpect(jsonPath("$.parameterGroups[0].labels").value(parameterLabels))
        .andExpect(jsonPath("$.parameterGroups[0].isTable").value(false))
        .andExpect(jsonPath("$.parameterGroups[0].parentId").value(parameterGroupParentId))
        .andExpect(jsonPath("$.parameterGroups[0].parameters").value(mutableListOf(parameterId)))
        .andExpect(jsonPath("$.runTemplates[0].id").value(runTemplateId))
        .andExpect(jsonPath("$.runTemplates[0].name").value(runTemplateName))
        .andExpect(jsonPath("$.runTemplates[0].labels").value(parameterLabels))
        .andExpect(jsonPath("$.runTemplates[0].description").value(description))
        .andExpect(jsonPath("$.runTemplates[0].tags").value(tags))
        .andExpect(jsonPath("$.runTemplates[0].computeSize").value(runTemplateComputeSize))
        .andExpect(jsonPath("$.runTemplates[0].runSizing.requests.cpu").value("cpu_requests"))
        .andExpect(jsonPath("$.runTemplates[0].runSizing.requests.memory").value("memory_requests"))
        .andExpect(jsonPath("$.runTemplates[0].runSizing.limits.cpu").value("cpu_limits"))
        .andExpect(jsonPath("$.runTemplates[0].runSizing.limits.memory").value("memory_limits"))
        .andExpect(
            jsonPath("$.runTemplates[0].parameterGroups").value(mutableListOf(parameterGroupId)))
        .andExpect(jsonPath("$.runTemplates[0].executionTimeout").value(10))
        .andExpect(jsonPath("$.url").value(url))
        .andExpect(jsonPath("$.tags").value(tags))
        .andExpect(jsonPath("$.organizationId").value(organizationId))
        .andExpect(jsonPath("$.sdkVersion").value(SOLUTION_SDK_VERSION))
        .andExpect(jsonPath("$.security.default").value(ROLE_NONE))
        .andExpect(jsonPath("$.security.accessControlList[0].role").value(ROLE_ADMIN))
        .andExpect(jsonPath("$.security.accessControlList[0].id").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.security.accessControlList[1].role").value(NEW_USER_ROLE))
        .andExpect(jsonPath("$.security.accessControlList[1].id").value(NEW_USER_ID))
        .andDo(MockMvcResultHandlers.print())
        .andDo(document("organizations/{organization_id}/solutions/{solution_id}/POST"))
  }

  @Test
  @WithMockOauth2User
  fun get_solution() {

    val description = "this_is_a_description"
    val tags = mutableListOf("tag1", "tag2")
    val parameterId = "parameter1"
    val parameterLabels = mutableMapOf("fr" to "this_is_a_label")
    val parameterVarType = "this_is_a_vartype"
    val parameterName = "my_parameter_name"
    val parameterDesc = "my_parameter_desc"
    val parameterDefaultValue = "this_is_a_default_value"
    val parameterMinValue = "this_is_a_minimal_value"
    val parameterMaxValue = "this_is_a_maximal_value"
    val parameterRegexValidation = "this_is_a_regex_to_validate_value"
    val options =
        mutableMapOf(
            "you_can_put" to "whatever_you_want_here",
            "even" to JSONObject(mapOf("object" to "if_you_want")))
    val parameters =
        mutableListOf(
            RunTemplateParameter(
                parameterId,
                parameterVarType,
                parameterName,
                parameterDesc,
                parameterLabels,
                parameterDefaultValue,
                parameterMinValue,
                parameterMaxValue,
                parameterRegexValidation,
                options))
    val parameterGroupId = "parameterGroup1"
    val parameterGroupParentId = "this_is_a_parent_id"
    val parameterGroups =
        mutableListOf(
            RunTemplateParameterGroup(
                parameterGroupId,
                parameterLabels,
                false,
                options,
                parameterGroupParentId,
                mutableListOf(parameterId)))
    val runTemplateId = "runtemplate1"
    val runTemplateName = "this_is_a_name"
    val runTemplateComputeSize = "this_is_a_compute_size"
    val runTemplateRunSizing =
        RunTemplateResourceSizing(
            ResourceSizeInfo("cpu_requests", "memory_requests"),
            ResourceSizeInfo("cpu_limits", "memory_limits"))
    val runTemplates =
        mutableListOf(
            RunTemplate(
                runTemplateId,
                runTemplateName,
                parameterLabels,
                description,
                tags,
                runTemplateComputeSize,
                runTemplateRunSizing,
                mutableListOf(parameterGroupId),
                10))

    val url = "this_is_the_solution_url"
    val security =
        SolutionSecurity(
            default = ROLE_NONE,
            accessControlList =
                mutableListOf(
                    SolutionAccessControl(id = PLATFORM_ADMIN_EMAIL, role = ROLE_ADMIN),
                    SolutionAccessControl(id = NEW_USER_ID, role = NEW_USER_ROLE)))
    val solutionCreateRequest =
        constructSolutionCreateRequest(
            SOLUTION_KEY,
            SOLUTION_NAME,
            SOLUTION_REPOSITORY,
            SOLUTION_VERSION,
            SOLUTION_SIMULATOR,
            description,
            false,
            tags,
            parameters,
            parameterGroups,
            runTemplates,
            url,
            security)

    val solutionId = createSolutionAndReturnId(mvc, organizationId, solutionCreateRequest)

    mvc.perform(
            get("/organizations/$organizationId/solutions/$solutionId")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.id").value(solutionId))
        .andExpect(jsonPath("$.name").value(SOLUTION_NAME))
        .andExpect(jsonPath("$.key").value(SOLUTION_KEY))
        .andExpect(jsonPath("$.ownerId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.version").value(SOLUTION_VERSION))
        .andExpect(jsonPath("$.description").value(description))
        .andExpect(jsonPath("$.alwaysPull").value(false))
        .andExpect(jsonPath("$.parameters[0].id").value(parameterId))
        .andExpect(jsonPath("$.parameters[0].labels").value(parameterLabels))
        .andExpect(jsonPath("$.parameters[0].name").value(parameterName))
        .andExpect(jsonPath("$.parameters[0].description").value(parameterDesc))
        .andExpect(jsonPath("$.parameters[0].varType").value(parameterVarType))
        .andExpect(jsonPath("$.parameters[0].defaultValue").value(parameterDefaultValue))
        .andExpect(jsonPath("$.parameters[0].minValue").value(parameterMinValue))
        .andExpect(jsonPath("$.parameters[0].maxValue").value(parameterMaxValue))
        .andExpect(jsonPath("$.parameters[0].regexValidation").value(parameterRegexValidation))
        .andExpect(
            jsonPath("$.parameters[0].options[\"you_can_put\"]").value("whatever_you_want_here"))
        .andExpect(jsonPath("$.parameters[0].options[\"even\"][\"object\"]").value("if_you_want"))
        .andExpect(jsonPath("$.parameterGroups[0].id").value(parameterGroupId))
        .andExpect(jsonPath("$.parameterGroups[0].labels").value(parameterLabels))
        .andExpect(jsonPath("$.parameterGroups[0].isTable").value(false))
        .andExpect(jsonPath("$.parameterGroups[0].parentId").value(parameterGroupParentId))
        .andExpect(jsonPath("$.parameterGroups[0].parameters").value(mutableListOf(parameterId)))
        .andExpect(
            jsonPath("$.parameterGroups[0].options[\"you_can_put\"]")
                .value("whatever_you_want_here"))
        .andExpect(
            jsonPath("$.parameterGroups[0].options[\"even\"][\"object\"]").value("if_you_want"))
        .andExpect(jsonPath("$.runTemplates[0].id").value(runTemplateId))
        .andExpect(jsonPath("$.runTemplates[0].name").value(runTemplateName))
        .andExpect(jsonPath("$.runTemplates[0].labels").value(parameterLabels))
        .andExpect(jsonPath("$.runTemplates[0].description").value(description))
        .andExpect(jsonPath("$.runTemplates[0].tags").value(tags))
        .andExpect(jsonPath("$.runTemplates[0].computeSize").value(runTemplateComputeSize))
        .andExpect(jsonPath("$.runTemplates[0].runSizing.requests.cpu").value("cpu_requests"))
        .andExpect(jsonPath("$.runTemplates[0].runSizing.requests.memory").value("memory_requests"))
        .andExpect(jsonPath("$.runTemplates[0].runSizing.limits.cpu").value("cpu_limits"))
        .andExpect(jsonPath("$.runTemplates[0].runSizing.limits.memory").value("memory_limits"))
        .andExpect(
            jsonPath("$.runTemplates[0].parameterGroups").value(mutableListOf(parameterGroupId)))
        .andExpect(jsonPath("$.runTemplates[0].executionTimeout").value(10))
        .andExpect(jsonPath("$.url").value(url))
        .andExpect(jsonPath("$.tags").value(tags))
        .andExpect(jsonPath("$.organizationId").value(organizationId))
        .andExpect(jsonPath("$.sdkVersion").value(SOLUTION_SDK_VERSION))
        .andExpect(jsonPath("$.security.default").value(ROLE_NONE))
        .andExpect(jsonPath("$.security.accessControlList[0].role").value(ROLE_ADMIN))
        .andExpect(jsonPath("$.security.accessControlList[0].id").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.security.accessControlList[1].role").value(NEW_USER_ROLE))
        .andExpect(jsonPath("$.security.accessControlList[1].id").value(NEW_USER_ID))
        .andDo(MockMvcResultHandlers.print())
        .andDo(document("organizations/{organization_id}/solutions/{solution_id}/GET"))
  }

  @Test
  @WithMockOauth2User
  fun delete_solution() {
    val solutionId =
        createSolutionAndReturnId(mvc, organizationId, constructSolutionCreateRequest())

    mvc.perform(
            delete("/organizations/$organizationId/solutions/$solutionId")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andDo(MockMvcResultHandlers.print())
        .andDo(document("organizations/{organization_id}/solutions/{solution_id}/DELETE"))
  }

  @Test
  @WithMockOauth2User
  fun update_solution() {

    val solutionId =
        createSolutionAndReturnId(mvc, organizationId, constructSolutionCreateRequest())

    val description = "this_is_a_description"
    val tags = mutableListOf("tag1", "tag2")
    val url = "this_is_the_solution_url"

    val solutionUpdateRequest =
        constructSolutionUpdateRequest(
            SOLUTION_KEY,
            SOLUTION_NAME,
            SOLUTION_REPOSITORY,
            SOLUTION_VERSION,
            SOLUTION_SIMULATOR,
            description,
            true,
            tags,
            url)

    mvc.perform(
            patch("/organizations/$organizationId/solutions/$solutionId")
                .content(JSONObject(solutionUpdateRequest).toString())
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.id").value(solutionId))
        .andExpect(jsonPath("$.name").value(SOLUTION_NAME))
        .andExpect(jsonPath("$.key").value(SOLUTION_KEY))
        .andExpect(jsonPath("$.ownerId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.version").value(SOLUTION_VERSION))
        .andExpect(jsonPath("$.description").value(description))
        .andExpect(jsonPath("$.alwaysPull").value(true))
        .andExpect(jsonPath("$.url").value(url))
        .andExpect(jsonPath("$.tags").value(tags))
        .andExpect(jsonPath("$.organizationId").value(organizationId))
        .andExpect(jsonPath("$.sdkVersion").value(SOLUTION_SDK_VERSION))
        .andExpect(jsonPath("$.security.default").value(ROLE_NONE))
        .andExpect(jsonPath("$.security.accessControlList[0].role").value(ROLE_ADMIN))
        .andExpect(jsonPath("$.security.accessControlList[0].id").value(PLATFORM_ADMIN_EMAIL))
        .andDo(MockMvcResultHandlers.print())
        .andDo(document("organizations/{organization_id}/solutions/{solution_id}/PATCH"))
  }

  @Test
  @WithMockOauth2User
  fun add_solution_parameter_groups() {

    val solutionId =
        createSolutionAndReturnId(mvc, organizationId, constructSolutionCreateRequest())

    val parameterId = "parameter1"
    val parameterLabels = mutableMapOf("fr" to "this_is_a_label")
    val options =
        mutableMapOf(
            "you_can_put" to "whatever_you_want_here",
            "even" to JSONObject(mapOf("object" to "if_you_want")))
    val parameterGroupId = "parameterGroup1"
    val parameterGroupParentId = "this_is_a_parent_id"
    val parameterGroups =
        mutableListOf(
            RunTemplateParameterGroup(
                parameterGroupId,
                parameterLabels,
                false,
                options,
                parameterGroupParentId,
                mutableListOf(parameterId)))

    mvc.perform(
            patch("/organizations/$organizationId/solutions/$solutionId/parameterGroups")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSONArray(parameterGroups).toString())
                .with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$[0].id").value(parameterGroupId))
        .andExpect(jsonPath("$[0].labels").value(parameterLabels))
        .andExpect(jsonPath("$[0].isTable").value(false))
        .andExpect(jsonPath("$[0].parentId").value(parameterGroupParentId))
        .andExpect(jsonPath("$[0].options[\"you_can_put\"]").value("whatever_you_want_here"))
        .andExpect(jsonPath("$[0].options[\"even\"][\"object\"]").value("if_you_want"))
        .andExpect(jsonPath("$[0].parameters").value(mutableListOf(parameterId)))
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/solutions/{solution_id}/parameterGroups/PATCH"))
  }

  @Test
  @WithMockOauth2User
  fun delete_solution_parameter_groups() {

    val parameterId = "parameter1"
    val parameterLabels = mutableMapOf("fr" to "this_is_a_label")
    val options =
        mutableMapOf(
            "you_can_put" to "whatever_you_want_here",
            "even" to JSONObject(mapOf("object" to "if_you_want")))
    val parameterGroupId = "parameterGroup1"
    val parameterGroupParentId = "this_is_a_parent_id"
    val parameterGroups =
        mutableListOf(
            RunTemplateParameterGroup(
                parameterGroupId,
                parameterLabels,
                false,
                options,
                parameterGroupParentId,
                mutableListOf(parameterId)))

    val solutionId =
        createSolutionAndReturnId(
            mvc, organizationId, constructSolutionCreateRequest(parameterGroups = parameterGroups))
    mvc.perform(
            delete("/organizations/$organizationId/solutions/$solutionId/parameterGroups")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/solutions/{solution_id}/parameterGroups/DELETE"))
  }

  @Test
  @WithMockOauth2User
  fun create_solution_parameter() {

    val parameterLabels = mutableMapOf("fr" to "this_is_a_label")
    val parameterVarType = "this_is_a_vartype"
    val parameterName = "my_parameter_name"
    val parameterDesc = "my_parameter_desc"
    val parameterDefaultValue = "this_is_a_default_value"
    val parameterMinValue = "this_is_a_minimal_value"
    val parameterMaxValue = "this_is_a_maximal_value"
    val parameterRegexValidation = "this_is_a_regex_to_validate_value"
    val options =
        mutableMapOf(
            "you_can_put" to "whatever_you_want_here",
            "even" to JSONObject(mapOf("object" to "if_you_want")))
    val parameterCreateRequest =
            RunTemplateParameterCreateRequest(
                parameterName,
                parameterVarType,
                parameterDesc,
                parameterLabels,
                parameterDefaultValue,
                parameterMinValue,
                parameterMaxValue,
                parameterRegexValidation,
                options)

    val solutionId =
        createSolutionAndReturnId(mvc, organizationId, constructSolutionCreateRequest())

    mvc.perform(
            post("/organizations/$organizationId/solutions/$solutionId/parameters")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSONObject(parameterCreateRequest).toString())
                .with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.labels").value(parameterLabels))
        .andExpect(jsonPath("$.varType").value(parameterVarType))
        .andExpect(jsonPath("$.description").value(parameterDesc))
        .andExpect(jsonPath("$.name").value(parameterName))
        .andExpect(jsonPath("$.defaultValue").value(parameterDefaultValue))
        .andExpect(jsonPath("$.minValue").value(parameterMinValue))
        .andExpect(jsonPath("$.maxValue").value(parameterMaxValue))
        .andExpect(jsonPath("$.regexValidation").value(parameterRegexValidation))
        .andExpect(jsonPath("$.options[\"you_can_put\"]").value("whatever_you_want_here"))
        .andExpect(jsonPath("$.options[\"even\"][\"object\"]").value("if_you_want"))
        .andDo(MockMvcResultHandlers.print())
        .andDo(document("organizations/{organization_id}/solutions/{solution_id}/parameters/POST"))
  }

    //TODO missing /parameters/{parameter_id}/GET
    //TODO missing /parameters/{parameter_id}/PATCH
    //TODO missing /parameters/POST

    @Test
    @WithMockOauth2User
    fun list_solution_parameters() {

        val parameterId = "parameter1"
        val parameterLabels = mutableMapOf("fr" to "this_is_a_label")
        val parameterVarType = "this_is_a_vartype"
        val parameterName = "my_parameter_name"
        val parameterDesc = "my_parameter_desc"
        val parameterDefaultValue = "this_is_a_default_value"
        val parameterMinValue = "this_is_a_minimal_value"
        val parameterMaxValue = "this_is_a_maximal_value"
        val parameterRegexValidation = "this_is_a_regex_to_validate_value"
        val options =
            mutableMapOf(
                "you_can_put" to "whatever_you_want_here",
                "even" to JSONObject(mapOf("object" to "if_you_want")))
        val parameters =
            mutableListOf(
                RunTemplateParameter(
                    parameterId,
                    parameterVarType,
                    parameterName,
                    parameterDesc,
                    parameterLabels,
                    parameterDefaultValue,
                    parameterMinValue,
                    parameterMaxValue,
                    parameterRegexValidation,
                    options))
        val solutionId =
            createSolutionAndReturnId(
                mvc, organizationId, constructSolutionCreateRequest(parameters = parameters))
        mvc.perform(
            get("/organizations/$organizationId/solutions/$solutionId/parameters")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful)
            .andDo(MockMvcResultHandlers.print())
            .andExpect(jsonPath("$[0].id").value(parameterId))
            .andExpect(jsonPath("$[0].labels").value(parameterLabels))
            .andExpect(jsonPath("$[0].varType").value(parameterVarType))
            .andExpect(jsonPath("$[0].name").value(parameterName))
            .andExpect(jsonPath("$[0].description").value(parameterDesc))
            .andExpect(jsonPath("$[0].defaultValue").value(parameterDefaultValue))
            .andExpect(jsonPath("$[0].minValue").value(parameterMinValue))
            .andExpect(jsonPath("$[0].maxValue").value(parameterMaxValue))
            .andExpect(jsonPath("$[0].regexValidation").value(parameterRegexValidation))
            .andExpect(jsonPath("$[0].options[\"you_can_put\"]").value("whatever_you_want_here"))
            .andExpect(jsonPath("$[0].options[\"even\"][\"object\"]").value("if_you_want"))
            .andDo(
                document("organizations/{organization_id}/solutions/{solution_id}/parameters/GET"))
    }



    @Test
  @WithMockOauth2User
  fun delete_solution_parameters() {

    val parameterId = "parameter1"
    val parameterLabels = mutableMapOf("fr" to "this_is_a_label")
    val parameterVarType = "this_is_a_vartype"
    val parameterName = "my_parameter_name"
    val parameterDesc = "my_parameter_desc"
    val parameterDefaultValue = "this_is_a_default_value"
    val parameterMinValue = "this_is_a_minimal_value"
    val parameterMaxValue = "this_is_a_maximal_value"
    val parameterRegexValidation = "this_is_a_regex_to_validate_value"
    val options =
        mutableMapOf(
            "you_can_put" to "whatever_you_want_here",
            "even" to JSONObject(mapOf("object" to "if_you_want")))
    val parameters =
        mutableListOf(
            RunTemplateParameter(
                parameterId,
                parameterVarType,
                parameterName,
                parameterDesc,
                parameterLabels,
                parameterDefaultValue,
                parameterMinValue,
                parameterMaxValue,
                parameterRegexValidation,
                options))
    val solutionId =
        createSolutionAndReturnId(
            mvc, organizationId, constructSolutionCreateRequest(parameters = parameters))
    mvc.perform(
            delete("/organizations/$organizationId/solutions/$solutionId/parameters/$parameterId")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document("organizations/{organization_id}/solutions/{solution_id}/parameters/{parameter_id}/DELETE"))
  }

  @Test
  @WithMockOauth2User
  fun add_solution_runTemplates() {

    val description = "this_is_a_description"
    val tags = mutableListOf("tag1", "tag2")
    val parameterLabels = mutableMapOf("fr" to "this_is_a_label")
    val parameterGroupId = "parameterGroup1"
    val runTemplateId = "runtemplate1"
    val runTemplateName = "this_is_a_name"
    val runTemplateComputeSize = "this_is_a_compute_size"
    val runTemplateRunSizing =
        RunTemplateResourceSizing(
            ResourceSizeInfo("cpu_requests", "memory_requests"),
            ResourceSizeInfo("cpu_limits", "memory_limits"))
    val runTemplates =
        mutableListOf(
            RunTemplate(
                runTemplateId,
                runTemplateName,
                parameterLabels,
                description,
                tags,
                runTemplateComputeSize,
                runTemplateRunSizing,
                mutableListOf(parameterGroupId),
                10))

    val solutionId =
        createSolutionAndReturnId(mvc, organizationId, constructSolutionCreateRequest())

    mvc.perform(
            patch("/organizations/$organizationId/solutions/$solutionId/runTemplates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSONArray(runTemplates).toString())
                .with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$[0].id").value(runTemplateId))
        .andExpect(jsonPath("$[0].name").value(runTemplateName))
        .andExpect(jsonPath("$[0].labels").value(parameterLabels))
        .andExpect(jsonPath("$[0].description").value(description))
        .andExpect(jsonPath("$[0].tags").value(tags))
        .andExpect(jsonPath("$[0].computeSize").value(runTemplateComputeSize))
        .andExpect(jsonPath("$[0].runSizing.requests.cpu").value("cpu_requests"))
        .andExpect(jsonPath("$[0].runSizing.requests.memory").value("memory_requests"))
        .andExpect(jsonPath("$[0].runSizing.limits.cpu").value("cpu_limits"))
        .andExpect(jsonPath("$[0].runSizing.limits.memory").value("memory_limits"))
        .andExpect(jsonPath("$[0].parameterGroups").value(mutableListOf(parameterGroupId)))
        .andExpect(jsonPath("$[0].executionTimeout").value(10))
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document("organizations/{organization_id}/solutions/{solution_id}/runTemplates/PATCH"))
  }

  @Test
  @WithMockOauth2User
  fun delete_solution_runTemplates() {

    val description = "this_is_a_description"
    val tags = mutableListOf("tag1", "tag2")
    val parameterLabels = mutableMapOf("fr" to "this_is_a_label")
    val parameterGroupId = "parameterGroup1"
    val runTemplateId = "runtemplate1"
    val runTemplateName = "this_is_a_name"
    val runTemplateComputeSize = "this_is_a_compute_size"
    val runTemplateRunSizing =
        RunTemplateResourceSizing(
            ResourceSizeInfo("cpu_requests", "memory_requests"),
            ResourceSizeInfo("cpu_limits", "memory_limits"))
    val runTemplates =
        mutableListOf(
            RunTemplate(
                runTemplateId,
                runTemplateName,
                parameterLabels,
                description,
                tags,
                runTemplateComputeSize,
                runTemplateRunSizing,
                mutableListOf(parameterGroupId),
                10))

    val solutionId =
        createSolutionAndReturnId(
            mvc, organizationId, constructSolutionCreateRequest(runTemplates = runTemplates))

    mvc.perform(
            delete("/organizations/$organizationId/solutions/$solutionId/runTemplates")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document("organizations/{organization_id}/solutions/{solution_id}/runTemplates/DELETE"))
  }

  @Test
  @WithMockOauth2User
  fun delete_solution_runTemplate() {

    val description = "this_is_a_description"
    val tags = mutableListOf("tag1", "tag2")
    val parameterLabels = mutableMapOf("fr" to "this_is_a_label")
    val parameterGroupId = "parameterGroup1"
    val runTemplateId = "runtemplate1"
    val runTemplateName = "this_is_a_name"
    val runTemplateComputeSize = "this_is_a_compute_size"
    val runTemplateRunSizing =
        RunTemplateResourceSizing(
            ResourceSizeInfo("cpu_requests", "memory_requests"),
            ResourceSizeInfo("cpu_limits", "memory_limits"))
    val runTemplates =
        mutableListOf(
            RunTemplate(
                runTemplateId,
                runTemplateName,
                parameterLabels,
                description,
                tags,
                runTemplateComputeSize,
                runTemplateRunSizing,
                mutableListOf(parameterGroupId),
                10))

    val solutionId =
        createSolutionAndReturnId(
            mvc, organizationId, constructSolutionCreateRequest(runTemplates = runTemplates))

    mvc.perform(
            delete(
                    "/organizations/$organizationId/solutions/$solutionId/runTemplates/$runTemplateId")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/solutions/{solution_id}/runTemplates/{run_template_id}/DELETE"))
  }

  @Test
  @WithMockOauth2User
  fun update_solution_runTemplate() {
    val runTemplateId = "runtemplate1"

    val runTemplates =
        mutableListOf(
            RunTemplate(
                runTemplateId,
                "this_is_a_name",
                mutableMapOf("fr" to "this_is_a_label"),
                "this_is_a_description",
                mutableListOf("tag1", "tag2"),
                "this_is_a_compute_size",
                RunTemplateResourceSizing(
                    ResourceSizeInfo("cpu_requests", "memory_requests"),
                    ResourceSizeInfo("cpu_limits", "memory_limits")),
                mutableListOf("parameterGroup1"),
                10))

    val solutionId =
        createSolutionAndReturnId(
            mvc, organizationId, constructSolutionCreateRequest(runTemplates = runTemplates))

    val description = "this_is_a_description2"
    val tags = mutableListOf("tag1", "tag2", "tag3")
    val parameterLabels = mutableMapOf("de" to "this_is_a_label")
    val parameterGroupId = "parameterGroup2"
    val runTemplateName = "this_is_a_name2"
    val runTemplateComputeSize = "this_is_a_compute_size2"
    val runTemplateRunSizing =
        RunTemplateResourceSizing(
            ResourceSizeInfo("cpu_requests2", "memory_requests2"),
            ResourceSizeInfo("cpu_limits2", "memory_limits2"))
    val newRunTemplate =
        RunTemplate(
            runTemplateId,
            runTemplateName,
            parameterLabels,
            description,
            tags,
            runTemplateComputeSize,
            runTemplateRunSizing,
            mutableListOf(parameterGroupId),
            100)

    mvc.perform(
            patch(
                    "/organizations/$organizationId/solutions/$solutionId/runTemplates/$runTemplateId")
                .content(JSONObject(newRunTemplate).toString())
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$[0].id").value(runTemplateId))
        .andExpect(jsonPath("$[0].name").value(runTemplateName))
        .andExpect(jsonPath("$[0].labels").value(parameterLabels))
        .andExpect(jsonPath("$[0].description").value(description))
        .andExpect(jsonPath("$[0].tags").value(tags))
        .andExpect(jsonPath("$[0].computeSize").value(runTemplateComputeSize))
        .andExpect(jsonPath("$[0].runSizing.requests.cpu").value("cpu_requests2"))
        .andExpect(jsonPath("$[0].runSizing.requests.memory").value("memory_requests2"))
        .andExpect(jsonPath("$[0].runSizing.limits.cpu").value("cpu_limits2"))
        .andExpect(jsonPath("$[0].runSizing.limits.memory").value("memory_limits2"))
        .andExpect(jsonPath("$[0].parameterGroups").value(mutableListOf(parameterGroupId)))
        .andExpect(jsonPath("$[0].executionTimeout").value(100))
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/solutions/{solution_id}/runTemplates/{run_template_id}/PATCH"))
  }

  @Test
  @WithMockOauth2User
  fun get_solution_security() {

    val solutionId =
        createSolutionAndReturnId(mvc, organizationId, constructSolutionCreateRequest())

    mvc.perform(get("/organizations/$organizationId/solutions/$solutionId/security"))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.default").value(ROLE_NONE))
        .andExpect(jsonPath("$.accessControlList[0].role").value(ROLE_ADMIN))
        .andExpect(jsonPath("$.accessControlList[0].id").value(PLATFORM_ADMIN_EMAIL))
        .andDo(MockMvcResultHandlers.print())
        .andDo(document("organizations/{organization_id}/solutions/{solution_id}/security/GET"))
  }

  @Test
  @WithMockOauth2User
  fun add_solution_security_access() {

    val solutionId =
        createSolutionAndReturnId(mvc, organizationId, constructSolutionCreateRequest())

    mvc.perform(
            post("/organizations/$organizationId/solutions/$solutionId/security/access")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                        "id": "$NEW_USER_ID",
                        "role": "$NEW_USER_ROLE"
                        }
                    """
                        .trimMargin())
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.role").value(NEW_USER_ROLE))
        .andExpect(jsonPath("$.id").value(NEW_USER_ID))
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/solutions/{solution_id}/security/access/POST"))
  }

  @Test
  @WithMockOauth2User
  fun get_solution_security_access() {

    val solutionId =
        createSolutionAndReturnId(mvc, organizationId, constructSolutionCreateRequest())

    mvc.perform(
            get(
                "/organizations/$organizationId/solutions/$solutionId/security/access/$PLATFORM_ADMIN_EMAIL"))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.role").value(ROLE_ADMIN))
        .andExpect(jsonPath("$.id").value(PLATFORM_ADMIN_EMAIL))
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/solutions/{solution_id}/security/access/{identity_id}/GET"))
  }

  @Test
  @WithMockOauth2User
  fun update_solution_security_access() {

    val solutionSecurity =
        SolutionSecurity(
            default = ROLE_NONE,
            accessControlList =
                mutableListOf(
                    SolutionAccessControl(id = PLATFORM_ADMIN_EMAIL, role = ROLE_ADMIN),
                    SolutionAccessControl(id = NEW_USER_ID, role = NEW_USER_ROLE)))
    val solutionId =
        createSolutionAndReturnId(
            mvc, organizationId, constructSolutionCreateRequest(security = solutionSecurity))

    mvc.perform(
            patch(
                    "/organizations/$organizationId/solutions/$solutionId/security/access/$NEW_USER_ID")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"role":"$ROLE_VIEWER"}""")
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.role").value(ROLE_VIEWER))
        .andExpect(jsonPath("$.id").value(NEW_USER_ID))
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/solutions/{solution_id}/security/access/{identity_id}/PATCH"))
  }

  @Test
  @WithMockOauth2User
  fun delete_solution_security_access() {

    val solutionSecurity =
        SolutionSecurity(
            default = ROLE_NONE,
            accessControlList =
                mutableListOf(
                    SolutionAccessControl(id = PLATFORM_ADMIN_EMAIL, role = ROLE_ADMIN),
                    SolutionAccessControl(id = NEW_USER_ID, role = NEW_USER_ROLE)))
    val solutionId =
        createSolutionAndReturnId(
            mvc, organizationId, constructSolutionCreateRequest(security = solutionSecurity))

    mvc.perform(
            delete(
                    "/organizations/$organizationId/solutions/$solutionId/security/access/$NEW_USER_ID")
                .with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/solutions/{solution_id}/security/access/{identity_id}/DELETE"))
  }

  @Test
  @WithMockOauth2User
  fun update_solution_security_default() {

    val solutionId =
        createSolutionAndReturnId(mvc, organizationId, constructSolutionCreateRequest())

    mvc.perform(
            patch("/organizations/$organizationId/solutions/$solutionId/security/default")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"role":"$ROLE_VIEWER"}""")
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.default").value(ROLE_VIEWER))
        .andExpect(jsonPath("$.accessControlList[0].id").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.accessControlList[0].role").value(ROLE_ADMIN))
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/solutions/{solution_id}/security/default/POST"))
  }

  @Test
  @WithMockOauth2User
  fun list_solution_users() {

    val solutionSecurity =
        SolutionSecurity(
            default = ROLE_NONE,
            accessControlList =
                mutableListOf(
                    SolutionAccessControl(id = PLATFORM_ADMIN_EMAIL, role = ROLE_ADMIN),
                    SolutionAccessControl(id = NEW_USER_ID, role = NEW_USER_ROLE)))
    val solutionId =
        createSolutionAndReturnId(
            mvc, organizationId, constructSolutionCreateRequest(security = solutionSecurity))

    mvc.perform(
            get("/organizations/$organizationId/solutions/$solutionId/security/users")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$[0]").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$[1]").value(NEW_USER_ID))
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document("organizations/{organization_id}/solutions/{solution_id}/security/users/GET"))
  }
}
