package com.cosmotech.api.home.solution

import com.cosmotech.api.home.Constants.PLATFORM_ADMIN_EMAIL
import com.cosmotech.api.home.ControllerTestBase
import com.cosmotech.api.home.ControllerTestUtils.OrganizationUtils.createOrganizationAndReturnId
import com.cosmotech.api.home.annotations.WithMockOauth2User
import com.cosmotech.api.home.solution.SolutionConstants.RequestContent.MINIMAL_SOLUTION_REQUEST_CREATION
import com.cosmotech.api.home.solution.SolutionConstants.SOLUTION_KEY
import com.cosmotech.api.home.solution.SolutionConstants.SOLUTION_NAME
import com.cosmotech.api.home.solution.SolutionConstants.SOLUTION_REPOSITORY
import com.cosmotech.api.home.solution.SolutionConstants.SOLUTION_SIMULATOR
import com.cosmotech.api.home.solution.SolutionConstants.SOLUTION_VERSION
import com.cosmotech.api.home.workspace.WorkspaceConstants.RequestContent.MINIMAL_WORKSPACE_REQUEST_CREATION
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
class SolutionControllerTests : ControllerTestBase() {


    private val logger = LoggerFactory.getLogger(SolutionControllerTests::class.java)

    @Test
    @WithMockOauth2User
    fun create_solution() {

        val organizationId = createOrganizationAndReturnId(mvc)

        mvc
            .perform(
                post("/organizations/$organizationId/solutions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(MINIMAL_SOLUTION_REQUEST_CREATION)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf())
            )
            .andExpect(status().is2xxSuccessful)
            .andExpect(jsonPath("$.name").value(SOLUTION_NAME))
            .andExpect(jsonPath("$.key").value(SOLUTION_KEY))
            .andExpect(jsonPath("$.repository").value(SOLUTION_REPOSITORY))
            .andExpect(jsonPath("$.csmSimulator").value(SOLUTION_SIMULATOR))
            .andExpect(jsonPath("$.version").value(SOLUTION_VERSION))
            .andExpect(jsonPath("$.organizationId").value(organizationId))
            .andExpect(jsonPath("$.ownerId").value(PLATFORM_ADMIN_EMAIL))
            .andExpect(jsonPath("$.security.default").value(ROLE_NONE))
            .andExpect(jsonPath("$.security.accessControlList[0].role").value(ROLE_ADMIN))
            .andExpect(jsonPath("$.security.accessControlList[0].id").value(PLATFORM_ADMIN_EMAIL))
            .andDo(MockMvcResultHandlers.print())
            .andDo(document("organizations/{organization_id}/solutions/POST"))
    }
}