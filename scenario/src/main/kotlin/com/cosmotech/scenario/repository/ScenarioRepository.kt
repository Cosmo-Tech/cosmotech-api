// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenario.repository

import com.cosmotech.scenario.domain.Scenario
import com.redis.om.spring.annotations.Query
import com.redis.om.spring.repository.RedisDocumentRepository
import java.util.Optional
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ScenarioRepository : RedisDocumentRepository<Scenario, String> {

  @Query("@organizationId:{\$organizationId} @workspaceId:{\$workspaceId} @id:{\$scenarioId}")
  fun findBy(
      @Param("organizationId") organizationId: String,
      @Param("workspaceId") workspaceId: String,
      @Param("scenarioId") scenarioId: String
  ): Optional<Scenario>

  fun findByValidationStatus(validationStatus: String, pageable: Pageable): Page<Scenario>

  @Query("(@validationStatus:\$validationStatus) \$securityConstraint")
  fun findByValidationStatusAndSecurity(
      @Param("validationStatus") validationStatus: String,
      @Param("securityConstraint") securityConstraint: String,
      pageable: Pageable
  ): Page<Scenario>

  fun findByParentId(parentId: String, pageable: Pageable): Page<Scenario>

  fun findByOrganizationId(organizationId: String, pageable: Pageable): Page<Scenario>

  @Query("(@parentId:{\$parentId}) \$securityConstraint")
  fun findByParentIdAndSecurity(
      @Param("parentId") parentId: String,
      @Param("securityConstraint") securityConstraint: String,
      pageable: Pageable
  ): Page<Scenario>

  fun findByRootId(rootId: String, pageable: Pageable): Page<Scenario>

  @Query("(@rootId:{\$rootId}) \$securityConstraint")
  fun findByRootIdAndSecurity(
      @Param("rootId") rootId: String,
      @Param("securityConstraint") securityConstraint: String,
      pageable: Pageable
  ): Page<Scenario>

  fun findByWorkspaceId(workspaceId: String, pageable: Pageable): Page<Scenario>

  @Query("(@workspaceId:{\$workspaceId}) \$securityConstraint")
  fun findByWorkspaceIdAndSecurity(
      @Param("workspaceId") workspaceId: String,
      @Param("securityConstraint") securityConstraint: String,
      pageable: Pageable
  ): Page<Scenario>
}
