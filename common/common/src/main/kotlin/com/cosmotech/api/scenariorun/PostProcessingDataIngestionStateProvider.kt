// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.scenariorun

interface PostProcessingDataIngestionStateProvider {

  /** Provide scenario run post-processing data ingestion state */
  fun getStateFor(
      organizationId: String,
      workspaceKey: String,
      scenarioRunId: String,
      csmSimulationRun: String
  ): DataIngestionState?
}
