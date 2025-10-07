// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.part.services

import com.cosmotech.common.config.CsmPlatformProperties
import com.cosmotech.common.config.PostgresConfiguration
import com.cosmotech.dataset.domain.DatasetPart
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.sql.SQLException
import kotlin.use
import org.postgresql.copy.CopyManager
import org.postgresql.core.BaseConnection
import org.slf4j.LoggerFactory
import org.springframework.core.io.ByteArrayResource
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
class RelationalDatasetPartManagementService(
    val postgresConfiguration: PostgresConfiguration,
    val csmPlatformProperties: CsmPlatformProperties
) : DatasetPartManagementService {

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

    val connection = postgresConfiguration.writerJdbcTemplate().dataSource!!.connection
    try {
      connection.autoCommit = false
      val copyManager = CopyManager(connection as BaseConnection)
      // truncate table if overwrite is true
      if (overwrite) {
        connection.createStatement().use { it.execute("DROP TABLE IF EXISTS ${datasetPart.id}") }
      }
      copyManager.copyIn(
          "COPY ${datasetPart.id} FROM STDIN WITH FORMAT CSV, HEADER TRUE", inputStream)
      connection.commit()
    } catch (ex: SQLException) {
      connection.rollback()
      logger.error("Transaction Failed and roll back was performed.")
      throw ex
    } finally {
      connection.close()
    }
  }

  override fun getData(datasetPart: DatasetPart): Resource {
    logger.debug("RelationalDatasetPartManagementService#getData")
    val out = ByteArrayOutputStream()
    val connection = postgresConfiguration.readerJdbcTemplate().dataSource!!.connection

    val copyManager = CopyManager(connection as BaseConnection)
    // Use CopyManager to copy data from the table to the output stream in CSV format
    copyManager.copyOut("COPY ${datasetPart.id} TO STDOUT WITH FORMAT CSV, HEADER TRUE", out)
    return ByteArrayResource(out.toByteArray())
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
        .writerJdbcTemplate()
        .queryForObject(sql, Boolean::class.java, tableName) ?: false
  }
}
