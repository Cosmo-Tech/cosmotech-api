// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.user.azure.repositories

import com.cosmotech.user.domain.User
import com.redis.om.spring.repository.RedisDocumentRepository
import org.springframework.stereotype.Repository

@Repository interface UserRepository : RedisDocumentRepository<User, String>
