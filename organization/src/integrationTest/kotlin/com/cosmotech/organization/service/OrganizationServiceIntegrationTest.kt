// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.organization.service

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.rbac.ROLE_ADMIN
import com.cosmotech.api.rbac.ROLE_VIEWER
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
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertDoesNotThrow
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
class OrganizationServiceIntegrationTest : CsmRedisTestBase() {

  private val logger = LoggerFactory.getLogger(OrganizationServiceIntegrationTest::class.java)

  @Autowired lateinit var rediSearchIndexer: RediSearchIndexer

  @Autowired lateinit var organizationApiService: OrganizationApiService

  var organization1 = makeOrganization("o-organization-1", "Organization-1")
  val organization2 = makeOrganization("o-organization-2", "Organization-2")

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
                accessControlList = mutableListOf(OrganizationAccessControl(name, role))))
  }
  fun makeOrganization(id: String, name: String): Organization {
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
  fun `test findAllOrganizations() when limit configured is exceeded`() {
    val organizationFetchedLimitMax = csmPlatformProperties.twincache.organization.maxResult
    val beyondMaxNumber = organizationFetchedLimitMax + 1

    logger.info(
        "Creating $beyondMaxNumber organizations with $organizationFetchedLimitMax fetchable ...")
    IntRange(1, beyondMaxNumber).forEach {
      var newOrganization = makeOrganization("o-organization-test-$it", "Organization-test-$it")
      organizationApiService.registerOrganization(newOrganization)
    }
    var organizationList = organizationApiService.findAllOrganizations(1, 1)
    val numberOfOrganizationFetched = organizationList.size
    logger.info("findAllOrganizations() have fetched $numberOfOrganizationFetched organizations")

    assertEquals(numberOfOrganizationFetched, organizationFetchedLimitMax)
    assertNotEquals(numberOfOrganizationFetched, beyondMaxNumber)
  }

  @Test
  fun `test findAllOrganizations() when limit configured is not exceeded`() {
    val organizationFetchedLimitMax = csmPlatformProperties.twincache.organization.maxResult
    val underMaxNumber = organizationFetchedLimitMax - 1

    logger.info(
        "Creating $underMaxNumber organizations with $organizationFetchedLimitMax fetchable ...")
    IntRange(1, underMaxNumber).forEach {
      var newOrganization = makeOrganization("o-organization-test-$it", "Organization-test-$it")
      organizationApiService.registerOrganization(newOrganization)
    }
    var organizationList = organizationApiService.findAllOrganizations(1, 1)
    val numberOfOrganizationFetched = organizationList.size
    logger.info("findAllOrganizations() have fetched $numberOfOrganizationFetched organizations")

    assertEquals(numberOfOrganizationFetched, underMaxNumber)
    assertNotEquals(numberOfOrganizationFetched, organizationFetchedLimitMax)
  }

  /*@ParameterizedTest
  @RedisTestContextsSource
  fun `test create organization`(context: RedisTestContext) {
    logger.info("Create new organizations...")
    val organization1 = makeOrganization("toto", "tutu")
    val organizationRegistered = organizationApiService.registerOrganization(organization1)
    assertNotNull(
        context.sync().jsonGet("${Organization::class.java.name}:${organizationRegistered.id}"))
  }

  @Test
  fun `test update organization`() {
    logger.info("Create new organizations...")
    val organization1 = makeOrganization("toto", "tutu")
    val organizationRegistered = organizationApiService.registerOrganization(organization1)
    organizationRegistered.name = "Organization updated"
    val organizationUpdated =
        organizationApiService.updateOrganization(
            organizationRegistered.id!!, organizationRegistered)
    assertEquals(
        organizationUpdated, organizationApiService.findOrganizationById(organizationUpdated.id!!))
  }*/

  @Test
  fun `test CRUD organization`() {
    logger.info("Create new organizations...")
    organizationRegistered1 = organizationApiService.registerOrganization(organization1)
    assertNotNull(organizationRegistered1)

    logger.info("Fetch new organization created...")
    organizationRetrieved1 =
        organizationApiService.findOrganizationById(organizationRegistered1.id!!)
    assertEquals(organizationRegistered1, organizationRetrieved1)

    logger.info("Import organization...")
    organizationRegistered2 = organizationApiService.importOrganization(organization2)
    assertNotNull(organizationRegistered2)

    logger.info("Fetch all Organizations...")
    var organizationList = organizationApiService.findAllOrganizations(1, 1)
    assertTrue(organizationList.size == 2)

    logger.info("Deleting organization...")
    organizationApiService.unregisterOrganization(organizationRegistered2.id!!)
    organizationList = organizationApiService.findAllOrganizations(1, 1)
    assertTrue { organizationList.size == 1 }
    logger.info("Deleting organization...")
    organizationApiService.unregisterOrganization(organizationRegistered2.id!!)
    organizationList = organizationApiService.findAllOrganizations(1, 1)
    assertTrue { organizationList.size == 1 }
  }

  @Test
  fun `test updating organization`() {

    logger.info("Updating organization : ${organizationRegistered1.id}...")
    // TODO Change the next line
    logger.info(organizationRetrieved1.id)
    organization1 = makeOrganization("o-organization-1", "Organization-1.2")
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

  @Test
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

    logger.info("should throw CsmAccessForbiddenException when getting the list of users")
    assertThrows<CsmAccessForbiddenException> {
      organizationApiService.getOrganizationSecurityUsers(organizationRegistered1.id!!)
    }
  }

  fun `test RBAC AccessControls on Organization as User Unauthorized`() {
    logger.info("should throw CsmAccessForbiddenException when getting the list of users")
    assertThrows<CsmAccessForbiddenException> {
      organizationApiService.getOrganizationSecurityUsers(organizationRegistered1.id!!)
    }
  }

  @Test
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

  @TestFactory
  fun `test read organization`() =
      mapOf(
          "viewer" to false,
          "editor" to false,
          "admin" to false,
          "validator" to true,
          "user" to false,
          "none" to true)
          .map { (role, shouldThrow) ->
            dynamicTest("Test read : $role") {
              every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
              val organizationCreated =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole("id", "my.account-tester@cosmotech.com", role))
              if (shouldThrow) {
                assertThrows<CsmAccessForbiddenException> {
                  organizationApiService.findOrganizationById(organizationCreated.id!!)
                }
              } else {
                assertDoesNotThrow {
                  organizationApiService.findOrganizationById(organizationCreated.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test find all organizations`() =
      mapOf(
          "viewer" to false,
          "editor" to false,
          "validator" to false,
          "user" to false,
          "none" to true,
          "admin" to false,
      )
          .map { (role, shouldThrow) ->
            dynamicTest("Test find all : $role") {
              every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
              every { getCurrentAuthenticatedMail(any()) } returns "name"
              val organizationCreated =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole("id", "name", role))

              val allOrganizations = organizationApiService.findAllOrganizations(null, null)
              if (shouldThrow) {
                assertEquals(4, allOrganizations.size)
                logger.info("Throwing")
              } else {
                assertNotNull(allOrganizations)
                logger.info("Nothing")
              }
            }
          }

  @TestFactory
  fun `test unregister organization`() =
      mapOf(
          ROLE_VIEWER to true,
          //          createOrganization("id", "name", "editor") to true,
          ROLE_ADMIN to false,
          //          createOrganization("id", "name", "validator") to true,
          //          ROLE_ORGANIZATION_USER to false,
          //          "" to false,
          //          createOrganization("id", "name", "none") to true)
          )
          .map { (role, shouldThrow) ->
            dynamicTest("Test unregister") {
              val organizationRegistered =
                  organizationApiService.registerOrganization(makeOrganization("id", "name"))
              every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)
              if (shouldThrow) {
                assertThrows<CsmAccessForbiddenException> {
                  organizationApiService.unregisterOrganization(organizationRegistered.id!!)
                }
              } else {
                assertDoesNotThrow {
                  organizationApiService.unregisterOrganization(organizationRegistered.id!!)
                }
              }
            }
          }
}
