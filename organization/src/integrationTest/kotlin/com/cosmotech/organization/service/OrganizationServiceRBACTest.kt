// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.organization.service

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.rbac.PERMISSION_DELETE
import com.cosmotech.api.rbac.PERMISSION_READ
import com.cosmotech.api.rbac.PERMISSION_READ_SECURITY
import com.cosmotech.api.rbac.PERMISSION_WRITE
import com.cosmotech.api.rbac.PERMISSION_WRITE_SECURITY
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
import com.cosmotech.organization.OrganizationApiServiceInterface
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationRole
import com.cosmotech.organization.domain.OrganizationSecurity
import com.redis.om.spring.indexing.RediSearchIndexer
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockkStatic
import java.util.*
import kotlin.test.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.runner.RunWith
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
class OrganizationServiceRBACTest : CsmRedisTestBase() {
  val CONNECTED_ADMIN_USER = "test.admin@cosmotech.com"
  val TEST_USER_MAIL = "testuser@mail.fr"

  // NEEDED: recreate indexes in redis
  @Autowired lateinit var rediSearchIndexer: RediSearchIndexer
  @Autowired lateinit var csmPlatformProperties: CsmPlatformProperties
  @Autowired lateinit var organizationApiService: OrganizationApiServiceInterface

  @BeforeAll
  fun globalSetup() {
    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
  }

  @BeforeEach
  fun setUp() {
    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
    every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "my.account-tester"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf()
    rediSearchIndexer.createIndexFor(Organization::class.java)
  }

  @TestFactory
  fun `test RBAC findAllOrganizations`() =
      mapOf(
              ROLE_VIEWER to 1,
              ROLE_EDITOR to 2,
              ROLE_VALIDATOR to 3,
              ROLE_USER to 4,
              ROLE_NONE to 4,
              ROLE_ADMIN to 5,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC findAllOrganizations : $role") {
              organizationApiService.registerOrganization(
                  mockOrganizationWithRole(id = TEST_USER_MAIL, role = role))

              val organizations = organizationApiService.findAllOrganizations(null, null)
              assertEquals(shouldThrow, organizations.size)
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
            DynamicTest.dynamicTest("Test RBAC findOrganizationById : $role") {
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL
              val organization =
                  organizationApiService.registerOrganization(
                      mockOrganizationWithRole(TEST_USER_MAIL, role))

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      organizationApiService.findOrganizationById(organization.id!!)
                    }
                assertEquals(
                    "RBAC ${organization.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
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
            DynamicTest.dynamicTest("Test RBAC unregisterOrganization : $role") {
              val organization =
                  organizationApiService.registerOrganization(
                      mockOrganizationWithRole(TEST_USER_MAIL, role))

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      organizationApiService.unregisterOrganization(organization.id!!)
                    }
                assertEquals(
                    "RBAC ${organization.id!!} - User does not have permission $PERMISSION_DELETE",
                    exception.message)
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
            DynamicTest.dynamicTest("Test RBAC updateOrganization : $role") {
              val organization =
                  organizationApiService.registerOrganization(
                      mockOrganizationWithRole(id = TEST_USER_MAIL, role = role))

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      organizationApiService.updateOrganization(
                          organization.id!!,
                          mockOrganizationWithRole(id = TEST_USER_MAIL, role = role))
                    }
                assertEquals(
                    "RBAC ${organization.id!!} - User does not have permission $PERMISSION_WRITE",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  organizationApiService.updateOrganization(
                      organization.id!!, mockOrganizationWithRole(id = TEST_USER_MAIL, role = role))
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC getOrganizationPermissions`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC getOrganizationPermissions : $role") {
              val organization =
                  organizationApiService.registerOrganization(
                      mockOrganizationWithRole(TEST_USER_MAIL, role))

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      organizationApiService.getOrganizationPermissions(organization.id!!, role)
                    }
                assertEquals(
                    "RBAC ${organization.id!!} - User does not have permission $PERMISSION_READ_SECURITY",
                    exception.message)
              } else
                  assertDoesNotThrow {
                    organizationApiService.getOrganizationPermissions(organization.id!!, role)
                  }
            }
          }

  @TestFactory
  fun `test RBAC getOrganizationSecurity`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC getOrganizationSecurity : $role") {
              val organization =
                  organizationApiService.registerOrganization(
                      mockOrganizationWithRole(TEST_USER_MAIL, role))

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      organizationApiService.getOrganizationSecurity(organization.id!!)
                    }
                assertEquals(
                    "RBAC ${organization.id!!} - User does not have permission $PERMISSION_READ_SECURITY",
                    exception.message)
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
            DynamicTest.dynamicTest("Test RBAC setOrganizationDefaultSecurity : $role") {
              val organization =
                  organizationApiService.registerOrganization(
                      mockOrganizationWithRole(TEST_USER_MAIL, role))

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      organizationApiService.setOrganizationDefaultSecurity(
                          organization.id!!, OrganizationRole(role))
                    }
                assertEquals(
                    "RBAC ${organization.id!!} - User does not have permission $PERMISSION_WRITE_SECURITY",
                    exception.message)
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
            DynamicTest.dynamicTest("Test RBAC addOrganizationAccessControl : $role") {
              val organization =
                  organizationApiService.registerOrganization(
                      mockOrganizationWithRole(TEST_USER_MAIL, role))

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      organizationApiService.addOrganizationAccessControl(
                          organization.id!!, OrganizationAccessControl("id", role))
                    }
                assertEquals(
                    "RBAC ${organization.id!!} - User does not have permission $PERMISSION_WRITE_SECURITY",
                    exception.message)
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
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC getOrganizationAccessControl : $role") {
              val organization =
                  organizationApiService.registerOrganization(
                      mockOrganizationWithRole(TEST_USER_MAIL, role))

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      organizationApiService.getOrganizationAccessControl(
                          organization.id!!, TEST_USER_MAIL)
                    }
                assertEquals(
                    "RBAC ${organization.id!!} - User does not have permission $PERMISSION_READ_SECURITY",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  organizationApiService.getOrganizationAccessControl(
                      organization.id!!, TEST_USER_MAIL)
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
            DynamicTest.dynamicTest("Test RBAC removeOrganizationAccessControl : $role") {
              val organization =
                  organizationApiService.registerOrganization(
                      mockOrganizationWithRole(TEST_USER_MAIL, role))

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      organizationApiService.removeOrganizationAccessControl(
                          organization.id!!, TEST_USER_MAIL)
                    }
                assertEquals(
                    "RBAC ${organization.id!!} - User does not have permission $PERMISSION_WRITE_SECURITY",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  organizationApiService.removeOrganizationAccessControl(
                      organization.id!!, TEST_USER_MAIL)
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
            DynamicTest.dynamicTest("Test RBAC updateOrganizationAccessControl : $role") {
              val organization =
                  organizationApiService.registerOrganization(
                      mockOrganizationWithRole(TEST_USER_MAIL, role))

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      organizationApiService.updateOrganizationAccessControl(
                          organization.id!!, TEST_USER_MAIL, OrganizationRole(role))
                    }
                assertEquals(
                    "RBAC ${organization.id!!} - User does not have permission $PERMISSION_WRITE_SECURITY",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  organizationApiService.updateOrganizationAccessControl(
                      organization.id!!, TEST_USER_MAIL, OrganizationRole(role))
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC getOrganizationSecurityUsers`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC getOrganizationSecurityUsers : $role") {
              val organization =
                  organizationApiService.registerOrganization(
                      mockOrganizationWithRole(TEST_USER_MAIL, role))

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      organizationApiService.getOrganizationSecurityUsers(organization.id!!)
                    }
                assertEquals(
                    "RBAC ${organization.id!!} - User does not have permission $PERMISSION_READ_SECURITY",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  organizationApiService.getOrganizationSecurityUsers(organization.id!!)
                }
              }
            }
          }

  fun mockOrganizationWithRole(id: String, role: String): Organization {
    return Organization(
        id = UUID.randomUUID().toString(),
        name = "Organization Name",
        ownerId = "my.account-tester@cosmotech.com",
        security =
            OrganizationSecurity(
                default = ROLE_NONE,
                accessControlList =
                    mutableListOf(
                        OrganizationAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                        OrganizationAccessControl(id = id, role = role))))
  }
}
