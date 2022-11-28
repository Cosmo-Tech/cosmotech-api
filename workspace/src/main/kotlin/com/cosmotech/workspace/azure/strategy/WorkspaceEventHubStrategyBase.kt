// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.workspace.azure.strategy

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.workspace.azure.EventHubRole
import com.cosmotech.workspace.azure.WorkspaceEventHubInfo
import com.cosmotech.workspace.domain.Workspace

abstract class WorkspaceEventHubStrategyBase : IWorkspaceEventHubStrategy {
  override fun getWorkspaceEventHubInfo(
      organizationId: String,
      workspace: Workspace,
      eventHubRole: EventHubRole,
  ): WorkspaceEventHubInfo {
    val namespace = this.getWorkspaceEventHubNamespace(organizationId, workspace.key)
    val name = this.getWorkspaceEventHubName(organizationId, workspace.key, eventHubRole)
    val authenticationStrategy = this.getWorkspaceEventHubAuthenticationStrategy(workspace)
    return if (authenticationStrategy ==
        CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication.Strategy
            .SHARED_ACCESS_POLICY) {
      val sasKeyName = this.getWorkspaceEventHubSasKeyName(workspace)
      val sasKey = this.getWorkspaceEventHubSasKey(organizationId, workspace)
      WorkspaceEventHubInfo(namespace, name, authenticationStrategy, sasKeyName, sasKey)
    } else {
      WorkspaceEventHubInfo(namespace, name, authenticationStrategy)
    }
  }
}
