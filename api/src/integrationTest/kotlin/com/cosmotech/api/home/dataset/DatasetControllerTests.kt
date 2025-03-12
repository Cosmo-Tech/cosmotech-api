// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.home.runner

import com.cosmotech.api.home.Constants.PLATFORM_ADMIN_EMAIL
import com.cosmotech.api.home.ControllerTestBase
import com.cosmotech.api.home.ControllerTestUtils.OrganizationUtils.constructOrganizationCreateRequest
import com.cosmotech.api.home.ControllerTestUtils.OrganizationUtils.createOrganizationAndReturnId
import com.cosmotech.api.home.annotations.WithMockOauth2User
import com.cosmotech.api.home.dataset.DatasetConstants.DATASET_NAME
import com.cosmotech.api.rbac.ROLE_ADMIN
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.dataset.domain.Dataset
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
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
class DatasetControllerTests: ControllerTestBase() {

    private lateinit var organizationId:String

    private val logger = LoggerFactory.getLogger(DatasetControllerTests::class.java)

    @BeforeEach
    fun beforeEach() {
        organizationId = createOrganizationAndReturnId(mvc, constructOrganizationCreateRequest())
    }


    @Test
    @WithMockOauth2User
    fun create_dataset() {
        mvc
            .perform(
                post("/organizations/$organizationId/datasets")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(JSONObject(constructDataset()).toString())
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf())
            ).andExpect(status().is2xxSuccessful)
            .andExpect(jsonPath("$.name").value(DATASET_NAME))
            .andExpect(jsonPath("$.ownerId").value(PLATFORM_ADMIN_EMAIL))
            .andExpect(jsonPath("$.security.default").value(ROLE_NONE))
            .andExpect(jsonPath("$.security.accessControlList[0].role").value(ROLE_ADMIN))
            .andExpect(jsonPath("$.security.accessControlList[0].id").value(PLATFORM_ADMIN_EMAIL))
            .andDo(MockMvcResultHandlers.print())
            .andDo(document("organizations/{organization_id}/datasets/POST"))
    }

    fun constructDataset(name : String = DATASET_NAME): Dataset {
        return Dataset(
            name = name
        )
    }
}