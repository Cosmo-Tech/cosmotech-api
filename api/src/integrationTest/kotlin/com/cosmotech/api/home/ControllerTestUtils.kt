package com.cosmotech.api.home

import com.cosmotech.api.home.organization.OrganizationConstants.ORGANIZATION_NAME
import com.cosmotech.api.home.solution.SolutionConstants.RequestContent.MINIMAL_SOLUTION_REQUEST_CREATION
import org.json.JSONObject
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

class ControllerTestUtils {

    companion object OrganizationUtils{
        @JvmStatic
        fun createOrganizationAndReturnId(mvc: MockMvc, name:String = ORGANIZATION_NAME): String = JSONObject(
            mvc
                .perform(
                    post("/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"name":"$name"}""")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf())
                ).andReturn().response.contentAsString
        ).getString("id")

        @JvmStatic
        fun createSolutionAndReturnId(mvc: MockMvc, organizationId:String): String = JSONObject(
            mvc
                .perform(
                    post("/organizations/$organizationId/solutions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MINIMAL_SOLUTION_REQUEST_CREATION)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf())
                ).andReturn().response.contentAsString
        ).getString("id")
    }

}