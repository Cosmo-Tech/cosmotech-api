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

  fun storeData(inputStream: InputStream, datasetPart: DatasetPart, overwrite: Boolean) {
    if (writerJdbcTemplate.existTable(datasetPart.id) && !overwrite) {
      throw IllegalArgumentException(
          "Table ${datasetPart.id} already exists and overwrite is set to false.")
    }
    writerJdbcTemplate.dataSource!!.connection.use { connection ->
      try {
        connection.autoCommit = false
        if (overwrite) {
          connection.createStatement().use { it.execute("DROP TABLE IF EXISTS ${datasetPart.id}") }
        }
        CopyManager(connection as BaseConnection)
            .copyIn("COPY ${datasetPart.id} FROM STDIN WITH FORMAT CSV, HEADER TRUE", inputStream)
        connection.commit()
      } catch (ex: SQLException) {
        connection.rollback()
        logger.error("Transaction Failed and roll back was performed.")
        throw ex
      }
    }
  }

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
