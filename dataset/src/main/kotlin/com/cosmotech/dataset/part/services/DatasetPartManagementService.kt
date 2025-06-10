// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.part.services

import com.cosmotech.dataset.domain.DatasetPart
import org.springframework.web.multipart.MultipartFile

/**
 * Service interface for managing dataset parts.
 *
 * This interface defines the contract for operations related to managing parts of a dataset,
 * including saving and deleting dataset parts.
 */
interface DatasetPartManagementService {

  fun storeData(file: MultipartFile): DatasetPart?

  fun delete(datasetPart: DatasetPart)
}
