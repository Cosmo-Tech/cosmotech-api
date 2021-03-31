// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.

package com.cosmotech.user

import com.cosmotech.api.AbstractPhoenixService
import com.cosmotech.user.api.UsersApiService
import com.cosmotech.user.domain.User
import org.springframework.stereotype.Service

@Service
class UserServiceImpl : AbstractPhoenixService(), UsersApiService {
  override fun findAllUsers(): List<User> {
    TODO("Not yet implemented")
  }

  override fun findUserById(userId: String): User {
    TODO("Not yet implemented")
  }

  override fun registerUser(user: User): User {
    TODO("Not yet implemented")
  }

  override fun unregisterUser(userId: String): User {
    TODO("Not yet implemented")
  }

  override fun updateUser(userId: String, user: User): User {
    TODO("Not yet implemented")
  }
}
