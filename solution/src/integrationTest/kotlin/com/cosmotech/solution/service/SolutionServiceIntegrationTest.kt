// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.solution.service

import com.azure.storage.blob.BlobServiceClient
import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.rbac.ROLE_ADMIN
import com.cosmotech.api.rbac.ROLE_EDITOR
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.rbac.ROLE_USER
import com.cosmotech.api.rbac.ROLE_VALIDATOR
import com.cosmotech.api.rbac.ROLE_VIEWER
import com.cosmotech.api.security.ROLE_PLATFORM_ADMIN
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
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
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

const val FAKE_MAIL = "fake@mail.fr"
const val CONNECTED_ADMIN_USER = "test.admin@cosmotech.com"
const val CONNECTED_READER_USER = "test.user@cosmotech.com"

@ActiveProfiles(profiles = ["solution-test"])
@ExtendWith(MockKExtension::class)
@ExtendWith(SpringExtension::class)
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SolutionServiceIntegrationTest : CsmRedisTestBase() {

  private val logger = LoggerFactory.getLogger(SolutionServiceIntegrationTest::class.java)

  @MockK(relaxed = true) private lateinit var azureStorageBlobServiceClient: BlobServiceClient

  @Autowired lateinit var rediSearchIndexer: RediSearchIndexer
  @Autowired lateinit var organizationApiService: OrganizationApiService
  @Autowired lateinit var solutionApiService: SolutionApiService
  @Autowired lateinit var csmPlatformProperties: CsmPlatformProperties

  lateinit var organization: Organization
  lateinit var solution: Solution

  lateinit var organizationRegistered: Organization
  lateinit var solutionRegistered: Solution

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

    organization = mockOrganization("Organization test")
    organizationRegistered = organizationApiService.registerOrganization(organization)

    solution = mockSolution(organizationRegistered.id!!)
    solutionRegistered = solutionApiService.createSolution(organizationRegistered.id!!, solution)
  }

  @Test
  fun `test verify updateRunTemplate works as intended`() {

    val solution =
        Solution(
            "id",
            organizationRegistered.id!!,
            "key",
            "name",
            runTemplates = mutableListOf(RunTemplate(id = "one"), RunTemplate(id = "two")))
    solutionRegistered = solutionApiService.createSolution(organizationRegistered.id!!, solution)

    val endTemplates =
        solutionApiService.updateSolutionRunTemplate(
            organizationRegistered.id!!,
            solutionRegistered.id!!,
            "one",
            RunTemplate(id = "one", name = "name_one"))

    val expectedSolution =
        Solution(
            "id",
            organizationRegistered.id!!,
            "key",
            "name",
            runTemplates =
                mutableListOf(RunTemplate(id = "one", name = "name_one"), RunTemplate(id = "two")))
    // Assert that no runTemplate were deleted
    assertEquals(expectedSolution.runTemplates!!.size, endTemplates.size)
    assertEquals(expectedSolution.runTemplates!!, endTemplates)
  }

  @Test
  fun `test CRUD operations on Solution`() {

    logger.info("should add a new solution")
    val solution2 = mockSolution(organizationRegistered.id!!)
    val solutionCreated = solutionApiService.createSolution(organizationRegistered.id!!, solution2)

    logger.info("should find the new solution by id and assert it is the same as the one created")
    val solutionFound =
        solutionApiService.findSolutionById(organizationRegistered.id!!, solutionCreated.id!!)
    assertEquals(solutionCreated, solutionFound)

    logger.info(
        "should find all solutions for the organization and assert the list contains 2 elements")
    val solutionsFound =
        solutionApiService.findAllSolutions(organizationRegistered.id!!, null, null)
    assertTrue(solutionsFound.size == 2)

    logger.info("should update the solution and assert that the name has been updated")
    solutionCreated.name = "My solution updated"
    val solutionUpdated =
        solutionApiService.updateSolution(
            organizationRegistered.id!!, solutionCreated.id!!, solutionCreated)
    assertEquals(solutionCreated.name, solutionUpdated.name)

    logger.info(
        "should delete the solution and assert that the list of solutions contains only 1 element")
    solutionApiService.deleteSolution(organizationRegistered.id!!, solutionCreated.id!!)
    val solutionsFoundAfterDelete =
        solutionApiService.findAllSolutions(organizationRegistered.id!!, null, null)
    assertTrue(solutionsFoundAfterDelete.size == 1)
  }

  @Test
  fun `can delete solution when user is not the owner and is Platform Admin`() {
    logger.info("Register new solution...")
    val solution = mockSolution(organizationRegistered.id!!)
    val solutionCreated = solutionApiService.createSolution(organizationRegistered.id!!, solution)

    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "test.admin"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf(ROLE_PLATFORM_ADMIN)

    solutionApiService.deleteSolution(organizationRegistered.id!!, solutionCreated.id!!)

    assertThrows<CsmResourceNotFoundException> {
      solutionCreated.id?.let {
        solutionApiService.findSolutionById(organizationRegistered.id!!, it)
      }
    }
  }

  @Test
  fun `cannot delete solution when user is not the owner and is not Platform Admin`() {
    logger.info("Register new solution...")
    val solution = mockSolution(organizationRegistered.id!!)
    val solutionCreated = solutionApiService.createSolution(organizationRegistered.id!!, solution)

    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_READER_USER
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "test.other.user"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf()
    assertThrows<CsmAccessForbiddenException> {
      solutionApiService.deleteSolution(organizationRegistered.id!!, solutionCreated.id!!)
    }
  }

  @Test
  fun `can update solution when user is not the owner and is Platform Admin`() {
    logger.info("Register new solution...")
    val solution = mockSolution(organizationRegistered.id!!)
    val solutionCreated = solutionApiService.createSolution(organizationRegistered.id!!, solution)

    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "test.other.user"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf(ROLE_PLATFORM_ADMIN)
    solutionCreated.ownerId = "new_owner_id"

    val updateSolution =
        solutionApiService.updateSolution(
            organizationRegistered.id!!, solutionCreated.id!!, solutionCreated)

    updateSolution.id?.let { solutionApiService.findSolutionById(organizationRegistered.id!!, it) }

    assertEquals("new_owner_id", updateSolution.ownerId)
  }

  @Test
  fun `cannot update solution when user is not the owner and is not Platform Admin`() {
    logger.info("Register new solution...")
    val solution = mockSolution(organizationRegistered.id!!)
    val solutionCreated = solutionApiService.createSolution(organizationRegistered.id!!, solution)

    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_READER_USER
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "test.other.user"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf()
    solutionCreated.ownerId = "new_owner_id"

    assertThrows<CsmAccessForbiddenException> {
      solutionApiService.updateSolution(
          organizationRegistered.id!!, solutionCreated.id!!, solutionCreated)
    }
  }

  @Test
  fun `test Parameter Group operations on Solution`() {

    logger.info(
        "should add 2 new parameter groups to the solution and assert that the list contains 2 elements")
    val parameterGroup1 = RunTemplateParameterGroup(id = "parameterGroupId1")
    val parameterGroup2 = RunTemplateParameterGroup(id = "parameterGroupId2")
    solutionApiService.addOrReplaceParameterGroups(
        solutionRegistered.organizationId!!,
        solutionRegistered.id!!,
        listOf(parameterGroup1, parameterGroup2))
    val foundSolution =
        solutionApiService.findSolutionById(
            solutionRegistered.organizationId!!, solutionRegistered.id!!)
    assertTrue(foundSolution.parameterGroups!!.size == 2)

    logger.info(
        "should replace the parameter groups and assert that the list contains only 1 element")
    val parameterGroup3 =
        RunTemplateParameterGroup(
            id = "parameterGroupId1", labels = mutableMapOf("pkey" to "value"))
    solutionApiService.addOrReplaceParameterGroups(
        solutionRegistered.organizationId!!, solutionRegistered.id!!, listOf(parameterGroup3))
    val foundSolutionAfterReplace =
        solutionApiService.findSolutionById(
            solutionRegistered.organizationId!!, solutionRegistered.id!!)
    assertTrue(foundSolutionAfterReplace.parameterGroups!!.size == 2)
    assertTrue(foundSolutionAfterReplace.parameterGroups!!.first().labels!!.containsKey("pkey"))

    logger.info("should remove all parameter groups and assert that the list is empty")
    solutionApiService.removeAllSolutionParameterGroups(
        solutionRegistered.organizationId!!, solutionRegistered.id!!)
    val foundSolutionAfterRemove =
        solutionApiService.findSolutionById(
            solutionRegistered.organizationId!!, solutionRegistered.id!!)
    assertTrue(foundSolutionAfterRemove.parameterGroups!!.isEmpty())
  }

  @Test
  fun `test Parameter operations on Solution`() {

    logger.info(
        "should add 2 new parameters to the solution and assert that the list contains 2 elements")
    val parameter1 = RunTemplateParameter(id = "parameterId1")
    val parameter2 = RunTemplateParameter(id = "parameterId2")
    solutionApiService.addOrReplaceParameters(
        solutionRegistered.organizationId!!,
        solutionRegistered.id!!,
        listOf(parameter1, parameter2))
    val foundSolution =
        solutionApiService.findSolutionById(
            solutionRegistered.organizationId!!, solutionRegistered.id!!)
    assertTrue(foundSolution.parameters!!.size == 2)

    logger.info("should replace the parameters and assert that the list contains only 1 element")
    val parameter3 =
        RunTemplateParameter(id = "parameterId1", labels = mutableMapOf("pkey" to "value"))
    solutionApiService.addOrReplaceParameters(
        solutionRegistered.organizationId!!, solutionRegistered.id!!, listOf(parameter3))
    val foundSolutionAfterReplace =
        solutionApiService.findSolutionById(
            solutionRegistered.organizationId!!, solutionRegistered.id!!)
    assertTrue(foundSolutionAfterReplace.parameters!!.size == 2)
    assertTrue(foundSolutionAfterReplace.parameters!!.first().labels!!.containsKey("pkey"))

    logger.info("should remove all parameters and assert that the list is empty")
    solutionApiService.removeAllSolutionParameters(
        solutionRegistered.organizationId!!, solutionRegistered.id!!)
    val foundSolutionAfterRemove =
        solutionApiService.findSolutionById(
            solutionRegistered.organizationId!!, solutionRegistered.id!!)
    assertTrue(foundSolutionAfterRemove.parameters!!.isEmpty())
  }

  @Test
  fun `test RunTemplate operations on Solution`() {

    logger.info(
        "should add 2 new run templates to the solution and assert that the list contains 2 elements")
    val runTemplate1 = RunTemplate(id = "runTemplateId1")
    val runTemplate2 = RunTemplate(id = "runTemplateId2")
    solutionApiService.addOrReplaceRunTemplates(
        solutionRegistered.organizationId!!,
        solutionRegistered.id!!,
        listOf(runTemplate1, runTemplate2))
    val foundSolution =
        solutionApiService.findSolutionById(
            solutionRegistered.organizationId!!, solutionRegistered.id!!)
    assertTrue(foundSolution.runTemplates!!.size == 2)

    logger.info(
        "should replace the first run template and assert that the list contains 2 elements")
    val runTemplate3 = RunTemplate(id = "runTemplateId1", name = "runTemplateName")
    solutionApiService.addOrReplaceRunTemplates(
        solutionRegistered.organizationId!!, solutionRegistered.id!!, listOf(runTemplate3))
    val foundSolutionAfterReplace =
        solutionApiService.findSolutionById(
            solutionRegistered.organizationId!!, solutionRegistered.id!!)
    assertTrue(foundSolutionAfterReplace.runTemplates!!.size == 2)
    assertTrue(foundSolutionAfterReplace.runTemplates!!.first().name == "runTemplateName")

    logger.info("should update the run template and assert that the name has been updated")
    val runTemplate4 = RunTemplate(id = "runTemplateId1", name = "runTemplateNameNew")
    solutionApiService.updateSolutionRunTemplate(
        solutionRegistered.organizationId!!, solutionRegistered.id!!, runTemplate4.id, runTemplate4)
    val foundSolutionAfterUpdate =
        solutionApiService.findSolutionById(
            solutionRegistered.organizationId!!, solutionRegistered.id!!)
    assertTrue(foundSolutionAfterUpdate.runTemplates!!.first().name == "runTemplateNameNew")

    logger.info("should remove all run templates and assert that the list is empty")
    solutionApiService.removeAllRunTemplates(
        solutionRegistered.organizationId!!, solutionRegistered.id!!)
    val foundSolutionAfterRemove =
        solutionApiService.findSolutionById(
            solutionRegistered.organizationId!!, solutionRegistered.id!!)
    assertTrue(foundSolutionAfterRemove.runTemplates!!.isEmpty())
  }

  @Test
  fun `test find All Solutions with different pagination params`() {
    val numberOfSolutions = 20
    val defaultPageSize = csmPlatformProperties.twincache.solution.defaultPageSize
    val expectedSize = 15
    IntRange(1, numberOfSolutions - 1).forEach {
      solutionApiService.createSolution(
          organizationId = organizationRegistered.id!!,
          solution = mockSolution(organizationRegistered.id!!))
    }
    logger.info("should find all solutions and assert there are $numberOfSolutions")
    var solutions = solutionApiService.findAllSolutions(organizationRegistered.id!!, null, null)
    assertEquals(numberOfSolutions, solutions.size)

    logger.info("should find all solutions and assert it equals defaultPageSize: $defaultPageSize")
    solutions = solutionApiService.findAllSolutions(organizationRegistered.id!!, 0, null)
    assertEquals(defaultPageSize, solutions.size)

    logger.info("should find all solutions and assert there are expected size: $expectedSize")
    solutions = solutionApiService.findAllSolutions(organizationRegistered.id!!, 0, expectedSize)
    assertEquals(expectedSize, solutions.size)

    logger.info("should find all solutions and assert it returns the second / last page")
    solutions = solutionApiService.findAllSolutions(organizationRegistered.id!!, 1, expectedSize)
    assertEquals(numberOfSolutions - expectedSize, solutions.size)
  }

  @Test
  fun `test find All Solutions with wrong pagination params`() {
    logger.info("should throw IllegalArgumentException when page and size are zero")
    assertThrows<IllegalArgumentException> {
      solutionApiService.findAllSolutions(organizationRegistered.id!!, null, 0)
    }
    logger.info("should throw IllegalArgumentException when page is negative")
    assertThrows<IllegalArgumentException> {
      solutionApiService.findAllSolutions(organizationRegistered.id!!, -1, 1)
    }
    logger.info("should throw IllegalArgumentException when size is negative")
    assertThrows<IllegalArgumentException> {
      solutionApiService.findAllSolutions(organizationRegistered.id!!, 0, -1)
    }
  }
  @Nested
  inner class RBACTests {

    @MockK lateinit var resource: Resource
    @MockK lateinit var azureStorageBlobServiceClient: BlobServiceClient
    @MockK lateinit var resourceScanner: ResourceScanner
    @MockK lateinit var inputStream: InputStream

    private fun prepareTestEnvironment(role: String) {
      val organization = mockOrganization()
      val solution = mockSolution(organization.id!!, role = role)

      every { getCurrentAccountIdentifier(any()) } returns FAKE_MAIL
      every { getCurrentAuthenticatedRoles(any()) } returns listOf(role)

      organizationRegistered = organizationApiService.registerOrganization(organization)
      solutionRegistered = solutionApiService.createSolution(organizationRegistered.id!!, solution)
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
                prepareTestEnvironment(role)

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    solutionApiService.findSolutionById(
                        organizationRegistered.id!!, solutionRegistered.id!!)
                  }
                } else {
                  assertDoesNotThrow {
                    solutionApiService.findSolutionById(
                        organizationRegistered.id!!, solutionRegistered.id!!)
                  }
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
                organization = mockOrganization(role = role)
                organizationRegistered = organizationApiService.registerOrganization(organization)
                solution = mockSolution()

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    solutionApiService.createSolution(organizationRegistered.id!!, solution)
                  }
                } else {
                  assertDoesNotThrow {
                    solutionApiService.createSolution(organizationRegistered.id!!, solution)
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
                prepareTestEnvironment(role)

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    solutionApiService.deleteSolution(
                        organizationRegistered.id!!, solutionRegistered.id!!)
                  }
                } else {
                  assertDoesNotThrow {
                    solutionApiService.deleteSolution(
                        organizationRegistered.id!!, solutionRegistered.id!!)
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
                prepareTestEnvironment(role)

                solution = mockSolution()

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    solutionApiService.updateSolution(
                        organizationRegistered.id!!, solutionRegistered.id!!, solution)
                  }
                } else {
                  assertDoesNotThrow {
                    solutionApiService.updateSolution(
                        organizationRegistered.id!!, solutionRegistered.id!!, solution)
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
                prepareTestEnvironment(role)

                val runTemplateParameter = RunTemplateParameter("id")

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    solutionApiService.addOrReplaceParameters(
                        organizationRegistered.id!!,
                        solutionRegistered.id!!,
                        listOf(runTemplateParameter))
                  }
                } else {
                  assertDoesNotThrow {
                    solutionApiService.addOrReplaceParameters(
                        organizationRegistered.id!!,
                        solutionRegistered.id!!,
                        listOf(runTemplateParameter))
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
                prepareTestEnvironment(role)

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    solutionApiService.removeAllSolutionParameters(
                        organizationRegistered.id!!, solutionRegistered.id!!)
                  }
                } else {
                  assertDoesNotThrow {
                    solutionApiService.removeAllSolutionParameters(
                        organizationRegistered.id!!, solutionRegistered.id!!)
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
                prepareTestEnvironment(role)

                val runTemplateParameterGroup = RunTemplateParameterGroup("id")

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    solutionApiService.addOrReplaceParameterGroups(
                        organizationRegistered.id!!,
                        solutionRegistered.id!!,
                        listOf(runTemplateParameterGroup))
                  }
                } else {
                  assertDoesNotThrow {
                    solutionApiService.addOrReplaceParameterGroups(
                        organizationRegistered.id!!,
                        solutionRegistered.id!!,
                        listOf(runTemplateParameterGroup))
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
                prepareTestEnvironment(role)

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    solutionApiService.removeAllSolutionParameterGroups(
                        organizationRegistered.id!!, solutionRegistered.id!!)
                  }
                } else {
                  assertDoesNotThrow {
                    solutionApiService.removeAllSolutionParameterGroups(
                        organizationRegistered.id!!, solutionRegistered.id!!)
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
                prepareTestEnvironment(role)

                val runTemplate = RunTemplate("id")

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    solutionApiService.addOrReplaceRunTemplates(
                        organizationRegistered.id!!, solutionRegistered.id!!, listOf(runTemplate))
                  }
                } else {
                  assertDoesNotThrow {
                    solutionApiService.addOrReplaceRunTemplates(
                        organizationRegistered.id!!, solutionRegistered.id!!, listOf(runTemplate))
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
                prepareTestEnvironment(role)

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    solutionApiService.removeAllRunTemplates(
                        organizationRegistered.id!!, solutionRegistered.id!!)
                  }
                } else {
                  assertDoesNotThrow {
                    solutionApiService.removeAllRunTemplates(
                        organizationRegistered.id!!, solutionRegistered.id!!)
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
                prepareTestEnvironment(role)

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    solutionApiService.deleteSolutionRunTemplate(
                        organizationRegistered.id!!, solutionRegistered.id!!, "runTemplate")
                  }
                } else {
                  assertDoesNotThrow {
                    solutionApiService.deleteSolutionRunTemplate(
                        organizationRegistered.id!!, solutionRegistered.id!!, "runTemplate")
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
                prepareTestEnvironment(role)

                val runTemplate = RunTemplate("runTemplate", "name")

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    solutionApiService.updateSolutionRunTemplate(
                        organizationRegistered.id!!,
                        solutionRegistered.id!!,
                        "runTemplate",
                        runTemplate)
                  }
                } else {
                  assertDoesNotThrow {
                    solutionApiService.updateSolutionRunTemplate(
                        organizationRegistered.id!!,
                        solutionRegistered.id!!,
                        "runTemplate",
                        runTemplate)
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
                prepareTestEnvironment(role)

                val runTemplateHandlerId = RunTemplateHandlerId.values().first()

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    solutionApiService.downloadRunTemplateHandler(
                        organizationRegistered.id!!,
                        solutionRegistered.id!!,
                        "runTemplate",
                        runTemplateHandlerId)
                  }
                } else {
                  assertDoesNotThrow {
                    solutionApiService.downloadRunTemplateHandler(
                        organizationRegistered.id!!,
                        solutionRegistered.id!!,
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
                prepareTestEnvironment(role)
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

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    solutionApiService.uploadRunTemplateHandler(
                        organizationRegistered.id!!,
                        solutionRegistered.id!!,
                        "runTemplate",
                        runTemplateHandlerId,
                        resource,
                        true)
                  }
                } else {
                  assertDoesNotThrow {
                    solutionApiService.uploadRunTemplateHandler(
                        organizationRegistered.id!!,
                        solutionRegistered.id!!,
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
                prepareTestEnvironment(role)

                if (shouldThrow) {
                  assertThrows<CsmAccessForbiddenException> {
                    solutionApiService.importSolution(organizationRegistered.id!!, solution)
                  }
                } else {
                  assertDoesNotThrow {
                    solutionApiService.importSolution(organizationRegistered.id!!, solution)
                  }
                }
              }
            }
  }

  fun mockOrganization(
      id: String = "organization_id",
      roleName: String = FAKE_MAIL,
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
                        OrganizationAccessControl(id = CONNECTED_READER_USER, role = "reader"),
                        OrganizationAccessControl(id = CONNECTED_ADMIN_USER, role = "admin"),
                        OrganizationAccessControl(id = roleName, role = role))))
  }

  fun mockSolution(
      organizationId: String = organizationRegistered.id!!,
      roleName: String = FAKE_MAIL,
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
                        SolutionAccessControl(id = CONNECTED_READER_USER, role = "reader"),
                        SolutionAccessControl(id = CONNECTED_ADMIN_USER, role = "admin"),
                        SolutionAccessControl(id = roleName, role = role))))
  }
}
