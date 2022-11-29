// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.workspace.azure

import com.cosmotech.workspace.azure.strategy.IWorkspaceEventHubStrategy
import com.cosmotech.workspace.domain.Workspace
import org.springframework.stereotype.Component

const val WORKSPACE_EVENTHUB_ACCESSKEY_SECRET = "eventHubAccessKey"
private const val STRATEGY_DEDICATED = "Dedicated"
private const val STRATEGY_SHARED = "Shared"

@Component
class WorkspaceEventHubService(private val strategies: Map<String, IWorkspaceEventHubStrategy>) :
    IWorkspaceEventHubService {

  override fun getWorkspaceEventHubInfo(
      organizationId: String,
      workspace: Workspace,
      eventHubRole: EventHubRole,
  ): WorkspaceEventHubInfo {
    val strategy =
        if (workspace.useDedicatedEventHubNamespace == true) {
          this.strategies[STRATEGY_DEDICATED]
        } else {
          this.strategies[STRATEGY_SHARED]
        }

    return strategy?.getWorkspaceEventHubInfo(organizationId, workspace, eventHubRole)
        ?: throw IllegalStateException(
            "No Event Hub strategy found for workspace $organizationId - ${workspace.id}")
  }
}
