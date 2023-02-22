// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.solution.service

import com.azure.storage.blob.BlobServiceClient
import com.cosmotech.api.rbac.ROLE_NONE
import com.cosmotech.api.tests.CsmRedisTestBase
import com.cosmotech.api.utils.getCurrentAuthenticatedMail
import com.cosmotech.api.utils.getCurrentAuthenticatedRoles
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationSecurity
import com.cosmotech.solution.api.SolutionApiService
import com.cosmotech.solution.domain.RunTemplate
import com.cosmotech.solution.domain.RunTemplateParameter
import com.cosmotech.solution.domain.RunTemplateParameterGroup
import com.cosmotech.solution.domain.Solution
import com.redis.om.spring.RediSearchIndexer
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockkStatic
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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

  @MockK(relaxed = true) private lateinit var azureStorageBlobServiceClient: BlobServiceClient

  @Autowired lateinit var rediSearchIndexer: RediSearchIndexer
  @Autowired lateinit var organizationApiService: OrganizationApiService
  @Autowired lateinit var solutionApiService: SolutionApiService

  lateinit var organization: Organization
  lateinit var solution: Solution

  lateinit var organizationRegistered: Organization
  lateinit var solutionRegistered: Solution

  @BeforeEach
  fun setUp() {
    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
    every { getCurrentAuthenticatedMail(any()) } returns CONNECTED_ADMIN_USER
    every { getCurrentAuthenticatedUserName() } returns "test.user"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("user")

    ReflectionTestUtils.setField(
        solutionApiService, "azureStorageBlobServiceClient", azureStorageBlobServiceClient)

    rediSearchIndexer.createIndexFor(Solution::class.java)

    organization = mockOrganization("Organization test")
    organizationRegistered = organizationApiService.registerOrganization(organization)

    solution = mockSolution(organizationRegistered.id!!)
    solutionRegistered = solutionApiService.createSolution(organizationRegistered.id!!, solution)
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
    val solutionsFound = solutionApiService.findAllSolutions(organizationRegistered.id!!)
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
    val solutionsFoundAfterDelete = solutionApiService.findAllSolutions(organizationRegistered.id!!)
    assertTrue(solutionsFoundAfterDelete.size == 1)
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

    logger.info("should replace the first run template and assert that the list contains 2 elements")
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
        solutionRegistered.organizationId!!,
        solutionRegistered.id!!,
        runTemplate4.id!!,
        runTemplate4)
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

  fun mockOrganization(id: String): Organization {
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
                        OrganizationAccessControl(id = CONNECTED_ADMIN_USER, role = "admin"))))
  }

  fun mockSolution(organizationId: String): Solution {
    return Solution(
        id = "solutionId",
        key = UUID.randomUUID().toString(),
        name = "My solution",
        organizationId = organizationId,
        ownerId = "ownerId")
  }
}
