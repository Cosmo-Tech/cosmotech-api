// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.part.services

import com.cosmotech.common.config.CsmPlatformProperties
import com.cosmotech.common.config.DATASET_INPUTS_SCHEMA
import com.cosmotech.common.config.existTable
import com.cosmotech.common.utils.sanitizeDatasetPartId
import com.cosmotech.dataset.domain.DatasetPart
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.sql.SQLException
import kotlin.use
import org.apache.commons.lang3.StringUtils
import org.postgresql.copy.CopyManager
import org.postgresql.core.BaseConnection
import org.slf4j.LoggerFactory
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

/**
 * Service implementation for managing dataset parts in a relational database.
 *
 * This service provides methods to manage parts of a dataset stored in a relational database,
 * including saving and deleting dataset part entities.
 */
@Service("DB")
class RelationalDatasetPartManagementService(
    val writerJdbcTemplate: JdbcTemplate,
    val readerJdbcTemplate: JdbcTemplate,
    val csmPlatformProperties: CsmPlatformProperties
) : DatasetPartManagementService {

  private val logger = LoggerFactory.getLogger(RelationalDatasetPartManagementService::class.java)

  override fun storeData(file: MultipartFile, datasetPart: DatasetPart, overwrite: Boolean) {
    storeData(file.inputStream, datasetPart, overwrite)
  }

  override fun storeData(file: Resource, datasetPart: DatasetPart, overwrite: Boolean) {
    storeData(file.inputStream, datasetPart, overwrite)
  }

  @Suppress("NestedBlockDepth")
  fun storeData(inputStream: InputStream, datasetPart: DatasetPart, overwrite: Boolean) {

    val tableExists = writerJdbcTemplate.existTable(datasetPart.id)

    if (tableExists && !overwrite) {
      throw IllegalArgumentException(
          "Table ${datasetPart.id} already exists and overwrite is set to false.")
    }

    inputStream.bufferedReader().use { reader ->
      val headers = validateHeaders(reader)
      val readerUserName = csmPlatformProperties.databases.data.reader.username
      writerJdbcTemplate.dataSource!!.connection.use { connection ->
        try {
          connection.autoCommit = false
          val tableName = "${DATASET_INPUTS_SCHEMA}.${datasetPart.id.sanitizeDatasetPartId()}"
          if (overwrite) {
            val prepareStatement = connection.prepareStatement("DROP TABLE IF EXISTS $tableName")
            prepareStatement.execute()
          }

          if (!tableExists) {
            val prepareStatement =
                connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS $tableName ${constructSQLColumnsValues(headers)};" +
                        "GRANT SELECT ON $tableName to \"$readerUserName\";")
            prepareStatement.execute()
          }
          val insertedRows =
              CopyManager(connection as BaseConnection)
                  .copyIn("COPY $tableName FROM STDIN WITH CSV", reader)
          logger.info("Inserted $insertedRows rows into table $tableName")

          connection.commit()
        } catch (ex: SQLException) {
          connection.rollback()
          logger.error("Transaction Failed and roll back was performed.")
          throw ex
        }
      }
    }
  }

  private fun validateHeaders(reader: BufferedReader): List<String> {
    val headers = reader.readLine()?.split(",", "\n") ?: emptyList()

    require(headers.isNotEmpty()) { "No headers found in dataset part file" }
    require(headers.all { it.isNotBlank() }) { "Empty headers found in dataset part file" }
    require(headers.map { it.lowercase().trim() }.distinct().size == headers.size) {
      "Duplicate headers found in dataset part file"
    }
    require(headers.all { Regex("[a-zA-Z0-9_\"\' ]+").matches(it) }) {
      "Invalid header name found in dataset part file: header name must match [a-zA-Z0-9_\"\' ]+ (found: ${headers})"
    }

    return headers
  }

  private fun constructSQLColumnsValues(headers: List<String>): String =
      headers
          .map { it.trim() }
          .joinToString(separator = " TEXT, ", prefix = "(", postfix = " TEXT)") {
            StringUtils.wrapIfMissing(it, "\"")
          }

  override fun getData(datasetPart: DatasetPart): Resource {
    val tableName = "${DATASET_INPUTS_SCHEMA}.${datasetPart.id.sanitizeDatasetPartId()}"
    val out = ByteArrayOutputStream()
    readerJdbcTemplate.dataSource!!.connection.use { connection ->
      CopyManager(connection as BaseConnection)
          .copyOut("COPY $tableName TO STDOUT WITH CSV HEADER", out)
    }
    return ByteArrayResource(out.toByteArray())
  }

  override fun delete(datasetPart: DatasetPart) {
    val tableName = "${DATASET_INPUTS_SCHEMA}.${datasetPart.id.sanitizeDatasetPartId()}"
    writerJdbcTemplate.dataSource!!.connection.use { connection ->
      try {
        connection.autoCommit = false
        connection.createStatement().use { statement ->
          statement.execute("DROP TABLE $tableName;")
        }
        connection.commit()
      } catch (ex: SQLException) {
        connection.rollback()
        logger.error("Transaction Failed and roll back was performed.")
        throw ex
      }
    }
  }
}
