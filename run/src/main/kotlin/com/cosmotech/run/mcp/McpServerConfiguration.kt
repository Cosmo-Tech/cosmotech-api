// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run.mcp

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
      workspaceMcpBridge: WorkspaceMcpBridge,
      solutionMcpBridge: SolutionMcpBridge,
      runMcpBridge: RunMcpBridge,
  ): List<ToolCallback> {
    return MethodToolCallbackProvider.builder()
        .toolObjects(organizationMcpBridge, runnerMcpBridge, workspaceMcpBridge,solutionMcpBridge,runMcpBridge)
        .build()
        .getToolCallbacks()
        .toList()
  }
}
