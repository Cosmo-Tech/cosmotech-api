// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun

import com.cosmotech.solution.domain.RunTemplate

class SolutionContainerStepSpec(
    val mode: String,
    val providerVar: String,
    val pathVar: String,
    val source: ((template: RunTemplate) -> String?)? = null,
    val path: ((organizationId: String, workspaceId: String) -> String)? = null,
)
