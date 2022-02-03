// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.events

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

class ScenarioDatasetListChanged(
    publisher: Any,
    val organizationId: String,
    val workspaceId: String,
    val scenarioId: String,
    val datasetList: List<String>?
) : CsmEvent(publisher)

class ScenarioDeleted(
    publisher: Any,
    val organizationId: String,
    val workspaceId: String,
    val scenarioId: String
) : CsmEvent(publisher)
