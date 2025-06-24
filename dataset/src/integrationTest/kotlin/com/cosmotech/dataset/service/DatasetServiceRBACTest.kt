// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.service

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
import com.cosmotech.api.tests.CsmTestBase
import com.cosmotech.api.utils.getCurrentAccountIdentifier
import com.cosmotech.api.utils.getCurrentAuthenticatedRoles
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.dataset.DatasetApiServiceInterface
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.dataset.domain.DatasetAccessControl
import com.cosmotech.dataset.domain.DatasetCreateRequest
import com.cosmotech.dataset.domain.DatasetPart
import com.cosmotech.dataset.domain.DatasetPartCreateRequest
import com.cosmotech.dataset.domain.DatasetPartTypeEnum
import com.cosmotech.dataset.domain.DatasetPartUpdateRequest
import com.cosmotech.dataset.domain.DatasetRole
import com.cosmotech.dataset.domain.DatasetSecurity
import com.cosmotech.dataset.domain.DatasetUpdateRequest
import com.cosmotech.organization.OrganizationApiServiceInterface
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationCreateRequest
import com.cosmotech.organization.domain.OrganizationSecurity
import com.cosmotech.solution.SolutionApiServiceInterface
import com.cosmotech.solution.domain.RunTemplateCreateRequest
import com.cosmotech.solution.domain.RunTemplateParameterCreateRequest
import com.cosmotech.solution.domain.RunTemplateParameterGroupCreateRequest
import com.cosmotech.solution.domain.Solution
import com.cosmotech.solution.domain.SolutionAccessControl
import com.cosmotech.solution.domain.SolutionCreateRequest
import com.cosmotech.solution.domain.SolutionSecurity
import com.cosmotech.workspace.WorkspaceApiServiceInterface
import com.cosmotech.workspace.domain.Workspace
import com.cosmotech.workspace.domain.WorkspaceAccessControl
import com.cosmotech.workspace.domain.WorkspaceCreateRequest
import com.cosmotech.workspace.domain.WorkspaceSecurity
import com.cosmotech.workspace.domain.WorkspaceSolution
import com.redis.om.spring.indexing.RediSearchIndexer
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockkStatic
import java.io.FileInputStream
import java.util.UUID
import kotlin.test.Ignore
import kotlin.test.assertEquals
import org.apache.commons.io.IOUtils
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
import org.springframework.core.io.ResourceLoader
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.web.multipart.MultipartFile

@ActiveProfiles(profiles = ["dataset-test"])
@ExtendWith(MockKExtension::class)
@ExtendWith(SpringExtension::class)
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DatasetServiceRBACTest : CsmTestBase() {
  val CONNECTED_ADMIN_USER = "test.admin@cosmotech.com"
  val CONNECTED_DEFAULT_USER = "test.user@cosmotech.com"
  val CUSTOMER_SOURCE_FILE_NAME = "customers.csv"

  private val logger = LoggerFactory.getLogger(DatasetServiceIntegrationTest::class.java)

  @Autowired lateinit var rediSearchIndexer: RediSearchIndexer
  @Autowired lateinit var organizationApiService: OrganizationApiServiceInterface
  @Autowired lateinit var workspaceApiService: WorkspaceApiServiceInterface
  @Autowired lateinit var solutionApiService: SolutionApiServiceInterface
  @Autowired lateinit var csmPlatformProperties: CsmPlatformProperties
  @Autowired lateinit var datasetApiService: DatasetApiServiceInterface
  @Autowired lateinit var resourceLoader: ResourceLoader

  lateinit var organization: OrganizationCreateRequest
  lateinit var workspace: WorkspaceCreateRequest
  lateinit var solution: SolutionCreateRequest
  lateinit var dataset: DatasetCreateRequest
  lateinit var organizationSaved: Organization
  lateinit var workspaceSaved: Workspace
  lateinit var solutionSaved: Solution
  lateinit var datasetSaved: Dataset
  lateinit var mockMultipartFiles: Array<MultipartFile>

  @BeforeEach
  fun setUp() {
    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "test.user"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("user")

    val resourceTestFile = resourceLoader.getResource("classpath:/$CUSTOMER_SOURCE_FILE_NAME").file

    val fileToSend = FileInputStream(resourceTestFile)

    mockMultipartFiles =
        arrayOf(
            MockMultipartFile(
                "files",
                CUSTOMER_SOURCE_FILE_NAME,
                MediaType.MULTIPART_FORM_DATA_VALUE,
                IOUtils.toByteArray(fileToSend)))

    rediSearchIndexer.createIndexFor(Organization::class.java)
    rediSearchIndexer.createIndexFor(Workspace::class.java)
    rediSearchIndexer.createIndexFor(Solution::class.java)
    rediSearchIndexer.createIndexFor(Dataset::class.java)
    rediSearchIndexer.createIndexFor(DatasetPart::class.java)

    organization = makeOrganizationCreateRequest("Organization test")
    organizationSaved = organizationApiService.createOrganization(organization)

    solution = makeSolution()
    solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

    workspace = makeWorkspaceCreateRequest()
    workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id, workspace)

    dataset = makeDatasetCreateRequest()
    datasetSaved =
        datasetApiService.createDataset(
            organizationSaved.id, workspaceSaved.id, dataset, mockMultipartFiles)
  }

  @TestFactory
  fun `test RBAC getDataset`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC getDataset : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_DEFAULT_USER
              val datasetCreated =
                  datasetApiService.createDataset(
                      organizationSaved.id,
                      workspaceSaved.id,
                      makeDatasetCreateRequest(
                          datasetSecurity =
                              DatasetSecurity(
                                  default = ROLE_NONE,
                                  accessControlList =
                                      mutableListOf(
                                          DatasetAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                                          DatasetAccessControl(
                                              id = CONNECTED_DEFAULT_USER, role = role)))),
                      mockMultipartFiles)

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.getDataset(
                          organizationSaved.id, workspaceSaved.id, datasetCreated.id)
                    }
                assertEquals(
                    "RBAC ${datasetCreated.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.getDataset(
                      organizationSaved.id, workspaceSaved.id, datasetCreated.id)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC createDatasetAccessControl`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC createDatasetAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_DEFAULT_USER

              dataset =
                  makeDatasetCreateRequest(
                      datasetSecurity =
                          DatasetSecurity(
                              default = ROLE_NONE,
                              accessControlList =
                                  mutableListOf(
                                      DatasetAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                                      DatasetAccessControl(
                                          id = CONNECTED_DEFAULT_USER, role = role))))
              datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, mockMultipartFiles)

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.createDatasetAccessControl(
                          organizationSaved.id,
                          workspaceSaved.id,
                          datasetSaved.id,
                          DatasetAccessControl("NewUser", role))
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id} - User does not have permission $PERMISSION_WRITE_SECURITY",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.createDatasetAccessControl(
                      organizationSaved.id,
                      workspaceSaved.id,
                      datasetSaved.id,
                      DatasetAccessControl("NewUser", role))
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC searchDatasets`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC searchDatasets : $role") {
              organization =
                  makeOrganizationCreateRequest(name = "Organization test", role = ROLE_USER)
              organizationSaved = organizationApiService.createOrganization(organization)

              solution = makeSolution()
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              workspace = makeWorkspaceCreateRequest(role = role)
              workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_DEFAULT_USER

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.searchDatasets(
                          organizationSaved.id, workspaceSaved.id, listOf(), null, null)
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.searchDatasets(
                      organizationSaved.id, workspaceSaved.id, listOf(), null, null)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC deleteDataset`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC deleteDataset : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_DEFAULT_USER

              dataset =
                  makeDatasetCreateRequest(
                      datasetSecurity =
                          DatasetSecurity(
                              default = ROLE_NONE,
                              accessControlList =
                                  mutableListOf(
                                      DatasetAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                                      DatasetAccessControl(
                                          id = CONNECTED_DEFAULT_USER, role = role))))
              datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, mockMultipartFiles)

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.deleteDataset(
                          organizationSaved.id, workspaceSaved.id, datasetSaved.id)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id} - User does not have permission $PERMISSION_DELETE",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.deleteDataset(
                      organizationSaved.id, workspaceSaved.id, datasetSaved.id)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC deleteDatasetAccessControl`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC deleteDatasetAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_DEFAULT_USER

              dataset =
                  makeDatasetCreateRequest(
                      datasetSecurity =
                          DatasetSecurity(
                              default = ROLE_NONE,
                              accessControlList =
                                  mutableListOf(
                                      DatasetAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                                      DatasetAccessControl(
                                          id = CONNECTED_DEFAULT_USER, role = role))))
              datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, mockMultipartFiles)

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.deleteDatasetAccessControl(
                          organizationSaved.id,
                          workspaceSaved.id,
                          datasetSaved.id,
                          CONNECTED_DEFAULT_USER)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id} - User does not have permission $PERMISSION_WRITE_SECURITY",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.deleteDatasetAccessControl(
                      organizationSaved.id,
                      workspaceSaved.id,
                      datasetSaved.id,
                      CONNECTED_DEFAULT_USER)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC createDataset`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC createDataset : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_DEFAULT_USER

              organization =
                  makeOrganizationCreateRequest(
                      name = "Organization test",
                      userName = CONNECTED_DEFAULT_USER,
                      role = ROLE_USER)
              organizationSaved = organizationApiService.createOrganization(organization)

              solution = makeSolution()
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              workspace = makeWorkspaceCreateRequest(userName = CONNECTED_DEFAULT_USER, role = role)
              workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              dataset =
                  makeDatasetCreateRequest(
                      datasetSecurity =
                          DatasetSecurity(
                              default = ROLE_NONE,
                              accessControlList =
                                  mutableListOf(
                                      DatasetAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                                      DatasetAccessControl(
                                          id = CONNECTED_DEFAULT_USER, role = role))))

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetSaved =
                          datasetApiService.createDataset(
                              organizationSaved.id, workspaceSaved.id, dataset, mockMultipartFiles)
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id} - User does not have permission $PERMISSION_CREATE_CHILDREN",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetSaved =
                      datasetApiService.createDataset(
                          organizationSaved.id, workspaceSaved.id, dataset, mockMultipartFiles)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC getDatasetAccessControl`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC getDatasetAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_DEFAULT_USER

              dataset =
                  makeDatasetCreateRequest(
                      datasetSecurity =
                          DatasetSecurity(
                              default = ROLE_NONE,
                              accessControlList =
                                  mutableListOf(
                                      DatasetAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                                      DatasetAccessControl(
                                          id = CONNECTED_DEFAULT_USER, role = role))))
              datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, mockMultipartFiles)

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.getDatasetAccessControl(
                          organizationSaved.id,
                          workspaceSaved.id,
                          datasetSaved.id,
                          CONNECTED_DEFAULT_USER)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id} - User does not have permission $PERMISSION_READ_SECURITY",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.getDatasetAccessControl(
                      organizationSaved.id,
                      workspaceSaved.id,
                      datasetSaved.id,
                      CONNECTED_DEFAULT_USER)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC listDatasetSecurityUsers`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC listDatasetSecurityUsers : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_DEFAULT_USER

              dataset =
                  makeDatasetCreateRequest(
                      datasetSecurity =
                          DatasetSecurity(
                              default = ROLE_NONE,
                              accessControlList =
                                  mutableListOf(
                                      DatasetAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                                      DatasetAccessControl(
                                          id = CONNECTED_DEFAULT_USER, role = role))))
              datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, mockMultipartFiles)

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.listDatasetSecurityUsers(
                          organizationSaved.id, workspaceSaved.id, datasetSaved.id)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id} - User does not have permission $PERMISSION_READ_SECURITY",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.listDatasetSecurityUsers(
                      organizationSaved.id, workspaceSaved.id, datasetSaved.id)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC listDatasets`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC listDatasets : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_DEFAULT_USER

              organization =
                  makeOrganizationCreateRequest(
                      name = "Organization test",
                      userName = CONNECTED_DEFAULT_USER,
                      role = ROLE_USER)
              organizationSaved = organizationApiService.createOrganization(organization)

              solution = makeSolution()
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              workspace = makeWorkspaceCreateRequest(userName = CONNECTED_DEFAULT_USER, role = role)
              workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.listDatasets(
                          organizationSaved.id, workspaceSaved.id, null, null)
                    }
                assertEquals(
                    "RBAC ${workspaceSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.listDatasets(
                      organizationSaved.id, workspaceSaved.id, null, null)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC updateDataset`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC updateDataset : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_DEFAULT_USER

              organization =
                  makeOrganizationCreateRequest(
                      name = "Organization test",
                      userName = CONNECTED_DEFAULT_USER,
                      role = ROLE_USER)
              organizationSaved = organizationApiService.createOrganization(organization)

              solution = makeSolution()
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              workspace = makeWorkspaceCreateRequest()
              workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              dataset =
                  makeDatasetCreateRequest(
                      datasetSecurity =
                          DatasetSecurity(
                              default = ROLE_NONE,
                              accessControlList =
                                  mutableListOf(
                                      DatasetAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                                      DatasetAccessControl(
                                          id = CONNECTED_DEFAULT_USER, role = role))))

              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
              datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, mockMultipartFiles)

              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_DEFAULT_USER

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.updateDataset(
                          organizationSaved.id,
                          workspaceSaved.id,
                          datasetSaved.id,
                          DatasetUpdateRequest(),
                          arrayOf())
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id} - User does not have permission $PERMISSION_WRITE",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.updateDataset(
                      organizationSaved.id,
                      workspaceSaved.id,
                      datasetSaved.id,
                      DatasetUpdateRequest(),
                      arrayOf())
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC searchDatasetParts`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC searchDatasetParts : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_DEFAULT_USER

              dataset =
                  makeDatasetCreateRequest(
                      datasetSecurity =
                          DatasetSecurity(
                              default = ROLE_NONE,
                              accessControlList =
                                  mutableListOf(
                                      DatasetAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                                      DatasetAccessControl(
                                          id = CONNECTED_DEFAULT_USER, role = role))))
              datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, mockMultipartFiles)

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.searchDatasetParts(
                          organizationSaved.id,
                          workspaceSaved.id,
                          datasetSaved.id,
                          listOf(),
                          null,
                          null)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.searchDatasetParts(
                      organizationSaved.id,
                      workspaceSaved.id,
                      datasetSaved.id,
                      listOf(),
                      null,
                      null)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC updateDatasetAccessControl`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC updateDatasetAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_DEFAULT_USER

              dataset =
                  makeDatasetCreateRequest(
                      datasetSecurity =
                          DatasetSecurity(
                              default = ROLE_NONE,
                              accessControlList =
                                  mutableListOf(
                                      DatasetAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                                      DatasetAccessControl(
                                          id = CONNECTED_DEFAULT_USER, role = role))))
              datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, mockMultipartFiles)

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.updateDatasetAccessControl(
                          organizationSaved.id,
                          workspaceSaved.id,
                          datasetSaved.id,
                          CONNECTED_DEFAULT_USER,
                          DatasetRole(role))
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id} - User does not have permission $PERMISSION_WRITE_SECURITY",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.updateDatasetAccessControl(
                      organizationSaved.id,
                      workspaceSaved.id,
                      datasetSaved.id,
                      CONNECTED_DEFAULT_USER,
                      DatasetRole(role))
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC updateDatasetDefaultSecurity`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC updateDatasetDefaultSecurity : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_DEFAULT_USER

              dataset =
                  makeDatasetCreateRequest(
                      datasetSecurity =
                          DatasetSecurity(
                              default = ROLE_NONE,
                              accessControlList =
                                  mutableListOf(
                                      DatasetAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                                      DatasetAccessControl(
                                          id = CONNECTED_DEFAULT_USER, role = role))))
              datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, mockMultipartFiles)

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.updateDatasetDefaultSecurity(
                          organizationSaved.id,
                          workspaceSaved.id,
                          datasetSaved.id,
                          DatasetRole(role))
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id} - User does not have permission $PERMISSION_WRITE_SECURITY",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.updateDatasetDefaultSecurity(
                      organizationSaved.id, workspaceSaved.id, datasetSaved.id, DatasetRole(role))
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC createDatasetPart`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC createDatasetPart : $role") {
              organization =
                  makeOrganizationCreateRequest(
                      name = "Organization test",
                      userName = CONNECTED_DEFAULT_USER,
                      role = ROLE_USER)
              organizationSaved = organizationApiService.createOrganization(organization)

              solution = makeSolution()
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              workspace =
                  makeWorkspaceCreateRequest(userName = CONNECTED_DEFAULT_USER, role = ROLE_USER)
              workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              dataset =
                  makeDatasetCreateRequest(
                      datasetSecurity =
                          DatasetSecurity(
                              default = ROLE_NONE,
                              accessControlList =
                                  mutableListOf(
                                      DatasetAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                                      DatasetAccessControl(
                                          id = CONNECTED_DEFAULT_USER, role = role))))

              datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, mockMultipartFiles)

              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_DEFAULT_USER

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.createDatasetPart(
                          organizationSaved.id,
                          workspaceSaved.id,
                          datasetSaved.id,
                          mockMultipartFiles[0],
                          makeDatasetPartCreateRequest())
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id} - User does not have permission $PERMISSION_WRITE",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.createDatasetPart(
                      organizationSaved.id,
                      workspaceSaved.id,
                      datasetSaved.id,
                      mockMultipartFiles[0],
                      makeDatasetPartCreateRequest())
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC deleteDatasetPart`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC deleteDatasetPart : $role") {
              organization =
                  makeOrganizationCreateRequest(
                      name = "Organization test",
                      userName = CONNECTED_DEFAULT_USER,
                      role = ROLE_USER)
              organizationSaved = organizationApiService.createOrganization(organization)

              solution = makeSolution()
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              workspace =
                  makeWorkspaceCreateRequest(userName = CONNECTED_DEFAULT_USER, role = ROLE_USER)
              workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              dataset =
                  makeDatasetCreateRequest(
                      datasetSecurity =
                          DatasetSecurity(
                              default = ROLE_NONE,
                              accessControlList =
                                  mutableListOf(
                                      DatasetAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                                      DatasetAccessControl(
                                          id = CONNECTED_DEFAULT_USER, role = role))))

              datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, mockMultipartFiles)

              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_DEFAULT_USER

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.deleteDatasetPart(
                          organizationSaved.id,
                          workspaceSaved.id,
                          datasetSaved.id,
                          datasetSaved.parts[0].id)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id} - User does not have permission $PERMISSION_WRITE",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.deleteDatasetPart(
                      organizationSaved.id,
                      workspaceSaved.id,
                      datasetSaved.id,
                      datasetSaved.parts[0].id)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC downloadDatasetPart`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC downloadDatasetPart : $role") {
              organization =
                  makeOrganizationCreateRequest(
                      name = "Organization test",
                      userName = CONNECTED_DEFAULT_USER,
                      role = ROLE_USER)
              organizationSaved = organizationApiService.createOrganization(organization)

              solution = makeSolution()
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              workspace =
                  makeWorkspaceCreateRequest(userName = CONNECTED_DEFAULT_USER, role = ROLE_USER)
              workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              dataset =
                  makeDatasetCreateRequest(
                      datasetSecurity =
                          DatasetSecurity(
                              default = ROLE_NONE,
                              accessControlList =
                                  mutableListOf(
                                      DatasetAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                                      DatasetAccessControl(
                                          id = CONNECTED_DEFAULT_USER, role = role))))

              datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, mockMultipartFiles)

              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_DEFAULT_USER

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.downloadDatasetPart(
                          organizationSaved.id,
                          workspaceSaved.id,
                          datasetSaved.id,
                          datasetSaved.parts[0].id)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.downloadDatasetPart(
                      organizationSaved.id,
                      workspaceSaved.id,
                      datasetSaved.id,
                      datasetSaved.parts[0].id)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC getDatasetPart`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC getDatasetPart : $role") {
              organization =
                  makeOrganizationCreateRequest(
                      name = "Organization test",
                      userName = CONNECTED_DEFAULT_USER,
                      role = ROLE_USER)
              organizationSaved = organizationApiService.createOrganization(organization)

              solution = makeSolution()
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              workspace =
                  makeWorkspaceCreateRequest(userName = CONNECTED_DEFAULT_USER, role = ROLE_USER)
              workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              dataset =
                  makeDatasetCreateRequest(
                      datasetSecurity =
                          DatasetSecurity(
                              default = ROLE_NONE,
                              accessControlList =
                                  mutableListOf(
                                      DatasetAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                                      DatasetAccessControl(
                                          id = CONNECTED_DEFAULT_USER, role = role))))

              datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, mockMultipartFiles)

              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_DEFAULT_USER

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.getDatasetPart(
                          organizationSaved.id,
                          workspaceSaved.id,
                          datasetSaved.id,
                          datasetSaved.parts[0].id)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.getDatasetPart(
                      organizationSaved.id,
                      workspaceSaved.id,
                      datasetSaved.id,
                      datasetSaved.parts[0].id)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC listDatasetParts`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC listDatasetParts : $role") {
              organization =
                  makeOrganizationCreateRequest(
                      name = "Organization test",
                      userName = CONNECTED_DEFAULT_USER,
                      role = ROLE_USER)
              organizationSaved = organizationApiService.createOrganization(organization)

              solution = makeSolution()
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              workspace =
                  makeWorkspaceCreateRequest(userName = CONNECTED_DEFAULT_USER, role = ROLE_USER)
              workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              dataset =
                  makeDatasetCreateRequest(
                      datasetSecurity =
                          DatasetSecurity(
                              default = ROLE_NONE,
                              accessControlList =
                                  mutableListOf(
                                      DatasetAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                                      DatasetAccessControl(
                                          id = CONNECTED_DEFAULT_USER, role = role))))

              datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, mockMultipartFiles)

              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_DEFAULT_USER

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.listDatasetParts(
                          organizationSaved.id, workspaceSaved.id, datasetSaved.id, null, null)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.listDatasetParts(
                      organizationSaved.id, workspaceSaved.id, datasetSaved.id, null, null)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC replaceDatasetPart`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC replaceDatasetPart : $role") {
              organization =
                  makeOrganizationCreateRequest(
                      name = "Organization test",
                      userName = CONNECTED_DEFAULT_USER,
                      role = ROLE_USER)
              organizationSaved = organizationApiService.createOrganization(organization)

              solution = makeSolution()
              solutionSaved = solutionApiService.createSolution(organizationSaved.id, solution)

              workspace =
                  makeWorkspaceCreateRequest(userName = CONNECTED_DEFAULT_USER, role = ROLE_USER)
              workspaceSaved = workspaceApiService.createWorkspace(organizationSaved.id, workspace)

              dataset =
                  makeDatasetCreateRequest(
                      datasetSecurity =
                          DatasetSecurity(
                              default = ROLE_NONE,
                              accessControlList =
                                  mutableListOf(
                                      DatasetAccessControl(CONNECTED_ADMIN_USER, ROLE_ADMIN),
                                      DatasetAccessControl(
                                          id = CONNECTED_DEFAULT_USER, role = role))))

              datasetSaved =
                  datasetApiService.createDataset(
                      organizationSaved.id, workspaceSaved.id, dataset, mockMultipartFiles)

              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_DEFAULT_USER

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.replaceDatasetPart(
                          organizationSaved.id,
                          workspaceSaved.id,
                          datasetSaved.id,
                          datasetSaved.parts[0].id,
                          mockMultipartFiles[0],
                          makeDatasetPartUpdateRequest())
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id} - User does not have permission $PERMISSION_WRITE",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.replaceDatasetPart(
                      organizationSaved.id,
                      workspaceSaved.id,
                      datasetSaved.id,
                      datasetSaved.parts[0].id,
                      mockMultipartFiles[0],
                      makeDatasetPartUpdateRequest())
                }
              }
            }
          }

  @Ignore("This method is not ready yet")
  @TestFactory
  fun `test RBAC queryData`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { TODO("Not yet implemented") }

  fun makeDatasetPartUpdateRequest(description: String? = null, tags: MutableList<String>? = null) =
      DatasetPartUpdateRequest(description = description, tags = tags)

  fun makeDatasetPartCreateRequest(
      datasetPartName: String = "Test dataset part name",
      datasetPartDescription: String = "Test dataset part description",
      datasetPartTags: MutableList<String> = mutableListOf("part", "public", "test"),
      datasetPartSourceName: String = CUSTOMER_SOURCE_FILE_NAME,
      datasetPartType: DatasetPartTypeEnum = DatasetPartTypeEnum.File
  ): DatasetPartCreateRequest {
    return DatasetPartCreateRequest(
        name = datasetPartName,
        sourceName = datasetPartSourceName,
        description = datasetPartDescription,
        tags = datasetPartTags,
        type = datasetPartType)
  }

  fun makeDatasetCreateRequest(
      datasetName: String = "Test dataset name",
      datasetDescription: String = "Test dataset description",
      datasetTags: MutableList<String> = mutableListOf("public", "test"),
      datasetPartName: String = "Test dataset part name",
      datasetSecurity: DatasetSecurity? = null,
      datasetPartDescription: String = "Test dataset part description",
      datasetPartTags: MutableList<String> = mutableListOf("part", "public", "test"),
      datasetPartSourceName: String = CUSTOMER_SOURCE_FILE_NAME,
      datasetPartType: DatasetPartTypeEnum = DatasetPartTypeEnum.File
  ): DatasetCreateRequest {
    val datasetPartCreateRequest =
        DatasetPartCreateRequest(
            name = datasetPartName,
            sourceName = datasetPartSourceName,
            description = datasetPartDescription,
            tags = datasetPartTags,
            type = datasetPartType)

    return DatasetCreateRequest(
        name = datasetName,
        description = datasetDescription,
        tags = datasetTags,
        parts = mutableListOf(datasetPartCreateRequest),
        security = datasetSecurity)
  }

  fun makeOrganizationCreateRequest(
      name: String = "Organization Name",
      userName: String = CONNECTED_DEFAULT_USER,
      role: String = ROLE_VIEWER
  ) =
      OrganizationCreateRequest(
          name = name,
          security =
              OrganizationSecurity(
                  default = ROLE_NONE,
                  accessControlList =
                      mutableListOf(
                          OrganizationAccessControl(id = CONNECTED_ADMIN_USER, role = ROLE_ADMIN),
                          OrganizationAccessControl(userName, role))))

  fun makeSolution(userName: String = CONNECTED_DEFAULT_USER, role: String = ROLE_USER) =
      SolutionCreateRequest(
          key = UUID.randomUUID().toString(),
          name = "My solution",
          runTemplates = mutableListOf(RunTemplateCreateRequest("template")),
          parameters = mutableListOf(RunTemplateParameterCreateRequest("parameter", "string")),
          parameterGroups = mutableListOf(RunTemplateParameterGroupCreateRequest("group")),
          repository = "repository",
          version = "1.0.0",
          security =
              SolutionSecurity(
                  default = ROLE_NONE,
                  accessControlList =
                      mutableListOf(
                          SolutionAccessControl(id = CONNECTED_ADMIN_USER, role = ROLE_ADMIN),
                          SolutionAccessControl(id = userName, role = role))))

  fun makeWorkspaceCreateRequest(
      solutionId: String = solutionSaved.id,
      name: String = "name",
      userName: String = CONNECTED_DEFAULT_USER,
      role: String = ROLE_USER
  ) =
      WorkspaceCreateRequest(
          key = UUID.randomUUID().toString(),
          name = name,
          solution =
              WorkspaceSolution(
                  solutionId = solutionId,
              ),
          security =
              WorkspaceSecurity(
                  default = ROLE_NONE,
                  accessControlList =
                      mutableListOf(
                          WorkspaceAccessControl(id = userName, role = role),
                          WorkspaceAccessControl(id = CONNECTED_ADMIN_USER, role = ROLE_ADMIN))))
}
