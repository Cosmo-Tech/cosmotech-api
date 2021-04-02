// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api

import org.springframework.context.annotation.Primary
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import springfox.documentation.spring.web.DocumentationCache
import springfox.documentation.spring.web.plugins.DocumentationPluginsManager
import springfox.documentation.swagger.web.InMemorySwaggerResourcesProvider
import springfox.documentation.swagger.web.SwaggerResource

@Component
@Primary
class CsmApiSwaggerResourcesProvider(
    environment: Environment?,
    documentationCache: DocumentationCache?,
    pluginsManager: DocumentationPluginsManager?
) : InMemorySwaggerResourcesProvider(environment, documentationCache, pluginsManager) {

  override fun get(): List<SwaggerResource> {
    return listOf(
        SwaggerResource().apply {
          name = "Main definition"
          url = "/openapi.json"
        })
  }
}
