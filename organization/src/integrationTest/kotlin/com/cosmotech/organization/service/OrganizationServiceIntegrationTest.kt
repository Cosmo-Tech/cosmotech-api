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
import com.cosmotech.organization.domain.OrganizationCreateRequest
import com.cosmotech.organization.domain.OrganizationRole
import com.cosmotech.organization.domain.OrganizationSecurity
import com.cosmotech.organization.domain.OrganizationUpdateRequest
import com.redis.om.spring.indexing.RediSearchIndexer
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.mockkStatic
import java.time.Instant
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
  var startTime: Long = 0

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
    startTime = Instant.now().toEpochMilli()
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
      testlistOrganizations(null, null, numberOfOrganizationToCreate)
      testlistOrganizations(0, null, defaultPageSize)
      testlistOrganizations(0, 10, 10)
      testlistOrganizations(1, 200, 0)
      testlistOrganizations(1, 15, 5)
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
      testlistOrganizationsWithWrongValues()
    }

    @Test
    fun `getOrganization as resource admin`() {
      val organizationRegistered =
          organizationApiService.createOrganization(
              makeSimpleOrganizationCreateRequest("o-connector-test-1"))
      assertNotNull(organizationApiService.getOrganization(organizationRegistered.id))
    }

    @Test
    fun `getOrganization as not resource admin`() {
      testgetOrganizationAsNotOwner(false, null, null, null, true) { runAsOrganizationUser() }
    }

    @Test
    fun `getOrganization as not resource admin but with READ role`() {
      testgetOrganizationAsNotOwner(true, TEST_USER_ID, ROLE_NONE, ROLE_USER, false) {
        runAsOrganizationUser()
      }
    }

    @Test
    fun `getOrganization as not resource admin but with WRITE role`() {
      testgetOrganizationAsNotOwner(true, TEST_USER_ID, ROLE_NONE, ROLE_EDITOR, false) {
        runAsOrganizationUser()
      }
    }

    @Test
    fun `getOrganization as not resource admin but with NONE role`() {
      testgetOrganizationAsNotOwner(true, TEST_USER_ID, ROLE_NONE, ROLE_NONE, true) {
        runAsOrganizationUser()
      }
    }

    @Test
    fun `createOrganization with minimal values`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationToRegister = makeSimpleOrganizationCreateRequest(name)
        val organizationRegistered =
            organizationApiService.createOrganization(organizationToRegister)
        assertEquals(
            OrganizationSecurity(
                default = ROLE_NONE,
                mutableListOf(OrganizationAccessControl(id = TEST_USER_ID, role = ROLE_ADMIN))),
            organizationRegistered.security)
        assertEquals(name, organizationRegistered.name)
        assertTrue(organizationRegistered.id.startsWith("o-"))
      }
    }

    @Test
    fun `createOrganization without required organization name`() {
      assertThrows<IllegalArgumentException> {
        organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(""))
      }
    }

    @Test
    fun `createOrganization with security values`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationToRegister =
            makeOrganizationCreateRequestWithSimpleSecurity(
                name, OTHER_TEST_USER_ID, ROLE_USER, ROLE_NONE)
        val organizationRegistered =
            organizationApiService.createOrganization(organizationToRegister)
        assertEquals(
            OrganizationSecurity(
                default = ROLE_USER,
                mutableListOf(
                    OrganizationAccessControl(id = OTHER_TEST_USER_ID, role = ROLE_NONE),
                    OrganizationAccessControl(id = TEST_USER_ID, role = ROLE_ADMIN))),
            organizationRegistered.security)
        assertEquals(name, organizationRegistered.name)
        assertTrue(organizationRegistered.id.startsWith("o-"))
      }
    }

    @Test
    fun `deleteOrganization as resource admin`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationToRegister = makeSimpleOrganizationCreateRequest(name)
        val organizationRegistered =
            organizationApiService.createOrganization(organizationToRegister)
        organizationApiService.deleteOrganization(organizationRegistered.id)
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
            makeOrganizationCreateRequestWithSimpleSecurity(
                name, OTHER_TEST_USER_ID, ROLE_USER, ROLE_ADMIN)
        val organizationRegistered =
            organizationApiService.createOrganization(organizationToRegister)
        organizationApiService.deleteOrganization(organizationRegistered.id)
      }
    }

    @Test
    fun `deleteOrganization not as resource admin but DELETE permission `() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationToRegister =
            makeOrganizationCreateRequestWithSimpleSecurity(
                name, TEST_USER_ID, ROLE_NONE, ROLE_ADMIN)
        val organizationRegistered =
            organizationApiService.createOrganization(organizationToRegister)
        runAsOrganizationUser()
        organizationApiService.deleteOrganization(organizationRegistered.id)
      }
    }

    @Test
    fun `updateOrganization as resource admin, organization name`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))
        val newName = "my-new-name"
        val organizationUpdated =
            organizationApiService.updateOrganization(
                organizationRegistered.id, OrganizationUpdateRequest(newName))

        assertEquals(newName, organizationApiService.getOrganization(organizationUpdated.id).name)

        // Update organization with empty body
        organizationApiService.updateOrganization(
            organizationUpdated.id, OrganizationUpdateRequest())

        assertEquals(newName, organizationApiService.getOrganization(organizationUpdated.id).name)

        // Update organization with null value as name
        organizationApiService.updateOrganization(
            organizationUpdated.id, OrganizationUpdateRequest(null))

        assertEquals(newName, organizationApiService.getOrganization(organizationUpdated.id).name)
      }
    }

    @Test
    fun `updateOrganization not as resource admin with WRITE permission`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.createOrganization(
                makeOrganizationCreateRequestWithSimpleSecurity(
                    name, TEST_USER_ID, ROLE_NONE, ROLE_EDITOR))

        runAsOrganizationUser()

        val newName = "my-new-name"
        val organizationUpdated =
            organizationApiService.updateOrganization(
                organizationRegistered.id, OrganizationUpdateRequest(newName))

        assertEquals(newName, organizationApiService.getOrganization(organizationUpdated.id).name)

        // Update organization with empty body
        organizationApiService.updateOrganization(
            organizationUpdated.id, OrganizationUpdateRequest())

        assertEquals(newName, organizationApiService.getOrganization(organizationUpdated.id).name)

        // Update organization with null value as name
        organizationApiService.updateOrganization(
            organizationUpdated.id, OrganizationUpdateRequest(null))

        assertEquals(newName, organizationApiService.getOrganization(organizationUpdated.id).name)
      }
    }

    @Test
    fun `updateOrganization not as resource admin with no WRITE permission`() {
      assertThrows<CsmAccessForbiddenException> {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))
        runAsOrganizationUser()
        organizationApiService.updateOrganization(
            organizationRegistered.id, OrganizationUpdateRequest("name"))
      }
    }

    @Test
    fun listPermissions() {
      val mapAllPermissions =
          listOf(
              ComponentRolePermissions(
                  component = "organization",
                  roles =
                      mutableMapOf(
                          ROLE_VIEWER to mutableListOf(PERMISSION_READ),
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
                          ROLE_VIEWER to mutableListOf(PERMISSION_READ),
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
                          ROLE_VIEWER to mutableListOf(PERMISSION_READ),
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
      assertEquals(mapAllPermissions, organizationApiService.listPermissions())
    }

    @Test
    fun `getOrganizationPermissions as resource admin`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))
        var organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(
                organizationRegistered.id, ROLE_VIEWER)
        assertEquals(mutableListOf(PERMISSION_READ), organizationUserPermissions)
        organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(organizationRegistered.id, ROLE_USER)
        assertEquals(
            mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_CREATE_CHILDREN),
            organizationUserPermissions)
        organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(
                organizationRegistered.id, ROLE_EDITOR)
        assertEquals(
            mutableListOf(
                PERMISSION_READ,
                PERMISSION_READ_SECURITY,
                PERMISSION_CREATE_CHILDREN,
                PERMISSION_WRITE),
            organizationUserPermissions)
        organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(organizationRegistered.id, ROLE_ADMIN)
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
                organizationRegistered.id, UNKNOWN_IDENTIFIER)
        assertEquals(emptyList(), organizationUserPermissions)
      }
    }

    @Test
    fun `getOrganizationPermissions as not resource admin and READ_SECURITY permission`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.createOrganization(
                makeOrganizationCreateRequestWithSimpleSecurity(
                    name, TEST_USER_ID, ROLE_NONE, ROLE_USER))
        runAsOrganizationUser()
        var organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(
                organizationRegistered.id, ROLE_VIEWER)
        assertEquals(mutableListOf(PERMISSION_READ), organizationUserPermissions)
        organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(organizationRegistered.id, ROLE_USER)
        assertEquals(
            mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_CREATE_CHILDREN),
            organizationUserPermissions)
        organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(
                organizationRegistered.id, ROLE_EDITOR)
        assertEquals(
            mutableListOf(
                PERMISSION_READ,
                PERMISSION_READ_SECURITY,
                PERMISSION_CREATE_CHILDREN,
                PERMISSION_WRITE),
            organizationUserPermissions)
        organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(organizationRegistered.id, ROLE_ADMIN)
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
                organizationRegistered.id, UNKNOWN_IDENTIFIER)
        assertEquals(emptyList(), organizationUserPermissions)
      }
    }

    @Test
    fun `getOrganizationPermissions as not resource admin and no permission`() {
      assertThrows<CsmAccessForbiddenException> {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))
        runAsOrganizationUser()
        organizationApiService.getOrganizationPermissions(organizationRegistered.id, ROLE_VIEWER)
      }
    }

    @Test
    fun `getOrganizationSecurity as resource admin`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))
        assertNotNull(organizationApiService.getOrganizationSecurity(organizationRegistered.id))
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
        val organizationRegistered =
            organizationApiService.createOrganization(
                makeOrganizationCreateRequestWithSimpleSecurity(
                    name, TEST_USER_ID, ROLE_NONE, ROLE_USER))
        runAsOrganizationUser()
        assertNotNull(organizationApiService.getOrganizationSecurity(organizationRegistered.id))
      }
    }

    @Test
    fun `getOrganizationSecurity as not resource admin with no READ_SECURITY permission`() {
      assertThrows<CsmAccessForbiddenException> {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.createOrganization(
                makeOrganizationCreateRequestWithSimpleSecurity(
                    name, TEST_USER_ID, ROLE_NONE, ROLE_NONE))
        runAsOrganizationUser()
        organizationApiService.getOrganizationSecurity(organizationRegistered.id)
      }
    }

    @Test
    fun `updateOrganizationDefaultSecurity as resource admin and existing role`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))
        val defaultRoleCreated = organizationRegistered.security.default
        assertNotNull(
            organizationApiService.updateOrganizationDefaultSecurity(
                organizationRegistered.id, OrganizationRole(ROLE_ADMIN)))
        val defaultRoleUpdated =
            organizationApiService.getOrganizationSecurity(organizationRegistered.id).default
        assertNotEquals(defaultRoleCreated, defaultRoleUpdated)
      }
    }

    @Test
    fun `updateOrganizationDefaultSecurity as resource admin and non-existing role`() {
      assertThrows<CsmClientException> {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))
        organizationApiService.updateOrganizationDefaultSecurity(
            organizationRegistered.id, OrganizationRole(UNKNOWN_IDENTIFIER))
      }
    }

    @Test
    fun `updateOrganizationDefaultSecurity as not resource admin and WRITE_SECURITY_PERMISSION`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.createOrganization(
                makeOrganizationCreateRequestWithSimpleSecurity(
                    name, TEST_USER_ID, ROLE_NONE, ROLE_ADMIN))
        val defaultRoleCreated = organizationRegistered.security.default
        runAsOrganizationUser()
        assertNotNull(
            organizationApiService.updateOrganizationDefaultSecurity(
                organizationRegistered.id, OrganizationRole(ROLE_ADMIN)))
        val defaultRoleUpdated =
            organizationApiService.getOrganizationSecurity(organizationRegistered.id).default
        assertNotEquals(defaultRoleCreated, defaultRoleUpdated)
      }
    }

    @Test
    fun `updateOrganizationDefaultSecurity as not resource admin and no WRITE_SECURITY_PERMISSION`() {
      assertThrows<CsmAccessForbiddenException> {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.createOrganization(
                makeOrganizationCreateRequestWithSimpleSecurity(
                    name, TEST_USER_ID, ROLE_NONE, ROLE_USER))
        runAsOrganizationUser()
        organizationApiService.updateOrganizationDefaultSecurity(
            organizationRegistered.id, OrganizationRole(ROLE_ADMIN))
      }
    }

    @Test
    fun `getOrganizationAccessControl as resource admin and current user`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))
        val organizationRole =
            organizationApiService.getOrganizationAccessControl(
                organizationRegistered.id, TEST_USER_ID)
        assertNotNull(organizationRole)
        assertEquals(ROLE_ADMIN, organizationRole.role)
        assertEquals(TEST_USER_ID, organizationRole.id)
      }
    }

    @Test
    fun `getOrganizationAccessControl as resource admin and non-existing user`() {
      assertThrows<CsmResourceNotFoundException> {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))
        organizationApiService.getOrganizationAccessControl(organizationRegistered.id, "UNKOWN")
      }
    }

    @Test
    fun `getOrganizationAccessControl as not resource admin, READ_SECURITY permission and existing user`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.createOrganization(
                makeOrganizationCreateRequestWithSimpleSecurity(
                    name, TEST_USER_ID, ROLE_NONE, ROLE_USER))
        runAsOrganizationUser()
        val organizationRole =
            organizationApiService.getOrganizationAccessControl(
                organizationRegistered.id, TEST_USER_ID)
        assertNotNull(organizationRole)
        assertEquals(ROLE_USER, organizationRole.role)
        assertEquals(TEST_USER_ID, organizationRole.id)
      }
    }

    @Test
    fun `getOrganizationAccessControl as not resource admin, READ_SECURITY permission and non existing user`() {
      assertThrows<CsmResourceNotFoundException> {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.createOrganization(
                makeOrganizationCreateRequestWithSimpleSecurity(
                    name, TEST_USER_ID, ROLE_NONE, ROLE_USER))
        runAsOrganizationUser()
        organizationApiService.getOrganizationAccessControl(
            organizationRegistered.id, UNKNOWN_IDENTIFIER)
      }
    }

    @Test
    fun `getOrganizationAccessControl as not resource admin, no READ_SECURITY permission`() {
      assertThrows<CsmAccessForbiddenException> {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.createOrganization(
                makeOrganizationCreateRequestWithSimpleSecurity(
                    name, TEST_USER_ID, ROLE_NONE, ROLE_NONE))
        runAsOrganizationUser()
        organizationApiService.getOrganizationAccessControl(
            organizationRegistered.id, UNKNOWN_IDENTIFIER)
      }
    }

    @Test
    fun `createOrganizationAccessControl as resource admin`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))
        assertThrows<CsmResourceNotFoundException> {
          organizationApiService.getOrganizationAccessControl(
              organizationRegistered.id, OTHER_TEST_USER_ID)
        }
        val otherUserACL = OrganizationAccessControl(id = OTHER_TEST_USER_ID, role = ROLE_VIEWER)
        assertNotNull(
            organizationApiService.createOrganizationAccessControl(
                organizationRegistered.id, otherUserACL))

        val otherUserACLRetrieved =
            organizationApiService.getOrganizationAccessControl(
                organizationRegistered.id, OTHER_TEST_USER_ID)
        assertEquals(
            OrganizationAccessControl(id = OTHER_TEST_USER_ID, ROLE_VIEWER), otherUserACLRetrieved)
      }
    }

    @Test
    fun `createOrganizationAccessControl as resource admin (ROLE_NONE)`() {
      assertThrows<CsmClientException> {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))

        val otherUserACL = OrganizationAccessControl(id = OTHER_TEST_USER_ID, role = ROLE_NONE)
        organizationApiService.createOrganizationAccessControl(
            organizationRegistered.id, otherUserACL)
      }
    }

    @Test
    fun `createOrganizationAccessControl as not resource admin and PERMISSION_WRITE_SECURITY`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.createOrganization(
                makeOrganizationCreateRequestWithSimpleSecurity(
                    name, TEST_USER_ID, ROLE_NONE, ROLE_ADMIN))
        runAsOrganizationUser()
        assertThrows<CsmResourceNotFoundException> {
          organizationApiService.getOrganizationAccessControl(
              organizationRegistered.id, TEST_ADMIN_USER_ID)
        }
        val otherUserACL = OrganizationAccessControl(id = TEST_ADMIN_USER_ID, role = ROLE_VIEWER)
        assertNotNull(
            organizationApiService.createOrganizationAccessControl(
                organizationRegistered.id, otherUserACL))

        val otherUserACLRetrieved =
            organizationApiService.getOrganizationAccessControl(
                organizationRegistered.id, TEST_ADMIN_USER_ID)
        assertEquals(
            OrganizationAccessControl(TEST_ADMIN_USER_ID, ROLE_VIEWER), otherUserACLRetrieved)
      }
    }

    @Test
    fun `createOrganizationAccessControl as not resource admin and no PERMISSION_WRITE_SECURITY`() {
      assertThrows<CsmAccessForbiddenException> {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.createOrganization(
                makeOrganizationCreateRequestWithSimpleSecurity(
                    name, TEST_USER_ID, ROLE_NONE, ROLE_USER))
        runAsOrganizationUser()
        assertThrows<CsmResourceNotFoundException> {
          organizationApiService.getOrganizationAccessControl(
              organizationRegistered.id, TEST_ADMIN_USER_ID)
        }
        val otherUserACL = OrganizationAccessControl(id = TEST_ADMIN_USER_ID, role = ROLE_VIEWER)
        organizationApiService.createOrganizationAccessControl(
            organizationRegistered.id, otherUserACL)
      }
    }

    @Test
    fun `updateOrganizationAccessControl as resource admin cannot update last admin`() {
      assertThrows<CsmAccessForbiddenException> {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))
        organizationApiService.updateOrganizationAccessControl(
            organizationRegistered.id, TEST_USER_ID, OrganizationRole(role = ROLE_VIEWER))
      }
    }

    @Test
    fun `updateOrganizationAccessControl as resource admin can update user (!= ROLE_NONE)`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))

        organizationApiService.createOrganizationAccessControl(
            organizationRegistered.id,
            OrganizationAccessControl(id = OTHER_TEST_USER_ID, role = ROLE_VIEWER))

        assertNotNull(
            organizationApiService.updateOrganizationAccessControl(
                organizationRegistered.id,
                OTHER_TEST_USER_ID,
                OrganizationRole(role = ROLE_EDITOR)))

        val userACLRetrieved =
            organizationApiService.getOrganizationAccessControl(
                organizationRegistered.id, OTHER_TEST_USER_ID)
        assertNotEquals(ROLE_VIEWER, userACLRetrieved.role)
        assertEquals(ROLE_EDITOR, userACLRetrieved.role)
      }
    }

    @Test
    fun `updateOrganizationAccessControl as resource admin cannot update user (= ROLE_NONE)`() {
      assertThrows<CsmClientException> {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))

        organizationApiService.updateOrganizationAccessControl(
            organizationRegistered.id, TEST_USER_ID, OrganizationRole(role = ROLE_NONE))
      }
    }

    @Test
    fun `updateOrganizationAccessControl as resource admin and unknown ACL user`() {
      assertThrows<CsmResourceNotFoundException> {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))

        organizationApiService.updateOrganizationAccessControl(
            organizationRegistered.id, UNKNOWN_IDENTIFIER, OrganizationRole(role = ROLE_EDITOR))
      }
    }

    @Test
    fun `updateOrganizationAccessControl as resource admin and wrong role`() {
      assertThrows<CsmClientException> {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))

        organizationApiService.updateOrganizationAccessControl(
            organizationRegistered.id, TEST_USER_ID, OrganizationRole(role = UNKNOWN_IDENTIFIER))
      }
    }

    @Test
    fun `updateOrganizationAccessControl as not resource admin, WRITE_SECURITY permission, can update user (!= ROLE_NONE)`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))

        organizationApiService.createOrganizationAccessControl(
            organizationRegistered.id,
            OrganizationAccessControl(id = TEST_USER_ID, role = ROLE_ADMIN))

        runAsOrganizationUser()

        assertNotNull(
            organizationApiService.updateOrganizationAccessControl(
                organizationRegistered.id,
                OTHER_TEST_USER_ID,
                OrganizationRole(role = ROLE_EDITOR)))

        val userACLRetrieved =
            organizationApiService.getOrganizationAccessControl(
                organizationRegistered.id, OTHER_TEST_USER_ID)
        assertEquals(ROLE_EDITOR, userACLRetrieved.role)
      }
    }

    @Test
    fun `updateOrganizationAccessControl as not resource admin, no WRITE_SECURITY permission`() {
      assertThrows<CsmAccessForbiddenException> {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))

        organizationApiService.createOrganizationAccessControl(
            organizationRegistered.id,
            OrganizationAccessControl(id = TEST_USER_ID, role = ROLE_VIEWER))

        runAsOrganizationUser()

        organizationApiService.updateOrganizationAccessControl(
            organizationRegistered.id, OTHER_TEST_USER_ID, OrganizationRole(role = ROLE_EDITOR))
      }
    }

    @Test
    fun `updateOrganizationAccessControl as not resource admin, no WRITE_SECURITY permission, ROLE_NONE`() {
      assertThrows<CsmClientException> {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))

        organizationApiService.createOrganizationAccessControl(
            organizationRegistered.id,
            OrganizationAccessControl(id = TEST_USER_ID, role = ROLE_VIEWER))

        runAsOrganizationUser()

        organizationApiService.updateOrganizationAccessControl(
            organizationRegistered.id, OTHER_TEST_USER_ID, OrganizationRole(role = ROLE_NONE))
      }
    }

    @Test
    fun `deleteOrganizationAccessControl as resource admin`() {
      val name = "o-connector-test-1"
      val organizationRegistered =
          organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))

      val otherUserACL = OrganizationAccessControl(id = OTHER_TEST_USER_ID, role = ROLE_VIEWER)
      organizationApiService.createOrganizationAccessControl(
          organizationRegistered.id, otherUserACL)

      organizationApiService.deleteOrganizationAccessControl(
          organizationRegistered.id, OTHER_TEST_USER_ID)
      assertThrows<CsmResourceNotFoundException> {
        organizationApiService.getOrganizationAccessControl(
            organizationRegistered.id, OTHER_TEST_USER_ID)
      }
    }

    @Test
    fun `deleteOrganizationAccessControl as not resource admin, WRITE_SECURITY permission`() {
      val name = "o-connector-test-1"
      runAsDifferentOrganizationUser()
      val organizationRegistered =
          organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))

      val otherUserACL = OrganizationAccessControl(id = TEST_USER_ID, role = ROLE_ADMIN)
      organizationApiService.createOrganizationAccessControl(
          organizationRegistered.id, otherUserACL)

      runAsOrganizationUser()
      organizationApiService.deleteOrganizationAccessControl(
          organizationRegistered.id, OTHER_TEST_USER_ID)
      assertThrows<CsmResourceNotFoundException> {
        organizationApiService.getOrganizationAccessControl(
            organizationRegistered.id, OTHER_TEST_USER_ID)
      }
    }

    @Test
    fun `deleteOrganizationAccessControl as not resource admin, no WRITE_SECURITY permission`() {
      val name = "o-connector-test-1"
      runAsDifferentOrganizationUser()
      val organizationRegistered =
          organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))

      val otherUserACL = OrganizationAccessControl(id = TEST_USER_ID, role = ROLE_VIEWER)
      organizationApiService.createOrganizationAccessControl(
          organizationRegistered.id, otherUserACL)

      runAsOrganizationUser()
      assertThrows<CsmAccessForbiddenException> {
        organizationApiService.deleteOrganizationAccessControl(
            organizationRegistered.id, OTHER_TEST_USER_ID)
      }
    }

    @Test
    fun `listOrganizationSecurityUsers as resource admin`() {
      val name = "o-connector-test-1"
      val organizationRegistered =
          organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))

      val orgaUsers =
          organizationApiService.listOrganizationSecurityUsers(organizationRegistered.id)
      assertEquals(listOf(TEST_USER_ID), orgaUsers)
    }

    @Test
    fun `listOrganizationSecurityUsers as not resource admin, READ_SECURITY permission`() {
      val name = "o-connector-test-1"
      runAsDifferentOrganizationUser()
      val organizationRegistered =
          organizationApiService.createOrganization(
              makeOrganizationCreateRequestWithSimpleSecurity(
                  name, TEST_USER_ID, ROLE_NONE, ROLE_USER))
      runAsOrganizationUser()
      val orgaUsers =
          organizationApiService.listOrganizationSecurityUsers(organizationRegistered.id)
      assertEquals(listOf(TEST_USER_ID, OTHER_TEST_USER_ID), orgaUsers)
    }

    @Test
    fun `listOrganizationSecurityUsers as not resource admin, no READ_SECURITY permission`() {
      val name = "o-connector-test-1"
      runAsDifferentOrganizationUser()
      val organizationRegistered =
          organizationApiService.createOrganization(
              makeOrganizationCreateRequestWithSimpleSecurity(
                  name, TEST_USER_ID, ROLE_NONE, ROLE_NONE))
      runAsOrganizationUser()
      assertThrows<CsmAccessForbiddenException> {
        organizationApiService.listOrganizationSecurityUsers(organizationRegistered.id)
      }
    }

    @Test
    fun `testVerifyPermissionsAndReturnOrganization`() {
      val name = "o-connector-test-1"
      val organizationRegistered =
          organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))
      assertDoesNotThrow {
        val organizationVerified =
            organizationApiService.getVerifiedOrganization(organizationRegistered.id)
        assertEquals(organizationRegistered, organizationVerified)
      }
    }

    @Test
    fun `testVerifyPermissionsAndReturnOrganization with organization with restricted permission`() {
      val name = "o-connector-test-1"
      runAsDifferentOrganizationUser()
      val organizationRegistered =
          organizationApiService.createOrganization(
              makeOrganizationCreateRequestWithSimpleSecurity(
                  name, TEST_USER_ID, ROLE_NONE, ROLE_NONE))
      runAsOrganizationUser()
      assertThrows<CsmAccessForbiddenException> {
        organizationApiService.getVerifiedOrganization(organizationRegistered.id)
      }
    }

    @Test
    fun `testVerifyPermissionsAndReturnOrganization with unknown organization id`() {
      assertThrows<CsmResourceNotFoundException> {
        organizationApiService.getVerifiedOrganization("wrong_orga_id")
      }
    }

    @Test
    fun `As a viewer, I can only see my information in security property for getOrganization`() {
      val organization = makeOrganizationCreateRequest(role = ROLE_VIEWER)
      var organizationSaved = organizationApiService.createOrganization(organization)

      organizationSaved = organizationApiService.getOrganization(organizationSaved.id)
      assertEquals(
          OrganizationSecurity(
              default = ROLE_NONE,
              mutableListOf(OrganizationAccessControl(TEST_USER_ID, ROLE_VIEWER))),
          organizationSaved.security)
      assertEquals(1, organizationSaved.security.accessControlList.size)
    }

    @Test
    fun `As a viewer, I can only see my information in security property for listOrganizations`() {
      val organization = makeOrganizationCreateRequest(role = ROLE_VIEWER)
      organizationApiService.createOrganization(organization)

      val organizations = organizationApiService.listOrganizations(null, null)
      organizations.forEach {
        assertEquals(
            OrganizationSecurity(
                default = ROLE_NONE,
                mutableListOf(OrganizationAccessControl(TEST_USER_ID, ROLE_VIEWER))),
            it.security)
        assertEquals(1, it.security.accessControlList.size)
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
      testlistOrganizations(null, null, numberOfOrganizationToCreate)
      testlistOrganizations(0, null, defaultPageSize)
      testlistOrganizations(0, 10, 10)
      testlistOrganizations(1, 200, 0)
      testlistOrganizations(1, 15, 5)
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
      testlistOrganizationsWithWrongValues()
    }

    @Test
    fun `getOrganization as resource admin`() {
      val organizationRegistered =
          organizationApiService.createOrganization(
              makeSimpleOrganizationCreateRequest("o-connector-test-1"))
      assertNotNull(organizationApiService.getOrganization(organizationRegistered.id))
    }

    @Test
    fun `getOrganization as not resource admin`() {
      testgetOrganizationAsNotOwner(false, null, null, null, false) { runAsPlatformAdmin() }
    }

    @Test
    fun `createOrganization with minimal values`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationToRegister = makeSimpleOrganizationCreateRequest(name)
        val organizationRegistered =
            organizationApiService.createOrganization(organizationToRegister)
        assertEquals(
            OrganizationSecurity(
                default = ROLE_NONE,
                mutableListOf(
                    OrganizationAccessControl(id = TEST_ADMIN_USER_ID, role = ROLE_ADMIN))),
            organizationRegistered.security)
        assertEquals(name, organizationRegistered.name)
        assertTrue(organizationRegistered.id.startsWith("o-"))
      }
    }
  }

  @Test
  fun `createOrganization without required organization name`() {
    assertThrows<IllegalArgumentException> {
      organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(""))
    }
  }

  @Test
  fun `createOrganization with security values`() {
    assertDoesNotThrow {
      val name = "o-connector-test-1"
      val organizationToRegister =
          makeOrganizationCreateRequestWithSimpleSecurity(
              name, OTHER_TEST_USER_ID, ROLE_USER, ROLE_NONE)
      val organizationRegistered = organizationApiService.createOrganization(organizationToRegister)
      assertEquals(
          OrganizationSecurity(
              default = ROLE_USER,
              mutableListOf(
                  OrganizationAccessControl(id = OTHER_TEST_USER_ID, role = ROLE_NONE),
                  OrganizationAccessControl(id = defaultName, role = ROLE_ADMIN))),
          organizationRegistered.security)
      assertEquals(name, organizationRegistered.name)
      assertTrue(organizationRegistered.id.startsWith("o-"))
    }
  }

  @Test
  fun `deleteOrganization as resource admin`() {
    assertDoesNotThrow {
      val name = "o-connector-test-1"
      val organizationToRegister = makeSimpleOrganizationCreateRequest(name)
      val organizationRegistered = organizationApiService.createOrganization(organizationToRegister)
      organizationApiService.deleteOrganization(organizationRegistered.id)
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
          makeOrganizationCreateRequestWithSimpleSecurity(name, defaultName, ROLE_NONE, ROLE_NONE)
      val organizationRegistered = organizationApiService.createOrganization(organizationToRegister)
      runAsPlatformAdmin()
      organizationApiService.deleteOrganization(organizationRegistered.id)
    }
  }

  @Test
  fun `updateOrganization as resource admin organization name`() {
    assertDoesNotThrow {
      val organizationRegistered =
          organizationApiService.createOrganization(
              makeSimpleOrganizationCreateRequest("o-connector-test-1"))
      val newName = "my-new-name"
      val organizationUpdated =
          organizationApiService.updateOrganization(
              organizationRegistered.id, OrganizationUpdateRequest(newName))

      assertEquals(newName, organizationApiService.getOrganization(organizationUpdated.id).name)

      // Update organization with empty body
      organizationApiService.updateOrganization(organizationUpdated.id, OrganizationUpdateRequest())

      assertEquals(newName, organizationApiService.getOrganization(organizationUpdated.id).name)

      // Update organization with null value as name
      organizationApiService.updateOrganization(
          organizationUpdated.id, OrganizationUpdateRequest(null))

      assertEquals(newName, organizationApiService.getOrganization(organizationUpdated.id).name)
    }
  }

  @Test
  fun `updateOrganization as not resource admin with WRITE permission`() {
    assertDoesNotThrow {
      val name = "o-connector-test-1"
      runAsDifferentOrganizationUser()
      val organizationRegistered =
          organizationApiService.createOrganization(
              makeOrganizationCreateRequestWithSimpleSecurity(
                  name, TEST_ADMIN_USER_ID, ROLE_NONE, ROLE_EDITOR))

      runAsPlatformAdmin()

      val newName = "my-new-name"
      val organizationUpdated =
          organizationApiService.updateOrganization(
              organizationRegistered.id, OrganizationUpdateRequest(newName))

      assertEquals(newName, organizationApiService.getOrganization(organizationUpdated.id).name)

      // Update organization with empty body
      organizationApiService.updateOrganization(organizationUpdated.id, OrganizationUpdateRequest())

      assertEquals(newName, organizationApiService.getOrganization(organizationUpdated.id).name)

      // Update organization with null value as name
      organizationApiService.updateOrganization(
          organizationUpdated.id, OrganizationUpdateRequest(null))

      assertEquals(newName, organizationApiService.getOrganization(organizationUpdated.id).name)
    }
  }

  @Test
  fun `updateOrganization as not resource admin  with no WRITE permission`() {
    assertDoesNotThrow {
      val name = "o-connector-test-1"
      runAsDifferentOrganizationUser()
      val organizationRegistered =
          organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))
      runAsPlatformAdmin()
      val newName = "my-new-name"
      val organizationUpdated =
          organizationApiService.updateOrganization(
              organizationRegistered.id, OrganizationUpdateRequest(newName))

      assertEquals(newName, organizationApiService.getOrganization(organizationUpdated.id).name)

      // Update organization with empty body
      organizationApiService.updateOrganization(organizationUpdated.id, OrganizationUpdateRequest())

      assertEquals(newName, organizationApiService.getOrganization(organizationUpdated.id).name)

      // Update organization with null value as name
      organizationApiService.updateOrganization(
          organizationUpdated.id, OrganizationUpdateRequest(null))

      assertEquals(newName, organizationApiService.getOrganization(organizationUpdated.id).name)
    }
  }

  @Test
  fun listPermissions() {
    val mapAllPermissions =
        listOf(
            ComponentRolePermissions(
                component = "organization",
                roles =
                    mutableMapOf(
                        ROLE_VIEWER to mutableListOf(PERMISSION_READ),
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
                        ROLE_VIEWER to mutableListOf(PERMISSION_READ),
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
                        ROLE_VIEWER to mutableListOf(PERMISSION_READ),
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
    assertEquals(mapAllPermissions, organizationApiService.listPermissions())
  }

  @Test
  fun `getOrganizationPermissions as resource admin`() {
    assertDoesNotThrow {
      val name = "o-connector-test-1"
      val organizationRegistered =
          organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))
      var organizationUserPermissions =
          organizationApiService.getOrganizationPermissions(organizationRegistered.id, ROLE_VIEWER)
      assertEquals(mutableListOf(PERMISSION_READ), organizationUserPermissions)
      organizationUserPermissions =
          organizationApiService.getOrganizationPermissions(organizationRegistered.id, ROLE_USER)
      assertEquals(
          mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_CREATE_CHILDREN),
          organizationUserPermissions)
      organizationUserPermissions =
          organizationApiService.getOrganizationPermissions(organizationRegistered.id, ROLE_EDITOR)
      assertEquals(
          mutableListOf(
              PERMISSION_READ,
              PERMISSION_READ_SECURITY,
              PERMISSION_CREATE_CHILDREN,
              PERMISSION_WRITE),
          organizationUserPermissions)
      organizationUserPermissions =
          organizationApiService.getOrganizationPermissions(organizationRegistered.id, ROLE_ADMIN)
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
              organizationRegistered.id, UNKNOWN_IDENTIFIER)
      assertEquals(emptyList(), organizationUserPermissions)
    }
  }

  @Test
  fun `getOrganizationPermissions as not resource admin and READ_SECURITY permission`() {
    assertDoesNotThrow {
      val name = "o-connector-test-1"
      runAsDifferentOrganizationUser()
      val organizationRegistered =
          organizationApiService.createOrganization(
              makeOrganizationCreateRequestWithSimpleSecurity(
                  name, TEST_USER_ID, ROLE_NONE, ROLE_VIEWER))
      runAsPlatformAdmin()
      var organizationUserPermissions =
          organizationApiService.getOrganizationPermissions(organizationRegistered.id, ROLE_VIEWER)
      assertEquals(mutableListOf(PERMISSION_READ), organizationUserPermissions)
      organizationUserPermissions =
          organizationApiService.getOrganizationPermissions(organizationRegistered.id, ROLE_USER)
      assertEquals(
          mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_CREATE_CHILDREN),
          organizationUserPermissions)
      organizationUserPermissions =
          organizationApiService.getOrganizationPermissions(organizationRegistered.id, ROLE_EDITOR)
      assertEquals(
          mutableListOf(
              PERMISSION_READ,
              PERMISSION_READ_SECURITY,
              PERMISSION_CREATE_CHILDREN,
              PERMISSION_WRITE),
          organizationUserPermissions)
      organizationUserPermissions =
          organizationApiService.getOrganizationPermissions(organizationRegistered.id, ROLE_ADMIN)
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
              organizationRegistered.id, UNKNOWN_IDENTIFIER)
      assertEquals(emptyList(), organizationUserPermissions)
    }
  }

  @Test
  fun `getOrganizationPermissions as not resource admin and no permission`() {
    assertDoesNotThrow {
      val name = "o-connector-test-1"
      runAsDifferentOrganizationUser()
      val organizationRegistered =
          organizationApiService.createOrganization(
              makeOrganizationCreateRequestWithSimpleSecurity(
                  name, TEST_USER_ID, ROLE_NONE, ROLE_VIEWER))
      runAsPlatformAdmin()
      var organizationUserPermissions =
          organizationApiService.getOrganizationPermissions(organizationRegistered.id, ROLE_VIEWER)
      assertEquals(mutableListOf(PERMISSION_READ), organizationUserPermissions)
      organizationUserPermissions =
          organizationApiService.getOrganizationPermissions(organizationRegistered.id, ROLE_USER)
      assertEquals(
          mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_CREATE_CHILDREN),
          organizationUserPermissions)
      organizationUserPermissions =
          organizationApiService.getOrganizationPermissions(organizationRegistered.id, ROLE_EDITOR)
      assertEquals(
          mutableListOf(
              PERMISSION_READ,
              PERMISSION_READ_SECURITY,
              PERMISSION_CREATE_CHILDREN,
              PERMISSION_WRITE),
          organizationUserPermissions)
      organizationUserPermissions =
          organizationApiService.getOrganizationPermissions(organizationRegistered.id, ROLE_ADMIN)
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
              organizationRegistered.id, UNKNOWN_IDENTIFIER)
      assertEquals(emptyList(), organizationUserPermissions)
    }
  }

  @Test
  fun `getOrganizationSecurity as resource admin`() {
    assertDoesNotThrow {
      val name = "o-connector-test-1"
      val organizationRegistered =
          organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))
      assertNotNull(organizationApiService.getOrganizationSecurity(organizationRegistered.id))
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
      organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name = "org1"))
      organizationApiService.getOrganizationSecurity("org1")
    }
  }

  @Test
  fun `getOrganizationSecurity as not resource admin with READ_SECURITY permission`() {
    assertDoesNotThrow {
      val name = "o-connector-test-1"
      runAsDifferentOrganizationUser()
      val organizationRegistered =
          organizationApiService.createOrganization(
              makeOrganizationCreateRequestWithSimpleSecurity(
                  name, TEST_USER_ID, ROLE_NONE, ROLE_VIEWER))
      runAsPlatformAdmin()
      assertNotNull(organizationApiService.getOrganizationSecurity(organizationRegistered.id))
    }
  }

  @Test
  fun `getOrganizationSecurity as not resource admin with no READ_SECURITY permission`() {
    assertDoesNotThrow {
      val name = "o-connector-test-1"
      runAsDifferentOrganizationUser()
      val organizationRegistered =
          organizationApiService.createOrganization(
              makeOrganizationCreateRequestWithSimpleSecurity(
                  name, defaultName, ROLE_NONE, ROLE_NONE))
      runAsPlatformAdmin()
      assertNotNull(organizationApiService.getOrganizationSecurity(organizationRegistered.id))
    }
  }

  @Test
  fun `updateOrganizationDefaultSecurity as resource admin and existing role`() {
    assertDoesNotThrow {
      val name = "o-connector-test-1"
      val organizationRegistered =
          organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))
      val defaultRoleCreated = organizationRegistered.security.default
      assertNotNull(
          organizationApiService.updateOrganizationDefaultSecurity(
              organizationRegistered.id, OrganizationRole(ROLE_ADMIN)))
      val defaultRoleUpdated =
          organizationApiService.getOrganizationSecurity(organizationRegistered.id).default
      assertNotEquals(defaultRoleCreated, defaultRoleUpdated)
    }
  }

  @Test
  fun `updateOrganizationDefaultSecurity as resource admin and non-existing role`() {
    assertThrows<CsmClientException> {
      val name = "o-connector-test-1"
      val organizationRegistered =
          organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))
      organizationApiService.updateOrganizationDefaultSecurity(
          organizationRegistered.id, OrganizationRole(UNKNOWN_IDENTIFIER))
    }
  }

  @Test
  fun `updateOrganizationDefaultSecurity as not resource admin`() {
    assertDoesNotThrow {
      val name = "o-connector-test-1"
      runAsDifferentOrganizationUser()
      val organizationRegistered =
          organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))
      val defaultRoleCreated = organizationRegistered.security.default
      runAsPlatformAdmin()
      assertNotNull(
          organizationApiService.updateOrganizationDefaultSecurity(
              organizationRegistered.id, OrganizationRole(ROLE_ADMIN)))
      val defaultRoleUpdated =
          organizationApiService.getOrganizationSecurity(organizationRegistered.id).default
      assertNotEquals(defaultRoleCreated, defaultRoleUpdated)
    }
  }

  @Test
  fun `updateOrganizationDefaultSecurity as not resource admin and no WRITE_SECURITY_PERMISSION`() {
    assertDoesNotThrow {
      val name = "o-connector-test-1"
      runAsDifferentOrganizationUser()
      val organizationRegistered =
          organizationApiService.createOrganization(
              makeOrganizationCreateRequestWithSimpleSecurity(
                  name, TEST_ADMIN_USER_ID, ROLE_NONE, ROLE_NONE))
      val defaultRoleCreated = organizationRegistered.security.default
      runAsPlatformAdmin()
      assertNotNull(
          organizationApiService.updateOrganizationDefaultSecurity(
              organizationRegistered.id, OrganizationRole(ROLE_ADMIN)))
      val defaultRoleUpdated =
          organizationApiService.getOrganizationSecurity(organizationRegistered.id).default
      assertNotEquals(defaultRoleCreated, defaultRoleUpdated)
    }
  }

  @Test
  fun `getOrganizationAccessControl as not resource admin and current user`() {
    assertDoesNotThrow {
      val name = "o-connector-test-1"
      val organizationRegistered =
          organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))
      val organizationRole =
          organizationApiService.getOrganizationAccessControl(
              organizationRegistered.id, defaultName)
      assertNotNull(organizationRole)
      assertEquals(ROLE_ADMIN, organizationRole.role)
      assertEquals(defaultName, organizationRole.id)
    }
  }

  @Test
  fun `getOrganizationAccessControl as resource admin and non-existing user`() {
    assertThrows<CsmResourceNotFoundException> {
      val name = "o-connector-test-1"
      val organizationRegistered =
          organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))
      organizationApiService.getOrganizationAccessControl(organizationRegistered.id, "UNKOWN")
    }
  }

  @Test
  fun `getOrganizationAccessControl as not resource admin, READ_SECURITY permission and existing user`() {
    assertDoesNotThrow {
      val name = "o-connector-test-1"
      runAsDifferentOrganizationUser()
      val organizationRegistered =
          organizationApiService.createOrganization(
              makeOrganizationCreateRequestWithSimpleSecurity(
                  name, TEST_USER_ID, ROLE_NONE, ROLE_VIEWER))
      runAsPlatformAdmin()
      val organizationRole =
          organizationApiService.getOrganizationAccessControl(
              organizationRegistered.id, TEST_USER_ID)
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
      val organizationRegistered =
          organizationApiService.createOrganization(
              makeOrganizationCreateRequestWithSimpleSecurity(
                  name, TEST_USER_ID, ROLE_NONE, ROLE_VIEWER))
      runAsPlatformAdmin()
      organizationApiService.getOrganizationAccessControl(
          organizationRegistered.id, UNKNOWN_IDENTIFIER)
    }
  }

  @Test
  fun `getOrganizationAccessControl as not resource admin, no READ_SECURITY permission, non-existing user`() {
    assertThrows<CsmResourceNotFoundException> {
      val name = "o-connector-test-1"
      runAsDifferentOrganizationUser()
      val organizationRegistered =
          organizationApiService.createOrganization(
              makeOrganizationCreateRequestWithSimpleSecurity(
                  name, TEST_ADMIN_USER_ID, ROLE_NONE, ROLE_NONE))
      runAsPlatformAdmin()
      organizationApiService.getOrganizationAccessControl(
          organizationRegistered.id, UNKNOWN_IDENTIFIER)
    }
  }

  @Test
  fun `getOrganizationAccessControl as not resource admin, no READ_SECURITY permission, existing user`() {
    assertDoesNotThrow {
      val name = "o-connector-test-1"
      runAsDifferentOrganizationUser()
      val organizationRegistered =
          organizationApiService.createOrganization(
              makeOrganizationCreateRequestWithSimpleSecurity(
                  name, TEST_USER_ID, ROLE_NONE, ROLE_NONE))
      runAsPlatformAdmin()
      val organizationAccessControl =
          organizationApiService.getOrganizationAccessControl(
              organizationRegistered.id, TEST_USER_ID)
      assertNotNull(organizationAccessControl)
      assertEquals(TEST_USER_ID, organizationAccessControl.id)
      assertEquals(ROLE_NONE, organizationAccessControl.role)
    }
  }

  @Test
  fun `createOrganizationAccessControl as resource admin`() {
    assertDoesNotThrow {
      val name = "o-connector-test-1"
      val organizationRegistered =
          organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))
      assertThrows<CsmResourceNotFoundException> {
        organizationApiService.getOrganizationAccessControl(
            organizationRegistered.id, OTHER_TEST_USER_ID)
      }
      val otherUserACL = OrganizationAccessControl(id = OTHER_TEST_USER_ID, role = ROLE_VIEWER)
      assertNotNull(
          organizationApiService.createOrganizationAccessControl(
              organizationRegistered.id, otherUserACL))

      val otherUserACLRetrieved =
          organizationApiService.getOrganizationAccessControl(
              organizationRegistered.id, OTHER_TEST_USER_ID)
      assertEquals(
          OrganizationAccessControl(OTHER_TEST_USER_ID, ROLE_VIEWER), otherUserACLRetrieved)
    }
  }

  @Test
  fun `createOrganizationAccessControl as resource admin (ROLE_NONE)`() {
    assertThrows<CsmClientException> {
      val name = "o-connector-test-1"
      val organizationRegistered =
          organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))

      val otherUserACL = OrganizationAccessControl(id = OTHER_TEST_USER_ID, role = ROLE_NONE)
      organizationApiService.createOrganizationAccessControl(
          organizationRegistered.id, otherUserACL)
    }
  }

  @Test
  fun `createOrganizationAccessControl as not resource admin and PERMISSION_WRITE_SECURITY`() {
    assertDoesNotThrow {
      val name = "o-connector-test-1"
      runAsDifferentOrganizationUser()
      val organizationRegistered =
          organizationApiService.createOrganization(
              makeOrganizationCreateRequestWithSimpleSecurity(
                  name, TEST_ADMIN_USER_ID, ROLE_NONE, ROLE_ADMIN))
      runAsPlatformAdmin()
      assertThrows<CsmResourceNotFoundException> {
        organizationApiService.getOrganizationAccessControl(organizationRegistered.id, TEST_USER_ID)
      }
      val otherUserACL = OrganizationAccessControl(id = TEST_USER_ID, role = ROLE_VIEWER)
      assertNotNull(
          organizationApiService.createOrganizationAccessControl(
              organizationRegistered.id, otherUserACL))

      val otherUserACLRetrieved =
          organizationApiService.getOrganizationAccessControl(
              organizationRegistered.id, TEST_USER_ID)
      assertEquals(otherUserACL, otherUserACLRetrieved)
    }
  }

  @Test
  fun `createOrganizationAccessControl as not resource admin and no PERMISSION_WRITE_SECURITY`() {
    assertDoesNotThrow {
      val name = "o-connector-test-1"
      runAsDifferentOrganizationUser()
      val organizationRegistered =
          organizationApiService.createOrganization(
              makeOrganizationCreateRequestWithSimpleSecurity(
                  name, defaultName, ROLE_NONE, ROLE_NONE))
      runAsPlatformAdmin()
      assertThrows<CsmResourceNotFoundException> {
        organizationApiService.getOrganizationAccessControl(organizationRegistered.id, TEST_USER_ID)
      }
      val otherUserACL = OrganizationAccessControl(id = TEST_USER_ID, role = ROLE_VIEWER)
      assertNotNull(
          organizationApiService.createOrganizationAccessControl(
              organizationRegistered.id, otherUserACL))

      val otherUserACLRetrieved =
          organizationApiService.getOrganizationAccessControl(
              organizationRegistered.id, TEST_USER_ID)
      assertEquals(otherUserACL, otherUserACLRetrieved)
    }
  }

  @Test
  fun `updateOrganizationAccessControl as resource admin cannot update last admin`() {
    assertThrows<CsmAccessForbiddenException> {
      val name = "o-connector-test-1"
      val organizationRegistered =
          organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))
      organizationApiService.updateOrganizationAccessControl(
          organizationRegistered.id, defaultName, OrganizationRole(role = ROLE_VIEWER))
    }
  }

  @Test
  fun `updateOrganizationAccessControl as resource admin can update user (!= ROLE_NONE)`() {
    assertDoesNotThrow {
      val name = "o-connector-test-1"
      val organizationRegistered =
          organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))

      organizationApiService.createOrganizationAccessControl(
          organizationRegistered.id,
          OrganizationAccessControl(id = OTHER_TEST_USER_ID, role = ROLE_VIEWER))

      assertNotNull(
          organizationApiService.updateOrganizationAccessControl(
              organizationRegistered.id, OTHER_TEST_USER_ID, OrganizationRole(role = ROLE_EDITOR)))

      val userACLRetrieved =
          organizationApiService.getOrganizationAccessControl(
              organizationRegistered.id, OTHER_TEST_USER_ID)
      assertNotEquals(ROLE_VIEWER, userACLRetrieved.role)
      assertEquals(ROLE_EDITOR, userACLRetrieved.role)
    }
  }

  @Test
  fun `updateOrganizationAccessControl as resource admin cannot update user (= ROLE_NONE)`() {
    assertThrows<CsmClientException> {
      val name = "o-connector-test-1"
      val organizationRegistered =
          organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))

      organizationApiService.updateOrganizationAccessControl(
          organizationRegistered.id, TEST_USER_ID, OrganizationRole(role = ROLE_NONE))
    }
  }

  @Test
  fun `updateOrganizationAccessControl as resource admin and unknown ACL user`() {
    assertThrows<CsmResourceNotFoundException> {
      val name = "o-connector-test-1"
      val organizationRegistered =
          organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))

      organizationApiService.updateOrganizationAccessControl(
          organizationRegistered.id, UNKNOWN_IDENTIFIER, OrganizationRole(role = ROLE_EDITOR))
    }
  }

  @Test
  fun `updateOrganizationAccessControl as resource admin and wrong role`() {
    assertThrows<CsmClientException> {
      val name = "o-connector-test-1"
      val organizationRegistered =
          organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))

      organizationApiService.updateOrganizationAccessControl(
          organizationRegistered.id, TEST_USER_ID, OrganizationRole(role = UNKNOWN_IDENTIFIER))
    }
  }

  @Test
  fun `updateOrganizationAccessControl as not resource admin, WRITE_SECURITY permission, can update user (!= ROLE_NONE)`() {
    assertDoesNotThrow {
      val name = "o-connector-test-1"
      runAsDifferentOrganizationUser()
      val organizationRegistered =
          organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))

      organizationApiService.createOrganizationAccessControl(
          organizationRegistered.id,
          OrganizationAccessControl(id = TEST_ADMIN_USER_ID, role = ROLE_ADMIN))

      runAsPlatformAdmin()

      assertNotNull(
          organizationApiService.updateOrganizationAccessControl(
              organizationRegistered.id, OTHER_TEST_USER_ID, OrganizationRole(role = ROLE_EDITOR)))

      val userACLRetrieved =
          organizationApiService.getOrganizationAccessControl(
              organizationRegistered.id, OTHER_TEST_USER_ID)
      assertEquals(ROLE_EDITOR, userACLRetrieved.role)
    }
  }

  @Test
  fun `updateOrganizationAccessControl as not resource admin, no WRITE_SECURITY permission`() {
    assertDoesNotThrow {
      val name = "o-connector-test-1"
      runAsDifferentOrganizationUser()
      val organizationRegistered =
          organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))

      organizationApiService.createOrganizationAccessControl(
          organizationRegistered.id,
          OrganizationAccessControl(id = TEST_ADMIN_USER_ID, role = ROLE_VIEWER))

      runAsPlatformAdmin()

      assertNotNull(
          organizationApiService.updateOrganizationAccessControl(
              organizationRegistered.id, TEST_ADMIN_USER_ID, OrganizationRole(role = ROLE_EDITOR)))

      val userACLRetrieved =
          organizationApiService.getOrganizationAccessControl(
              organizationRegistered.id, TEST_ADMIN_USER_ID)
      assertEquals(ROLE_EDITOR, userACLRetrieved.role)
    }
  }

  @Test
  fun `updateOrganizationAccessControl as not resource admin, no WRITE_SECURITY permission, ROLE_NONE`() {
    assertThrows<CsmClientException> {
      val name = "o-connector-test-1"
      runAsDifferentOrganizationUser()
      val organizationRegistered =
          organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))

      organizationApiService.createOrganizationAccessControl(
          organizationRegistered.id,
          OrganizationAccessControl(id = TEST_USER_ID, role = ROLE_VIEWER))

      runAsPlatformAdmin()

      organizationApiService.updateOrganizationAccessControl(
          organizationRegistered.id, OTHER_TEST_USER_ID, OrganizationRole(role = ROLE_NONE))
    }
  }

  @Test
  fun `deleteOrganizationAccessControl as resource admin`() {
    val name = "o-connector-test-1"
    val organizationRegistered =
        organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))

    val otherUserACL = OrganizationAccessControl(id = OTHER_TEST_USER_ID, role = ROLE_VIEWER)
    organizationApiService.createOrganizationAccessControl(organizationRegistered.id, otherUserACL)

    organizationApiService.deleteOrganizationAccessControl(
        organizationRegistered.id, OTHER_TEST_USER_ID)
    assertThrows<CsmResourceNotFoundException> {
      organizationApiService.getOrganizationAccessControl(
          organizationRegistered.id, OTHER_TEST_USER_ID)
    }
  }

  @Test
  fun `deleteOrganizationAccessControl as not resource admin, WRITE_SECURITY permission`() {
    val name = "o-connector-test-1"
    runAsDifferentOrganizationUser()
    val organizationRegistered =
        organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))

    val otherUserACL = OrganizationAccessControl(id = TEST_USER_ID, role = ROLE_ADMIN)
    organizationApiService.createOrganizationAccessControl(organizationRegistered.id, otherUserACL)

    runAsPlatformAdmin()
    organizationApiService.deleteOrganizationAccessControl(
        organizationRegistered.id, OTHER_TEST_USER_ID)
    assertThrows<CsmResourceNotFoundException> {
      organizationApiService.getOrganizationAccessControl(
          organizationRegistered.id, OTHER_TEST_USER_ID)
    }
  }

  @Test
  fun `deleteOrganizationAccessControl as not resource admin, no WRITE_SECURITY permission`() {
    val name = "o-connector-test-1"
    runAsDifferentOrganizationUser()
    val organizationRegistered =
        organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))

    val otherUserACL = OrganizationAccessControl(id = TEST_USER_ID, role = ROLE_ADMIN)
    organizationApiService.createOrganizationAccessControl(organizationRegistered.id, otherUserACL)

    runAsPlatformAdmin()
    organizationApiService.deleteOrganizationAccessControl(
        organizationRegistered.id, OTHER_TEST_USER_ID)
    assertThrows<CsmResourceNotFoundException> {
      organizationApiService.getOrganizationAccessControl(
          organizationRegistered.id, OTHER_TEST_USER_ID)
    }
  }

  @Test
  fun `listOrganizationSecurityUsers as resource admin`() {
    val name = "o-connector-test-1"
    val organizationRegistered =
        organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))

    val orgaUsers = organizationApiService.listOrganizationSecurityUsers(organizationRegistered.id)
    assertEquals(listOf(defaultName), orgaUsers)
  }

  @Test
  fun `listOrganizationSecurityUsers as not resource admin, READ_SECURITY permission`() {
    val name = "o-connector-test-1"
    runAsDifferentOrganizationUser()
    val organizationRegistered =
        organizationApiService.createOrganization(
            makeOrganizationCreateRequestWithSimpleSecurity(
                name, TEST_USER_ID, ROLE_NONE, ROLE_VIEWER))
    runAsPlatformAdmin()
    val orgaUsers = organizationApiService.listOrganizationSecurityUsers(organizationRegistered.id)
    assertEquals(listOf(TEST_USER_ID, OTHER_TEST_USER_ID), orgaUsers)
  }

  @Test
  fun `listOrganizationSecurityUsers as not resource admin, no READ_SECURITY permission`() {
    val name = "o-connector-test-1"
    runAsDifferentOrganizationUser()
    val organizationRegistered =
        organizationApiService.createOrganization(
            makeOrganizationCreateRequestWithSimpleSecurity(
                name, TEST_USER_ID, ROLE_NONE, ROLE_NONE))
    runAsPlatformAdmin()
    val orgaUsers = organizationApiService.listOrganizationSecurityUsers(organizationRegistered.id)
    assertEquals(listOf(TEST_USER_ID, OTHER_TEST_USER_ID), orgaUsers)
  }

  @Test
  fun `access control list shouldn't contain more than one time each user on creation`() {
    val brokenOrganization =
        OrganizationCreateRequest(
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
        OrganizationCreateRequest(
            name = "organization",
            security =
                OrganizationSecurity(
                    default = ROLE_NONE,
                    accessControlList =
                        mutableListOf(OrganizationAccessControl(defaultName, ROLE_ADMIN))))
    val organizationSaved = organizationApiService.createOrganization(workingOrganization)

    assertThrows<IllegalArgumentException> {
      organizationApiService.createOrganizationAccessControl(
          organizationSaved.id, OrganizationAccessControl(defaultName, ROLE_EDITOR))
    }
  }

  @Test
  fun `testVerifyPermissionsAndReturnOrganization`() {
    val name = "o-connector-test-1"
    val organizationRegistered =
        organizationApiService.createOrganization(makeSimpleOrganizationCreateRequest(name))
    assertDoesNotThrow {
      val organizationVerified =
          organizationApiService.getVerifiedOrganization(organizationRegistered.id)
      assertEquals(organizationRegistered, organizationVerified)
    }
  }

  @Test
  fun `testVerifyPermissionsAndReturnOrganization with organization with restricted permission`() {
    val name = "o-connector-test-1"
    runAsDifferentOrganizationUser()
    val organizationRegistered =
        organizationApiService.createOrganization(
            makeOrganizationCreateRequestWithSimpleSecurity(
                name, TEST_USER_ID, ROLE_NONE, ROLE_NONE))
    runAsPlatformAdmin()
    assertDoesNotThrow {
      val organizationVerified =
          organizationApiService.getVerifiedOrganization(organizationRegistered.id)
      assertEquals(organizationRegistered, organizationVerified)
    }
  }

  @Test
  fun `testVerifyPermissionsAndReturnOrganization with unknown organization id`() {
    assertThrows<CsmResourceNotFoundException> {
      organizationApiService.getVerifiedOrganization("wrong_orga_id")
    }
  }

  @Test
  fun `assert timestamps are functional for base CRUD`() {
    val organizationSaved =
        organizationApiService.createOrganization(
            makeSimpleOrganizationCreateRequest("organization"))
    assertTrue(organizationSaved.createInfo.timestamp > startTime)
    assertEquals(organizationSaved.createInfo, organizationSaved.updateInfo)

    val updateTime = Instant.now().toEpochMilli()
    val organizationUpdated =
        organizationApiService.updateOrganization(
            organizationSaved.id, OrganizationUpdateRequest("organizationUpdated"))

    assertTrue { updateTime < organizationUpdated.updateInfo.timestamp }
    assertEquals(organizationSaved.createInfo, organizationUpdated.createInfo)
    assertTrue { organizationSaved.createInfo.timestamp < organizationUpdated.updateInfo.timestamp }
    assertTrue { organizationSaved.updateInfo.timestamp < organizationUpdated.updateInfo.timestamp }

    val organizationFetched = organizationApiService.getOrganization(organizationSaved.id)

    assertEquals(organizationUpdated.createInfo, organizationFetched.createInfo)
    assertEquals(organizationUpdated.updateInfo, organizationFetched.updateInfo)
  }

  @Test
  fun `assert timestamps are functional for RBAC CRUD`() {
    val organizationSaved =
        organizationApiService.createOrganization(
            makeSimpleOrganizationCreateRequest("organization"))
    organizationApiService.createOrganizationAccessControl(
        organizationSaved.id, OrganizationAccessControl("newUser", ROLE_USER))

    val rbacAdded = organizationApiService.getOrganization(organizationSaved.id)

    assertEquals(organizationSaved.createInfo, rbacAdded.createInfo)
    assertTrue { organizationSaved.updateInfo.timestamp < rbacAdded.updateInfo.timestamp }

    organizationApiService.updateOrganizationAccessControl(
        organizationSaved.id, "newUser", OrganizationRole(ROLE_VIEWER))
    val rbacUpdated = organizationApiService.getOrganization(organizationSaved.id)

    assertEquals(rbacAdded.createInfo, rbacUpdated.createInfo)
    assertTrue { rbacAdded.updateInfo.timestamp < rbacUpdated.updateInfo.timestamp }

    organizationApiService.getOrganizationAccessControl(organizationSaved.id, "newUser")
    val rbacFetched = organizationApiService.getOrganization(organizationSaved.id)

    assertEquals(rbacUpdated.createInfo, rbacFetched.createInfo)
    assertEquals(rbacUpdated.updateInfo, rbacFetched.updateInfo)

    organizationApiService.deleteOrganizationAccessControl(organizationSaved.id, "newUser")
    val rbacDeleted = organizationApiService.getOrganization(organizationSaved.id)

    assertEquals(rbacFetched.createInfo, rbacDeleted.createInfo)
    assertTrue { rbacFetched.updateInfo.timestamp < rbacDeleted.updateInfo.timestamp }
  }

  private fun testFindAllWithRBAC(
      numberOfOrganizationCreated: Int,
      numberOfOrganizationReachableByTestUser: Int
  ) {
    val defaultPageSize = csmPlatformProperties.twincache.organization.defaultPageSize

    testlistOrganizations(null, null, numberOfOrganizationReachableByTestUser)
    testlistOrganizations(0, null, defaultPageSize)
    testlistOrganizations(0, numberOfOrganizationCreated, numberOfOrganizationReachableByTestUser)
    testlistOrganizations(1, 200, 0)
    testlistOrganizations(1, 15, 15)
  }

  private fun testlistOrganizationsWithWrongValues() {
    logger.info("Should throw IllegalArgumentException when page and size are zeros")
    assertThrows<IllegalArgumentException> { organizationApiService.listOrganizations(0, 0) }

    logger.info("Should throw IllegalArgumentException when page is negative")
    assertThrows<IllegalArgumentException> { organizationApiService.listOrganizations(-1, 10) }

    logger.info("Should throw IllegalArgumentException when size is negative")
    assertThrows<IllegalArgumentException> { organizationApiService.listOrganizations(0, -1) }
  }

  private fun testgetOrganizationAsNotOwner(
      hasUserSecurity: Boolean,
      userId: String?,
      defaultRole: String?,
      userRole: String?,
      throwException: Boolean,
      rungetOrganizationAs: () -> Unit
  ) {
    runAsDifferentOrganizationUser()
    val organizationId = "o-connector-test-1"
    val organization =
        if (hasUserSecurity) {
          makeOrganizationCreateRequestWithSimpleSecurity(
              organizationId, userId!!, defaultRole!!, userRole!!)
        } else {
          makeSimpleOrganizationCreateRequest(organizationId)
        }
    val organizationRegistered = organizationApiService.createOrganization(organization)

    rungetOrganizationAs()
    if (throwException) {
      assertThrows<CsmAccessForbiddenException> {
        (organizationApiService.getOrganization(organizationRegistered.id))
      }
    } else {
      assertNotNull(organizationRegistered)
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
      val newOrganization = makeSimpleOrganizationCreateRequest("o-connector-test-$it")
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
            makeOrganizationCreateRequestWithSimpleSecurity(
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
  internal fun makeSimpleOrganizationCreateRequest(name: String): OrganizationCreateRequest {
    return OrganizationCreateRequest(name = name)
  }

  /** Create default test Connector */
  internal fun makeOrganizationCreateRequestWithSimpleSecurity(
      name: String,
      userName: String,
      defaultSecurity: String,
      role: String,
  ): OrganizationCreateRequest {
    return OrganizationCreateRequest(
        name = name,
        security =
            OrganizationSecurity(
                default = defaultSecurity,
                accessControlList =
                    mutableListOf(OrganizationAccessControl(id = userName, role = role))))
  }

  fun makeOrganizationCreateRequest(
      id: String = "organization_id",
      userName: String = TEST_USER_ID,
      role: String = ROLE_ADMIN
  ): OrganizationCreateRequest {
    return OrganizationCreateRequest(
        name = "Organization Name",
        security =
            OrganizationSecurity(
                default = ROLE_NONE,
                accessControlList =
                    mutableListOf(
                        OrganizationAccessControl(id = TEST_ADMIN_USER_ID, role = "admin"),
                        OrganizationAccessControl(id = userName, role = role))))
  }

  internal fun testlistOrganizations(page: Int?, size: Int?, expectedResultSize: Int) {
    val organizationList = organizationApiService.listOrganizations(page, size)
    logger.info("Organization list retrieved contains : ${organizationList.size} elements")
    assertEquals(expectedResultSize, organizationList.size)
  }
}
