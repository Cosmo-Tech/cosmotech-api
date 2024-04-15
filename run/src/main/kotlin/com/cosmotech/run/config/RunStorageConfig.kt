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

  @Value("\${csm.platform.storage.admin.username}")
  private lateinit var adminStorageUsername: String

  @Value("\${csm.platform.storage.admin.password}")
  private lateinit var adminStoragePassword: String

  @Value("\${csm.platform.storage.reader.username}")
  private lateinit var readerStorageUsername: String

  @Value("\${csm.platform.storage.reader.password}")
  private lateinit var readerStoragePassword: String

  @Value("\${csm.platform.storage.host}") private lateinit var storageHost: String

  private val jdbcdriverClass = "org.postgresql.Driver"

  @Bean
  fun adminRunStorageDatasource(): DriverManagerDataSource {
    val dataSource =
        DriverManagerDataSource(storageHost, adminStorageUsername, adminStoragePassword)
    dataSource.setDriverClassName(jdbcdriverClass)
    return dataSource
  }

  @Bean
  fun adminRunStorageTemplate(
      @Qualifier("adminRunStorageDatasource") dataSource: DataSource
  ): JdbcTemplate {
    return JdbcTemplate(dataSource)
  }

  @Bean
  fun readerRunStorageDatasource(): DriverManagerDataSource {
    val dataSource =
        DriverManagerDataSource(storageHost, readerStorageUsername, readerStoragePassword)
    dataSource.setDriverClassName(jdbcdriverClass)
    return dataSource
  }

  @Bean
  fun readerRunStorageTemplate(
      @Qualifier("readerRunStorageDatasource") dataSource: DataSource
  ): JdbcTemplate {
    return JdbcTemplate(dataSource)
  }
}

fun JdbcTemplate.existDB(name: String): Boolean {
  return this.queryForList("SELECT * FROM pg_catalog.pg_database WHERE datname='$name'").size == 1
}

fun JdbcTemplate.existTable(name: String, isProbeData: Boolean): Boolean {
  val query = "select count(*) from information_schema.tables where table_name = ?"
  return this.queryForObject(query, Int::class.java, name.toDataTableName(isProbeData)) == 1
}

fun String.toDataTableName(isProbeData:Boolean): String = if(isProbeData) "P_$this" else "CD_$this"

fun JdbcTemplate.createDB(name: String, readerStorageUsername: String): String {
  this.execute("CREATE DATABASE \"$name\"")
  this.execute("GRANT CONNECT ON DATABASE \"$name\" to $readerStorageUsername")
  return name
}

fun JdbcTemplate.dropDB(name: String) {
  if (this.existDB(name)) this.execute("DROP DATABASE \"$name\"")
}
