// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.home.workspace

import com.cosmotech.api.home.Constants.PLATFORM_ADMIN_EMAIL
import com.cosmotech.api.rbac.ROLE_ADMIN

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

    object RequestContent {

    }

    object Errors {
        val emptyNameOrganizationCreationRequestError =
            """{"type":"https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/400","title":"Bad Request","status":400,"detail":"name: size must be between 1 and 50","instance":"/organizations"}"""
    }
}