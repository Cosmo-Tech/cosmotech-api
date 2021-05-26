package com.cosmotech.api.config

import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.oauth2.config.annotation.web.configuration.ResourceServerConfigurerAdapter
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.oauth2.config.annotation.web.configurers.ResourceServerSecurityConfigurer


@Configuration
@EnableWebSecurity(debug =true)
class SecurityConfig: ResourceServerConfigurerAdapter() {

    override fun configure(http: HttpSecurity) {
        http
            .authorizeRequests()
                .antMatchers(HttpMethod.GET, "/actuator/health/**").permitAll()
                .antMatchers(HttpMethod.GET, "/actuator/info").permitAll()
                .antMatchers(HttpMethod.GET, "/").permitAll()
                .antMatchers(HttpMethod.GET, "/swagger-ui.html").permitAll()
                .antMatchers(HttpMethod.GET, "/swagger-ui/**").permitAll()
                .antMatchers(HttpMethod.GET, "/openapi.*").permitAll()
                .antMatchers(HttpMethod.GET, "/openapi/*").permitAll()
                .antMatchers(HttpMethod.GET, "/openapi").permitAll()
                .antMatchers(HttpMethod.GET, "/error").permitAll()
                //.antMatchers("/connectors").hasAuthority("#oauth2.hasRole(\"PLATFORM_ADMIN\")")
                .anyRequest().authenticated()
    }

    override fun configure(oauthServer: ResourceServerSecurityConfigurer) {
      // Disable resource id which should match aud
      // Note: setting security.oauth2.resource.id do not set this
        oauthServer.resourceId(null);
    }
}
