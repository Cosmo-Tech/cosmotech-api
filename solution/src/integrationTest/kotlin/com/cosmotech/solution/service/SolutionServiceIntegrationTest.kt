// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.solution.service

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.containerregistry.ContainerRegistryService
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
import com.cosmotech.solution.domain.*
import com.redis.om.spring.RediSearchIndexer
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.mockkStatic
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
import org.junit.runner.RunWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.util.ReflectionTestUtils

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

  private var containerRegistryService: ContainerRegistryService = mockk(relaxed = true)

  lateinit var organization: OrganizationCreateRequest
  lateinit var solution: SolutionCreateRequest

  lateinit var organizationSaved: Organization
  lateinit var solutionSaved: Solution

  @BeforeEach
  fun setUp() {
    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
    ReflectionTestUtils.setField(
        solutionApiService, "containerRegistryService", containerRegistryService)
    every { containerRegistryService.getImageLabel(any(), any(), any()) } returns null
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
            parameters = mutableListOf(RunTemplateParameter("parameter", "string")),
            parameterGroups = mutableListOf(),
            security =
                SolutionSecurity(
                    ROLE_ADMIN,
                    mutableListOf(SolutionAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN))))
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
    val solutionFound = solutionApiService.getSolution(organizationSaved.id, solutionCreated.id)
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
                      organizationSaved.id, makeSolution(organizationSaved.id))
              assertEquals(imageLabel, solutionCreated.sdkVersion)

              assertEquals(
                  imageLabel,
                  solutionApiService
                      .getSolution(organizationSaved.id, solutionCreated.id)
                      .sdkVersion)

              val solutions = solutionApiService.listSolutions(organizationSaved.id, null, null)
              assertFalse(solutions.isEmpty())
              solutions.forEach { assertEquals(imageLabel, it.sdkVersion) }

              val solutionUpdateRequest = SolutionUpdateRequest(name = "New name")
              val solutionUpdated =
                  solutionApiService.updateSolution(
                      organizationSaved.id, solutionCreated.id, solutionUpdateRequest)
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
      solutionApiService.updateSolution(
          organizationSaved.id, solutionCreated.id, solutionUpdateRequest)
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
              organizationSaved.id, "non-existing-solution-id")
        }
    assertEquals(
        "Solution non-existing-solution-id not found in organization ${organizationSaved.id}",
        exception.message)
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
                        regexValidation = "\\d",
                        description = "this_is_a_description",
                        labels = mutableMapOf("fr" to "this_is_a_label"),
                        options = mutableMapOf("option1" to "value1", "option2" to 10.0)),
                    RunTemplateParameterCreateRequest(
                        id = "parameterName2",
                        varType = "int",
                        defaultValue = "5",
                        minValue = "0",
                        maxValue = "1000",
                        regexValidation = "\\d",
                        description = "this_is_a_description2",
                        labels = mutableMapOf("fr" to "this_is_a_label2"),
                        options = mutableMapOf("option1" to "value1", "option2" to 100.8))))

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
    assertEquals("\\d", firstParam.regexValidation)
    assertEquals("this_is_a_description", firstParam.description)
    assertEquals(mutableMapOf("fr" to "this_is_a_label"), firstParam.labels)
    assertEquals(2, firstParam.options?.size)
    assertEquals("value1", firstParam.options?.get("option1"))
    assertEquals(10.0, firstParam.options?.get("option2"))
    val secondParam = parameterList[1]
    assertEquals("parameterName2", secondParam.id)
    assertEquals("int", secondParam.varType)
    assertEquals("5", secondParam.defaultValue)
    assertEquals("0", secondParam.minValue)
    assertEquals("1000", secondParam.maxValue)
    assertEquals("\\d", secondParam.regexValidation)
    assertEquals("this_is_a_description2", secondParam.description)
    assertEquals(mutableMapOf("fr" to "this_is_a_label2"), secondParam.labels)
    assertEquals(2, secondParam.options?.size)
    assertEquals("value1", secondParam.options?.get("option1"))
    assertEquals(100.8, secondParam.options?.get("option2"))
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
                        regexValidation = "\\d",
                        description = "this_is_a_description",
                        labels = mutableMapOf("fr" to "this_is_a_label"),
                        options = mutableMapOf("option1" to "value1", "option2" to 10.0)),
                    RunTemplateParameterCreateRequest(
                        id = "parameterName2",
                        varType = "int",
                        defaultValue = "5",
                        minValue = "0",
                        maxValue = "1000",
                        regexValidation = "\\d",
                        description = "this_is_a_description2",
                        labels = mutableMapOf("fr" to "this_is_a_label2"),
                        options = mutableMapOf("option1" to "value1", "option2" to 100.8))))

    val newSolution =
        solutionApiService.createSolution(organizationSaved.id, newSolutionWithParameters)

    val solutionParameter =
        solutionApiService.getSolutionParameter(
            organizationSaved.id, newSolution.id, newSolution.parameters[0].id)

    assertNotNull(solutionParameter)
    assertEquals("parameterName", solutionParameter.id)
    assertEquals("int", solutionParameter.varType)
    assertEquals("0", solutionParameter.defaultValue)
    assertEquals("0", solutionParameter.minValue)
    assertEquals("100", solutionParameter.maxValue)
    assertEquals("\\d", solutionParameter.regexValidation)
    assertEquals("this_is_a_description", solutionParameter.description)
    assertEquals(mutableMapOf("fr" to "this_is_a_label"), solutionParameter.labels)
    assertEquals(2, solutionParameter.options?.size)
    assertEquals("value1", solutionParameter.options?.get("option1"))
    assertEquals(10.0, solutionParameter.options?.get("option2"))
  }

  @Test
  fun `test get solution parameter with non-existing parameter`() {
    val exception =
        assertThrows<CsmResourceNotFoundException> {
          solutionApiService.getSolutionParameter(
              organizationSaved.id, solutionSaved.id, "non-existing-solution-parameter-id")
        }
    assertEquals(
        "Solution parameter with id non-existing-solution-parameter-id does not exist",
        exception.message)
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
                        regexValidation = "\\d",
                        description = "this_is_a_description",
                        labels = mutableMapOf("fr" to "this_is_a_label"),
                        options = mutableMapOf("option1" to "value1", "option2" to 10.0)),
                    RunTemplateParameterCreateRequest(
                        id = "parameterName2",
                        varType = "int",
                        defaultValue = "5",
                        minValue = "0",
                        maxValue = "1000",
                        regexValidation = "\\d",
                        description = "this_is_a_description2",
                        labels = mutableMapOf("fr" to "this_is_a_label2"),
                        options = mutableMapOf("option1" to "value1", "option2" to 100.8))))

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
                regexValidation = "\\w",
                description = "new_this_is_a_description2",
                labels = mutableMapOf("en" to "new_this_is_a_label2"),
                options = mutableMapOf("option1" to "newvalue1")))
    assertNotNull(solutionParameter)
    assertEquals(parameterId, solutionParameter.id)
    assertEquals("string", solutionParameter.varType)
    assertEquals("", solutionParameter.defaultValue)
    assertEquals("", solutionParameter.minValue)
    assertEquals("", solutionParameter.maxValue)
    assertEquals("\\w", solutionParameter.regexValidation)
    assertEquals("new_this_is_a_description2", solutionParameter.description)
    assertEquals(mutableMapOf("en" to "new_this_is_a_label2"), solutionParameter.labels)
    assertEquals(1, solutionParameter.options?.size)
    assertEquals("newvalue1", solutionParameter.options?.get("option1"))
  }

  @Test
  fun `test update solution parameter with non-existing parameter`() {
    val exception =
        assertThrows<CsmResourceNotFoundException> {
          solutionApiService.updateSolutionParameter(
              organizationSaved.id,
              solutionSaved.id,
              "non-existing-solution-parameter-id",
              RunTemplateParameterUpdateRequest())
        }
    assertEquals(
        "Solution parameter with id non-existing-solution-parameter-id does not exist",
        exception.message)
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
                        regexValidation = "\\d",
                        description = "this_is_a_description",
                        labels = mutableMapOf("fr" to "this_is_a_label"),
                        options = mutableMapOf("option1" to "value1", "option2" to 10.0)),
                    RunTemplateParameterCreateRequest(
                        id = "parameterName2",
                        varType = "int",
                        defaultValue = "5",
                        minValue = "0",
                        maxValue = "1000",
                        regexValidation = "\\d",
                        description = "this_is_a_description2",
                        labels = mutableMapOf("fr" to "this_is_a_label2"),
                        options = mutableMapOf("option1" to "value1", "option2" to 100.8))))

    val newSolution =
        solutionApiService.createSolution(organizationSaved.id, newSolutionWithParameters)

    var listSolutionParameters =
        solutionApiService.listSolutionParameters(organizationSaved.id, newSolution.id)

    val parameterIdToDelete = listSolutionParameters[0].id
    val parameterIdToKeep = listSolutionParameters[1].id
    solutionApiService.deleteSolutionParameter(
        organizationSaved.id, newSolution.id, parameterIdToDelete)

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
              organizationSaved.id, solutionSaved.id, "non-existing-solution-parameter-id")
        }
    assertEquals(
        "Solution parameter with id non-existing-solution-parameter-id does not exist",
        exception.message)
  }

  @Test
  fun `test create solution parameter with non-existing parameter`() {
    val exception =
        assertThrows<CsmResourceNotFoundException> {
          solutionApiService.createSolutionParameter(
              organizationSaved.id,
              "non-existing-solution-id",
              RunTemplateParameterCreateRequest(
                  id = "my_parameter_name", varType = "my_vartype_parameter"))
        }
    assertEquals(
        "Solution non-existing-solution-id not found in organization ${organizationSaved.id}",
        exception.message)
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
            regexValidation = "\\d",
            description = "this_is_a_description2",
            labels = mutableMapOf("fr" to "this_is_a_label2"),
            options = mutableMapOf("option1" to "value1", "option2" to 100.8))

    solutionApiService.createSolutionParameter(
        organizationSaved.id, newSolutionWithEmptyParameters.id, parameterCreateRequest)

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
    assertEquals("\\d", newParam.regexValidation)
    assertEquals("this_is_a_description2", newParam.description)
    assertEquals(mutableMapOf("fr" to "this_is_a_label2"), newParam.labels)
    assertEquals("value1", newParam.options?.get("option1"))
    assertEquals(100.8, newParam.options?.get("option2"))
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
            regexValidation = "\\d",
            description = "this_is_a_description2",
            labels = mutableMapOf("fr" to "this_is_a_label2"),
            options = mutableMapOf("option1" to "value1", "option2" to 100.8))
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
              solutionParameterCreateRequest)
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
                regexValidation = "\\d",
                description = "this_is_a_description2",
                labels = mutableMapOf("fr" to "this_is_a_label2"),
                options = mutableMapOf("option1" to "value1", "option2" to 100.8)),
            RunTemplateParameterCreateRequest(
                id = "ParaMeterName",
                varType = "int",
                defaultValue = "5",
                minValue = "0",
                maxValue = "1000",
                regexValidation = "\\d",
                description = "this_is_a_description2",
                labels = mutableMapOf("fr" to "this_is_a_label2"),
                options = mutableMapOf("option1" to "value1", "option2" to 100.8)))

    val newSolutionWithoutParameters = makeSolution(parameter = parametersCreateRequest)

    val exception =
        assertThrows<IllegalArgumentException> {
          solutionApiService.createSolution(organizationSaved.id, newSolutionWithoutParameters)
        }

    assertEquals("Several solution parameters have same id!", exception.message)
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
    val labels: MutableMap<String, String> = mutableMapOf("fr" to "runTemplateName")
    val runTemplate3 = RunTemplate(id = "runTemplateId1", labels = labels)
    solutionApiService.updateSolutionRunTemplates(
        solutionSaved.organizationId, solutionSaved.id, listOf(runTemplate3))
    val foundSolutionAfterReplace =
        solutionApiService.getSolution(solutionSaved.organizationId, solutionSaved.id)
    assertEquals(2, foundSolutionAfterReplace.runTemplates.size)
    assertEquals(
        "runTemplateName", foundSolutionAfterReplace.runTemplates.first().labels?.get("fr"))

    logger.info("should update the run template and assert that the name has been updated")
    labels["fr"] = "runTemplateNameNew"
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
          organizationId = organizationSaved.id,
          solutionCreateRequest = makeSolution(organizationSaved.id))
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
            parameters =
                mutableListOf(RunTemplateParameterCreateRequest("parameterName", "string")),
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
  fun `assert createSolution take all infos in considerations`() {
    val solutionKey = "key"
    val solutionName = "name"
    val solutionDescription = "description"
    val solutionVersion = "1.0.0"
    val solutionTags = mutableListOf("tag1", "tag2")
    val solutionUrl = "url"
    val solutionRunTemplates = mutableListOf(RunTemplate(id = "template"))
    val solutionParameterGroups = mutableListOf(RunTemplateParameterGroup(id = "group"))
    val csmSimulator = "simulator"
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
                    RunTemplateParameterCreateRequest(id = "parameterName", varType = "string")),
            security =
                SolutionSecurity(
                    default = ROLE_ADMIN,
                    accessControlList =
                        mutableListOf(SolutionAccessControl("user_id", ROLE_ADMIN))),
            csmSimulator = csmSimulator,
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
    assertEquals(solutionRunTemplates, solutionSaved.runTemplates)
    assertEquals(1, solutionSaved.parameters.size)
    assertEquals("parameterName", solutionSaved.parameters[0].id)
    assertEquals("string", solutionSaved.parameters[0].varType)
    assertEquals(solutionParameterGroups, solutionSaved.parameterGroups)
    assertEquals(csmSimulator, solutionSaved.csmSimulator)
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
    val solutionRunTemplates = mutableListOf(RunTemplate(id = "template"))
    val solutionParameterGroups = mutableListOf(RunTemplateParameterGroup(id = "group"))
    val csmSimulator = "simulator"
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
                    RunTemplateParameterCreateRequest(id = "parameterName", varType = "string")),
            security =
                SolutionSecurity(
                    default = ROLE_ADMIN,
                    accessControlList =
                        mutableListOf(SolutionAccessControl("user_id", ROLE_ADMIN))),
            csmSimulator = csmSimulator,
            url = solutionUrl,
            alwaysPull = true,
        )
    solutionSaved = solutionApiService.createSolution(organizationSaved.id, solutionCreateRequest)

    val updatedKey = "new key"
    val updatedName = "new name"
    val updatedDescription = "new description"
    val updatedTags = mutableListOf("newTag1", "newTag2")
    val updatedRepository = "new_repo"
    val updatedCsmSimulator = "new_simulator"
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
            csmSimulator = updatedCsmSimulator,
            parameters =
                mutableListOf(
                    RunTemplateParameterCreateRequest(
                        id = "parameterNameUpdated", varType = "int")),
            url = newUrl,
            version = newVersion)

    solutionSaved =
        solutionApiService.updateSolution(
            organizationSaved.id, solutionSaved.id, solutionUpdateRequest)

    assertEquals(updatedKey, solutionSaved.key)
    assertEquals(updatedName, solutionSaved.name)
    assertEquals(updatedDescription, solutionSaved.description)
    assertEquals(updatedTags, solutionSaved.tags)
    assertEquals(updatedRepository, solutionSaved.repository)
    assertEquals(updatedCsmSimulator, solutionSaved.csmSimulator)
    assertEquals(solutionRunTemplates, solutionSaved.runTemplates)
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
    val solutionRunTemplates = mutableListOf(RunTemplate(id = "template"))
    val solutionParameterGroups = mutableListOf(RunTemplateParameterGroup(id = "group"))
    val csmSimulator = "simulator"
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
                    RunTemplateParameterCreateRequest(id = "parameterName", varType = "string")),
            security =
                SolutionSecurity(
                    default = ROLE_ADMIN,
                    accessControlList =
                        mutableListOf(SolutionAccessControl("user_id", ROLE_ADMIN))),
            csmSimulator = csmSimulator,
            url = solutionUrl,
            alwaysPull = true,
        )
    solutionSaved = solutionApiService.createSolution(organizationSaved.id, solutionCreateRequest)

    val updatedKey = "new key"
    val updatedName = "new name"
    val updatedDescription = "new description"
    val updatedTags = mutableListOf("newTag1", "newTag2")
    val updatedRepository = "new_repo"
    val updatedCsmSimulator = "new_simulator"
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
            csmSimulator = updatedCsmSimulator,
            url = newUrl,
            version = newVersion)

    solutionSaved =
        solutionApiService.updateSolution(
            organizationSaved.id, solutionSaved.id, solutionUpdateRequest)

    assertEquals(updatedKey, solutionSaved.key)
    assertEquals(updatedName, solutionSaved.name)
    assertEquals(updatedDescription, solutionSaved.description)
    assertEquals(updatedTags, solutionSaved.tags)
    assertEquals(updatedRepository, solutionSaved.repository)
    assertEquals(updatedCsmSimulator, solutionSaved.csmSimulator)
    assertEquals(solutionRunTemplates, solutionSaved.runTemplates)
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
    val solutionRunTemplates = mutableListOf(RunTemplate(id = "template"))
    val solutionParameterGroups = mutableListOf(RunTemplateParameterGroup(id = "group"))
    val csmSimulator = "simulator"
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
                    RunTemplateParameterCreateRequest(id = "parameterName", varType = "string")),
            security =
                SolutionSecurity(
                    default = ROLE_ADMIN,
                    accessControlList =
                        mutableListOf(SolutionAccessControl("user_id", ROLE_ADMIN))),
            csmSimulator = csmSimulator,
            url = solutionUrl,
            alwaysPull = true,
        )
    solutionSaved = solutionApiService.createSolution(organizationSaved.id, solutionCreateRequest)

    val updatedKey = "new key"
    val updatedName = "new name"
    val updatedDescription = "new description"
    val updatedTags = mutableListOf("newTag1", "newTag2")
    val updatedRepository = "new_repo"
    val updatedCsmSimulator = "new_simulator"
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
                    RunTemplateParameterCreateRequest(id = "pArAmEtErNaMe", varType = "string")),
            csmSimulator = updatedCsmSimulator,
            url = newUrl,
            version = newVersion)

    val exception =
        assertThrows<IllegalArgumentException> {
          solutionApiService.updateSolution(
              organizationSaved.id, solutionSaved.id, solutionUpdateRequest)
        }

    assertEquals("Several solution parameters have same id!", exception.message)
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
      parameter: MutableList<RunTemplateParameterCreateRequest> = mutableListOf(),
      parameterGroup: MutableList<RunTemplateParameterGroup> = mutableListOf(),
      userName: String = CONNECTED_READER_USER,
      role: String = ROLE_VIEWER
  ) =
      SolutionCreateRequest(
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
