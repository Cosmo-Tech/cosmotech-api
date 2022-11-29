// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.workspace.azure

import com.cosmotech.api.config.CsmPlatformProperties

const val NOT_AVAILABLE = "N/A"
private const val SAS_NONE_VALUE = "_NONE_"

data class WorkspaceEventHubInfo(
    val eventHubNamespace: String,
    val eventHubAvailable: Boolean,
    val eventHubName: String,
    val eventHubUri: String,
    val eventHubCredentialType:
        CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureEventBus.Authentication.Strategy,
    val eventHubSasKeyName: String = SAS_NONE_VALUE,
    val eventHubSasKey: String = SAS_NONE_VALUE,
)
