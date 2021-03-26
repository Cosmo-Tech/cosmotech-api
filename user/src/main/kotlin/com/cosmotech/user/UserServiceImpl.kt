package com.cosmotech.user

import com.cosmotech.api.AbstractPhoenixService
import com.cosmotech.user.api.UsersApiService
import com.cosmotech.user.domain.User
import org.springframework.stereotype.Service

@Service
class UserServiceImpl: AbstractPhoenixService(), UsersApiService {
    override fun findAll(): List<User> {
        TODO("Not yet implemented")
    }

    override fun findById(userId: String): User {
        TODO("Not yet implemented")
    }

    override fun register(user: User): User {
        TODO("Not yet implemented")
    }

    override fun unregister(userId: String): User {
        TODO("Not yet implemented")
    }

    override fun update(userId: String, user: User): User {
        TODO("Not yet implemented")
    }
}