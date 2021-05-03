// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api

import io.swagger.v3.oas.annotations.Hidden
import javax.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
@Hidden
class HomeController {

  @Value("\${api.swagger-ui.base-path:}") private lateinit var swaggerUiBasePath: String

  @GetMapping(value = ["/"], produces = [MediaType.TEXT_HTML_VALUE])
  fun redirectHomeToSwaggerUi(httpServletResponse: HttpServletResponse) {
    val pathSeparator = if (swaggerUiBasePath.endsWith("/")) "" else "/"
    httpServletResponse.sendRedirect("${swaggerUiBasePath}${pathSeparator}swagger-ui.html")
  }
}
