// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run.mcp

import com.cosmotech.run.api.RunApiService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

@Component
class RunMcpBridge(private val runApiService: RunApiService) {

  @Tool(name = "get_run_status", description = "Get the status of a simulation run")
  fun get_simulation_run_status(
      @ToolParam(description = "The organization id", required = true) organizationId: String,
      @ToolParam(description = "The workspace id", required = true) workspaceId: String,
      @ToolParam(description = "The runner id", required = true) runnerId: String,
      @ToolParam(description = "The run id", required = true) runId: String,
  ): String {
    val runStatus = runApiService.getRunStatus(organizationId, workspaceId, runnerId,runId)
    return "Simulation run status : ${runStatus.state?.value}"
  }

}
