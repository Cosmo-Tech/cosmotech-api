package com.cosmotech.run.repository

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import javax.sql.DataSource

@Configuration
class RunJdbcConfig {

    @Value("\${spring.datasource.username}")
    private lateinit var jdbcUserName : String

    @Value("\${spring.datasource.password}")
    private lateinit var jdbcUserPassword : String

    @Value("\${spring.datasource.url}")
    private lateinit var jdbcUrl : String

    @Value("\${spring.datasource.driver-class-name}")
    private lateinit var jdbcdriverClass : String

    @Bean
    fun runDataSource() : DriverManagerDataSource{
        val dataSource = DriverManagerDataSource(jdbcUrl, jdbcUserName, jdbcUserPassword)
        dataSource.setDriverClassName(jdbcdriverClass)
        return dataSource
    }

    @Bean("runJdbcTemplate")
    fun jdbcTemplate(@Qualifier("runDataSource") dataSource: DataSource) : JdbcTemplate{
        return JdbcTemplate(dataSource)
    }

}
