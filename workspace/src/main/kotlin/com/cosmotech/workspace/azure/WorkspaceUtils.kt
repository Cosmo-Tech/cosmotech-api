// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.workspace.azure

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.utils.SecretManager
import com.cosmotech.workspace.api.WorkspaceApiService
import com.cosmotech.workspace.domain.Workspace

const val WORKSPACE_EVENTHUB_ACCESSKEY_SECRET = "eventHubAccessKey"

fun getWorkspaceSecretName(organizationId: String, workspaceKey: String) =
        getWorkspaceUniqueName(organizationId, workspaceKey)

fun getWorkspaceUniqueName(organizationId: String, workspaceKey: String) =
    "${organizationId}-${workspaceKey}".lowercase()

// TODO: To dedicated class with service injection
fun getWorkspaceEventHubInfo(
        organizationId: String,
        workspace: Workspace,
        eventHubRole: EventHubRole,
        csmPlatformProperties: CsmPlatformProperties,
        secretManager: SecretManager): WorkspaceEventHubInfo {
    val namespace = getWorkspaceEventHubNamespace(organizationId, workspace, csmPlatformProperties)
    val name = getWorkspaceEventHubName(organizationId, workspace, eventHubRole)
    val sasKeyName = getWorkspaceEventHubSasKeyName(workspace, csmPlatformProperties)
    val sasKey = getWorkspaceEventHubSasKey(organizationId, workspace, csmPlatformProperties, secretManager)

    return WorkspaceEventHubInfo(namespace, name, sasKeyName, sasKey)
}

fun getWorkspaceEventHubSasKey(organizationId: String, workspace: Workspace, csmPlatformProperties: CsmPlatformProperties, secretManager: SecretManager): String {
    // TODO: Use dedicated context + workspace key or fallback global conf
    val secretName = getWorkspaceSecretName(organizationId, workspace.key)
    val secretData = secretManager.readSecret(csmPlatformProperties.namespace, secretName)
    return secretData[WORKSPACE_EVENTHUB_ACCESSKEY_SECRET] ?: throw IllegalStateException("No Event Hub access key found for workspace ${workspace.id}")
}

fun getWorkspaceEventHubSasKeyName(workspace: Workspace, csmPlatformProperties: CsmPlatformProperties): String {

}

fun getWorkspaceEventHubName(organizationId: String, workspace: Workspace, eventHubRole: EventHubRole): String {
   return if (workspace.useDedicatedEventHubNamespace == true) {
        getDedicatedEventHubName(eventHubRole)
    } else {
        getCommonEventHubName(organizationId, workspace.key, eventHubRole)
    }
}

fun getCommonEventHubName(organizationId: String, workspaceKey: String, eventHubRole: EventHubRole): String {
   val baseName = getWorkspaceUniqueName(organizationId, workspaceKey)
    return when(eventHubRole) {
        EventHubRole.PROBES_MEASURES -> baseName
        EventHubRole.CONTROL_PLANE -> "$baseName-scenariorun"
        EventHubRole.SCENARIO_METADATA -> "NA"
        EventHubRole.SCENARIO_RUN_METADATA -> "NA"
    }
}

fun getDedicatedEventHubName(eventHubRole: EventHubRole): String {
    return when(eventHubRole) {
        EventHubRole.PROBES_MEASURES -> "probesmeasures"
        EventHubRole.CONTROL_PLANE -> "scenariorun"
        EventHubRole.SCENARIO_METADATA -> "scenariometadata"
        EventHubRole.SCENARIO_RUN_METADATA -> "scenariorunmetadata"
    }
}

fun getWorkspaceEventHubNamespace(
        organizationId: String,
        workspace: Workspace,
        csmPlatformProperties: CsmPlatformProperties): String {
    return if (workspace.useDedicatedEventHubNamespace == true) {
        getWorkspaceDedicatedEventHubNamespace(organizationId, workspace.key)
    } else {
        csmPlatformProperties.azure?.eventBus?.baseUri ?: throw IllegalStateException(
            "Missing Azure Event Hub namespace in configuration")
    }
}

fun getWorkspaceDedicatedEventHubNamespace(organizationId: String, key: String): String {
    val uniqueName = getWorkspaceUniqueName(organizationId, key)
    return "${uniqueName}.servicebus.windows.net".lowercase()
}
