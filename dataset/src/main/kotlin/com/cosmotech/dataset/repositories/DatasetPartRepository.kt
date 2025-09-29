// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.repositories

import com.cosmotech.common.redis.Sanitize
import com.cosmotech.common.redis.SecurityConstraint
import com.cosmotech.dataset.domain.DatasetPart
import com.redis.om.spring.annotations.Query
import com.redis.om.spring.repository.RedisDocumentRepository
import java.util.Optional
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.query.Param

interface DatasetPartRepository : RedisDocumentRepository<DatasetPart, String> {

  @Query(
      "@organizationId:{\$organizationId} @workspaceId:{\$workspaceId} " +
          "@datasetId:{\$datasetId} @id:{\$datasetPartId}")
  fun findBy(
      @Sanitize @Param("organizationId") organizationId: String,
      @Sanitize @Param("workspaceId") workspaceId: String,
      @Sanitize @Param("datasetId") datasetId: String,
      @Sanitize @Param("datasetPartId") datasetPartId: String
  ): Optional<DatasetPart>

  @Query(
      "(@organizationId:{\$organizationId} @workspaceId:{\$workspaceId} " +
          "@datasetId:{\$datasetId}) \$securityConstraint")
  fun findByOrganizationIdAndWorkspaceIdAndDatasetId(
      @Sanitize @Param("organizationId") organizationId: String,
      @Sanitize @Param("workspaceId") workspaceId: String,
      @Sanitize @Param("datasetId") datasetId: String,
      @SecurityConstraint @Param("securityConstraint") securityConstraint: String,
      pageRequest: PageRequest
  ): Page<DatasetPart>

  @Query(
      "(@organizationId:{\$organizationId} @workspaceId:{\$workspaceId} " +
          "@datasetId:{\$datasetId})")
  fun findByOrganizationIdAndWorkspaceIdAndDatasetIdNoSecurity(
      @Sanitize @Param("organizationId") organizationId: String,
      @Sanitize @Param("workspaceId") workspaceId: String,
      @Sanitize @Param("datasetId") datasetId: String,
      pageRequest: PageRequest
  ): Page<DatasetPart>

  @Query(
      "@organizationId:{\$organizationId} @workspaceId:{\$workspaceId} " +
          "@datasetId:{\$datasetId} @tags:{\$tags}")
  fun findDatasetPartByTags(
      @Sanitize @Param("organizationId") organizationId: String,
      @Sanitize @Param("workspaceId") workspaceId: String,
      @Sanitize @Param("datasetId") datasetId: String,
      @Param("tags") tags: List<String>,
      pageRequest: PageRequest
  ): Page<DatasetPart>
}
