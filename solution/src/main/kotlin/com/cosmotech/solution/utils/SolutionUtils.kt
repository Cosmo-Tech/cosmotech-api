// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.solution.utils

import com.cosmotech.api.utils.sanitizeForCloudStorage
import com.cosmotech.solution.domain.RunTemplateHandlerId

fun getCloudPath(
    organizationId: String,
    solutionId: String,
    runTemplateId: String,
    handlerId: RunTemplateHandlerId,
) =
    StringBuilder(organizationId.sanitizeForCloudStorage())
        .append("/")
        .append(solutionId.sanitizeForCloudStorage())
        .append("/")
        .append(runTemplateId)
        .append("/")
        .append(handlerId)
        .append(".zip")
        .toString()
