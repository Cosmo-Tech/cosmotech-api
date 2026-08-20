// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.security.keycloak

object KeycloakConstants {
  const val MAX_PAGE_SIZE = 100
  const val OFFSET_START = 0

  // filter set on get groups (used in KeycloakClient.getGroupsHierarchy)
  const val FILTER_ATTRIBUTE = "public"
  const val FILTER_VALUE = "true"

  // key of the map returned by count groups function (used in KeycloakClient.getGroupsHierarchy)
  const val GROUP_COUNT_MAP_KEY = "count"
  const val GROUP_SEARCH_EXP = "*"
}
