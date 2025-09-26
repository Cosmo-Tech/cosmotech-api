// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.events

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

class UserUnregisteredForOrganization(
    publisher: Any,
    val organizationId: String,
    val userId: String
) : CsmEvent(publisher)
