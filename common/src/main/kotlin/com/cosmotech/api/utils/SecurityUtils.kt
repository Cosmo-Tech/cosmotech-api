// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.utils

import java.lang.IllegalStateException
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal

fun getCurrentAuthentication(): Authentication? = SecurityContextHolder.getContext().authentication

fun getCurrentUserName(): String? = getCurrentAuthentication()?.name

fun getCurrentUserUPN(): String? =
    (getCurrentAuthentication()?.principal as? OAuth2AuthenticatedPrincipal)?.getAttribute<String>(
        "upn")

fun getCurrentUserRoles(): List<String>? =
    (getCurrentAuthentication()?.principal as? OAuth2AuthenticatedPrincipal)?.getAttribute<
        List<String>>("roles")

fun getCurrentAuthenticatedUserName() =
    getCurrentUserName()
        ?: throw IllegalStateException("User Authentication not found in Security Context")

fun getCurrentAuthenticatedUserUPN() =
    getCurrentUserUPN()
        ?: throw IllegalStateException("User UPN not found in Authentication Principal")

fun getCurrentAuthenticatedUserRoles() =
    getCurrentUserRoles()
        ?: throw IllegalStateException("User roles not found in Authentication Principal")
