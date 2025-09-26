// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.runner.repository

import com.cosmotech.common.redis.Sanitize
import com.cosmotech.common.redis.SecurityConstraint
import com.cosmotech.runner.domain.Runner
import com.redis.om.spring.annotations.Query
import com.redis.om.spring.repository.RedisDocumentRepository
import java.util.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface RunnerRepository : RedisDocumentRepository<Runner, String> {

  @Query("@organizationId:{\$organizationId} @workspaceId:{\$workspaceId} @id:{\$runnerId}")
  fun findBy(
      @Sanitize @Param("organizationId") organizationId: String,
      @Sanitize @Param("workspaceId") workspaceId: String,
      @Sanitize @Param("runnerId") runnerId: String
  ): Optional<Runner>

  @Query(
      "(@organizationId:{\$organizationId} @workspaceId:{\$workspaceId}) @validationStatus:\$validationStatus")
  fun findByValidationStatus(
      @Sanitize @Param("organizationId") organizationId: String,
      @Sanitize @Param("workspaceId") workspaceId: String,
      @Sanitize @Param("validationStatus") validationStatus: String,
      pageable: Pageable
  ): Page<Runner>

  @Query(
      "(@organizationId:{\$organizationId} @workspaceId:{\$workspaceId}) " +
          "@validationStatus:\$validationStatus \$securityConstraint")
  fun findByValidationStatusAndSecurity(
      @Sanitize @Param("organizationId") organizationId: String,
      @Sanitize @Param("workspaceId") workspaceId: String,
      @Sanitize @Param("validationStatus") validationStatus: String,
      @SecurityConstraint @Param("securityConstraint") securityConstraint: String,
      pageable: Pageable
  ): Page<Runner>

  @Query("(@organizationId:{\$organizationId} @workspaceId:{\$workspaceId} @parentId:{\$parentId})")
  fun findByParentId(
      @Sanitize @Param("organizationId") organizationId: String,
      @Sanitize @Param("workspaceId") workspaceId: String,
      @Sanitize @Param("parentId") parentId: String,
      pageable: Pageable
  ): Page<Runner>

  @Query("(@organizationId:{\$organizationId})")
  fun findByOrganizationId(
      @Sanitize @Param("organizationId") organizationId: String,
      pageable: Pageable
  ): Page<Runner>

  @Query(
      "(@organizationId:{\$organizationId} @workspaceId:{\$workspaceId} @parentId:{\$parentId}) \$securityConstraint")
  fun findByParentIdAndSecurity(
      @Sanitize @Param("organizationId") organizationId: String,
      @Sanitize @Param("workspaceId") workspaceId: String,
      @Sanitize @Param("parentId") parentId: String,
      @SecurityConstraint @Param("securityConstraint") securityConstraint: String,
      pageable: Pageable
  ): Page<Runner>

  @Query("(@organizationId:{\$organizationId} @workspaceId:{\$workspaceId})")
  fun findByWorkspaceId(
      @Sanitize @Param("organizationId") organizationId: String,
      @Sanitize @Param("workspaceId") workspaceId: String,
      pageable: Pageable
  ): Page<Runner>

  @Query("(@organizationId:{\$organizationId} @workspaceId:{\$workspaceId}) \$securityConstraint")
  fun findByWorkspaceIdAndSecurity(
      @Sanitize @Param("organizationId") organizationId: String,
      @Sanitize @Param("workspaceId") workspaceId: String,
      @SecurityConstraint @Param("securityConstraint") securityConstraint: String,
      pageable: Pageable
  ): Page<Runner>
}
