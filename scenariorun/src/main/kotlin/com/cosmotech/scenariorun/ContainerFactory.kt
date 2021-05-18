// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun

import com.cosmotech.api.azure.sanitizeForAzureStorage
import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.connector.api.ConnectorApiService
import com.cosmotech.connector.domain.Connector
import com.cosmotech.dataset.api.DatasetApiService
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.domain.Organization
import com.cosmotech.scenario.api.ScenarioApiService
import com.cosmotech.scenario.domain.Scenario
import com.cosmotech.scenariorun.api.ScenariorunApiService
import com.cosmotech.scenariorun.domain.ScenarioRunContainer
import com.cosmotech.scenariorun.domain.ScenarioRunStartContainers
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.solution.domain.RunTemplate
import com.cosmotech.solution.domain.RunTemplateStepSource
import com.cosmotech.solution.domain.Solution
import com.cosmotech.solution.utils.*
import com.cosmotech.workspace.api.WorkspaceApiService
import com.cosmotech.workspace.domain.Workspace
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

private const val PARAMETERS_WORKSPACE_FILE = "%WORKSPACE_FILE%"
private const val PARAMETERS_DATASET_ID = "%DATASETID%"
private const val CONTAINER_FETCH_DATASET = "fetchDatasetContainer"
private const val CONTAINER_FETCH_PARAMETERS = "fetchScenarioParametersContainer"
private const val CONTAINER_FETCH_DATASET_PARAMETERS = "fetchScenarioDatasetParametersContainer"
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
private const val AZURE_TENANT_ID_VAR = "AZURE_TENANT_ID"
private const val AZURE_CLIENT_ID_VAR = "AZURE_CLIENT_ID"
private const val AZURE_CLIENT_SECRET_VAR = "AZURE_CLIENT_SECRET"
private const val API_BASE_URL_VAR = "CSM_API_URL"
private const val DATASET_PATH_VAR = "CSM_DATASET_ABSOLUTE_PATH"
private const val DATASET_PATH = "/mnt/scenariorun-data"
private const val PARAMETERS_PATH_VAR = "CSM_PARAMETERS_ABSOLUTE_PATH"
private const val PARAMETERS_PATH = "/mnt/scenariorun-parameters"
private const val FETCH_PATH_VAR = "CSM_FETCH_ABSOLUTE_PATH"
private const val PARAMETERS_FETCH_CONTAINER_SCENARIO_VAR = "CSM_SCENARIO_ID"
private const val SEND_DATAWAREHOUSE_PARAMETERS_VAR = "CSM_SEND_DATAWAREHOUSE_PARAMETERS"
private const val SEND_DATAWAREHOUSE_DATASETS_VAR = "CSM_SEND_DATAWAREHOUSE_DATASETS"
private const val ADX_DATA_INGESTION_URI_VAR = "ADX_DATA_INGESTION_URI"
private const val ADX_DATABASE = "ADX_DATABASE"
private const val RUN_TEMPLATE_ID_VAR = "CSM_RUN_TEMPLATE_ID"
private const val CONTAINER_MODE_VAR = "CSM_CONTAINER_MODE"
private const val ENTRYPOINT_NAME = "entrypoint.py"
private const val EVENT_HUB_CONTROL_PLANE_VAR = "CSM_CONTROL_PLANE_TOPIC"
private const val CONTROL_PLANE_SUFFIX = "-scenariorun"
private const val EVENT_HUB_MEASURES_VAR = "CSM_PROBES_MEASURES_TOPIC"
private const val CSM_SIMULATION_VAR = "CSM_SIMULATION"
private const val NODE_PARAM_NONE = "%NONE%"
private const val NODE_LABEL_DEFAULT = "basic"
private const val NODE_LABEL_SUFFIX = "pool"
private const val GENERATE_NAME_PREFIX = "workflow-"
private const val GENERATE_NAME_SUFFIX = "-"
private const val STEP_SOURCE_LOCAL = "local"
private const val STEP_SOURCE_CLOUD = "azureStorage"
private const val AZURE_STORAGE_CONNECTION_STRING = "AZURE_STORAGE_CONNECTION_STRING"

@Component
class ContainerFactory(
    @Autowired val csmPlatformProperties: CsmPlatformProperties,
    val steps: Map<String, SolutionContainerStepSpec> =
        mapOf(
            "handle-parameters" to
                SolutionContainerStepSpec(
                    mode = "handle-parameters",
                    providerVar = "CSM_PARAMETERS_HANDLER_PROVIDER",
                    pathVar = "CSM_PARAMETERS_HANDLER_PATH",
                    source =
                        fun(template): String? {
                          return ContainerFactory.getSource(template.parametersHandlerSource)
                        },
                    path =
                        fun(organizationId, solutionId): String {
                          return getCloudPath(
                              organizationId, solutionId, StepResource.PARAMETERS_HANDLER)
                        }),
            "validate" to
                SolutionContainerStepSpec(
                    mode = "validate",
                    providerVar = "CSM_DATASET_VALIDATOR_PROVIDER",
                    pathVar = "CSM_DATASET_VALIDATOR_PATH",
                    source =
                        fun(template): String? {
                          return ContainerFactory.getSource(template.datasetValidatorSource)
                        },
                    path =
                        fun(organizationId, solutionId): String {
                          return getCloudPath(organizationId, solutionId, StepResource.VALIDATOR)
                        }),
            "prerun" to
                SolutionContainerStepSpec(
                    mode = "prerun",
                    providerVar = "CSM_PRERUN_PROVIDER",
                    pathVar = "CSM_PRERUN_PATH",
                    source =
                        fun(template): String? {
                          return ContainerFactory.getSource(template.preRunSource)
                        },
                    path =
                        fun(organizationId, solutionId): String {
                          return getCloudPath(organizationId, solutionId, StepResource.PRERUN)
                        }),
            "engine" to
                SolutionContainerStepSpec(
                    mode = "engine",
                    providerVar = "CSM_ENGINE_PROVIDER",
                    pathVar = "CSM_ENGINE_PATH",
                    source =
                        fun(template): String? {
                          return ContainerFactory.getSource(template.runSource)
                        },
                    path =
                        fun(organizationId, solutionId): String {
                          return getCloudPath(organizationId, solutionId, StepResource.ENGINE)
                        }),
            "postrun" to
                SolutionContainerStepSpec(
                    mode = "postrun",
                    providerVar = "CSM_POSTRUN_PROVIDER",
                    pathVar = "CSM_POSTRUN_PATH",
                    source =
                        fun(template): String? {
                          return ContainerFactory.getSource(template.postRunSource)
                        },
                    path =
                        fun(organizationId, solutionId): String {
                          return getCloudPath(organizationId, solutionId, StepResource.POSTRUN)
                        }),
        )
) {
  private val logger = LoggerFactory.getLogger(ArgoAdapter::class.java)

  fun getStartInfo(
      organizationId: String,
      workspaceId: String,
      scenarioId: String,
      scenarioRunService: ScenariorunApiService,
      scenarioService: ScenarioApiService,
      workspaceService: WorkspaceApiService,
      solutionService: SolutionApiService,
      organizationService: OrganizationApiService,
      connectorService: ConnectorApiService,
      datasetService: DatasetApiService
  ): StartInfo {
    val organization = organizationService.findOrganizationById(organizationId)
    val workspace = workspaceService.findWorkspaceById(organizationId, workspaceId)
    val solution = solutionService.findSolutionById(organizationId, workspace.solution.solutionId)
    val scenario = scenarioService.findScenarioById(organizationId, workspaceId, scenarioId)
    val runTemplate = this.getRunTemplate(solution, (scenario.runTemplateId ?: ""))
    val datasetsAndConnectors =
        findDatasetsAndConnectors(
            organizationId, scenario, solution, datasetService, connectorService, runTemplate)

    return StartInfo(
        startContainers =
            buildContainersStart(
                scenario,
                datasetsAndConnectors.datasets,
                datasetsAndConnectors.connectors,
                workspace,
                organization,
                solution),
        scenario = scenario,
        workspace = workspace,
        solution = solution,
        runTemplate = runTemplate,
    )
  }

  fun findDatasetsAndConnectors(
      organizationId: String,
      scenario: Scenario,
      solution: Solution,
      datasetService: DatasetApiService,
      connectorService: ConnectorApiService,
      runTemplate: RunTemplate
  ): DatasetsConnectors {
    var datasets: MutableMap<String, Dataset> = mutableMapOf()
    var connectors: MutableMap<String, Connector> = mutableMapOf()
    scenario.datasetList?.forEach { datasetId ->
      addDatasetAndConnector(
          organizationId, datasetId, datasets, connectors, datasetService, connectorService)
    }
    val parameterGroupIds = runTemplate.parameterGroups
    if (parameterGroupIds != null) {
      var parametersIds =
          (solution.parameterGroups?.filter { it.id in parameterGroupIds }?.map { it.parameters })
              ?.flatten()
      if (parametersIds != null) {
        solution
            .parameters
            ?.filter { it.id in parametersIds }
            ?.filter { it.varType == PARAMETERS_DATASET_ID }
            ?.forEach { parameter ->
              val parameterValue =
                  scenario.parametersValues?.find { it.parameterId == parameter.id }
              if (parameterValue != null && parameterValue.value != "") {
                addDatasetAndConnector(
                    organizationId,
                    parameterValue.value,
                    datasets,
                    connectors,
                    datasetService,
                    connectorService)
              }
            }
      }
    }
    return DatasetsConnectors(
        datasets = datasets.values.toList(), connectors = connectors.values.toList())
  }

  fun addDatasetAndConnector(
      organizationId: String,
      datasetId: String,
      datasets: MutableMap<String, Dataset>,
      connectors: MutableMap<String, Connector>,
      datasetService: DatasetApiService,
      connectorService: ConnectorApiService
  ) {
    if (datasetId !in datasets) {
      val dataset = datasetService.findDatasetById(organizationId, datasetId)
      datasets.put(datasetId, dataset)
      val connectorId = dataset.connector?.id
      if (connectorId == null)
          throw IllegalStateException("Connector Id for Dataset ${datasetId} is null")
      if (connectorId !in connectors) {
        val connector = connectorService.findConnectorById(connectorId)
        connectors.put(connectorId, connector)
      }
    }
  }

  fun buildContainersStart(
      scenario: Scenario,
      datasets: List<Dataset>?,
      connectors: List<Connector>?,
      workspace: Workspace,
      organization: Organization,
      solution: Solution
  ): ScenarioRunStartContainers {
    if (scenario.runTemplateId == "")
        throw IllegalStateException("Scenario runTemplateId cannot be null")
    val template = getRunTemplate(solution, (scenario.runTemplateId ?: ""))
    val nodeLabel =
        if (template.computeSize == NODE_PARAM_NONE) null
        else {
          if (template.computeSize != null) "${template.computeSize}${NODE_LABEL_SUFFIX}"
          else "${NODE_LABEL_DEFAULT}${NODE_LABEL_SUFFIX}"
        }
    val containers =
        buildContainersPipeline(scenario, datasets, connectors, workspace, organization, solution)
    val generateName = "${GENERATE_NAME_PREFIX}${scenario.id}${GENERATE_NAME_SUFFIX}".lowercase()
    return ScenarioRunStartContainers(
        generateName = generateName,
        nodeLabel = nodeLabel,
        containers = containers,
    )
  }

  fun buildContainersPipeline(
      scenario: Scenario,
      datasets: List<Dataset>?,
      connectors: List<Connector>?,
      workspace: Workspace,
      organization: Organization,
      solution: Solution
  ): List<ScenarioRunContainer> {
    if (scenario.id == null) throw IllegalStateException("Scenario Id cannot be null")
    val runTemplateId =
        scenario.runTemplateId
            ?: throw IllegalStateException("Scenario runTemplateId cannot be null")
    val template = getRunTemplate(solution, runTemplateId)

    var containers: MutableList<ScenarioRunContainer> = mutableListOf()

    if (testStep(template.fetchDatasets) && datasets != null && connectors != null) {
      var datasetCount = 1
      scenario.datasetList?.forEach { datasetId ->
        val dataset = datasets.find { it.id == datasetId }
        if (dataset == null)
            throw IllegalStateException("Dataset ${datasetId} not found in Datasets")
        val connector =
            connectors.find { connector -> connector.id == (dataset.connector?.id ?: "") }
        if (connector == null)
            throw IllegalStateException(
                "Connector id ${dataset.connector?.id} not found in connectors list")
        containers.add(
            this.buildFromDataset(
                dataset,
                connector,
                datasetCount,
                false,
                dataset.id ?: "",
                organization.id ?: "",
                workspace.id ?: ""))
        datasetCount++
      }
    }
    if (testStep(template.fetchScenarioParameters)) {
      containers.add(this.buildScenarioParametersFetchContainer(scenario.id ?: ""))
      containers.addAll(
          buildScenarioParametersDatasetFetchContainers(
              scenario, solution, datasets, connectors, organization.id ?: "", workspace.id ?: ""))
    }
    if (testStep(template.applyParameters))
        containers.add(
            this.buildApplyParametersContainer(organization, workspace, solution, runTemplateId))
    if (testStep(template.validateData))
        containers.add(
            this.buildValidateDataContainer(organization, workspace, solution, runTemplateId))
    val sendParameters =
        getSendOptionValue(
            workspace.sendInputToDataWarehouse, template.sendInputParametersToDataWarehouse)
    val sendDatasets =
        getSendOptionValue(workspace.sendInputToDataWarehouse, template.sendDatasetsToDataWarehouse)
    if (sendParameters || sendDatasets)
        containers.add(this.buildSendDataWarehouseContainer(organization.id, workspace, template))
    if (testStep(template.preRun))
        containers.add(this.buildPreRunContainer(organization, workspace, solution, runTemplateId))
    if (testStep(template.run))
        containers.add(this.buildRunContainer(organization, workspace, solution, runTemplateId))
    if (testStep(template.postRun))
        containers.add(this.buildPostRunContainer(organization, workspace, solution, runTemplateId))

    return containers.toList()
  }

  private fun testStep(step: Boolean?): Boolean {
    return step ?: true
  }

  fun buildFromDataset(
      dataset: Dataset,
      connector: Connector,
      datasetCount: Int,
      parametersFetch: Boolean,
      fetchId: String,
      organizationId: String,
      workspaceId: String,
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
    return ScenarioRunContainer(
        name = "${nameBase}-${datasetCount.toString()}",
        image =
            getImageName(
                csmPlatformProperties.azure?.containerRegistries?.core ?: "",
                connector.repository,
                connector.version),
        envVars =
            getDatasetEnvVars(
                dataset, connector, fetchPathBase, fetchId, organizationId, workspaceId),
        runArgs = getDatasetRunArgs(dataset, connector, organizationId, workspaceId))
  }

  fun buildScenarioParametersDatasetFetchContainers(
      scenario: Scenario,
      solution: Solution,
      datasets: List<Dataset>?,
      connectors: List<Connector>?,
      organizationId: String,
      workspaceId: String,
  ): List<ScenarioRunContainer> {
    var containers: MutableList<ScenarioRunContainer> = mutableListOf()
    var datasetParameterCount = 1
    if (scenario.parametersValues != null) {
      scenario.parametersValues?.forEach { parameterValue ->
        val parameter =
            solution.parameters?.find { solutionParameter ->
              solutionParameter.id == parameterValue.parameterId
            }
        if (parameter == null)
            throw IllegalStateException(
                "Parameter ${parameterValue.parameterId} not found in Solution ${solution.id}")
        if (parameter.varType == PARAMETERS_DATASET_ID) {
          val dataset = datasets?.find { dataset -> dataset.id == parameterValue.value }
          if (dataset == null)
              throw IllegalStateException(
                  "Dataset ${parameterValue.value} cannot be found in Datasets")
          val connector = connectors?.find { connector -> connector.id == dataset.connector?.id }
          if (connector == null)
              throw IllegalStateException(
                  "Connector id ${dataset.connector?.id} not found in connectors list")

          containers.add(
              buildFromDataset(
                  dataset,
                  connector,
                  datasetParameterCount,
                  true,
                  parameter.id,
                  organizationId,
                  workspaceId))
          datasetParameterCount++
        }
      }
    }

    return containers.toList()
  }
  fun buildScenarioParametersFetchContainer(scenarioId: String): ScenarioRunContainer {
    val envVars = getCommonEnvVars()
    envVars.put(PARAMETERS_FETCH_CONTAINER_SCENARIO_VAR, scenarioId)
    return ScenarioRunContainer(
        name = CONTAINER_FETCH_PARAMETERS,
        image = csmPlatformProperties.images.scenarioFetchParameters,
        envVars = envVars,
    )
  }

  fun buildSendDataWarehouseContainer(
      organizationId: String?,
      workspace: Workspace,
      runTemplate: RunTemplate
  ): ScenarioRunContainer {
    if (organizationId == null) throw IllegalStateException("Organization Id cannot be null")
    val envVars = getCommonEnvVars()
    val sendParameters =
        getSendOptionValue(
            workspace.sendInputToDataWarehouse, runTemplate.sendInputParametersToDataWarehouse)
    val sendDatasets =
        getSendOptionValue(
            workspace.sendInputToDataWarehouse, runTemplate.sendDatasetsToDataWarehouse)
    envVars.put(SEND_DATAWAREHOUSE_PARAMETERS_VAR, (sendParameters).toString())
    envVars.put(SEND_DATAWAREHOUSE_DATASETS_VAR, (sendDatasets).toString())
    envVars.put(
        ADX_DATA_INGESTION_URI_VAR,
        csmPlatformProperties.azure?.dataWarehouseCluster?.options?.ingestionUri ?: "")
    envVars.put(ADX_DATABASE, "${organizationId}-${workspace.key}")
    return ScenarioRunContainer(
        name = CONTAINER_SEND_DATAWAREHOUSE,
        image = csmPlatformProperties.images.sendDataWarehouse,
        envVars = envVars)
  }

  fun buildApplyParametersContainer(
      organization: Organization,
      workspace: Workspace,
      solution: Solution,
      runTemplateId: String
  ): ScenarioRunContainer {
    return this.buildSolutionContainer(
        organization,
        workspace,
        solution,
        runTemplateId,
        CONTAINER_APPLY_PARAMETERS,
        steps.get(CONTAINER_APPLY_PARAMETERS_MODE))
  }

  fun buildValidateDataContainer(
      organization: Organization,
      workspace: Workspace,
      solution: Solution,
      runTemplateId: String
  ): ScenarioRunContainer {
    return this.buildSolutionContainer(
        organization,
        workspace,
        solution,
        runTemplateId,
        CONTAINER_VALIDATE_DATA,
        steps.get(CONTAINER_VALIDATE_DATA_MODE))
  }

  fun buildPreRunContainer(
      organization: Organization,
      workspace: Workspace,
      solution: Solution,
      runTemplateId: String
  ): ScenarioRunContainer {
    return this.buildSolutionContainer(
        organization,
        workspace,
        solution,
        runTemplateId,
        CONTAINER_PRERUN,
        steps.get(CONTAINER_PRERUN_MODE))
  }

  fun buildRunContainer(
      organization: Organization,
      workspace: Workspace,
      solution: Solution,
      runTemplateId: String
  ): ScenarioRunContainer {
    return this.buildSolutionContainer(
        organization,
        workspace,
        solution,
        runTemplateId,
        CONTAINER_RUN,
        steps.get(CONTAINER_RUN_MODE))
  }

  fun buildPostRunContainer(
      organization: Organization,
      workspace: Workspace,
      solution: Solution,
      runTemplateId: String
  ): ScenarioRunContainer {
    return this.buildSolutionContainer(
        organization,
        workspace,
        solution,
        runTemplateId,
        CONTAINER_POSTRUN,
        steps.get(CONTAINER_POSTRUN_MODE))
  }

  fun getSendOptionValue(workspaceOption: Boolean?, templateOption: Boolean?): Boolean {
    if (templateOption != null) {
      return templateOption
    } else {
      return workspaceOption ?: true
    }
  }

  private fun getRunTemplate(solution: Solution, runTemplateId: String): RunTemplate {
    val template = solution.runTemplates.find { runTemplate -> runTemplate.id == runTemplateId }
    if (template == null) {
      throw IllegalStateException(
          "runTemplateId ${runTemplateId} not found in Solution ${solution.id}")
    }

    return template
  }

  private fun buildSolutionContainer(
      organization: Organization,
      workspace: Workspace,
      solution: Solution,
      runTemplateId: String,
      name: String,
      step: SolutionContainerStepSpec?
  ): ScenarioRunContainer {

    if (step == null) throw IllegalStateException("Solution Container Step Spec is not defined")
    val template = getRunTemplate(solution, runTemplateId)
    val imageName =
        getImageName(
            csmPlatformProperties.azure?.containerRegistries?.solutions ?: "",
            solution.repository,
            solution.version)
    val envVars = getCommonEnvVars()
    envVars.put(RUN_TEMPLATE_ID_VAR, runTemplateId)
    envVars.put(CONTAINER_MODE_VAR, step.mode)
    envVars.put(
        EVENT_HUB_CONTROL_PLANE_VAR,
        "${csmPlatformProperties.azure?.eventBus?.baseUri}/${organization.id}-${workspace.key}${CONTROL_PLANE_SUFFIX}".lowercase())
    envVars.put(
        EVENT_HUB_MEASURES_VAR,
        "${csmPlatformProperties.azure?.eventBus?.baseUri}/${organization.id}-${workspace.key}".lowercase())
    val csmSimulation = template.csmSimulation
    if (csmSimulation != null) {
      envVars.put(CSM_SIMULATION_VAR, csmSimulation)
    }
    val source = step.source?.invoke(template)
    if (source != null) {
      envVars.put(step.providerVar, source)
      envVars.put(
          AZURE_STORAGE_CONNECTION_STRING,
          csmPlatformProperties.azure?.storage?.connectionString ?: "")
      if (organization.id == null) throw IllegalStateException("Organization id cannot be null")
      if (workspace.id == null) throw IllegalStateException("Workspace id cannot be null")
      if (source != STEP_SOURCE_LOCAL) {
        envVars.put(
            step.pathVar, (step.path?.invoke(organization.id ?: "", solution.id ?: "") ?: ""))
      }
    }
    return ScenarioRunContainer(
        name = name,
        image = imageName,
        envVars = envVars,
        entrypoint = ENTRYPOINT_NAME,
    )
  }

  private fun getImageName(registry: String, repository: String, version: String): String {
    if (registry != "") return "${registry}/${repository}:${version}"
    else return "${repository}:${version}"
  }

  private fun getDatasetEnvVars(
      dataset: Dataset,
      connector: Connector,
      fetchPathBase: String,
      fetchId: String,
      organizationId: String,
      workspaceId: String,
  ): Map<String, String> {
    val envVars = getCommonEnvVars()
    envVars.put(FETCH_PATH_VAR, "${fetchPathBase}/${fetchId}")
    val datasetEnvVars =
        connector
            .parameterGroups
            ?.flatMap { it.parameters }
            ?.filter { it.envVar != null }
            ?.associateBy(
                { it.envVar ?: "" },
                {
                  resolvePath(
                      dataset.connector?.parametersValues?.getOrDefault(it.id, it.default ?: "")
                          ?: "",
                      organizationId,
                      workspaceId)
                })
    if (datasetEnvVars != null) envVars.putAll(datasetEnvVars)
    return envVars
  }

  private fun resolvePath(path: String, organizationId: String, workspaceId: String): String {
    return path.replace(
        PARAMETERS_WORKSPACE_FILE, "${organizationId}/${workspaceId}".sanitizeForAzureStorage())
  }

  private fun getDatasetRunArgs(
      dataset: Dataset,
      connector: Connector,
      organizationId: String,
      workspaceId: String
  ): List<String>? {
    return connector.parameterGroups?.flatMap { it.parameters }?.filter { it.envVar == null }?.map {
      resolvePath(
          dataset.connector?.parametersValues?.getOrDefault(it.id, it.default ?: "") ?: "",
          organizationId,
          workspaceId)
    }
  }

  private fun getCommonEnvVars(): MutableMap<String, String> {
    return mutableMapOf(
        AZURE_TENANT_ID_VAR to (csmPlatformProperties.azure?.credentials?.tenantId ?: ""),
        AZURE_CLIENT_ID_VAR to (csmPlatformProperties.azure?.credentials?.clientId ?: ""),
        AZURE_CLIENT_SECRET_VAR to (csmPlatformProperties.azure?.credentials?.clientSecret ?: ""),
        API_BASE_URL_VAR to
            "${csmPlatformProperties.api.baseUrl}/${csmPlatformProperties.api.basePath}/${csmPlatformProperties.api.version}",
        DATASET_PATH_VAR to DATASET_PATH,
        PARAMETERS_PATH_VAR to PARAMETERS_PATH,
    )
  }

  companion object {
    private fun getSource(source: RunTemplateStepSource?): String? {
      if (source == null) return null
      if (source == RunTemplateStepSource.local) return STEP_SOURCE_LOCAL
      if (source == RunTemplateStepSource.cloud) return STEP_SOURCE_CLOUD
      return null
    }
  }

  data class DatasetsConnectors(val datasets: List<Dataset>, val connectors: List<Connector>)

  data class StartInfo(
      val startContainers: ScenarioRunStartContainers,
      val scenario: Scenario,
      val workspace: Workspace,
      val solution: Solution,
      val runTemplate: RunTemplate
  )
}
