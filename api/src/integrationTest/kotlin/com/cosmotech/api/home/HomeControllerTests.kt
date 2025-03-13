// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.home

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@ActiveProfiles(profiles = ["test"])
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HomeControllerTests : ControllerTestBase() {

  @Test
  fun `redirects to Swagger UI from home if accepting HTML`() {
    this.mvc
        .perform(get("/").accept(MediaType.TEXT_HTML))
        .andExpect(status().is3xxRedirection)
        .andExpect { result -> assertEquals("/swagger-ui.html", result.response.redirectedUrl) }
        .andDo(MockMvcResultHandlers.print())
  }

  @Test
  fun `redirects to Swagger UI from swagger-ui if accepting HTML`() {
    this.mvc
        .perform(get("/swagger-ui.html").accept(MediaType.TEXT_HTML))
        .andExpect(status().is3xxRedirection)
        .andExpect { result ->
          assertEquals("/swagger-ui/index.html", result.response.redirectedUrl)
        }
        .andDo(MockMvcResultHandlers.print())
  }

  @TestFactory
  fun `redirects to openapi if accepting JSON`(): Collection<DynamicTest> =
      listOf("/", "/openapi.json").map { path ->
        DynamicTest.dynamicTest(path) {
          this.mvc
              .perform(get("/").accept(MediaType.APPLICATION_JSON_VALUE))
              .andExpect(status().is3xxRedirection)
              .andExpect { result -> assertEquals("/openapi", result.response.redirectedUrl) }
              .andDo(MockMvcResultHandlers.print())
        }
      }
}
