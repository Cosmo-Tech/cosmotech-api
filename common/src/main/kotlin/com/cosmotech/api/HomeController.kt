// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api

import javax.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HomeController {

  @Value("\${api.swagger-ui.base-path}") private lateinit var swaggerUiBasePath: String

  @GetMapping("/")
  fun redirectHomeToSwaggerUi(httpServletResponse: HttpServletResponse) {
    val pathSeparator = if (swaggerUiBasePath.endsWith("/")) "" else "/"
    httpServletResponse.sendRedirect("${swaggerUiBasePath}${pathSeparator}swagger-ui.html")
  }
}
