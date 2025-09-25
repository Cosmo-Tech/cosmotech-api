// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.events

class TriggerRunnerEvent(
    publisher: Any,
    val organizationId: String,
    val workspaceId: String,
    val runnerId: String
) : CsmRequestResponseEvent<String>(publisher)

class AskRunnerStatusEvent(
    publisher: Any,
    val organizationId: String,
    val workspaceId: String,
    val runnerId: String
) : CsmRequestResponseEvent<String>(publisher)

class RunnerDeleted(
    publisher: Any,
    val organizationId: String,
    val workspaceId: String,
    val runnerId: String
) : CsmEvent(publisher)

class UpdateRunnerStatus(
    publisher: Any,
    val organizationId: String,
    val workspaceId: String,
    val runnerId: String,
    val lastRunId: String,
) : CsmRequestResponseEvent<String>(publisher)
