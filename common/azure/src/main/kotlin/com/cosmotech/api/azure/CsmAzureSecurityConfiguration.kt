// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.azure

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.security.AbstractSecurityConfiguration
import com.cosmotech.api.security.CsmJwtClaimValueInCollectionValidator
import com.cosmotech.api.security.CsmSecurityValidator
import com.cosmotech.api.security.ROLE_ORGANIZATION_USER
import com.cosmotech.api.security.ROLE_ORGANIZATION_VIEWER
import com.cosmotech.api.security.ROLE_PLATFORM_ADMIN
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder

@Configuration
@EnableWebSecurity
@ConditionalOnProperty(
    name = ["csm.platform.identityProvider.code"], havingValue = "azure", matchIfMissing = true)
@EnableGlobalMethodSecurity(securedEnabled = true, prePostEnabled = true, proxyTargetClass = true)
internal class CsmAzureSecurityConfiguration(
    private val aadJwtAuthenticationConverter: Converter<Jwt, out AbstractAuthenticationToken>,
    private val csmPlatformProperties: CsmPlatformProperties,
) : AbstractSecurityConfiguration() {

  private val logger = LoggerFactory.getLogger(CsmAzureSecurityConfiguration::class.java)

  private val organizationAdminGroup =
      csmPlatformProperties.identityProvider?.adminGroup ?: ROLE_PLATFORM_ADMIN
  private val organizationUserGroup =
      csmPlatformProperties.identityProvider?.userGroup ?: ROLE_ORGANIZATION_USER
  private val organizationViewerGroup =
      csmPlatformProperties.identityProvider?.viewerGroup ?: ROLE_ORGANIZATION_VIEWER

  override fun configure(http: HttpSecurity) {
    logger.info("Azure Active Directory http security configuration")
    super.getOAuth2JwtConfigurer(
            http, organizationAdminGroup, organizationUserGroup, organizationViewerGroup)
        ?.jwtAuthenticationConverter(aadJwtAuthenticationConverter)
  }

  @Bean
  fun jwtDecoder(csmSecurityValidator: CsmSecurityValidator): JwtDecoder {
    val allowedTenants =
        (csmSecurityValidator.getAllowedTenants() +
                csmPlatformProperties.authorization.allowedTenants)
            .filterNotNull()
            .filterNot(String::isBlank)
            .toSet()
    if (allowedTenants.isEmpty()) {
      logger.warn(
          "Could not determine list of tenants allowed. " +
              "This means no Tenant is allowed to use this API. " +
              "Is this intentional? " +
              "If not, please properly configure any of the following properties: " +
              "'csm.platform.<provider>.credentials.core.tenantId' " +
              "or 'csm.platform.authorization.allowed-tenants'" +
              " or 'csm.platform.<provider>.credentials.customer.tenantId' ")
    }

    val nimbusJwtDecoder =
        NimbusJwtDecoder.withJwkSetUri(csmSecurityValidator.getJwksSetUri()).build()
    val validators = csmSecurityValidator.getValidators().toMutableList()

    if ("*" in allowedTenants) {
      logger.info(
          "All tenants allowed to authenticate, since the following property contains a wildcard " +
              "element: csm.platform.authorization.allowed-tenants")
    } else {
      // Validate against the list of allowed tenants
      validators.add(
          CsmJwtClaimValueInCollectionValidator(
              claimName = csmPlatformProperties.authorization.tenantIdJwtClaim,
              allowed = allowedTenants))
    }

    nimbusJwtDecoder.setJwtValidator(DelegatingOAuth2TokenValidator(validators))
    return nimbusJwtDecoder
  }
}
