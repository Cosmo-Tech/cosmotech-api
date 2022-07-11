// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories

@Configuration
@EnableRedisRepositories
class RedisConfig {

  @Bean
  fun connectionFactory(): RedisConnectionFactory? {
    return JedisConnectionFactory()
  }

  @Bean
  fun redisTemplate(redisConnectionFactory: RedisConnectionFactory?): RedisTemplate<*, *>? {
    val template = RedisTemplate<ByteArray, ByteArray>()
    template.setConnectionFactory(redisConnectionFactory!!)
    return template
  }
}
