// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.security

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity

@Configuration
@EnableWebSecurity(debug = true)
@ConditionalOnProperty(
    name = ["csm.platform.identityProvider.code"], havingValue = "okta", matchIfMissing = false)
@EnableGlobalMethodSecurity(securedEnabled = true, prePostEnabled = true, proxyTargetClass = true)
internal class OktaSecurityConfiguration : AbstractSecurityConfiguration() {

  private val logger = LoggerFactory.getLogger(OktaSecurityConfiguration::class.java)

  override fun configure(http: HttpSecurity) {
    logger.info("Okta http security configuration")
    super.getOAuth2JwtConfigurer(http)
  }
}
