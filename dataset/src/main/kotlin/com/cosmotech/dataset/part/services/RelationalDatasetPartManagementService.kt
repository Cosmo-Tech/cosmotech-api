// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.part.services

import com.cosmotech.common.config.CsmPlatformProperties
import com.cosmotech.common.config.existTable
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
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.BufferedReader

/**
 * Service implementation for managing dataset parts in a relational database.
 *
 * This service provides methods to manage parts of a dataset stored in a relational database,
 * including saving and deleting dataset part entities.
 */
@Service("Relational")
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
      val values = constructValues(headers)

      writerJdbcTemplate.dataSource!!.connection.use { connection ->
        try {
          connection.autoCommit = false
          val tableName = "inputs.${datasetPart.id.replace('-', '_')}"
          if (overwrite) {
              val prepareStatement = connection.prepareStatement("DROP TABLE IF EXISTS ?")
              prepareStatement.setString(1, tableName)
              prepareStatement.execute()
          }

          if (!tableExists) {
              val prepareStatement = connection.prepareStatement("CREATE TABLE IF NOT EXISTS ? ?")
              prepareStatement.setString(1, tableName)
              prepareStatement.setString(2, values)
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

    private fun validateHeaders(reader: BufferedReader): List<String>{
        val headers = reader
            .readLine()
            .split(",", "\n")

        require(headers.isNotEmpty())
          { "No headers found in dataset part file" }
        require(headers.all { it.isNotBlank() })
          { "Empty headers found in dataset part file" }
        require(headers.distinct().size == headers.size)
          { "Duplicate headers found in dataset part file" }
        require(headers.all { Regex.fromLiteral("[a-zA-Z0-9_]+").matches(it)})
          {"Invalid header name found in dataset part file: header name must match [a-zA-Z0-9_]+"}

        return headers
    }

    private fun constructValues(headers: List<String>): String = headers
        .joinToString(
            separator = " TEXT, ", prefix = "(", postfix = " TEXT)", transform = String::trim
        )

    override fun getData(datasetPart: DatasetPart): Resource {
    val out = ByteArrayOutputStream()
    readerJdbcTemplate.dataSource!!.connection.use { connection ->
      CopyManager(connection as BaseConnection)
          .copyOut("COPY ${datasetPart.id} TO STDOUT WITH FORMAT CSV, HEADER TRUE", out)
    }
    return ByteArrayResource(out.toByteArray())
  }

  override fun delete(datasetPart: DatasetPart) {
    writerJdbcTemplate.dataSource!!.connection.use { connection ->
      try {
        connection.autoCommit = false
        connection.createStatement().use { statement ->
          statement.execute("DROP TABLE ${datasetPart.id};")
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
