// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.runner.mcp

import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.tool.method.MethodToolCallbackProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class McpServerConfiguration {

  @Bean
  fun myCustomMcpTools(
      organizationMcpBridge: OrganizationMcpBridge,
      runnerMcpBridge: RunnerMcpBridge,
      workspaceMcpBridge: WorkspaceMcpBridge
  ): List<ToolCallback> {
    return MethodToolCallbackProvider.builder()
        .toolObjects(organizationMcpBridge,runnerMcpBridge,workspaceMcpBridge)
        .build()
        .getToolCallbacks()
        .toList()
  }
}
