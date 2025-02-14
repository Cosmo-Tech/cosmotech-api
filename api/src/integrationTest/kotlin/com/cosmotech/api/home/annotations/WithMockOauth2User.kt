// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.home.annotations

import com.cosmotech.api.home.factory.WithMockOauth2UserSecurityContextFactory
import org.springframework.security.test.context.support.WithSecurityContext

@Retention(AnnotationRetention.RUNTIME)
@WithSecurityContext(factory = WithMockOauth2UserSecurityContextFactory::class)
annotation class WithMockOauth2User(val issuer: String = "test-issuer",
                                    val sub: String = "test-subject",
                                    val email: String = "user.admin@test.com",
                                    val roles: Array<String> = ["Platform.Admin"],
                                    val expiresIn: Long = 600)