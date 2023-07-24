// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.organization.service

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.rbac.*
import com.cosmotech.api.security.ROLE_ORGANIZATION_USER
import com.cosmotech.api.security.ROLE_PLATFORM_ADMIN
import com.cosmotech.api.tests.CsmRedisTestBase
import com.cosmotech.api.utils.getCurrentAccountIdentifier
import com.cosmotech.api.utils.getCurrentAuthenticatedRoles
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.api.utils.getCurrentAuthentication
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationSecurity
import com.redis.om.spring.RediSearchIndexer
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.jupiter.api.*
import kotlin.test.assertEquals
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
    fun `find All Organizations with correct values and no RBAC`() {
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

      val numberOfOrganizationCreated = batchOrganizationCreationWithRBAC(TEST_USER_ID)
      // This number represents the amount of Organization that test.user can read regarding RBAC
      // This is typically all simple combinations except "securityRole to none"
      // We have 36 combinations per user in batchOrganizationCreationWithRBAC
      // So 36 combinations minus "securityRole to 'none'" equals 35
      testFindAllWithRBAC(numberOfOrganizationCreated, 35)
    }

    @Test
    fun `find All Organizations with correct values and no RBAC for current user`() {

      val numberOfOrganizationCreated = batchOrganizationCreationWithRBAC(OTHER_TEST_USER_ID)
      // This number represents the amount of Organization that test.user can read regarding RBAC
      // We have 36 combinations per user in batchOrganizationCreationWithRBAC
      // securityRole does not refer to test.user
      // The only configuration that fit for test.user is defaultRole with
      // ROLE_VIEWER (x6), ROLE_EDITOR (x6), ROLE_VALIDATOR (x6), ROLE_USER (x6), ROLE_ADMIN (x6) = 30
      testFindAllWithRBAC(numberOfOrganizationCreated, 30)
    }

    @Test
    fun `find All Organizations with wrong values`() {
      testFindAllOrganizationsWithWrongValues()
    }

  }

  @Nested
  inner class AsPlatformAdmin {

    @BeforeEach
    fun setUp() {
      runAsPlatformAdmin()
    }

    @Test
    fun `find All Organizations with correct values and no RBAC`() {
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
      testFindAllWithRBAC(numberOfOrganizationCreated,36)
    }

    @Test
    fun `find All Organizations with wrong values`() {
      testFindAllOrganizationsWithWrongValues()
    }

  }

  private fun testFindAllWithRBAC(numberOfOrganizationCreated: Int,
                                  numberOfOrganizationReachableByTestUser: Int) {
    val defaultPageSize = csmPlatformProperties.twincache.organization.defaultPageSize

    testFindAllOrganizations(null, null, numberOfOrganizationReachableByTestUser)
    testFindAllOrganizations(0, null, defaultPageSize)
    testFindAllOrganizations(0, numberOfOrganizationCreated, numberOfOrganizationReachableByTestUser)
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

  /** Organization batch creation with RBAC*/
  internal fun batchOrganizationCreationWithRBAC(userId: String): Int {
    val roleList = listOf(
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
        val organization = createTestOrganizationWithSimpleSecurity(
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
    return Organization(
      id = "organization_id",
      name = name)
  }

  /** Create default test Connector */
  internal fun createTestOrganizationWithSimpleSecurity(name: String,
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
        accessControlList =
        mutableListOf(
          OrganizationAccessControl(userId, role)))
    )
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
