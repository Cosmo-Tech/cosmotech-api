// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
                 <<< Response : [${response.status}]
                  ${response.contentAsString}
                """.trimIndent())
              }
            }
            .build()
  }

  @Test
  fun `redirects home to Swagger UI`() {
    this.mvc
        .perform(get("/").accept(MediaType.TEXT_HTML))
        .andExpect(status().is3xxRedirection)
        .andExpect { result -> assertEquals("/swagger-ui.html", result.response.redirectedUrl) }
  }
}
