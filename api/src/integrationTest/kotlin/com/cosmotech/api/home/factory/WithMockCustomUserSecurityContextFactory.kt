// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.home.factory

import com.cosmotech.api.home.annotations.WithMockOauth2User
import com.cosmotech.dataset.utils.convertToJsonValue
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication
import org.springframework.security.test.context.support.WithSecurityContextFactory
import java.time.LocalDateTime
import java.time.ZoneOffset

class WithMockOauth2UserSecurityContextFactory : WithSecurityContextFactory<WithMockOauth2User> {

    private val ISSUER_CLAIM_NAME = "iss"
    private val SUBJECT_CLAIM_NAME = "sub"
    private val ROLES_CLAIM_NAME = "userRoles"
    private val EMAIL_CLAIM_NAME = "email"


    override fun createSecurityContext(oauth2User: WithMockOauth2User): SecurityContext {
        val principalAttributes = mapOf(
            ISSUER_CLAIM_NAME to oauth2User.issuer,
            SUBJECT_CLAIM_NAME  to oauth2User.sub,
            ROLES_CLAIM_NAME to oauth2User.roles.toList(),
            EMAIL_CLAIM_NAME  to oauth2User.email,
        )

        val dateNow = LocalDateTime.now().toInstant(ZoneOffset.UTC)
        val context = SecurityContextHolder.createEmptyContext()
        val principal = DefaultOAuth2AuthenticatedPrincipal(
            principalAttributes,
            oauth2User
                .roles
                .toList()
                .map { SimpleGrantedAuthority(it) })
        val auth: Authentication =
            BearerTokenAuthentication(principal,
                OAuth2AccessToken(
                    OAuth2AccessToken.TokenType.BEARER,
                    principalAttributes.convertToJsonValue().toString(),
                    dateNow,
                    dateNow.plusSeconds(oauth2User.expiresIn)
                ),
                principal.authorities)
        context.authentication = auth
        return context
    }


}