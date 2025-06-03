// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.repositories

import com.cosmotech.api.redis.Sanitize
import com.cosmotech.api.redis.SecurityConstraint
import com.cosmotech.dataset.domain.Dataset
import com.redis.om.spring.annotations.Query
import com.redis.om.spring.repository.RedisDocumentRepository
import java.util.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.query.Param

interface DatasetRepository : RedisDocumentRepository<Dataset, String> {

  @Query("@organizationId:{\$organizationId} @workspaceId:{\$workspaceId} @id:{\$datasetId}")
  fun findBy(
      @Sanitize @Param("organizationId") organizationId: String,
      @Sanitize @Param("workspaceId") workspaceId: String,
      @Sanitize @Param("datasetId") datasetId: String
  ): Optional<Dataset>

  @Query("(@organizationId:{\$organizationId} @workspaceId:{\$workspaceId}) \$securityConstraint")
  fun findByOrganizationIdAndWorkspaceId(
      @Sanitize @Param("organizationId") organizationId: String,
      @Sanitize @Param("workspaceId") workspaceId: String,
      @SecurityConstraint @Param("securityConstraint") securityConstraint: String,
      pageRequest: PageRequest
  ): Page<Dataset>

  @Query("(@organizationId:{\$organizationId} @workspaceId:{\$workspaceId})")
  fun findByOrganizationIdAndWorkspaceIdNoSecurity(
      @Sanitize @Param("organizationId") organizationId: String,
      @Sanitize @Param("workspaceId") workspaceId: String,
      pageRequest: PageRequest
  ): Page<Dataset>

  @Query("@organizationId:{\$organizationId} @workspaceId:{\$workspaceId} @tags:{\$tags}")
  fun findDatasetByTags(
      @Sanitize @Param("organizationId") organizationId: String,
      @Sanitize @Param("workspaceId") workspaceId: String,
      @Param("tags") tags: Set<String>,
      pageRequest: PageRequest
  ): Page<Dataset>
}
