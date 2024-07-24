// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run

import com.cosmotech.run.api.RunApiService
import com.cosmotech.run.domain.Run

interface RunApiServiceInterface : RunApiService {
  fun listAllRuns(organizationId: String, workspaceId: String, runnerId: String): List<Run>
}
