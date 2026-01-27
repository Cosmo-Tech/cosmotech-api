// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.solution.service

import com.cosmotech.common.config.CsmPlatformProperties
import com.cosmotech.common.containerregistry.ContainerRegistryService
import com.cosmotech.common.exceptions.CsmAccessForbiddenException
import com.cosmotech.common.exceptions.CsmResourceNotFoundException
import com.cosmotech.common.rbac.ROLE_ADMIN
import com.cosmotech.common.rbac.ROLE_EDITOR
import com.cosmotech.common.rbac.ROLE_NONE
import com.cosmotech.common.rbac.ROLE_USER
import com.cosmotech.common.rbac.ROLE_VIEWER
import com.cosmotech.common.security.ROLE_PLATFORM_ADMIN
import com.cosmotech.common.tests.CsmTestBase
import com.cosmotech.common.utils.getCurrentAccountGroups
import com.cosmotech.common.utils.getCurrentAccountIdentifier
import com.cosmotech.common.utils.getCurrentAuthenticatedRoles
import com.cosmotech.common.utils.getCurrentAuthenticatedUserName
import com.cosmotech.organization.OrganizationApiServiceInterface
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationCreateRequest
import com.cosmotech.organization.domain.OrganizationSecurity
import com.cosmotech.solution.SolutionApiServiceInterface
import com.cosmotech.solution.domain.*
import com.redis.om.spring.indexing.RediSearchIndexer
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.mockkStatic
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.util.ReflectionTestUtils

const val CONNECTED_ADMIN_USER = "test.admin@cosmotech.com"
const val CONNECTED_READER_USER = "test.user@cosmotech.com"

@ActiveProfiles(profiles = ["solution-test"])
@ExtendWith(MockKExtension::class)
@ExtendWith(SpringExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Suppress("FunctionName")
class SolutionServiceIntegrationTest : CsmTestBase() {

  private val logger = LoggerFactory.getLogger(SolutionServiceIntegrationTest::class.java)

  @Autowired lateinit var rediSearchIndexer: RediSearchIndexer
  @Autowired lateinit var organizationApiService: OrganizationApiServiceInterface
  @Autowired lateinit var solutionApiService: SolutionApiServiceInterface
  @Autowired lateinit var csmPlatformProperties: CsmPlatformProperties

  private var containerRegistryService: ContainerRegistryService = mockk(relaxed = true)
  private var startTime: Long = 0

  lateinit var organization: OrganizationCreateRequest
  lateinit var solution: SolutionCreateRequest

  lateinit var organizationSaved: Organization
  lateinit var solutionSaved: Solution

  @BeforeEach
  fun setUp() {
    mockkStatic("com.cosmotech.common.utils.SecurityUtilsKt")
    ReflectionTestUtils.setField(
        solutionApiService,
        "containerRegistryService",
        containerRegistryService,
    )
    every { containerRegistryService.getImageLabel(any(), any(), any()) } returns null
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
    every { getCurrentAccountGroups(any()) } returns listOf("myTestGroup")
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "test.user"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("user")

    startTime = Instant.now().toEpochMilli()

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
        makeSolution(
            runTemplates =
                mutableListOf(
                    RunTemplateCreateRequest(id = "one", parameterGroups = mutableListOf()),
                    RunTemplateCreateRequest(id = "two", parameterGroups = mutableListOf()),
                )
        )
    solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

    val runTemplateUpdated =
        solutionApiService.updateSolutionRunTemplate(
            organizationSaved.id,
            solutionSaved.id,
            "one",
            RunTemplateUpdateRequest(name = "name_one"),
        )

    val solutionUpdated = solutionApiService.getSolution(organizationSaved.id, solutionSaved.id)
    // Assert that no runTemplate were deleted
    assertEquals(2, solutionUpdated.runTemplates.size)
    val oneRunTemplate = solutionUpdated.runTemplates.first { it.id == "one" }
    assertEquals("one", oneRunTemplate.id)
    assertEquals("name_one", runTemplateUpdated.name)
  }

  @Test
  fun `test CRUD operations on Solution`() {

    logger.info("should add a new solution")
    val solution2 = makeSolution(organizationSaved.id)
    val solutionCreated = solutionApiService.createSolution(organizationSaved.id, solution2)

    logger.info("should find the new solution by id and assert it is the same as the one created")
    val solutionFound = solutionApiService.getSolution(organizationSaved.id, solutionCreated.id)
    assertEquals(solutionCreated, solutionFound)

    logger.info(
        "should find all solutions for the organization and assert the list contains 2 elements"
    )
    val solutionsFound = solutionApiService.listSolutions(organizationSaved.id, null, null)
    assertTrue(solutionsFound.size == 2)

    logger.info("should update the solution and assert that the name has been updated")
    val solutionUpdateRequest = SolutionUpdateRequest(name = "My solution updated")
    val solutionUpdated =
        solutionApiService.updateSolution(
            organizationSaved.id,
            solutionCreated.id,
            solutionUpdateRequest,
        )
    assertEquals("My solution updated", solutionUpdated.name)

    logger.info(
        "should delete the solution and assert that the list of solutions contains only 1 element"
    )
    solutionApiService.deleteSolution(organizationSaved.id, solutionCreated.id)
    val solutionsFoundAfterDelete =
        solutionApiService.listSolutions(organizationSaved.id, null, null)
    assertTrue(solutionsFoundAfterDelete.size == 1)
  }

  @Test
  fun `test create solution with a run template with wrong runSizing`() {

    val solutionKey = "key"
    val solutionName = "name"
    val solutionDescription = "description"
    val solutionVersion = "1.0.0"
    val solutionTags = mutableListOf("tag1", "tag2")
    val solutionUrl = "url"
    val solutionRunTemplates =
        mutableListOf(
            RunTemplateCreateRequest(
                id = "template",
                runSizing =
                    RunTemplateResourceSizing(
                        requests = ResourceSizeInfo(cpu = "3Go", memory = "3Go"),
                        limits = ResourceSizeInfo(cpu = "4Go", memory = "4Go"),
                    ),
            )
        )
    val solutionParameterGroups =
        mutableListOf(RunTemplateParameterGroupCreateRequest(id = "group"))
    val solutionRepository = "repository"

    val solutionCreateRequest =
        SolutionCreateRequest(
            key = solutionKey,
            name = solutionName,
            description = solutionDescription,
            version = solutionVersion,
            tags = solutionTags,
            repository = solutionRepository,
            runTemplates = solutionRunTemplates,
            parameterGroups = solutionParameterGroups,
            parameters =
                mutableListOf(
                    RunTemplateParameterCreateRequest(id = "parameterName", varType = "string")
                ),
            security =
                SolutionSecurity(
                    default = ROLE_ADMIN,
                    accessControlList = mutableListOf(SolutionAccessControl("user_id", ROLE_ADMIN)),
                ),
            url = solutionUrl,
            alwaysPull = true,
        )
    val exception =
        assertThrows<IllegalArgumentException> {
          solutionApiService.createSolution(organizationSaved.id, solutionCreateRequest)
        }

    assertEquals(
        "Invalid quantity format. " +
            "Please check runSizing values " +
            "{requests.cpu=3Go, requests.memory=3Go, limits.cpu=4Go, limits.memory=4Go}",
        exception.message,
    )
  }

  @Test
  fun `test update solution with wrong runTemplates resource sizing`() {

    val exception =
        assertThrows<IllegalArgumentException> {
          solutionApiService.updateSolution(
              organizationSaved.id,
              solutionSaved.id,
              SolutionUpdateRequest(
                  runTemplates =
                      mutableListOf(
                          RunTemplateCreateRequest(
                              id = "template",
                              runSizing =
                                  RunTemplateResourceSizing(
                                      requests = ResourceSizeInfo(cpu = "3Go", memory = "3Go"),
                                      limits = ResourceSizeInfo(cpu = "4Go", memory = "4Go"),
                                  ),
                          )
                      )
              ),
          )
        }
    assertEquals(
        "Invalid quantity format. " +
            "Please check runSizing values " +
            "{requests.cpu=3Go, requests.memory=3Go, limits.cpu=4Go, limits.memory=4Go}",
        exception.message,
    )
  }

  @Test
  fun `test update solution with empty SolutionUpdateRequest`() {

    val solutionKey = "key"
    val solutionName = "name"
    val solutionDescription = "description"
    val solutionVersion = "1.0.0"
    val solutionTags = mutableListOf("tag1", "tag2")
    val solutionUrl = "url"
    val solutionRunTemplates = mutableListOf(RunTemplateCreateRequest(id = "template"))
    val solutionParameterGroups =
        mutableListOf(RunTemplateParameterGroupCreateRequest(id = "group"))
    val solutionRepository = "repository"

    val solutionCreateRequest =
        SolutionCreateRequest(
            key = solutionKey,
            name = solutionName,
            description = solutionDescription,
            version = solutionVersion,
            tags = solutionTags,
            repository = solutionRepository,
            runTemplates = solutionRunTemplates,
            parameterGroups = solutionParameterGroups,
            parameters =
                mutableListOf(
                    RunTemplateParameterCreateRequest(id = "parameterName", varType = "string")
                ),
            security =
                SolutionSecurity(
                    default = ROLE_ADMIN,
                    accessControlList = mutableListOf(SolutionAccessControl("user_id", ROLE_ADMIN)),
                ),
            url = solutionUrl,
            alwaysPull = true,
        )
    val newSolution = solutionApiService.createSolution(organizationSaved.id, solutionCreateRequest)

    val solutionUpdated =
        solutionApiService.updateSolution(
            organizationSaved.id,
            newSolution.id,
            SolutionUpdateRequest(),
        )
    assertEquals(newSolution, solutionUpdated)
  }

  @TestFactory
  fun `sdkVersion on Solution CRUD`() =
      mapOf(
              "no image label" to null,
              "with image label" to "11.3.0-rc1-456.abc",
          )
          .map { (name, imageLabel) ->
            DynamicTest.dynamicTest("sdkVersion on Solution CRUD: $name") {
              every { containerRegistryService.getImageLabel(any(), any(), any()) } returns
                  imageLabel

              val solutionCreated =
                  solutionApiService.createSolution(
                      organizationSaved.id,
                      makeSolution(organizationSaved.id),
                  )
              assertEquals(imageLabel, solutionCreated.sdkVersion)

              assertEquals(
                  imageLabel,
                  solutionApiService
                      .getSolution(organizationSaved.id, solutionCreated.id)
                      .sdkVersion,
              )

              val solutions = solutionApiService.listSolutions(organizationSaved.id, null, null)
              assertFalse(solutions.isEmpty())
              solutions.forEach { assertEquals(imageLabel, it.sdkVersion) }

              val solutionUpdateRequest = SolutionUpdateRequest(name = "New name")
              val solutionUpdated =
                  solutionApiService.updateSolution(
                      organizationSaved.id,
                      solutionCreated.id,
                      solutionUpdateRequest,
                  )
              assertEquals(solutionUpdateRequest.name, solutionUpdated.name)
              assertEquals(imageLabel, solutionUpdated.sdkVersion)
            }
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
            organizationSaved.id,
            solutionCreated.id,
            solutionUpdateRequest,
        )

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
      solutionApiService.updateSolution(
          organizationSaved.id,
          solutionCreated.id,
          solutionUpdateRequest,
      )
    }
  }

  @Test
  fun `test empty list solution parameters`() {
    val parameterList =
        solutionApiService.listSolutionParameters(organizationSaved.id, solutionSaved.id)
    assertTrue(parameterList.isEmpty())
  }

  @Test
  fun `test list solution parameters with non-existing solution`() {
    val exception =
        assertThrows<CsmResourceNotFoundException> {
          solutionApiService.listSolutionParameters(
              organizationSaved.id,
              "non-existing-solution-id",
          )
        }
    assertEquals(
        "Solution 'non-existing-solution-id' not found in Organization '${organizationSaved.id}'",
        exception.message,
    )
  }

  @Test
  fun `test list solution parameters`() {

    val newSolutionWithParameters =
        makeSolution(
            organizationSaved.id,
            parameter =
                mutableListOf(
                    RunTemplateParameterCreateRequest(
                        id = "parameterName",
                        varType = "int",
                        defaultValue = "0",
                        minValue = "0",
                        maxValue = "100",
                        description = "this_is_a_description",
                        labels = mutableMapOf("fr" to "this_is_a_label"),
                        additionalData = mutableMapOf("option1" to "value1", "option2" to 10.0),
                    ),
                    RunTemplateParameterCreateRequest(
                        id = "parameterName2",
                        varType = "int",
                        defaultValue = "5",
                        minValue = "0",
                        maxValue = "1000",
                        description = "this_is_a_description2",
                        labels = mutableMapOf("fr" to "this_is_a_label2"),
                        additionalData = mutableMapOf("option1" to "value1", "option2" to 100.8),
                    ),
                ),
        )

    val newSolution =
        solutionApiService.createSolution(organizationSaved.id, newSolutionWithParameters)
    val parameterList =
        solutionApiService.listSolutionParameters(organizationSaved.id, newSolution.id)

    assertEquals(2, parameterList.size)
    val firstParam = parameterList[0]
    assertEquals("parameterName", firstParam.id)
    assertEquals("int", firstParam.varType)
    assertEquals("0", firstParam.defaultValue)
    assertEquals("0", firstParam.minValue)
    assertEquals("100", firstParam.maxValue)
    assertEquals("this_is_a_description", firstParam.description)
    assertEquals(mutableMapOf("fr" to "this_is_a_label"), firstParam.labels)
    assertEquals(2, firstParam.additionalData?.size)
    assertEquals("value1", firstParam.additionalData?.get("option1"))
    assertEquals(10.0, firstParam.additionalData?.get("option2"))
    val secondParam = parameterList[1]
    assertEquals("parameterName2", secondParam.id)
    assertEquals("int", secondParam.varType)
    assertEquals("5", secondParam.defaultValue)
    assertEquals("0", secondParam.minValue)
    assertEquals("1000", secondParam.maxValue)
    assertEquals("this_is_a_description2", secondParam.description)
    assertEquals(mutableMapOf("fr" to "this_is_a_label2"), secondParam.labels)
    assertEquals(2, secondParam.additionalData?.size)
    assertEquals("value1", secondParam.additionalData?.get("option1"))
    assertEquals(100.8, secondParam.additionalData?.get("option2"))
  }

  @Test
  fun `test get solution parameter`() {
    val newSolutionWithParameters =
        makeSolution(
            organizationSaved.id,
            parameter =
                mutableListOf(
                    RunTemplateParameterCreateRequest(
                        id = "parameterName",
                        varType = "int",
                        defaultValue = "0",
                        minValue = "0",
                        maxValue = "100",
                        description = "this_is_a_description",
                        labels = mutableMapOf("fr" to "this_is_a_label"),
                        additionalData = mutableMapOf("option1" to "value1", "option2" to 10.0),
                    ),
                    RunTemplateParameterCreateRequest(
                        id = "parameterName2",
                        varType = "int",
                        defaultValue = "5",
                        minValue = "0",
                        maxValue = "1000",
                        description = "this_is_a_description2",
                        labels = mutableMapOf("fr" to "this_is_a_label2"),
                        additionalData = mutableMapOf("option1" to "value1", "option2" to 100.8),
                    ),
                ),
        )

    val newSolution =
        solutionApiService.createSolution(organizationSaved.id, newSolutionWithParameters)

    val solutionParameter =
        solutionApiService.getSolutionParameter(
            organizationSaved.id,
            newSolution.id,
            newSolution.parameters[0].id,
        )

    assertNotNull(solutionParameter)
    assertEquals("parameterName", solutionParameter.id)
    assertEquals("int", solutionParameter.varType)
    assertEquals("0", solutionParameter.defaultValue)
    assertEquals("0", solutionParameter.minValue)
    assertEquals("100", solutionParameter.maxValue)
    assertEquals("this_is_a_description", solutionParameter.description)
    assertEquals(mutableMapOf("fr" to "this_is_a_label"), solutionParameter.labels)
    assertEquals(2, solutionParameter.additionalData?.size)
    assertEquals("value1", solutionParameter.additionalData?.get("option1"))
    assertEquals(10.0, solutionParameter.additionalData?.get("option2"))
  }

  @Test
  fun `test get solution parameter with non-existing parameter`() {
    val exception =
        assertThrows<CsmResourceNotFoundException> {
          solutionApiService.getSolutionParameter(
              organizationSaved.id,
              solutionSaved.id,
              "non-existing-solution-parameter-id",
          )
        }
    assertEquals(
        "Solution parameter with id non-existing-solution-parameter-id does not exist",
        exception.message,
    )
  }

  @Test
  fun `test update solution parameter`() {
    val newSolutionWithParameters =
        makeSolution(
            organizationSaved.id,
            parameter =
                mutableListOf(
                    RunTemplateParameterCreateRequest(
                        id = "parameterName",
                        varType = "int",
                        defaultValue = "0",
                        minValue = "0",
                        maxValue = "100",
                        description = "this_is_a_description",
                        labels = mutableMapOf("fr" to "this_is_a_label"),
                        additionalData = mutableMapOf("option1" to "value1", "option2" to 10.0),
                    ),
                    RunTemplateParameterCreateRequest(
                        id = "parameterName2",
                        varType = "int",
                        defaultValue = "5",
                        minValue = "0",
                        maxValue = "1000",
                        description = "this_is_a_description2",
                        labels = mutableMapOf("fr" to "this_is_a_label2"),
                        additionalData = mutableMapOf("option1" to "value1", "option2" to 100.8),
                    ),
                ),
        )

    val newSolution =
        solutionApiService.createSolution(organizationSaved.id, newSolutionWithParameters)

    val parameterId = newSolution.parameters[0].id
    val solutionParameter =
        solutionApiService.updateSolutionParameter(
            organizationSaved.id,
            newSolution.id,
            parameterId,
            RunTemplateParameterUpdateRequest(
                varType = "string",
                defaultValue = "",
                minValue = "",
                maxValue = "",
                description = "new_this_is_a_description2",
                labels = mutableMapOf("en" to "new_this_is_a_label2"),
                additionalData = mutableMapOf("option1" to "newValue1"),
            ),
        )
    assertNotNull(solutionParameter)
    assertEquals(parameterId, solutionParameter.id)
    assertEquals("string", solutionParameter.varType)
    assertEquals("", solutionParameter.defaultValue)
    assertEquals("", solutionParameter.minValue)
    assertEquals("", solutionParameter.maxValue)
    assertEquals("new_this_is_a_description2", solutionParameter.description)
    assertEquals(mutableMapOf("en" to "new_this_is_a_label2"), solutionParameter.labels)
    assertEquals(1, solutionParameter.additionalData?.size)
    assertEquals("newValue1", solutionParameter.additionalData?.get("option1"))
  }

  @Test
  fun `test update solution parameter with non-existing parameter`() {
    val exception =
        assertThrows<CsmResourceNotFoundException> {
          solutionApiService.updateSolutionParameter(
              organizationSaved.id,
              solutionSaved.id,
              "non-existing-solution-parameter-id",
              RunTemplateParameterUpdateRequest(),
          )
        }
    assertEquals(
        "Solution parameter with id non-existing-solution-parameter-id does not exist",
        exception.message,
    )
  }

  @Test
  fun `test delete solution parameter`() {

    val newSolutionWithParameters =
        makeSolution(
            organizationSaved.id,
            parameter =
                mutableListOf(
                    RunTemplateParameterCreateRequest(
                        id = "parameterName",
                        varType = "int",
                        defaultValue = "0",
                        minValue = "0",
                        maxValue = "100",
                        description = "this_is_a_description",
                        labels = mutableMapOf("fr" to "this_is_a_label"),
                        additionalData = mutableMapOf("option1" to "value1", "option2" to 10.0),
                    ),
                    RunTemplateParameterCreateRequest(
                        id = "parameterName2",
                        varType = "int",
                        defaultValue = "5",
                        minValue = "0",
                        maxValue = "1000",
                        description = "this_is_a_description2",
                        labels = mutableMapOf("fr" to "this_is_a_label2"),
                        additionalData = mutableMapOf("option1" to "value1", "option2" to 100.8),
                    ),
                ),
        )

    val newSolution =
        solutionApiService.createSolution(organizationSaved.id, newSolutionWithParameters)

    var listSolutionParameters =
        solutionApiService.listSolutionParameters(organizationSaved.id, newSolution.id)

    val parameterIdToDelete = listSolutionParameters[0].id
    val parameterIdToKeep = listSolutionParameters[1].id
    solutionApiService.deleteSolutionParameter(
        organizationSaved.id,
        newSolution.id,
        parameterIdToDelete,
    )

    listSolutionParameters =
        solutionApiService.listSolutionParameters(organizationSaved.id, newSolution.id)

    assertNotNull(listSolutionParameters)
    assertEquals(1, listSolutionParameters.size)
    assertEquals(parameterIdToKeep, listSolutionParameters[0].id)
  }

  @Test
  fun `test delete solution parameter with non-existing parameter`() {
    val exception =
        assertThrows<CsmResourceNotFoundException> {
          solutionApiService.deleteSolutionParameter(
              organizationSaved.id,
              solutionSaved.id,
              "non-existing-solution-parameter-id",
          )
        }
    assertEquals(
        "Solution parameter with id non-existing-solution-parameter-id does not exist",
        exception.message,
    )
  }

  @Test
  fun `test create solution parameter with non-existing parameter`() {
    val exception =
        assertThrows<CsmResourceNotFoundException> {
          solutionApiService.createSolutionParameter(
              organizationSaved.id,
              "non-existing-solution-id",
              RunTemplateParameterCreateRequest(
                  id = "my_parameter_name",
                  varType = "my_varType_parameter",
              ),
          )
        }
    assertEquals(
        "Solution 'non-existing-solution-id' not found in Organization '${organizationSaved.id}'",
        exception.message,
    )
  }

  @Test
  fun `test create solution parameter `() {
    val newSolutionWithoutParameters = makeSolution(organizationSaved.id)

    val newSolutionWithEmptyParameters =
        solutionApiService.createSolution(organizationSaved.id, newSolutionWithoutParameters)

    assertTrue(newSolutionWithEmptyParameters.parameters.isEmpty())

    val parameterCreateRequest =
        RunTemplateParameterCreateRequest(
            id = "parameterName2",
            varType = "int",
            defaultValue = "5",
            minValue = "0",
            maxValue = "1000",
            description = "this_is_a_description2",
            labels = mutableMapOf("fr" to "this_is_a_label2"),
            additionalData = mutableMapOf("option1" to "value1", "option2" to 100.8),
        )

    solutionApiService.createSolutionParameter(
        organizationSaved.id,
        newSolutionWithEmptyParameters.id,
        parameterCreateRequest,
    )

    val newSolutionWithNewParameter =
        solutionApiService.getSolution(organizationSaved.id, newSolutionWithEmptyParameters.id)

    assertFalse(newSolutionWithNewParameter.parameters.isEmpty())
    assertEquals(1, newSolutionWithNewParameter.parameters.size)
    val newParam = newSolutionWithNewParameter.parameters[0]
    assertEquals("parameterName2", newParam.id)
    assertEquals("int", newParam.varType)
    assertEquals("5", newParam.defaultValue)
    assertEquals("0", newParam.minValue)
    assertEquals("1000", newParam.maxValue)
    assertEquals("this_is_a_description2", newParam.description)
    assertEquals(mutableMapOf("fr" to "this_is_a_label2"), newParam.labels)
    assertEquals("value1", newParam.additionalData?.get("option1"))
    assertEquals(100.8, newParam.additionalData?.get("option2"))
  }

  @Test
  fun `test create solution parameter with already existing parameter id`() {
    val solutionParameterCreateRequest =
        RunTemplateParameterCreateRequest(
            id = "pAramEterName",
            varType = "int",
            defaultValue = "5",
            minValue = "0",
            maxValue = "1000",
            description = "this_is_a_description2",
            labels = mutableMapOf("fr" to "this_is_a_label2"),
            additionalData = mutableMapOf("option1" to "value1", "option2" to 100.8),
        )
    val newSolutionWithParameter =
        makeSolution(parameter = mutableListOf(solutionParameterCreateRequest))

    val newSolutionWithEmptyParameters =
        solutionApiService.createSolution(organizationSaved.id, newSolutionWithParameter)

    assertEquals(1, newSolutionWithEmptyParameters.parameters.size)

    val exception =
        assertThrows<IllegalArgumentException> {
          solutionApiService.createSolutionParameter(
              organizationSaved.id,
              newSolutionWithEmptyParameters.id,
              solutionParameterCreateRequest,
          )
        }

    assertEquals("Parameter with id 'pAramEterName' already exists", exception.message)
  }

  @Test
  fun `test create solution with several parameters with the same id `() {

    val parametersCreateRequest =
        mutableListOf(
            RunTemplateParameterCreateRequest(
                id = "pAramEterName",
                varType = "int",
                defaultValue = "5",
                minValue = "0",
                maxValue = "1000",
                description = "this_is_a_description2",
                labels = mutableMapOf("fr" to "this_is_a_label2"),
                additionalData = mutableMapOf("option1" to "value1", "option2" to 100.8),
            ),
            RunTemplateParameterCreateRequest(
                id = "ParaMeterName",
                varType = "int",
                defaultValue = "5",
                minValue = "0",
                maxValue = "1000",
                description = "this_is_a_description2",
                labels = mutableMapOf("fr" to "this_is_a_label2"),
                additionalData = mutableMapOf("option1" to "value1", "option2" to 100.8),
            ),
        )

    val newSolutionWithoutParameters = makeSolution(parameter = parametersCreateRequest)

    val exception =
        assertThrows<IllegalArgumentException> {
          solutionApiService.createSolution(organizationSaved.id, newSolutionWithoutParameters)
        }

    assertEquals("One or several solution items have same id : parameters", exception.message)
  }

  @Test
  fun `test find All Solutions with different pagination params`() {
    val numberOfSolutions = 20
    val defaultPageSize = csmPlatformProperties.databases.resources.solution.defaultPageSize
    val expectedSize = 15
    IntRange(1, numberOfSolutions - 1).forEach {
      solutionApiService.createSolution(
          organizationId = organizationSaved.id,
          solutionCreateRequest = makeSolution(organizationSaved.id),
      )
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
              RunTemplateUpdateRequest(),
          )
        }
    assertEquals(
        "Solution run template with id WrongRunTemplateId does not exist",
        assertThrows.message,
    )
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
            organizationSaved.id,
            solutionSaved.id,
            SolutionRole(ROLE_VIEWER),
        )
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
            runTemplates = mutableListOf(RunTemplateCreateRequest(id = "templates")),
            parameters =
                mutableListOf(RunTemplateParameterCreateRequest("parameterName", "string")),
            parameterGroups = mutableListOf(RunTemplateParameterGroupCreateRequest("group")),
            version = "1.0.0",
            security =
                SolutionSecurity(
                    default = ROLE_NONE,
                    accessControlList =
                        mutableListOf(
                            SolutionAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                            SolutionAccessControl(CONNECTED_ADMIN_USER, ROLE_EDITOR),
                        ),
                ),
        )
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
          SolutionAccessControl(CONNECTED_ADMIN_USER, ROLE_EDITOR),
      )
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
            mutableListOf(SolutionAccessControl(CONNECTED_READER_USER, ROLE_VIEWER)),
        ),
        solutionSaved.security,
    )
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
              mutableListOf(SolutionAccessControl(CONNECTED_READER_USER, ROLE_VIEWER)),
          ),
          it.security,
      )
      assertEquals(1, it.security.accessControlList.size)
    }
  }

  @Test
  fun `assert createSolution take all infos in considerations`() {
    val solutionKey = "key"
    val solutionName = "name"
    val solutionDescription = "description"
    val solutionVersion = "1.0.0"
    val solutionTags = mutableListOf("tag1", "tag2")
    val solutionUrl = "url"
    val solutionRunTemplates = mutableListOf(RunTemplateCreateRequest(id = "template"))
    val solutionParameterGroups =
        mutableListOf(RunTemplateParameterGroupCreateRequest(id = "group"))
    val solutionRepository = "repository"

    val solutionCreateRequest =
        SolutionCreateRequest(
            key = solutionKey,
            name = solutionName,
            description = solutionDescription,
            version = solutionVersion,
            tags = solutionTags,
            repository = solutionRepository,
            runTemplates = solutionRunTemplates,
            parameterGroups = solutionParameterGroups,
            parameters =
                mutableListOf(
                    RunTemplateParameterCreateRequest(id = "parameterName", varType = "string")
                ),
            security =
                SolutionSecurity(
                    default = ROLE_ADMIN,
                    accessControlList = mutableListOf(SolutionAccessControl("user_id", ROLE_ADMIN)),
                ),
            url = solutionUrl,
            alwaysPull = true,
        )

    solutionSaved = solutionApiService.createSolution(organizationSaved.id, solutionCreateRequest)

    assertEquals(solutionKey, solutionSaved.key)
    assertEquals(solutionName, solutionSaved.name)
    assertEquals(solutionDescription, solutionSaved.description)
    assertEquals(solutionVersion, solutionSaved.version)
    assertEquals(solutionTags, solutionSaved.tags)
    assertEquals(solutionRepository, solutionSaved.repository)
    assertEquals(solutionRunTemplates.size, solutionSaved.runTemplates.size)
    assertEquals("template", solutionSaved.runTemplates[0].id)
    assertEquals(1, solutionSaved.parameters.size)
    assertEquals("parameterName", solutionSaved.parameters[0].id)
    assertEquals("string", solutionSaved.parameters[0].varType)
    assertEquals(1, solutionSaved.parameterGroups.size)
    assertEquals("group", solutionSaved.parameterGroups[0].id)
    assertEquals(solutionUrl, solutionSaved.url)
    assertEquals(ROLE_ADMIN, solutionSaved.security.default)
    assertEquals(1, solutionSaved.security.accessControlList.size)
    assertEquals("user_id", solutionSaved.security.accessControlList[0].id)
    assertEquals(ROLE_ADMIN, solutionSaved.security.accessControlList[0].role)
    assertTrue(solutionSaved.alwaysPull!!)
  }

  @Test
  fun `assert updateSolution take all infos in considerations`() {
    val solutionKey = "key"
    val solutionName = "name"
    val solutionDescription = "description"
    val solutionVersion = "1.0.0"
    val solutionTags = mutableListOf("tag1", "tag2")
    val solutionUrl = "url"
    val solutionRunTemplates = mutableListOf(RunTemplateCreateRequest(id = "template"))
    val solutionParameterGroups =
        mutableListOf(RunTemplateParameterGroupCreateRequest(id = "group"))
    val solutionRepository = "repository"

    val solutionCreateRequest =
        SolutionCreateRequest(
            key = solutionKey,
            name = solutionName,
            description = solutionDescription,
            version = solutionVersion,
            tags = solutionTags,
            repository = solutionRepository,
            runTemplates = solutionRunTemplates,
            parameterGroups = solutionParameterGroups,
            parameters =
                mutableListOf(
                    RunTemplateParameterCreateRequest(id = "parameterName", varType = "string")
                ),
            security =
                SolutionSecurity(
                    default = ROLE_ADMIN,
                    accessControlList = mutableListOf(SolutionAccessControl("user_id", ROLE_ADMIN)),
                ),
            url = solutionUrl,
            alwaysPull = true,
        )
    solutionSaved = solutionApiService.createSolution(organizationSaved.id, solutionCreateRequest)

    val updatedKey = "new key"
    val updatedName = "new name"
    val updatedDescription = "new description"
    val updatedTags = mutableListOf("newTag1", "newTag2")
    val updatedRepository = "new_repo"
    val newUrl = "new_url"
    val newVersion = "20.0.0"
    val solutionUpdateRequest =
        SolutionUpdateRequest(
            key = updatedKey,
            name = updatedName,
            description = updatedDescription,
            tags = updatedTags,
            alwaysPull = false,
            repository = updatedRepository,
            runTemplates = solutionRunTemplates,
            parameters =
                mutableListOf(
                    RunTemplateParameterCreateRequest(id = "parameterNameUpdated", varType = "int")
                ),
            url = newUrl,
            version = newVersion,
        )

    solutionSaved =
        solutionApiService.updateSolution(
            organizationSaved.id,
            solutionSaved.id,
            solutionUpdateRequest,
        )

    assertEquals(updatedKey, solutionSaved.key)
    assertEquals(updatedName, solutionSaved.name)
    assertEquals(updatedDescription, solutionSaved.description)
    assertEquals(updatedTags, solutionSaved.tags)
    assertEquals(updatedRepository, solutionSaved.repository)
    assertEquals(solutionRunTemplates.size, solutionSaved.runTemplates.size)
    assertEquals("template", solutionSaved.runTemplates[0].id)
    assertEquals(newUrl, solutionSaved.url)
    assertEquals(newVersion, solutionSaved.version)
    assertEquals(1, solutionSaved.parameters.size)
    assertEquals("parameterNameUpdated", solutionSaved.parameters[0].id)
    assertEquals("int", solutionSaved.parameters[0].varType)
    assertEquals(ROLE_ADMIN, solutionSaved.security.default)
    assertEquals(1, solutionSaved.security.accessControlList.size)
    assertEquals("user_id", solutionSaved.security.accessControlList[0].id)
    assertEquals(ROLE_ADMIN, solutionSaved.security.accessControlList[0].role)
    assertFalse(solutionSaved.alwaysPull!!)
  }

  @Test
  fun `assert updateSolution with all information set and empty parameters list in update`() {
    val solutionKey = "key"
    val solutionName = "name"
    val solutionDescription = "description"
    val solutionVersion = "1.0.0"
    val solutionTags = mutableListOf("tag1", "tag2")
    val solutionUrl = "url"
    val solutionRunTemplates = mutableListOf(RunTemplateCreateRequest(id = "template"))
    val solutionParameterGroups =
        mutableListOf(RunTemplateParameterGroupCreateRequest(id = "group"))
    val solutionRepository = "repository"

    val solutionCreateRequest =
        SolutionCreateRequest(
            key = solutionKey,
            name = solutionName,
            description = solutionDescription,
            version = solutionVersion,
            tags = solutionTags,
            repository = solutionRepository,
            runTemplates = solutionRunTemplates,
            parameterGroups = solutionParameterGroups,
            parameters =
                mutableListOf(
                    RunTemplateParameterCreateRequest(id = "parameterName", varType = "string")
                ),
            security =
                SolutionSecurity(
                    default = ROLE_ADMIN,
                    accessControlList = mutableListOf(SolutionAccessControl("user_id", ROLE_ADMIN)),
                ),
            url = solutionUrl,
            alwaysPull = true,
        )
    solutionSaved = solutionApiService.createSolution(organizationSaved.id, solutionCreateRequest)

    val updatedKey = "new key"
    val updatedName = "new name"
    val updatedDescription = "new description"
    val updatedTags = mutableListOf("newTag1", "newTag2")
    val updatedRepository = "new_repo"
    val newUrl = "new_url"
    val newVersion = "20.0.0"
    val solutionUpdateRequest =
        SolutionUpdateRequest(
            key = updatedKey,
            name = updatedName,
            description = updatedDescription,
            runTemplates = solutionRunTemplates,
            tags = updatedTags,
            alwaysPull = false,
            repository = updatedRepository,
            url = newUrl,
            version = newVersion,
            parameters = mutableListOf(),
        )

    solutionSaved =
        solutionApiService.updateSolution(
            organizationSaved.id,
            solutionSaved.id,
            solutionUpdateRequest,
        )

    assertEquals(updatedKey, solutionSaved.key)
    assertEquals(updatedName, solutionSaved.name)
    assertEquals(updatedDescription, solutionSaved.description)
    assertEquals(updatedTags, solutionSaved.tags)
    assertEquals(updatedRepository, solutionSaved.repository)
    assertEquals(solutionRunTemplates.size, solutionSaved.runTemplates.size)
    assertEquals("template", solutionSaved.runTemplates[0].id)
    assertEquals(newUrl, solutionSaved.url)
    assertEquals(newVersion, solutionSaved.version)
    assertEquals(0, solutionSaved.parameters.size)
    assertEquals(ROLE_ADMIN, solutionSaved.security.default)
    assertEquals(1, solutionSaved.security.accessControlList.size)
    assertEquals("user_id", solutionSaved.security.accessControlList[0].id)
    assertEquals(ROLE_ADMIN, solutionSaved.security.accessControlList[0].role)
    assertFalse(solutionSaved.alwaysPull!!)
  }

  @Test
  fun `assert updateSolution with all information set and duplicate id parameters list in update`() {
    val solutionKey = "key"
    val solutionName = "name"
    val solutionDescription = "description"
    val solutionVersion = "1.0.0"
    val solutionTags = mutableListOf("tag1", "tag2")
    val solutionUrl = "url"
    val solutionRunTemplates = mutableListOf(RunTemplateCreateRequest(id = "template"))
    val solutionParameterGroups =
        mutableListOf(RunTemplateParameterGroupCreateRequest(id = "group"))
    val solutionRepository = "repository"

    val solutionCreateRequest =
        SolutionCreateRequest(
            key = solutionKey,
            name = solutionName,
            description = solutionDescription,
            version = solutionVersion,
            tags = solutionTags,
            repository = solutionRepository,
            runTemplates = solutionRunTemplates,
            parameterGroups = solutionParameterGroups,
            parameters =
                mutableListOf(
                    RunTemplateParameterCreateRequest(id = "parameterName", varType = "string")
                ),
            security =
                SolutionSecurity(
                    default = ROLE_ADMIN,
                    accessControlList = mutableListOf(SolutionAccessControl("user_id", ROLE_ADMIN)),
                ),
            url = solutionUrl,
            alwaysPull = true,
        )
    solutionSaved = solutionApiService.createSolution(organizationSaved.id, solutionCreateRequest)

    val updatedKey = "new key"
    val updatedName = "new name"
    val updatedDescription = "new description"
    val updatedTags = mutableListOf("newTag1", "newTag2")
    val updatedRepository = "new_repo"
    val newUrl = "new_url"
    val newVersion = "20.0.0"
    val solutionUpdateRequest =
        SolutionUpdateRequest(
            key = updatedKey,
            name = updatedName,
            description = updatedDescription,
            tags = updatedTags,
            alwaysPull = false,
            repository = updatedRepository,
            parameters =
                mutableListOf(
                    RunTemplateParameterCreateRequest(id = "PaRaMeTeRnAmE", varType = "string"),
                    RunTemplateParameterCreateRequest(id = "pArAmEtErNaMe", varType = "string"),
                ),
            url = newUrl,
            version = newVersion,
        )

    val exception =
        assertThrows<IllegalArgumentException> {
          solutionApiService.updateSolution(
              organizationSaved.id,
              solutionSaved.id,
              solutionUpdateRequest,
          )
        }

    assertEquals("One or several solution items have same id : parameters", exception.message)
  }

  @Test
  fun `assert updateSolution with all information set and empty run template list in update`() {
    val solutionKey = "key"
    val solutionName = "name"
    val solutionDescription = "description"
    val solutionVersion = "1.0.0"
    val solutionTags = mutableListOf("tag1", "tag2")
    val solutionUrl = "url"
    val solutionRunTemplates = mutableListOf(RunTemplateCreateRequest(id = "template"))
    val solutionParameterGroups =
        mutableListOf(RunTemplateParameterGroupCreateRequest(id = "group"))
    val solutionRepository = "repository"

    val solutionCreateRequest =
        SolutionCreateRequest(
            key = solutionKey,
            name = solutionName,
            description = solutionDescription,
            version = solutionVersion,
            tags = solutionTags,
            repository = solutionRepository,
            runTemplates = solutionRunTemplates,
            parameterGroups = solutionParameterGroups,
            parameters =
                mutableListOf(
                    RunTemplateParameterCreateRequest(id = "parameterName", varType = "string")
                ),
            security =
                SolutionSecurity(
                    default = ROLE_ADMIN,
                    accessControlList = mutableListOf(SolutionAccessControl("user_id", ROLE_ADMIN)),
                ),
            url = solutionUrl,
            alwaysPull = true,
        )
    solutionSaved = solutionApiService.createSolution(organizationSaved.id, solutionCreateRequest)

    val updatedKey = "new key"
    val updatedName = "new name"
    val updatedDescription = "new description"
    val updatedTags = mutableListOf("newTag1", "newTag2")
    val updatedRepository = "new_repo"
    val newUrl = "new_url"
    val newVersion = "20.0.0"
    val solutionUpdateRequest =
        SolutionUpdateRequest(
            key = updatedKey,
            name = updatedName,
            description = updatedDescription,
            tags = updatedTags,
            alwaysPull = false,
            repository = updatedRepository,
            url = newUrl,
            version = newVersion,
            runTemplates = mutableListOf(),
            parameters = mutableListOf(),
        )

    solutionSaved =
        solutionApiService.updateSolution(
            organizationSaved.id,
            solutionSaved.id,
            solutionUpdateRequest,
        )

    assertEquals(updatedKey, solutionSaved.key)
    assertEquals(updatedName, solutionSaved.name)
    assertEquals(updatedDescription, solutionSaved.description)
    assertEquals(updatedTags, solutionSaved.tags)
    assertEquals(updatedRepository, solutionSaved.repository)
    assertEquals(0, solutionSaved.runTemplates.size)
    assertEquals(newUrl, solutionSaved.url)
    assertEquals(newVersion, solutionSaved.version)
    assertEquals(0, solutionSaved.parameters.size)
    assertEquals(ROLE_ADMIN, solutionSaved.security.default)
    assertEquals(1, solutionSaved.security.accessControlList.size)
    assertEquals("user_id", solutionSaved.security.accessControlList[0].id)
    assertEquals(ROLE_ADMIN, solutionSaved.security.accessControlList[0].role)
    assertFalse(solutionSaved.alwaysPull!!)
  }

  @Test
  fun `assert updateSolution with all information set and duplicate id run templates list in update`() {
    val solutionKey = "key"
    val solutionName = "name"
    val solutionDescription = "description"
    val solutionVersion = "1.0.0"
    val solutionTags = mutableListOf("tag1", "tag2")
    val solutionUrl = "url"
    val solutionRunTemplates = mutableListOf(RunTemplateCreateRequest(id = "template"))
    val solutionParameterGroups =
        mutableListOf(RunTemplateParameterGroupCreateRequest(id = "group"))
    val solutionRepository = "repository"

    val solutionCreateRequest =
        SolutionCreateRequest(
            key = solutionKey,
            name = solutionName,
            description = solutionDescription,
            version = solutionVersion,
            tags = solutionTags,
            repository = solutionRepository,
            runTemplates = solutionRunTemplates,
            parameterGroups = solutionParameterGroups,
            parameters =
                mutableListOf(
                    RunTemplateParameterCreateRequest(id = "parameterName", varType = "string")
                ),
            security =
                SolutionSecurity(
                    default = ROLE_ADMIN,
                    accessControlList = mutableListOf(SolutionAccessControl("user_id", ROLE_ADMIN)),
                ),
            url = solutionUrl,
            alwaysPull = true,
        )
    solutionSaved = solutionApiService.createSolution(organizationSaved.id, solutionCreateRequest)

    val updatedKey = "new key"
    val updatedName = "new name"
    val updatedDescription = "new description"
    val updatedTags = mutableListOf("newTag1", "newTag2")
    val updatedRepository = "new_repo"
    val newUrl = "new_url"
    val newVersion = "20.0.0"
    val solutionUpdateRequest =
        SolutionUpdateRequest(
            key = updatedKey,
            name = updatedName,
            description = updatedDescription,
            tags = updatedTags,
            alwaysPull = false,
            repository = updatedRepository,
            parameterGroups = solutionParameterGroups,
            runTemplates =
                mutableListOf(
                    RunTemplateCreateRequest(id = "PaRaMeTeRnAmE"),
                    RunTemplateCreateRequest(id = "pArAmEtErNaMe"),
                ),
            url = newUrl,
            version = newVersion,
        )

    val exception =
        assertThrows<IllegalArgumentException> {
          solutionApiService.updateSolution(
              organizationSaved.id,
              solutionSaved.id,
              solutionUpdateRequest,
          )
        }

    assertEquals("One or several solution items have same id : runTemplates", exception.message)
  }

  @Test
  fun `test empty list solution parameter groups`() {
    val parameterGroupList =
        solutionApiService.listSolutionParameterGroups(organizationSaved.id, solutionSaved.id)
    assertTrue(parameterGroupList.isEmpty())
  }

  @Test
  fun `test list solution parameter groups with non-existing solution`() {
    val exception =
        assertThrows<CsmResourceNotFoundException> {
          solutionApiService.listSolutionParameterGroups(
              organizationSaved.id,
              "non-existing-solution-id",
          )
        }
    assertEquals(
        "Solution 'non-existing-solution-id' not found in Organization '${organizationSaved.id}'",
        exception.message,
    )
  }

  @Test
  fun `test list solution parameter groups`() {

    val newSolutionWithParameterGroups =
        makeSolution(
            organizationSaved.id,
            parameterGroup =
                mutableListOf(
                    RunTemplateParameterGroupCreateRequest(
                        id = "parameterGroupId",
                        description = "this_is_a_description",
                        labels = mutableMapOf("fr" to "this_is_a_label"),
                        additionalData = mutableMapOf("option1" to "value1", "option2" to 10.0),
                        parameters = mutableListOf("parameterId1", "parameterId2"),
                    ),
                    RunTemplateParameterGroupCreateRequest(
                        id = "parameterGroupId2",
                        description = "this_is_a_description2",
                        labels = mutableMapOf("fr" to "this_is_a_label2"),
                        additionalData = mutableMapOf("option2" to "value2", "option3" to 20.0),
                        parameters = mutableListOf("parameterId3", "parameterId4"),
                    ),
                ),
        )

    val newSolution =
        solutionApiService.createSolution(organizationSaved.id, newSolutionWithParameterGroups)
    val parameterGroupList =
        solutionApiService.listSolutionParameterGroups(organizationSaved.id, newSolution.id)

    assertEquals(2, parameterGroupList.size)
    val firstParamGroup = parameterGroupList[0]
    assertEquals("parameterGroupId", firstParamGroup.id)
    assertEquals("this_is_a_description", firstParamGroup.description)
    assertEquals(mutableMapOf("fr" to "this_is_a_label"), firstParamGroup.labels)
    assertEquals("value1", firstParamGroup.additionalData?.get("option1"))
    assertEquals(10.0, firstParamGroup.additionalData?.get("option2"))
    assertEquals(mutableListOf("parameterId1", "parameterId2"), firstParamGroup.parameters)
    val secondParamGroup = parameterGroupList[1]
    assertEquals("parameterGroupId2", secondParamGroup.id)
    assertEquals("this_is_a_description2", secondParamGroup.description)
    assertEquals(mutableMapOf("fr" to "this_is_a_label2"), secondParamGroup.labels)
    assertEquals("value2", secondParamGroup.additionalData?.get("option2"))
    assertEquals(20.0, secondParamGroup.additionalData?.get("option3"))
    assertEquals(mutableListOf("parameterId3", "parameterId4"), secondParamGroup.parameters)
  }

  @Test
  fun `test get solution parameter group`() {
    val newSolutionWithParameterGroups =
        makeSolution(
            organizationSaved.id,
            parameterGroup =
                mutableListOf(
                    RunTemplateParameterGroupCreateRequest(
                        id = "parameterGroupId",
                        description = "this_is_a_description",
                        labels = mutableMapOf("fr" to "this_is_a_label"),
                        additionalData = mutableMapOf("option1" to "value1", "option2" to 10.0),
                        parameters = mutableListOf("parameterId1", "parameterId2"),
                    ),
                    RunTemplateParameterGroupCreateRequest(
                        id = "parameterGroupId2",
                        description = "this_is_a_description2",
                        labels = mutableMapOf("fr" to "this_is_a_label2"),
                        additionalData = mutableMapOf("option2" to "value2", "option3" to 20.0),
                        parameters = mutableListOf("parameterId3", "parameterId4"),
                    ),
                ),
        )

    val newSolution =
        solutionApiService.createSolution(organizationSaved.id, newSolutionWithParameterGroups)

    val solutionParameterGroup =
        solutionApiService.getSolutionParameterGroup(
            organizationSaved.id,
            newSolution.id,
            newSolution.parameterGroups[0].id,
        )

    assertNotNull(solutionParameterGroup)
    assertEquals("parameterGroupId", solutionParameterGroup.id)
    assertEquals("this_is_a_description", solutionParameterGroup.description)
    assertEquals(mutableMapOf("fr" to "this_is_a_label"), solutionParameterGroup.labels)
    assertEquals(2, solutionParameterGroup.additionalData?.size)
    assertEquals("value1", solutionParameterGroup.additionalData?.get("option1"))
    assertEquals(10.0, solutionParameterGroup.additionalData?.get("option2"))
    assertEquals(mutableListOf("parameterId1", "parameterId2"), solutionParameterGroup.parameters)
  }

  @Test
  fun `test get solution parameter group with non-existing parameter group`() {
    val exception =
        assertThrows<CsmResourceNotFoundException> {
          solutionApiService.getSolutionParameterGroup(
              organizationSaved.id,
              solutionSaved.id,
              "non-existing-solution-parameter-group-id",
          )
        }
    assertEquals(
        "Solution parameter group with id non-existing-solution-parameter-group-id does not exist",
        exception.message,
    )
  }

  @Test
  fun `test update solution parameter group`() {
    val newSolutionWithParameterGroups =
        makeSolution(
            organizationSaved.id,
            parameterGroup =
                mutableListOf(
                    RunTemplateParameterGroupCreateRequest(
                        id = "parameterGroupId",
                        description = "this_is_a_description",
                        labels = mutableMapOf("fr" to "this_is_a_label"),
                        additionalData = mutableMapOf("option1" to "value1", "option2" to 10.0),
                        parameters = mutableListOf("parameterId1", "parameterId2"),
                    ),
                    RunTemplateParameterGroupCreateRequest(
                        id = "parameterGroupId2",
                        description = "this_is_a_description2",
                        labels = mutableMapOf("fr" to "this_is_a_label2"),
                        additionalData = mutableMapOf("option2" to "value2", "option3" to 20.0),
                        parameters = mutableListOf("parameterId3", "parameterId4"),
                    ),
                ),
        )

    val newSolution =
        solutionApiService.createSolution(organizationSaved.id, newSolutionWithParameterGroups)

    val parameterGroupId = newSolution.parameterGroups[0].id
    val solutionParameterGroup =
        solutionApiService.updateSolutionParameterGroup(
            organizationSaved.id,
            newSolution.id,
            parameterGroupId,
            RunTemplateParameterGroupUpdateRequest(
                description = "this_is_a_description3",
                labels = mutableMapOf("fr" to "this_is_a_label3"),
                additionalData = mutableMapOf("option3" to "value1"),
                parameters = mutableListOf("parameterId13", "parameterId23"),
            ),
        )
    assertNotNull(solutionParameterGroup)
    assertEquals(parameterGroupId, solutionParameterGroup.id)
    assertEquals("this_is_a_description3", solutionParameterGroup.description)
    assertEquals(mutableMapOf("fr" to "this_is_a_label3"), solutionParameterGroup.labels)
    assertEquals(1, solutionParameterGroup.additionalData?.size)
    assertEquals("value1", solutionParameterGroup.additionalData?.get("option3"))
    assertEquals(mutableListOf("parameterId13", "parameterId23"), solutionParameterGroup.parameters)
  }

  @Test
  fun `test update solution parameter group with non-existing parameter group`() {
    val exception =
        assertThrows<CsmResourceNotFoundException> {
          solutionApiService.updateSolutionParameterGroup(
              organizationSaved.id,
              solutionSaved.id,
              "non-existing-solution-parameter-group-id",
              RunTemplateParameterGroupUpdateRequest(),
          )
        }
    assertEquals(
        "Solution parameter group with id non-existing-solution-parameter-group-id does not exist",
        exception.message,
    )
  }

  @Test
  fun `test delete solution parameter group`() {

    val newSolutionWithParameterGroups =
        makeSolution(
            organizationSaved.id,
            parameterGroup =
                mutableListOf(
                    RunTemplateParameterGroupCreateRequest(
                        id = "parameterGroupId",
                        description = "this_is_a_description",
                        labels = mutableMapOf("fr" to "this_is_a_label"),
                        additionalData = mutableMapOf("option1" to "value1", "option2" to 10.0),
                        parameters = mutableListOf("parameterId1", "parameterId2"),
                    ),
                    RunTemplateParameterGroupCreateRequest(
                        id = "parameterGroupId2",
                        description = "this_is_a_description2",
                        labels = mutableMapOf("fr" to "this_is_a_label2"),
                        additionalData = mutableMapOf("option2" to "value2", "option3" to 20.0),
                        parameters = mutableListOf("parameterId3", "parameterId4"),
                    ),
                ),
        )

    val newSolution =
        solutionApiService.createSolution(organizationSaved.id, newSolutionWithParameterGroups)

    var listSolutionParameterGroups =
        solutionApiService.listSolutionParameterGroups(organizationSaved.id, newSolution.id)

    val parameterGroupIdToDelete = listSolutionParameterGroups[0].id
    val parameterGroupIdToKeep = listSolutionParameterGroups[1].id
    solutionApiService.deleteSolutionParameterGroup(
        organizationSaved.id,
        newSolution.id,
        parameterGroupIdToDelete,
    )

    listSolutionParameterGroups =
        solutionApiService.listSolutionParameterGroups(organizationSaved.id, newSolution.id)

    assertNotNull(listSolutionParameterGroups)
    assertEquals(1, listSolutionParameterGroups.size)
    assertEquals(parameterGroupIdToKeep, listSolutionParameterGroups[0].id)
  }

  @Test
  fun `test delete solution parameter group with non-existing parameter group`() {
    val exception =
        assertThrows<CsmResourceNotFoundException> {
          solutionApiService.deleteSolutionParameterGroup(
              organizationSaved.id,
              solutionSaved.id,
              "non-existing-solution-parameter-group-id",
          )
        }
    assertEquals(
        "Solution parameter group with id non-existing-solution-parameter-group-id does not exist",
        exception.message,
    )
  }

  @Test
  fun `test create solution parameter group with non-existing parameter group`() {
    val exception =
        assertThrows<CsmResourceNotFoundException> {
          solutionApiService.createSolutionParameterGroup(
              organizationSaved.id,
              "non-existing-solution-id",
              RunTemplateParameterGroupCreateRequest(id = "my_parameter_group_name"),
          )
        }
    assertEquals(
        "Solution 'non-existing-solution-id' not found in Organization '${organizationSaved.id}'",
        exception.message,
    )
  }

  @Test
  fun `test create solution parameter group`() {
    val newSolutionWithoutParameterGroups = makeSolution(organizationSaved.id)

    val newSolutionWithEmptyParameterGroups =
        solutionApiService.createSolution(organizationSaved.id, newSolutionWithoutParameterGroups)

    assertTrue(newSolutionWithEmptyParameterGroups.parameterGroups.isEmpty())

    val parameterGroupCreateRequest =
        RunTemplateParameterGroupCreateRequest(
            id = "parameterGroupId",
            description = "this_is_a_description",
            labels = mutableMapOf("fr" to "this_is_a_label"),
            additionalData = mutableMapOf("option1" to "value1", "option2" to 10.0),
            parameters = mutableListOf("parameterId1", "parameterId2"),
        )

    solutionApiService.createSolutionParameterGroup(
        organizationSaved.id,
        newSolutionWithEmptyParameterGroups.id,
        parameterGroupCreateRequest,
    )

    val newSolutionWithNewParameter =
        solutionApiService.getSolution(organizationSaved.id, newSolutionWithEmptyParameterGroups.id)

    assertFalse(newSolutionWithNewParameter.parameterGroups.isEmpty())
    assertEquals(1, newSolutionWithNewParameter.parameterGroups.size)
    val newParamGroup = newSolutionWithNewParameter.parameterGroups[0]
    assertEquals("parameterGroupId", newParamGroup.id)
    assertEquals("this_is_a_description", newParamGroup.description)
    assertEquals(mutableMapOf("fr" to "this_is_a_label"), newParamGroup.labels)
    assertEquals(2, newParamGroup.additionalData?.size)
    assertEquals("value1", newParamGroup.additionalData?.get("option1"))
    assertEquals(10.0, newParamGroup.additionalData?.get("option2"))
    assertEquals(mutableListOf("parameterId1", "parameterId2"), newParamGroup.parameters)
  }

  @Test
  fun `test create solution parameter group with already existing parameter group id`() {
    val parameterGroupCreateRequest =
        RunTemplateParameterGroupCreateRequest(
            id = "parameterGroupId",
            description = "this_is_a_description",
            labels = mutableMapOf("fr" to "this_is_a_label"),
            additionalData = mutableMapOf("option1" to "value1", "option2" to 10.0),
            parameters = mutableListOf("parameterId1", "parameterId2"),
        )

    val newSolutionWithParameterGroup =
        solutionApiService.createSolution(
            organizationSaved.id,
            makeSolution(parameterGroup = mutableListOf(parameterGroupCreateRequest)),
        )

    assertEquals(1, newSolutionWithParameterGroup.parameterGroups.size)

    val exception =
        assertThrows<IllegalArgumentException> {
          solutionApiService.createSolutionParameterGroup(
              organizationSaved.id,
              newSolutionWithParameterGroup.id,
              parameterGroupCreateRequest,
          )
        }

    assertEquals("Parameter Group with id 'parameterGroupId' already exists", exception.message)
  }

  @Test
  fun `test create solution with several parameters groups with the same id `() {

    val newSolutionWithParameterGroupsDuplicateIds =
        makeSolution(
            organizationSaved.id,
            parameterGroup =
                mutableListOf(
                    RunTemplateParameterGroupCreateRequest(
                        id = "PaRamEtErGrOuPId",
                        description = "this_is_a_description",
                        labels = mutableMapOf("fr" to "this_is_a_label"),
                        additionalData = mutableMapOf("option1" to "value1", "option2" to 10.0),
                        parameters = mutableListOf("parameterId1", "parameterId2"),
                    ),
                    RunTemplateParameterGroupCreateRequest(
                        id = "pArAmEtErGrOuPId",
                        description = "this_is_a_description2",
                        labels = mutableMapOf("fr" to "this_is_a_label2"),
                        additionalData = mutableMapOf("option2" to "value2", "option3" to 20.0),
                        parameters = mutableListOf("parameterId3", "parameterId4"),
                    ),
                ),
        )

    val exception =
        assertThrows<IllegalArgumentException> {
          solutionApiService.createSolution(
              organizationSaved.id,
              newSolutionWithParameterGroupsDuplicateIds,
          )
        }

    assertEquals("One or several solution items have same id : parameterGroups", exception.message)
  }

  @Test
  fun `test empty list solution run templates`() {
    val runTemplateList =
        solutionApiService.listRunTemplates(organizationSaved.id, solutionSaved.id)
    assertTrue(runTemplateList.isEmpty())
  }

  @Test
  fun `test list run templates with non-existing solution`() {
    val exception =
        assertThrows<CsmResourceNotFoundException> {
          solutionApiService.listRunTemplates(organizationSaved.id, "non-existing-solution-id")
        }
    assertEquals(
        "Solution 'non-existing-solution-id' not found in Organization '${organizationSaved.id}'",
        exception.message,
    )
  }

  @Test
  fun `test list run templates`() {

    val newSolutionWithRunTemplates =
        makeSolution(
            organizationSaved.id,
            runTemplates =
                mutableListOf(
                    RunTemplateCreateRequest(
                        id = "runTemplateId",
                        description = "this_is_a_description",
                        labels = mutableMapOf("fr" to "this_is_a_label"),
                        name = "runTemplateName1",
                        tags = mutableListOf("runTemplateTag1", "runTemplateTag2"),
                        computeSize = "this_is_a_computeSize",
                        runSizing =
                            RunTemplateResourceSizing(
                                requests = ResourceSizeInfo(cpu = "1", memory = "1G"),
                                limits = ResourceSizeInfo(cpu = "2", memory = "2G"),
                            ),
                        parameterGroups = mutableListOf("parameterGroup1", "parameterGroup2"),
                        executionTimeout = 10,
                    ),
                    RunTemplateCreateRequest(
                        id = "runTemplateId2",
                        description = "this_is_a_description2",
                        labels = mutableMapOf("fr" to "this_is_a_label2"),
                        name = "runTemplateName2",
                        tags = mutableListOf("runTemplateTag3", "runTemplateTag4"),
                        computeSize = "this_is_a_computeSize2",
                        runSizing =
                            RunTemplateResourceSizing(
                                requests = ResourceSizeInfo(cpu = "3", memory = "3G"),
                                limits = ResourceSizeInfo(cpu = "4", memory = "4G"),
                            ),
                        parameterGroups = mutableListOf("parameterGroup3", "parameterGroup4"),
                        executionTimeout = 20,
                    ),
                ),
        )

    val newSolution =
        solutionApiService.createSolution(organizationSaved.id, newSolutionWithRunTemplates)
    val runTemplateList = solutionApiService.listRunTemplates(organizationSaved.id, newSolution.id)

    assertEquals(2, runTemplateList.size)
    val firstRunTemplate = runTemplateList[0]
    assertEquals("runTemplateId", firstRunTemplate.id)
    assertEquals("this_is_a_description", firstRunTemplate.description)
    assertEquals(mutableMapOf("fr" to "this_is_a_label"), firstRunTemplate.labels)
    assertEquals("runTemplateName1", firstRunTemplate.name)
    assertEquals(mutableListOf("runTemplateTag1", "runTemplateTag2"), firstRunTemplate.tags)
    assertEquals("this_is_a_computeSize", firstRunTemplate.computeSize)
    assertEquals(
        mutableListOf("parameterGroup1", "parameterGroup2"),
        firstRunTemplate.parameterGroups,
    )
    assertEquals(10, firstRunTemplate.executionTimeout!!)
    assertEquals("1", firstRunTemplate.runSizing?.requests?.cpu)
    assertEquals("1G", firstRunTemplate.runSizing?.requests?.memory)
    assertEquals("2", firstRunTemplate.runSizing?.limits?.cpu)
    assertEquals("2G", firstRunTemplate.runSizing?.limits?.memory)
    val secondRunTemplate = runTemplateList[1]
    assertEquals("runTemplateId2", secondRunTemplate.id)
    assertEquals("this_is_a_description2", secondRunTemplate.description)
    assertEquals(mutableMapOf("fr" to "this_is_a_label2"), secondRunTemplate.labels)
    assertEquals("runTemplateName2", secondRunTemplate.name)
    assertEquals(mutableListOf("runTemplateTag3", "runTemplateTag4"), secondRunTemplate.tags)
    assertEquals("this_is_a_computeSize2", secondRunTemplate.computeSize)
    assertEquals(
        mutableListOf("parameterGroup3", "parameterGroup4"),
        secondRunTemplate.parameterGroups,
    )
    assertEquals(20, secondRunTemplate.executionTimeout!!)
    assertEquals("3", secondRunTemplate.runSizing?.requests?.cpu)
    assertEquals("3G", secondRunTemplate.runSizing?.requests?.memory)
    assertEquals("4", secondRunTemplate.runSizing?.limits?.cpu)
    assertEquals("4G", secondRunTemplate.runSizing?.limits?.memory)
  }

  @Test
  fun `test get solution run template`() {
    val newSolutionWithRunTemplates =
        makeSolution(
            organizationSaved.id,
            runTemplates =
                mutableListOf(
                    RunTemplateCreateRequest(
                        id = "runTemplateId",
                        description = "this_is_a_description",
                        labels = mutableMapOf("fr" to "this_is_a_label"),
                        name = "runTemplateName1",
                        tags = mutableListOf("runTemplateTag1", "runTemplateTag2"),
                        computeSize = "this_is_a_computeSize",
                        runSizing =
                            RunTemplateResourceSizing(
                                requests = ResourceSizeInfo(cpu = "1", memory = "1G"),
                                limits = ResourceSizeInfo(cpu = "2", memory = "2G"),
                            ),
                        parameterGroups = mutableListOf("parameterGroup1", "parameterGroup2"),
                        executionTimeout = 10,
                    ),
                    RunTemplateCreateRequest(
                        id = "runTemplateId2",
                        description = "this_is_a_description2",
                        labels = mutableMapOf("fr" to "this_is_a_label2"),
                        name = "runTemplateName2",
                        tags = mutableListOf("runTemplateTag3", "runTemplateTag4"),
                        computeSize = "this_is_a_computeSize2",
                        runSizing =
                            RunTemplateResourceSizing(
                                requests = ResourceSizeInfo(cpu = "3", memory = "3G"),
                                limits = ResourceSizeInfo(cpu = "4", memory = "4G"),
                            ),
                        parameterGroups = mutableListOf("parameterGroup3", "parameterGroup4"),
                        executionTimeout = 20,
                    ),
                ),
        )

    val newSolution =
        solutionApiService.createSolution(organizationSaved.id, newSolutionWithRunTemplates)

    val runTemplate =
        solutionApiService.getRunTemplate(
            organizationSaved.id,
            newSolution.id,
            newSolution.runTemplates[0].id,
        )

    assertNotNull(runTemplate)
    assertEquals("runTemplateId", runTemplate.id)
    assertEquals("this_is_a_description", runTemplate.description)
    assertEquals(mutableMapOf("fr" to "this_is_a_label"), runTemplate.labels)
    assertEquals("runTemplateName1", runTemplate.name)
    assertEquals(mutableListOf("runTemplateTag1", "runTemplateTag2"), runTemplate.tags)
    assertEquals("this_is_a_computeSize", runTemplate.computeSize)
    assertEquals(mutableListOf("parameterGroup1", "parameterGroup2"), runTemplate.parameterGroups)
    assertEquals(10, runTemplate.executionTimeout!!)
    assertEquals("1", runTemplate.runSizing?.requests?.cpu)
    assertEquals("1G", runTemplate.runSizing?.requests?.memory)
    assertEquals("2", runTemplate.runSizing?.limits?.cpu)
    assertEquals("2G", runTemplate.runSizing?.limits?.memory)
  }

  @Test
  fun `test get solution run template with non-existing run template id`() {
    val exception =
        assertThrows<CsmResourceNotFoundException> {
          solutionApiService.getRunTemplate(
              organizationSaved.id,
              solutionSaved.id,
              "non-existing-solution-run-template-id",
          )
        }
    assertEquals(
        "Solution run template with id non-existing-solution-run-template-id does not exist",
        exception.message,
    )
  }

  @Test
  fun `test update solution run template with wrong runSizing`() {
    val newSolutionWithRunTemplates =
        makeSolution(
            organizationSaved.id,
            runTemplates =
                mutableListOf(
                    RunTemplateCreateRequest(
                        id = "runTemplateId",
                        description = "this_is_a_description",
                        labels = mutableMapOf("fr" to "this_is_a_label"),
                        name = "runTemplateName1",
                        tags = mutableListOf("runTemplateTag1", "runTemplateTag2"),
                        computeSize = "this_is_a_computeSize",
                        runSizing =
                            RunTemplateResourceSizing(
                                requests = ResourceSizeInfo(cpu = "1", memory = "1G"),
                                limits = ResourceSizeInfo(cpu = "2", memory = "2G"),
                            ),
                        parameterGroups = mutableListOf("parameterGroup1", "parameterGroup2"),
                        executionTimeout = 10,
                    )
                ),
        )

    val newSolution =
        solutionApiService.createSolution(organizationSaved.id, newSolutionWithRunTemplates)

    val runTemplateId = newSolution.runTemplates[0].id
    val exception =
        assertThrows<IllegalArgumentException> {
          solutionApiService.updateSolutionRunTemplate(
              organizationSaved.id,
              newSolution.id,
              runTemplateId,
              RunTemplateUpdateRequest(
                  description = "this_is_a_description3",
                  labels = mutableMapOf("fr" to "this_is_a_label3"),
                  name = "runTemplateName3",
                  runSizing =
                      RunTemplateResourceSizing(
                          requests = ResourceSizeInfo(cpu = "1Go", memory = "1G"),
                          limits = ResourceSizeInfo(cpu = "1", memory = "1G"),
                      ),
              ),
          )
        }

    assertEquals(
        "Invalid quantity format. " +
            "Please check runSizing values " +
            "{requests.cpu=1Go, requests.memory=1G, limits.cpu=1, limits.memory=1G}",
        exception.message,
    )
  }

  @Test
  fun `test update solution run template`() {
    val newSolutionWithRunTemplates =
        makeSolution(
            organizationSaved.id,
            runTemplates =
                mutableListOf(
                    RunTemplateCreateRequest(
                        id = "runTemplateId",
                        description = "this_is_a_description",
                        labels = mutableMapOf("fr" to "this_is_a_label"),
                        name = "runTemplateName1",
                        tags = mutableListOf("runTemplateTag1", "runTemplateTag2"),
                        computeSize = "this_is_a_computeSize",
                        runSizing =
                            RunTemplateResourceSizing(
                                requests = ResourceSizeInfo(cpu = "1", memory = "1G"),
                                limits = ResourceSizeInfo(cpu = "2", memory = "2G"),
                            ),
                        parameterGroups = mutableListOf("parameterGroup1", "parameterGroup2"),
                        executionTimeout = 10,
                    ),
                    RunTemplateCreateRequest(
                        id = "runTemplateId2",
                        description = "this_is_a_description2",
                        labels = mutableMapOf("fr" to "this_is_a_label2"),
                        name = "runTemplateName2",
                        tags = mutableListOf("runTemplateTag3", "runTemplateTag4"),
                        computeSize = "this_is_a_computeSize2",
                        runSizing =
                            RunTemplateResourceSizing(
                                requests = ResourceSizeInfo(cpu = "3", memory = "3G"),
                                limits = ResourceSizeInfo(cpu = "4", memory = "4G"),
                            ),
                        parameterGroups = mutableListOf("parameterGroup3", "parameterGroup4"),
                        executionTimeout = 20,
                    ),
                ),
        )

    val newSolution =
        solutionApiService.createSolution(organizationSaved.id, newSolutionWithRunTemplates)

    val runTemplateId = newSolution.runTemplates[0].id
    val runTemplate =
        solutionApiService.updateSolutionRunTemplate(
            organizationSaved.id,
            newSolution.id,
            runTemplateId,
            RunTemplateUpdateRequest(
                description = "this_is_a_description3",
                labels = mutableMapOf("fr" to "this_is_a_label3"),
                name = "runTemplateName3",
                tags = mutableListOf("runTemplateTag5"),
                computeSize = "this_is_a_computeSize3",
                runSizing =
                    RunTemplateResourceSizing(
                        requests = ResourceSizeInfo(cpu = "1", memory = "1G"),
                        limits = ResourceSizeInfo(cpu = "1", memory = "1G"),
                    ),
                parameterGroups = mutableListOf("parameterGroup5"),
                executionTimeout = 5,
            ),
        )
    assertNotNull(runTemplate)
    assertEquals("runTemplateId", runTemplate.id)
    assertEquals("this_is_a_description3", runTemplate.description)
    assertEquals(mutableMapOf("fr" to "this_is_a_label3"), runTemplate.labels)
    assertEquals("runTemplateName3", runTemplate.name)
    assertEquals(mutableListOf("runTemplateTag5"), runTemplate.tags)
    assertEquals("this_is_a_computeSize3", runTemplate.computeSize)
    assertEquals(mutableListOf("parameterGroup5"), runTemplate.parameterGroups)
    assertEquals(5, runTemplate.executionTimeout!!)
    assertEquals("1", runTemplate.runSizing?.requests?.cpu)
    assertEquals("1G", runTemplate.runSizing?.requests?.memory)
    assertEquals("1", runTemplate.runSizing?.limits?.cpu)
    assertEquals("1G", runTemplate.runSizing?.limits?.memory)
  }

  @Test
  fun `test update run template with non-existing run template id`() {
    val exception =
        assertThrows<CsmResourceNotFoundException> {
          solutionApiService.updateSolutionRunTemplate(
              organizationSaved.id,
              solutionSaved.id,
              "non-existing-solution-run-template-id",
              RunTemplateUpdateRequest(),
          )
        }
    assertEquals(
        "Solution run template with id non-existing-solution-run-template-id does not exist",
        exception.message,
    )
  }

  @Test
  fun `test delete solution run template`() {

    val newSolutionWithRunTemplates =
        makeSolution(
            organizationSaved.id,
            runTemplates =
                mutableListOf(
                    RunTemplateCreateRequest(
                        id = "runTemplateId",
                        description = "this_is_a_description",
                        labels = mutableMapOf("fr" to "this_is_a_label"),
                        name = "runTemplateName1",
                        tags = mutableListOf("runTemplateTag1", "runTemplateTag2"),
                        computeSize = "this_is_a_computeSize",
                        runSizing =
                            RunTemplateResourceSizing(
                                requests = ResourceSizeInfo(cpu = "1", memory = "1G"),
                                limits = ResourceSizeInfo(cpu = "2", memory = "2G"),
                            ),
                        parameterGroups = mutableListOf("parameterGroup1", "parameterGroup2"),
                        executionTimeout = 10,
                    ),
                    RunTemplateCreateRequest(
                        id = "runTemplateId2",
                        description = "this_is_a_description2",
                        labels = mutableMapOf("fr" to "this_is_a_label2"),
                        name = "runTemplateName2",
                        tags = mutableListOf("runTemplateTag3", "runTemplateTag4"),
                        computeSize = "this_is_a_computeSize2",
                        runSizing =
                            RunTemplateResourceSizing(
                                requests = ResourceSizeInfo(cpu = "3", memory = "3G"),
                                limits = ResourceSizeInfo(cpu = "4", memory = "4G"),
                            ),
                        parameterGroups = mutableListOf("parameterGroup3", "parameterGroup4"),
                        executionTimeout = 20,
                    ),
                ),
        )

    val newSolution =
        solutionApiService.createSolution(organizationSaved.id, newSolutionWithRunTemplates)

    var listRunTemplates = solutionApiService.listRunTemplates(organizationSaved.id, newSolution.id)

    val runTemplateIdToDelete = listRunTemplates[0].id
    val runTemplateIdToKeep = listRunTemplates[1].id
    solutionApiService.deleteSolutionRunTemplate(
        organizationSaved.id,
        newSolution.id,
        runTemplateIdToDelete,
    )

    listRunTemplates = solutionApiService.listRunTemplates(organizationSaved.id, newSolution.id)

    assertNotNull(listRunTemplates)
    assertEquals(1, listRunTemplates.size)
    assertEquals(runTemplateIdToKeep, listRunTemplates[0].id)
  }

  @Test
  fun `test delete solution run template with non-existing run template id`() {
    val exception =
        assertThrows<CsmResourceNotFoundException> {
          solutionApiService.deleteSolutionRunTemplate(
              organizationSaved.id,
              solutionSaved.id,
              "non-existing-solution-run-template-id",
          )
        }
    assertEquals(
        "Solution run template with id non-existing-solution-run-template-id does not exist",
        exception.message,
    )
  }

  @Test
  fun `test create solution run template with non-existing solution id`() {
    val exception =
        assertThrows<CsmResourceNotFoundException> {
          solutionApiService.createSolutionRunTemplate(
              organizationSaved.id,
              "non-existing-solution-id",
              RunTemplateCreateRequest(id = "my_run_template_id"),
          )
        }
    assertEquals(
        "Solution 'non-existing-solution-id' not found in Organization '${organizationSaved.id}'",
        exception.message,
    )
  }

  @Test
  fun `test create solution run template`() {
    val newSolutionWithoutRunTemplates = makeSolution(organizationSaved.id)

    val newSolutionWithEmptyRunTemplates =
        solutionApiService.createSolution(organizationSaved.id, newSolutionWithoutRunTemplates)

    assertTrue(newSolutionWithEmptyRunTemplates.parameterGroups.isEmpty())

    val runTemplateCreateRequest =
        RunTemplateCreateRequest(
            id = "runTemplateId",
            description = "this_is_a_description",
            labels = mutableMapOf("fr" to "this_is_a_label"),
            name = "runTemplateName1",
            tags = mutableListOf("runTemplateTag1", "runTemplateTag2"),
            computeSize = "this_is_a_computeSize",
            runSizing =
                RunTemplateResourceSizing(
                    requests = ResourceSizeInfo(cpu = "1", memory = "1G"),
                    limits = ResourceSizeInfo(cpu = "2", memory = "2G"),
                ),
            parameterGroups = mutableListOf("parameterGroup1", "parameterGroup2"),
            executionTimeout = 10,
        )

    solutionApiService.createSolutionRunTemplate(
        organizationSaved.id,
        newSolutionWithEmptyRunTemplates.id,
        runTemplateCreateRequest,
    )

    val newSolutionWithNewRunTemplate =
        solutionApiService.getSolution(organizationSaved.id, newSolutionWithEmptyRunTemplates.id)

    assertFalse(newSolutionWithNewRunTemplate.runTemplates.isEmpty())
    assertEquals(1, newSolutionWithNewRunTemplate.runTemplates.size)
    val newRunTemplate = newSolutionWithNewRunTemplate.runTemplates[0]
    assertEquals("runTemplateId", newRunTemplate.id)
    assertEquals("this_is_a_description", newRunTemplate.description)
    assertEquals(mutableMapOf("fr" to "this_is_a_label"), newRunTemplate.labels)
    assertEquals("runTemplateName1", newRunTemplate.name)
    assertEquals(mutableListOf("runTemplateTag1", "runTemplateTag2"), newRunTemplate.tags)
    assertEquals("this_is_a_computeSize", newRunTemplate.computeSize)
    assertEquals(
        mutableListOf("parameterGroup1", "parameterGroup2"),
        newRunTemplate.parameterGroups,
    )
    assertEquals(10, newRunTemplate.executionTimeout!!)
    assertEquals("1", newRunTemplate.runSizing?.requests?.cpu)
    assertEquals("1G", newRunTemplate.runSizing?.requests?.memory)
    assertEquals("2", newRunTemplate.runSizing?.limits?.cpu)
    assertEquals("2G", newRunTemplate.runSizing?.limits?.memory)
  }

  @Test
  fun `test create solution run template with wrong runSizing`() {
    val newSolutionWithoutRunTemplates = makeSolution(organizationSaved.id)

    val newSolutionWithEmptyRunTemplates =
        solutionApiService.createSolution(organizationSaved.id, newSolutionWithoutRunTemplates)

    assertTrue(newSolutionWithEmptyRunTemplates.parameterGroups.isEmpty())

    val runTemplateCreateRequest =
        RunTemplateCreateRequest(
            id = "runTemplateId",
            description = "this_is_a_description",
            labels = mutableMapOf("fr" to "this_is_a_label"),
            name = "runTemplateName1",
            tags = mutableListOf("runTemplateTag1", "runTemplateTag2"),
            computeSize = "this_is_a_computeSize",
            runSizing =
                RunTemplateResourceSizing(
                    requests = ResourceSizeInfo(cpu = "1", memory = "1G"),
                    limits = ResourceSizeInfo(cpu = "2", memory = "2G"),
                ),
            parameterGroups = mutableListOf("parameterGroup1", "parameterGroup2"),
            executionTimeout = 10,
        )

    solutionApiService.createSolutionRunTemplate(
        organizationSaved.id,
        newSolutionWithEmptyRunTemplates.id,
        runTemplateCreateRequest,
    )

    val newSolutionWithNewRunTemplate =
        solutionApiService.getSolution(organizationSaved.id, newSolutionWithEmptyRunTemplates.id)

    assertFalse(newSolutionWithNewRunTemplate.runTemplates.isEmpty())
    assertEquals(1, newSolutionWithNewRunTemplate.runTemplates.size)
    val newRunTemplate = newSolutionWithNewRunTemplate.runTemplates[0]
    assertEquals("runTemplateId", newRunTemplate.id)
    assertEquals("this_is_a_description", newRunTemplate.description)
    assertEquals(mutableMapOf("fr" to "this_is_a_label"), newRunTemplate.labels)
    assertEquals("runTemplateName1", newRunTemplate.name)
    assertEquals(mutableListOf("runTemplateTag1", "runTemplateTag2"), newRunTemplate.tags)
    assertEquals("this_is_a_computeSize", newRunTemplate.computeSize)
    assertEquals(
        mutableListOf("parameterGroup1", "parameterGroup2"),
        newRunTemplate.parameterGroups,
    )
    assertEquals(10, newRunTemplate.executionTimeout!!)
    assertEquals("1", newRunTemplate.runSizing?.requests?.cpu)
    assertEquals("1G", newRunTemplate.runSizing?.requests?.memory)
    assertEquals("2", newRunTemplate.runSizing?.limits?.cpu)
    assertEquals("2G", newRunTemplate.runSizing?.limits?.memory)
  }

  @Test
  fun `test create solution run template with already existing run template id`() {
    val runTemplateCreateRequest =
        RunTemplateCreateRequest(
            id = "runTemplateId",
            description = "this_is_a_description",
            labels = mutableMapOf("fr" to "this_is_a_label"),
            name = "runTemplateName1",
            runSizing =
                RunTemplateResourceSizing(
                    requests = ResourceSizeInfo(cpu = "1Go", memory = "1G"),
                    limits = ResourceSizeInfo(cpu = "2", memory = "2G"),
                ),
        )

    val exception =
        assertThrows<IllegalArgumentException> {
          solutionApiService.createSolution(
              organizationSaved.id,
              makeSolution(runTemplates = mutableListOf(runTemplateCreateRequest)),
          )
        }

    assertEquals(
        "Invalid quantity format. " +
            "Please check runSizing values " +
            "{requests.cpu=1Go, requests.memory=1G, limits.cpu=2, limits.memory=2G}",
        exception.message,
    )
  }

  @Test
  fun `test create solution with several run template with the same id `() {

    val newSolutionWithRunTemplates =
        makeSolution(
            organizationSaved.id,
            runTemplates =
                mutableListOf(
                    RunTemplateCreateRequest(
                        id = "rUnTeMpLaTeId",
                        description = "this_is_a_description",
                        labels = mutableMapOf("fr" to "this_is_a_label"),
                        name = "runTemplateName1",
                        tags = mutableListOf("runTemplateTag1", "runTemplateTag2"),
                        computeSize = "this_is_a_computeSize",
                        runSizing =
                            RunTemplateResourceSizing(
                                requests = ResourceSizeInfo(cpu = "1Go", memory = "1Go"),
                                limits = ResourceSizeInfo(cpu = "2Go", memory = "2Go"),
                            ),
                        parameterGroups = mutableListOf("parameterGroup1", "parameterGroup2"),
                        executionTimeout = 10,
                    ),
                    RunTemplateCreateRequest(
                        id = "RuNtEmPlAtEId",
                        description = "this_is_a_description2",
                        labels = mutableMapOf("fr" to "this_is_a_label2"),
                        name = "runTemplateName2",
                        tags = mutableListOf("runTemplateTag3", "runTemplateTag4"),
                        computeSize = "this_is_a_computeSize2",
                        runSizing =
                            RunTemplateResourceSizing(
                                requests = ResourceSizeInfo(cpu = "3Go", memory = "3Go"),
                                limits = ResourceSizeInfo(cpu = "4Go", memory = "4Go"),
                            ),
                        parameterGroups = mutableListOf("parameterGroup3", "parameterGroup4"),
                        executionTimeout = 20,
                    ),
                ),
        )

    val exception =
        assertThrows<IllegalArgumentException> {
          solutionApiService.createSolution(organizationSaved.id, newSolutionWithRunTemplates)
        }

    assertEquals("One or several solution items have same id : runTemplates", exception.message)
  }

  @Test
  fun `assert timestamps are functional for base CRUD`() {
    solutionSaved = solutionApiService.createSolution(organizationSaved.id, makeSolution())
    assertTrue(solutionSaved.createInfo.timestamp > startTime)
    assertEquals(solutionSaved.createInfo, solutionSaved.updateInfo)

    val updateTime = Instant.now().toEpochMilli()
    val solutionUpdated =
        solutionApiService.updateSolution(
            organizationSaved.id,
            solutionSaved.id,
            SolutionUpdateRequest("solutionUpdated"),
        )

    assertTrue { updateTime < solutionUpdated.updateInfo.timestamp }
    assertEquals(solutionSaved.createInfo, solutionUpdated.createInfo)
    assertTrue { solutionSaved.createInfo.timestamp < solutionUpdated.updateInfo.timestamp }
    assertTrue { solutionSaved.updateInfo.timestamp < solutionUpdated.updateInfo.timestamp }

    val solutionFetched = solutionApiService.getSolution(organizationSaved.id, solutionSaved.id)

    assertEquals(solutionUpdated.createInfo, solutionFetched.createInfo)
    assertEquals(solutionUpdated.updateInfo, solutionFetched.updateInfo)
  }

  @Test
  fun `assert timestamps are functional for parameters CRUD`() {
    solutionSaved = solutionApiService.createSolution(organizationSaved.id, makeSolution())
    solutionApiService.createSolutionParameter(
        organizationSaved.id,
        solutionSaved.id,
        RunTemplateParameterCreateRequest(id = "id", varType = "varType"),
    )
    val parameterAdded = solutionApiService.getSolution(organizationSaved.id, solutionSaved.id)

    assertEquals(solutionSaved.createInfo, parameterAdded.createInfo)
    assertTrue { solutionSaved.updateInfo.timestamp < parameterAdded.updateInfo.timestamp }

    solutionApiService.getSolutionParameter(organizationSaved.id, solutionSaved.id, "id")
    val parameterFetched = solutionApiService.getSolution(organizationSaved.id, solutionSaved.id)

    assertEquals(parameterAdded.createInfo, parameterFetched.createInfo)
    assertEquals(parameterAdded.updateInfo, parameterFetched.updateInfo)

    solutionApiService.updateSolutionParameter(
        organizationSaved.id,
        solutionSaved.id,
        "id",
        RunTemplateParameterUpdateRequest("description"),
    )
    val parameterUpdated = solutionApiService.getSolution(organizationSaved.id, solutionSaved.id)

    assertEquals(parameterFetched.createInfo, parameterUpdated.createInfo)
    assertTrue { parameterFetched.updateInfo.timestamp < parameterUpdated.updateInfo.timestamp }

    solutionApiService.deleteSolutionParameter(organizationSaved.id, solutionSaved.id, "id")
    val parameterDeleted = solutionApiService.getSolution(organizationSaved.id, solutionSaved.id)

    assertEquals(parameterUpdated.createInfo, parameterDeleted.createInfo)
    assertTrue { parameterUpdated.updateInfo.timestamp < parameterDeleted.updateInfo.timestamp }
  }

  @Test
  fun `assert timestamps are functional for parameterGroups CRUD`() {
    solutionSaved = solutionApiService.createSolution(organizationSaved.id, makeSolution())
    solutionApiService.createSolutionParameterGroup(
        organizationSaved.id,
        solutionSaved.id,
        RunTemplateParameterGroupCreateRequest("id"),
    )
    val parameterGroupAdded = solutionApiService.getSolution(organizationSaved.id, solutionSaved.id)

    assertEquals(solutionSaved.createInfo, parameterGroupAdded.createInfo)
    assertTrue { solutionSaved.updateInfo.timestamp < parameterGroupAdded.updateInfo.timestamp }

    solutionApiService.getSolutionParameterGroup(organizationSaved.id, solutionSaved.id, "id")
    val parameterGroupFetched =
        solutionApiService.getSolution(organizationSaved.id, solutionSaved.id)

    assertEquals(parameterGroupAdded.createInfo, parameterGroupFetched.createInfo)
    assertEquals(parameterGroupAdded.updateInfo, parameterGroupFetched.updateInfo)

    solutionApiService.updateSolutionParameterGroup(
        organizationSaved.id,
        solutionSaved.id,
        "id",
        RunTemplateParameterGroupUpdateRequest("description"),
    )
    val parameterGroupUpdated =
        solutionApiService.getSolution(organizationSaved.id, solutionSaved.id)

    assertEquals(parameterGroupFetched.createInfo, parameterGroupUpdated.createInfo)
    assertTrue {
      parameterGroupFetched.updateInfo.timestamp < parameterGroupUpdated.updateInfo.timestamp
    }

    solutionApiService.deleteSolutionParameterGroup(organizationSaved.id, solutionSaved.id, "id")
    val parameterGroupDeleted =
        solutionApiService.getSolution(organizationSaved.id, solutionSaved.id)

    assertEquals(parameterGroupUpdated.createInfo, parameterGroupDeleted.createInfo)
    assertTrue {
      parameterGroupUpdated.updateInfo.timestamp < parameterGroupDeleted.updateInfo.timestamp
    }
  }

  @Test
  fun `assert timestamps are functional for runTemplates CRUD`() {
    solutionSaved = solutionApiService.createSolution(organizationSaved.id, makeSolution())
    solutionApiService.createSolutionRunTemplate(
        organizationSaved.id,
        solutionSaved.id,
        RunTemplateCreateRequest("id"),
    )
    val runTemplatedAdded = solutionApiService.getSolution(organizationSaved.id, solutionSaved.id)

    assertEquals(solutionSaved.createInfo, runTemplatedAdded.createInfo)
    assertTrue { solutionSaved.updateInfo.timestamp < runTemplatedAdded.updateInfo.timestamp }

    solutionApiService.getRunTemplate(organizationSaved.id, solutionSaved.id, "id")
    val runTemplateFetched = solutionApiService.getSolution(organizationSaved.id, solutionSaved.id)

    assertEquals(runTemplatedAdded.createInfo, runTemplateFetched.createInfo)
    assertEquals(runTemplatedAdded.updateInfo, runTemplateFetched.updateInfo)

    solutionApiService.updateSolutionRunTemplate(
        organizationSaved.id,
        solutionSaved.id,
        "id",
        RunTemplateUpdateRequest("description"),
    )
    val runTemplateUpdated = solutionApiService.getSolution(organizationSaved.id, solutionSaved.id)

    assertEquals(runTemplateFetched.createInfo, runTemplateUpdated.createInfo)
    assertTrue { runTemplateFetched.updateInfo.timestamp < runTemplateUpdated.updateInfo.timestamp }

    solutionApiService.deleteSolutionRunTemplate(organizationSaved.id, solutionSaved.id, "id")
    val runTemplateDeleted = solutionApiService.getSolution(organizationSaved.id, solutionSaved.id)

    assertEquals(runTemplateUpdated.createInfo, runTemplateDeleted.createInfo)
    assertTrue { runTemplateUpdated.updateInfo.timestamp < runTemplateDeleted.updateInfo.timestamp }
  }

  @Test
  fun `assert timestamps are functional for RBAC CRUD`() {
    solutionSaved = solutionApiService.createSolution(organizationSaved.id, makeSolution())
    solutionApiService.createSolutionAccessControl(
        organizationSaved.id,
        solutionSaved.id,
        SolutionAccessControl("newUser", ROLE_USER),
    )
    val rbacAdded = solutionApiService.getSolution(organizationSaved.id, solutionSaved.id)

    assertEquals(solutionSaved.createInfo, rbacAdded.createInfo)
    assertTrue { solutionSaved.updateInfo.timestamp < rbacAdded.updateInfo.timestamp }

    solutionApiService.getSolutionAccessControl(organizationSaved.id, solutionSaved.id, "newUser")
    val rbacFetched = solutionApiService.getSolution(organizationSaved.id, solutionSaved.id)

    assertEquals(rbacAdded.createInfo, rbacFetched.createInfo)
    assertEquals(rbacAdded.updateInfo, rbacFetched.updateInfo)

    solutionApiService.updateSolutionAccessControl(
        organizationSaved.id,
        solutionSaved.id,
        "newUser",
        SolutionRole(ROLE_VIEWER),
    )
    val rbacUpdated = solutionApiService.getSolution(organizationSaved.id, solutionSaved.id)

    assertEquals(rbacFetched.createInfo, rbacUpdated.createInfo)
    assertTrue { rbacFetched.updateInfo.timestamp < rbacUpdated.updateInfo.timestamp }

    solutionApiService.deleteSolutionAccessControl(
        organizationSaved.id,
        solutionSaved.id,
        "newUser",
    )
    val rbacDeleted = solutionApiService.getSolution(organizationSaved.id, solutionSaved.id)

    assertEquals(rbacUpdated.createInfo, rbacDeleted.createInfo)
    assertTrue { rbacUpdated.updateInfo.timestamp < rbacDeleted.updateInfo.timestamp }
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
                        OrganizationAccessControl(id = CONNECTED_ADMIN_USER, role = ROLE_ADMIN),
                    ),
            ),
    )
  }

  fun makeSolution(
      organizationId: String = organizationSaved.id,
      runTemplates: MutableList<RunTemplateCreateRequest> = mutableListOf(),
      version: String = "1.0.0",
      repository: String = "repository",
      parameter: MutableList<RunTemplateParameterCreateRequest> = mutableListOf(),
      parameterGroup: MutableList<RunTemplateParameterGroupCreateRequest> = mutableListOf(),
      userName: String = CONNECTED_READER_USER,
      role: String = ROLE_VIEWER,
  ) =
      SolutionCreateRequest(
          key = UUID.randomUUID().toString(),
          name = "My solution",
          runTemplates = runTemplates,
          version = version,
          repository = repository,
          parameters = parameter,
          parameterGroups = parameterGroup,
          security =
              SolutionSecurity(
                  default = ROLE_NONE,
                  accessControlList =
                      mutableListOf(
                          SolutionAccessControl(id = userName, role = role),
                          SolutionAccessControl(id = CONNECTED_ADMIN_USER, role = ROLE_ADMIN),
                      ),
              ),
      )
}
