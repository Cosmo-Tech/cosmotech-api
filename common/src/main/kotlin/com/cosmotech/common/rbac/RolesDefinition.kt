// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.rbac

import org.springframework.stereotype.Component

// openapi generator takes only last term if _ in name or bad parsing if -
const val ROLE_VIEWER = "viewer"
const val ROLE_EDITOR = "editor"
const val ROLE_ADMIN = "admin"
const val ROLE_VALIDATOR = "validator"
const val ROLE_USER = "user"
const val ROLE_NONE = "none"

// apply same format rules for permission for consistency
const val PERMISSION_READ = "read"
const val PERMISSION_READ_SECURITY = "read_security"
const val PERMISSION_CREATE_CHILDREN = "create_children"
const val PERMISSION_WRITE = "write"
const val PERMISSION_WRITE_SECURITY = "write_security"
const val PERMISSION_DELETE = "delete"
const val PERMISSION_LAUNCH = "launch"
const val PERMISSION_VALIDATE = "validate"

val NO_PERMISSIONS = emptyList<String>()

val COMMON_ROLE_READER_PERMISSIONS = listOf(PERMISSION_READ)
val COMMON_ROLE_USER_PERMISSIONS =
    listOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_CREATE_CHILDREN)
val COMMON_ROLE_EDITOR_PERMISSIONS =
    listOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_CREATE_CHILDREN, PERMISSION_WRITE)
val COMMON_ROLE_ADMIN_PERMISSIONS =
    listOf(
        PERMISSION_READ,
        PERMISSION_READ_SECURITY,
        PERMISSION_CREATE_CHILDREN,
        PERMISSION_WRITE,
        PERMISSION_WRITE_SECURITY,
        PERMISSION_DELETE,
    )

// Scenario roles & permissions
val RUNNER_ROLE_VIEWER_PERMISSIONS = listOf(PERMISSION_READ)
val RUNNER_ROLE_EDITOR_PERMISSIONS =
    listOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_LAUNCH, PERMISSION_WRITE)
val RUNNER_ROLE_VALIDATOR_PERMISSIONS =
    listOf(
        PERMISSION_READ,
        PERMISSION_READ_SECURITY,
        PERMISSION_LAUNCH,
        PERMISSION_WRITE,
        PERMISSION_VALIDATE)
val RUNNER_ROLE_ADMIN_PERMISSIONS =
    listOf(
        PERMISSION_READ,
        PERMISSION_READ_SECURITY,
        PERMISSION_LAUNCH,
        PERMISSION_WRITE,
        PERMISSION_VALIDATE,
        PERMISSION_WRITE_SECURITY,
        PERMISSION_DELETE,
    )

@Component
data class RolesDefinition(
    val permissions: MutableMap<String, List<String>> = mutableMapOf(),
    val adminRole: String = ROLE_ADMIN
)

fun getAllRolesDefinition(): Map<String, MutableMap<String, MutableList<String>>> {
  return mapOf(
      "organization" to
          getCommonRolesDefinition()
              .permissions
              .mapValues { it.value.toMutableList() }
              .toMutableMap(),
      "workspace" to
          getCommonRolesDefinition()
              .permissions
              .mapValues { it.value.toMutableList() }
              .toMutableMap(),
      "runner" to
          getRunnerRolesDefinition()
              .permissions
              .mapValues { it.value.toMutableList() }
              .toMutableMap())
}

fun getPermissions(role: String, rolesDefinition: RolesDefinition): List<String> {
  return rolesDefinition.permissions[role] ?: mutableListOf()
}

fun getCommonRolesDefinition(): RolesDefinition {
  return RolesDefinition(
      permissions =
          mutableMapOf(
              ROLE_NONE to NO_PERMISSIONS,
              ROLE_VIEWER to COMMON_ROLE_READER_PERMISSIONS,
              ROLE_USER to COMMON_ROLE_USER_PERMISSIONS,
              ROLE_EDITOR to COMMON_ROLE_EDITOR_PERMISSIONS,
              ROLE_ADMIN to COMMON_ROLE_ADMIN_PERMISSIONS,
          ),
      adminRole = ROLE_ADMIN)
}

fun getRunnerRolesDefinition(): RolesDefinition {
  return RolesDefinition(
      permissions =
          mutableMapOf(
              ROLE_NONE to NO_PERMISSIONS,
              ROLE_VIEWER to RUNNER_ROLE_VIEWER_PERMISSIONS,
              ROLE_EDITOR to RUNNER_ROLE_EDITOR_PERMISSIONS,
              ROLE_VALIDATOR to RUNNER_ROLE_VALIDATOR_PERMISSIONS,
              ROLE_ADMIN to RUNNER_ROLE_ADMIN_PERMISSIONS,
          ),
      adminRole = ROLE_ADMIN)
}
