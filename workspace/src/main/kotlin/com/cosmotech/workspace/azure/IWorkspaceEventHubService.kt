// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.workspace.azure

import com.cosmotech.workspace.domain.Workspace

interface IWorkspaceEventHubService {
  fun getWorkspaceEventHubInfo(
      organizationId: String,
      workspace: Workspace,
      eventHubRole: EventHubRole,
  ): WorkspaceEventHubInfo
}
