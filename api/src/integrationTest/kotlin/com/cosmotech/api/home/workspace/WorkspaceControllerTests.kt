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
import com.cosmotech.api.home.organization.OrganizationConstants.NEW_USER_ID
import com.cosmotech.api.home.organization.OrganizationConstants.NEW_USER_ROLE
import com.cosmotech.api.home.withPlatformAdminHeader
import com.cosmotech.api.home.workspace.WorkspaceConstants.WORKSPACE_KEY
import com.cosmotech.api.home.workspace.WorkspaceConstants.WORKSPACE_NAME
import com.cosmotech.common.rbac.ROLE_ADMIN
import com.cosmotech.common.rbac.ROLE_NONE
import com.cosmotech.common.rbac.ROLE_VIEWER
import com.cosmotech.workspace.domain.WorkspaceAccessControl
import com.cosmotech.workspace.domain.WorkspaceSecurity
import org.apache.commons.io.IOUtils
import org.hamcrest.core.StringContains.containsString
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
  fun get_workspace_with_wrong_ids_format() {
    mvc.perform(
            get("/organizations/wrong-orgId/workspaces/wrong-workspaceId")
                .withPlatformAdminHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest)
        .andExpect(jsonPath("$.detail", containsString("wrong-orgId:must match \"^o-\\w{10,20}\"")))
        .andExpect(
            jsonPath("$.detail", containsString("wrong-workspaceId:must match \"^w-\\w{10,20}\"")))

    mvc.perform(
            get("/organizations/wrong-orgId/workspaces/w-123456abcdef")
                .withPlatformAdminHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest)
        .andExpect(jsonPath("$.detail", containsString("wrong-orgId:must match \"^o-\\w{10,20}\"")))

    mvc.perform(
            get("/organizations/o-123456abcdef/workspaces/wrong-workspaceId")
                .withPlatformAdminHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest)
        .andExpect(
            jsonPath("$.detail", containsString("wrong-workspaceId:must match \"^w-\\w{10,20}\"")))
  }

  @Test
  fun create_workspace() {

    val description = "here_is_workspace_description"
    val version = "1.0.0"
    val datasetCopy = false
    val tags = mutableListOf("tag1,tag2")
    val additionalData =
        mutableMapOf(
            "you_can_put" to "whatever_you_want_here", "even" to mapOf("object" to "if_you_want"))
    val workspaceSecurity =
        WorkspaceSecurity(
            default = ROLE_NONE,
            accessControlList =
                mutableListOf(
                    WorkspaceAccessControl(id = PLATFORM_ADMIN_EMAIL, role = ROLE_ADMIN),
                    WorkspaceAccessControl(id = NEW_USER_ID, role = NEW_USER_ROLE)))

    val workspaceDatasetId = "d-12345678910"
    mvc.perform(
            post("/organizations/$organizationId/workspaces")
                .withPlatformAdminHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    JSONObject(
                            constructWorkspaceCreateRequest(
                                WORKSPACE_KEY,
                                WORKSPACE_NAME,
                                solutionId,
                                workspaceDatasetId,
                                mutableMapOf(
                                    "solution_parameter1" to "solution_parameter1_defaultValue",
                                    "solution_parameter2" to "solution_parameter2_defaultValue"),
                                description,
                                version,
                                datasetCopy,
                                workspaceSecurity,
                                additionalData,
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
        .andExpect(jsonPath("$.solution.datasetId").value(workspaceDatasetId))
        .andExpect(
            jsonPath("$.solution.defaultParameterValues.solution_parameter1")
                .value("solution_parameter1_defaultValue"))
        .andExpect(
            jsonPath("$.solution.defaultParameterValues.solution_parameter2")
                .value("solution_parameter2_defaultValue"))
        .andExpect(jsonPath("$.datasetCopy").value(datasetCopy))
        .andExpect(jsonPath("$.tags").value(tags))
        .andExpect(jsonPath("$.additionalData").value(additionalData))
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
  fun update_workspace() {

    val workspaceId =
        createWorkspaceAndReturnId(mvc, organizationId, WORKSPACE_KEY, WORKSPACE_NAME, solutionId)

    val description = "here_is_workspace_description"
    val datasetCopy = false
    val tags = mutableListOf("tag1,tag2")
    val additionalData =
        mutableMapOf(
            "you_can_put" to "whatever_you_want_here", "even" to mapOf("object" to "if_you_want"))

    val workspaceDatasetId = "d-12345678910"
    mvc.perform(
            patch("/organizations/$organizationId/workspaces/$workspaceId")
                .withPlatformAdminHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    JSONObject(
                            constructWorkspaceUpdateRequest(
                                WORKSPACE_KEY,
                                WORKSPACE_NAME,
                                solutionId,
                                workspaceDatasetId,
                                mutableMapOf(
                                    "solution_parameter1" to "solution_parameter1_defaultValue",
                                    "solution_parameter2" to "solution_parameter2_defaultValue"),
                                description,
                                datasetCopy,
                                additionalData,
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
        .andExpect(jsonPath("$.solution.datasetId").value(workspaceDatasetId))
        .andExpect(
            jsonPath("$.solution.defaultParameterValues.solution_parameter1")
                .value("solution_parameter1_defaultValue"))
        .andExpect(
            jsonPath("$.solution.defaultParameterValues.solution_parameter2")
                .value("solution_parameter2_defaultValue"))
        .andExpect(jsonPath("$.datasetCopy").value(datasetCopy))
        .andExpect(jsonPath("$.tags").value(tags))
        .andExpect(jsonPath("$.additionalData").value(additionalData))
        .andExpect(jsonPath("$.organizationId").value(organizationId))
        .andExpect(jsonPath("$.solution.solutionId").value(solutionId))
        .andExpect(jsonPath("$.security.default").value(ROLE_NONE))
        .andExpect(jsonPath("$.security.accessControlList[0].role").value(ROLE_ADMIN))
        .andExpect(jsonPath("$.security.accessControlList[0].id").value(PLATFORM_ADMIN_EMAIL))
        .andDo(MockMvcResultHandlers.print())
        .andDo(document("organizations/{organization_id}/workspaces/{workspace_id}/PATCH"))
  }

  @Test
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
                .withPlatformAdminHeader()
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$[0].id").value(firstWorkspaceId))
        .andExpect(jsonPath("$[0].key").value(firstWorkspaceKey))
        .andExpect(jsonPath("$[0].name").value(firstWorkspaceName))
        .andExpect(jsonPath("$[0].createInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$[0].updateInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$[0].organizationId").value(organizationId))
        .andExpect(jsonPath("$[0].solution.solutionId").value(solutionId))
        .andExpect(jsonPath("$[0].solution.datasetId").value(null))
        .andExpect(jsonPath("$[0].solution.defaultParameterValues").value(null))
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
        .andExpect(jsonPath("$[1].solution.datasetId").value(null))
        .andExpect(jsonPath("$[1].solution.defaultParameterValues").value(null))
        .andExpect(jsonPath("$[1].security.default").value(ROLE_NONE))
        .andExpect(jsonPath("$[1].security.accessControlList[0].role").value(ROLE_ADMIN))
        .andExpect(jsonPath("$[1].security.accessControlList[0].id").value(PLATFORM_ADMIN_EMAIL))
        .andDo(MockMvcResultHandlers.print())
        .andDo(document("organizations/{organization_id}/workspaces/GET"))
  }

  @Test
  fun get_workspace() {

    val description = "here_is_workspace_description"
    val version = "1.0.0"
    val datasetCopy = false
    val tags = mutableListOf("tag1,tag2")
    val additionalData =
        mutableMapOf(
            "you_can_put" to "whatever_you_want_here", "even" to mapOf("object" to "if_you_want"))

    val workspaceDatasetId = "d-12345678910"
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
                workspaceDatasetId,
                mutableMapOf(
                    "solution_parameter1" to "solution_parameter1_defaultValue",
                    "solution_parameter2" to "solution_parameter2_defaultValue"),
                description,
                version,
                datasetCopy,
                workspaceSecurity,
                additionalData,
                tags))

    mvc.perform(
            get("/organizations/$organizationId/workspaces/$workspaceId")
                .withPlatformAdminHeader()
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.name").value(WORKSPACE_NAME))
        .andExpect(jsonPath("$.key").value(WORKSPACE_KEY))
        .andExpect(jsonPath("$.createInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.updateInfo.userId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.version").value(version))
        .andExpect(jsonPath("$.description").value(description))
        .andExpect(jsonPath("$.datasetCopy").value(datasetCopy))
        .andExpect(jsonPath("$.tags").value(tags))
        .andExpect(jsonPath("$.additionalData").value(additionalData))
        .andExpect(jsonPath("$.organizationId").value(organizationId))
        .andExpect(jsonPath("$.solution.solutionId").value(solutionId))
        .andExpect(jsonPath("$.solution.datasetId").value(workspaceDatasetId))
        .andExpect(
            jsonPath("$.solution.defaultParameterValues.solution_parameter1")
                .value("solution_parameter1_defaultValue"))
        .andExpect(
            jsonPath("$.solution.defaultParameterValues.solution_parameter2")
                .value("solution_parameter2_defaultValue"))
        .andExpect(jsonPath("$.security.default").value(ROLE_NONE))
        .andExpect(jsonPath("$.security.accessControlList[0].role").value(ROLE_ADMIN))
        .andExpect(jsonPath("$.security.accessControlList[0].id").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.security.accessControlList[1].role").value(NEW_USER_ROLE))
        .andExpect(jsonPath("$.security.accessControlList[1].id").value(NEW_USER_ID))
        .andDo(MockMvcResultHandlers.print())
        .andDo(document("organizations/{organization_id}/workspaces/{workspace_id}/GET"))
  }

  @Test
  fun delete_workspace() {

    val workspaceId =
        createWorkspaceAndReturnId(mvc, organizationId, WORKSPACE_KEY, WORKSPACE_NAME, solutionId)

    mvc.perform(
            delete("/organizations/$organizationId/workspaces/$workspaceId")
                .withPlatformAdminHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andDo(MockMvcResultHandlers.print())
        .andDo(document("organizations/{organization_id}/workspaces/{workspace_id}/DELETE"))
  }

  @Test
  fun get_workspace_security() {

    val workspaceId =
        createWorkspaceAndReturnId(
            mvc, organizationId, constructWorkspaceCreateRequest(solutionId = solutionId))

    mvc.perform(
            get("/organizations/$organizationId/workspaces/$workspaceId/security")
                .withPlatformAdminHeader())
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.default").value(ROLE_NONE))
        .andExpect(jsonPath("$.accessControlList[0].role").value(ROLE_ADMIN))
        .andExpect(jsonPath("$.accessControlList[0].id").value(PLATFORM_ADMIN_EMAIL))
        .andDo(MockMvcResultHandlers.print())
        .andDo(document("organizations/{organization_id}/workspaces/{workspace_id}/security/GET"))
  }

  @Test
  fun add_workspace_security_access() {

    val workspaceId =
        createWorkspaceAndReturnId(
            mvc, organizationId, constructWorkspaceCreateRequest(solutionId = solutionId))

    mvc.perform(
            post("/organizations/$organizationId/workspaces/$workspaceId/security/access")
                .withPlatformAdminHeader()
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
  fun get_workspace_security_access() {

    val workspaceId =
        createWorkspaceAndReturnId(
            mvc, organizationId, constructWorkspaceCreateRequest(solutionId = solutionId))

    mvc.perform(
            get(
                    "/organizations/$organizationId/workspaces/$workspaceId/security/access/$PLATFORM_ADMIN_EMAIL")
                .withPlatformAdminHeader())
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.role").value(ROLE_ADMIN))
        .andExpect(jsonPath("$.id").value(PLATFORM_ADMIN_EMAIL))
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/workspaces/{workspace_id}/security/access/{identity_id}/GET"))
  }

  @Test
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
                .withPlatformAdminHeader()
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
                .withPlatformAdminHeader()
                .with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/workspaces/{workspace_id}/security/access/{identity_id}/DELETE"))
  }

  @Test
  fun update_workspace_security_default() {

    val workspaceId =
        createWorkspaceAndReturnId(
            mvc, organizationId, constructWorkspaceCreateRequest(solutionId = solutionId))

    mvc.perform(
            patch("/organizations/$organizationId/workspaces/$workspaceId/security/default")
                .withPlatformAdminHeader()
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
                .withPlatformAdminHeader()
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
  fun get_workspace_files() {

    val workspaceId =
        createWorkspaceAndReturnId(
            mvc, organizationId, constructWorkspaceCreateRequest(solutionId = solutionId))

    mvc.perform(
            get("/organizations/$organizationId/workspaces/$workspaceId/files")
                .withPlatformAdminHeader()
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful)
        .andDo(MockMvcResultHandlers.print())
        .andDo(document("organizations/{organization_id}/workspaces/{workspace_id}/files/GET"))
  }

  @Test
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
                .withPlatformAdminHeader()
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.fileName").value("path/to/a/directory/$fileName"))
        .andDo(MockMvcResultHandlers.print())
        .andDo(document("organizations/{organization_id}/workspaces/{workspace_id}/files/POST"))
  }

  @Test
  fun delete_workspace_files() {

    val workspaceId =
        createWorkspaceAndReturnId(
            mvc, organizationId, constructWorkspaceCreateRequest(solutionId = solutionId))

    mvc.perform(
            delete("/organizations/$organizationId/workspaces/$workspaceId/files")
                .withPlatformAdminHeader()
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andDo(MockMvcResultHandlers.print())
        .andDo(document("organizations/{organization_id}/workspaces/{workspace_id}/files/DELETE"))
  }

  @Test
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
            .withPlatformAdminHeader()
            .accept(MediaType.APPLICATION_JSON)
            .with(csrf()))

    mvc.perform(
            delete("/organizations/$organizationId/workspaces/$workspaceId/files/delete")
                .param("file_name", destination + fileName)
                .withPlatformAdminHeader()
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/workspaces/{workspace_id}/files/delete/DELETE"))
  }

  @Test
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
            .withPlatformAdminHeader()
            .accept(MediaType.APPLICATION_JSON)
            .with(csrf()))

    mvc.perform(
            get("/organizations/$organizationId/workspaces/$workspaceId/files/download")
                .param("file_name", destination + fileName)
                .withPlatformAdminHeader()
                .accept(MediaType.APPLICATION_OCTET_STREAM))
        .andExpect(status().is2xxSuccessful)
        .andDo(MockMvcResultHandlers.print())
        .andDo(
            document(
                "organizations/{organization_id}/workspaces/{workspace_id}/files/download/GET"))
  }

  @Test
  fun `download workspace file with wrong file name`() {

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
            .withPlatformAdminHeader()
            .accept(MediaType.APPLICATION_JSON)
            .with(csrf()))

    mvc.perform(
            get("/organizations/$organizationId/workspaces/$workspaceId/files/download")
                .param("file_name", "Wrong file name")
                .withPlatformAdminHeader()
                .accept(MediaType.APPLICATION_OCTET_STREAM))
        .andExpect(status().is4xxClientError)
        .andExpect(jsonPath("$.detail").value("Wrong file name does not exist."))
        .andDo(MockMvcResultHandlers.print())
  }
}
