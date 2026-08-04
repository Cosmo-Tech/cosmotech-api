// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.iaminfo.service

import com.cosmotech.common.CsmPhoenixService
import com.cosmotech.common.exceptions.CsmAccessForbiddenException
import com.cosmotech.common.rbac.CsmAdmin
import com.cosmotech.common.security.ROLE_PLATFORM_ADMIN
import com.cosmotech.common.security.keycloak.KeycloakClient
import com.cosmotech.common.security.keycloak.KeycloakMemberGroup
import com.cosmotech.common.security.keycloak.KeycloakMemberUser
import com.cosmotech.common.security.keycloak.KeycloakMembers
import com.cosmotech.iaminfo.IAMInfoApiServiceInterface
import com.cosmotech.iaminfo.domain.MemberGroup
import com.cosmotech.iaminfo.domain.MemberUser
import com.cosmotech.iaminfo.domain.Members
import org.springframework.stereotype.Service

@Service
class IAMInfoServiceImpl(
    private val keycloak: KeycloakClient,
    private val csmAdmin: CsmAdmin,
) : CsmPhoenixService(), IAMInfoApiServiceInterface {
  override fun listIAMGroups(): List<String> {
    // open to all users, no permission check needed
    return keycloak.getAllGroups().map { it.name }
  }

  override fun listIAMMembers(): Members {
    // only be accessible by Platform.admin users
    if (!csmAdmin.verifyCurrentRolesAdmin()) {
      throw CsmAccessForbiddenException("User does not have permission $ROLE_PLATFORM_ADMIN")
    }
    return keycloak.listKeycloakMembers().toMembers()
  }

  fun KeycloakMemberUser.toMemberUser() = MemberUser(id = this.id, role = this.role)

  fun KeycloakMemberGroup.toMemberGroup() =
      MemberGroup(
          id = this.id,
          role = this.role,
          users = this.users.toMutableList(),
      )

  fun KeycloakMembers.toMembers() =
      Members(
          users = this.users.map { it.toMemberUser() }.toMutableList(),
          groups = this.groups.map { it.toMemberGroup() }.toMutableList(),
      )
}
