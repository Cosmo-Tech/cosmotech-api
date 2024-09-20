// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.organization.service

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.exceptions.CsmClientException
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.rbac.PERMISSION_CREATE_CHILDREN
import com.cosmotech.api.rbac.PERMISSION_DELETE
import com.cosmotech.api.rbac.PERMISSION_LAUNCH
import com.cosmotech.api.rbac.PERMISSION_READ
import com.cosmotech.api.rbac.PERMISSION_READ_SECURITY
import com.cosmotech.api.rbac.PERMISSION_VALIDATE
import com.cosmotech.api.rbac.PERMISSION_WRITE
import com.cosmotech.api.rbac.PERMISSION_WRITE_SECURITY
import com.cosmotech.api.rbac.ROLE_ADMIN
import com.cosmotech.api.rbac.ROLE_EDITOR
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.rbac.ROLE_USER
import com.cosmotech.api.rbac.ROLE_VALIDATOR
import com.cosmotech.api.rbac.ROLE_VIEWER
import com.cosmotech.api.security.ROLE_ORGANIZATION_USER
import com.cosmotech.api.security.ROLE_PLATFORM_ADMIN
import com.cosmotech.api.tests.CsmRedisTestBase
import com.cosmotech.api.utils.getCurrentAccountIdentifier
import com.cosmotech.api.utils.getCurrentAuthenticatedRoles
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.api.utils.getCurrentAuthentication
import com.cosmotech.organization.OrganizationApiServiceInterface
import com.cosmotech.organization.domain.ComponentRolePermissions
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationRequest
import com.cosmotech.organization.domain.OrganizationRole
import com.cosmotech.organization.domain.OrganizationSecurity
import com.cosmotech.organization.domain.OrganizationUpdate
import com.redis.om.spring.RediSearchIndexer
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.runner.RunWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.junit4.SpringRunner

@ActiveProfiles(profiles = ["organization-test"])
@ExtendWith(MockKExtension::class)
@ExtendWith(SpringExtension::class)
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Suppress("FunctionName")
class OrganizationServiceIntegrationTest : CsmRedisTestBase() {

  private val logger = LoggerFactory.getLogger(OrganizationServiceIntegrationTest::class.java)

  private val OTHER_TEST_USER_ID = "test.other.user@cosmotech.com"
  private val TEST_USER_ID = "test.user@cosmotech.com"
  private val TEST_ADMIN_USER_ID = "test.admin@cosmotech.com"
  private val UNKNOWN_IDENTIFIER = "unknown"

  @Autowired lateinit var rediSearchIndexer: RediSearchIndexer

  @Autowired lateinit var organizationApiService: OrganizationApiServiceInterface

  @Autowired lateinit var csmPlatformProperties: CsmPlatformProperties

  val defaultName = "my.account-tester@cosmotech.com"

  @BeforeAll
  fun globalSetup() {
    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
  }

  @BeforeEach
  fun setUp() {
    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
    every { getCurrentAccountIdentifier(any()) } returns defaultName
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "my.account-tester"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf()
    rediSearchIndexer.createIndexFor(Organization::class.java)
  }

  @Nested
  inner class AsOrganizationUser {

    @BeforeEach
    fun setUp() {
      runAsOrganizationUser()
    }

    @Test
    fun `listOrganizations with correct values`() {
      val numberOfOrganizationToCreate = 20
      val defaultPageSize = csmPlatformProperties.twincache.organization.defaultPageSize

      batchOrganizationCreation(numberOfOrganizationToCreate)
      testListOrganizations(null, null, numberOfOrganizationToCreate)
      testListOrganizations(0, null, defaultPageSize)
      testListOrganizations(0, 10, 10)
      testListOrganizations(1, 200, 0)
      testListOrganizations(1, 15, 5)
    }

    @Test
    fun `listOrganizations with correct values and RBAC for current user`() {
      runAsDifferentOrganizationUser()
      val numberOfOrganizationCreated = createOrganizationsWithAllCombinationOfRole(TEST_USER_ID)

      runAsOrganizationUser()
      // This number represents the amount of Organization that test.user can read regarding RBAC
      // This is typically all simple combinations except "securityRole to none"
      // We have 36 combinations per user in batchOrganizationCreationWithRBAC
      // So 36 combinations minus "securityRole to 'none'" equals 35
      testFindAllWithRBAC(numberOfOrganizationCreated, 35)
    }

    @Test
    fun `listOrganizations with correct values and no RBAC for current user`() {
      runAsDifferentOrganizationUser()
      val numberOfOrganizationCreated =
          createOrganizationsWithAllCombinationOfRole(OTHER_TEST_USER_ID)

      runAsOrganizationUser()
      // This number represents the amount of Organization that test.user can read regarding RBAC
      // We have 36 combinations per user in batchOrganizationCreationWithRBAC
      // securityRole does not refer to test.user
      // The only configuration that fit for test.user is defaultRole with
      // ROLE_VIEWER (x6), ROLE_EDITOR (x6), ROLE_VALIDATOR (x6), ROLE_USER (x6), ROLE_ADMIN (x6) =
      // 30
      testFindAllWithRBAC(numberOfOrganizationCreated, 30)
    }

    @Test
    fun `listOrganizations with wrong values`() {
      testListOrganizationsWithWrongValues()
    }

    @Test
    fun `findOrganizationById as resource admin`() {
      val organizationCreated =
          organizationApiService.createOrganization(OrganizationRequest("o-connector-test-1"))
      assertNotNull(organizationApiService.getOrganization(organizationCreated.id))
    }

    @Test
    fun `findOrganizationById as not resource admin`() {
      testGetOrganizationAsNotOwner(false, null, null, null, true) { runAsOrganizationUser() }
    }

    @Test
    fun `findOrganizationById as not resource admin but with READ role`() {
      testGetOrganizationAsNotOwner(true, TEST_USER_ID, ROLE_NONE, ROLE_USER, false) {
        runAsOrganizationUser()
      }
    }

    @Test
    fun `findOrganizationById as not resource admin but with WRITE role`() {
      testGetOrganizationAsNotOwner(true, TEST_USER_ID, ROLE_NONE, ROLE_EDITOR, false) {
        runAsOrganizationUser()
      }
    }

    @Test
    fun `findOrganizationById as not resource admin but with NONE role`() {
      testGetOrganizationAsNotOwner(true, TEST_USER_ID, ROLE_NONE, ROLE_NONE, true) {
        runAsOrganizationUser()
      }
    }

    @Test
    fun `createOrganization with minimal values`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationToRegister = createTestOrganization(name)
        val organizationCreated = organizationApiService.createOrganization(organizationToRegister)
        assertEquals(
            OrganizationSecurity(
                default = ROLE_NONE,
                mutableListOf(OrganizationAccessControl(id = TEST_USER_ID, role = ROLE_ADMIN))),
            organizationCreated.security)
        assertEquals(name, organizationCreated.name)
        assertTrue(organizationCreated.id.startsWith("o-"))
      }
    }

    @Test
    fun `createOrganization without required organization name`() {
      assertThrows<IllegalArgumentException> {
        organizationApiService.createOrganization(OrganizationRequest(name = ""))
      }
    }

    @Test
    fun `createOrganization with security values`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationToRegister =
            createTestOrganizationWithSimpleSecurity(name, OTHER_TEST_USER_ID, ROLE_USER, ROLE_NONE)
        val organizationCreated = organizationApiService.createOrganization(organizationToRegister)
        assertEquals(
            OrganizationSecurity(
                default = ROLE_USER,
                mutableListOf(
                    OrganizationAccessControl(id = OTHER_TEST_USER_ID, role = ROLE_NONE),
                    OrganizationAccessControl(id = TEST_USER_ID, role = ROLE_ADMIN))),
            organizationCreated.security)
        assertEquals(name, organizationCreated.name)
        assertTrue(organizationCreated.id.startsWith("o-"))
      }
    }

    @Test
    fun `deleteOrganization as resource admin`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationToRegister = createTestOrganization(name)
        val organizationCreated = organizationApiService.createOrganization(organizationToRegister)
        organizationApiService.deleteOrganization(organizationCreated.id)
      }
    }

    @Test
    fun `deleteOrganization unexisting organization `() {
      assertThrows<CsmResourceNotFoundException> {
        organizationApiService.deleteOrganization("o-connector-test-1")
      }
    }

    @Test
    fun `deleteOrganization no DELETE permission `() {
      assertThrows<CsmAccessForbiddenException> {
        val name = "o-connector-test-1"
        val organizationToRegister =
            createTestOrganizationWithSimpleSecurity(
                name, OTHER_TEST_USER_ID, ROLE_USER, ROLE_ADMIN)
        val organizationCreated = organizationApiService.createOrganization(organizationToRegister)
        organizationApiService.deleteOrganization(organizationCreated.id)
      }
    }

    @Test
    fun `deleteOrganization not as resource admin but DELETE permission `() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationToRegister =
            createTestOrganizationWithSimpleSecurity(name, TEST_USER_ID, ROLE_NONE, ROLE_ADMIN)
        val organizationCreated = organizationApiService.createOrganization(organizationToRegister)
        runAsOrganizationUser()
        organizationApiService.deleteOrganization(organizationCreated.id)
      }
    }

    @Test
    fun `updateOrganization as resource admin, organization name`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationCreated =
            organizationApiService.createOrganization(createTestOrganization(name))

        organizationCreated.name = "my-new-name"
        organizationApiService.updateOrganization(
            organizationCreated.id, OrganizationUpdate(name = organizationCreated.name))

        assertEquals(
            organizationCreated, organizationApiService.getOrganization(organizationCreated.id))
      }
    }

    @Test
    fun `updateOrganization not as resource admin with WRITE permission`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationCreated =
            organizationApiService.createOrganization(
                createTestOrganizationWithSimpleSecurity(
                    name, TEST_USER_ID, ROLE_NONE, ROLE_EDITOR))

        runAsOrganizationUser()

        organizationCreated.name = "my-new-name"
        organizationApiService.updateOrganization(
            organizationCreated.id, OrganizationUpdate(name = organizationCreated.name))

        assertEquals(
            organizationCreated, organizationApiService.getOrganization(organizationCreated.id))
      }
    }

    @Test
    fun `updateOrganization not as resource admin with no WRITE permission`() {
      assertThrows<CsmAccessForbiddenException> {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationCreated =
            organizationApiService.createOrganization(createTestOrganization(name))
        runAsOrganizationUser()
        organizationApiService.updateOrganization(
            organizationCreated.id, OrganizationUpdate(name = organizationCreated.name))
      }
    }

    @Test
    fun getAllPermissions() {
      val mapAllPermissions =
          listOf(
              ComponentRolePermissions(
                  component = "organization",
                  roles =
                      mutableMapOf(
                          ROLE_VIEWER to mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY),
                          ROLE_USER to
                              mutableListOf(
                                  PERMISSION_READ,
                                  PERMISSION_READ_SECURITY,
                                  PERMISSION_CREATE_CHILDREN),
                          ROLE_EDITOR to
                              mutableListOf(
                                  PERMISSION_READ,
                                  PERMISSION_READ_SECURITY,
                                  PERMISSION_CREATE_CHILDREN,
                                  PERMISSION_WRITE),
                          ROLE_ADMIN to
                              mutableListOf(
                                  PERMISSION_READ,
                                  PERMISSION_READ_SECURITY,
                                  PERMISSION_CREATE_CHILDREN,
                                  PERMISSION_WRITE,
                                  PERMISSION_WRITE_SECURITY,
                                  PERMISSION_DELETE),
                      )),
              ComponentRolePermissions(
                  component = "workspace",
                  roles =
                      mutableMapOf(
                          ROLE_VIEWER to mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY),
                          ROLE_USER to
                              mutableListOf(
                                  PERMISSION_READ,
                                  PERMISSION_READ_SECURITY,
                                  PERMISSION_CREATE_CHILDREN),
                          ROLE_EDITOR to
                              mutableListOf(
                                  PERMISSION_READ,
                                  PERMISSION_READ_SECURITY,
                                  PERMISSION_CREATE_CHILDREN,
                                  PERMISSION_WRITE),
                          ROLE_ADMIN to
                              mutableListOf(
                                  PERMISSION_READ,
                                  PERMISSION_READ_SECURITY,
                                  PERMISSION_CREATE_CHILDREN,
                                  PERMISSION_WRITE,
                                  PERMISSION_WRITE_SECURITY,
                                  PERMISSION_DELETE),
                      )),
              ComponentRolePermissions(
                  component = "runner",
                  roles =
                      mutableMapOf(
                          ROLE_VIEWER to mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY),
                          ROLE_EDITOR to
                              mutableListOf(
                                  PERMISSION_READ,
                                  PERMISSION_READ_SECURITY,
                                  PERMISSION_LAUNCH,
                                  PERMISSION_WRITE),
                          ROLE_VALIDATOR to
                              mutableListOf(
                                  PERMISSION_READ,
                                  PERMISSION_READ_SECURITY,
                                  PERMISSION_LAUNCH,
                                  PERMISSION_WRITE,
                                  PERMISSION_VALIDATE),
                          ROLE_ADMIN to
                              mutableListOf(
                                  PERMISSION_READ,
                                  PERMISSION_READ_SECURITY,
                                  PERMISSION_LAUNCH,
                                  PERMISSION_WRITE,
                                  PERMISSION_VALIDATE,
                                  PERMISSION_WRITE_SECURITY,
                                  PERMISSION_DELETE),
                      )))
      assertEquals(mapAllPermissions, organizationApiService.getAllPermissions())
    }

    @Test
    fun `getOrganizationPermissions as resource admin`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationCreated =
            organizationApiService.createOrganization(createTestOrganization(name))
        var organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(organizationCreated.id, ROLE_VIEWER)
        assertEquals(
            mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY), organizationUserPermissions)
        organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(organizationCreated.id, ROLE_USER)
        assertEquals(
            mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_CREATE_CHILDREN),
            organizationUserPermissions)
        organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(organizationCreated.id, ROLE_EDITOR)
        assertEquals(
            mutableListOf(
                PERMISSION_READ,
                PERMISSION_READ_SECURITY,
                PERMISSION_CREATE_CHILDREN,
                PERMISSION_WRITE),
            organizationUserPermissions)
        organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(organizationCreated.id, ROLE_ADMIN)
        assertEquals(
            mutableListOf(
                PERMISSION_READ,
                PERMISSION_READ_SECURITY,
                PERMISSION_CREATE_CHILDREN,
                PERMISSION_WRITE,
                PERMISSION_WRITE_SECURITY,
                PERMISSION_DELETE),
            organizationUserPermissions)
        organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(
                organizationCreated.id, UNKNOWN_IDENTIFIER)
        assertEquals(emptyList(), organizationUserPermissions)
      }
    }

    @Test
    fun `getOrganizationPermissions as not resource admin and READ_SECURITY permission`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationCreated =
            organizationApiService.createOrganization(
                createTestOrganizationWithSimpleSecurity(
                    name, TEST_USER_ID, ROLE_NONE, ROLE_VIEWER))
        runAsOrganizationUser()
        var organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(organizationCreated.id, ROLE_VIEWER)
        assertEquals(
            mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY), organizationUserPermissions)
        organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(organizationCreated.id, ROLE_USER)
        assertEquals(
            mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_CREATE_CHILDREN),
            organizationUserPermissions)
        organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(organizationCreated.id, ROLE_EDITOR)
        assertEquals(
            mutableListOf(
                PERMISSION_READ,
                PERMISSION_READ_SECURITY,
                PERMISSION_CREATE_CHILDREN,
                PERMISSION_WRITE),
            organizationUserPermissions)
        organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(organizationCreated.id, ROLE_ADMIN)
        assertEquals(
            mutableListOf(
                PERMISSION_READ,
                PERMISSION_READ_SECURITY,
                PERMISSION_CREATE_CHILDREN,
                PERMISSION_WRITE,
                PERMISSION_WRITE_SECURITY,
                PERMISSION_DELETE),
            organizationUserPermissions)
        organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(
                organizationCreated.id, UNKNOWN_IDENTIFIER)
        assertEquals(emptyList(), organizationUserPermissions)
      }
    }

    @Test
    fun `getOrganizationPermissions as not resource admin and no permission`() {
      assertThrows<CsmAccessForbiddenException> {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationCreated =
            organizationApiService.createOrganization(createTestOrganization(name))
        runAsOrganizationUser()
        organizationApiService.getOrganizationPermissions(organizationCreated.id, ROLE_VIEWER)
      }
    }

    @Test
    fun `getOrganizationSecurity as resource admin`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationCreated =
            organizationApiService.createOrganization(createTestOrganization(name))
        assertNotNull(organizationApiService.getOrganizationSecurity(organizationCreated.id))
      }
    }

    @Test
    fun `getOrganizationSecurity with no security organization`() {
      assertThrows<CsmResourceNotFoundException> {
        organizationApiService.getOrganizationSecurity(UNKNOWN_IDENTIFIER)
      }
    }

    @Test
    fun `getOrganizationSecurity as not resource admin with READ_SECURITY permission`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationCreated =
            organizationApiService.createOrganization(
                createTestOrganizationWithSimpleSecurity(
                    name, TEST_USER_ID, ROLE_NONE, ROLE_VIEWER))
        runAsOrganizationUser()
        assertNotNull(organizationApiService.getOrganizationSecurity(organizationCreated.id))
      }
    }

    @Test
    fun `getOrganizationSecurity as not resource admin with no READ_SECURITY permission`() {
      assertThrows<CsmAccessForbiddenException> {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationCreated =
            organizationApiService.createOrganization(
                createTestOrganizationWithSimpleSecurity(name, TEST_USER_ID, ROLE_NONE, ROLE_NONE))
        runAsOrganizationUser()
        organizationApiService.getOrganizationSecurity(organizationCreated.id)
      }
    }

    @Test
    fun `setOrganizationDefaultSecurity as resource admin and existing role`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationCreated =
            organizationApiService.createOrganization(createTestOrganization(name))
        val defaultRoleCreated = organizationCreated.security.default
        assertNotNull(
            organizationApiService.setOrganizationDefaultSecurity(
                organizationCreated.id, OrganizationRole(ROLE_ADMIN)))
        val defaultRoleUpdated =
            organizationApiService.getOrganizationSecurity(organizationCreated.id).default
        assertNotEquals(defaultRoleCreated, defaultRoleUpdated)
      }
    }

    @Test
    fun `setOrganizationDefaultSecurity as resource admin and non-existing role`() {
      assertThrows<CsmClientException> {
        val name = "o-connector-test-1"
        val organizationCreated =
            organizationApiService.createOrganization(createTestOrganization(name))
        organizationApiService.setOrganizationDefaultSecurity(
            organizationCreated.id, OrganizationRole(UNKNOWN_IDENTIFIER))
      }
    }

    @Test
    fun `setOrganizationDefaultSecurity as not resource admin and WRITE_SECURITY_PERMISSION`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationCreated =
            organizationApiService.createOrganization(
                createTestOrganizationWithSimpleSecurity(name, TEST_USER_ID, ROLE_NONE, ROLE_ADMIN))
        val defaultRoleCreated = organizationCreated.security.default
        runAsOrganizationUser()
        assertNotNull(
            organizationApiService.setOrganizationDefaultSecurity(
                organizationCreated.id, OrganizationRole(ROLE_ADMIN)))
        val defaultRoleUpdated =
            organizationApiService.getOrganizationSecurity(organizationCreated.id).default
        assertNotEquals(defaultRoleCreated, defaultRoleUpdated)
      }
    }

    @Test
    fun `setOrganizationDefaultSecurity as not resource admin and no WRITE_SECURITY_PERMISSION`() {
      assertThrows<CsmAccessForbiddenException> {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationCreated =
            organizationApiService.createOrganization(
                createTestOrganizationWithSimpleSecurity(name, TEST_USER_ID, ROLE_NONE, ROLE_USER))
        runAsOrganizationUser()
        organizationApiService.setOrganizationDefaultSecurity(
            organizationCreated.id, OrganizationRole(ROLE_ADMIN))
      }
    }

    @Test
    fun `getOrganizationAccessControl as resource admin and current user`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationCreated =
            organizationApiService.createOrganization(createTestOrganization(name))
        val organizationRole =
            organizationApiService.getOrganizationAccessControl(
                organizationCreated.id, TEST_USER_ID)
        assertNotNull(organizationRole)
        assertEquals(ROLE_ADMIN, organizationRole.role)
        assertEquals(TEST_USER_ID, organizationRole.id)
      }
    }

    @Test
    fun `getOrganizationAccessControl as resource admin and non-existing user`() {
      assertThrows<CsmResourceNotFoundException> {
        val name = "o-connector-test-1"
        val organizationCreated =
            organizationApiService.createOrganization(createTestOrganization(name))
        organizationApiService.getOrganizationAccessControl(organizationCreated.id, "UNKOWN")
      }
    }

    @Test
    fun `getOrganizationAccessControl as not resource admin, READ_SECURITY permission and existing user`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationCreated =
            organizationApiService.createOrganization(
                createTestOrganizationWithSimpleSecurity(
                    name, TEST_USER_ID, ROLE_NONE, ROLE_VIEWER))
        runAsOrganizationUser()
        val organizationRole =
            organizationApiService.getOrganizationAccessControl(
                organizationCreated.id, TEST_USER_ID)
        assertNotNull(organizationRole)
        assertEquals(ROLE_VIEWER, organizationRole.role)
        assertEquals(TEST_USER_ID, organizationRole.id)
      }
    }

    @Test
    fun `getOrganizationAccessControl as not resource admin, READ_SECURITY permission and non existing user`() {
      assertThrows<CsmResourceNotFoundException> {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationCreated =
            organizationApiService.createOrganization(
                createTestOrganizationWithSimpleSecurity(
                    name, TEST_USER_ID, ROLE_NONE, ROLE_VIEWER))
        runAsOrganizationUser()
        organizationApiService.getOrganizationAccessControl(
            organizationCreated.id, UNKNOWN_IDENTIFIER)
      }
    }

    @Test
    fun `getOrganizationAccessControl as not resource admin, no READ_SECURITY permission`() {
      assertThrows<CsmAccessForbiddenException> {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationCreated =
            organizationApiService.createOrganization(
                createTestOrganizationWithSimpleSecurity(name, TEST_USER_ID, ROLE_NONE, ROLE_NONE))
        runAsOrganizationUser()
        organizationApiService.getOrganizationAccessControl(
            organizationCreated.id, UNKNOWN_IDENTIFIER)
      }
    }

    @Test
    fun `addOrganizationAccessControl as resource admin`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationCreated =
            organizationApiService.createOrganization(createTestOrganization(name))
        assertThrows<CsmResourceNotFoundException> {
          organizationApiService.getOrganizationAccessControl(
              organizationCreated.id, OTHER_TEST_USER_ID)
        }
        val otherUserACL = OrganizationAccessControl(id = OTHER_TEST_USER_ID, role = ROLE_VIEWER)
        assertNotNull(
            organizationApiService.addOrganizationAccessControl(
                organizationCreated.id, otherUserACL))

        val otherUserACLRetrieved =
            organizationApiService.getOrganizationAccessControl(
                organizationCreated.id, OTHER_TEST_USER_ID)
        assertEquals(otherUserACL, otherUserACLRetrieved)
      }
    }

    @Test
    fun `addOrganizationAccessControl as resource admin (ROLE_NONE)`() {
      assertThrows<CsmClientException> {
        val name = "o-connector-test-1"
        val organizationCreated =
            organizationApiService.createOrganization(createTestOrganization(name))

        val otherUserACL = OrganizationAccessControl(id = OTHER_TEST_USER_ID, role = ROLE_NONE)
        organizationApiService.addOrganizationAccessControl(organizationCreated.id, otherUserACL)
      }
    }

    @Test
    fun `addOrganizationAccessControl as not resource admin and PERMISSION_WRITE_SECURITY`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationCreated =
            organizationApiService.createOrganization(
                createTestOrganizationWithSimpleSecurity(name, TEST_USER_ID, ROLE_NONE, ROLE_ADMIN))
        runAsOrganizationUser()
        assertThrows<CsmResourceNotFoundException> {
          organizationApiService.getOrganizationAccessControl(
              organizationCreated.id, TEST_ADMIN_USER_ID)
        }
        val otherUserACL = OrganizationAccessControl(id = TEST_ADMIN_USER_ID, role = ROLE_VIEWER)
        assertNotNull(
            organizationApiService.addOrganizationAccessControl(
                organizationCreated.id, otherUserACL))

        val otherUserACLRetrieved =
            organizationApiService.getOrganizationAccessControl(
                organizationCreated.id, TEST_ADMIN_USER_ID)
        assertEquals(otherUserACL, otherUserACLRetrieved)
      }
    }

    @Test
    fun `addOrganizationAccessControl as not resource admin and no PERMISSION_WRITE_SECURITY`() {
      assertThrows<CsmAccessForbiddenException> {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationCreated =
            organizationApiService.createOrganization(
                createTestOrganizationWithSimpleSecurity(
                    name, TEST_USER_ID, ROLE_NONE, ROLE_VIEWER))
        runAsOrganizationUser()
        assertThrows<CsmResourceNotFoundException> {
          organizationApiService.getOrganizationAccessControl(
              organizationCreated.id, TEST_ADMIN_USER_ID)
        }
        val otherUserACL = OrganizationAccessControl(id = TEST_ADMIN_USER_ID, role = ROLE_VIEWER)
        organizationApiService.addOrganizationAccessControl(organizationCreated.id, otherUserACL)
      }
    }

    @Test
    fun `updateOrganizationAccessControl as resource admin cannot update last admin`() {
      assertThrows<CsmAccessForbiddenException> {
        val name = "o-connector-test-1"
        val organizationCreated =
            organizationApiService.createOrganization(createTestOrganization(name))
        organizationApiService.updateOrganizationAccessControl(
            organizationCreated.id, TEST_USER_ID, OrganizationRole(role = ROLE_VIEWER))
      }
    }

    @Test
    fun `updateOrganizationAccessControl as resource admin can update user (!= ROLE_NONE)`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationCreated =
            organizationApiService.createOrganization(createTestOrganization(name))

        organizationApiService.addOrganizationAccessControl(
            organizationCreated.id,
            OrganizationAccessControl(id = OTHER_TEST_USER_ID, role = ROLE_VIEWER))

        assertNotNull(
            organizationApiService.updateOrganizationAccessControl(
                organizationCreated.id, OTHER_TEST_USER_ID, OrganizationRole(role = ROLE_EDITOR)))

        val userACLRetrieved =
            organizationApiService.getOrganizationAccessControl(
                organizationCreated.id, OTHER_TEST_USER_ID)
        assertNotEquals(ROLE_VIEWER, userACLRetrieved.role)
        assertEquals(ROLE_EDITOR, userACLRetrieved.role)
      }
    }

    @Test
    fun `updateOrganizationAccessControl as resource admin cannot update user (= ROLE_NONE)`() {
      assertThrows<CsmClientException> {
        val name = "o-connector-test-1"
        val organizationCreated =
            organizationApiService.createOrganization(createTestOrganization(name))

        organizationApiService.updateOrganizationAccessControl(
            organizationCreated.id, TEST_USER_ID, OrganizationRole(role = ROLE_NONE))
      }
    }

    @Test
    fun `updateOrganizationAccessControl as resource admin and unknown ACL user`() {
      assertThrows<CsmResourceNotFoundException> {
        val name = "o-connector-test-1"
        val organizationCreated =
            organizationApiService.createOrganization(createTestOrganization(name))

        organizationApiService.updateOrganizationAccessControl(
            organizationCreated.id, UNKNOWN_IDENTIFIER, OrganizationRole(role = ROLE_EDITOR))
      }
    }

    @Test
    fun `updateOrganizationAccessControl as resource admin and wrong role`() {
      assertThrows<CsmClientException> {
        val name = "o-connector-test-1"
        val organizationCreated =
            organizationApiService.createOrganization(createTestOrganization(name))

        organizationApiService.updateOrganizationAccessControl(
            organizationCreated.id, TEST_USER_ID, OrganizationRole(role = UNKNOWN_IDENTIFIER))
      }
    }

    @Test
    fun `updateOrganizationAccessControl as not resource admin, WRITE_SECURITY permission, can update user (!= ROLE_NONE)`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationCreated =
            organizationApiService.createOrganization(createTestOrganization(name))

        organizationApiService.addOrganizationAccessControl(
            organizationCreated.id, OrganizationAccessControl(id = TEST_USER_ID, role = ROLE_ADMIN))

        runAsOrganizationUser()

        assertNotNull(
            organizationApiService.updateOrganizationAccessControl(
                organizationCreated.id, OTHER_TEST_USER_ID, OrganizationRole(role = ROLE_EDITOR)))

        val userACLRetrieved =
            organizationApiService.getOrganizationAccessControl(
                organizationCreated.id, OTHER_TEST_USER_ID)
        assertEquals(ROLE_EDITOR, userACLRetrieved.role)
      }
    }

    @Test
    fun `updateOrganizationAccessControl as not resource admin, no WRITE_SECURITY permission`() {
      assertThrows<CsmAccessForbiddenException> {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationCreated =
            organizationApiService.createOrganization(createTestOrganization(name))

        organizationApiService.addOrganizationAccessControl(
            organizationCreated.id,
            OrganizationAccessControl(id = TEST_USER_ID, role = ROLE_VIEWER))

        runAsOrganizationUser()

        organizationApiService.updateOrganizationAccessControl(
            organizationCreated.id, OTHER_TEST_USER_ID, OrganizationRole(role = ROLE_EDITOR))
      }
    }

    @Test
    fun `updateOrganizationAccessControl as not resource admin, no WRITE_SECURITY permission, ROLE_NONE`() {
      assertThrows<CsmClientException> {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationCreated =
            organizationApiService.createOrganization(createTestOrganization(name))

        organizationApiService.addOrganizationAccessControl(
            organizationCreated.id,
            OrganizationAccessControl(id = TEST_USER_ID, role = ROLE_VIEWER))

        runAsOrganizationUser()

        organizationApiService.updateOrganizationAccessControl(
            organizationCreated.id, OTHER_TEST_USER_ID, OrganizationRole(role = ROLE_NONE))
      }
    }

    @Test
    fun `removeOrganizationAccessControl as resource admin`() {
      val name = "o-connector-test-1"
      val organizationCreated =
          organizationApiService.createOrganization(createTestOrganization(name))

      val otherUserACL = OrganizationAccessControl(id = OTHER_TEST_USER_ID, role = ROLE_VIEWER)
      organizationApiService.addOrganizationAccessControl(organizationCreated.id, otherUserACL)

      organizationApiService.removeOrganizationAccessControl(
          organizationCreated.id, OTHER_TEST_USER_ID)
      assertThrows<CsmResourceNotFoundException> {
        organizationApiService.getOrganizationAccessControl(
            organizationCreated.id, OTHER_TEST_USER_ID)
      }
    }

    @Test
    fun `removeOrganizationAccessControl as not resource admin, WRITE_SECURITY permission`() {
      val name = "o-connector-test-1"
      runAsDifferentOrganizationUser()
      val organizationCreated =
          organizationApiService.createOrganization(createTestOrganization(name))

      val otherUserACL = OrganizationAccessControl(id = TEST_USER_ID, role = ROLE_ADMIN)
      organizationApiService.addOrganizationAccessControl(organizationCreated.id, otherUserACL)

      runAsOrganizationUser()
      organizationApiService.removeOrganizationAccessControl(
          organizationCreated.id, OTHER_TEST_USER_ID)
      assertThrows<CsmResourceNotFoundException> {
        organizationApiService.getOrganizationAccessControl(
            organizationCreated.id, OTHER_TEST_USER_ID)
      }
    }

    @Test
    fun `removeOrganizationAccessControl as not resource admin, no WRITE_SECURITY permission`() {
      val name = "o-connector-test-1"
      runAsDifferentOrganizationUser()
      val organizationCreated =
          organizationApiService.createOrganization(createTestOrganization(name))

      val otherUserACL = OrganizationAccessControl(id = TEST_USER_ID, role = ROLE_VIEWER)
      organizationApiService.addOrganizationAccessControl(organizationCreated.id, otherUserACL)

      runAsOrganizationUser()
      assertThrows<CsmAccessForbiddenException> {
        organizationApiService.removeOrganizationAccessControl(
            organizationCreated.id, OTHER_TEST_USER_ID)
      }
    }

    @Test
    fun `getOrganizationSecurityUsers as resource admin`() {
      val name = "o-connector-test-1"
      val organizationCreated =
          organizationApiService.createOrganization(createTestOrganization(name))

      val orgaUsers = organizationApiService.getOrganizationSecurityUsers(organizationCreated.id)
      assertEquals(listOf(TEST_USER_ID), orgaUsers)
    }

    @Test
    fun `getOrganizationSecurityUsers as not resource admin, READ_SECURITY permission`() {
      val name = "o-connector-test-1"
      runAsDifferentOrganizationUser()
      val organizationCreated =
          organizationApiService.createOrganization(
              createTestOrganizationWithSimpleSecurity(name, TEST_USER_ID, ROLE_NONE, ROLE_VIEWER))
      runAsOrganizationUser()
      val orgaUsers = organizationApiService.getOrganizationSecurityUsers(organizationCreated.id)
      assertEquals(listOf(TEST_USER_ID, OTHER_TEST_USER_ID), orgaUsers)
    }

    @Test
    fun `getOrganizationSecurityUsers as not resource admin, no READ_SECURITY permission`() {
      val name = "o-connector-test-1"
      runAsDifferentOrganizationUser()
      val organizationCreated =
          organizationApiService.createOrganization(
              createTestOrganizationWithSimpleSecurity(name, TEST_USER_ID, ROLE_NONE, ROLE_NONE))
      runAsOrganizationUser()
      assertThrows<CsmAccessForbiddenException> {
        organizationApiService.getOrganizationSecurityUsers(organizationCreated.id)
      }
    }

    @Test
    fun `testVerifyPermissionsAndReturnOrganization`() {
      val name = "o-connector-test-1"
      val organizationCreated =
          organizationApiService.createOrganization(createTestOrganization(name))
      assertDoesNotThrow {
        val organizationVerified =
            organizationApiService.getVerifiedOrganization(organizationCreated.id)
        assertEquals(organizationCreated, organizationVerified)
      }
    }

    @Test
    fun `testVerifyPermissionsAndReturnOrganization with organization with restricted permission`() {
      val name = "o-connector-test-1"
      runAsDifferentOrganizationUser()
      val organizationCreated =
          organizationApiService.createOrganization(
              createTestOrganizationWithSimpleSecurity(name, TEST_USER_ID, ROLE_NONE, ROLE_NONE))
      runAsOrganizationUser()
      assertThrows<CsmAccessForbiddenException> {
        organizationApiService.getVerifiedOrganization(organizationCreated.id)
      }
    }

    @Test
    fun `testVerifyPermissionsAndReturnOrganization with unknown organization id`() {
      assertThrows<CsmResourceNotFoundException> {
        organizationApiService.getVerifiedOrganization("wrong_orga_id")
      }
    }
  }
  @Nested
  inner class AsPlatformAdmin {

    @BeforeEach
    fun setUp() {
      runAsPlatformAdmin()
    }

    @Test
    fun `find All Organizations with correct values`() {
      val numberOfOrganizationToCreate = 20
      val defaultPageSize = csmPlatformProperties.twincache.organization.defaultPageSize

      batchOrganizationCreation(numberOfOrganizationToCreate)
      testListOrganizations(null, null, numberOfOrganizationToCreate)
      testListOrganizations(0, null, defaultPageSize)
      testListOrganizations(0, 10, 10)
      testListOrganizations(1, 200, 0)
      testListOrganizations(1, 15, 5)
    }

    @Test
    fun `find All Organizations with correct values and RBAC for current user`() {

      val numberOfOrganizationCreated =
          createOrganizationsWithAllCombinationOfRole(TEST_ADMIN_USER_ID)
      // This number represents the amount of Organization that test.user can read regarding RBAC
      // We have 36 combinations per user in batchOrganizationCreationWithRBAC
      testFindAllWithRBAC(numberOfOrganizationCreated, 36)
    }

    @Test
    fun `find All Organizations with correct values and no RBAC for current user`() {

      val numberOfOrganizationCreated =
          createOrganizationsWithAllCombinationOfRole(OTHER_TEST_USER_ID)
      // This number represents the amount of Organization that test.user can read regarding RBAC
      // We have 36 combinations per user in batchOrganizationCreationWithRBAC
      testFindAllWithRBAC(numberOfOrganizationCreated, 36)
    }

    @Test
    fun `find All Organizations with wrong values`() {
      testListOrganizationsWithWrongValues()
    }

    @Test
    fun `findOrganizationById as resource admin`() {
      val organizationCreated =
          organizationApiService.createOrganization(createTestOrganization("o-connector-test-1"))
      assertNotNull(organizationApiService.getOrganization(organizationCreated.id))
    }

    @Test
    fun `findOrganizationById as not resource admin`() {
      testGetOrganizationAsNotOwner(false, null, null, null, false) { runAsPlatformAdmin() }
    }

    @Test
    fun `createOrganization with minimal values`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationToRegister = createTestOrganization(name)
        val organizationCreated = organizationApiService.createOrganization(organizationToRegister)
        assertEquals(
            OrganizationSecurity(
                default = ROLE_NONE,
                mutableListOf(
                    OrganizationAccessControl(id = TEST_ADMIN_USER_ID, role = ROLE_ADMIN))),
            organizationCreated.security)
        assertEquals(name, organizationCreated.name)
        assertTrue(organizationCreated.id.startsWith("o-"))
      }
    }

    @Test
    fun `createOrganization without required organization name`() {
      assertThrows<IllegalArgumentException> {
        organizationApiService.createOrganization(createTestOrganization(""))
      }
    }

    @Test
    fun `createOrganization with security values`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationToRegister =
            createTestOrganizationWithSimpleSecurity(name, OTHER_TEST_USER_ID, ROLE_USER, ROLE_NONE)
        val organizationCreated = organizationApiService.createOrganization(organizationToRegister)
        assertEquals(
            OrganizationSecurity(
                default = ROLE_USER,
                mutableListOf(
                    OrganizationAccessControl(id = OTHER_TEST_USER_ID, role = ROLE_NONE),
                    OrganizationAccessControl(id = TEST_ADMIN_USER_ID, role = ROLE_ADMIN))),
            organizationCreated.security)
        assertEquals(name, organizationCreated.name)
        assertTrue(organizationCreated.id.startsWith("o-"))
      }
    }

    @Test
    fun `deleteOrganization as resource admin`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationToRegister = createTestOrganization(name)
        val organizationCreated = organizationApiService.createOrganization(organizationToRegister)
        organizationApiService.deleteOrganization(organizationCreated.id)
      }
    }

    @Test
    fun `deleteOrganization unexisting organization `() {
      assertThrows<CsmResourceNotFoundException> {
        organizationApiService.deleteOrganization("o-connector-test-1")
      }
    }

    @Test
    fun `deleteOrganization as not resource admin `() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationToRegister =
            createTestOrganizationWithSimpleSecurity(name, TEST_ADMIN_USER_ID, ROLE_NONE, ROLE_NONE)
        val organizationCreated = organizationApiService.createOrganization(organizationToRegister)
        runAsPlatformAdmin()
        organizationApiService.deleteOrganization(organizationCreated.id)
      }
    }

    @Test
    fun `updateOrganization as resource admin organization name`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationCreated =
            organizationApiService.createOrganization(createTestOrganization(name))

        organizationCreated.name = "my-new-name"
        organizationApiService.updateOrganization(
            organizationCreated.id, OrganizationUpdate(name = organizationCreated.name))

        assertEquals(
            organizationCreated, organizationApiService.getOrganization(organizationCreated.id))
      }
    }

    @Test
    fun `updateOrganization as not resource admin with WRITE permission`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationCreated =
            organizationApiService.createOrganization(
                createTestOrganizationWithSimpleSecurity(
                    name, TEST_ADMIN_USER_ID, ROLE_NONE, ROLE_EDITOR))

        runAsPlatformAdmin()

        organizationCreated.name = "my-new-name"
        organizationApiService.updateOrganization(
            organizationCreated.id, OrganizationUpdate(name = organizationCreated.name))

        assertEquals(
            organizationCreated, organizationApiService.getOrganization(organizationCreated.id))
      }
    }

    @Test
    fun `updateOrganization as not resource admin  with no WRITE permission`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationCreated =
            organizationApiService.createOrganization(createTestOrganization(name))
        runAsPlatformAdmin()
        organizationApiService.updateOrganization(
            organizationCreated.id, OrganizationUpdate(name = organizationCreated.name))
      }
    }

    @Test
    fun getAllPermissions() {
      val mapAllPermissions =
          listOf(
              ComponentRolePermissions(
                  component = "organization",
                  roles =
                      mutableMapOf(
                          ROLE_VIEWER to mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY),
                          ROLE_USER to
                              mutableListOf(
                                  PERMISSION_READ,
                                  PERMISSION_READ_SECURITY,
                                  PERMISSION_CREATE_CHILDREN),
                          ROLE_EDITOR to
                              mutableListOf(
                                  PERMISSION_READ,
                                  PERMISSION_READ_SECURITY,
                                  PERMISSION_CREATE_CHILDREN,
                                  PERMISSION_WRITE),
                          ROLE_ADMIN to
                              mutableListOf(
                                  PERMISSION_READ,
                                  PERMISSION_READ_SECURITY,
                                  PERMISSION_CREATE_CHILDREN,
                                  PERMISSION_WRITE,
                                  PERMISSION_WRITE_SECURITY,
                                  PERMISSION_DELETE),
                      )),
              ComponentRolePermissions(
                  component = "workspace",
                  roles =
                      mutableMapOf(
                          ROLE_VIEWER to mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY),
                          ROLE_USER to
                              mutableListOf(
                                  PERMISSION_READ,
                                  PERMISSION_READ_SECURITY,
                                  PERMISSION_CREATE_CHILDREN),
                          ROLE_EDITOR to
                              mutableListOf(
                                  PERMISSION_READ,
                                  PERMISSION_READ_SECURITY,
                                  PERMISSION_CREATE_CHILDREN,
                                  PERMISSION_WRITE),
                          ROLE_ADMIN to
                              mutableListOf(
                                  PERMISSION_READ,
                                  PERMISSION_READ_SECURITY,
                                  PERMISSION_CREATE_CHILDREN,
                                  PERMISSION_WRITE,
                                  PERMISSION_WRITE_SECURITY,
                                  PERMISSION_DELETE),
                      )),
              ComponentRolePermissions(
                  component = "runner",
                  roles =
                      mutableMapOf(
                          ROLE_VIEWER to mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY),
                          ROLE_EDITOR to
                              mutableListOf(
                                  PERMISSION_READ,
                                  PERMISSION_READ_SECURITY,
                                  PERMISSION_LAUNCH,
                                  PERMISSION_WRITE),
                          ROLE_VALIDATOR to
                              mutableListOf(
                                  PERMISSION_READ,
                                  PERMISSION_READ_SECURITY,
                                  PERMISSION_LAUNCH,
                                  PERMISSION_WRITE,
                                  PERMISSION_VALIDATE),
                          ROLE_ADMIN to
                              mutableListOf(
                                  PERMISSION_READ,
                                  PERMISSION_READ_SECURITY,
                                  PERMISSION_LAUNCH,
                                  PERMISSION_WRITE,
                                  PERMISSION_VALIDATE,
                                  PERMISSION_WRITE_SECURITY,
                                  PERMISSION_DELETE),
                      )))
      assertEquals(mapAllPermissions, organizationApiService.getAllPermissions())
    }

    @Test
    fun `getOrganizationPermissions as resource admin`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationCreated =
            organizationApiService.createOrganization(createTestOrganization(name))
        var organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(organizationCreated.id, ROLE_VIEWER)
        assertEquals(
            mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY), organizationUserPermissions)
        organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(organizationCreated.id, ROLE_USER)
        assertEquals(
            mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_CREATE_CHILDREN),
            organizationUserPermissions)
        organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(organizationCreated.id, ROLE_EDITOR)
        assertEquals(
            mutableListOf(
                PERMISSION_READ,
                PERMISSION_READ_SECURITY,
                PERMISSION_CREATE_CHILDREN,
                PERMISSION_WRITE),
            organizationUserPermissions)
        organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(organizationCreated.id, ROLE_ADMIN)
        assertEquals(
            mutableListOf(
                PERMISSION_READ,
                PERMISSION_READ_SECURITY,
                PERMISSION_CREATE_CHILDREN,
                PERMISSION_WRITE,
                PERMISSION_WRITE_SECURITY,
                PERMISSION_DELETE),
            organizationUserPermissions)
        organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(
                organizationCreated.id, UNKNOWN_IDENTIFIER)
        assertEquals(emptyList(), organizationUserPermissions)
      }
    }

    @Test
    fun `getOrganizationPermissions as not resource admin and READ_SECURITY permission`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationCreated =
            organizationApiService.createOrganization(
                createTestOrganizationWithSimpleSecurity(
                    name, TEST_USER_ID, ROLE_NONE, ROLE_VIEWER))
        runAsPlatformAdmin()
        var organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(organizationCreated.id, ROLE_VIEWER)
        assertEquals(
            mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY), organizationUserPermissions)
        organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(organizationCreated.id, ROLE_USER)
        assertEquals(
            mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_CREATE_CHILDREN),
            organizationUserPermissions)
        organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(organizationCreated.id, ROLE_EDITOR)
        assertEquals(
            mutableListOf(
                PERMISSION_READ,
                PERMISSION_READ_SECURITY,
                PERMISSION_CREATE_CHILDREN,
                PERMISSION_WRITE),
            organizationUserPermissions)
        organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(organizationCreated.id, ROLE_ADMIN)
        assertEquals(
            mutableListOf(
                PERMISSION_READ,
                PERMISSION_READ_SECURITY,
                PERMISSION_CREATE_CHILDREN,
                PERMISSION_WRITE,
                PERMISSION_WRITE_SECURITY,
                PERMISSION_DELETE),
            organizationUserPermissions)
        organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(
                organizationCreated.id, UNKNOWN_IDENTIFIER)
        assertEquals(emptyList(), organizationUserPermissions)
      }
    }

    @Test
    fun `getOrganizationPermissions as not resource admin and no permission`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationCreated =
            organizationApiService.createOrganization(
                createTestOrganizationWithSimpleSecurity(
                    name, TEST_USER_ID, ROLE_NONE, ROLE_VIEWER))
        runAsPlatformAdmin()
        var organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(organizationCreated.id, ROLE_VIEWER)
        assertEquals(
            mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY), organizationUserPermissions)
        organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(organizationCreated.id, ROLE_USER)
        assertEquals(
            mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_CREATE_CHILDREN),
            organizationUserPermissions)
        organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(organizationCreated.id, ROLE_EDITOR)
        assertEquals(
            mutableListOf(
                PERMISSION_READ,
                PERMISSION_READ_SECURITY,
                PERMISSION_CREATE_CHILDREN,
                PERMISSION_WRITE),
            organizationUserPermissions)
        organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(organizationCreated.id, ROLE_ADMIN)
        assertEquals(
            mutableListOf(
                PERMISSION_READ,
                PERMISSION_READ_SECURITY,
                PERMISSION_CREATE_CHILDREN,
                PERMISSION_WRITE,
                PERMISSION_WRITE_SECURITY,
                PERMISSION_DELETE),
            organizationUserPermissions)
        organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(
                organizationCreated.id, UNKNOWN_IDENTIFIER)
        assertEquals(emptyList(), organizationUserPermissions)
      }
    }

    @Test
    fun `getOrganizationSecurity as resource admin`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationCreated =
            organizationApiService.createOrganization(createTestOrganization(name))
        assertNotNull(organizationApiService.getOrganizationSecurity(organizationCreated.id))
      }
    }

    @Test
    fun `getOrganizationSecurity with non existing organization`() {
      assertThrows<CsmResourceNotFoundException> {
        organizationApiService.getOrganizationSecurity(UNKNOWN_IDENTIFIER)
      }
    }

    @Test
    fun `getOrganizationSecurity with no security organization`() {
      assertThrows<CsmResourceNotFoundException> {
        organizationApiService.createOrganization(createTestOrganization(name = "org1"))
        organizationApiService.getOrganizationSecurity("org1")
      }
    }

    @Test
    fun `getOrganizationSecurity as not resource admin with READ_SECURITY permission`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationCreated =
            organizationApiService.createOrganization(
                createTestOrganizationWithSimpleSecurity(
                    name, TEST_USER_ID, ROLE_NONE, ROLE_VIEWER))
        runAsPlatformAdmin()
        assertNotNull(organizationApiService.getOrganizationSecurity(organizationCreated.id))
      }
    }

    @Test
    fun `getOrganizationSecurity as not resource admin with no READ_SECURITY permission`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationCreated =
            organizationApiService.createOrganization(
                createTestOrganizationWithSimpleSecurity(
                    name, TEST_ADMIN_USER_ID, ROLE_NONE, ROLE_NONE))
        runAsPlatformAdmin()
        assertNotNull(organizationApiService.getOrganizationSecurity(organizationCreated.id))
      }
    }

    @Test
    fun `setOrganizationDefaultSecurity as resource admin and existing role`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationCreated =
            organizationApiService.createOrganization(createTestOrganization(name))
        val defaultRoleCreated = organizationCreated.security.default
        assertNotNull(
            organizationApiService.setOrganizationDefaultSecurity(
                organizationCreated.id, OrganizationRole(ROLE_ADMIN)))
        val defaultRoleUpdated =
            organizationApiService.getOrganizationSecurity(organizationCreated.id).default
        assertNotEquals(defaultRoleCreated, defaultRoleUpdated)
      }
    }

    @Test
    fun `setOrganizationDefaultSecurity as resource admin and non-existing role`() {
      assertThrows<CsmClientException> {
        val name = "o-connector-test-1"
        val organizationCreated =
            organizationApiService.createOrganization(createTestOrganization(name))
        organizationApiService.setOrganizationDefaultSecurity(
            organizationCreated.id, OrganizationRole(UNKNOWN_IDENTIFIER))
      }
    }

    @Test
    fun `setOrganizationDefaultSecurity as not resource admin`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationCreated =
            organizationApiService.createOrganization(createTestOrganization(name))
        val defaultRoleCreated = organizationCreated.security.default
        runAsPlatformAdmin()
        assertNotNull(
            organizationApiService.setOrganizationDefaultSecurity(
                organizationCreated.id, OrganizationRole(ROLE_ADMIN)))
        val defaultRoleUpdated =
            organizationApiService.getOrganizationSecurity(organizationCreated.id).default
        assertNotEquals(defaultRoleCreated, defaultRoleUpdated)
      }
    }

    @Test
    fun `setOrganizationDefaultSecurity as not resource admin and no WRITE_SECURITY_PERMISSION`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationCreated =
            organizationApiService.createOrganization(
                createTestOrganizationWithSimpleSecurity(
                    name, TEST_ADMIN_USER_ID, ROLE_NONE, ROLE_NONE))
        val defaultRoleCreated = organizationCreated.security.default
        runAsPlatformAdmin()
        assertNotNull(
            organizationApiService.setOrganizationDefaultSecurity(
                organizationCreated.id, OrganizationRole(ROLE_ADMIN)))
        val defaultRoleUpdated =
            organizationApiService.getOrganizationSecurity(organizationCreated.id).default
        assertNotEquals(defaultRoleCreated, defaultRoleUpdated)
      }
    }

    @Test
    fun `getOrganizationAccessControl as not resource admin and current user`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationCreated =
            organizationApiService.createOrganization(createTestOrganization(name))
        val organizationRole =
            organizationApiService.getOrganizationAccessControl(
                organizationCreated.id, TEST_ADMIN_USER_ID)
        assertNotNull(organizationRole)
        assertEquals(ROLE_ADMIN, organizationRole.role)
        assertEquals(TEST_ADMIN_USER_ID, organizationRole.id)
      }
    }

    @Test
    fun `getOrganizationAccessControl as resource admin and non-existing user`() {
      assertThrows<CsmResourceNotFoundException> {
        val name = "o-connector-test-1"
        val organizationCreated =
            organizationApiService.createOrganization(createTestOrganization(name))
        organizationApiService.getOrganizationAccessControl(organizationCreated.id, "UNKOWN")
      }
    }

    @Test
    fun `getOrganizationAccessControl as not resource admin, READ_SECURITY permission and existing user`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationCreated =
            organizationApiService.createOrganization(
                createTestOrganizationWithSimpleSecurity(
                    name, TEST_ADMIN_USER_ID, ROLE_NONE, ROLE_VIEWER))
        runAsPlatformAdmin()
        val organizationRole =
            organizationApiService.getOrganizationAccessControl(
                organizationCreated.id, TEST_ADMIN_USER_ID)
        assertNotNull(organizationRole)
        assertEquals(ROLE_VIEWER, organizationRole.role)
        assertEquals(TEST_ADMIN_USER_ID, organizationRole.id)
      }
    }

    @Test
    fun `getOrganizationAccessControl as not resource admin, READ_SECURITY permission and non existing user`() {
      assertThrows<CsmResourceNotFoundException> {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationCreated =
            organizationApiService.createOrganization(
                createTestOrganizationWithSimpleSecurity(
                    name, TEST_ADMIN_USER_ID, ROLE_NONE, ROLE_VIEWER))
        runAsPlatformAdmin()
        organizationApiService.getOrganizationAccessControl(
            organizationCreated.id, UNKNOWN_IDENTIFIER)
      }
    }

    @Test
    fun `getOrganizationAccessControl as not resource admin, no READ_SECURITY permission, non-existing user`() {
      assertThrows<CsmResourceNotFoundException> {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationCreated =
            organizationApiService.createOrganization(
                createTestOrganizationWithSimpleSecurity(
                    name, TEST_ADMIN_USER_ID, ROLE_NONE, ROLE_NONE))
        runAsPlatformAdmin()
        organizationApiService.getOrganizationAccessControl(
            organizationCreated.id, UNKNOWN_IDENTIFIER)
      }
    }

    @Test
    fun `getOrganizationAccessControl as not resource admin, no READ_SECURITY permission, existing user`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationCreated =
            organizationApiService.createOrganization(
                createTestOrganizationWithSimpleSecurity(
                    name, TEST_ADMIN_USER_ID, ROLE_NONE, ROLE_NONE))
        runAsPlatformAdmin()
        val organizationAccessControl =
            organizationApiService.getOrganizationAccessControl(
                organizationCreated.id, TEST_ADMIN_USER_ID)
        assertNotNull(organizationAccessControl)
        assertEquals(TEST_ADMIN_USER_ID, organizationAccessControl.id)
        assertEquals(ROLE_NONE, organizationAccessControl.role)
      }
    }

    @Test
    fun `addOrganizationAccessControl as resource admin`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationCreated =
            organizationApiService.createOrganization(createTestOrganization(name))
        assertThrows<CsmResourceNotFoundException> {
          organizationApiService.getOrganizationAccessControl(
              organizationCreated.id, OTHER_TEST_USER_ID)
        }
        val otherUserACL = OrganizationAccessControl(id = OTHER_TEST_USER_ID, role = ROLE_VIEWER)
        assertNotNull(
            organizationApiService.addOrganizationAccessControl(
                organizationCreated.id, otherUserACL))

        val otherUserACLRetrieved =
            organizationApiService.getOrganizationAccessControl(
                organizationCreated.id, OTHER_TEST_USER_ID)
        assertEquals(otherUserACL, otherUserACLRetrieved)
      }
    }

    @Test
    fun `addOrganizationAccessControl as resource admin (ROLE_NONE)`() {
      assertThrows<CsmClientException> {
        val name = "o-connector-test-1"
        val organizationCreated =
            organizationApiService.createOrganization(createTestOrganization(name))

        val otherUserACL = OrganizationAccessControl(id = OTHER_TEST_USER_ID, role = ROLE_NONE)
        organizationApiService.addOrganizationAccessControl(organizationCreated.id, otherUserACL)
      }
    }

    @Test
    fun `addOrganizationAccessControl as not resource admin and PERMISSION_WRITE_SECURITY`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationCreated =
            organizationApiService.createOrganization(
                createTestOrganizationWithSimpleSecurity(
                    name, TEST_ADMIN_USER_ID, ROLE_NONE, ROLE_ADMIN))
        runAsPlatformAdmin()
        assertThrows<CsmResourceNotFoundException> {
          organizationApiService.getOrganizationAccessControl(organizationCreated.id, TEST_USER_ID)
        }
        val otherUserACL = OrganizationAccessControl(id = TEST_USER_ID, role = ROLE_VIEWER)
        assertNotNull(
            organizationApiService.addOrganizationAccessControl(
                organizationCreated.id, otherUserACL))

        val otherUserACLRetrieved =
            organizationApiService.getOrganizationAccessControl(
                organizationCreated.id, TEST_USER_ID)
        assertEquals(otherUserACL, otherUserACLRetrieved)
      }
    }

    @Test
    fun `addOrganizationAccessControl as not resource admin and no PERMISSION_WRITE_SECURITY`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationCreated =
            organizationApiService.createOrganization(
                createTestOrganizationWithSimpleSecurity(
                    name, TEST_ADMIN_USER_ID, ROLE_NONE, ROLE_NONE))
        runAsPlatformAdmin()
        assertThrows<CsmResourceNotFoundException> {
          organizationApiService.getOrganizationAccessControl(organizationCreated.id, TEST_USER_ID)
        }
        val otherUserACL = OrganizationAccessControl(id = TEST_USER_ID, role = ROLE_VIEWER)
        assertNotNull(
            organizationApiService.addOrganizationAccessControl(
                organizationCreated.id, otherUserACL))

        val otherUserACLRetrieved =
            organizationApiService.getOrganizationAccessControl(
                organizationCreated.id, TEST_USER_ID)
        assertEquals(otherUserACL, otherUserACLRetrieved)
      }
    }

    @Test
    fun `updateOrganizationAccessControl as resource admin cannot update last admin`() {
      assertThrows<CsmAccessForbiddenException> {
        val name = "o-connector-test-1"
        val organizationCreated =
            organizationApiService.createOrganization(createTestOrganization(name))
        organizationApiService.updateOrganizationAccessControl(
            organizationCreated.id, TEST_ADMIN_USER_ID, OrganizationRole(role = ROLE_VIEWER))
      }
    }

    @Test
    fun `updateOrganizationAccessControl as resource admin can update user (!= ROLE_NONE)`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationCreated =
            organizationApiService.createOrganization(createTestOrganization(name))

        organizationApiService.addOrganizationAccessControl(
            organizationCreated.id,
            OrganizationAccessControl(id = OTHER_TEST_USER_ID, role = ROLE_VIEWER))

        assertNotNull(
            organizationApiService.updateOrganizationAccessControl(
                organizationCreated.id, OTHER_TEST_USER_ID, OrganizationRole(role = ROLE_EDITOR)))

        val userACLRetrieved =
            organizationApiService.getOrganizationAccessControl(
                organizationCreated.id, OTHER_TEST_USER_ID)
        assertNotEquals(ROLE_VIEWER, userACLRetrieved.role)
        assertEquals(ROLE_EDITOR, userACLRetrieved.role)
      }
    }

    @Test
    fun `updateOrganizationAccessControl as resource admin cannot update user (= ROLE_NONE)`() {
      assertThrows<CsmClientException> {
        val name = "o-connector-test-1"
        val organizationCreated =
            organizationApiService.createOrganization(createTestOrganization(name))

        organizationApiService.updateOrganizationAccessControl(
            organizationCreated.id, TEST_USER_ID, OrganizationRole(role = ROLE_NONE))
      }
    }

    @Test
    fun `updateOrganizationAccessControl as resource admin and unknown ACL user`() {
      assertThrows<CsmResourceNotFoundException> {
        val name = "o-connector-test-1"
        val organizationCreated =
            organizationApiService.createOrganization(createTestOrganization(name))

        organizationApiService.updateOrganizationAccessControl(
            organizationCreated.id, UNKNOWN_IDENTIFIER, OrganizationRole(role = ROLE_EDITOR))
      }
    }

    @Test
    fun `updateOrganizationAccessControl as resource admin and wrong role`() {
      assertThrows<CsmClientException> {
        val name = "o-connector-test-1"
        val organizationCreated =
            organizationApiService.createOrganization(createTestOrganization(name))

        organizationApiService.updateOrganizationAccessControl(
            organizationCreated.id, TEST_USER_ID, OrganizationRole(role = UNKNOWN_IDENTIFIER))
      }
    }

    @Test
    fun `updateOrganizationAccessControl as not resource admin, WRITE_SECURITY permission, can update user (!= ROLE_NONE)`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationCreated =
            organizationApiService.createOrganization(createTestOrganization(name))

        organizationApiService.addOrganizationAccessControl(
            organizationCreated.id,
            OrganizationAccessControl(id = TEST_ADMIN_USER_ID, role = ROLE_ADMIN))

        runAsPlatformAdmin()

        assertNotNull(
            organizationApiService.updateOrganizationAccessControl(
                organizationCreated.id, OTHER_TEST_USER_ID, OrganizationRole(role = ROLE_EDITOR)))

        val userACLRetrieved =
            organizationApiService.getOrganizationAccessControl(
                organizationCreated.id, OTHER_TEST_USER_ID)
        assertEquals(ROLE_EDITOR, userACLRetrieved.role)
      }
    }

    @Test
    fun `updateOrganizationAccessControl as not resource admin, no WRITE_SECURITY permission`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationCreated =
            organizationApiService.createOrganization(createTestOrganization(name))

        organizationApiService.addOrganizationAccessControl(
            organizationCreated.id,
            OrganizationAccessControl(id = TEST_ADMIN_USER_ID, role = ROLE_VIEWER))

        runAsPlatformAdmin()

        assertNotNull(
            organizationApiService.updateOrganizationAccessControl(
                organizationCreated.id, TEST_ADMIN_USER_ID, OrganizationRole(role = ROLE_EDITOR)))

        val userACLRetrieved =
            organizationApiService.getOrganizationAccessControl(
                organizationCreated.id, TEST_ADMIN_USER_ID)
        assertEquals(ROLE_EDITOR, userACLRetrieved.role)
      }
    }

    @Test
    fun `updateOrganizationAccessControl as not resource admin, no WRITE_SECURITY permission, ROLE_NONE`() {
      assertThrows<CsmClientException> {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationCreated =
            organizationApiService.createOrganization(createTestOrganization(name))

        organizationApiService.addOrganizationAccessControl(
            organizationCreated.id,
            OrganizationAccessControl(id = TEST_USER_ID, role = ROLE_VIEWER))

        runAsPlatformAdmin()

        organizationApiService.updateOrganizationAccessControl(
            organizationCreated.id, OTHER_TEST_USER_ID, OrganizationRole(role = ROLE_NONE))
      }
    }

    @Test
    fun `removeOrganizationAccessControl as resource admin`() {
      val name = "o-connector-test-1"
      val organizationCreated =
          organizationApiService.createOrganization(createTestOrganization(name))

      val otherUserACL = OrganizationAccessControl(id = OTHER_TEST_USER_ID, role = ROLE_VIEWER)
      organizationApiService.addOrganizationAccessControl(organizationCreated.id, otherUserACL)

      organizationApiService.removeOrganizationAccessControl(
          organizationCreated.id, OTHER_TEST_USER_ID)
      assertThrows<CsmResourceNotFoundException> {
        organizationApiService.getOrganizationAccessControl(
            organizationCreated.id, OTHER_TEST_USER_ID)
      }
    }

    @Test
    fun `removeOrganizationAccessControl as not resource admin, WRITE_SECURITY permission`() {
      val name = "o-connector-test-1"
      runAsDifferentOrganizationUser()
      val organizationCreated =
          organizationApiService.createOrganization(createTestOrganization(name))

      val otherUserACL = OrganizationAccessControl(id = TEST_USER_ID, role = ROLE_ADMIN)
      organizationApiService.addOrganizationAccessControl(organizationCreated.id, otherUserACL)

      runAsPlatformAdmin()
      organizationApiService.removeOrganizationAccessControl(
          organizationCreated.id, OTHER_TEST_USER_ID)
      assertThrows<CsmResourceNotFoundException> {
        organizationApiService.getOrganizationAccessControl(
            organizationCreated.id, OTHER_TEST_USER_ID)
      }
    }

    @Test
    fun `removeOrganizationAccessControl as not resource admin, no WRITE_SECURITY permission`() {
      val name = "o-connector-test-1"
      runAsDifferentOrganizationUser()
      val organizationCreated =
          organizationApiService.createOrganization(createTestOrganization(name))

      val otherUserACL = OrganizationAccessControl(id = TEST_USER_ID, role = ROLE_ADMIN)
      organizationApiService.addOrganizationAccessControl(organizationCreated.id, otherUserACL)

      runAsPlatformAdmin()
      organizationApiService.removeOrganizationAccessControl(
          organizationCreated.id, OTHER_TEST_USER_ID)
      assertThrows<CsmResourceNotFoundException> {
        organizationApiService.getOrganizationAccessControl(
            organizationCreated.id, OTHER_TEST_USER_ID)
      }
    }

    @Test
    fun `getOrganizationSecurityUsers as resource admin`() {
      val name = "o-connector-test-1"
      val organizationCreated =
          organizationApiService.createOrganization(createTestOrganization(name))

      val orgaUsers = organizationApiService.getOrganizationSecurityUsers(organizationCreated.id)
      assertEquals(listOf(TEST_ADMIN_USER_ID), orgaUsers)
    }

    @Test
    fun `getOrganizationSecurityUsers as not resource admin, READ_SECURITY permission`() {
      val name = "o-connector-test-1"
      runAsDifferentOrganizationUser()
      val organizationCreated =
          organizationApiService.createOrganization(
              createTestOrganizationWithSimpleSecurity(name, TEST_USER_ID, ROLE_NONE, ROLE_VIEWER))
      runAsPlatformAdmin()
      val orgaUsers = organizationApiService.getOrganizationSecurityUsers(organizationCreated.id)
      assertEquals(listOf(TEST_USER_ID, OTHER_TEST_USER_ID), orgaUsers)
    }

    @Test
    fun `getOrganizationSecurityUsers as not resource admin, no READ_SECURITY permission`() {
      val name = "o-connector-test-1"
      runAsDifferentOrganizationUser()
      val organizationCreated =
          organizationApiService.createOrganization(
              createTestOrganizationWithSimpleSecurity(name, TEST_USER_ID, ROLE_NONE, ROLE_NONE))
      runAsPlatformAdmin()
      val orgaUsers = organizationApiService.getOrganizationSecurityUsers(organizationCreated.id)
      assertEquals(listOf(TEST_USER_ID, OTHER_TEST_USER_ID), orgaUsers)
    }

    @Test
    fun `access control list shouldn't contain more than one time each user on creation`() {
      val brokenOrganization =
          OrganizationRequest(
              name = "organization",
              security =
                  OrganizationSecurity(
                      default = ROLE_NONE,
                      accessControlList =
                          mutableListOf(
                              OrganizationAccessControl(TEST_USER_ID, ROLE_ADMIN),
                              OrganizationAccessControl(TEST_USER_ID, ROLE_EDITOR))))
      assertThrows<IllegalArgumentException> {
        organizationApiService.createOrganization(brokenOrganization)
      }
    }

    @Test
    fun `access control list shouldn't contain more than one time each user on ACL addition`() {
      val workingOrganization =
          OrganizationRequest(
              name = "organization",
              security =
                  OrganizationSecurity(
                      default = ROLE_NONE,
                      accessControlList =
                          mutableListOf(OrganizationAccessControl(TEST_USER_ID, ROLE_ADMIN))))
      val organizationSaved = organizationApiService.createOrganization(workingOrganization)

      assertThrows<IllegalArgumentException> {
        organizationApiService.addOrganizationAccessControl(
            organizationSaved.id, OrganizationAccessControl(TEST_USER_ID, ROLE_EDITOR))
      }
    }

    @Test
    fun `testVerifyPermissionsAndReturnOrganization`() {
      val name = "o-connector-test-1"
      val organizationCreated =
          organizationApiService.createOrganization(createTestOrganization(name))
      assertDoesNotThrow {
        val organizationVerified =
            organizationApiService.getVerifiedOrganization(organizationCreated.id)
        assertEquals(organizationCreated, organizationVerified)
      }
    }

    @Test
    fun `testVerifyPermissionsAndReturnOrganization with organization with restricted permission`() {
      val name = "o-connector-test-1"
      runAsDifferentOrganizationUser()
      val organizationCreated =
          organizationApiService.createOrganization(
              createTestOrganizationWithSimpleSecurity(name, TEST_USER_ID, ROLE_NONE, ROLE_NONE))
      runAsPlatformAdmin()
      assertDoesNotThrow {
        val organizationVerified =
            organizationApiService.getVerifiedOrganization(organizationCreated.id)
        assertEquals(organizationCreated, organizationVerified)
      }
    }

    @Test
    fun `testVerifyPermissionsAndReturnOrganization with unknown organization id`() {
      assertThrows<CsmResourceNotFoundException> {
        organizationApiService.getVerifiedOrganization("wrong_orga_id")
      }
    }
  }

  private fun testFindAllWithRBAC(
      numberOfOrganizationCreated: Int,
      numberOfOrganizationReachableByTestUser: Int
  ) {
    val defaultPageSize = csmPlatformProperties.twincache.organization.defaultPageSize

    testListOrganizations(null, null, numberOfOrganizationReachableByTestUser)
    testListOrganizations(0, null, defaultPageSize)
    testListOrganizations(0, numberOfOrganizationCreated, numberOfOrganizationReachableByTestUser)
    testListOrganizations(1, 200, 0)
    testListOrganizations(1, 15, 15)
  }

  private fun testListOrganizationsWithWrongValues() {
    logger.info("Should throw IllegalArgumentException when page and size are zeros")
    assertThrows<IllegalArgumentException> { organizationApiService.listOrganizations(0, 0) }

    logger.info("Should throw IllegalArgumentException when page is negative")
    assertThrows<IllegalArgumentException> { organizationApiService.listOrganizations(-1, 10) }

    logger.info("Should throw IllegalArgumentException when size is negative")
    assertThrows<IllegalArgumentException> { organizationApiService.listOrganizations(0, -1) }
  }

  private fun testGetOrganizationAsNotOwner(
      hasUserSecurity: Boolean,
      userId: String?,
      defaultRole: String?,
      userRole: String?,
      throwException: Boolean,
      runFindOrganizationByIdAs: () -> Unit
  ) {
    runAsDifferentOrganizationUser()
    val organizationId = "o-connector-test-1"
    val organization =
        if (hasUserSecurity) {
          createTestOrganizationWithSimpleSecurity(
              organizationId, userId!!, defaultRole!!, userRole!!)
        } else {
          createTestOrganization(organizationId)
        }
    val organizationCreated = organizationApiService.createOrganization(organization)

    runFindOrganizationByIdAs()
    if (throwException) {
      assertThrows<CsmAccessForbiddenException> {
        (organizationApiService.getOrganization(organizationCreated.id))
      }
    } else {
      assertNotNull(organizationCreated)
    }
  }

  /** Run a test with current user as Organization.User */
  private fun runAsOrganizationUser() {
    mockkStatic(::getCurrentAuthentication)
    every { getCurrentAuthentication() } returns mockk<BearerTokenAuthentication>()
    every { getCurrentAccountIdentifier(any()) } returns TEST_USER_ID
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "test.user"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf(ROLE_ORGANIZATION_USER)
  }

  /** Run a test with current user as Platform.Admin */
  private fun runAsPlatformAdmin() {
    mockkStatic(::getCurrentAuthentication)
    every { getCurrentAuthentication() } returns mockk<BearerTokenAuthentication>()
    every { getCurrentAccountIdentifier(any()) } returns TEST_ADMIN_USER_ID
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "test.admin"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf(ROLE_PLATFORM_ADMIN)
  }

  /** Run a test with different Organization.User */
  private fun runAsDifferentOrganizationUser() {
    every { getCurrentAccountIdentifier(any()) } returns OTHER_TEST_USER_ID
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "test.other.user"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf(ROLE_ORGANIZATION_USER)
  }

  /** Organization batch creation */
  internal fun batchOrganizationCreation(numberOfOrganizationToCreate: Int) {
    logger.info("Creating $numberOfOrganizationToCreate connectors...")
    IntRange(1, numberOfOrganizationToCreate).forEach {
      val newOrganization = createTestOrganization("o-connector-test-$it")
      organizationApiService.createOrganization(newOrganization)
    }
  }

  /** Organization batch creation with RBAC */
  internal fun createOrganizationsWithAllCombinationOfRole(userId: String): Int {
    val roleList =
        listOf(
            ROLE_VIEWER,
            ROLE_EDITOR,
            ROLE_VALIDATOR,
            ROLE_USER,
            ROLE_NONE,
            ROLE_ADMIN,
        )
    var numberOfOrganizationCreated = 0
    roleList.forEach { defaultSecurity ->
      roleList.forEach { securityRole ->
        val organization =
            createTestOrganizationWithSimpleSecurity(
                "Organization with $defaultSecurity as default and $userId as $securityRole",
                userId,
                defaultSecurity,
                securityRole)
        organizationApiService.createOrganization(organization)
        numberOfOrganizationCreated++
      }
    }
    return numberOfOrganizationCreated
  }

  /** Create default test Connector */
  internal fun createTestOrganization(name: String): OrganizationRequest {
    return OrganizationRequest(name = name)
  }

  /** Create default test Connector */
  internal fun createTestOrganizationWithSimpleSecurity(
      name: String,
      userName: String,
      defaultSecurity: String,
      role: String,
  ): OrganizationRequest {
    return OrganizationRequest(
        name = name,
        security =
            OrganizationSecurity(
                default = defaultSecurity,
                accessControlList = mutableListOf(OrganizationAccessControl(userName, role))))
  }

  internal fun testListOrganizations(page: Int?, size: Int?, expectedResultSize: Int) {
    val organizationList = organizationApiService.listOrganizations(page, size)
    logger.info("Organization list retrieved contains : ${organizationList.size} elements")
    assertEquals(expectedResultSize, organizationList.size)
  }
}
