// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.tests

import com.redis.testcontainers.RedisStackContainer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.localstack.LocalStackContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.postgresql.PostgreSQLContainer.POSTGRESQL_PORT
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
open class CsmTestBase {

  companion object {
    private const val DEFAULT_REDIS_PORT = 6379
    private const val LOCALSTACK_FULL_IMAGE_NAME = "localstack/localstack:4.14.0"

    var postgres: PostgreSQLContainer =
        PostgreSQLContainer("postgres:latest")
            .withCopyFileToContainer(
                MountableFile.forClasspathResource("init-db.sql"),
                "/docker-entrypoint-initdb.d/",
            )

    var redisStackServer = RedisStackContainer(RedisStackContainer.DEFAULT_IMAGE_NAME)

    val localStackServer =
        LocalStackContainer(DockerImageName.parse(LOCALSTACK_FULL_IMAGE_NAME)).withServices("s3")

    init {
      redisStackServer.start()
      postgres.start()
      localStackServer.start()
      localStackServer.execInContainer("awslocal", "s3", "mb", "s3://test-bucket")
    }

    @JvmStatic
    @DynamicPropertySource
    fun connectionProperties(registry: DynamicPropertyRegistry) {
      initPostgresConfiguration(registry)
      initRedisConfiguration(registry)
      initS3Configuration(registry)
    }

    private fun initRedisConfiguration(registry: DynamicPropertyRegistry) {
      val containerIp =
          redisStackServer.containerInfo.networkSettings.networks.entries
              .elementAt(0)
              .value
              .ipAddress ?: "cannot_find_redis_container_ip"

      registry.add("spring.data.redis.host") { containerIp }
      registry.add("spring.data.redis.port") { DEFAULT_REDIS_PORT }
    }

    private fun initS3Configuration(registry: DynamicPropertyRegistry) {
      registry.add("spring.cloud.aws.s3.endpoint") { localStackServer.endpoint }
      registry.add("spring.cloud.aws.credentials.access-key") { localStackServer.accessKey }
      registry.add("spring.cloud.aws.credentials.secret-key") { localStackServer.secretKey }
      registry.add("spring.cloud.aws.s3.region") { localStackServer.region }
    }

    private fun initPostgresConfiguration(registry: DynamicPropertyRegistry) {
      registry.add("csm.platform.databases.data.host") { postgres.host }
      registry.add("csm.platform.databases.data.port") { postgres.getMappedPort(POSTGRESQL_PORT) }
    }
  }

  @BeforeAll
  fun beforeAll() {
    redisStackServer.start()
    localStackServer.start()
    postgres.start()
  }

  @BeforeEach
  fun flushAll() {
    redisStackServer.execInContainer("redis-cli", "flushall")
  }

  @AfterAll
  fun afterAll() {
    postgres.stop()
    localStackServer.stop()
    redisStackServer.stop()
  }
}
