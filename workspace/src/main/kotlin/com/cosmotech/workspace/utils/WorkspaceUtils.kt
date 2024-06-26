// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.workspace.utils

fun getWorkspaceSecretName(organizationId: String, workspaceId: String) =
    getWorkspaceUniqueName(organizationId, workspaceId)

fun getWorkspaceUniqueName(organizationId: String, workspaceKey: String) =
    "$organizationId-$workspaceKey".lowercase()
