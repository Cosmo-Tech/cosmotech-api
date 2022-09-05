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
import com.cosmotech.workspace.domain.WorkspaceAccessControlWithPermissions
import com.cosmotech.workspace.domain.WorkspaceRole
import com.cosmotech.workspace.domain.WorkspaceRoleItems
import com.cosmotech.workspace.domain.WorkspaceSecurity
import com.cosmotech.workspace.domain.WorkspaceSecurityUsers
import java.lang.IllegalStateException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

private val workspaceRbacLogger: Logger = LoggerFactory.getLogger("WorkspaceRbacModule")

@Component
@Qualifier("Workspace")
internal class RbacConfiguration(
    val csmPlatformProperties: CsmPlatformProperties,
    @Qualifier("Workspace") val rolesDefinition: RolesDefinition,
    val csmAdmin: CsmAdmin,
)

@Configuration
internal class WorkspaceRoles {
  @Bean(name = ["Workspace"])
  fun getWorkspaceRolesDefinition(): RolesDefinition = getCommonRolesDefinition()
}

internal fun rbac(conf: RbacConfiguration): WorkspaceRbac {
  return WorkspaceRbac(conf.csmPlatformProperties, conf.rolesDefinition, conf.csmAdmin)
}

internal fun rbac(
    conf: RbacConfiguration,
    workspace: Workspace,
    initPossible: Boolean = false
): WorkspaceRbac {
  val rbacObj = rbac(conf)
  rbacObj.initFor(workspace, initPossible)
  return rbacObj
}

internal fun rbac(
    conf: RbacConfiguration,
    workspace: Workspace,
    permission: String,
    initPossible: Boolean = false
): WorkspaceRbac {
  val rbacObj = rbac(conf, workspace, initPossible)
  if (rbacObj.newSecurity) {
    workspaceRbacLogger.info(
        "RBAC Workspace ${workspace.id} - New Security created, passing ACL verification")
  } else {
    rbacObj.verify(permission)
  }
  return rbacObj
}

internal class WorkspaceRbac(
    csmPlatformProperties: CsmPlatformProperties,
    workspaceRoleDefinition: RolesDefinition,
    csmAdmin: CsmAdmin,
) : CsmRbac(csmPlatformProperties, workspaceRoleDefinition, csmAdmin) {
  private val logger: Logger = LoggerFactory.getLogger(this::class.java)
  private var isInit: Boolean = false
  var newSecurity: Boolean = false

  fun initFor(workspace: Workspace, initPossible: Boolean = false) {
    if (this.isInit) {
      logger.debug("RBAC Workspace ${workspace.id} - rbac already init")
      return
    } else {
      logger.info("RBAC Workspace ${workspace.id} - Configuring rbac from security")
      val workspaceId: String = workspace.id ?: throw IllegalStateException("Workspace id is null")
      if (csmPlatformProperties.rbac.enabled && workspace.security == null) {
        if (initPossible) {
          logger.debug("RBAC Workspace ${workspace.id} - No ACL defined but security init mode on")
          this.newSecurity = true
        } else {
          throw IllegalStateException(
              "RBAC Workspace ${workspace.id} - No ACL defined, " +
                  " you must migrate it or initialize it with a first access control.")
        }
      }

      val acl: MutableMap<String, List<String>> =
          workspace
              .security
              ?.accessControlList
              ?.associateBy({ it.id }, { it.roles.map { role -> role.toString() } })
              ?.toMutableMap()
              ?: mutableMapOf()
      if (csmPlatformProperties.rbac.enabled && acl.size == 0 && !initPossible) {
        throw IllegalStateException(
            "RBAC Workspace ${workspace.id} - No ACL defined, " +
                "you must migrate it or initialize it with a first access control.")
      }
      val defaultAcl: List<String> =
          workspace.security?.default?.map { it.toString() }?.toList() ?: listOf()
      val resourceSecurity = createResourceSecurity(defaultAcl, acl)

      this.setResourceInfo(workspaceId, resourceSecurity)
      this.isInit = true
    }
  }

  fun update(workspace: Workspace) {
    logger.info("RBAC Workspace ${workspace.id} - Updating security from rbac")
    if (workspace.security == null) {
      logger.debug("RBAC Workspace ${workspace.id} - Creating security since it does not exist yet")
      workspace.security = WorkspaceSecurity()
    }

    workspace.security?.default =
        this.resourceSecurity.default.map { WorkspaceRole.valueOf(it) }.toMutableList()
    workspace.security?.accessControlList =
        this.resourceSecurity
            .accessControlList
            .roles
            .entries
            .map {
              WorkspaceAccessControl(
                  id = it.key,
                  roles = (it.value.map { role -> WorkspaceRole.valueOf(role) }.toMutableList()))
            }
            .toMutableList()
    this.newSecurity = false
  }

  fun setDefault(workspaceRoleItems: WorkspaceRoleItems) {
    this.setDefault(workspaceRoleItems.roles.map { it.toString() }.toList())
  }

  fun getWorkspaceAccessControlWithPermissions(
      user: String
  ): WorkspaceAccessControlWithPermissions {
    val userInfo = this.getUserInfo(user)
    return WorkspaceAccessControlWithPermissions(
        id = userInfo.id,
        roles = userInfo.roles.map { WorkspaceRole.valueOf(it) }.toMutableList(),
        permissions = userInfo.permissions.toMutableList())
  }

  fun setWorkspaceAccess(workspaceAccessControl: WorkspaceAccessControl) {
    this.setUserRoles(workspaceAccessControl.id, workspaceAccessControl.roles.map { it.toString() })
  }

  fun getWorkspaceUsers(): WorkspaceSecurityUsers {
    return WorkspaceSecurityUsers(users = this.getUsers().toMutableList())
  }

  // This function is let here but it should disappear for dedicated migration endpoints
  @Suppress("MaxLineLength")
  private fun migrateSecurity(workspace: Workspace): ResourceSecurity {
    logger.warn(
        "RBAC Workspace ${workspace.id} - Security does not exist. Trying to migrate it with options defined in config. New security will be stored only for update commands")
    val migratedSecurity = migrateResourceSecurity(this.csmPlatformProperties, this.rolesDefinition)
    if (csmPlatformProperties.rbac.enabled && migratedSecurity == null) {
      throw IllegalStateException(
          "RBAC Workspace ${workspace.id} - No ACL defined and it cannot be migrated. Check your migration options in rbac configuration.")
    } else {
      return migratedSecurity ?: ResourceSecurity()
    }
  }
}
