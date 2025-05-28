// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.parts.service

import com.cosmotech.api.CsmPhoenixService
import com.cosmotech.dataset.domain.DatasetPart
import com.cosmotech.dataset.domain.DatasetPartCreateRequest
import com.cosmotech.dataset.domain.DatasetPartUpdateRequest
import com.cosmotech.dataset.parts.DatasetApiPartServiceInterface
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service

@Service
@Suppress("EmptyDefaultConstructor", "TooManyFunctions")
class DatasetPartServiceImpl() : CsmPhoenixService(), DatasetApiPartServiceInterface {

  override fun createDatasetPart(
      organizationId: String,
      workspaceId: String,
      datasetId: String,
      datasetPartCreateRequest: DatasetPartCreateRequest,
      file: Resource?
  ): DatasetPart {
    TODO("Not yet implemented")
  }

  override fun deleteDatasetPart(
      organizationId: String,
      workspaceId: String,
      datasetId: String,
      datasetPartId: String
  ) {
    TODO("Not yet implemented")
  }

  override fun downloadDatasetPart(
      organizationId: String,
      workspaceId: String,
      datasetId: String,
      datasetPartId: String
  ): Resource {
    TODO("Not yet implemented")
  }

  override fun getDatasetPart(
      organizationId: String,
      workspaceId: String,
      datasetId: String,
      datasetPartId: String
  ): DatasetPart {
    TODO("Not yet implemented")
  }

  override fun listDatasetParts(
      organizationId: String,
      workspaceId: String,
      datasetId: String
  ): List<DatasetPart> {
    TODO("Not yet implemented")
  }

  override fun queryData(
      organizationId: String,
      workspaceId: String,
      datasetId: String,
      datasetPartId: String,
      filters: List<String>?,
      sums: List<String>?,
      counts: List<String>?,
      offset: Int?,
      limit: Int?
  ): List<Any> {
    TODO("Not yet implemented")
  }

  override fun replaceDatasetPart(
      organizationId: String,
      workspaceId: String,
      datasetId: String,
      datasetPartId: String,
      file: Resource?,
      datasetPart: DatasetPartUpdateRequest?
  ): DatasetPart {
    TODO("Not yet implemented")
  }
}
