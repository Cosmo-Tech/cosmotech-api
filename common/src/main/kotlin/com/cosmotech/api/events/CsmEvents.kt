// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.events

import org.springframework.context.ApplicationEvent

sealed class CsmEvent(open val publisher: Any) : ApplicationEvent(publisher)

class OrganizationRegistered(override val publisher: Any, val organizationId: String) :
    CsmEvent(publisher)

class OrganizationUnregistered(override val publisher: Any, val organizationId: String) :
    CsmEvent(publisher)

class UserAddedToOrganization(
    override val publisher: Any,
    val organizationId: String,
    val userId: String
) : CsmEvent(publisher)

class UserRemovedFromOrganization(
    override val publisher: Any,
    val organizationId: String,
    val userId: String
) : CsmEvent(publisher)
