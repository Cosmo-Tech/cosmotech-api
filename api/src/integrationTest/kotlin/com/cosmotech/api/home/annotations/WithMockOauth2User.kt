// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.home.annotations

import com.cosmotech.api.home.Constants.DEFAULT_ISSUER
import com.cosmotech.api.home.Constants.DEFAULT_SUBJECT
import com.cosmotech.api.home.Constants.PLATFORM_ADMIN_EMAIL
import com.cosmotech.api.home.factories.WithMockOauth2UserSecurityContextFactory
import com.cosmotech.api.security.ROLE_PLATFORM_ADMIN
import org.springframework.security.test.context.support.WithSecurityContext

/**
 * This annotation allows developers to mock the Oauth2 user connected Some values are defined by
 * default (Admin profile by default) but you can specify:
 * - issuer
 * - subject
 * - email
 * - roles
 */
@Retention(AnnotationRetention.RUNTIME)
@WithSecurityContext(factory = WithMockOauth2UserSecurityContextFactory::class)
annotation class WithMockOauth2User(
    val issuer: String = DEFAULT_ISSUER,
    val sub: String = DEFAULT_SUBJECT,
    val email: String = PLATFORM_ADMIN_EMAIL,
    val roles: Array<String> = [ROLE_PLATFORM_ADMIN],
    val expiresIn: Long = 600
)
