// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.twingraph.api

import com.cosmotech.api.config.CsmPlatformProperties
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig

@Configuration
class TwingraphConfig {

  val logger: Logger = LoggerFactory.getLogger(TwingraphConfig::class.java)

  @Bean
  @Suppress("MagicNumber")
  fun csmJedisPool(csmPlatformProperties: CsmPlatformProperties): JedisPool {
    val twincacheProperties = csmPlatformProperties.twincache!!
    val password = twincacheProperties.password
    val host = twincacheProperties.host
    val port = twincacheProperties.port.toInt()
    val timeout = 10000
    val poolConfig = JedisPoolConfig()
    logger.info(
        "Starting Redis with Host:{}, Port:{}, Timeout(ms):{}, PoolConfig:{}",
        host,
        port,
        timeout,
        poolConfig)
    return JedisPool(poolConfig, host, port, timeout, password)
  }
}
