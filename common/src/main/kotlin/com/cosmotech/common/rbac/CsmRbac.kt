// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.rbac

import com.cosmotech.common.config.CsmPlatformProperties
import com.cosmotech.common.exceptions.CsmAccessForbiddenException
import com.cosmotech.common.exceptions.CsmClientException
import com.cosmotech.common.exceptions.CsmResourceNotFoundException
import com.cosmotech.common.rbac.model.RbacAccessControl
import com.cosmotech.common.rbac.model.RbacSecurity
import com.cosmotech.common.utils.getCurrentAccountGroups
import com.cosmotech.common.utils.getCurrentAccountIdentifier
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Suppress("TooManyFunctions")
@Component
open class CsmRbac(
    protected val csmPlatformProperties: CsmPlatformProperties,
    protected val csmAdmin: CsmAdmin,
) {

  private val logger: Logger = LoggerFactory.getLogger(this::class.java)

  fun initSecurity(security: RbacSecurity): RbacSecurity {
    var objectSecurity = security.copy()

    // Check for duplicate identities
    val accessControls = mutableListOf<String>()
    objectSecurity.accessControlList.forEach {
      require(!(accessControls.contains(it.id))) {
        "Entity ${it.id} is referenced multiple times in the security"
      }
      accessControls.add(it.id)
    }

    // Make sure we have at least one admin
    if (!objectSecurity.accessControlList.any { it.role == ROLE_ADMIN }) {
      val currentUserId = getCurrentAccountIdentifier(csmPlatformProperties)
      val currentUserACL = objectSecurity.accessControlList.find { it.id == currentUserId }
      if (currentUserACL != null) {
        currentUserACL.role = ROLE_ADMIN
      } else {
        objectSecurity.accessControlList.add(RbacAccessControl(currentUserId, ROLE_ADMIN))
      }
    }

    return objectSecurity
  }

  fun verify(
      rbacSecurity: RbacSecurity,
      permission: String,
      rolesDefinition: RolesDefinition = getCommonRolesDefinition(),
  ) {
    if (!this.check(rbacSecurity, permission, rolesDefinition))
        throw CsmAccessForbiddenException(
            "RBAC ${rbacSecurity.id} - User does not have permission $permission"
        )
  }

  fun check(
      rbacSecurity: RbacSecurity,
      permission: String,
      rolesDefinition: RolesDefinition = getCommonRolesDefinition(),
  ): Boolean {
    logger.info("RBAC ${rbacSecurity.id} - Verifying permission $permission for entity")
    if (!this.csmPlatformProperties.rbac.enabled) {
      logger.debug("RBAC ${rbacSecurity.id} - RBAC check not enabled")
      return true
    }
    var entityIsAdminOrHasPermission = this.isAdmin(rbacSecurity, rolesDefinition)
    if (!entityIsAdminOrHasPermission) {
      val entity = getCurrentAccountIdentifier(this.csmPlatformProperties)
      val groups = getCurrentAccountGroups(this.csmPlatformProperties)
      entityIsAdminOrHasPermission =
          this.verifyRbac(rbacSecurity, permission, rolesDefinition, entity, groups)
    }
    return entityIsAdminOrHasPermission
  }

  fun setDefault(
      rbacSecurity: RbacSecurity,
      defaultRole: String,
      rolesDefinition: RolesDefinition = getCommonRolesDefinition(),
  ): RbacSecurity {
    logger.info("RBAC ${rbacSecurity.id} - Setting default security")
    if (defaultRole != ROLE_NONE) {
      this.verifyRoleOrThrow(rbacSecurity, defaultRole, rolesDefinition)
    }
    rbacSecurity.default = defaultRole
    return rbacSecurity
  }

  fun addEntityRole(
      parentRbacSecurity: RbacSecurity,
      rbacSecurity: RbacSecurity,
      entityId: String,
      role: String,
      rolesDefinition: RolesDefinition = getCommonRolesDefinition(),
  ): RbacSecurity {

    if (!isAdmin(rbacSecurity, rolesDefinition)) {
      this.checkEntityExists(
          parentRbacSecurity,
          entityId,
          "Entity $entityId not found in parent ${parentRbacSecurity.id} component",
      )
    }
    return setEntityRole(rbacSecurity, entityId, role, rolesDefinition)
  }

  fun setEntityRole(
      rbacSecurity: RbacSecurity,
      entityId: String,
      role: String,
      rolesDefinition: RolesDefinition = getCommonRolesDefinition(),
  ): RbacSecurity {
    logger.info("RBAC ${rbacSecurity.id} - Setting entity $entityId roles")
    this.verifyRoleOrThrow(rbacSecurity, role, rolesDefinition)
    val currentACLRole =
        rbacSecurity.accessControlList
            .firstOrNull { it.id.lowercase() == entityId.lowercase() }
            ?.role
    val adminRole = this.getAdminRole(rolesDefinition)
    if (
        currentACLRole == adminRole &&
            role != adminRole &&
            this.getAdminCount(rbacSecurity, rolesDefinition) == 1
    ) {
      throw CsmAccessForbiddenException(
          "RBAC ${rbacSecurity.id} - It is forbidden to unset the last administrator"
      )
    }
    val accessList = rbacSecurity.accessControlList
    val entityAccess = accessList.find { it.id == entityId }
    if (entityAccess == null) {
      accessList.add(RbacAccessControl(entityId, role))
    } else {
      entityAccess.role = role
    }
    return rbacSecurity
  }

  fun getEntities(rbacSecurity: RbacSecurity): List<String> {
    return (rbacSecurity.accessControlList.map { it.id })
  }

  fun getAccessControl(rbacSecurity: RbacSecurity, entityId: String): RbacAccessControl {
    return rbacSecurity.accessControlList.find { it.id == entityId }
        ?: throw CsmResourceNotFoundException(
            "Entity $entityId not found in ${rbacSecurity.id} component"
        )
  }

  fun checkEntityExists(
      rbacSecurity: RbacSecurity,
      entityId: String,
      exceptionEntityNotFoundMessage: String,
  ): RbacAccessControl {
    return rbacSecurity.accessControlList.find { it.id == entityId }
        ?: throw CsmResourceNotFoundException(exceptionEntityNotFoundMessage)
  }

  fun removeEntity(
      rbacSecurity: RbacSecurity,
      entityId: String,
      rolesDefinition: RolesDefinition = getCommonRolesDefinition(),
  ): RbacSecurity {
    logger.info("RBAC ${rbacSecurity.id} - Removing entity $entityId from security")
    checkEntityExists(rbacSecurity, entityId, "Entity $entityId not found")
    val role = this.getEntityRole(rbacSecurity, entityId)
    if (
        role == (this.getAdminRole(rolesDefinition)) &&
            this.getAdminCount(rbacSecurity, rolesDefinition) == 1
    ) {
      throw CsmAccessForbiddenException(
          "RBAC ${rbacSecurity.id} - It is forbidden to remove the last administrator"
      )
    }
    rbacSecurity.accessControlList.removeIf { it.id == entityId }
    return rbacSecurity
  }

  fun isAdmin(rbacSecurity: RbacSecurity, rolesDefinition: RolesDefinition): Boolean {
    var isAdmin = this.isAdminToken(rbacSecurity)
    if (!isAdmin) {
      val user = getCurrentAccountIdentifier(this.csmPlatformProperties)
      val groups = getCurrentAccountGroups(this.csmPlatformProperties)
      isAdmin = this.verifyAdminRole(rbacSecurity, user, groups, rolesDefinition)
    }
    return isAdmin
  }

  internal fun verifyAdminRole(
      rbacSecurity: RbacSecurity,
      user: String,
      groups: List<String>,
      rolesDefinition: RolesDefinition,
  ): Boolean {
    logger.debug("RBAC ${rbacSecurity.id} - Verifying if $user has default admin rbac role")
    val isAdmin =
        if (rbacSecurity.accessControlList.any() { it.id == user }) {
          this.getEntityRole(rbacSecurity, user) == this.getAdminRole(rolesDefinition)
        } else {
          groups.any {
            this.getEntityRole(rbacSecurity, it) == this.getAdminRole(rolesDefinition)
          } || rbacSecurity.default == this.getAdminRole(rolesDefinition)
        }
    logger.debug("RBAC ${rbacSecurity.id} - $user has default admin rbac role: $isAdmin")
    return isAdmin
  }

  internal fun verifyUser(
      rbacSecurity: RbacSecurity,
      permission: String,
      rolesDefinition: RolesDefinition,
      user: String,
      groups: List<String>,
  ): Boolean {
    logger.debug("RBAC ${rbacSecurity.id} - Verifying $user has permission in ACL: $permission")
    val isAuthorized =
        if (rbacSecurity.accessControlList.any() { it.id == user }) {
          verifyPermissionFromRole(permission, getEntityRole(rbacSecurity, user), rolesDefinition)
        } else {
          groups.any {
            verifyPermissionFromRole(permission, getEntityRole(rbacSecurity, it), rolesDefinition)
          } || verifyPermissionFromRole(permission, rbacSecurity.default, rolesDefinition)
        }
    logger.debug("RBAC ${rbacSecurity.id} - $user has permission $permission in ACL: $isAuthorized")
    return isAuthorized
  }

  internal fun verifyDefault(
      rbacSecurity: RbacSecurity,
      permission: String,
      rolesDefinition: RolesDefinition,
  ): Boolean {
    logger.debug("RBAC ${rbacSecurity.id} - Verifying default roles for permission: $permission")
    val defaultRole = if (rbacSecurity.default == ROLE_NONE) null else rbacSecurity.default
    val isAuthorized = this.verifyPermissionFromRole(permission, defaultRole, rolesDefinition)
    logger.debug(
        "RBAC ${rbacSecurity.id} - default roles for permission $permission: $isAuthorized"
    )
    return isAuthorized
  }

  internal fun verifyRbac(
      rbacSecurity: RbacSecurity,
      permission: String,
      rolesDefinition: RolesDefinition,
      user: String,
      groups: List<String>,
  ): Boolean {
    return (this.verifyDefault(rbacSecurity, permission, rolesDefinition) ||
        this.verifyUser(rbacSecurity, permission, rolesDefinition, user, groups))
  }

  internal fun verifyPermissionFromRole(
      permission: String,
      role: String?,
      rolesDefinition: RolesDefinition,
  ): Boolean {
    return this.verifyPermission(
        permission,
        this.getRolePermissions(role, rolesDefinition.permissions),
    )
  }

  internal fun getRolePermissions(
      role: String?,
      rolesDefinition: MutableMap<String, List<String>>,
  ): List<String> {
    return rolesDefinition[role] ?: listOf()
  }

  internal fun getEntityRole(rbacSecurity: RbacSecurity, entity: String): String {
    return rbacSecurity.accessControlList
        .firstOrNull { it.id.lowercase() == entity.lowercase() }
        ?.role ?: rbacSecurity.default
  }

  internal fun getAdminCount(rbacSecurity: RbacSecurity, rolesDefinition: RolesDefinition): Int {
    return rbacSecurity.accessControlList
        .map { it.role }
        .filter { it == this.getAdminRole(rolesDefinition) }
        .count()
  }

  internal fun verifyRoleOrThrow(
      rbacSecurity: RbacSecurity,
      role: String,
      rolesDefinition: RolesDefinition,
  ) {
    if (!rolesDefinition.permissions.keys.contains(role))
        throw CsmClientException("RBAC ${rbacSecurity.id} - Role $role does not exist")
  }

  internal fun verifyPermission(permission: String, entityPermissions: List<String>): Boolean {
    return entityPermissions.contains(permission)
  }

  internal fun verifyPermissionFromRoles(
      permission: String,
      roles: List<String>,
      rolesDefinition: RolesDefinition,
  ): Boolean {
    return roles.any { role -> this.verifyPermissionFromRole(permission, role, rolesDefinition) }
  }

  internal fun isAdminToken(rbacSecurity: RbacSecurity): Boolean {
    logger.debug("RBAC ${rbacSecurity.id} - Verifying if entity has platform admin role in token")
    val isAdmin = csmAdmin.verifyCurrentRolesAdmin()
    logger.debug("RBAC ${rbacSecurity.id} - entity has platform admin role in token: $isAdmin")
    return isAdmin
  }

  internal fun getRolePermissions(role: String, rolesDefinition: RolesDefinition): List<String> {
    return rolesDefinition.permissions[role] ?: listOf()
  }

  internal fun getAdminRole(rolesDefinition: RolesDefinition): String {
    return rolesDefinition.adminRole
  }
}
