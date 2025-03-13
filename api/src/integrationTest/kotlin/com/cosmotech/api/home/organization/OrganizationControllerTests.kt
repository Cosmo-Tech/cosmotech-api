// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.home.organization

import com.cosmotech.api.home.Constants.ORGANIZATION_USER_EMAIL
import com.cosmotech.api.home.Constants.PLATFORM_ADMIN_EMAIL
import com.cosmotech.api.home.ControllerTestBase
import com.cosmotech.api.home.ControllerTestUtils.OrganizationUtils.constructOrganizationCreateRequest
import com.cosmotech.api.home.ControllerTestUtils.OrganizationUtils.createOrganizationAndReturnId
import com.cosmotech.api.home.annotations.WithMockOauth2User
import com.cosmotech.api.home.organization.OrganizationConstants.Errors.emptyNameOrganizationCreationRequestError
import com.cosmotech.api.home.organization.OrganizationConstants.NEW_ORGANIZATION_NAME
import com.cosmotech.api.home.organization.OrganizationConstants.NEW_USER_ID
import com.cosmotech.api.home.organization.OrganizationConstants.NEW_USER_ROLE
import com.cosmotech.api.home.organization.OrganizationConstants.ORGANIZATION_NAME
import com.cosmotech.api.home.organization.OrganizationConstants.RequestContent.EMPTY_NAME_ORGANIZATION_REQUEST_CREATION
import com.cosmotech.api.home.organization.OrganizationConstants.RequestContent.MINIMAL_ORGANIZATION_ACCESS_CONTROL_REQUEST
import com.cosmotech.api.home.organization.OrganizationConstants.RequestContent.MINIMAL_ORGANIZATION_REQUEST_CREATION
import com.cosmotech.api.home.organization.OrganizationConstants.RequestContent.MINIMAL_ORGANIZATION_REQUEST_UPDATE
import com.cosmotech.api.home.organization.OrganizationConstants.RequestContent.ORGANIZATION_REQUEST_CREATION_WITH_ACCESSES
import com.cosmotech.api.rbac.ROLE_ADMIN
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.rbac.ROLE_VIEWER
import com.cosmotech.api.security.ROLE_ORGANIZATION_USER
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@ActiveProfiles(profiles = ["test"])
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrganizationControllerTests : ControllerTestBase() {

  private val logger = LoggerFactory.getLogger(OrganizationControllerTests::class.java)

  @Test
  @WithMockOauth2User
  fun create_organization_without_accesses() {
    mvc.perform(
            post("/organizations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(MINIMAL_ORGANIZATION_REQUEST_CREATION)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
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
  fun create_organization() {
    mvc.perform(
            post("/organizations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ORGANIZATION_REQUEST_CREATION_WITH_ACCESSES)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.name").value(ORGANIZATION_NAME))
        .andExpect(jsonPath("$.ownerId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.security.default").value(ROLE_NONE))
        .andExpect(jsonPath("$.security.accessControlList[0].role").value(ROLE_ADMIN))
        .andExpect(jsonPath("$.security.accessControlList[0].id").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.security.accessControlList[1].role").value(NEW_USER_ROLE))
        .andExpect(jsonPath("$.security.accessControlList[1].id").value(NEW_USER_ID))
        .andDo(MockMvcResultHandlers.print())
        .andDo(document("organizations/POST"))
  }

  @Test
  @WithMockOauth2User
  fun update_organization() {

    val organizationId = createOrganizationAndReturnId(mvc, constructOrganizationCreateRequest())

    mvc.perform(
            patch("/organizations/$organizationId")
                .contentType(MediaType.APPLICATION_JSON)
                .content(MINIMAL_ORGANIZATION_REQUEST_UPDATE)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.name").value(NEW_ORGANIZATION_NAME))
        .andExpect(jsonPath("$.ownerId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.security.default").value(ROLE_NONE))
        .andExpect(jsonPath("$.security.accessControlList[0].role").value(ROLE_ADMIN))
        .andExpect(jsonPath("$.security.accessControlList[0].id").value(PLATFORM_ADMIN_EMAIL))
        .andDo(MockMvcResultHandlers.print())
        .andDo(document("organizations/{organization_id}/PATCH"))
  }

  @Test
  @WithMockOauth2User
  fun list_organizations() {

    val firstOrganizationId =
        createOrganizationAndReturnId(
            mvc, constructOrganizationCreateRequest("my_first_organization"))
    val secondOrganizationId =
        createOrganizationAndReturnId(
            mvc, constructOrganizationCreateRequest("my_second_organization"))

    mvc.perform(
            get("/organizations")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$[0].id").value(firstOrganizationId))
        .andExpect(jsonPath("$[0].name").value("my_first_organization"))
        .andExpect(jsonPath("$[0].ownerId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$[0].security.default").value(ROLE_NONE))
        .andExpect(jsonPath("$[0].security.accessControlList[0].role").value(ROLE_ADMIN))
        .andExpect(jsonPath("$[0].security.accessControlList[0].id").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$[1].id").value(secondOrganizationId))
        .andExpect(jsonPath("$[1].name").value("my_second_organization"))
        .andExpect(jsonPath("$[1].ownerId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$[1].security.default").value(ROLE_NONE))
        .andExpect(jsonPath("$[1].security.accessControlList[0].role").value(ROLE_ADMIN))
        .andExpect(jsonPath("$[1].security.accessControlList[0].id").value(PLATFORM_ADMIN_EMAIL))
        .andDo(MockMvcResultHandlers.print())
        .andDo(document("organizations/GET"))
  }

  @Test
  @WithMockOauth2User
  fun get_organization() {

    val organizationId = createOrganizationAndReturnId(mvc, constructOrganizationCreateRequest())

    mvc.perform(get("/organizations/$organizationId"))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.name").value(ORGANIZATION_NAME))
        .andExpect(jsonPath("$.ownerId").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.security.default").value(ROLE_NONE))
        .andExpect(jsonPath("$.security.accessControlList[0].role").value(ROLE_ADMIN))
        .andExpect(jsonPath("$.security.accessControlList[0].id").value(PLATFORM_ADMIN_EMAIL))
        .andDo(MockMvcResultHandlers.print())
        .andDo(document("organizations/{organization_id}/GET"))
  }

  @Test
  @WithMockOauth2User
  fun delete_organization() {

    val organizationId = createOrganizationAndReturnId(mvc, constructOrganizationCreateRequest())

    mvc.perform(delete("/organizations/$organizationId").with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andDo(MockMvcResultHandlers.print())
        .andDo(document("organizations/{organization_id}/DELETE"))
  }

  @Test
  @WithMockOauth2User
  fun get_organization_security() {

    val organizationId = createOrganizationAndReturnId(mvc, constructOrganizationCreateRequest())

    mvc.perform(get("/organizations/$organizationId/security"))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.default").value(ROLE_NONE))
        .andExpect(jsonPath("$.accessControlList[0].role").value(ROLE_ADMIN))
        .andExpect(jsonPath("$.accessControlList[0].id").value(PLATFORM_ADMIN_EMAIL))
        .andDo(MockMvcResultHandlers.print())
        .andDo(document("organizations/{organization_id}/security/GET"))
  }

  @Test
  @WithMockOauth2User
  fun add_organization_security_access() {

    val organizationId = createOrganizationAndReturnId(mvc, constructOrganizationCreateRequest())

    mvc.perform(
            post("/organizations/$organizationId/security/access")
                .contentType(MediaType.APPLICATION_JSON)
                .content(MINIMAL_ORGANIZATION_ACCESS_CONTROL_REQUEST)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.role").value(NEW_USER_ROLE))
        .andExpect(jsonPath("$.id").value(NEW_USER_ID))
        .andDo(MockMvcResultHandlers.print())
        .andDo(document("organizations/{organization_id}/security/access/POST"))
  }

  @Test
  @WithMockOauth2User
  fun get_organization_security_access() {

    val organizationId = createOrganizationAndReturnId(mvc, constructOrganizationCreateRequest())

    mvc.perform(get("/organizations/$organizationId/security/access/$PLATFORM_ADMIN_EMAIL"))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.role").value(ROLE_ADMIN))
        .andExpect(jsonPath("$.id").value(PLATFORM_ADMIN_EMAIL))
        .andDo(MockMvcResultHandlers.print())
        .andDo(document("organizations/{organization_id}/security/access/{identity_id}/GET"))
  }

  @Test
  @WithMockOauth2User
  fun update_organization_security_access() {

    val organizationId =
        JSONObject(
                mvc.perform(
                        post("/organizations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ORGANIZATION_REQUEST_CREATION_WITH_ACCESSES)
                            .accept(MediaType.APPLICATION_JSON)
                            .with(csrf()))
                    .andReturn()
                    .response
                    .contentAsString)
            .getString("id")

    mvc.perform(
            patch("/organizations/$organizationId/security/access/$NEW_USER_ID")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"role":"$ROLE_VIEWER"}""")
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.role").value(ROLE_VIEWER))
        .andExpect(jsonPath("$.id").value(NEW_USER_ID))
        .andDo(MockMvcResultHandlers.print())
        .andDo(document("organizations/{organization_id}/security/access/{identity_id}/PATCH"))
  }

  @Test
  @WithMockOauth2User
  fun delete_organization_security_access() {

    val organizationId =
        JSONObject(
                mvc.perform(
                        post("/organizations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ORGANIZATION_REQUEST_CREATION_WITH_ACCESSES)
                            .accept(MediaType.APPLICATION_JSON)
                            .with(csrf()))
                    .andReturn()
                    .response
                    .contentAsString)
            .getString("id")

    mvc.perform(delete("/organizations/$organizationId/security/access/$NEW_USER_ID").with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andDo(MockMvcResultHandlers.print())
        .andDo(document("organizations/{organization_id}/security/access/{identity_id}/DELETE"))
  }

  @Test
  @WithMockOauth2User
  fun update_organization_security_default() {

    val organizationId =
        JSONObject(
                mvc.perform(
                        post("/organizations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(MINIMAL_ORGANIZATION_REQUEST_CREATION)
                            .accept(MediaType.APPLICATION_JSON)
                            .with(csrf()))
                    .andReturn()
                    .response
                    .contentAsString)
            .getString("id")

    mvc.perform(
            patch("/organizations/$organizationId/security/default")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"role":"$ROLE_VIEWER"}""")
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$.default").value(ROLE_VIEWER))
        .andExpect(jsonPath("$.accessControlList[0].id").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$.accessControlList[0].role").value(ROLE_ADMIN))
        .andDo(MockMvcResultHandlers.print())
        .andDo(document("organizations/{organization_id}/security/default/POST"))
  }

  @Test
  @WithMockOauth2User
  fun list_organization_users() {

    val organizationId =
        JSONObject(
                mvc.perform(
                        post("/organizations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ORGANIZATION_REQUEST_CREATION_WITH_ACCESSES)
                            .accept(MediaType.APPLICATION_JSON)
                            .with(csrf()))
                    .andReturn()
                    .response
                    .contentAsString)
            .getString("id")

    mvc.perform(
            get("/organizations/$organizationId/security/users")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful)
        .andExpect(jsonPath("$[0]").value(PLATFORM_ADMIN_EMAIL))
        .andExpect(jsonPath("$[1]").value(NEW_USER_ID))
        .andDo(MockMvcResultHandlers.print())
        .andDo(document("organizations/{organization_id}/security/users/GET"))
  }

  @Test
  @WithMockOauth2User
  fun create_organization_with_empty_name() {
    mvc.perform(
            post("/organizations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(EMPTY_NAME_ORGANIZATION_REQUEST_CREATION)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is4xxClientError)
        .andExpect(status().isBadRequest)
        .andExpect(content().string(emptyNameOrganizationCreationRequestError))
        .andDo(MockMvcResultHandlers.print())
  }

  @Test
  @WithMockOauth2User(email = ORGANIZATION_USER_EMAIL, roles = [ROLE_ORGANIZATION_USER])
  fun create_organization_as_organization_user() {
    mvc.perform(
            post("/organizations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(MINIMAL_ORGANIZATION_REQUEST_CREATION)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is4xxClientError)
        .andExpect(status().isForbidden)
        .andExpect(status().reason("Forbidden"))
        .andDo(MockMvcResultHandlers.print())
  }

  @Test
  @WithMockOauth2User(email = ORGANIZATION_USER_EMAIL, roles = [ROLE_ORGANIZATION_USER])
  fun update_organization_as_organization_user() {
    mvc.perform(
            patch("/organizations/my-org-id")
                .contentType(MediaType.APPLICATION_JSON)
                .content(MINIMAL_ORGANIZATION_REQUEST_CREATION)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is4xxClientError)
        .andExpect(status().isForbidden)
        .andExpect(status().reason("Forbidden"))
        .andDo(MockMvcResultHandlers.print())
  }
}
