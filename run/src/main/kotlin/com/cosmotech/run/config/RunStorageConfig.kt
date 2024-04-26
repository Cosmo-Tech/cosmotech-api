// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run.config

import javax.sql.DataSource
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource

@Configuration
class RunStorageConfig {

  @Value("\${csm.platform.internalResultServices.storage.admin.username}")
  private lateinit var adminStorageUsername: String

  @Value("\${csm.platform.internalResultServices.storage.admin.password}")
  private lateinit var adminStoragePassword: String

  @Value("\${csm.platform.internalResultServices.storage.host}") private lateinit var host: String
  @Value("\${csm.platform.internalResultServices.storage.port}") private lateinit var port: String

  private val jdbcdriverClass = "org.postgresql.Driver"

  @Bean
  fun adminRunStorageDatasource(): DriverManagerDataSource {
    val dataSource =
        DriverManagerDataSource(
            "jdbc:postgresql://$host:$port/postgres", adminStorageUsername, adminStoragePassword)
    dataSource.setDriverClassName(jdbcdriverClass)
    return dataSource
  }

  @Bean
  fun adminRunStorageTemplate(
      @Qualifier("adminRunStorageDatasource") dataSource: DataSource
  ): JdbcTemplate {
    return JdbcTemplate(dataSource)
  }
}

fun JdbcTemplate.existDB(name: String): Boolean {
  return this.queryForList("SELECT * FROM pg_catalog.pg_database WHERE datname='$name'").size == 1
}

fun JdbcTemplate.existTable(name: String): Boolean {
  return this.queryForList(
          "SELECT * FROM information_schema.tables " + "WHERE table_name='${name}'")
      .size == 1
}

fun String.toDataTableName(isProbeData: Boolean): String =
    if (isProbeData) "P_$this" else "CD_$this"

fun JdbcTemplate.createDB(name: String, comment: String? = null): String {
  this.execute("CREATE DATABASE \"$name\"")
  if (comment != null)
    this.execute("COMMENT ON DATABASE \"$name\" IS '$comment'")
  return name
}

fun JdbcTemplate.dropDB(name: String) {
  if (this.existDB(name)) this.execute("DROP DATABASE \"$name\"")
}
