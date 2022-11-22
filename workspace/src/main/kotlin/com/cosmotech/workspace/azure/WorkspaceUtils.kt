// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.workspace.azure

const val WORKSPACE_EVENTHUB_ACCESSKEY_SECRET = "eventHubAccessKey"

fun getWorkspaceSecretName(organizationId: String, workspaceKey: String) =
    "${organizationId}-${workspaceKey}".lowercase()
