// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.azure

import com.azure.spring.aad.AADAuthorizationServerEndpoints
import com.azure.spring.aad.webapi.AADJwtBearerTokenAuthenticationConverter
import com.azure.spring.aad.webapi.AADResourceServerConfiguration
import com.azure.spring.aad.webapi.AADResourceServerProperties
import com.azure.spring.autoconfigure.aad.AADAuthenticationProperties
import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.security.CsmSecurityValidator
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    name = ["csm.platform.identityProvider.code"], havingValue = "azure", matchIfMissing = true)
internal class CsmAzureBasedSecurityConfiguration(
    private val csmPlatformProperties: CsmPlatformProperties,
) {

  @Bean
  fun aadJwtAuthenticationConverter(): Converter<Jwt, out AbstractAuthenticationToken> {
    val claimAuthorityMap =
        AADResourceServerProperties.DEFAULT_CLAIM_TO_AUTHORITY_PREFIX_MAP.toMutableMap()

    csmPlatformProperties.azure?.claimToAuthorityPrefix?.let { claimAuthorityMap.putAll(it) }
    return AADJwtBearerTokenAuthenticationConverter(
        csmPlatformProperties.authorization.principalJwtClaim, claimAuthorityMap)
  }

  @Bean
  fun csmSecurityValidator(
      aadResourceServerConfiguration: AADResourceServerConfiguration,
      aadAuthenticationProperties: AADAuthenticationProperties,
  ) =
      CsmAzureBasedSecurityValidator(
          csmPlatformProperties, aadResourceServerConfiguration, aadAuthenticationProperties)
}

internal class CsmAzureBasedSecurityValidator(
    private val csmPlatformProperties: CsmPlatformProperties,
    private val aadResourceServerConfiguration: AADResourceServerConfiguration,
    private val aadAuthenticationProperties: AADAuthenticationProperties,
) : CsmSecurityValidator {
  override fun getAllowedTenants() =
      listOf(
          csmPlatformProperties.azure?.credentials?.core?.tenantId,
          csmPlatformProperties.azure?.credentials?.customer?.tenantId)

  override fun getJwksSetUri(): String =
      AADAuthorizationServerEndpoints(
              aadAuthenticationProperties.baseUri, aadAuthenticationProperties.tenantId)
          .jwkSetEndpoint()

  override fun getValidators(): List<OAuth2TokenValidator<Jwt>> =
      aadResourceServerConfiguration.createDefaultValidator()
}
