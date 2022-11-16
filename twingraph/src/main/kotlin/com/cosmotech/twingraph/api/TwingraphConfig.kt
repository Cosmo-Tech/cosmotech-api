// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.twingraph.api

import com.redislabs.redisgraph.impl.api.RedisGraph
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import redis.clients.jedis.Jedis
import redis.clients.jedis.exceptions.JedisAccessControlException

@Configuration
class TwingraphConfig {

  val logger: Logger = LoggerFactory.getLogger(TwingraphConfig::class.java)

  @Bean
  fun jedis(): Jedis? {
    val properties = twingraphProperties()
    return try {
      val jedis = Jedis(properties.host, properties.port)
      jedis.auth(properties.password)
      jedis
    } catch (e: JedisAccessControlException) {
      logger.warn(
          "Twingraph redis cannot be accessed on ${properties.host}:${properties.port} : ${e.message}")
      null
    }
  }

  @Bean
  fun redisGraph(): RedisGraph {
    return RedisGraph(jedis())
  }

  @Bean
  fun twingraphProperties(): TwinGraphProperties {
    return TwinGraphProperties()
  }
}

@ConfigurationProperties(prefix = "twin-graph")
data class TwinGraphProperties(
    var host: String = "localhost",
    var port: Int = 6379,
    var password: String = ""
)
