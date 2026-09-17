// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api

import com.redis.om.spring.annotations.EnableRedisDocumentRepositories
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EnableRedisDocumentRepositories(basePackages = ["com.cosmotech"])
@ComponentScan(
    basePackages = ["com.cosmotech"],
    excludeFilters =
        [
            ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = ["com\\.cosmotech\\.\\w+\\.Application"],
            )
        ],
)
class CsmApiApplication

fun main(args: Array<String>) {
  runApplication<CsmApiApplication>(args = args)
}
