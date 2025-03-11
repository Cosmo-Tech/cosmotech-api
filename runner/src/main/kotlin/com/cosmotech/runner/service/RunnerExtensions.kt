// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.runner.service

import com.cosmotech.api.rbac.model.RbacAccessControl
import com.cosmotech.api.rbac.model.RbacSecurity
import com.cosmotech.runner.domain.Runner

// for compatibility
// USE RunnerInstance.getSecurity instead
fun Runner.getRbac(): RbacSecurity {
  return RbacSecurity(
      this.id,
      this.security.default,
      this.security.accessControlList.map { RbacAccessControl(it.id, it.role) }.toMutableList())
}
