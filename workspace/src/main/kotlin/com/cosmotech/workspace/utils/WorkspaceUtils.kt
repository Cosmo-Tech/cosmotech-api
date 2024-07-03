// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.workspace.utils

import com.cosmotech.api.config.CsmPlatformProperties
import java.nio.file.Path

fun getWorkspaceSecretName(organizationId: String, workspaceId: String) =
    getWorkspaceUniqueName(organizationId, workspaceId)

fun getWorkspaceUniqueName(organizationId: String, workspaceKey: String) =
    "$organizationId-$workspaceKey".lowercase()

fun getWorkspaceFilesDir(
    csmPlatformProperties: CsmPlatformProperties,
    organizationId: String,
    workspaceId: String
) =
    Path.of(csmPlatformProperties.blobPersistence.path, organizationId, workspaceId)
        .toAbsolutePath()

fun getWorkspaceFilePath(
    csmPlatformProperties: CsmPlatformProperties,
    organizationId: String,
    workspaceId: String,
    fileName: String
) =
    Path.of(csmPlatformProperties.blobPersistence.path, organizationId, workspaceId, fileName)
        .toAbsolutePath()
