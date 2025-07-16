// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.part.services

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.dataset.domain.DatasetPart
import io.awspring.cloud.s3.S3Template
import java.io.InputStream
import org.slf4j.LoggerFactory
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

/**
 * Implementation of the `DatasetPartManagementService` for managing dataset parts stored as files.
 *
 * This service provides operations for saving and deleting dataset parts in a file-based storage
 * system.
 */
@Service("File")
class FileDatasetPartManagementService(
    private val csmPlatformProperties: CsmPlatformProperties,
    private val s3Template: S3Template
) : DatasetPartManagementService {

  private val logger = LoggerFactory.getLogger(FileDatasetPartManagementService::class.java)

  override fun storeData(file: MultipartFile, datasetPart: DatasetPart, overwrite: Boolean) {
    val organizationId = datasetPart.organizationId
    val workspaceId = datasetPart.workspaceId
    val datasetId = datasetPart.datasetId
    val datasetPartId = datasetPart.id
    val filePath = constructFilePath(organizationId, workspaceId, datasetId, datasetPartId)
    uploadFile(filePath, overwrite, datasetPartId, file.size, file.inputStream)
  }

  override fun storeData(file: Resource, datasetPart: DatasetPart, overwrite: Boolean) {
    val organizationId = datasetPart.organizationId
    val workspaceId = datasetPart.workspaceId
    val datasetId = datasetPart.datasetId
    val datasetPartId = datasetPart.id
    val filePath = constructFilePath(organizationId, workspaceId, datasetId, datasetPartId)
    uploadFile(filePath, overwrite, datasetPartId, file.contentLength(), file.inputStream)
  }

  override fun getData(datasetPart: DatasetPart): Resource {
    val filePath =
        constructFilePath(
            datasetPart.organizationId,
            datasetPart.workspaceId,
            datasetPart.datasetId,
            datasetPart.id)
    logger.debug(
        "Downloading file resource for dataset part #{} from path {}", datasetPart.id, filePath)
    return s3Template.download(csmPlatformProperties.s3.bucketName, filePath)
  }

  override fun delete(datasetPart: DatasetPart) {
    val filePath =
        constructFilePath(
            datasetPart.organizationId,
            datasetPart.workspaceId,
            datasetPart.datasetId,
            datasetPart.id)
    logger.debug("Deleting file resource from workspace #{} from path {}", datasetPart.id, filePath)

    s3Template.deleteObject(csmPlatformProperties.s3.bucketName, filePath)
  }

  fun constructFilePath(
      organizationId: String,
      workspaceId: String,
      datasetId: String,
      datasetPartId: String
  ) = "$organizationId/$workspaceId/$datasetId/$datasetPartId"

  private fun uploadFile(
      filePath: String,
      overwrite: Boolean,
      fileName: String,
      fileSize: Long,
      file: InputStream
  ) {
    val fileAlreadyExists = s3Template.objectExists(csmPlatformProperties.s3.bucketName, filePath)

    check(overwrite || !fileAlreadyExists) { "File $filePath already exists" }

    if (fileAlreadyExists) {
      logger.debug("Deleting existing file $filePath before overwriting it")
      s3Template.deleteObject(csmPlatformProperties.s3.bucketName, filePath)
    }
    logger.debug("Saving file $fileName of size $fileSize to $filePath")
    s3Template.upload(csmPlatformProperties.s3.bucketName, filePath, file)
  }
}
