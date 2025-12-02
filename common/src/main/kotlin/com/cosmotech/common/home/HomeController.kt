// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.home

import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
internal class HomeController(
    @Value("\${server.servlet.context-path:}") private val servletContextPath: String
) {

  private val baseEndpoint = servletContextPath.substringBeforeLast("/")

  @GetMapping(value = ["/", "/index.html"], produces = [MediaType.TEXT_HTML_VALUE])
  fun redirectHomeToSwaggerUi(httpServletResponse: HttpServletResponse) {
    httpServletResponse.sendRedirect("$baseEndpoint/swagger-ui.html")
  }

  @GetMapping(
      value =
          [
              "/",
          ],
      produces = [MediaType.APPLICATION_JSON_VALUE],
  )
  fun redirectHomeToOpenApi(httpServletResponse: HttpServletResponse) {
    redirectOpenApiJsonToOpenApi(httpServletResponse)
  }

  @GetMapping(value = ["/openapi.json"])
  fun redirectOpenApiJsonToOpenApi(httpServletResponse: HttpServletResponse) {
    httpServletResponse.sendRedirect("$baseEndpoint/openapi")
  }
}
