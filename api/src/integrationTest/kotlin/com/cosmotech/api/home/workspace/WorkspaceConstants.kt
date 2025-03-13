// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.home.workspace

/**
 * Constant class that contains for Workspace endpoints:
 * - default payload (RequestContent) for API calls
 * - default error messages (Errors) returned by API
 */
object WorkspaceConstants {

  const val WORKSPACE_NAME = "my_workspace_name"
  const val WORKSPACE_KEY = "my_workspace_key"
  const val NEW_USER_ID = "new.user@cosmotech.com"
  const val NEW_USER_ROLE = "editor"

  object RequestContent {}

  object Errors {}
}
