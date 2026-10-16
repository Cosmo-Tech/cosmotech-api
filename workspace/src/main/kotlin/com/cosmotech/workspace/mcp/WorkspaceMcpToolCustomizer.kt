// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.workspace.mcp

import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import org.springdoc.ai.customizers.McpToolCustomizer
import org.springdoc.ai.customizers.McpToolDefinitionContext
import org.springframework.stereotype.Component

/** Drops springdoc-generated tools replaced by [WorkspaceFileMcpTools]. */
@Component
class WorkspaceMcpToolCustomizer : McpToolCustomizer {

  private val replacedOperationIds = setOf("createWorkspaceFile")

  override fun customize(
      context: McpToolDefinitionContext,
      path: String,
      method: PathItem.HttpMethod,
      operation: Operation,
  ): McpToolDefinitionContext {
    if (operation.operationId in replacedOperationIds) {
      context.isExclude = true
    }
    return context
  }
}
