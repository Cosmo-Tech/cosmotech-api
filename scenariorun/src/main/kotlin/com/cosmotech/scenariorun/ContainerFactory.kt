// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun

import com.cosmotech.api.azure.eventhubs.AzureEventHubsClient
import com.cosmotech.api.azure.sanitizeForAzureStorage
import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.config.CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus
import com.cosmotech.api.config.CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication.Strategy.SHARED_ACCESS_POLICY
import com.cosmotech.api.config.CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication.Strategy.TENANT_CLIENT_CREDENTIALS
import com.cosmotech.api.utils.sanitizeForKubernetes
import com.cosmotech.connector.api.ConnectorApiService
import com.cosmotech.connector.domain.Connector
import com.cosmotech.dataset.api.DatasetApiService
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.domain.Organization
import com.cosmotech.scenario.api.ScenarioApiService
import com.cosmotech.scenario.domain.Scenario
import com.cosmotech.scenariorun.container.SolutionContainerStepSpec
import com.cosmotech.scenariorun.container.StartInfo
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
import com.cosmotech.solution.domain.RunTemplateStepSource
import com.cosmotech.solution.domain.Solution
import com.cosmotech.solution.utils.getCloudPath
import com.cosmotech.workspace.api.WorkspaceApiService
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
internal const val AZURE_TENANT_ID_VAR = "AZURE_TENANT_ID"
internal const val AZURE_CLIENT_ID_VAR = "AZURE_CLIENT_ID"
internal const val AZURE_CLIENT_SECRET_VAR = "AZURE_CLIENT_SECRET"
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
private const val PARAMETERS_FETCH_CONTAINER_ORGANIZATION_VAR = "CSM_ORGANIZATION_ID"
private const val PARAMETERS_FETCH_CONTAINER_WORKSPACE_VAR = "CSM_WORKSPACE_ID"
private const val PARAMETERS_FETCH_CONTAINER_SCENARIO_VAR = "CSM_SCENARIO_ID"
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
private const val ENTRYPOINT_NAME = "entrypoint.py"
private const val EVENT_HUB_MEASURES_VAR = "CSM_PROBES_MEASURES_TOPIC"
internal const val EVENT_HUB_CONTROL_PLANE_VAR = "CSM_CONTROL_PLANE_TOPIC"
private const val CSM_SIMULATION_VAR = "CSM_SIMULATION"
private const val NODE_PARAM_NONE = "%NONE%"
private const val NODE_LABEL_DEFAULT = "basic"
private const val NODE_LABEL_SUFFIX = "pool"
private const val GENERATE_NAME_PREFIX = "workflow-"
private const val GENERATE_NAME_SUFFIX = "-"
private const val STEP_SOURCE_LOCAL = "local"
private const val STEP_SOURCE_CLOUD = "azureStorage"
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
internal const val CSM_JOB_ID_LABEL_KEY = "com.cosmotech/job_id"
internal const val SCENARIO_DATA_DOWNLOAD_ARTIFACT_NAME = "downloadUrl"

public const val CSM_DAG_ROOT = "DAG_ROOT"

@Component
@Suppress("LargeClass", "TooManyFunctions")
internal class ContainerFactory(
    private val csmPlatformProperties: CsmPlatformProperties,
    private val scenarioService: ScenarioApiService,
    private val workspaceService: WorkspaceApiService,
    private val solutionService: SolutionApiService,
    private val organizationService: OrganizationApiService,
    private val connectorService: ConnectorApiService,
    private val datasetService: DatasetApiService,
    private val azureEventHubsClient: AzureEventHubsClient
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
      scenarioDataDownload: Boolean = false,
      scenarioDataDownloadJobId: String? = null,
  ): StartInfo {
    val organization = organizationService.findOrganizationById(organizationId)
    val workspace = workspaceService.findWorkspaceById(organizationId, workspaceId)
    if (workspace.solution.solutionId == null)
        throw IllegalStateException("You cannot start a workspace with no solutionId defined")
    val solution =
        solutionService.findSolutionById(organizationId, workspace.solution.solutionId ?: "")
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
                csmSimulationId,
                scenarioDataDownload,
                scenarioDataDownloadJobId),
        scenario = scenario,
        workspace = workspace,
        solution = solution,
        runTemplate = runTemplate,
        csmSimulationId = csmSimulationId,
    )
  }

  internal fun buildContainersStart(
      scenario: Scenario,
      datasets: List<Dataset>?,
      connectors: List<Connector>?,
      workspace: Workspace,
      organization: Organization,
      solution: Solution,
      csmSimulationId: String,
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
          (template.computeSize?.removeSuffix(NODE_LABEL_SUFFIX) ?: NODE_LABEL_DEFAULT).plus(
              NODE_LABEL_SUFFIX)
        }
    val containers =
        buildContainersPipeline(
            scenario,
            datasets,
            connectors,
            workspace,
            organization,
            solution,
            csmSimulationId,
            scenarioDataDownload,
            scenarioDataDownloadJobId,
        )
    val generateName =
        "${GENERATE_NAME_PREFIX}${scenario.id}${GENERATE_NAME_SUFFIX}".sanitizeForKubernetes()
    return ScenarioRunStartContainers(
        generateName = generateName,
        nodeLabel = nodeLabel,
        containers = containers,
        csmSimulationId = csmSimulationId,
        labels = mapOf(CSM_JOB_ID_LABEL_KEY to (scenarioDataDownloadJobId ?: "")))
  }

  @Suppress("LongMethod") // Exception for this method - too tedious to update
  internal fun buildContainersPipeline(
      scenario: Scenario,
      datasets: List<Dataset>?,
      connectors: List<Connector>?,
      workspace: Workspace,
      organization: Organization,
      solution: Solution,
      csmSimulationId: String,
      scenarioDataDownload: Boolean = false,
      scenarioDataDownloadJobId: String? = null,
  ): List<ScenarioRunContainer> {
    if (scenario.id == null) throw IllegalStateException("Scenario Id cannot be null")
    val runTemplateId =
        scenario.runTemplateId
            ?: throw IllegalStateException("Scenario runTemplateId cannot be null")
    val template = getRunTemplate(solution, runTemplateId)

    var containers: MutableList<ScenarioRunContainer> = mutableListOf()

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
            csmSimulationId))

    if (scenarioDataDownload) {
      containers.addAll(
          buildScenarioDataDownloadContainersPipeline(
              currentDependencies,
              organization,
              workspace,
              scenario,
              solution,
              template,
              csmSimulationId,
              scenarioDataDownloadJobId!!))
    } else {
      containers.addAll(
          buildFetchScenarioParametersContainersPipeline(
              currentDependencies,
              template,
              organization,
              workspace,
              scenario,
              csmSimulationId,
              solution,
              datasets,
              connectors))

      if (currentDependencies.isNullOrEmpty()) {
        currentDependencies = null
      }

      if (testStep(template.applyParameters)) {
        containers.addAll(
            buildApplyParametersContainersPipeline(
                currentDependencies,
                organization,
                workspace,
                solution,
                runTemplateId,
                csmSimulationId))
        currentDependencies = mutableListOf(CONTAINER_APPLY_PARAMETERS)
      }
      if (testStep(template.validateData)) {
        containers.addAll(
            buildValidateDataContainersPipeline(
                currentDependencies,
                organization,
                workspace,
                solution,
                runTemplateId,
                csmSimulationId))
        currentDependencies = mutableListOf(CONTAINER_VALIDATE_DATA)
      }

      containers.addAll(
          buildSendDataWarehouseContainersPipeline(
              currentDependencies, workspace, template, organization, csmSimulationId))

      if (testStep(template.preRun)) {
        containers.addAll(
            buildPreRunContainersPipeline(
                currentDependencies,
                organization,
                workspace,
                solution,
                runTemplateId,
                csmSimulationId))
        currentDependencies = mutableListOf(CONTAINER_PRERUN)
      }

      if (testStep(template.run)) {
        containers.addAll(
            buildRunContainersPipeline(
                currentDependencies,
                organization,
                workspace,
                solution,
                runTemplateId,
                csmSimulationId))
        currentDependencies = mutableListOf(CONTAINER_RUN)
      }

      if (testStep(template.postRun)) {
        containers.addAll(
            buildPostRunContainersPipeline(
                currentDependencies,
                organization,
                workspace,
                solution,
                runTemplateId,
                csmSimulationId,
            ))
      }
    }

    if (template.stackSteps == true) {
      containers = stackSolutionContainers(containers)
    }

    return containers.toList()
  }

  private fun buildPostRunContainersPipeline(
      currentDependencies: MutableList<String>?,
      organization: Organization,
      workspace: Workspace,
      solution: Solution,
      runTemplateId: String,
      csmSimulationId: String,
  ) =
      listOf(
          this.buildPostRunContainer(
              organization,
              workspace,
              solution,
              runTemplateId,
              csmSimulationId,
              currentDependencies))

  private fun buildScenarioDataDownloadContainersPipeline(
      dependencies: MutableList<String>?,
      organization: Organization,
      workspace: Workspace,
      scenario: Scenario,
      solution: Solution,
      template: RunTemplate,
      csmSimulationId: String,
      scenarioDataDownloadJobId: String,
  ): List<ScenarioRunContainer> {
    val runTemplateId = template.id
    var scenarioDataTransformContainer: ScenarioRunContainer? = null
    if (testStep(template.scenarioDataDownloadTransform)) {
      scenarioDataTransformContainer =
          this.buildSolutionContainer(
              organization,
              workspace,
              solution,
              runTemplateId,
              "${RunTemplateHandlerId.scenariodata_transform}Container".sanitizeForKubernetes(),
              steps[RunTemplateHandlerId.scenariodata_transform.toString()],
              csmSimulationId,
              dependencies,
          )
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
            csmSimulationId,
            scenarioDataDownloadJobId!!)
    return listOfNotNull(scenarioDataTransformContainer, scenarioDataUploadContainer)
  }

  private fun buildRunContainersPipeline(
      currentDependencies: MutableList<String>?,
      organization: Organization,
      workspace: Workspace,
      solution: Solution,
      runTemplateId: String,
      csmSimulationId: String,
  ) =
      listOf(
          this.buildRunContainer(
              organization,
              workspace,
              solution,
              runTemplateId,
              csmSimulationId,
              currentDependencies))

  private fun buildPreRunContainersPipeline(
      currentDependencies: MutableList<String>?,
      organization: Organization,
      workspace: Workspace,
      solution: Solution,
      runTemplateId: String,
      csmSimulationId: String,
  ) =
      listOf(
          this.buildPreRunContainer(
              organization,
              workspace,
              solution,
              runTemplateId,
              csmSimulationId,
              currentDependencies))

  private fun buildSendDataWarehouseContainersPipeline(
      currentDependencies: MutableList<String>?,
      workspace: Workspace,
      template: RunTemplate,
      organization: Organization,
      csmSimulationId: String,
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
                organization.id, workspace, template, csmSimulationId, currentDependencies))
    return containers.toList()
  }

  private fun buildValidateDataContainersPipeline(
      currentDependencies: MutableList<String>?,
      organization: Organization,
      workspace: Workspace,
      solution: Solution,
      runTemplateId: String,
      csmSimulationId: String,
  ) =
      listOf(
          this.buildValidateDataContainer(
              organization,
              workspace,
              solution,
              runTemplateId,
              csmSimulationId,
              currentDependencies))

  private fun buildApplyParametersContainersPipeline(
      currentDependencies: MutableList<String>?,
      organization: Organization,
      workspace: Workspace,
      solution: Solution,
      runTemplateId: String,
      csmSimulationId: String,
  ) =
      listOf(
          this.buildApplyParametersContainer(
              organization,
              workspace,
              solution,
              runTemplateId,
              csmSimulationId,
              currentDependencies))

  private fun buildScenarioDataUploadContainersPipeline(
      dependencies: List<String>?,
      organizationId: String,
      workspaceId: String,
      workspaceKey: String,
      scenarioId: String,
      csmSimulationId: String,
      scenarioDataDownloadJobId: String,
  ): ScenarioRunContainer {
    val envVars =
        getCommonEnvVars(csmPlatformProperties, csmSimulationId, organizationId, workspaceKey)
    envVars[SCENARIO_DATA_ABSOLUTE_PATH_ENV_VAR] = DATASET_PATH
    envVars[PARAMETERS_FETCH_CONTAINER_ORGANIZATION_VAR] = organizationId
    envVars[PARAMETERS_FETCH_CONTAINER_WORKSPACE_VAR] = workspaceId
    envVars[PARAMETERS_FETCH_CONTAINER_SCENARIO_VAR] = scenarioId
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
                    name = SCENARIO_DATA_DOWNLOAD_ARTIFACT_NAME, path = "download_url")))
  }

  private fun buildFetchScenarioParametersContainersPipeline(
      currentDependencies: MutableList<String>?,
      template: RunTemplate,
      organization: Organization,
      workspace: Workspace,
      scenario: Scenario,
      csmSimulationId: String,
      solution: Solution,
      datasets: List<Dataset>?,
      connectors: List<Connector>?
  ): List<ScenarioRunContainer> {
    val containers: MutableList<ScenarioRunContainer> = mutableListOf()
    if (testStep(template.fetchScenarioParameters)) {
      val container =
          this.buildScenarioParametersFetchContainer(
              organization.id ?: "",
              workspace.id ?: "",
              workspace.key,
              scenario.id ?: "",
              csmSimulationId,
              template.parametersJson)
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
              workspace.key,
              csmSimulationId)
      containers.addAll(datasetParamContainers)
      currentDependencies?.addAll(datasetParamContainers.map { dsContainer -> dsContainer.name })
    }
    return containers.toList()
  }

  private fun buildFetchDatasetsContainersPipeline(
      currentDependencies: MutableList<String>?,
      template: RunTemplate,
      datasets: List<Dataset>?,
      connectors: List<Connector>?,
      scenario: Scenario,
      organization: Organization,
      workspace: Workspace,
      csmSimulationId: String
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
                workspace.key,
                csmSimulationId)
        containers.add(container)
        currentDependencies?.add(container.name)
        datasetCount++
      }
    }
    return containers.toList()
  }

  private fun testStep(step: Boolean?): Boolean {
    return step ?: true
  }

  internal fun buildFromDataset(
      dataset: Dataset,
      connector: Connector,
      datasetCount: Int,
      parametersFetch: Boolean,
      fetchId: String?,
      organizationId: String,
      workspaceId: String,
      workspaceKey: String,
      csmSimulationId: String
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
        name = "${nameBase}-$datasetCount",
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
                workspaceKey,
                csmSimulationId),
        runArgs =
            getDatasetRunArgs(
                csmPlatformProperties, dataset, connector, organizationId, workspaceId))
  }

  private fun buildScenarioParametersDatasetFetchContainers(
      scenario: Scenario,
      solution: Solution,
      datasets: List<Dataset>?,
      connectors: List<Connector>?,
      organizationId: String,
      workspaceId: String,
      workspaceKey: String,
      csmSimulationId: String,
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
        if (parameter.varType == PARAMETERS_DATASET_ID) {
          val datasetIdList = getDatasetIdListFromValue(parameterValue.value)
          datasetIdList.forEachIndexed { index, datasetId ->
            val dataset =
                datasets?.find { dataset -> dataset.id == datasetId }
                    ?: throw IllegalStateException(
                        "Dataset ${datasetId} cannot be found in Datasets")
            val connector =
                connectors?.find { connector -> connector.id == dataset.connector?.id }
                    ?: throw IllegalStateException(
                        "Connector id ${dataset.connector?.id} not found in connectors list")

            val fetchId =
                if (datasetIdList.size == 1) parameter.id else "${parameter.id}-${index.toString()}"
            containers.add(
                buildFromDataset(
                    dataset,
                    connector,
                    datasetParameterCount,
                    true,
                    fetchId,
                    organizationId,
                    workspaceId,
                    workspaceKey,
                    csmSimulationId))
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
      workspaceKey: String,
      scenarioId: String,
      csmSimulationId: String,
      jsonFile: Boolean? = null,
  ): ScenarioRunContainer {
    val envVars =
        getCommonEnvVars(csmPlatformProperties, csmSimulationId, organizationId, workspaceKey)
    envVars[FETCH_PATH_VAR] = PARAMETERS_PATH
    envVars[PARAMETERS_FETCH_CONTAINER_ORGANIZATION_VAR] = organizationId
    envVars[PARAMETERS_FETCH_CONTAINER_WORKSPACE_VAR] = workspaceId
    envVars[PARAMETERS_FETCH_CONTAINER_SCENARIO_VAR] = scenarioId
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
    )
  }

  internal fun buildSendDataWarehouseContainer(
      organizationId: String?,
      workspace: Workspace,
      runTemplate: RunTemplate,
      csmSimulationId: String,
      dependencies: List<String>? = null,
  ): ScenarioRunContainer {
    if (organizationId == null) throw IllegalStateException("Organization Id cannot be null")
    val envVars =
        getCommonEnvVars(csmPlatformProperties, csmSimulationId, organizationId, workspace.key)
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
        envVars = envVars)
  }

  internal fun buildApplyParametersContainer(
      organization: Organization,
      workspace: Workspace,
      solution: Solution,
      runTemplateId: String,
      csmSimulationId: String,
      dependencies: List<String>? = null,
  ): ScenarioRunContainer {
    return this.buildSolutionContainer(
        organization,
        workspace,
        solution,
        runTemplateId,
        CONTAINER_APPLY_PARAMETERS,
        steps[CONTAINER_APPLY_PARAMETERS_MODE],
        csmSimulationId,
        dependencies,
    )
  }

  internal fun buildValidateDataContainer(
      organization: Organization,
      workspace: Workspace,
      solution: Solution,
      runTemplateId: String,
      csmSimulationId: String,
      dependencies: List<String>? = null,
  ): ScenarioRunContainer {
    return this.buildSolutionContainer(
        organization,
        workspace,
        solution,
        runTemplateId,
        CONTAINER_VALIDATE_DATA,
        steps[CONTAINER_VALIDATE_DATA_MODE],
        csmSimulationId,
        dependencies,
    )
  }

  internal fun buildPreRunContainer(
      organization: Organization,
      workspace: Workspace,
      solution: Solution,
      runTemplateId: String,
      csmSimulationId: String,
      dependencies: List<String>? = null,
  ): ScenarioRunContainer {
    return this.buildSolutionContainer(
        organization,
        workspace,
        solution,
        runTemplateId,
        CONTAINER_PRERUN,
        steps[CONTAINER_PRERUN_MODE],
        csmSimulationId,
        dependencies,
    )
  }

  internal fun buildRunContainer(
      organization: Organization,
      workspace: Workspace,
      solution: Solution,
      runTemplateId: String,
      csmSimulationId: String,
      dependencies: List<String>? = null,
  ): ScenarioRunContainer {
    return this.buildSolutionContainer(
        organization,
        workspace,
        solution,
        runTemplateId,
        CONTAINER_RUN,
        steps[CONTAINER_RUN_MODE],
        csmSimulationId,
        dependencies,
    )
  }

  internal fun buildPostRunContainer(
      organization: Organization,
      workspace: Workspace,
      solution: Solution,
      runTemplateId: String,
      csmSimulationId: String,
      dependencies: List<String>? = null,
  ): ScenarioRunContainer {
    return this.buildSolutionContainer(
        organization,
        workspace,
        solution,
        runTemplateId,
        CONTAINER_POSTRUN,
        steps[CONTAINER_POSTRUN_MODE],
        csmSimulationId,
        dependencies,
    )
  }

  internal fun getSendOptionValue(workspaceOption: Boolean?, templateOption: Boolean?): Boolean {
    return templateOption ?: (workspaceOption ?: true)
  }

  private fun getRunTemplate(solution: Solution, runTemplateId: String): RunTemplate {
    return solution.runTemplates.find { runTemplate -> runTemplate.id == runTemplateId }
        ?: throw IllegalStateException(
            "runTemplateId $runTemplateId not found in Solution ${solution.id}")
  }

  private fun buildSolutionContainer(
      organization: Organization,
      workspace: Workspace,
      solution: Solution,
      runTemplateId: String,
      name: String,
      step: SolutionContainerStepSpec?,
      csmSimulationId: String,
      dependencies: List<String>? = null,
  ): ScenarioRunContainer {

    if (step == null) throw IllegalStateException("Solution Container Step Spec is not defined")
    val template = getRunTemplate(solution, runTemplateId)
    val imageName =
        getImageName(
            csmPlatformProperties.azure?.containerRegistries?.solutions ?: "",
            solution.repository,
            solution.version)
    val envVars =
        getCommonEnvVars(
            csmPlatformProperties, csmSimulationId, organization.id ?: "", workspace.key)
    envVars[RUN_TEMPLATE_ID_VAR] = runTemplateId
    envVars[CONTAINER_MODE_VAR] = step.mode

    envVars.putAll(getEventHubEnvVars(organization, workspace))

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
      if (source != STEP_SOURCE_LOCAL) {
        envVars[step.pathVar] =
            (step.path?.invoke(organization.id ?: "", solution.id ?: "", runTemplateId) ?: "")
      }
    }
    return ScenarioRunContainer(
        name = name,
        image = imageName,
        envVars = envVars,
        dependencies = dependencies,
        entrypoint = ENTRYPOINT_NAME,
        solutionContainer = true,
    )
  }

  private fun getEventHubEnvVars(
      organization: Organization,
      workspace: Workspace
  ): Map<String, String> {
    val envVars: MutableMap<String, String> = mutableMapOf()
    val eventBus = csmPlatformProperties.azure?.eventBus!!
    if (workspace.useDedicatedEventHubNamespace != true) {

      val eventHubBase = "${eventBus.baseUri}/${organization.id}-${workspace.key}".lowercase()
      envVars[EVENT_HUB_MEASURES_VAR] = eventHubBase
      val doesEventHubExist =
          checkEventHubExistenceFromCredentialType(
              eventBus, eventBus.baseUri, "${organization.id}-${workspace.key}-scenariorun")
      if (doesEventHubExist) {
        logger.debug(
            "Control Plane Event Hub ({}/{}) does exist => " +
                "instructing engine to send summary message at the end of the simulation",
            eventBus.baseUri,
            "${organization.id}-${workspace.key}-scenariorun")
        envVars[EVENT_HUB_CONTROL_PLANE_VAR] = "${eventHubBase}-scenariorun"
      } else {
        logger.debug(
            "Control Plane Event Hub ({}/{}) does not exist => " +
                "summary message will *not* be sent at the end of the simulation",
            eventBus.baseUri,
            "${organization.id}-${workspace.key}-scenariorun")
      }

      envVars.putAll(getSpecificEventHubAuthenticationEnvVars(eventBus))
    } else {
      val baseUri = "amqps://${organization.id}-${workspace.key}.servicebus.windows.net".lowercase()
      envVars[EVENT_HUB_MEASURES_VAR] = "${baseUri}/probesmeasures"

      val doesEventHubExists =
          checkEventHubExistenceFromCredentialType(
              eventBus, "${organization.id}-${workspace.key}.servicebus.windows.net", "scenariorun")
      if (doesEventHubExists) {
        logger.debug(
            "Control Plane Event Hub ({}/{}) does exist => " +
                "instructing engine to send summary message at the end of the simulation",
            "${organization.id}-${workspace.key}.servicebus.windows.net",
            "scenariorun")
        envVars[EVENT_HUB_CONTROL_PLANE_VAR] = "${baseUri}/scenariorun"
      } else {
        logger.debug(
            "Control Plane Event Hub ({}/{}) does not exist => " +
                "summary message will *not* be sent at the end of the simulation",
            eventBus.baseUri,
            "${organization.id}-${workspace.key}-scenariorun")
      }

      logger.debug(
          "Workspace ${workspace.id} set to use dedicated eventhub namespace " +
              " => " +
              "using tenant id, client id and client secret already put in the " +
              "common env vars")
    }

    return envVars.toMap()
  }

  private fun checkEventHubExistenceFromCredentialType(
      eventBus: CsmPlatformAzureEventBus,
      eventHubNamespace: String,
      eventHubName: String
  ): Boolean {
    val doesEventHubExists =
        when (eventBus.authentication.strategy) {
          SHARED_ACCESS_POLICY -> {
            azureEventHubsClient.doesEventHubExist(
                eventHubNamespace.lowercase(),
                eventHubName.lowercase(),
                eventBus.authentication.sharedAccessPolicy?.namespace?.name
                    ?: throw IllegalStateException(
                        "Missing configuration property: " +
                            "csm.platform.azure.eventBus.authentication.sharedAccessPolicy." +
                            "namespace.name"),
                eventBus.authentication.sharedAccessPolicy?.namespace?.key
                    ?: throw IllegalStateException(
                        "Missing configuration property: " +
                            "csm.platform.azure.eventBus.authentication.sharedAccessPolicy." +
                            "namespace.key"),
                ignoreErrors = true)
          }
          TENANT_CLIENT_CREDENTIALS -> {
            azureEventHubsClient.doesEventHubExist(
                eventHubNamespace, eventHubName.lowercase(), ignoreErrors = true)
          }
        }
    return doesEventHubExists
  }

  private fun getSpecificEventHubAuthenticationEnvVars(
      eventBus: CsmPlatformAzureEventBus
  ): Map<String, String> =
      when (eventBus.authentication.strategy) {
        SHARED_ACCESS_POLICY -> {
          // PROD-8071, PROD-8072 : support for shared access policies in the context of a platform
          // deployed in the customer tenant.
          // PROD-8074: In the AMQP Consumers, Shared Access Policy credentials take precedence over
          // the tenant client credentials => we can therefore safely append the former to the
          // existing env vars.
          // PROD-8599: AZURE_EVENT_HUB_SHARED_ACCESS_POLICY_ENV_VAR,
          // AZURE_EVENT_HUB_SHARED_ACCESS_KEY_ENV_VAR
          // are renamed, but are kept for backward compability reason
          val sharedAccessPolicyNamespaceName =
              eventBus.authentication.sharedAccessPolicy?.namespace?.name
                  ?: throw IllegalStateException(
                      "Missing configuration property: " +
                          "csm.platform.azure.eventBus.authentication.sharedAccessPolicy" +
                          ".namespace.name")
          val sharedAccessPolicyNamespaceKey =
              eventBus.authentication.sharedAccessPolicy?.namespace?.key
                  ?: throw IllegalStateException(
                      "Missing configuration property: " +
                          "csm.platform.azure.eventBus.authentication.sharedAccessPolicy" +
                          ".namespace.key")
          mapOf(
              AZURE_EVENT_HUB_SHARED_ACCESS_POLICY_ENV_VAR to sharedAccessPolicyNamespaceName,
              AZURE_EVENT_HUB_SHARED_ACCESS_KEY_ENV_VAR to sharedAccessPolicyNamespaceKey,
              CSM_AMQPCONSUMER_USER_ENV_VAR to sharedAccessPolicyNamespaceName,
              CSM_AMQPCONSUMER_PASSWORD_ENV_VAR to sharedAccessPolicyNamespaceKey,
              CSM_CONTROL_PLANE_USER_ENV_VAR to sharedAccessPolicyNamespaceName,
              CSM_CONTROL_PLANE_PASSWORD_ENV_VAR to sharedAccessPolicyNamespaceKey)
        }
        TENANT_CLIENT_CREDENTIALS -> {
          logger.debug(
              "csm.platform.azure.eventBus.authentication.strategy set to " +
                  "TENANT_CLIENT_CREDENTIALS => " +
                  "using tenant id, client id and client secret already put in the " +
                  "common env vars")
          mapOf()
        }
      }

  private fun getImageName(registry: String, repository: String, version: String? = null): String {
    val repoVersion = if (version == null) repository else "${repository}:${version}"
    return if (registry != "") "${registry}/${repoVersion}" else repoVersion
  }
}

internal fun getCommonEnvVars(
    csmPlatformProperties: CsmPlatformProperties,
    csmSimulationId: String,
    organizationId: String,
    workspaceKey: String,
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
  val commonEnvVars =
      mapOf(
          CSM_SIMULATION_ID to csmSimulationId,
          API_BASE_URL_VAR to csmPlatformProperties.api.baseUrl,
          API_BASE_SCOPE_VAR to "${csmPlatformProperties.azure?.appIdUri}${API_SCOPE_SUFFIX}",
          DATASET_PATH_VAR to DATASET_PATH,
          PARAMETERS_PATH_VAR to PARAMETERS_PATH,
          AZURE_DATA_EXPLORER_RESOURCE_URI_VAR to
              (csmPlatformProperties.azure?.dataWarehouseCluster?.baseUri ?: ""),
          AZURE_DATA_EXPLORER_RESOURCE_INGEST_URI_VAR to
              (csmPlatformProperties.azure?.dataWarehouseCluster?.options?.ingestionUri ?: ""),
          AZURE_DATA_EXPLORER_DATABASE_NAME to "${organizationId}-${workspaceKey}",
      )

  return (identityEnvVars + commonEnvVars).toMutableMap()
}

private fun stackSolutionContainers(
    containers: MutableList<ScenarioRunContainer>
): MutableList<ScenarioRunContainer> {
  val stackedContainers: MutableList<ScenarioRunContainer> = mutableListOf()
  var stackedContainer: ScenarioRunContainer? = null
  var stackedIndex = 1
  for (container in containers) {
    if (container.solutionContainer != true) {
      if (stackedContainer != null) {
        stackedContainers.add(stackedContainer)
        stackedIndex++
      }
      stackedContainer = null
      stackedContainers.add(container)
    } else {
      if (stackedContainer == null) {
        stackedContainer = container
      } else {
        stackedContainer = mergeSolutionContainer(stackedIndex, stackedContainer, container)
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
  val modes = "${stackedMode},${containerMode}"
  val envVars = stackedContainer.envVars?.toMutableMap()

  envVars?.put(CONTAINER_MODE_VAR, modes)

  return stackedContainer.copy(
      name = MULTIPLE_STEPS_NAME + stackedIndex.toString(),
      envVars = envVars,
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
          PARAMETERS_WORKSPACE_FILE, "${organizationId}/${workspaceId}".sanitizeForAzureStorage())
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
  return connector.parameterGroups?.flatMap { it.parameters }?.filter { it.envVar == null }?.map {
    resolvePlatformVars(
        csmPlatformProperties,
        dataset.connector?.parametersValues?.getOrDefault(it.id, it.default ?: "") ?: "",
        organizationId,
        workspaceId)
  }
}

private fun getSource(source: RunTemplateStepSource?) =
    when (source) {
      RunTemplateStepSource.local -> STEP_SOURCE_LOCAL
      RunTemplateStepSource.cloud -> STEP_SOURCE_CLOUD
      else -> null
    }
