// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.workspace.azure.strategy

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.workspace.azure.EventHubRole
import com.cosmotech.workspace.azure.WorkspaceEventHubInfo
import com.cosmotech.workspace.domain.Workspace

interface IWorkspaceEventHubStrategy {
  fun getWorkspaceEventHubInfo(
      organizationId: String,
      workspace: Workspace,
      eventHubRole: EventHubRole,
  ): WorkspaceEventHubInfo

  fun getWorkspaceEventHubSasKey(
      organizationId: String,
      workspace: Workspace,
  ): String

  fun getWorkspaceEventHubSasKeyName(
      workspace: Workspace,
  ): String

  fun getWorkspaceEventHubName(
      organizationId: String,
      workspaceKey: String,
      eventHubRole: EventHubRole
  ): String

  fun getWorkspaceEventHubNamespace(
      organizationId: String,
      workspaceKey: String,
  ): String

  fun getWorkspaceEventHubAuthenticationStrategy(
      workspace: Workspace,
  ): CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication.Strategy
}
