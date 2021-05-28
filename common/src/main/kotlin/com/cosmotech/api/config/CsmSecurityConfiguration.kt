// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.config

import com.azure.spring.aad.AADAuthorizationServerEndpoints
import com.azure.spring.aad.webapi.AADJwtBearerTokenAuthenticationConverter
import com.azure.spring.aad.webapi.AADResourceServerConfiguration
import com.azure.spring.aad.webapi.validator.AADJwtClaimValidator
import com.azure.spring.autoconfigure.aad.AADAuthenticationProperties
import com.azure.spring.autoconfigure.aad.AADTokenClaim
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(securedEnabled = true, prePostEnabled = true, proxyTargetClass = true)
class CsmSecurityConfiguration(
    private val csmPlatformProperties: CsmPlatformProperties,
    private val aadResourceServerConfiguration: AADResourceServerConfiguration,
    private val aadAuthenticationProperties: AADAuthenticationProperties
) : WebSecurityConfigurerAdapter() {

  override fun configure(http: HttpSecurity) {
    http
        .authorizeRequests { requests ->
          requests
              .antMatchers(HttpMethod.OPTIONS, "/**")
              .permitAll()
              .antMatchers(HttpMethod.GET, "/actuator/health/**")
              .permitAll()
              .antMatchers(HttpMethod.GET, "/actuator/info")
              .permitAll()
              .antMatchers(HttpMethod.GET, "/")
              .permitAll()
              .antMatchers(HttpMethod.GET, "/swagger-ui.html")
              .permitAll()
              .antMatchers(HttpMethod.GET, "/swagger-ui/**")
              .permitAll()
              .antMatchers(HttpMethod.GET, "/openapi.*")
              .permitAll()
              .antMatchers(HttpMethod.GET, "/openapi/*")
              .permitAll()
              .antMatchers(HttpMethod.GET, "/openapi")
              .permitAll()
              .antMatchers(HttpMethod.GET, "/error")
              .permitAll()
              .antMatchers("/connectors", "/connectors/**")
              .hasAuthority("APPROLE_Platform.Admin")
              .anyRequest()
              .authenticated()
        }
        .oauth2ResourceServer()
        .jwt()
        .jwtAuthenticationConverter(
            AADJwtBearerTokenAuthenticationConverter().apply { setPrincipalClaimName("oid") })
  }

  @Bean
  fun jwtDecoder(): JwtDecoder {
    val identityEndpoints =
        AADAuthorizationServerEndpoints(
            aadAuthenticationProperties.baseUri, aadAuthenticationProperties.tenantId)
    val nimbusJwtDecoder =
        NimbusJwtDecoder.withJwkSetUri(identityEndpoints.jwkSetEndpoint()).build()
    val validators = aadResourceServerConfiguration.createDefaultValidator().toMutableList()

    val platformAllowedTenants = csmPlatformProperties.authorization.allowedTenants.toMutableSet()
    csmPlatformProperties.azure?.credentials?.tenantId?.let { platformAllowedTenants.add(it) }
    val allowedTenants = platformAllowedTenants.filterNot { it.isBlank() }

    validators.add { jwt ->
      // Validate against the list of allowed tenants
      AADJwtClaimValidator<String>(AADTokenClaim.TID) { allowedTenants.contains(it) }.validate(jwt)
    }
    nimbusJwtDecoder.setJwtValidator(DelegatingOAuth2TokenValidator(validators))
    return nimbusJwtDecoder
  }
}
