package com.cosmotech.api.home

import com.cosmotech.api.tests.CsmRedisTestBase
import com.redis.testcontainers.RedisServer
import com.redis.testcontainers.RedisStackContainer
import com.redis.testcontainers.junit.AbstractTestcontainersRedisTestBase
import org.junit.jupiter.api.BeforeEach
import org.slf4j.LoggerFactory
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
abstract class ControllerTestBase(private val context: WebApplicationContext) : AbstractTestcontainersRedisTestBase() {

    lateinit var mvc: MockMvc

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