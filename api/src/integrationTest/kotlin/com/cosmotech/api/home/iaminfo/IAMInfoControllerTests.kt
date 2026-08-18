// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.home.iaminfo

import com.cosmotech.api.home.ControllerTestBase
import com.cosmotech.api.home.withOrganizationUserHeader
import com.cosmotech.api.home.withPlatformAdminHeader
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
        .andDo(MockMvcResultHandlers.print())
  }

  @Test
  fun `list IAM members as platform admin`() {
    mvc.perform(
            get("/iaminfo/members").withPlatformAdminHeader().accept(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.users").exists())
        .andExpect(jsonPath("$.groups").exists())
        .andDo(MockMvcResultHandlers.print())
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
