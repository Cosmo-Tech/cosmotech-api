package com.cosmotech.api.home.workspace

import com.cosmotech.api.home.Constants.PLATFORM_ADMIN_EMAIL
import com.cosmotech.api.home.ControllerTestBase
import com.cosmotech.api.home.ControllerTestUtils.OrganizationUtils.createOrganizationAndReturnId
import com.cosmotech.api.home.ControllerTestUtils.OrganizationUtils.createSolutionAndReturnId
import com.cosmotech.api.home.annotations.WithMockOauth2User
import com.cosmotech.api.home.workspace.WorkspaceConstants.WORKSPACE_KEY
import com.cosmotech.api.home.workspace.WorkspaceConstants.WORKSPACE_NAME
import com.cosmotech.api.rbac.ROLE_ADMIN
import com.cosmotech.api.rbac.ROLE_NONE
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@ActiveProfiles(profiles = ["test"])
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WorkspaceControllerTests : ControllerTestBase() {


    private val logger = LoggerFactory.getLogger(WorkspaceControllerTests::class.java)

    @Test
    @WithMockOauth2User
    fun create_workspace() {

        val organizationId = createOrganizationAndReturnId(mvc)
        val solutionId = createSolutionAndReturnId(mvc,organizationId)

        mvc
            .perform(
                post("/organizations/$organizationId/workspaces")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"key":"$WORKSPACE_KEY", "name":"$WORKSPACE_NAME", "solution": {"solutionId":"$solutionId" } }""")
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf())
            )
            .andExpect(status().is2xxSuccessful)
            .andExpect(jsonPath("$.name").value(WORKSPACE_NAME))
            .andExpect(jsonPath("$.key").value(WORKSPACE_KEY))
            .andExpect(jsonPath("$.ownerId").value(PLATFORM_ADMIN_EMAIL))
            .andExpect(jsonPath("$.organizationId").value(organizationId))
            .andExpect(jsonPath("$.solution.solutionId").value(solutionId))
            .andExpect(jsonPath("$.security.default").value(ROLE_NONE))
            .andExpect(jsonPath("$.security.accessControlList[0].role").value(ROLE_ADMIN))
            .andExpect(jsonPath("$.security.accessControlList[0].id").value(PLATFORM_ADMIN_EMAIL))
            .andDo(MockMvcResultHandlers.print())
            .andDo(document("organizations/{organization_id}/workspaces/POST"))
    }
}