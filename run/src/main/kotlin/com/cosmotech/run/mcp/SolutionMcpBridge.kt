// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run.mcp

import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.workspace.api.WorkspaceApiService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

@Component
class SolutionMcpBridge(private val solutionApiService: SolutionApiService) {

  @Tool(name = "list_solutions", description = "List all solutions")
  fun listSolutions(
      @ToolParam(description = "The organization id", required = true) organizationId: String,
      @ToolParam(description = "The index of the page to retrieve (default: 0)", required = false)
      page: Int? = 0,
      @ToolParam(
          description = "The number of solution per page (default: 10)",
          required = false,
      )
      size: Int? = 10,
  ): String {
    return solutionApiService.listSolutions(organizationId, page, size).joinToString { solution
      ->
      StringBuilder("Solution id: ").append(solution.id)
          .append(" - Solution name: ").append(solution.name)
          .append(" - Solution run template list: ").append(
              solution.runTemplates.joinToString(prefix = "{", postfix = "}", separator = ",", transform =  {"runtemplate: id-> ${it.id} - name -> ${it.name} - description -> ${it.description}"})
          )
    }
  }

    @Tool(name = "list_runtemplates", description = "List run templates for a solution")
    fun listRuntemplates(
        @ToolParam(description = "The organization id", required = true) organizationId: String,
        @ToolParam(description = "The solution id", required = true) solutionId: String
    ): String {
        return solutionApiService.listRunTemplates(organizationId, solutionId).joinToString { runtemplate
            ->
            StringBuilder("Runtemplate id: ").append(runtemplate.id)
                .append(" - Runtemplate name: ").append(runtemplate.name)
                .append(" - Runtemplate parameterGroup ids: ").append(
                    runtemplate.parameterGroups.joinToString(prefix = "{", postfix = "}", separator = ",", transform =  {"parameterGroup id: $it"})
                )
        }
    }

    @Tool(name = "getSolutionParameterGroup", description = "Retrieve a solution parameter group definition")
    fun getSolutionParameterGroup(
        @ToolParam(description = "The organization id", required = true) organizationId: String,
        @ToolParam(description = "The solution id", required = true) solutionId: String,
        @ToolParam(description = "The parameter group id", required = true) parameterGroupId: String,
    ): String {
        val parameterGroup = solutionApiService.getSolutionParameterGroup(organizationId, solutionId, parameterGroupId)
        return StringBuilder("Parameter Group id: ").append(parameterGroup.id)
                .append(" - Parameter Group description: ").append(parameterGroup.description)
                .append(" - Parameter parameter ids: ").append(
                    parameterGroup.parameters.joinToString(prefix = "{",
                        postfix = "}",
                        separator = ",",
                        transform =  {"parameter id: $it"}
                    )
                ).toString()
        }

    @Tool(name = "list_solutionParameters", description = "List all parameters for a solution")
    fun listSolutionParameters(
        @ToolParam(description = "The organization id", required = true) organizationId: String,
        @ToolParam(description = "The solution id", required = true) solutionId: String
    ): String {
        return solutionApiService.listSolutionParameters(organizationId, solutionId).joinToString { parameter
            ->
            StringBuilder("Parameter id: ").append(parameter.id)
                .append(" - Parameter description: ").append(parameter.description)
                .append(" - Parameter varType: ").append(parameter.varType)
        }
    }

}
