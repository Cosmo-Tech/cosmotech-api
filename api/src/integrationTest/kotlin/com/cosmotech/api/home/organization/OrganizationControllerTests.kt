// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.home.organization

import com.cosmotech.api.home.Constants.ORGANIZATION_USER_EMAIL
import com.cosmotech.api.home.Constants.PLATFORM_ADMIN_EMAIL
import com.cosmotech.api.home.ControllerTestBase
import com.cosmotech.api.home.annotations.WithMockOauth2User
import com.cosmotech.api.home.organization.OrganizationConstants.Errors.emptyNameOrganizationCreationRequestError
import com.cosmotech.api.home.organization.OrganizationConstants.ORGANIZATION_NAME
import com.cosmotech.api.home.organization.OrganizationConstants.RequestContent.EMPTY_NAME_ORGANIZATION_REQUEST_CREATION
import com.cosmotech.api.home.organization.OrganizationConstants.RequestContent.MINIMAL_ORGANIZATION_REQUEST_CREATION
import com.cosmotech.api.rbac.ROLE_ADMIN
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.security.ROLE_ORGANIZATION_USER
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.web.context.WebApplicationContext


@ActiveProfiles(profiles = ["test"])
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrganizationControllerTests(context: WebApplicationContext): ControllerTestBase(context) {

    private val logger = LoggerFactory.getLogger(OrganizationControllerTests::class.java)


    @Test
    @WithMockOauth2User
    fun test_create_organization_with_only_mandatory_fields_as_platform_admin() {
        mvc
            .perform(
                post("/organizations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(MINIMAL_ORGANIZATION_REQUEST_CREATION)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf())
            )
            .andExpect(status().is2xxSuccessful)
            .andExpect(jsonPath("$.name").value(ORGANIZATION_NAME))
            .andExpect(jsonPath("$.ownerId").value(PLATFORM_ADMIN_EMAIL))
            .andExpect(jsonPath("$.security.default").value(ROLE_NONE))
            .andExpect(jsonPath("$.security.accessControlList[0].role").value(ROLE_ADMIN))
            .andExpect(jsonPath("$.security.accessControlList[0].id").value(PLATFORM_ADMIN_EMAIL))
            .andDo(MockMvcResultHandlers.print())
    }

    @Test
    @WithMockOauth2User
    fun test_create_organization_with_empty_name() {
        mvc
            .perform(
                post("/organizations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(EMPTY_NAME_ORGANIZATION_REQUEST_CREATION)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf())
            )
            .andExpect(status().is4xxClientError)
            .andExpect(status().isBadRequest)
            .andExpect(content().string(emptyNameOrganizationCreationRequestError))
            .andDo(MockMvcResultHandlers.print())
    }

    @Test
    @WithMockOauth2User(email = ORGANIZATION_USER_EMAIL, roles = [ROLE_ORGANIZATION_USER])
    fun test_create_organization_as_organization_user() {
        mvc
            .perform(
                post("/organizations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(MINIMAL_ORGANIZATION_REQUEST_CREATION)
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().is4xxClientError)
            .andExpect(status().isForbidden)
            .andExpect(status().reason("Forbidden"))
            .andDo(MockMvcResultHandlers.print())
    }

}