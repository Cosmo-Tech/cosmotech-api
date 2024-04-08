// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run.config

import org.json.JSONObject
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import javax.sql.DataSource

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

fun JdbcTemplate.createDB(name: String): String {
  this.execute("CREATE DATABASE \"$name\"")
  return name
}

fun JdbcTemplate.createCustomDataTable(tableName: String): String {
  this.execute("CREATE TABLE \"${tableName.toCustomDataTableName()}\" (custom_data jsonb)")
  return tableName.toCustomDataTableName()
}

fun JdbcTemplate.insertCustomData(
    tableName: String,
    data: List<Map<String, String>>
): List<Map<String, String>> {
  data.forEach { dataLine ->
    this.execute(
        "INSERT INTO \"${tableName.toCustomDataTableName()}\" (custom_data) VALUES ('${JSONObject(dataLine)}')")
  }
  return data
}

fun JdbcTemplate.existTable(name: String): Boolean {
  val query = "select count(*) from information_schema.tables where table_name = ?"
  return this.queryForObject(query, Int::class.java, name.toCustomDataTableName()) == 1
}

fun String.toCustomDataTableName(): String = "CD_$this"
