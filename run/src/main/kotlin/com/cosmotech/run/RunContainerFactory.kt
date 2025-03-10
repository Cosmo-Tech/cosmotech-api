// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.containerregistry.ContainerRegistryService
import com.cosmotech.api.utils.sanitizeForKubernetes
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.domain.Organization
import com.cosmotech.run.container.BASIC_SIZING
import com.cosmotech.run.container.HIGH_CPU_SIZING
import com.cosmotech.run.container.HIGH_MEMORY_SIZING
import com.cosmotech.run.container.Sizing
import com.cosmotech.run.container.StartInfo
import com.cosmotech.run.container.toContainerResourceSizing
import com.cosmotech.run.container.toSizing
import com.cosmotech.run.domain.RunContainer
import com.cosmotech.run.workflow.RunStartContainers
import com.cosmotech.runner.api.RunnerApiService
import com.cosmotech.runner.domain.Runner
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.solution.domain.RunTemplate
import com.cosmotech.solution.domain.Solution
import com.cosmotech.workspace.api.WorkspaceApiService
import com.cosmotech.workspace.domain.Workspace
import org.springframework.stereotype.Component

const val CONTAINER_CSM_ORC = "csmorchestrator"
internal const val IDENTITY_PROVIDER = "IDENTITY_PROVIDER"
internal const val IDP_CLIENT_ID = "IDP_CLIENT_ID"
internal const val IDP_CLIENT_SECRET = "IDP_CLIENT_SECRET"
internal const val IDP_BASE_URL = "IDP_BASE_URL"
internal const val IDP_TENANT_ID = "IDP_TENANT_ID"
internal const val TWIN_CACHE_HOST = "TWIN_CACHE_HOST"
internal const val TWIN_CACHE_PASSWORD = "TWIN_CACHE_PASSWORD"
internal const val TWIN_CACHE_USERNAME = "TWIN_CACHE_USERNAME"
internal const val TWIN_CACHE_PORT = "TWIN_CACHE_PORT"
private const val CSM_SIMULATION_ID = "CSM_SIMULATION_ID"
private const val API_BASE_URL_VAR = "CSM_API_URL"
private const val API_BASE_SCOPE_VAR = "CSM_API_SCOPE"
private const val DATASET_PATH_VAR = "CSM_DATASET_ABSOLUTE_PATH"
private const val DATASET_PATH = "/mnt/scenariorun-data"
private const val PARAMETERS_PATH_VAR = "CSM_PARAMETERS_ABSOLUTE_PATH"
private const val PARAMETERS_PATH = "/mnt/scenariorun-parameters"
private const val OUTPUT_PATH_VAR = "CSM_OUTPUT_ABSOLUTE_PATH"
private const val OUTPUT_PATH = "/pkg/share/Simulation/Output"
private const val TEMP_PATH_VAR = "CSM_TEMP_ABSOLUTE_PATH"
private const val TEMP_PATH = "/usr/tmp"
private const val PARAMETERS_ORGANIZATION_VAR = "CSM_ORGANIZATION_ID"
private const val PARAMETERS_WORKSPACE_VAR = "CSM_WORKSPACE_ID"
private const val PARAMETERS_RUNNER_VAR = "CSM_RUNNER_ID"
private const val PARAMETERS_RUN_VAR = "CSM_RUN_ID"

private const val RUN_TEMPLATE_ID_VAR = "CSM_RUN_TEMPLATE_ID"
private const val ENTRYPOINT_NAME = "entrypoint.py"
private const val EVENT_HUB_MEASURES_VAR = "CSM_PROBES_MEASURES_TOPIC"
private const val NODE_PARAM_NONE = "%NONE%"
const val NODE_LABEL_DEFAULT = "basic"
private const val NODE_LABEL_HIGH_CPU = "highcpu"
private const val NODE_LABEL_HIGH_MEMORY = "highmemory"
const val NODE_LABEL_SUFFIX = "pool"
private const val GENERATE_NAME_SUFFIX = "-"

internal const val CSM_AMQPCONSUMER_USER_ENV_VAR = "CSM_AMQPCONSUMER_USER"
internal const val CSM_AMQPCONSUMER_PASSWORD_ENV_VAR = "CSM_AMQPCONSUMER_PASSWORD"

internal const val CSM_JOB_ID_LABEL_KEY = "cosmotech.com/job_id"
internal const val WORKFLOW_TYPE_LABEL = "cosmotech.com/workflowtype"
internal const val ORGANIZATION_ID_LABEL = "cosmotech.com/organizationId"
internal const val WORKSPACE_ID_LABEL = "cosmotech.com/workspaceId"
internal const val RUNNER_ID_LABEL = "cosmotech.com/runnerId"

const val CSM_DAG_ROOT = "DAG_ROOT"

private val LABEL_SIZING =
    mapOf(
        NODE_LABEL_DEFAULT to BASIC_SIZING,
        NODE_LABEL_HIGH_CPU to HIGH_CPU_SIZING,
        NODE_LABEL_HIGH_MEMORY to HIGH_MEMORY_SIZING,
    )


@Component
class RunContainerFactory(
    private val csmPlatformProperties: CsmPlatformProperties,
    private val runnerApiService: RunnerApiService,
    private val workspaceService: WorkspaceApiService,
    private val solutionService: SolutionApiService,
    private val organizationService: OrganizationApiService,
    private val containerRegistryService: ContainerRegistryService
) {

  fun getStartInfo(
      organizationId: String,
      workspaceId: String,
      runnerId: String,
      workflowType: String,
      runId: String
  ): StartInfo {
    val organization = organizationService.getOrganization(organizationId)
    val workspace = workspaceService.getWorkspace(organizationId, workspaceId)
    val solution =
        solutionService.getSolution(organizationId, workspace.solution.solutionId)

    val solutionRepository =
        solution.repository

    if (csmPlatformProperties.containerRegistry.checkSolutionImage) {
      containerRegistryService.checkSolutionImage(solutionRepository, solution.version)
    }

    val runner = runnerApiService.getRunner(organizationId, workspaceId, runnerId)
    val runTemplate = this.getRunTemplate(solution, (runner.runTemplateId ?: ""))

    return StartInfo(
        startContainers =
            buildContainersStart(
                runner,
                workspace,
                organization,
                solution,
                runId,
                runId,
                workflowType,
            ),
        runner = runner,
        workspace = workspace,
        solution = solution,
        runTemplate = runTemplate,
        csmSimulationId = runId,
    )
  }

  @SuppressWarnings("LongMethod")
  internal fun buildContainersStart(
      runner: Runner,
      workspace: Workspace,
      organization: Organization,
      solution: Solution,
      runId: String,
      csmSimulationId: String,
      workflowType: String,
  ): RunStartContainers {

    if (runner.runTemplateId.isNullOrBlank())
        throw IllegalStateException("Runner runTemplateId cannot be null")

    val template = getRunTemplate(solution, (runner.runTemplateId ?: ""))

    val nodeLabel =
        if (template.computeSize == NODE_PARAM_NONE) {
          NODE_LABEL_DEFAULT
        } else {
          (template.computeSize?.removeSuffix(NODE_LABEL_SUFFIX) ?: NODE_LABEL_DEFAULT)
        }

    val runSizing = runner.runSizing?.toSizing()

    val runTemplateSizing = template.runSizing?.toSizing()

    var defaultSizing = BASIC_SIZING

    if (!nodeLabel.isBlank()) {
      defaultSizing = LABEL_SIZING[nodeLabel] ?: BASIC_SIZING
    }

    val containers: MutableList<RunContainer> = mutableListOf()

    val runTemplateId =
        runner.runTemplateId ?: throw IllegalStateException("Runner runTemplateId cannot be null")

    val imageName =
        getImageName(
            csmPlatformProperties.containerRegistry.host, solution.repository, solution.version)

    val envVars =
        getCommonEnvVars(
            csmPlatformProperties,
            csmSimulationId,
            organization.id,
          workspace.id,
            runner.id!!,
            runId)

    envVars[RUN_TEMPLATE_ID_VAR] = runTemplateId

    envVars.putAll(getEventBusEnvVars(workspace))

    val customSizing = runSizing ?: (runTemplateSizing ?: defaultSizing)

    containers.add(
        RunContainer(
            name = CONTAINER_CSM_ORC,
            image = imageName,
            envVars = envVars,
            dependencies = null,
            entrypoint = ENTRYPOINT_NAME,
            solutionContainer = true,
            nodeLabel = nodeLabel,
            runSizing = customSizing.toContainerResourceSizing()))

    val generateName = "${runId}$GENERATE_NAME_SUFFIX".sanitizeForKubernetes()

    return RunStartContainers(
        generateName = generateName,
        nodeLabel = nodeLabel.plus(NODE_LABEL_SUFFIX),
        containers = containers.toList(),
        csmSimulationId = csmSimulationId,
        labels =
            mapOf(
                CSM_JOB_ID_LABEL_KEY to runId,
                WORKFLOW_TYPE_LABEL to workflowType,
                ORGANIZATION_ID_LABEL to organization.id,
                WORKSPACE_ID_LABEL to workspace.id,
                RUNNER_ID_LABEL to runner.id!!,
            ))
  }

  internal fun buildSingleContainerStart(
      containerName: String,
      imageName: String,
      jobId: String,
      imageRegistry: String = "",
      imageVersion: String = "latest",
      containerEnvVars: MutableMap<String, String>,
      workflowType: String,
      nodeLabel: String = NODE_LABEL_DEFAULT
  ): RunStartContainers {

    var defaultSizing = BASIC_SIZING

    if (nodeLabel.isNotBlank()) {
      defaultSizing = LABEL_SIZING[nodeLabel] ?: BASIC_SIZING
    }

    val container =
        buildSimpleContainer(
            imageRegistry,
            imageName,
            imageVersion,
            defaultSizing,
            containerName,
            containerEnvVars,
            nodeLabel)

    val generateName = "${jobId}$GENERATE_NAME_SUFFIX".sanitizeForKubernetes()

    return RunStartContainers(
        generateName = generateName,
        nodeLabel = nodeLabel.plus(NODE_LABEL_SUFFIX),
        containers = listOf(container),
        csmSimulationId = jobId,
        labels =
            mapOf(
                CSM_JOB_ID_LABEL_KEY to jobId,
                WORKFLOW_TYPE_LABEL to workflowType,
                ORGANIZATION_ID_LABEL to "none",
                WORKSPACE_ID_LABEL to "none",
                RUNNER_ID_LABEL to "none",
            ))
  }

  internal fun buildSimpleContainer(
      imageRegistry: String,
      imageName: String,
      imageVersion: String,
      nodeSizing: Sizing,
      containerName: String,
      containerEnvVars: MutableMap<String, String>,
      nodeLabel: String
  ): RunContainer {

    val envVars = getMinimalCommonEnvVars(csmPlatformProperties)
    envVars.putAll(containerEnvVars)

    return RunContainer(
        name = containerName,
        image = getImageName(imageRegistry, imageName, imageVersion),
        dependencies = listOf(CSM_DAG_ROOT),
        envVars = envVars,
        nodeLabel = nodeLabel,
        runSizing = nodeSizing.toContainerResourceSizing())
  }

  private fun getRunTemplate(solution: Solution, runTemplateId: String): RunTemplate {
    return solution.runTemplates.find { runTemplate -> runTemplate.id == runTemplateId }
        ?: throw IllegalStateException(
            "runTemplateId $runTemplateId not found in Solution ${solution.id}")
  }

  private fun getEventBusEnvVars(workspace: Workspace): Map<String, String> {

    val envVars: MutableMap<String, String> = mutableMapOf()

    if (csmPlatformProperties.internalResultServices?.enabled == true) {
      val internalServiceInfo = csmPlatformProperties.internalResultServices!!
      val eventHubUri =
          "amqp://" +
              "${internalServiceInfo.eventBus.host}:${internalServiceInfo.eventBus.port}/${workspace.id}"
      envVars.putAll(
          mapOf(
              CSM_AMQPCONSUMER_USER_ENV_VAR to internalServiceInfo.eventBus.sender.username,
              CSM_AMQPCONSUMER_PASSWORD_ENV_VAR to internalServiceInfo.eventBus.sender.password,
              EVENT_HUB_MEASURES_VAR to eventHubUri))
    }

    return envVars.toMap()
  }

  private fun getImageName(registry: String, repository: String, version: String? = null): String {
    var imageRef = if (registry.isNotEmpty()) "$registry/" else ""
    imageRef += repository
    version?.let { imageRef += ":$version" }
    return imageRef
  }
}

/**
 * Get scopes used by containers
 *
 * @return all scopes defined join by ","
 */
internal fun getContainerScopes(csmPlatformProperties: CsmPlatformProperties): String {
  return csmPlatformProperties.identityProvider.containerScopes.keys.joinToString(separator = " ")
}

internal fun getMinimalCommonEnvVars(
    csmPlatformProperties: CsmPlatformProperties
): MutableMap<String, String> {

  val twinCacheEnvVars: MutableMap<String, String> = mutableMapOf()
  val twinCacheInfo = csmPlatformProperties.twincache
  twinCacheEnvVars.putAll(
      mapOf(
          TWIN_CACHE_HOST to (twinCacheInfo.host),
          TWIN_CACHE_PORT to (twinCacheInfo.port),
          TWIN_CACHE_PASSWORD to (twinCacheInfo.password),
          TWIN_CACHE_USERNAME to (twinCacheInfo.username),
      ))
  val containerScopes = getContainerScopes(csmPlatformProperties)
  val commonEnvVars =
      mapOf(
          IDENTITY_PROVIDER to (csmPlatformProperties.identityProvider.code),
          IDP_TENANT_ID to csmPlatformProperties.identityProvider.identity.tenantId,
          IDP_CLIENT_ID to csmPlatformProperties.identityProvider.identity.clientId,
          IDP_CLIENT_SECRET to csmPlatformProperties.identityProvider.identity.clientSecret,
          IDP_BASE_URL to csmPlatformProperties.identityProvider.serverBaseUrl,
          API_BASE_URL_VAR to csmPlatformProperties.api.baseUrl,
          API_BASE_SCOPE_VAR to containerScopes,
          DATASET_PATH_VAR to DATASET_PATH,
          PARAMETERS_PATH_VAR to PARAMETERS_PATH,
          OUTPUT_PATH_VAR to OUTPUT_PATH,
          TEMP_PATH_VAR to TEMP_PATH,
      )
  return (commonEnvVars + twinCacheEnvVars).toMutableMap()
}

internal fun getCommonEnvVars(
    csmPlatformProperties: CsmPlatformProperties,
    csmSimulationId: String,
    organizationId: String,
    workspaceId: String,
    runnerId: String,
    runId: String
): MutableMap<String, String> {

  val minimalEnvVars = getMinimalCommonEnvVars(csmPlatformProperties)

  val commonEnvVars =
      mapOf(
          CSM_SIMULATION_ID to csmSimulationId,
          PARAMETERS_ORGANIZATION_VAR to organizationId,
          PARAMETERS_WORKSPACE_VAR to workspaceId,
          PARAMETERS_RUNNER_VAR to runnerId,
          PARAMETERS_RUN_VAR to runId)
  return (minimalEnvVars + commonEnvVars).toMutableMap()
}
