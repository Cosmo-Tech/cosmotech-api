// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.part.services

import com.cosmotech.common.postgres.PostgresConfiguration
import com.cosmotech.dataset.domain.DatasetPart
import java.io.InputStream
import kotlin.use
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.slf4j.LoggerFactory
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

/**
 * Service implementation for managing dataset parts in a relational database.
 *
 * This service provides methods to manage parts of a dataset stored in a relational database,
 * including saving and deleting dataset part entities.
 */
@Service("Relational")
class RelationalDatasetPartManagementService(val postgresConfiguration: PostgresConfiguration) :
    DatasetPartManagementService {

  private val logger = LoggerFactory.getLogger(RelationalDatasetPartManagementService::class.java)

  override fun storeData(file: MultipartFile, datasetPart: DatasetPart, overwrite: Boolean) {
    storeData(file.inputStream, datasetPart, overwrite)
  }

  override fun storeData(file: Resource, datasetPart: DatasetPart, overwrite: Boolean) {
    storeData(file.inputStream, datasetPart, overwrite)
  }

  fun storeData(inputStream: InputStream, datasetPart: DatasetPart, overwrite: Boolean) {
    logger.debug("RelationalDatasetPartManagementService#storeData")
    if (isTableExists(datasetPart.id) && !overwrite) {
      throw IllegalArgumentException(
          "Table ${datasetPart.id} already exists and overwrite is set to false.")
    }

    // read csv file from input stream
    val csvFormat = CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).get()
    val csvParser = CSVParser.parse(inputStream.bufferedReader(), csvFormat)

    val connection = postgresConfiguration.writerJdbcTemplate().dataSource!!.connection
    try {
      connection.autoCommit = false
      // create table if not exists
      if (!isTableExists(datasetPart.id)) {
        connection.createStatement().use { statement ->
          statement.execute(
              "CREATE TABLE IF NOT EXISTS ${datasetPart.id} ( ${csvParser.headerNames.joinToString { "$it TEXT" }} );")
        }
      }
      // truncate table if overwrite is true
      if (overwrite) {
        connection.createStatement().use { it.execute("TRUNCATE TABLE ${datasetPart.id}") }
      }
      // create sql prepared statement for insert
      val sqlHeader = csvParser.headerNames.joinToString()
      val valuePlaceholders = List(csvParser.headerNames.size) { "?" }.joinToString()
      val insertSql = "INSERT INTO ${datasetPart.id}($sqlHeader) VALUES($valuePlaceholders);"
      connection.prepareStatement(insertSql).use { preparedStatement ->
        csvParser.forEach { record: CSVRecord ->
          csvParser.headerMap.forEach { (header, index) ->
            preparedStatement.setString(index + 1, record.get(header))
          }
          preparedStatement.addBatch()
        }
        preparedStatement.executeBatch()
      }

      connection.commit()
    } catch (ex: Exception) {
      connection.rollback()
      logger.error("Transaction Failed and roll back was performed.")
      ex.printStackTrace()
    } finally {
      connection.close()
    }
  }

  override fun getData(datasetPart: DatasetPart): Resource {
    logger.debug("RelationalDatasetPartManagementService#getData")
    TODO("Not yet implemented")
  }

  override fun delete(datasetPart: DatasetPart) {
    logger.debug("RelationalDatasetPartManagementService#delete")
    val connection = postgresConfiguration.writerJdbcTemplate().dataSource!!.connection
    connection.createStatement().use { statement ->
      statement.execute("DROP TABLE ${datasetPart.id};")
    }
  }

  fun isTableExists(tableName: String): Boolean {
    val datasetSchema: String =
        "datasets" // CsmPlatformProperties.CsmServiceResult.CsmStorage.CsmStorageUser
    val sql =
        """
            SELECT EXISTS (
                SELECT 1
                FROM information_schema.tables
                WHERE table_schema = $datasetSchema AND table_name = ?
            )
        """
            .trimIndent()
    return postgresConfiguration
        .adminJdbcTemplate()
        .queryForObject(sql, Boolean::class.java, tableName) ?: false
  }
}
