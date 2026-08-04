// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.runner.mcp

import com.cosmotech.organization.api.OrganizationApiService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

@Component
class OrganizationMcpBridge(private val organizationApiService: OrganizationApiService) {

  @Tool(name = "list_organizations", description = "List all organizations")
  fun listOrganizations(
      @ToolParam(description = "The index of the page to retrieve (default: 0)", required = false)
      page: Int? = 0,
      @ToolParam(
          description = "The number of organizations per page (default: 10)",
          required = false,
      )
      size: Int? = 10,
  ): String {
    return organizationApiService.listOrganizations(page, size).joinToString { organization ->
      StringBuilder().append(organization.id).append(" - ").append(organization.name)
    }
  }
}
