// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun

import com.cosmotech.connector.domain.Connector
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.scenariorun.domain.ScenarioRunContainer
import com.cosmotech.scenariorun.domain.ScenarioRunStartContainers
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.solution.domain.Solution
import com.cosmotech.solution.domain.RunTemplate
import com.cosmotech.scenario.domain.Scenario
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class ContainerFactory(
    @Value("\${csm.platform.security.azure.tenant-id:}") val azureTenantId: String,
    @Value("\${csm.platform.security.azure.client-id:}") val azureClientId: String,
    @Value("\${csm.platform.security.azure.client-secret:}") val azureClientSecret: String,
    @Value("\${csm.platform.base-url:}") val apiBaseUrl: String,
    @Value("\${csm.platform.security.token:}") val apiToken: String,
    @Value("\${csm.platform.images.scenario-fetch-parameters:}") val scenarioFetchParametersImage: String,
    @Value("\${csm.platform.images.send-datawarehouse:}") val sendDataWarehouseImage: String,
    @Value("\${csm.platform.services.adx-dataingestion-uri:}") val adxDataIngestionUri: String,
    @Value("\${csm.platform.services.eventhub-cluster-uri:}") val eventHubClusterUri: String,
) {
  private val logger = LoggerFactory.getLogger(ArgoAdapter::class.java)
  private val CONTAINER_FETCH_DATASET = "fetchDatasetContainers"
  private val CONTAINER_FETCH_PARAMETERS = "fetchScenarioParametersContainer"
  private val CONTAINER_SEND_DATAWAREHOUSE = "sendDataWarehouseContainer"
  private val CONTAINER_APPLY_PARAMETERS = "applyParametersContainer"
  private val CONTAINER_APPLY_PARAMETERS_MODE = "handle-parameters"
  private val CONTAINER_VALIDATE_DATA = "validateDataContainer"
  private val CONTAINER_VALIDATE_DATA_MODE = "validate"
  private val CONTAINER_PRERUN = "preRunContainer"
  private val CONTAINER_PRERUN_MODE = "prerun"
  private val CONTAINER_RUN = "runContainer"
  private val CONTAINER_RUN_MODE = "engine"
  private val CONTAINER_POSTRUN = "postRunContainer"
  private val CONTAINER_POSTRUN_MODE = "postrun"
  private val azureTenantIdVar = "AZURE_TENANT_ID"
  private val azureClientIdVar = "AZURE_CLIENT_ID"
  private val azureClientSecretVar = "AZURE_CLIENT_SECRET"
  private val apiBaseUrlVar = "CSM_API_URL"
  private val apiTokenVar = "CSM_API_TOKEN"
  private val datasetPathVar = "CSM_DATASET_ABSOLUTE_PATH"
  private val datasetPath = "/mnt/scenariorun-data"
  private val parametersPathVar = "CSM_PARAMETERS_ABSOLUTE_PATH"
  private val parametersPath = "/mnt/scenariorun-parameters"
  private val parametersFetchContainerScenarioVar = "CSM_SCENARIO_ID"
  private val sendDataWarehouseParametersVar= "CSM_SEND_DATAWAREHOUSE_PARAMETERS"
  private val sendDataWarehouseDatasetsVar= "CSM_SEND_DATAWAREHOUSE_DATASETS"
  private val adxDataIngestionUriVar= "ADX_DATA_INGESTION_URI"
  private val adxDatabase= "ADX_DATABASE"
  private val runTemplateIdVar = "CSM_RUN_TEMPLATE_ID"
  private val containerModeVar = "CSM_CONTAINER_MODE"
  private val entrypointName = "entrypoint.py"
  private val eventHubControlPlaneVar = "CSM_CONTROL_PLANE_TOPIC"
  private val controlPlaneSuffix = "-scenariorun"
  private val eventHubMeasuresVar = "CSM_PROBES_MEASURES_TOPIC"
  private val csmSimulationVar = "CSM_SIMULATION"
  private val nodeLabelDefault = "basic"
  private val nodeLabelSuffix = "pool"
  private val generateNamePrefix = "workflow-"
  private val generateNameSuffix = "-"

  fun buildContainersStart(scenario: Scenario, datasets: List<Dataset>?, connectors: List<Connector>?, workspace: Workspace, solution: Solution): ScenarioRunStartContainers {
    val template = getRunTemplate(solution, scenario.runTemplateId)
    val nodeLabel = if (template.computeSize != null) "${template.computeSize}${nodeLabelSuffix}" else "${nodeLabelDefault}${nodeLabelSuffix}"
    val containers = buildContainersPipeline(scenario, datasets, connectors, workspace, solution)
    val generateName = "${generateNamePrefix}${scenario.id}${generateNameSuffix}"
    return ScenarioRunStartContainers(
      generateName = generateName,
      nodeLabel = nodeLabel,
      containers = containers,
    )
  }

  fun buildContainersPipeline(scenario: Scenario, datasets: List<Dataset>?, connectors: List<Connector>?, workspace: Workspace, solution: Solution): List<ScenarioRunContainer> {
    if (scenario.id == null) throw IllegalStateException("Scenario Id cannot be null")
    val template = getRunTemplate(solution, scenario.runTemplateId)

    var containers: MutableList<ScenarioRunContainer> = mutableListOf()

    if (testStep(template.fetchDatasets) && datasets != null && connectors != null) {
    var datasetCount = 1
    datasets.forEach {
      val connector = connectors.find { connector -> connector.id == it.connector.id }
      if (connector == null) throw IllegalStateException("Connector id ${it.connector.id} not found in connectors list")
      containers.add(this.buildFromDataset(it, connector, datasetCount))
      datasetCount++
    }
  }
    if (testStep(template.fetchScenarioParameters)) containers.add(this.buildScenarioParametersFetchContainer(scenario.id ?: ""))
    if (testStep(template.applyParameters)) containers.add(this.buildApplyParametersContainer(workspace.key, solution, scenario.runTemplateId))
    if (testStep(template.validateData)) containers.add(this.buildValidateDataContainer(workspace.key, solution, scenario.runTemplateId))
    val sendParameters = getSendOptionValue(workspace.sendInputToDataWarehouse, template.sendInputParametersToDataWarehouse)
    val sendDatasets = getSendOptionValue(workspace.sendInputToDataWarehouse, template.sendDatasetsToDataWarehouse)
    if (sendParameters || sendDatasets) containers.add(this.buildSendDataWarehouseContainer(workspace, template))
    if (testStep(template.preRun)) containers.add(this.buildPreRunContainer(workspace.key, solution, scenario.runTemplateId))
    if (testStep(template.run)) containers.add(this.buildRunContainer(workspace.key, solution, scenario.runTemplateId))
    if (testStep(template.postRun)) containers.add(this.buildPostRunContainer(workspace.key, solution, scenario.runTemplateId))

    return containers.toList()
  }

  private fun testStep(step: Boolean?): Boolean {
    return step ?: true
  }

  fun buildFromDataset(dataset: Dataset, connector: Connector, datasetCount: Int): ScenarioRunContainer {
    if (dataset.connector.id != connector.id)
        throw IllegalStateException("Dataset connector id and Connector id do not match")
    return ScenarioRunContainer(
        name = "${CONTAINER_FETCH_DATASET}-${datasetCount.toString()}",
        image = getImageName(connector.repository, connector.version),
        envVars = getDatasetEnvVars(dataset, connector),
        runArgs = getDatasetRunArgs(dataset, connector))
  }

  fun buildScenarioParametersFetchContainer(scenarioId: String): ScenarioRunContainer {
    val envVars = getCommonEnvVars()
    envVars.put(parametersFetchContainerScenarioVar, scenarioId)
    return ScenarioRunContainer(
      name = CONTAINER_FETCH_PARAMETERS,
      image = scenarioFetchParametersImage,
      envVars = envVars,
    )
  }

  fun buildSendDataWarehouseContainer(workspace: Workspace, runTemplate: RunTemplate): ScenarioRunContainer {
    val envVars = getCommonEnvVars()
    val sendParameters = getSendOptionValue(workspace.sendInputToDataWarehouse, runTemplate.sendInputParametersToDataWarehouse)
    val sendDatasets = getSendOptionValue(workspace.sendInputToDataWarehouse, runTemplate.sendDatasetsToDataWarehouse)
    envVars.put(sendDataWarehouseParametersVar, (sendParameters ?: true).toString())
    envVars.put(sendDataWarehouseDatasetsVar, (sendDatasets ?: true).toString())
    envVars.put(adxDataIngestionUriVar, adxDataIngestionUri)
    envVars.put(adxDatabase, workspace.key)
    return ScenarioRunContainer(
      name = CONTAINER_SEND_DATAWAREHOUSE,
      image = sendDataWarehouseImage,
      envVars = envVars
    )
  }

  fun buildApplyParametersContainer(workspaceKey: String, solution: Solution, runTemplateId: String): ScenarioRunContainer {
    return this.buildSolutionContainer(workspaceKey, solution, runTemplateId, CONTAINER_APPLY_PARAMETERS, CONTAINER_APPLY_PARAMETERS_MODE)
  }

  fun buildValidateDataContainer(workspaceKey: String, solution: Solution, runTemplateId: String): ScenarioRunContainer {
    return this.buildSolutionContainer(workspaceKey, solution, runTemplateId, CONTAINER_VALIDATE_DATA, CONTAINER_VALIDATE_DATA_MODE)
  }

  fun buildPreRunContainer(workspaceKey: String, solution: Solution, runTemplateId: String): ScenarioRunContainer {
    return this.buildSolutionContainer(workspaceKey, solution, runTemplateId, CONTAINER_PRERUN, CONTAINER_PRERUN_MODE)
  }

  fun buildRunContainer(workspaceKey: String, solution: Solution, runTemplateId: String): ScenarioRunContainer {
    return this.buildSolutionContainer(workspaceKey, solution, runTemplateId, CONTAINER_RUN, CONTAINER_RUN_MODE)
  }

  fun buildPostRunContainer(workspaceKey: String, solution: Solution, runTemplateId: String): ScenarioRunContainer {
    return this.buildSolutionContainer(workspaceKey, solution, runTemplateId, CONTAINER_POSTRUN, CONTAINER_POSTRUN_MODE)
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
      throw IllegalStateException("runTemplateId ${runTemplateId} not found in Solution ${solution.id}")
    }

    return template
  }

  private fun buildSolutionContainer(workspaceKey: String, solution: Solution, runTemplateId: String, name: String, mode: String): ScenarioRunContainer {

    val template = getRunTemplate(solution, runTemplateId)
    val imageName = getImageName(solution.repository, solution.version)
    val envVars = getCommonEnvVars()
    envVars.put(runTemplateIdVar, runTemplateId)
    envVars.put(containerModeVar, mode)
    envVars.put(eventHubControlPlaneVar, "${eventHubClusterUri}/${workspaceKey}${controlPlaneSuffix}")
    envVars.put(eventHubMeasuresVar, "${eventHubClusterUri}/${workspaceKey}")
    val csmSimulation = template.csmSimulation
    if (csmSimulation != null) {
      envVars.put(csmSimulationVar, csmSimulation)
    }
    return ScenarioRunContainer(
      name = name,
      image = imageName,
      envVars = envVars,
      entrypoint = entrypointName,
    )
  }

  private fun getImageName(repository: String, version: String): String {
    return "${repository}:${version}"
  }

  private fun getDatasetEnvVars(dataset: Dataset, connector: Connector): Map<String, String> {
    val envVars = getCommonEnvVars()
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
        azureTenantIdVar to azureTenantId,
        azureClientIdVar to azureClientId,
        azureClientSecretVar to azureClientSecret,
        apiBaseUrlVar to apiBaseUrl,
        apiTokenVar to apiToken,
        datasetPathVar to datasetPath,
        parametersPathVar to parametersPath,
    )
  }
}
