// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.home.factories

import com.cosmotech.api.home.annotations.WithMockOauth2User
import com.cosmotech.api.utils.objectMapper
import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.json.JSONObject
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication
import org.springframework.security.test.context.support.WithSecurityContextFactory

/**
 * Class that override default Authentication based on annotation WithMockOauth2User
 *
 * @see WithMockOauth2User N.B: Authentication will not contain signed or valid BearerToken but
 *   simple JWT Fallback behavior on non-existing claim (or when ParseException is thrown) are dealt
 *   within SecurityUtils class
 * @see <a
 *   href="https://github.com/Cosmo-Tech/cosmotech-api-common/blob/main/src/main/kotlin/com/cosmotech/api/utils/SecurityUtils.kt</a>
 */
class WithMockOauth2UserSecurityContextFactory : WithSecurityContextFactory<WithMockOauth2User> {

  private val ISSUER_CLAIM_NAME = "iss"
  private val SUBJECT_CLAIM_NAME = "sub"
  private val ROLES_CLAIM_NAME = "userRoles"
  private val EMAIL_CLAIM_NAME = "email"

  override fun createSecurityContext(oauth2User: WithMockOauth2User): SecurityContext {
    val principalAttributes =
        mapOf(
            ISSUER_CLAIM_NAME to oauth2User.issuer,
            SUBJECT_CLAIM_NAME to oauth2User.sub,
            ROLES_CLAIM_NAME to oauth2User.roles.toList(),
            EMAIL_CLAIM_NAME to oauth2User.email,
        )

    val dateNow = LocalDateTime.now().toInstant(ZoneOffset.UTC)
    val context = SecurityContextHolder.createEmptyContext()
    val principal =
        DefaultOAuth2AuthenticatedPrincipal(
            principalAttributes, oauth2User.roles.toList().map { SimpleGrantedAuthority(it) })
    val auth: Authentication =
        BearerTokenAuthentication(
            principal,
            OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                principalAttributes.convertToJsonValue().toString(),
                dateNow,
                dateNow.plusSeconds(oauth2User.expiresIn)),
            principal.authorities)
    context.authentication = auth
    return context
  }
}

val jsonObjectMapper: ObjectMapper =
    objectMapper().configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true)

@Suppress("SwallowedException")
fun Any.convertToJsonValue(): Any {
  var result = this
  if (this is String) {
    if (this.startsWith("{") || this.startsWith("[") || this.startsWith("\"")) {
      result =
          try {
            val firstTry = jsonObjectMapper.readValue<JSONObject>(this)
            if (firstTry.isEmpty) {
              jsonObjectMapper.readValue(this)
            } else {
              firstTry
            }
          } catch (e: JacksonException) {
            return result
          }
    }
  }
  return result
}
