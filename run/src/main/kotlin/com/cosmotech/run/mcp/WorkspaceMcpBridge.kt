// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run.mcp

import com.cosmotech.workspace.api.WorkspaceApiService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

@Component
class WorkspaceMcpBridge(private val workspaceApiService: WorkspaceApiService) {

  @Tool(name = "list_workspaces", description = "List all workspaces")
  fun listWorkspaces(
      @ToolParam(description = "The organization id", required = true) organizationId: String,
      @ToolParam(description = "The index of the page to retrieve (default: 0)", required = false)
      page: Int? = 0,
      @ToolParam(
          description = "The number of workspace per page (default: 10)",
          required = false,
      )
      size: Int? = 10,
  ): String {
    return workspaceApiService.listWorkspaces(organizationId, page, size).joinToString { workspace
      ->
      StringBuilder().append(workspace.id).append(" - ").append(workspace.name)
    }
  }
}
