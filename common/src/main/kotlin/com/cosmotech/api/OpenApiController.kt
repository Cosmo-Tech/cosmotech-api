// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.servers.Server
import io.swagger.v3.parser.OpenAPIV3Parser
import io.swagger.v3.parser.core.models.SwaggerParseResult
import java.util.*
import javax.servlet.ServletContext
import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController

val openApiYamlParseResult: SwaggerParseResult by lazy {
  val parseResult =
      OpenAPIV3Parser().readContents(ClassPathResource("static/openapi.yaml").file.readText())
  if (!parseResult.messages.isNullOrEmpty()) {
    throw IllegalStateException(
        "Unable to parse OpenAPI definition from 'classpath:openapi.yaml' : ${parseResult.messages}")
  }
  parseResult
}

@RestController
class OpenApiController(val servletContext: ServletContext) {

  private val openApiParsed: OpenAPI by lazy {
    val openAPI =
        openApiYamlParseResult.openAPI
            ?: throw IllegalStateException(
                "Unable to parse OpenAPI definition from 'classpath:openapi.yaml' : ${openApiYamlParseResult.messages}")
    val serverList =
        mutableListOf<Server>(
            OpenApiServerUrlMatching().apply {
              val contextPath = servletContext.contextPath
              url = if (contextPath.isNullOrBlank()) "/" else contextPath
              description = "inferred"
            })
    openAPI.servers?.filterNot { serverList.contains(it) }?.forEach {
      serverList.add(OpenApiServerUrlMatching(it))
    }
    openAPI.servers = serverList
    openAPI
  }

  @GetMapping("/openapi.json", produces = [MediaType.APPLICATION_JSON_VALUE])
  @ResponseBody
  fun getOpenAPIJson() = openApiParsed
}

private class OpenApiServerUrlMatching() : Server() {

  constructor(server: Server) : this() {
    this.url = server.url
    this.description = server.description
    this.extensions = server.extensions
    this.variables = server.variables
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }
    if (other !is Server) {
      return false
    }
    return this.url?.equals(other.url) == true
  }

  override fun hashCode(): Int {
    return Objects.hashCode(this.url)
  }
}
