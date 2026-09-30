// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.config

import io.mockk.mockk
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.servers.Server
import kotlin.test.Test
import kotlin.test.assertEquals

class CsmOpenAPIConfigurationTests {
  private val configuration = CsmOpenAPIConfiguration(mockk())

  @Test
  fun `advertises the configured relative gateway server`() {
    val openAPI = OpenAPI()

    configuration.configureServers(openAPI, "/tenant-modapi-ci/gateway-api")

    assertEquals(listOf("/tenant-modapi-ci/gateway-api"), openAPI.servers.map(Server::getUrl))
  }

  @Test
  fun `empty server URL leaves server discovery enabled`() {
    val openAPI = OpenAPI()

    configuration.configureServers(openAPI, "")

    assertEquals(emptyList(), openAPI.servers)
  }
}
