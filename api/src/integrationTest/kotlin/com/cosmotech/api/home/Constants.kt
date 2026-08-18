// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.home

object Constants {

  const val PLATFORM_ADMIN_EMAIL = "user.admin@test.com"
  const val ORGANIZATION_USER_EMAIL = "user.org@test.com"
  const val PLATFORM_ADMIN_API_KEY_VALUE = "PlatformAdminApiTestKeyValue"
  const val ORGANIZATION_USER_API_KEY_VALUE = "OrganizationUserApiTestKeyValue"

  /**
   * Public Keycloak group declared in the test realm
   * (common/src/main/resources/test-cosmotech.json) with the attribute `public=true`. Only public
   * groups are returned by `KeycloakClient.listRBACMembers`.
   */
  const val PUBLIC_GROUP_NAME = "test-public-group"

  /** Keycloak group declared in the test realm *without* the `public=true` attribute. */
  const val PRIVATE_GROUP_NAME = "test-private-group"

  /** Identity that does not exist in the Keycloak test realm. */
  const val UNKNOWN_IDENTITY = "unknown.user@test.com"
}
