// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run.service

import com.cosmotech.api.tests.CsmRedisTestBase
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.PostgreSQLContainer.POSTGRESQL_PORT
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.MountableFile

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
open class CsmPostgresTestBase : CsmRedisTestBase() {

  companion object {
    private const val ADMIN_USER_CREDENTIALS = "adminusertest"
    private const val READER_USER_CREDENTIALS = "readusertest"
    private const val WRITER_USER_CREDENTIALS = "writeusertest"

    var postgres: PostgreSQLContainer<*> =
        PostgreSQLContainer("postgres:alpine3.19")
            .withCopyFileToContainer(
                MountableFile.forClasspathResource("init-db.sql"), "/docker-entrypoint-initdb.d/")

    init {
      postgres.start()
    }

    @JvmStatic
    @DynamicPropertySource
    fun connectionProperties(registry: DynamicPropertyRegistry) {
      registry.add("csm.platform.storage.host") { postgres.host }
      registry.add("csm.platform.storage.port") { postgres.getMappedPort(POSTGRESQL_PORT) }
      registry.add("csm.platform.storage.admin.username") { ADMIN_USER_CREDENTIALS }
      registry.add("csm.platform.storage.admin.password") { ADMIN_USER_CREDENTIALS }
      registry.add("csm.platform.storage.writer.username") { WRITER_USER_CREDENTIALS }
      registry.add("csm.platform.storage.writer.password") { WRITER_USER_CREDENTIALS }
      registry.add("csm.platform.storage.reader.username") { READER_USER_CREDENTIALS }
      registry.add("csm.platform.storage.reader.password") { READER_USER_CREDENTIALS }
    }
  }

  @BeforeAll
  fun beforeAll() {
    postgres.start()
  }

  @AfterAll
  fun afterAll() {
    postgres.stop()
  }
}
