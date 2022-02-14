// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.azure

import com.cosmotech.api.security.AbstractSecurityConfiguration
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.oauth2.jwt.Jwt

@Configuration
@EnableWebSecurity
@ConditionalOnProperty(
    name = ["csm.platform.identityProvider.code"], havingValue = "aad", matchIfMissing = true)
@EnableGlobalMethodSecurity(securedEnabled = true, prePostEnabled = true, proxyTargetClass = true)
internal class CsmAzureSecurityConfiguration(
    private val aadJwtAuthenticationConverter: Converter<Jwt, out AbstractAuthenticationToken>
) : AbstractSecurityConfiguration() {

  private val logger = LoggerFactory.getLogger(CsmAzureSecurityConfiguration::class.java)

  override fun configure(http: HttpSecurity) {
    logger.info("Azure Active Directory http security configuration")
    super.getOAuth2JwtConfigurer(http)?.jwtAuthenticationConverter(aadJwtAuthenticationConverter)
  }
}
