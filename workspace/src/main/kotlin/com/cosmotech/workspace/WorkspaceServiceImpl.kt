// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.workspace

import com.azure.cosmos.models.CosmosContainerProperties
import com.cosmotech.api.AbstractCosmosBackedService
import com.cosmotech.api.events.OrganizationRegistered
import com.cosmotech.api.events.OrganizationUnregistered
import com.cosmotech.workspace.api.WorkspaceApiService
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceFile
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["csm.platform.vendor"], havingValue = "azure", matchIfMissing = true)
class WorkspaceServiceImpl : AbstractCosmosBackedService(), WorkspaceApiService {
  override fun findAllWorkspaces(organizationId: String): List<Workspace> {
    TODO("Not yet implemented")
  }

  override fun findWorkspaceById(organizationId: String, workspaceId: String): Workspace {
    TODO("Not yet implemented")
  }

  override fun createWorkspace(organizationId: String, workspace: Workspace): Workspace {
    TODO("Not yet implemented")
  }

  override fun updateWorkspace(
      organizationId: String,
      workspaceId: String,
      workspace: Workspace
  ): Workspace {
    TODO("Not yet implemented")
  }

  override fun deleteWorkspace(organizationId: String, workspaceId: String): Workspace {
    TODO("Not yet implemented")
  }

  override fun deleteWorkspaceFile(
      organizationId: kotlin.String,
      workspaceId: kotlin.String,
      workspaceFile: WorkspaceFile
  ): WorkspaceFile {
    TODO("Not yet implemented")
  }

  override fun uploadWorkspaceFile(
      organizationId: kotlin.String,
      workspaceId: kotlin.String,
      fileName: org.springframework.core.io.Resource?
  ): WorkspaceFile {
    TODO("Not yet implemented")
  }
  override fun findAllWorkspaceFiles(
      organizationId: kotlin.String,
      workspaceId: kotlin.String
  ): List<WorkspaceFile> {
    TODO("Not yet implemented")
  }

  @EventListener(OrganizationRegistered::class)
  fun onOrganizationRegistered(organizationRegistered: OrganizationRegistered) {
    cosmosClient
        .getDatabase(databaseName)
        .createContainerIfNotExists(
            CosmosContainerProperties(
                "${organizationRegistered.organizationId}_workspace_data", "/workspaceId"))
  }

  @EventListener(OrganizationUnregistered::class)
  @Async("csm-in-process-event-executor")
  fun onOrganizationUnregistered(organizationUnregistered: OrganizationUnregistered) {
    cosmosTemplate.deleteContainer("${organizationUnregistered.organizationId}_workspace_data")
  }
}
