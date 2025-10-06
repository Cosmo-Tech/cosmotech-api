// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource

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

fun JdbcTemplate.existDB(name: String): Boolean {
  return this.queryForList("SELECT * FROM pg_catalog.pg_database WHERE datname='$name'").size == 1
}

fun JdbcTemplate.existTable(name: String): Boolean {
  return this.queryForList(
          "SELECT * FROM information_schema.tables " + "WHERE table_name ilike '${name}'")
      .size >= 1
}

fun String.toDataTableName(isProbeData: Boolean): String =
    (if (isProbeData) "P_$this" else "CD_$this").lowercase()

fun JdbcTemplate.createDB(name: String, comment: String? = null): String {
  this.execute("CREATE DATABASE \"$name\"")
  if (comment != null) this.execute("COMMENT ON DATABASE \"$name\" IS '$comment'")
  return name
}

fun JdbcTemplate.dropDB(name: String) {
  if (this.existDB(name)) this.execute("DROP DATABASE \"$name\"")
}
