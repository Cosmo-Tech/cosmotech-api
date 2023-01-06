// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.workspace.azure.strategy

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.utils.SecretManager
import com.cosmotech.workspace.azure.EventHubRole
import com.cosmotech.workspace.azure.NOT_AVAILABLE
import com.cosmotech.workspace.azure.WORKSPACE_EVENTHUB_ACCESSKEY_SECRET
import com.cosmotech.workspace.azure.getWorkspaceSecretName
import com.cosmotech.workspace.azure.getWorkspaceUniqueName
import com.cosmotech.workspace.domain.Workspace
import org.springframework.stereotype.Component

private const val SERVICE_BUS_FQDN_SUFFIX = ".servicebus.windows.net"
private const val DEFAULT_EVENTHUB_SAS_NAME = "RootManageSharedAccessKey"
private const val EVENTHUB_PROBES_MEASURES = "probesmeasures"
private const val EVENTHUB_CONTROL_PLANE = "scenariorun"
private const val EVENTHUB_SCENARIO_METADATA = "scenariometadata"
private const val EVENTHUB_SCENARIO_RUN_METADATA = "scenariorunmetadata"

@Component("Dedicated")
class WorkspaceEventHubStrategyDedicated(
    private val csmPlatformProperties: CsmPlatformProperties,
    private val secretManager: SecretManager,
) : WorkspaceEventHubStrategyBase() {

  override fun getWorkspaceEventHubSasKey(
      organizationId: String,
      workspace: Workspace,
  ): String {
    val secretName = getWorkspaceSecretName(organizationId, workspace.key)
    val secretData = secretManager.readSecret(csmPlatformProperties.namespace, secretName)
    return secretData[WORKSPACE_EVENTHUB_ACCESSKEY_SECRET]
        ?: throw IllegalStateException(
            "No Event Hub access key found for workspace ${workspace.id}. Use the /secret endpoint to create a new one")
  }

  override fun getWorkspaceEventHubSasKeyName(
      workspace: Workspace,
  ): String {
    val workspaceSasKeyName = workspace.dedicatedEventHubSasKeyName
    return if (workspaceSasKeyName.isNullOrEmpty()) {
      DEFAULT_EVENTHUB_SAS_NAME
    } else {
      workspaceSasKeyName
    }
  }

  override fun getWorkspaceEventHubName(
      organizationId: String,
      workspace: Workspace,
      eventHubRole: EventHubRole
  ): String {
    return when (eventHubRole) {
      EventHubRole.PROBES_MEASURES -> EVENTHUB_PROBES_MEASURES
      EventHubRole.CONTROL_PLANE ->
          workspace.sendScenarioRunToEventHub.takeIf { it != false }?.let { EVENTHUB_CONTROL_PLANE }
              ?: NOT_AVAILABLE
      EventHubRole.SCENARIO_METADATA ->
          workspace.sendScenarioMetadataToEventHub.takeIf { it == true }?.let {
            EVENTHUB_SCENARIO_METADATA
          }
              ?: NOT_AVAILABLE
      EventHubRole.SCENARIO_RUN_METADATA ->
          workspace.sendScenarioMetadataToEventHub.takeIf { it == true }?.let {
            EVENTHUB_SCENARIO_RUN_METADATA
          }
              ?: NOT_AVAILABLE
    }
  }

  override fun getWorkspaceEventHubNamespace(organizationId: String, workspaceKey: String): String {
    return "${getWorkspaceUniqueName(organizationId, workspaceKey)}$SERVICE_BUS_FQDN_SUFFIX"
  }

  override fun getWorkspaceEventHubAuthenticationStrategy(
      workspace: Workspace,
  ): CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication.Strategy {
    val workspaceAuthenticationStrategy = workspace.dedicatedEventHubAuthenticationStrategy
    return if (workspaceAuthenticationStrategy.isNullOrEmpty()) {
      csmPlatformProperties.azure?.eventBus?.authentication?.strategy
          ?: throw IllegalStateException(
              "No Event Hub authentication strategy found for workspace ${workspace.id} or in global configuration")
    } else {
      CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication.Strategy
          .valueOf(workspaceAuthenticationStrategy)
    }
  }
}
