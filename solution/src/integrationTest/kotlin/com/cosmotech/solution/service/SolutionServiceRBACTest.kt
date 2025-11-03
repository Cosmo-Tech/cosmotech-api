// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.solution.service

import com.cosmotech.common.config.CsmPlatformProperties
import com.cosmotech.common.containerregistry.ContainerRegistryService
import com.cosmotech.common.exceptions.CsmAccessForbiddenException
import com.cosmotech.common.rbac.PERMISSION_CREATE_CHILDREN
import com.cosmotech.common.rbac.PERMISSION_DELETE
import com.cosmotech.common.rbac.PERMISSION_READ
import com.cosmotech.common.rbac.PERMISSION_READ_SECURITY
import com.cosmotech.common.rbac.PERMISSION_WRITE
import com.cosmotech.common.rbac.PERMISSION_WRITE_SECURITY
import com.cosmotech.common.rbac.ROLE_ADMIN
import com.cosmotech.common.rbac.ROLE_EDITOR
import com.cosmotech.common.rbac.ROLE_NONE
import com.cosmotech.common.rbac.ROLE_USER
import com.cosmotech.common.rbac.ROLE_VIEWER
import com.cosmotech.common.tests.CsmTestBase
import com.cosmotech.common.utils.ResourceScanner
import com.cosmotech.common.utils.getCurrentAccountGroups
import com.cosmotech.common.utils.getCurrentAccountIdentifier
import com.cosmotech.common.utils.getCurrentAuthenticatedRoles
import com.cosmotech.common.utils.getCurrentAuthenticatedUserName
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationCreateRequest
import com.cosmotech.organization.domain.OrganizationSecurity
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.solution.domain.*
import com.redis.om.spring.indexing.RediSearchIndexer
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.mockkStatic
import java.io.InputStream
import java.util.*
import kotlin.test.assertEquals
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
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.util.ReflectionTestUtils

@ActiveProfiles(profiles = ["solution-test"])
@ExtendWith(MockKExtension::class)
@ExtendWith(SpringExtension::class)
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SolutionServiceRBACTest : CsmTestBase() {

  val TEST_USER_MAIL = "testuser@mail.fr"

  @Autowired lateinit var rediSearchIndexer: RediSearchIndexer

  @Autowired lateinit var organizationApiService: OrganizationApiService

  @Autowired lateinit var solutionApiService: SolutionApiService

  @Autowired lateinit var csmPlatformProperties: CsmPlatformProperties

  private var containerRegistryService: ContainerRegistryService = mockk(relaxed = true)

  lateinit var organization: OrganizationCreateRequest
  lateinit var solution: SolutionCreateRequest

  lateinit var organizationSaved: Organization
  lateinit var solutionSaved: Solution

  @MockK lateinit var resource: Resource
  @MockK lateinit var resourceScanner: ResourceScanner
  @MockK lateinit var inputStream: InputStream

  @BeforeEach
  fun setUp() {
    mockkStatic("com.cosmotech.common.utils.SecurityUtilsKt")
    ReflectionTestUtils.setField(
        solutionApiService, "containerRegistryService", containerRegistryService)
    every { containerRegistryService.getImageLabel(any(), any(), any()) } returns null
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
    every { getCurrentAccountGroups(any()) } returns listOf("myTestGroup")

    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "test.user"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("user")

    rediSearchIndexer.createIndexFor(Organization::class.java)
    rediSearchIndexer.createIndexFor(Solution::class.java)
  }

  @TestFactory
  fun `test Organization RBAC getSolution`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC getSolution : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationCreateRequest(id = TEST_USER_MAIL, role = role)
              organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.getSolution(organizationSaved.id, solutionSaved.id)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.getSolution(organizationSaved.id, solutionSaved.id)
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC getSolution`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Solution RBAC getSolution : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization =
                  makeOrganizationCreateRequest(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.getSolution(organizationSaved.id, solutionSaved.id)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.getSolution(organizationSaved.id, solutionSaved.id)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC listSolutions`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC listSolutions : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              organizationSaved =
                  organizationApiService.createOrganization(
                      makeOrganizationCreateRequest(id = TEST_USER_MAIL, role = role))
              solutionApiService.createSolution(
                  organizationSaved.id,
                  makeSolutionWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN))
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.listSolutions(organizationSaved.id, null, null)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.listSolutions(organizationSaved.id, null, null)
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

              organization = makeOrganizationCreateRequest(id = TEST_USER_MAIL, role = role)
              organizationSaved = organizationApiService.createOrganization(organization)
              solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.createSolution(organizationSaved.id, solution)
                    }

                assertEquals(
                    "RBAC ${organizationSaved.id} - User does not have permission $PERMISSION_CREATE_CHILDREN",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.createSolution(organizationSaved.id, solution)
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

              val organization = makeOrganizationCreateRequest(id = TEST_USER_MAIL, role = role)
              organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.deleteSolution(organizationSaved.id, solutionSaved.id)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.deleteSolution(organizationSaved.id, solutionSaved.id)
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

              val organization =
                  makeOrganizationCreateRequest(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.deleteSolution(organizationSaved.id, solutionSaved.id)
                    }

                assertEquals(
                    "RBAC ${solutionSaved.id} - User does not have permission $PERMISSION_DELETE",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.deleteSolution(organizationSaved.id, solutionSaved.id)
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

              organization = makeOrganizationCreateRequest(id = TEST_USER_MAIL, role = role)
              organizationSaved = organizationApiService.createOrganization(organization)
              solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)

              val solutionUpdateRequest = SolutionUpdateRequest(name = "new_name")

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.updateSolution(
                          organizationSaved.id, solutionSaved.id, solutionUpdateRequest)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.updateSolution(
                      organizationSaved.id, solutionSaved.id, solutionUpdateRequest)
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

              organization = makeOrganizationCreateRequest(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.createOrganization(organization)
              solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = role)

              val solutionUpdateRequest = SolutionUpdateRequest(name = "new_name")

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.updateSolution(
                          organizationSaved.id, solutionSaved.id, solutionUpdateRequest)
                    }

                assertEquals(
                    "RBAC ${solutionSaved.id} - User does not have permission $PERMISSION_WRITE",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.updateSolution(
                      organizationSaved.id, solutionSaved.id, solutionUpdateRequest)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC createSolutionAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC createSolutionAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              organization = makeOrganizationCreateRequest(id = TEST_USER_MAIL, role = role)
              organizationSaved = organizationApiService.createOrganization(organization)
              solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.createSolutionAccessControl(
                          organizationSaved.id,
                          solutionSaved.id,
                          SolutionAccessControl("user", ROLE_USER))
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.createSolutionAccessControl(
                      organizationSaved.id,
                      solutionSaved.id,
                      SolutionAccessControl("user", ROLE_USER))
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC createSolutionAccessControl`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Solution RBAC createSolutionAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              organization = makeOrganizationCreateRequest(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.createOrganization(organization)
              solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.createSolutionAccessControl(
                          organizationSaved.id,
                          solutionSaved.id,
                          SolutionAccessControl("user", ROLE_USER))
                    }

                assertEquals(
                    "RBAC ${solutionSaved.id} - User does not have permission $PERMISSION_WRITE_SECURITY",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.createSolutionAccessControl(
                      organizationSaved.id,
                      solutionSaved.id,
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

              organization = makeOrganizationCreateRequest(id = TEST_USER_MAIL, role = role)
              organizationSaved = organizationApiService.createOrganization(organization)
              solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.getSolutionAccessControl(
                          organizationSaved.id, solutionSaved.id, TEST_USER_MAIL)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.getSolutionAccessControl(
                      organizationSaved.id, solutionSaved.id, TEST_USER_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC getSolutionAccessControl`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Solution RBAC getSolutionAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              organization = makeOrganizationCreateRequest(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.createOrganization(organization)
              solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.getSolutionAccessControl(
                          organizationSaved.id, solutionSaved.id, TEST_USER_MAIL)
                    }

                assertEquals(
                    "RBAC ${solutionSaved.id} - User does not have permission $PERMISSION_READ_SECURITY",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.getSolutionAccessControl(
                      organizationSaved.id, solutionSaved.id, TEST_USER_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC listSolutionSecurityUsers`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC listSolutionSecurityUsers : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              organization = makeOrganizationCreateRequest(id = TEST_USER_MAIL, role = role)
              organizationSaved = organizationApiService.createOrganization(organization)
              solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.listSolutionSecurityUsers(
                          organizationSaved.id, solutionSaved.id)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.listSolutionSecurityUsers(
                      organizationSaved.id, solutionSaved.id)
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC listSolutionSecurityUsers`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Solution RBAC listSolutionSecurityUsers : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              organization = makeOrganizationCreateRequest(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.createOrganization(organization)
              solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.listSolutionSecurityUsers(
                          organizationSaved.id, solutionSaved.id)
                    }

                assertEquals(
                    "RBAC ${solutionSaved.id} - User does not have permission $PERMISSION_READ_SECURITY",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.listSolutionSecurityUsers(
                      organizationSaved.id, solutionSaved.id)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC deleteSolutionAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC deleteSolutionAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              organization = makeOrganizationCreateRequest(id = TEST_USER_MAIL, role = role)
              organizationSaved = organizationApiService.createOrganization(organization)
              solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.deleteSolutionAccessControl(
                          organizationSaved.id, solutionSaved.id, TEST_USER_MAIL)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.deleteSolutionAccessControl(
                      organizationSaved.id, solutionSaved.id, TEST_USER_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC deleteSolutionAccessControl`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Solution RBAC deleteSolutionAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              organization = makeOrganizationCreateRequest(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.createOrganization(organization)
              solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.deleteSolutionAccessControl(
                          organizationSaved.id, solutionSaved.id, TEST_USER_MAIL)
                    }

                assertEquals(
                    "RBAC ${solutionSaved.id} - User does not have permission $PERMISSION_WRITE_SECURITY",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.deleteSolutionAccessControl(
                      organizationSaved.id, solutionSaved.id, TEST_USER_MAIL)
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

              organization = makeOrganizationCreateRequest(id = TEST_USER_MAIL, role = role)
              organizationSaved = organizationApiService.createOrganization(organization)
              solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.updateSolutionAccessControl(
                          organizationSaved.id,
                          solutionSaved.id,
                          TEST_USER_MAIL,
                          SolutionRole(ROLE_USER))
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.updateSolutionAccessControl(
                      organizationSaved.id,
                      solutionSaved.id,
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

              organization = makeOrganizationCreateRequest(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.createOrganization(organization)
              solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.updateSolutionAccessControl(
                          organizationSaved.id,
                          solutionSaved.id,
                          TEST_USER_MAIL,
                          SolutionRole(ROLE_USER))
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id} - User does not have permission $PERMISSION_WRITE_SECURITY",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.updateSolutionAccessControl(
                      organizationSaved.id,
                      solutionSaved.id,
                      TEST_USER_MAIL,
                      SolutionRole(ROLE_USER))
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

              val organization = makeOrganizationCreateRequest(id = TEST_USER_MAIL, role = role)
              organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.deleteSolutionRunTemplate(
                          organizationSaved.id, solutionSaved.id, "runTemplate")
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.deleteSolutionRunTemplate(
                      organizationSaved.id, solutionSaved.id, "runTemplate")
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC deleteSolutionRunTemplate`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Solution RBAC deleteSolutionRunTemplate : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization =
                  makeOrganizationCreateRequest(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.deleteSolutionRunTemplate(
                          organizationSaved.id, solutionSaved.id, "runTemplate")
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id} - User does not have permission $PERMISSION_WRITE",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.deleteSolutionRunTemplate(
                      organizationSaved.id, solutionSaved.id, "runTemplate")
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

              val organization = makeOrganizationCreateRequest(id = TEST_USER_MAIL, role = role)
              organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              val runTemplate = RunTemplateUpdateRequest(name = "name")

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.updateSolutionRunTemplate(
                          organizationSaved.id, solutionSaved.id, "runTemplate", runTemplate)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.updateSolutionRunTemplate(
                      organizationSaved.id, solutionSaved.id, "runTemplate", runTemplate)
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

              val organization =
                  makeOrganizationCreateRequest(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolutionWithRole(id = TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              val runTemplate = RunTemplateUpdateRequest(name = "name")

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.updateSolutionRunTemplate(
                          organizationSaved.id, solutionSaved.id, "runTemplate", runTemplate)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id} - User does not have permission $PERMISSION_WRITE",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.updateSolutionRunTemplate(
                      organizationSaved.id, solutionSaved.id, "runTemplate", runTemplate)
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

              val organization = makeOrganizationCreateRequest(id = TEST_USER_MAIL, role = role)
              organizationSaved = organizationApiService.createOrganization(organization)
              val solution =
                  makeSolutionWithRole(organizationSaved.id, TEST_USER_MAIL, role = ROLE_ADMIN)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.getSolutionSecurity(organizationSaved.id, solutionSaved.id)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.getSolutionSecurity(organizationSaved.id, solutionSaved.id)
                }
              }
            }
          }

  @TestFactory
  fun `test Solution RBAC getSolutionSecurity`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Solution RBAC getSolutionSecurity : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization =
                  makeOrganizationCreateRequest(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolutionWithRole(organizationSaved.id, TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.getSolutionSecurity(organizationSaved.id, solutionSaved.id)
                    }

                assertEquals(
                    "RBAC ${solutionSaved.id} - User does not have permission $PERMISSION_READ_SECURITY",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.getSolutionSecurity(organizationSaved.id, solutionSaved.id)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC updateSolutionDefaultSecurity`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest(
                "Test Organization RBAC updateSolutionDefaultSecurity : $role") {
                  every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

                  val organization = makeOrganizationCreateRequest(id = TEST_USER_MAIL, role = role)
                  organizationSaved = organizationApiService.createOrganization(organization)
                  val solution =
                      makeSolutionWithRole(organizationSaved.id, TEST_USER_MAIL, role = ROLE_ADMIN)
                  solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

                  every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

                  if (shouldThrow) {
                    val exception =
                        assertThrows<CsmAccessForbiddenException> {
                          solutionApiService.updateSolutionDefaultSecurity(
                              organizationSaved.id, solutionSaved.id, SolutionRole(ROLE_VIEWER))
                        }
                    assertEquals(
                        "RBAC ${organizationSaved.id} - User does not have permission $PERMISSION_READ",
                        exception.message)
                  } else {
                    assertDoesNotThrow {
                      solutionApiService.updateSolutionDefaultSecurity(
                          organizationSaved.id, solutionSaved.id, SolutionRole(ROLE_VIEWER))
                    }
                  }
                }
          }

  @TestFactory
  fun `test Solution RBAC updateSolutionDefaultSecurity`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Solution RBAC updateSolutionDefaultSecurity : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization =
                  makeOrganizationCreateRequest(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolutionWithRole(organizationSaved.id, TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.updateSolutionDefaultSecurity(
                          organizationSaved.id, solutionSaved.id, SolutionRole(ROLE_VIEWER))
                    }

                assertEquals(
                    "RBAC ${solutionSaved.id} - User does not have permission $PERMISSION_WRITE_SECURITY",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.updateSolutionDefaultSecurity(
                      organizationSaved.id, solutionSaved.id, SolutionRole(ROLE_VIEWER))
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC list solution parameters`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC listSolutionParameters : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization =
                  makeOrganizationCreateRequest(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolutionWithRole(organizationSaved.id, TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.listSolutionParameters(
                          organizationSaved.id, solutionSaved.id)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.listSolutionParameters(organizationSaved.id, solutionSaved.id)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC get solution parameter`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC getSolutionParameter : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization =
                  makeOrganizationCreateRequest(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolutionWithRole(organizationSaved.id, TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.getSolutionParameter(
                          organizationSaved.id, solutionSaved.id, "parameter")
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.getSolutionParameter(
                      organizationSaved.id, solutionSaved.id, "parameter")
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC create solution parameter`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC createSolutionParameter : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization =
                  makeOrganizationCreateRequest(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolutionWithRole(organizationSaved.id, TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              val parameterToCreate =
                  RunTemplateParameterCreateRequest(id = "parameter2", varType = "string")

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.createSolutionParameter(
                          organizationSaved.id, solutionSaved.id, parameterToCreate)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id} - User does not have permission $PERMISSION_WRITE",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.createSolutionParameter(
                      organizationSaved.id, solutionSaved.id, parameterToCreate)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC update solution parameter`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC updateSolutionParameter : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization =
                  makeOrganizationCreateRequest(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolutionWithRole(organizationSaved.id, TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              val parameterToUpdate = RunTemplateParameterUpdateRequest(varType = "description")

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.updateSolutionParameter(
                          organizationSaved.id, solutionSaved.id, "parameter", parameterToUpdate)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id} - User does not have permission $PERMISSION_WRITE",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.updateSolutionParameter(
                      organizationSaved.id, solutionSaved.id, "parameter", parameterToUpdate)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC delete solution parameter`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC deleteSolutionParameter : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization =
                  makeOrganizationCreateRequest(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolutionWithRole(organizationSaved.id, TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.deleteSolutionParameter(
                          organizationSaved.id, solutionSaved.id, "parameter")
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id} - User does not have permission $PERMISSION_WRITE",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.deleteSolutionParameter(
                      organizationSaved.id, solutionSaved.id, "parameter")
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC list solution parameter groups`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC listSolutionParameterGroups : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization =
                  makeOrganizationCreateRequest(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolutionWithRole(organizationSaved.id, TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.listSolutionParameterGroups(
                          organizationSaved.id, solutionSaved.id)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.listSolutionParameterGroups(
                      organizationSaved.id, solutionSaved.id)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC get solution parameter group`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC getSolutionParameterGroup : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization =
                  makeOrganizationCreateRequest(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolutionWithRole(organizationSaved.id, TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.getSolutionParameterGroup(
                          organizationSaved.id, solutionSaved.id, "group")
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.getSolutionParameterGroup(
                      organizationSaved.id, solutionSaved.id, "group")
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC create solution parameter group`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC createSolutionParameterGroup : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization =
                  makeOrganizationCreateRequest(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolutionWithRole(organizationSaved.id, TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              val parameterGroupToCreate = RunTemplateParameterGroupCreateRequest(id = "group2")

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.createSolutionParameterGroup(
                          organizationSaved.id, solutionSaved.id, parameterGroupToCreate)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id} - User does not have permission $PERMISSION_WRITE",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.createSolutionParameterGroup(
                      organizationSaved.id, solutionSaved.id, parameterGroupToCreate)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC update solution parameter group`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC updateSolutionParameterGroup : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization =
                  makeOrganizationCreateRequest(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolutionWithRole(organizationSaved.id, TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              val parameterGroupToUpdate =
                  RunTemplateParameterGroupUpdateRequest(description = "description")

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.updateSolutionParameterGroup(
                          organizationSaved.id, solutionSaved.id, "group", parameterGroupToUpdate)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id} - User does not have permission $PERMISSION_WRITE",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.updateSolutionParameterGroup(
                      organizationSaved.id, solutionSaved.id, "group", parameterGroupToUpdate)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC delete solution parameter group`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC deleteSolutionParameterGroup : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization =
                  makeOrganizationCreateRequest(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolutionWithRole(organizationSaved.id, TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.deleteSolutionParameterGroup(
                          organizationSaved.id, solutionSaved.id, "group")
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id} - User does not have permission $PERMISSION_WRITE",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.deleteSolutionParameterGroup(
                      organizationSaved.id, solutionSaved.id, "group")
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC list solution run templates`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC listRunTemplates : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization =
                  makeOrganizationCreateRequest(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolutionWithRole(organizationSaved.id, TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.listRunTemplates(organizationSaved.id, solutionSaved.id)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.listRunTemplates(organizationSaved.id, solutionSaved.id)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC get solution run template`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC getRunTemplate : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization =
                  makeOrganizationCreateRequest(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolutionWithRole(organizationSaved.id, TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.getRunTemplate(
                          organizationSaved.id, solutionSaved.id, "runTemplate")
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.getRunTemplate(
                      organizationSaved.id, solutionSaved.id, "runTemplate")
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC create solution run template`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC createSolutionRunTemplate : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization =
                  makeOrganizationCreateRequest(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolutionWithRole(organizationSaved.id, TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              val runTemplateToCreate = RunTemplateCreateRequest(id = "group2")

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.createSolutionRunTemplate(
                          organizationSaved.id, solutionSaved.id, runTemplateToCreate)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id} - User does not have permission $PERMISSION_WRITE",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.createSolutionRunTemplate(
                      organizationSaved.id, solutionSaved.id, runTemplateToCreate)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC update solution run template`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC updateSolutionRunTemplate : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization =
                  makeOrganizationCreateRequest(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolutionWithRole(organizationSaved.id, TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              val runTemplateToUpdate = RunTemplateUpdateRequest(description = "description")

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.updateSolutionRunTemplate(
                          organizationSaved.id,
                          solutionSaved.id,
                          "runTemplate",
                          runTemplateToUpdate)
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id} - User does not have permission $PERMISSION_WRITE",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.updateSolutionRunTemplate(
                      organizationSaved.id, solutionSaved.id, "runTemplate", runTemplateToUpdate)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC delete solution run template`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC deleteSolutionRunTemplate : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization =
                  makeOrganizationCreateRequest(id = TEST_USER_MAIL, role = ROLE_ADMIN)
              organizationSaved = organizationApiService.createOrganization(organization)
              val solution = makeSolutionWithRole(organizationSaved.id, TEST_USER_MAIL, role = role)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      solutionApiService.deleteSolutionRunTemplate(
                          organizationSaved.id, solutionSaved.id, "runTemplate")
                    }
                assertEquals(
                    "RBAC ${solutionSaved.id} - User does not have permission $PERMISSION_WRITE",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  solutionApiService.deleteSolutionRunTemplate(
                      organizationSaved.id, solutionSaved.id, "runTemplate")
                }
              }
            }
          }

  fun makeOrganizationCreateRequest(
      id: String = TEST_USER_MAIL,
      role: String = ROLE_ADMIN
  ): OrganizationCreateRequest {
    return OrganizationCreateRequest(
        name = "Organization Name",
        security =
            OrganizationSecurity(
                default = ROLE_NONE,
                accessControlList =
                    mutableListOf(
                        OrganizationAccessControl(id = CONNECTED_ADMIN_USER, role = ROLE_ADMIN),
                        OrganizationAccessControl(id = id, role = role))))
  }

  fun makeSolutionWithRole(
      organizationId: String = organizationSaved.id,
      id: String,
      role: String
  ) =
      SolutionCreateRequest(
          key = UUID.randomUUID().toString(),
          name = "My solution",
          runTemplates = mutableListOf(RunTemplateCreateRequest(id = "runTemplate")),
          version = "1.0.0",
          repository = "repository",
          parameterGroups = mutableListOf(RunTemplateParameterGroupCreateRequest(id = "group")),
          parameters =
              mutableListOf(
                  RunTemplateParameterCreateRequest(id = "parameter", varType = "string")),
          security =
              SolutionSecurity(
                  default = ROLE_NONE,
                  accessControlList =
                      mutableListOf(
                          SolutionAccessControl(id = CONNECTED_ADMIN_USER, role = ROLE_ADMIN),
                          SolutionAccessControl(id = id, role = role))))
}
