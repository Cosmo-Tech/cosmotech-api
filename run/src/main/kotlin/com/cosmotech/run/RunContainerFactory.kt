// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.config.CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication.Strategy.SHARED_ACCESS_POLICY
import com.cosmotech.api.containerregistry.ContainerRegistryService
import com.cosmotech.api.utils.sanitizeForKubernetes
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.domain.Organization
import com.cosmotech.run.container.BASIC_SIZING
import com.cosmotech.run.container.HIGH_CPU_SIZING
import com.cosmotech.run.container.HIGH_MEMORY_SIZING
import com.cosmotech.run.container.StartInfo
import com.cosmotech.run.container.toContainerResourceSizing
import com.cosmotech.run.container.toSizing
import com.cosmotech.run.domain.RunContainer
import com.cosmotech.run.domain.RunStartContainers
import com.cosmotech.runner.api.RunnerApiService
import com.cosmotech.runner.domain.Runner
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.solution.domain.RunTemplate
import com.cosmotech.solution.domain.RunTemplateOrchestrator
import com.cosmotech.solution.domain.Solution
import com.cosmotech.workspace.api.WorkspaceApiService
import com.cosmotech.workspace.azure.EventHubRole
import com.cosmotech.workspace.azure.IWorkspaceEventHubService
import com.cosmotech.workspace.domain.Workspace
import java.util.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

private const val CONTAINER_CSM_ORC = "CSMOrchestrator"
internal const val IDENTITY_PROVIDER = "IDENTITY_PROVIDER"
internal const val AZURE_TENANT_ID_VAR = "AZURE_TENANT_ID"
internal const val AZURE_CLIENT_ID_VAR = "AZURE_CLIENT_ID"
internal const val AZURE_CLIENT_SECRET_VAR = "AZURE_CLIENT_SECRET"
internal const val OKTA_CLIENT_ID = "OKTA_CLIENT_ID"
internal const val OKTA_CLIENT_SECRET = "OKTA_CLIENT_SECRET"
internal const val OKTA_CLIENT_ISSUER = "OKTA_CLIENT_ISSUER"
internal const val TWIN_CACHE_HOST = "TWIN_CACHE_HOST"
internal const val TWIN_CACHE_PASSWORD = "TWIN_CACHE_PASSWORD"
internal const val TWIN_CACHE_USERNAME = "TWIN_CACHE_USERNAME"
internal const val TWIN_CACHE_PORT = "TWIN_CACHE_PORT"
private const val CSM_AZURE_MANAGED_IDENTITY_VAR = "CSM_AZURE_MANAGED_IDENTITY"
private const val CSM_SIMULATION_ID = "CSM_SIMULATION_ID"
private const val API_BASE_URL_VAR = "CSM_API_URL"
private const val API_BASE_SCOPE_VAR = "CSM_API_SCOPE"
private const val API_SCOPE_SUFFIX = "/.default"
private const val DATASET_PATH_VAR = "CSM_DATASET_ABSOLUTE_PATH"
private const val DATASET_PATH = "/mnt/scenariorun-data"
private const val PARAMETERS_PATH_VAR = "CSM_PARAMETERS_ABSOLUTE_PATH"
private const val PARAMETERS_PATH = "/mnt/scenariorun-parameters"
private const val PARAMETERS_ORGANIZATION_VAR = "CSM_ORGANIZATION_ID"
private const val PARAMETERS_WORKSPACE_VAR = "CSM_WORKSPACE_ID"
private const val PARAMETERS_RUNNER_VAR = "CSM_RUNNER_ID"
private const val PARAMETERS_RUNNER_RUN_VAR = "CSM_RUNNER_RUN_ID"

private const val AZURE_DATA_EXPLORER_RESOURCE_URI_VAR = "AZURE_DATA_EXPLORER_RESOURCE_URI"
private const val AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI_VAR =
    "AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI"
private const val AZURE_DATA_EXPLORER_DATABASE_NAME = "AZURE_DATA_EXPLORER_DATABASE_NAME"
private const val RUN_TEMPLATE_ID_VAR = "CSM_RUN_TEMPLATE_ID"
private const val CONTAINER_MODE_VAR = "CSM_CONTAINER_MODE"
private const val CONTAINER_ORCHESTRATOR_LEGACY_VAR = "CSM_ENTRYPOINT_LEGACY"
private const val ENTRYPOINT_NAME = "entrypoint.py"
private const val EVENT_HUB_MEASURES_VAR = "CSM_PROBES_MEASURES_TOPIC"
internal const val EVENT_HUB_CONTROL_PLANE_VAR = "CSM_CONTROL_PLANE_TOPIC"
private const val CSM_SIMULATION_VAR = "CSM_SIMULATION"
private const val NODE_PARAM_NONE = "%NONE%"
const val NODE_LABEL_DEFAULT = "basic"
private const val NODE_LABEL_HIGH_CPU = "highcpu"
private const val NODE_LABEL_HIGH_MEMORY = "highmemory"
const val NODE_LABEL_SUFFIX = "pool"
private const val GENERATE_NAME_PREFIX = "workflow-"
private const val GENERATE_NAME_SUFFIX = "-"

internal const val AZURE_EVENT_HUB_SHARED_ACCESS_POLICY_ENV_VAR =
    "AZURE_EVENT_HUB_SHARED_ACCESS_POLICY"
internal const val AZURE_EVENT_HUB_SHARED_ACCESS_KEY_ENV_VAR = "AZURE_EVENT_HUB_SHARED_ACCESS_KEY"
internal const val CSM_AMQPCONSUMER_USER_ENV_VAR = "CSM_AMQPCONSUMER_USER"
internal const val CSM_AMQPCONSUMER_PASSWORD_ENV_VAR = "CSM_AMQPCONSUMER_PASSWORD"
private const val CSM_CONTROL_PLANE_USER_ENV_VAR = "CSM_CONTROL_PLANE_USER"
private const val CSM_CONTROL_PLANE_PASSWORD_ENV_VAR = "CSM_CONTROL_PLANE_PASSWORD"

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

private val CSM_ORC_ORCHESTRATOR_VALUE = RunTemplateOrchestrator.csmOrc.value

@Component
class RunContainerFactory(
    private val csmPlatformProperties: CsmPlatformProperties,
    private val runnerApiService: RunnerApiService,
    private val workspaceService: WorkspaceApiService,
    private val solutionService: SolutionApiService,
    private val organizationService: OrganizationApiService,
    private val workspaceEventHubService: IWorkspaceEventHubService,
    private val containerRegistryService: ContainerRegistryService
) {

  private val logger = LoggerFactory.getLogger(RunContainerFactory::class.java)

  fun getStartInfo(
      organizationId: String,
      workspaceId: String,
      runnerId: String,
      workflowType: String,
      runId: String
  ): StartInfo {
    val organization = organizationService.findOrganizationById(organizationId)
    val workspace = workspaceService.findWorkspaceById(organizationId, workspaceId)
    if (workspace.solution.solutionId == null)
        throw IllegalStateException("You cannot start a workspace with no solutionId defined")
    val solution =
        solutionService.findSolutionById(organizationId, workspace.solution.solutionId ?: "")

    if (csmPlatformProperties.containerRegistry.checkSolutionImage) {
      containerRegistryService.checkSolutionImage(solution.repository!!, solution.version!!)
    }

    val runner = runnerApiService.getRunner(organizationId, workspaceId, runnerId)
    val runTemplate = this.getRunTemplate(solution, (runner.runTemplateId ?: ""))
    val csmSimulationId = UUID.randomUUID().toString()

    return StartInfo(
        startContainers =
            buildContainersStart(
                runner,
                workspace,
                organization,
                solution,
                runId,
                csmSimulationId,
                workflowType,
            ),
        runner = runner,
        workspace = workspace,
        solution = solution,
        runTemplate = runTemplate,
        csmSimulationId = csmSimulationId,
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
          null
        } else {
          (template.computeSize?.removeSuffix(NODE_LABEL_SUFFIX) ?: NODE_LABEL_DEFAULT)
        }

    val runSizing = runner.runSizing?.toSizing()

    val runTemplateSizing = template.runSizing?.toSizing()

    var defaultSizing = BASIC_SIZING

    if (!nodeLabel.isNullOrBlank()) {
      defaultSizing = LABEL_SIZING[nodeLabel] ?: BASIC_SIZING
    }

    val containers: MutableList<RunContainer> = mutableListOf()

    val runTemplateId =
        runner.runTemplateId ?: throw IllegalStateException("Runner runTemplateId cannot be null")

    val imageName =
        getImageName(
            csmPlatformProperties.azure?.containerRegistries?.solutions ?: "",
            solution.repository,
            solution.version)

    val envVars =
        getCommonEnvVars(
            csmPlatformProperties,
            csmSimulationId,
            organization.id!!,
            workspace.id!!,
            runner.id!!,
            runId,
            workspace.key)

    envVars[RUN_TEMPLATE_ID_VAR] = runTemplateId
    envVars[CONTAINER_MODE_VAR] = CSM_ORC_ORCHESTRATOR_VALUE
    envVars[CONTAINER_ORCHESTRATOR_LEGACY_VAR] = "false"

    envVars.putAll(getEventHubEnvVars(organization, workspace))

    val csmSimulation = template.csmSimulation
    if (csmSimulation != null) {
      envVars[CSM_SIMULATION_VAR] = csmSimulation
    }
    val customSizing = runSizing ?: (runTemplateSizing ?: defaultSizing)

    containers.add(
        RunContainer(
            name = CONTAINER_CSM_ORC,
            image = imageName,
            envVars = envVars,
            dependencies = null,
            entrypoint = ENTRYPOINT_NAME,
            solutionContainer = true,
            nodeLabel = nodeLabel ?: NODE_LABEL_DEFAULT,
            runSizing = customSizing.toContainerResourceSizing()))

    val generateName =
        "$GENERATE_NAME_PREFIX${runner.id}$GENERATE_NAME_SUFFIX".sanitizeForKubernetes()

    return RunStartContainers(
        generateName = generateName,
        nodeLabel = nodeLabel?.plus(NODE_LABEL_SUFFIX),
        containers = containers.toList(),
        csmSimulationId = csmSimulationId,
        labels =
            mapOf(
                CSM_JOB_ID_LABEL_KEY to (runner.id ?: ""),
                WORKFLOW_TYPE_LABEL to workflowType,
                ORGANIZATION_ID_LABEL to organization.id!!,
                WORKSPACE_ID_LABEL to workspace.id!!,
                RUNNER_ID_LABEL to runner.id!!,
            ))
  }

  private fun getRunTemplate(solution: Solution, runTemplateId: String): RunTemplate {
    return solution.runTemplates?.find { runTemplate -> runTemplate.id == runTemplateId }
        ?: throw IllegalStateException(
            "runTemplateId $runTemplateId not found in Solution ${solution.id}")
  }

  private fun getEventHubEnvVars(
      organization: Organization,
      workspace: Workspace
  ): Map<String, String> {
    logger.debug(
        "Get Event Hub env vars for workspace {} with dedicated namespace: {}",
        workspace.id,
        workspace.useDedicatedEventHubNamespace ?: "null")
    val envVars: MutableMap<String, String> = mutableMapOf()
    val eventHubProbesMeasures =
        workspaceEventHubService.getWorkspaceEventHubInfo(
            organization.id ?: "", workspace, EventHubRole.PROBES_MEASURES)
    envVars[EVENT_HUB_MEASURES_VAR] = eventHubProbesMeasures.eventHubUri
    if (eventHubProbesMeasures.eventHubCredentialType == SHARED_ACCESS_POLICY) {
      logger.debug("Adding event Hub Shared Access key information in env vars")
      envVars.putAll(
          mapOf(
              AZURE_EVENT_HUB_SHARED_ACCESS_POLICY_ENV_VAR to
                  eventHubProbesMeasures.eventHubSasKeyName,
              AZURE_EVENT_HUB_SHARED_ACCESS_KEY_ENV_VAR to eventHubProbesMeasures.eventHubSasKey,
              CSM_AMQPCONSUMER_USER_ENV_VAR to eventHubProbesMeasures.eventHubSasKeyName,
              CSM_AMQPCONSUMER_PASSWORD_ENV_VAR to eventHubProbesMeasures.eventHubSasKey,
          ))
    } else {
      logger.debug("Event hub in tenant credential mode")
    }

    val eventHubControlPlane =
        workspaceEventHubService.getWorkspaceEventHubInfo(
            organization.id ?: "", workspace, EventHubRole.CONTROL_PLANE)
    if (eventHubControlPlane.eventHubAvailable) {
      logger.debug("Adding control plane event hub information in env vars")
      envVars[EVENT_HUB_CONTROL_PLANE_VAR] = eventHubControlPlane.eventHubUri
      if (eventHubProbesMeasures.eventHubCredentialType == SHARED_ACCESS_POLICY) {
        envVars.putAll(
            mapOf(
                CSM_CONTROL_PLANE_USER_ENV_VAR to eventHubProbesMeasures.eventHubSasKeyName,
                CSM_CONTROL_PLANE_PASSWORD_ENV_VAR to eventHubProbesMeasures.eventHubSasKey,
            ))
      }
    } else {
      logger.warn("Control plane event hub is not available")
    }

    return envVars.toMap()
  }

  private fun getImageName(registry: String, repository: String?, version: String? = null): String {
    val repoVersion =
        repository.let { if (version == null) it else "$it:$version" }
            ?: throw IllegalStateException("Solution repository is not defined")
    return if (registry.isNotEmpty()) "$registry/$repoVersion" else repoVersion
  }
}

/**
 * Get scopes used by containers
 * @return all scopes defined join by ","
 */
internal fun getContainerScopes(csmPlatformProperties: CsmPlatformProperties): String {

  if (csmPlatformProperties.identityProvider != null) {
    val containerScopes =
        csmPlatformProperties.identityProvider?.containerScopes?.keys?.joinToString(separator = " ")
            ?: ""
    if (containerScopes.isBlank() && csmPlatformProperties.identityProvider!!.code == "azure") {
      return "${csmPlatformProperties.azure?.appIdUri}$API_SCOPE_SUFFIX"
    }
    return containerScopes
  }
  return "${csmPlatformProperties.azure?.appIdUri}$API_SCOPE_SUFFIX"
}

internal fun getMinimalCommonEnvVars(
    csmPlatformProperties: CsmPlatformProperties,
    azureManagedIdentity: Boolean? = null,
    azureAuthenticationWithCustomerAppRegistration: Boolean? = null,
): MutableMap<String, String> {
  if (azureManagedIdentity == true && azureAuthenticationWithCustomerAppRegistration == true) {
    throw IllegalArgumentException(
        "Don't know which authentication mechanism to use to connect " +
            "against Azure services. Both azureManagedIdentity and " +
            "azureAuthenticationWithCustomerAppRegistration cannot be set to true")
  }
  val identityEnvVars =
      if (azureManagedIdentity == true) {
        mapOf(CSM_AZURE_MANAGED_IDENTITY_VAR to "true")
      } else if (azureAuthenticationWithCustomerAppRegistration == true) {
        mapOf(
            AZURE_TENANT_ID_VAR to (csmPlatformProperties.azure?.credentials?.customer?.tenantId!!),
            AZURE_CLIENT_ID_VAR to (csmPlatformProperties.azure?.credentials?.customer?.clientId!!),
            AZURE_CLIENT_SECRET_VAR to
                (csmPlatformProperties.azure?.credentials?.customer?.clientSecret!!),
        )
      } else {
        mapOf(
            AZURE_TENANT_ID_VAR to (csmPlatformProperties.azure?.credentials?.core?.tenantId!!),
            AZURE_CLIENT_ID_VAR to (csmPlatformProperties.azure?.credentials?.core?.clientId!!),
            AZURE_CLIENT_SECRET_VAR to
                (csmPlatformProperties.azure?.credentials?.core?.clientSecret!!),
        )
      }
  val oktaEnvVars: MutableMap<String, String> = mutableMapOf()
  if (csmPlatformProperties.identityProvider?.code == "okta") {
    oktaEnvVars.putAll(
        mapOf(
            OKTA_CLIENT_ID to (csmPlatformProperties.okta?.clientId!!),
            OKTA_CLIENT_SECRET to (csmPlatformProperties.okta?.clientSecret!!),
            OKTA_CLIENT_ISSUER to (csmPlatformProperties.okta?.issuer!!),
        ))
  }

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
          IDENTITY_PROVIDER to (csmPlatformProperties.identityProvider?.code ?: "azure"),
          API_BASE_URL_VAR to csmPlatformProperties.api.baseUrl,
          API_BASE_SCOPE_VAR to containerScopes,
          DATASET_PATH_VAR to DATASET_PATH,
          PARAMETERS_PATH_VAR to PARAMETERS_PATH,
      )
  return (identityEnvVars + commonEnvVars + oktaEnvVars + twinCacheEnvVars).toMutableMap()
}

internal fun getCommonEnvVars(
    csmPlatformProperties: CsmPlatformProperties,
    csmSimulationId: String,
    organizationId: String,
    workspaceId: String,
    runnerId: String,
    runId: String,
    workspaceKey: String,
    azureManagedIdentity: Boolean? = null,
    azureAuthenticationWithCustomerAppRegistration: Boolean? = null,
): MutableMap<String, String> {

  val minimalEnvVars =
      getMinimalCommonEnvVars(
          csmPlatformProperties,
          azureManagedIdentity,
          azureAuthenticationWithCustomerAppRegistration)

  val commonEnvVars =
      mapOf(
          CSM_SIMULATION_ID to csmSimulationId,
          AZURE_DATA_EXPLORER_RESOURCE_URI_VAR to
              (csmPlatformProperties.azure?.dataWarehouseCluster?.baseUri ?: ""),
          AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI_VAR to
              (csmPlatformProperties.azure?.dataWarehouseCluster?.options?.ingestionUri ?: ""),
          AZURE_DATA_EXPLORER_DATABASE_NAME to "$organizationId-$workspaceKey".lowercase(),
          PARAMETERS_ORGANIZATION_VAR to organizationId,
          PARAMETERS_WORKSPACE_VAR to workspaceId,
          PARAMETERS_RUNNER_VAR to runnerId,
          PARAMETERS_RUNNER_RUN_VAR to runId)
  return (minimalEnvVars + commonEnvVars).toMutableMap()
}
