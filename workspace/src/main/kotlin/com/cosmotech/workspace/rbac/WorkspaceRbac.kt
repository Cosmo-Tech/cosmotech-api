// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.workspace.rbac

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.rbac.CsmAdmin
import com.cosmotech.api.rbac.CsmRbac
import com.cosmotech.api.rbac.ResourceSecurity
import com.cosmotech.api.rbac.RolesDefinition
import com.cosmotech.api.rbac.createResourceSecurity
import com.cosmotech.api.rbac.getCommonRolesDefinition
import com.cosmotech.api.rbac.migrateResourceSecurity
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceAccessControl
import com.cosmotech.workspace.domain.WorkspaceRole
import com.cosmotech.workspace.domain.WorkspaceRoleItems
import com.cosmotech.workspace.domain.WorkspaceSecurity
import com.cosmotech.workspace.domain.WorkspaceAccessControlWithPermissions
import java.lang.IllegalStateException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.beans.factory.annotation.Qualifier

@Configuration
internal class RbacConfiguration {
  @Bean(name=["Workspace"])
  fun getRolesDefinition(): RolesDefinition = getCommonRolesDefinition()
}

@Component
internal class WorkspaceRbac(
  csmPlatformProperties: CsmPlatformProperties,
  @Qualifier("Workspace") workspaceRoleDefinition: RolesDefinition,
  csmAdmin: CsmAdmin,
) :
    CsmRbac(csmPlatformProperties, workspaceRoleDefinition, csmAdmin) {
  private val logger: Logger = LoggerFactory.getLogger(this::class.java)

  fun initFor(workspace: Workspace) {
    logger.info("Configuring workspace ${workspace.id} rbac from security")
    var resourceSecurity: ResourceSecurity
    if (workspace.security == null) {
      resourceSecurity = migrateSecurity(workspace)
    } else {
      val acl: MutableMap<String, List<String>> =
          workspace
              .security
              ?.accessControlList
              ?.entries
              ?.associateBy({ it.key }, { it.value.roles.map { role -> role.toString() } })
              ?.toMutableMap()
              ?: mutableMapOf()
      if (csmPlatformProperties.rbac.enabled && acl.size == 0) {
        throw IllegalStateException("The workspace ${workspace.id} has no ACL defined")
      }
      val defaultAcl: List<String> =
          workspace.security?.default?.map { it.toString() }?.toList() ?: listOf()
      resourceSecurity = createResourceSecurity(defaultAcl, acl)
    }

    val workspaceId: String = workspace.id ?: throw IllegalStateException("Workspace id is null")
    this.setResourceInfo(workspaceId, resourceSecurity)
  }

  fun update(workspace: Workspace) {
    logger.info("Creating workspace ${workspace.id} security from rbac")
    if (workspace.security == null) {
      logger.debug("Creating workspace ${workspace.id} security since it does not exist yet")
      workspace.security = WorkspaceSecurity()
    }

    workspace.security?.default =
        this.resourceSecurity.default.map { WorkspaceRole.valueOf(it) }.toMutableList()
    workspace.security?.accessControlList =
        this.resourceSecurity
            .accessControlList
            .roles
            .entries
            .associateBy(
                { it.key },
                {
                  WorkspaceAccessControl(
                      id = it.key,
                      roles =
                          (it.value.map { role -> WorkspaceRole.valueOf(role) }.toMutableList()))
                })
            .toMutableMap()
  }

  fun setDefault(workspaceRoleItems: WorkspaceRoleItems) {
    this.setDefault(workspaceRoleItems.roles.map { it.toString() }.toList())
  }

  fun getWorkspaceAccessControlWithPermissions(user: String): WorkspaceAccessControlWithPermissions  {
    val userInfo = this.getUserInfo(user)
    return WorkspaceAccessControlWithPermissions(
      id = userInfo.id,
      roles = userInfo.roles.map { WorkspaceRole.valueOf(it) }.toMutableList(),
      permissions = userInfo.permissions.toMutableList()
    )
  }

  fun setWorkspaceAccess(
      workspaceAccessControl: WorkspaceAccessControl
    ) {
      this.setUserRoles(
        workspaceAccessControl.id,
        workspaceAccessControl.roles.map { it.toString() }
      )
    }

  @Suppress("MaxLineLength")
  private fun migrateSecurity(workspace: Workspace): ResourceSecurity {
    logger.warn(
        "Security for workspace ${workspace.id} does not exist. Trying to migrate it with options defined in config")
    val migratedSecurity = migrateResourceSecurity(this.csmPlatformProperties, this.rolesDefinition)
    if (csmPlatformProperties.rbac.enabled && migratedSecurity == null) {
      throw IllegalStateException(
          "The workspace ${workspace.id} has no ACL defined and it cannot be migrated. Check your migration options in rbac configuration.")
    } else {
      return migratedSecurity ?: ResourceSecurity()
    }
  }
}
