// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.tests

import com.redis.om.spring.annotations.EnableRedisDocumentRepositories
import com.redis.testcontainers.RedisServer
import com.redis.testcontainers.RedisStackContainer
import com.redis.testcontainers.junit.AbstractTestcontainersRedisTestBase
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.PostgreSQLContainer.POSTGRESQL_PORT
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile

@EnableRedisDocumentRepositories(basePackages = ["com.cosmotech"])
open class CsmTestBase : AbstractTestcontainersRedisTestBase() {

  companion object {
    private const val DEFAULT_REDIS_PORT = 6379
    private const val LOCALSTACK_FULL_IMAGE_NAME = "localstack/localstack:latest"

    var postgres: PostgreSQLContainer<*> =
        PostgreSQLContainer("postgres:latest")
            .withCopyFileToContainer(
                MountableFile.forClasspathResource("init-db.sql"),
                "/docker-entrypoint-initdb.d/",
            )

    var redisStackServer = RedisStackContainer(RedisStackContainer.DEFAULT_IMAGE_NAME)

    val localStackServer =
        LocalStackContainer(DockerImageName.parse(LOCALSTACK_FULL_IMAGE_NAME))
            .withServices(LocalStackContainer.Service.S3)

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
    postgres.start()
  }

  @AfterAll
  fun afterAll() {
    postgres.stop()
  }

  override fun redisServers(): MutableCollection<RedisServer> {
    return mutableListOf(redisStackServer)
  }
}
