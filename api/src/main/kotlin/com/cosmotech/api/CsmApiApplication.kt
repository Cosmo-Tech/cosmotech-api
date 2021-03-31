package com.cosmotech.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType

@SpringBootApplication
@ComponentScan(
    basePackages = ["com.cosmotech"],
    excludeFilters =
        [
            ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern =
                    [
                        "com\\.cosmotech\\.\\w+\\.Application",
                        "com\\.cosmotech\\.\\w+\\.api\\.DefaultExceptionHandler"])])
class CsmApiApplication

// TODO Add Controller Advice for all sub-projects exceptions

fun main(args: Array<String>) {
  runApplication<CsmApiApplication>(*args)
}
