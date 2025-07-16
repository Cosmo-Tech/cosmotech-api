// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.part.services

import com.cosmotech.dataset.domain.DatasetPart
import org.springframework.core.io.Resource
import org.springframework.web.multipart.MultipartFile

/**
 * Service interface for managing dataset parts.
 *
 * This interface defines the contract for operations related to managing parts of a dataset,
 * including saving and deleting dataset parts.
 */
interface DatasetPartManagementService {

  fun storeData(file: MultipartFile, datasetPart: DatasetPart, overwrite: Boolean)

  fun storeData(file: Resource, datasetPart: DatasetPart, overwrite: Boolean)

  fun getData(datasetPart: DatasetPart): Resource

  fun delete(datasetPart: DatasetPart)
}
