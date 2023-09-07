// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.repository

import com.cosmotech.api.redis.Sanitize
import com.cosmotech.dataset.domain.Dataset
import com.redis.om.spring.annotations.Query
import com.redis.om.spring.repository.RedisDocumentRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface DatasetRepository : RedisDocumentRepository<Dataset, String> {

  fun findByOrganizationIdAndMain(organizationId: String, main: Boolean, pageRequest: PageRequest): Page<Dataset>

  @Query("@tags:{\$tags}")
  fun findDatasetByTags(@Param("tags") tags: Set<String>, pageRequest: PageRequest): Page<Dataset>

  @Query("@connector_id:{\$connectorId}")
  fun findDatasetByConnectorId(
      @Sanitize @Param("connectorId") connectorId: String,
      pageRequest: PageRequest
  ): Page<Dataset>
}
