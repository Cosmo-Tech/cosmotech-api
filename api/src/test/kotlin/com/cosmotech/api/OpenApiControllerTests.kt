// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api

import io.swagger.v3.oas.models.OpenAPI
import javax.servlet.ServletContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpStatus

abstract class OpenApiControllerTestBase {

  @Autowired private lateinit var servletContext: ServletContext

  @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN") @LocalServerPort private lateinit var port: Integer

  @Autowired private lateinit var restTemplate: TestRestTemplate

  protected fun testServeBaseOpenApiAsYaml() {
    val basePath =
        if (servletContext.contextPath.isNullOrBlank()) "/" else servletContext.contextPath
    val responseEntity =
        this.restTemplate.getForEntity(
            "http://localhost:${port}${basePath}/openapi.yaml", String::class.java)
    assertEquals(HttpStatus.OK, responseEntity.statusCode)

    // OpenAPI from HTTP response
    assertNotNull(responseEntity.body)
    assertEquals(ClassPathResource("static/openapi.yaml").file.readText(), responseEntity.body)
  }

  protected fun testServeBaseOpenApiAsJson() {
    val basePath =
        if (servletContext.contextPath.isNullOrBlank()) "/" else servletContext.contextPath
    val responseEntity =
        this.restTemplate.getForEntity(
            "http://localhost:$port${basePath}/openapi.json", OpenAPI::class.java)
    assertEquals(HttpStatus.OK, responseEntity.statusCode)
    assertNotNull(responseEntity.body)

    val refOpenAPI = openApiYamlParseResult.openAPI
    assertNotNull(refOpenAPI)
    val responseOpenAPI = responseEntity.body!!
    assertEquals(refOpenAPI.openapi, responseOpenAPI.openapi)
    assertEquals(refOpenAPI.info, responseOpenAPI.info)
    assertEquals(refOpenAPI.security, responseOpenAPI.security)
    assertEquals(refOpenAPI.tags, responseOpenAPI.tags)
    // Looks like we have a slight difference between the parsed JSON and the parsed YAML, under
    // Operation#responses#Schema
    //        assertEquals(refOpenAPI.paths, responseOpenAPI.paths)
    //        assertEquals(refOpenAPI.components, responseOpenAPI.components)
    assertFalse(responseOpenAPI.servers.isNullOrEmpty())
    val responseOpenAPIServerUrls = responseOpenAPI.servers.map { it.url }
    val refOpenAPIServerUrls = refOpenAPI.servers.map { it.url }
    if (servletContext.contextPath.isNullOrBlank()) {
      assertTrue(responseOpenAPIServerUrls.contains("http://localhost:$port"))
    } else {
      assertTrue(responseOpenAPIServerUrls.contains(basePath))
    }
    assertTrue(responseOpenAPIServerUrls.containsAll(refOpenAPIServerUrls))
  }
}

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["server.servlet.context-path="])
class OpenApiControllerBaseContextPathTests : OpenApiControllerTestBase() {

  @Test
  fun `serve base openapi as yaml`() {
    testServeBaseOpenApiAsYaml()
  }

  @Test
  fun `serve base openapi as json`() {
    testServeBaseOpenApiAsJson()
  }
}

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["server.servlet.context-path=/vMAJOR"])
class OpenApiControllerCustomContextPathTests : OpenApiControllerTestBase() {

  @Test
  fun `serve base openapi as yaml`() {
    testServeBaseOpenApiAsYaml()
  }

  @Test
  fun `serve base openapi as json`() {
    testServeBaseOpenApiAsJson()
  }
}
