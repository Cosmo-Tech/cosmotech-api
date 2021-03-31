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
                type = FilterType.ASSIGNABLE_TYPE,
                classes =
                    [
                        com.cosmotech.dataset.Application::class,
                        com.cosmotech.connector.Application::class,
                        com.cosmotech.organization.Application::class,
                        com.cosmotech.user.Application::class]),
            ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes =
                    [
                        com.cosmotech.dataset.api.DefaultExceptionHandler::class,
                        com.cosmotech.connector.api.DefaultExceptionHandler::class,
                        com.cosmotech.organization.api.DefaultExceptionHandler::class,
                        com.cosmotech.user.api.DefaultExceptionHandler::class])])
class CsmApiApplication

// TODO Add Controller Advice for all sub-projects exceptions

fun main(args: Array<String>) {
  runApplication<CsmApiApplication>(*args)
}
