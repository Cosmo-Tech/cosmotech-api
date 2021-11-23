// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.security

import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt

interface CsmSecurityValidator {

  /** Return list of tenants allowed to use this API. */
  fun getAllowedTenants(): Collection<String?> = listOf()

  /** Return the JWKS Validation URI, for checking tokens integrity */
  fun getJwksSetUri(): String

  /**
   * Return additional validators to register besides the one that checks the token issuer is within
   * the list of allowed tenants
   */
  fun getValidators(): Collection<OAuth2TokenValidator<Jwt>> = listOf()
}
