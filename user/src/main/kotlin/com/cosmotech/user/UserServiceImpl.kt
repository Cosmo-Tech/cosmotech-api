// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.user

import com.cosmotech.api.AbstractPhoenixService
import com.cosmotech.user.api.UserApiService
import com.cosmotech.user.domain.User
import com.cosmotech.user.domain.UserDetails
import org.springframework.stereotype.Service

@Service
class UserServiceImpl : AbstractPhoenixService(), UserApiService {
  override fun authorizeUser(): Unit {
    TODO("Not yet implemented")
  }

  override fun findAllUsers(): List<User> {
    TODO("Not yet implemented")
  }

  override fun findUserById(userId: kotlin.String): UserDetails {
    TODO("Not yet implemented")
  }

  override fun getCurrentUser(): UserDetails {
    TODO("Not yet implemented")
  }

  override fun getOrganizationCurrentUser(organizationId: kotlin.String): UserDetails {
    TODO("Not yet implemented")
  }

  override fun getWorkspaceCurrentUser(
      organizationId: kotlin.String,
      workspaceId: kotlin.String
  ): UserDetails {
    TODO("Not yet implemented")
  }

  override fun registerUser(user: User): User {
    TODO("Not yet implemented")
  }

  override fun unregisterUser(userId: kotlin.String): User {
    TODO("Not yet implemented")
  }

  override fun updateUser(userId: kotlin.String, user: User): User {
    TODO("Not yet implemented")
  }
}
