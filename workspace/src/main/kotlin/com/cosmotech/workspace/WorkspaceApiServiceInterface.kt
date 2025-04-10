// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.workspace

import com.cosmotech.api.rbac.PERMISSION_READ
import com.cosmotech.workspace.api.WorkspaceApiService
import com.cosmotech.workspace.domain.Workspace

interface WorkspaceApiServiceInterface : WorkspaceApiService {

  fun getVerifiedWorkspace(
      organizationId: String,
      workspaceId: String,
      requiredPermission: String = PERMISSION_READ
  ): Workspace

  fun deleteAllS3WorkspaceObjects(organizationId: String, workspace: Workspace)
}
