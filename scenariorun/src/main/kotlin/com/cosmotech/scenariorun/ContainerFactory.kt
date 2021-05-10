// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun

import com.cosmotech.connector.domain.Connector
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.scenariorun.domain.ScenarioRunContainer
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.solution.domain.Solution
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

  fun buildFromDataset(dataset: Dataset, connector: Connector): ScenarioRunContainer {
    if (dataset.connector.id != connector.id)
        throw IllegalStateException("Dataset connector id and Connector id do not match")
    return ScenarioRunContainer(
        name = CONTAINER_FETCH_DATASET,
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

  fun buildSendDataWarehouseContainer(workspace: Workspace): ScenarioRunContainer {
    if (workspace.services?.dataWarehouse?.resourceUri == null) {
      throw IllegalStateException("DataWarehouse Database service Uri is not specified on Workspace")
    }
    val envVars = getCommonEnvVars()
    var sendParameters = workspace.sendInputToDataWarehouse
    var sendDatasets = workspace.sendInputToDataWarehouse
    envVars.put(sendDataWarehouseParametersVar, (sendParameters ?: true).toString())
    envVars.put(sendDataWarehouseDatasetsVar, (sendDatasets ?: true).toString())
    envVars.put(adxDataIngestionUriVar, adxDataIngestionUri)
    envVars.put(adxDatabase, workspace.services?.dataWarehouse?.resourceUri ?: "")
    return ScenarioRunContainer(
      name = CONTAINER_SEND_DATAWAREHOUSE,
      image = sendDataWarehouseImage,
      envVars = envVars
    )
  }

  fun buildApplyParametersContainer(solution: Solution, runTemplateId: String): ScenarioRunContainer {
    return this.buildSolutionContainer(solution, runTemplateId, CONTAINER_APPLY_PARAMETERS, CONTAINER_APPLY_PARAMETERS_MODE)
  }

  private fun buildSolutionContainer(solution: Solution, runTemplateId: String, name: String, mode: String): ScenarioRunContainer {
    val imageName = getImageName(solution.repository, solution.version)
    val envVars = getCommonEnvVars()
    envVars.put(runTemplateIdVar, runTemplateId)
    envVars.put(containerModeVar, mode)
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
