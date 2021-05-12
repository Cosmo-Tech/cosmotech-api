// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.workspace.utils

enum class StepResource(val step: String) {
  PARAMETERS_HANDLER("parameters_handler"),
  VALIDATOR("validator"),
  PRERUN("prerun"),
  ENGINE("engine"),
  POSTRUN("postrun"),
}

fun getCloudPath(organizationId: String, workspaceId: String, stepResource: StepResource): String {
  return "${organizationId}/${workspaceId}/${stepResource.step}.zip"
}
