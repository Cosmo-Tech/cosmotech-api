// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.organization.service

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
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
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
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
    every { getCurrentAuthenticatedMail(any()) } returns defaultName
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
  fun `test findAllOrganizations() when limit configured is exceeded`() {
    val organizationFetchedLimitMax = csmPlatformProperties.twincache.organization.maxResult
    val beyondMaxNumber = organizationFetchedLimitMax + 1

    logger.info(
        "Creating $beyondMaxNumber organizations with $organizationFetchedLimitMax fetchable ...")
    IntRange(1, beyondMaxNumber).forEach {
      val newOrganization = makeOrganization("o-organization-test-$it", "Organization-test-$it")
      organizationApiService.registerOrganization(newOrganization)
    }
    val organizationList = organizationApiService.findAllOrganizations(1, 1)
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
      val newOrganization = makeOrganization("o-organization-test-$it", "Organization-test-$it")
      organizationApiService.registerOrganization(newOrganization)
    }
    val organizationList = organizationApiService.findAllOrganizations(1, 1)
    val numberOfOrganizationFetched = organizationList.size
    logger.info("findAllOrganizations() have fetched $numberOfOrganizationFetched organizations")

    assertEquals(numberOfOrganizationFetched, underMaxNumber)
    assertNotEquals(numberOfOrganizationFetched, organizationFetchedLimitMax)
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
        organizationApiService.getOrganizationSecurity(organizationRegistered.id!!)
            .accessControlList
            .size)
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
              val organizationCreated =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole("id", defaultName, role))
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
          } /*

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
                          every { getCurrentAuthenticatedMail(any()) } returns name
                          val organizationCreated =
                              organizationApiService.registerOrganization(
                                  makeOrganizationWithRole("id", name, role))

                          val allOrganizations = organizationApiService.findAllOrganizations(null, null)
                          if (shouldThrow) {
                            assertEquals(4, allOrganizations.size)
                          } else {
                            assertNotNull(allOrganizations)
                          }
                        }
                      }
            */
  @TestFactory
  fun `test unregister organization`() =
      mapOf(
          "viewer" to true,
          "editor" to true,
          "admin" to false,
          "validator" to true,
          "user" to true,
          "none" to true)
          .map { (role, shouldThrow) ->
            dynamicTest("Test unregister : $role") {
              val organizationRegistered =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole("id", defaultName, role))
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

  @TestFactory
  fun `test update organization`() =
      mapOf(
          "viewer" to true,
          "editor" to false,
          "admin" to false,
          "validator" to true,
          "user" to true,
          "none" to true)
          .map { (role, shouldThrow) ->
            dynamicTest("Test update : $role") {
              val organizationRegistered =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole("id", defaultName, role))
              if (shouldThrow) {
                assertThrows<CsmAccessForbiddenException> {
                  organizationApiService.updateOrganization(
                      organizationRegistered.id!!,
                      makeOrganizationWithRole(
                          organizationRegistered.id!!, "modifiedOrganization", role))
                }
              } else {
                assertDoesNotThrow {
                  organizationApiService.updateOrganization(
                      organizationRegistered.id!!,
                      makeOrganizationWithRole(
                          organizationRegistered.id!!, "modifiedOrganization", role))
                }
              }
            }
          }

  @TestFactory
  fun `test updateTenantCredentials organization`() =
      mapOf(
          "viewer" to true,
          "editor" to false,
          "admin" to false,
          "validator" to true,
          "user" to true,
          "none" to true)
          .map { (role, shouldThrow) ->
            dynamicTest("Test updateTenantCredentials : $role") {
              val organizationRegistered =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole("id", defaultName, role))
              if (shouldThrow) {
                assertThrows<CsmAccessForbiddenException> {
                  organizationApiService.updateTenantCredentialsByOrganizationId(
                      organizationRegistered.id!!, mapOf(Pair("", "")))
                }
              } else {
                assertDoesNotThrow {
                  organizationApiService.updateTenantCredentialsByOrganizationId(
                      organizationRegistered.id!!, mapOf(Pair("", "")))
                }
              }
            }
          }

  @TestFactory
  fun `test updateStorageConfiguration organization`() =
      mapOf(
          "viewer" to true,
          "editor" to false,
          "admin" to false,
          "validator" to true,
          "user" to true,
          "none" to true)
          .map { (role, shouldThrow) ->
            dynamicTest("Test updateStorageConfiguration : $role") {
              val organizationRegistered =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole("id", defaultName, role))
              if (shouldThrow) {
                assertThrows<CsmAccessForbiddenException> {
                  organizationApiService.updateStorageByOrganizationId(
                      organizationRegistered.id!!, OrganizationService())
                }
              } else {
                assertDoesNotThrow {
                  organizationApiService.updateStorageByOrganizationId(
                      organizationRegistered.id!!, OrganizationService())
                }
              }
            }
          }

  @TestFactory
  fun `test updateSolutionsContainerRegistry organization`() =
      mapOf(
          "viewer" to true,
          "editor" to false,
          "admin" to false,
          "validator" to true,
          "user" to true,
          "none" to true)
          .map { (role, shouldThrow) ->
            dynamicTest("Test updateSolutionsContainerRegistry : $role") {
              val organizationRegistered =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole("id", defaultName, role))
              if (shouldThrow) {
                assertThrows<CsmAccessForbiddenException> {
                  organizationApiService.updateSolutionsContainerRegistryByOrganizationId(
                      organizationRegistered.id!!, OrganizationService())
                }
              } else {
                assertDoesNotThrow {
                  organizationApiService.updateSolutionsContainerRegistryByOrganizationId(
                      organizationRegistered.id!!, OrganizationService())
                }
              }
            }
          }

  @TestFactory
  fun `test updateStorage organization`() =
      mapOf(
          "viewer" to true,
          "editor" to false,
          "admin" to false,
          "validator" to true,
          "user" to true,
          "none" to true)
          .map { (role, shouldThrow) ->
            dynamicTest("Test updateStorage : $role") {
              val organizationRegistered =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole("id", defaultName, role))
              if (shouldThrow) {
                assertThrows<CsmAccessForbiddenException> {
                  organizationApiService.updateStorageByOrganizationId(
                      organizationRegistered.id!!, OrganizationService())
                }
              } else {
                assertDoesNotThrow {
                  organizationApiService.updateStorageByOrganizationId(
                      organizationRegistered.id!!, OrganizationService())
                }
              }
            }
          }

  @TestFactory
  fun `test getOrganizationSecurity organization`() =
      mapOf(
          "viewer" to false,
          "editor" to false,
          "admin" to false,
          "validator" to true,
          "user" to false,
          "none" to true)
          .map { (role, shouldThrow) ->
            dynamicTest("Test getOrganizationSecurity : $role") {
              val organizationRegistered =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole("id", defaultName, role))
              if (shouldThrow) {
                assertThrows<CsmAccessForbiddenException> {
                  organizationApiService.getOrganizationSecurity(organizationRegistered.id!!)
                }
              } else {
                assertDoesNotThrow {
                  organizationApiService.getOrganizationSecurity(organizationRegistered.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test setOrganizationDefaultSecurity organization`() =
      mapOf(
          "viewer" to true,
          "editor" to true,
          "admin" to false,
          "validator" to true,
          "user" to true,
          "none" to true)
          .map { (role, shouldThrow) ->
            dynamicTest("Test setOrganizationDefaultSecurity : $role") {
              val organizationRegistered =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole("id", defaultName, role))
              if (shouldThrow) {
                assertThrows<CsmAccessForbiddenException> {
                  organizationApiService.setOrganizationDefaultSecurity(
                      organizationRegistered.id!!, OrganizationRole(role))
                }
              } else {
                assertDoesNotThrow {
                  organizationApiService.setOrganizationDefaultSecurity(
                      organizationRegistered.id!!, OrganizationRole(role))
                }
              }
            }
          }

  @TestFactory
  fun `test getOrganizationAccessControl organization`() =
      mapOf(
          "viewer" to false,
          "editor" to false,
          "admin" to false,
          "validator" to true,
          "user" to false,
          "none" to true)
          .map { (role, shouldThrow) ->
            dynamicTest("Test getOrganizationAccessControl : $role") {
              val organizationRegistered =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole("id", defaultName, role))
              if (shouldThrow) {
                assertThrows<CsmAccessForbiddenException> {
                  organizationApiService.getOrganizationAccessControl(
                      organizationRegistered.id!!, defaultName)
                }
              } else {
                assertDoesNotThrow {
                  organizationApiService.getOrganizationAccessControl(
                      organizationRegistered.id!!, defaultName)
                }
              }
            }
          }

  @TestFactory
  fun `test addOrganizationAccessControl organization`() =
      mapOf(
          "viewer" to true,
          "editor" to true,
          "admin" to false,
          "validator" to true,
          "user" to true,
          "none" to true)
          .map { (role, shouldThrow) ->
            dynamicTest("Test addOrganizationAccessControl : $role") {
              val organizationRegistered =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole("id", defaultName, role))
              if (shouldThrow) {
                assertThrows<CsmAccessForbiddenException> {
                  organizationApiService.addOrganizationAccessControl(
                      organizationRegistered.id!!, OrganizationAccessControl("id", "viewer"))
                }
              } else {
                assertDoesNotThrow {
                  organizationApiService.addOrganizationAccessControl(
                      organizationRegistered.id!!, OrganizationAccessControl("id", "viewer"))
                }
              }
            }
          }

  @TestFactory
  fun `test updateOrganizationAccessControl organization`() =
      mapOf(
          "viewer" to true,
          "editor" to true,
          "admin" to false,
          "validator" to true,
          "user" to true,
          "none" to true)
          .map { (role, shouldThrow) ->
            dynamicTest("Test updateOrganizationAccessControl : $role") {
              val organizationRegistered =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole("id", defaultName, role))
              if (shouldThrow) {
                assertThrows<CsmAccessForbiddenException> {
                  organizationApiService.updateOrganizationAccessControl(
                      organizationRegistered.id!!, "2$defaultName", OrganizationRole("user"))
                }
              } else {
                assertDoesNotThrow {
                  organizationApiService.updateOrganizationAccessControl(
                      organizationRegistered.id!!, "2$defaultName", OrganizationRole("user"))
                }
              }
            }
          }

  @TestFactory
  fun `test removeOrganizationAccessControl organization`() =
      mapOf(
          "viewer" to true,
          "editor" to true,
          "admin" to false,
          "validator" to true,
          "user" to true,
          "none" to true)
          .map { (role, shouldThrow) ->
            dynamicTest("Test removeOrganizationAccessControl : $role") {
              val organizationRegistered =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole("id", defaultName, role))
              if (shouldThrow) {
                assertThrows<CsmAccessForbiddenException> {
                  organizationApiService.removeOrganizationAccessControl(
                      organizationRegistered.id!!, "2$defaultName")
                }
              } else {
                assertDoesNotThrow {
                  organizationApiService.removeOrganizationAccessControl(
                      organizationRegistered.id!!, "2$defaultName")
                }
              }
            }
          }

  @TestFactory
  fun `test getOrganizationSecurityUsers`() =
      mapOf(
          "viewer" to false,
          "editor" to false,
          "admin" to false,
          "validator" to true,
          "user" to false,
          "none" to true)
          .map { (role, shouldThrow) ->
            dynamicTest("Test get users with role $role") {
              val organizationCreated =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole("id", defaultName, role))

              if (shouldThrow) {
                assertThrows<CsmAccessForbiddenException> {
                  organizationApiService.getOrganizationSecurityUsers(organizationCreated.id!!)
                }
              } else {
                assertDoesNotThrow {
                  organizationApiService.getOrganizationSecurityUsers(organizationCreated.id!!)
                }
              }
            }
          }
}
