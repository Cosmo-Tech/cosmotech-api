// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.solution

import com.cosmotech.api.rbac.PERMISSION_READ
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.solution.domain.Solution

interface SolutionApiServiceInterface : SolutionApiService {

  fun getVerifiedSolution(
      organizationId: String,
      solutionId: String,
      requiredPermission: String = PERMISSION_READ
  ): Solution
}
