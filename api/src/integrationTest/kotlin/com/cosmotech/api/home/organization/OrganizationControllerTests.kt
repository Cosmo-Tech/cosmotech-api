package com.cosmotech.api.home.organization

import com.cosmotech.api.home.ControllerTestBase
import com.cosmotech.api.home.annotations.WithMockOauth2User
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.context.WebApplicationContext
import kotlin.test.assertNotNull


@ActiveProfiles(profiles = ["test"])
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrganizationControllerTests(context: WebApplicationContext): ControllerTestBase(context) {

    @Test
    @WithMockOauth2User
    fun test_create_organization_with_only_mandatory_fields() {
        val contentAsString = mvc
            .perform(
                post("/organizations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"my_new_organization_name\"}")
                    .accept(MediaType.APPLICATION_JSON)
                    .with(jwt().authorities(SimpleGrantedAuthority("Platform.Admin")))
            )
            .andExpect(status().is2xxSuccessful)
            .andExpect(jsonPath("$.name").value("my_new_organization_name"))
            .andExpect(jsonPath("$.ownerId").value("user.admin@test.com"))
            .andReturn().response.contentAsString
        assertNotNull(contentAsString)
    }


}