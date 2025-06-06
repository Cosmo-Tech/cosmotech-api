// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.home.workspace

import com.cosmotech.api.home.Constants.PLATFORM_ADMIN_EMAIL
import com.cosmotech.api.home.ControllerTestBase
import com.cosmotech.api.home.ControllerTestUtils.OrganizationUtils.constructOrganizationCreateRequest
import com.cosmotech.api.home.ControllerTestUtils.OrganizationUtils.createOrganizationAndReturnId
import com.cosmotech.api.home.ControllerTestUtils.SolutionUtils.constructSolutionCreateRequest
import com.cosmotech.api.home.ControllerTestUtils.SolutionUtils.createSolutionAndReturnId
import com.cosmotech.api.home.ControllerTestUtils.WorkspaceUtils.constructWorkspaceCreateRequest
import com.cosmotech.api.home.ControllerTestUtils.WorkspaceUtils.constructWorkspaceUpdateRequest
import com.cosmotech.api.home.ControllerTestUtils.WorkspaceUtils.createWorkspaceAndReturnId
import com.cosmotech.api.home.annotations.WithMockOauth2User
import com.cosmotech.api.home.organization.OrganizationConstants.NEW_USER_ID
import com.cosmotech.api.home.organization.OrganizationConstants.NEW_USER_ROLE
import com.cosmotech.api.home.workspace.WorkspaceConstants.WORKSPACE_KEY
import com.cosmotech.api.home.workspace.WorkspaceConstants.WORKSPACE_NAME
import com.cosmotech.api.rbac.ROLE_ADMIN
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.rbac.ROLE_VIEWER
import com.cosmotech.workspace.domain.WorkspaceAccessControl
import com.cosmotech.workspace.domain.WorkspaceSecurity
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
import org.testcontainers.shaded.org.apache.commons.io.IOUtils

@ActiveProfiles(profiles = ["test"])
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WorkspaceControllerTests : ControllerTestBase() {

  private val logger = LoggerFactory.getLogger(WorkspaceControllerTests::class.java)

  private lateinit var organizationId: String
  private lateinit var solutionId: String

  @BeforeEach
  fun beforeEach() {
    organizationId = createOrganizationAndReturnId(mvc, constructOrganizationCreateRequest())
    solutionId = createSolutionAndReturnId(mvc, organizationId, constructSolutionCreateRequest())
  }

  @Test
  @WithMockOauth2User
  fun create_workspace() {

    val description = "here_is_workspace_description"
    val url = "https://portal.cosmotech.com/"
    val version = "1.0.0"
    val datasetCopy = false
    val runTemplateFilter = mutableListOf("runtemplateId1,runtemplateId2")
    val tags = mutableListOf("tag1,tag2")
    val defaultRunTemplateDataset =
        mutableMapOf<String, Any>(
            "runtemplateId1" to "datasetId1", "runtemplateId2" to "datasetId2")
    val iframes =
        mutableMapOf(
            "you_can_put" to "whatever_you_want_here",
            "even" to JSONObject(mapOf("object" to "if_you_want")))
    val options =
        mutableMapOf(
            "you_can_put" to "whatever_you_want_here",
            "even" to JSONObject(mapOf("object" to "if_you_want")))
    val workspaceSecurity =
        WorkspaceSecurity(
            default = ROLE_NONE,
            accessControlList =
                mutableListOf(
                    WorkspaceAccessControl(id = PLATFORM_ADMIN_EMAIL, role = ROLE_ADMIN),
                    WorkspaceAccessControl(id = NEW_USER_ID, role = NEW_USER_ROLE)))

    mvc.perform(
            post("/organizations/$organizationId/workspaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    JSONObject(
                            constructWorkspaceCreateRequest(
                                WORKSPACE_KEY,
                                WORKSPACE_NAME,
                                solutionId,
                                description,
                                version,
                                runTemplateFilter,
                                defaultRunTemplateDataset,
                                datasetCopy,
                                workspaceSecurity,
                                url,
                                iframes,
                                options,
                                tags))
                        .toString())
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.name").value(WORKSPACE_NAME))
        .andExpect(jsonPath("$.key").value(WORKSPACE_KEY))
        .andExpect(jsonPath("$.createInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.updateInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.description").value(description))
        .andExpect(jsonPath("$.version").value(version))
        .andExpect(jsonPath("$.solution.runTemplateFilter").value(runTemplateFilter))
        .andExpect(
            jsonPath("$.solution.defaultRunTemplateDataset").value(defaultRunTemplateDataset))
        .andExpect(jsonPath("$.datasetCopy").value(datasetCopy))
        .andExpect(jsonPath("$.tags").value(tags))
        .andExpect(jsonPath("$.webApp.url").value(url))
        .andExpect(jsonPath("$.organizationId").value(organizationId))
        .andExpect(jsonPath("$.solution.solutionId").value(solutionId))
        .andExpect(jsonPath("$.security.default").value(ROLE_NONE))
        .andExpect(jsonPath("$.security.accessControlList[0].role").value(ROLE_ADMIN))
        .andExpect(jsonPath("$.security.accessControlList[0].id").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.security.accessControlList[1].role").value(NEW_USER_ROLE))
        .andExpect(jsonPath("$.security.accessControlList[1].id").value(NEW_USER_ID))
        .andDo(MockMvcResultHandlers.print())
        .andDo(document("organizations/{organization_id}/workspaces/POST"))
  }

  @Test
  @WithMockOauth2User
  fun update_workspace() {

    val workspaceId =
        createWorkspaceAndReturnId(mvc, organizationId, WORKSPACE_KEY, WORKSPACE_NAME, solutionId)

    val description = "here_is_workspace_description"
    val url = "https://portal.cosmotech.com/"
    val datasetCopy = false
    val runTemplateFilter = mutableListOf("runtemplateId1,runtemplateId2")
    val tags = mutableListOf("tag1,tag2")
    val defaultRunTemplateDataset =
        mutableMapOf<String, Any>(
            "runtemplateId1" to "datasetId1", "runtemplateId2" to "datasetId2")
    val iframes =
        mutableMapOf(
            "you_can_put" to "whatever_you_want_here",
            "even" to JSONObject(mapOf("object" to "if_you_want")))
    val options =
        mutableMapOf(
            "you_can_put" to "whatever_you_want_here",
            "even" to JSONObject(mapOf("object" to "if_you_want")))

    mvc.perform(
            patch("/organizations/$organizationId/workspaces/$workspaceId")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    JSONObject(
                            constructWorkspaceUpdateRequest(
                                WORKSPACE_KEY,
                                WORKSPACE_NAME,
                                solutionId,
                                description,
                                runTemplateFilter,
                                defaultRunTemplateDataset,
                                datasetCopy,
                                url,
                                iframes,
                                options,
                                tags))
                        .toString())
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.name").value(WORKSPACE_NAME))
        .andExpect(jsonPath("$.key").value(WORKSPACE_KEY))
        .andExpect(jsonPath("$.createInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.updateInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.description").value(description))
        .andExpect(jsonPath("$.solution.runTemplateFilter").value(runTemplateFilter))
        .andExpect(
            jsonPath("$.solution.defaultRunTemplateDataset").value(defaultRunTemplateDataset))
        .andExpect(jsonPath("$.datasetCopy").value(datasetCopy))
        .andExpect(jsonPath("$.tags").value(tags))
        .andExpect(jsonPath("$.webApp.url").value(url))
        .andExpect(jsonPath("$.organizationId").value(organizationId))
        .andExpect(jsonPath("$.solution.solutionId").value(solutionId))
        .andExpect(jsonPath("$.security.default").value(ROLE_NONE))
        .andExpect(jsonPath("$.security.accessControlList[0].role").value(ROLE_ADMIN))
        .andExpect(jsonPath("$.security.accessControlList[0].id").value(PLATFORM_ADMIN_EMAIL))
        .andDo(MockMvcResultHandlers.print())
        .andDo(document("organizations/{organization_id}/workspaces/{workspace_id}/PATCH"))
  }

  @Test
  @WithMockOauth2User
  fun list_workspaces() {

    val firstWorkspaceKey = "first_workspace_key"
    val firstWorkspaceName = "first_workspace_name"
    val firstWorkspaceId =
        createWorkspaceAndReturnId(
            mvc,
            organizationId,
            constructWorkspaceCreateRequest(
                key = firstWorkspaceKey,
                name = firstWorkspaceName,
                solutionId = solutionId,
            ))
    val secondWorkspaceKey = "second_workspace_key"
    val secondWorkspaceName = "second_workspace_name"
    val secondWorkspaceId =
        createWorkspaceAndReturnId(
            mvc,
            organizationId,
            constructWorkspaceCreateRequest(
                key = secondWorkspaceKey,
                name = secondWorkspaceName,
                solutionId = solutionId,
            ))

    mvc.perform(
            get("/organizations/$organizationId/workspaces")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$[0].id").value(firstWorkspaceId))
        .andExpect(jsonPath("$[0].key").value(firstWorkspaceKey))
        .andExpect(jsonPath("$[0].name").value(firstWorkspaceName))
        .andExpect(jsonPath("$[0].createInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$[0].updateInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$[0].organizationId").value(organizationId))
        .andExpect(jsonPath("$[0].solution.solutionId").value(solutionId))
        .andExpect(jsonPath("$[0].security.default").value(ROLE_NONE))
        .andExpect(jsonPath("$[0].security.accessControlList[0].role").value(ROLE_ADMIN))
        .andExpect(jsonPath("$[0].security.accessControlList[0].id").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$[1].id").value(secondWorkspaceId))
        .andExpect(jsonPath("$[1].key").value(secondWorkspaceKey))
        .andExpect(jsonPath("$[1].name").value(secondWorkspaceName))
        .andExpect(jsonPath("$[1].createInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$[1].updateInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$[1].organizationId").value(organizationId))
        .andExpect(jsonPath("$[1].solution.solutionId").value(solutionId))
        .andExpect(jsonPath("$[1].security.default").value(ROLE_NONE))
        .andExpect(jsonPath("$[1].security.accessControlList[0].role").value(ROLE_ADMIN))
        .andExpect(jsonPath("$[1].security.accessControlList[0].id").value(PLATFORM_ADMIN_EMAIL))
        .andDo(MockMvcResultHandlers.print())
        .andDo(document("organizations/{organization_id}/workspaces/GET"))
  }

  @Test
  @WithMockOauth2User
  fun get_workspace() {

    val description = "here_is_workspace_description"
    val url = "https://portal.cosmotech.com/"
    val version = "1.0.0"
    val datasetCopy = false
    val runTemplateFilter = mutableListOf("runtemplateId1,runtemplateId2")
    val tags = mutableListOf("tag1,tag2")
    val defaultRunTemplateDataset =
        mutableMapOf<String, Any>(
            "runtemplateId1" to "datasetId1", "runtemplateId2" to "datasetId2")
    val iframes =
        mutableMapOf(
            "you_can_put" to "whatever_you_want_here",
            "even" to JSONObject(mapOf("object" to "if_you_want")))
    val options =
        mutableMapOf(
            "you_can_put" to "whatever_you_want_here",
            "even" to JSONObject(mapOf("object" to "if_you_want")))
    val workspaceSecurity =
        WorkspaceSecurity(
            default = ROLE_NONE,
            accessControlList =
                mutableListOf(
                    WorkspaceAccessControl(id = PLATFORM_ADMIN_EMAIL, role = ROLE_ADMIN),
                    WorkspaceAccessControl(id = NEW_USER_ID, role = NEW_USER_ROLE)))
    val workspaceId =
        createWorkspaceAndReturnId(
            mvc,
            organizationId,
            constructWorkspaceCreateRequest(
                WORKSPACE_KEY,
                WORKSPACE_NAME,
                solutionId,
                description,
                version,
                runTemplateFilter,
                defaultRunTemplateDataset,
                datasetCopy,
                workspaceSecurity,
                url,
                iframes,
                options,
                tags))

    mvc.perform(
            get("/organizations/$organizationId/workspaces/$workspaceId")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.name").value(WORKSPACE_NAME))
        .andExpect(jsonPath("$.key").value(WORKSPACE_KEY))
        .andExpect(jsonPath("$.createInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.updateInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.version").value(version))
        .andExpect(jsonPath("$.description").value(description))
        .andExpect(jsonPath("$.solution.runTemplateFilter").value(runTemplateFilter))
        .andExpect(
            jsonPath("$.solution.defaultRunTemplateDataset").value(defaultRunTemplateDataset))
        .andExpect(jsonPath("$.datasetCopy").value(datasetCopy))
        .andExpect(jsonPath("$.tags").value(tags))
        .andExpect(jsonPath("$.webApp.url").value(url))
        .andExpect(jsonPath("$.organizationId").value(organizationId))
        .andExpect(jsonPath("$.solution.solutionId").value(solutionId))
        .andExpect(jsonPath("$.security.default").value(ROLE_NONE))
        .andExpect(jsonPath("$.security.accessControlList[0].role").value(ROLE_ADMIN))
        .andExpect(jsonPath("$.security.accessControlList[0].id").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.security.accessControlList[1].role").value(NEW_USER_ROLE))
        .andExpect(jsonPath("$.security.accessControlList[1].id").value(NEW_USER_ID))
        .andDo(MockMvcResultHandlers.print())
        .andDo(document("organizations/{organization_id}/workspaces/{workspace_id}/GET"))
  }

  @Test
  @WithMockOauth2User
  fun delete_workspace() {

    val workspaceId =
        createWorkspaceAndReturnId(mvc, organizationId, WORKSPACE_KEY, WORKSPACE_NAME, solutionId)

    mvc.perform(
            delete("/organizations/$organizationId/workspaces/$workspaceId")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andDo(MockMvcResultHandlers.print())
        .andDo(document("organizations/{organization_id}/workspaces/{workspace_id}/DELETE"))
  }

  @Test
  @WithMockOauth2User
  fun get_workspace_security() {

    val workspaceId =
        createWorkspaceAndReturnId(
            mvc, organizationId, constructWorkspaceCreateRequest(solutionId = solutionId))

    mvc.perform(get("/organizations/$organizationId/workspaces/$workspaceId/security"))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.default").value(ROLE_NONE))
        .andExpect(jsonPath("$.accessControlList[0].role").value(ROLE_ADMIN))
        .andExpect(jsonPath("$.accessControlList[0].id").value(PLATFORM_ADMIN_EMAIL))
        .andDo(MockMvcResultHandlers.print())
        .andDo(document("organizations/{organization_id}/workspaces/{workspace_id}/security/GET"))
  }

  @Test
  @WithMockOauth2User
  fun add_workspace_security_access() {

    val workspaceId =
        createWorkspaceAndReturnId(
            mvc, organizationId, constructWorkspaceCreateRequest(solutionId = solutionId))

    mvc.perform(
            post("/organizations/$organizationId/workspaces/$workspaceId/security/access")
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
                "organizations/{organization_id}/workspaces/{workspace_id}/security/access/POST"))
  }

  @Test
  @WithMockOauth2User
  fun get_workspace_security_access() {

    val workspaceId =
        createWorkspaceAndReturnId(
            mvc, organizationId, constructWorkspaceCreateRequest(solutionId = solutionId))

    mvc.perform(
            get(
                "/organizations/$organizationId/workspaces/$workspaceId/security/access/$PLATFORM_ADMIN_EMAIL"))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.role").value(ROLE_ADMIN))
        .andExpect(jsonPath("$.id").value(PLATFORM_ADMIN_EMAIL))
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/workspaces/{workspace_id}/security/access/{identity_id}/GET"))
  }

  @Test
  @WithMockOauth2User
  fun update_workspace_security_access() {

    val workspaceSecurity =
        WorkspaceSecurity(
            default = ROLE_NONE,
            accessControlList =
                mutableListOf(
                    WorkspaceAccessControl(id = PLATFORM_ADMIN_EMAIL, role = ROLE_ADMIN),
                    WorkspaceAccessControl(id = NEW_USER_ID, role = NEW_USER_ROLE)))
    val workspaceId =
        createWorkspaceAndReturnId(
            mvc,
            organizationId,
            constructWorkspaceCreateRequest(
                key = WORKSPACE_KEY,
                name = WORKSPACE_NAME,
                solutionId = solutionId,
                security = workspaceSecurity))

    mvc.perform(
            patch(
                    "/organizations/$organizationId/workspaces/$workspaceId/security/access/$NEW_USER_ID")
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
                "organizations/{organization_id}/workspaces/{workspace_id}/security/access/{identity_id}/PATCH"))
  }

  @Test
  @WithMockOauth2User
  fun delete_workspace_security_access() {

    val workspaceSecurity =
        WorkspaceSecurity(
            default = ROLE_NONE,
            accessControlList =
                mutableListOf(
                    WorkspaceAccessControl(id = PLATFORM_ADMIN_EMAIL, role = ROLE_ADMIN),
                    WorkspaceAccessControl(id = NEW_USER_ID, role = NEW_USER_ROLE)))
    val workspaceId =
        createWorkspaceAndReturnId(
            mvc,
            organizationId,
            constructWorkspaceCreateRequest(
                key = WORKSPACE_KEY,
                name = WORKSPACE_NAME,
                solutionId = solutionId,
                security = workspaceSecurity))

    mvc.perform(
            delete(
                    "/organizations/$organizationId/workspaces/$workspaceId/security/access/$NEW_USER_ID")
                .with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/workspaces/{workspace_id}/security/access/{identity_id}/DELETE"))
  }

  @Test
  @WithMockOauth2User
  fun update_workspace_security_default() {

    val workspaceId =
        createWorkspaceAndReturnId(
            mvc, organizationId, constructWorkspaceCreateRequest(solutionId = solutionId))

    mvc.perform(
            patch("/organizations/$organizationId/workspaces/$workspaceId/security/default")
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
                "organizations/{organization_id}/workspaces/{workspace_id}/security/default/POST"))
  }

  @Test
  @WithMockOauth2User
  fun list_workspace_users() {

    val workspaceSecurity =
        WorkspaceSecurity(
            default = ROLE_NONE,
            accessControlList =
                mutableListOf(
                    WorkspaceAccessControl(id = PLATFORM_ADMIN_EMAIL, role = ROLE_ADMIN),
                    WorkspaceAccessControl(id = NEW_USER_ID, role = NEW_USER_ROLE)))
    val workspaceId =
        createWorkspaceAndReturnId(
            mvc,
            organizationId,
            constructWorkspaceCreateRequest(
                key = WORKSPACE_KEY,
                name = WORKSPACE_NAME,
                solutionId = solutionId,
                security = workspaceSecurity))

    mvc.perform(
            get("/organizations/$organizationId/workspaces/$workspaceId/security/users")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$[0]").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$[1]").value(NEW_USER_ID))
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/workspaces/{workspace_id}/security/users/GET"))
  }

  @Test
  @WithMockOauth2User
  fun get_workspace_files() {

    val workspaceId =
        createWorkspaceAndReturnId(
            mvc, organizationId, constructWorkspaceCreateRequest(solutionId = solutionId))

    mvc.perform(
            get("/organizations/$organizationId/workspaces/$workspaceId/files")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful)
        .andDo(MockMvcResultHandlers.print())
        .andDo(document("organizations/{organization_id}/workspaces/{workspace_id}/files/GET"))
  }

  @Test
  @WithMockOauth2User
  fun create_workspace_files() {
    val workspaceId =
        createWorkspaceAndReturnId(
            mvc, organizationId, constructWorkspaceCreateRequest(solutionId = solutionId))
    val fileName = "test.txt"
    val fileToUpload =
        this::class.java.getResourceAsStream("/workspace/$fileName")
            ?: throw IllegalStateException(
                "$fileName file used for organizations/{organization_id}/workspaces/{workspace_id}/files/POST endpoint documentation cannot be null")

    val mockFile =
        MockMultipartFile(
            "file", fileName, MediaType.TEXT_PLAIN_VALUE, IOUtils.toByteArray(fileToUpload))

    mvc.perform(
            multipart("/organizations/$organizationId/workspaces/$workspaceId/files")
                .file(mockFile)
                .param("overwrite", "true")
                .param("destination", "path/to/a/directory/")
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.fileName").value("path/to/a/directory/$fileName"))
        .andDo(MockMvcResultHandlers.print())
        .andDo(document("organizations/{organization_id}/workspaces/{workspace_id}/files/POST"))
  }

  @Test
  @WithMockOauth2User
  fun delete_workspace_files() {

    val workspaceId =
        createWorkspaceAndReturnId(
            mvc, organizationId, constructWorkspaceCreateRequest(solutionId = solutionId))

    mvc.perform(
            delete("/organizations/$organizationId/workspaces/$workspaceId/files")
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andDo(MockMvcResultHandlers.print())
        .andDo(document("organizations/{organization_id}/workspaces/{workspace_id}/files/DELETE"))
  }

  @Test
  @WithMockOauth2User
  fun delete_workspace_file() {
    val workspaceId =
        createWorkspaceAndReturnId(
            mvc, organizationId, constructWorkspaceCreateRequest(solutionId = solutionId))

    val fileName = "test.txt"
    val fileToUpload =
        this::class.java.getResourceAsStream("/workspace/$fileName")
            ?: throw IllegalStateException(
                "$fileName file used for organizations/{organization_id}/workspaces/{workspace_id}/files/POST endpoint documentation cannot be null")

    val mockFile =
        MockMultipartFile(
            "file", fileName, MediaType.TEXT_PLAIN_VALUE, IOUtils.toByteArray(fileToUpload))

    val destination = "path/to/a/directory/"
    mvc.perform(
        multipart("/organizations/$organizationId/workspaces/$workspaceId/files")
            .file(mockFile)
            .param("overwrite", "true")
            .param("destination", destination)
            .accept(MediaType.APPLICATION_JSON)
            .with(csrf()))

    mvc.perform(
            delete("/organizations/$organizationId/workspaces/$workspaceId/files/delete")
                .param("file_name", destination + fileName)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/workspaces/{workspace_id}/files/delete/DELETE"))
  }

  @Test
  @WithMockOauth2User
  fun download_workspace_file() {

    val workspaceId =
        createWorkspaceAndReturnId(
            mvc, organizationId, constructWorkspaceCreateRequest(solutionId = solutionId))

    val fileName = "test.txt"
    val fileToUpload =
        this::class.java.getResourceAsStream("/workspace/$fileName")
            ?: throw IllegalStateException(
                "$fileName file used for organizations/{organization_id}/workspaces/{workspace_id}/files/POST endpoint documentation cannot be null")

    val mockFile =
        MockMultipartFile(
            "file", fileName, MediaType.TEXT_PLAIN_VALUE, IOUtils.toByteArray(fileToUpload))

    val destination = "path/to/a/directory/"
    mvc.perform(
        multipart("/organizations/$organizationId/workspaces/$workspaceId/files")
            .file(mockFile)
            .param("overwrite", "true")
            .param("destination", destination)
            .accept(MediaType.APPLICATION_JSON)
            .with(csrf()))

    mvc.perform(
            get("/organizations/$organizationId/workspaces/$workspaceId/files/download")
                .param("file_name", destination + fileName)
                .accept(MediaType.APPLICATION_OCTET_STREAM))
        .andExpect(status().is2xxSuccessful)
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/workspaces/{workspace_id}/files/download/GET"))
  }
}
