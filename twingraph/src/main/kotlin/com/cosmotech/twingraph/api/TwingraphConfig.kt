// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.twingraph.api

import com.cosmotech.api.config.CsmPlatformProperties
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.UnifiedJedis
import redis.clients.jedis.providers.PooledConnectionProvider

@Configuration
class TwingraphConfig(val csmPlatformProperties: CsmPlatformProperties) {

  val logger: Logger = LoggerFactory.getLogger(TwingraphConfig::class.java)

  @Bean
  fun jedis(): UnifiedJedis {
    val properties = csmPlatformProperties.twincache
    val config = HostAndPort(properties?.host, properties?.port?.toInt() ?: 6379)
    val clientConfig = DefaultJedisClientConfig.builder()
      .password(properties?.password)
      .build()
    return UnifiedJedis(PooledConnectionProvider(config, clientConfig))
  }
}