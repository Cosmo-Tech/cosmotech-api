// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.twingraph.api

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.data.redis.RedisProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig

@Configuration
class TwingraphConfig {

  val logger: Logger = LoggerFactory.getLogger(TwingraphConfig::class.java)

  @Bean
  fun csmJedisPool(redisProperties: RedisProperties): JedisPool {
    val password = redisProperties.password ?: ""
    val timeout = redisProperties.timeout.toMillis().toInt()
    val poolConfig = JedisPoolConfig()
    logger.info(
        "Starting Redis with Host:{}, Port:{}, Timeout(ms):{}, PoolConfig:{}",
        redisProperties.host,
        redisProperties.port,
        timeout,
        poolConfig)
    return JedisPool(poolConfig, redisProperties.host, redisProperties.port, timeout, password)
  }
}
