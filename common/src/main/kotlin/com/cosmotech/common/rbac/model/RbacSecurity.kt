// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.rbac.model

import com.cosmotech.common.rbac.ROLE_NONE

data class RbacSecurity(
    var id: String?,
    var default: String = ROLE_NONE,
    var accessControlList: kotlin.collections.MutableList<RbacAccessControl> = mutableListOf()
)
