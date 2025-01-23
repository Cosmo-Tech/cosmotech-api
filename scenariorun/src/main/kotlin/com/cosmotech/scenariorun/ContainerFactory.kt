// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun

import com.cosmotech.api.azure.sanitizeForAzureStorage
import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.config.CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication.Strategy.SHARED_ACCESS_POLICY
import com.cosmotech.api.containerregistry.ContainerRegistryService
import com.cosmotech.api.utils.sanitizeForKubernetes
import com.cosmotech.connector.api.ConnectorApiService
import com.cosmotech.connector.domain.Connector
import com.cosmotech.dataset.api.DatasetApiService
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.domain.Organization
import com.cosmotech.scenario.api.ScenarioApiService
import com.cosmotech.scenario.domain.Scenario
import com.cosmotech.scenariorun.container.BASIC_SIZING
import com.cosmotech.scenariorun.container.HIGH_CPU_SIZING
import com.cosmotech.scenariorun.container.HIGH_MEMORY_SIZING
import com.cosmotech.scenariorun.container.Sizing
import com.cosmotech.scenariorun.container.SolutionContainerStepSpec
import com.cosmotech.scenariorun.container.StartInfo
import com.cosmotech.scenariorun.container.toContainerResourceSizing
import com.cosmotech.scenariorun.container.toSizing
import com.cosmotech.scenariorun.dataset.PARAMETERS_DATASET_ID
import com.cosmotech.scenariorun.dataset.findDatasetsAndConnectors
import com.cosmotech.scenariorun.dataset.getDatasetEnvVars
import com.cosmotech.scenariorun.dataset.getDatasetIdListFromValue
import com.cosmotech.scenariorun.domain.ScenarioRunContainer
import com.cosmotech.scenariorun.domain.ScenarioRunContainerArtifact
import com.cosmotech.scenariorun.domain.ScenarioRunStartContainers
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.solution.domain.RunTemplate
import com.cosmotech.solution.domain.RunTemplateHandlerId
import com.cosmotech.solution.domain.RunTemplateOrchestrator
import com.cosmotech.solution.domain.RunTemplateStepSource
import com.cosmotech.solution.domain.Solution
import com.cosmotech.solution.utils.getCloudPath
import com.cosmotech.workspace.api.WorkspaceApiService
import com.cosmotech.workspace.azure.EventHubRole
import com.cosmotech.workspace.azure.IWorkspaceEventHubService
import com.cosmotech.workspace.domain.Workspace
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

private const val PARAMETERS_WORKSPACE_FILE = "%WORKSPACE_FILE%"
private const val PARAMETERS_STORAGE_CONNECTION_STRING = "%STORAGE_CONNECTION_STRING%"
internal const val CONTAINER_FETCH_DATASET = "fetchDatasetContainer"
private const val CONTAINER_FETCH_PARAMETERS = "fetchScenarioParametersContainer"
internal const val CONTAINER_FETCH_DATASET_PARAMETERS = "fetchScenarioDatasetParametersContainer"
private const val CONTAINER_SEND_DATAWAREHOUSE = "sendDataWarehouseContainer"
private const val CONTAINER_APPLY_PARAMETERS = "applyParametersContainer"
private const val CONTAINER_APPLY_PARAMETERS_MODE = "handle-parameters"
private const val CONTAINER_VALIDATE_DATA = "validateDataContainer"
private const val CONTAINER_VALIDATE_DATA_MODE = "validate"
private const val CONTAINER_PRERUN = "preRunContainer"
private const val CONTAINER_PRERUN_MODE = "prerun"
private const val CONTAINER_RUN = "runContainer"
private const val CONTAINER_RUN_MODE = "engine"
private const val CONTAINER_POSTRUN = "postRunContainer"
private const val CONTAINER_POSTRUN_MODE = "postrun"
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
internal const val FETCH_PATH_VAR = "CSM_FETCH_ABSOLUTE_PATH"
private const val PARAMETERS_ORGANIZATION_VAR = "CSM_ORGANIZATION_ID"
private const val PARAMETERS_WORKSPACE_VAR = "CSM_WORKSPACE_ID"
private const val PARAMETERS_SCENARIO_VAR = "CSM_SCENARIO_ID"
private const val PARAMETERS_SCENARIO_RUN_VAR = "CSM_SCENARIO_RUN_ID"
private const val PARAMETERS_FETCH_CONTAINER_CSV_VAR = "WRITE_CSV"
private const val PARAMETERS_FETCH_CONTAINER_JSON_VAR = "WRITE_JSON"
private const val SEND_DATAWAREHOUSE_PARAMETERS_VAR = "CSM_SEND_DATAWAREHOUSE_PARAMETERS"
private const val SEND_DATAWAREHOUSE_DATASETS_VAR = "CSM_SEND_DATAWAREHOUSE_DATASETS"
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
private const val STEP_SOURCE_LOCAL = "local"
private const val STEP_SOURCE_CLOUD = "azureStorage"
private const val STEP_SOURCE_GIT = "git"
private const val STEP_SOURCE_PLATFORM = "platform"
private const val AZURE_STORAGE_CONNECTION_STRING = "AZURE_STORAGE_CONNECTION_STRING"
private const val MULTIPLE_STEPS_NAME = "multipleStepsContainer-"
internal const val AZURE_EVENT_HUB_SHARED_ACCESS_POLICY_ENV_VAR =
    "AZURE_EVENT_HUB_SHARED_ACCESS_POLICY"
internal const val AZURE_EVENT_HUB_SHARED_ACCESS_KEY_ENV_VAR = "AZURE_EVENT_HUB_SHARED_ACCESS_KEY"
internal const val CSM_AMQPCONSUMER_USER_ENV_VAR = "CSM_AMQPCONSUMER_USER"
internal const val CSM_AMQPCONSUMER_PASSWORD_ENV_VAR = "CSM_AMQPCONSUMER_PASSWORD"
private const val CSM_CONTROL_PLANE_USER_ENV_VAR = "CSM_CONTROL_PLANE_USER"
private const val CSM_CONTROL_PLANE_PASSWORD_ENV_VAR = "CSM_CONTROL_PLANE_PASSWORD"
internal const val AZURE_AAD_POD_ID_BINDING_LABEL = "aadpodidbinding"
private const val SCENARIO_DATA_ABSOLUTE_PATH_ENV_VAR = "CSM_DATA_ABSOLUTE_PATH"
private const val SCENARIO_DATA_UPLOAD_LOG_LEVEL_ENV_VAR = "CSM_LOG_LEVEL"
internal const val CSM_JOB_ID_LABEL_KEY = "cosmotech.com/job_id"
internal const val SCENARIO_DATA_DOWNLOAD_ARTIFACT_NAME = "downloadUrl"
internal const val WORKFLOW_TYPE_LABEL = "cosmotech.com/workflowtype"
internal const val ORGANIZATION_ID_LABEL = "cosmotech.com/organizationId"
internal const val WORKSPACE_ID_LABEL = "cosmotech.com/workspaceId"
internal const val SCENARIO_ID_LABEL = "cosmotech.com/scenarioId"

const val CSM_DAG_ROOT = "DAG_ROOT"

private val LABEL_SIZING =
    mapOf(
        NODE_LABEL_DEFAULT to BASIC_SIZING,
        NODE_LABEL_HIGH_CPU to HIGH_CPU_SIZING,
        NODE_LABEL_HIGH_MEMORY to HIGH_MEMORY_SIZING,
    )

private val CSM_ORC_ORCHESTRATOR_VALUE = RunTemplateOrchestrator.csmOrc.value

@Component
@Suppress("LargeClass", "TooManyFunctions")
class ContainerFactory(
    private val csmPlatformProperties: CsmPlatformProperties,
    private val scenarioService: ScenarioApiService,
    private val workspaceService: WorkspaceApiService,
    private val solutionService: SolutionApiService,
    private val organizationService: OrganizationApiService,
    private val connectorService: ConnectorApiService,
    private val datasetService: DatasetApiService,
    private val workspaceEventHubService: IWorkspaceEventHubService,
    private val containerRegistryService: ContainerRegistryService
) {

  private val logger = LoggerFactory.getLogger(ContainerFactory::class.java)

  private val steps: Map<String, SolutionContainerStepSpec>

  init {
    this.steps =
        mapOf(
            "handle-parameters" to
                SolutionContainerStepSpec(
                    mode = "handle-parameters",
                    providerVar = "CSM_PARAMETERS_HANDLER_PROVIDER",
                    pathVar = "CSM_PARAMETERS_HANDLER_PATH",
                    source = { template -> getSource(template.parametersHandlerSource) },
                    path = { organizationId, solutionId, runTemplateId ->
                      getCloudPath(
                          organizationId,
                          solutionId,
                          runTemplateId,
                          RunTemplateHandlerId.parameters_handler)
                    }),
            "validate" to
                SolutionContainerStepSpec(
                    mode = "validate",
                    providerVar = "CSM_DATASET_VALIDATOR_PROVIDER",
                    pathVar = "CSM_DATASET_VALIDATOR_PATH",
                    source = { template -> getSource(template.datasetValidatorSource) },
                    path = { organizationId, solutionId, runTemplateId ->
                      getCloudPath(
                          organizationId, solutionId, runTemplateId, RunTemplateHandlerId.validator)
                    }),
            "prerun" to
                SolutionContainerStepSpec(
                    mode = "prerun",
                    providerVar = "CSM_PRERUN_PROVIDER",
                    pathVar = "CSM_PRERUN_PATH",
                    source = { template -> getSource(template.preRunSource) },
                    path = { organizationId, solutionId, runTemplateId ->
                      getCloudPath(
                          organizationId, solutionId, runTemplateId, RunTemplateHandlerId.prerun)
                    }),
            "engine" to
                SolutionContainerStepSpec(
                    mode = "engine",
                    providerVar = "CSM_ENGINE_PROVIDER",
                    pathVar = "CSM_ENGINE_PATH",
                    source = { template -> getSource(template.runSource) },
                    path = { organizationId, solutionId, runTemplateId ->
                      getCloudPath(
                          organizationId, solutionId, runTemplateId, RunTemplateHandlerId.engine)
                    }),
            "postrun" to
                SolutionContainerStepSpec(
                    mode = "postrun",
                    providerVar = "CSM_POSTRUN_PROVIDER",
                    pathVar = "CSM_POSTRUN_PATH",
                    source = { template -> getSource(template.postRunSource) },
                    path = { organizationId, solutionId, runTemplateId ->
                      getCloudPath(
                          organizationId, solutionId, runTemplateId, RunTemplateHandlerId.postrun)
                    }),
            RunTemplateHandlerId.scenariodata_transform.toString() to
                SolutionContainerStepSpec(
                    mode = "scenariodata-transform",
                    providerVar = "CSM_SCENARIODATA_TRANSFORM_PROVIDER",
                    pathVar = "CSM_SCENARIODATA_TRANSFORM_PATH",
                    source = { template -> getSource(template.scenariodataTransformSource) },
                    path = { organizationId, solutionId, runTemplateId ->
                      getCloudPath(
                          organizationId,
                          solutionId,
                          runTemplateId,
                          RunTemplateHandlerId.scenariodata_transform)
                    }),
        )
  }

  fun getStartInfo(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      workflowType: String,
      scenarioRunId: String? = "",
      scenarioDataDownload: Boolean = false,
      scenarioDataDownloadJobId: String? = null,
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

    val scenario = scenarioService.findScenarioById(organizationId, workspaceId, scenarioId)
    val runTemplate = this.getRunTemplate(solution, (scenario.runTemplateId ?: ""))
    val datasetsAndConnectors =
        findDatasetsAndConnectors(
            datasetService, connectorService, organizationId, scenario, solution, runTemplate)
    val csmSimulationId = UUID.randomUUID().toString()

    return StartInfo(
        startContainers =
            buildContainersStart(
                scenario,
                datasetsAndConnectors.datasets,
                datasetsAndConnectors.connectors,
                workspace,
                organization,
                solution,
                scenarioRunId!!,
                workflowType,
                scenarioDataDownload,
                scenarioDataDownloadJobId),
        scenario = scenario,
        workspace = workspace,
        solution = solution,
        runTemplate = runTemplate,
        csmSimulationId = csmSimulationId,
    )
  }

  internal fun buildSingleContainerStart(
      containerName: String,
      imageName: String,
      jobId: String,
      imageRegistry: String = "",
      imageVersion: String = "latest",
      containerEnvVars: MutableMap<String, String>,
      workflowType: String,
      artifacts: Map<String, String> = mapOf(),
      nodeLabel: String = NODE_LABEL_DEFAULT
  ): ScenarioRunStartContainers {

    var defaultSizing = BASIC_SIZING

    if (!nodeLabel.isNullOrBlank()) {
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
            artifacts,
            nodeLabel)

    val generateName = "${jobId}$GENERATE_NAME_SUFFIX".sanitizeForKubernetes()

    return ScenarioRunStartContainers(
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
                SCENARIO_ID_LABEL to "none",
            ))
  }

  internal fun buildSimpleContainer(
      imageRegistry: String,
      imageName: String,
      imageVersion: String,
      nodeSizing: Sizing,
      containerName: String,
      containerEnvVars: MutableMap<String, String>,
      artifacts: Map<String, String>,
      nodeLabel: String = NODE_LABEL_DEFAULT
  ): ScenarioRunContainer {

    val envVars = getMinimalCommonEnvVars(csmPlatformProperties)
    envVars.putAll(containerEnvVars)

    return ScenarioRunContainer(
        name = containerName,
        image = getImageName(imageRegistry, imageName, imageVersion),
        dependencies = listOf(CSM_DAG_ROOT),
        envVars = envVars,
        nodeLabel = nodeLabel,
        artifacts = artifacts.map { ScenarioRunContainerArtifact(name = it.key, path = it.value) },
        runSizing = nodeSizing.toContainerResourceSizing())
  }

  @SuppressWarnings("LongParameterList")
  internal fun buildContainersStart(
      scenario: Scenario,
      datasets: List<Dataset>?,
      connectors: List<Connector>?,
      workspace: Workspace,
      organization: Organization,
      solution: Solution,
      scenarioRunId: String,
      workflowType: String,
      scenarioDataDownload: Boolean = false,
      scenarioDataDownloadJobId: String? = null,
  ): ScenarioRunStartContainers {

    if (scenario.runTemplateId == "")
        throw IllegalStateException("Scenario runTemplateId cannot be null")

    val template = getRunTemplate(solution, (scenario.runTemplateId ?: ""))

    val nodeLabel =
        if (template.computeSize == NODE_PARAM_NONE) {
          null
        } else {
          (template.computeSize?.removeSuffix(NODE_LABEL_SUFFIX) ?: NODE_LABEL_DEFAULT)
        }

    val scenarioSizing = scenario.runSizing?.toSizing()

    val runTemplateSizing = template.runSizing?.toSizing()

    var defaultSizing = BASIC_SIZING

    if (!nodeLabel.isNullOrBlank()) {
      defaultSizing = LABEL_SIZING[nodeLabel] ?: BASIC_SIZING
    }

    val containers =
        buildContainersPipeline(
            scenario,
            datasets,
            connectors,
            workspace,
            organization,
            solution,
            scenarioRunId,
            scenarioRunId,
            scenarioDataDownload,
            scenarioDataDownloadJobId,
            scenarioRunLabel = nodeLabel ?: NODE_LABEL_DEFAULT,
            scenarioSizing ?: (runTemplateSizing ?: defaultSizing))
    val generateName =
        "${GENERATE_NAME_PREFIX}${scenarioRunId}$GENERATE_NAME_SUFFIX".sanitizeForKubernetes()

    return ScenarioRunStartContainers(
        generateName = generateName,
        nodeLabel = nodeLabel?.plus(NODE_LABEL_SUFFIX),
        containers = containers,
        csmSimulationId = scenarioRunId,
        labels =
            mapOf(
                CSM_JOB_ID_LABEL_KEY to (scenarioDataDownloadJobId ?: scenarioRunId),
                WORKFLOW_TYPE_LABEL to workflowType,
                ORGANIZATION_ID_LABEL to organization.id!!,
                WORKSPACE_ID_LABEL to workspace.id!!,
                SCENARIO_ID_LABEL to scenario.id!!,
            ))
  }

  internal fun getOrchestratorType(orchestratorType: String?): String {
    return orchestratorType ?: CSM_ORC_ORCHESTRATOR_VALUE
  }

  @Suppress("LongMethod", "LongParameterList") // Exception for this method - too tedious to update
  internal fun buildContainersPipeline(
      scenario: Scenario,
      datasets: List<Dataset>?,
      connectors: List<Connector>?,
      workspace: Workspace,
      organization: Organization,
      solution: Solution,
      scenarioRunId: String,
      csmSimulationId: String,
      scenarioDataDownload: Boolean = false,
      scenarioDataDownloadJobId: String? = null,
      scenarioRunLabel: String,
      scenarioRunSizing: Sizing
  ): List<ScenarioRunContainer> {
    if (scenario.id == null) throw IllegalStateException("Scenario Id cannot be null")
    val runTemplateId =
        scenario.runTemplateId
            ?: throw IllegalStateException("Scenario runTemplateId cannot be null")
    val template = getRunTemplate(solution, runTemplateId)

    var containers: MutableList<ScenarioRunContainer> = mutableListOf()

    if (getOrchestratorType(template.orchestratorType?.value) == CSM_ORC_ORCHESTRATOR_VALUE) {
      containers.addAll(
          buildCSMOrchestratorPipeline(
              organization,
              workspace,
              scenario,
              solution,
              scenarioRunId,
              runTemplateId,
              csmSimulationId,
              scenarioRunLabel,
              scenarioRunSizing))
    } else {
      var currentDependencies: MutableList<String>? = mutableListOf()

      containers.addAll(
          buildFetchDatasetsContainersPipeline(
              currentDependencies,
              template,
              datasets,
              connectors,
              scenario,
              organization,
              workspace,
              scenarioRunId,
              csmSimulationId,
              NODE_LABEL_DEFAULT,
              BASIC_SIZING))

      if (scenarioDataDownload) {
        containers.addAll(
            buildScenarioDataDownloadContainersPipeline(
                currentDependencies,
                organization,
                workspace,
                scenario,
                solution,
                template,
                scenarioRunId,
                csmSimulationId,
                scenarioDataDownloadJobId!!,
                NODE_LABEL_DEFAULT,
                BASIC_SIZING))
      } else {
        containers.addAll(
            buildFetchScenarioParametersContainersPipeline(
                currentDependencies,
                template,
                organization,
                workspace,
                scenario,
                scenarioRunId,
                csmSimulationId,
                solution,
                datasets,
                connectors,
                NODE_LABEL_DEFAULT,
                BASIC_SIZING))

        if (currentDependencies.isNullOrEmpty()) {
          currentDependencies = null
        }

        if (testStep(template.applyParameters)) {
          containers.addAll(
              buildApplyParametersContainersPipeline(
                  currentDependencies,
                  organization,
                  workspace,
                  scenario,
                  solution,
                  scenarioRunId,
                  runTemplateId,
                  csmSimulationId,
                  NODE_LABEL_DEFAULT,
                  BASIC_SIZING))
          currentDependencies = mutableListOf(CONTAINER_APPLY_PARAMETERS)
        }
        if (testStep(template.validateData)) {
          containers.addAll(
              buildValidateDataContainersPipeline(
                  currentDependencies,
                  organization,
                  workspace,
                  scenario,
                  solution,
                  scenarioRunId,
                  runTemplateId,
                  csmSimulationId,
                  NODE_LABEL_DEFAULT,
                  BASIC_SIZING))
          currentDependencies = mutableListOf(CONTAINER_VALIDATE_DATA)
        }

        containers.addAll(
            buildSendDataWarehouseContainersPipeline(
                currentDependencies,
                workspace,
                template,
                organization,
                scenario,
                scenarioRunId,
                csmSimulationId,
                NODE_LABEL_DEFAULT,
                BASIC_SIZING))

        if (testStep(template.preRun)) {
          containers.addAll(
              buildPreRunContainersPipeline(
                  currentDependencies,
                  organization,
                  workspace,
                  scenario,
                  solution,
                  scenarioRunId,
                  runTemplateId,
                  csmSimulationId,
                  NODE_LABEL_DEFAULT,
                  BASIC_SIZING))
          currentDependencies = mutableListOf(CONTAINER_PRERUN)
        }

        if (testStep(template.run)) {
          containers.addAll(
              buildRunContainersPipeline(
                  currentDependencies,
                  organization,
                  workspace,
                  scenario,
                  solution,
                  scenarioRunId,
                  runTemplateId,
                  csmSimulationId,
                  scenarioRunLabel,
                  scenarioRunSizing))
          currentDependencies = mutableListOf(CONTAINER_RUN)
        }

        if (testStep(template.postRun)) {
          containers.addAll(
              buildPostRunContainersPipeline(
                  currentDependencies,
                  organization,
                  workspace,
                  scenario,
                  solution,
                  scenarioRunId,
                  runTemplateId,
                  csmSimulationId,
                  NODE_LABEL_DEFAULT,
                  BASIC_SIZING))
        }
      }

      if (template.stackSteps == true) {
        containers = stackSolutionContainers(containers)
      }
    }

    return containers.toList()
  }

  private fun buildCSMOrchestratorPipeline(
      organization: Organization,
      workspace: Workspace,
      scenario: Scenario,
      solution: Solution,
      scenarioRunId: String,
      runTemplateId: String,
      csmSimulationId: String,
      nodeSizingLabel: String,
      customSizing: Sizing
  ) =
      listOf(
          this.buildCSMOrchestratorContainer(
              organization,
              workspace,
              scenario,
              solution,
              scenarioRunId,
              runTemplateId,
              csmSimulationId,
              nodeSizingLabel,
              customSizing))

  internal fun buildCSMOrchestratorContainer(
      organization: Organization,
      workspace: Workspace,
      scenario: Scenario,
      solution: Solution,
      scenarioRunId: String,
      runTemplateId: String,
      csmSimulationId: String,
      nodeSizingLabel: String,
      customSizing: Sizing
  ): ScenarioRunContainer {

    val imageName =
        getImageName(
            csmPlatformProperties.azure?.containerRegistries?.solutions ?: "",
            solution.repository,
            solution.version)
    val envVars =
        getCommonEnvVars(
            csmPlatformProperties,
            csmSimulationId,
            organization.id ?: "",
            workspace.id ?: "",
            scenario.id ?: "",
            scenarioRunId,
            workspace.key)
    envVars[RUN_TEMPLATE_ID_VAR] = runTemplateId
    envVars[CONTAINER_MODE_VAR] = CSM_ORC_ORCHESTRATOR_VALUE
    envVars[CONTAINER_ORCHESTRATOR_LEGACY_VAR] = "false"

    envVars.putAll(getEventHubEnvVars(organization, workspace))

    val template = getRunTemplate(solution, runTemplateId)
    val csmSimulation = template.csmSimulation
    if (csmSimulation != null) {
      envVars[CSM_SIMULATION_VAR] = csmSimulation
    }
    return ScenarioRunContainer(
        name = CONTAINER_CSM_ORC,
        image = imageName,
        envVars = envVars,
        dependencies = null,
        entrypoint = ENTRYPOINT_NAME,
        solutionContainer = true,
        nodeLabel = nodeSizingLabel,
        runSizing = customSizing.toContainerResourceSizing())
  }

  private fun buildPostRunContainersPipeline(
      currentDependencies: MutableList<String>?,
      organization: Organization,
      workspace: Workspace,
      scenario: Scenario,
      solution: Solution,
      scenarioRunId: String,
      runTemplateId: String,
      csmSimulationId: String,
      nodeSizingLabel: String,
      customSizing: Sizing
  ) =
      listOf(
          this.buildPostRunContainer(
              organization,
              workspace,
              scenario,
              solution,
              scenarioRunId,
              runTemplateId,
              csmSimulationId,
              currentDependencies,
              nodeSizingLabel,
              customSizing))

  @SuppressWarnings("LongParameterList")
  private fun buildScenarioDataDownloadContainersPipeline(
      dependencies: MutableList<String>?,
      organization: Organization,
      workspace: Workspace,
      scenario: Scenario,
      solution: Solution,
      template: RunTemplate,
      scenarioRunId: String,
      csmSimulationId: String,
      scenarioDataDownloadJobId: String,
      nodeSizingLabel: String,
      customSizing: Sizing
  ): List<ScenarioRunContainer> {
    val runTemplateId = template.id
    var scenarioDataTransformContainer: ScenarioRunContainer? = null
    if (testStep(template.scenarioDataDownloadTransform)) {
      scenarioDataTransformContainer =
          this.buildSolutionContainer(
              organization,
              workspace,
              scenario.id ?: "",
              scenarioRunId,
              solution,
              runTemplateId,
              "${RunTemplateHandlerId.scenariodata_transform}Container".sanitizeForKubernetes(),
              steps[RunTemplateHandlerId.scenariodata_transform.toString()],
              csmSimulationId,
              dependencies,
              nodeSizingLabel,
              customSizing)
    }
    val scenarioDataUploadContainer =
        this.buildScenarioDataUploadContainersPipeline(
            if (scenarioDataTransformContainer != null) {
              listOf(scenarioDataTransformContainer.name)
            } else {
              dependencies
            },
            organization.id!!,
            workspace.id!!,
            workspace.key,
            scenario.id!!,
            scenarioRunId,
            csmSimulationId,
            scenarioDataDownloadJobId,
            nodeSizingLabel,
            customSizing)
    return listOfNotNull(scenarioDataTransformContainer, scenarioDataUploadContainer)
  }

  private fun buildRunContainersPipeline(
      currentDependencies: MutableList<String>?,
      organization: Organization,
      workspace: Workspace,
      scenario: Scenario,
      solution: Solution,
      scenarioRunId: String,
      runTemplateId: String,
      csmSimulationId: String,
      nodeSizingLabel: String,
      customSizing: Sizing
  ) =
      listOf(
          this.buildRunContainer(
              organization,
              workspace,
              scenario,
              solution,
              scenarioRunId,
              runTemplateId,
              csmSimulationId,
              currentDependencies,
              nodeSizingLabel,
              customSizing))

  private fun buildPreRunContainersPipeline(
      currentDependencies: MutableList<String>?,
      organization: Organization,
      workspace: Workspace,
      scenario: Scenario,
      solution: Solution,
      scenarioRunId: String,
      runTemplateId: String,
      csmSimulationId: String,
      nodeSizingLabel: String,
      customSizing: Sizing
  ) =
      listOf(
          this.buildPreRunContainer(
              organization,
              workspace,
              scenario,
              solution,
              scenarioRunId,
              runTemplateId,
              csmSimulationId,
              currentDependencies,
              nodeSizingLabel,
              customSizing))

  private fun buildSendDataWarehouseContainersPipeline(
      currentDependencies: MutableList<String>?,
      workspace: Workspace,
      template: RunTemplate,
      organization: Organization,
      scenario: Scenario,
      scenarioRunId: String,
      csmSimulationId: String,
      nodeSizingLabel: String,
      customSizing: Sizing
  ): List<ScenarioRunContainer> {
    val containers: MutableList<ScenarioRunContainer> = mutableListOf()
    val sendParameters =
        getSendOptionValue(
            workspace.sendInputToDataWarehouse, template.sendInputParametersToDataWarehouse)
    val sendDatasets =
        getSendOptionValue(workspace.sendInputToDataWarehouse, template.sendDatasetsToDataWarehouse)
    if (sendParameters || sendDatasets)
        containers.add(
            this.buildSendDataWarehouseContainer(
                organization.id ?: "",
                workspace,
                scenario.id ?: "",
                scenarioRunId,
                template,
                csmSimulationId,
                currentDependencies,
                nodeSizingLabel,
                customSizing))
    return containers.toList()
  }

  private fun buildValidateDataContainersPipeline(
      currentDependencies: MutableList<String>?,
      organization: Organization,
      workspace: Workspace,
      scenario: Scenario,
      solution: Solution,
      scenarioRunId: String,
      runTemplateId: String,
      csmSimulationId: String,
      nodeSizingLabel: String,
      customSizing: Sizing
  ) =
      listOf(
          this.buildValidateDataContainer(
              organization,
              workspace,
              scenario,
              solution,
              scenarioRunId,
              runTemplateId,
              csmSimulationId,
              currentDependencies,
              nodeSizingLabel,
              customSizing))

  private fun buildApplyParametersContainersPipeline(
      currentDependencies: MutableList<String>?,
      organization: Organization,
      workspace: Workspace,
      scenario: Scenario,
      solution: Solution,
      scenarioRunId: String,
      runTemplateId: String,
      csmSimulationId: String,
      nodeSizingLabel: String,
      customSizing: Sizing
  ) =
      listOf(
          this.buildApplyParametersContainer(
              organization,
              workspace,
              scenario,
              solution,
              scenarioRunId,
              runTemplateId,
              csmSimulationId,
              currentDependencies,
              nodeSizingLabel,
              customSizing))

  private fun buildScenarioDataUploadContainersPipeline(
      dependencies: List<String>?,
      organizationId: String,
      workspaceId: String,
      workspaceKey: String,
      scenarioId: String,
      scenarioRunId: String,
      csmSimulationId: String,
      scenarioDataDownloadJobId: String,
      nodeSizingLabel: String,
      customSizing: Sizing
  ): ScenarioRunContainer {
    val envVars =
        getCommonEnvVars(
            csmPlatformProperties,
            csmSimulationId,
            organizationId,
            workspaceId,
            scenarioId,
            scenarioRunId,
            workspaceKey)
    envVars[SCENARIO_DATA_ABSOLUTE_PATH_ENV_VAR] = DATASET_PATH
    envVars[SCENARIO_DATA_UPLOAD_LOG_LEVEL_ENV_VAR] = if (logger.isDebugEnabled) "debug" else "info"
    envVars[AZURE_STORAGE_CONNECTION_STRING] =
        csmPlatformProperties.azure?.storage?.connectionString!!
    envVars["AZURE_STORAGE_CONTAINER_BLOB_PREFIX"] =
        "scenariodata/$scenarioDataDownloadJobId".sanitizeForAzureStorage()
    check(csmPlatformProperties.images.scenarioDataUpload.isNotBlank())
    val imageRepoAndTag = csmPlatformProperties.images.scenarioDataUpload.split(":", limit = 2)
    val repository = imageRepoAndTag[0]
    val tag =
        if (imageRepoAndTag.size >= 2) {
          imageRepoAndTag[1]
        } else {
          null
        }
    return ScenarioRunContainer(
        name = "scenarioDataUploadContainer",
        image =
            getImageName(
                csmPlatformProperties.azure?.containerRegistries?.core ?: "", repository, tag),
        dependencies = dependencies,
        envVars = envVars,
        artifacts =
            listOf(
                ScenarioRunContainerArtifact(
                    name = SCENARIO_DATA_DOWNLOAD_ARTIFACT_NAME, path = "download_url")),
        nodeLabel = nodeSizingLabel,
        runSizing = customSizing.toContainerResourceSizing())
  }

  @Suppress("LongParameterList")
  private fun buildFetchScenarioParametersContainersPipeline(
      currentDependencies: MutableList<String>?,
      template: RunTemplate,
      organization: Organization,
      workspace: Workspace,
      scenario: Scenario,
      scenarioRunId: String,
      csmSimulationId: String,
      solution: Solution,
      datasets: List<Dataset>?,
      connectors: List<Connector>?,
      nodeSizingLabel: String,
      customSizing: Sizing
  ): List<ScenarioRunContainer> {
    val containers: MutableList<ScenarioRunContainer> = mutableListOf()
    if (testStep(template.fetchScenarioParameters)) {
      val container =
          this.buildScenarioParametersFetchContainer(
              organization.id ?: "",
              workspace.id ?: "",
              scenario.id ?: "",
              scenarioRunId,
              workspace.key,
              csmSimulationId,
              template.parametersJson,
              nodeSizingLabel,
              customSizing)
      containers.add(container)
      currentDependencies?.add(container.name)
      val datasetParamContainers =
          buildScenarioParametersDatasetFetchContainers(
              scenario,
              solution,
              datasets,
              connectors,
              organization.id ?: "",
              workspace.id ?: "",
              scenarioRunId,
              workspace.key,
              csmSimulationId,
              nodeSizingLabel,
              customSizing)
      containers.addAll(datasetParamContainers)
      currentDependencies?.addAll(datasetParamContainers.map { dsContainer -> dsContainer.name })
    }
    return containers.toList()
  }

  @SuppressWarnings("LongParameterList")
  private fun buildFetchDatasetsContainersPipeline(
      currentDependencies: MutableList<String>?,
      template: RunTemplate,
      datasets: List<Dataset>?,
      connectors: List<Connector>?,
      scenario: Scenario,
      organization: Organization,
      workspace: Workspace,
      scenarioRunId: String,
      csmSimulationId: String,
      nodeSizingLabel: String,
      customSizing: Sizing
  ): List<ScenarioRunContainer> {
    val containers: MutableList<ScenarioRunContainer> = mutableListOf()
    if (testStep(template.fetchDatasets) && datasets != null && connectors != null) {
      var datasetCount = 1
      scenario.datasetList?.forEach { datasetId ->
        val dataset =
            datasets.find { it.id == datasetId }
                ?: throw IllegalStateException("Dataset $datasetId not found in Datasets")
        val connector =
            connectors.find { connector -> connector.id == (dataset.connector?.id ?: "") }
                ?: throw IllegalStateException(
                    "Connector id ${dataset.connector?.id} not found in connectors list")
        val container =
            this.buildFromDataset(
                dataset,
                connector,
                datasetCount,
                false,
                null,
                organization.id ?: "",
                workspace.id ?: "",
                scenario.id ?: "",
                scenarioRunId,
                workspace.key,
                csmSimulationId,
                nodeSizingLabel,
                customSizing)
        containers.add(container)
        currentDependencies?.add(container.name)
        datasetCount++
      }
    }
    return containers.toList()
  }

  private fun testStep(step: Boolean?): Boolean {
    return step ?: false
  }

  @Suppress("LongParameterList")
  internal fun buildFromDataset(
      dataset: Dataset,
      connector: Connector,
      datasetCount: Int,
      parametersFetch: Boolean,
      fetchId: String?,
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      scenarioRunId: String,
      workspaceKey: String,
      csmSimulationId: String,
      nodeSizingLabel: String,
      customSizing: Sizing
  ): ScenarioRunContainer {
    val dsConnectorId =
        dataset.connector?.id
            ?: throw IllegalStateException("Dataset Connector cannot be null or have a null id")
    if (dsConnectorId != connector.id)
        throw IllegalStateException("Dataset connector id and Connector id do not match")
    var nameBase = CONTAINER_FETCH_DATASET
    var fetchPathBase = DATASET_PATH
    if (parametersFetch) {
      nameBase = CONTAINER_FETCH_DATASET_PARAMETERS
      fetchPathBase = PARAMETERS_PATH
    }
    val labels =
        if (connector.azureManagedIdentity == true)
            mapOf(
                AZURE_AAD_POD_ID_BINDING_LABEL to
                    (csmPlatformProperties.azure?.credentials?.core?.aadPodIdBinding!!))
        else null
    return ScenarioRunContainer(
        name = "$nameBase-$datasetCount",
        image =
            getImageName(
                csmPlatformProperties.azure?.containerRegistries?.core ?: "",
                connector.repository,
                connector.version),
        labels = labels,
        dependencies = listOf(CSM_DAG_ROOT),
        envVars =
            getDatasetEnvVars(
                csmPlatformProperties,
                dataset,
                connector,
                fetchPathBase,
                fetchId,
                organizationId,
                workspaceId,
                scenarioId,
                scenarioRunId,
                workspaceKey,
                csmSimulationId),
        runArgs =
            getDatasetRunArgs(
                csmPlatformProperties, dataset, connector, organizationId, workspaceId),
        nodeLabel = nodeSizingLabel,
        runSizing = customSizing.toContainerResourceSizing())
  }

  @SuppressWarnings("LongParameterList")
  private fun buildScenarioParametersDatasetFetchContainers(
      scenario: Scenario,
      solution: Solution,
      datasets: List<Dataset>?,
      connectors: List<Connector>?,
      organizationId: String,
      workspaceId: String,
      scenarioRunId: String,
      workspaceKey: String,
      csmSimulationId: String,
      nodeSizingLabel: String,
      customSizing: Sizing
  ): List<ScenarioRunContainer> {
    val containers: MutableList<ScenarioRunContainer> = mutableListOf()
    var datasetParameterCount = 1
    if (scenario.parametersValues != null) {
      scenario.parametersValues?.forEach { parameterValue ->
        val parameter =
            solution.parameters?.find { solutionParameter ->
              solutionParameter.id == parameterValue.parameterId
            }
                ?: throw IllegalStateException(
                    "Parameter ${parameterValue.parameterId} not found in Solution ${solution.id}")
        if (parameter.varType == PARAMETERS_DATASET_ID && parameterValue.value.isNotBlank()) {
          val datasetIdList = getDatasetIdListFromValue(parameterValue.value)
          datasetIdList.forEachIndexed { index, datasetId ->
            val dataset =
                datasets?.find { dataset -> dataset.id == datasetId }
                    ?: throw IllegalStateException("Dataset $datasetId cannot be found in Datasets")
            val connector =
                connectors?.find { connector -> connector.id == dataset.connector?.id }
                    ?: throw IllegalStateException(
                        "Connector id ${dataset.connector?.id} not found in connectors list")

            val fetchId = if (datasetIdList.size == 1) parameter.id else "${parameter.id}-$index"
            containers.add(
                buildFromDataset(
                    dataset,
                    connector,
                    datasetParameterCount,
                    true,
                    fetchId,
                    organizationId,
                    workspaceId,
                    scenario.id ?: "",
                    scenarioRunId,
                    workspaceKey,
                    csmSimulationId,
                    nodeSizingLabel,
                    customSizing))
            datasetParameterCount++
          }
        }
      }
    }

    return containers.toList()
  }

  internal fun buildScenarioParametersFetchContainer(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      scenarioRunId: String,
      workspaceKey: String,
      csmSimulationId: String,
      jsonFile: Boolean? = null,
      nodeSizingLabel: String,
      customSizing: Sizing
  ): ScenarioRunContainer {
    val envVars =
        getCommonEnvVars(
            csmPlatformProperties,
            csmSimulationId,
            organizationId,
            workspaceId,
            scenarioId,
            scenarioRunId,
            workspaceKey)
    envVars[FETCH_PATH_VAR] = PARAMETERS_PATH
    if (jsonFile != null && jsonFile) {
      envVars[PARAMETERS_FETCH_CONTAINER_CSV_VAR] = "false"
      envVars[PARAMETERS_FETCH_CONTAINER_JSON_VAR] = "true"
    }
    return ScenarioRunContainer(
        name = CONTAINER_FETCH_PARAMETERS,
        image =
            getImageName(
                csmPlatformProperties.azure?.containerRegistries?.core ?: "",
                csmPlatformProperties.images.scenarioFetchParameters),
        dependencies = listOf(CSM_DAG_ROOT),
        envVars = envVars,
        nodeLabel = nodeSizingLabel,
        runSizing = customSizing.toContainerResourceSizing())
  }

  internal fun buildSendDataWarehouseContainer(
      organizationId: String,
      workspace: Workspace,
      scenarioId: String,
      scenarioRunId: String,
      runTemplate: RunTemplate,
      csmSimulationId: String,
      dependencies: List<String>? = null,
      nodeSizingLabel: String,
      customSizing: Sizing
  ): ScenarioRunContainer {
    val envVars =
        getCommonEnvVars(
            csmPlatformProperties,
            csmSimulationId,
            organizationId,
            workspace.id ?: "",
            scenarioId,
            scenarioRunId,
            workspace.key)
    val sendParameters =
        getSendOptionValue(
            workspace.sendInputToDataWarehouse, runTemplate.sendInputParametersToDataWarehouse)
    val sendDatasets =
        getSendOptionValue(
            workspace.sendInputToDataWarehouse, runTemplate.sendDatasetsToDataWarehouse)
    envVars[SEND_DATAWAREHOUSE_PARAMETERS_VAR] = (sendParameters).toString()
    envVars[SEND_DATAWAREHOUSE_DATASETS_VAR] = (sendDatasets).toString()
    return ScenarioRunContainer(
        name = CONTAINER_SEND_DATAWAREHOUSE,
        image =
            getImageName(
                csmPlatformProperties.azure?.containerRegistries?.core ?: "",
                csmPlatformProperties.images.sendDataWarehouse),
        dependencies = dependencies,
        envVars = envVars,
        nodeLabel = nodeSizingLabel,
        runSizing = customSizing.toContainerResourceSizing())
  }

  internal fun buildApplyParametersContainer(
      organization: Organization,
      workspace: Workspace,
      scenario: Scenario,
      solution: Solution,
      scenarioRunId: String,
      runTemplateId: String,
      csmSimulationId: String,
      dependencies: List<String>? = null,
      nodeSizingLabel: String,
      customSizing: Sizing
  ): ScenarioRunContainer {
    return this.buildSolutionContainer(
        organization,
        workspace,
        scenario.id ?: "",
        scenarioRunId,
        solution,
        runTemplateId,
        CONTAINER_APPLY_PARAMETERS,
        steps[CONTAINER_APPLY_PARAMETERS_MODE],
        csmSimulationId,
        dependencies,
        nodeSizingLabel,
        customSizing)
  }

  internal fun buildValidateDataContainer(
      organization: Organization,
      workspace: Workspace,
      scenario: Scenario,
      solution: Solution,
      scenarioRunId: String,
      runTemplateId: String,
      csmSimulationId: String,
      dependencies: List<String>? = null,
      nodeSizingLabel: String,
      customSizing: Sizing
  ): ScenarioRunContainer {
    return this.buildSolutionContainer(
        organization,
        workspace,
        scenario.id ?: "",
        scenarioRunId,
        solution,
        runTemplateId,
        CONTAINER_VALIDATE_DATA,
        steps[CONTAINER_VALIDATE_DATA_MODE],
        csmSimulationId,
        dependencies,
        nodeSizingLabel,
        customSizing)
  }

  internal fun buildPreRunContainer(
      organization: Organization,
      workspace: Workspace,
      scenario: Scenario,
      solution: Solution,
      scenarioRunId: String,
      runTemplateId: String,
      csmSimulationId: String,
      dependencies: List<String>? = null,
      nodeSizingLabel: String,
      customSizing: Sizing
  ): ScenarioRunContainer {
    return this.buildSolutionContainer(
        organization,
        workspace,
        scenario.id ?: "",
        scenarioRunId,
        solution,
        runTemplateId,
        CONTAINER_PRERUN,
        steps[CONTAINER_PRERUN_MODE],
        csmSimulationId,
        dependencies,
        nodeSizingLabel,
        customSizing)
  }

  internal fun buildRunContainer(
      organization: Organization,
      workspace: Workspace,
      scenario: Scenario,
      solution: Solution,
      scenarioRunId: String,
      runTemplateId: String,
      csmSimulationId: String,
      dependencies: List<String>? = null,
      nodeSizingLabel: String,
      customSizing: Sizing
  ): ScenarioRunContainer {
    return this.buildSolutionContainer(
        organization,
        workspace,
        scenario.id ?: "",
        scenarioRunId,
        solution,
        runTemplateId,
        CONTAINER_RUN,
        steps[CONTAINER_RUN_MODE],
        csmSimulationId,
        dependencies,
        nodeSizingLabel,
        customSizing)
  }

  internal fun buildPostRunContainer(
      organization: Organization,
      workspace: Workspace,
      scenario: Scenario,
      solution: Solution,
      scenarioRunId: String,
      runTemplateId: String,
      csmSimulationId: String,
      dependencies: List<String>? = null,
      nodeSizingLabel: String,
      customSizing: Sizing
  ): ScenarioRunContainer {
    return this.buildSolutionContainer(
        organization,
        workspace,
        scenario.id ?: "",
        scenarioRunId,
        solution,
        runTemplateId,
        CONTAINER_POSTRUN,
        steps[CONTAINER_POSTRUN_MODE],
        csmSimulationId,
        dependencies,
        nodeSizingLabel,
        customSizing)
  }

  internal fun getSendOptionValue(workspaceOption: Boolean?, templateOption: Boolean?): Boolean {
    return templateOption ?: (workspaceOption ?: true)
  }

  private fun getRunTemplate(solution: Solution, runTemplateId: String): RunTemplate {
    return solution.runTemplates?.find { runTemplate -> runTemplate.id == runTemplateId }
        ?: throw IllegalStateException(
            "runTemplateId $runTemplateId not found in Solution ${solution.id}")
  }
  @Suppress("LongParameterList", "CyclomaticComplexMethod")
  private fun buildSolutionContainer(
      organization: Organization,
      workspace: Workspace,
      scenarioId: String,
      scenarioRunId: String,
      solution: Solution,
      runTemplateId: String,
      name: String,
      step: SolutionContainerStepSpec?,
      csmSimulationId: String,
      dependencies: List<String>? = null,
      sizingLabel: String,
      customSizing: Sizing
  ): ScenarioRunContainer {

    if (step == null) throw IllegalStateException("Solution Container Step Spec is not defined")
    val imageName =
        getImageName(
            csmPlatformProperties.azure?.containerRegistries?.solutions ?: "",
            solution.repository,
            solution.version)
    val envVars =
        getCommonEnvVars(
            csmPlatformProperties,
            csmSimulationId,
            organization.id ?: "",
            workspace.id ?: "",
            scenarioId,
            scenarioRunId,
            workspace.key,
        )
    envVars[RUN_TEMPLATE_ID_VAR] = runTemplateId
    envVars[CONTAINER_MODE_VAR] = step.mode
    envVars[CONTAINER_ORCHESTRATOR_LEGACY_VAR] = "true"

    envVars.putAll(getEventHubEnvVars(organization, workspace))

    val template = getRunTemplate(solution, runTemplateId)
    val csmSimulation = template.csmSimulation
    if (csmSimulation != null) {
      envVars[CSM_SIMULATION_VAR] = csmSimulation
    }
    val source = step.source?.invoke(template)
    if (source != null) {
      envVars[step.providerVar] = source
      envVars[AZURE_STORAGE_CONNECTION_STRING] =
          csmPlatformProperties.azure?.storage?.connectionString ?: ""
      if (organization.id == null) throw IllegalStateException("Organization id cannot be null")
      if (workspace.id == null) throw IllegalStateException("Workspace id cannot be null")
      if (source == STEP_SOURCE_CLOUD || source == STEP_SOURCE_PLATFORM) {
        envVars[step.pathVar] =
            (step.path?.invoke(organization.id ?: "", solution.id ?: "", runTemplateId) ?: "")
      } else if (source == STEP_SOURCE_GIT) {
        envVars[step.pathVar] = template.gitRepositoryUrl ?: ""
        envVars["CSM_RUN_TEMPLATE_GIT_BRANCH_NAME"] = template.gitBranchName ?: ""
        envVars["CSM_RUN_TEMPLATE_SOURCE_DIRECTORY"] = template.runTemplateSourceDir ?: ""
      }
    }
    return ScenarioRunContainer(
        name = name,
        image = imageName,
        envVars = envVars,
        dependencies = dependencies,
        entrypoint = ENTRYPOINT_NAME,
        solutionContainer = true,
        nodeLabel = sizingLabel,
        runSizing = customSizing.toContainerResourceSizing())
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
    scenarioId: String,
    scenarioRunId: String,
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
          PARAMETERS_SCENARIO_VAR to scenarioId,
          PARAMETERS_SCENARIO_RUN_VAR to scenarioRunId)
  return (minimalEnvVars + commonEnvVars).toMutableMap()
}

private fun getReplacedDependenciesFromPreviousStackedContainer(
    mergedContainerNames: MutableMap<String, String>,
    container: ScenarioRunContainer
): ScenarioRunContainer {
  var copyOfCurrentContainer: ScenarioRunContainer = container
  var dependencies = mutableListOf<String>()
  container.dependencies?.forEach {
    if (mergedContainerNames.containsKey(it)) {
      dependencies.add(mergedContainerNames.get(it)!!)
    }
  }
  if (dependencies.isNotEmpty()) {
    copyOfCurrentContainer = container.copy(dependencies = dependencies)
  }
  return copyOfCurrentContainer
}

private fun stackSolutionContainers(
    containers: MutableList<ScenarioRunContainer>
): MutableList<ScenarioRunContainer> {
  val stackedContainers = mutableListOf<ScenarioRunContainer>()
  var stackedContainer: ScenarioRunContainer? = null
  var stackedIndex = 1
  val mergedContainerNames = mutableMapOf<String, String>()
  for (container in containers) {
    if (container.solutionContainer != true) {
      var copyOfCurrentContainer: ScenarioRunContainer = container
      if (stackedContainer != null) {
        stackedContainers.add(stackedContainer)
        copyOfCurrentContainer = container.copy(dependencies = mutableListOf(stackedContainer.name))
        stackedIndex++
      }
      stackedContainer = null
      stackedContainers.add(copyOfCurrentContainer)
    } else {
      if (stackedContainer == null) {
        stackedContainer =
            getReplacedDependenciesFromPreviousStackedContainer(mergedContainerNames, container)
      } else {
        val previousStackedContainer = stackedContainer
        stackedContainer = mergeSolutionContainer(stackedIndex, stackedContainer, container)
        mergedContainerNames[previousStackedContainer.name] = stackedContainer.name
        mergedContainerNames[container.name] = stackedContainer.name
      }
    }
  }

  if (stackedContainer != null) stackedContainers.add(stackedContainer)

  return stackedContainers
}

private fun mergeSolutionContainer(
    stackedIndex: Int,
    stackedContainer: ScenarioRunContainer,
    container: ScenarioRunContainer
): ScenarioRunContainer {
  val stackedMode = stackedContainer.envVars?.getOrDefault(CONTAINER_MODE_VAR, "") ?: ""
  val containerMode = container.envVars?.getOrDefault(CONTAINER_MODE_VAR, "") ?: ""
  val modes = "$stackedMode,$containerMode"
  val envVars = container.envVars?.toMutableMap()
  stackedContainer.envVars?.let { envVars?.putAll(it) }

  envVars?.put(CONTAINER_MODE_VAR, modes)

  // Node label and sizing is the first none basic node label
  val nodeLabel =
      if (stackedContainer.nodeLabel == NODE_LABEL_DEFAULT &&
          container.nodeLabel != NODE_LABEL_DEFAULT) {
        container.nodeLabel
      } else {
        stackedContainer.nodeLabel
      }

  val runSizing =
      if (stackedContainer.nodeLabel == NODE_LABEL_DEFAULT &&
          container.nodeLabel != NODE_LABEL_DEFAULT) {
        container.runSizing
      } else {
        stackedContainer.runSizing
      }

  return stackedContainer.copy(
      name = MULTIPLE_STEPS_NAME + stackedIndex.toString(),
      envVars = envVars,
      nodeLabel = nodeLabel,
      runSizing = runSizing,
  )
}

internal fun resolvePlatformVars(
    csmPlatformProperties: CsmPlatformProperties,
    path: String,
    organizationId: String,
    workspaceId: String
): String {
  var newValue =
      path.replace(
          PARAMETERS_WORKSPACE_FILE, "$organizationId/$workspaceId".sanitizeForAzureStorage())
  newValue =
      newValue.replace(
          PARAMETERS_STORAGE_CONNECTION_STRING,
          csmPlatformProperties.azure?.storage?.connectionString ?: "")
  return newValue
}

private fun getDatasetRunArgs(
    csmPlatformProperties: CsmPlatformProperties,
    dataset: Dataset,
    connector: Connector,
    organizationId: String,
    workspaceId: String
): List<String>? {
  return connector.parameterGroups
      ?.flatMap { it.parameters }
      ?.filter { it.envVar == null }
      ?.map {
        resolvePlatformVars(
            csmPlatformProperties,
            dataset.connector?.parametersValues?.getOrDefault(it.id, it.default ?: "") ?: "",
            organizationId,
            workspaceId)
      }
}

private fun getSource(source: RunTemplateStepSource?) =
    when (source) {
      RunTemplateStepSource.cloud -> STEP_SOURCE_CLOUD
      RunTemplateStepSource.git -> STEP_SOURCE_GIT
      RunTemplateStepSource.local -> STEP_SOURCE_LOCAL
      RunTemplateStepSource.platform -> STEP_SOURCE_PLATFORM
      else -> null
    }
