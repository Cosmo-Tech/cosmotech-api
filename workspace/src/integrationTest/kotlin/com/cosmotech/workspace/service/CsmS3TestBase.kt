// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.workspace.service

import com.redis.om.spring.annotations.EnableRedisDocumentRepositories
import com.redis.testcontainers.RedisServer
import com.redis.testcontainers.RedisStackContainer
import com.redis.testcontainers.junit.AbstractTestcontainersRedisTestBase
import com.redis.testcontainers.junit.RedisTestContext
import com.redis.testcontainers.junit.RedisTestContextsSource
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.slf4j.LoggerFactory
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName

@EnableRedisDocumentRepositories(basePackages = ["com.cosmotech"])
open class CsmS3TestBase : AbstractTestcontainersRedisTestBase() {

  companion object {

    private const val DEFAULT_REDIS_PORT = 6379

    private const val REDIS_STACK_LASTEST_TAG_WITH_GRAPH = "6.2.6-v9"

    @JvmStatic
    val redisStackServer =
        RedisStackContainer(
            RedisStackContainer.DEFAULT_IMAGE_NAME.withTag(REDIS_STACK_LASTEST_TAG_WITH_GRAPH))

    @JvmStatic
    val localStackServer =
        LocalStackContainer(DockerImageName.parse("localstack/localstack:3.5.0"))
            .withServices(LocalStackContainer.Service.S3)

    private val logger = LoggerFactory.getLogger(CsmS3TestBase::class.java)

    init {
      redisStackServer.start()
      localStackServer.start()
      localStackServer.execInContainer("awslocal", "s3", "mb", "s3://test-bucket")
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
      registry.add("spring.cloud.aws.s3.endpoint") { localStackServer.endpoint }
      registry.add("spring.cloud.aws.credentials.access-key") { localStackServer.accessKey }
      registry.add("spring.cloud.aws.credentials.secret-key") { localStackServer.secretKey }
      registry.add("spring.cloud.aws.s3.region") { localStackServer.region }
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
