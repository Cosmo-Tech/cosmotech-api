// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource

const val DATASET_INPUTS_SCHEMA = "inputs"

@Configuration
class PostgresConfiguration(val csmPlatformProperties: CsmPlatformProperties) {

  private val jdbcDriverClass = "org.postgresql.Driver"

  private val jdbcUrl =
      "jdbc:postgresql://${csmPlatformProperties.databases.data.host}" +
          ":${csmPlatformProperties.databases.data.port}" +
          "/${csmPlatformProperties.databases.data.database}"

  @Bean
  fun readerJdbcTemplate(): JdbcTemplate {
    val dataSource =
        DriverManagerDataSource(
            jdbcUrl,
            csmPlatformProperties.databases.data.reader.username,
            csmPlatformProperties.databases.data.reader.password)
    dataSource.setDriverClassName(jdbcDriverClass)
    return JdbcTemplate(dataSource)
  }

  @Bean
  fun writerJdbcTemplate(): JdbcTemplate {
    val dataSource =
        DriverManagerDataSource(
            jdbcUrl,
            csmPlatformProperties.databases.data.writer.username,
            csmPlatformProperties.databases.data.writer.password)
    dataSource.setDriverClassName(jdbcDriverClass)
    return JdbcTemplate(dataSource)
  }
}

fun JdbcTemplate.existTable(name: String): Boolean {
  return this.queryForList(
          "SELECT * FROM information_schema.tables " +
              "WHERE " +
              "table_schema = $DATASET_INPUTS_SCHEMA AND " +
              "table_name = '$name'")
      .isNotEmpty()
}
