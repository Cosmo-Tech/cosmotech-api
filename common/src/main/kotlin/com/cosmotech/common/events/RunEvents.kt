// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.events

enum class RunType(val value: String) {
  Run("run"),
  Delete("delete"),
}

class RunStart(publisher: Any, val runnerData: Any, val runType: RunType) :
    CsmRequestResponseEvent<String>(publisher)

class RunStop(publisher: Any, val runnerData: Any) : CsmEvent(publisher)

class AskRunStatusEvent(
    publisher: Any,
    val organizationId: String,
    val workspaceId: String,
    val runnerId: String,
    val runId: String,
) : CsmRequestResponseEvent<String>(publisher)

class HasRunningRuns(
    publisher: Any,
    val organizationId: String,
    val workspaceId: String,
    val runnerId: String,
) : CsmRequestResponseEvent<Boolean>(publisher)

class RunDeleted(
    publisher: Any,
    val organizationId: String,
    val workspaceId: String,
    val runnerId: String,
    val runId: String,
    val lastRun: String?,
) : CsmEvent(publisher)
