package com.cosmotech.api

import java.io.File
import java.nio.charset.StandardCharsets
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.http.HttpStatus

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Disabled("Using 'com.rameshkp.openapi-merger-gradle-plugin' instead")
class GenerateSwaggerTests {

  private val logger = LoggerFactory.getLogger(GenerateSwaggerTests::class.java)

  @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN") @LocalServerPort private lateinit var port: Integer

  @Autowired private lateinit var restTemplate: TestRestTemplate

  @Test
  fun `generate openapi spec`() {
    // At the time of writing, only JSON is supported as output by SpringFox.
    // cf. this PR (https://github.com/springfox/springfox/pull/2102 ) for YAML support.
    val apiDocsResponseEntity =
        this.restTemplate.getForEntity("http://localhost:$port/v3/api-docs", String::class.java)
    assertEquals(HttpStatus.OK, apiDocsResponseEntity.statusCode)
    val apiDocs = apiDocsResponseEntity.body!!
    assertNotNull(apiDocs)
    assertFalse(apiDocs.isBlank())
    // TODO Generate in the Gradle build dir instead
    val outputFile =
        File(System.getProperty("java.io.tmpdir", "/tmp"), "cosmotech-api.openapi.json")
    outputFile.writeText(apiDocs, StandardCharsets.UTF_8)
    logger.info(
        "/!\\ Cosmo Tech resulting OpenAPI specification file generated at: ${outputFile.absolutePath}")
  }
}
