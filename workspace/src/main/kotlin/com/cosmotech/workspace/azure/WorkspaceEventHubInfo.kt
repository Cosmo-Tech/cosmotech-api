package com.cosmotech.workspace.azure

data class WorkspaceEventHubInfo(
    val eventHubNamespace: String,
    val eventHubName: String,
    val eventHubSasKeyName: String,
    val eventHubSasKey: String,
)
