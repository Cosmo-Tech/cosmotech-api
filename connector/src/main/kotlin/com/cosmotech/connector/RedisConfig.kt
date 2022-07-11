// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.connector

import com.redis.om.spring.annotations.EnableRedisDocumentRepositories
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory

@Configuration
@EnableRedisDocumentRepositories
class RedisConfig {

    @Bean
    fun jedisConnectionFactory(): JedisConnectionFactory? {
        return JedisConnectionFactory()
    }
}
