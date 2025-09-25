// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.rbac

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.security.ROLE_PLATFORM_ADMIN
import com.cosmotech.api.utils.getCurrentAuthenticatedRoles
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class CsmAdmin(val csmPlatformProperties: CsmPlatformProperties) {
  private val logger: Logger = LoggerFactory.getLogger(this::class.java)

  fun verifyRolesAdmin(roles: List<String>): Boolean {
    logger.debug("Verifying if token roles contains Platform Admin")
    val customAdminGroup = csmPlatformProperties.identityProvider.adminGroup
    if (customAdminGroup.isNullOrBlank()) {
      return roles.contains(ROLE_PLATFORM_ADMIN)
    }
    return roles.any { it == ROLE_PLATFORM_ADMIN || it == customAdminGroup }
  }

  fun verifyCurrentRolesAdmin(): Boolean {
    return this.verifyRolesAdmin(getCurrentAuthenticatedRoles(this.csmPlatformProperties))
  }
}
