// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.tests

import com.redis.testcontainers.RedisContainer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.postgresql.PostgreSQLContainer.POSTGRESQL_PORT
import org.testcontainers.utility.MountableFile

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
open class CsmTestBase {

  companion object {
    private const val DEFAULT_REDIS_PORT = 6379

    var postgres: PostgreSQLContainer =
        PostgreSQLContainer("postgres:latest")
            .withCopyFileToContainer(
                MountableFile.forClasspathResource("init-db.sql"),
                "/docker-entrypoint-initdb.d/",
            )

    var redisServer = RedisContainer(RedisContainer.DEFAULT_IMAGE_NAME)

    val seaweedServer =
        GenericContainer("chrislusf/seaweedfs:latest")
            .withEnv(
                mapOf(
                    "AWS_ACCESS_KEY_ID" to "test",
                    "AWS_SECRET_ACCESS_KEY" to "test",
                    "S3_BUCKET" to "test-bucket",
                    "S3_REGION" to "us-east-1",
                )
            )

    init {
      redisServer.start()
      postgres.start()
      seaweedServer.start()
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
          redisServer.containerInfo.networkSettings.networks.entries.elementAt(0).value.ipAddress
              ?: "cannot_find_redis_container_ip"

      registry.add("spring.data.redis.host") { containerIp }
      registry.add("spring.data.redis.port") { DEFAULT_REDIS_PORT }
    }

    private fun initS3Configuration(registry: DynamicPropertyRegistry) {
      val containerIp =
          seaweedServer.containerInfo.networkSettings.networks.entries.elementAt(0).value.ipAddress
              ?: "cannot_find_seaweed_container_ip"
      val seaweedEnvMap = seaweedServer.envMap
      registry.add("spring.cloud.aws.s3.endpoint") {
        "http://$containerIp:8333/${seaweedEnvMap["S3_BUCKET"]!!}"
      }
      registry.add("spring.cloud.aws.credentials.access-key") {
        seaweedEnvMap["AWS_ACCESS_KEY_ID"]!!
      }
      registry.add("spring.cloud.aws.credentials.secret-key") {
        seaweedEnvMap["AWS_SECRET_ACCESS_KEY"]!!
      }
      registry.add("spring.cloud.aws.s3.region") { seaweedEnvMap["S3_REGION"]!! }
    }

    private fun initPostgresConfiguration(registry: DynamicPropertyRegistry) {
      registry.add("csm.platform.databases.data.host") { postgres.host }
      registry.add("csm.platform.databases.data.port") { postgres.getMappedPort(POSTGRESQL_PORT) }
    }
  }

  @BeforeAll
  fun beforeAll() {
    redisServer.start()
    seaweedServer.start()
    postgres.start()
  }

  @BeforeEach
  fun flushAll() {
    redisServer.execInContainer("redis-cli", "flushall")
  }

  @AfterAll
  fun afterAll() {
    postgres.stop()
    seaweedServer.stop()
    redisServer.stop()
  }
}
