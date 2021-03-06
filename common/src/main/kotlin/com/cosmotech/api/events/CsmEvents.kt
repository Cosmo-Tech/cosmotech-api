// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.events

import org.springframework.context.ApplicationEvent

sealed class CsmEvent(publisher: Any) : ApplicationEvent(publisher)

class OrganizationRegistered(publisher: Any, val organizationId: String) : CsmEvent(publisher)

class OrganizationUnregistered(publisher: Any, val organizationId: String) : CsmEvent(publisher)

class UserAddedToOrganization(
    publisher: Any,
    val organizationId: String,
    val organizationName: String,
    val userId: String,
    val roles: List<String>? = null
) : CsmEvent(publisher)

class UserRemovedFromOrganization(publisher: Any, val organizationId: String, val userId: String) :
    CsmEvent(publisher)

class UserRegistered(publisher: Any, val userId: String) : CsmEvent(publisher)

class UserUnregistered(publisher: Any, val userId: String) : CsmEvent(publisher)

class UserUnregisteredForOrganization(
    publisher: Any,
    val organizationId: String,
    val userId: String
) : CsmEvent(publisher)

class UserAddedToScenario(
    publisher: Any,
    val organizationId: String,
    val userId: String,
    val roles: List<String>? = null
) : CsmEvent(publisher)

class UserRemovedFromScenario(
    publisher: Any,
    val organizationId: String,
    val workspaceId: String,
    val scenarioId: String,
    val userId: String
) : CsmEvent(publisher)

class UserAddedToWorkspace(
    publisher: Any,
    val organizationId: String,
    val userId: String,
    val roles: List<String>? = null
) : CsmEvent(publisher)

class UserRemovedFromWorkspace(
    publisher: Any,
    val organizationId: String,
    val workspaceId: String,
    val userId: String
) : CsmEvent(publisher)

class ConnectorRemoved(publisher: Any, val connectorId: String) : CsmEvent(publisher)

class ConnectorRemovedForOrganization(
    publisher: Any,
    val organizationId: String,
    val connectorId: String
) : CsmEvent(publisher)

class ScenarioRunStartedForScenario(
    publisher: Any,
    val organizationId: String,
    val workspaceId: String,
    val scenarioId: String,
    val scenarioRunData: ScenarioRunData,
    val workflowData: WorkflowData
) : CsmEvent(publisher) {
  data class ScenarioRunData(val scenarioRunId: String, val csmSimulationRun: String)
  data class WorkflowData(val workflowId: String, val workflowName: String)
}

class ScenarioDatasetListChanged(
    publisher: Any,
    val organizationId: String,
    val workspaceId: String,
    val scenarioId: String,
    val datasetList: List<String>?
) : CsmEvent(publisher)
