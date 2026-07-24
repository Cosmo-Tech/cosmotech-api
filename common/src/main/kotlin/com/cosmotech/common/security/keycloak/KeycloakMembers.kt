// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.security.keycloak

data class KeycloakMemberUser(
    val id: String,
    val role: String,
)

data class KeycloakMemberGroup(
    val id: String,
    val role: String,
    val users: List<String>,
)

data class KeycloakMembers(
    val users: List<KeycloakMemberUser>,
    val groups: List<KeycloakMemberGroup>,
)
