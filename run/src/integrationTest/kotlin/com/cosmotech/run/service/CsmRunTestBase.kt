// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run.service

import com.cosmotech.api.tests.CsmRedisTestBase
import com.rabbitmq.client.ConnectionFactory.DEFAULT_AMQP_PORT
import com.redis.om.spring.annotations.EnableRedisDocumentRepositories
import com.redis.testcontainers.RedisServer
import com.redis.testcontainers.RedisStackContainer
import com.redis.testcontainers.junit.AbstractTestcontainersRedisTestBase
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.slf4j.LoggerFactory
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.PostgreSQLContainer.POSTGRESQL_PORT
import org.testcontainers.containers.RabbitMQContainer
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnableRedisDocumentRepositories(basePackages = ["com.cosmotech"])
open class CsmRunTestBase : AbstractTestcontainersRedisTestBase() {

  companion object {
    private const val ADMIN_USER_CREDENTIALS = "adminusertest"
    private const val READER_USER_CREDENTIALS = "readusertest"
    private const val WRITER_USER_CREDENTIALS = "writeusertest"
    private const val DEFAULT_REDIS_PORT = 6379
    private const val REDIS_STACK_LASTEST_TAG_WITH_GRAPH = "6.2.6-v9"

    private val logger = LoggerFactory.getLogger(CsmRunTestBase::class.java)

    var postgres: PostgreSQLContainer<*> =
        PostgreSQLContainer("postgres:alpine3.19")
            .withCopyFileToContainer(
                MountableFile.forClasspathResource("init-db.sql"), "/docker-entrypoint-initdb.d/")

    var rabbit: RabbitMQContainer =
        RabbitMQContainer(DockerImageName.parse("rabbitmq:3.7.25-management-alpine"))

    var redisStackServer =
        RedisStackContainer(
            RedisStackContainer.DEFAULT_IMAGE_NAME.withTag(REDIS_STACK_LASTEST_TAG_WITH_GRAPH))

    init {
      redisStackServer.start()
      rabbit.start()
      postgres.start()
    }

    @JvmStatic
    @DynamicPropertySource
    fun connectionProperties(registry: DynamicPropertyRegistry) {
      initPostgresConfiguration(registry)
      initRedisConfiguration(registry)
      initRabbitMQConfiguration(registry)
    }

    private fun initRedisConfiguration(registry: DynamicPropertyRegistry) {
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

    private fun initRabbitMQConfiguration(registry: DynamicPropertyRegistry) {
      registry.add("spring.rabbitmq.host") { rabbit.host }
      registry.add("spring.rabbitmq.port") { rabbit.getMappedPort(DEFAULT_AMQP_PORT) }
      registry.add("spring.rabbitmq.username") { rabbit.adminUsername }
      registry.add("spring.rabbitmq.password") { rabbit.adminPassword }
      registry.add("csm.platform.internalResultServices.eventBus.listener.username") {
        rabbit.adminUsername
      }
      registry.add("csm.platform.internalResultServices.eventBus.listener.password") {
        rabbit.adminPassword
      }
    }

    private fun initPostgresConfiguration(registry: DynamicPropertyRegistry) {
      registry.add("csm.platform.internalResultServices.storage.host") { postgres.host }
      registry.add("csm.platform.internalResultServices.storage.port") {
        postgres.getMappedPort(POSTGRESQL_PORT)
      }
      registry.add("csm.platform.internalResultServices.storage.admin.username") {
        ADMIN_USER_CREDENTIALS
      }
      registry.add("csm.platform.internalResultServices.storage.admin.password") {
        ADMIN_USER_CREDENTIALS
      }
      registry.add("csm.platform.internalResultServices.storage.writer.username") {
        WRITER_USER_CREDENTIALS
      }
      registry.add("csm.platform.internalResultServices.storage.writer.password") {
        WRITER_USER_CREDENTIALS
      }
      registry.add("csm.platform.internalResultServices.storage.reader.username") {
        READER_USER_CREDENTIALS
      }
      registry.add("csm.platform.internalResultServices.storage.reader.password") {
        READER_USER_CREDENTIALS
      }
    }
  }

  @BeforeAll
  fun beforeAll() {
    redisStackServer.start()
    rabbit.start()
    postgres.start()
  }

  @AfterAll
  fun afterAll() {
    rabbit.stop()
    postgres.stop()
  }

  override fun redisServers(): MutableCollection<RedisServer> {
    return mutableListOf(CsmRedisTestBase.redisStackServer)
  }
}
