// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorunresult.service

import com.cosmotech.connector.domain.Connector
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.dataset.domain.DatasetConnector
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationSecurity
import com.cosmotech.scenario.domain.Scenario
import com.cosmotech.scenario.domain.ScenarioSecurity
import com.cosmotech.solution.domain.Solution
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceSecurity
import com.cosmotech.workspace.domain.WorkspaceSolution
import java.util.*

fun makeOrganization(
    name: String = "MyTestOrganization",
    security: OrganizationSecurity
): Organization {
  return Organization(name = name, ownerId = "my.account-tester@cosmotech.com", security = security)
}

fun makeConnector(name: String = "MyTestConnector"): Connector {
  return Connector(
      key = UUID.randomUUID().toString(),
      name = name,
      repository = "/repository",
      version = "0.0",
      ioTypes = listOf(Connector.IoTypes.read))
}

fun makeDataset(
    organizationId: String,
    connector: Connector,
    name: String = "MyTestDataset"
): Dataset {
  return Dataset(
      name = name,
      organizationId = organizationId,
      ownerId = "ownerId",
      connector =
          DatasetConnector(
              id = connector.id,
              name = connector.name,
              version = connector.version,
          ),
  )
}

fun makeSolution(organizationId: String, name: String = "MyTestSolution"): Solution {
  return Solution(
      id = "solutionId",
      key = UUID.randomUUID().toString(),
      name = name,
      organizationId = organizationId,
      ownerId = "ownerId")
}

fun makeWorkspace(
    organizationId: String,
    solutionId: String,
    name: String = "MyTestWorkspace",
    security: WorkspaceSecurity
): Workspace {
  return Workspace(
      key = UUID.randomUUID().toString(),
      name = name,
      solution =
          WorkspaceSolution(
              solutionId = solutionId,
          ),
      organizationId = organizationId,
      ownerId = "ownerId",
      security = security)
}

fun makeScenario(
    organizationId: String,
    workspaceId: String,
    solutionId: String,
    name: String = "MyTestScenario",
    datasetList: MutableList<String>,
    parentId: String? = null,
    security: ScenarioSecurity
): Scenario {
  return Scenario(
      name = name,
      organizationId = organizationId,
      workspaceId = workspaceId,
      solutionId = solutionId,
      ownerId = "ownerId",
      datasetList = datasetList,
      parentId = parentId,
      security = security)
}
