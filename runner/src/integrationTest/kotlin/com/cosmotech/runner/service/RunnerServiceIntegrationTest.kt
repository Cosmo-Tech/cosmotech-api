// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.runner.service

import com.cosmotech.common.config.CsmPlatformProperties
import com.cosmotech.common.containerregistry.ContainerRegistryService
import com.cosmotech.common.events.CsmEventPublisher
import com.cosmotech.common.events.GetRunnerAttachedToDataset
import com.cosmotech.common.events.HasRunningRuns
import com.cosmotech.common.events.RunStart
import com.cosmotech.common.events.UpdateRunnerStatus
import com.cosmotech.common.exceptions.CsmAccessForbiddenException
import com.cosmotech.common.exceptions.CsmResourceNotFoundException
import com.cosmotech.common.rbac.ROLE_ADMIN
import com.cosmotech.common.rbac.ROLE_EDITOR
import com.cosmotech.common.rbac.ROLE_NONE
import com.cosmotech.common.rbac.ROLE_USER
import com.cosmotech.common.rbac.ROLE_VALIDATOR
import com.cosmotech.common.rbac.ROLE_VIEWER
import com.cosmotech.common.security.ROLE_ORGANIZATION_USER
import com.cosmotech.common.security.ROLE_PLATFORM_ADMIN
import com.cosmotech.common.tests.CsmTestBase
import com.cosmotech.common.utils.getCurrentAccountGroups
import com.cosmotech.common.utils.getCurrentAccountIdentifier
import com.cosmotech.common.utils.getCurrentAuthenticatedRoles
import com.cosmotech.common.utils.getCurrentAuthenticatedUserName
import com.cosmotech.dataset.DatasetApiServiceInterface
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.dataset.domain.DatasetCreateRequest
import com.cosmotech.dataset.domain.DatasetPart
import com.cosmotech.dataset.domain.DatasetPartCreateRequest
import com.cosmotech.dataset.domain.DatasetPartTypeEnum
import com.cosmotech.dataset.domain.DatasetPartUpdateRequest
import com.cosmotech.organization.OrganizationApiServiceInterface
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationCreateRequest
import com.cosmotech.organization.domain.OrganizationSecurity
import com.cosmotech.runner.RunnerApiServiceInterface
import com.cosmotech.runner.domain.*
import com.cosmotech.runner.domain.RunnerRole
import com.cosmotech.solution.SolutionApiServiceInterface
import com.cosmotech.solution.domain.*
import com.cosmotech.workspace.WorkspaceApiServiceInterface
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceAccessControl
import com.cosmotech.workspace.domain.WorkspaceCreateRequest
import com.cosmotech.workspace.domain.WorkspaceSecurity
import com.cosmotech.workspace.domain.WorkspaceSolution
import com.cosmotech.workspace.domain.WorkspaceUpdateRequest
import com.ninjasquad.springmockk.SpykBean
import com.redis.om.spring.indexing.RediSearchIndexer
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.mockkStatic
import java.io.FileInputStream
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.apache.commons.io.IOUtils
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.runner.RunWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.InputStreamResource
import org.springframework.core.io.ResourceLoader
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.util.ReflectionTestUtils

@ActiveProfiles(profiles = ["runner-test"])
@ExtendWith(MockKExtension::class)
@ExtendWith(SpringExtension::class)
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RunnerServiceIntegrationTest : CsmTestBase() {

  val CONNECTED_ADMIN_USER = "test.admin@cosmotech.com"
  val CONNECTED_READER_USER = "test.reader@cosmotech.com"
  val TEST_USER_MAIL = "fake@mail.fr"
  val CUSTOMERS_FILE_NAME = "customers.csv"
  val CUSTOMERS_5_LINES_FILE_NAME = "customers_5_lines.csv"

  private val logger = LoggerFactory.getLogger(RunnerServiceIntegrationTest::class.java)
  private val defaultName = "my.account-tester@cosmotech.com"

  @SpykBean private lateinit var eventPublisher: CsmEventPublisher

  @Autowired lateinit var rediSearchIndexer: RediSearchIndexer
  @Autowired lateinit var organizationApiService: OrganizationApiServiceInterface
  @SpykBean lateinit var datasetApiService: DatasetApiServiceInterface
  @Autowired lateinit var solutionApiService: SolutionApiServiceInterface
  @Autowired lateinit var workspaceApiService: WorkspaceApiServiceInterface
  @Autowired lateinit var runnerApiService: RunnerApiServiceInterface
  @Autowired lateinit var csmPlatformProperties: CsmPlatformProperties
  @Autowired lateinit var resourceLoader: ResourceLoader

  private var containerRegistryService: ContainerRegistryService = mockk(relaxed = true)
  private var startTime: Long = 0

  lateinit var dataset: DatasetCreateRequest
  lateinit var solution: SolutionCreateRequest
  lateinit var organization: OrganizationCreateRequest
  lateinit var workspace: WorkspaceCreateRequest
  lateinit var runner: RunnerCreateRequest
  lateinit var parentRunner: RunnerCreateRequest

  lateinit var datasetSaved: Dataset
  lateinit var solutionSaved: Solution
  lateinit var organizationSaved: Organization
  lateinit var workspaceSaved: Workspace
  lateinit var runnerSaved: Runner
  lateinit var parentRunnerSaved: Runner

  val runTemplateParameterValue1 =
      RunnerRunTemplateParameterValue(
          parameterId = "param1",
          value = "param1value",
          isInherited = true,
          varType = "String",
      )

  val runTemplateParameterValue2 =
      RunnerRunTemplateParameterValue(
          parameterId = "param2",
          value = "param2value",
          varType = "String",
      )

  @BeforeEach
  fun setUp() {
    mockkStatic("com.cosmotech.common.utils.SecurityUtilsKt")
    mockkStatic("com.cosmotech.common.utils.RedisUtilsKt")
    mockkStatic("org.springframework.web.context.request.RequestContextHolder")

    ReflectionTestUtils.setField(
        solutionApiService,
        "containerRegistryService",
        containerRegistryService,
    )
    every { containerRegistryService.getImageLabel(any(), any(), any()) } returns null
    mockkStatic("com.cosmotech.common.utils.SecurityUtilsKt")
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
    every { getCurrentAccountGroups(any()) } returns listOf("myTestGroup")
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "test.user"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf(ROLE_ORGANIZATION_USER)

    rediSearchIndexer.createIndexFor(Organization::class.java)
    rediSearchIndexer.createIndexFor(Dataset::class.java)
    rediSearchIndexer.createIndexFor(DatasetPart::class.java)
    rediSearchIndexer.createIndexFor(Solution::class.java)
    rediSearchIndexer.createIndexFor(Workspace::class.java)
    rediSearchIndexer.createIndexFor(Runner::class.java)

    startTime = Instant.now().toEpochMilli()

    organization = makeOrganizationCreateRequest()
    organizationSaved = organizationApiService.createOrganization(organization)

    solution = makeSolution(organizationSaved.id)
    solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

    workspace = makeWorkspaceCreateRequest("Workspace")
    workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id, workspace)

    dataset = makeDataset("Dataset")
    datasetSaved =
        datasetApiService.createDataset(
            organizationSaved.id,
            workspaceSaved.id,
            dataset,
            emptyArray(),
        )

    parentRunner =
        makeRunnerCreateRequest(
            name = "RunnerParent",
            datasetList = mutableListOf(datasetSaved.id),
            parametersValues = mutableListOf(runTemplateParameterValue1),
        )

    parentRunnerSaved =
        runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, parentRunner)

    runner =
        makeRunnerCreateRequest(
            name = "Runner",
            parentId = parentRunnerSaved.id,
            datasetList = mutableListOf(datasetSaved.id),
            parametersValues = mutableListOf(runTemplateParameterValue2),
        )

    runnerSaved = runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
  }

  @Test
  fun `test prototype injection runnerService in runnerApiService`() {
    assertNotEquals(runnerApiService.getRunnerService(), runnerApiService.getRunnerService())
  }

  @Test
  fun `test createRunner and check parameterValues data`() {

    logger.info(
        "should create a new Runner and retrieve parameter varType from solution ignoring the one declared"
    )
    val newRunner =
        makeRunnerCreateRequest(
            name = "NewRunner",
            datasetList = mutableListOf(datasetSaved.id),
            parametersValues =
                mutableListOf(
                    RunnerRunTemplateParameterValue(
                        parameterId = "param1",
                        value = "7",
                        varType = "ignored_var_type",
                    )
                ),
        )
    val newRunnerSaved =
        runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, newRunner)

    assertNotNull(newRunnerSaved.parametersValues)
    assertTrue(newRunnerSaved.parametersValues.size == 1)
    assertEquals("param1", newRunnerSaved.parametersValues[0].parameterId)
    assertEquals("7", newRunnerSaved.parametersValues[0].value)
    assertEquals("integer", newRunnerSaved.parametersValues[0].varType)
  }

  @Test
  fun `test updateRunner and check parameterValues data`() {

    logger.info(
        "should create a new Runner and retrieve parameter varType from solution ignoring the one declared"
    )
    val creationParameterValue =
        RunnerRunTemplateParameterValue(
            parameterId = "param1",
            value = "7",
            varType = "ignored_var_type",
        )
    val newRunner =
        makeRunnerCreateRequest(
            name = "NewRunner",
            datasetList = mutableListOf(datasetSaved.id),
            parametersValues = mutableListOf(creationParameterValue),
        )
    val newRunnerSaved =
        runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, newRunner)

    assertNotNull(newRunnerSaved.parametersValues)
    assertTrue(newRunnerSaved.parametersValues.size == 1)
    assertEquals(creationParameterValue, newRunnerSaved.parametersValues[0])

    val newParameterValue =
        RunnerRunTemplateParameterValue(
            parameterId = "param1",
            value = "10",
            varType = "still_ignored_var_type",
        )
    val updateRunnerSaved =
        runnerApiService.updateRunner(
            organizationSaved.id,
            workspaceSaved.id,
            newRunnerSaved.id,
            RunnerUpdateRequest(parametersValues = mutableListOf(newParameterValue)),
        )

    assertNotNull(updateRunnerSaved.parametersValues)
    assertTrue(updateRunnerSaved.parametersValues.size == 1)
    assertEquals("param1", updateRunnerSaved.parametersValues[0].parameterId)
    assertEquals("10", updateRunnerSaved.parametersValues[0].value)
    assertEquals("integer", updateRunnerSaved.parametersValues[0].varType)
  }

  @Test
  fun `test updateRunner and check parameterValues after updating a simple solution parameter to DATASET_PART varType`() {

    // 1 - create a runner with an integer parameter
    val parameterId = "param1"
    val creationParameterValue =
        RunnerRunTemplateParameterValue(parameterId = parameterId, value = "7", varType = "integer")
    val newRunner =
        makeRunnerCreateRequest(
            name = "NewRunner",
            datasetList = mutableListOf(datasetSaved.id),
            parametersValues = mutableListOf(creationParameterValue),
        )
    val newRunnerSaved =
        runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, newRunner)

    assertNotNull(newRunnerSaved.parametersValues)
    assertEquals(1, newRunnerSaved.parametersValues.size)
    assertEquals(creationParameterValue, newRunnerSaved.parametersValues[0])

    // 2 - the solution parameter varType is changed for a dataset varType

    solutionApiService.updateSolutionParameter(
        organizationSaved.id,
        solutionSaved.id,
        parameterId,
        RunTemplateParameterUpdateRequest(varType = DATASET_PART_VARTYPE_FILE),
    )

    // 3 - try to update the runner and check that the parameters are updated too
    val newParameterValue =
        RunnerRunTemplateParameterValue(
            parameterId = parameterId,
            value = "10",
            varType = "integer",
        )

    val updateRunnerSaved =
        runnerApiService.updateRunner(
            organizationSaved.id,
            workspaceSaved.id,
            newRunnerSaved.id,
            RunnerUpdateRequest(parametersValues = mutableListOf(newParameterValue)),
        )

    assertNotNull(updateRunnerSaved.parametersValues)
    assertTrue(updateRunnerSaved.parametersValues.isEmpty())
  }

  @Test
  fun `test CRUD operations on Runner as Platform Admin`() {
    every { getCurrentAccountIdentifier(any()) } returns "random_user_with_patform_admin_role"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf(ROLE_PLATFORM_ADMIN)
    var initialRunnerList =
        runnerApiService.listRunners(organizationSaved.id, workspaceSaved.id, null, null)

    logger.info("should create a new Runner")
    val newRunner =
        makeRunnerCreateRequest(name = "NewRunner", datasetList = mutableListOf(datasetSaved.id))
    val newRunnerSaved =
        runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, newRunner)

    logger.info("should find all Runners and assert there is one more")
    var runnerList =
        runnerApiService.listRunners(organizationSaved.id, workspaceSaved.id, null, null)
    assertEquals(initialRunnerList.size + 1, runnerList.size)

    logger.info("should find a Runner by Id and assert it is the one created")
    val runnerRetrieved =
        runnerApiService.getRunner(organizationSaved.id, workspaceSaved.id, runnerSaved.id)
    assertEquals(runnerSaved.apply { datasets.parameters = mutableListOf() }, runnerRetrieved)

    logger.info("should update the Runner and assert the name has been updated")
    val runnerUpdated =
        runnerApiService.updateRunner(
            organizationSaved.id,
            workspaceSaved.id,
            runnerRetrieved.id,
            RunnerUpdateRequest(name = "Runner Updated"),
        )
    assertEquals(
        runnerRetrieved.copy(name = "Runner Updated"),
        runnerUpdated.copy(updateInfo = runnerRetrieved.updateInfo).apply {
          datasets.parameters = mutableListOf()
        },
    )

    logger.info("should delete the Runner and assert there is one less Runner left")
    runnerApiService.deleteRunner(organizationSaved.id, workspaceSaved.id, newRunnerSaved.id)
    val runnerListAfterDelete =
        runnerApiService.listRunners(organizationSaved.id, workspaceSaved.id, null, null)
    assertEquals(runnerList.size - 1, runnerListAfterDelete.size)

    // We create more runner than there can be on one page of default size to assert
    // deleteAllRunners still works with high quantities of runners
    repeat(csmPlatformProperties.databases.resources.runner.defaultPageSize + 1) {
      runnerApiService.createRunner(
          organizationSaved.id,
          workspaceSaved.id,
          makeRunnerCreateRequest(),
      )
    }
  }

  @Test
  fun `test datasets_parameters is listed into listRunners response`() {
    val workspaceDatasetCreateRequest =
        makeDataset(
            name = "WorkspaceDataset",
            parts =
                mutableListOf(
                    DatasetPartCreateRequest(name = "defaultPart", sourceName = "test.txt")
                ),
        )
    val workspaceDataset =
        datasetApiService.createDataset(
            organizationSaved.id,
            workspaceSaved.id,
            workspaceDatasetCreateRequest,
            arrayOf(
                MockMultipartFile(
                    "files",
                    "test.txt",
                    MediaType.MULTIPART_FORM_DATA_VALUE,
                    "test".toByteArray(),
                )
            ),
        )
    workspaceSaved =
        workspaceApiService.updateWorkspace(
            organizationSaved.id,
            workspaceSaved.id,
            WorkspaceUpdateRequest(
                solution =
                    WorkspaceSolution(
                        solutionId = solutionSaved.id,
                        datasetId = workspaceDataset.id,
                        defaultParameterValues =
                            mutableMapOf("param2" to workspaceDataset.parts[0].id),
                    )
            ),
        )
    val runnerWithInheritedDatasetParameterCreateRequest =
        makeRunnerCreateRequest(
            name = "Runner_with_inherited_dataset_parameter",
            datasetList = mutableListOf(datasetSaved.id),
        )
    val runnerWithInheritedDatasetParameter =
        runnerApiService.createRunner(
            organizationSaved.id,
            workspaceSaved.id,
            runnerWithInheritedDatasetParameterCreateRequest,
        )

    val runnerList =
        runnerApiService.listRunners(organizationSaved.id, workspaceSaved.id, null, null)
    assertEquals(3, runnerList.size)

    val runnerFromList = runnerList.firstOrNull { it.id == runnerWithInheritedDatasetParameter.id }
    assertNotNull(runnerFromList)
    val runnerDatasetParameters = runnerFromList.datasets.parameters as MutableList<DatasetPart>
    assertNotNull(runnerDatasetParameters)
    assertEquals(1, runnerDatasetParameters.size)
    assertEquals("test.txt", runnerDatasetParameters[0].sourceName)
    assertEquals("param2", runnerDatasetParameters[0].name)
  }

  @Test
  fun `test datasets_parameters is listed into createRunner response`() {
    val workspaceDatasetCreateRequest =
        makeDataset(
            name = "WorkspaceDataset",
            parts =
                mutableListOf(
                    DatasetPartCreateRequest(name = "defaultPart", sourceName = "test.txt")
                ),
        )
    val workspaceDataset =
        datasetApiService.createDataset(
            organizationSaved.id,
            workspaceSaved.id,
            workspaceDatasetCreateRequest,
            arrayOf(
                MockMultipartFile(
                    "files",
                    "test.txt",
                    MediaType.MULTIPART_FORM_DATA_VALUE,
                    "test".toByteArray(),
                )
            ),
        )
    workspaceSaved =
        workspaceApiService.updateWorkspace(
            organizationSaved.id,
            workspaceSaved.id,
            WorkspaceUpdateRequest(
                solution =
                    WorkspaceSolution(
                        solutionId = solutionSaved.id,
                        datasetId = workspaceDataset.id,
                        defaultParameterValues =
                            mutableMapOf("param2" to workspaceDataset.parts[0].id),
                    )
            ),
        )
    val runnerWithInheritedDatasetParameterCreateRequest =
        makeRunnerCreateRequest(
            name = "Runner_with_inherited_dataset_parameter",
            datasetList = mutableListOf(datasetSaved.id),
        )
    val runnerWithInheritedDatasetParameter =
        runnerApiService.createRunner(
            organizationSaved.id,
            workspaceSaved.id,
            runnerWithInheritedDatasetParameterCreateRequest,
        )

    assertNotNull(runnerWithInheritedDatasetParameter)
    val runnerDatasetParameters =
        runnerWithInheritedDatasetParameter.datasets.parameters as MutableList<DatasetPart>
    assertNotNull(runnerDatasetParameters)
    assertEquals(1, runnerDatasetParameters.size)
    assertEquals("test.txt", runnerDatasetParameters[0].sourceName)
    assertEquals("param2", runnerDatasetParameters[0].name)
  }

  @Test
  fun `test datasets_parameters is listed into updateRunner response`() {
    val workspaceDatasetCreateRequest =
        makeDataset(
            name = "WorkspaceDataset",
            parts =
                mutableListOf(
                    DatasetPartCreateRequest(name = "defaultPart", sourceName = "test.txt")
                ),
        )
    val workspaceDataset =
        datasetApiService.createDataset(
            organizationSaved.id,
            workspaceSaved.id,
            workspaceDatasetCreateRequest,
            arrayOf(
                MockMultipartFile(
                    "files",
                    "test.txt",
                    MediaType.MULTIPART_FORM_DATA_VALUE,
                    "test".toByteArray(),
                )
            ),
        )
    workspaceSaved =
        workspaceApiService.updateWorkspace(
            organizationSaved.id,
            workspaceSaved.id,
            WorkspaceUpdateRequest(
                solution =
                    WorkspaceSolution(
                        solutionId = solutionSaved.id,
                        datasetId = workspaceDataset.id,
                        defaultParameterValues =
                            mutableMapOf("param2" to workspaceDataset.parts[0].id),
                    )
            ),
        )
    val runnerWithInheritedDatasetParameterCreateRequest =
        makeRunnerCreateRequest(
            name = "Runner_with_inherited_dataset_parameter",
            datasetList = mutableListOf(datasetSaved.id),
        )
    val runnerWithInheritedDatasetParameter =
        runnerApiService.createRunner(
            organizationSaved.id,
            workspaceSaved.id,
            runnerWithInheritedDatasetParameterCreateRequest,
        )

    val runnerUpdatedWithInheritedDatasetParameter =
        runnerApiService.updateRunner(
            organizationSaved.id,
            workspaceSaved.id,
            runnerWithInheritedDatasetParameter.id,
            RunnerUpdateRequest(name = "New Dataset name"),
        )

    assertNotNull(runnerUpdatedWithInheritedDatasetParameter)
    val runnerDatasetParameters =
        runnerUpdatedWithInheritedDatasetParameter.datasets.parameters as MutableList<DatasetPart>
    assertNotNull(runnerDatasetParameters)
    assertEquals(1, runnerDatasetParameters.size)
    assertEquals("test.txt", runnerDatasetParameters[0].sourceName)
    assertEquals("param2", runnerDatasetParameters[0].name)
  }

  @Test
  fun `test find All Runners with different pagination params`() {
    val numberOfRunners = 20
    val defaultPageSize = csmPlatformProperties.databases.resources.runner.defaultPageSize
    val expectedSize = 15
    IntRange(1, numberOfRunners - 1).forEach {
      val runner =
          makeRunnerCreateRequest(name = "Runner$it", datasetList = mutableListOf(datasetSaved.id))
      runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)
    }

    logger.info("should find all Runners and assert there are $numberOfRunners")
    var runnerList =
        runnerApiService.listRunners(organizationSaved.id, workspaceSaved.id, null, null)
    assertEquals(defaultPageSize, runnerList.size)

    logger.info("should find all Runners and assert it equals defaultPageSize: $defaultPageSize")
    runnerList = runnerApiService.listRunners(organizationSaved.id, workspaceSaved.id, 0, null)
    assertEquals(defaultPageSize, runnerList.size)

    logger.info("should find all Runners and assert there are expected size: $expectedSize")
    runnerList =
        runnerApiService.listRunners(organizationSaved.id, workspaceSaved.id, 0, expectedSize)
    assertEquals(expectedSize, runnerList.size)

    logger.info("should find all Runners and assert it returns the second / last page")
    runnerList =
        runnerApiService.listRunners(organizationSaved.id, workspaceSaved.id, 1, defaultPageSize)
    assertEquals(1, runnerList.size)
  }

  @Test
  fun `test find All Runners with wrong pagination params`() {
    logger.info("Should throw IllegalArgumentException when page and size are zeros")
    assertThrows<IllegalArgumentException> {
      runnerApiService.listRunners(organizationSaved.id, workspaceSaved.id, 0, 0)
    }

    logger.info("Should throw IllegalArgumentException when page is negative")
    assertThrows<IllegalArgumentException> {
      runnerApiService.listRunners(organizationSaved.id, workspaceSaved.id, -1, 10)
    }

    logger.info("Should throw IllegalArgumentException when size is negative")
    assertThrows<IllegalArgumentException> {
      runnerApiService.listRunners(organizationSaved.id, workspaceSaved.id, 0, -1)
    }
  }

  @Test
  fun `update parentId on Runner delete`() {
    // Create a 3 level hierarchy: grandParent <- parent <- child
    val grandParentCreation = makeRunnerCreateRequest()
    val grandParentRunner =
        runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, grandParentCreation)
    val parentCreation = makeRunnerCreateRequest(parentId = grandParentRunner.id)
    val parentRunner =
        runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, parentCreation)
    val childCreation = makeRunnerCreateRequest(parentId = parentRunner.id)
    val childRunner =
        runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, childCreation)

    // Initial parents check
    assertEquals(grandParentRunner.id, parentRunner.parentId)
    assertEquals(parentRunner.id, childRunner.parentId)

    // Delete intermediate parent, child should refer to grandParent
    runnerApiService.deleteRunner(organizationSaved.id, workspaceSaved.id, parentRunner.id)
    var newChildParentId =
        runnerApiService.getRunner(organizationSaved.id, workspaceSaved.id, childRunner.id).parentId
    assertEquals(grandParentRunner.id, newChildParentId)

    // Delete root grandParent, child should clear its parent
    runnerApiService.deleteRunner(organizationSaved.id, workspaceSaved.id, grandParentRunner.id)
    newChildParentId =
        runnerApiService.getRunner(organizationSaved.id, workspaceSaved.id, childRunner.id).parentId
    assertNull(newChildParentId)
  }

  @Test
  fun `update rootId on root Runner delete`() {
    // Create a 3 level hierarchy: grandParent <- parent1 <- child1
    //                                         <- parent2 <- child2
    val grandParentCreation = makeRunnerCreateRequest()
    val grandParentRunner =
        runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, grandParentCreation)
    val parentCreation = makeRunnerCreateRequest(parentId = grandParentRunner.id)
    val parentRunner1 =
        runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, parentCreation)
    val parentRunner2 =
        runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, parentCreation)
    var childCreation = makeRunnerCreateRequest(parentId = parentRunner1.id)
    val childRunner1 =
        runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, childCreation)
    childCreation.parentId = parentRunner2.id
    val childRunner2 =
        runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, childCreation)

    // Initial parents check
    assertEquals(grandParentRunner.id, parentRunner1.parentId)
    assertEquals(grandParentRunner.id, parentRunner2.parentId)
    assertEquals(parentRunner1.id, childRunner1.parentId)
    assertEquals(parentRunner2.id, childRunner2.parentId)
    // Initial root check
    assertEquals(grandParentRunner.id, parentRunner1.rootId)
    assertEquals(grandParentRunner.id, parentRunner2.rootId)
    assertEquals(grandParentRunner.id, childRunner1.rootId)
    assertEquals(grandParentRunner.id, childRunner2.rootId)

    // Delete grand parent
    runnerApiService.deleteRunner(organizationSaved.id, workspaceSaved.id, grandParentRunner.id)
    assertNull(
        runnerApiService.getRunner(organizationSaved.id, workspaceSaved.id, parentRunner1.id).rootId
    )
    assertNull(
        runnerApiService.getRunner(organizationSaved.id, workspaceSaved.id, parentRunner2.id).rootId
    )
    assertEquals(
        parentRunner1.id,
        runnerApiService.getRunner(organizationSaved.id, workspaceSaved.id, childRunner1.id).rootId,
    )
    assertEquals(
        parentRunner2.id,
        runnerApiService.getRunner(organizationSaved.id, workspaceSaved.id, childRunner2.id).rootId,
    )
  }

  @Test
  fun `test RBAC RunnerSecurity as Platform Admin`() {
    every { getCurrentAuthenticatedRoles(any()) } returns listOf(ROLE_PLATFORM_ADMIN)

    logger.info("should test default security is set to ROLE_NONE")
    val runnerSecurity =
        runnerApiService.getRunnerSecurity(organizationSaved.id, workspaceSaved.id, runnerSaved.id)
    assertEquals(ROLE_NONE, runnerSecurity.default)

    logger.info("should set default security to ROLE_VIEWER and assert it has been set")
    val runnerRole = RunnerRole(ROLE_VIEWER)
    val runnerSecurityRegistered =
        runnerApiService.updateRunnerDefaultSecurity(
            organizationSaved.id,
            workspaceSaved.id,
            runnerSaved.id,
            runnerRole,
        )
    assertEquals(runnerRole.role, runnerSecurityRegistered.default)
  }

  @Test
  fun `test RBAC RunnerSecurity as Unauthorized User`() {
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_READER_USER

    logger.info("should throw CsmAccessForbiddenException when trying to access RunnerSecurity")
    // Test default security
    assertThrows<CsmAccessForbiddenException> {
      runnerApiService.getRunnerSecurity(organizationSaved.id, workspaceSaved.id, runnerSaved.id)
    }

    logger.info("should throw CsmAccessForbiddenException when trying to set default security")
    val runnerRole = RunnerRole(ROLE_VIEWER)
    assertThrows<CsmAccessForbiddenException> {
      runnerApiService.updateRunnerDefaultSecurity(
          organizationSaved.id,
          workspaceSaved.id,
          runnerSaved.id,
          runnerRole,
      )
    }
  }

  @Test
  fun `test AccessControls management on Runner as ressource Admin`() {
    logger.info("should add an Access Control and assert it has been added")
    val runnerAccessControl = RunnerAccessControl(TEST_USER_MAIL, ROLE_VIEWER)
    var runnerAccessControlRegistered =
        runnerApiService.createRunnerAccessControl(
            organizationSaved.id,
            workspaceSaved.id,
            runnerSaved.id,
            runnerAccessControl,
        )
    assertEquals(runnerAccessControl, runnerAccessControlRegistered)

    logger.info("should get the Access Control and assert it is the one created")
    runnerAccessControlRegistered =
        runnerApiService.getRunnerAccessControl(
            organizationSaved.id,
            workspaceSaved.id,
            runnerSaved.id,
            TEST_USER_MAIL,
        )
    assertEquals(runnerAccessControl, runnerAccessControlRegistered)

    logger.info(
        "should add an Access Control and assert it is the one created in the parameter dataset"
    )
    assertDoesNotThrow {
      datasetApiService.getDatasetAccessControl(
          organizationSaved.id,
          workspaceSaved.id,
          runnerSaved.datasets.parameter,
          TEST_USER_MAIL,
      )
    }

    logger.info("should update the Access Control and assert it has been updated")
    runnerAccessControlRegistered =
        runnerApiService.updateRunnerAccessControl(
            organizationSaved.id,
            workspaceSaved.id,
            runnerSaved.id,
            TEST_USER_MAIL,
            RunnerRole(ROLE_EDITOR),
        )
    assertEquals(ROLE_EDITOR, runnerAccessControlRegistered.role)

    logger.info(
        "Should not change the parameter dataset access control because ACL already exist on it"
    )
    assertEquals(
        ROLE_VIEWER,
        datasetApiService
            .getDatasetAccessControl(
                organizationSaved.id,
                workspaceSaved.id,
                runnerSaved.datasets.parameter,
                TEST_USER_MAIL,
            )
            .role,
    )

    logger.info("should get the list of users and assert there are 2")
    val userList =
        runnerApiService.listRunnerSecurityUsers(
            organizationSaved.id,
            workspaceSaved.id,
            runnerSaved.id,
        )
    assertEquals(3, userList.size)

    logger.info("should remove the Access Control and assert it has been removed")
    runnerApiService.deleteRunnerAccessControl(
        organizationSaved.id,
        workspaceSaved.id,
        runnerSaved.id,
        TEST_USER_MAIL,
    )
    assertThrows<CsmResourceNotFoundException> {
      runnerApiService.getRunnerAccessControl(
          organizationSaved.id,
          workspaceSaved.id,
          runnerSaved.id,
          TEST_USER_MAIL,
      )
    }

    logger.info(
        "should remove the Access Control and assert it has been removed in the parameter dataset"
    )
    assertThrows<CsmResourceNotFoundException> {
      datasetApiService.getDatasetAccessControl(
          organizationSaved.id,
          workspaceSaved.id,
          runnerSaved.datasets.parameter,
          TEST_USER_MAIL,
      )
    }
  }

  @Test
  fun `test AccessControls management on Runner as Unauthorized User`() {
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_READER_USER

    logger.info("should throw CsmAccessForbiddenException when trying to add RunnerAccessControl")
    val runnerAccessControl = RunnerAccessControl(TEST_USER_MAIL, ROLE_VIEWER)
    assertThrows<CsmAccessForbiddenException> {
      runnerApiService.createRunnerAccessControl(
          organizationSaved.id,
          workspaceSaved.id,
          runnerSaved.id,
          runnerAccessControl,
      )
    }

    logger.info("should throw CsmAccessForbiddenException when trying to get RunnerAccessControl")
    assertThrows<CsmAccessForbiddenException> {
      runnerApiService.getRunnerAccessControl(
          organizationSaved.id,
          workspaceSaved.id,
          runnerSaved.id,
          TEST_USER_MAIL,
      )
    }

    logger.info(
        "should throw CsmAccessForbiddenException when trying to update RunnerAccessControl"
    )
    assertThrows<CsmAccessForbiddenException> {
      runnerApiService.updateRunnerAccessControl(
          organizationSaved.id,
          workspaceSaved.id,
          runnerSaved.id,
          TEST_USER_MAIL,
          RunnerRole(ROLE_VIEWER),
      )
    }

    logger.info("should throw CsmAccessForbiddenException when getting the list of users")
    assertThrows<CsmAccessForbiddenException> {
      runnerApiService.listRunnerSecurityUsers(
          organizationSaved.id,
          workspaceSaved.id,
          runnerSaved.id,
      )
    }

    logger.info(
        "should throw CsmAccessForbiddenException when trying to remove RunnerAccessControl"
    )
    assertThrows<CsmAccessForbiddenException> {
      runnerApiService.deleteRunnerAccessControl(
          organizationSaved.id,
          workspaceSaved.id,
          runnerSaved.id,
          TEST_USER_MAIL,
      )
    }
  }

  @Test
  fun `test deleting a running runner`() {
    runnerApiService.updateRunner(
        organizationSaved.id,
        workspaceSaved.id,
        runnerSaved.id,
        RunnerUpdateRequest(),
    )

    every { eventPublisher.publishEvent(any<HasRunningRuns>()) } answers
        {
          firstArg<HasRunningRuns>().response = true
        }

    val exception =
        assertThrows<Exception> {
          runnerApiService.deleteRunner(organizationSaved.id, workspaceSaved.id, runnerSaved.id)
        }
    assertEquals(
        "Can't delete runner ${runnerSaved.id}: at least one run is still running",
        exception.message,
    )
  }

  @Test
  fun `test on runner delete keep bases datasets but not parameters dataset`() {
    runnerApiService.deleteRunner(organizationSaved.id, workspaceSaved.id, runnerSaved.id)

    runnerSaved.datasets.bases.forEach { dataset ->
      assertDoesNotThrow {
        datasetApiService.getDataset(organizationSaved.id, workspaceSaved.id, dataset)
      }
    }
    val parameterDatasetId = runnerSaved.datasets.parameter
    val exception =
        assertThrows<CsmResourceNotFoundException> {
          datasetApiService.getDataset(organizationSaved.id, workspaceSaved.id, parameterDatasetId)
        }
    assertEquals(
        "Dataset $parameterDatasetId not found " +
            "in organization ${organizationSaved.id} " +
            "and workspace ${workspaceSaved.id}",
        exception.message,
    )
  }

  @Test
  fun `test on runner creation with null datasetList when parent has empty datasetList`() {
    val parentRunnerWithEmptyDatasetList = makeRunnerCreateRequest()
    assertNotNull(parentRunnerWithEmptyDatasetList.datasetList)
    assertTrue { parentRunnerWithEmptyDatasetList.datasetList!!.isEmpty() }

    val parentId =
        runnerApiService
            .createRunner(organizationSaved.id, workspaceSaved.id, parentRunnerWithEmptyDatasetList)
            .id
    val childRunnerWithNullDatasetList =
        makeRunnerCreateRequest(parentId = parentId, datasetList = mutableListOf())
    val childRunnerDatasetList =
        runnerApiService
            .createRunner(organizationSaved.id, workspaceSaved.id, childRunnerWithNullDatasetList)
            .datasets
            .bases

    assertNotNull(childRunnerDatasetList)
    assertTrue { childRunnerDatasetList.isEmpty() }
  }

  @Test
  fun `test on runner creation with null datasetList when parent has non-empty datasetList`() {
    val parentDatasetList = mutableListOf(datasetSaved.id)
    val parentRunnerWithNonEmptyDatasetList =
        makeRunnerCreateRequest(datasetList = parentDatasetList)
    assertNotNull(parentRunnerWithNonEmptyDatasetList.datasetList)
    assertTrue { parentRunnerWithNonEmptyDatasetList.datasetList!!.isNotEmpty() }

    val parentId =
        runnerApiService
            .createRunner(
                organizationSaved.id,
                workspaceSaved.id,
                parentRunnerWithNonEmptyDatasetList,
            )
            .id
    val childRunnerWithNullDatasetList =
        makeRunnerCreateRequest(parentId = parentId, datasetList = null)
    val childRunnerDatasetList =
        runnerApiService
            .createRunner(organizationSaved.id, workspaceSaved.id, childRunnerWithNullDatasetList)
            .datasets
            .bases

    assertNotNull(childRunnerDatasetList)
    assertEquals(parentDatasetList, childRunnerDatasetList)
  }

  @Test
  fun `test on runner creation with empty datasetList when parent has non-empty datasetList`() {

    val parentDatasetList = mutableListOf(datasetSaved.id)
    val parentRunnerWithNonEmptyDatasetList =
        makeRunnerCreateRequest(datasetList = parentDatasetList)
    assertNotNull(parentRunnerWithNonEmptyDatasetList.datasetList)
    assertTrue { parentRunnerWithNonEmptyDatasetList.datasetList!!.isNotEmpty() }

    val parentId =
        runnerApiService
            .createRunner(
                organizationSaved.id,
                workspaceSaved.id,
                parentRunnerWithNonEmptyDatasetList,
            )
            .id
    val childRunnerWithEmptyDatasetList =
        makeRunnerCreateRequest(parentId = parentId, datasetList = mutableListOf())
    val childRunnerDatasetList =
        runnerApiService
            .createRunner(organizationSaved.id, workspaceSaved.id, childRunnerWithEmptyDatasetList)
            .datasets
            .bases

    assertNotNull(childRunnerDatasetList)
    assertTrue(childRunnerDatasetList.isEmpty())
  }

  @Test
  fun `test on runner creation with non-empty datasetList when parent has non-empty datasetList`() {
    val parentDatasetList = mutableListOf(datasetSaved.id)
    val parentRunnerWithNonEmptyDatasetList =
        makeRunnerCreateRequest(datasetList = parentDatasetList)
    assertNotNull(parentRunnerWithNonEmptyDatasetList.datasetList)
    assertTrue { parentRunnerWithNonEmptyDatasetList.datasetList!!.isNotEmpty() }

    val parentId =
        runnerApiService
            .createRunner(
                organizationSaved.id,
                workspaceSaved.id,
                parentRunnerWithNonEmptyDatasetList,
            )
            .id

    val childDataset =
        datasetApiService.createDataset(
            organizationSaved.id,
            workspaceSaved.id,
            makeDataset(name = "For Child Runner"),
            emptyArray(),
        )

    val childDatasetList = mutableListOf(childDataset.id)
    val childRunnerWithNonEmptyDatasetList =
        makeRunnerCreateRequest(parentId = parentId, datasetList = childDatasetList)
    val childRunnerDatasetList =
        runnerApiService
            .createRunner(
                organizationSaved.id,
                workspaceSaved.id,
                childRunnerWithNonEmptyDatasetList,
            )
            .datasets
            .bases

    assertNotNull(childRunnerDatasetList)
    assertEquals(childDatasetList, childRunnerDatasetList)
  }

  @Test
  fun `test updating (adding) runner's datasetList add runner users to new dataset`() {
    val newDataset =
        datasetApiService.createDataset(
            organizationSaved.id,
            workspaceSaved.id,
            makeDataset(),
            emptyArray(),
        )
    runnerSaved =
        runnerApiService.updateRunner(
            organizationSaved.id,
            workspaceSaved.id,
            runnerSaved.id,
            RunnerUpdateRequest(datasetList = mutableListOf(datasetSaved.id, newDataset.id)),
        )

    val runnerUserList =
        runnerApiService.listRunnerSecurityUsers(
            organizationSaved.id,
            workspaceSaved.id,
            runnerSaved.id,
        )

    val datasetUserList =
        datasetApiService.listDatasetSecurityUsers(
            organizationSaved.id,
            workspaceSaved.id,
            newDataset.id,
        )
    datasetUserList.containsAll(runnerUserList)
  }

  @Test
  fun `access control list shouldn't contain more than one time each user on creation`() {
    organizationSaved =
        organizationApiService.createOrganization(makeOrganizationCreateRequest("organization"))
    solutionSaved = solutionApiService.createSolution(organizationSaved.id, makeSolution())
    workspaceSaved =
        workspaceApiService.createWorkspace(organizationSaved.id, makeWorkspaceCreateRequest())
    val brokenRunner =
        RunnerCreateRequest(
            name = "runner",
            runTemplateId = "runTemplate",
            solutionId = solutionSaved.id,
            datasetList = mutableListOf(),
            security =
                RunnerSecurity(
                    default = ROLE_NONE,
                    accessControlList =
                        mutableListOf(
                            RunnerAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                            RunnerAccessControl(CONNECTED_ADMIN_USER, ROLE_EDITOR),
                        ),
                ),
        )
    assertThrows<IllegalArgumentException> {
      runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, brokenRunner)
    }
  }

  @Test
  fun `access control list can't add an existing user`() {
    organizationSaved =
        organizationApiService.createOrganization(makeOrganizationCreateRequest("organization"))
    solutionSaved = solutionApiService.createSolution(organizationSaved.id, makeSolution())
    workspaceSaved =
        workspaceApiService.createWorkspace(organizationSaved.id, makeWorkspaceCreateRequest())
    val workingRunner = makeRunnerCreateRequest()
    runnerSaved =
        runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, workingRunner)

    val runnerSavedSecurityUsers =
        runnerApiService.listRunnerSecurityUsers(
            organizationSaved.id,
            workspaceSaved.id,
            runnerSaved.id,
        )
    assertEquals(2, runnerSavedSecurityUsers.size)

    assertThrows<IllegalArgumentException> {
      runnerApiService.createRunnerAccessControl(
          organizationSaved.id,
          workspaceSaved.id,
          runnerSaved.id,
          RunnerAccessControl(defaultName, ROLE_EDITOR),
      )
    }

    val runnerSecurityUsers =
        runnerApiService.listRunnerSecurityUsers(
            organizationSaved.id,
            workspaceSaved.id,
            runnerSaved.id,
        )
    assertEquals(2, runnerSecurityUsers.size)
    assert(runnerSavedSecurityUsers == runnerSecurityUsers)
  }

  @Test
  fun `access control list can't update a non-existing user`() {
    organizationSaved =
        organizationApiService.createOrganization(makeOrganizationCreateRequest("organization"))
    solutionSaved = solutionApiService.createSolution(organizationSaved.id, makeSolution())
    workspaceSaved =
        workspaceApiService.createWorkspace(organizationSaved.id, makeWorkspaceCreateRequest())
    val workingRunner = makeRunnerCreateRequest()
    runnerSaved =
        runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, workingRunner)

    val runnerSavedSecurityUsers =
        runnerApiService.listRunnerSecurityUsers(
            organizationSaved.id,
            workspaceSaved.id,
            runnerSaved.id,
        )
    assertEquals(2, runnerSavedSecurityUsers.size)

    assertThrows<CsmResourceNotFoundException> {
      runnerApiService.updateRunnerAccessControl(
          organizationSaved.id,
          workspaceSaved.id,
          runnerSaved.id,
          "invalid user",
          RunnerRole(ROLE_VIEWER),
      )
    }

    val runnerSecurityUsers =
        runnerApiService.listRunnerSecurityUsers(
            organizationSaved.id,
            workspaceSaved.id,
            runnerSaved.id,
        )
    assertEquals(2, runnerSecurityUsers.size)
    assert(runnerSavedSecurityUsers == runnerSecurityUsers)
  }

  @Test
  fun `on Runner delete linked datasets should not be deleted`() {
    workspace =
        WorkspaceCreateRequest(
            key = "key",
            name = "workspace",
            solution = WorkspaceSolution(solutionSaved.id),
            datasetCopy = false,
        )
    workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id, workspace)
    val runnerDataset =
        datasetApiService.createDataset(
            organizationSaved.id,
            workspaceSaved.id,
            makeDataset(),
            emptyArray(),
        )

    runner = makeRunnerCreateRequest(datasetList = mutableListOf(runnerDataset.id))
    runnerSaved = runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)

    val datasetRetrieved =
        datasetApiService.getDataset(
            organizationSaved.id,
            workspaceSaved.id,
            runnerSaved.datasets.bases[0],
        )
    runnerApiService.deleteRunner(organizationSaved.id, workspaceSaved.id, runnerSaved.id)

    assertDoesNotThrow {
      datasetApiService.getDataset(organizationSaved.id, workspaceSaved.id, datasetRetrieved.id)
    }
  }

  @Test
  fun `users added to runner RBAC should not have the corresponding role set in bases datasets`() {
    workspace =
        WorkspaceCreateRequest(
            key = "key",
            name = "workspace",
            solution = WorkspaceSolution(solutionSaved.id),
            datasetCopy = true,
        )
    workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id, workspace)
    val runnerDataset =
        datasetApiService.createDataset(
            organizationSaved.id,
            workspaceSaved.id,
            makeDataset("runnerDataset"),
            emptyArray(),
        )
    runner = makeRunnerCreateRequest(datasetList = mutableListOf(runnerDataset.id))
    runnerSaved = runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)

    runnerApiService.createRunnerAccessControl(
        organizationSaved.id,
        workspaceSaved.id,
        runnerSaved.id,
        RunnerAccessControl(id = "id", role = ROLE_EDITOR),
    )

    val retrievedDataset =
        datasetApiService.getDataset(
            organizationSaved.id,
            workspaceSaved.id,
            runnerSaved.datasets.bases[0],
        )

    val exception =
        assertThrows<CsmResourceNotFoundException> {
          datasetApiService.getDatasetAccessControl(
              organizationSaved.id,
              workspaceSaved.id,
              retrievedDataset.id,
              "id",
          )
        }
    assertEquals("Entity id not found in ${retrievedDataset.id} component", exception.message)
  }

  @Test
  fun `test create Runner with only mandatory fields`() {
    val userId = "random_user_with_patform_admin_role"
    every { getCurrentAccountIdentifier(any()) } returns userId
    every { getCurrentAuthenticatedRoles(any()) } returns listOf(ROLE_PLATFORM_ADMIN)

    logger.info("should create a new Runner")
    val name = "new_runner"
    val runTemplateId = "runTemplateId"
    val newRunner =
        RunnerCreateRequest(
            name = name,
            solutionId = solutionSaved.id,
            runTemplateId = runTemplateId,
        )

    val newRunnerCreated =
        runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, newRunner)

    assertNotNull(newRunnerCreated)
    assertEquals(name, newRunnerCreated.name)
    assertEquals(runTemplateId, newRunnerCreated.runTemplateId)
    assertEquals(ROLE_NONE, newRunnerCreated.security.default)
    assertEquals(userId, newRunnerCreated.security.accessControlList[0].id)
    assertEquals(ROLE_ADMIN, newRunnerCreated.security.accessControlList[0].role)
  }

  @Test
  fun `test runner creation with unknown runtemplateId`() {
    val runnerWithWrongRunTemplateId =
        makeRunnerCreateRequest(
            name = "Runner_With_unknown_runtemplate_id",
            parentId = "unknown_parent_id",
            runTemplateId = "unknown_runtemplate_id",
            datasetList = mutableListOf(datasetSaved.id),
            parametersValues = mutableListOf(runTemplateParameterValue2),
        )

    val assertThrows =
        assertThrows<CsmResourceNotFoundException> {
          runnerApiService.createRunner(
              organizationSaved.id,
              workspaceSaved.id,
              runnerWithWrongRunTemplateId,
          )
        }
    assertEquals(
        "Solution run template with id ${runnerWithWrongRunTemplateId.runTemplateId} does not exist",
        assertThrows.message,
    )
  }

  @Test
  fun `test runner creation with unknown parentId`() {
    val parentId = "unknown_parent_id"
    val runnerWithWrongParentId =
        makeRunnerCreateRequest(
            name = "Runner_With_unknown_parent",
            parentId = parentId,
            datasetList = mutableListOf(datasetSaved.id),
            parametersValues = mutableListOf(runTemplateParameterValue2),
        )

    val assertThrows =
        assertThrows<IllegalArgumentException> {
          runnerApiService.createRunner(
              organizationSaved.id,
              workspaceSaved.id,
              runnerWithWrongParentId,
          )
        }
    assertTrue(assertThrows.message!!.startsWith("Parent Id $parentId define on"))
  }

  @Test
  fun `test inherited parameters values`() {
    val runnerSavedParametersValues = runnerSaved.parametersValues
    assertNotNull(runnerSavedParametersValues)
    assertEquals(1, runnerSavedParametersValues.size)
    assertEquals(mutableListOf(runTemplateParameterValue1), runnerSavedParametersValues)
  }

  @Test
  fun `test empty inherited parameters values from parent`() {
    val parentRunnerWithEmptyParams = makeRunnerCreateRequest(name = "parent")
    val parentRunnerSaved =
        runnerApiService.createRunner(
            organizationSaved.id,
            workspaceSaved.id,
            parentRunnerWithEmptyParams,
        )

    val parentRunnerUpdated =
        runnerApiService.updateRunner(
            organizationSaved.id,
            workspaceSaved.id,
            parentRunnerSaved.id,
            RunnerUpdateRequest(
                parametersValues =
                    mutableListOf(
                        RunnerRunTemplateParameterValue(
                            parameterId = "param1",
                            value = "param1value",
                            isInherited = false,
                            varType = "String",
                        )
                    )
            ),
        )

    val childRunnerWithEmptyParams =
        makeRunnerCreateRequest(name = "child", parentId = parentRunnerUpdated.id)

    val childRunnerWithEmptyParamsSaved =
        runnerApiService.createRunner(
            organizationSaved.id,
            workspaceSaved.id,
            childRunnerWithEmptyParams,
        )

    assertNotNull(childRunnerWithEmptyParamsSaved.parametersValues)
    assertEquals(1, childRunnerWithEmptyParamsSaved.parametersValues.size)
    assertEquals(
        mutableListOf(runTemplateParameterValue1),
        childRunnerWithEmptyParamsSaved.parametersValues,
    )
  }

  @Test
  fun `test create runner with inherited DB dataset parameter from workspace solution`() {

    solutionSaved =
        solutionApiService.createSolution(
            organizationSaved.id,
            SolutionCreateRequest(
                key = UUID.randomUUID().toString(),
                name = "Solution with 1 default datasetPart parameter",
                parameterGroups =
                    mutableListOf(
                        RunTemplateParameterGroupCreateRequest(
                            id = "defaultDatasetPartParameterGroup",
                            parameters = mutableListOf("datasetPartParam"),
                        )
                    ),
                parameters =
                    mutableListOf(
                        RunTemplateParameterCreateRequest(
                            id = "datasetPartParam",
                            defaultValue = "this_value_is_ignored",
                            varType = DATASET_PART_VARTYPE_DB,
                        )
                    ),
                runTemplates =
                    mutableListOf(
                        RunTemplateCreateRequest(
                            id = "runTemplateWithOneDatasetPartByDefault",
                            parameterGroups = mutableListOf("defaultDatasetPartParameterGroup"),
                        )
                    ),
                repository = "repository",
                version = "1.0.0",
            ),
        )
    workspace =
        WorkspaceCreateRequest(
            key = "key",
            name = "workspace",
            solution = WorkspaceSolution(solutionSaved.id),
            datasetCopy = false,
        )
    workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id, workspace)

    val customersFile = resourceLoader.getResource("classpath:/$CUSTOMERS_FILE_NAME").file
    val customersInputStream = FileInputStream(customersFile)
    val customersMultipartFile =
        MockMultipartFile(
            "file",
            CUSTOMERS_FILE_NAME,
            "text/csv",
            IOUtils.toByteArray(customersInputStream),
        )
    val workspaceDataset =
        datasetApiService.createDataset(
            organizationSaved.id,
            workspaceSaved.id,
            DatasetCreateRequest(
                name = "runnerDataset",
                parts =
                    mutableListOf(
                        DatasetPartCreateRequest(
                            name = "datasetPart1",
                            sourceName = CUSTOMERS_FILE_NAME,
                            type = DatasetPartTypeEnum.DB,
                        )
                    ),
            ),
            arrayOf(customersMultipartFile),
        )
    workspaceSaved =
        workspaceApiService.updateWorkspace(
            organizationSaved.id,
            workspaceSaved.id,
            WorkspaceUpdateRequest(
                solution =
                    WorkspaceSolution(
                        solutionId = solutionSaved.id,
                        datasetId = workspaceDataset.id,
                        defaultParameterValues =
                            mutableMapOf("datasetPartParam" to workspaceDataset.parts[0].id),
                    )
            ),
        )

    val runner =
        runnerApiService.createRunner(
            organizationSaved.id,
            workspaceSaved.id,
            makeRunnerCreateRequest(runTemplateId = "runTemplateWithOneDatasetPartByDefault"),
        )

    val runnerDatasetId = runner.datasets.parameter
    val runnerDataset =
        datasetApiService.getDataset(organizationSaved.id, workspaceSaved.id, runnerDatasetId)
    val inheritedRunnerDatasetPartContent =
        datasetApiService.downloadDatasetPart(
            organizationSaved.id,
            workspaceSaved.id,
            runnerDataset.id,
            runnerDataset.parts[0].id,
        )
    val expectedText = FileInputStream(customersFile).bufferedReader().use { it.readText() }
    val retrievedText =
        InputStreamResource(inheritedRunnerDatasetPartContent).inputStream.bufferedReader().use {
          it.readText()
        }
    assertEquals(expectedText, retrievedText)
  }

  @Test
  fun `test create runner with inherited DB dataset parameter from parent`() {

    solutionSaved =
        solutionApiService.createSolution(
            organizationSaved.id,
            SolutionCreateRequest(
                key = UUID.randomUUID().toString(),
                name = "Solution with 1 default datasetPart parameter",
                parameterGroups =
                    mutableListOf(
                        RunTemplateParameterGroupCreateRequest(
                            id = "defaultDatasetPartParameterGroup",
                            parameters = mutableListOf("datasetPartParam"),
                        )
                    ),
                parameters =
                    mutableListOf(
                        RunTemplateParameterCreateRequest(
                            id = "datasetPartParam",
                            defaultValue = "this_value_is_ignored",
                            varType = DATASET_PART_VARTYPE_DB,
                        )
                    ),
                runTemplates =
                    mutableListOf(
                        RunTemplateCreateRequest(
                            id = "runTemplateWithOneDatasetPartByDefault",
                            parameterGroups = mutableListOf("defaultDatasetPartParameterGroup"),
                        )
                    ),
                repository = "repository",
                version = "1.0.0",
            ),
        )
    workspace =
        WorkspaceCreateRequest(
            key = "key",
            name = "workspace",
            solution = WorkspaceSolution(solutionSaved.id),
            datasetCopy = false,
        )
    workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id, workspace)

    val customersFile = resourceLoader.getResource("classpath:/$CUSTOMERS_FILE_NAME").file
    val customersInputStream = FileInputStream(customersFile)
    val customersMultipartFile =
        MockMultipartFile(
            "file",
            CUSTOMERS_FILE_NAME,
            "text/csv",
            IOUtils.toByteArray(customersInputStream),
        )
    val workspaceDataset =
        datasetApiService.createDataset(
            organizationSaved.id,
            workspaceSaved.id,
            DatasetCreateRequest(
                name = "runnerDataset",
                parts =
                    mutableListOf(
                        DatasetPartCreateRequest(
                            name = "datasetPart1",
                            sourceName = CUSTOMERS_FILE_NAME,
                            type = DatasetPartTypeEnum.DB,
                        )
                    ),
            ),
            arrayOf(customersMultipartFile),
        )
    workspaceSaved =
        workspaceApiService.updateWorkspace(
            organizationSaved.id,
            workspaceSaved.id,
            WorkspaceUpdateRequest(
                solution =
                    WorkspaceSolution(
                        solutionId = solutionSaved.id,
                        datasetId = workspaceDataset.id,
                        defaultParameterValues =
                            mutableMapOf("datasetPartParam" to workspaceDataset.parts[0].id),
                    )
            ),
        )

    val runner =
        runnerApiService.createRunner(
            organizationSaved.id,
            workspaceSaved.id,
            makeRunnerCreateRequest(runTemplateId = "runTemplateWithOneDatasetPartByDefault"),
        )

    val runnerDatasetId = runner.datasets.parameter
    val runnerDataset =
        datasetApiService.getDataset(organizationSaved.id, workspaceSaved.id, runnerDatasetId)

    val customers5File = resourceLoader.getResource("classpath:/$CUSTOMERS_5_LINES_FILE_NAME").file
    val customers5InputStream = FileInputStream(customers5File)
    val customers5MultipartFile =
        MockMultipartFile(
            "file",
            CUSTOMERS_5_LINES_FILE_NAME,
            "text/csv",
            IOUtils.toByteArray(customers5InputStream),
        )

    val runnerDatasetPartId = runnerDataset.parts[0].id

    datasetApiService.replaceDatasetPart(
        organizationSaved.id,
        workspaceSaved.id,
        runnerDataset.id,
        runnerDatasetPartId,
        customers5MultipartFile,
        DatasetPartUpdateRequest(sourceName = CUSTOMERS_5_LINES_FILE_NAME),
    )

    val childRunner =
        runnerApiService.createRunner(
            organizationSaved.id,
            workspaceSaved.id,
            makeRunnerCreateRequest(
                runTemplateId = "runTemplateWithOneDatasetPartByDefault",
                parentId = runner.id,
            ),
        )

    val childRunnerDatasetId = childRunner.datasets.parameter
    val childRunnerDataset =
        datasetApiService.getDataset(organizationSaved.id, workspaceSaved.id, childRunnerDatasetId)

    val childRunnerDatasetPartContent =
        datasetApiService.downloadDatasetPart(
            organizationSaved.id,
            workspaceSaved.id,
            childRunnerDataset.id,
            childRunnerDataset.parts[0].id,
        )
    val expectedText = FileInputStream(customers5File).bufferedReader().use { it.readText() }
    val retrievedText =
        InputStreamResource(childRunnerDatasetPartContent).inputStream.bufferedReader().use {
          it.readText()
        }
    assertEquals(expectedText, retrievedText)
  }

  @Test
  fun `startRun send event`() {
    val expectedRunId = "run-genid12345"
    every { eventPublisher.publishEvent(any<RunStart>()) } answers
        {
          firstArg<RunStart>().response = expectedRunId
        }

    val run = runnerApiService.startRun(organizationSaved.id, workspaceSaved.id, runnerSaved.id)
    assertEquals(expectedRunId, run.id)
  }

  @Test
  fun `test getRunner when runner has not been started yet`() {

    val runner = runnerApiService.getRunner(organizationSaved.id, workspaceSaved.id, runnerSaved.id)

    assertNull(runner.lastRunInfo.lastRunId)
    assertEquals(LastRunInfo.LastRunStatus.NotStarted, runner.lastRunInfo.lastRunStatus)
  }

  @Test
  fun `test getRunner when runner has just been created`() {

    val runner =
        runnerApiService.createRunner(
            organizationSaved.id,
            workspaceSaved.id,
            makeRunnerCreateRequest(),
        )

    assertNull(runner.lastRunInfo.lastRunId)
    assertEquals(LastRunInfo.LastRunStatus.NotStarted, runner.lastRunInfo.lastRunStatus)
  }

  @Test
  fun `test getRunner when runner has been started`() {

    val expectedRunId = "run-genid12345"
    every { eventPublisher.publishEvent(any<RunStart>()) } answers
        {
          firstArg<RunStart>().response = expectedRunId
        }

    val run = runnerApiService.startRun(organizationSaved.id, workspaceSaved.id, runnerSaved.id)
    assertEquals(expectedRunId, run.id)

    // This line points to the fact that the run status is not final, so the getRunner trigger an
    // update
    // for lastRunInfo
    every { eventPublisher.publishEvent(any<UpdateRunnerStatus>()) } answers
        {
          firstArg<UpdateRunnerStatus>().response = "Running"
        }

    val runnerStarted =
        runnerApiService.getRunner(organizationSaved.id, workspaceSaved.id, runnerSaved.id)

    assertEquals(expectedRunId, runnerStarted.lastRunInfo.lastRunId)
    assertEquals(LastRunInfo.LastRunStatus.Running, runnerStarted.lastRunInfo.lastRunStatus)
  }

  @Test
  fun `test getRunner when runner has been stopped`() {

    val expectedRunId = "run-genid12345"

    every { eventPublisher.publishEvent(any()) } answers
        {
          firstArg<RunStart>().response = expectedRunId
        } andThenAnswer
        {
          // This line points to the fact that the run status is not final, so the getRunner trigger
          // an update
          // for lastRunInfo
          firstArg<UpdateRunnerStatus>().response = "Running"
        } andThenAnswer
        {
          // Mock the RunStop event
        } andThenAnswer
        {
          // Simulate the workflow's stop and the runner.lastInfo update after workflow's stop
          // For the getRunner Called at the end on the test
          firstArg<UpdateRunnerStatus>().response = "Failed"
        }

    val run = runnerApiService.startRun(organizationSaved.id, workspaceSaved.id, runnerSaved.id)
    assertEquals(expectedRunId, run.id)

    runnerApiService.stopRun(organizationSaved.id, workspaceSaved.id, runnerSaved.id)

    val runnerStarted =
        runnerApiService.getRunner(organizationSaved.id, workspaceSaved.id, runnerSaved.id)

    assertEquals(expectedRunId, runnerStarted.lastRunInfo.lastRunId)
    assertEquals(LastRunInfo.LastRunStatus.Failed, runnerStarted.lastRunInfo.lastRunStatus)
  }

  @Test
  fun `test to stop a runner when is already finished but lastRunInfo is not updated to Successful`() {

    val expectedRunId = "run-genid12345"

    every { eventPublisher.publishEvent(any()) } answers
        {
          firstArg<RunStart>().response = expectedRunId
        } andThenAnswer
        {
          // This line points to the fact that the run status is not final, so the getRunner trigger
          // an update
          // for lastRunInfo
          firstArg<UpdateRunnerStatus>().response = "Successful"
        }

    val run = runnerApiService.startRun(organizationSaved.id, workspaceSaved.id, runnerSaved.id)
    assertEquals(expectedRunId, run.id)

    assertDoesNotThrow {
      runnerApiService.stopRun(organizationSaved.id, workspaceSaved.id, runnerSaved.id)
    }
  }

  @Test
  fun `test to stop a runner when is already finished but lastRunInfo is not updated to Failed`() {

    val expectedRunId = "run-genid12345"

    every { eventPublisher.publishEvent(any()) } answers
        {
          firstArg<RunStart>().response = expectedRunId
        } andThenAnswer
        {
          // This line points the fact that the run status is not final so the getRunner trigger an
          // update
          // for lastRunInfo
          firstArg<UpdateRunnerStatus>().response = "Failed"
        }

    val run = runnerApiService.startRun(organizationSaved.id, workspaceSaved.id, runnerSaved.id)
    assertEquals(expectedRunId, run.id)

    assertDoesNotThrow {
      runnerApiService.stopRun(organizationSaved.id, workspaceSaved.id, runnerSaved.id)
    }
  }

  @Test
  fun `As a viewer, I can only see my information in security property for getRunner`() {
    every { getCurrentAccountIdentifier(any()) } returns defaultName
    runner = makeRunnerCreateRequest(userName = defaultName, role = ROLE_VIEWER)
    runnerSaved = runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)

    runnerSaved =
        runnerApiService.getRunner(organizationSaved.id, workspaceSaved.id, runnerSaved.id)
    assertEquals(
        RunnerSecurity(
            default = ROLE_NONE,
            mutableListOf(RunnerAccessControl(defaultName, ROLE_VIEWER)),
        ),
        runnerSaved.security,
    )
    assertEquals(1, runnerSaved.security.accessControlList.size)
  }

  @Test
  fun `As a viewer, I can only see my information in security property for listRunners`() {
    every { getCurrentAccountIdentifier(any()) } returns defaultName
    organizationSaved = organizationApiService.createOrganization(organization)
    solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
    workspace = makeWorkspaceCreateRequest()
    workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id, workspace)
    runner = makeRunnerCreateRequest(userName = defaultName, role = ROLE_VIEWER)
    runnerSaved = runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)

    val runners = runnerApiService.listRunners(organizationSaved.id, workspaceSaved.id, null, null)
    runners.forEach {
      assertEquals(
          RunnerSecurity(
              default = ROLE_NONE,
              mutableListOf(RunnerAccessControl(defaultName, ROLE_VIEWER)),
          ),
          it.security,
      )
      assertEquals(1, it.security.accessControlList.size)
    }
  }

  @Test
  fun `As a validator, I can see whole security property for getRunner`() {
    every { getCurrentAccountIdentifier(any()) } returns defaultName
    runner = makeRunnerCreateRequest(userName = defaultName, role = ROLE_VALIDATOR)
    runnerSaved = runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)

    runnerSaved =
        runnerApiService.getRunner(organizationSaved.id, workspaceSaved.id, runnerSaved.id)

    assertEquals(2, runnerSaved.security.accessControlList.size)
    assertEquals(ROLE_NONE, runnerSaved.security.default)
    assertEquals(
        RunnerAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
        runnerSaved.security.accessControlList[0],
    )
    assertEquals(
        RunnerAccessControl(defaultName, ROLE_VALIDATOR),
        runnerSaved.security.accessControlList[1],
    )
  }

  @Test
  fun `As a validator, I can see whole security property for listRunners`() {
    every { getCurrentAccountIdentifier(any()) } returns defaultName
    organizationSaved = organizationApiService.createOrganization(organization)
    solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)
    workspace = makeWorkspaceCreateRequest()
    workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id, workspace)
    runner = makeRunnerCreateRequest(userName = defaultName, role = ROLE_VALIDATOR)
    runnerSaved = runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runner)

    val runners = runnerApiService.listRunners(organizationSaved.id, workspaceSaved.id, null, null)
    runners.forEach {
      assertEquals(2, it.security.accessControlList.size)
      assertEquals(ROLE_NONE, it.security.default)
      assertEquals(
          RunnerAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
          it.security.accessControlList[0],
      )
      assertEquals(
          RunnerAccessControl(defaultName, ROLE_VALIDATOR),
          it.security.accessControlList[1],
      )
    }
  }

  @Test
  fun `assert timestamps are functional for base CRUD`() {
    runnerSaved =
        runnerApiService.createRunner(
            organizationSaved.id,
            workspaceSaved.id,
            makeRunnerCreateRequest(),
        )
    assertTrue(runnerSaved.createInfo.timestamp > startTime)
    assertEquals(runnerSaved.createInfo, runnerSaved.updateInfo)

    val updateTime = Instant.now().toEpochMilli()
    val runnerUpdated =
        runnerApiService.updateRunner(
            organizationSaved.id,
            workspaceSaved.id,
            runnerSaved.id,
            RunnerUpdateRequest("runnerUpdated"),
        )

    assertTrue { updateTime < runnerUpdated.updateInfo.timestamp }
    assertEquals(runnerSaved.createInfo, runnerUpdated.createInfo)
    assertTrue { runnerSaved.createInfo.timestamp < runnerUpdated.updateInfo.timestamp }
    assertTrue { runnerSaved.updateInfo.timestamp < runnerUpdated.updateInfo.timestamp }

    val runnerFetched =
        runnerApiService.getRunner(organizationSaved.id, workspaceSaved.id, runnerSaved.id)

    assertEquals(runnerUpdated.createInfo, runnerFetched.createInfo)
    assertEquals(runnerUpdated.updateInfo, runnerFetched.updateInfo)
  }

  @Test
  fun `assert timestamps are functional for RBAC CRUD`() {
    runnerSaved =
        runnerApiService.createRunner(
            organizationSaved.id,
            workspaceSaved.id,
            makeRunnerCreateRequest(),
        )
    runnerApiService.createRunnerAccessControl(
        organizationSaved.id,
        workspaceSaved.id,
        runnerSaved.id,
        RunnerAccessControl("newUser", ROLE_VIEWER),
    )
    val rbacAdded =
        runnerApiService.getRunner(organizationSaved.id, workspaceSaved.id, runnerSaved.id)

    assertEquals(runnerSaved.createInfo, rbacAdded.createInfo)
    assertTrue { runnerSaved.updateInfo.timestamp < rbacAdded.updateInfo.timestamp }

    runnerApiService.getRunnerAccessControl(
        organizationSaved.id,
        workspaceSaved.id,
        runnerSaved.id,
        "newUser",
    )
    val rbacFetched =
        runnerApiService.getRunner(organizationSaved.id, workspaceSaved.id, runnerSaved.id)

    assertEquals(rbacAdded.createInfo, rbacFetched.createInfo)
    assertEquals(rbacAdded.updateInfo, rbacFetched.updateInfo)

    runnerApiService.updateRunnerAccessControl(
        organizationSaved.id,
        workspaceSaved.id,
        runnerSaved.id,
        "newUser",
        RunnerRole(ROLE_VIEWER),
    )
    val rbacUpdated =
        runnerApiService.getRunner(organizationSaved.id, workspaceSaved.id, runnerSaved.id)

    assertEquals(rbacFetched.createInfo, rbacUpdated.createInfo)
    assertTrue { rbacFetched.updateInfo.timestamp < rbacUpdated.updateInfo.timestamp }

    runnerApiService.deleteRunnerAccessControl(
        organizationSaved.id,
        workspaceSaved.id,
        runnerSaved.id,
        "newUser",
    )
    val rbacDeleted =
        runnerApiService.getRunner(organizationSaved.id, workspaceSaved.id, runnerSaved.id)

    assertEquals(rbacUpdated.createInfo, rbacDeleted.createInfo)
    assertTrue { rbacUpdated.updateInfo.timestamp < rbacDeleted.updateInfo.timestamp }
  }

  @Test
  fun `test runner creation based on solution with dataset's type parameters`() {

    // 1 - Create a solution
    val solutionCreateRequestWithDatasetParameters =
        SolutionCreateRequest(
            key = UUID.randomUUID().toString(),
            name = "My solution with dataset parameter",
            parameterGroups =
                mutableListOf(
                    RunTemplateParameterGroupCreateRequest(
                        id = "testParameterGroups",
                        parameters = mutableListOf("my_property_name"),
                    )
                ),
            parameters =
                mutableListOf(
                    RunTemplateParameterCreateRequest(
                        id = "my_property_name",
                        defaultValue = "ignored_default_value",
                        varType = DATASET_PART_VARTYPE_FILE,
                    )
                ),
            runTemplates =
                mutableListOf(
                    RunTemplateCreateRequest(
                        id = "runTemplateId",
                        parameterGroups = mutableListOf("testParameterGroups"),
                    )
                ),
            repository = "repository",
            version = "1.0.0",
        )
    solutionSaved =
        solutionApiService.createSolution(
            organizationSaved.id,
            solutionCreateRequestWithDatasetParameters,
        )

    // 2 - Create a workspace
    workspace = makeWorkspaceCreateRequest("Workspace")

    workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id, workspace)

    // 3- Create default workspace's dataset parameter

    val resourceTestFile = resourceLoader.getResource("classpath:/$CUSTOMERS_FILE_NAME").file
    val input = FileInputStream(resourceTestFile)
    val multipartFile =
        MockMultipartFile("file", CUSTOMERS_FILE_NAME, "text/csv", IOUtils.toByteArray(input))
    val workspaceDataset =
        datasetApiService.createDataset(
            organizationSaved.id,
            workspaceSaved.id,
            DatasetCreateRequest(
                name = "Dataset workspace",
                description = "Dataset used for default parameter in ${workspaceSaved.id}",
                parts =
                    mutableListOf(
                        DatasetPartCreateRequest(
                            name = "my_property_name",
                            sourceName = CUSTOMERS_FILE_NAME,
                            type = DatasetPartTypeEnum.File,
                        )
                    ),
            ),
            arrayOf(multipartFile),
        )

    // 4- Create update workspace with default dataset

    workspaceApiService.updateWorkspace(
        organizationSaved.id,
        workspaceSaved.id,
        WorkspaceUpdateRequest(
            solution =
                WorkspaceSolution(
                    solutionId = solutionSaved.id,
                    datasetId = workspaceDataset.id,
                    defaultParameterValues =
                        mutableMapOf("my_property_name" to workspaceDataset.parts[0].id),
                )
        ),
    )

    // 5 - Create a runner with the newly created solution using the runTemplate
    val runnerCreateRequest =
        RunnerCreateRequest(
            name = "Runner with expected dataset parameter",
            solutionId = solutionSaved.id,
            runTemplateId = "runTemplateId",
        )

    val createdRunner =
        runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runnerCreateRequest)

    // 4 - Check runner parameters
    val retrievedRunner =
        runnerApiService.getRunner(organizationSaved.id, workspaceSaved.id, createdRunner.id)
    assertNotNull(retrievedRunner.datasets.parameters)
    assertNotEquals(0, retrievedRunner.datasets.parameters!!.size)

    val datasetParameter =
        datasetApiService.getDataset(
            organizationSaved.id,
            workspaceSaved.id,
            retrievedRunner.datasets.parameter,
        )

    assertEquals(datasetParameter.parts.size, retrievedRunner.datasets.parameters!!.size)

    // 4 - Check DatasetPart content on runner
    val runnerDatasetPartId = datasetParameter.parts[0].id

    val downloadDatasetPart =
        datasetApiService.downloadDatasetPart(
            organizationSaved.id,
            workspaceSaved.id,
            retrievedRunner.datasets.parameter,
            runnerDatasetPartId,
        )

    val expectedText = FileInputStream(resourceTestFile).bufferedReader().use { it.readText() }
    val retrievedText =
        InputStreamResource(downloadDatasetPart).inputStream.bufferedReader().use { it.readText() }

    assertEquals(expectedText, retrievedText)
  }

  @Test
  fun `test runner creation based on solution without dataset's type parameters`() {

    // 1 - Create a solution with dataset parameter reference
    val simpleParameterId = "my_property_name"
    val simpleParameterDefaultValue = "my_default_value"
    val simpleParameterVarType = "string"
    val solutionCreateRequestWithDatasetParameters =
        SolutionCreateRequest(
            key = UUID.randomUUID().toString(),
            name = "My solution with dataset parameter",
            parameterGroups =
                mutableListOf(
                    RunTemplateParameterGroupCreateRequest(
                        id = "testParameterGroups",
                        parameters = mutableListOf(simpleParameterId),
                    )
                ),
            parameters =
                mutableListOf(
                    RunTemplateParameterCreateRequest(
                        id = simpleParameterId,
                        defaultValue = simpleParameterDefaultValue,
                        varType = simpleParameterVarType,
                    )
                ),
            runTemplates =
                mutableListOf(
                    RunTemplateCreateRequest(
                        id = "runTemplateId",
                        parameterGroups = mutableListOf("testParameterGroups"),
                    )
                ),
            repository = "repository",
            version = "1.0.0",
        )
    solutionSaved =
        solutionApiService.createSolution(
            organizationSaved.id,
            solutionCreateRequestWithDatasetParameters,
        )

    workspace = makeWorkspaceCreateRequest("Workspace")
    workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id, workspace)

    // 2 - Create a runner with the newly created solution using the runTemplate
    val runnerCreateRequest =
        RunnerCreateRequest(
            name = "Runner with expected dataset parameter",
            solutionId = solutionSaved.id,
            runTemplateId = "runTemplateId",
        )

    val createdRunner =
        runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runnerCreateRequest)

    // 3 - Check DatasetPart content on runner
    val retrievedRunner =
        runnerApiService.getRunner(organizationSaved.id, workspaceSaved.id, createdRunner.id)
    assertNotNull(retrievedRunner.datasets.parameters)
    assertEquals(0, retrievedRunner.datasets.parameters!!.size)

    val datasetParameter =
        datasetApiService.getDataset(
            organizationSaved.id,
            workspaceSaved.id,
            retrievedRunner.datasets.parameter,
        )

    assertEquals(0, datasetParameter.parts.size)

    // 4 - Check runner parameters

    val runnerParametersValues = retrievedRunner.parametersValues.toList()
    assertEquals(1, runnerParametersValues.size)
    val runnerParameterValue = runnerParametersValues[0]
    assertEquals(simpleParameterId, runnerParameterValue.parameterId)
    assertEquals(simpleParameterDefaultValue, runnerParameterValue.value)
    assertEquals(simpleParameterVarType, runnerParameterValue.varType)
  }

  @Test
  fun `test runner creation based on solution without dataset's type parameters with overrided parameter value on workspace`() {

    // 1 - Create a solution with simple solution parameter
    val simpleParameterId = "my_property_name"
    val simpleParameterDefaultValue = "my_default_value"
    val simpleParameterVarType = "string"
    val solutionCreateRequestWithDatasetParameters =
        SolutionCreateRequest(
            key = UUID.randomUUID().toString(),
            name = "My solution with dataset parameter",
            parameterGroups =
                mutableListOf(
                    RunTemplateParameterGroupCreateRequest(
                        id = "testParameterGroups",
                        parameters = mutableListOf(simpleParameterId),
                    )
                ),
            parameters =
                mutableListOf(
                    RunTemplateParameterCreateRequest(
                        id = simpleParameterId,
                        defaultValue = simpleParameterDefaultValue,
                        varType = simpleParameterVarType,
                    )
                ),
            runTemplates =
                mutableListOf(
                    RunTemplateCreateRequest(
                        id = "runTemplateId",
                        parameterGroups = mutableListOf("testParameterGroups"),
                    )
                ),
            repository = "repository",
            version = "1.0.0",
        )
    solutionSaved =
        solutionApiService.createSolution(
            organizationSaved.id,
            solutionCreateRequestWithDatasetParameters,
        )

    val simpleParameterOverridedValue = "my_override_value"
    workspace =
        makeWorkspaceCreateRequest(
            name = "Workspace",
            workspaceSolution =
                WorkspaceSolution(
                    solutionId = solutionSaved.id,
                    defaultParameterValues =
                        mutableMapOf(simpleParameterId to simpleParameterOverridedValue),
                ),
        )
    workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id, workspace)

    // 2 - Create a runner with the newly created solution using the runTemplate
    val runnerCreateRequest =
        RunnerCreateRequest(
            name = "Runner with expected dataset parameter",
            solutionId = solutionSaved.id,
            runTemplateId = "runTemplateId",
        )

    val createdRunner =
        runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, runnerCreateRequest)

    // 3 - Check DatasetPart content on runner
    val retrievedRunner =
        runnerApiService.getRunner(organizationSaved.id, workspaceSaved.id, createdRunner.id)
    assertNotNull(retrievedRunner.datasets.parameters)
    assertEquals(0, retrievedRunner.datasets.parameters!!.size)

    val datasetParameter =
        datasetApiService.getDataset(
            organizationSaved.id,
            workspaceSaved.id,
            retrievedRunner.datasets.parameter,
        )

    assertEquals(0, datasetParameter.parts.size)

    // 4 - Check runner parameters

    val runnerParametersValues = retrievedRunner.parametersValues.toList()
    assertEquals(1, runnerParametersValues.size)
    val runnerParameterValue = runnerParametersValues[0]
    assertEquals(simpleParameterId, runnerParameterValue.parameterId)
    assertEquals(simpleParameterOverridedValue, runnerParameterValue.value)
    assertEquals(simpleParameterVarType, runnerParameterValue.varType)
  }

  @Test
  fun `test runner creation with parent and solution with only dataset's type parameters`() {

    // 1 - Create a solution with dataset parameter reference
    val solutionCreateRequestWithDatasetParameters =
        SolutionCreateRequest(
            key = UUID.randomUUID().toString(),
            name = "My solution with dataset parameter",
            parameterGroups =
                mutableListOf(
                    RunTemplateParameterGroupCreateRequest(
                        id = "testParameterGroups",
                        parameters = mutableListOf("my_property_name"),
                    )
                ),
            parameters =
                mutableListOf(
                    RunTemplateParameterCreateRequest(
                        id = "my_property_name",
                        varType = DATASET_PART_VARTYPE_FILE,
                    )
                ),
            runTemplates =
                mutableListOf(
                    RunTemplateCreateRequest(
                        id = "runTemplateId",
                        parameterGroups = mutableListOf("testParameterGroups"),
                    )
                ),
            repository = "repository",
            version = "1.0.0",
        )
    solutionSaved =
        solutionApiService.createSolution(
            organizationSaved.id,
            solutionCreateRequestWithDatasetParameters,
        )

    // 2 - Create a workspace

    workspace = makeWorkspaceCreateRequest("Workspace")
    workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id, workspace)

    // 3- Create default workspace's dataset parameter

    val resourceTestFile = resourceLoader.getResource("classpath:/$CUSTOMERS_FILE_NAME").file
    val input = FileInputStream(resourceTestFile)
    val multipartFile =
        MockMultipartFile("file", CUSTOMERS_FILE_NAME, "text/csv", IOUtils.toByteArray(input))
    val workspaceDataset =
        datasetApiService.createDataset(
            organizationSaved.id,
            workspaceSaved.id,
            DatasetCreateRequest(
                name = "Dataset workspace",
                description = "Dataset used for default parameter in ${workspaceSaved.id}",
                parts =
                    mutableListOf(
                        DatasetPartCreateRequest(
                            name = "my_property_name",
                            sourceName = CUSTOMERS_FILE_NAME,
                            type = DatasetPartTypeEnum.File,
                        )
                    ),
            ),
            arrayOf(multipartFile),
        )
    // 4- Create update workspace with default dataset

    workspaceApiService.updateWorkspace(
        organizationSaved.id,
        workspaceSaved.id,
        WorkspaceUpdateRequest(
            solution =
                WorkspaceSolution(
                    solutionId = solutionSaved.id,
                    datasetId = workspaceDataset.id,
                    defaultParameterValues =
                        mutableMapOf("my_property_name" to workspaceDataset.parts[0].id),
                )
        ),
    )

    // 5 - Create a parent runner with the newly created solution using the runTemplate with no
    // value
    val parentRunnerCreateRequest =
        RunnerCreateRequest(
            name = "Parent Runner with expected dataset parameter",
            solutionId = solutionSaved.id,
            runTemplateId = "runTemplateId",
        )

    val parentCreatedRunner =
        runnerApiService.createRunner(
            organizationSaved.id,
            workspaceSaved.id,
            parentRunnerCreateRequest,
        )

    // 4 - Check parent runner parameters
    val retrievedParentRunner =
        runnerApiService.getRunner(organizationSaved.id, workspaceSaved.id, parentCreatedRunner.id)
    assertNotNull(retrievedParentRunner.datasets.parameters)
    assertEquals(1, retrievedParentRunner.datasets.parameters!!.size)

    val datasetParameterFromParentRunner =
        datasetApiService.getDataset(
            organizationSaved.id,
            workspaceSaved.id,
            retrievedParentRunner.datasets.parameter,
        )

    assertEquals(1, datasetParameterFromParentRunner.parts.size)

    // 5 - Create a child runner
    val childRunnerCreateRequest =
        RunnerCreateRequest(
            name = "Child Runner with expected dataset parameter",
            solutionId = solutionSaved.id,
            runTemplateId = "runTemplateId",
            parentId = parentCreatedRunner.id,
        )

    val childCreatedRunner =
        runnerApiService.createRunner(
            organizationSaved.id,
            workspaceSaved.id,
            childRunnerCreateRequest,
        )

    // 6 - Check child runner parameters
    val retrievedChildRunner =
        runnerApiService.getRunner(organizationSaved.id, workspaceSaved.id, childCreatedRunner.id)
    assertNotNull(retrievedChildRunner.datasets.parameters)
    assertEquals(1, retrievedChildRunner.datasets.parameters!!.size)
    assertNotEquals(
        retrievedParentRunner.datasets.parameter,
        retrievedChildRunner.datasets.parameter,
    )

    val datasetParameterFromChildRunner =
        datasetApiService.getDataset(
            organizationSaved.id,
            workspaceSaved.id,
            retrievedChildRunner.datasets.parameter,
        )

    assertEquals(
        datasetParameterFromChildRunner.parts,
        retrievedChildRunner.datasets.parameters as MutableList<DatasetPart>,
    )

    assertNotEquals(
        datasetParameterFromChildRunner.parts,
        retrievedParentRunner.datasets.parameters as MutableList<DatasetPart>,
    )

    // 7 - Check DatasetPart content on child runner
    val childRunnerDatasetPartId = datasetParameterFromChildRunner.parts[0].id

    val downloadChildRunnerDatasetPart =
        datasetApiService.downloadDatasetPart(
            organizationSaved.id,
            workspaceSaved.id,
            retrievedChildRunner.datasets.parameter,
            childRunnerDatasetPartId,
        )

    val parentRunnerDatasetPartId = datasetParameterFromParentRunner.parts[0].id

    val downloadParentRunnerDatasetPart =
        datasetApiService.downloadDatasetPart(
            organizationSaved.id,
            workspaceSaved.id,
            retrievedParentRunner.datasets.parameter,
            parentRunnerDatasetPartId,
        )

    val expectedText = FileInputStream(resourceTestFile).bufferedReader().use { it.readText() }

    val retrievedTextFromChildRunner =
        InputStreamResource(downloadChildRunnerDatasetPart).inputStream.bufferedReader().use {
          it.readText()
        }
    val retrievedTextFromParentRunner =
        InputStreamResource(downloadParentRunnerDatasetPart).inputStream.bufferedReader().use {
          it.readText()
        }

    assertEquals(expectedText, retrievedTextFromChildRunner)
    assertEquals(expectedText, retrievedTextFromParentRunner)
  }

  @Test
  fun `test runner creation with parent and solution with dataset's type and normal parameters`() {

    // 1 - Create a solution with dataset parameter reference
    val parameterStringId = "my_property_name"
    val parameterStringDefaultValue = "This is my default name"
    val parameterStringVarType = "string"
    val parameterFileId = "my_property_file"
    val solutionCreateRequestWithDatasetParameters =
        SolutionCreateRequest(
            key = UUID.randomUUID().toString(),
            name = "My solution with dataset parameter",
            parameterGroups =
                mutableListOf(
                    RunTemplateParameterGroupCreateRequest(
                        id = "testParameterGroups",
                        parameters = mutableListOf(parameterFileId, parameterStringId),
                    )
                ),
            parameters =
                mutableListOf(
                    RunTemplateParameterCreateRequest(
                        id = parameterFileId,
                        varType = DATASET_PART_VARTYPE_FILE,
                    ),
                    RunTemplateParameterCreateRequest(
                        id = parameterStringId,
                        defaultValue = parameterStringDefaultValue,
                        varType = parameterStringVarType,
                    ),
                ),
            runTemplates =
                mutableListOf(
                    RunTemplateCreateRequest(
                        id = "runTemplateId",
                        parameterGroups = mutableListOf("testParameterGroups"),
                    )
                ),
            repository = "repository",
            version = "1.0.0",
        )
    solutionSaved =
        solutionApiService.createSolution(
            organizationSaved.id,
            solutionCreateRequestWithDatasetParameters,
        )

    workspace = makeWorkspaceCreateRequest("Workspace")
    workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id, workspace)

    // 2- Create default workspace's dataset parameter

    val resourceTestFile = resourceLoader.getResource("classpath:/$CUSTOMERS_FILE_NAME").file
    val input = FileInputStream(resourceTestFile)
    val multipartFile =
        MockMultipartFile("file", CUSTOMERS_FILE_NAME, "text/csv", IOUtils.toByteArray(input))
    val workspaceDataset =
        datasetApiService.createDataset(
            organizationSaved.id,
            workspaceSaved.id,
            DatasetCreateRequest(
                name = "Dataset workspace",
                description = "Dataset used for default parameter in ${workspaceSaved.id}",
                parts =
                    mutableListOf(
                        DatasetPartCreateRequest(
                            name = parameterFileId,
                            sourceName = CUSTOMERS_FILE_NAME,
                            type = DatasetPartTypeEnum.File,
                        )
                    ),
            ),
            arrayOf(multipartFile),
        )

    // 3- Create update workspace with default dataset

    workspaceApiService.updateWorkspace(
        organizationSaved.id,
        workspaceSaved.id,
        WorkspaceUpdateRequest(
            solution =
                WorkspaceSolution(
                    solutionId = solutionSaved.id,
                    datasetId = workspaceDataset.id,
                    defaultParameterValues =
                        mutableMapOf(parameterFileId to workspaceDataset.parts[0].id),
                )
        ),
    )

    // 4 - Create a parent runner with the newly created solution using the runTemplate
    val parentRunnerCreateRequest =
        RunnerCreateRequest(
            name = "Parent Runner with expected dataset parameter",
            solutionId = solutionSaved.id,
            runTemplateId = "runTemplateId",
        )

    val parentCreatedRunner =
        runnerApiService.createRunner(
            organizationSaved.id,
            workspaceSaved.id,
            parentRunnerCreateRequest,
        )

    // 5 - Check parent runner parameters
    val retrievedParentRunner =
        runnerApiService.getRunner(organizationSaved.id, workspaceSaved.id, parentCreatedRunner.id)
    assertEquals(1, retrievedParentRunner.parametersValues.size)
    val parentRunnerParameterValue = retrievedParentRunner.parametersValues[0]
    assertEquals(parameterStringId, parentRunnerParameterValue.parameterId)
    assertEquals(parameterStringDefaultValue, parentRunnerParameterValue.value)
    assertEquals(parameterStringVarType, parentRunnerParameterValue.varType)
    assertNotNull(retrievedParentRunner.datasets.parameters)
    assertNotEquals(0, retrievedParentRunner.datasets.parameters!!.size)

    val datasetParameterFromParentRunner =
        datasetApiService.getDataset(
            organizationSaved.id,
            workspaceSaved.id,
            retrievedParentRunner.datasets.parameter,
        )

    assertEquals(
        datasetParameterFromParentRunner.parts.size,
        retrievedParentRunner.datasets.parameters!!.size,
    )
    assertEquals(
        datasetParameterFromParentRunner.parts,
        retrievedParentRunner.datasets.parameters!! as MutableList<DatasetPart>,
    )

    // 6 - Create a child runner
    val childRunnerCreateRequest =
        RunnerCreateRequest(
            name = "Child Runner with expected dataset parameter",
            solutionId = solutionSaved.id,
            runTemplateId = "runTemplateId",
            parentId = parentCreatedRunner.id,
        )

    val childCreatedRunner =
        runnerApiService.createRunner(
            organizationSaved.id,
            workspaceSaved.id,
            childRunnerCreateRequest,
        )

    // 7 - Check child runner parameters
    val retrievedChildRunner =
        runnerApiService.getRunner(organizationSaved.id, workspaceSaved.id, childCreatedRunner.id)
    assertNotNull(retrievedChildRunner.datasets.parameters)
    assertEquals(1, retrievedChildRunner.datasets.parameters!!.size)
    assertNotEquals(
        retrievedParentRunner.datasets.parameter,
        retrievedChildRunner.datasets.parameter,
    )
    assertEquals(1, retrievedChildRunner.parametersValues.size)
    val childRunnerParameterValue = retrievedChildRunner.parametersValues[0]
    assertEquals(parameterStringId, childRunnerParameterValue.parameterId)
    assertEquals(parameterStringDefaultValue, childRunnerParameterValue.value)
    assertEquals(parameterStringVarType, childRunnerParameterValue.varType)

    val datasetParameterFromChildRunner =
        datasetApiService.getDataset(
            organizationSaved.id,
            workspaceSaved.id,
            retrievedChildRunner.datasets.parameter,
        )

    assertEquals(
        datasetParameterFromChildRunner.parts,
        retrievedChildRunner.datasets.parameters as MutableList<DatasetPart>,
    )

    assertNotEquals(
        datasetParameterFromChildRunner.parts,
        retrievedParentRunner.datasets.parameters as MutableList<DatasetPart>,
    )
  }

  @Test
  fun `test runner creation with parent and solution with neither dataset's type nor normal parameters`() {

    // 1 - Create a solution with dataset parameter reference
    val solutionCreateRequestWithDatasetParameters =
        SolutionCreateRequest(
            key = UUID.randomUUID().toString(),
            name = "My solution with dataset parameter",
            repository = "repository",
            version = "1.0.0",
            runTemplates =
                mutableListOf(
                    RunTemplateCreateRequest(
                        id = "runTemplateId",
                        parameterGroups = mutableListOf(),
                    )
                ),
        )
    solutionSaved =
        solutionApiService.createSolution(
            organizationSaved.id,
            solutionCreateRequestWithDatasetParameters,
        )

    workspace = makeWorkspaceCreateRequest("Workspace")
    workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id, workspace)

    // 2 - Create a parent runner with the newly created solution using the runTemplate
    val parentRunnerCreateRequest =
        RunnerCreateRequest(
            name = "Parent Runner with expected dataset parameter",
            solutionId = solutionSaved.id,
            runTemplateId = "runTemplateId",
        )

    val parentCreatedRunner =
        runnerApiService.createRunner(
            organizationSaved.id,
            workspaceSaved.id,
            parentRunnerCreateRequest,
        )

    // 4 - Check parent runner parameters
    val retrievedParentRunner =
        runnerApiService.getRunner(organizationSaved.id, workspaceSaved.id, parentCreatedRunner.id)
    assertEquals(0, retrievedParentRunner.parametersValues.size)
    assertNotNull(retrievedParentRunner.datasets.parameters)
    assertEquals(0, retrievedParentRunner.datasets.parameters!!.size)

    val datasetParameterFromParentRunner =
        datasetApiService.getDataset(
            organizationSaved.id,
            workspaceSaved.id,
            retrievedParentRunner.datasets.parameter,
        )

    assertEquals(0, datasetParameterFromParentRunner.parts.size)
    assertEquals(0, retrievedParentRunner.datasets.parameters!!.size)

    // 5 - Create a child runner
    val childRunnerCreateRequest =
        RunnerCreateRequest(
            name = "Child Runner with expected dataset parameter",
            solutionId = solutionSaved.id,
            runTemplateId = "runTemplateId",
            parentId = parentCreatedRunner.id,
        )

    val childCreatedRunner =
        runnerApiService.createRunner(
            organizationSaved.id,
            workspaceSaved.id,
            childRunnerCreateRequest,
        )

    // 6 - Check child runner parameters
    val retrievedChildRunner =
        runnerApiService.getRunner(organizationSaved.id, workspaceSaved.id, childCreatedRunner.id)
    assertNotNull(retrievedChildRunner.datasets.parameters)
    assertEquals(0, retrievedChildRunner.datasets.parameters!!.size)
    assertNotEquals(
        retrievedParentRunner.datasets.parameter,
        retrievedChildRunner.datasets.parameter,
    )
    assertEquals(0, retrievedChildRunner.parametersValues.size)

    val datasetParameterFromChildRunner =
        datasetApiService.getDataset(
            organizationSaved.id,
            workspaceSaved.id,
            retrievedChildRunner.datasets.parameter,
        )

    assertEquals(0, retrievedChildRunner.datasets.parameters!!.size)
    assertEquals(0, datasetParameterFromChildRunner.parts.size)
  }

  @Test
  fun `test onGetRunnerAttachedToDataset behaviour`() {

    logger.info(
        "should create a new Runner and retrieve parameter varType from solution ignoring the one declared"
    )
    val newRunner =
        makeRunnerCreateRequest(
            name = "NewRunner",
            datasetList = mutableListOf(datasetSaved.id),
            parametersValues =
                mutableListOf(
                    RunnerRunTemplateParameterValue(
                        parameterId = "param1",
                        value = "7",
                        varType = "ignored_var_type",
                    )
                ),
        )
    val newRunnerSaved =
        runnerApiService.createRunner(organizationSaved.id, workspaceSaved.id, newRunner)

    assertNotNull(newRunnerSaved)
    assertNotNull(newRunnerSaved.datasets)
    val datasetParameterId = newRunnerSaved.datasets.parameter

    val getAttachedRunnerToDataset =
        GetRunnerAttachedToDataset(
            this,
            organizationSaved.id,
            workspaceSaved.id,
            datasetParameterId,
        )
    eventPublisher.publishEvent(getAttachedRunnerToDataset)

    assertEquals(newRunnerSaved.id, getAttachedRunnerToDataset.response)
  }

  fun makeDataset(
      name: String = "name",
      parts: MutableList<DatasetPartCreateRequest> = mutableListOf(),
  ): DatasetCreateRequest {
    return DatasetCreateRequest(name = name, parts = parts)
  }

  fun makeSolution(organizationId: String = organizationSaved.id): SolutionCreateRequest {
    return SolutionCreateRequest(
        key = UUID.randomUUID().toString(),
        name = "My solution",
        parameterGroups =
            mutableListOf(
                RunTemplateParameterGroupCreateRequest(
                    id = "testParameterGroups",
                    parameters = mutableListOf("param1", "param2"),
                )
            ),
        parameters =
            mutableListOf(
                RunTemplateParameterCreateRequest(
                    id = "param1",
                    maxValue = "10",
                    minValue = "0",
                    defaultValue = "5",
                    varType = "integer",
                ),
                RunTemplateParameterCreateRequest(
                    id = "param2",
                    varType = DATASET_PART_VARTYPE_FILE,
                ),
            ),
        runTemplates =
            mutableListOf(
                RunTemplateCreateRequest(
                    id = "runTemplateId",
                    parameterGroups = mutableListOf("testParameterGroups"),
                )
            ),
        repository = "repository",
        version = "1.0.0",
        security =
            SolutionSecurity(
                default = ROLE_NONE,
                accessControlList =
                    mutableListOf(
                        SolutionAccessControl(id = CONNECTED_ADMIN_USER, role = ROLE_ADMIN),
                        SolutionAccessControl(id = defaultName, role = ROLE_USER),
                    ),
            ),
    )
  }

  fun makeOrganizationCreateRequest(userName: String = defaultName, role: String = ROLE_ADMIN) =
      OrganizationCreateRequest(
          name = "Organization Name",
          security =
              OrganizationSecurity(
                  default = ROLE_NONE,
                  accessControlList =
                      mutableListOf(
                          OrganizationAccessControl(id = CONNECTED_READER_USER, role = "reader"),
                          OrganizationAccessControl(id = CONNECTED_ADMIN_USER, role = "admin"),
                          OrganizationAccessControl(id = userName, role = role),
                      ),
              ),
      )

  fun makeWorkspaceCreateRequest(
      name: String = "name",
      workspaceSolution: WorkspaceSolution = WorkspaceSolution(solutionId = solutionSaved.id),
      userName: String = defaultName,
      role: String = ROLE_ADMIN,
  ) =
      WorkspaceCreateRequest(
          key = UUID.randomUUID().toString(),
          name = name,
          solution = workspaceSolution,
          security =
              WorkspaceSecurity(
                  default = ROLE_NONE,
                  mutableListOf(
                      WorkspaceAccessControl(id = userName, role = role),
                      WorkspaceAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                  ),
              ),
      )

  fun makeRunnerCreateRequest(
      name: String = "name",
      datasetList: MutableList<String>? = mutableListOf(),
      parentId: String? = null,
      userName: String = defaultName,
      role: String = ROLE_USER,
      runTemplateId: String = "runTemplateId",
      parametersValues: MutableList<RunnerRunTemplateParameterValue>? = null,
  ) =
      RunnerCreateRequest(
          name = name,
          runTemplateId = runTemplateId,
          datasetList = datasetList,
          parentId = parentId,
          parametersValues = parametersValues,
          solutionId = solutionSaved.id,
          additionalData =
              mutableMapOf(
                  "you_can_put" to "whatever_you_want_here",
                  "even" to mapOf("object" to "if_you_want"),
              ),
          security =
              RunnerSecurity(
                  ROLE_NONE,
                  mutableListOf(
                      RunnerAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                      RunnerAccessControl(userName, role),
                  ),
              ),
      )
}
