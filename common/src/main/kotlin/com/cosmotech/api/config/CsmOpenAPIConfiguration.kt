// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.parser.OpenAPIV3Parser
import java.io.BufferedReader
import java.util.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CsmOpenAPIConfiguration {

  @Value("\${api.version:?}") private lateinit var apiVersion: String

  @Bean
  fun csmOpenAPI(): OpenAPI {

    val openApiYamlInputStream =
        CsmOpenAPIConfiguration::class.java.getResourceAsStream("/static/openapi.yaml")
            ?: throw IllegalStateException(
                "Unable to parse OpenAPI definition from 'classpath:/static/openapi.yaml'")
    val openApiYamlContent =
        openApiYamlInputStream.use { it.bufferedReader().use(BufferedReader::readText) }
    val openApiYamlParseResult = OpenAPIV3Parser().readContents(openApiYamlContent)
    if (!openApiYamlParseResult.messages.isNullOrEmpty()) {
      throw IllegalStateException(
          "Unable to parse OpenAPI definition from 'classpath:/static/openapi.yaml' : ${openApiYamlParseResult.messages}")
    }
    val openAPI =
        openApiYamlParseResult.openAPI
            ?: throw IllegalStateException(
                "Couldn't parse resource 'classpath:openapi.yaml' : ${openApiYamlParseResult.messages}")

    openAPI.info.version = apiVersion

    // Remove any set of servers already defined in the input openapi.yaml,
    // so as to have the base URL auto-generated based on the incoming requests
    openAPI.servers = listOf()

    return openAPI
  }
}
