// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.repository

import com.cosmotech.api.redis.Sanitize
import com.cosmotech.api.redis.SecurityConstraint
import com.cosmotech.dataset.domain.Dataset
import com.redis.om.spring.annotations.Query
import com.redis.om.spring.repository.RedisDocumentRepository
import java.util.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface DatasetRepository : RedisDocumentRepository<Dataset, String> {

  @Query("@organizationId:{\$organizationId} @id:{\$datasetId}")
  fun findBy(
      @Sanitize @Param("organizationId") organizationId: String,
      @Sanitize @Param("datasetId") datasetId: String
  ): Optional<Dataset>

  @Query("(@organizationId:{\$organizationId}) \$securityConstraint")
  fun findByOrganizationId(
      @Sanitize @Param("organizationId") organizationId: String,
      @SecurityConstraint @Param("securityConstraint") securityConstraint: String,
      pageRequest: PageRequest
  ): Page<Dataset>

  @Query("(@organizationId:{\$organizationId})")
  fun findByOrganizationIdNoSecurity(
      @Sanitize @Param("organizationId") organizationId: String,
      pageRequest: PageRequest
  ): Page<Dataset>

  @Query("@organizationId:{\$organizationId} @tags:{\$tags}")
  fun findDatasetByTags(
      @Sanitize @Param("organizationId") organizationId: String,
      @Param("tags") tags: Set<String>,
      pageRequest: PageRequest
  ): Page<Dataset>

  @Query("@connector_id:{\$connectorId}")
  fun findDatasetByConnectorId(
      @Sanitize @Param("connectorId") connectorId: String,
      pageRequest: PageRequest
  ): Page<Dataset>
}
