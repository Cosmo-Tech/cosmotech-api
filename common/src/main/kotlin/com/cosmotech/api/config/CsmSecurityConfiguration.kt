// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.config

import com.azure.spring.aad.webapi.AADResourceServerWebSecurityConfigurerAdapter
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(securedEnabled = true, prePostEnabled = true, proxyTargetClass = true)
class SecurityConfig : AADResourceServerWebSecurityConfigurerAdapter() {

  override fun configure(http: HttpSecurity) {
    super.configure(http)
    http.authorizeRequests { requests ->
      requests
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
          .antMatchers("/connectors")
          .hasAuthority("APPROLE_Platform.Admin")
          .anyRequest()
          .authenticated()
    }
  }
}
