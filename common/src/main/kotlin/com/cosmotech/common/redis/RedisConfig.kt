// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.redis

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ssl.SslBundles
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.JedisClientConfig
import redis.clients.jedis.Protocol
import redis.clients.jedis.UnifiedJedis

@Configuration
open class RedisConfig {

  @Value("\${spring.data.redis.host}") private lateinit var twincacheHost: String

  @Value("\${spring.data.redis.port}") private lateinit var twincachePort: String

  // This property path is not compatible with spring.data.redis used by redis-om auto configuration
  @Value("\${csm.platform.twincache.tls.enabled}") private val tlsEnabled: Boolean = false

  // This property path is not compatible with spring.data.redis used by redis-om auto configuration
  // It is duplicated since spring.data.redis.ssl.bundle cannot be empty
  @Value("\${csm.platform.twincache.tls.bundle}") private var tlsBundle: String = ""

  @Value("\${spring.data.redis.password}") private lateinit var twincachePassword: String

  @Bean
  open fun csmJedisClientConfig(sslBundles: SslBundles): JedisClientConfig {
    return if (tlsEnabled && tlsBundle.isNotBlank()) {
      DefaultJedisClientConfig.builder()
          .ssl(tlsEnabled)
          .sslSocketFactory(sslBundles.getBundle(tlsBundle).createSslContext().socketFactory)
          .password(twincachePassword)
          .timeoutMillis(Protocol.DEFAULT_TIMEOUT)
          .build()
    } else {
      DefaultJedisClientConfig.builder()
          .ssl(tlsEnabled)
          .password(twincachePassword)
          .timeoutMillis(Protocol.DEFAULT_TIMEOUT)
          .build()
    }
  }

  @Bean
  open fun unifiedJedis(csmJedisClientConfig: JedisClientConfig): UnifiedJedis {
    return UnifiedJedis(HostAndPort(twincacheHost, twincachePort.toInt()), csmJedisClientConfig)
  }
}
