// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.events

sealed class CsmRequestResponseEvent<T>(publisher: Any) : CsmEvent(publisher) {
  var response: T? = null
}

class WorkflowStatusRequest(
    publisher: Any,
    val workflowId: String,
    val workflowName: String,
) : CsmRequestResponseEvent<String>(publisher)

class WorkflowPhaseToStateRequest(
    publisher: Any,
    val organizationId: String,
    val workspaceKey: String,
    val jobId: String?,
    val workflowPhase: String?,
) : CsmRequestResponseEvent<String>(publisher)
