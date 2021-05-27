// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.solution.utils

import com.cosmotech.solution.domain.RunTemplateHandlerId

fun getCloudPath(
    organizationId: String,
    solutionId: String,
    handlerId: RunTemplateHandlerId
): String {
  return "${organizationId}/${solutionId}/${handlerId}.zip"
}
