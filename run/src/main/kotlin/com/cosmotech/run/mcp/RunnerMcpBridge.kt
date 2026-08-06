// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run.mcp

import com.cosmotech.runner.api.RunnerApiService
import com.cosmotech.runner.domain.Runner
import com.cosmotech.runner.domain.RunnerCreateRequest
import com.cosmotech.runner.domain.RunnerRunTemplateParameterValue
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
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
      @ToolParam(description = "The organization id", required = true) organizationId: String,
      @ToolParam(description = "The workspace id", required = true) workspaceId: String,
      @ToolParam(description = "The index of the page to retrieve (default: 0)", required = false)
      page: Int? = 0,
      @ToolParam(
          description = "The number of workspace per page (default: 10)",
          required = false,
      )
      size: Int? = 10,
  ): String {
    return runnerApiService.listRunners(organizationId, workspaceId, page, size).joinToString {
        runner ->
      StringBuilder().append(runner.id).append(" - ").append(runner.name)
    }
  }

    @Tool(name = "create_runner", description = "Create a runner")
    fun createRunner(
        @ToolParam(description = "The organization id", required = true) organizationId: String,
        @ToolParam(description = "The workspace id", required = true) workspaceId: String,
        @ToolParam(description = "The runner name", required = true)  name: String,
        @ToolParam(description = "The solution id", required = true)  solutionId: String,
        @ToolParam(description = "The runtemplate id", required = true)  runTemplateId: String,
        @ToolParam(description = "The runner description", required = false)  description: String? = null,
        @ToolParam(description = "The dataset list ids", required = false)  datasetList: MutableList<String>? = arrayListOf(),
        @ToolParam(description = "The parameters map: keys are parameterId and values are parameterValues id", required = false)  parametersMaps: Map<String,String>? = emptyMap()
    ): String {
        val runnerCreateRequest = RunnerCreateRequest(
            name = name,
            description = description,
            runTemplateId = runTemplateId,
            solutionId = solutionId,
            datasetList = datasetList,
            parametersValues = parametersMaps?.map { RunnerRunTemplateParameterValue(it.key, it.value) }?.toMutableList()
        )
        val runner = runnerApiService.createRunner(organizationId, workspaceId, runnerCreateRequest)
        return runner.toString()
    }
}
