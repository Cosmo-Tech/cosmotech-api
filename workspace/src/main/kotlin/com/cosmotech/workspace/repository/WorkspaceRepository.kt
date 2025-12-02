// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.workspace.repository

import com.cosmotech.common.redis.Sanitize
import com.cosmotech.common.redis.SecurityConstraint
import com.cosmotech.workspace.domain.Workspace
import com.redis.om.spring.annotations.Query
import com.redis.om.spring.repository.RedisDocumentRepository
import java.util.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.query.Param

interface WorkspaceRepository : RedisDocumentRepository<Workspace, String> {

  @Query("@organizationId:{\$organizationId} @id:{\$workspaceId}")
  fun findBy(
      @Sanitize @Param("organizationId") organizationId: String,
      @Sanitize @Param("workspaceId") workspaceId: String,
  ): Optional<Workspace>

  @Query("@organizationId:{\$organizationId}")
  fun findByOrganizationId(
      @Sanitize @Param("organizationId") organizationId: String,
      pageable: Pageable,
  ): Page<Workspace>

  @Query("(@organizationId:{\$organizationId})  \$securityConstraint")
  fun findByOrganizationIdAndSecurity(
      @Sanitize @Param("organizationId") organizationId: String,
      @SecurityConstraint @Param("securityConstraint") securityConstraint: String,
      pageable: Pageable,
  ): Page<Workspace>
}
