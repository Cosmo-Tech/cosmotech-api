// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
@file:JvmName("SecurityUtilsKt")

package com.cosmotech.common.utils

import com.cosmotech.common.config.CsmPlatformProperties
import com.cosmotech.common.security.filters.ApiKeyAuthentication
import com.nimbusds.jose.util.JSONObjectUtils
import com.nimbusds.jwt.JWTClaimNames
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.JWTParser
import java.text.ParseException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken

fun getCurrentAuthentication(): Authentication? = SecurityContextHolder.getContext().authentication

fun getCurrentAuthenticatedUserName(configuration: CsmPlatformProperties): String {
  return getValueFromAuthenticatedToken(configuration) {
    try {
      val jwtClaimsSet = JWTParser.parse(it).jwtClaimsSet
      jwtClaimsSet.getStringClaim(configuration.authorization.principalJwtClaim)
          ?: jwtClaimsSet.getStringClaim(configuration.authorization.applicationIdJwtClaim)
          ?: throw IllegalStateException("User Authentication not found in Security Context")
    } catch (e: ParseException) {
      JSONObjectUtils.parse(it)[configuration.authorization.principalJwtClaim] as String
    }
  }
}

fun getCurrentAuthenticatedIssuer(configuration: CsmPlatformProperties): String {
  return getValueFromAuthenticatedToken(configuration) {
    try {
      JWTParser.parse(it).jwtClaimsSet.issuer
    } catch (e: ParseException) {
      JSONObjectUtils.parse(it)[JWTClaimNames.ISSUER] as String
    }
  }
}

fun getCurrentAccountIdentifier(configuration: CsmPlatformProperties): String {
  return getValueFromAuthenticatedToken(configuration) {
    try {
      val jwtClaimsSet = JWTParser.parse(it).jwtClaimsSet
      jwtClaimsSet.getStringClaim(configuration.authorization.mailJwtClaim)
          ?: jwtClaimsSet.getStringClaim(configuration.authorization.applicationIdJwtClaim)
    } catch (e: ParseException) {
      JSONObjectUtils.parse(it)[configuration.authorization.mailJwtClaim] as String
    }
  }
}

fun getCurrentAccountGroups(configuration: CsmPlatformProperties): List<String> {
  return getValueFromAuthenticatedToken(configuration) {
    try {
      val jwt = JWTParser.parse(it)
      jwt.jwtClaimsSet.getStringListClaim(configuration.authorization.groupJwtClaim) ?: emptyList()
    } catch (e: ParseException) {
      JSONObjectUtils.parse(it)[configuration.authorization.groupJwtClaim] as List<String>
    }
  }
}

fun getCurrentAuthenticatedRoles(configuration: CsmPlatformProperties): List<String> {
  return getValueFromAuthenticatedToken(configuration) {
    try {
      val jwt = JWTParser.parse(it)
      jwt.jwtClaimsSet.getStringListClaim(configuration.authorization.rolesJwtClaim) ?: emptyList()
    } catch (e: ParseException) {
      JSONObjectUtils.parse(it)[configuration.authorization.rolesJwtClaim] as List<String>
    }
  }
}

fun <T> getValueFromAuthenticatedToken(
    configuration: CsmPlatformProperties,
    actionLambda: (String) -> T,
): T {
  val authentication = getCurrentAuthentication()
  checkNotNull(authentication) { "User Authentication not found in Security Context" }

  if (authentication is JwtAuthenticationToken) {
    return authentication.token.tokenValue.let { actionLambda(it) }
  }
  if (authentication is ApiKeyAuthentication) {
    val headerApiKey = authentication.apiKey
    var jwtClaimsSet: JWTClaimsSet? = null

    configuration.authorization.allowedApiKeyConsumers.forEach { apiKeyConsumer ->
      if (headerApiKey.isNotEmpty() && headerApiKey == apiKeyConsumer.apiKey) {
        jwtClaimsSet =
            JWTClaimsSet.Builder()
                .issuer(apiKeyConsumer.name)
                .claim(configuration.authorization.principalJwtClaim, authentication.principal)
                .claim(configuration.authorization.mailJwtClaim, authentication.principal)
                .claim(
                    configuration.authorization.rolesJwtClaim,
                    authentication.authorities
                        .map { (it as SimpleGrantedAuthority).authority }
                        .toList(),
                )
                .build()
      }
    }
    if (jwtClaimsSet == null) {
      throw BadCredentialsException("Api key sent is not allowed")
    }
    return actionLambda(jwtClaimsSet.toString())
  }

  return (authentication as BearerTokenAuthentication).token.tokenValue.let { actionLambda(it) }
}
