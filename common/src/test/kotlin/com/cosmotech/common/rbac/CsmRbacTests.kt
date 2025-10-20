// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.rbac

import com.cosmotech.common.config.CsmPlatformProperties
import com.cosmotech.common.exceptions.CsmAccessForbiddenException
import com.cosmotech.common.exceptions.CsmClientException
import com.cosmotech.common.exceptions.CsmResourceNotFoundException
import com.cosmotech.common.rbac.model.RbacAccessControl
import com.cosmotech.common.rbac.model.RbacSecurity
import com.cosmotech.common.security.ROLE_ORGANIZATION_USER
import com.cosmotech.common.security.ROLE_PLATFORM_ADMIN
import com.cosmotech.common.utils.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder

const val PERM_READ = "readtestperm"
const val PERM_WRITE = "writetestperm"
const val PERM_ADMIN = "admintestperm"

const val ROLE_READER = "readertestrole"
const val ROLE_WRITER = "writertestrole"
const val ROLE_ADMIN = "admin"
const val ROLE_NOTIN = "notintestrole"

const val USER_WRITER = "usertestwriter@cosmotech.com"
const val USER_READER = "usertestreader@cosmotech.com"
const val USER_NONE = "usertestnone@cosmotech.com"
const val USER_IN_PARENT = "usertestinparent@cosmotech.com"
const val USER_ADMIN = "usertestadmin@cosmotech.com"
const val USER_ADMIN_2 = "usertestadmin2@cosmotech.com"
const val USER_NOTIN = "usertestnotin@cosmotech.com"
const val USER_MAIL_TOKEN = "john.doe@cosmotech.com"

const val USER_NEW_READER = "usertestnew@cosmotech.com"

const val APP_REG_ID = "f6fbd519-9a53-4c6b-aabb-dfre52s16742"
const val COMPONENT_ID = "component_id"

@Suppress("LargeClass")
class CsmRbacTests {
  private val ROLE_NONE_PERMS: List<String> = listOf()
  private val ROLE_READER_PERMS = listOf(PERM_READ)
  private val ROLE_WRITER_PERMS = listOf(PERM_READ, PERM_WRITE)
  private val ROLE_ADMIN_PERMS = listOf(PERM_ADMIN)
  val CUSTOM_ADMIN_GROUP = "MyCustomAdminGroup"
  val CUSTOM_USER_GROUP = "MyCustomUserGroup"
  val CUSTOM_VIEWER_GROUP = "MyCustomViewerGroup"

  private val USER_READER_ROLE = ROLE_READER
  private val USER_WRITER_ROLE = ROLE_WRITER
  private val USER_ADMIN_ROLE = ROLE_ADMIN
  private val USER_NONE_ROLE = ""

  private val logger: Logger = LoggerFactory.getLogger(this::class.java)

  private lateinit var csmPlatformProperties: CsmPlatformProperties
  private lateinit var admin: CsmAdmin
  private lateinit var rbac: CsmRbac
  private lateinit var securityContext: SecurityContext

  private lateinit var rolesDefinition: RolesDefinition

  lateinit var parentRbacSecurity: RbacSecurity
  lateinit var rbacSecurity: RbacSecurity

  private val DEFAULT_IDENTITY_PROVIDER =
      CsmPlatformProperties.CsmIdentityProvider(
          authorizationUrl = "http://my-fake-authorization.url/autorize",
          tokenUrl = "http://my-fake-token.url/token",
          adminGroup = CUSTOM_ADMIN_GROUP,
          userGroup = CUSTOM_USER_GROUP,
          viewerGroup = CUSTOM_VIEWER_GROUP,
          serverBaseUrl = "http://localhost:8080/",
          identity =
              CsmPlatformProperties.CsmIdentityProvider.CsmIdentity(
                  tenantId = "my_tenant_id",
                  clientId = "my_client_id",
                  clientSecret = "my_client_secret"))

  @BeforeTest
  fun beforeEachTest() {
    logger.trace("Begin test")
    csmPlatformProperties = mockk<CsmPlatformProperties>()
    every { csmPlatformProperties.rbac.enabled } answers { true }
    every { csmPlatformProperties.authorization.rolesJwtClaim } answers { "roles" }
    every { csmPlatformProperties.authorization.mailJwtClaim } answers { "upn" }
    every { csmPlatformProperties.authorization.applicationIdJwtClaim } answers { "oid" }
    every { csmPlatformProperties.identityProvider } answers { DEFAULT_IDENTITY_PROVIDER }
    rolesDefinition =
        RolesDefinition(
            adminRole = ROLE_ADMIN,
            permissions =
                mutableMapOf(
                    ROLE_READER to ROLE_READER_PERMS,
                    ROLE_WRITER to ROLE_WRITER_PERMS,
                    ROLE_ADMIN to ROLE_ADMIN_PERMS,
                ))

    parentRbacSecurity =
        RbacSecurity(
            COMPONENT_ID,
            ROLE_READER,
            mutableListOf(
                RbacAccessControl(USER_WRITER, USER_WRITER_ROLE),
                RbacAccessControl(USER_READER, USER_READER_ROLE),
                RbacAccessControl(USER_IN_PARENT, USER_READER_ROLE),
                RbacAccessControl(USER_NONE, USER_NONE_ROLE),
                RbacAccessControl(USER_ADMIN, USER_ADMIN_ROLE),
                RbacAccessControl(USER_MAIL_TOKEN, USER_READER_ROLE),
            ))

    rbacSecurity =
        RbacSecurity(
            COMPONENT_ID,
            ROLE_READER,
            mutableListOf(
                RbacAccessControl(USER_WRITER, USER_WRITER_ROLE),
                RbacAccessControl(USER_READER, USER_READER_ROLE),
                RbacAccessControl(USER_NONE, USER_NONE_ROLE),
                RbacAccessControl(USER_ADMIN, USER_ADMIN_ROLE),
                RbacAccessControl(USER_MAIL_TOKEN, USER_READER_ROLE),
                RbacAccessControl(APP_REG_ID, ROLE_USER)))

    admin = CsmAdmin(csmPlatformProperties)
    rbac = CsmRbac(csmPlatformProperties, admin)

    securityContext = mockk<SecurityContext>()

    mockkStatic("org.springframework.security.core.context.SecurityContextHolder")
    every { SecurityContextHolder.getContext() } returns securityContext

    mockkStatic(::getCurrentAuthenticatedRoles)
    every { getCurrentAccountIdentifier(csmPlatformProperties) } returns USER_NOTIN
  }

  @Test
  fun `rbac option is true by default`() {
    assertFalse(CsmPlatformProperties.CsmRbac().enabled)
  }

  // CsmAdmin tests
  @Test
  fun `role Platform Admin OK`() {
    val userRoles = listOf(ROLE_PLATFORM_ADMIN)
    assertTrue(admin.verifyRolesAdmin(userRoles))
  }

  @Test
  fun `Custom role Platform Admin OK`() {
    val userRoles = listOf(CUSTOM_ADMIN_GROUP)
    assertTrue(admin.verifyRolesAdmin(userRoles))
  }

  @Test
  fun `Custom role and regular Platform Admin OK`() {
    val userRoles = listOf(CUSTOM_ADMIN_GROUP, ROLE_PLATFORM_ADMIN)
    assertTrue(admin.verifyRolesAdmin(userRoles))
  }

  @Test
  fun `Custom role Platform Admin NOK`() {
    val userRoles = listOf(CUSTOM_USER_GROUP, CUSTOM_VIEWER_GROUP)
    assertFalse(admin.verifyRolesAdmin(userRoles))
  }

  @Test
  fun `roles with Platform Admin OK`() {
    val userRoles = listOf(ROLE_PLATFORM_ADMIN, ROLE_ORGANIZATION_USER)
    assertTrue(admin.verifyRolesAdmin(userRoles))
  }

  @Test
  fun `role Organization User KO`() {
    val userRoles = listOf(ROLE_ORGANIZATION_USER)
    assertFalse(admin.verifyRolesAdmin(userRoles))
  }

  @Test
  fun `No role KO`() {
    val userRoles: List<String> = listOf()
    assertFalse(admin.verifyRolesAdmin(userRoles))
  }

  @Test
  fun `current user role Admin OK`() {
    every { getCurrentAuthenticatedRoles(csmPlatformProperties) } returns
        listOf(ROLE_PLATFORM_ADMIN)
    assertTrue(admin.verifyCurrentRolesAdmin())
  }

  @Test
  fun `current user role Admin KO`() {
    every { getCurrentAuthenticatedRoles(csmPlatformProperties) } returns
        listOf(ROLE_ORGANIZATION_USER)
    assertFalse(admin.verifyCurrentRolesAdmin())
  }

  // CsmRBac tests
  @Test
  fun `verify permission read OK`() {
    assertTrue(rbac.verifyPermission(PERM_READ, ROLE_READER_PERMS))
  }

  @Test
  fun `verify permission read KO`() {
    assertFalse(rbac.verifyPermission(PERM_READ, ROLE_NONE_PERMS))
  }

  @Test
  fun `get permission from role`() {
    assertEquals(ROLE_READER_PERMS, rbac.getRolePermissions(ROLE_READER, rolesDefinition))
  }

  @Test
  fun `get permission from bad role`() {
    assertEquals(listOf(), rbac.getRolePermissions(ROLE_NOTIN, rolesDefinition))
  }

  @Test
  fun `verify permission read from role reader OK`() {
    assertTrue(rbac.verifyPermissionFromRole(PERM_READ, ROLE_READER, rolesDefinition))
  }

  @Test
  fun `verify permission write from role reader KO`() {
    assertFalse(rbac.verifyPermissionFromRole(PERM_WRITE, ROLE_READER, rolesDefinition))
  }

  @Test
  fun `verify permission read from role writer OK`() {
    assertTrue(rbac.verifyPermissionFromRole(PERM_READ, ROLE_WRITER, rolesDefinition))
  }

  @Test
  fun `verify permission writer from roles reader writer OK`() {
    assertTrue(
        rbac.verifyPermissionFromRoles(
            PERM_READ, listOf(USER_WRITER_ROLE, USER_READER_ROLE), rolesDefinition))
  }

  @Test
  fun `find role for user from resource security`() {
    assertEquals(ROLE_READER, rbac.getUserRole(rbacSecurity, USER_READER))
  }

  @Test
  fun `find roles for admin from resource security`() {
    assertEquals(ROLE_ADMIN, rbac.getUserRole(rbacSecurity, USER_ADMIN))
  }

  @Test
  fun `verify permission read for user writer OK`() {
    assertTrue(rbac.verifyUser(rbacSecurity, PERM_READ, rolesDefinition, USER_READER, emptyList()))
  }

  @Test
  fun `verify permission write for user writer KO`() {
    assertFalse(
        rbac.verifyUser(rbacSecurity, PERM_WRITE, rolesDefinition, USER_READER, emptyList()))
  }

  @Test
  fun `verify permission read for user none KO`() {
    assertFalse(rbac.verifyUser(rbacSecurity, PERM_READ, rolesDefinition, USER_NONE, emptyList()))
  }

  @Test
  fun `verify permission read from default security OK`() {
    assertTrue(rbac.verifyDefault(rbacSecurity, PERM_READ, rolesDefinition))
  }

  @Test
  fun `verify permission write from default security KO`() {
    assertFalse(rbac.verifyDefault(rbacSecurity, PERM_WRITE, rolesDefinition))
  }

  @Test
  fun `add new reader user and verify read permission OK`() {
    rbac.setUserRole(rbacSecurity, USER_NEW_READER, USER_READER_ROLE, rolesDefinition)
    assertTrue(
        rbac.verifyUser(rbacSecurity, PERM_READ, rolesDefinition, USER_NEW_READER, emptyList()))
  }

  @Test
  fun `add new reader user and verify write permission KO`() {
    rbac.setUserRole(rbacSecurity, USER_NEW_READER, USER_READER_ROLE, rolesDefinition)
    assertFalse(
        rbac.verifyUser(rbacSecurity, PERM_WRITE, rolesDefinition, USER_NEW_READER, emptyList()))
  }

  @Test
  fun `adding a user with role none throws exception`() {
    assertThrows<CsmClientException> {
      rbac.setUserRole(rbacSecurity, USER_NEW_READER, ROLE_NONE, rolesDefinition)
    }
  }

  @Test
  fun `should throw NotFoundException if user not in parent user list and not admin`() {
    every { getCurrentAccountIdentifier(csmPlatformProperties) } returns USER_NONE
    every { getCurrentAuthenticatedRoles(csmPlatformProperties) } returns
        listOf(ROLE_ORGANIZATION_USER)
    assertThrows<CsmResourceNotFoundException> {
      rbac.addUserRole(
          parentRbacSecurity, rbacSecurity, USER_NOTIN, USER_READER_ROLE, rolesDefinition)
    }
  }

  @Test
  fun `should add User if user in parent user list and not admin`() {
    every { getCurrentAccountIdentifier(csmPlatformProperties) } returns USER_NONE
    every { getCurrentAuthenticatedRoles(csmPlatformProperties) } returns
        listOf(ROLE_ORGANIZATION_USER)
    val rbacSecurity =
        rbac.addUserRole(
            parentRbacSecurity, rbacSecurity, USER_IN_PARENT, USER_READER_ROLE, rolesDefinition)
    assertTrue(
        rbac.verifyUser(rbacSecurity, PERM_READ, rolesDefinition, USER_IN_PARENT, emptyList()))
  }

  @Test
  fun `should add User role if admin without checking parent RBAC`() {
    every { getCurrentAuthenticatedRoles(csmPlatformProperties) } returns
        listOf(ROLE_PLATFORM_ADMIN)
    val rbacSecurity =
        rbac.addUserRole(
            parentRbacSecurity, rbacSecurity, USER_NOTIN, USER_READER_ROLE, rolesDefinition)
    assertTrue(rbac.verifyUser(rbacSecurity, PERM_READ, rolesDefinition, USER_NOTIN, emptyList()))
  }

  @Test
  fun `remove new reader user and verify read permission KO with default none`() {
    rbacSecurity = RbacSecurity(COMPONENT_ID, ROLE_NONE, mutableListOf())

    rbac.setUserRole(rbacSecurity, USER_NEW_READER, USER_READER_ROLE, rolesDefinition)
    rbac.removeUser(rbacSecurity, USER_NEW_READER, rolesDefinition)
    assertFalse(
        rbac.verifyUser(rbacSecurity, PERM_READ, rolesDefinition, USER_NEW_READER, emptyList()))
  }

  @Test
  fun `remove new reader user and verify read permission OK with default reader`() {
    rbacSecurity = RbacSecurity(COMPONENT_ID, ROLE_READER, mutableListOf())

    rbac.setUserRole(rbacSecurity, USER_NEW_READER, USER_READER_ROLE, rolesDefinition)
    rbac.removeUser(rbacSecurity, USER_NEW_READER, rolesDefinition)
    assertTrue(
        rbac.verifyUser(rbacSecurity, PERM_READ, rolesDefinition, USER_NEW_READER, emptyList()))
  }

  @Test
  fun `remove new reader user and verify read permission OK with default admin`() {
    rbacSecurity = RbacSecurity(COMPONENT_ID, ROLE_ADMIN, mutableListOf())

    rbac.setUserRole(rbacSecurity, USER_NEW_READER, USER_READER_ROLE, rolesDefinition)
    rbac.removeUser(rbacSecurity, USER_NEW_READER, rolesDefinition)
    assertTrue(
        rbac.verifyUser(rbacSecurity, PERM_ADMIN, rolesDefinition, USER_NEW_READER, emptyList()))
  }

  @Test
  fun `update existing new user and verify write permission OK`() {
    rbac.setUserRole(rbacSecurity, USER_NEW_READER, USER_WRITER_ROLE, rolesDefinition)
    assertTrue(
        rbac.verifyUser(rbacSecurity, PERM_WRITE, rolesDefinition, USER_NEW_READER, emptyList()))
  }

  @Test
  fun `update existing new user and verify read permission OK`() {
    rbac.setUserRole(rbacSecurity, USER_NEW_READER, USER_READER_ROLE, rolesDefinition)
    assertTrue(
        rbac.verifyUser(rbacSecurity, PERM_READ, rolesDefinition, USER_NEW_READER, emptyList()))
  }

  @Test
  fun `user with no roles has default read permission`() {
    assertTrue(rbac.verifyRbac(rbacSecurity, PERM_READ, rolesDefinition, USER_NONE, emptyList()))
  }

  @Test
  fun `update default security to no roles and verify read OK for reader user`() {
    rbac.setDefault(rbacSecurity, USER_READER_ROLE, rolesDefinition)
    assertTrue(rbac.verifyRbac(rbacSecurity, PERM_READ, rolesDefinition, USER_READER, emptyList()))
  }

  @Test
  fun `update default security to writer role and verify write OK for reader user`() {
    rbac.setDefault(rbacSecurity, USER_WRITER_ROLE, rolesDefinition)
    assertTrue(rbac.verifyRbac(rbacSecurity, PERM_WRITE, rolesDefinition, USER_READER, emptyList()))
  }

  @Test
  fun `update default security to roles not in permission KO`() {
    assertThrows<CsmClientException> { rbac.setDefault(rbacSecurity, ROLE_NOTIN, rolesDefinition) }
  }

  @Test
  fun `check admin user with PLATFORM USER token role write OK`() {
    every { getCurrentAuthenticatedRoles(csmPlatformProperties) } returns
        listOf(ROLE_ORGANIZATION_USER)
    every { getCurrentAccountIdentifier(csmPlatformProperties) } returns USER_ADMIN
    assertTrue(rbac.check(rbacSecurity, PERM_WRITE, rolesDefinition))
  }

  @Test
  fun `check none user with PLATFORM ADMIN token role write OK`() {
    every { getCurrentAuthenticatedRoles(csmPlatformProperties) } returns
        listOf(ROLE_PLATFORM_ADMIN)
    assertTrue(rbac.check(rbacSecurity, PERM_WRITE, rolesDefinition))
  }

  @Test
  fun `check writer user with PLATFORM USER token role write OK`() {
    every { getCurrentAuthenticatedRoles(csmPlatformProperties) } returns
        listOf(ROLE_ORGANIZATION_USER)
    every { getCurrentAccountIdentifier(csmPlatformProperties) } returns USER_WRITER
    assertTrue(rbac.check(rbacSecurity, PERM_WRITE, rolesDefinition))
  }

  @Test
  fun `check none user with PLATFORM USER token role write KO`() {
    every { getCurrentAuthenticatedRoles(csmPlatformProperties) } returns
        listOf(ROLE_ORGANIZATION_USER)
    every { getCurrentAccountIdentifier(csmPlatformProperties) } returns USER_NONE
    assertFalse(rbac.check(rbacSecurity, PERM_WRITE, rolesDefinition))
  }

  @Test
  fun `check reader user with PLATFORM USER token role write KO`() {
    every { getCurrentAuthenticatedRoles(csmPlatformProperties) } returns
        listOf(ROLE_ORGANIZATION_USER)
    every { getCurrentAccountIdentifier(csmPlatformProperties) } returns USER_READER
    assertFalse(rbac.check(rbacSecurity, PERM_WRITE, rolesDefinition))
  }

  @Test
  fun `check return OK if rbac flag set to false`() {
    every { getCurrentAuthenticatedRoles(csmPlatformProperties) } returns
        listOf(ROLE_ORGANIZATION_USER)
    every { csmPlatformProperties.rbac.enabled } answers { false }
    every { getCurrentAccountIdentifier(csmPlatformProperties) } returns USER_READER
    assertTrue(rbac.check(rbacSecurity, PERM_WRITE, rolesDefinition))
  }

  @Test
  fun `check return OK if rbac flag set to false for current user`() {
    every { getCurrentAuthenticatedRoles(csmPlatformProperties) } returns
        listOf(ROLE_ORGANIZATION_USER)
    every { csmPlatformProperties.rbac.enabled } answers { false }
    assertTrue(rbac.check(rbacSecurity, PERM_WRITE, rolesDefinition))
  }

  @Test
  fun `check current user with PLATFORM USER token role read OK`() {
    every { getCurrentAuthenticatedRoles(csmPlatformProperties) } returns
        listOf(ROLE_ORGANIZATION_USER)
    assertTrue(rbac.check(rbacSecurity, PERM_READ, rolesDefinition))
  }

  @Test
  fun `verify KO throw exception`() {
    every { getCurrentAuthenticatedRoles(csmPlatformProperties) } returns
        listOf(ROLE_ORGANIZATION_USER)
    every { getCurrentAccountIdentifier(csmPlatformProperties) } returns USER_READER
    assertThrows<CsmAccessForbiddenException> {
      rbac.verify(rbacSecurity, PERM_WRITE, rolesDefinition)
    }
  }

  @Test
  fun `verify OK does not throw exception`() {
    every { getCurrentAuthenticatedRoles(csmPlatformProperties) } returns
        listOf(ROLE_ORGANIZATION_USER)
    every { getCurrentAccountIdentifier(csmPlatformProperties) } returns USER_WRITER
    assertDoesNotThrow { rbac.verify(rbacSecurity, PERM_WRITE, rolesDefinition) }
  }

  @Test
  fun `verify KO current user throw exception`() {
    every { getCurrentAuthenticatedRoles(csmPlatformProperties) } returns
        listOf(ROLE_ORGANIZATION_USER)
    assertThrows<CsmAccessForbiddenException> {
      rbac.verify(rbacSecurity, PERM_WRITE, rolesDefinition)
    }
  }

  @Test
  fun `verify OK organization user does not throw exception`() {
    every { getCurrentAuthenticatedRoles(csmPlatformProperties) } returns
        listOf(ROLE_ORGANIZATION_USER)
    assertDoesNotThrow { rbac.verify(rbacSecurity, PERM_READ, rolesDefinition) }
  }

  @Test
  fun `verify return OK if rbac flag set to false`() {
    every { csmPlatformProperties.rbac.enabled } answers { false }
    every { getCurrentAuthenticatedRoles(csmPlatformProperties) } returns
        listOf(ROLE_ORGANIZATION_USER)
    assertDoesNotThrow { rbac.verify(rbacSecurity, PERM_WRITE, rolesDefinition) }
  }

  @Test
  fun `owner with PLATFORM USER token not admin with rbac`() {
    every { getCurrentAuthenticatedRoles(csmPlatformProperties) } returns
        listOf("Organization.User")
    every { getCurrentAccountIdentifier(csmPlatformProperties) } returns USER_NONE
    assertFalse(rbac.isAdmin(rbacSecurity, rolesDefinition))
  }

  @Test
  fun `user with PLATFORM ADMIN roles admin with rbac`() {
    every { getCurrentAuthenticatedRoles(csmPlatformProperties) } returns
        listOf(ROLE_PLATFORM_ADMIN)
    assertTrue(rbac.isAdmin(rbacSecurity, rolesDefinition))
  }

  @Test
  fun `get special admin role`() {
    assertEquals(ROLE_ADMIN, rbac.getAdminRole(rolesDefinition))
  }

  @Test
  fun `user has admin role`() {
    every { getCurrentAuthenticatedRoles(csmPlatformProperties) } returns
        listOf(ROLE_ORGANIZATION_USER)
    assertTrue(rbac.verifyAdminRole(rbacSecurity, USER_ADMIN, emptyList(), rolesDefinition))
  }

  @Test
  fun `user has not admin role`() {
    every { getCurrentAuthenticatedRoles(csmPlatformProperties) } returns
        listOf(ROLE_ORGANIZATION_USER)
    assertFalse(rbac.verifyAdminRole(rbacSecurity, USER_READER, emptyList(), rolesDefinition))
  }

  @Test
  fun `readerRole is not admin`() {
    every { getCurrentAuthenticatedRoles(csmPlatformProperties) } returns
        listOf(ROLE_ORGANIZATION_USER)
    every { getCurrentAccountIdentifier(csmPlatformProperties) } returns USER_READER
    assertFalse(rbac.isAdmin(rbacSecurity, rolesDefinition))
  }

  @Test
  fun `adminRole is admin`() {
    every { getCurrentAuthenticatedRoles(csmPlatformProperties) } returns
        listOf(ROLE_ORGANIZATION_USER)
    every { getCurrentAccountIdentifier(csmPlatformProperties) } returns USER_ADMIN
    assertTrue(rbac.isAdmin(rbacSecurity, rolesDefinition))
  }

  @Test
  fun `user with PLATFORM ADMIN is admin`() {
    every { getCurrentAuthenticatedRoles(csmPlatformProperties) } returns
        listOf(ROLE_PLATFORM_ADMIN)
    assertTrue(rbac.isAdmin(rbacSecurity, rolesDefinition))
  }

  @Test
  fun `get count of users with admin role`() {
    assertEquals(1, rbac.getAdminCount(rbacSecurity, rolesDefinition))
  }

  @Test
  fun `get count of users with new admin role`() {
    rbac.setUserRole(rbacSecurity, USER_NEW_READER, USER_ADMIN_ROLE, rolesDefinition)
    assertEquals(2, rbac.getAdminCount(rbacSecurity, rolesDefinition))
  }

  @Test
  fun `throw exception if last admin deleted`() {
    assertThrows<CsmAccessForbiddenException> {
      rbac.removeUser(rbacSecurity, USER_ADMIN, rolesDefinition)
    }
  }

  @Test
  fun `throw exception if last admin from two is deleted`() {
    rbac.setUserRole(rbacSecurity, USER_NEW_READER, USER_ADMIN_ROLE, rolesDefinition)
    rbac.removeUser(rbacSecurity, USER_NEW_READER, rolesDefinition)
    assertThrows<CsmAccessForbiddenException> {
      rbac.removeUser(rbacSecurity, USER_ADMIN, rolesDefinition)
    }
  }

  @Test
  fun `throw exception if last admin removed from setRole`() {
    assertThrows<CsmAccessForbiddenException> {
      rbac.setUserRole(rbacSecurity, USER_ADMIN, USER_READER_ROLE, rolesDefinition)
    }
  }

  @Test
  fun `throw exception if role does not exist with setRole`() {
    assertThrows<CsmClientException> {
      rbac.setUserRole(rbacSecurity, USER_READER, ROLE_NOTIN, rolesDefinition)
    }
  }

  @Test
  fun `get user list`() {
    assertEquals(
        listOf(USER_WRITER, USER_READER, USER_NONE, USER_ADMIN, USER_MAIL_TOKEN, APP_REG_ID),
        rbac.getUsers(rbacSecurity))
  }

  @Test
  fun `create resource security with default admin`() {
    val resourceSecurity = rbacSecurity
    assertTrue(
        resourceSecurity.accessControlList.any {
          it.id == USER_ADMIN && it.role == rolesDefinition.adminRole
        })
  }

  @Test
  fun `create resource security with two admins`() {
    val resourceSecurity = rbacSecurity
    resourceSecurity.let {
      it.accessControlList.add(RbacAccessControl(USER_ADMIN_2, USER_ADMIN_ROLE))
    }
    assertTrue(
        (resourceSecurity.accessControlList.any {
          it.id == USER_ADMIN && it.role == rolesDefinition.adminRole
        }) &&
            (resourceSecurity.accessControlList.any {
              it.id == USER_ADMIN_2 && it.role == rolesDefinition.adminRole
            }))
  }

  // Role definition tests
  @Test
  fun `get default role definition permissions`() {
    val expected: MutableMap<String, List<String>> =
        mutableMapOf(
            ROLE_NONE to NO_PERMISSIONS,
            ROLE_VIEWER to COMMON_ROLE_READER_PERMISSIONS,
            ROLE_USER to COMMON_ROLE_USER_PERMISSIONS,
            ROLE_EDITOR to COMMON_ROLE_EDITOR_PERMISSIONS,
            ROLE_ADMIN to COMMON_ROLE_ADMIN_PERMISSIONS,
        )
    assertEquals(expected, getCommonRolesDefinition().permissions)
  }

  @Test
  fun `get default role definition default admin`() {
    assertEquals(ROLE_ADMIN, getCommonRolesDefinition().adminRole)
  }

  @Test
  fun `add custom role definition`() {
    val definition = getCommonRolesDefinition()
    val customRole = "custom_role"
    val customRolePermissions = listOf(PERMISSION_READ, "custom_permission")
    definition.permissions.put(customRole, customRolePermissions)
    val expected: MutableMap<String, List<String>> =
        mutableMapOf(
            ROLE_NONE to NO_PERMISSIONS,
            ROLE_VIEWER to COMMON_ROLE_READER_PERMISSIONS,
            ROLE_USER to COMMON_ROLE_USER_PERMISSIONS,
            ROLE_EDITOR to COMMON_ROLE_EDITOR_PERMISSIONS,
            ROLE_ADMIN to COMMON_ROLE_ADMIN_PERMISSIONS,
            customRole to customRolePermissions,
        )
    assertEquals(expected, definition.permissions)
  }

  @Test
  fun `check new permission custom ok`() {
    val definition = getCommonRolesDefinition()
    val customRole = "custom_role"
    val customPermission = "custom_permission"
    val customRolePermissions = listOf(PERMISSION_READ, customPermission)
    definition.permissions.put(customRole, customRolePermissions)
    val rbacTest = CsmRbac(csmPlatformProperties, admin)
    rbacTest.setUserRole(rbacSecurity, USER_NEW_READER, customRole, definition)
    every { getCurrentAuthenticatedRoles(csmPlatformProperties) } returns
        listOf(ROLE_ORGANIZATION_USER)
    every { getCurrentAccountIdentifier(csmPlatformProperties) } returns USER_NEW_READER
    assertTrue(rbacTest.check(rbacSecurity, customPermission, definition))
  }

  // Utility methods for rbac creation
  @Test
  fun `can add resource id and resource security in a second step`() {
    val definition = getCommonRolesDefinition()
    val rbacTest = CsmRbac(csmPlatformProperties, admin)
    rbacTest.setUserRole(rbacSecurity, USER_READER, ROLE_VIEWER, definition)
    every { getCurrentAuthenticatedRoles(csmPlatformProperties) } returns
        listOf(ROLE_ORGANIZATION_USER)
    every { getCurrentAccountIdentifier(csmPlatformProperties) } returns USER_READER
    assertTrue(rbacTest.check(rbacSecurity, PERMISSION_READ, definition))
  }

  @TestFactory
  fun `add ACL entry when default role is admin with only one admin defined`() =
      listOf(ROLE_VIEWER, ROLE_USER, ROLE_EDITOR, ROLE_ADMIN).map { role ->
        DynamicTest.dynamicTest(
            "add ACL entry $role when default role is admin (with only one admin defined)") {
              val rbacDefinition =
                  RbacSecurity(
                      id = "rbacOnlyOneAdminWithAdminDefaultRole",
                      default = ROLE_ADMIN,
                      accessControlList =
                          mutableListOf(
                              RbacAccessControl(id = "test.user@test.com", role = ROLE_ADMIN)))
              val newUserId = "whatever.user@test.com"
              rbac.setUserRole(rbacDefinition, newUserId, role, getCommonRolesDefinition())
              assertTrue(rbacDefinition.accessControlList.size == 2)
              assertTrue(
                  rbacDefinition.accessControlList.contains(
                      RbacAccessControl(id = newUserId, role = role)))
            }
      }

  @TestFactory
  fun `update ACL entry with only one admin defined`() =
      mapOf(ROLE_VIEWER to true, ROLE_USER to true, ROLE_EDITOR to true, ROLE_ADMIN to false).map {
          (role, shouldThrows) ->
        DynamicTest.dynamicTest("update ACL entry $role with only one admin defined") {
          val userId = "test.user@test.com"
          val rbacDefinition =
              RbacSecurity(
                  id = "rbacOnlyOneAdmin",
                  default = ROLE_NONE,
                  accessControlList =
                      mutableListOf(RbacAccessControl(id = userId, role = ROLE_ADMIN)))
          if (shouldThrows) {
            val assertThrows =
                assertThrows<CsmAccessForbiddenException> {
                  rbac.setUserRole(rbacDefinition, userId, role, getCommonRolesDefinition())
                }
            assertEquals(
                "RBAC ${rbacDefinition.id} - It is forbidden to unset the last administrator",
                assertThrows.message)
          } else {
            assertDoesNotThrow {
              rbac.setUserRole(rbacDefinition, userId, role, getCommonRolesDefinition())
              assertTrue(rbacDefinition.accessControlList.size == 1)
              assertTrue(
                  rbacDefinition.accessControlList.contains(
                      RbacAccessControl(id = userId, role = role)))
            }
          }
        }
      }

  @TestFactory
  fun `remove ACL entry when default role is admin with only one admin defined`() =
      listOf(ROLE_VIEWER, ROLE_USER, ROLE_EDITOR, ROLE_ADMIN).map { role ->
        DynamicTest.dynamicTest(
            "remove ACL entry $role when default role is admin (with only one admin defined)") {
              val userId = "test.user@test.com"
              val rbacDefinition =
                  RbacSecurity(
                      id = "rbacOnlyOneAdminWithAdminDefaultRole",
                      default = ROLE_ADMIN,
                      accessControlList =
                          mutableListOf(RbacAccessControl(id = userId, role = ROLE_ADMIN)))
              val assertThrows =
                  assertThrows<CsmAccessForbiddenException> {
                    rbac.removeUser(rbacDefinition, userId, getCommonRolesDefinition())
                  }
              assertEquals(
                  "RBAC ${rbacDefinition.id} - It is forbidden to remove the last administrator",
                  assertThrows.message)
            }
      }

  @TestFactory
  fun `remove ACL entry when default role is admin with an admin and another user defined`() =
      listOf(ROLE_VIEWER, ROLE_USER, ROLE_EDITOR, ROLE_ADMIN).map { role ->
        DynamicTest.dynamicTest(
            "remove ACL entry $role when default role is admin (with only one admin defined)") {
              val userId = "test.user@test.com"
              val rbacDefinition =
                  RbacSecurity(
                      id = "rbacOnlyOneAdminWithAdminDefaultRole",
                      default = ROLE_ADMIN,
                      accessControlList =
                          mutableListOf(
                              RbacAccessControl(id = USER_ADMIN, role = ROLE_ADMIN),
                              RbacAccessControl(id = userId, role = role)))
              assertDoesNotThrow {
                rbac.removeUser(rbacDefinition, userId, getCommonRolesDefinition())
                assertTrue(rbacDefinition.accessControlList.size == 1)
                assertTrue(
                    rbacDefinition.accessControlList.contains(
                        RbacAccessControl(id = USER_ADMIN, role = ROLE_ADMIN)))
              }
            }
      }

  @Test
  fun `can add resource id and resource security in one call`() {
    val definition = getCommonRolesDefinition()
    val rbacTest = CsmRbac(csmPlatformProperties, admin)
    rbacSecurity =
        RbacSecurity(
            COMPONENT_ID,
            ROLE_READER,
            mutableListOf(
                RbacAccessControl(USER_READER, ROLE_VIEWER),
            ))
    every { getCurrentAuthenticatedRoles(csmPlatformProperties) } returns
        listOf(ROLE_ORGANIZATION_USER)
    every { getCurrentAccountIdentifier(csmPlatformProperties) } returns USER_READER
    assertTrue(rbacTest.check(rbacSecurity, PERMISSION_READ, definition))
  }

  @Test
  fun `can create resource security from list and map directly`() {
    val definition = getCommonRolesDefinition()
    val rbacTest = CsmRbac(csmPlatformProperties, admin)
    rbacSecurity =
        RbacSecurity(
            COMPONENT_ID,
            ROLE_VIEWER,
            mutableListOf(
                RbacAccessControl(USER_WRITER, ROLE_EDITOR),
            ))
    every { getCurrentAuthenticatedRoles(csmPlatformProperties) } returns
        listOf(ROLE_ORGANIZATION_USER)
    every { getCurrentAccountIdentifier(csmPlatformProperties) } returns USER_READER
    assertTrue(rbacTest.check(rbacSecurity, PERMISSION_READ, definition))
  }

  @Test
  fun `can create resource security from list and map directly KO`() {
    val definition = getCommonRolesDefinition()
    val rbacTest = CsmRbac(csmPlatformProperties, admin)
    rbacSecurity =
        RbacSecurity(
            COMPONENT_ID,
            ROLE_VIEWER,
            mutableListOf(
                RbacAccessControl(USER_WRITER, ROLE_EDITOR),
            ))
    every { getCurrentAuthenticatedRoles(csmPlatformProperties) } returns
        listOf(ROLE_ORGANIZATION_USER)
    every { getCurrentAccountIdentifier(csmPlatformProperties) } returns USER_READER
    assertFalse(rbacTest.check(rbacSecurity, PERMISSION_WRITE_SECURITY, definition))
  }

  @Test
  fun `can create resource security with current user as admin`() {
    val definition = getCommonRolesDefinition()
    val rbacTest = CsmRbac(csmPlatformProperties, admin)
    rbacSecurity =
        RbacSecurity(
            COMPONENT_ID,
            ROLE_VIEWER,
            mutableListOf(
                RbacAccessControl(
                    getCurrentAccountIdentifier(csmPlatformProperties), definition.adminRole),
            ))
    every { getCurrentAuthenticatedRoles(csmPlatformProperties) } returns
        listOf(ROLE_ORGANIZATION_USER)
    every { getCurrentAccountIdentifier(csmPlatformProperties) } returns USER_MAIL_TOKEN
    assertFalse(rbacTest.check(rbacSecurity, PERMISSION_READ_SECURITY, definition))
  }

  @Test
  fun `can create resource security from list and map directly writer`() {
    val definition = getCommonRolesDefinition()
    val rbacTest = CsmRbac(csmPlatformProperties, admin)
    rbacSecurity =
        RbacSecurity(
            COMPONENT_ID,
            ROLE_VIEWER,
            mutableListOf(
                RbacAccessControl(USER_WRITER, ROLE_EDITOR),
            ))
    every { getCurrentAuthenticatedRoles(csmPlatformProperties) } returns
        listOf(ROLE_ORGANIZATION_USER)
    every { getCurrentAccountIdentifier(csmPlatformProperties) } returns USER_WRITER
    assertTrue(rbacTest.check(rbacSecurity, PERMISSION_WRITE, definition))
  }

  @Test
  fun `can create resource security from list and map directly token`() {
    val definition = getCommonRolesDefinition()
    val rbacTest = CsmRbac(csmPlatformProperties, admin)
    rbacSecurity =
        RbacSecurity(
            COMPONENT_ID,
            ROLE_VIEWER,
            mutableListOf(
                RbacAccessControl(USER_MAIL_TOKEN, ROLE_EDITOR),
            ))
    every { getCurrentAuthenticatedRoles(csmPlatformProperties) } returns
        listOf(ROLE_ORGANIZATION_USER)
    every { getCurrentAccountIdentifier(csmPlatformProperties) } returns USER_MAIL_TOKEN
    assertDoesNotThrow { rbacTest.verify(rbacSecurity, PERMISSION_WRITE, definition) }
  }

  @Test
  fun `can create resource security from list and map directly in rbac writer`() {
    val definition = getCommonRolesDefinition()
    val rbacTest = CsmRbac(csmPlatformProperties, admin)
    rbacSecurity =
        RbacSecurity(
            COMPONENT_ID,
            ROLE_VIEWER,
            mutableListOf(
                RbacAccessControl(USER_WRITER, ROLE_EDITOR),
            ))
    every { getCurrentAuthenticatedRoles(csmPlatformProperties) } returns
        listOf(ROLE_ORGANIZATION_USER)
    every { getCurrentAccountIdentifier(csmPlatformProperties) } returns USER_WRITER
    assertTrue(rbacTest.check(rbacSecurity, PERMISSION_WRITE, definition))
  }

  @Test
  fun `when removing throw 404 if user not exists`() {
    assertThrows<CsmResourceNotFoundException> { rbac.removeUser(rbacSecurity, USER_NOTIN) }
  }

  @Test
  fun `when getting AccessControl throw 404 if user not exists`() {
    assertThrows<CsmResourceNotFoundException> { rbac.getAccessControl(rbacSecurity, USER_NOTIN) }
  }

  @Test
  fun `app reg can be reader`() {
    every { getCurrentAuthenticatedRoles(csmPlatformProperties) } returns
        listOf(ROLE_ORGANIZATION_USER)
    every { getCurrentAccountIdentifier(csmPlatformProperties) } returns APP_REG_ID
    assertTrue(rbac.check(rbacSecurity, PERMISSION_READ))
  }

  @Test
  fun `app reg can be admin with Platform Admin`() {
    every { getCurrentAuthenticatedRoles(csmPlatformProperties) } returns
        listOf(ROLE_PLATFORM_ADMIN)
    every { getCurrentAccountIdentifier(csmPlatformProperties) } returns APP_REG_ID
    assertTrue(rbac.isAdmin(rbacSecurity, getCommonRolesDefinition()))
  }

  @Test
  fun `app reg can be admin with default admin`() {
    every { getCurrentAuthenticatedRoles(csmPlatformProperties) } returns
        listOf(ROLE_ORGANIZATION_USER)
    every { getCurrentAccountIdentifier(csmPlatformProperties) } returns APP_REG_ID
    val emptyRbacSecuWithDefaultAdmin = RbacSecurity(COMPONENT_ID, ROLE_ADMIN, mutableListOf())
    assertTrue(rbac.isAdmin(emptyRbacSecuWithDefaultAdmin, getCommonRolesDefinition()))
  }

  @Test
  fun `app reg can be admin with ACL admin`() {
    every { getCurrentAuthenticatedRoles(csmPlatformProperties) } returns
        listOf(ROLE_ORGANIZATION_USER)
    every { getCurrentAccountIdentifier(csmPlatformProperties) } returns APP_REG_ID
    val emptyRbacSecuWithDefaultNone =
        RbacSecurity(
            COMPONENT_ID, ROLE_NONE, mutableListOf(RbacAccessControl(APP_REG_ID, ROLE_ADMIN)))
    assertTrue(rbac.isAdmin(emptyRbacSecuWithDefaultNone, getCommonRolesDefinition()))
  }

  @Test
  fun `verifyRbac for app_reg`() {
    every { getCurrentAuthenticatedRoles(csmPlatformProperties) } returns
        listOf(ROLE_ORGANIZATION_USER)
    every { getCurrentAccountIdentifier(csmPlatformProperties) } returns APP_REG_ID
    assertTrue(
        rbac.verifyRbac(
            rbacSecurity, PERMISSION_READ, getCommonRolesDefinition(), APP_REG_ID, emptyList()))
  }

  @Test
  fun `initSecurity empty ACL`() {
    val inputSecurity = RbacSecurity(COMPONENT_ID, ROLE_NONE, mutableListOf())
    val security = rbac.initSecurity(inputSecurity)
    assertEquals(
        inputSecurity.copy(
            accessControlList = mutableListOf(RbacAccessControl(USER_NOTIN, ROLE_ADMIN))),
        security)
  }

  @Test
  fun `initSecurity duplicate ACL`() {
    assertThrows<IllegalArgumentException> {
      rbac.initSecurity(
          RbacSecurity(
              COMPONENT_ID,
              ROLE_NONE,
              mutableListOf(
                  RbacAccessControl(USER_READER, ROLE_USER),
                  RbacAccessControl(USER_READER, ROLE_ADMIN))))
    }
  }

  @Test
  fun `initSecurity no admin in ACL without current user`() {
    val inputSecurity =
        RbacSecurity(
            COMPONENT_ID, ROLE_NONE, mutableListOf(RbacAccessControl(USER_NOTIN, ROLE_USER)))
    val security = rbac.initSecurity(inputSecurity)
    assertEquals(
        inputSecurity.copy(
            accessControlList = mutableListOf(RbacAccessControl(USER_NOTIN, ROLE_ADMIN))),
        security)
  }

  @Test
  fun `initSecurity no admin in ACL with current user`() {
    val inputSecurity =
        RbacSecurity(
            COMPONENT_ID, ROLE_NONE, mutableListOf(RbacAccessControl(USER_READER, ROLE_USER)))
    val security = rbac.initSecurity(inputSecurity)
    assertEquals(
        inputSecurity.copy(
            accessControlList =
                mutableListOf(
                    RbacAccessControl(USER_READER, ROLE_USER),
                    RbacAccessControl(USER_NOTIN, ROLE_ADMIN))),
        security)
  }
}
