// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.home

import com.cosmotech.api.home.annotations.WithMockOauth2User
import com.cosmotech.api.tests.CsmRedisTestBase
import com.redis.testcontainers.RedisServer
import com.redis.testcontainers.RedisStackContainer
import com.redis.testcontainers.junit.AbstractTestcontainersRedisTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.slf4j.LoggerFactory
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertNotNull


@ActiveProfiles(profiles = ["test"])
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class HomeControllerTests(private val context: WebApplicationContext) : AbstractTestcontainersRedisTestBase() {

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

    @Nested
    inner class Organization {

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
                .andExpect(jsonPath("$.ownerId").value("test-subject"))
                .andReturn().response.contentAsString
            assertNotNull(contentAsString)
        }

    }

    companion object {

        private const val DEFAULT_REDIS_PORT = 6379

        private const val REDIS_STACK_LASTEST_TAG_WITH_GRAPH = "6.2.6-v18"

        @JvmStatic
        val redisStackServer =
            RedisStackContainer(
                RedisStackContainer.DEFAULT_IMAGE_NAME.withTag(REDIS_STACK_LASTEST_TAG_WITH_GRAPH))

        private val logger = LoggerFactory.getLogger(CsmRedisTestBase::class.java)

        init {
            redisStackServer.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun connectionProperties(registry: DynamicPropertyRegistry) {
            logger.error("Override properties to connect to Testcontainers:")
            val containerIp =
                redisStackServer.containerInfo.networkSettings.networks.entries
                    .elementAt(0)
                    .value
                    .ipAddress
            logger.error(
                "* Test-Container 'Redis': spring.data.redis.host = {} ; spring.data.redis.port = {}",
                containerIp,
                DEFAULT_REDIS_PORT)

            registry.add("spring.data.redis.host") { containerIp }
            registry.add("spring.data.redis.port") { DEFAULT_REDIS_PORT }
        }
    }


    override fun redisServers(): MutableCollection<RedisServer> {
        return mutableListOf(redisStackServer)
    }

}

