// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun.container

import com.cosmotech.solution.domain.RunTemplate

internal class SolutionContainerStepSpec(
    val mode: String,
    val providerVar: String,
    val pathVar: String,
    val source: ((template: RunTemplate) -> String?)? = null,
    val path: ((organizationId: String, solutionId: String, runTemplateId: String) -> String)? =
        null,
)
