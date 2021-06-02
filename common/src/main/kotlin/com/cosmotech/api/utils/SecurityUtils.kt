// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.utils

import java.lang.IllegalStateException
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder

fun getCurrentAuthentication(): Authentication? = SecurityContextHolder.getContext().authentication

fun getCurrentUserName(): String? = getCurrentAuthentication()?.name

fun getCurrentAuthenticatedUserName() =
    getCurrentUserName()
        ?: throw IllegalStateException("User Authentication not found in Security Context")
