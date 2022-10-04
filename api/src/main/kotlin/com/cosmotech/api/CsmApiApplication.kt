// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api

import com.redis.om.spring.annotations.EnableRedisDocumentRepositories
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate

@SpringBootApplication
@EnableRedisDocumentRepositories("com.cosmotech")
@ComponentScan(
    basePackages = ["com.cosmotech"],
    excludeFilters =
        [
            ComponentScan.Filter(
                type = FilterType.REGEX, pattern = ["com\\.cosmotech\\.\\w+\\.Application"])])
class CsmApiApplication

@Bean
fun redisTemplate(connectionFactory: RedisConnectionFactory?): RedisTemplate<*, *>? {
  val template: RedisTemplate<*, *> = RedisTemplate<Any, Any>()
  template.setConnectionFactory(connectionFactory!!)
  return template
}

fun main(args: Array<String>) {
  runApplication<CsmApiApplication>(args = args)
}
