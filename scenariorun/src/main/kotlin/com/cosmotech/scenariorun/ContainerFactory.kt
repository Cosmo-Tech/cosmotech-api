// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.connector.domain.Connector
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.scenario.domain.Scenario
import com.cosmotech.scenariorun.domain.ScenarioRunContainer
import com.cosmotech.scenariorun.domain.ScenarioRunStartContainers
import com.cosmotech.solution.domain.RunTemplate
import com.cosmotech.solution.domain.Solution
import com.cosmotech.workspace.domain.Workspace
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

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
private const val NODE_LABEL_DEFAULT = "basic"
private const val NODE_LABEL_SUFFIX = "pool"
private const val GENERATE_NAME_PREFIX = "workflow-"
private const val GENERATE_NAME_SUFFIX = "-"

@Component
class ContainerFactory(@Autowired val csmPlatformProperties: CsmPlatformProperties) {
  private val logger = LoggerFactory.getLogger(ArgoAdapter::class.java)

  fun buildContainersStart(
      scenario: Scenario,
      datasets: List<Dataset>?,
      connectors: List<Connector>?,
      workspace: Workspace,
      solution: Solution
  ): ScenarioRunStartContainers {
    val template = getRunTemplate(solution, scenario.runTemplateId)
    val nodeLabel =
        if (template.computeSize != null) "${template.computeSize}${NODE_LABEL_SUFFIX}"
        else "${NODE_LABEL_DEFAULT}${NODE_LABEL_SUFFIX}"
    val containers = buildContainersPipeline(scenario, datasets, connectors, workspace, solution)
    val generateName = "${GENERATE_NAME_PREFIX}${scenario.id}${GENERATE_NAME_SUFFIX}"
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
      solution: Solution
  ): List<ScenarioRunContainer> {
    if (scenario.id == null) throw IllegalStateException("Scenario Id cannot be null")
    val template = getRunTemplate(solution, scenario.runTemplateId)

    var containers: MutableList<ScenarioRunContainer> = mutableListOf()

    if (testStep(template.fetchDatasets) && datasets != null && connectors != null) {
      var datasetCount = 1
      scenario.datasetList?.forEach { datasetId ->
        val dataset = datasets.find { it.id == datasetId }
        if (dataset == null)
            throw IllegalStateException("Dataset ${datasetId} not found in Datasets")
        val connector = connectors.find { connector -> connector.id == dataset.connector.id }
        if (connector == null)
            throw IllegalStateException(
                "Connector id ${dataset.connector.id} not found in connectors list")
        containers.add(
            this.buildFromDataset(dataset, connector, datasetCount, false, dataset.id ?: ""))
        datasetCount++
      }
    }
    if (testStep(template.fetchScenarioParameters)) {
      containers.add(this.buildScenarioParametersFetchContainer(scenario.id ?: ""))
      containers.addAll(
          buildScenarioParametersDatasetFetchContainers(scenario, solution, datasets, connectors))
    }
    if (testStep(template.applyParameters))
        containers.add(
            this.buildApplyParametersContainer(workspace.key, solution, scenario.runTemplateId))
    if (testStep(template.validateData))
        containers.add(
            this.buildValidateDataContainer(workspace.key, solution, scenario.runTemplateId))
    val sendParameters =
        getSendOptionValue(
            workspace.sendInputToDataWarehouse, template.sendInputParametersToDataWarehouse)
    val sendDatasets =
        getSendOptionValue(workspace.sendInputToDataWarehouse, template.sendDatasetsToDataWarehouse)
    if (sendParameters || sendDatasets)
        containers.add(this.buildSendDataWarehouseContainer(workspace, template))
    if (testStep(template.preRun))
        containers.add(this.buildPreRunContainer(workspace.key, solution, scenario.runTemplateId))
    if (testStep(template.run))
        containers.add(this.buildRunContainer(workspace.key, solution, scenario.runTemplateId))
    if (testStep(template.postRun))
        containers.add(this.buildPostRunContainer(workspace.key, solution, scenario.runTemplateId))

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
  ): ScenarioRunContainer {
    if (dataset.connector.id != connector.id)
        throw IllegalStateException("Dataset connector id and Connector id do not match")
    var nameBase = CONTAINER_FETCH_DATASET
    var fetchPathBase = DATASET_PATH
    if (parametersFetch) {
      nameBase = CONTAINER_FETCH_DATASET_PARAMETERS
      fetchPathBase = PARAMETERS_PATH
    }
    return ScenarioRunContainer(
        name = "${nameBase}-${datasetCount.toString()}",
        image = getImageName(connector.repository, connector.version),
        envVars = getDatasetEnvVars(dataset, connector, fetchPathBase, fetchId),
        runArgs = getDatasetRunArgs(dataset, connector))
  }

  fun buildScenarioParametersDatasetFetchContainers(
      scenario: Scenario,
      solution: Solution,
      datasets: List<Dataset>?,
      connectors: List<Connector>?
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
          val dataset = datasets?.find { dataset -> dataset.id == parameterValue?.value }
          if (dataset == null)
              throw IllegalStateException(
                  "Dataset ${parameterValue.value} cannot be found in Datasets")
          val connector = connectors?.find { connector -> connector.id == dataset.connector?.id }
          if (connector == null)
              throw IllegalStateException(
                  "Connector id ${dataset.connector.id} not found in connectors list")

          containers.add(
              buildFromDataset(dataset, connector, datasetParameterCount, true, parameter.id))
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
      workspace: Workspace,
      runTemplate: RunTemplate
  ): ScenarioRunContainer {
    val envVars = getCommonEnvVars()
    val sendParameters =
        getSendOptionValue(
            workspace.sendInputToDataWarehouse, runTemplate.sendInputParametersToDataWarehouse)
    val sendDatasets =
        getSendOptionValue(
            workspace.sendInputToDataWarehouse, runTemplate.sendDatasetsToDataWarehouse)
    envVars.put(SEND_DATAWAREHOUSE_PARAMETERS_VAR, (sendParameters ?: true).toString())
    envVars.put(SEND_DATAWAREHOUSE_DATASETS_VAR, (sendDatasets ?: true).toString())
    envVars.put(
        ADX_DATA_INGESTION_URI_VAR,
        csmPlatformProperties.azure?.dataWarehouseCluster?.options?.ingestionUri ?: "")
    envVars.put(ADX_DATABASE, workspace.key)
    return ScenarioRunContainer(
        name = CONTAINER_SEND_DATAWAREHOUSE,
        image = csmPlatformProperties.images.sendDataWarehouse,
        envVars = envVars)
  }

  fun buildApplyParametersContainer(
      workspaceKey: String,
      solution: Solution,
      runTemplateId: String
  ): ScenarioRunContainer {
    return this.buildSolutionContainer(
        workspaceKey,
        solution,
        runTemplateId,
        CONTAINER_APPLY_PARAMETERS,
        CONTAINER_APPLY_PARAMETERS_MODE)
  }

  fun buildValidateDataContainer(
      workspaceKey: String,
      solution: Solution,
      runTemplateId: String
  ): ScenarioRunContainer {
    return this.buildSolutionContainer(
        workspaceKey,
        solution,
        runTemplateId,
        CONTAINER_VALIDATE_DATA,
        CONTAINER_VALIDATE_DATA_MODE)
  }

  fun buildPreRunContainer(
      workspaceKey: String,
      solution: Solution,
      runTemplateId: String
  ): ScenarioRunContainer {
    return this.buildSolutionContainer(
        workspaceKey, solution, runTemplateId, CONTAINER_PRERUN, CONTAINER_PRERUN_MODE)
  }

  fun buildRunContainer(
      workspaceKey: String,
      solution: Solution,
      runTemplateId: String
  ): ScenarioRunContainer {
    return this.buildSolutionContainer(
        workspaceKey, solution, runTemplateId, CONTAINER_RUN, CONTAINER_RUN_MODE)
  }

  fun buildPostRunContainer(
      workspaceKey: String,
      solution: Solution,
      runTemplateId: String
  ): ScenarioRunContainer {
    return this.buildSolutionContainer(
        workspaceKey, solution, runTemplateId, CONTAINER_POSTRUN, CONTAINER_POSTRUN_MODE)
  }

  private fun getSendOptionValue(workspaceOption: Boolean?, templateOption: Boolean?): Boolean {
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
      workspaceKey: String,
      solution: Solution,
      runTemplateId: String,
      name: String,
      mode: String
  ): ScenarioRunContainer {

    val template = getRunTemplate(solution, runTemplateId)
    val imageName = getImageName(solution.repository, solution.version)
    val envVars = getCommonEnvVars()
    envVars.put(RUN_TEMPLATE_ID_VAR, runTemplateId)
    envVars.put(CONTAINER_MODE_VAR, mode)
    envVars.put(
        EVENT_HUB_CONTROL_PLANE_VAR,
        "${csmPlatformProperties.azure?.eventBus?.baseUri}/${workspaceKey}${CONTROL_PLANE_SUFFIX}")
    envVars.put(
        EVENT_HUB_MEASURES_VAR, "${csmPlatformProperties.azure?.eventBus?.baseUri}/${workspaceKey}")
    val csmSimulation = template.csmSimulation
    if (csmSimulation != null) {
      envVars.put(CSM_SIMULATION_VAR, csmSimulation)
    }
    return ScenarioRunContainer(
        name = name,
        image = imageName,
        envVars = envVars,
        entrypoint = ENTRYPOINT_NAME,
    )
  }

  private fun getImageName(repository: String, version: String): String {
    return "${repository}:${version}"
  }

  private fun getDatasetEnvVars(
      dataset: Dataset,
      connector: Connector,
      fetchPathBase: String,
      fetchId: String
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
                { dataset.connector.parametersValues?.getOrDefault(it.id, "") ?: "" })
    if (datasetEnvVars != null) envVars.putAll(datasetEnvVars)
    return envVars
  }

  private fun getDatasetRunArgs(dataset: Dataset, connector: Connector): List<String>? {
    return connector.parameterGroups?.flatMap { it.parameters }?.filter { it.envVar == null }?.map {
      dataset.connector.parametersValues?.getOrDefault(it.id, "") ?: ""
    }
  }

  private fun getCommonEnvVars(): MutableMap<String, String> {
    return mutableMapOf(
        AZURE_TENANT_ID_VAR to (csmPlatformProperties.azure?.credentials?.tenantId ?: ""),
        AZURE_CLIENT_ID_VAR to (csmPlatformProperties.azure?.credentials?.clientId ?: ""),
        AZURE_CLIENT_SECRET_VAR to (csmPlatformProperties.azure?.credentials?.clientSecret ?: ""),
        API_BASE_URL_VAR to csmPlatformProperties.api.baseUrl,
        DATASET_PATH_VAR to DATASET_PATH,
        PARAMETERS_PATH_VAR to PARAMETERS_PATH,
    )
  }
}
