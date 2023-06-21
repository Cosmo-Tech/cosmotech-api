// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.organization.service

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.rbac.ROLE_ADMIN
import com.cosmotech.api.rbac.ROLE_EDITOR
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.rbac.ROLE_USER
import com.cosmotech.api.rbac.ROLE_VALIDATOR
import com.cosmotech.api.rbac.ROLE_VIEWER
import com.cosmotech.api.tests.CsmRedisTestBase
import com.cosmotech.api.utils.getCurrentAccountIdentifier
import com.cosmotech.api.utils.getCurrentAuthenticatedRoles
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationRole
import com.cosmotech.organization.domain.OrganizationSecurity
import com.cosmotech.organization.domain.OrganizationService
import com.cosmotech.organization.domain.OrganizationServices
import com.redis.om.spring.RediSearchIndexer
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockkStatic
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.runner.RunWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
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

  @Autowired lateinit var rediSearchIndexer: RediSearchIndexer

  @Autowired lateinit var organizationApiService: OrganizationApiService

  val defaultName = "my.account-tester@cosmotech.com"

  @Autowired lateinit var csmPlatformProperties: CsmPlatformProperties

  @BeforeEach
  fun setUp() {
    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
    every { getCurrentAccountIdentifier(any()) } returns defaultName
    every { getCurrentAuthenticatedUserName() } returns "my.account-tester"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf()
    rediSearchIndexer.createIndexFor(Organization::class.java)
  }

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
  fun `test find All Organization with different pagination params`() {
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")

    val numberOfOrganization = 20
    val defaultPageSize = csmPlatformProperties.twincache.organization.defaultPageSize
    val expectedSize = 15
    IntRange(1, numberOfOrganization).forEach {
      var newOrganization = makeOrganization("o-organization-test-$it", "Organization-test-$it")
      organizationApiService.registerOrganization(newOrganization)
    }
    logger.info("should find all Organizations and assert there are $numberOfOrganization")
    var organizationList = organizationApiService.findAllOrganizations(null, null)
    assertEquals(numberOfOrganization, organizationList.size)

    logger.info(
        "should find all Organizations and assert it equals defaultPageSize: $defaultPageSize")
    organizationList = organizationApiService.findAllOrganizations(0, null)
    assertEquals(defaultPageSize, organizationList.size)

    logger.info("should find all Organizations and assert there are expected size: $expectedSize")
    organizationList = organizationApiService.findAllOrganizations(0, expectedSize)
    assertEquals(expectedSize, organizationList.size)

    logger.info("should find all Organizations and assert it returns the  second / last page")
    organizationList = organizationApiService.findAllOrganizations(1, expectedSize)
    assertEquals(numberOfOrganization - expectedSize, organizationList.size)
  }

  @Test
  fun `test find All Organizations with wrong pagination params`() {
    logger.info("should throw IllegalArgumentException when page and size are zero")
    assertThrows<IllegalArgumentException> { organizationApiService.findAllOrganizations(0, 0) }
    logger.info("should throw IllegalArgumentException when page is negative")
    assertThrows<IllegalArgumentException> { organizationApiService.findAllOrganizations(-1, 1) }
    logger.info("should throw IllegalArgumentException when size is negative")
    assertThrows<IllegalArgumentException> { organizationApiService.findAllOrganizations(0, -1) }
  }

  @Test
  fun `test CRUD organization`() {
    var organizationRegistered: Organization
    val organizationRetrieved: Organization

    logger.info("Create new organizations...")
    organizationRegistered =
        organizationApiService.registerOrganization(makeOrganization("o-organization", defaultName))
    assertNotNull(organizationRegistered)

    logger.info("Fetch new organization created...")
    organizationRetrieved = organizationApiService.findOrganizationById(organizationRegistered.id!!)
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

  @TestFactory
  fun `test RBAC find all organizations`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test RBAC find all : $role") {
              every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
              every { getCurrentAccountIdentifier(any()) } returns defaultName
              organizationApiService.registerOrganization(
                  makeOrganizationWithRole("id", defaultName, role))

              val allOrganizations = organizationApiService.findAllOrganizations(null, null)
              if (shouldThrow) {
                assertEquals(4, allOrganizations.size)
              } else {
                assertNotNull(allOrganizations)
              }
            }
          }
}
