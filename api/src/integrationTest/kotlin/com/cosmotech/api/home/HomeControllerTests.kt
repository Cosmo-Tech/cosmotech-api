// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.home

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.slf4j.LoggerFactory
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@ActiveProfiles(profiles = ["test"])
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HomeControllerTests(private val context: WebApplicationContext) {

  private val logger = LoggerFactory.getLogger(HomeControllerTests::class.java)

  private lateinit var mvc: MockMvc

  @BeforeEach
  fun beforeEach() {
    this.mvc =
        MockMvcBuilders.webAppContextSetup(context)
            .alwaysDo<DefaultMockMvcBuilder> { result ->
              if (logger.isTraceEnabled) {
                val response = result.response
                logger.trace(
                    """
                 <<< Response : 
                 [${response.status}]
                 ${response.headerNames.associateWith { response.getHeaderValues(it) }.entries.joinToString("\n")}}
                    
                  ${response.contentAsString}
                """
                        .trimIndent())
              }
            }
            .build()
  }

  @TestFactory
  fun `redirects to Swagger UI from home if accepting HTML`(): Collection<DynamicTest> =
      listOf("/", "/index.html").map { path ->
        DynamicTest.dynamicTest(path) {
          this.mvc
              .perform(get(path).accept(MediaType.TEXT_HTML))
              .andExpect(status().is3xxRedirection)
              .andExpect { result ->
                assertEquals("/swagger-ui.html", result.response.redirectedUrl)
              }
        }
      }

  @TestFactory
  fun `redirects to openapi if accepting JSON`(): Collection<DynamicTest> =
      listOf("/", "/openapi.json").map { path ->
        DynamicTest.dynamicTest(path) {
          this.mvc
              .perform(get("/").accept(MediaType.APPLICATION_JSON_VALUE))
              .andExpect(status().is3xxRedirection)
              .andExpect { result -> assertEquals("/openapi", result.response.redirectedUrl) }
        }
      }
}
