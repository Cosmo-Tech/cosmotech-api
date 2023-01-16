// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.workspace.repository

import com.cosmotech.workspace.domain.Workspace
import com.redis.om.spring.annotations.Query
import com.redis.om.spring.repository.RedisDocumentRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface WorkspaceRepository : RedisDocumentRepository<Workspace, String> {

  fun findByOrganizationId(organizationId: String): List<Workspace>

  @Query("(@organizationId:{\$organizationId})  \$securityConstraint")
  fun findByOrganizationIdAndSecurity(
      @Param("organizationId") organizationId: String,
      @Param("securityConstraint") securityConstraint: String
  ): List<Workspace>

  @Query("(-@security_default:{none}) | (@security_accessControlList_id:{\$userId})")
  fun findWorkspacesBySecurity(@Param("userId") userId: String): List<Workspace>
}
