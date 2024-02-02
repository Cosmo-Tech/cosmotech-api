package com.cosmotech.workspace

import com.cosmotech.workspace.api.WorkspaceApiService
import com.cosmotech.workspace.domain.Workspace

interface EnhancedWorkspaceApiService: WorkspaceApiService {

    fun verifyPermissionsAndReturnWorkspace(
        workspaceId: String,
        requiredPermissions: List<String>
    ): Workspace

}