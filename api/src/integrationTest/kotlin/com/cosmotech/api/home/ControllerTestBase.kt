// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.home

import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.organization.domain.Organization
import com.cosmotech.run.domain.Run
import com.cosmotech.runner.domain.Runner
import com.cosmotech.solution.domain.Solution
import com.cosmotech.workspace.domain.Workspace
import com.redis.om.spring.RediSearchIndexer
import com.redis.testcontainers.RedisServer
import com.redis.testcontainers.RedisStackContainer
import com.redis.testcontainers.junit.AbstractTestcontainersRedisTestBase
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.http.HttpHeaders
import org.springframework.restdocs.RestDocumentationContextProvider
import org.springframework.restdocs.RestDocumentationExtension
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration
import org.springframework.restdocs.operation.preprocess.Preprocessors.*
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.PostgreSQLContainer.POSTGRESQL_PORT
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile

@Testcontainers
@EnableWebSecurity
@ExtendWith(RestDocumentationExtension::class)
@AutoConfigureRestDocs
abstract class ControllerTestBase : AbstractTestcontainersRedisTestBase() {

  @Autowired private lateinit var context: WebApplicationContext

  @Autowired lateinit var rediSearchIndexer: RediSearchIndexer

  lateinit var mvc: MockMvc

  private val logger = LoggerFactory.getLogger(ControllerTestBase::class.java)

  @BeforeEach
  fun beforeEach(restDocumentationContextProvider: RestDocumentationContextProvider) {

    rediSearchIndexer.createIndexFor(Organization::class.java)
    rediSearchIndexer.createIndexFor(Workspace::class.java)
    rediSearchIndexer.createIndexFor(Dataset::class.java)
    rediSearchIndexer.createIndexFor(Solution::class.java)
    rediSearchIndexer.createIndexFor(Runner::class.java)
    rediSearchIndexer.createIndexFor(Run::class.java)

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
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .apply<DefaultMockMvcBuilder>(
                documentationConfiguration(restDocumentationContextProvider)
                    .operationPreprocessors()
                    .withRequestDefaults(
                        modifyHeaders().remove(HttpHeaders.CONTENT_LENGTH), prettyPrint())
                    .withResponseDefaults(
                        modifyHeaders()
                            .remove("X-Content-Type-Options")
                            .remove("X-XSS-Protection")
                            .remove("X-Frame-Options")
                            .remove(HttpHeaders.CONTENT_LENGTH)
                            .remove(HttpHeaders.CACHE_CONTROL),
                        prettyPrint()))
            .build()
  }

  companion object {
    private const val ADMIN_USER_CREDENTIALS = "adminusertest"
    private const val READER_USER_CREDENTIALS = "readusertest"
    private const val WRITER_USER_CREDENTIALS = "writeusertest"
    private const val DEFAULT_REDIS_PORT = 6379
    private const val REDIS_STACK_LATEST_TAG_WITH_GRAPH = "6.2.6-v18"
    private const val LOCALSTACK_FULL_IMAGE_NAME = "localstack/localstack:3.5.0"

    var postgres: PostgreSQLContainer<*> =
        PostgreSQLContainer("postgres:alpine3.19")
            .withCopyFileToContainer(
                MountableFile.forClasspathResource("init-db.sql"), "/docker-entrypoint-initdb.d/")

    var redisStackServer =
        RedisStackContainer(
            RedisStackContainer.DEFAULT_IMAGE_NAME.withTag(REDIS_STACK_LATEST_TAG_WITH_GRAPH))

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
