// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.workspace.azure

fun getWorkspaceSecretName(organizationId: String, workspaceKey: String) =
    getWorkspaceUniqueName(organizationId, workspaceKey)

fun getWorkspaceUniqueName(organizationId: String, workspaceKey: String) =
    "${organizationId}-${workspaceKey}".lowercase()
