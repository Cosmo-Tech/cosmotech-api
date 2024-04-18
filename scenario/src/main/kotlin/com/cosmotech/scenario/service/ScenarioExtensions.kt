// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenario.service

import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.rbac.model.RbacAccessControl
import com.cosmotech.api.rbac.model.RbacSecurity
import com.cosmotech.scenario.domain.Scenario
import com.cosmotech.scenario.domain.ScenarioAccessControl
import com.cosmotech.scenario.domain.ScenarioSecurity

fun Scenario.getRbac(): RbacSecurity {
  return RbacSecurity(
      this.id,
      this.security?.default ?: ROLE_NONE,
      this.security?.accessControlList?.map { RbacAccessControl(it.id, it.role) }?.toMutableList()
          ?: mutableListOf())
}

fun Scenario.setRbac(rbacSecurity: RbacSecurity) {
  this.security =
      ScenarioSecurity(
          rbacSecurity.default,
          rbacSecurity.accessControlList
              .map { ScenarioAccessControl(it.id, it.role) }
              .toMutableList())
}
