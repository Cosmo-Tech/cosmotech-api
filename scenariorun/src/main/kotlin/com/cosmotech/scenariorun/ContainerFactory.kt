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
import com.cosmotech.solution.domain.RunTemplateHandlerId
import com.cosmotech.solution.domain.RunTemplateStepSource
import com.cosmotech.solution.domain.Solution
import com.cosmotech.solution.utils.*
import com.cosmotech.workspace.api.WorkspaceApiService
import com.cosmotech.workspace.domain.Workspace
import java.util.UUID
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

private const val PARAMETERS_WORKSPACE_FILE = "%WORKSPACE_FILE%"
private const val PARAMETERS_DATASET_ID = "%DATASETID%"
private const val PARAMETERS_STORAGE_CONNECTION_STRING = "%STORAGE_CONNECTION_STRING%"
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
private const val CSM_SIMULATION_ID = "CSM_SIMULATION_ID"
private const val API_BASE_URL_VAR = "CSM_API_URL"
private const val API_BASE_SCOPE_VAR = "CSM_API_SCOPE"
private const val API_SCOPE_SUFFIX = "/.default"
private const val DATASET_PATH_VAR = "CSM_DATASET_ABSOLUTE_PATH"
private const val DATASET_PATH = "/mnt/scenariorun-data"
private const val PARAMETERS_PATH_VAR = "CSM_PARAMETERS_ABSOLUTE_PATH"
private const val PARAMETERS_PATH = "/mnt/scenariorun-parameters"
private const val FETCH_PATH_VAR = "CSM_FETCH_ABSOLUTE_PATH"
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
                    source = { template -> getSource(template.parametersHandlerSource) },
                    path = { organizationId, solutionId ->
                      getCloudPath(
                          organizationId, solutionId, RunTemplateHandlerId.parameters_handler)
                    }),
            "validate" to
                SolutionContainerStepSpec(
                    mode = "validate",
                    providerVar = "CSM_DATASET_VALIDATOR_PROVIDER",
                    pathVar = "CSM_DATASET_VALIDATOR_PATH",
                    source = { template -> getSource(template.datasetValidatorSource) },
                    path = { organizationId, solutionId ->
                      getCloudPath(organizationId, solutionId, RunTemplateHandlerId.validator)
                    }),
            "prerun" to
                SolutionContainerStepSpec(
                    mode = "prerun",
                    providerVar = "CSM_PRERUN_PROVIDER",
                    pathVar = "CSM_PRERUN_PATH",
                    source = { template -> getSource(template.preRunSource) },
                    path = { organizationId, solutionId ->
                      getCloudPath(organizationId, solutionId, RunTemplateHandlerId.prerun)
                    }),
            "engine" to
                SolutionContainerStepSpec(
                    mode = "engine",
                    providerVar = "CSM_ENGINE_PROVIDER",
                    pathVar = "CSM_ENGINE_PATH",
                    source = { template -> getSource(template.runSource) },
                    path = { organizationId, solutionId ->
                      getCloudPath(organizationId, solutionId, RunTemplateHandlerId.engine)
                    }),
            "postrun" to
                SolutionContainerStepSpec(
                    mode = "postrun",
                    providerVar = "CSM_POSTRUN_PROVIDER",
                    pathVar = "CSM_POSTRUN_PATH",
                    source = { template -> getSource(template.postRunSource) },
                    path = { organizationId, solutionId ->
                      getCloudPath(organizationId, solutionId, RunTemplateHandlerId.postrun)
                    }),
        )
) {

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
    if (workspace.solution.solutionId == null)
        throw IllegalStateException("You cannot start a workspace with no solutionId defined")
    val solution =
        solutionService.findSolutionById(organizationId, workspace.solution.solutionId ?: "")
    val scenario = scenarioService.findScenarioById(organizationId, workspaceId, scenarioId)
    val runTemplate = this.getRunTemplate(solution, (scenario.runTemplateId ?: ""))
    val datasetsAndConnectors =
        findDatasetsAndConnectors(
            organizationId, scenario, solution, datasetService, connectorService, runTemplate)
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
                csmSimulationId),
        scenario = scenario,
        workspace = workspace,
        solution = solution,
        runTemplate = runTemplate,
        csmSimulationId = csmSimulationId,
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
    val datasets: MutableMap<String, Dataset> = mutableMapOf()
    val connectors: MutableMap<String, Connector> = mutableMapOf()
    scenario.datasetList?.forEach { datasetId ->
      addDatasetAndConnector(
          organizationId, datasetId, datasets, connectors, datasetService, connectorService)
    }
    val parameterGroupIds = runTemplate.parameterGroups
    if (parameterGroupIds != null) {
      val parametersIds =
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
      datasets[datasetId] = dataset
      val connectorId =
          dataset.connector?.id
              ?: throw IllegalStateException("Connector Id for Dataset $datasetId is null")
      if (connectorId !in connectors) {
        val connector = connectorService.findConnectorById(connectorId)
        connectors[connectorId] = connector
      }
    }
  }

  fun buildContainersStart(
      scenario: Scenario,
      datasets: List<Dataset>?,
      connectors: List<Connector>?,
      workspace: Workspace,
      organization: Organization,
      solution: Solution,
      csmSimulationId: String
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
        buildContainersPipeline(
            scenario, datasets, connectors, workspace, organization, solution, csmSimulationId)
    val generateName = "${GENERATE_NAME_PREFIX}${scenario.id}${GENERATE_NAME_SUFFIX}".lowercase()
    return ScenarioRunStartContainers(
        generateName = generateName,
        nodeLabel = nodeLabel,
        containers = containers,
        csmSimulationId = csmSimulationId,
    )
  }

  fun buildContainersPipeline(
      scenario: Scenario,
      datasets: List<Dataset>?,
      connectors: List<Connector>?,
      workspace: Workspace,
      organization: Organization,
      solution: Solution,
      csmSimulationId: String
  ): List<ScenarioRunContainer> {
    if (scenario.id == null) throw IllegalStateException("Scenario Id cannot be null")
    val runTemplateId =
        scenario.runTemplateId
            ?: throw IllegalStateException("Scenario runTemplateId cannot be null")
    val template = getRunTemplate(solution, runTemplateId)

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
        containers.add(
            this.buildFromDataset(
                dataset,
                connector,
                datasetCount,
                false,
                null,
                organization.id ?: "",
                workspace.id ?: "",
                workspace.key,
                csmSimulationId))
        datasetCount++
      }
    }
    if (testStep(template.fetchScenarioParameters)) {
      containers.add(
          this.buildScenarioParametersFetchContainer(
              organization.id ?: "",
              workspace.id ?: "",
              workspace.key,
              scenario.id ?: "",
              csmSimulationId,
              template.parametersJson))
      containers.addAll(
          buildScenarioParametersDatasetFetchContainers(
              scenario,
              solution,
              datasets,
              connectors,
              organization.id ?: "",
              workspace.id ?: "",
              workspace.key,
              csmSimulationId))
    }
    if (testStep(template.applyParameters))
        containers.add(
            this.buildApplyParametersContainer(
                organization, workspace, solution, runTemplateId, csmSimulationId))
    if (testStep(template.validateData))
        containers.add(
            this.buildValidateDataContainer(
                organization, workspace, solution, runTemplateId, csmSimulationId))
    val sendParameters =
        getSendOptionValue(
            workspace.sendInputToDataWarehouse, template.sendInputParametersToDataWarehouse)
    val sendDatasets =
        getSendOptionValue(workspace.sendInputToDataWarehouse, template.sendDatasetsToDataWarehouse)
    if (sendParameters || sendDatasets)
        containers.add(
            this.buildSendDataWarehouseContainer(
                organization.id, workspace, template, csmSimulationId))
    if (testStep(template.preRun))
        containers.add(
            this.buildPreRunContainer(
                organization, workspace, solution, runTemplateId, csmSimulationId))
    if (testStep(template.run))
        containers.add(
            this.buildRunContainer(
                organization, workspace, solution, runTemplateId, csmSimulationId))
    if (testStep(template.postRun))
        containers.add(
            this.buildPostRunContainer(
                organization, workspace, solution, runTemplateId, csmSimulationId))

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
    return ScenarioRunContainer(
        name = "${nameBase}-$datasetCount",
        image =
            getImageName(
                csmPlatformProperties.azure?.containerRegistries?.core ?: "",
                connector.repository,
                connector.version),
        envVars =
            getDatasetEnvVars(
                dataset,
                connector,
                fetchPathBase,
                fetchId,
                organizationId,
                workspaceId,
                workspaceKey,
                csmSimulationId),
        runArgs = getDatasetRunArgs(dataset, connector, organizationId, workspaceId))
  }

  fun buildScenarioParametersDatasetFetchContainers(
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
          val dataset =
              datasets?.find { dataset -> dataset.id == parameterValue.value }
                  ?: throw IllegalStateException(
                      "Dataset ${parameterValue.value} cannot be found in Datasets")
          val connector =
              connectors?.find { connector -> connector.id == dataset.connector?.id }
                  ?: throw IllegalStateException(
                      "Connector id ${dataset.connector?.id} not found in connectors list")

          containers.add(
              buildFromDataset(
                  dataset,
                  connector,
                  datasetParameterCount,
                  true,
                  parameter.id,
                  organizationId,
                  workspaceId,
                  workspaceKey,
                  csmSimulationId))
          datasetParameterCount++
        }
      }
    }

    return containers.toList()
  }
  fun buildScenarioParametersFetchContainer(
      organizationId: String,
      workspaceId: String,
      workspaceKey: String,
      scenarioId: String,
      csmSimulationId: String,
      jsonFile: Boolean? = null,
  ): ScenarioRunContainer {
    val envVars = getCommonEnvVars(csmSimulationId, organizationId, workspaceKey)
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
        envVars = envVars,
    )
  }

  fun buildSendDataWarehouseContainer(
      organizationId: String?,
      workspace: Workspace,
      runTemplate: RunTemplate,
      csmSimulationId: String
  ): ScenarioRunContainer {
    if (organizationId == null) throw IllegalStateException("Organization Id cannot be null")
    val envVars = getCommonEnvVars(csmSimulationId, organizationId, workspace.key)
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
        envVars = envVars)
  }

  fun buildApplyParametersContainer(
      organization: Organization,
      workspace: Workspace,
      solution: Solution,
      runTemplateId: String,
      csmSimulationId: String
  ): ScenarioRunContainer {
    return this.buildSolutionContainer(
        organization,
        workspace,
        solution,
        runTemplateId,
        CONTAINER_APPLY_PARAMETERS,
        steps[CONTAINER_APPLY_PARAMETERS_MODE],
        csmSimulationId)
  }

  fun buildValidateDataContainer(
      organization: Organization,
      workspace: Workspace,
      solution: Solution,
      runTemplateId: String,
      csmSimulationId: String
  ): ScenarioRunContainer {
    return this.buildSolutionContainer(
        organization,
        workspace,
        solution,
        runTemplateId,
        CONTAINER_VALIDATE_DATA,
        steps[CONTAINER_VALIDATE_DATA_MODE],
        csmSimulationId)
  }

  fun buildPreRunContainer(
      organization: Organization,
      workspace: Workspace,
      solution: Solution,
      runTemplateId: String,
      csmSimulationId: String
  ): ScenarioRunContainer {
    return this.buildSolutionContainer(
        organization,
        workspace,
        solution,
        runTemplateId,
        CONTAINER_PRERUN,
        steps[CONTAINER_PRERUN_MODE],
        csmSimulationId)
  }

  fun buildRunContainer(
      organization: Organization,
      workspace: Workspace,
      solution: Solution,
      runTemplateId: String,
      csmSimulationId: String
  ): ScenarioRunContainer {
    return this.buildSolutionContainer(
        organization,
        workspace,
        solution,
        runTemplateId,
        CONTAINER_RUN,
        steps[CONTAINER_RUN_MODE],
        csmSimulationId)
  }

  fun buildPostRunContainer(
      organization: Organization,
      workspace: Workspace,
      solution: Solution,
      runTemplateId: String,
      csmSimulationId: String
  ): ScenarioRunContainer {
    return this.buildSolutionContainer(
        organization,
        workspace,
        solution,
        runTemplateId,
        CONTAINER_POSTRUN,
        steps[CONTAINER_POSTRUN_MODE],
        csmSimulationId)
  }

  fun getSendOptionValue(workspaceOption: Boolean?, templateOption: Boolean?): Boolean {
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
      csmSimulationId: String
  ): ScenarioRunContainer {

    if (step == null) throw IllegalStateException("Solution Container Step Spec is not defined")
    val template = getRunTemplate(solution, runTemplateId)
    val imageName =
        getImageName(
            csmPlatformProperties.azure?.containerRegistries?.solutions ?: "",
            solution.repository,
            solution.version)
    val envVars = getCommonEnvVars(csmSimulationId, organization.id ?: "", workspace.key)
    envVars[RUN_TEMPLATE_ID_VAR] = runTemplateId
    envVars[CONTAINER_MODE_VAR] = step.mode
    envVars[EVENT_HUB_CONTROL_PLANE_VAR] =
        "${csmPlatformProperties.azure?.eventBus?.baseUri}/${organization.id}-${workspace.key}${CONTROL_PLANE_SUFFIX}".lowercase()
    envVars[EVENT_HUB_MEASURES_VAR] =
        "${csmPlatformProperties.azure?.eventBus?.baseUri}/${organization.id}-${workspace.key}".lowercase()
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
        envVars[step.pathVar] = (step.path?.invoke(organization.id ?: "", solution.id ?: "") ?: "")
      }
    }
    return ScenarioRunContainer(
        name = name,
        image = imageName,
        envVars = envVars,
        entrypoint = ENTRYPOINT_NAME,
    )
  }

  private fun getImageName(registry: String, repository: String, version: String? = null): String {
    val repoVersion = if (version == null) repository else "${repository}:${version}"
    return if (registry != "") "${registry}/${repoVersion}" else repoVersion
  }

  private fun getDatasetEnvVars(
      dataset: Dataset,
      connector: Connector,
      fetchPathBase: String,
      fetchId: String?,
      organizationId: String,
      workspaceId: String,
      workspaceKey: String,
      csmSimulationId: String
  ): Map<String, String> {
    val envVars = getCommonEnvVars(csmSimulationId, organizationId, workspaceKey)
    val fetchPath = if (fetchId == null) fetchPathBase else "${fetchPathBase}/${fetchId}"
    envVars[FETCH_PATH_VAR] = fetchPath
    val datasetEnvVars =
        connector
            .parameterGroups
            ?.flatMap { it.parameters }
            ?.filter { it.envVar != null }
            ?.associateBy(
                { it.envVar ?: "" },
                {
                  resolvePlatformVars(
                      dataset.connector?.parametersValues?.getOrDefault(it.id, it.default ?: "")
                          ?: "",
                      organizationId,
                      workspaceId)
                })
    if (datasetEnvVars != null) envVars.putAll(datasetEnvVars)
    return envVars
  }

  private fun resolvePlatformVars(
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
      dataset: Dataset,
      connector: Connector,
      organizationId: String,
      workspaceId: String
  ): List<String>? {
    return connector.parameterGroups?.flatMap { it.parameters }?.filter { it.envVar == null }?.map {
      resolvePlatformVars(
          dataset.connector?.parametersValues?.getOrDefault(it.id, it.default ?: "") ?: "",
          organizationId,
          workspaceId)
    }
  }

  private fun getCommonEnvVars(
      csmSimulationId: String,
      organizationId: String,
      workspaceKey: String
  ): MutableMap<String, String> {
    return mutableMapOf(
        AZURE_TENANT_ID_VAR to (csmPlatformProperties.azure?.credentials?.tenantId ?: ""),
        AZURE_CLIENT_ID_VAR to (csmPlatformProperties.azure?.credentials?.clientId ?: ""),
        AZURE_CLIENT_SECRET_VAR to (csmPlatformProperties.azure?.credentials?.clientSecret ?: ""),
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
      val runTemplate: RunTemplate,
      val csmSimulationId: String,
  )
}
