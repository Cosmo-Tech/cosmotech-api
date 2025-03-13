// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.home.meta

import com.cosmotech.api.home.ControllerTestBase
import com.cosmotech.api.home.annotations.WithMockOauth2User
import kotlin.test.assertEquals
import org.json.JSONObject
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
class MetaControllerTests : ControllerTestBase() {

  private val logger = LoggerFactory.getLogger(MetaControllerTests::class.java)

  @Test
  @WithMockOauth2User
  fun about() {
    mvc.perform(get("/about").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful)
        .andDo {
          val aboutVersion =
              JSONObject(it.getResponse().getContentAsString()).getJSONObject("version")

          var expectedRelease =
              "${aboutVersion.getInt("major")}.${aboutVersion.getInt("minor")}.${aboutVersion.getInt("patch")}"
          val label = aboutVersion.getString("label")
          if (!label.isEmpty()) {
            expectedRelease += "-$label"
          }
          var expectedFull = expectedRelease
          val build = aboutVersion.getString("build")
          if (!build.isEmpty()) {
            expectedFull += "-$build"
          }

          assertEquals(expectedFull, aboutVersion.getString("full"))
          assertEquals(expectedRelease, aboutVersion.getString("release"))
        }
        .andDo(MockMvcResultHandlers.print())
        .andDo(document("about/GET"))
  }
}
