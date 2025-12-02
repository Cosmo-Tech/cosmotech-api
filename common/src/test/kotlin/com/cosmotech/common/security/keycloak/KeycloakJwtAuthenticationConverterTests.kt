// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.security.keycloak

import com.cosmotech.common.config.CsmPlatformProperties
import io.mockk.every
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken

class KeycloakJwtAuthenticationConverterTests {

  private val logger: Logger = LoggerFactory.getLogger(this::class.java)

  private lateinit var csmPlatformProperties: CsmPlatformProperties

  lateinit var keycloakJwtAuthenticationConverter: KeycloakJwtAuthenticationConverter

  lateinit var jwt: Jwt

  @BeforeTest
  fun beforeEachTest() {
    logger.trace("Begin test")
    csmPlatformProperties = mockk<CsmPlatformProperties>()
    jwt = mockk<Jwt>()
    keycloakJwtAuthenticationConverter = KeycloakJwtAuthenticationConverter(csmPlatformProperties)
  }

  @Test
  fun `convertRolesToAuthorities with correct values`() {
    val principalClaimValue = "my.principal@me.com"
    val principalClaimName = "email"
    val claims =
        mutableMapOf(
            "claim1" to "10",
            "claimRoles" to listOf("role1", "role2", "role3"),
            "claimName" to "myClaimName",
            "sub" to "123-456-798",
            principalClaimName to principalClaimValue,
        )
    val expectedSimpleGrantedAuthorities =
        listOf(
            SimpleGrantedAuthority("role1"),
            SimpleGrantedAuthority("role2"),
            SimpleGrantedAuthority("role3"),
        )

    every { jwt.claims } returns claims
    every { csmPlatformProperties.authorization.rolesJwtClaim } returns "claimRoles"
    every { csmPlatformProperties.authorization.principalJwtClaim } returns principalClaimName
    every { jwt.getClaimAsString(principalClaimName) } returns principalClaimValue

    val jwtConverted = keycloakJwtAuthenticationConverter.convert(jwt)

    assertEquals(
        JwtAuthenticationToken(jwt, expectedSimpleGrantedAuthorities, principalClaimName),
        jwtConverted,
    )
  }

  @Test
  fun `convertRolesToAuthorities with correct values when no principalJwtClaim set`() {
    val principalClaimName = "unexisting-principal-claim"
    val applicationIdClaimValue = "123-456-798"
    val applicationIdClaimName = "sub"
    val claims =
        mutableMapOf(
            "claim1" to "10",
            "claimRoles" to listOf("role1", "role2", "role3"),
            "claimName" to "myClaimName",
            applicationIdClaimName to applicationIdClaimValue,
        )
    val expectedSimpleGrantedAuthorities =
        listOf(
            SimpleGrantedAuthority("role1"),
            SimpleGrantedAuthority("role2"),
            SimpleGrantedAuthority("role3"),
        )

    every { jwt.claims } returns claims
    every { csmPlatformProperties.authorization.rolesJwtClaim } returns "claimRoles"
    every { csmPlatformProperties.authorization.principalJwtClaim } returns principalClaimName
    every { csmPlatformProperties.authorization.applicationIdJwtClaim } returns
        applicationIdClaimName
    every { jwt.getClaimAsString(principalClaimName) } returns null
    every { jwt.getClaimAsString(applicationIdClaimName) } returns applicationIdClaimValue

    val jwtConverted = keycloakJwtAuthenticationConverter.convert(jwt)

    assertEquals(
        JwtAuthenticationToken(jwt, expectedSimpleGrantedAuthorities, applicationIdClaimValue),
        jwtConverted,
    )
  }

  @Test
  fun `convertRolesToAuthorities with non-existing role claim values`() {
    val principalClaimValue = "my.principal@me.com"
    val principalClaimName = "email"
    val claims =
        mutableMapOf(
            "claim1" to "10",
            "claimRoles" to listOf("role1", "role2", "role3"),
            "claimName" to "myClaimName",
            "sub" to "123-456-798",
            principalClaimName to principalClaimValue,
        )

    every { jwt.claims } returns claims
    every { csmPlatformProperties.authorization.rolesJwtClaim } returns "unexisting-role-claim"
    every { csmPlatformProperties.authorization.principalJwtClaim } returns principalClaimName
    every { jwt.getClaimAsString(principalClaimName) } returns principalClaimValue

    val jwtConverted = keycloakJwtAuthenticationConverter.convert(jwt)

    assertEquals(JwtAuthenticationToken(jwt, emptyList(), principalClaimName), jwtConverted)
  }

  @Test
  fun `convertRolesToAuthorities with non-existing role claim values when no principalJwtClaim set`() {
    val principalClaimName = "unexisting-principal-claim"
    val applicationIdClaimValue = "123-456-798"
    val applicationIdClaimName = "sub"
    val claims =
        mutableMapOf(
            "claim1" to "10",
            "claimRoles" to listOf("role1", "role2", "role3"),
            "claimName" to "myClaimName",
            "sub" to "123-456-798",
        )

    every { jwt.claims } returns claims
    every { csmPlatformProperties.authorization.rolesJwtClaim } returns "unexisting-role-claim"
    every { csmPlatformProperties.authorization.principalJwtClaim } returns principalClaimName
    every { csmPlatformProperties.authorization.applicationIdJwtClaim } returns
        applicationIdClaimName
    every { jwt.getClaimAsString(principalClaimName) } returns null
    every { jwt.getClaimAsString(applicationIdClaimName) } returns applicationIdClaimValue

    val jwtConverted = keycloakJwtAuthenticationConverter.convert(jwt)

    assertEquals(JwtAuthenticationToken(jwt, emptyList(), applicationIdClaimName), jwtConverted)
  }

  @Test
  fun `convertRolesToAuthorities with existing role claim but no roles defined`() {
    val principalClaimValue = "my.principal@me.com"
    val principalClaimName = "email"
    val claims =
        mutableMapOf(
            "claim1" to "10",
            "claimRoles" to emptyList<String>(),
            "claimName" to "myClaimName",
            "sub" to "123-456-798",
            principalClaimName to principalClaimValue,
        )

    every { jwt.claims } returns claims
    every { csmPlatformProperties.authorization.rolesJwtClaim } returns "claimRoles"
    every { csmPlatformProperties.authorization.principalJwtClaim } returns principalClaimName
    every { jwt.getClaimAsString(principalClaimName) } returns principalClaimValue

    val jwtConverted = keycloakJwtAuthenticationConverter.convert(jwt)

    assertEquals(JwtAuthenticationToken(jwt, emptyList(), principalClaimName), jwtConverted)
  }

  @Test
  fun `convertRolesToAuthorities with existing role claim but no roles defined when no principalJwtClaim set`() {
    val principalClaimName = "unexisting-principal-claim"
    val applicationIdClaimValue = "123-456-798"
    val applicationIdClaimName = "sub"
    val claims =
        mutableMapOf(
            "claim1" to "10",
            "claimRoles" to emptyList<String>(),
            "claimName" to "myClaimName",
            "sub" to "123-456-798",
        )

    every { jwt.claims } returns claims
    every { csmPlatformProperties.authorization.rolesJwtClaim } returns "claimRoles"
    every { csmPlatformProperties.authorization.principalJwtClaim } returns principalClaimName
    every { csmPlatformProperties.authorization.applicationIdJwtClaim } returns
        applicationIdClaimName
    every { jwt.getClaimAsString(principalClaimName) } returns null
    every { jwt.getClaimAsString(applicationIdClaimName) } returns applicationIdClaimValue

    val jwtConverted = keycloakJwtAuthenticationConverter.convert(jwt)

    assertEquals(JwtAuthenticationToken(jwt, emptyList(), applicationIdClaimName), jwtConverted)
  }
}
