// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run.service

import com.cosmotech.api.tests.CsmRedisTestBase
import com.rabbitmq.client.ConnectionFactory.DEFAULT_AMQP_PORT
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
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
open class CsmRunTestBase : CsmRedisTestBase() {

  companion object {
    private const val ADMIN_USER_CREDENTIALS = "adminusertest"
    private const val READER_USER_CREDENTIALS = "readusertest"
    private const val WRITER_USER_CREDENTIALS = "writeusertest"

    var postgres: PostgreSQLContainer<*> =
        PostgreSQLContainer("postgres:alpine3.19")
            .withCopyFileToContainer(
                MountableFile.forClasspathResource("init-db.sql"), "/docker-entrypoint-initdb.d/")

    var rabbit: RabbitMQContainer =
        RabbitMQContainer(DockerImageName.parse("rabbitmq:3.7.25-management-alpine"))

    init {
      rabbit.start()
      postgres.start()
    }

    @JvmStatic
    @DynamicPropertySource
    fun connectionProperties(registry: DynamicPropertyRegistry) {
      initPostgresConfiguration(registry)
      initRabbitMQConfiguration(registry)
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
    rabbit.start()
    postgres.start()
  }

  @AfterAll
  fun afterAll() {
    rabbit.stop()
    postgres.stop()
  }
}
