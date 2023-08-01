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
import org.junit.Assert.assertNotEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.runner.RunWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.junit4.SpringRunner
import java.util.*
import kotlin.test.assertTrue

@ActiveProfiles(profiles = ["organization-test"])
@ExtendWith(MockKExtension::class)
@ExtendWith(SpringExtension::class)
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrganizationServiceIntegrationTest : CsmRedisTestBase() {

  private val logger = LoggerFactory.getLogger(OrganizationServiceIntegrationTest::class.java)

  private val OTHER_TEST_USER_ID = "test.other.user@cosmotech.com"
  private val TEST_USER_ID = "test.user@cosmotech.com"
  private val TEST_ADMIN_USER_ID = "test.admin@cosmotech.com"

  @Autowired lateinit var rediSearchIndexer: RediSearchIndexer
  @Autowired lateinit var organizationApiService: OrganizationApiService
  @Autowired lateinit var csmPlatformProperties: CsmPlatformProperties

  @BeforeAll
  fun globalSetup() {
    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
  }

  @BeforeEach
  fun setUp() {
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
      testFindOrganizationByIdAsNotOwner(false, null, null, null, true){
        runAsOrganizationUser()
      }
    }

    @Test
    fun `findOrganizationById as not owner but with READ role`() {
      testFindOrganizationByIdAsNotOwner(true, TEST_USER_ID, ROLE_NONE, ROLE_USER, false){
        runAsOrganizationUser()
      }
    }

    @Test
    fun `findOrganizationById as not owner but with WRITE role`() {
      testFindOrganizationByIdAsNotOwner(true, TEST_USER_ID, ROLE_NONE, ROLE_EDITOR, false){
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
        val organizationRegistered = organizationApiService.registerOrganization(organizationToRegister)
        assertEquals(
          OrganizationSecurity(
            default = ROLE_NONE,
            mutableListOf(OrganizationAccessControl(id=TEST_USER_ID, role=ROLE_ADMIN))
          ),
          organizationRegistered.security
        )
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
    fun `registerOrganization with security values`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationToRegister = createTestOrganizationWithSimpleSecurity(name,OTHER_TEST_USER_ID, ROLE_USER,
          ROLE_NONE)
        val organizationRegistered = organizationApiService.registerOrganization(organizationToRegister)
        assertEquals(
          OrganizationSecurity(
            default = ROLE_USER,
            mutableListOf(OrganizationAccessControl(id=OTHER_TEST_USER_ID, role=ROLE_NONE))
          ),
          organizationRegistered.security
        )
        assertEquals(name, organizationRegistered.name)
        assertTrue(organizationRegistered.id!!.startsWith("o-"))
      }
    }

    @Test
    fun `unregisterOrganization owned organization `() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationToRegister = createTestOrganization(name)
        val organizationRegistered = organizationApiService.registerOrganization(organizationToRegister)
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
        val organizationToRegister = createTestOrganizationWithSimpleSecurity(name,OTHER_TEST_USER_ID, ROLE_USER,
          ROLE_NONE)
        val organizationRegistered = organizationApiService.registerOrganization(organizationToRegister)
        organizationApiService.unregisterOrganization(organizationRegistered.id!!)
      }
    }

    @Test
    fun `unregisterOrganization not owned but DELETE permission `() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationToRegister = createTestOrganizationWithSimpleSecurity(name,TEST_USER_ID, ROLE_NONE,
          ROLE_ADMIN)
        val organizationRegistered = organizationApiService.registerOrganization(organizationToRegister)
        runAsOrganizationUser()
        organizationApiService.unregisterOrganization(organizationRegistered.id!!)
      }
    }

    @Test
    fun `updateOrganization owned organization name and services`(){
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered = organizationApiService.registerOrganization(createTestOrganization(name))

        organizationRegistered.name = "my-new-name"
        organizationRegistered.services = OrganizationServices(
          tenantCredentials = mutableMapOf(),
          storage =
          OrganizationService(
            cloudService = "cloud",
            baseUri = "base",
            platformService = "platform",
            resourceUri = "resource",
            credentials = mutableMapOf(Pair("credential1", 10.0))
          ),
          solutionsContainerRegistry =
          OrganizationService(
            cloudService = "cloud",
            baseUri = "base",
            platformService = "platform",
            resourceUri = "resource",
            credentials = mutableMapOf(Pair("credential2", "toto"))
          )
        )
        organizationApiService.updateOrganization(organizationRegistered.id!!,organizationRegistered)

        assertEquals(organizationRegistered, organizationApiService.findOrganizationById(organizationRegistered.id!!))
      }
    }

    @Test
    fun `updateOrganization owned organization security`(){
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered = organizationApiService.registerOrganization(createTestOrganization(name))

        organizationRegistered.security = OrganizationSecurity(
          default = ROLE_NONE,
          mutableListOf(OrganizationAccessControl(id=OTHER_TEST_USER_ID, role = ROLE_USER))
        )

        organizationApiService.updateOrganization(organizationRegistered.id!!,organizationRegistered)

        // Organization Security cannot be updated via updateOrganization endpoint
        // setOrganizationDefaultSecurity or
        // addOrganizationAccessControl/updateOrganizationAccessControl/removeOrganizationAccessControl
        // Should be used instead
        assertNotEquals(organizationRegistered, organizationApiService.findOrganizationById(organizationRegistered.id!!))
      }
    }

    @Test
    fun `updateOrganization not owned organization with WRITE permission`(){
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered = organizationApiService.registerOrganization(
          createTestOrganizationWithSimpleSecurity(name, TEST_USER_ID, ROLE_NONE, ROLE_EDITOR)
        )

        runAsOrganizationUser()

        organizationRegistered.name = "my-new-name"
        organizationRegistered.services = OrganizationServices(
          tenantCredentials = mutableMapOf(),
          storage =
          OrganizationService(
            cloudService = "cloud",
            baseUri = "base",
            platformService = "platform",
            resourceUri = "resource",
            credentials = mutableMapOf(Pair("credential1", 10.0))
          ),
          solutionsContainerRegistry =
          OrganizationService(
            cloudService = "cloud",
            baseUri = "base",
            platformService = "platform",
            resourceUri = "resource",
            credentials = mutableMapOf(Pair("credential2", "toto"))
          )
        )

        organizationApiService.updateOrganization(organizationRegistered.id!!,organizationRegistered)

        assertEquals(organizationRegistered, organizationApiService.findOrganizationById(organizationRegistered.id!!))
      }
    }

    @Test
    fun `updateOrganization not owned organization with no WRITE permission`(){
      assertThrows<CsmAccessForbiddenException> {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered = organizationApiService.registerOrganization(createTestOrganization(name))
        runAsOrganizationUser()
        organizationApiService.updateOrganization(organizationRegistered.id!!,organizationRegistered)
      }
    }

    @Test
    fun `updateSolutionsContainerRegistryByOrganizationId with owned organization`(){
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered = organizationApiService.registerOrganization(createTestOrganization(name))

        val newSolutionsContainerRegistry = OrganizationService(
          cloudService = "cloud",
          baseUri = "base",
          platformService = "platform",
          resourceUri = "resource",
          credentials = mutableMapOf(Pair("cred2", "test2")))

        organizationApiService.updateSolutionsContainerRegistryByOrganizationId(organizationRegistered.id!!,newSolutionsContainerRegistry)

        assertNotEquals(newSolutionsContainerRegistry, organizationApiService.findOrganizationById(organizationRegistered.id!!).services!!.solutionsContainerRegistry)
      }
    }

    @Test
    fun `updateSolutionsContainerRegistryByOrganizationId with not owned organization and WRITE permission`(){
      assertDoesNotThrow {
        runAsDifferentOrganizationUser()
        val name = "o-connector-test-1"
        val organizationRegistered = organizationApiService.registerOrganization(createTestOrganizationWithSimpleSecurity(name, TEST_USER_ID,
          ROLE_NONE,
          ROLE_EDITOR))

        runAsOrganizationUser()
        val newSolutionsContainerRegistry = OrganizationService(
          cloudService = "cloud",
          baseUri = "base",
          platformService = "platform",
          resourceUri = "resource",
          credentials = mutableMapOf(Pair("cred2", "test2")))

        organizationApiService.updateSolutionsContainerRegistryByOrganizationId(organizationRegistered.id!!,newSolutionsContainerRegistry)

        assertNotEquals(newSolutionsContainerRegistry, organizationApiService.findOrganizationById(organizationRegistered.id!!).services!!.solutionsContainerRegistry)
      }
    }

    @Test
    fun `updateSolutionsContainerRegistryByOrganizationId with not owned organization and READ permission`(){
      assertThrows<CsmAccessForbiddenException> {
        runAsDifferentOrganizationUser()
        val name = "o-connector-test-1"
        val organizationRegistered = organizationApiService.registerOrganization(createTestOrganizationWithSimpleSecurity(name, TEST_USER_ID,
          ROLE_NONE,
          ROLE_USER))

        runAsOrganizationUser()
        val newSolutionsContainerRegistry = OrganizationService(
          cloudService = "cloud",
          baseUri = "base",
          platformService = "platform",
          resourceUri = "resource",
          credentials = mutableMapOf(Pair("cred2", "test2")))

        organizationApiService.updateSolutionsContainerRegistryByOrganizationId(organizationRegistered.id!!,newSolutionsContainerRegistry)

      }
    }


    @Test
    fun `updateStorageByOrganizationId with owned organization`(){
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered = organizationApiService.registerOrganization(createTestOrganization(name))

        val newStorage = OrganizationService(
          cloudService = "cloud",
          baseUri = "base",
          platformService = "platform",
          resourceUri = "resource",
          credentials = mutableMapOf(Pair("cred2", "test2")))

        organizationApiService.updateStorageByOrganizationId(organizationRegistered.id!!,newStorage)

        assertNotEquals(newStorage, organizationApiService.findOrganizationById(organizationRegistered.id!!).services!!.storage)
      }
    }

    @Test
    fun `updateStorageByOrganizationId with not owned organization and WRITE permission`(){
      assertDoesNotThrow {
        runAsDifferentOrganizationUser()
        val name = "o-connector-test-1"
        val organizationRegistered = organizationApiService.registerOrganization(createTestOrganizationWithSimpleSecurity(name, TEST_USER_ID,
          ROLE_NONE,
          ROLE_EDITOR))

        runAsOrganizationUser()
        val newStorage = OrganizationService(
          cloudService = "cloud",
          baseUri = "base",
          platformService = "platform",
          resourceUri = "resource",
          credentials = mutableMapOf(Pair("cred2", "test2")))

        organizationApiService.updateStorageByOrganizationId(organizationRegistered.id!!,newStorage)

        assertNotEquals(newStorage, organizationApiService.findOrganizationById(organizationRegistered.id!!).services!!.storage)
      }
    }

    @Test
    fun `updateStorageByOrganizationId with not owned organization and READ permission`(){
      assertThrows<CsmAccessForbiddenException> {
        runAsDifferentOrganizationUser()
        val name = "o-connector-test-1"
        val organizationRegistered = organizationApiService.registerOrganization(createTestOrganizationWithSimpleSecurity(name, TEST_USER_ID,
          ROLE_NONE,
          ROLE_USER))

        runAsOrganizationUser()
        val newStorage = OrganizationService(
          cloudService = "cloud",
          baseUri = "base",
          platformService = "platform",
          resourceUri = "resource",
          credentials = mutableMapOf(Pair("cred2", "test2")))

        organizationApiService.updateStorageByOrganizationId(organizationRegistered.id!!,newStorage)

      }
    }

    @Test
    fun `updateTenantCredentialsByOrganizationId with owned organization and empty body`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered = organizationApiService.registerOrganization(createTestOrganization(name))
        val updateResult =
          organizationApiService.updateTenantCredentialsByOrganizationId(organizationRegistered.id!!, mapOf())
        assertTrue(updateResult.isEmpty())
      }
    }

    @Test
    fun `updateTenantCredentialsByOrganizationId with owned organization and correct body`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered = organizationApiService.registerOrganization(createTestOrganization(name))
        val credentials = mutableMapOf(Pair("cred2", "test2"))
        val updateResult =
          organizationApiService.updateTenantCredentialsByOrganizationId(organizationRegistered.id!!, credentials)
        assertEquals(credentials, updateResult)
      }
    }

    @Test
    fun `updateTenantCredentialsByOrganizationId with not owned organization, empty body and no WRITE permission`() {
      assertThrows<CsmAccessForbiddenException> {
        runAsDifferentOrganizationUser()
        val name = "o-connector-test-1"
        val organizationRegistered = organizationApiService.registerOrganization(createTestOrganization(name))
        runAsOrganizationUser()
        organizationApiService.updateTenantCredentialsByOrganizationId(organizationRegistered.id!!, mapOf())
      }
    }

    @Test
    fun `updateTenantCredentialsByOrganizationId with not owned organization, empty body and WRITE permission`() {
      assertDoesNotThrow {
        runAsDifferentOrganizationUser()
        val name = "o-connector-test-1"
        val organizationRegistered = organizationApiService.registerOrganization(
          createTestOrganizationWithSimpleSecurity(name,TEST_USER_ID, ROLE_NONE, ROLE_EDITOR))
        val credentials = mutableMapOf(Pair("cred2", "test2"))
        runAsOrganizationUser()
        val updateResult =
          organizationApiService.updateTenantCredentialsByOrganizationId(organizationRegistered.id!!, credentials)
        assertEquals(credentials, updateResult)
      }
    }

    @Test
    fun getAllPermissions() {
      val mapAllPermissions = listOf(ComponentRolePermissions(
        component = "organization",
        roles = mutableMapOf(
          ROLE_VIEWER to mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY),
          ROLE_USER to mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_CREATE_CHILDREN),
          ROLE_EDITOR to mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_CREATE_CHILDREN, PERMISSION_WRITE),
          ROLE_ADMIN to mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_CREATE_CHILDREN, PERMISSION_WRITE, PERMISSION_WRITE_SECURITY, PERMISSION_DELETE),
        )
      ),
        ComponentRolePermissions(
          component = "workspace",
          roles = mutableMapOf(
            ROLE_VIEWER to mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY),
            ROLE_USER to mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_CREATE_CHILDREN),
            ROLE_EDITOR to mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_CREATE_CHILDREN, PERMISSION_WRITE),
            ROLE_ADMIN to mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_CREATE_CHILDREN, PERMISSION_WRITE, PERMISSION_WRITE_SECURITY, PERMISSION_DELETE),
          )
        ),
        ComponentRolePermissions(
          component = "scenario",
          roles = mutableMapOf(
            ROLE_VIEWER to mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY),
            ROLE_EDITOR to mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_LAUNCH, PERMISSION_WRITE),
            ROLE_VALIDATOR to mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_LAUNCH, PERMISSION_WRITE, PERMISSION_VALIDATE),
            ROLE_ADMIN to mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_LAUNCH, PERMISSION_WRITE, PERMISSION_VALIDATE, PERMISSION_WRITE_SECURITY, PERMISSION_DELETE),
          )
        )
      )
      assertEquals(mapAllPermissions, organizationApiService.getAllPermissions())
    }

    @Test
    fun `getOrganizationPermissions with owned organization`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered = organizationApiService.registerOrganization(createTestOrganization(name))
        var organizationUserPermissions =
          organizationApiService.getOrganizationPermissions(organizationRegistered.id!!, ROLE_VIEWER)
        assertEquals(mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY), organizationUserPermissions)
        organizationUserPermissions =
          organizationApiService.getOrganizationPermissions(organizationRegistered.id!!, ROLE_USER)
        assertEquals(mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_CREATE_CHILDREN), organizationUserPermissions)
        organizationUserPermissions =
          organizationApiService.getOrganizationPermissions(organizationRegistered.id!!, ROLE_EDITOR)
        assertEquals(mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_CREATE_CHILDREN, PERMISSION_WRITE), organizationUserPermissions)
        organizationUserPermissions =
          organizationApiService.getOrganizationPermissions(organizationRegistered.id!!, ROLE_ADMIN)
        assertEquals(mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_CREATE_CHILDREN, PERMISSION_WRITE, PERMISSION_WRITE_SECURITY, PERMISSION_DELETE), organizationUserPermissions)
        organizationUserPermissions =
          organizationApiService.getOrganizationPermissions(organizationRegistered.id!!, "UNKNOWN")
        assertEquals(emptyList(), organizationUserPermissions)
      }
    }

    @Test
    fun `getOrganizationPermissions with not owned organization and READ_SECURITY permission`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered = organizationApiService.registerOrganization(
          createTestOrganizationWithSimpleSecurity(name,TEST_USER_ID,ROLE_NONE, ROLE_VIEWER)
        )
        runAsOrganizationUser()
        var organizationUserPermissions =
          organizationApiService.getOrganizationPermissions(organizationRegistered.id!!, ROLE_VIEWER)
        assertEquals(mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY), organizationUserPermissions)
        organizationUserPermissions =
          organizationApiService.getOrganizationPermissions(organizationRegistered.id!!, ROLE_USER)
        assertEquals(mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_CREATE_CHILDREN), organizationUserPermissions)
        organizationUserPermissions =
          organizationApiService.getOrganizationPermissions(organizationRegistered.id!!, ROLE_EDITOR)
        assertEquals(mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_CREATE_CHILDREN, PERMISSION_WRITE), organizationUserPermissions)
        organizationUserPermissions =
          organizationApiService.getOrganizationPermissions(organizationRegistered.id!!, ROLE_ADMIN)
        assertEquals(mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_CREATE_CHILDREN, PERMISSION_WRITE, PERMISSION_WRITE_SECURITY, PERMISSION_DELETE), organizationUserPermissions)
        organizationUserPermissions =
          organizationApiService.getOrganizationPermissions(organizationRegistered.id!!, "UNKNOWN")
        assertEquals(emptyList(), organizationUserPermissions)
      }
    }

    @Test
    fun `getOrganizationPermissions with not owned organization and no permission`() {
      assertThrows<CsmAccessForbiddenException> {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered = organizationApiService.registerOrganization(createTestOrganization(name))
        runAsOrganizationUser()
        organizationApiService.getOrganizationPermissions(organizationRegistered.id!!, ROLE_VIEWER)
      }
    }

    @Test
    fun `getOrganizationSecurity with owned organization`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered = organizationApiService.registerOrganization(createTestOrganization(name))
        assertNotNull(organizationApiService.getOrganizationSecurity(organizationRegistered.id!!))
      }
    }

    @Test
    fun `getOrganizationSecurity with not owned organization with READ_SECURITY permission`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered = organizationApiService.registerOrganization(
          createTestOrganizationWithSimpleSecurity(name, TEST_USER_ID, ROLE_NONE, ROLE_VIEWER)
        )
        runAsOrganizationUser()
        assertNotNull(organizationApiService.getOrganizationSecurity(organizationRegistered.id!!))
      }
    }

    @Test
    fun `getOrganizationSecurity with not owned organization with no READ_SECURITY permission`() {
      assertThrows<CsmAccessForbiddenException> {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered = organizationApiService.registerOrganization(
          createTestOrganizationWithSimpleSecurity(name, TEST_USER_ID, ROLE_NONE, ROLE_NONE)
        )
        runAsOrganizationUser()
        organizationApiService.getOrganizationSecurity(organizationRegistered.id!!)
      }
    }

    @Test
    fun `setOrganizationDefaultSecurity with owned organization and existing role`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered = organizationApiService.registerOrganization(createTestOrganization(name))
        val defaultRoleCreated = organizationRegistered.security?.default
        assertNotNull(organizationApiService.setOrganizationDefaultSecurity(organizationRegistered.id!!, OrganizationRole(ROLE_ADMIN)))
        val defaultRoleUpdated = organizationApiService.getOrganizationSecurity(organizationRegistered.id!!).default
        assertNotEquals(defaultRoleCreated,defaultRoleUpdated)
      }
    }

    @Test
    fun `setOrganizationDefaultSecurity with owned organization and non-existing role`() {
      assertThrows<CsmClientException> {
        val name = "o-connector-test-1"
        val organizationRegistered = organizationApiService.registerOrganization(createTestOrganization(name))
        organizationApiService.setOrganizationDefaultSecurity(organizationRegistered.id!!, OrganizationRole("UNKNOWN"))
      }
    }

    @Test
    fun `setOrganizationDefaultSecurity with not owned organization and WRITE_SECURITY_PERMISSION`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered = organizationApiService.registerOrganization(
          createTestOrganizationWithSimpleSecurity(name, TEST_USER_ID, ROLE_NONE, ROLE_ADMIN))
        val defaultRoleCreated = organizationRegistered.security?.default
        runAsOrganizationUser()
        assertNotNull(organizationApiService.setOrganizationDefaultSecurity(organizationRegistered.id!!, OrganizationRole(ROLE_ADMIN)))
        val defaultRoleUpdated = organizationApiService.getOrganizationSecurity(organizationRegistered.id!!).default
        assertNotEquals(defaultRoleCreated,defaultRoleUpdated)
      }
    }

    @Test
    fun `setOrganizationDefaultSecurity with not owned organization and no WRITE_SECURITY_PERMISSION`() {
      assertThrows<CsmAccessForbiddenException> {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered = organizationApiService.registerOrganization(
          createTestOrganizationWithSimpleSecurity(name, TEST_USER_ID, ROLE_NONE, ROLE_USER))
        runAsOrganizationUser()
        organizationApiService.setOrganizationDefaultSecurity(organizationRegistered.id!!, OrganizationRole(ROLE_ADMIN))
      }
    }

    @Test
    fun `getOrganizationAccessControl with owned organization and current user`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered = organizationApiService.registerOrganization(createTestOrganization(name))
        val organizationRole = organizationApiService.getOrganizationAccessControl(organizationRegistered.id!!, TEST_USER_ID)
        assertNotNull(organizationRole)
        assertEquals(ROLE_ADMIN, organizationRole.role)
        assertEquals(TEST_USER_ID, organizationRole.id)
      }
    }

    @Test
    fun `getOrganizationAccessControl with owned organization and non-existing user`() {
      assertThrows<CsmResourceNotFoundException> {
        val name = "o-connector-test-1"
        val organizationRegistered = organizationApiService.registerOrganization(createTestOrganization(name))
        organizationApiService.getOrganizationAccessControl(organizationRegistered.id!!, "UNKOWN")
      }
    }

    @Test
    fun `getOrganizationAccessControl with not owned organization, READ_SECURITY permission and existing user`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered = organizationApiService.registerOrganization(
          createTestOrganizationWithSimpleSecurity(name, TEST_USER_ID, ROLE_NONE, ROLE_VIEWER))
        runAsOrganizationUser()
        val organizationRole = organizationApiService.getOrganizationAccessControl(organizationRegistered.id!!, TEST_USER_ID)
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
        val organizationRegistered = organizationApiService.registerOrganization(
          createTestOrganizationWithSimpleSecurity(name, TEST_USER_ID, ROLE_NONE, ROLE_VIEWER))
        runAsOrganizationUser()
        organizationApiService.getOrganizationAccessControl(organizationRegistered.id!!, "UNKNOWN")
      }
    }

    @Test
    fun `getOrganizationAccessControl with not owned organization, no READ_SECURITY permission`() {
      assertThrows<CsmAccessForbiddenException> {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered = organizationApiService.registerOrganization(
          createTestOrganizationWithSimpleSecurity(name, TEST_USER_ID, ROLE_NONE, ROLE_NONE))
        runAsOrganizationUser()
        organizationApiService.getOrganizationAccessControl(organizationRegistered.id!!, "UNKNOWN")
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
      testFindOrganizationByIdAsNotOwner(false, null, null, null, false){
        runAsPlatformAdmin()
      }
    }

    @Test
    fun `registerOrganization with minimal values`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationToRegister = createTestOrganization(name)
        val organizationRegistered = organizationApiService.registerOrganization(organizationToRegister)
        assertEquals(
          OrganizationSecurity(
            default = ROLE_NONE,
            mutableListOf(OrganizationAccessControl(id=TEST_ADMIN_USER_ID, role=ROLE_ADMIN))
          ),
          organizationRegistered.security
        )
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
    fun `registerOrganization with security values`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationToRegister = createTestOrganizationWithSimpleSecurity(name,OTHER_TEST_USER_ID, ROLE_USER,
          ROLE_NONE)
        val organizationRegistered = organizationApiService.registerOrganization(organizationToRegister)
        assertEquals(
          OrganizationSecurity(
            default = ROLE_USER,
            mutableListOf(OrganizationAccessControl(id=OTHER_TEST_USER_ID, role=ROLE_NONE))
          ),
          organizationRegistered.security
        )
        assertEquals(name, organizationRegistered.name)
        assertTrue(organizationRegistered.id!!.startsWith("o-"))
      }
    }

    @Test
    fun `unregisterOrganization owned organization `() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationToRegister = createTestOrganization(name)
        val organizationRegistered = organizationApiService.registerOrganization(organizationToRegister)
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
        val organizationToRegister = createTestOrganizationWithSimpleSecurity(name,TEST_ADMIN_USER_ID, ROLE_NONE,
          ROLE_NONE)
        val organizationRegistered = organizationApiService.registerOrganization(organizationToRegister)
        runAsPlatformAdmin()
        organizationApiService.unregisterOrganization(organizationRegistered.id!!)
      }
    }

    @Test
    fun `updateOrganization owned organization name and services`(){
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered = organizationApiService.registerOrganization(createTestOrganization(name))

        organizationRegistered.name = "my-new-name"
        organizationRegistered.services = OrganizationServices(
          tenantCredentials = mutableMapOf(),
          storage =
          OrganizationService(
            cloudService = "cloud",
            baseUri = "base",
            platformService = "platform",
            resourceUri = "resource",
            credentials = mutableMapOf(Pair("credential1", 10.0))
          ),
          solutionsContainerRegistry =
          OrganizationService(
            cloudService = "cloud",
            baseUri = "base",
            platformService = "platform",
            resourceUri = "resource",
            credentials = mutableMapOf(Pair("credential2", "toto"))
          )
        )
        organizationApiService.updateOrganization(organizationRegistered.id!!,organizationRegistered)

        assertEquals(organizationRegistered, organizationApiService.findOrganizationById(organizationRegistered.id!!))
      }
    }

    @Test
    fun `updateOrganization owned organization security`(){
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered = organizationApiService.registerOrganization(createTestOrganization(name))

        organizationRegistered.security = OrganizationSecurity(
          default = ROLE_NONE,
          mutableListOf(OrganizationAccessControl(id=OTHER_TEST_USER_ID, role = ROLE_USER))
        )

        organizationApiService.updateOrganization(organizationRegistered.id!!,organizationRegistered)

        // Organization Security cannot be updated via updateOrganization endpoint
        // setOrganizationDefaultSecurity or
        // addOrganizationAccessControl/updateOrganizationAccessControl/removeOrganizationAccessControl
        // Should be used instead
        assertNotEquals(organizationRegistered, organizationApiService.findOrganizationById(organizationRegistered.id!!))
      }
    }

    @Test
    fun `updateOrganization not owned organization with WRITE permission`(){
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered = organizationApiService.registerOrganization(
          createTestOrganizationWithSimpleSecurity(name, TEST_ADMIN_USER_ID, ROLE_NONE, ROLE_EDITOR)
        )

        runAsPlatformAdmin()

        organizationRegistered.name = "my-new-name"
        organizationRegistered.services = OrganizationServices(
          tenantCredentials = mutableMapOf(),
          storage =
          OrganizationService(
            cloudService = "cloud",
            baseUri = "base",
            platformService = "platform",
            resourceUri = "resource",
            credentials = mutableMapOf(Pair("credential1", 10.0))
          ),
          solutionsContainerRegistry =
          OrganizationService(
            cloudService = "cloud",
            baseUri = "base",
            platformService = "platform",
            resourceUri = "resource",
            credentials = mutableMapOf(Pair("credential2", "toto"))
          )
        )

        organizationApiService.updateOrganization(organizationRegistered.id!!,organizationRegistered)

        assertEquals(organizationRegistered, organizationApiService.findOrganizationById(organizationRegistered.id!!))
      }
    }

    @Test
    fun `updateOrganization not owned organization with no WRITE permission`(){
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered = organizationApiService.registerOrganization(createTestOrganization(name))
        runAsPlatformAdmin()
        organizationApiService.updateOrganization(organizationRegistered.id!!,organizationRegistered)
      }
    }


    @Test
    fun `updateSolutionsContainerRegistryByOrganizationId with owned organization`(){
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered = organizationApiService.registerOrganization(createTestOrganization(name))

        val newSolutionsContainerRegistry = OrganizationService(
          cloudService = "cloud",
          baseUri = "base",
          platformService = "platform",
          resourceUri = "resource",
          credentials = mutableMapOf(Pair("cred2", "test2")))

        organizationApiService.updateSolutionsContainerRegistryByOrganizationId(organizationRegistered.id!!,newSolutionsContainerRegistry)

        assertNotEquals(newSolutionsContainerRegistry, organizationApiService.findOrganizationById(organizationRegistered.id!!).services!!.solutionsContainerRegistry)
      }
    }

    @Test
    fun `updateSolutionsContainerRegistryByOrganizationId with not owned organization and WRITE permission`(){
      assertDoesNotThrow {
        runAsDifferentOrganizationUser()
        val name = "o-connector-test-1"
        val organizationRegistered = organizationApiService.registerOrganization(createTestOrganizationWithSimpleSecurity(name, TEST_USER_ID,
          ROLE_NONE,
          ROLE_EDITOR))

        runAsPlatformAdmin()
        val newSolutionsContainerRegistry = OrganizationService(
          cloudService = "cloud",
          baseUri = "base",
          platformService = "platform",
          resourceUri = "resource",
          credentials = mutableMapOf(Pair("cred2", "test2")))

        organizationApiService.updateSolutionsContainerRegistryByOrganizationId(organizationRegistered.id!!,newSolutionsContainerRegistry)

        assertNotEquals(newSolutionsContainerRegistry, organizationApiService.findOrganizationById(organizationRegistered.id!!).services!!.solutionsContainerRegistry)
      }
    }

    @Test
    fun `updateSolutionsContainerRegistryByOrganizationId with not owned organization and READ permission`(){
      assertDoesNotThrow {
        runAsDifferentOrganizationUser()
        val name = "o-connector-test-1"
        val organizationRegistered = organizationApiService.registerOrganization(createTestOrganizationWithSimpleSecurity(name, TEST_USER_ID,
          ROLE_NONE,
          ROLE_EDITOR))

        runAsPlatformAdmin()
        val newSolutionsContainerRegistry = OrganizationService(
          cloudService = "cloud",
          baseUri = "base",
          platformService = "platform",
          resourceUri = "resource",
          credentials = mutableMapOf(Pair("cred2", "test2")))

        organizationApiService.updateSolutionsContainerRegistryByOrganizationId(organizationRegistered.id!!,newSolutionsContainerRegistry)

        assertNotEquals(newSolutionsContainerRegistry, organizationApiService.findOrganizationById(organizationRegistered.id!!).services!!.solutionsContainerRegistry)
      }
    }

    @Test
    fun `updateStorageByOrganizationId with owned organization`(){
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered = organizationApiService.registerOrganization(createTestOrganization(name))

        val newStorage = OrganizationService(
          cloudService = "cloud",
          baseUri = "base",
          platformService = "platform",
          resourceUri = "resource",
          credentials = mutableMapOf(Pair("cred2", "test2")))

        organizationApiService.updateStorageByOrganizationId(organizationRegistered.id!!,newStorage)

        assertNotEquals(newStorage, organizationApiService.findOrganizationById(organizationRegistered.id!!).services!!.storage)
      }
    }

    @Test
    fun `updateStorageByOrganizationId with not owned organization and WRITE permission`(){
      assertDoesNotThrow {
        runAsDifferentOrganizationUser()
        val name = "o-connector-test-1"
        val organizationRegistered = organizationApiService.registerOrganization(createTestOrganizationWithSimpleSecurity(name, TEST_USER_ID,
          ROLE_NONE,
          ROLE_EDITOR))

        runAsPlatformAdmin()
        val newStorage = OrganizationService(
          cloudService = "cloud",
          baseUri = "base",
          platformService = "platform",
          resourceUri = "resource",
          credentials = mutableMapOf(Pair("cred2", "test2")))

        organizationApiService.updateStorageByOrganizationId(organizationRegistered.id!!,newStorage)

        assertNotEquals(newStorage, organizationApiService.findOrganizationById(organizationRegistered.id!!).services!!.storage)
      }
    }

    @Test
    fun `updateStorageByOrganizationId with not owned organization and READ permission`(){
      assertDoesNotThrow {
        runAsDifferentOrganizationUser()
        val name = "o-connector-test-1"
        val organizationRegistered = organizationApiService.registerOrganization(createTestOrganizationWithSimpleSecurity(name, TEST_USER_ID,
          ROLE_NONE,
          ROLE_EDITOR))

        runAsPlatformAdmin()
        val newStorage = OrganizationService(
          cloudService = "cloud",
          baseUri = "base",
          platformService = "platform",
          resourceUri = "resource",
          credentials = mutableMapOf(Pair("cred2", "test2")))

        organizationApiService.updateStorageByOrganizationId(organizationRegistered.id!!,newStorage)

        assertNotEquals(newStorage, organizationApiService.findOrganizationById(organizationRegistered.id!!).services!!.storage)
      }
    }

    @Test
    fun `updateTenantCredentialsByOrganizationId with owned organization and empty body`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered = organizationApiService.registerOrganization(createTestOrganization(name))
        val updateResult =
          organizationApiService.updateTenantCredentialsByOrganizationId(organizationRegistered.id!!, mapOf())
        assertTrue(updateResult.isEmpty())
      }
    }

    @Test
    fun `updateTenantCredentialsByOrganizationId with owned organization and correct body`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered = organizationApiService.registerOrganization(createTestOrganization(name))
        val credentials = mutableMapOf(Pair("cred2", "test2"))
        val updateResult =
          organizationApiService.updateTenantCredentialsByOrganizationId(organizationRegistered.id!!, credentials)
        assertEquals(credentials, updateResult)
      }
    }

    @Test
    fun `updateTenantCredentialsByOrganizationId with not owned organization, empty body and no WRITE permission`() {
      assertDoesNotThrow {
        runAsDifferentOrganizationUser()
        val name = "o-connector-test-1"
        val organizationRegistered = organizationApiService.registerOrganization(createTestOrganization(name))
        runAsPlatformAdmin()
        val updateResult =
          organizationApiService.updateTenantCredentialsByOrganizationId(organizationRegistered.id!!, mapOf())
        assertTrue(updateResult.isEmpty())
      }
    }

    @Test
    fun `updateTenantCredentialsByOrganizationId with not owned organization, empty body and WRITE permission`() {
      assertDoesNotThrow {
        runAsDifferentOrganizationUser()
        val name = "o-connector-test-1"
        val organizationRegistered = organizationApiService.registerOrganization(
          createTestOrganizationWithSimpleSecurity(name,TEST_USER_ID, ROLE_NONE, ROLE_EDITOR))
        val credentials = mutableMapOf(Pair("cred2", "test2"))
        runAsPlatformAdmin()
        val updateResult =
          organizationApiService.updateTenantCredentialsByOrganizationId(organizationRegistered.id!!, credentials)
        assertEquals(credentials, updateResult)
      }
    }

    @Test
    fun getAllPermissions() {
      val mapAllPermissions = listOf(ComponentRolePermissions(
        component = "organization",
        roles = mutableMapOf(
          ROLE_VIEWER to mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY),
          ROLE_USER to mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_CREATE_CHILDREN),
          ROLE_EDITOR to mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_CREATE_CHILDREN, PERMISSION_WRITE),
          ROLE_ADMIN to mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_CREATE_CHILDREN, PERMISSION_WRITE, PERMISSION_WRITE_SECURITY, PERMISSION_DELETE),
        )
      ),
        ComponentRolePermissions(
          component = "workspace",
          roles = mutableMapOf(
            ROLE_VIEWER to mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY),
            ROLE_USER to mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_CREATE_CHILDREN),
            ROLE_EDITOR to mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_CREATE_CHILDREN, PERMISSION_WRITE),
            ROLE_ADMIN to mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_CREATE_CHILDREN, PERMISSION_WRITE, PERMISSION_WRITE_SECURITY, PERMISSION_DELETE),
          )
        ),
        ComponentRolePermissions(
          component = "scenario",
          roles = mutableMapOf(
            ROLE_VIEWER to mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY),
            ROLE_EDITOR to mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_LAUNCH, PERMISSION_WRITE),
            ROLE_VALIDATOR to mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_LAUNCH, PERMISSION_WRITE, PERMISSION_VALIDATE),
            ROLE_ADMIN to mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_LAUNCH, PERMISSION_WRITE, PERMISSION_VALIDATE, PERMISSION_WRITE_SECURITY, PERMISSION_DELETE),
          )
        )
      )
      assertEquals(mapAllPermissions, organizationApiService.getAllPermissions())
    }

    @Test
    fun `getOrganizationPermissions with owned organization`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered = organizationApiService.registerOrganization(createTestOrganization(name))
        var organizationUserPermissions =
          organizationApiService.getOrganizationPermissions(organizationRegistered.id!!, ROLE_VIEWER)
        assertEquals(mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY), organizationUserPermissions)
        organizationUserPermissions =
          organizationApiService.getOrganizationPermissions(organizationRegistered.id!!, ROLE_USER)
        assertEquals(mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_CREATE_CHILDREN), organizationUserPermissions)
        organizationUserPermissions =
          organizationApiService.getOrganizationPermissions(organizationRegistered.id!!, ROLE_EDITOR)
        assertEquals(mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_CREATE_CHILDREN, PERMISSION_WRITE), organizationUserPermissions)
        organizationUserPermissions =
          organizationApiService.getOrganizationPermissions(organizationRegistered.id!!, ROLE_ADMIN)
        assertEquals(mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_CREATE_CHILDREN, PERMISSION_WRITE, PERMISSION_WRITE_SECURITY, PERMISSION_DELETE), organizationUserPermissions)
        organizationUserPermissions =
          organizationApiService.getOrganizationPermissions(organizationRegistered.id!!, "UNKNOWN")
        assertEquals(emptyList(), organizationUserPermissions)
      }
    }

    @Test
    fun `getOrganizationPermissions with not owned organization and READ_SECURITY permission`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered = organizationApiService.registerOrganization(
          createTestOrganizationWithSimpleSecurity(name,TEST_USER_ID,ROLE_NONE, ROLE_VIEWER)
        )
        runAsPlatformAdmin()
        var organizationUserPermissions =
          organizationApiService.getOrganizationPermissions(organizationRegistered.id!!, ROLE_VIEWER)
        assertEquals(mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY), organizationUserPermissions)
        organizationUserPermissions =
          organizationApiService.getOrganizationPermissions(organizationRegistered.id!!, ROLE_USER)
        assertEquals(mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_CREATE_CHILDREN), organizationUserPermissions)
        organizationUserPermissions =
          organizationApiService.getOrganizationPermissions(organizationRegistered.id!!, ROLE_EDITOR)
        assertEquals(mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_CREATE_CHILDREN, PERMISSION_WRITE), organizationUserPermissions)
        organizationUserPermissions =
          organizationApiService.getOrganizationPermissions(organizationRegistered.id!!, ROLE_ADMIN)
        assertEquals(mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_CREATE_CHILDREN, PERMISSION_WRITE, PERMISSION_WRITE_SECURITY, PERMISSION_DELETE), organizationUserPermissions)
        organizationUserPermissions =
          organizationApiService.getOrganizationPermissions(organizationRegistered.id!!, "UNKNOWN")
        assertEquals(emptyList(), organizationUserPermissions)
      }
    }

    @Test
    fun `getOrganizationPermissions with not owned organization and no permission`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered = organizationApiService.registerOrganization(
          createTestOrganizationWithSimpleSecurity(name,TEST_USER_ID,ROLE_NONE, ROLE_VIEWER)
        )
        runAsPlatformAdmin()
        var organizationUserPermissions =
          organizationApiService.getOrganizationPermissions(organizationRegistered.id!!, ROLE_VIEWER)
        assertEquals(mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY), organizationUserPermissions)
        organizationUserPermissions =
          organizationApiService.getOrganizationPermissions(organizationRegistered.id!!, ROLE_USER)
        assertEquals(mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_CREATE_CHILDREN), organizationUserPermissions)
        organizationUserPermissions =
          organizationApiService.getOrganizationPermissions(organizationRegistered.id!!, ROLE_EDITOR)
        assertEquals(mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_CREATE_CHILDREN, PERMISSION_WRITE), organizationUserPermissions)
        organizationUserPermissions =
          organizationApiService.getOrganizationPermissions(organizationRegistered.id!!, ROLE_ADMIN)
        assertEquals(mutableListOf(PERMISSION_READ, PERMISSION_READ_SECURITY, PERMISSION_CREATE_CHILDREN, PERMISSION_WRITE, PERMISSION_WRITE_SECURITY, PERMISSION_DELETE), organizationUserPermissions)
        organizationUserPermissions =
          organizationApiService.getOrganizationPermissions(organizationRegistered.id!!, "UNKNOWN")
        assertEquals(emptyList(), organizationUserPermissions)
      }
    }

    @Test
    fun `getOrganizationSecurity with owned organization`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered = organizationApiService.registerOrganization(createTestOrganization(name))
        assertNotNull(organizationApiService.getOrganizationSecurity(organizationRegistered.id!!))
      }
    }

    @Test
    fun `getOrganizationSecurity with not owned organization with READ_SECURITY permission`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered = organizationApiService.registerOrganization(
          createTestOrganizationWithSimpleSecurity(name, TEST_USER_ID, ROLE_NONE, ROLE_VIEWER)
        )
        runAsPlatformAdmin()
        assertNotNull(organizationApiService.getOrganizationSecurity(organizationRegistered.id!!))
      }
    }

    @Test
    fun `getOrganizationSecurity with not owned organization with no READ_SECURITY permission`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered = organizationApiService.registerOrganization(
          createTestOrganizationWithSimpleSecurity(name, TEST_ADMIN_USER_ID, ROLE_NONE, ROLE_NONE)
        )
        runAsPlatformAdmin()
        assertNotNull(organizationApiService.getOrganizationSecurity(organizationRegistered.id!!))
      }
    }

    @Test
    fun `setOrganizationDefaultSecurity with owned organization and existing role`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered = organizationApiService.registerOrganization(createTestOrganization(name))
        val defaultRoleCreated = organizationRegistered.security?.default
        assertNotNull(organizationApiService.setOrganizationDefaultSecurity(organizationRegistered.id!!, OrganizationRole(ROLE_ADMIN)))
        val defaultRoleUpdated = organizationApiService.getOrganizationSecurity(organizationRegistered.id!!).default
        assertNotEquals(defaultRoleCreated,defaultRoleUpdated)
      }
    }

    @Test
    fun `setOrganizationDefaultSecurity with owned organization and non-existing role`() {
      assertThrows<CsmClientException> {
        val name = "o-connector-test-1"
        val organizationRegistered = organizationApiService.registerOrganization(createTestOrganization(name))
        organizationApiService.setOrganizationDefaultSecurity(organizationRegistered.id!!, OrganizationRole("UNKNOWN"))
      }
    }

    @Test
    fun `setOrganizationDefaultSecurity with not owned organization`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered = organizationApiService.registerOrganization(createTestOrganization(name))
        val defaultRoleCreated = organizationRegistered.security?.default
        runAsPlatformAdmin()
        assertNotNull(organizationApiService.setOrganizationDefaultSecurity(organizationRegistered.id!!, OrganizationRole(ROLE_ADMIN)))
        val defaultRoleUpdated = organizationApiService.getOrganizationSecurity(organizationRegistered.id!!).default
        assertNotEquals(defaultRoleCreated,defaultRoleUpdated)
      }
    }

    @Test
    fun `setOrganizationDefaultSecurity with not owned organization and no WRITE_SECURITY_PERMISSION`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered = organizationApiService.registerOrganization(
          createTestOrganizationWithSimpleSecurity(name, TEST_ADMIN_USER_ID, ROLE_NONE, ROLE_NONE))
        val defaultRoleCreated = organizationRegistered.security?.default
        runAsPlatformAdmin()
        assertNotNull(organizationApiService.setOrganizationDefaultSecurity(organizationRegistered.id!!, OrganizationRole(ROLE_ADMIN)))
        val defaultRoleUpdated = organizationApiService.getOrganizationSecurity(organizationRegistered.id!!).default
        assertNotEquals(defaultRoleCreated,defaultRoleUpdated)
      }
    }

    @Test
    fun `getOrganizationAccessControl with owned organization and current user`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        val organizationRegistered = organizationApiService.registerOrganization(createTestOrganization(name))
        val organizationRole = organizationApiService.getOrganizationAccessControl(organizationRegistered.id!!, TEST_ADMIN_USER_ID)
        assertNotNull(organizationRole)
        assertEquals(ROLE_ADMIN, organizationRole.role)
        assertEquals(TEST_ADMIN_USER_ID, organizationRole.id)
      }
    }

    @Test
    fun `getOrganizationAccessControl with owned organization and non-existing user`() {
      assertThrows<CsmResourceNotFoundException> {
        val name = "o-connector-test-1"
        val organizationRegistered = organizationApiService.registerOrganization(createTestOrganization(name))
        organizationApiService.getOrganizationAccessControl(organizationRegistered.id!!, "UNKOWN")
      }
    }

    @Test
    fun `getOrganizationAccessControl with not owned organization, READ_SECURITY permission and existing user`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered = organizationApiService.registerOrganization(
          createTestOrganizationWithSimpleSecurity(name, TEST_ADMIN_USER_ID, ROLE_NONE, ROLE_VIEWER))
        runAsPlatformAdmin()
        val organizationRole = organizationApiService.getOrganizationAccessControl(organizationRegistered.id!!, TEST_ADMIN_USER_ID)
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
        val organizationRegistered = organizationApiService.registerOrganization(
          createTestOrganizationWithSimpleSecurity(name, TEST_ADMIN_USER_ID, ROLE_NONE, ROLE_VIEWER))
        runAsPlatformAdmin()
        organizationApiService.getOrganizationAccessControl(organizationRegistered.id!!, "UNKNOWN")
      }
    }

    @Test
    fun `getOrganizationAccessControl with not owned organization, no READ_SECURITY permission, non-existing user`() {
      assertThrows<CsmResourceNotFoundException> {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered = organizationApiService.registerOrganization(
          createTestOrganizationWithSimpleSecurity(name, TEST_ADMIN_USER_ID, ROLE_NONE, ROLE_NONE))
        runAsPlatformAdmin()
        organizationApiService.getOrganizationAccessControl(organizationRegistered.id!!, "UNKNOWN")
      }
    }

    @Test
    fun `getOrganizationAccessControl with not owned organization, no READ_SECURITY permission, existing user`() {
      assertDoesNotThrow {
        val name = "o-connector-test-1"
        runAsDifferentOrganizationUser()
        val organizationRegistered = organizationApiService.registerOrganization(
          createTestOrganizationWithSimpleSecurity(name, TEST_ADMIN_USER_ID, ROLE_NONE, ROLE_NONE))
        runAsPlatformAdmin()
        val organizationAccessControl =
          organizationApiService.getOrganizationAccessControl(organizationRegistered.id!!, TEST_ADMIN_USER_ID)
        assertNotNull(organizationAccessControl)
        assertEquals(TEST_ADMIN_USER_ID, organizationAccessControl.id)
        assertEquals(ROLE_NONE, organizationAccessControl.role)
      }
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
    val organization = if (hasUserSecurity) {
      createTestOrganizationWithSimpleSecurity(
        organizationId, userId!!, defaultRole!!, userRole!!
      )
    } else {
      createTestOrganization(organizationId)
    }
    val organizationRegistered =
      organizationApiService.registerOrganization(organization)

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
}
  /*

    fun makeOrganizationWithRole(id: String, name: String, role: String): Organization {
      return Organization(
          id = id,
          name = name,
          ownerId = name,
          services =
              OrganizationServices(
                  tenantCredentials = mutableMapOf(),
                  storage =
                      OrganizationService(
                          cloudService = "cloud",
                          baseUri = "base",
                          platformService = "platform",
                          resourceUri = "resource",
                          credentials = mutableMapOf(Pair(name, role))),
                  solutionsContainerRegistry =
                      OrganizationService(
                          cloudService = "cloud",
                          baseUri = "base",
                          platformService = "platform",
                          resourceUri = "resource",
                          credentials = mutableMapOf(Pair(name, role)))),
          security =
              OrganizationSecurity(
                  default = "none",
                  accessControlList =
                      mutableListOf(
                          OrganizationAccessControl(name, role),
                          OrganizationAccessControl("2$name", "viewer"))))
    }
    fun makeOrganization(id: String, name: String): Organization {
      return Organization(
          id = id,
          name = name,
          ownerId = name,
          services =
              OrganizationServices(
                  tenantCredentials = mutableMapOf(),
                  storage =
                      OrganizationService(
                          cloudService = "cloud",
                          baseUri = "base",
                          platformService = "platform",
                          resourceUri = "resource",
                          credentials = mutableMapOf(Pair(name, "admin"))),
                  solutionsContainerRegistry =
                      OrganizationService(
                          cloudService = "cloud",
                          baseUri = "base",
                          platformService = "platform",
                          resourceUri = "resource",
                          credentials = mutableMapOf(Pair(name, "admin")))),
          security =
              OrganizationSecurity(
                  default = "none",
                  accessControlList =
                      mutableListOf(OrganizationAccessControl(id = name, role = "admin"))))
    }

    @Test
    fun `test CRUD organization`() {

      logger.info("Create new organizations...")
      var organizationRegistered: Organization =
        organizationApiService.registerOrganization(makeOrganization("o-organization", defaultName))
      assertNotNull(organizationRegistered)

      logger.info("Fetch new organization created...")
      val organizationRetrieved: Organization = organizationApiService.findOrganizationById(organizationRegistered.id!!)
      assertEquals(organizationRegistered, organizationRetrieved)

      logger.info("Import organization...")
      organizationRegistered =
          organizationApiService.importOrganization(makeOrganization("o-organization-2", defaultName))
      assertNotNull(organizationRegistered)

      logger.info("Fetch all Organizations...")
      var organizationList = organizationApiService.findAllOrganizations(null, null)
      assertEquals(2, organizationList.size)

      logger.info("Deleting organization...")
      organizationApiService.unregisterOrganization(organizationRegistered.id!!)
      organizationList = organizationApiService.findAllOrganizations(null, null)
      assertEquals(1, organizationList.size)
    }

    @Test
    fun `test updating organization`() {
      val organizationRegistered =
          organizationApiService.registerOrganization(makeOrganization("o-organization", defaultName))
      var organizationRetrieved: Organization

      logger.info("Updating organization : ${organizationRegistered.id}...")
      organizationApiService.updateOrganization(
          organizationRegistered.id!!, makeOrganization("o-organization-1", "Organization-1.2"))
      organizationRetrieved = organizationApiService.findOrganizationById(organizationRegistered.id!!)
      assertNotEquals(organizationRegistered.name, organizationRetrieved.name)

      logger.info("Update Solution Container Registry...")
      organizationApiService.updateSolutionsContainerRegistryByOrganizationId(
          organizationRegistered.id!!,
          organizationService = OrganizationService(baseUri = "dummyURI"))
      organizationRetrieved = organizationApiService.findOrganizationById(organizationRegistered.id!!)
      assertNotEquals(
          organizationRegistered.services!!.solutionsContainerRegistry!!.baseUri,
          organizationRetrieved.services!!.solutionsContainerRegistry!!.baseUri)

      logger.info("Update Tenant credentials for organization : ${organizationRegistered.id}...")
      organizationApiService.updateTenantCredentialsByOrganizationId(
          organizationRegistered.id!!, mapOf(Pair("my.account-tester2@cosmotech.com", "admin")))
      organizationRetrieved = organizationApiService.findOrganizationById(organizationRegistered.id!!)
      assertNotEquals(
          organizationRegistered.services!!.tenantCredentials,
          organizationRetrieved.services!!.tenantCredentials)

      logger.info("Update storage configuration for organization : ${organizationRegistered.id}...")
      organizationApiService.updateStorageByOrganizationId(
          organizationRegistered.id!!,
          OrganizationService(baseUri = "https://csmphoenixcontainer.blob.core.windows.net"))
      organizationRetrieved = organizationApiService.findOrganizationById(organizationRegistered.id!!)
      assertNotEquals(
          organizationRegistered.services!!.storage, organizationRetrieved.services!!.storage)

      logger.info(
          "Update solutions container registry configuration for organization : " +
              "${organizationRegistered}...")
      organizationApiService.updateSolutionsContainerRegistryByOrganizationId(
          organizationRegistered.id!!, OrganizationService(baseUri = "newBaseUri"))
      organizationRetrieved = organizationApiService.findOrganizationById(organizationRegistered.id!!)
      assertNotEquals(
          organizationRegistered.services!!.solutionsContainerRegistry!!.baseUri,
          organizationRetrieved.services!!.solutionsContainerRegistry!!.baseUri)
    }

    @Test
    fun `test security for organization`() {
      val organizationRegistered =
          organizationApiService.registerOrganization(makeOrganization("o-organization", defaultName))
      val organizationRetrieved: Organization

      logger.info("Get all permissions per component...")
      val permissionList = organizationApiService.getAllPermissions()
      assertNotNull(permissionList)

      logger.info("Get organization : ${organizationRegistered.id} permissions for role 'XXXX'...")
      val permission =
          organizationApiService.getOrganizationPermissions(organizationRegistered.id!!, "admin")
      assertNotNull(permission)

      logger.info("Get the security information for organization : ${organizationRegistered.id}...")
      logger.warn(organizationRegistered.toString())
      assertNotNull(organizationApiService.getOrganizationSecurity(organizationRegistered.id!!))

      logger.info("Set the organization default security...")
      organizationApiService.setOrganizationDefaultSecurity(
          organizationRegistered.id!!, OrganizationRole("editor"))
      organizationRetrieved = organizationApiService.findOrganizationById(organizationRegistered.id!!)
      assertNotEquals(organizationRegistered, organizationRetrieved)

      logger.info("Get the security users list for organization : ${organizationRegistered.id}...")
      val securityUserList =
          organizationApiService.getOrganizationSecurityUsers(organizationRegistered.id!!)
      assertNotNull(securityUserList)
    }

    @Test
    fun `test access control for organization`() {
      val organizationRegistered =
          organizationApiService.registerOrganization(makeOrganization("o-organization", defaultName))
      var organizationRetrieved: Organization

      logger.info("Add a control access to organization : ${organizationRegistered.id}...")
      organizationApiService.addOrganizationAccessControl(
          organizationRegistered.id!!,
          OrganizationAccessControl("my.account-tester3@cosmotech.com", "viewer"))
      organizationRetrieved = organizationApiService.findOrganizationById(organizationRegistered.id!!)
      assertNotEquals(organizationRegistered, organizationRetrieved)

      logger.info("Get a control access for organization : ${organizationRegistered.id}...")
      val accessControl1 =
          organizationApiService.getOrganizationAccessControl(
              organizationRegistered.id!!, "my.account-tester@cosmotech.com")
      assertNotNull(accessControl1)

      logger.info("Update access control for organization : ${organizationRegistered.id}...")
      organizationApiService.updateOrganizationAccessControl(
          organizationRegistered.id!!, "my.account-tester3@cosmotech.com", OrganizationRole("user"))
      val accessControl2 =
          organizationApiService.getOrganizationAccessControl(
              organizationRegistered.id!!, "my.account-tester3@cosmotech.com")
      assertNotEquals(accessControl1, accessControl2)

      logger.info("Remove access control from organization : ${organizationRegistered.id}...")
      organizationApiService.removeOrganizationAccessControl(
          organizationRegistered.id!!, "my.account-tester3@cosmotech.com")
      assertEquals(
          1,
          organizationApiService
              .getOrganizationSecurity(organizationRegistered.id!!)
              .accessControlList
              .size)
    }

  }
  */
