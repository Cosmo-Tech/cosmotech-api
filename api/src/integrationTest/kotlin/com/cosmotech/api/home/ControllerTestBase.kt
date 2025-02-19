// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.home

import com.redis.testcontainers.RedisServer
import com.redis.testcontainers.RedisStackContainer
import com.redis.testcontainers.junit.AbstractTestcontainersRedisTestBase
import org.junit.jupiter.api.BeforeEach
import org.slf4j.LoggerFactory
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@EnableWebSecurity
abstract class ControllerTestBase(private val context: WebApplicationContext) : AbstractTestcontainersRedisTestBase() {

    lateinit var mvc: MockMvc
    private val logger = LoggerFactory.getLogger(ControllerTestBase::class.java)

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
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
    }


    companion object {

        private const val DEFAULT_REDIS_PORT = 6379

        private const val REDIS_STACK_LASTEST_TAG_WITH_GRAPH = "6.2.6-v18"

        @JvmStatic
        val redisStackServer =
            RedisStackContainer(
                RedisStackContainer.DEFAULT_IMAGE_NAME.withTag(REDIS_STACK_LASTEST_TAG_WITH_GRAPH))

        init {
            redisStackServer.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun connectionProperties(registry: DynamicPropertyRegistry) {
            val containerIp =
                redisStackServer.containerInfo.networkSettings.networks.entries
                    .elementAt(0)
                    .value
                    .ipAddress
            registry.add("spring.data.redis.host") { containerIp }
            registry.add("spring.data.redis.port") { DEFAULT_REDIS_PORT }
        }
    }


    override fun redisServers(): MutableCollection<RedisServer> {
        return mutableListOf(redisStackServer)
    }
}