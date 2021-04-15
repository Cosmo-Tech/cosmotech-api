// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.workspace

import com.cosmotech.api.AbstractPhoenixService
import com.cosmotech.workspace.api.WorkspaceApiService
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceUserDetails
import org.springframework.stereotype.Service

@Service
class WorkspaceServiceImpl : AbstractPhoenixService(), WorkspaceApiService {
  override fun findAllWorkspaces(organizationId: String): List<Workspace> {
    TODO("Not yet implemented")
  }

  override fun findWorkspaceById(organizationId: String, workspaceId: String): Workspace {
    TODO("Not yet implemented")
  }

  override fun getCurrentWorkspaceUser(
      organizationId: kotlin.String,
      workspaceId: kotlin.String
  ): WorkspaceUserDetails {
    TODO("Not yet implemented")
  }

  override fun createWorkspace(organizationId: String, workspace: Workspace): Workspace {
    TODO("Not yet implemented")
  }

  override fun updateWorkspace(
      organizationId: String,
      workspaceId: String,
      workspace: Workspace
  ): Workspace {
    TODO("Not yet implemented")
  }

  override fun deleteWorkspace(organizationId: String, workspaceId: String): Workspace {
    TODO("Not yet implemented")
  }
}
