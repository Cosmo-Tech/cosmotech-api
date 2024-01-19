// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.solution.service

import com.azure.storage.blob.BlobServiceClient
import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.rbac.PERMISSION_CREATE_CHILDREN
import com.cosmotech.api.rbac.PERMISSION_DELETE
import com.cosmotech.api.rbac.PERMISSION_READ
import com.cosmotech.api.rbac.PERMISSION_READ_SECURITY
import com.cosmotech.api.rbac.PERMISSION_WRITE
import com.cosmotech.api.rbac.PERMISSION_WRITE_SECURITY
import com.cosmotech.api.rbac.ROLE_ADMIN
import com.cosmotech.api.rbac.ROLE_EDITOR
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.rbac.ROLE_USER
import com.cosmotech.api.rbac.ROLE_VIEWER
import com.cosmotech.api.tests.CsmRedisTestBase
import com.cosmotech.api.utils.ResourceScanner
import com.cosmotech.api.utils.getCurrentAccountIdentifier
import com.cosmotech.api.utils.getCurrentAuthenticatedRoles
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationSecurity
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.solution.domain.RunTemplate
import com.cosmotech.solution.domain.RunTemplateHandlerId
import com.cosmotech.solution.domain.RunTemplateParameter
import com.cosmotech.solution.domain.RunTemplateParameterGroup
import com.cosmotech.solution.domain.Solution
import com.cosmotech.solution.domain.SolutionAccessControl
import com.cosmotech.solution.domain.SolutionRole
import com.cosmotech.solution.domain.SolutionSecurity
import com.redis.om.spring.RediSearchIndexer
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockkStatic
import java.io.InputStream
import java.util.*
import kotlin.test.assertEquals
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.util.ReflectionTestUtils

@ActiveProfiles(profiles = ["solution-test"])
@ExtendWith(MockKExtension::class)
@ExtendWith(SpringExtension::class)
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SolutionServiceRBACTest : CsmRedisTestBase() {

  val TEST_USER_MAIL = "testuser@mail.fr"

  @MockK(relaxed = true) private lateinit var azureStorageBlobServiceClient: BlobServiceClient

  @Autowired lateinit var rediSearchIndexer: RediSearchIndexer

  @Autowired lateinit var organizationApiService: OrganizationApiService

  @Autowired lateinit var solutionApiService: SolutionApiService

  @Autowired lateinit var csmPlatformProperties: CsmPlatformProperties

  lateinit var organization: Organization
  lateinit var solution: Solution

  lateinit var organizationSaved: Organization
  lateinit var solutionSaved: Solution

  @MockK lateinit var resource: Resource
  @MockK lateinit var resourceScanner: ResourceScanner
  @MockK lateinit var inputStream: InputStream
  @MockK lateinit var resourceLoader: ResourceLoader

  @BeforeEach
  fun setUp() {
    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "test.user"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("user")

    rediSearchIndexer.createIndexFor(Organization::class.java)
    rediSearchIndexer.createIndexFor(Solution::class.java)
  }

  @TestFactory
  fun `test Organization RBAC findSolutionById`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC findSolutionById : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.findSolutionById(
                          organizationSaved.id!!, solutionSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.findSolutionById(organizationSaved.id!!, solutionSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC findSolutionById`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Solution RBAC findSolutionById : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.findSolutionById(
                          organizationSaved.id!!, solutionSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.findSolutionById(organizationSaved.id!!, solutionSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC findAllSolutions`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC findAllSolutions : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.registerOrganization(
                      makeOrganizationWithRole(
                          organizationId = "id", id = TEST_USER_MAIL, role = role))
              solutionApiService.createSolution(
                  organizationSaved.id!!,
                  makeSolutionWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN))
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.findAllSolutions(organizationSaved.id!!, null, null)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.findAllSolutions(organizationSaved.id!!, null, null)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC createSolution`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC createSolution : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.createSolution(organizationSaved.id!!, solution)
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_CREATE_CHILDREN",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC deleteSolution`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC deleteSolution : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.deleteSolution(organizationSaved.id!!, solutionSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.deleteSolution(organizationSaved.id!!, solutionSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC deleteSolution`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Solution RBAC deleteSolution : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.deleteSolution(organizationSaved.id!!, solutionSaved.id!!)
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_DELETE",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  solutionApiService.deleteSolution(organizationSaved.id!!, solutionSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC updateSolution`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC updateSolution : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.updateSolution(
                          organizationSaved.id!!, solutionSaved.id!!, solution)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.updateSolution(
                      organizationSaved.id!!, solutionSaved.id!!, solution)
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC updateSolution`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Solution RBAC updateSolution : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.registerOrganization(organization)
              solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = role)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.updateSolution(
                          organizationSaved.id!!, solutionSaved.id!!, solution)
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_WRITE",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  solutionApiService.updateSolution(
                      organizationSaved.id!!, solutionSaved.id!!, solution)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC addSolutionAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC addSolutionAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.addSolutionAccessControl(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          SolutionAccessControl("user", ROLE_USER))
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.addSolutionAccessControl(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      SolutionAccessControl("user", ROLE_USER))
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC addSolutionAccessControl`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Solution RBAC addSolutionAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.registerOrganization(organization)
              solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.addSolutionAccessControl(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          SolutionAccessControl("user", ROLE_USER))
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_WRITE_SECURITY",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  solutionApiService.addSolutionAccessControl(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      SolutionAccessControl("user", ROLE_USER))
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC getSolutionAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC getSolutionAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.getSolutionAccessControl(
                          organizationSaved.id!!, solutionSaved.id!!, TEST_USER_MAIL)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.getSolutionAccessControl(
                      organizationSaved.id!!, solutionSaved.id!!, TEST_USER_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC getSolutionAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Solution RBAC getSolutionAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.registerOrganization(organization)
              solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.getSolutionAccessControl(
                          organizationSaved.id!!, solutionSaved.id!!, TEST_USER_MAIL)
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ_SECURITY",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  solutionApiService.getSolutionAccessControl(
                      organizationSaved.id!!, solutionSaved.id!!, TEST_USER_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC getSolutionSecurityUsers`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC getSolutionSecurityUsers : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.getSolutionSecurityUsers(
                          organizationSaved.id!!, solutionSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.getSolutionSecurityUsers(
                      organizationSaved.id!!, solutionSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC getSolutionSecurityUsers`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Solution RBAC getSolutionSecurityUsers : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.registerOrganization(organization)
              solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.getSolutionSecurityUsers(
                          organizationSaved.id!!, solutionSaved.id!!)
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ_SECURITY",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  solutionApiService.getSolutionSecurityUsers(
                      organizationSaved.id!!, solutionSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC removeSolutionAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC removeSolutionAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.removeSolutionAccessControl(
                          organizationSaved.id!!, solutionSaved.id!!, TEST_USER_MAIL)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.removeSolutionAccessControl(
                      organizationSaved.id!!, solutionSaved.id!!, TEST_USER_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC removeSolutionAccessControl`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Solution RBAC removeSolutionAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.registerOrganization(organization)
              solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.removeSolutionAccessControl(
                          organizationSaved.id!!, solutionSaved.id!!, TEST_USER_MAIL)
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_WRITE_SECURITY",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  solutionApiService.removeSolutionAccessControl(
                      organizationSaved.id!!, solutionSaved.id!!, TEST_USER_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC updateSolutionAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC updateSolutionAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.updateSolutionAccessControl(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          TEST_USER_MAIL,
                          SolutionRole(ROLE_USER))
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.updateSolutionAccessControl(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      TEST_USER_MAIL,
                      SolutionRole(ROLE_USER))
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC updateSolutionAccessControl`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Solution RBAC updateSolutionAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.registerOrganization(organization)
              solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.updateSolutionAccessControl(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          TEST_USER_MAIL,
                          SolutionRole(ROLE_USER))
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_WRITE_SECURITY",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  solutionApiService.updateSolutionAccessControl(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      TEST_USER_MAIL,
                      SolutionRole(ROLE_USER))
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC addOrReplaceParameters`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC addOrReplaceParameters : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              val runTemplateParameter = RunTemplateParameter("id")

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.addOrReplaceParameters(
                          organizationSaved.id!!, solutionSaved.id!!, listOf(runTemplateParameter))
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.addOrReplaceParameters(
                      organizationSaved.id!!, solutionSaved.id!!, listOf(runTemplateParameter))
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC addOrReplaceParameters`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Solution RBAC addOrReplaceParameters : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              val runTemplateParameter = RunTemplateParameter("id")

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.addOrReplaceParameters(
                          organizationSaved.id!!, solutionSaved.id!!, listOf(runTemplateParameter))
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_WRITE",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  solutionApiService.addOrReplaceParameters(
                      organizationSaved.id!!, solutionSaved.id!!, listOf(runTemplateParameter))
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC removeAllSolutionParameters`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC removeAllSolutionParameters : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.removeAllSolutionParameters(
                          organizationSaved.id!!, solutionSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.removeAllSolutionParameters(
                      organizationSaved.id!!, solutionSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC removeAllSolutionParameters`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Solution RBAC removeAllSolutionParameters : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.removeAllSolutionParameters(
                          organizationSaved.id!!, solutionSaved.id!!)
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_DELETE",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  solutionApiService.removeAllSolutionParameters(
                      organizationSaved.id!!, solutionSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC addOrReplaceParameterGroups`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC addOrReplaceParameterGroups : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              val runTemplateParameterGroup = RunTemplateParameterGroup("id")

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.addOrReplaceParameterGroups(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          listOf(runTemplateParameterGroup))
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.addOrReplaceParameterGroups(
                      organizationSaved.id!!, solutionSaved.id!!, listOf(runTemplateParameterGroup))
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC addOrReplaceParameterGroups`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Solution RBAC addOrReplaceParameterGroups : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              val runTemplateParameterGroup = RunTemplateParameterGroup("id")

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.addOrReplaceParameterGroups(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          listOf(runTemplateParameterGroup))
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_WRITE",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  solutionApiService.addOrReplaceParameterGroups(
                      organizationSaved.id!!, solutionSaved.id!!, listOf(runTemplateParameterGroup))
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC removeAllSolutionParameterGroups`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest(
                "Test Organization RBAC removeAllSolutionParameterGroups : $role") {
                  every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

                  val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
                  organizationSaved = organizationApiService.registerOrganization(organization)
                  val solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
                  solutionSaved =
                      solutionApiService.createSolution(organizationSaved.id!!, solution)

                  every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

                  if (shouldThrow) {
                    val exception =
                        assertThrows<CsmAccessForbiddenException> {
                          solutionApiService.removeAllSolutionParameterGroups(
                              organizationSaved.id!!, solutionSaved.id!!)
                        }
                    assertEquals(
                        "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                        exception.message)
                  } else {
                    assertDoesNotThrow {
                      solutionApiService.removeAllSolutionParameterGroups(
                          organizationSaved.id!!, solutionSaved.id!!)
                    }
                  }
                }
          }

  @TestFactory
  fun `test Solution RBAC removeAllSolutionParameterGroups`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Solution RBAC removeAllSolutionParameterGroups : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.removeAllSolutionParameterGroups(
                          organizationSaved.id!!, solutionSaved.id!!)
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_DELETE",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  solutionApiService.removeAllSolutionParameterGroups(
                      organizationSaved.id!!, solutionSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC addOrReplaceRunTemplates`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC addOrReplaceRunTemplates : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              val runTemplate = RunTemplate("id")

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.addOrReplaceRunTemplates(
                          organizationSaved.id!!, solutionSaved.id!!, listOf(runTemplate))
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.addOrReplaceRunTemplates(
                      organizationSaved.id!!, solutionSaved.id!!, listOf(runTemplate))
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC addOrReplaceRunTemplates`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Solution RBAC addOrReplaceRunTemplates : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              val runTemplate = RunTemplate("id")

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.addOrReplaceRunTemplates(
                          organizationSaved.id!!, solutionSaved.id!!, listOf(runTemplate))
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_WRITE",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  solutionApiService.addOrReplaceRunTemplates(
                      organizationSaved.id!!, solutionSaved.id!!, listOf(runTemplate))
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC removeAllRunTemplates`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC addOrReplaceRunTemplates : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.removeAllRunTemplates(
                          organizationSaved.id!!, solutionSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.removeAllRunTemplates(
                      organizationSaved.id!!, solutionSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC removeAllRunTemplates`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Solution RBAC addOrReplaceRunTemplates : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.removeAllRunTemplates(
                          organizationSaved.id!!, solutionSaved.id!!)
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_DELETE",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  solutionApiService.removeAllRunTemplates(
                      organizationSaved.id!!, solutionSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC deleteSolutionRunTemplate`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC deleteSolutionRunTemplate : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.deleteSolutionRunTemplate(
                          organizationSaved.id!!, solutionSaved.id!!, "runTemplate")
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.deleteSolutionRunTemplate(
                      organizationSaved.id!!, solutionSaved.id!!, "runTemplate")
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC deleteSolutionRunTemplate`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Solution RBAC deleteSolutionRunTemplate : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.deleteSolutionRunTemplate(
                          organizationSaved.id!!, solutionSaved.id!!, "runTemplate")
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_DELETE",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  solutionApiService.deleteSolutionRunTemplate(
                      organizationSaved.id!!, solutionSaved.id!!, "runTemplate")
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC updateSolutionRunTemplate`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC updateSolutionRunTemplate : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              val runTemplate = RunTemplate("runTemplate", "name")

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.updateSolutionRunTemplate(
                          organizationSaved.id!!, solutionSaved.id!!, "runTemplate", runTemplate)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.updateSolutionRunTemplate(
                      organizationSaved.id!!, solutionSaved.id!!, "runTemplate", runTemplate)
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC updateSolutionRunTemplate`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Solution RBAC updateSolutionRunTemplate : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              val runTemplate = RunTemplate("runTemplate", "name")

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.updateSolutionRunTemplate(
                          organizationSaved.id!!, solutionSaved.id!!, "runTemplate", runTemplate)
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_WRITE",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  solutionApiService.updateSolutionRunTemplate(
                      organizationSaved.id!!, solutionSaved.id!!, "runTemplate", runTemplate)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC downloadRunTemplateHandler`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC downloadRunTemplateHandler : $role") {
              ReflectionTestUtils.setField(
                  solutionApiService,
                  "azureStorageBlobServiceClient",
                  azureStorageBlobServiceClient)
              ReflectionTestUtils.setField(solutionApiService, "resourceLoader", resourceLoader)

              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              val runTemplateHandlerId = RunTemplateHandlerId.values().first()

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.downloadRunTemplateHandler(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          "runTemplate",
                          runTemplateHandlerId)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                  every { resourceLoader.getResource(any()).inputStream } returns
                          (this::class.java.getResource("/test_download.zip")?.openStream()!!)

                  assertDoesNotThrow {
                  solutionApiService.downloadRunTemplateHandler(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      "runTemplate",
                      runTemplateHandlerId)
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC downloadRunTemplateHandler`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Solution RBAC downloadRunTemplateHandler : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              val runTemplateHandlerId = RunTemplateHandlerId.values().first()

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.downloadRunTemplateHandler(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          "runTemplate",
                          runTemplateHandlerId)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                every { resourceLoader.getResource(any()).inputStream } returns
                    (this::class.java.getResource("/test_download.zip")?.openStream()!!)

                assertDoesNotThrow {
                  solutionApiService.downloadRunTemplateHandler(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      "runTemplate",
                      runTemplateHandlerId)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC uploadRunTemplateHandler`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC uploadRunTemplateHandler : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              ReflectionTestUtils.setField(solutionApiService, "resourceScanner", resourceScanner)
              every { resourceScanner.scanMimeTypes(any(), any()) } returns Unit
              mockkStatic(ArchiveStreamFactory::class)
              every { ArchiveStreamFactory.detect(any()) } returns "zip"
              ReflectionTestUtils.setField(
                  solutionApiService,
                  "azureStorageBlobServiceClient",
                  azureStorageBlobServiceClient)
              every {
                azureStorageBlobServiceClient
                    .getBlobContainerClient(any())
                    .getBlobClient(any())
                    .upload(any(), any(), any())
              } returns Unit

              every { resource.contentLength() } returns 0
              every { resource.getInputStream() } returns inputStream
              every { inputStream.read() } returns 0

              val runTemplateHandlerId = RunTemplateHandlerId.values().first()

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.uploadRunTemplateHandler(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          "runTemplate",
                          runTemplateHandlerId,
                          resource,
                          true)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.uploadRunTemplateHandler(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      "runTemplate",
                      runTemplateHandlerId,
                      resource,
                      true)
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC uploadRunTemplateHandler`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Solution RBAC uploadRunTemplateHandler : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              ReflectionTestUtils.setField(solutionApiService, "resourceScanner", resourceScanner)
              every { resourceScanner.scanMimeTypes(any(), any()) } returns Unit
              mockkStatic(ArchiveStreamFactory::class)
              every { ArchiveStreamFactory.detect(any()) } returns "zip"
              ReflectionTestUtils.setField(
                  solutionApiService,
                  "azureStorageBlobServiceClient",
                  azureStorageBlobServiceClient)
              every {
                azureStorageBlobServiceClient
                    .getBlobContainerClient(any())
                    .getBlobClient(any())
                    .upload(any(), any(), any())
              } returns Unit

              every { resource.contentLength() } returns 0
              every { resource.getInputStream() } returns inputStream
              every { inputStream.read() } returns 0

              val runTemplateHandlerId = RunTemplateHandlerId.values().first()

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.uploadRunTemplateHandler(
                          organizationSaved.id!!,
                          solutionSaved.id!!,
                          "runTemplate",
                          runTemplateHandlerId,
                          resource,
                          true)
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_WRITE",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  solutionApiService.uploadRunTemplateHandler(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      "runTemplate",
                      runTemplateHandlerId,
                      resource,
                      true)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC getSolutionSecurity`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC getSolutionSecurity : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val solution =
                  makeSolutionWithRole(organizationSaved.id!!, TEST_USER_MAIL, role = ROLE_ADMIN)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.getSolutionSecurity(
                          organizationSaved.id!!, solutionSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.getSolutionSecurity(organizationSaved.id!!, solutionSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC getSolutionSecurity`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Solution RBAC getSolutionSecurity : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val solution =
                  makeSolutionWithRole(organizationSaved.id!!, TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.getSolutionSecurity(
                          organizationSaved.id!!, solutionSaved.id!!)
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ_SECURITY",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  solutionApiService.getSolutionSecurity(organizationSaved.id!!, solutionSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC setSolutionDefaultSecurity`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC setSolutionDefaultSecurity : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val solution =
                  makeSolutionWithRole(organizationSaved.id!!, TEST_USER_MAIL, role = ROLE_ADMIN)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.setSolutionDefaultSecurity(
                          organizationSaved.id!!, solutionSaved.id!!, SolutionRole(ROLE_VIEWER))
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.setSolutionDefaultSecurity(
                      organizationSaved.id!!, solutionSaved.id!!, SolutionRole(ROLE_VIEWER))
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC setSolutionDefaultSecurity`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Solution RBAC setSolutionDefaultSecurity : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val solution =
                  makeSolutionWithRole(organizationSaved.id!!, TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.setSolutionDefaultSecurity(
                          organizationSaved.id!!, solutionSaved.id!!, SolutionRole(ROLE_VIEWER))
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${solutionSaved.id!!} - User does not have permission $PERMISSION_WRITE_SECURITY",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  solutionApiService.setSolutionDefaultSecurity(
                      organizationSaved.id!!, solutionSaved.id!!, SolutionRole(ROLE_VIEWER))
                }
              }
            }
          }

  fun makeOrganizationWithRole(
      organizationId: String = "organization_id",
      id: String,
      role: String
  ): Organization {
    return Organization(
        id = organizationId,
        name = "Organization Name",
        ownerId = "my.account-tester@cosmotech.com",
        security =
            OrganizationSecurity(
                default = ROLE_NONE,
                accessControlList =
                    mutableListOf(
                        OrganizationAccessControl(id = CONNECTED_ADMIN_USER, role = ROLE_ADMIN),
                        OrganizationAccessControl(id = id, role = role))))
  }

  fun makeSolutionWithRole(
      organizationId: String = organizationSaved.id!!,
      id: String,
      role: String
  ): Solution {
    return Solution(
        id = "solutionId",
        key = UUID.randomUUID().toString(),
        name = "My solution",
        organizationId = organizationId,
        ownerId = "ownerId",
        runTemplates = mutableListOf(RunTemplate("runTemplate")),
        security =
            SolutionSecurity(
                default = ROLE_NONE,
                accessControlList =
                    mutableListOf(
                        SolutionAccessControl(id = CONNECTED_ADMIN_USER, role = ROLE_ADMIN),
                        SolutionAccessControl(id = id, role = role))))
  }
}
