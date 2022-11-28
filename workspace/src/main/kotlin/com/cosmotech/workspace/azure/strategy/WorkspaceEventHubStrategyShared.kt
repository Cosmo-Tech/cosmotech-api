// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.workspace.azure.strategy

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.workspace.azure.EventHubRole
import com.cosmotech.workspace.azure.NOT_AVAILABLE
import com.cosmotech.workspace.azure.WorkspaceEventHubInfo
import com.cosmotech.workspace.azure.getWorkspaceUniqueName
import com.cosmotech.workspace.domain.Workspace
import org.springframework.stereotype.Component

private const val CONTROL_PLANE_SUFFIX = "-scenariorun"

@Component("WorkspaceEventHubStrategyShared")
class WorkspaceEventHubStrategyShared(
    private val csmPlatformProperties: CsmPlatformProperties,
) : WorkspaceEventHubStrategyBase() {

  override fun getWorkspaceEventHubSasKey(
      organizationId: String,
      workspace: Workspace,
  ): String {
    return csmPlatformProperties.azure?.eventBus?.authentication?.sharedAccessPolicy?.namespace?.key
        ?: throw IllegalStateException(
            "No global Event Hub access key found for workspace ${workspace.id}")
  }

  override fun getWorkspaceEventHubSasKeyName(
      workspace: Workspace,
  ): String {
    return csmPlatformProperties.azure
        ?.eventBus
        ?.authentication
        ?.sharedAccessPolicy
        ?.namespace
        ?.name
        ?: throw IllegalStateException(
            "No global Event Hub SAS key name found for workspace ${workspace.id}")
  }

  override fun getWorkspaceEventHubName(
      organizationId: String,
      workspaceKey: String,
      eventHubRole: EventHubRole
  ): String {
    val baseName = getWorkspaceUniqueName(organizationId, workspaceKey)
    return when (eventHubRole) {
      EventHubRole.PROBES_MEASURES -> baseName
      EventHubRole.CONTROL_PLANE -> "$baseName$CONTROL_PLANE_SUFFIX"
      EventHubRole.SCENARIO_METADATA -> NOT_AVAILABLE
      EventHubRole.SCENARIO_RUN_METADATA -> NOT_AVAILABLE
    }
  }

  override fun getWorkspaceEventHubNamespace(organizationId: String, workspaceKey: String): String {
    return this.removeAMQPProtocol(
        csmPlatformProperties.azure?.eventBus?.baseUri?.lowercase()
            ?: throw IllegalStateException("No Event Hub base URI found"))
  }

  override fun getWorkspaceEventHubAuthenticationStrategy(
      workspace: Workspace,
  ): CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication.Strategy {
    return csmPlatformProperties.azure?.eventBus?.authentication?.strategy
        ?: throw IllegalStateException(
            "No global Event Hub authentication strategy found for workspace ${workspace.id}")
  }

  private fun removeAMQPProtocol(uri: String): String {
    return uri.lowercase().replaceFirst("amqps://", "").replaceFirst("amqp://", "")
  }
}
