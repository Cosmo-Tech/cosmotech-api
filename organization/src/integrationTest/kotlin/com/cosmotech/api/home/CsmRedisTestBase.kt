// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.home

import com.redis.om.spring.annotations.EnableRedisDocumentRepositories
import com.redis.testcontainers.RedisServer
import com.redis.testcontainers.RedisStackContainer
import com.redis.testcontainers.junit.AbstractTestcontainersRedisTestBase
import com.redis.testcontainers.junit.RedisTestContext
import com.redis.testcontainers.junit.RedisTestContextsSource
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.slf4j.LoggerFactory
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@ActiveProfiles(profiles = ["orgatest"])
@EnableRedisDocumentRepositories(basePackages = ["com.cosmotech"])
open class CsmRedisTestBase : AbstractTestcontainersRedisTestBase() {

  companion object {

    @JvmStatic
    val redisStackServer =
        RedisStackContainer(
            RedisStackContainer.DEFAULT_IMAGE_NAME.withTag(RedisStackContainer.DEFAULT_TAG))

    private val logger = LoggerFactory.getLogger(CsmRedisTestBase::class.java)

    init {
      redisStackServer.start()
    }
    @JvmStatic
    @DynamicPropertySource
    fun connectionProperties(registry: DynamicPropertyRegistry) {
      logger.error("Override properties to connect to Testcontainers:")
      val containerIp =
          redisStackServer.containerInfo.networkSettings.networks.entries.elementAt(0)
              .value
              .ipAddress
      logger.error(
          "* Test-Container 'Redis': spring.redis.host = {} ; spring.redis.port = {}",
          containerIp,
          6379)

      registry.add("spring.redis.host") { containerIp }
      registry.add("spring.redis.port") { 6379 }
    }
  }

  override fun redisServers(): MutableCollection<RedisServer> {
    return mutableListOf(redisStackServer)
  }

  @ParameterizedTest
  @RedisTestContextsSource
  fun canPing(context: RedisTestContext) {
    Assertions.assertEquals("PONG", context.sync().ping())
  }

  @ParameterizedTest
  @RedisTestContextsSource
  fun canWrite(context: RedisTestContext) {
    val hash = mutableMapOf<String, String>()
    hash["field1"] = "value1"
    context.sync().hset("hash:test", hash)
    val response = context.sync().hgetall("hash:test")
    Assertions.assertEquals(hash, response)
  }
}
