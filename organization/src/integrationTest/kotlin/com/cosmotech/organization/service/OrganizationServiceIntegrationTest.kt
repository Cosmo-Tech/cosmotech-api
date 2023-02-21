// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.organization.service

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.tests.CsmRedisTestBase
import com.cosmotech.api.utils.getCurrentAuthenticatedMail
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
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
class OrganizationServiceIntegrationTest : CsmRedisTestBase() {

  private val logger = LoggerFactory.getLogger(OrganizationServiceIntegrationTest::class.java)

  @Autowired lateinit var rediSearchIndexer: RediSearchIndexer

  @Autowired lateinit var organizationApiService: OrganizationApiService

  var organization1 = mockOrganization("o-organization-1", "Organization-1")
  val organization2 = mockOrganization("o-organization-2", "Organization-2")

  var organizationRegistered1 = Organization()
  var organizationRegistered2 = Organization()

  var organizationRetrieved1 = Organization()
  var organizationRetrieved2 = Organization()

  var organizationSecurity = OrganizationSecurity("", mutableListOf())

  @Autowired lateinit var csmPlatformProperties: CsmPlatformProperties

  @BeforeEach
  fun setUp() {
    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
    every { getCurrentAuthenticatedMail(any()) } returns "my.account-tester@cosmotech.com"
    every { getCurrentAuthenticatedUserName() } returns "my.account-tester"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf()
    rediSearchIndexer.createIndexFor(Organization::class.java)
  }

  fun mockOrganization(id: String, name: String): Organization {
    return Organization(
        id = id,
        name = name,
        ownerId = "my.account-tester@cosmotech.com",
        services =
            OrganizationServices(
                tenantCredentials = mutableMapOf(),
                storage =
                    OrganizationService(
                        cloudService = "cloud",
                        baseUri = "base",
                        platformService = "platform",
                        resourceUri = "resource",
                        credentials =
                            mutableMapOf(Pair("my.account-tester@cosmotech.com", "reader"))),
                solutionsContainerRegistry =
                    OrganizationService(
                        cloudService = "cloud",
                        baseUri = "base",
                        platformService = "platform",
                        resourceUri = "resource",
                        credentials =
                            mutableMapOf(Pair("my.account-tester@cosmotech.com", "reader")))),
        security =
            OrganizationSecurity(
                default = "none",
                accessControlList =
                    mutableListOf(
                        OrganizationAccessControl(
                            id = "my.account-tester2@cosmotech.com", role = "reader"),
                        OrganizationAccessControl(
                            id = "my.account-tester@cosmotech.com", role = "admin"))))
  }

  @Test
  fun `test findAllOrganizations() within a single page`() {
    logger.info("Creating 10 organization with 100 organizations by page...")
    IntRange(1, 10).forEach {
      var newOrganization = mockOrganization("o-organization-test-$it", "Organization-test-$it")
      organizationApiService.registerOrganization(newOrganization)
    }
    var organizationList = organizationApiService.findAllOrganizations(0, 100)
    val numberOfOrganizationFetched = organizationList.size
    logger.info("findAllOrganizations() have fetched $numberOfOrganizationFetched organizations")

    assertEquals(10, numberOfOrganizationFetched)
  }

  @Test
  fun `test findAllOrganizations() within 2 pages`() {
    logger.info("Creating 10 organizations with 6 organizations by page ...")
    val organizationNames = mutableListOf<String>()
    IntRange(1, 10).forEach {
      val organizationName = "Organization-test-$it"
      var newOrganization = mockOrganization("o-organization-test-$it", organizationName)
      organizationApiService.registerOrganization(newOrganization)
      organizationNames.add(organizationName)
    }

    val organizationListFetched = organizationApiService.findAllOrganizations(0, 6).toMutableList()
    val numberOfOrganizationFetched = organizationListFetched.size
    logger.info(
        "findAllOrganizations() have fetched $numberOfOrganizationFetched organizations within the page 0")
    assertEquals(6, numberOfOrganizationFetched)
    assertNotEquals(10, numberOfOrganizationFetched)

    organizationListFetched.addAll(organizationApiService.findAllOrganizations(1, 6))
    assertEquals(10, organizationListFetched.size)
    assertEquals(organizationNames, organizationListFetched.stream().map { it.name }.toList())
  }

  @Test
  fun `test CRUD organization`() {
    logger.info("Create new organizations...")
    organizationRegistered1 = organizationApiService.registerOrganization(organization1)

    logger.info("Fetch new organization created...")
    var organizationRetrieved1 =
        organizationApiService.findOrganizationById(organizationRegistered1.id!!)
    assertEquals(organizationRegistered1, organizationRetrieved1)

    logger.info("Import organization...")
    organizationRegistered2 = organizationApiService.importOrganization(organization2)
    assertNotNull(organizationRegistered2)

    logger.info("Fetch all Organizations...")
    var organizationList = organizationApiService.findAllOrganizations(0, 100)
    assertTrue(organizationList.size == 2)

    logger.info("Deleting organization...")
    organizationApiService.unregisterOrganization(organizationRegistered2.id!!)
    organizationList = organizationApiService.findAllOrganizations()
    assertTrue { organizationList.size == 1 }
  }

  fun `test updating organization`() {

    logger.info("Updating organization : ${organizationRegistered1.id}...")
    // TODO Change the next line
    organization1 = mockOrganization("o-organization-1", "Organization-1.2")
    organizationApiService.updateOrganization(organizationRegistered1.id!!, organization1)
    organizationRetrieved2 =
        organizationApiService.findOrganizationById(organizationRegistered1.id!!)
    assertNotEquals(organizationRetrieved2, organizationRetrieved1)
    logger.info("Updated organization")

    logger.info("Update Solution Container Registry...")
    organizationApiService.updateSolutionsContainerRegistryByOrganizationId(
        organizationRegistered1.id!!,
        organizationService = OrganizationService(baseUri = "dummyURI"))
    organizationRetrieved1 =
        organizationApiService.findOrganizationById(organizationRegistered1.id!!)
    assertNotEquals(organizationRetrieved1, organizationRetrieved2)

    logger.info("Update Tenant credentials for organization : ${organizationRegistered1.id}...")
    organizationApiService.updateTenantCredentialsByOrganizationId(
        organizationRegistered1.id!!, mapOf(Pair("my.account-tester2@cosmotech.com", "admin")))
    organizationRetrieved2 =
        organizationApiService.findOrganizationById(organizationRegistered1.id!!)
    assertNotEquals(organizationRetrieved1, organizationRetrieved2)

    logger.info("Update storage configuration for organization : ${organizationRegistered1.id}...")
    organizationApiService.updateStorageByOrganizationId(
        organizationRegistered1.id!!,
        OrganizationService(baseUri = "https://csmphoenixcontainer.blob.core.windows.net"))
    organizationRetrieved1 =
        organizationApiService.findOrganizationById(organizationRegistered1.id!!)
    assertNotEquals(organizationRetrieved1, organizationRetrieved2)

    logger.info(
        "Update solutions container registry configuration for organization : " +
            "${organizationRegistered1}...")
    organizationApiService.updateSolutionsContainerRegistryByOrganizationId(
        organizationRegistered1.id!!, OrganizationService(baseUri = "newBaseUri"))
    organizationRetrieved2 =
        organizationApiService.findOrganizationById(organizationRegistered1.id!!)
    assertNotEquals(organizationRetrieved1, organizationRetrieved2)
  }

  fun `test security for organization`() {
    logger.info("Get all permissions per component...")
    val permissionList = organizationApiService.getAllPermissions()
    assertNotNull(permissionList)

    logger.info("Get organization : ${organizationRegistered1.id} permissions for role 'XXXX'...")
    val permission =
        organizationApiService.getOrganizationPermissions(organizationRegistered1.id!!, "admin")
    assertNotNull(permission)

    logger.info("Get the security information for organization : ${organizationRegistered1.id}...")
    logger.warn(organizationRegistered1.toString())
    organizationSecurity =
        organizationApiService.getOrganizationSecurity(organizationRegistered1.id!!)
    assertNotNull(organizationSecurity)

    logger.info("Set the organization default security...")
    organizationApiService.setOrganizationDefaultSecurity(
        organizationRegistered1.id!!, OrganizationRole("editor"))
    organizationRetrieved1 =
        organizationApiService.findOrganizationById(organizationRegistered1.id!!)
    assertNotEquals(organizationRetrieved1, organizationRetrieved2)

    logger.info("Get the security users list for organization : ${organizationRegistered1.id}...")
    val securityUserList =
        organizationApiService.getOrganizationSecurityUsers(organizationRegistered1.id!!)
    assertNotNull(securityUserList)
  }

  fun `test access control for organization`() {
    logger.info("Add a control access to organization : ${organizationRegistered1.id}...")
    organizationApiService.addOrganizationAccessControl(
        organizationRegistered1.id!!,
        OrganizationAccessControl("my.account-tester3@cosmotech.com", "admin"))
    organizationRetrieved2 =
        organizationApiService.findOrganizationById(organizationRegistered1.id!!)
    assertNotEquals(organizationRetrieved1, organizationRetrieved2)

    logger.info("Get a control access for organization : ${organizationRegistered1.id}...")
    val accessControl1 =
        organizationApiService.getOrganizationAccessControl(
            organizationRegistered1.id!!, "my.account-tester@cosmotech.com")
    assertNotNull(accessControl1)

    logger.info("Update access control for organization : ${organizationRegistered1.id}...")
    organizationApiService.updateOrganizationAccessControl(
        organizationRegistered1.id!!, "my.account-tester@cosmotech.com", OrganizationRole("editor"))
    val accessControl2 =
        organizationApiService.getOrganizationAccessControl(
            organizationRegistered1.id!!, "my.account-tester@cosmotech.com")
    assertNotEquals(accessControl1, accessControl2)

    logger.info("Remove access control from organization : ${organizationRegistered1.id}...")
    organizationApiService.removeOrganizationAccessControl(
        organizationRegistered1.id!!, "my.account-tester@cosmotech.com")
    organizationSecurity =
        organizationApiService.getOrganizationSecurity(organizationRegistered1.id!!)
    assertFalse { organizationSecurity.accessControlList.size < 2 }
  }
}
