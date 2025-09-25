// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.rbac

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.exceptions.CsmClientException
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.rbac.model.RbacAccessControl
import com.cosmotech.api.rbac.model.RbacSecurity
import com.cosmotech.api.utils.getCurrentAccountIdentifier
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Suppress("TooManyFunctions")
@Component
open class CsmRbac(
    protected val csmPlatformProperties: CsmPlatformProperties,
    protected val csmAdmin: CsmAdmin
) {

  private val logger: Logger = LoggerFactory.getLogger(this::class.java)

  fun initSecurity(security: RbacSecurity): RbacSecurity {
    var objectSecurity = security.copy()

    // Check for duplicate identities
    val accessControls = mutableListOf<String>()
    objectSecurity.accessControlList.forEach {
      if (accessControls.contains(it.id)) {
        throw IllegalArgumentException("User ${it.id} is referenced multiple times in the security")
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
      rolesDefinition: RolesDefinition = getCommonRolesDefinition()
  ) {
    if (!this.check(rbacSecurity, permission, rolesDefinition))
        throw CsmAccessForbiddenException(
            "RBAC ${rbacSecurity.id} - User does not have permission $permission")
  }

  fun check(
      rbacSecurity: RbacSecurity,
      permission: String,
      rolesDefinition: RolesDefinition = getCommonRolesDefinition()
  ): Boolean {
    logger.info("RBAC ${rbacSecurity.id} - Verifying permission $permission for user")
    if (!this.csmPlatformProperties.rbac.enabled) {
      logger.debug("RBAC ${rbacSecurity.id} - RBAC check not enabled")
      return true
    }
    var userIsAdminOrHasPermission = this.isAdmin(rbacSecurity, rolesDefinition)
    if (!userIsAdminOrHasPermission) {
      val user = getCurrentAccountIdentifier(this.csmPlatformProperties)
      userIsAdminOrHasPermission = this.verifyRbac(rbacSecurity, permission, rolesDefinition, user)
    }
    return userIsAdminOrHasPermission
  }

  fun setDefault(
      rbacSecurity: RbacSecurity,
      defaultRole: String,
      rolesDefinition: RolesDefinition = getCommonRolesDefinition()
  ): RbacSecurity {
    logger.info("RBAC ${rbacSecurity.id} - Setting default security")
    if (defaultRole != ROLE_NONE) {
      this.verifyRoleOrThrow(rbacSecurity, defaultRole, rolesDefinition)
    }
    rbacSecurity.default = defaultRole
    return rbacSecurity
  }

  fun addUserRole(
      parentRbacSecurity: RbacSecurity,
      rbacSecurity: RbacSecurity,
      userId: String,
      role: String,
      rolesDefinition: RolesDefinition = getCommonRolesDefinition()
  ): RbacSecurity {

    if (!isAdmin(rbacSecurity, rolesDefinition)) {
      this.checkUserExists(
          parentRbacSecurity,
          userId,
          "User $userId not found in parent ${parentRbacSecurity.id} component")
    }
    return setUserRole(rbacSecurity, userId, role, rolesDefinition)
  }

  fun setUserRole(
      rbacSecurity: RbacSecurity,
      userId: String,
      role: String,
      rolesDefinition: RolesDefinition = getCommonRolesDefinition()
  ): RbacSecurity {
    logger.info("RBAC ${rbacSecurity.id} - Setting user $userId roles")
    this.verifyRoleOrThrow(rbacSecurity, role, rolesDefinition)
    val currentACLRole =
        rbacSecurity.accessControlList.firstOrNull { it.id.lowercase() == userId.lowercase() }?.role
    val adminRole = this.getAdminRole(rolesDefinition)
    if (currentACLRole == adminRole &&
        role != adminRole &&
        this.getAdminCount(rbacSecurity, rolesDefinition) == 1) {
      throw CsmAccessForbiddenException(
          "RBAC ${rbacSecurity.id} - It is forbidden to unset the last administrator")
    }
    val accessList = rbacSecurity.accessControlList
    val userAccess = accessList.find { it.id == userId }
    if (userAccess == null) {
      accessList.add(RbacAccessControl(userId, role))
    } else {
      userAccess.role = role
    }
    return rbacSecurity
  }

  fun getUsers(rbacSecurity: RbacSecurity): List<String> {
    return (rbacSecurity.accessControlList.map { it.id })
  }

  fun getAccessControl(rbacSecurity: RbacSecurity, userId: String): RbacAccessControl {
    return rbacSecurity.accessControlList.find { it.id == userId }
        ?: throw CsmResourceNotFoundException(
            "User $userId not found in ${rbacSecurity.id} component")
  }

  fun checkUserExists(
      rbacSecurity: RbacSecurity,
      userId: String,
      exceptionUserNotFoundMessage: String
  ): RbacAccessControl {
    return rbacSecurity.accessControlList.find { it.id == userId }
        ?: throw CsmResourceNotFoundException(exceptionUserNotFoundMessage)
  }

  fun removeUser(
      rbacSecurity: RbacSecurity,
      userId: String,
      rolesDefinition: RolesDefinition = getCommonRolesDefinition()
  ): RbacSecurity {
    logger.info("RBAC ${rbacSecurity.id} - Removing user $userId from security")
    checkUserExists(rbacSecurity, userId, "User $userId not found")
    val role = this.getUserRole(rbacSecurity, userId)
    if (role == (this.getAdminRole(rolesDefinition)) &&
        this.getAdminCount(rbacSecurity, rolesDefinition) == 1) {
      throw CsmAccessForbiddenException(
          "RBAC ${rbacSecurity.id} - It is forbidden to remove the last administrator")
    }
    rbacSecurity.accessControlList.removeIf { it.id == userId }
    return rbacSecurity
  }

  fun isAdmin(rbacSecurity: RbacSecurity, rolesDefinition: RolesDefinition): Boolean {
    var isAdmin = this.isAdminToken(rbacSecurity)
    if (!isAdmin) {
      val user = getCurrentAccountIdentifier(this.csmPlatformProperties)
      isAdmin = this.verifyAdminRole(rbacSecurity, user, rolesDefinition)
    }
    return isAdmin
  }

  internal fun verifyAdminRole(
      rbacSecurity: RbacSecurity,
      user: String,
      rolesDefinition: RolesDefinition
  ): Boolean {
    logger.debug("RBAC ${rbacSecurity.id} - Verifying if $user has default admin rbac role")
    val isAdmin = this.getUserRole(rbacSecurity, user) == this.getAdminRole(rolesDefinition)
    logger.debug("RBAC ${rbacSecurity.id} - $user has default admin rbac role: $isAdmin")
    return isAdmin
  }

  internal fun verifyUser(
      rbacSecurity: RbacSecurity,
      permission: String,
      rolesDefinition: RolesDefinition,
      user: String
  ): Boolean {
    logger.debug("RBAC ${rbacSecurity.id} - Verifying $user has permission in ACL: $permission")
    val isAuthorized =
        this.verifyPermissionFromRole(permission, getUserRole(rbacSecurity, user), rolesDefinition)
    logger.debug("RBAC ${rbacSecurity.id} - $user has permission $permission in ACL: $isAuthorized")
    return isAuthorized
  }

  internal fun verifyDefault(
      rbacSecurity: RbacSecurity,
      permission: String,
      rolesDefinition: RolesDefinition
  ): Boolean {
    logger.debug("RBAC ${rbacSecurity.id} - Verifying default roles for permission: $permission")
    val defaultRole = if (rbacSecurity.default == ROLE_NONE) null else rbacSecurity.default
    val isAuthorized = this.verifyPermissionFromRole(permission, defaultRole, rolesDefinition)
    logger.debug(
        "RBAC ${rbacSecurity.id} - default roles for permission $permission: $isAuthorized")
    return isAuthorized
  }

  internal fun verifyRbac(
      rbacSecurity: RbacSecurity,
      permission: String,
      rolesDefinition: RolesDefinition,
      user: String
  ): Boolean {
    return (this.verifyDefault(rbacSecurity, permission, rolesDefinition) ||
        this.verifyUser(rbacSecurity, permission, rolesDefinition, user))
  }

  internal fun verifyPermissionFromRole(
      permission: String,
      role: String?,
      rolesDefinition: RolesDefinition
  ): Boolean {
    return this.verifyPermission(
        permission, this.getRolePermissions(role, rolesDefinition.permissions))
  }

  internal fun getRolePermissions(
      role: String?,
      rolesDefinition: MutableMap<String, List<String>>
  ): List<String> {
    return rolesDefinition[role] ?: listOf()
  }

  internal fun getUserRole(rbacSecurity: RbacSecurity, user: String): String {
    return rbacSecurity.accessControlList
        .firstOrNull { it.id.lowercase() == user.lowercase() }
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
      rolesDefinition: RolesDefinition
  ) {
    if (!rolesDefinition.permissions.keys.contains(role))
        throw CsmClientException("RBAC ${rbacSecurity.id} - Role $role does not exist")
  }

  internal fun verifyPermission(permission: String, userPermissions: List<String>): Boolean {
    return userPermissions.contains(permission)
  }

  internal fun verifyPermissionFromRoles(
      permission: String,
      roles: List<String>,
      rolesDefinition: RolesDefinition
  ): Boolean {
    return roles.any { role -> this.verifyPermissionFromRole(permission, role, rolesDefinition) }
  }

  internal fun isAdminToken(rbacSecurity: RbacSecurity): Boolean {
    logger.debug("RBAC ${rbacSecurity.id} - Verifying if user has platform admin role in token")
    val isAdmin = csmAdmin.verifyCurrentRolesAdmin()
    logger.debug("RBAC ${rbacSecurity.id} - user has platform admin role in token: $isAdmin")
    return isAdmin
  }

  internal fun getRolePermissions(role: String, rolesDefinition: RolesDefinition): List<String> {
    return rolesDefinition.permissions[role] ?: listOf()
  }

  internal fun getAdminRole(rolesDefinition: RolesDefinition): String {
    return rolesDefinition.adminRole
  }
}
