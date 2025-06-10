// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.part.services

import com.cosmotech.dataset.domain.DatasetPart
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

/**
 * Implementation of the `DatasetPartManagementService` for managing dataset parts stored as files.
 *
 * This service provides operations for saving and deleting dataset parts in a file-based storage
 * system.
 */
@Service("File")
class FileDatasetPartManagementService : DatasetPartManagementService {

  private val logger = LoggerFactory.getLogger(FileDatasetPartManagementService::class.java)

  override fun storeData(file: MultipartFile): DatasetPart? {
    logger.debug("Saving file ${file.originalFilename} of size ${file.size} as File")
    return null
  }

  override fun delete(datasetPart: DatasetPart) {
    TODO("Not yet implemented")
  }
}
