// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun

import com.cosmotech.connector.domain.Connector
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.scenariorun.domain.ScenarioRunContainer
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
) {
  private val logger = LoggerFactory.getLogger(ArgoAdapter::class.java)
  private val CONTAINER_FETCH_DATASET = "fetchDatasetContainers"
  private val azureTenantIdVar = "AZURE_TENANT_ID"
  private val azureClientIdVar = "AZURE_CLIENT_ID"
  private val azureClientSecretVar = "AZURE_CLIENT_SECRET"
  private val apiBaseUrlVar = "CSM_API_URL"
  private val apiTokenVar = "CSM_API_TOKEN"

  fun buildFromDataset(dataset: Dataset, connector: Connector): ScenarioRunContainer {
    if (dataset.connector.id != connector.id)
        throw IllegalStateException("Dataset connector id and Connector id do not match")
    return ScenarioRunContainer(
        name = CONTAINER_FETCH_DATASET,
        image = getConnectorImage(connector),
        envVars = getDatasetEnvVars(dataset, connector),
        runArgs = getDatasetRunArgs(dataset, connector))
  }

  private fun getConnectorImage(connector: Connector): String {
    return "${connector.repository}:${connector.version}"
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
    )
  }
}
