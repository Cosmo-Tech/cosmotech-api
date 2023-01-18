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
  @Query("(@validationStatus:{\$validationStatus})")
  fun findScenariosByValidationStatus(
      @Param("validationStatus") validationStatus: String
  ): List<Scenario>

  @Query("(@validationStatus:{\$validationStatus}) (@security_accessControlList_id:{\$userId})")
  fun findScenariosByValidationStatusBySecurity(
      @Param("validationStatus") validationStatus: String,
      @Param("userId") userId: String
  ): List<Scenario>

  @Query("(@parentId:{\$parentId})")
  fun findChildScenarios(@Param("parentId") parentId: String): List<Scenario>

  @Query("(@parentId:{\$parentId}) (@security_accessControlList_id:{\$userId})")
  fun findChildScenariosBySecurity(
      @Param("parentId") parentId: String,
      @Param("userId") userId: String
  ): List<Scenario>

  @Query("(@rootId:{\$rootId})")
  fun findScenarioOfRoot(@Param("rootId") rootId: String): List<Scenario>

  @Query("(@rootId:{\$rootId}) (@security_accessControlList_id:{\$userId})")
  fun findScenariosOfRootBySecurity(
      @Param("rootId") rootId: String,
      @Param("userId") userId: String
  ): List<Scenario>

  @Query("(@workspaceId:{\$workspaceId}) (@security_accessControlList_id:{\$userId})")
  fun findScenariosByWorkspaceIdBySecurity(
      @Param("workspaceId") workspaceId: String,
      @Param("userId") userId: String
  ): List<Scenario>

  @Query("(@workspaceId:{\$workspaceId})")
  fun findScenariosByWorkspaceId(@Param("workspaceId") workspaceId: String): List<Scenario>
}
