// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.home.dataset

import com.cosmotech.api.home.Constants.PLATFORM_ADMIN_EMAIL
import com.cosmotech.api.home.ControllerTestBase
import com.cosmotech.api.home.ControllerTestUtils.OrganizationUtils.constructOrganizationCreateRequest
import com.cosmotech.api.home.ControllerTestUtils.OrganizationUtils.createOrganizationAndReturnId
import com.cosmotech.api.home.ControllerTestUtils.SolutionUtils.constructSolutionCreateRequest
import com.cosmotech.api.home.ControllerTestUtils.SolutionUtils.createSolutionAndReturnId
import com.cosmotech.api.home.ControllerTestUtils.WorkspaceUtils.constructWorkspaceCreateRequest
import com.cosmotech.api.home.ControllerTestUtils.WorkspaceUtils.createWorkspaceAndReturnId
import com.cosmotech.api.home.annotations.WithMockOauth2User
import com.cosmotech.api.home.dataset.DatasetConstants.DATASET_DESCRIPTION
import com.cosmotech.api.home.dataset.DatasetConstants.DATASET_NAME
import com.cosmotech.api.home.dataset.DatasetConstants.DATASET_PART_DESCRIPTION
import com.cosmotech.api.home.dataset.DatasetConstants.DATASET_PART_NAME
import com.cosmotech.api.home.run.RunConstants.RequestContent.DESCRIPTION
import com.cosmotech.api.home.run.RunConstants.RequestContent.PARAMETER_GROUP_ID
import com.cosmotech.api.home.run.RunConstants.RequestContent.PARAMETER_LABELS
import com.cosmotech.api.home.run.RunConstants.RequestContent.RUN_TEMPLATE_COMPUTE_SIZE
import com.cosmotech.api.home.run.RunConstants.RequestContent.RUN_TEMPLATE_NAME
import com.cosmotech.api.home.run.RunConstants.RequestContent.TAGS
import com.cosmotech.api.home.runner.RunnerConstants.RUNNER_RUN_TEMPLATE
import com.cosmotech.api.rbac.ROLE_ADMIN
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.dataset.domain.DatasetAccessControl
import com.cosmotech.dataset.domain.DatasetCreateRequest
import com.cosmotech.dataset.domain.DatasetPartCreateRequest
import com.cosmotech.dataset.domain.DatasetPartTypeEnum
import com.cosmotech.dataset.domain.DatasetSecurity
import com.cosmotech.solution.domain.RunTemplateCreateRequest
import com.cosmotech.solution.domain.RunTemplateResourceSizing
import org.hamcrest.Matchers.greaterThan
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.shaded.org.apache.commons.io.IOUtils

@ActiveProfiles(profiles = ["test"])
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DatasetControllerTests : ControllerTestBase() {

  private lateinit var organizationId: String
  private lateinit var solutionId: String
  private lateinit var workspaceId: String

  private val logger = LoggerFactory.getLogger(DatasetControllerTests::class.java)

  @BeforeEach
  fun beforeEach() {

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
  }

  @Test
  @WithMockOauth2User
  fun create_dataset() {
    val datasetCreateRequest =
        MockMultipartFile(
            "datasetCreateRequest",
            null,
            MediaType.APPLICATION_JSON_VALUE,
            JSONObject(constructDataset()).toString().byteInputStream())
    val fileName = "test.txt"
    val fileToUpload =
        this::class.java.getResourceAsStream("/dataset/$fileName")
            ?: throw IllegalStateException(
                "$fileName file used for organizations/{organization_id}/workspaces/{workspace_id}/datasets/POST endpoint documentation cannot be null")
    val files =
        MockMultipartFile(
            "files",
            fileName,
            MediaType.MULTIPART_FORM_DATA_VALUE,
            IOUtils.toByteArray(fileToUpload))
    mvc.perform(
            multipart("/organizations/$organizationId/workspaces/$workspaceId/datasets")
                .file(datasetCreateRequest)
                .file(files)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.name").value(DATASET_NAME))
        .andExpect(jsonPath("$.description").value(DATASET_DESCRIPTION))
        .andExpect(jsonPath("$.organizationId").value(organizationId))
        .andExpect(jsonPath("$.workspaceId").value(workspaceId))
        .andExpect(jsonPath("$.createInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.createInfo.timestamp").isNumber)
        .andExpect(jsonPath("$.createInfo.timestamp").value(greaterThan(0.toLong())))
        .andExpect(jsonPath("$.updateInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.updateInfo.timestamp").isNumber)
        .andExpect(jsonPath("$.updateInfo.timestamp").value(greaterThan(0.toLong())))
        .andExpect(jsonPath("$.parts[0].name").value(DATASET_PART_NAME))
        .andExpect(jsonPath("$.parts[0].description").value(DATASET_PART_DESCRIPTION))
        .andExpect(jsonPath("$.parts[0].organizationId").value(organizationId))
        .andExpect(jsonPath("$.parts[0].workspaceId").value(workspaceId))
        .andExpect(jsonPath("$.parts[0].tags").value(mutableListOf("tag_part1", "tag_part2")))
        .andExpect(jsonPath("$.parts[0].type").value(DatasetPartTypeEnum.Relational.value))
        .andExpect(jsonPath("$.parts[0].createInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.parts[0].createInfo.timestamp").isNumber)
        .andExpect(jsonPath("$.parts[0].createInfo.timestamp").value(greaterThan(0.toLong())))
        .andExpect(jsonPath("$.parts[0].updateInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.parts[0].updateInfo.timestamp").isNumber)
        .andExpect(jsonPath("$.parts[0].updateInfo.timestamp").value(greaterThan(0.toLong())))
        .andExpect(jsonPath("$.tags").value(mutableListOf("tag1", "tag2")))
        .andExpect(jsonPath("$.security.default").value(ROLE_NONE))
        .andExpect(jsonPath("$.security.accessControlList[0].role").value(ROLE_ADMIN))
        .andExpect(jsonPath("$.security.accessControlList[0].id").value(PLATFORM_ADMIN_EMAIL))
        .andDo(MockMvcResultHandlers.print())
        .andDo(document("organizations/{organization_id}/workspaces/{workspace_id}/datasets/POST"))
  }

  fun constructDataset(name: String = DATASET_NAME): DatasetCreateRequest {
    return DatasetCreateRequest(
        name = name,
        description = DATASET_DESCRIPTION,
        tags = mutableListOf("tag1", "tag2"),
        parts =
            mutableListOf(
                DatasetPartCreateRequest(
                    name = DATASET_PART_NAME,
                    description = DATASET_PART_DESCRIPTION,
                    tags = mutableListOf("tag_part1", "tag_part2"),
                    type = DatasetPartTypeEnum.Relational)),
        security =
            DatasetSecurity(
                default = ROLE_NONE,
                accessControlList =
                    mutableListOf(
                        DatasetAccessControl(id = PLATFORM_ADMIN_EMAIL, role = ROLE_ADMIN))))
  }
}
