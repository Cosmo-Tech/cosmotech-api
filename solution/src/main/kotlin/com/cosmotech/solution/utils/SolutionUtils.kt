// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.solution.utils

import com.cosmotech.api.azure.sanitizeForAzureStorage
import com.cosmotech.solution.domain.RunTemplateHandlerId

fun getCloudPath(
    organizationId: String,
    solutionId: String,
    runTemplateId: String,
    handlerId: RunTemplateHandlerId,
) =
    StringBuilder(organizationId.sanitizeForAzureStorage())
        .append("/")
        .append(solutionId.sanitizeForAzureStorage())
        .append("/")
        .append(runTemplateId)
        .append("/")
        .append(handlerId)
        .append(".zip")
        .toString()
