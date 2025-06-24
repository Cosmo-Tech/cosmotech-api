// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.home.dataset

import com.cosmotech.api.home.Constants.ORGANIZATION_USER_EMAIL
import com.cosmotech.api.home.Constants.PLATFORM_ADMIN_EMAIL
import com.cosmotech.api.home.ControllerTestBase
import com.cosmotech.api.home.ControllerTestUtils.DatasetUtils.constructDatasetCreateRequest
import com.cosmotech.api.home.ControllerTestUtils.DatasetUtils.constructDatasetPartCreateRequest
import com.cosmotech.api.home.ControllerTestUtils.DatasetUtils.createDatasetAndReturnId
import com.cosmotech.api.home.ControllerTestUtils.DatasetUtils.createDatasetPartAndReturnId
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
import com.cosmotech.api.home.dataset.DatasetConstants.TEST_FILE_NAME
import com.cosmotech.api.home.run.RunConstants.RequestContent.DESCRIPTION
import com.cosmotech.api.home.run.RunConstants.RequestContent.PARAMETER_GROUP_ID
import com.cosmotech.api.home.run.RunConstants.RequestContent.PARAMETER_LABELS
import com.cosmotech.api.home.run.RunConstants.RequestContent.RUN_TEMPLATE_COMPUTE_SIZE
import com.cosmotech.api.home.run.RunConstants.RequestContent.RUN_TEMPLATE_NAME
import com.cosmotech.api.home.run.RunConstants.RequestContent.TAGS
import com.cosmotech.api.home.runner.RunnerConstants.RUNNER_RUN_TEMPLATE
import com.cosmotech.api.rbac.ROLE_ADMIN
import com.cosmotech.api.rbac.ROLE_EDITOR
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.rbac.ROLE_VIEWER
import com.cosmotech.dataset.domain.DatasetAccessControl
import com.cosmotech.dataset.domain.DatasetPartCreateRequest
import com.cosmotech.dataset.domain.DatasetPartTypeEnum
import com.cosmotech.dataset.domain.DatasetPartUpdateRequest
import com.cosmotech.dataset.domain.DatasetRole
import com.cosmotech.dataset.domain.DatasetSecurity
import com.cosmotech.dataset.domain.DatasetUpdateRequest
import com.cosmotech.solution.domain.RunTemplateCreateRequest
import com.cosmotech.solution.domain.RunTemplateResourceSizing
import java.io.InputStream
import kotlin.test.Ignore
import org.apache.commons.io.IOUtils
import org.hamcrest.Matchers.greaterThan
import org.json.JSONArray
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

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
            JSONObject(constructDatasetCreateRequest()).toString().byteInputStream())

    val fileToUpload =
        this::class.java.getResourceAsStream("/dataset/$TEST_FILE_NAME")
            ?: throw IllegalStateException(
                "$TEST_FILE_NAME file used for organizations/{organization_id}/workspaces/{workspace_id}/datasets/POST endpoint documentation cannot be null")
    val files =
        MockMultipartFile(
            "files",
            TEST_FILE_NAME,
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
        .andExpect(jsonPath("$.parts[0].sourceName").value(TEST_FILE_NAME))
        .andExpect(jsonPath("$.parts[0].tags").value(mutableListOf("tag_part1", "tag_part2")))
        .andExpect(jsonPath("$.parts[0].type").value(DatasetPartTypeEnum.File.value))
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

  @Test
  @WithMockOauth2User
  fun download_dataset_part() {

    val datasetId =
        createDatasetAndReturnId(mvc, organizationId, workspaceId, constructDatasetCreateRequest())
    val datasetPartId =
        createDatasetPartAndReturnId(
            mvc, organizationId, workspaceId, datasetId, constructDatasetPartCreateRequest())

    val fileUploaded =
        this::class.java.getResourceAsStream("/dataset/$TEST_FILE_NAME")
            ?: throw IllegalStateException(
                "$TEST_FILE_NAME file used for endpoints test documentation cannot be null")

    mvc.perform(
            get(
                    "/organizations/$organizationId/workspaces/$workspaceId/datasets/$datasetId/parts/$datasetPartId/download")
                .accept(MediaType.APPLICATION_OCTET_STREAM))
        .andExpect(status().is2xxSuccessful)
        .andExpect { result ->
          result.response.contentAsString == fileUploaded.bufferedReader().readText()
        }
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/parts/{dataset_part_id}/download/GET"))
  }

  @Test
  @WithMockOauth2User
  fun get_dataset() {

    val datasetId =
        createDatasetAndReturnId(mvc, organizationId, workspaceId, constructDatasetCreateRequest())
    mvc.perform(
            get("/organizations/$organizationId/workspaces/$workspaceId/datasets/$datasetId")
                .accept(MediaType.APPLICATION_JSON))
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
        .andExpect(jsonPath("$.parts[0].sourceName").value(TEST_FILE_NAME))
        .andExpect(jsonPath("$.parts[0].tags").value(mutableListOf("tag_part1", "tag_part2")))
        .andExpect(jsonPath("$.parts[0].type").value(DatasetPartTypeEnum.File.value))
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
        .andDo(
            document(
                "organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/GET"))
  }

  @Test
  @WithMockOauth2User
  fun delete_dataset() {

    val datasetId =
        createDatasetAndReturnId(mvc, organizationId, workspaceId, constructDatasetCreateRequest())

    mvc.perform(
            delete("/organizations/$organizationId/workspaces/$workspaceId/datasets/$datasetId")
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/DELETE"))
  }

  @Test
  @WithMockOauth2User
  fun create_dataset_access_control() {

    val datasetId =
        createDatasetAndReturnId(mvc, organizationId, workspaceId, constructDatasetCreateRequest())

    mvc.perform(
            post(
                    "/organizations/$organizationId/workspaces/$workspaceId/datasets/$datasetId/security/access")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    JSONObject(
                            DatasetAccessControl(
                                role = ROLE_ADMIN,
                                id = ORGANIZATION_USER_EMAIL,
                            ))
                        .toString())
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.role").value(ROLE_ADMIN))
        .andExpect(jsonPath("$.id").value(ORGANIZATION_USER_EMAIL))
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/security/access/POST"))
  }

  @Test
  @WithMockOauth2User
  fun get_dataset_access_control() {

    val datasetSecurity =
        DatasetSecurity(
            default = ROLE_NONE,
            accessControlList =
                mutableListOf(
                    DatasetAccessControl(role = ROLE_ADMIN, id = PLATFORM_ADMIN_EMAIL),
                    DatasetAccessControl(role = ROLE_EDITOR, id = ORGANIZATION_USER_EMAIL)))

    val datasetId =
        createDatasetAndReturnId(
            mvc,
            organizationId,
            workspaceId,
            constructDatasetCreateRequest(security = datasetSecurity))

    mvc.perform(
            get(
                    "/organizations/$organizationId/workspaces/$workspaceId/datasets/$datasetId/security/access/$ORGANIZATION_USER_EMAIL")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.role").value(ROLE_EDITOR))
        .andExpect(jsonPath("$.id").value(ORGANIZATION_USER_EMAIL))
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/security/access/{identity_id}/GET"))
  }

  @Test
  @WithMockOauth2User
  fun delete_dataset_access_control() {

    val datasetSecurity =
        DatasetSecurity(
            default = ROLE_NONE,
            accessControlList =
                mutableListOf(
                    DatasetAccessControl(role = ROLE_ADMIN, id = PLATFORM_ADMIN_EMAIL),
                    DatasetAccessControl(role = ROLE_EDITOR, id = ORGANIZATION_USER_EMAIL)))

    val datasetId =
        createDatasetAndReturnId(
            mvc,
            organizationId,
            workspaceId,
            constructDatasetCreateRequest(security = datasetSecurity))

    mvc.perform(
            delete(
                    "/organizations/$organizationId/workspaces/$workspaceId/datasets/$datasetId/security/access/$ORGANIZATION_USER_EMAIL")
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/security/access/{identity_id}/DELETE"))
  }

  @Test
  @WithMockOauth2User
  fun create_dataset_part() {

    val datasetId =
        createDatasetAndReturnId(mvc, organizationId, workspaceId, constructDatasetCreateRequest())

    val fileToUpload =
        this::class.java.getResourceAsStream("/dataset/$TEST_FILE_NAME")
            ?: throw IllegalStateException(
                "$TEST_FILE_NAME file used for organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/parts/POST endpoint documentation cannot be null")
    val file =
        MockMultipartFile(
            "file",
            TEST_FILE_NAME,
            MediaType.MULTIPART_FORM_DATA_VALUE,
            IOUtils.toByteArray(fileToUpload))

    val datasetPartCreateRequest =
        MockMultipartFile(
            "datasetPartCreateRequest",
            null,
            MediaType.APPLICATION_JSON_VALUE,
            JSONObject(constructDatasetPartCreateRequest()).toString().byteInputStream())
    mvc.perform(
            multipart(
                    "/organizations/$organizationId/workspaces/$workspaceId/datasets/$datasetId/parts")
                .file(file)
                .file(datasetPartCreateRequest)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.name").value(DATASET_PART_NAME))
        .andExpect(jsonPath("$.description").value(DATASET_PART_DESCRIPTION))
        .andExpect(jsonPath("$.sourceName").value(TEST_FILE_NAME))
        .andExpect(jsonPath("$.createInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.createInfo.timestamp").isNumber)
        .andExpect(jsonPath("$.createInfo.timestamp").value(greaterThan(0.toLong())))
        .andExpect(jsonPath("$.updateInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.updateInfo.timestamp").isNumber)
        .andExpect(jsonPath("$.updateInfo.timestamp").value(greaterThan(0.toLong())))
        .andExpect(jsonPath("$.tags").value(mutableListOf("tag_part1", "tag_part3")))
        .andExpect(jsonPath("$.type").value(DatasetPartTypeEnum.File.value))
        .andExpect(jsonPath("$.organizationId").value(organizationId))
        .andExpect(jsonPath("$.workspaceId").value(workspaceId))
        .andExpect(jsonPath("$.datasetId").value(datasetId))
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/parts/POST"))
  }

  @Test
  @WithMockOauth2User
  fun delete_dataset_part() {

    val datasetId =
        createDatasetAndReturnId(mvc, organizationId, workspaceId, constructDatasetCreateRequest())

    val datasetPartId =
        createDatasetPartAndReturnId(
            mvc, organizationId, workspaceId, datasetId, constructDatasetPartCreateRequest())

    mvc.perform(
            delete(
                    "/organizations/$organizationId/workspaces/$workspaceId/datasets/$datasetId/parts/$datasetPartId")
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/parts/{dataset_part_id}/DELETE"))
  }

  @Test
  @WithMockOauth2User
  fun get_dataset_part() {

    val datasetId =
        createDatasetAndReturnId(mvc, organizationId, workspaceId, constructDatasetCreateRequest())

    val datasetPartId =
        createDatasetPartAndReturnId(
            mvc, organizationId, workspaceId, datasetId, constructDatasetPartCreateRequest())

    mvc.perform(
            get(
                    "/organizations/$organizationId/workspaces/$workspaceId/datasets/$datasetId/parts/$datasetPartId")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.id").value(datasetPartId))
        .andExpect(jsonPath("$.name").value(DATASET_PART_NAME))
        .andExpect(jsonPath("$.description").value(DATASET_PART_DESCRIPTION))
        .andExpect(jsonPath("$.sourceName").value(TEST_FILE_NAME))
        .andExpect(jsonPath("$.createInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.createInfo.timestamp").isNumber)
        .andExpect(jsonPath("$.createInfo.timestamp").value(greaterThan(0.toLong())))
        .andExpect(jsonPath("$.updateInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.updateInfo.timestamp").isNumber)
        .andExpect(jsonPath("$.updateInfo.timestamp").value(greaterThan(0.toLong())))
        .andExpect(jsonPath("$.tags").value(mutableListOf("tag_part1", "tag_part3")))
        .andExpect(jsonPath("$.type").value(DatasetPartTypeEnum.File.value))
        .andExpect(jsonPath("$.organizationId").value(organizationId))
        .andExpect(jsonPath("$.workspaceId").value(workspaceId))
        .andExpect(jsonPath("$.datasetId").value(datasetId))
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/parts/{dataset_part_id}/GET"))
  }

  @Test
  @WithMockOauth2User
  fun list_dataset_parts() {

    val datasetId =
        createDatasetAndReturnId(mvc, organizationId, workspaceId, constructDatasetCreateRequest())

    val datasetPartId =
        createDatasetPartAndReturnId(
            mvc, organizationId, workspaceId, datasetId, constructDatasetPartCreateRequest())
    mvc.perform(
            get("/organizations/$organizationId/workspaces/$workspaceId/datasets/$datasetId/parts")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$[0].name").value(DATASET_PART_NAME))
        .andExpect(jsonPath("$[0].description").value(DATASET_PART_DESCRIPTION))
        .andExpect(jsonPath("$[0].sourceName").value(TEST_FILE_NAME))
        .andExpect(jsonPath("$[0].createInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$[0].createInfo.timestamp").isNumber)
        .andExpect(jsonPath("$[0].createInfo.timestamp").value(greaterThan(0.toLong())))
        .andExpect(jsonPath("$[0].updateInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$[0].updateInfo.timestamp").isNumber)
        .andExpect(jsonPath("$[0].updateInfo.timestamp").value(greaterThan(0.toLong())))
        .andExpect(jsonPath("$[0].tags").value(mutableListOf("tag_part1", "tag_part2")))
        .andExpect(jsonPath("$[0].type").value(DatasetPartTypeEnum.File.value))
        .andExpect(jsonPath("$[0].organizationId").value(organizationId))
        .andExpect(jsonPath("$[0].workspaceId").value(workspaceId))
        .andExpect(jsonPath("$[0].datasetId").value(datasetId))
        .andExpect(jsonPath("$[1].id").value(datasetPartId))
        .andExpect(jsonPath("$[1].name").value(DATASET_PART_NAME))
        .andExpect(jsonPath("$[1].description").value(DATASET_PART_DESCRIPTION))
        .andExpect(jsonPath("$[1].sourceName").value(TEST_FILE_NAME))
        .andExpect(jsonPath("$[1].createInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$[1].createInfo.timestamp").isNumber)
        .andExpect(jsonPath("$[1].createInfo.timestamp").value(greaterThan(0.toLong())))
        .andExpect(jsonPath("$[1].updateInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$[1].updateInfo.timestamp").isNumber)
        .andExpect(jsonPath("$[1].updateInfo.timestamp").value(greaterThan(0.toLong())))
        .andExpect(jsonPath("$[1].tags").value(mutableListOf("tag_part1", "tag_part3")))
        .andExpect(jsonPath("$[1].type").value(DatasetPartTypeEnum.File.value))
        .andExpect(jsonPath("$[1].organizationId").value(organizationId))
        .andExpect(jsonPath("$[1].workspaceId").value(workspaceId))
        .andExpect(jsonPath("$[1].datasetId").value(datasetId))
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/parts/GET"))
  }

  @Test
  @WithMockOauth2User
  fun search_dataset_parts() {

    val datasetId =
        createDatasetAndReturnId(mvc, organizationId, workspaceId, constructDatasetCreateRequest())

    val datasetPartId =
        createDatasetPartAndReturnId(
            mvc, organizationId, workspaceId, datasetId, constructDatasetPartCreateRequest())

    mvc.perform(
            post(
                    "/organizations/$organizationId/workspaces/$workspaceId/datasets/$datasetId/parts/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSONArray(listOf("tag_part3")).toString())
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andDo(MockMvcResultHandlers.print())
        .andExpect(jsonPath("$[0].id").value(datasetPartId))
        .andExpect(jsonPath("$[0].name").value(DATASET_PART_NAME))
        .andExpect(jsonPath("$[0].description").value(DATASET_PART_DESCRIPTION))
        .andExpect(jsonPath("$[0].organizationId").value(organizationId))
        .andExpect(jsonPath("$[0].workspaceId").value(workspaceId))
        .andExpect(jsonPath("$[0].sourceName").value(TEST_FILE_NAME))
        .andExpect(jsonPath("$[0].tags").value(mutableListOf("tag_part1", "tag_part3")))
        .andExpect(jsonPath("$[0].createInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$[0].createInfo.timestamp").isNumber)
        .andExpect(jsonPath("$[0].createInfo.timestamp").value(greaterThan(0.toLong())))
        .andExpect(jsonPath("$[0].updateInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$[0].updateInfo.timestamp").isNumber)
        .andExpect(jsonPath("$[0].updateInfo.timestamp").value(greaterThan(0.toLong())))
        .andDo(
            document(
                "organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/parts/search/POST"))
  }

  @Test
  @WithMockOauth2User
  fun list_dataset_users() {

    val datasetId =
        createDatasetAndReturnId(
            mvc,
            organizationId,
            workspaceId,
            constructDatasetCreateRequest(
                security =
                    DatasetSecurity(
                        default = ROLE_NONE,
                        accessControlList =
                            mutableListOf(
                                DatasetAccessControl(id = PLATFORM_ADMIN_EMAIL, role = ROLE_ADMIN),
                                DatasetAccessControl(
                                    id = ORGANIZATION_USER_EMAIL, role = ROLE_EDITOR)))))

    mvc.perform(
            get(
                    "/organizations/$organizationId/workspaces/$workspaceId/datasets/$datasetId/security/users")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful)
        .andDo(MockMvcResultHandlers.print())
        .andExpect(
            jsonPath("$").value(mutableListOf(PLATFORM_ADMIN_EMAIL, ORGANIZATION_USER_EMAIL)))
        .andDo(
            document(
                "organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/security/users/GET"))
  }

  @Test
  @WithMockOauth2User
  fun list_datasets() {

    val datasetId =
        createDatasetAndReturnId(mvc, organizationId, workspaceId, constructDatasetCreateRequest())

    mvc.perform(
            get("/organizations/$organizationId/workspaces/$workspaceId/datasets")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful)
        .andDo(MockMvcResultHandlers.print())
        .andExpect(jsonPath("$[0].id").value(datasetId))
        .andExpect(jsonPath("$[0].name").value(DATASET_NAME))
        .andExpect(jsonPath("$[0].description").value(DATASET_DESCRIPTION))
        .andExpect(jsonPath("$[0].organizationId").value(organizationId))
        .andExpect(jsonPath("$[0].workspaceId").value(workspaceId))
        .andExpect(jsonPath("$[0].createInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$[0].createInfo.timestamp").isNumber)
        .andExpect(jsonPath("$[0].createInfo.timestamp").value(greaterThan(0.toLong())))
        .andExpect(jsonPath("$[0].updateInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$[0].updateInfo.timestamp").isNumber)
        .andExpect(jsonPath("$[0].updateInfo.timestamp").value(greaterThan(0.toLong())))
        .andExpect(jsonPath("$[0].parts[0].name").value(DATASET_PART_NAME))
        .andExpect(jsonPath("$[0].parts[0].description").value(DATASET_PART_DESCRIPTION))
        .andExpect(jsonPath("$[0].parts[0].sourceName").value(TEST_FILE_NAME))
        .andExpect(jsonPath("$[0].parts[0].organizationId").value(organizationId))
        .andExpect(jsonPath("$[0].parts[0].workspaceId").value(workspaceId))
        .andExpect(jsonPath("$[0].parts[0].tags").value(mutableListOf("tag_part1", "tag_part2")))
        .andExpect(jsonPath("$[0].parts[0].type").value(DatasetPartTypeEnum.File.value))
        .andExpect(jsonPath("$[0].parts[0].createInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$[0].parts[0].createInfo.timestamp").isNumber)
        .andExpect(jsonPath("$[0].parts[0].createInfo.timestamp").value(greaterThan(0.toLong())))
        .andExpect(jsonPath("$[0].parts[0].updateInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$[0].parts[0].updateInfo.timestamp").isNumber)
        .andExpect(jsonPath("$[0].parts[0].updateInfo.timestamp").value(greaterThan(0.toLong())))
        .andExpect(jsonPath("$[0].tags").value(mutableListOf("tag1", "tag2")))
        .andExpect(jsonPath("$[0].security.default").value(ROLE_NONE))
        .andExpect(jsonPath("$[0].security.accessControlList[0].role").value(ROLE_ADMIN))
        .andExpect(jsonPath("$[0].security.accessControlList[0].id").value(PLATFORM_ADMIN_EMAIL))
        .andDo(document("organizations/{organization_id}/workspaces/{workspace_id}/datasets/GET"))
  }

  @Test
  @WithMockOauth2User
  fun search_datasets() {

    val datasetId =
        createDatasetAndReturnId(mvc, organizationId, workspaceId, constructDatasetCreateRequest())

    mvc.perform(
            post("/organizations/$organizationId/workspaces/$workspaceId/datasets/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSONArray(listOf("tag1")).toString())
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andDo(MockMvcResultHandlers.print())
        .andExpect(jsonPath("$[0].id").value(datasetId))
        .andExpect(jsonPath("$[0].name").value(DATASET_NAME))
        .andExpect(jsonPath("$[0].description").value(DATASET_DESCRIPTION))
        .andExpect(jsonPath("$[0].organizationId").value(organizationId))
        .andExpect(jsonPath("$[0].workspaceId").value(workspaceId))
        .andExpect(jsonPath("$[0].createInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$[0].createInfo.timestamp").isNumber)
        .andExpect(jsonPath("$[0].createInfo.timestamp").value(greaterThan(0.toLong())))
        .andExpect(jsonPath("$[0].updateInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$[0].updateInfo.timestamp").isNumber)
        .andExpect(jsonPath("$[0].updateInfo.timestamp").value(greaterThan(0.toLong())))
        .andExpect(jsonPath("$[0].parts[0].name").value(DATASET_PART_NAME))
        .andExpect(jsonPath("$[0].parts[0].description").value(DATASET_PART_DESCRIPTION))
        .andExpect(jsonPath("$[0].parts[0].sourceName").value(TEST_FILE_NAME))
        .andExpect(jsonPath("$[0].parts[0].organizationId").value(organizationId))
        .andExpect(jsonPath("$[0].parts[0].workspaceId").value(workspaceId))
        .andExpect(jsonPath("$[0].parts[0].tags").value(mutableListOf("tag_part1", "tag_part2")))
        .andExpect(jsonPath("$[0].parts[0].type").value(DatasetPartTypeEnum.File.value))
        .andExpect(jsonPath("$[0].parts[0].createInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$[0].parts[0].createInfo.timestamp").isNumber)
        .andExpect(jsonPath("$[0].parts[0].createInfo.timestamp").value(greaterThan(0.toLong())))
        .andExpect(jsonPath("$[0].parts[0].updateInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$[0].parts[0].updateInfo.timestamp").isNumber)
        .andExpect(jsonPath("$[0].parts[0].updateInfo.timestamp").value(greaterThan(0.toLong())))
        .andExpect(jsonPath("$[0].tags").value(mutableListOf("tag1", "tag2")))
        .andExpect(jsonPath("$[0].security.default").value(ROLE_NONE))
        .andExpect(jsonPath("$[0].security.accessControlList[0].role").value(ROLE_ADMIN))
        .andExpect(jsonPath("$[0].security.accessControlList[0].id").value(PLATFORM_ADMIN_EMAIL))
        .andDo(
            document(
                "organizations/{organization_id}/workspaces/{workspace_id}/datasets/search/POST"))
  }

  @Ignore("This method is not ready yet")
  @Test
  @WithMockOauth2User
  fun query_data() {
    TODO("Not yet implemented")
  }

  @Test
  @WithMockOauth2User
  fun replace_dataset_part() {

    val datasetId =
        createDatasetAndReturnId(mvc, organizationId, workspaceId, constructDatasetCreateRequest())

    val datasetPartId =
        createDatasetPartAndReturnId(
            mvc, organizationId, workspaceId, datasetId, constructDatasetPartCreateRequest())

    val newDescription = "this_a_new_description_for_dataset_part"
    val newTags = mutableListOf("tag_part1_updated", "tag_part2_updated")

    val datasetPartUpdateRequest =
        MockMultipartFile(
            "datasetPartUpdateRequest",
            null,
            MediaType.APPLICATION_JSON_VALUE,
            JSONObject(DatasetPartUpdateRequest(description = newDescription, tags = newTags))
                .toString()
                .byteInputStream())
    val newFile =
        MockMultipartFile(
            "file", "test.csv", MediaType.MULTIPART_FORM_DATA_VALUE, InputStream.nullInputStream())

    mvc.perform(
            multipart(
                    "/organizations/$organizationId/workspaces/$workspaceId/datasets/$datasetId/parts/$datasetPartId")
                .file(datasetPartUpdateRequest)
                .file(newFile)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf())
                // By default, behind multipart, the HTTP verb used is POST
                // We can override the HTTP verb as following
                // https://stackoverflow.com/questions/38571716/how-to-put-multipart-form-data-using-spring-mockmvc
                .with { request ->
                  request.method = "PUT"
                  request
                })
        .andExpect(status().is2xxSuccessful)
        .andDo(MockMvcResultHandlers.print())
        .andExpect(jsonPath("$.id").value(datasetPartId))
        .andExpect(jsonPath("$.name").value(DATASET_PART_NAME))
        .andExpect(jsonPath("$.sourceName").value(TEST_FILE_NAME))
        .andExpect(jsonPath("$.description").value(newDescription))
        .andExpect(jsonPath("$.createInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.createInfo.timestamp").isNumber)
        .andExpect(jsonPath("$.createInfo.timestamp").value(greaterThan(0.toLong())))
        .andExpect(jsonPath("$.updateInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.updateInfo.timestamp").isNumber)
        .andExpect(jsonPath("$.updateInfo.timestamp").value(greaterThan(0.toLong())))
        .andExpect(jsonPath("$.tags").value(newTags))
        .andExpect(jsonPath("$.type").value(DatasetPartTypeEnum.File.value))
        .andExpect(jsonPath("$.organizationId").value(organizationId))
        .andExpect(jsonPath("$.workspaceId").value(workspaceId))
        .andExpect(jsonPath("$.datasetId").value(datasetId))
        .andDo(
            document(
                "organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/parts/{dataset_part_id}/PUT"))
  }

  @Test
  @WithMockOauth2User
  fun update_dataset() {
    val datasetId =
        createDatasetAndReturnId(
            mvc,
            organizationId,
            workspaceId,
            constructDatasetCreateRequest(
                security =
                    DatasetSecurity(
                        default = ROLE_NONE,
                        accessControlList =
                            mutableListOf(
                                DatasetAccessControl(id = PLATFORM_ADMIN_EMAIL, role = ROLE_ADMIN),
                                DatasetAccessControl(
                                    id = ORGANIZATION_USER_EMAIL, role = ROLE_EDITOR)))))

    val newName = "this_a_new_name_for_dataset"
    val newDescription = "this_a_new_description_for_dataset"
    val newTags = mutableListOf("tag1_updated", "tag2_updated")

    val newPartName = "this_a_new_name_for_dataset_part"
    val newPartDescription = "this_a_new_description_for_dataset_part"
    val newPartTags = mutableListOf("tag1_part_updated", "tag2_part_updated")

    val fileToUpload =
        this::class.java.getResourceAsStream("/dataset/$TEST_FILE_NAME")
            ?: throw IllegalStateException(
                "$TEST_FILE_NAME file used for organizations/{organization_id}/workspaces/{workspace_id}/datasets/POST endpoint documentation cannot be null")

    val datasetUpdateRequest =
        DatasetUpdateRequest(
            name = newName,
            description = newDescription,
            tags = newTags,
            parts =
                mutableListOf(
                    DatasetPartCreateRequest(
                        name = newPartName,
                        sourceName = TEST_FILE_NAME,
                        description = newPartDescription,
                        tags = newPartTags,
                        type = DatasetPartTypeEnum.File)),
            security =
                DatasetSecurity(
                    default = ROLE_NONE,
                    accessControlList =
                        mutableListOf(
                            DatasetAccessControl(id = PLATFORM_ADMIN_EMAIL, role = ROLE_ADMIN),
                            DatasetAccessControl(
                                id = ORGANIZATION_USER_EMAIL, role = ROLE_VIEWER))))

    val datasetUpdateRequestMultipartFile =
        MockMultipartFile(
            "datasetUpdateRequest",
            null,
            MediaType.APPLICATION_JSON_VALUE,
            JSONObject(datasetUpdateRequest).toString().byteInputStream())
    val newFile =
        MockMultipartFile(
            "files",
            TEST_FILE_NAME,
            MediaType.MULTIPART_FORM_DATA_VALUE,
            IOUtils.toByteArray(fileToUpload))

    mvc.perform(
            multipart("/organizations/$organizationId/workspaces/$workspaceId/datasets/$datasetId")
                .file(datasetUpdateRequestMultipartFile)
                .file(newFile)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf())
                // By default, behind multipart, the HTTP verb used is POST
                // We can override the HTTP verb as following
                // https://stackoverflow.com/questions/38571716/how-to-put-multipart-form-data-using-spring-mockmvc
                .with { request ->
                  request.method = "PATCH"
                  request
                })
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.name").value(newName))
        .andExpect(jsonPath("$.description").value(newDescription))
        .andExpect(jsonPath("$.organizationId").value(organizationId))
        .andExpect(jsonPath("$.workspaceId").value(workspaceId))
        .andExpect(jsonPath("$.createInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.createInfo.timestamp").isNumber)
        .andExpect(jsonPath("$.createInfo.timestamp").value(greaterThan(0.toLong())))
        .andExpect(jsonPath("$.updateInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.updateInfo.timestamp").isNumber)
        .andExpect(jsonPath("$.updateInfo.timestamp").value(greaterThan(0.toLong())))
        .andExpect(jsonPath("$.parts[0].name").value(newPartName))
        .andExpect(jsonPath("$.parts[0].description").value(newPartDescription))
        .andExpect(jsonPath("$.parts[0].organizationId").value(organizationId))
        .andExpect(jsonPath("$.parts[0].workspaceId").value(workspaceId))
        .andExpect(jsonPath("$.parts[0].sourceName").value(TEST_FILE_NAME))
        .andExpect(jsonPath("$.parts[0].tags").value(newPartTags))
        .andExpect(jsonPath("$.parts[0].type").value(DatasetPartTypeEnum.File.value))
        .andExpect(jsonPath("$.parts[0].createInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.parts[0].createInfo.timestamp").isNumber)
        .andExpect(jsonPath("$.parts[0].createInfo.timestamp").value(greaterThan(0.toLong())))
        .andExpect(jsonPath("$.parts[0].updateInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.parts[0].updateInfo.timestamp").isNumber)
        .andExpect(jsonPath("$.parts[0].updateInfo.timestamp").value(greaterThan(0.toLong())))
        .andExpect(jsonPath("$.tags").value(newTags))
        .andExpect(jsonPath("$.security.default").value(ROLE_NONE))
        .andExpect(jsonPath("$.security.accessControlList[0].role").value(ROLE_ADMIN))
        .andExpect(jsonPath("$.security.accessControlList[0].id").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.security.accessControlList[1].role").value(ROLE_VIEWER))
        .andExpect(jsonPath("$.security.accessControlList[1].id").value(ORGANIZATION_USER_EMAIL))
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/PATCH"))
  }

  @Test
  @WithMockOauth2User
  fun update_dataset_access_control() {

    val datasetId =
        createDatasetAndReturnId(
            mvc,
            organizationId,
            workspaceId,
            constructDatasetCreateRequest(
                security =
                    DatasetSecurity(
                        default = ROLE_NONE,
                        accessControlList =
                            mutableListOf(
                                DatasetAccessControl(id = PLATFORM_ADMIN_EMAIL, role = ROLE_ADMIN),
                                DatasetAccessControl(
                                    id = ORGANIZATION_USER_EMAIL, role = ROLE_EDITOR)))))

    mvc.perform(
            patch(
                    "/organizations/$organizationId/workspaces/$workspaceId/datasets/$datasetId/security/access/$ORGANIZATION_USER_EMAIL")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSONObject(DatasetRole(role = ROLE_ADMIN)).toString())
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.role").value(ROLE_ADMIN))
        .andExpect(jsonPath("$.id").value(ORGANIZATION_USER_EMAIL))
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/security/access/{identity_id}/PATCH"))
  }

  @Test
  @WithMockOauth2User
  fun update_dataset_default_security() {

    val datasetId =
        createDatasetAndReturnId(mvc, organizationId, workspaceId, constructDatasetCreateRequest())

    mvc.perform(
            patch(
                    "/organizations/$organizationId/workspaces/$workspaceId/datasets/$datasetId/security/default")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSONObject(DatasetRole(role = ROLE_VIEWER)).toString())
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.default").value(ROLE_VIEWER))
        .andExpect(jsonPath("$.accessControlList[0].id").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.accessControlList[0].role").value(ROLE_ADMIN))
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/workspaces/{workspace_id}/datasets/{dataset_id}/security/default/PATCH"))
  }
}
