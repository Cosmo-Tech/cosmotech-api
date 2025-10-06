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
    private const val READER_USER_CREDENTIALS = "readusertest"
    private const val WRITER_USER_CREDENTIALS = "writeusertest"
    private const val DEFAULT_REDIS_PORT = 6379
    private const val LOCALSTACK_FULL_IMAGE_NAME = "localstack/localstack:3.5.0"

    var postgres: PostgreSQLContainer<*> =
        PostgreSQLContainer("postgres:alpine3.19")
            .withCopyFileToContainer(
                MountableFile.forClasspathResource("init-db.sql"), "/docker-entrypoint-initdb.d/")

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
              .ipAddress

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
      registry.add("csm.platform.databases.data.writer.username") { WRITER_USER_CREDENTIALS }
      registry.add("csm.platform.databases.data.writer.password") { WRITER_USER_CREDENTIALS }
      registry.add("csm.platform.databases.data.reader.username") { READER_USER_CREDENTIALS }
      registry.add("csm.platform.databases.data.reader.password") { READER_USER_CREDENTIALS }
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
