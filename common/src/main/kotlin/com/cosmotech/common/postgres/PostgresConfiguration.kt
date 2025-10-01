// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.postgres

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource

@Configuration
class PostgresConfiguration {
  @Value("\${csm.platform.internalResultServices.storage.admin.username}")
  private lateinit var adminStorageUsername: String
  @Value("\${csm.platform.internalResultServices.storage.admin.password}")
  private lateinit var adminStoragePassword: String
  @Value("\${csm.platform.internalResultServices.storage.reader.username}")
  private lateinit var readerStorageUsername: String
  @Value("\${csm.platform.internalResultServices.storage.reader.password}")
  private lateinit var readerStoragePassword: String
  @Value("\${csm.platform.internalResultServices.storage.writer.username}")
  private lateinit var writerStorageUsername: String
  @Value("\${csm.platform.internalResultServices.storage.writer.password}")
  private lateinit var writerStoragePassword: String
  @Value("\${csm.platform.internalResultServices.storage.host}") private lateinit var host: String
  @Value("\${csm.platform.internalResultServices.storage.port}") private lateinit var port: String
  @Value("\${csm.platform.internalResultServices.storage.datasets.name:cosmotech}")
  private lateinit var dbName: String

  private val jdbcdriverClass = "org.postgresql.Driver"

  @Bean
  fun adminJdbcTemplate(): JdbcTemplate {
    val dataSource =
        DriverManagerDataSource(
            "jdbc:postgresql://$host:$port/$dbName", adminStorageUsername, adminStoragePassword)
    dataSource.setDriverClassName(jdbcdriverClass)
    return JdbcTemplate(dataSource)
  }

  @Bean
  fun readerJdbcTemplate(): JdbcTemplate {
    val dataSource =
        DriverManagerDataSource(
            "jdbc:postgresql://$host:$port/$dbName", readerStorageUsername, readerStoragePassword)
    dataSource.setDriverClassName(jdbcdriverClass)
    return JdbcTemplate(dataSource)
  }

  @Bean
  fun writerJdbcTemplate(): JdbcTemplate {
    val dataSource =
        DriverManagerDataSource(
            "jdbc:postgresql://$host:$port/$dbName", writerStorageUsername, writerStoragePassword)
    dataSource.setDriverClassName(jdbcdriverClass)
    return JdbcTemplate(dataSource)
  }
}
