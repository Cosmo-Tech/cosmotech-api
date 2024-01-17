// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenario.repository

import com.cosmotech.api.redis.Sanitize
import com.cosmotech.api.redis.SecurityConstraint
import com.cosmotech.scenario.domain.Scenario
import com.redis.om.spring.annotations.Query
import com.redis.om.spring.repository.RedisDocumentRepository
import java.util.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ScenarioRepository : RedisDocumentRepository<Scenario, String> {

  @Query("@organizationId:{\$organizationId} @workspaceId:{\$workspaceId} @id:{\$scenarioId}")
  fun findBy(
      @Sanitize @Param("organizationId") organizationId: String,
      @Sanitize @Param("workspaceId") workspaceId: String,
      @Sanitize @Param("scenarioId") scenarioId: String
  ): Optional<Scenario>

  @Query(
      "(@organizationId:{\$organizationId} @workspaceId:{\$workspaceId}) @validationStatus:\$validationStatus")
  fun findByValidationStatus(
      @Sanitize @Param("organizationId") organizationId: String,
      @Sanitize @Param("workspaceId") workspaceId: String,
      @Sanitize @Param("validationStatus") validationStatus: String,
      pageable: Pageable
  ): Page<Scenario>

  @Query(
      "(@organizationId:{\$organizationId} @workspaceId:{\$workspaceId}) " +
          "@validationStatus:\$validationStatus \$securityConstraint")
  fun findByValidationStatusAndSecurity(
      @Sanitize @Param("organizationId") organizationId: String,
      @Sanitize @Param("workspaceId") workspaceId: String,
      @Sanitize @Param("validationStatus") validationStatus: String,
      @SecurityConstraint @Param("securityConstraint") securityConstraint: String,
      pageable: Pageable
  ): Page<Scenario>

  @Query("(@organizationId:{\$organizationId} @workspaceId:{\$workspaceId} @parentId:{\$parentId})")
  fun findByParentId(
      @Sanitize @Param("organizationId") organizationId: String,
      @Sanitize @Param("workspaceId") workspaceId: String,
      @Sanitize @Param("parentId") parentId: String,
      pageable: Pageable
  ): Page<Scenario>

  @Query("(@organizationId:{\$organizationId})")
  fun findByOrganizationId(
      @Sanitize @Param("organizationId") organizationId: String,
      pageable: Pageable
  ): Page<Scenario>

  @Query(
      "(@organizationId:{\$organizationId} @workspaceId:{\$workspaceId} @parentId:{\$parentId}) \$securityConstraint")
  fun findByParentIdAndSecurity(
      @Sanitize @Param("organizationId") organizationId: String,
      @Sanitize @Param("workspaceId") workspaceId: String,
      @Sanitize @Param("parentId") parentId: String,
      @SecurityConstraint @Param("securityConstraint") securityConstraint: String,
      pageable: Pageable
  ): Page<Scenario>

  @Query("(@organizationId:{\$organizationId} @workspaceId:{\$workspaceId} @rootId:{\$rootId})")
  fun findByRootId(
      @Sanitize @Param("organizationId") organizationId: String,
      @Sanitize @Param("workspaceId") workspaceId: String,
      @Sanitize @Param("rootId") rootId: String,
      pageable: Pageable
  ): Page<Scenario>

  @Query(
      "(@organizationId:{\$organizationId} @workspaceId:{\$workspaceId} @rootId:{\$rootId}) \$securityConstraint")
  fun findByRootIdAndSecurity(
      @Sanitize @Param("organizationId") organizationId: String,
      @Sanitize @Param("workspaceId") workspaceId: String,
      @Sanitize @Param("rootId") rootId: String,
      @SecurityConstraint @Param("securityConstraint") securityConstraint: String,
      pageable: Pageable
  ): Page<Scenario>

  @Query("(@organizationId:{\$organizationId} @workspaceId:{\$workspaceId})")
  fun findByWorkspaceId(
      @Sanitize @Param("organizationId") organizationId: String,
      @Sanitize @Param("workspaceId") workspaceId: String,
      pageable: Pageable
  ): Page<Scenario>

  @Query("(@organizationId:{\$organizationId} @workspaceId:{\$workspaceId}) \$securityConstraint")
  fun findByWorkspaceIdAndSecurity(
      @Sanitize @Param("organizationId") organizationId: String,
      @Sanitize @Param("workspaceId") workspaceId: String,
      @SecurityConstraint @Param("securityConstraint") securityConstraint: String,
      pageable: Pageable
  ): Page<Scenario>
}
