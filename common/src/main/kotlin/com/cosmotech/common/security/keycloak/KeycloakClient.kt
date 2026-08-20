// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.security.keycloak

import com.cosmotech.common.config.CsmPlatformProperties
import com.cosmotech.common.exceptions.CsmResourceNotFoundException
import com.cosmotech.common.rbac.model.RbacAccessControl
import com.cosmotech.common.security.ROLE_ORGANIZATION_USER
import com.cosmotech.common.security.ROLE_ORGANIZATION_VIEWER
import com.cosmotech.common.security.ROLE_PLATFORM_ADMIN
import com.cosmotech.common.security.keycloak.KeycloakConstants.FILTER_ATTRIBUTE
import com.cosmotech.common.security.keycloak.KeycloakConstants.FILTER_VALUE
import com.cosmotech.common.security.keycloak.KeycloakConstants.GROUP_COUNT_MAP_KEY
import com.cosmotech.common.security.keycloak.KeycloakConstants.GROUP_SEARCH_EXP
import com.cosmotech.common.security.keycloak.KeycloakConstants.MAX_PAGE_SIZE
import com.cosmotech.common.security.keycloak.KeycloakConstants.OFFSET_START
import org.keycloak.admin.client.Keycloak
import org.keycloak.representations.idm.GroupRepresentation
import org.keycloak.representations.idm.RoleRepresentation
import org.keycloak.representations.idm.UserRepresentation
import org.springframework.stereotype.Component

@Component
class KeycloakClient(
    private val csmPlatformProperties: CsmPlatformProperties,
    val serverUrl: String = csmPlatformProperties.identityProvider.serverBaseUrl,
    val realm: String = csmPlatformProperties.identityProvider.identity.tenantId,
    val adminClientId: String = csmPlatformProperties.identityProvider.admin.clientId,
    val username: String = csmPlatformProperties.identityProvider.admin.username,
    val password: String = csmPlatformProperties.identityProvider.admin.password,
) {

  fun getKeycloakInstance(): Keycloak {
    return Keycloak.getInstance(
        serverUrl,
        realm,
        username,
        password,
        adminClientId,
    )
  }

  private fun isGroupPublic(group: GroupRepresentation): Boolean {
    return group.attributes != null &&
        group.attributes.containsKey(FILTER_ATTRIBUTE) &&
        group.attributes[FILTER_ATTRIBUTE]?.contains(FILTER_VALUE) == true
  }

  private fun getGroupsHierarchy(): List<GroupRepresentation> {
    val keycloak = getKeycloakInstance()
    val realmResource = keycloak.realm(realm)

    val groupCount: Long = realmResource.groups().count(true)[GROUP_COUNT_MAP_KEY]!!

    val groups = mutableListOf<GroupRepresentation>()
    var offset = OFFSET_START
    while (offset < groupCount) {
      groups.addAll(realmResource.groups().groups(GROUP_SEARCH_EXP, offset, MAX_PAGE_SIZE, false))
      offset += MAX_PAGE_SIZE
    }
    return groups
  }

  fun getAllGroups(): List<GroupRepresentation> {
    val groups = getGroupsHierarchy()
    return groups.filter { isGroupPublic(it) }
  }

  fun getUsersInGroup(groupId: String): List<UserRepresentation> {
    val keycloak = getKeycloakInstance()
    val realmResource = keycloak.realm(realm)
    val group = realmResource.groups().group(groupId)
    if (!isGroupPublic(group.toRepresentation())) {
      throw CsmResourceNotFoundException("Group with id $groupId does not exist")
    }
    return group.members()
  }

  fun getUserRoles(userId: String): List<RoleRepresentation> {
    val keycloak = getKeycloakInstance()
    val realmResource = keycloak.realm(realm)
    val userResource = realmResource.users().get(userId)
    return userResource.roles().realmLevel().listAll()
  }

  fun getAllUsers(): List<UserRepresentation> {
    val keycloak = getKeycloakInstance()
    val realmResource = keycloak.realm(realm)
    return realmResource.users().list()
  }

  fun listRBACMembers(rbac: List<RbacAccessControl>): KeycloakMembers {
    val rbacById = rbac.associateBy { it.id }
    val groups = getAllGroups().filter { rbacById.containsKey(it.name) }
    val users = getAllUsers().filter { rbacById.containsKey(it.username) }
    return KeycloakMembers(
        users =
            users.map { user ->
              KeycloakMemberUser(
                  id = user.username,
                  role = rbacById[user.username]!!.role,
              )
            },
        groups =
            groups.map { group ->
              KeycloakMemberGroup(
                  id = group.name,
                  role = rbacById[group.name]!!.role,
                  users = getUsersInGroup(group.id).map { it.username }.distinct(),
              )
            },
    )
  }

  fun listKeycloakMembers(): KeycloakMembers {
    val groups = getAllGroups()
    val users = getAllUsers()
    return KeycloakMembers(
        users =
            users.map { user ->
              KeycloakMemberUser(
                  id = user.username,
                  role = extractKeycloakRole(getUserRoles(user.id).map { it.name }),
              )
            },
        groups =
            groups.map { group ->
              KeycloakMemberGroup(
                  id = group.name,
                  role = extractKeycloakRole(group.realmRoles),
                  users = getUsersInGroup(group.id).map { it.username }.distinct(),
              )
            },
    )
  }

  private fun extractKeycloakRole(roles: List<String>?): String {
    roles ?: return ""
    return when {
      roles.contains(ROLE_PLATFORM_ADMIN) -> ROLE_PLATFORM_ADMIN
      roles.contains(ROLE_ORGANIZATION_USER) -> ROLE_ORGANIZATION_USER
      roles.contains(ROLE_ORGANIZATION_VIEWER) -> ROLE_ORGANIZATION_VIEWER
      else -> ""
    }
  }
}
