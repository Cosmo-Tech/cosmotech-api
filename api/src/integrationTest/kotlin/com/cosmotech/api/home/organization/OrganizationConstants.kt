// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.home.organization

import com.cosmotech.api.home.Constants.PLATFORM_ADMIN_EMAIL
import com.cosmotech.api.rbac.ROLE_ADMIN

/**
 * Constant class that contains:
 * - default payload (RequestContent) for API calls
 * -default error messages (Errors) returned by API
 */
object OrganizationConstants {

    const val ORGANIZATION_NAME = "my_organization_name"
    const val NEW_USER_ID = "new.user@cosmotech.com"
    const val NEW_USER_ROLE = "editor"
    const val NEW_ORGANIZATION_NAME = "my_new_organization_name"

    object RequestContent {
        const val MINIMAL_ORGANIZATION_REQUEST_CREATION = """{"name":"$ORGANIZATION_NAME"}"""
        const val ORGANIZATION_REQUEST_CREATION_WITH_ACCESSES = """{"name":"$ORGANIZATION_NAME","security":{"default":"none","accessControlList":[{"id":"$PLATFORM_ADMIN_EMAIL", "role":"$ROLE_ADMIN"},{"id":"$NEW_USER_ID", "role":"$NEW_USER_ROLE"}] }}"""
        const val EMPTY_NAME_ORGANIZATION_REQUEST_CREATION = """{"name":""}"""
        const val MINIMAL_ORGANIZATION_REQUEST_UPDATE = """{"name":"$NEW_ORGANIZATION_NAME"}"""
        const val MINIMAL_ORGANIZATION_ACCESS_CONTROL_REQUEST = """{"id":"$NEW_USER_ID","role":"$NEW_USER_ROLE"}"""
    }

    object Errors {
        val emptyNameOrganizationCreationRequestError =
            """{"type":"https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/400","title":"Bad Request","status":400,"detail":"name: size must be between 1 and 50","instance":"/organizations"}"""
    }

}