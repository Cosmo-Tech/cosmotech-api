package com.cosmotech.common.postgres

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import javax.sql.DataSource

@Configuration
class PostgresConfiguration {
    @Value("\${csm.platform.internalResultServices.storage.admin.username}")
    private lateinit var adminStorageUsername: String
    @Value("\${csm.platform.internalResultServices.storage.admin.password}")
    private lateinit var adminStoragePassword: String
    @Value("\${csm.platform.internalResultServices.storage.host}") private lateinit var host: String
    @Value("\${csm.platform.internalResultServices.storage.port}") private lateinit var port: String
    @Value("\${csm.platform.internalResultServices.storage.db.name}") private lateinit var dbName: String
    @Value("\${csm.platform.internalResultServices.storage.db.schema}") private lateinit var schema: String

    private val jdbcdriverClass = "org.postgresql.Driver"

    @Bean
    fun adminDatasource(): DriverManagerDataSource {
        val dataSource =
            DriverManagerDataSource(
                "jdbc:postgresql://$host:$port/$dbName", adminStorageUsername, adminStoragePassword)
        dataSource.setDriverClassName(jdbcdriverClass)
        return dataSource
    }

    @Bean
    fun adminJdbcTemplate(
        @Qualifier("adminDatasource") dataSource: DataSource
    ): JdbcTemplate {
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
