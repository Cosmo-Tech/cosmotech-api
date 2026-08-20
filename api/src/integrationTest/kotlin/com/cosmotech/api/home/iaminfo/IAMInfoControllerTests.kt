// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.home.iaminfo

import com.cosmotech.api.home.Constants.ORGANIZATION_USER_EMAIL
import com.cosmotech.api.home.Constants.PLATFORM_ADMIN_EMAIL
import com.cosmotech.api.home.Constants.PRIVATE_GROUP_NAME
import com.cosmotech.api.home.Constants.PUBLIC_GROUP_NAME
import com.cosmotech.api.home.Constants.UNKNOWN_IDENTITY
import com.cosmotech.api.home.ControllerTestBase
import com.cosmotech.api.home.withOrganizationUserHeader
import com.cosmotech.api.home.withPlatformAdminHeader
import com.cosmotech.common.security.ROLE_ORGANIZATION_USER
import com.cosmotech.common.security.ROLE_ORGANIZATION_VIEWER
import com.cosmotech.common.security.ROLE_PLATFORM_ADMIN
import org.hamcrest.Matchers.hasItem
import org.json.JSONArray
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@ActiveProfiles(profiles = ["test"])
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IAMInfoControllerTests : ControllerTestBase() {

  private val logger = LoggerFactory.getLogger(IAMInfoControllerTests::class.java)

  @Test
  fun `list IAM groups as platform admin`() {
    mvc.perform(get("/iaminfo/groups").withPlatformAdminHeader().accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful)
        .andExpect { result ->
          // response should be a JSON array of group names, open to all users
          JSONArray(result.response.contentAsString)
        }
        .andExpect(jsonPath("$[?(@ == 'test-public-group')]").exists())
        .andDo(MockMvcResultHandlers.print())
        .andDo(document("iaminfo/groups/GET"))
  }

  @Test
  fun `list IAM groups as organization user`() {
    // listIAMGroups is open to all users, no permission check needed
    mvc.perform(
            get("/iaminfo/groups").withOrganizationUserHeader().accept(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().is2xxSuccessful)
        .andExpect { result -> JSONArray(result.response.contentAsString) }
        .andExpect(jsonPath("$[?(@ == 'test-public-group')]").exists())
        .andDo(MockMvcResultHandlers.print())
  }

  @Test
  fun `list IAM members as platform admin`() {
    mvc.perform(
            get("/iaminfo/members").withPlatformAdminHeader().accept(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().is2xxSuccessful)
        .andDo(MockMvcResultHandlers.print())
        .andExpect(jsonPath("$.users").isArray)
        .andExpect(jsonPath("$.users[?(@.id == '%s')]", PLATFORM_ADMIN_EMAIL).exists())
        .andExpect(
            jsonPath("$.users[?(@.id == '%s')].role", PLATFORM_ADMIN_EMAIL)
                .value(ROLE_PLATFORM_ADMIN)
        )
        .andExpect(jsonPath("$.users[?(@.id == '%s')]", ORGANIZATION_USER_EMAIL).exists())
        .andExpect(
            jsonPath("$.users[?(@.id == '%s')].role", ORGANIZATION_USER_EMAIL)
                .value(ROLE_ORGANIZATION_USER)
        )
        // an identity unknown to Keycloak is never exposed, neither as user nor as group
        .andExpect(jsonPath("$.users[?(@.id == '%s')]", UNKNOWN_IDENTITY).doesNotExist())
        .andExpect(jsonPath("$.groups[?(@.id == '%s')]", UNKNOWN_IDENTITY).doesNotExist())
        // only groups flagged `public=true` are returned, with their role and their members
        .andExpect(jsonPath("$.groups").isArray)
        .andExpect(jsonPath("$.groups[?(@.id == '%s')]", PUBLIC_GROUP_NAME).exists())
        .andExpect(
            jsonPath("$.groups[?(@.id == '%s')].role", PUBLIC_GROUP_NAME)
                .value(ROLE_ORGANIZATION_VIEWER)
        )
        .andExpect(
            jsonPath("$.groups[?(@.id == '%s')].users[*]", PUBLIC_GROUP_NAME)
                .value(hasItem<String>(PLATFORM_ADMIN_EMAIL))
        )
        .andExpect(
            jsonPath("$.groups[?(@.id == '%s')].users[*]", PUBLIC_GROUP_NAME)
                .value(hasItem<String>(ORGANIZATION_USER_EMAIL))
        )
        // a group without the `public=true` attribute is filtered out
        .andExpect(jsonPath("$.groups[?(@.id == '%s')]", PRIVATE_GROUP_NAME).doesNotExist())
        .andDo(document("iaminfo/members/GET"))
  }

  @Test
  fun `list IAM members as organization user should be forbidden`() {
    // listIAMMembers is only accessible to Platform.admin users
    mvc.perform(
            get("/iaminfo/members").withOrganizationUserHeader().accept(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isForbidden)
        .andDo(MockMvcResultHandlers.print())
  }
}
