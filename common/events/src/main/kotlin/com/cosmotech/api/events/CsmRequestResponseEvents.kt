// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.events

import java.time.ZonedDateTime

sealed class CsmRequestResponseEvent<T>(publisher: Any) : CsmEvent(publisher) {
  var response: T? = null
}

class WorkflowStatusRequest(
    publisher: Any,
    val workflowId: String,
    val workflowName: String,
) : CsmRequestResponseEvent<String>(publisher)

class ScenarioDataDownloadRequest(
    publisher: Any,
    val jobId: String,
    val organizationId: String,
    val workspaceId: String,
    val scenarioId: String
) : CsmRequestResponseEvent<Map<String, Any>>(publisher)

class ScenarioDataDownloadJobInfoRequest(
    publisher: Any,
    val jobId: String,
    val organizationId: String,
) : CsmRequestResponseEvent<Pair<String?, String>>(publisher)

class WorkflowPhaseToStateRequest(
    publisher: Any,
    val organizationId: String,
    val workspaceKey: String,
    val jobId: String?,
    val workflowPhase: String?,
) : CsmRequestResponseEvent<String>(publisher)

class ScenarioRunEndToEndStateRequest(
    publisher: Any,
    val organizationId: String,
    val workspaceId: String,
    val scenarioRunId: String
) : CsmRequestResponseEvent<String>(publisher)

class ScenarioRunEndTimeRequest(
    publisher: Any,
    val organizationId: String,
    val workspaceId: String,
    val scenarioRunId: String
) : CsmRequestResponseEvent<ZonedDateTime>(publisher)
