// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.utils

import com.azure.spring.aad.AADOAuth2AuthenticatedPrincipal
import java.lang.IllegalStateException
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder

fun getCurrentAuthentication(): Authentication? = SecurityContextHolder.getContext().authentication

fun getCurrentUserName(): String? = getCurrentAuthentication()?.name

fun getCurrentUserUPN(): String? =
    (getCurrentAuthentication()?.principal as? AADOAuth2AuthenticatedPrincipal)?.attributes
        ?.getOrDefault("upn", null) as?
        String?

fun getCurrentAuthenticatedUserName() =
    getCurrentUserName()
        ?: throw IllegalStateException("User Authentication not found in Security Context")

fun getCurrentAuthenticatedUserUPN() =
    getCurrentUserUPN()
        ?: throw IllegalStateException("User UPN not found in Authentication Principal")
