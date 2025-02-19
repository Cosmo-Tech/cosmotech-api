// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.home.organization

object OrganizationConstants {

    const val ORGANIZATION_NAME = "my_new_organization_name"

    object RequestContent {
        const val ORGANIZATION_NAME = "name"
        const val MINIMAL_ORGANIZATION_REQUEST_CREATION = """{"name":"${OrganizationConstants.ORGANIZATION_NAME}"}"""
        const val EMPTY_NAME_ORGANIZATION_REQUEST_CREATION = """{"name":""}"""
    }

    object Errors {
        val emptyNameOrganizationCreationRequestError =
            """{"type":"https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/400","title":"Bad Request","status":400,"detail":"name: size must be between 1 and 50","instance":"/organizations"}"""
    }





}