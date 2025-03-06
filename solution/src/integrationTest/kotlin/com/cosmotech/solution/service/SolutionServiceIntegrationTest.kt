// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.solution.service

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.rbac.ROLE_ADMIN
import com.cosmotech.api.rbac.ROLE_EDITOR
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.rbac.ROLE_VIEWER
import com.cosmotech.api.security.ROLE_PLATFORM_ADMIN
import com.cosmotech.api.tests.CsmRedisTestBase
import com.cosmotech.api.utils.getCurrentAccountIdentifier
import com.cosmotech.api.utils.getCurrentAuthenticatedRoles
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.organization.OrganizationApiServiceInterface
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationCreateRequest
import com.cosmotech.organization.domain.OrganizationSecurity
import com.cosmotech.solution.SolutionApiServiceInterface
import com.cosmotech.solution.domain.RunTemplate
import com.cosmotech.solution.domain.RunTemplateParameter
import com.cosmotech.solution.domain.RunTemplateParameterGroup
import com.cosmotech.solution.domain.Solution
import com.cosmotech.solution.domain.SolutionAccessControl
import com.cosmotech.solution.domain.SolutionCreateRequest
import com.cosmotech.solution.domain.SolutionRole
import com.cosmotech.solution.domain.SolutionSecurity
import com.cosmotech.solution.domain.SolutionUpdateRequest
import com.cosmotech.solution.repository.SolutionRepository
import com.redis.om.spring.RediSearchIndexer
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockkStatic
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.runner.RunWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.junit4.SpringRunner

const val CONNECTED_ADMIN_USER = "test.admin@cosmotech.com"
const val CONNECTED_READER_USER = "test.user@cosmotech.com"

@ActiveProfiles(profiles = ["solution-test"])
@ExtendWith(MockKExtension::class)
@ExtendWith(SpringExtension::class)
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SolutionServiceIntegrationTest : CsmRedisTestBase() {

  private val logger = LoggerFactory.getLogger(SolutionServiceIntegrationTest::class.java)

  @Autowired lateinit var rediSearchIndexer: RediSearchIndexer
  @Autowired lateinit var organizationApiService: OrganizationApiServiceInterface
  @Autowired lateinit var solutionApiService: SolutionApiServiceInterface
  @Autowired lateinit var csmPlatformProperties: CsmPlatformProperties

  lateinit var organization: OrganizationCreateRequest
  lateinit var solution: SolutionCreateRequest

  lateinit var organizationSaved: Organization
  lateinit var solutionSaved: Solution

  @BeforeEach
  fun setUp() {
    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "test.user"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("user")

    rediSearchIndexer.createIndexFor(Organization::class.java)
    rediSearchIndexer.createIndexFor(Solution::class.java)

    organization = makeOrganizationCreateRequest("Organization test")
    organizationSaved = organizationApiService.createOrganization(organization)

    solution = makeSolution(organizationSaved.id)
    solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
  }

  @Test
  fun `test verify updateRunTemplate works as intended`() {
    val solution =
      makeSolution(runTemplates = mutableListOf(RunTemplate(id = "one"), RunTemplate(id = "two")))
    solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

    val endTemplates =
        solutionApiService.updateSolutionRunTemplate(
            organizationSaved.id,
          solutionSaved.id,
            "one",
            RunTemplate(id = "one", name = "name_one"))

    val expectedSolution =
        Solution(
            id = solutionSaved.id,
            ownerId = solutionSaved.ownerId,
            organizationId = solutionSaved.organizationId,
            key = "key",
            name = "name",
            runTemplates =
                mutableListOf(RunTemplate(id = "one", name = "name_one"), RunTemplate(id = "two")),
          version = "1.0.0",
          repository = "repository",
          csmSimulator = "simulator",
          parameters = mutableListOf(RunTemplateParameter("parameter")),
          parameterGroups = mutableListOf(),
          security = SolutionSecurity(ROLE_ADMIN, mutableListOf(SolutionAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN)))
        )
    // Assert that no runTemplate were deleted
    assertEquals(expectedSolution.runTemplates.size, endTemplates.size)
    assertEquals(expectedSolution.runTemplates, endTemplates)
  }

  @Test
  fun `test CRUD operations on Solution`() {

    logger.info("should add a new solution")
    val solution2 = makeSolution(organizationSaved.id)
    val solutionCreated = solutionApiService.createSolution(organizationSaved.id, solution2)

    logger.info("should find the new solution by id and assert it is the same as the one created")
    val solutionFound =
        solutionApiService.getSolution(organizationSaved.id, solutionCreated.id)
    assertEquals(solutionCreated, solutionFound)

    logger.info(
        "should find all solutions for the organization and assert the list contains 2 elements")
    val solutionsFound = solutionApiService.listSolutions(organizationSaved.id, null, null)
    assertTrue(solutionsFound.size == 2)

    logger.info("should update the solution and assert that the name has been updated")
    val solutionUpdateRequest = SolutionUpdateRequest(name = "My solution updated")
    val solutionUpdated =
        solutionApiService.updateSolution(
            organizationSaved.id, solutionCreated.id, solutionUpdateRequest)
    assertEquals("My solution updated", solutionUpdated.name)

    logger.info(
        "should delete the solution and assert that the list of solutions contains only 1 element")
    solutionApiService.deleteSolution(organizationSaved.id, solutionCreated.id)
    val solutionsFoundAfterDelete =
        solutionApiService.listSolutions(organizationSaved.id, null, null)
    assertTrue(solutionsFoundAfterDelete.size == 1)
  }

  @Test
  fun `can delete solution when user is not the owner and is Platform Admin`() {
    logger.info("Register new solution...")
    val solution = makeSolution(organizationSaved.id)
    val solutionCreated = solutionApiService.createSolution(organizationSaved.id, solution)

    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "test.admin"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf(ROLE_PLATFORM_ADMIN)

    solutionApiService.deleteSolution(organizationSaved.id, solutionCreated.id)

    assertThrows<CsmResourceNotFoundException> {
      solutionCreated.id.let { solutionApiService.getSolution(organizationSaved.id, it) }
    }
  }

  @Test
  fun `cannot delete solution when user is not the owner and is not Platform Admin`() {
    logger.info("Register new solution...")
    val solution = makeSolution(organizationSaved.id)
    val solutionCreated = solutionApiService.createSolution(organizationSaved.id, solution)

    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_READER_USER
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "test.other.user"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf()
    assertThrows<CsmAccessForbiddenException> {
      solutionApiService.deleteSolution(organizationSaved.id, solutionCreated.id)
    }
  }

  @Test
  fun `can update solution when user is not the owner and is Platform Admin`() {
    logger.info("Register new solution...")
    val solution = makeSolution(organizationSaved.id)
    val solutionCreated = solutionApiService.createSolution(organizationSaved.id, solution)

    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "test.other.user"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf(ROLE_PLATFORM_ADMIN)
    val solutionUpdateRequest = SolutionUpdateRequest(name = "new_name")

    val updateSolution =
        solutionApiService.updateSolution(
            organizationSaved.id, solutionCreated.id, solutionUpdateRequest)

    updateSolution.id.let { solutionApiService.getSolution(organizationSaved.id, it) }

    assertEquals("new_name", updateSolution.name)
  }

  @Test
  fun `cannot update solution when user is not the owner and is not Platform Admin`() {
    logger.info("Register new solution...")
    val solution = makeSolution(organizationSaved.id)
    val solutionCreated = solutionApiService.createSolution(organizationSaved.id, solution)
    val solutionUpdateRequest = SolutionUpdateRequest(name = "new_name")

    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_READER_USER
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "test.other.user"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf()

    assertThrows<CsmAccessForbiddenException> {
      solutionApiService.updateSolution(organizationSaved.id, solutionCreated.id, solutionUpdateRequest)
    }
  }

  @Test
  fun `test Parameter Group operations on Solution`() {

    logger.info(
        "should add 2 new parameter groups to the solution and assert that the list contains 2 elements")
    val parameterGroup1 = RunTemplateParameterGroup(id = "parameterGroupId1")
    val parameterGroup2 = RunTemplateParameterGroup(id = "parameterGroupId2")
    solutionApiService.updateSolutionParameterGroups(
      solutionSaved.organizationId,
      solutionSaved.id,
        listOf(parameterGroup1, parameterGroup2))
    val foundSolution =
        solutionApiService.getSolution(solutionSaved.organizationId, solutionSaved.id)
    assertTrue(foundSolution.parameterGroups.size == 2)

    logger.info(
        "should replace the parameter groups and assert that the list contains only 1 element")
    val parameterGroup3 =
        RunTemplateParameterGroup(
            id = "parameterGroupId1", labels = mutableMapOf("pkey" to "value"))
    solutionApiService.updateSolutionParameterGroups(
      solutionSaved.organizationId, solutionSaved.id, listOf(parameterGroup3))
    val foundSolutionAfterReplace =
        solutionApiService.getSolution(solutionSaved.organizationId, solutionSaved.id)
    assertTrue(foundSolutionAfterReplace.parameterGroups.size == 2)
    assertTrue(foundSolutionAfterReplace.parameterGroups.first().labels!!.containsKey("pkey"))

    logger.info("should remove all parameter groups and assert that the list is empty")
    solutionApiService.deleteSolutionParameterGroups(
      solutionSaved.organizationId, solutionSaved.id
    )
    val foundSolutionAfterRemove =
        solutionApiService.getSolution(solutionSaved.organizationId, solutionSaved.id)
    assertTrue(foundSolutionAfterRemove.parameterGroups.isEmpty())
  }

  @Test
  fun `test Parameter operations on Solution`() {

    logger.info(
        "should add 2 new parameters to the solution and assert that the list contains 2 elements")
    val parameter1 = RunTemplateParameter(id = "parameterId1")
    val parameter2 = RunTemplateParameter(id = "parameterId2")
    solutionApiService.updateSolutionParameters(
      solutionSaved.organizationId, solutionSaved.id, listOf(parameter1, parameter2))
    val foundSolution =
        solutionApiService.getSolution(solutionSaved.organizationId, solutionSaved.id)
    assertTrue(foundSolution.parameters.size == 2)

    logger.info("should replace the parameters and assert that the list contains only 1 element")
    val parameter3 =
        RunTemplateParameter(id = "parameterId1", labels = mutableMapOf("pkey" to "value"))
    solutionApiService.updateSolutionParameters(
      solutionSaved.organizationId, solutionSaved.id, listOf(parameter3))
    val foundSolutionAfterReplace =
        solutionApiService.getSolution(solutionSaved.organizationId, solutionSaved.id)
    assertTrue(foundSolutionAfterReplace.parameters.size == 2)
    assertTrue(foundSolutionAfterReplace.parameters.first().labels!!.containsKey("pkey"))

    logger.info("should remove all parameters and assert that the list is empty")
    solutionApiService.deleteSolutionParameters(
      solutionSaved.organizationId, solutionSaved.id
    )
    val foundSolutionAfterRemove =
        solutionApiService.getSolution(solutionSaved.organizationId, solutionSaved.id)
    assertTrue(foundSolutionAfterRemove.parameters.isEmpty())
  }

  @Test
  fun `test RunTemplate operations on Solution`() {

    logger.info(
        "should add 2 new run templates to the solution and assert that the list contains 2 elements")
    val runTemplate1 = RunTemplate(id = "runTemplateId1")
    val runTemplate2 = RunTemplate(id = "runTemplateId2")
    solutionApiService.updateSolutionRunTemplates(
      solutionSaved.organizationId, solutionSaved.id, listOf(runTemplate1, runTemplate2))
    val foundSolution =
        solutionApiService.getSolution(solutionSaved.organizationId, solutionSaved.id)
    assertEquals(2, foundSolution.runTemplates.size)

    logger.info(
        "should replace the first run template and assert that the list contains 2 elements")
    val labels: MutableMap<String, String>? = mutableMapOf("fr" to "runTemplateName")
    val runTemplate3 = RunTemplate(id = "runTemplateId1", labels = labels)
    solutionApiService.updateSolutionRunTemplates(
      solutionSaved.organizationId, solutionSaved.id, listOf(runTemplate3))
    val foundSolutionAfterReplace =
        solutionApiService.getSolution(solutionSaved.organizationId, solutionSaved.id)
    assertEquals(2, foundSolutionAfterReplace.runTemplates.size)
    assertEquals(
        "runTemplateName", foundSolutionAfterReplace.runTemplates.first().labels?.get("fr"))

    logger.info("should update the run template and assert that the name has been updated")
    labels?.set("fr", "runTemplateNameNew")
    val runTemplate4 = RunTemplate(id = "runTemplateId1", labels = labels)
    solutionApiService.updateSolutionRunTemplate(
      solutionSaved.organizationId, solutionSaved.id, runTemplate4.id, runTemplate4)
    val foundSolutionAfterUpdate =
        solutionApiService.getSolution(solutionSaved.organizationId, solutionSaved.id)
    assertEquals(
        "runTemplateNameNew", foundSolutionAfterUpdate.runTemplates.first().labels?.get("fr"))

    logger.info("should remove all run templates and assert that the list is empty")
    solutionApiService.deleteSolutionRunTemplates(solutionSaved.organizationId, solutionSaved.id)
    val foundSolutionAfterRemove =
        solutionApiService.getSolution(solutionSaved.organizationId, solutionSaved.id)
    assertTrue(foundSolutionAfterRemove.runTemplates.isEmpty())
  }

  @Test
  fun `test find All Solutions with different pagination params`() {
    val numberOfSolutions = 20
    val defaultPageSize = csmPlatformProperties.twincache.solution.defaultPageSize
    val expectedSize = 15
    IntRange(1, numberOfSolutions - 1).forEach {
      solutionApiService.createSolution(
          organizationId = organizationSaved.id, solutionCreateRequest = makeSolution(organizationSaved.id))
    }
    logger.info("should find all solutions and assert there are $numberOfSolutions")
    var solutions = solutionApiService.listSolutions(organizationSaved.id, null, null)
    assertEquals(numberOfSolutions, solutions.size)

    logger.info("should find all solutions and assert it equals defaultPageSize: $defaultPageSize")
    solutions = solutionApiService.listSolutions(organizationSaved.id, 0, null)
    assertEquals(defaultPageSize, solutions.size)

    logger.info("should find all solutions and assert there are expected size: $expectedSize")
    solutions = solutionApiService.listSolutions(organizationSaved.id, 0, expectedSize)
    assertEquals(expectedSize, solutions.size)

    logger.info("should find all solutions and assert it returns the second / last page")
    solutions = solutionApiService.listSolutions(organizationSaved.id, 1, expectedSize)
    assertEquals(numberOfSolutions - expectedSize, solutions.size)
  }

  @Test
  fun `test find All Solutions with wrong pagination params`() {
    logger.info("should throw IllegalArgumentException when page and size are zero")
    assertThrows<IllegalArgumentException> {
      solutionApiService.listSolutions(organizationSaved.id, null, 0)
    }
    logger.info("should throw IllegalArgumentException when page is negative")
    assertThrows<IllegalArgumentException> {
      solutionApiService.listSolutions(organizationSaved.id, -1, 1)
    }
    logger.info("should throw IllegalArgumentException when size is negative")
    assertThrows<IllegalArgumentException> {
      solutionApiService.listSolutions(organizationSaved.id, 0, -1)
    }
  }

  @Test
  fun `test create solution with null runTemplates`() {

    val solutionWithNullRunTemplates = makeSolution(organizationSaved.id)
    val solutionWithNullRunTemplatesSaved =
        solutionApiService.createSolution(organizationSaved.id, solutionWithNullRunTemplates)

    assertNotNull(solutionWithNullRunTemplatesSaved)
    assertNotNull(solutionWithNullRunTemplatesSaved.runTemplates)
    assertEquals(0, solutionWithNullRunTemplatesSaved.runTemplates.size)
  }

  @Test
  fun `test create solution with empty runTemplates list`() {

    val solutionWithNullRunTemplates =
        makeSolution(organizationSaved.id, runTemplates = mutableListOf())
    val solutionWithNullRunTemplatesSaved =
        solutionApiService.createSolution(organizationSaved.id, solutionWithNullRunTemplates)

    assertNotNull(solutionWithNullRunTemplatesSaved)
    assertNotNull(solutionWithNullRunTemplatesSaved.runTemplates)
    assertEquals(0, solutionWithNullRunTemplatesSaved.runTemplates.size)
  }

  @Test
  fun `test update solution RunTemplate with wrong runTemplateId`() {

    val baseSolution = makeSolution(organizationSaved.id)
    val baseSolutionSaved = solutionApiService.createSolution(organizationSaved.id, baseSolution)

    val assertThrows =
        assertThrows<CsmResourceNotFoundException> {
          solutionApiService.updateSolutionRunTemplate(
              organizationSaved.id,
            baseSolutionSaved.id,
              "WrongRunTemplateId",
              RunTemplate(id = "FakeRunTemplateId"))
        }
    assertEquals("Run Template 'WrongRunTemplateId' *not* found", assertThrows.message)
  }

  @Test
  fun `test get security endpoint`() {
    // should return the current security
    val solutionSecurity =
        solutionApiService.getSolutionSecurity(organizationSaved.id, solutionSaved.id)
    assertEquals(solutionSaved.security, solutionSecurity)
  }

  @Test
  fun `test set default security endpoint`() {
    // should update the default security and assert it worked
    val solutionDefaultSecurity =
        solutionApiService.updateSolutionDefaultSecurity(
            organizationSaved.id, solutionSaved.id, SolutionRole(ROLE_VIEWER))
    solutionSaved = solutionApiService.getSolution(organizationSaved.id, solutionSaved.id)
    assertEquals(solutionSaved.security, solutionDefaultSecurity)
  }

  @Test
  fun `access control list shouldn't contain more than one time each user on creation`() {
    organizationSaved =
        organizationApiService.createOrganization(makeOrganizationCreateRequest("organization"))
    val brokenSolution =
        SolutionCreateRequest(
          name = "solution",
          key = "key",
          repository = "repository",
          runTemplates = mutableListOf(RunTemplate("templates")),
          csmSimulator = "simulator",
          parameters = mutableListOf(RunTemplateParameter("parameter")),
          parameterGroups = mutableListOf(RunTemplateParameterGroup("group")),
          version = "1.0.0",
          security =
              SolutionSecurity(
                  default = ROLE_NONE,
                  accessControlList =
                      mutableListOf(
                          SolutionAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                          SolutionAccessControl(CONNECTED_ADMIN_USER, ROLE_EDITOR))))
    assertThrows<IllegalArgumentException> {
      solutionApiService.createSolution(organizationSaved.id, brokenSolution)
    }
  }

  @Test
  fun `access control list shouldn't contain more than one time each user on ACL addition`() {
    organizationSaved =
        organizationApiService.createOrganization(makeOrganizationCreateRequest("organization"))
    val workingSolution = makeSolution()
    solutionSaved = solutionApiService.createSolution(organizationSaved.id, workingSolution)

    assertThrows<IllegalArgumentException> {
      solutionApiService.createSolutionAccessControl(
          organizationSaved.id,
        solutionSaved.id,
          SolutionAccessControl(CONNECTED_ADMIN_USER, ROLE_EDITOR))
    }
  }

  @Test
  fun `As viewer, I can only see my information in security property for getSolution`() {
    solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_READER_USER

    solutionSaved = solutionApiService.getSolution(organizationSaved.id, solutionSaved.id)
    assertEquals(
        SolutionSecurity(
            default = ROLE_NONE,
            mutableListOf(SolutionAccessControl(CONNECTED_READER_USER, ROLE_VIEWER))),
        solutionSaved.security)
    assertEquals(1, solutionSaved.security.accessControlList.size)
  }

  @Test
  fun `As viewer, I can only see my information in security property for listSolutions`() {
    solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_READER_USER

    val solutions = solutionApiService.listSolutions(organizationSaved.id, null, null)
    solutions.forEach {
      assertEquals(
          SolutionSecurity(
              default = ROLE_NONE,
              mutableListOf(SolutionAccessControl(CONNECTED_READER_USER, ROLE_VIEWER))),
          it.security)
      assertEquals(1, it.security.accessControlList.size)
    }
  }

  @Test
  fun `assert createSolution take all infos in considerations`(){
    val solutionToCreate = Solution(
      id = "id",
      organizationId = organizationSaved.id,
      key = "key",
      name = "name",
      ownerId = "ownerId",
      description = "description",
      version = "1.0.0",
      tags = mutableListOf("tag1", "tag2"),
      url = "url",
      runTemplates = mutableListOf(RunTemplate(id = "template")),
      parameters = mutableListOf(RunTemplateParameter(id = "parameter")),
      parameterGroups = mutableListOf(RunTemplateParameterGroup(id = "group")),
      csmSimulator = "simulator",
      repository = "repository",
      alwaysPull = true,
      sdkVersion = "1.0.0",
      security = SolutionSecurity(
        default = ROLE_ADMIN,
        accessControlList = mutableListOf(SolutionAccessControl("id", ROLE_ADMIN)))
    )
    val solutionCreateRequest = SolutionCreateRequest(
      key = solutionToCreate.key,
      name = solutionToCreate.name,
      description = solutionToCreate.description,
      version = solutionToCreate.version,
      tags = solutionToCreate.tags,
      repository = solutionToCreate.repository,
      runTemplates = solutionToCreate.runTemplates,
      parameterGroups = solutionToCreate.parameterGroups,
      parameters = solutionToCreate.parameters,
      security = solutionToCreate.security,
      sdkVersion = solutionToCreate.sdkVersion,
      csmSimulator = solutionToCreate.csmSimulator,
      url = solutionToCreate.url,
      alwaysPull = solutionToCreate.alwaysPull,
    )

    solutionSaved = solutionApiService.createSolution(organizationSaved.id, solutionCreateRequest)

    solutionToCreate.id = solutionSaved.id
    solutionToCreate.ownerId = solutionSaved.ownerId
    assertEquals(solutionToCreate, solutionSaved)
  }

  @Test
  fun `assert updateSolution take all infos in considerations`(){
    var solutionToCreate = Solution(
      id = "id",
      organizationId = organizationSaved.id,
      key = "key",
      name = "name",
      ownerId = "ownerId",
      description = "description",
      version = "1.0.0",
      tags = mutableListOf("tag1", "tag2"),
      url = "url",
      runTemplates = mutableListOf(RunTemplate(id = "template")),
      parameters = mutableListOf(RunTemplateParameter(id = "parameter")),
      parameterGroups = mutableListOf(RunTemplateParameterGroup(id = "group")),
      csmSimulator = "simulator",
      repository = "repository",
      alwaysPull = true,
      sdkVersion = "1.0.0",
      security = SolutionSecurity(
        default = ROLE_ADMIN,
        accessControlList = mutableListOf(SolutionAccessControl("id", ROLE_ADMIN)))
    )
    val solutionCreateRequest = SolutionCreateRequest(
      key = solutionToCreate.key,
      name = solutionToCreate.name,
      description = solutionToCreate.description,
      version = solutionToCreate.version,
      tags = solutionToCreate.tags,
      repository = solutionToCreate.repository,
      runTemplates = solutionToCreate.runTemplates,
      parameterGroups = solutionToCreate.parameterGroups,
      parameters = solutionToCreate.parameters,
      csmSimulator = solutionToCreate.csmSimulator,
      security = solutionToCreate.security,
    )
    solutionSaved = solutionApiService.createSolution(organizationSaved.id, solutionCreateRequest)

    val solutionUpdateRequest = SolutionUpdateRequest(
      key = "new key",
      name = "new name",
      description = "new description",
      tags = mutableListOf("newTag1", "newTag2"),
      alwaysPull = false,
      repository = "new_repo",
      sdkVersion = "2.0.0",
      csmSimulator = "new_simulator",
      url = "new_url",
      version = "2.0.0"
    )
    val solutionUpdated = solutionToCreate.copy(
      id = solutionSaved.id,
      key = solutionUpdateRequest.key!!,
      name = solutionUpdateRequest.name!!,
      ownerId = solutionSaved.ownerId,
      description = solutionUpdateRequest.description,
      tags = solutionUpdateRequest.tags,
      alwaysPull = solutionUpdateRequest.alwaysPull,
      sdkVersion = solutionUpdateRequest.sdkVersion,
      csmSimulator = solutionUpdateRequest.csmSimulator!!,
      url = solutionUpdateRequest.url,
      repository = solutionUpdateRequest.repository!!,
      version = solutionUpdateRequest.version!!,
    )

    solutionSaved = solutionApiService.updateSolution(organizationSaved.id, solutionSaved.id, solutionUpdateRequest)

    assertEquals(solutionUpdated, solutionSaved)
  }

  fun makeOrganizationCreateRequest(id: String = "organization_id"): OrganizationCreateRequest {
    return OrganizationCreateRequest(
        name = "Organization Name",
        security =
            OrganizationSecurity(
                default = ROLE_NONE,
                accessControlList =
                    mutableListOf(
                        OrganizationAccessControl(id = CONNECTED_READER_USER, role = ROLE_VIEWER),
                        OrganizationAccessControl(id = CONNECTED_ADMIN_USER, role = ROLE_ADMIN))))
  }

  fun makeSolution(
    organizationId: String = organizationSaved.id,
    runTemplates: MutableList<RunTemplate> = mutableListOf(),
    version: String = "1.0.0",
    repository: String = "repository",
    csmSimulator: String = "simulator",
    parameter: MutableList<RunTemplateParameter> = mutableListOf(),
    parameterGroup: MutableList<RunTemplateParameterGroup> = mutableListOf(),
    userName: String = CONNECTED_READER_USER,
    role: String = ROLE_VIEWER
  ) = SolutionCreateRequest(
        key = UUID.randomUUID().toString(),
        name = "My solution",
        runTemplates = runTemplates,
        version = version,
        repository = repository,
        csmSimulator = csmSimulator,
        parameters = parameter,
        parameterGroups = parameterGroup,
        security =
            SolutionSecurity(
                default = ROLE_NONE,
                accessControlList =
                    mutableListOf(
                        SolutionAccessControl(id = userName, role = role),
                        SolutionAccessControl(id = CONNECTED_ADMIN_USER, role = ROLE_ADMIN))))
  }
