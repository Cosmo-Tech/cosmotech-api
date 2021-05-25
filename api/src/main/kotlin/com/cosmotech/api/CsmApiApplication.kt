// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer

@EnableResourceServer
@SpringBootApplication
@ComponentScan(
    basePackages = ["com.cosmotech"],
    excludeFilters =
        [
            ComponentScan.Filter(
                type = FilterType.REGEX, pattern = ["com\\.cosmotech\\.\\w+\\.Application"])])
class CsmApiApplication

fun main(args: Array<String>) {
  runApplication<CsmApiApplication>(*args)
}
