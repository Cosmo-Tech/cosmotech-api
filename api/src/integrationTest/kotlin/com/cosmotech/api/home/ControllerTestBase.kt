// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.home

import com.cosmotech.organization.domain.Organization
import com.cosmotech.solution.domain.Solution
import com.cosmotech.workspace.domain.Workspace
import com.redis.om.spring.RediSearchIndexer
import com.redis.testcontainers.RedisServer
import com.redis.testcontainers.RedisStackContainer
import com.redis.testcontainers.junit.AbstractTestcontainersRedisTestBase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.http.HttpHeaders
import org.springframework.restdocs.RestDocumentationContextProvider
import org.springframework.restdocs.RestDocumentationExtension
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration
import org.springframework.restdocs.operation.preprocess.Preprocessors.*
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
@ExtendWith(RestDocumentationExtension::class)
@AutoConfigureRestDocs
abstract class ControllerTestBase : AbstractTestcontainersRedisTestBase() {

    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    lateinit var rediSearchIndexer: RediSearchIndexer

    lateinit var mvc: MockMvc

    private val logger = LoggerFactory.getLogger(ControllerTestBase::class.java)

    @BeforeEach
    fun beforeEach(restDocumentationContextProvider: RestDocumentationContextProvider) {

        rediSearchIndexer.createIndexFor(Organization::class.java)
        rediSearchIndexer.createIndexFor(Workspace::class.java)
        rediSearchIndexer.createIndexFor(Solution::class.java)

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
                .apply<DefaultMockMvcBuilder>(
                    documentationConfiguration(restDocumentationContextProvider)
                        .operationPreprocessors()
                        .withRequestDefaults(
                            modifyHeaders().remove(HttpHeaders.CONTENT_LENGTH),
                            prettyPrint()
                        )
                        .withResponseDefaults(
                            modifyHeaders()
                                .remove("X-Content-Type-Options")
                                .remove("X-XSS-Protection")
                                .remove("X-Frame-Options")
                                .remove(HttpHeaders.CONTENT_LENGTH)
                                .remove(HttpHeaders.CACHE_CONTROL),
                            prettyPrint()
                        )
                )
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