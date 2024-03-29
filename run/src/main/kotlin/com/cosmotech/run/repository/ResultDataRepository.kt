// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.run.repository

import com.cosmotech.run.domain.ResultData
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.sql.ResultSet

@Repository interface ResultDataRepository : JpaRepository<ResultData, String> {

  @Query("SELECT count(*) FROM information_schema.tables "
                + "WHERE table_name = custom_table_name LIMIT 1;")
  fun checkIfTableExist(@Param("custom_table_name") tableName: String): ResultSet

  @Query("SELECT * FROM custom_table_name")
  fun getDataFromTable(@Param("custom_table_name") tableName: String): ResultSet

  @Query("CREATE TABLE custom_table_name schema")
  fun createTable(@Param("custom_table_name") tableName: String, @Param("schema") tableSchema: String)
}
