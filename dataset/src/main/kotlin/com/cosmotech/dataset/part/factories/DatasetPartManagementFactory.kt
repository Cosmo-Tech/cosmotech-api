// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.part.factories

import com.cosmotech.dataset.domain.DatasetPart
import com.cosmotech.dataset.part.services.DatasetPartManagementService
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile

/**
 * Factory class for managing multiple implementations of the `DatasetPartManagementService`. This
 * class allows for selecting a specific implementation of the service based on a given identifier
 * and executes corresponding operations tailored to the chosen implementation.
 *
 * @param datasetPartManagementServices A map of available implementations of
 *   `DatasetPartManagementService`.
 * @constructor Initializes the factory with a map of service implementations, where the key is the
 *   identifier and the value is the associated `DatasetPartManagementService`.
 */
@Component
class DatasetPartManagementFactory(
    private val datasetPartManagementServices: Map<String, DatasetPartManagementService>
) {

  /**
   * Retrieves the `DatasetPartManagementService` implementation associated with the given
   * identifier.
   *
   * @param implementation The name of the `DatasetPartManagementService` implementation to
   *   retrieve.
   * @return The corresponding `DatasetPartManagementService` implementation.
   * @throws IllegalStateException If no implementation is found for the specified identifier.
   */
  fun getDatasetPartManagementService(implementation: String): DatasetPartManagementService =
      datasetPartManagementServices[implementation]
          ?: throw IllegalStateException(
              "No implementation found for DatasetPartManagementService with name '$implementation'")

  fun storeData(datasetPart: DatasetPart, file: MultipartFile, overwrite: Boolean = false) {
    val datasetPartManagementService = getDatasetPartManagementService(datasetPart.type.value)
    datasetPartManagementService.storeData(file, datasetPart, overwrite)
  }

  fun storeData(datasetPart: DatasetPart, file: Resource, overwrite: Boolean = false) {
    val datasetPartManagementService = getDatasetPartManagementService(datasetPart.type.value)
    datasetPartManagementService.storeData(file, datasetPart, overwrite)
  }

  fun removeData(datasetPart: DatasetPart) {
    val datasetPartManagementService = getDatasetPartManagementService(datasetPart.type.value)
    datasetPartManagementService.delete(datasetPart)
  }

  fun getData(datasetPart: DatasetPart): Resource {
    val datasetPartManagementService = getDatasetPartManagementService(datasetPart.type.value)
    return datasetPartManagementService.getData(datasetPart)
  }
}
