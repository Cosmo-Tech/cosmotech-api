// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.workspace.azure

import com.cosmotech.workspace.azure.strategy.IWorkspaceEventHubStrategy
import com.cosmotech.workspace.domain.Workspace
import org.springframework.beans.factory.annotation.Qualifier

const val WORKSPACE_EVENTHUB_ACCESSKEY_SECRET = "eventHubAccessKey"

class WorkspaceEventHubService(
    @Qualifier("WorkspaceEventHubStrategyShared")
    private val workspaceEventHubStrategyShared: IWorkspaceEventHubStrategy,
    @Qualifier("WorkspaceEventHubStrategyDedicated")
    private val workspaceEventHubStrategyDedicated: IWorkspaceEventHubStrategy
) : IWorkspaceEventHubService {

  override fun getWorkspaceEventHubInfo(
      organizationId: String,
      workspace: Workspace,
      eventHubRole: EventHubRole,
  ): WorkspaceEventHubInfo {
    return if (workspace.useDedicatedEventHubNamespace == true) {
      this.workspaceEventHubStrategyDedicated.getWorkspaceEventHubInfo(
          organizationId, workspace, eventHubRole)
    } else {
      this.workspaceEventHubStrategyShared.getWorkspaceEventHubInfo(
          organizationId, workspace, eventHubRole)
    }
  }
}
