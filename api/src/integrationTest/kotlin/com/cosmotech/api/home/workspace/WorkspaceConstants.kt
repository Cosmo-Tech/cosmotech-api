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
    const val SOLUTION_ID = "sol-123456AbCdEf"
    const val NEW_USER_ID = "new.user@cosmotech.com"
    const val NEW_USER_ROLE = "editor"
    const val NEW_WORKSPACE_NAME = "my_new_workspace_name"

    object RequestContent {
        const val MINIMAL_WORKSPACE_REQUEST_CREATION = """{"key":"$WORKSPACE_KEY", "name":"$WORKSPACE_NAME", "solution": {"solutionId":"$SOLUTION_ID" } }"""
        const val WORKSPACE_REQUEST_CREATION_WITH_ACCESSES = """{"name":"$WORKSPACE_NAME","security":{"default":"none","accessControlList":[{"id":"$PLATFORM_ADMIN_EMAIL", "role":"$ROLE_ADMIN"},{"id":"$NEW_USER_ID", "role":"$NEW_USER_ROLE"}] }}"""
        const val EMPTY_NAME_WORKSPACE_REQUEST_CREATION = """{"name":""}"""
        const val MINIMAL_WORKSPACE_REQUEST_UPDATE = """{"name":"$NEW_WORKSPACE_NAME"}"""
        const val MINIMAL_WORKSPACE_ACCESS_CONTROL_REQUEST = """{"id":"$NEW_USER_ID","role":"$NEW_USER_ROLE"}"""
    }

    object Errors {
        val emptyNameOrganizationCreationRequestError =
            """{"type":"https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/400","title":"Bad Request","status":400,"detail":"name: size must be between 1 and 50","instance":"/organizations"}"""
    }
}