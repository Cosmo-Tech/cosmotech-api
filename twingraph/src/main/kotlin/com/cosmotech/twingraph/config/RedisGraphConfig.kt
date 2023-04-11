// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.twingraph.config

import com.redislabs.redisgraph.impl.api.RedisGraph
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import redis.clients.jedis.JedisPool

@Configuration
class RedisGraphConfig {
  @Bean
  fun csmRedisGraph(csmJedisPool: JedisPool): RedisGraph {
    return RedisGraph(csmJedisPool)
  }
}
