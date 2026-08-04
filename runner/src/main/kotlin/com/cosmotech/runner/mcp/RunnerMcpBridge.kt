// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.runner.mcp

import com.cosmotech.runner.api.RunnerApiService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

@Component
class RunnerMcpBridge(private val runnerApiService: RunnerApiService) {

  @Tool(name = "start_simulation", description = "Start a simulation")
  fun startSimulation(
      @ToolParam(description = "The organization id", required = true) organizationId: String,
      @ToolParam(description = "The workspace id", required = true) workspaceId: String,
      @ToolParam(description = "The runner id", required = true) runnerId: String,
  ): String {
    val runId = runnerApiService.startRun(organizationId, workspaceId, runnerId)
    return "Simulation run id : $runId"
  }


    @Tool(name = "list_runners", description = "List all runners")
    fun listRunners(
        @ToolParam(description = "The organization id", required = true)
        organizationId: String,
        @ToolParam(description = "The workspace id", required = true)
        workspaceId: String,
        @ToolParam(description = "The index of the page to retrieve (default: 0)", required = false)
        page: Int? = 0,
        @ToolParam(
            description = "The number of workspace per page (default: 10)",
            required = false,
        )
        size: Int? = 10,
    ): String {
        return runnerApiService.listRunners(organizationId,workspaceId,page, size).joinToString { runner ->
            StringBuilder().append(runner.id).append(" - ").append(runner.name)
        }
    }
}
