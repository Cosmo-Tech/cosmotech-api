// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.util.TokenBuffer
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.servers.Server
import io.swagger.v3.parser.OpenAPIV3Parser
import io.swagger.v3.parser.core.models.SwaggerParseResult
import java.util.*
import javax.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

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
class OpenApiController {
  @Value("\${api.version}") private lateinit var apiVersion: String

  @Value("\${api.swagger-ui.base-path}") private lateinit var swaggerUiBasePath: String

  private val openApiParsed: OpenAPI by lazy {
    val openAPI =
        openApiYamlParseResult.openAPI
            ?: throw IllegalStateException(
                "Couldn't parse resource 'classpath:openapi.yaml' : ${openApiYamlParseResult.messages}")
    openAPI.info.version = apiVersion
    openAPI
  }

  @GetMapping("/")
  fun redirectHomeToSwaggerUi(httpServletResponse: HttpServletResponse) {
    val pathSeparator = if (swaggerUiBasePath.endsWith("/")) "" else "/"
    httpServletResponse.sendRedirect("${swaggerUiBasePath}${pathSeparator}swagger-ui/")
  }

  @GetMapping("/openapi.json", produces = [MediaType.APPLICATION_JSON_VALUE])
  @ResponseBody
  fun getOpenAPIJson(): String {
    // Copy parsed OpenAPI to add dynamic base URL
    val objectMapper =
        ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    val tokenBuffer = TokenBuffer(objectMapper, false)
    objectMapper.writeValue(tokenBuffer, openApiParsed)
    val openAPICopy = objectMapper.readValue(tokenBuffer.asParser(), OpenAPI::class.java)

    val openApiEndpointBaseUri =
        swaggerUiBasePath.ifBlank {
          val uriString = ServletUriComponentsBuilder.fromCurrentRequest().build().toUriString()
          uriString.substring(0, uriString.lastIndexOf('/'))
        }
    val serverList =
        mutableListOf<Server>(
            OpenApiServerUrlMatching().apply {
              url = openApiEndpointBaseUri
              description = "inferred"
            })
    openAPICopy.servers?.filterNot { serverList.contains(it) }?.forEach {
      serverList.add(OpenApiServerUrlMatching(it))
    }
    openAPICopy.servers = serverList
    // We are serializing manually because we want in this sole case to avoid returning null fields.
    // Otherwise, Swagger UI may complain on null $ref.
    // But we do want the default object mapper to write null fields.
    return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(openAPICopy)
  }
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
