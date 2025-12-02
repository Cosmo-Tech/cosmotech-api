// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.events

class UserAddedToWorkspace(
    publisher: Any,
    val organizationId: String,
    val userId: String,
    val roles: List<String>? = null,
) : CsmEvent(publisher)

class UserRemovedFromWorkspace(
    publisher: Any,
    val organizationId: String,
    val workspaceId: String,
    val userId: String,
) : CsmEvent(publisher)

class WorkspaceDeleted(publisher: Any, val organizationId: String, val workspaceId: String) :
    CsmEvent(publisher)
