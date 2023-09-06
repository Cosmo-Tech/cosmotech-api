// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.organization.service

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.exceptions.CsmClientException
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.rbac.*
import com.cosmotech.api.security.ROLE_ORGANIZATION_USER
import com.cosmotech.api.security.ROLE_PLATFORM_ADMIN
import com.cosmotech.api.tests.CsmRedisTestBase
import com.cosmotech.api.utils.getCurrentAccountIdentifier
import com.cosmotech.api.utils.getCurrentAuthenticatedRoles
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.api.utils.getCurrentAuthentication
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.domain.*
import com.redis.om.spring.RediSearchIndexer
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.mockkStatic
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.jupiter.api.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
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

  @Autowired lateinit var organizationApiService: OrganizationApiService

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
    fun `findAllOrganizations with correct values`() {
      val numberOfOrganizationToCreate = 20
      val defaultPageSize = csmPlatformProperties.twincache.organization.defaultPageSize

      batchOrganizationCreation(numberOfOrganizationToCreate)
      testFindAllOrganizations(null, null, numberOfOrganizationToCreate)
      testFindAllOrganizations(0, null, defaultPageSize)
      testFindAllOrganizations(0, 10, 10)
      testFindAllOrganizations(1, 200, 0)
      testFindAllOrganizations(1, 15, 5)
    }

    @Test
    fun `findAllOrganizations with correct values and RBAC for current user`() {

      val numberOfOrganizationCreated = batchOrganizationCreationWithRBAC(TEST_USER_ID)
      // This number represents the amount of Organization that test.user can read regarding RBAC
      // This is typically all simple combinations except "securityRole to none"
      // We have 36 combinations per user in batchOrganizationCreationWithRBAC
      // So 36 combinations minus "securityRole to 'none'" equals 35
      testFindAllWithRBAC(numberOfOrganizationCreated, 35)
    }

    @Test
    fun `findAllOrganizations with correct values and no RBAC for current user`() {

      val numberOfOrganizationCreated = batchOrganizationCreationWithRBAC(OTHER_TEST_USER_ID)
      // This number represents the amount of Organization that test.user can read regarding RBAC
      // We have 36 combinations per user in batchOrganizationCreationWithRBAC
      // securityRole does not refer to test.user
      // The only configuration that fit for test.user is defaultRole with
      // ROLE_VIEWER (x6), ROLE_EDITOR (x6), ROLE_VALIDATOR (x6), ROLE_USER (x6), ROLE_ADMIN (x6) =
      // 30
      testFindAllWithRBAC(numberOfOrganizationCreated, 30)
    }

    @Test
    fun `findAllOrganizations with wrong values`() {
      testFindAllOrganizationsWithWrongValues()
    }

    @Test
    fun `findOrganizationById as owner`() {
      val organizationRegistered =
          organizationApiService.registerOrganization(createTestOrganization("o-connector-test-1"))
      assertNotNull(organizationApiService.findOrganizationById(organizationRegistered.id!!))
    }

    @Test
    fun `findOrganizationById as not owner`() {
      testFindOrganizationByIdAsNotOwner(false, null, null, null, true) { runAsOrganizationUser() }
    }

    @Test
    fun `findOrganizationById as not owner but with READ role`() {
      testFindOrganizationByIdAsNotOwner(true, TEST_USER_ID, ROLE_NONE, ROLE_USER, false) {
        runAsOrganizationUser()
      }
    }

    @Test
    fun `findOrganizationById as not owner but with WRITE role`() {
      testFindOrganizationByIdAsNotOwner(true, TEST_USER_ID, ROLE_NONE, ROLE_EDITOR, false) {
        runAsOrganizationUser()
      }
    }

    @Test
    fun `findOrganizationById as not owner but with NONE role`() {
      testFindOrganizationByIdAsNotOwner(true, TEST_USER_ID, ROLE_NONE, ROLE_NONE, true) {
        runAsOrganizationUser()
      }
    }

    @Test
    fun `registerOrganization with minimal values`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationToRegister = createTestOrganization(name)
        val organizationRegistered =
            organizationApiService.registerOrganization(organizationToRegister)
        assertEquals(
            OrganizationSecurity(
                default = ROLE_NONE,
                mutableListOf(OrganizationAccessControl(id = TEST_USER_ID, role = ROLE_ADMIN))),
            organizationRegistered.security)
        assertEquals(name, organizationRegistered.name)
        assertTrue(organizationRegistered.id!!.startsWith("o-"))
      }
    }

    @Test
    fun `registerOrganization without required organization name`() {
      assertThrows<IllegalArgumentException> {
        organizationApiService.registerOrganization(createTestOrganization(""))
      }
    }

    @Test
    fun `registerOrganization with null required organization name`() {
      assertThrows<IllegalArgumentException> {
        organizationApiService.registerOrganization(Organization(name = null))
      }
    }

    @Test
    fun `registerOrganization with security values`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationToRegister =
            createTestOrganizationWithSimpleSecurity(name, OTHER_TEST_USER_ID, ROLE_USER, ROLE_NONE)
        val organizationRegistered =
            organizationApiService.registerOrganization(organizationToRegister)
        assertEquals(
            OrganizationSecurity(
                default = ROLE_USER,
                mutableListOf(
                    OrganizationAccessControl(id = OTHER_TEST_USER_ID, role = ROLE_NONE))),
            organizationRegistered.security)
        assertEquals(name, organizationRegistered.name)
        assertTrue(organizationRegistered.id!!.startsWith("o-"))
      }
    }

    @Test
    fun `unregisterOrganization owned organization `() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationToRegister = createTestOrganization(name)
        val organizationRegistered =
            organizationApiService.registerOrganization(organizationToRegister)
        organizationApiService.unregisterOrganization(organizationRegistered.id!!)
      }
    }

    @Test
    fun `unregisterOrganization unexisting organization `() {
      assertThrows<CsmResourceNotFoundException> {
        organizationApiService.unregisterOrganization("o-connector-test-1")
      }
    }

    @Test
    fun `unregisterOrganization no DELETE permission `() {
      assertThrows<CsmAccessForbiddenException> {
        val name = "o-connector-test-1"
        val organizationToRegister =
            createTestOrganizationWithSimpleSecurity(name, OTHER_TEST_USER_ID, ROLE_USER, ROLE_NONE)
        val organizationRegistered =
            organizationApiService.registerOrganization(organizationToRegister)
        organizationApiService.unregisterOrganization(organizationRegistered.id!!)
      }
    }

    @Test
    fun `unregisterOrganization not owned but DELETE permission `() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationToRegister =
            createTestOrganizationWithSimpleSecurity(name, TEST_USER_ID, ROLE_NONE, ROLE_ADMIN)
        val organizationRegistered =
            organizationApiService.registerOrganization(organizationToRegister)
        runAsOrganizationUser()
        organizationApiService.unregisterOrganization(organizationRegistered.id!!)
      }
    }

    @Test
    fun `updateOrganization owned organization name and services`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))

        organizationRegistered.name = "my-new-name"
        organizationRegistered.services =
            OrganizationServices(
                tenantCredentials = mutableMapOf(),
                storage =
                    OrganizationService(
                        cloudService = "cloud",
                        baseUri = "base",
                        platformService = "platform",
                        resourceUri = "resource",
                        credentials = mutableMapOf(Pair("credential1", 10.0))),
                solutionsContainerRegistry =
                    OrganizationService(
                        cloudService = "cloud",
                        baseUri = "base",
                        platformService = "platform",
                        resourceUri = "resource",
                        credentials = mutableMapOf(Pair("credential2", "toto"))))
        organizationApiService.updateOrganization(
            organizationRegistered.id!!, organizationRegistered)

        assertEquals(
            organizationRegistered,
            organizationApiService.findOrganizationById(organizationRegistered.id!!))
      }
    }

    @Test
    fun `updateOrganization owned organization security`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))

        organizationRegistered.security =
            OrganizationSecurity(
                default = ROLE_NONE,
                mutableListOf(OrganizationAccessControl(id = OTHER_TEST_USER_ID, role = ROLE_USER)))

        organizationApiService.updateOrganization(
            organizationRegistered.id!!, organizationRegistered)

        // Organization Security cannot be updated via updateOrganization endpoint
        // setOrganizationDefaultSecurity or
        // addOrganizationAccessControl/updateOrganizationAccessControl/removeOrganizationAccessControl
        // Should be used instead
        assertNotEquals(
            organizationRegistered,
            organizationApiService.findOrganizationById(organizationRegistered.id!!))
      }
    }

    @Test
    fun `updateOrganization not owned organization with WRITE permission`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.registerOrganization(
                createTestOrganizationWithSimpleSecurity(
                    name, TEST_USER_ID, ROLE_NONE, ROLE_EDITOR))

        runAsOrganizationUser()

        organizationRegistered.name = "my-new-name"
        organizationRegistered.services =
            OrganizationServices(
                tenantCredentials = mutableMapOf(),
                storage =
                    OrganizationService(
                        cloudService = "cloud",
                        baseUri = "base",
                        platformService = "platform",
                        resourceUri = "resource",
                        credentials = mutableMapOf(Pair("credential1", 10.0))),
                solutionsContainerRegistry =
                    OrganizationService(
                        cloudService = "cloud",
                        baseUri = "base",
                        platformService = "platform",
                        resourceUri = "resource",
                        credentials = mutableMapOf(Pair("credential2", "toto"))))

        organizationApiService.updateOrganization(
            organizationRegistered.id!!, organizationRegistered)

        assertEquals(
            organizationRegistered,
            organizationApiService.findOrganizationById(organizationRegistered.id!!))
      }
    }

    @Test
    fun `updateOrganization not owned organization with no WRITE permission`() {
      assertThrows<CsmAccessForbiddenException> {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))
        runAsOrganizationUser()
        organizationApiService.updateOrganization(
            organizationRegistered.id!!, organizationRegistered)
      }
    }

    @Test
    fun `updateSolutionsContainerRegistryByOrganizationId with owned organization`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))

        val newSolutionsContainerRegistry =
            OrganizationService(
                cloudService = "cloud",
                baseUri = "base",
                platformService = "platform",
                resourceUri = "resource",
                credentials = mutableMapOf(Pair("cred2", "test2")))

        organizationApiService.updateSolutionsContainerRegistryByOrganizationId(
            organizationRegistered.id!!, newSolutionsContainerRegistry)

        assertNotEquals(
            newSolutionsContainerRegistry,
            organizationApiService
                .findOrganizationById(organizationRegistered.id!!)
                .services!!
                .solutionsContainerRegistry)
      }
    }

    @Test
    fun `updateSolutionsContainerRegistryByOrganizationId with not owned organization and WRITE permission`() {
      assertDoesNotThrow {
        runAsDifferentOrganizationUser()
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(
                createTestOrganizationWithSimpleSecurity(
                    name, TEST_USER_ID, ROLE_NONE, ROLE_EDITOR))

        runAsOrganizationUser()
        val newSolutionsContainerRegistry =
            OrganizationService(
                cloudService = "cloud",
                baseUri = "base",
                platformService = "platform",
                resourceUri = "resource",
                credentials = mutableMapOf(Pair("cred2", "test2")))

        organizationApiService.updateSolutionsContainerRegistryByOrganizationId(
            organizationRegistered.id!!, newSolutionsContainerRegistry)

        assertNotEquals(
            newSolutionsContainerRegistry,
            organizationApiService
                .findOrganizationById(organizationRegistered.id!!)
                .services!!
                .solutionsContainerRegistry)
      }
    }

    @Test
    fun `updateSolutionsContainerRegistryByOrganizationId with not owned organization and READ permission`() {
      assertThrows<CsmAccessForbiddenException> {
        runAsDifferentOrganizationUser()
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(
                createTestOrganizationWithSimpleSecurity(name, TEST_USER_ID, ROLE_NONE, ROLE_USER))

        runAsOrganizationUser()
        val newSolutionsContainerRegistry =
            OrganizationService(
                cloudService = "cloud",
                baseUri = "base",
                platformService = "platform",
                resourceUri = "resource",
                credentials = mutableMapOf(Pair("cred2", "test2")))

        organizationApiService.updateSolutionsContainerRegistryByOrganizationId(
            organizationRegistered.id!!, newSolutionsContainerRegistry)
      }
    }

    @Test
    fun `updateStorageByOrganizationId with owned organization`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))

        val newStorage =
            OrganizationService(
                cloudService = "cloud",
                baseUri = "base",
                platformService = "platform",
                resourceUri = "resource",
                credentials = mutableMapOf(Pair("cred2", "test2")))

        organizationApiService.updateStorageByOrganizationId(
            organizationRegistered.id!!, newStorage)

        assertNotEquals(
            newStorage,
            organizationApiService
                .findOrganizationById(organizationRegistered.id!!)
                .services!!
                .storage)
      }
    }

    @Test
    fun `updateStorageByOrganizationId with not owned organization and WRITE permission`() {
      assertDoesNotThrow {
        runAsDifferentOrganizationUser()
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(
                createTestOrganizationWithSimpleSecurity(
                    name, TEST_USER_ID, ROLE_NONE, ROLE_EDITOR))

        runAsOrganizationUser()
        val newStorage =
            OrganizationService(
                cloudService = "cloud",
                baseUri = "base",
                platformService = "platform",
                resourceUri = "resource",
                credentials = mutableMapOf(Pair("cred2", "test2")))

        organizationApiService.updateStorageByOrganizationId(
            organizationRegistered.id!!, newStorage)

        assertNotEquals(
            newStorage,
            organizationApiService
                .findOrganizationById(organizationRegistered.id!!)
                .services!!
                .storage)
      }
    }

    @Test
    fun `updateStorageByOrganizationId with not owned organization and READ permission`() {
      assertThrows<CsmAccessForbiddenException> {
        runAsDifferentOrganizationUser()
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(
                createTestOrganizationWithSimpleSecurity(name, TEST_USER_ID, ROLE_NONE, ROLE_USER))

        runAsOrganizationUser()
        val newStorage =
            OrganizationService(
                cloudService = "cloud",
                baseUri = "base",
                platformService = "platform",
                resourceUri = "resource",
                credentials = mutableMapOf(Pair("cred2", "test2")))

        organizationApiService.updateStorageByOrganizationId(
            organizationRegistered.id!!, newStorage)
      }
    }

    @Test
    fun `updateTenantCredentialsByOrganizationId with owned organization and empty body`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))
        val updateResult =
            organizationApiService.updateTenantCredentialsByOrganizationId(
                organizationRegistered.id!!, mapOf())
        assertTrue(updateResult.isEmpty())
      }
    }

    @Test
    fun `updateTenantCredentialsByOrganizationId with owned organization and correct body`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))
        val credentials = mutableMapOf(Pair("cred2", "test2"))
        val updateResult =
            organizationApiService.updateTenantCredentialsByOrganizationId(
                organizationRegistered.id!!, credentials)
        assertEquals(credentials, updateResult)
      }
    }

    @Test
    fun `updateTenantCredentialsByOrganizationId with not owned organization, empty body and no WRITE permission`() {
      assertThrows<CsmAccessForbiddenException> {
        runAsDifferentOrganizationUser()
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))
        runAsOrganizationUser()
        organizationApiService.updateTenantCredentialsByOrganizationId(
            organizationRegistered.id!!, mapOf())
      }
    }

    @Test
    fun `updateTenantCredentialsByOrganizationId with not owned organization, empty body and WRITE permission`() {
      assertDoesNotThrow {
        runAsDifferentOrganizationUser()
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(
                createTestOrganizationWithSimpleSecurity(
                    name, TEST_USER_ID, ROLE_NONE, ROLE_EDITOR))
        val credentials = mutableMapOf(Pair("cred2", "test2"))
        runAsOrganizationUser()
        val updateResult =
            organizationApiService.updateTenantCredentialsByOrganizationId(
                organizationRegistered.id!!, credentials)
        assertEquals(credentials, updateResult)
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
                  component = "scenario",
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
    fun `getOrganizationPermissions with owned organization`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))
        var organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(
                organizationRegistered.id!!, ROLE_VIEWER)
        assertEquals(
            mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY), organizationUserPermissions)
        organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(
                organizationRegistered.id!!, ROLE_USER)
        assertEquals(
            mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_CREATE_CHILDREN),
            organizationUserPermissions)
        organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(
                organizationRegistered.id!!, ROLE_EDITOR)
        assertEquals(
            mutableListOf(
                PERMISSION_READ,
                PERMISSION_READ_SECURITY,
                PERMISSION_CREATE_CHILDREN,
                PERMISSION_WRITE),
            organizationUserPermissions)
        organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(
                organizationRegistered.id!!, ROLE_ADMIN)
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
                organizationRegistered.id!!, UNKNOWN_IDENTIFIER)
        assertEquals(emptyList(), organizationUserPermissions)
      }
    }

    @Test
    fun `getOrganizationPermissions with not owned organization and READ_SECURITY permission`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.registerOrganization(
                createTestOrganizationWithSimpleSecurity(
                    name, TEST_USER_ID, ROLE_NONE, ROLE_VIEWER))
        runAsOrganizationUser()
        var organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(
                organizationRegistered.id!!, ROLE_VIEWER)
        assertEquals(
            mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY), organizationUserPermissions)
        organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(
                organizationRegistered.id!!, ROLE_USER)
        assertEquals(
            mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_CREATE_CHILDREN),
            organizationUserPermissions)
        organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(
                organizationRegistered.id!!, ROLE_EDITOR)
        assertEquals(
            mutableListOf(
                PERMISSION_READ,
                PERMISSION_READ_SECURITY,
                PERMISSION_CREATE_CHILDREN,
                PERMISSION_WRITE),
            organizationUserPermissions)
        organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(
                organizationRegistered.id!!, ROLE_ADMIN)
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
                organizationRegistered.id!!, UNKNOWN_IDENTIFIER)
        assertEquals(emptyList(), organizationUserPermissions)
      }
    }

    @Test
    fun `getOrganizationPermissions with not owned organization and no permission`() {
      assertThrows<CsmAccessForbiddenException> {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))
        runAsOrganizationUser()
        organizationApiService.getOrganizationPermissions(organizationRegistered.id!!, ROLE_VIEWER)
      }
    }

    @Test
    fun `getOrganizationSecurity with owned organization`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))
        assertNotNull(organizationApiService.getOrganizationSecurity(organizationRegistered.id!!))
      }
    }

    @Test
    fun `getOrganizationSecurity with no security organization`() {
      assertThrows<CsmResourceNotFoundException> {
        organizationApiService.getOrganizationSecurity(UNKNOWN_IDENTIFIER)
      }
    }

    @Test
    fun `getOrganizationSecurity with not owned organization with READ_SECURITY permission`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.registerOrganization(
                createTestOrganizationWithSimpleSecurity(
                    name, TEST_USER_ID, ROLE_NONE, ROLE_VIEWER))
        runAsOrganizationUser()
        assertNotNull(organizationApiService.getOrganizationSecurity(organizationRegistered.id!!))
      }
    }

    @Test
    fun `getOrganizationSecurity with not owned organization with no READ_SECURITY permission`() {
      assertThrows<CsmAccessForbiddenException> {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.registerOrganization(
                createTestOrganizationWithSimpleSecurity(name, TEST_USER_ID, ROLE_NONE, ROLE_NONE))
        runAsOrganizationUser()
        organizationApiService.getOrganizationSecurity(organizationRegistered.id!!)
      }
    }

    @Test
    fun `setOrganizationDefaultSecurity with owned organization and existing role`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))
        val defaultRoleCreated = organizationRegistered.security?.default
        assertNotNull(
            organizationApiService.setOrganizationDefaultSecurity(
                organizationRegistered.id!!, OrganizationRole(ROLE_ADMIN)))
        val defaultRoleUpdated =
            organizationApiService.getOrganizationSecurity(organizationRegistered.id!!).default
        assertNotEquals(defaultRoleCreated, defaultRoleUpdated)
      }
    }

    @Test
    fun `setOrganizationDefaultSecurity with owned organization and non-existing role`() {
      assertThrows<CsmClientException> {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))
        organizationApiService.setOrganizationDefaultSecurity(
            organizationRegistered.id!!, OrganizationRole(UNKNOWN_IDENTIFIER))
      }
    }

    @Test
    fun `setOrganizationDefaultSecurity with not owned organization and WRITE_SECURITY_PERMISSION`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.registerOrganization(
                createTestOrganizationWithSimpleSecurity(name, TEST_USER_ID, ROLE_NONE, ROLE_ADMIN))
        val defaultRoleCreated = organizationRegistered.security?.default
        runAsOrganizationUser()
        assertNotNull(
            organizationApiService.setOrganizationDefaultSecurity(
                organizationRegistered.id!!, OrganizationRole(ROLE_ADMIN)))
        val defaultRoleUpdated =
            organizationApiService.getOrganizationSecurity(organizationRegistered.id!!).default
        assertNotEquals(defaultRoleCreated, defaultRoleUpdated)
      }
    }

    @Test
    fun `setOrganizationDefaultSecurity with not owned organization and no WRITE_SECURITY_PERMISSION`() {
      assertThrows<CsmAccessForbiddenException> {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.registerOrganization(
                createTestOrganizationWithSimpleSecurity(name, TEST_USER_ID, ROLE_NONE, ROLE_USER))
        runAsOrganizationUser()
        organizationApiService.setOrganizationDefaultSecurity(
            organizationRegistered.id!!, OrganizationRole(ROLE_ADMIN))
      }
    }

    @Test
    fun `getOrganizationAccessControl with owned organization and current user`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))
        val organizationRole =
            organizationApiService.getOrganizationAccessControl(
                organizationRegistered.id!!, TEST_USER_ID)
        assertNotNull(organizationRole)
        assertEquals(ROLE_ADMIN, organizationRole.role)
        assertEquals(TEST_USER_ID, organizationRole.id)
      }
    }

    @Test
    fun `getOrganizationAccessControl with owned organization and non-existing user`() {
      assertThrows<CsmResourceNotFoundException> {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))
        organizationApiService.getOrganizationAccessControl(organizationRegistered.id!!, "UNKOWN")
      }
    }

    @Test
    fun `getOrganizationAccessControl with not owned organization, READ_SECURITY permission and existing user`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.registerOrganization(
                createTestOrganizationWithSimpleSecurity(
                    name, TEST_USER_ID, ROLE_NONE, ROLE_VIEWER))
        runAsOrganizationUser()
        val organizationRole =
            organizationApiService.getOrganizationAccessControl(
                organizationRegistered.id!!, TEST_USER_ID)
        assertNotNull(organizationRole)
        assertEquals(ROLE_VIEWER, organizationRole.role)
        assertEquals(TEST_USER_ID, organizationRole.id)
      }
    }

    @Test
    fun `getOrganizationAccessControl with not owned organization, READ_SECURITY permission and non existing user`() {
      assertThrows<CsmResourceNotFoundException> {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.registerOrganization(
                createTestOrganizationWithSimpleSecurity(
                    name, TEST_USER_ID, ROLE_NONE, ROLE_VIEWER))
        runAsOrganizationUser()
        organizationApiService.getOrganizationAccessControl(
            organizationRegistered.id!!, UNKNOWN_IDENTIFIER)
      }
    }

    @Test
    fun `getOrganizationAccessControl with not owned organization, no READ_SECURITY permission`() {
      assertThrows<CsmAccessForbiddenException> {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.registerOrganization(
                createTestOrganizationWithSimpleSecurity(name, TEST_USER_ID, ROLE_NONE, ROLE_NONE))
        runAsOrganizationUser()
        organizationApiService.getOrganizationAccessControl(
            organizationRegistered.id!!, UNKNOWN_IDENTIFIER)
      }
    }

    @Test
    fun `addOrganizationAccessControl with owned organization`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))
        assertThrows<CsmResourceNotFoundException> {
          organizationApiService.getOrganizationAccessControl(
              organizationRegistered.id!!, OTHER_TEST_USER_ID)
        }
        val otherUserACL = OrganizationAccessControl(id = OTHER_TEST_USER_ID, role = ROLE_VIEWER)
        assertNotNull(
            organizationApiService.addOrganizationAccessControl(
                organizationRegistered.id!!, otherUserACL))

        val otherUserACLRetrieved =
            organizationApiService.getOrganizationAccessControl(
                organizationRegistered.id!!, OTHER_TEST_USER_ID)
        assertEquals(otherUserACL, otherUserACLRetrieved)
      }
    }

    @Test
    fun `addOrganizationAccessControl with owned organization (ROLE_NONE)`() {
      assertThrows<CsmClientException> {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))

        val otherUserACL = OrganizationAccessControl(id = OTHER_TEST_USER_ID, role = ROLE_NONE)
        organizationApiService.addOrganizationAccessControl(
            organizationRegistered.id!!, otherUserACL)
      }
    }

    @Test
    fun `addOrganizationAccessControl with not owned organization and PERMISSION_WRITE_SECURITY`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.registerOrganization(
                createTestOrganizationWithSimpleSecurity(name, TEST_USER_ID, ROLE_NONE, ROLE_ADMIN))
        runAsOrganizationUser()
        assertThrows<CsmResourceNotFoundException> {
          organizationApiService.getOrganizationAccessControl(
              organizationRegistered.id!!, TEST_ADMIN_USER_ID)
        }
        val otherUserACL = OrganizationAccessControl(id = TEST_ADMIN_USER_ID, role = ROLE_VIEWER)
        assertNotNull(
            organizationApiService.addOrganizationAccessControl(
                organizationRegistered.id!!, otherUserACL))

        val otherUserACLRetrieved =
            organizationApiService.getOrganizationAccessControl(
                organizationRegistered.id!!, TEST_ADMIN_USER_ID)
        assertEquals(otherUserACL, otherUserACLRetrieved)
      }
    }

    @Test
    fun `addOrganizationAccessControl with not owned organization and no PERMISSION_WRITE_SECURITY`() {
      assertThrows<CsmAccessForbiddenException> {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.registerOrganization(
                createTestOrganizationWithSimpleSecurity(
                    name, TEST_USER_ID, ROLE_NONE, ROLE_VIEWER))
        runAsOrganizationUser()
        assertThrows<CsmResourceNotFoundException> {
          organizationApiService.getOrganizationAccessControl(
              organizationRegistered.id!!, TEST_ADMIN_USER_ID)
        }
        val otherUserACL = OrganizationAccessControl(id = TEST_ADMIN_USER_ID, role = ROLE_VIEWER)
        organizationApiService.addOrganizationAccessControl(
            organizationRegistered.id!!, otherUserACL)
      }
    }

    @Test
    fun `updateOrganizationAccessControl with owned organization cannot update last admin`() {
      assertThrows<CsmAccessForbiddenException> {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))
        organizationApiService.updateOrganizationAccessControl(
            organizationRegistered.id!!, TEST_USER_ID, OrganizationRole(role = ROLE_VIEWER))
      }
    }

    @Test
    fun `updateOrganizationAccessControl with owned organization can update user (!= ROLE_NONE)`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))

        organizationApiService.addOrganizationAccessControl(
            organizationRegistered.id!!,
            OrganizationAccessControl(id = OTHER_TEST_USER_ID, role = ROLE_VIEWER))

        assertNotNull(
            organizationApiService.updateOrganizationAccessControl(
                organizationRegistered.id!!,
                OTHER_TEST_USER_ID,
                OrganizationRole(role = ROLE_EDITOR)))

        val userACLRetrieved =
            organizationApiService.getOrganizationAccessControl(
                organizationRegistered.id!!, OTHER_TEST_USER_ID)
        assertNotEquals(ROLE_VIEWER, userACLRetrieved.role)
        assertEquals(ROLE_EDITOR, userACLRetrieved.role)
      }
    }

    @Test
    fun `updateOrganizationAccessControl with owned organization cannot update user (= ROLE_NONE)`() {
      assertThrows<CsmClientException> {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))

        organizationApiService.updateOrganizationAccessControl(
            organizationRegistered.id!!, TEST_USER_ID, OrganizationRole(role = ROLE_NONE))
      }
    }

    @Test
    fun `updateOrganizationAccessControl with owned organization and unknown ACL user`() {
      assertThrows<CsmResourceNotFoundException> {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))

        organizationApiService.updateOrganizationAccessControl(
            organizationRegistered.id!!, UNKNOWN_IDENTIFIER, OrganizationRole(role = ROLE_EDITOR))
      }
    }

    @Test
    fun `updateOrganizationAccessControl with owned organization and wrong role`() {
      assertThrows<CsmClientException> {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))

        organizationApiService.updateOrganizationAccessControl(
            organizationRegistered.id!!, TEST_USER_ID, OrganizationRole(role = UNKNOWN_IDENTIFIER))
      }
    }

    @Test
    fun `updateOrganizationAccessControl with not owned organization, WRITE_SECURITY permission, can update user (!= ROLE_NONE)`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))

        organizationApiService.addOrganizationAccessControl(
            organizationRegistered.id!!,
            OrganizationAccessControl(id = TEST_USER_ID, role = ROLE_ADMIN))

        runAsOrganizationUser()

        assertNotNull(
            organizationApiService.updateOrganizationAccessControl(
                organizationRegistered.id!!,
                OTHER_TEST_USER_ID,
                OrganizationRole(role = ROLE_EDITOR)))

        val userACLRetrieved =
            organizationApiService.getOrganizationAccessControl(
                organizationRegistered.id!!, OTHER_TEST_USER_ID)
        assertEquals(ROLE_EDITOR, userACLRetrieved.role)
      }
    }

    @Test
    fun `updateOrganizationAccessControl with not owned organization, no WRITE_SECURITY permission`() {
      assertThrows<CsmAccessForbiddenException> {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))

        organizationApiService.addOrganizationAccessControl(
            organizationRegistered.id!!,
            OrganizationAccessControl(id = TEST_USER_ID, role = ROLE_VIEWER))

        runAsOrganizationUser()

        organizationApiService.updateOrganizationAccessControl(
            organizationRegistered.id!!, OTHER_TEST_USER_ID, OrganizationRole(role = ROLE_EDITOR))
      }
    }

    @Test
    fun `updateOrganizationAccessControl with not owned organization, no WRITE_SECURITY permission, ROLE_NONE`() {
      assertThrows<CsmClientException> {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))

        organizationApiService.addOrganizationAccessControl(
            organizationRegistered.id!!,
            OrganizationAccessControl(id = TEST_USER_ID, role = ROLE_VIEWER))

        runAsOrganizationUser()

        organizationApiService.updateOrganizationAccessControl(
            organizationRegistered.id!!, OTHER_TEST_USER_ID, OrganizationRole(role = ROLE_NONE))
      }
    }

    @Test
    fun `removeOrganizationAccessControl with owned organization`() {
      val name = "o-connector-test-1"
      val organizationRegistered =
          organizationApiService.registerOrganization(createTestOrganization(name))

      val otherUserACL = OrganizationAccessControl(id = OTHER_TEST_USER_ID, role = ROLE_VIEWER)
      organizationApiService.addOrganizationAccessControl(organizationRegistered.id!!, otherUserACL)

      organizationApiService.removeOrganizationAccessControl(
          organizationRegistered.id!!, OTHER_TEST_USER_ID)
      assertThrows<CsmResourceNotFoundException> {
        organizationApiService.getOrganizationAccessControl(
            organizationRegistered.id!!, OTHER_TEST_USER_ID)
      }
    }

    @Test
    fun `removeOrganizationAccessControl with not owned organization, WRITE_SECURITY permission`() {
      val name = "o-connector-test-1"
      runAsDifferentOrganizationUser()
      val organizationRegistered =
          organizationApiService.registerOrganization(createTestOrganization(name))

      val otherUserACL = OrganizationAccessControl(id = TEST_USER_ID, role = ROLE_ADMIN)
      organizationApiService.addOrganizationAccessControl(organizationRegistered.id!!, otherUserACL)

      runAsOrganizationUser()
      organizationApiService.removeOrganizationAccessControl(
          organizationRegistered.id!!, OTHER_TEST_USER_ID)
      assertThrows<CsmResourceNotFoundException> {
        organizationApiService.getOrganizationAccessControl(
            organizationRegistered.id!!, OTHER_TEST_USER_ID)
      }
    }

    @Test
    fun `removeOrganizationAccessControl with not owned organization, no WRITE_SECURITY permission`() {
      val name = "o-connector-test-1"
      runAsDifferentOrganizationUser()
      val organizationRegistered =
          organizationApiService.registerOrganization(createTestOrganization(name))

      val otherUserACL = OrganizationAccessControl(id = TEST_USER_ID, role = ROLE_VIEWER)
      organizationApiService.addOrganizationAccessControl(organizationRegistered.id!!, otherUserACL)

      runAsOrganizationUser()
      assertThrows<CsmAccessForbiddenException> {
        organizationApiService.removeOrganizationAccessControl(
            organizationRegistered.id!!, OTHER_TEST_USER_ID)
      }
    }

    @Test
    fun `getOrganizationSecurityUsers with owned organization`() {
      val name = "o-connector-test-1"
      val organizationRegistered =
          organizationApiService.registerOrganization(createTestOrganization(name))

      val orgaUsers =
          organizationApiService.getOrganizationSecurityUsers(organizationRegistered.id!!)
      assertEquals(listOf(TEST_USER_ID), orgaUsers)
    }

    @Test
    fun `getOrganizationSecurityUsers with not owned organization, READ_SECURITY permission`() {
      val name = "o-connector-test-1"
      runAsDifferentOrganizationUser()
      val organizationRegistered =
          organizationApiService.registerOrganization(
              createTestOrganizationWithSimpleSecurity(name, TEST_USER_ID, ROLE_NONE, ROLE_VIEWER))
      runAsOrganizationUser()
      val orgaUsers =
          organizationApiService.getOrganizationSecurityUsers(organizationRegistered.id!!)
      assertEquals(listOf(TEST_USER_ID), orgaUsers)
    }

    @Test
    fun `getOrganizationSecurityUsers with not owned organization, no READ_SECURITY permission`() {
      val name = "o-connector-test-1"
      runAsDifferentOrganizationUser()
      val organizationRegistered =
          organizationApiService.registerOrganization(
              createTestOrganizationWithSimpleSecurity(name, TEST_USER_ID, ROLE_NONE, ROLE_NONE))
      runAsOrganizationUser()
      assertThrows<CsmAccessForbiddenException> {
        organizationApiService.getOrganizationSecurityUsers(organizationRegistered.id!!)
      }
    }

    @Test
    fun `importOrganization organization`() {
      val name = "o-connector-test-1"
      assertThrows<CsmAccessForbiddenException> {
        organizationApiService.importOrganization(createTestOrganization(name))
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
      testFindAllOrganizations(null, null, numberOfOrganizationToCreate)
      testFindAllOrganizations(0, null, defaultPageSize)
      testFindAllOrganizations(0, 10, 10)
      testFindAllOrganizations(1, 200, 0)
      testFindAllOrganizations(1, 15, 5)
    }

    @Test
    fun `find All Organizations with correct values and RBAC for current user`() {

      val numberOfOrganizationCreated = batchOrganizationCreationWithRBAC(TEST_ADMIN_USER_ID)
      // This number represents the amount of Organization that test.user can read regarding RBAC
      // We have 36 combinations per user in batchOrganizationCreationWithRBAC
      testFindAllWithRBAC(numberOfOrganizationCreated, 36)
    }

    @Test
    fun `find All Organizations with correct values and no RBAC for current user`() {

      val numberOfOrganizationCreated = batchOrganizationCreationWithRBAC(OTHER_TEST_USER_ID)
      // This number represents the amount of Organization that test.user can read regarding RBAC
      // We have 36 combinations per user in batchOrganizationCreationWithRBAC
      testFindAllWithRBAC(numberOfOrganizationCreated, 36)
    }

    @Test
    fun `find All Organizations with wrong values`() {
      testFindAllOrganizationsWithWrongValues()
    }

    @Test
    fun `findOrganizationById as owner`() {
      val organizationRegistered =
          organizationApiService.registerOrganization(createTestOrganization("o-connector-test-1"))
      assertNotNull(organizationApiService.findOrganizationById(organizationRegistered.id!!))
    }

    @Test
    fun `findOrganizationById as not owner`() {
      testFindOrganizationByIdAsNotOwner(false, null, null, null, false) { runAsPlatformAdmin() }
    }

    @Test
    fun `registerOrganization with minimal values`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationToRegister = createTestOrganization(name)
        val organizationRegistered =
            organizationApiService.registerOrganization(organizationToRegister)
        assertEquals(
            OrganizationSecurity(
                default = ROLE_NONE,
                mutableListOf(
                    OrganizationAccessControl(id = TEST_ADMIN_USER_ID, role = ROLE_ADMIN))),
            organizationRegistered.security)
        assertEquals(name, organizationRegistered.name)
        assertTrue(organizationRegistered.id!!.startsWith("o-"))
      }
    }

    @Test
    fun `registerOrganization with null required organization name`() {
      assertThrows<IllegalArgumentException> {
        organizationApiService.registerOrganization(Organization(name = null))
      }
    }

    @Test
    fun `registerOrganization without required organization name`() {
      assertThrows<IllegalArgumentException> {
        organizationApiService.registerOrganization(createTestOrganization(""))
      }
    }

    @Test
    fun `registerOrganization with security values`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationToRegister =
            createTestOrganizationWithSimpleSecurity(name, OTHER_TEST_USER_ID, ROLE_USER, ROLE_NONE)
        val organizationRegistered =
            organizationApiService.registerOrganization(organizationToRegister)
        assertEquals(
            OrganizationSecurity(
                default = ROLE_USER,
                mutableListOf(
                    OrganizationAccessControl(id = OTHER_TEST_USER_ID, role = ROLE_NONE))),
            organizationRegistered.security)
        assertEquals(name, organizationRegistered.name)
        assertTrue(organizationRegistered.id!!.startsWith("o-"))
      }
    }

    @Test
    fun `unregisterOrganization owned organization `() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationToRegister = createTestOrganization(name)
        val organizationRegistered =
            organizationApiService.registerOrganization(organizationToRegister)
        organizationApiService.unregisterOrganization(organizationRegistered.id!!)
      }
    }

    @Test
    fun `unregisterOrganization unexisting organization `() {
      assertThrows<CsmResourceNotFoundException> {
        organizationApiService.unregisterOrganization("o-connector-test-1")
      }
    }

    @Test
    fun `unregisterOrganization not owned organization `() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationToRegister =
            createTestOrganizationWithSimpleSecurity(name, TEST_ADMIN_USER_ID, ROLE_NONE, ROLE_NONE)
        val organizationRegistered =
            organizationApiService.registerOrganization(organizationToRegister)
        runAsPlatformAdmin()
        organizationApiService.unregisterOrganization(organizationRegistered.id!!)
      }
    }

    @Test
    fun `updateOrganization owned organization name and services`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))

        organizationRegistered.name = "my-new-name"
        organizationRegistered.services =
            OrganizationServices(
                tenantCredentials = mutableMapOf(),
                storage =
                    OrganizationService(
                        cloudService = "cloud",
                        baseUri = "base",
                        platformService = "platform",
                        resourceUri = "resource",
                        credentials = mutableMapOf(Pair("credential1", 10.0))),
                solutionsContainerRegistry =
                    OrganizationService(
                        cloudService = "cloud",
                        baseUri = "base",
                        platformService = "platform",
                        resourceUri = "resource",
                        credentials = mutableMapOf(Pair("credential2", "toto"))))
        organizationApiService.updateOrganization(
            organizationRegistered.id!!, organizationRegistered)

        assertEquals(
            organizationRegistered,
            organizationApiService.findOrganizationById(organizationRegistered.id!!))
      }
    }

    @Test
    fun `updateOrganization owned organization security`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))

        organizationRegistered.security =
            OrganizationSecurity(
                default = ROLE_NONE,
                mutableListOf(OrganizationAccessControl(id = OTHER_TEST_USER_ID, role = ROLE_USER)))

        organizationApiService.updateOrganization(
            organizationRegistered.id!!, organizationRegistered)

        // Organization Security cannot be updated via updateOrganization endpoint
        // setOrganizationDefaultSecurity or
        // addOrganizationAccessControl/updateOrganizationAccessControl/removeOrganizationAccessControl
        // Should be used instead
        assertNotEquals(
            organizationRegistered,
            organizationApiService.findOrganizationById(organizationRegistered.id!!))
      }
    }

    @Test
    fun `updateOrganization not owned organization with WRITE permission`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.registerOrganization(
                createTestOrganizationWithSimpleSecurity(
                    name, TEST_ADMIN_USER_ID, ROLE_NONE, ROLE_EDITOR))

        runAsPlatformAdmin()

        organizationRegistered.name = "my-new-name"
        organizationRegistered.services =
            OrganizationServices(
                tenantCredentials = mutableMapOf(),
                storage =
                    OrganizationService(
                        cloudService = "cloud",
                        baseUri = "base",
                        platformService = "platform",
                        resourceUri = "resource",
                        credentials = mutableMapOf(Pair("credential1", 10.0))),
                solutionsContainerRegistry =
                    OrganizationService(
                        cloudService = "cloud",
                        baseUri = "base",
                        platformService = "platform",
                        resourceUri = "resource",
                        credentials = mutableMapOf(Pair("credential2", "toto"))))

        organizationApiService.updateOrganization(
            organizationRegistered.id!!, organizationRegistered)

        assertEquals(
            organizationRegistered,
            organizationApiService.findOrganizationById(organizationRegistered.id!!))
      }
    }

    @Test
    fun `updateOrganization not owned organization with no WRITE permission`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))
        runAsPlatformAdmin()
        organizationApiService.updateOrganization(
            organizationRegistered.id!!, organizationRegistered)
      }
    }

    @Test
    fun `updateSolutionsContainerRegistryByOrganizationId with owned organization`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))

        val newSolutionsContainerRegistry =
            OrganizationService(
                cloudService = "cloud",
                baseUri = "base",
                platformService = "platform",
                resourceUri = "resource",
                credentials = mutableMapOf(Pair("cred2", "test2")))

        organizationApiService.updateSolutionsContainerRegistryByOrganizationId(
            organizationRegistered.id!!, newSolutionsContainerRegistry)

        assertNotEquals(
            newSolutionsContainerRegistry,
            organizationApiService
                .findOrganizationById(organizationRegistered.id!!)
                .services!!
                .solutionsContainerRegistry)
      }
    }

    @Test
    fun `updateSolutionsContainerRegistryByOrganizationId with not owned organization and WRITE permission`() {
      assertDoesNotThrow {
        runAsDifferentOrganizationUser()
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(
                createTestOrganizationWithSimpleSecurity(
                    name, TEST_USER_ID, ROLE_NONE, ROLE_EDITOR))

        runAsPlatformAdmin()
        val newSolutionsContainerRegistry =
            OrganizationService(
                cloudService = "cloud",
                baseUri = "base",
                platformService = "platform",
                resourceUri = "resource",
                credentials = mutableMapOf(Pair("cred2", "test2")))

        organizationApiService.updateSolutionsContainerRegistryByOrganizationId(
            organizationRegistered.id!!, newSolutionsContainerRegistry)

        assertNotEquals(
            newSolutionsContainerRegistry,
            organizationApiService
                .findOrganizationById(organizationRegistered.id!!)
                .services!!
                .solutionsContainerRegistry)
      }
    }

    @Test
    fun `updateSolutionsContainerRegistryByOrganizationId with not owned organization and READ permission`() {
      assertDoesNotThrow {
        runAsDifferentOrganizationUser()
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(
                createTestOrganizationWithSimpleSecurity(
                    name, TEST_USER_ID, ROLE_NONE, ROLE_EDITOR))

        runAsPlatformAdmin()
        val newSolutionsContainerRegistry =
            OrganizationService(
                cloudService = "cloud",
                baseUri = "base",
                platformService = "platform",
                resourceUri = "resource",
                credentials = mutableMapOf(Pair("cred2", "test2")))

        organizationApiService.updateSolutionsContainerRegistryByOrganizationId(
            organizationRegistered.id!!, newSolutionsContainerRegistry)

        assertNotEquals(
            newSolutionsContainerRegistry,
            organizationApiService
                .findOrganizationById(organizationRegistered.id!!)
                .services!!
                .solutionsContainerRegistry)
      }
    }

    @Test
    fun `updateStorageByOrganizationId with owned organization`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))

        val newStorage =
            OrganizationService(
                cloudService = "cloud",
                baseUri = "base",
                platformService = "platform",
                resourceUri = "resource",
                credentials = mutableMapOf(Pair("cred2", "test2")))

        organizationApiService.updateStorageByOrganizationId(
            organizationRegistered.id!!, newStorage)

        assertNotEquals(
            newStorage,
            organizationApiService
                .findOrganizationById(organizationRegistered.id!!)
                .services!!
                .storage)
      }
    }

    @Test
    fun `updateStorageByOrganizationId with not owned organization and WRITE permission`() {
      assertDoesNotThrow {
        runAsDifferentOrganizationUser()
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(
                createTestOrganizationWithSimpleSecurity(
                    name, TEST_USER_ID, ROLE_NONE, ROLE_EDITOR))

        runAsPlatformAdmin()
        val newStorage =
            OrganizationService(
                cloudService = "cloud",
                baseUri = "base",
                platformService = "platform",
                resourceUri = "resource",
                credentials = mutableMapOf(Pair("cred2", "test2")))

        organizationApiService.updateStorageByOrganizationId(
            organizationRegistered.id!!, newStorage)

        assertNotEquals(
            newStorage,
            organizationApiService
                .findOrganizationById(organizationRegistered.id!!)
                .services!!
                .storage)
      }
    }

    @Test
    fun `updateStorageByOrganizationId with not owned organization and READ permission`() {
      assertDoesNotThrow {
        runAsDifferentOrganizationUser()
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(
                createTestOrganizationWithSimpleSecurity(
                    name, TEST_USER_ID, ROLE_NONE, ROLE_EDITOR))

        runAsPlatformAdmin()
        val newStorage =
            OrganizationService(
                cloudService = "cloud",
                baseUri = "base",
                platformService = "platform",
                resourceUri = "resource",
                credentials = mutableMapOf(Pair("cred2", "test2")))

        organizationApiService.updateStorageByOrganizationId(
            organizationRegistered.id!!, newStorage)

        assertNotEquals(
            newStorage,
            organizationApiService
                .findOrganizationById(organizationRegistered.id!!)
                .services!!
                .storage)
      }
    }

    @Test
    fun `updateTenantCredentialsByOrganizationId with owned organization and empty body`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))
        val updateResult =
            organizationApiService.updateTenantCredentialsByOrganizationId(
                organizationRegistered.id!!, mapOf())
        assertTrue(updateResult.isEmpty())
      }
    }

    @Test
    fun `updateTenantCredentialsByOrganizationId with owned organization and correct body`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))
        val credentials = mutableMapOf(Pair("cred2", "test2"))
        val updateResult =
            organizationApiService.updateTenantCredentialsByOrganizationId(
                organizationRegistered.id!!, credentials)
        assertEquals(credentials, updateResult)
      }
    }

    @Test
    fun `updateTenantCredentialsByOrganizationId with not owned organization, empty body and no WRITE permission`() {
      assertDoesNotThrow {
        runAsDifferentOrganizationUser()
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))
        runAsPlatformAdmin()
        val updateResult =
            organizationApiService.updateTenantCredentialsByOrganizationId(
                organizationRegistered.id!!, mapOf())
        assertTrue(updateResult.isEmpty())
      }
    }

    @Test
    fun `updateTenantCredentialsByOrganizationId with not owned organization, empty body and WRITE permission`() {
      assertDoesNotThrow {
        runAsDifferentOrganizationUser()
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(
                createTestOrganizationWithSimpleSecurity(
                    name, TEST_USER_ID, ROLE_NONE, ROLE_EDITOR))
        val credentials = mutableMapOf(Pair("cred2", "test2"))
        runAsPlatformAdmin()
        val updateResult =
            organizationApiService.updateTenantCredentialsByOrganizationId(
                organizationRegistered.id!!, credentials)
        assertEquals(credentials, updateResult)
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
                  component = "scenario",
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
    fun `getOrganizationPermissions with owned organization`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))
        var organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(
                organizationRegistered.id!!, ROLE_VIEWER)
        assertEquals(
            mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY), organizationUserPermissions)
        organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(
                organizationRegistered.id!!, ROLE_USER)
        assertEquals(
            mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_CREATE_CHILDREN),
            organizationUserPermissions)
        organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(
                organizationRegistered.id!!, ROLE_EDITOR)
        assertEquals(
            mutableListOf(
                PERMISSION_READ,
                PERMISSION_READ_SECURITY,
                PERMISSION_CREATE_CHILDREN,
                PERMISSION_WRITE),
            organizationUserPermissions)
        organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(
                organizationRegistered.id!!, ROLE_ADMIN)
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
                organizationRegistered.id!!, UNKNOWN_IDENTIFIER)
        assertEquals(emptyList(), organizationUserPermissions)
      }
    }

    @Test
    fun `getOrganizationPermissions with not owned organization and READ_SECURITY permission`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.registerOrganization(
                createTestOrganizationWithSimpleSecurity(
                    name, TEST_USER_ID, ROLE_NONE, ROLE_VIEWER))
        runAsPlatformAdmin()
        var organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(
                organizationRegistered.id!!, ROLE_VIEWER)
        assertEquals(
            mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY), organizationUserPermissions)
        organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(
                organizationRegistered.id!!, ROLE_USER)
        assertEquals(
            mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_CREATE_CHILDREN),
            organizationUserPermissions)
        organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(
                organizationRegistered.id!!, ROLE_EDITOR)
        assertEquals(
            mutableListOf(
                PERMISSION_READ,
                PERMISSION_READ_SECURITY,
                PERMISSION_CREATE_CHILDREN,
                PERMISSION_WRITE),
            organizationUserPermissions)
        organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(
                organizationRegistered.id!!, ROLE_ADMIN)
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
                organizationRegistered.id!!, UNKNOWN_IDENTIFIER)
        assertEquals(emptyList(), organizationUserPermissions)
      }
    }

    @Test
    fun `getOrganizationPermissions with not owned organization and no permission`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.registerOrganization(
                createTestOrganizationWithSimpleSecurity(
                    name, TEST_USER_ID, ROLE_NONE, ROLE_VIEWER))
        runAsPlatformAdmin()
        var organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(
                organizationRegistered.id!!, ROLE_VIEWER)
        assertEquals(
            mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY), organizationUserPermissions)
        organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(
                organizationRegistered.id!!, ROLE_USER)
        assertEquals(
            mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_CREATE_CHILDREN),
            organizationUserPermissions)
        organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(
                organizationRegistered.id!!, ROLE_EDITOR)
        assertEquals(
            mutableListOf(
                PERMISSION_READ,
                PERMISSION_READ_SECURITY,
                PERMISSION_CREATE_CHILDREN,
                PERMISSION_WRITE),
            organizationUserPermissions)
        organizationUserPermissions =
            organizationApiService.getOrganizationPermissions(
                organizationRegistered.id!!, ROLE_ADMIN)
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
                organizationRegistered.id!!, UNKNOWN_IDENTIFIER)
        assertEquals(emptyList(), organizationUserPermissions)
      }
    }

    @Test
    fun `getOrganizationSecurity with owned organization`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))
        assertNotNull(organizationApiService.getOrganizationSecurity(organizationRegistered.id!!))
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
        organizationApiService.importOrganization(
            Organization(id = "org1", name = "myWonderfullOrganization"))
        organizationApiService.getOrganizationSecurity("org1")
      }
    }

    @Test
    fun `getOrganizationSecurity with not owned organization with READ_SECURITY permission`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.registerOrganization(
                createTestOrganizationWithSimpleSecurity(
                    name, TEST_USER_ID, ROLE_NONE, ROLE_VIEWER))
        runAsPlatformAdmin()
        assertNotNull(organizationApiService.getOrganizationSecurity(organizationRegistered.id!!))
      }
    }

    @Test
    fun `getOrganizationSecurity with not owned organization with no READ_SECURITY permission`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.registerOrganization(
                createTestOrganizationWithSimpleSecurity(
                    name, TEST_ADMIN_USER_ID, ROLE_NONE, ROLE_NONE))
        runAsPlatformAdmin()
        assertNotNull(organizationApiService.getOrganizationSecurity(organizationRegistered.id!!))
      }
    }

    @Test
    fun `setOrganizationDefaultSecurity with owned organization and existing role`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))
        val defaultRoleCreated = organizationRegistered.security?.default
        assertNotNull(
            organizationApiService.setOrganizationDefaultSecurity(
                organizationRegistered.id!!, OrganizationRole(ROLE_ADMIN)))
        val defaultRoleUpdated =
            organizationApiService.getOrganizationSecurity(organizationRegistered.id!!).default
        assertNotEquals(defaultRoleCreated, defaultRoleUpdated)
      }
    }

    @Test
    fun `setOrganizationDefaultSecurity with owned organization and non-existing role`() {
      assertThrows<CsmClientException> {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))
        organizationApiService.setOrganizationDefaultSecurity(
            organizationRegistered.id!!, OrganizationRole(UNKNOWN_IDENTIFIER))
      }
    }

    @Test
    fun `setOrganizationDefaultSecurity with not owned organization`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))
        val defaultRoleCreated = organizationRegistered.security?.default
        runAsPlatformAdmin()
        assertNotNull(
            organizationApiService.setOrganizationDefaultSecurity(
                organizationRegistered.id!!, OrganizationRole(ROLE_ADMIN)))
        val defaultRoleUpdated =
            organizationApiService.getOrganizationSecurity(organizationRegistered.id!!).default
        assertNotEquals(defaultRoleCreated, defaultRoleUpdated)
      }
    }

    @Test
    fun `setOrganizationDefaultSecurity with not owned organization and no WRITE_SECURITY_PERMISSION`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.registerOrganization(
                createTestOrganizationWithSimpleSecurity(
                    name, TEST_ADMIN_USER_ID, ROLE_NONE, ROLE_NONE))
        val defaultRoleCreated = organizationRegistered.security?.default
        runAsPlatformAdmin()
        assertNotNull(
            organizationApiService.setOrganizationDefaultSecurity(
                organizationRegistered.id!!, OrganizationRole(ROLE_ADMIN)))
        val defaultRoleUpdated =
            organizationApiService.getOrganizationSecurity(organizationRegistered.id!!).default
        assertNotEquals(defaultRoleCreated, defaultRoleUpdated)
      }
    }

    @Test
    fun `getOrganizationAccessControl with owned organization and current user`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))
        val organizationRole =
            organizationApiService.getOrganizationAccessControl(
                organizationRegistered.id!!, TEST_ADMIN_USER_ID)
        assertNotNull(organizationRole)
        assertEquals(ROLE_ADMIN, organizationRole.role)
        assertEquals(TEST_ADMIN_USER_ID, organizationRole.id)
      }
    }

    @Test
    fun `getOrganizationAccessControl with owned organization and non-existing user`() {
      assertThrows<CsmResourceNotFoundException> {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))
        organizationApiService.getOrganizationAccessControl(organizationRegistered.id!!, "UNKOWN")
      }
    }

    @Test
    fun `getOrganizationAccessControl with not owned organization, READ_SECURITY permission and existing user`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.registerOrganization(
                createTestOrganizationWithSimpleSecurity(
                    name, TEST_ADMIN_USER_ID, ROLE_NONE, ROLE_VIEWER))
        runAsPlatformAdmin()
        val organizationRole =
            organizationApiService.getOrganizationAccessControl(
                organizationRegistered.id!!, TEST_ADMIN_USER_ID)
        assertNotNull(organizationRole)
        assertEquals(ROLE_VIEWER, organizationRole.role)
        assertEquals(TEST_ADMIN_USER_ID, organizationRole.id)
      }
    }

    @Test
    fun `getOrganizationAccessControl with not owned organization, READ_SECURITY permission and non existing user`() {
      assertThrows<CsmResourceNotFoundException> {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.registerOrganization(
                createTestOrganizationWithSimpleSecurity(
                    name, TEST_ADMIN_USER_ID, ROLE_NONE, ROLE_VIEWER))
        runAsPlatformAdmin()
        organizationApiService.getOrganizationAccessControl(
            organizationRegistered.id!!, UNKNOWN_IDENTIFIER)
      }
    }

    @Test
    fun `getOrganizationAccessControl with not owned organization, no READ_SECURITY permission, non-existing user`() {
      assertThrows<CsmResourceNotFoundException> {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.registerOrganization(
                createTestOrganizationWithSimpleSecurity(
                    name, TEST_ADMIN_USER_ID, ROLE_NONE, ROLE_NONE))
        runAsPlatformAdmin()
        organizationApiService.getOrganizationAccessControl(
            organizationRegistered.id!!, UNKNOWN_IDENTIFIER)
      }
    }

    @Test
    fun `getOrganizationAccessControl with not owned organization, no READ_SECURITY permission, existing user`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.registerOrganization(
                createTestOrganizationWithSimpleSecurity(
                    name, TEST_ADMIN_USER_ID, ROLE_NONE, ROLE_NONE))
        runAsPlatformAdmin()
        val organizationAccessControl =
            organizationApiService.getOrganizationAccessControl(
                organizationRegistered.id!!, TEST_ADMIN_USER_ID)
        assertNotNull(organizationAccessControl)
        assertEquals(TEST_ADMIN_USER_ID, organizationAccessControl.id)
        assertEquals(ROLE_NONE, organizationAccessControl.role)
      }
    }

    @Test
    fun `addOrganizationAccessControl with owned organization`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))
        assertThrows<CsmResourceNotFoundException> {
          organizationApiService.getOrganizationAccessControl(
              organizationRegistered.id!!, OTHER_TEST_USER_ID)
        }
        val otherUserACL = OrganizationAccessControl(id = OTHER_TEST_USER_ID, role = ROLE_VIEWER)
        assertNotNull(
            organizationApiService.addOrganizationAccessControl(
                organizationRegistered.id!!, otherUserACL))

        val otherUserACLRetrieved =
            organizationApiService.getOrganizationAccessControl(
                organizationRegistered.id!!, OTHER_TEST_USER_ID)
        assertEquals(otherUserACL, otherUserACLRetrieved)
      }
    }

    @Test
    fun `addOrganizationAccessControl with owned organization (ROLE_NONE)`() {
      assertThrows<CsmClientException> {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))

        val otherUserACL = OrganizationAccessControl(id = OTHER_TEST_USER_ID, role = ROLE_NONE)
        organizationApiService.addOrganizationAccessControl(
            organizationRegistered.id!!, otherUserACL)
      }
    }

    @Test
    fun `addOrganizationAccessControl with not owned organization and PERMISSION_WRITE_SECURITY`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.registerOrganization(
                createTestOrganizationWithSimpleSecurity(
                    name, TEST_ADMIN_USER_ID, ROLE_NONE, ROLE_ADMIN))
        runAsPlatformAdmin()
        assertThrows<CsmResourceNotFoundException> {
          organizationApiService.getOrganizationAccessControl(
              organizationRegistered.id!!, TEST_USER_ID)
        }
        val otherUserACL = OrganizationAccessControl(id = TEST_USER_ID, role = ROLE_VIEWER)
        assertNotNull(
            organizationApiService.addOrganizationAccessControl(
                organizationRegistered.id!!, otherUserACL))

        val otherUserACLRetrieved =
            organizationApiService.getOrganizationAccessControl(
                organizationRegistered.id!!, TEST_USER_ID)
        assertEquals(otherUserACL, otherUserACLRetrieved)
      }
    }

    @Test
    fun `addOrganizationAccessControl with not owned organization and no PERMISSION_WRITE_SECURITY`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.registerOrganization(
                createTestOrganizationWithSimpleSecurity(
                    name, TEST_ADMIN_USER_ID, ROLE_NONE, ROLE_NONE))
        runAsPlatformAdmin()
        assertThrows<CsmResourceNotFoundException> {
          organizationApiService.getOrganizationAccessControl(
              organizationRegistered.id!!, TEST_USER_ID)
        }
        val otherUserACL = OrganizationAccessControl(id = TEST_USER_ID, role = ROLE_VIEWER)
        assertNotNull(
            organizationApiService.addOrganizationAccessControl(
                organizationRegistered.id!!, otherUserACL))

        val otherUserACLRetrieved =
            organizationApiService.getOrganizationAccessControl(
                organizationRegistered.id!!, TEST_USER_ID)
        assertEquals(otherUserACL, otherUserACLRetrieved)
      }
    }

    @Test
    fun `updateOrganizationAccessControl with owned organization cannot update last admin`() {
      assertThrows<CsmAccessForbiddenException> {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))
        organizationApiService.updateOrganizationAccessControl(
            organizationRegistered.id!!, TEST_ADMIN_USER_ID, OrganizationRole(role = ROLE_VIEWER))
      }
    }

    @Test
    fun `updateOrganizationAccessControl with owned organization can update user (!= ROLE_NONE)`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))

        organizationApiService.addOrganizationAccessControl(
            organizationRegistered.id!!,
            OrganizationAccessControl(id = OTHER_TEST_USER_ID, role = ROLE_VIEWER))

        assertNotNull(
            organizationApiService.updateOrganizationAccessControl(
                organizationRegistered.id!!,
                OTHER_TEST_USER_ID,
                OrganizationRole(role = ROLE_EDITOR)))

        val userACLRetrieved =
            organizationApiService.getOrganizationAccessControl(
                organizationRegistered.id!!, OTHER_TEST_USER_ID)
        assertNotEquals(ROLE_VIEWER, userACLRetrieved.role)
        assertEquals(ROLE_EDITOR, userACLRetrieved.role)
      }
    }

    @Test
    fun `updateOrganizationAccessControl with owned organization cannot update user (= ROLE_NONE)`() {
      assertThrows<CsmClientException> {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))

        organizationApiService.updateOrganizationAccessControl(
            organizationRegistered.id!!, TEST_USER_ID, OrganizationRole(role = ROLE_NONE))
      }
    }

    @Test
    fun `updateOrganizationAccessControl with owned organization and unknown ACL user`() {
      assertThrows<CsmResourceNotFoundException> {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))

        organizationApiService.updateOrganizationAccessControl(
            organizationRegistered.id!!, UNKNOWN_IDENTIFIER, OrganizationRole(role = ROLE_EDITOR))
      }
    }

    @Test
    fun `updateOrganizationAccessControl with owned organization and wrong role`() {
      assertThrows<CsmClientException> {
        val name = "o-connector-test-1"
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))

        organizationApiService.updateOrganizationAccessControl(
            organizationRegistered.id!!, TEST_USER_ID, OrganizationRole(role = UNKNOWN_IDENTIFIER))
      }
    }

    @Test
    fun `updateOrganizationAccessControl with not owned organization, WRITE_SECURITY permission, can update user (!= ROLE_NONE)`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))

        organizationApiService.addOrganizationAccessControl(
            organizationRegistered.id!!,
            OrganizationAccessControl(id = TEST_ADMIN_USER_ID, role = ROLE_ADMIN))

        runAsPlatformAdmin()

        assertNotNull(
            organizationApiService.updateOrganizationAccessControl(
                organizationRegistered.id!!,
                OTHER_TEST_USER_ID,
                OrganizationRole(role = ROLE_EDITOR)))

        val userACLRetrieved =
            organizationApiService.getOrganizationAccessControl(
                organizationRegistered.id!!, OTHER_TEST_USER_ID)
        assertEquals(ROLE_EDITOR, userACLRetrieved.role)
      }
    }

    @Test
    fun `updateOrganizationAccessControl with not owned organization, no WRITE_SECURITY permission`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))

        organizationApiService.addOrganizationAccessControl(
            organizationRegistered.id!!,
            OrganizationAccessControl(id = TEST_ADMIN_USER_ID, role = ROLE_VIEWER))

        runAsPlatformAdmin()

        assertNotNull(
            organizationApiService.updateOrganizationAccessControl(
                organizationRegistered.id!!,
                TEST_ADMIN_USER_ID,
                OrganizationRole(role = ROLE_EDITOR)))

        val userACLRetrieved =
            organizationApiService.getOrganizationAccessControl(
                organizationRegistered.id!!, TEST_ADMIN_USER_ID)
        assertEquals(ROLE_EDITOR, userACLRetrieved.role)
      }
    }

    @Test
    fun `updateOrganizationAccessControl with not owned organization, no WRITE_SECURITY permission, ROLE_NONE`() {
      assertThrows<CsmClientException> {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered =
            organizationApiService.registerOrganization(createTestOrganization(name))

        organizationApiService.addOrganizationAccessControl(
            organizationRegistered.id!!,
            OrganizationAccessControl(id = TEST_USER_ID, role = ROLE_VIEWER))

        runAsPlatformAdmin()

        organizationApiService.updateOrganizationAccessControl(
            organizationRegistered.id!!, OTHER_TEST_USER_ID, OrganizationRole(role = ROLE_NONE))
      }
    }

    @Test
    fun `removeOrganizationAccessControl with owned organization`() {
      val name = "o-connector-test-1"
      val organizationRegistered =
          organizationApiService.registerOrganization(createTestOrganization(name))

      val otherUserACL = OrganizationAccessControl(id = OTHER_TEST_USER_ID, role = ROLE_VIEWER)
      organizationApiService.addOrganizationAccessControl(organizationRegistered.id!!, otherUserACL)

      organizationApiService.removeOrganizationAccessControl(
          organizationRegistered.id!!, OTHER_TEST_USER_ID)
      assertThrows<CsmResourceNotFoundException> {
        organizationApiService.getOrganizationAccessControl(
            organizationRegistered.id!!, OTHER_TEST_USER_ID)
      }
    }

    @Test
    fun `removeOrganizationAccessControl with not owned organization, WRITE_SECURITY permission`() {
      val name = "o-connector-test-1"
      runAsDifferentOrganizationUser()
      val organizationRegistered =
          organizationApiService.registerOrganization(createTestOrganization(name))

      val otherUserACL = OrganizationAccessControl(id = TEST_USER_ID, role = ROLE_ADMIN)
      organizationApiService.addOrganizationAccessControl(organizationRegistered.id!!, otherUserACL)

      runAsPlatformAdmin()
      organizationApiService.removeOrganizationAccessControl(
          organizationRegistered.id!!, OTHER_TEST_USER_ID)
      assertThrows<CsmResourceNotFoundException> {
        organizationApiService.getOrganizationAccessControl(
            organizationRegistered.id!!, OTHER_TEST_USER_ID)
      }
    }

    @Test
    fun `removeOrganizationAccessControl with not owned organization, no WRITE_SECURITY permission`() {
      val name = "o-connector-test-1"
      runAsDifferentOrganizationUser()
      val organizationRegistered =
          organizationApiService.registerOrganization(createTestOrganization(name))

      val otherUserACL = OrganizationAccessControl(id = TEST_USER_ID, role = ROLE_ADMIN)
      organizationApiService.addOrganizationAccessControl(organizationRegistered.id!!, otherUserACL)

      runAsPlatformAdmin()
      organizationApiService.removeOrganizationAccessControl(
          organizationRegistered.id!!, OTHER_TEST_USER_ID)
      assertThrows<CsmResourceNotFoundException> {
        organizationApiService.getOrganizationAccessControl(
            organizationRegistered.id!!, OTHER_TEST_USER_ID)
      }
    }

    @Test
    fun `getOrganizationSecurityUsers with owned organization`() {
      val name = "o-connector-test-1"
      val organizationRegistered =
          organizationApiService.registerOrganization(createTestOrganization(name))

      val orgaUsers =
          organizationApiService.getOrganizationSecurityUsers(organizationRegistered.id!!)
      assertEquals(listOf(TEST_ADMIN_USER_ID), orgaUsers)
    }

    @Test
    fun `getOrganizationSecurityUsers with not owned organization, READ_SECURITY permission`() {
      val name = "o-connector-test-1"
      runAsDifferentOrganizationUser()
      val organizationRegistered =
          organizationApiService.registerOrganization(
              createTestOrganizationWithSimpleSecurity(name, TEST_USER_ID, ROLE_NONE, ROLE_VIEWER))
      runAsPlatformAdmin()
      val orgaUsers =
          organizationApiService.getOrganizationSecurityUsers(organizationRegistered.id!!)
      assertEquals(listOf(TEST_USER_ID), orgaUsers)
    }

    @Test
    fun `getOrganizationSecurityUsers with not owned organization, no READ_SECURITY permission`() {
      val name = "o-connector-test-1"
      runAsDifferentOrganizationUser()
      val organizationRegistered =
          organizationApiService.registerOrganization(
              createTestOrganizationWithSimpleSecurity(name, TEST_USER_ID, ROLE_NONE, ROLE_NONE))
      runAsPlatformAdmin()
      val orgaUsers =
          organizationApiService.getOrganizationSecurityUsers(organizationRegistered.id!!)
      assertEquals(listOf(TEST_USER_ID), orgaUsers)
    }

    @Test
    fun `importOrganization organization with minimal properties`() {
      val id = "minimal-required-properties"
      organizationApiService.importOrganization(Organization(id = id))
      assertNotNull(organizationApiService.findOrganizationById(id))
    }

    @Test
    fun `importOrganization organization without id`() {
      val name = "o-connector-test-1"
      assertThrows<CsmResourceNotFoundException> {
        organizationApiService.importOrganization(createTestOrganization(name))
      }
    }

    @Test
    fun `updateOrganization owned security from importOrganization with no security`() {
      val myOrganizationid = "o-connector-test-1"
      val organizationRegistered =
          organizationApiService.importOrganization(Organization(id = myOrganizationid))
      assertNull(organizationRegistered.security)

      val newOrganizationSecurity =
          OrganizationSecurity(
              default = ROLE_NONE,
              accessControlList =
                  mutableListOf(OrganizationAccessControl(id = TEST_USER_ID, role = ROLE_VIEWER)))
      organizationRegistered.security = newOrganizationSecurity
      val updatedOrganization =
          organizationApiService.updateOrganization(
              organizationRegistered.id!!, organizationRegistered)

      assertNotNull(updatedOrganization.security)
      assertEquals(newOrganizationSecurity, updatedOrganization.security)
    }
  }

  private fun testFindAllWithRBAC(
      numberOfOrganizationCreated: Int,
      numberOfOrganizationReachableByTestUser: Int
  ) {
    val defaultPageSize = csmPlatformProperties.twincache.organization.defaultPageSize

    testFindAllOrganizations(null, null, numberOfOrganizationReachableByTestUser)
    testFindAllOrganizations(0, null, defaultPageSize)
    testFindAllOrganizations(
        0, numberOfOrganizationCreated, numberOfOrganizationReachableByTestUser)
    testFindAllOrganizations(1, 200, 0)
    testFindAllOrganizations(1, 15, 15)
  }

  @Nested
  inner class RBACTest {

    @TestFactory
    fun `test RBAC findAllOrganizations`() =
        mapOf(
                ROLE_VIEWER to false,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to false,
                ROLE_USER to false,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              dynamicTest("Test RBAC findAllOrganizations : $role") {
                every { getCurrentAccountIdentifier(any()) } returns defaultName
                organizationApiService.registerOrganization(
                    mockOrganization("id", defaultName, role))
              }
            }

    @TestFactory
    fun `test RBAC registerOrganization`() =
        mapOf(
                ROLE_VIEWER to false,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to false,
                ROLE_USER to false,
                ROLE_NONE to false,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              dynamicTest("Test RBAC registerOrganization : $role") {
                every { getCurrentAccountIdentifier(any()) } returns defaultName

                assertDoesNotThrow {
                  organizationApiService.registerOrganization(mockOrganization("id", "name"))
                }
              }
            }

    @TestFactory
    fun `test RBAC findOrganizationById`() =
        mapOf(
                ROLE_VIEWER to false,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to true,
                ROLE_USER to false,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              dynamicTest("Test RBAC findOrganizationById : $role") {
                every { getCurrentAccountIdentifier(any()) } returns defaultName
                val organization =
                    organizationApiService.registerOrganization(
                        mockOrganization("id", defaultName, role))

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    organizationApiService.findOrganizationById(organization.id!!)
                  }
                } else {
                  assertDoesNotThrow {
                    organizationApiService.findOrganizationById(organization.id!!)
                  }
                }
              }
            }

    @TestFactory
    fun `test RBAC unregisterOrganization`() =
        mapOf(
                ROLE_VIEWER to true,
                ROLE_EDITOR to true,
                ROLE_VALIDATOR to true,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              dynamicTest("Test RBAC unregisterOrganization : $role") {
                every { getCurrentAccountIdentifier(any()) } returns defaultName
                val organization =
                    organizationApiService.registerOrganization(
                        mockOrganization("id", defaultName, role))

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    organizationApiService.unregisterOrganization(organization.id!!)
                  }
                } else {
                  assertDoesNotThrow {
                    organizationApiService.unregisterOrganization(organization.id!!)
                  }
                }
              }
            }

    @TestFactory
    fun `test RBAC updateOrganization`() =
        mapOf(
                ROLE_VIEWER to true,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to true,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              dynamicTest("Test RBAC updateOrganization : $role") {
                every { getCurrentAccountIdentifier(any()) } returns defaultName
                val organization =
                    organizationApiService.registerOrganization(mockOrganization("id", role = role))

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    organizationApiService.updateOrganization(
                        organization.id!!, mockOrganization("id", "name"))
                  }
                } else {
                  assertDoesNotThrow {
                    organizationApiService.updateOrganization(
                        organization.id!!, mockOrganization("id", "name"))
                  }
                }
              }
            }

    @TestFactory
    fun `test RBAC updateTenantCredentialsByOrganizationId`() =
        mapOf(
                ROLE_VIEWER to true,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to true,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              dynamicTest("Test RBAC updateTenantCredentialsByOrganizationId : $role") {
                every { getCurrentAccountIdentifier(any()) } returns defaultName
                val organization =
                    organizationApiService.registerOrganization(
                        mockOrganization("id", defaultName, role))

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    organizationApiService.updateTenantCredentialsByOrganizationId(
                        organization.id!!, mapOf())
                  }
                } else {
                  assertDoesNotThrow {
                    organizationApiService.updateTenantCredentialsByOrganizationId(
                        organization.id!!, mapOf())
                  }
                }
              }
            }

    @TestFactory
    fun `test RBAC updateStorageByOrganizationId`() =
        mapOf(
                ROLE_VIEWER to true,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to true,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              dynamicTest("Test RBAC updateStorageByOrganizationId : $role") {
                every { getCurrentAccountIdentifier(any()) } returns defaultName
                val organization =
                    organizationApiService.registerOrganization(
                        mockOrganization("id", defaultName, role))

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    organizationApiService.updateStorageByOrganizationId(
                        organization.id!!, OrganizationService())
                  }
                } else {
                  assertDoesNotThrow {
                    organizationApiService.updateStorageByOrganizationId(
                        organization.id!!, OrganizationService())
                  }
                }
              }
            }

    @TestFactory
    fun `test RBAC updateSolutionsContainerRegistryByOrganizationId`() =
        mapOf(
                ROLE_VIEWER to true,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to true,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              dynamicTest("Test RBAC updateSolutionsContainerRegistryByOrganizationId : $role") {
                every { getCurrentAccountIdentifier(any()) } returns defaultName
                val organization =
                    organizationApiService.registerOrganization(
                        mockOrganization("id", defaultName, role))

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    organizationApiService.updateSolutionsContainerRegistryByOrganizationId(
                        organization.id!!, OrganizationService())
                  }
                } else {
                  assertDoesNotThrow {
                    organizationApiService.updateSolutionsContainerRegistryByOrganizationId(
                        organization.id!!, OrganizationService())
                  }
                }
              }
            }

    @TestFactory
    fun `test RBAC getAllPermissions`() =
        mapOf(
                ROLE_VIEWER to false,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to false,
                ROLE_USER to false,
                ROLE_NONE to false,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              dynamicTest("Test RBAC getAllPermissions : $role") {
                every { getCurrentAccountIdentifier(any()) } returns defaultName

                assertDoesNotThrow { organizationApiService.getAllPermissions() }
              }
            }

    @TestFactory
    fun `test RBAC getOrganizationPermissions`() =
        mapOf(
                ROLE_VIEWER to false,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to true,
                ROLE_USER to false,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              dynamicTest("Test RBAC getOrganizationPermissions : $role") {
                every { getCurrentAccountIdentifier(any()) } returns defaultName
                val organization =
                    organizationApiService.registerOrganization(
                        mockOrganization("id", defaultName, role))

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    organizationApiService.getOrganizationPermissions(organization.id!!, role)
                  }
                } else
                    assertDoesNotThrow {
                      organizationApiService.getOrganizationPermissions(organization.id!!, role)
                    }
              }
            }

    @TestFactory
    fun `test RBAC getOrganizationSecurity`() =
        mapOf(
                ROLE_VIEWER to false,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to true,
                ROLE_USER to false,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              dynamicTest("Test RBAC getOrganizationSecurity : $role") {
                every { getCurrentAccountIdentifier(any()) } returns defaultName
                val organization =
                    organizationApiService.registerOrganization(
                        mockOrganization("id", defaultName, role))

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    organizationApiService.getOrganizationSecurity(organization.id!!)
                  }
                } else {
                  assertDoesNotThrow {
                    organizationApiService.getOrganizationSecurity(organization.id!!)
                  }
                }
              }
            }

    @TestFactory
    fun `test RBAC setOrganizationDefaultSecurity`() =
        mapOf(
                ROLE_VIEWER to true,
                ROLE_EDITOR to true,
                ROLE_VALIDATOR to true,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              dynamicTest("Test RBAC setOrganizationDefaultSecurity : $role") {
                every { getCurrentAccountIdentifier(any()) } returns defaultName
                val organization =
                    organizationApiService.registerOrganization(
                        mockOrganization("id", defaultName, role))

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    organizationApiService.setOrganizationDefaultSecurity(
                        organization.id!!, OrganizationRole(role))
                  }
                } else {
                  assertDoesNotThrow {
                    organizationApiService.setOrganizationDefaultSecurity(
                        organization.id!!, OrganizationRole(role))
                  }
                }
              }
            }

    @TestFactory
    fun `test RBAC addOrganizationAccessControl`() =
        mapOf(
                ROLE_VIEWER to true,
                ROLE_EDITOR to true,
                ROLE_VALIDATOR to true,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              dynamicTest("Test RBAC addOrganizationAccessControl : $role") {
                every { getCurrentAccountIdentifier(any()) } returns defaultName
                val organization =
                    organizationApiService.registerOrganization(
                        mockOrganization("id", defaultName, role))

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    organizationApiService.addOrganizationAccessControl(
                        organization.id!!, OrganizationAccessControl("id", role))
                  }
                } else {
                  assertDoesNotThrow {
                    organizationApiService.addOrganizationAccessControl(
                        organization.id!!, OrganizationAccessControl("id", role))
                  }
                }
              }
            }

    @TestFactory
    fun `test RBAC getOrganizationAccessControl`() =
        mapOf(
                ROLE_VIEWER to false,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to true,
                ROLE_USER to false,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              dynamicTest("Test RBAC getOrganizationAccessControl : $role") {
                every { getCurrentAccountIdentifier(any()) } returns defaultName
                val organization =
                    organizationApiService.registerOrganization(
                        mockOrganization("id", defaultName, role))

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    organizationApiService.getOrganizationAccessControl(
                        organization.id!!, defaultName)
                  }
                } else {
                  assertDoesNotThrow {
                    organizationApiService.getOrganizationAccessControl(
                        organization.id!!, defaultName)
                  }
                }
              }
            }

    @TestFactory
    fun `test RBAC removeOrganizationAccessControl`() =
        mapOf(
                ROLE_VIEWER to true,
                ROLE_EDITOR to true,
                ROLE_VALIDATOR to true,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              dynamicTest("Test RBAC removeOrganizationAccessControl : $role") {
                every { getCurrentAccountIdentifier(any()) } returns defaultName
                val organization =
                    organizationApiService.registerOrganization(
                        mockOrganization("id", defaultName, role))

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    organizationApiService.removeOrganizationAccessControl(
                        organization.id!!, defaultName)
                  }
                } else {
                  organizationApiService.addOrganizationAccessControl(
                      organization.id!!, OrganizationAccessControl("id", ROLE_ADMIN))
                  assertDoesNotThrow {
                    organizationApiService.removeOrganizationAccessControl(
                        organization.id!!, defaultName)
                  }
                }
              }
            }

    @TestFactory
    fun `test RBAC updateOrganizationAccessControl`() =
        mapOf(
                ROLE_VIEWER to true,
                ROLE_EDITOR to true,
                ROLE_VALIDATOR to true,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              dynamicTest("Test RBAC updateOrganizationAccessControl : $role") {
                every { getCurrentAccountIdentifier(any()) } returns defaultName
                val organization =
                    organizationApiService.registerOrganization(
                        mockOrganization("id", defaultName, role))

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    organizationApiService.updateOrganizationAccessControl(
                        organization.id!!, defaultName, OrganizationRole(role))
                  }
                } else {
                  assertDoesNotThrow {
                    organizationApiService.updateOrganizationAccessControl(
                        organization.id!!, defaultName, OrganizationRole(role))
                  }
                }
              }
            }

    @TestFactory
    fun `test RBAC getOrganizationSecurityUsers`() =
        mapOf(
                ROLE_VIEWER to false,
                ROLE_EDITOR to false,
                ROLE_VALIDATOR to true,
                ROLE_USER to false,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              dynamicTest("Test RBAC getOrganizationSecurityUsers : $role") {
                every { getCurrentAccountIdentifier(any()) } returns defaultName
                val organization =
                    organizationApiService.registerOrganization(
                        mockOrganization("id", defaultName, role))

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    organizationApiService.getOrganizationSecurityUsers(organization.id!!)
                  }
                } else {
                  assertDoesNotThrow {
                    organizationApiService.getOrganizationSecurityUsers(organization.id!!)
                  }
                }
              }
            }

    @TestFactory
    fun `test RBAC importOrganization`() =
        mapOf(
                ROLE_VIEWER to true,
                ROLE_EDITOR to true,
                ROLE_VALIDATOR to true,
                ROLE_USER to true,
                ROLE_NONE to true,
                ROLE_ADMIN to false,
            )
            .map { (role, shouldThrow) ->
              dynamicTest("Test RBAC importOrganization : $role") {
                runAsPlatformAdmin()
                every { getCurrentAccountIdentifier(any()) } returns defaultName
                val organization =
                    organizationApiService.registerOrganization(
                        mockOrganization("id", defaultName, role))

                assertDoesNotThrow {
                  organizationApiService.importOrganization(mockOrganization("id", "name"))
                }
              }
            }
  }

  private fun testFindAllOrganizationsWithWrongValues() {
    logger.info("Should throw IllegalArgumentException when page and size are zeros")
    assertThrows<IllegalArgumentException> { organizationApiService.findAllOrganizations(0, 0) }

    logger.info("Should throw IllegalArgumentException when page is negative")
    assertThrows<IllegalArgumentException> { organizationApiService.findAllOrganizations(-1, 10) }

    logger.info("Should throw IllegalArgumentException when size is negative")
    assertThrows<IllegalArgumentException> { organizationApiService.findAllOrganizations(0, -1) }
  }

  private fun testFindOrganizationByIdAsNotOwner(
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
    val organizationRegistered = organizationApiService.registerOrganization(organization)

    runFindOrganizationByIdAs()
    if (throwException) {
      assertThrows<CsmAccessForbiddenException> {
        (organizationApiService.findOrganizationById(organizationRegistered.id!!))
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
      val newOrganization = createTestOrganization("o-connector-test-$it")
      organizationApiService.registerOrganization(newOrganization)
    }
  }

  /** Organization batch creation with RBAC */
  internal fun batchOrganizationCreationWithRBAC(userId: String): Int {
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
        organizationApiService.registerOrganization(organization)
        numberOfOrganizationCreated++
      }
    }
    return numberOfOrganizationCreated
  }

  /** Create default test Connector */
  internal fun createTestOrganization(name: String): Organization {
    return Organization(name = name)
  }

  /** Create default test Connector */
  internal fun createTestOrganizationWithSimpleSecurity(
      name: String,
      userId: String,
      defaultSecurity: String,
      role: String,
  ): Organization {
    return Organization(
        id = "organization_id",
        name = name,
        security =
            OrganizationSecurity(
                default = defaultSecurity,
                accessControlList = mutableListOf(OrganizationAccessControl(userId, role))))
  }

  internal fun testFindAllOrganizations(page: Int?, size: Int?, expectedResultSize: Int) {
    val organizationList = organizationApiService.findAllOrganizations(page, size)
    logger.info("Organization list retrieved contains : ${organizationList.size} elements")
    assertEquals(expectedResultSize, organizationList.size)
  }

  fun mockOrganization(
      id: String,
      roleName: String = defaultName,
      role: String = ROLE_ADMIN
  ): Organization {
    return Organization(
        id = UUID.randomUUID().toString(),
        name = "Organization Name",
        ownerId = "my.account-tester@cosmotech.com",
        security =
            OrganizationSecurity(
                default = ROLE_NONE,
                accessControlList =
                    mutableListOf(
                        OrganizationAccessControl(id = roleName, role = role),
                        OrganizationAccessControl("userLambda", "viewer"))))
  }
}
