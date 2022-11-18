// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.twingraph.api

import com.redislabs.redisgraph.impl.api.RedisGraph
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.exceptions.JedisAccessControlException

@Configuration
class TwingraphConfig {

  val logger: Logger = LoggerFactory.getLogger(TwingraphConfig::class.java)

  @Bean
  fun jedisPool(): JedisPool? {
    val properties = twingraphProperties()
    return try {
      JedisPool(
          JedisPoolConfig(),
          properties.host,
          properties.port,
          properties.timeout,
          properties.password)
    } catch (e: JedisAccessControlException) {
      logger.warn(
          "Twingraph redis cannot be accessed on ${properties.host}:${properties.port} : ${e.message}")
      null
    }
  }

  @Bean
  fun redisGraph(): RedisGraph {
    return RedisGraph(jedisPool())
  }

  @Bean
  fun twingraphProperties(): TwinGraphProperties {
    return TwinGraphProperties()
  }
}

@ConfigurationProperties(prefix = "csm.platform.twin-graph")
data class TwinGraphProperties(
    var host: String = "localhost",
    var port: Int = 6379,
    var password: String = "",
    var timeout: Int = 60
)
