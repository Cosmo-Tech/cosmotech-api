// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.events

import org.springframework.context.ApplicationEvent

sealed class CsmEvent(publisher: Any) : ApplicationEvent(publisher)

class OrganizationRegistered(publisher: Any, val organizationId: String) : CsmEvent(publisher)

class OrganizationUnregistered(publisher: Any, val organizationId: String) : CsmEvent(publisher)

class UserAddedToOrganization(publisher: Any, val organizationId: String, val userId: String) :
    CsmEvent(publisher)

class UserRemovedFromOrganization(publisher: Any, val organizationId: String, val userId: String) :
    CsmEvent(publisher)

class UserRegistered(publisher: Any, val userId: String) : CsmEvent(publisher)

class UserUnregistered(publisher: Any, val userId: String) : CsmEvent(publisher)
