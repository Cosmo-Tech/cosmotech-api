// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenario.service

import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.rbac.model.RbacAccessControl
import com.cosmotech.api.rbac.model.RbacSecurity
import com.cosmotech.scenario.domain.Scenario
import com.cosmotech.scenario.domain.ScenarioAccessControl
import com.cosmotech.scenario.domain.ScenarioSecurity
import org.slf4j.LoggerFactory

// PROD-8051 : add parent and master last runs data
internal fun Scenario.addLastRunsInfo(
    scenarioServiceImpl: ScenarioServiceImpl,
    organizationId: String,
    workspaceId: String
): Scenario {
  val scenarioWithLastRunsInfo =
      listOf(this).addLastRunsInfo(scenarioServiceImpl, organizationId, workspaceId).first()
  this.parentLastRun = scenarioWithLastRunsInfo.parentLastRun
  this.rootLastRun = scenarioWithLastRunsInfo.rootLastRun
  return this
}

// Grouping for performance reasons. This allows to issue one call per parentId
// and one call per rootId
internal fun List<Scenario>.addLastRunsInfo(
    scenarioServiceImpl: ScenarioServiceImpl,
    organizationId: String,
    workspaceId: String
): List<Scenario> {
  val logger =
      LoggerFactory.getLogger("com.cosmotech.scenario.azure.ScenarioExtensions#addLastRunsInfo")
  return this.groupBy { it.parentId }
      .flatMap { (parentId, scenarios) ->
        if (!parentId.isNullOrBlank()) {
          val parentLastRun =
              try {
                scenarioServiceImpl.findScenarioByIdNoState(organizationId, workspaceId, parentId)
                    .lastRun
              } catch (iae: CsmResourceNotFoundException) {
                // There might be cases where the parent no longer exists
                val messageFormat =
                    "Parent scenario #{} not found " +
                        "=> returning null as 'parentLastRun' " +
                        "for all its {} direct child(ren)"
                logger.debug(messageFormat, parentId, scenarios.size)
                if (logger.isTraceEnabled) {
                  logger.trace(messageFormat, parentId, scenarios.size, iae)
                }
                null
              }
          scenarios.forEach { it.parentLastRun = parentLastRun }
        }
        scenarios
      }
      .groupBy { it.rootId }
      .flatMap { (rootId, scenarios) ->
        if (!rootId.isNullOrBlank()) {
          val rootLastRun =
              try {
                scenarioServiceImpl.findScenarioByIdNoState(organizationId, workspaceId, rootId)
                    .lastRun
              } catch (iae: CsmResourceNotFoundException) {
                // There might be cases where the root scenario no longer exists
                val messageFormat =
                    "Root scenario #{} not found" +
                        "=> returning null as 'rootLastRun' " +
                        "for all its {} child(ren)"
                logger.debug(messageFormat, rootId, scenarios.size)
                if (logger.isTraceEnabled) {
                  logger.trace(messageFormat, rootId, scenarios.size, iae)
                }
                null
              }
          scenarios.forEach { it.rootLastRun = rootLastRun }
        }
        scenarios
      }
}

fun Scenario.getRbac(): RbacSecurity {
  return RbacSecurity(
      this.id,
      this.security?.default ?: ROLE_NONE,
      this.security?.accessControlList?.map { RbacAccessControl(it.id, it.role) }?.toMutableList()
          ?: mutableListOf())
}

fun Scenario.setRbac(rbacSecurity: RbacSecurity) {
  this.security =
      ScenarioSecurity(
          rbacSecurity.default,
          rbacSecurity.accessControlList
              .map { ScenarioAccessControl(it.id, it.role) }
              .toMutableList())
}
