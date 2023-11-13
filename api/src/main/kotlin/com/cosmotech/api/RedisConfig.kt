// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api

import com.cosmotech.api.config.CsmPlatformProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisPassword
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.JedisClientConfig
import redis.clients.jedis.Protocol.DEFAULT_TIMEOUT
import redis.clients.jedis.UnifiedJedis

@Configuration
class RedisConfig {

  @Bean
  fun redisStandaloneConfiguration(
      csmPlatformProperties: CsmPlatformProperties
  ): RedisStandaloneConfiguration {
    return RedisStandaloneConfiguration().apply {
      hostName = csmPlatformProperties.twincache.host
      port = csmPlatformProperties.twincache.port.toInt()
      password = RedisPassword.of(csmPlatformProperties.twincache.password)
    }
  }

  @Bean
  fun jedisConnectionFactory(
      redisStandaloneConfiguration: RedisStandaloneConfiguration
  ): JedisConnectionFactory {
    return JedisConnectionFactory(redisStandaloneConfiguration)
  }

  @Bean
  fun csmJedisClientConfig(csmPlatformProperties: CsmPlatformProperties): JedisClientConfig =
      DefaultJedisClientConfig.builder()
          .password(csmPlatformProperties.twincache.password)
          .timeoutMillis(DEFAULT_TIMEOUT)
          .build()

  @Bean
  fun unifiedJedis(
      csmPlatformProperties: CsmPlatformProperties,
      csmJedisClientConfig: JedisClientConfig
  ): UnifiedJedis {
    return UnifiedJedis(
        HostAndPort(
            csmPlatformProperties.twincache.host, csmPlatformProperties.twincache.port.toInt()),
        csmJedisClientConfig)
  }
}
