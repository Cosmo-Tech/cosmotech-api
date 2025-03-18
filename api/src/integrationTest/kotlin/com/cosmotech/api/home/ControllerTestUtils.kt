// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.home

import com.cosmotech.api.home.organization.OrganizationConstants.ORGANIZATION_NAME
import com.cosmotech.api.home.runner.RunnerConstants.RUNNER_NAME
import com.cosmotech.api.home.runner.RunnerConstants.RUNNER_OWNER_NAME
import com.cosmotech.api.home.solution.SolutionConstants.SOLUTION_KEY
import com.cosmotech.api.home.solution.SolutionConstants.SOLUTION_NAME
import com.cosmotech.api.home.solution.SolutionConstants.SOLUTION_REPOSITORY
import com.cosmotech.api.home.solution.SolutionConstants.SOLUTION_SIMULATOR
import com.cosmotech.api.home.solution.SolutionConstants.SOLUTION_VERSION
import com.cosmotech.api.home.workspace.WorkspaceConstants.WORKSPACE_KEY
import com.cosmotech.api.home.workspace.WorkspaceConstants.WORKSPACE_NAME
import com.cosmotech.organization.domain.OrganizationCreateRequest
import com.cosmotech.organization.domain.OrganizationSecurity
import com.cosmotech.runner.domain.*
import com.cosmotech.solution.domain.*
import com.cosmotech.workspace.domain.*
import org.json.JSONObject
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

class ControllerTestUtils {

  object OrganizationUtils {
    @JvmStatic
    fun createOrganizationAndReturnId(
        mvc: MockMvc,
        organizationCreateRequest: OrganizationCreateRequest
    ): String =
        JSONObject(
                mvc.perform(
                        post("/organizations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(JSONObject(organizationCreateRequest).toString())
                            .accept(MediaType.APPLICATION_JSON)
                            .with(csrf()))
                    .andReturn()
                    .response
                    .contentAsString)
            .getString("id")

    @JvmStatic
    fun constructOrganizationCreateRequest(
        name: String = ORGANIZATION_NAME,
        security: OrganizationSecurity? = null
    ): OrganizationCreateRequest {
      return OrganizationCreateRequest(name = name, security = security)
    }
  }

  object SolutionUtils {

    @JvmStatic
    fun createSolutionAndReturnId(
        mvc: MockMvc,
        organizationId: String,
        solutionCreateRequest: SolutionCreateRequest
    ): String =
        JSONObject(
                mvc.perform(
                        post("/organizations/$organizationId/solutions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(JSONObject(solutionCreateRequest).toString())
                            .accept(MediaType.APPLICATION_JSON)
                            .with(csrf()))
                    .andReturn()
                    .response
                    .contentAsString)
            .getString("id")

    @JvmStatic
    fun constructSolutionCreateRequest(
        key: String = SOLUTION_KEY,
        name: String = SOLUTION_NAME,
        repository: String = SOLUTION_REPOSITORY,
        version: String = SOLUTION_VERSION,
        csmSimulator: String = SOLUTION_SIMULATOR,
        description: String = "",
        alwaysPull: Boolean? = null,
        tags: MutableList<String> = mutableListOf(),
        parameters: MutableList<RunTemplateParameterCreateRequest> = mutableListOf(),
        parameterGroups: MutableList<RunTemplateParameterGroup> = mutableListOf(),
        runTemplates: MutableList<RunTemplate> = mutableListOf(),
        url: String = "",
        security: SolutionSecurity? = null
    ): SolutionCreateRequest {
      return SolutionCreateRequest(
          key = key,
          name = name,
          repository = repository,
          version = version,
          csmSimulator = csmSimulator,
          description = description,
          alwaysPull = alwaysPull,
          tags = tags,
          parameters = parameters,
          parameterGroups = parameterGroups,
          runTemplates = runTemplates,
          url = url,
          security = security,
      )
    }

    @JvmStatic
    fun constructSolutionUpdateRequest(
        key: String = SOLUTION_KEY,
        name: String = SOLUTION_NAME,
        repository: String = SOLUTION_REPOSITORY,
        version: String = SOLUTION_VERSION,
        csmSimulator: String = SOLUTION_SIMULATOR,
        description: String = "",
        alwaysPull: Boolean? = null,
        tags: MutableList<String> = mutableListOf(),
        url: String = "",
    ): SolutionUpdateRequest {
      return SolutionUpdateRequest(
          key = key,
          name = name,
          repository = repository,
          version = version,
          csmSimulator = csmSimulator,
          description = description,
          alwaysPull = alwaysPull,
          tags = tags,
          url = url)
    }
  }

  object RunnerUtils {

    @JvmStatic
    fun createRunnerAndReturnId(
        mvc: MockMvc,
        organizationId: String,
        workspaceId: String,
        runnerCreateRequest: RunnerCreateRequest
    ): String =
        JSONObject(
                mvc.perform(
                        post("/organizations/$organizationId/workspaces/$workspaceId/runners")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(JSONObject(runnerCreateRequest).toString())
                            .accept(MediaType.APPLICATION_JSON)
                            .with(csrf()))
                    .andReturn()
                    .response
                    .contentAsString)
            .getString("id")

    @JvmStatic
    fun constructRunnerObject(
        name: String = RUNNER_NAME,
        solutionId: String,
        runTemplateId: String,
        parentId: String? = null,
        solutionName: String? = null,
        runTemplateName: String? = null,
        security: RunnerSecurity? = null,
        runSizing: RunnerResourceSizing? = null,
        ownerName: String = RUNNER_OWNER_NAME,
        description: String = "",
        tags: MutableList<String> = mutableListOf(),
        datasetList: MutableList<String>? = mutableListOf(),
        parametersValues: MutableList<RunnerRunTemplateParameterValue>? = mutableListOf(),
    ): RunnerCreateRequest {

      return RunnerCreateRequest(
          name = name,
          solutionId = solutionId,
          runTemplateId = runTemplateId,
          ownerName = ownerName,
          description = description,
          tags = tags,
          datasetList = datasetList,
          parentId = parentId,
          solutionName = solutionName,
          runTemplateName = runTemplateName,
          security = security,
          runSizing = runSizing,
          parametersValues = parametersValues)
    }

    @JvmStatic
    fun constructUpdateRunnerObject(
        name: String = RUNNER_NAME,
        runTemplateId: String,
        solutionName: String? = null,
        runTemplateName: String? = null,
        runSizing: RunnerResourceSizing? = null,
        ownerName: String = RUNNER_OWNER_NAME,
        description: String = "",
        tags: MutableList<String> = mutableListOf(),
        datasetList: MutableList<String>? = mutableListOf(),
        parametersValues: MutableList<RunnerRunTemplateParameterValue>? = mutableListOf(),
    ): RunnerUpdateRequest {

      return RunnerUpdateRequest(
          name = name,
          description = description,
          tags = tags,
          runTemplateId = runTemplateId,
          datasetList = datasetList,
          runSizing = runSizing,
          parametersValues = parametersValues,
          ownerName = ownerName,
          solutionName = solutionName,
          runTemplateName = runTemplateName)
    }
  }

  object WorkspaceUtils {

    @JvmStatic
    fun createWorkspaceAndReturnId(
        mvc: MockMvc,
        organizationId: String,
        workspaceKey: String,
        workspaceName: String,
        solutionId: String
    ): String =
        JSONObject(
                mvc.perform(
                        post("/organizations/$organizationId/workspaces")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                JSONObject(
                                        constructWorkspaceCreateRequest(
                                            key = workspaceKey,
                                            name = workspaceName,
                                            solutionId = solutionId))
                                    .toString())
                            .accept(MediaType.APPLICATION_JSON)
                            .with(csrf()))
                    .andReturn()
                    .response
                    .contentAsString)
            .getString("id")

    @JvmStatic
    fun createWorkspaceAndReturnId(
        mvc: MockMvc,
        organizationId: String,
        workspaceCreateRequest: WorkspaceCreateRequest
    ): String =
        JSONObject(
                mvc.perform(
                        post("/organizations/$organizationId/workspaces")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(JSONObject(workspaceCreateRequest).toString())
                            .accept(MediaType.APPLICATION_JSON)
                            .with(csrf()))
                    .andReturn()
                    .response
                    .contentAsString)
            .getString("id")

    @JvmStatic
    fun constructWorkspaceCreateRequest(
        key: String = WORKSPACE_KEY,
        name: String = WORKSPACE_NAME,
        solutionId: String,
        description: String = "",
        version: String = "",
        runTemplateFilter: MutableList<String> = mutableListOf(),
        defaultRunTemplateDataset: MutableMap<String, Any> = mutableMapOf(),
        datasetCopy: Boolean? = null,
        security: WorkspaceSecurity? = null,
        url: String = "",
        iframes: MutableMap<String, Any> = mutableMapOf(),
        options: MutableMap<String, Any> = mutableMapOf(),
        tags: MutableList<String> = mutableListOf()
    ): WorkspaceCreateRequest {
      return WorkspaceCreateRequest(
          key = key,
          name = name,
          solution =
              WorkspaceSolution(
                  solutionId = solutionId,
                  runTemplateFilter = runTemplateFilter,
                  defaultRunTemplateDataset = defaultRunTemplateDataset,
              ),
          description = description,
          version = version,
          datasetCopy = datasetCopy,
          security = security,
          tags = tags,
          webApp = WorkspaceWebApp(url = url, iframes = iframes, options = options))
    }

    @JvmStatic
    fun constructWorkspaceUpdateRequest(
        key: String,
        name: String,
        solutionId: String,
        description: String = "",
        runTemplateFilter: MutableList<String> = mutableListOf(),
        defaultRunTemplateDataset: MutableMap<String, Any> = mutableMapOf(),
        datasetCopy: Boolean? = null,
        url: String = "",
        iframes: MutableMap<String, Any> = mutableMapOf(),
        options: MutableMap<String, Any> = mutableMapOf(),
        tags: MutableList<String> = mutableListOf()
    ): WorkspaceUpdateRequest {

      return WorkspaceUpdateRequest(
          key = key,
          name = name,
          solution =
              WorkspaceSolution(
                  solutionId = solutionId,
                  runTemplateFilter = runTemplateFilter,
                  defaultRunTemplateDataset = defaultRunTemplateDataset),
          description = description,
          datasetCopy = datasetCopy,
          tags = tags,
          webApp = WorkspaceWebApp(url = url, iframes = iframes, options = options))
    }
  }
}
