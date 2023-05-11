// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api

import com.cosmotech.api.security.AbstractSecurityConfiguration
import com.cosmotech.api.security.endpointSecurityPublic
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.web.cors.CorsConfiguration

@Configuration
@EnableWebSecurity(debug = true)
@ConditionalOnProperty(
    name = ["csm.platform.identityProvider.code"], havingValue = "keycloak", matchIfMissing = false)
@EnableGlobalMethodSecurity(securedEnabled = true, prePostEnabled = true, proxyTargetClass = true)
internal class KeycloakSecurityConfiguration : AbstractSecurityConfiguration() {

  private val logger = LoggerFactory.getLogger(KeycloakSecurityConfiguration::class.java)

  override fun configure(http: HttpSecurity) {
    logger.info("Keycloak http security configuration")

    val corsHttpMethodsAllowed =
        HttpMethod.values().filterNot { it == HttpMethod.TRACE }.map(HttpMethod::name)

    http.cors()
        .configurationSource {
          CorsConfiguration().applyPermitDefaultValues().apply {
            allowedMethods = corsHttpMethodsAllowed
          }
        }
        .and()
        .authorizeRequests { requests ->
          requests.antMatchers(HttpMethod.OPTIONS, "/**").permitAll()

          // Public paths
          endpointSecurityPublic.forEach { path ->
            requests.antMatchers(HttpMethod.GET, path).permitAll()
          }

          requests.anyRequest().authenticated()
        }
        .oauth2Login()
  }
}
