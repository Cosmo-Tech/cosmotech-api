// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenario.repository

import com.cosmotech.scenario.domain.Scenario
import com.redis.om.spring.annotations.Query
import com.redis.om.spring.repository.RedisDocumentRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ScenarioRepository : RedisDocumentRepository<Scenario, String> {

  fun findByValidationStatus(validationStatus: String): List<Scenario>

  @Query("(@validationStatus:\$validationStatus) \$securityConstraint")
  fun findByValidationStatusAndSecurity(
      @Param("validationStatus") validationStatus: String,
      @Param("securityConstraint") securityConstraint: String
  ): List<Scenario>

  fun findByParentId(parentId: String): List<Scenario>

  @Query("(@parentId:{\$parentId}) \$securityConstraint")
  fun findByParentIdAndSecurity(
      @Param("parentId") parentId: String,
      @Param("securityConstraint") securityConstraint: String
  ): List<Scenario>

  fun findByRootId(rootId: String): List<Scenario>

  @Query("(@rootId:{\$rootId}) \$securityConstraint")
  fun findByRootIdAndSecurity(
      @Param("rootId") rootId: String,
      @Param("securityConstraint") securityConstraint: String
  ): List<Scenario>

  fun findByWorkspaceId(workspaceId: String): List<Scenario>

  @Query("(@workspaceId:{\$workspaceId}) \$securityConstraint")
  fun findByWorkspaceIdAndSecurity(
      @Param("workspaceId") workspaceId: String,
      @Param("securityConstraint") securityConstraint: String
  ): List<Scenario>
}
