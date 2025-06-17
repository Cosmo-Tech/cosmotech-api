// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.part.services

import com.cosmotech.dataset.domain.DatasetPart
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
class RelationalDatasetPartManagementService : DatasetPartManagementService {

  private val logger = LoggerFactory.getLogger(RelationalDatasetPartManagementService::class.java)

  override fun storeData(file: MultipartFile, datasetPart: DatasetPart, overwrite: Boolean) {
    logger.debug("RelationalDatasetPartManagementService#storeData")
    TODO("Not yet implemented")
  }

  override fun getData(datasetPart: DatasetPart): Resource {
    logger.debug("RelationalDatasetPartManagementService#getData")
    TODO("Not yet implemented")
  }

  override fun delete(datasetPart: DatasetPart) {
    logger.debug("RelationalDatasetPartManagementService#delete")
    TODO("Not yet implemented")
  }
}
