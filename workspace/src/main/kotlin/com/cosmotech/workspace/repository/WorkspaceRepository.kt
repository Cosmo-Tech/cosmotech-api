// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.workspace.repository

import com.cosmotech.workspace.domain.Workspace
import com.redis.om.spring.annotations.Query
import com.redis.om.spring.repository.RedisDocumentRepository
import java.util.Optional
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface WorkspaceRepository : RedisDocumentRepository<Workspace, String> {

  @Query("@organizationId:{\$organizationId} @id:{\$workspaceId}")
  fun findBy(
      @Param("organizationId") organizationId: String,
      @Param("workspaceId") workspaceId: String
  ): Optional<Workspace>
  fun findByOrganizationId(organizationId: String, pageable: Pageable): Page<Workspace>

  @Query("(@organizationId:{\$organizationId})  \$securityConstraint")
  fun findByOrganizationIdAndSecurity(
      @Param("organizationId") organizationId: String,
      @Param("securityConstraint") securityConstraint: String,
      pageable: Pageable
  ): Page<Workspace>
}
