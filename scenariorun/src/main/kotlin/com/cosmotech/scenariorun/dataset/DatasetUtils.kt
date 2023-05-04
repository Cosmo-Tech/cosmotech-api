// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun.dataset

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.exceptions.CsmClientException
import com.cosmotech.connector.api.ConnectorApiService
import com.cosmotech.connector.domain.Connector
import com.cosmotech.dataset.api.DatasetApiService
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.scenario.domain.Scenario
import com.cosmotech.scenario.domain.ScenarioRunTemplateParameterValue
import com.cosmotech.scenariorun.FETCH_PATH_VAR
import com.cosmotech.scenariorun.getCommonEnvVars
import com.cosmotech.scenariorun.resolvePlatformVars
import com.cosmotech.solution.domain.RunTemplate
import com.cosmotech.solution.domain.Solution

internal const val PARAMETERS_DATASET_ID = "%DATASETID%"

internal fun findDatasetsAndConnectors(
    datasetService: DatasetApiService,
    connectorService: ConnectorApiService,
    organizationId: String,
    scenario: Scenario,
    solution: Solution,
    runTemplate: RunTemplate
): DatasetsConnectors {
  val datasets: MutableMap<String, Dataset> = mutableMapOf()
  val connectors: MutableMap<String, Connector> = mutableMapOf()
  scenario.datasetList?.forEach { datasetId ->
    addDatasetAndConnector(
        datasetService, connectorService, organizationId, datasetId, datasets, connectors)
  }
  val parameterGroupIds = runTemplate.parameterGroups
  if (parameterGroupIds != null) {
    val parametersIds =
        (solution.parameterGroups?.filter { it.id in parameterGroupIds }?.map {
              it.parameters ?: listOf()
            })
            ?.flatten()
    if (parametersIds != null) {
      solution
          .parameters
          ?.filter { it.id in parametersIds }
          ?.filter { it.varType == PARAMETERS_DATASET_ID }
          ?.forEach { parameter ->
            val parameterValue = scenario.parametersValues?.find { it.parameterId == parameter.id }
            addParameterValue(
                parameterValue,
                datasetService,
                connectorService,
                organizationId,
                datasets,
                connectors,
            )
          }
    }
  }
  return DatasetsConnectors(
      datasets = datasets.values.toList(), connectors = connectors.values.toList())
}

private fun addParameterValue(
    parameterValue: ScenarioRunTemplateParameterValue?,
    datasetService: DatasetApiService,
    connectorService: ConnectorApiService,
    organizationId: String,
    datasets: MutableMap<String, Dataset>,
    connectors: MutableMap<String, Connector>,
) {
  if (parameterValue != null && parameterValue.value != "") {
    val value = parameterValue.value
    val datasetIdList = getDatasetIdListFromValue(value)
    datasetIdList.forEach {
      addDatasetAndConnector(
          datasetService,
          connectorService,
          organizationId,
          it,
          datasets,
          connectors,
      )
    }
  }
}

internal fun getDatasetIdListFromValue(paramValue: String): List<String> {
  if (paramValue.startsWith("[")) {
    if (!(paramValue.last().equals(']'))) {
      throw CsmClientException(
          "Malformed dataset id list, must start with [ and end with ]: $paramValue")
    }
    val datasetIds = paramValue.substring(1, paramValue.length - 1)
    return datasetIds.split(",")
  } else {
    return listOf(paramValue)
  }
}

internal fun addDatasetAndConnector(
    datasetService: DatasetApiService,
    connectorService: ConnectorApiService,
    organizationId: String,
    datasetId: String,
    datasets: MutableMap<String, Dataset>,
    connectors: MutableMap<String, Connector>,
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

internal fun getDatasetEnvVars(
    csmPlatformProperties: CsmPlatformProperties,
    dataset: Dataset,
    connector: Connector,
    fetchPathBase: String,
    fetchId: String?,
    organizationId: String,
    workspaceId: String,
    scenarioId: String,
    workspaceKey: String,
    csmSimulationId: String
): Map<String, String> {
  val envVars =
      getCommonEnvVars(
          csmPlatformProperties,
          csmSimulationId,
          organizationId,
          workspaceId,
          scenarioId,
          workspaceKey,
          azureManagedIdentity = connector.azureManagedIdentity,
          azureAuthenticationWithCustomerAppRegistration =
              connector.azureAuthenticationWithCustomerAppRegistration)
  val fetchPath = if (fetchId == null) fetchPathBase else "$fetchPathBase/$fetchId"
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
                    csmPlatformProperties,
                    dataset.connector?.parametersValues?.getOrDefault(it.id, it.default ?: "")
                        ?: "",
                    organizationId,
                    workspaceId)
              })
  if (datasetEnvVars != null) envVars.putAll(datasetEnvVars)
  return envVars
}

internal data class DatasetsConnectors(
    val datasets: List<Dataset>,
    val connectors: List<Connector>
)
