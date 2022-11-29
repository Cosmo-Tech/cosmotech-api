// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.workspace.azure.strategy

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.workspace.azure.EventHubRole
import com.cosmotech.workspace.azure.NOT_AVAILABLE
import com.cosmotech.workspace.azure.WorkspaceEventHubInfo
import com.cosmotech.workspace.domain.Workspace

private const val AMQP_PROTOCOL = "amqps"

abstract class WorkspaceEventHubStrategyBase : IWorkspaceEventHubStrategy {
  override fun getWorkspaceEventHubInfo(
      organizationId: String,
      workspace: Workspace,
      eventHubRole: EventHubRole,
  ): WorkspaceEventHubInfo {
    val namespace = this.getWorkspaceEventHubNamespace(organizationId, workspace.key)
    val name = this.getWorkspaceEventHubName(organizationId, workspace, eventHubRole)
    val available = name != NOT_AVAILABLE
    val uri = "$AMQP_PROTOCOL://$namespace/$name"
    val authenticationStrategy = this.getWorkspaceEventHubAuthenticationStrategy(workspace)
    return if (authenticationStrategy ==
        CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication.Strategy
            .SHARED_ACCESS_POLICY && available) {
      val sasKeyName = this.getWorkspaceEventHubSasKeyName(workspace)
      val sasKey = this.getWorkspaceEventHubSasKey(organizationId, workspace)
      WorkspaceEventHubInfo(
          namespace, available, name, uri, authenticationStrategy, sasKeyName, sasKey)
    } else {
      WorkspaceEventHubInfo(namespace, available, name, uri, authenticationStrategy)
    }
  }
}
