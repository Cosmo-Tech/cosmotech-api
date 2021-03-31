// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.

package com.cosmotech.user

import com.cosmotech.api.AbstractPhoenixService
import com.cosmotech.user.api.UsersApiService
import com.cosmotech.user.domain.User
import com.cosmotech.user.domain.UserDetails
import org.springframework.stereotype.Service

@Service
class UserServiceImpl : AbstractPhoenixService(), UsersApiService {
  override fun findAllUsers(): List<UserDetails> {
    TODO("Not yet implemented")
  }

  override fun findUserById(userId: String): UserDetails {
    TODO("Not yet implemented")
  }

  override fun registerUser(user: User): UserDetails {
    TODO("Not yet implemented")
  }

  override fun unregisterUser(userId: String): UserDetails {
    TODO("Not yet implemented")
  }

  override fun updateUser(userId: String, user: User): UserDetails {
    TODO("Not yet implemented")
  }
}
