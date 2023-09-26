// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.solution.service

import com.azure.storage.blob.BlobServiceClient
import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.rbac.ROLE_ADMIN
import com.cosmotech.api.rbac.ROLE_EDITOR
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.rbac.ROLE_USER
import com.cosmotech.api.rbac.ROLE_VALIDATOR
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
import com.cosmotech.solution.domain.SolutionSecurity
import com.redis.om.spring.RediSearchIndexer
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
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
import org.slf4j.LoggerFactory
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
class SolutionServiceRBACTest : CsmRedisTestBase() {

  private val logger = LoggerFactory.getLogger(SolutionServiceIntegrationTest::class.java)

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

  @BeforeEach
  fun setUp() {
    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "test.user"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("user")

    ReflectionTestUtils.setField(
        solutionApiService, "azureStorageBlobServiceClient", azureStorageBlobServiceClient)

    rediSearchIndexer.createIndexFor(Organization::class.java)
    rediSearchIndexer.createIndexFor(Solution::class.java)
  }

  @TestFactory
  fun `test RBAC findSolutionById`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC findSolutionById : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = mockOrganization()
              val solution = mockSolution(organization.id!!, role = role)

              organizationSaved = organizationApiService.registerOrganization(organization)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                assertThrows<CsmAccessForbiddenException> {
                  solutionApiService.findSolutionById(organizationSaved.id!!, solutionSaved.id!!)
                }
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
              ROLE_VIEWER to 1,
              ROLE_EDITOR to 2,
              ROLE_VALIDATOR to 3,
              ROLE_USER to 4,
              ROLE_NONE to 4,
              ROLE_ADMIN to 5,
          )
          .map { (role, expectedSize) ->
            DynamicTest.dynamicTest("Test RBAC findAllWorkspaces : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              if (!::organizationSaved.isInitialized) {
                organizationSaved =
                    organizationApiService.registerOrganization(
                        mockOrganization(id = "id", userName = FAKE_MAIL, role = ROLE_USER))
              }
              solutionApiService.createSolution(
                  organizationSaved.id!!, mockSolution(userName = FAKE_MAIL, role = role))
              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              val solutions =
                  solutionApiService.findAllSolutions(organizationSaved.id!!, null, null)
              when (role) {
                ROLE_VIEWER -> assertEquals(expectedSize, solutions.size)
                ROLE_EDITOR -> assertEquals(expectedSize, solutions.size)
                ROLE_VALIDATOR -> assertEquals(expectedSize, solutions.size)
                ROLE_USER -> assertEquals(expectedSize, solutions.size)
                ROLE_NONE -> assertEquals(expectedSize, solutions.size)
                ROLE_ADMIN -> assertEquals(expectedSize, solutions.size)
              }
            }
          }

  @TestFactory
  fun `test RBAC createSolution`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC createSolution : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              organization = mockOrganization(role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              solution = mockSolution()

              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                assertThrows<CsmAccessForbiddenException> {
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
                }
              } else {
                assertDoesNotThrow {
                  solutionApiService.createSolution(organizationSaved.id!!, solution)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC deleteSolution`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC deleteSolution : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = mockOrganization()
              val solution = mockSolution(organization.id!!, role = role)

              organizationSaved = organizationApiService.registerOrganization(organization)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                assertThrows<CsmAccessForbiddenException> {
                  solutionApiService.deleteSolution(organizationSaved.id!!, solutionSaved.id!!)
                }
              } else {
                assertDoesNotThrow {
                  solutionApiService.deleteSolution(organizationSaved.id!!, solutionSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC updateSolution`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC updateSolution : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              organization = mockOrganization()
              solution = mockSolution(organization.id!!, role = role)

              organizationSaved = organizationApiService.registerOrganization(organization)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              solution = mockSolution()

              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                assertThrows<CsmAccessForbiddenException> {
                  solutionApiService.updateSolution(
                      organizationSaved.id!!, solutionSaved.id!!, solution)
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
  fun `test RBAC addOrReplaceParameters`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC addOrReplaceParameters : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = mockOrganization()
              val solution = mockSolution(organization.id!!, role = role)

              organizationSaved = organizationApiService.registerOrganization(organization)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              val runTemplateParameter = RunTemplateParameter("id")

              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                assertThrows<CsmAccessForbiddenException> {
                  solutionApiService.addOrReplaceParameters(
                      organizationSaved.id!!, solutionSaved.id!!, listOf(runTemplateParameter))
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
  fun `test RBAC removeAllSolutionParameters`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC removeAllSolutionParameters : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = mockOrganization()
              val solution = mockSolution(organization.id!!, role = role)

              organizationSaved = organizationApiService.registerOrganization(organization)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                assertThrows<CsmAccessForbiddenException> {
                  solutionApiService.removeAllSolutionParameters(
                      organizationSaved.id!!, solutionSaved.id!!)
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
  fun `test RBAC addOrReplaceParameterGroups`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC addOrReplaceParameterGroups : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = mockOrganization()
              val solution = mockSolution(organization.id!!, role = role)

              organizationSaved = organizationApiService.registerOrganization(organization)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              val runTemplateParameterGroup = RunTemplateParameterGroup("id")

              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                assertThrows<CsmAccessForbiddenException> {
                  solutionApiService.addOrReplaceParameterGroups(
                      organizationSaved.id!!, solutionSaved.id!!, listOf(runTemplateParameterGroup))
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
  fun `test RBAC removeAllSolutionParameterGroups`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC removeAllSolutionParameterGroups : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = mockOrganization()
              val solution = mockSolution(organization.id!!, role = role)

              organizationSaved = organizationApiService.registerOrganization(organization)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                assertThrows<CsmAccessForbiddenException> {
                  solutionApiService.removeAllSolutionParameterGroups(
                      organizationSaved.id!!, solutionSaved.id!!)
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
  fun `test RBAC addOrReplaceRunTemplates`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC addOrReplaceRunTemplates : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = mockOrganization()
              val solution = mockSolution(organization.id!!, role = role)

              organizationSaved = organizationApiService.registerOrganization(organization)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              val runTemplate = RunTemplate("id")

              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                assertThrows<CsmAccessForbiddenException> {
                  solutionApiService.addOrReplaceRunTemplates(
                      organizationSaved.id!!, solutionSaved.id!!, listOf(runTemplate))
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
  fun `test RBAC removeAllRunTemplates`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC addOrReplaceRunTemplates : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = mockOrganization()
              val solution = mockSolution(organization.id!!, role = role)

              organizationSaved = organizationApiService.registerOrganization(organization)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                assertThrows<CsmAccessForbiddenException> {
                  solutionApiService.removeAllRunTemplates(
                      organizationSaved.id!!, solutionSaved.id!!)
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
  fun `test RBAC deleteSolutionRunTemplate`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC deleteSolutionRunTemplate : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = mockOrganization()
              val solution = mockSolution(organization.id!!, role = role)

              organizationSaved = organizationApiService.registerOrganization(organization)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                assertThrows<CsmAccessForbiddenException> {
                  solutionApiService.deleteSolutionRunTemplate(
                      organizationSaved.id!!, solutionSaved.id!!, "runTemplate")
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
  fun `test RBAC updateSolutionRunTemplate`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC updateSolutionRunTemplate : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = mockOrganization()
              val solution = mockSolution(organization.id!!, role = role)

              organizationSaved = organizationApiService.registerOrganization(organization)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              val runTemplate = RunTemplate("runTemplate", "name")

              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                assertThrows<CsmAccessForbiddenException> {
                  solutionApiService.updateSolutionRunTemplate(
                      organizationSaved.id!!, solutionSaved.id!!, "runTemplate", runTemplate)
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
  fun `test RBAC downloadRunTemplateHandler`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC downloadRunTemplateHandler : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = mockOrganization()
              val solution = mockSolution(organization.id!!, role = role)

              organizationSaved = organizationApiService.registerOrganization(organization)
              solutionSaved = solutionApiService.createSolution(organizationSaved.id!!, solution)

              val runTemplateHandlerId = RunTemplateHandlerId.values().first()

              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                assertThrows<CsmAccessForbiddenException> {
                  solutionApiService.downloadRunTemplateHandler(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      "runTemplate",
                      runTemplateHandlerId)
                }
              } else {
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
  fun `test RBAC uploadRunTemplateHandler`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC uploadRunTemplateHandler : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = mockOrganization()
              val solution = mockSolution(organization.id!!, role = role)

              organizationSaved = organizationApiService.registerOrganization(organization)
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

              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                assertThrows<CsmAccessForbiddenException> {
                  solutionApiService.uploadRunTemplateHandler(
                      organizationSaved.id!!,
                      solutionSaved.id!!,
                      "runTemplate",
                      runTemplateHandlerId,
                      resource,
                      true)
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

                unmockkStatic(ArchiveStreamFactory::class)
              }
            }
          }

  @TestFactory
  fun `test RBAC importSolution`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_VALIDATOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC importSolution : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = mockOrganization(role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)

              every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL

              if (shouldThrow) {
                assertThrows<CsmAccessForbiddenException> {
                  solutionApiService.importSolution(organizationSaved.id!!, solution)
                }
              } else {
                assertDoesNotThrow {
                  solutionApiService.importSolution(organizationSaved.id!!, solution)
                }
              }
            }
          }

  fun mockOrganization(
      id: String = "organization_id",
      userName: String = FAKE_MAIL,
      role: String = ROLE_ADMIN
  ): Organization {
    return Organization(
        id = id,
        name = "Organization Name",
        ownerId = "my.account-tester@cosmotech.com",
        security =
            OrganizationSecurity(
                default = ROLE_NONE,
                accessControlList =
                    mutableListOf(
                        OrganizationAccessControl(id = CONNECTED_ADMIN_USER, role = "admin"),
                        OrganizationAccessControl(id = userName, role = role))))
  }

  fun mockSolution(
      organizationId: String = organizationSaved.id!!,
      userName: String = FAKE_MAIL,
      role: String = ROLE_ADMIN
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
                        SolutionAccessControl(id = CONNECTED_ADMIN_USER, role = "admin"),
                        SolutionAccessControl(id = userName, role = role))))
  }
}
