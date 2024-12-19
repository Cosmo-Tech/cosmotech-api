// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
@file:Suppress("DEPRECATION")

package com.cosmotech.dataset.service

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.events.CsmEventPublisher
import com.cosmotech.api.events.TwingraphImportEvent
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
import com.cosmotech.api.utils.bulkQueryKey
import com.cosmotech.api.utils.getCurrentAccountIdentifier
import com.cosmotech.api.utils.getCurrentAuthenticatedRoles
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.connector.ConnectorApiServiceInterface
import com.cosmotech.connector.domain.Connector
import com.cosmotech.dataset.DatasetApiServiceInterface
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.dataset.domain.DatasetAccessControl
import com.cosmotech.dataset.domain.DatasetCompatibility
import com.cosmotech.dataset.domain.DatasetConnector
import com.cosmotech.dataset.domain.DatasetRole
import com.cosmotech.dataset.domain.DatasetSearch
import com.cosmotech.dataset.domain.DatasetSecurity
import com.cosmotech.dataset.domain.DatasetSourceType
import com.cosmotech.dataset.domain.DatasetTwinGraphQuery
import com.cosmotech.dataset.domain.GraphProperties
import com.cosmotech.dataset.domain.IngestionStatusEnum
import com.cosmotech.dataset.domain.SourceInfo
import com.cosmotech.dataset.domain.SubDatasetGraphQuery
import com.cosmotech.dataset.repository.DatasetRepository
import com.cosmotech.organization.OrganizationApiServiceInterface
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationSecurity
import com.ninjasquad.springmockk.SpykBean
import com.redis.om.spring.RediSearchIndexer
import com.redis.testcontainers.RedisStackContainer
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.mockkStatic
import java.io.File
import java.util.*
import kotlin.test.assertEquals
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
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
import org.springframework.core.io.ByteArrayResource
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.UnifiedJedis

@ActiveProfiles(profiles = ["dataset-test"])
@ExtendWith(MockKExtension::class)
@ExtendWith(SpringExtension::class)
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Suppress("LargeClass", "TooManyFunctions")
class DatasetServiceRBACTest : CsmRedisTestBase() {

  private val logger = LoggerFactory.getLogger(DatasetServiceIntegrationTest::class.java)

  @Autowired lateinit var rediSearchIndexer: RediSearchIndexer
  @SpykBean @Autowired lateinit var resourceScanner: ResourceScanner
  @SpykBean @Autowired lateinit var datasetApiService: DatasetApiServiceInterface
  @Autowired lateinit var datasetRepository: DatasetRepository
  @Autowired lateinit var connectorApiService: ConnectorApiServiceInterface
  @Autowired lateinit var organizationApiService: OrganizationApiServiceInterface
  @SpykBean @Autowired lateinit var csmPlatformProperties: CsmPlatformProperties
  @MockK(relaxUnitFun = true) private lateinit var eventPublisher: CsmEventPublisher

  lateinit var connectorSaved: Connector
  lateinit var dataset: Dataset
  lateinit var dataset2: Dataset
  lateinit var datasetSaved: Dataset
  lateinit var retrievedDataset1: Dataset

  lateinit var jedis: UnifiedJedis
  lateinit var organization: Organization
  lateinit var organizationSaved: Organization

  @BeforeAll
  fun beforeAll() {
    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
    mockkStatic("com.cosmotech.api.utils.RedisUtilsKt")
    mockkStatic("org.springframework.web.context.request.RequestContextHolder")
    val context = getContext(redisStackServer)
    val containerIp =
        (context.server as RedisStackContainer).containerInfo.networkSettings.ipAddress
    jedis = UnifiedJedis(HostAndPort(containerIp, REDIS_PORT))
    ReflectionTestUtils.setField(datasetApiService, "unifiedJedis", jedis)
    ReflectionTestUtils.setField(datasetApiService, "eventPublisher", eventPublisher)
  }

  @BeforeEach
  fun beforeEach() {
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "test.user"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf()
    rediSearchIndexer.createIndexFor(Connector::class.java)
    rediSearchIndexer.createIndexFor(Organization::class.java)
    rediSearchIndexer.createIndexFor(Dataset::class.java)

    connectorSaved = connectorApiService.registerConnector(makeConnector())

    organization = makeOrganization("Organization")
    dataset = makeDataset("d-dataset-1", "dataset-1")
    dataset2 = makeDataset("d-dataset-2", "dataset-2")
  }

  @AfterEach
  fun afterEach() {
    clearAllMocks()
  }

  @TestFactory
  fun `test Organization RBAC rollbackRefresh`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC rollbackRefresh : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole(role = ROLE_ADMIN)
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              materializeTwingraph()
              datasetRepository.save(
                  datasetSaved.apply { datasetSaved.ingestionStatus = IngestionStatusEnum.ERROR })

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.rollbackRefresh(organizationSaved.id!!, datasetSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.rollbackRefresh(organizationSaved.id!!, datasetSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC rollbackRefresh`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Dataset RBAC rollbackRefresh : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(role = ROLE_ADMIN)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole(role = role)
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              materializeTwingraph()
              datasetRepository.save(
                  datasetSaved.apply { datasetSaved.ingestionStatus = IngestionStatusEnum.ERROR })

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.rollbackRefresh(organizationSaved.id!!, datasetSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_WRITE",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.rollbackRefresh(organizationSaved.id!!, datasetSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC twingraphBatchUpdate`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC twingraphBatchUpdate : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole(role = ROLE_ADMIN)
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              materializeTwingraph()

              val datasetTwinGraphQuery = DatasetTwinGraphQuery("MATCH (n) RETURN n")
              val resource = ByteArrayResource("".toByteArray())
              every { resourceScanner.scanMimeTypes(any(), any()) } returns Unit
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.twingraphBatchUpdate(
                          organizationSaved.id!!,
                          datasetSaved.id!!,
                          datasetTwinGraphQuery,
                          resource)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.twingraphBatchUpdate(
                      organizationSaved.id!!, datasetSaved.id!!, datasetTwinGraphQuery, resource)
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC twingraphBatchUpdate`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Dataset RBAC twingraphBatchUpdate : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole()
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole(role = role)
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              materializeTwingraph()

              val datasetTwinGraphQuery = DatasetTwinGraphQuery("MATCH (n) RETURN n")
              val resource = ByteArrayResource("".toByteArray())
              every { resourceScanner.scanMimeTypes(any(), any()) } returns Unit
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.twingraphBatchUpdate(
                          organizationSaved.id!!,
                          datasetSaved.id!!,
                          datasetTwinGraphQuery,
                          resource)
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_WRITE",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  datasetApiService.twingraphBatchUpdate(
                      organizationSaved.id!!, datasetSaved.id!!, datasetTwinGraphQuery, resource)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC addOrReplaceDatasetCompatibilityElements`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest(
                "Test Organization RBAC addOrReplaceDatasetCompatibilityElements : $role") {
                  every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

                  val organization = makeOrganizationWithRole(role = role)
                  organizationSaved = organizationApiService.registerOrganization(organization)
                  val dataset = makeDatasetWithRole(role = ROLE_ADMIN)
                  datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
                  materializeTwingraph()

                  val datasetCompatibility = DatasetCompatibility("")

                  every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

                  if (shouldThrow) {
                    val exception =
                        assertThrows<CsmAccessForbiddenException> {
                          datasetApiService.addOrReplaceDatasetCompatibilityElements(
                              organizationSaved.id!!,
                              datasetSaved.id!!,
                              listOf(datasetCompatibility))
                        }
                    assertEquals(
                        "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                        exception.message)
                  } else {
                    assertDoesNotThrow {
                      datasetApiService.addOrReplaceDatasetCompatibilityElements(
                          organizationSaved.id!!, datasetSaved.id!!, listOf(datasetCompatibility))
                    }
                  }
                }
          }

  @TestFactory
  fun `test Dataset RBAC addOrReplaceDatasetCompatibilityElements`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest(
                "Test Dataset RBAC addOrReplaceDatasetCompatibilityElements : $role") {
                  every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

                  val organization = makeOrganizationWithRole()
                  organizationSaved = organizationApiService.registerOrganization(organization)
                  val dataset = makeDatasetWithRole(role = role)
                  datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
                  materializeTwingraph()

                  val datasetCompatibility = DatasetCompatibility("")

                  every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

                  if (shouldThrow) {
                    val exception =
                        assertThrows<CsmAccessForbiddenException> {
                          datasetApiService.addOrReplaceDatasetCompatibilityElements(
                              organizationSaved.id!!,
                              datasetSaved.id!!,
                              listOf(datasetCompatibility))
                        }
                    assertEquals(
                        "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_WRITE",
                        exception.message)
                  } else {
                    assertDoesNotThrow {
                      datasetApiService.addOrReplaceDatasetCompatibilityElements(
                          organizationSaved.id!!, datasetSaved.id!!, listOf(datasetCompatibility))
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
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole()

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.createDataset(organizationSaved.id!!, dataset)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_CREATE_CHILDREN",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.createDataset(organizationSaved.id!!, dataset)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC createSubDataset`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC createSubDataset : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole(role = ROLE_ADMIN)
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              materializeTwingraph()

              val subDatasetTwinGraphQuery = SubDatasetGraphQuery()

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.createSubDataset(
                          organizationSaved.id!!, datasetSaved.id!!, subDatasetTwinGraphQuery)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.createSubDataset(
                      organizationSaved.id!!, datasetSaved.id!!, subDatasetTwinGraphQuery)
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC createSubDataset`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Dataset RBAC createSubDataset : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole()
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole(role = role)
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              materializeTwingraph()

              val subDatasetTwinGraphQuery = SubDatasetGraphQuery()

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.createSubDataset(
                          organizationSaved.id!!, datasetSaved.id!!, subDatasetTwinGraphQuery)
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_CREATE_CHILDREN",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  datasetApiService.createSubDataset(
                      organizationSaved.id!!, datasetSaved.id!!, subDatasetTwinGraphQuery)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC createTwingraphEntities`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC createTwingraphEntities : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole(role = ROLE_ADMIN)
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              materializeTwingraph()

              val graphProperties =
                  GraphProperties(type = "node", name = "name", params = "param:0")

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.createTwingraphEntities(
                          organizationSaved.id!!,
                          datasetSaved.id!!,
                          "node",
                          listOf(graphProperties))
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.createTwingraphEntities(
                      organizationSaved.id!!, datasetSaved.id!!, "node", listOf(graphProperties))
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC createTwingraphEntities`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Dataset RBAC createTwingraphEntities : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole()
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole(role = role)
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              materializeTwingraph()

              val graphProperties =
                  GraphProperties(type = "node", name = "name", params = "param:0")

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.createTwingraphEntities(
                          organizationSaved.id!!,
                          datasetSaved.id!!,
                          "node",
                          listOf(graphProperties))
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_WRITE",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  datasetApiService.createTwingraphEntities(
                      organizationSaved.id!!, datasetSaved.id!!, "node", listOf(graphProperties))
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC deleteDataset`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC deleteDataset : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole(role = ROLE_ADMIN)
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              materializeTwingraph()

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.deleteDataset(organizationSaved.id!!, datasetSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.deleteDataset(organizationSaved.id!!, datasetSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC deleteDataset`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Dataset RBAC deleteDataset : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole()
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole(role = role)
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              materializeTwingraph()

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.deleteDataset(organizationSaved.id!!, datasetSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_DELETE",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.deleteDataset(organizationSaved.id!!, datasetSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC deleteTwingraphEntities`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC deleteTwingraphEntities : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole(role = ROLE_ADMIN)
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              materializeTwingraph()

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.deleteTwingraphEntities(
                          organizationSaved.id!!, datasetSaved.id!!, "node", listOf(""))
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.deleteTwingraphEntities(
                      organizationSaved.id!!, datasetSaved.id!!, "node", listOf(""))
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC deleteTwingraphEntities`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Dataset RBAC deleteTwingraphEntities : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole()
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole(role = role)
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              materializeTwingraph()

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.deleteTwingraphEntities(
                          organizationSaved.id!!, datasetSaved.id!!, "node", listOf(""))
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_WRITE",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  datasetApiService.deleteTwingraphEntities(
                      organizationSaved.id!!, datasetSaved.id!!, "node", listOf(""))
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC downloadTwingraph`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC downloadTwingraph : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole()
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              materializeTwingraph()

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              every { bulkQueryKey(any()) } returns "hash".toByteArray()
              jedis.setex("hash", 10.toLong(), "hashValue")

              every {
                (RequestContextHolder.getRequestAttributes() as ServletRequestAttributes).response
              } returns mockk(relaxed = true)

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.downloadTwingraph(organizationSaved.id!!, "hash")
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.downloadTwingraph(organizationSaved.id!!, "hash")
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC findAllDatasets`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC findAllDatasets : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole()
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              materializeTwingraph()

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.findAllDatasets(organizationSaved.id!!, null, null)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.findAllDatasets(organizationSaved.id!!, null, null)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC findDatasetById`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC findDatasetById : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole(role = ROLE_ADMIN)
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              materializeTwingraph()

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.findDatasetById(organizationSaved.id!!, datasetSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.findDatasetById(organizationSaved.id!!, datasetSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC findDatasetById`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Dataset RBAC findDatasetById : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole()
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole(role = role)
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              materializeTwingraph()

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.findDatasetById(organizationSaved.id!!, datasetSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.findDatasetById(organizationSaved.id!!, datasetSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC getDatasetTwingraphStatus`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC getDatasetTwingraphStatus : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole(role = ROLE_ADMIN)
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              materializeTwingraph()

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.getDatasetTwingraphStatus(
                          organizationSaved.id!!, datasetSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.getDatasetTwingraphStatus(
                      organizationSaved.id!!, datasetSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC getDatasetTwingraphStatus`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Dataset RBAC getDatasetTwingraphStatus : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole()
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole(role = role)
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              materializeTwingraph()

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.getDatasetTwingraphStatus(
                          organizationSaved.id!!, datasetSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.getDatasetTwingraphStatus(
                      organizationSaved.id!!, datasetSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC getTwingraphEntities`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC getTwingraphEntities : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole(role = ROLE_ADMIN)
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              materializeTwingraph()

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.getTwingraphEntities(
                          organizationSaved.id!!, datasetSaved.id!!, "node", listOf(""))
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.getTwingraphEntities(
                      organizationSaved.id!!, datasetSaved.id!!, "node", listOf(""))
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC getTwingraphEntities`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Dataset RBAC getTwingraphEntities : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole()
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole(role = role)
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              materializeTwingraph()

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.getTwingraphEntities(
                          organizationSaved.id!!, datasetSaved.id!!, "node", listOf(""))
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.getTwingraphEntities(
                      organizationSaved.id!!, datasetSaved.id!!, "node", listOf(""))
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC refreshDataset`() = runTest {
    mapOf(
            ROLE_VIEWER to false,
            ROLE_EDITOR to false,
            ROLE_USER to false,
            ROLE_NONE to true,
            ROLE_ADMIN to false,
        )
        .map { (role, shouldThrow) ->
          DynamicTest.dynamicTest("Test Organization RBAC refreshDataset : $role") {
            every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

            val organization = makeOrganizationWithRole(role = role)
            organizationSaved = organizationApiService.registerOrganization(organization)
            val dataset =
                makeDatasetWithRole(role = ROLE_ADMIN, sourceType = DatasetSourceType.Twincache)
            val datasetParentSaved =
                datasetApiService.createDataset(organizationSaved.id!!, dataset)
            datasetSaved = datasetParentSaved
            materializeTwingraph()
            datasetSaved =
                datasetApiService.createSubDataset(
                    organizationSaved.id!!, datasetParentSaved.id!!, SubDatasetGraphQuery())

            advanceUntilIdle()
            every { eventPublisher.publishEvent(any<TwingraphImportEvent>()) } answers
                {
                  firstArg<TwingraphImportEvent>().response = null
                }
            every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

            if (shouldThrow) {
              val exception =
                  assertThrows<CsmAccessForbiddenException> {
                    datasetApiService.refreshDataset(organizationSaved.id!!, datasetSaved.id!!)
                  }
              assertEquals(
                  "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                  exception.message)
            } else {
              assertDoesNotThrow {
                datasetApiService.refreshDataset(organizationSaved.id!!, datasetSaved.id!!)
              }
            }
          }
        }
  }

  @TestFactory
  fun `test Dataset RBAC refreshDataset`() = runTest {
    mapOf(
            ROLE_VIEWER to true,
            ROLE_EDITOR to false,
            ROLE_USER to true,
            ROLE_NONE to true,
            ROLE_ADMIN to false,
        )
        .map { (role, shouldThrow) ->
          DynamicTest.dynamicTest("Test Dataset RBAC refreshDataset : $role") {
            every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

            val organization = makeOrganizationWithRole()
            organizationSaved = organizationApiService.registerOrganization(organization)
            val dataset = makeDatasetWithRole(role = role, sourceType = DatasetSourceType.Twincache)
            val datasetParentSaved =
                datasetApiService.createDataset(organizationSaved.id!!, dataset)
            datasetSaved = datasetParentSaved
            materializeTwingraph()
            datasetSaved =
                datasetApiService.createSubDataset(
                    organizationSaved.id!!, datasetParentSaved.id!!, SubDatasetGraphQuery())

            advanceUntilIdle()
            every { eventPublisher.publishEvent(any<TwingraphImportEvent>()) } answers
                {
                  firstArg<TwingraphImportEvent>().response = null
                }
            every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

            if (shouldThrow) {
              val exception =
                  assertThrows<CsmAccessForbiddenException> {
                    datasetApiService.refreshDataset(organizationSaved.id!!, datasetSaved.id!!)
                  }
              if (role == ROLE_NONE) {
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_WRITE",
                    exception.message)
              }
            } else {
              assertDoesNotThrow {
                datasetApiService.refreshDataset(organizationSaved.id!!, datasetSaved.id!!)
              }
            }
          }
        }
  }

  @TestFactory
  fun `test Organization RBAC removeAllDatasetCompatibilityElements`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest(
                "Test Organization RBAC removeAllDatasetCompatibilityElements : $role") {
                  every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

                  val organization = makeOrganizationWithRole(role = role)
                  organizationSaved = organizationApiService.registerOrganization(organization)
                  val dataset = makeDatasetWithRole(role = ROLE_ADMIN)
                  datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
                  materializeTwingraph()

                  every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

                  if (shouldThrow) {
                    val exception =
                        assertThrows<CsmAccessForbiddenException> {
                          datasetApiService.removeAllDatasetCompatibilityElements(
                              organizationSaved.id!!, datasetSaved.id!!)
                        }
                    assertEquals(
                        "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                        exception.message)
                  } else {
                    assertDoesNotThrow {
                      datasetApiService.removeAllDatasetCompatibilityElements(
                          organizationSaved.id!!, datasetSaved.id!!)
                    }
                  }
                }
          }

  @TestFactory
  fun `test Dataset RBAC removeAllDatasetCompatibilityElements`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest(
                "Test Dataset RBAC removeAllDatasetCompatibilityElements : $role") {
                  every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

                  val organization = makeOrganizationWithRole()
                  organizationSaved = organizationApiService.registerOrganization(organization)
                  val dataset = makeDatasetWithRole(role = role)
                  datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
                  materializeTwingraph()

                  every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

                  if (shouldThrow) {
                    val exception =
                        assertThrows<CsmAccessForbiddenException> {
                          datasetApiService.removeAllDatasetCompatibilityElements(
                              organizationSaved.id!!, datasetSaved.id!!)
                        }
                    assertEquals(
                        "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_WRITE",
                        exception.message)
                  } else {
                    assertDoesNotThrow {
                      datasetApiService.removeAllDatasetCompatibilityElements(
                          organizationSaved.id!!, datasetSaved.id!!)
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
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole()
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              materializeTwingraph()

              val datasetSearch = DatasetSearch(mutableListOf("dataset"))

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.searchDatasets(
                          organizationSaved.id!!, datasetSearch, null, null)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.searchDatasets(
                      organizationSaved.id!!, datasetSearch, null, null)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC twingraphBatchQuery`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC twingraphBatchQuery : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole(role = ROLE_ADMIN)
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              materializeTwingraph()

              val datasetTwinGraphQuery = DatasetTwinGraphQuery("MATCH (n) RETURN n")
              every { datasetApiService.query(any(), any()) } returns mockk()

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.twingraphBatchQuery(
                          organizationSaved.id!!, datasetSaved.id!!, datasetTwinGraphQuery)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.twingraphBatchQuery(
                      organizationSaved.id!!, datasetSaved.id!!, datasetTwinGraphQuery)
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC twingraphBatchQuery`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Dataset RBAC twingraphBatchQuery : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole()
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole(role = role)
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              materializeTwingraph()

              val datasetTwinGraphQuery = DatasetTwinGraphQuery("MATCH (n) RETURN n")
              every { datasetApiService.query(any(), any()) } returns mockk()

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.twingraphBatchQuery(
                          organizationSaved.id!!, datasetSaved.id!!, datasetTwinGraphQuery)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.twingraphBatchQuery(
                      organizationSaved.id!!, datasetSaved.id!!, datasetTwinGraphQuery)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC twingraphQuery`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC twingraphQuery : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole(role = ROLE_ADMIN)
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              materializeTwingraph()
              val datasetTwinGraphQuery = DatasetTwinGraphQuery("MATCH (n) RETURN n")

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.twingraphQuery(
                          organizationSaved.id!!, datasetSaved.id!!, datasetTwinGraphQuery)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.twingraphQuery(
                      organizationSaved.id!!, datasetSaved.id!!, datasetTwinGraphQuery)
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC twingraphQuery`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Dataset RBAC twingraphQuery : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole()
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole(role = role)
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              materializeTwingraph()
              val datasetTwinGraphQuery = DatasetTwinGraphQuery("MATCH (n) RETURN n")

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.twingraphQuery(
                          organizationSaved.id!!, datasetSaved.id!!, datasetTwinGraphQuery)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.twingraphQuery(
                      organizationSaved.id!!, datasetSaved.id!!, datasetTwinGraphQuery)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC updateDataset`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC updateDataset : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole(role = ROLE_ADMIN)
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              materializeTwingraph()

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.updateDataset(
                          organizationSaved.id!!, datasetSaved.id!!, dataset)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.updateDataset(
                      organizationSaved.id!!, datasetSaved.id!!, dataset)
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC updateDataset`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Dataset RBAC updateDataset : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole()
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole(role = role)
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              materializeTwingraph()

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.updateDataset(
                          organizationSaved.id!!, datasetSaved.id!!, dataset)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_WRITE",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.updateDataset(
                      organizationSaved.id!!, datasetSaved.id!!, dataset)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC updateTwingraphEntities`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC updateTwingraphEntities : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole(role = ROLE_ADMIN)
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              materializeTwingraph()

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.updateTwingraphEntities(
                          organizationSaved.id!!, datasetSaved.id!!, "node", listOf())
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.updateTwingraphEntities(
                      organizationSaved.id!!, datasetSaved.id!!, "node", listOf())
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC updateTwingraphEntities`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Dataset RBAC updateTwingraphEntities : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole()
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole(role = role)
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              materializeTwingraph()

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.updateTwingraphEntities(
                          organizationSaved.id!!, datasetSaved.id!!, "node", listOf())
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_WRITE",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  datasetApiService.updateTwingraphEntities(
                      organizationSaved.id!!, datasetSaved.id!!, "node", listOf())
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC uploadTwingraph`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC uploadTwingraph : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDatasetWithRole(role = ROLE_ADMIN, sourceType = DatasetSourceType.File)
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              val fileName = this::class.java.getResource("/integrationTest.zip")?.file
              val file = File(fileName!!)
              val resource = ByteArrayResource(file.readBytes())

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.uploadTwingraph(
                          organizationSaved.id!!, datasetSaved.id!!, resource)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.uploadTwingraph(
                      organizationSaved.id!!, datasetSaved.id!!, resource)
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC uploadTwingraph`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Dataset RBAC uploadTwingraph : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole()
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole(role = role, sourceType = DatasetSourceType.File)
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              val fileName = this::class.java.getResource("/integrationTest.zip")?.file
              val file = File(fileName!!)
              val resource = ByteArrayResource(file.readBytes())

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.uploadTwingraph(
                          organizationSaved.id!!, datasetSaved.id!!, resource)
                    }
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_WRITE",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  datasetApiService.uploadTwingraph(
                      organizationSaved.id!!, datasetSaved.id!!, resource)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC addDatasetAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC addDatasetAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole(role = ROLE_ADMIN)
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              materializeTwingraph()

              val datasetAccessControl = DatasetAccessControl("id", ROLE_USER)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.addDatasetAccessControl(
                          organizationSaved.id!!, datasetSaved.id!!, datasetAccessControl)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.addDatasetAccessControl(
                      organizationSaved.id!!, datasetSaved.id!!, datasetAccessControl)
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC addDatasetAccessControl`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Dataset RBAC addDatasetAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole()
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole(role = role)
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              materializeTwingraph()

              val datasetAccessControl = DatasetAccessControl("id", ROLE_USER)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.addDatasetAccessControl(
                          organizationSaved.id!!, datasetSaved.id!!, datasetAccessControl)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_WRITE_SECURITY",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.addDatasetAccessControl(
                      organizationSaved.id!!, datasetSaved.id!!, datasetAccessControl)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC getDatasetAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC getDatasetAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset =
                  makeDatasetWithRole(role = ROLE_ADMIN, sourceType = DatasetSourceType.None)
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.getDatasetAccessControl(
                          organizationSaved.id!!, datasetSaved.id!!, TEST_USER_MAIL)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.getDatasetAccessControl(
                      organizationSaved.id!!, datasetSaved.id!!, TEST_USER_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC getDatasetAccessControl`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Dataset RBAC getDatasetAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole()
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole(role = role)
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              materializeTwingraph()

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.getDatasetAccessControl(
                          organizationSaved.id!!, datasetSaved.id!!, TEST_USER_MAIL)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ_SECURITY",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.getDatasetAccessControl(
                      organizationSaved.id!!, datasetSaved.id!!, TEST_USER_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC updateDatasetAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC updateDatasetAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole(role = ROLE_ADMIN)
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              materializeTwingraph()

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.updateDatasetAccessControl(
                          organizationSaved.id!!,
                          datasetSaved.id!!,
                          TEST_USER_MAIL,
                          DatasetRole(ROLE_USER))
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.updateDatasetAccessControl(
                      organizationSaved.id!!,
                      datasetSaved.id!!,
                      TEST_USER_MAIL,
                      DatasetRole(ROLE_USER))
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC updateDatasetAccessControl`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Dataset RBAC updateDatasetAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole()
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole(role = role)
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              materializeTwingraph()

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.updateDatasetAccessControl(
                          organizationSaved.id!!,
                          datasetSaved.id!!,
                          TEST_USER_MAIL,
                          DatasetRole(ROLE_USER))
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_WRITE_SECURITY",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.updateDatasetAccessControl(
                      organizationSaved.id!!,
                      datasetSaved.id!!,
                      TEST_USER_MAIL,
                      DatasetRole(ROLE_USER))
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC removeDatasetAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC removeDatasetAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole(role = ROLE_ADMIN)
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              materializeTwingraph()

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.removeDatasetAccessControl(
                          organizationSaved.id!!, datasetSaved.id!!, TEST_USER_MAIL)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.removeDatasetAccessControl(
                      organizationSaved.id!!, datasetSaved.id!!, TEST_USER_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC removeDatasetAccessControl`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Dataset RBAC removeDatasetAccessControl : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole()
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole(role = role)
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              materializeTwingraph()

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.removeDatasetAccessControl(
                          organizationSaved.id!!, datasetSaved.id!!, TEST_USER_MAIL)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_WRITE_SECURITY",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.removeDatasetAccessControl(
                      organizationSaved.id!!, datasetSaved.id!!, TEST_USER_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC getDatasetSecurityUsers`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC getDatasetSecurityUsers : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole(role = ROLE_ADMIN)
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              materializeTwingraph()

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.getDatasetSecurityUsers(
                          organizationSaved.id!!, datasetSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.getDatasetSecurityUsers(
                      organizationSaved.id!!, datasetSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC getDatasetSecurityUsers`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Dataset RBAC getDatasetSecurityUsers : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole()
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole(role = role)
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              materializeTwingraph()

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.getDatasetSecurityUsers(
                          organizationSaved.id!!, datasetSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ_SECURITY",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.getDatasetSecurityUsers(
                      organizationSaved.id!!, datasetSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC getDatasetSecurity`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC getDatasetSecurity : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole(role = ROLE_ADMIN)
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.getDatasetSecurity(
                          organizationSaved.id!!, datasetSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.getDatasetSecurity(organizationSaved.id!!, datasetSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC getDatasetSecurity`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Dataset RBAC getDatasetSecurity : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole()
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole(role = role)
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.getDatasetSecurity(
                          organizationSaved.id!!, datasetSaved.id!!)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ_SECURITY",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.getDatasetSecurity(organizationSaved.id!!, datasetSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test Organization RBAC setDatasetDefaultSecurity`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Organization RBAC setDatasetDefaultSecurity : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole(role = role)
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole(role = ROLE_ADMIN)
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.setDatasetDefaultSecurity(
                          organizationSaved.id!!, datasetSaved.id!!, DatasetRole(ROLE_VIEWER))
                    }
                assertEquals(
                    "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.setDatasetDefaultSecurity(
                      organizationSaved.id!!, datasetSaved.id!!, DatasetRole(ROLE_VIEWER))
                }
              }
            }
          }

  @TestFactory
  fun `test Dataset RBAC setDatasetDefaultSecurity`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test Dataset RBAC setDatasetDefaultSecurity : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole()
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole(role = role)
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)

              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.setDatasetDefaultSecurity(
                          organizationSaved.id!!, datasetSaved.id!!, DatasetRole(ROLE_VIEWER))
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_WRITE_SECURITY",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.setDatasetDefaultSecurity(
                      organizationSaved.id!!, datasetSaved.id!!, DatasetRole(ROLE_VIEWER))
                }
              }
            }
          }

  private fun materializeTwingraph(
      dataset: Dataset = datasetSaved,
      createTwingraph: Boolean = true
  ): Dataset {
    dataset.apply {
      if (createTwingraph && !this.twingraphId.isNullOrBlank()) {
        jedis.graphQuery(this.twingraphId, "CREATE (n:labelrouge)")
      }
      this.ingestionStatus = IngestionStatusEnum.SUCCESS
    }
    return datasetRepository.save(dataset)
  }

  fun makeConnector(): Connector {
    return Connector(
        key = "connector",
        name = "connector-1",
        repository = "repo",
        version = "1.0.0",
        ioTypes = listOf(),
        id = "c-AbCdEf123")
  }

  fun makeDataset(
      id: String,
      name: String,
      sourceType: DatasetSourceType = DatasetSourceType.Twincache
  ): Dataset {
    return Dataset(
        id = id,
        name = name,
        main = true,
        connector = DatasetConnector(id = connectorSaved.id, name = connectorSaved.name),
        tags = mutableListOf("test", "data"),
        sourceType = sourceType,
        security =
            DatasetSecurity(
                default = ROLE_NONE,
                accessControlList =
                    mutableListOf(
                        DatasetAccessControl(id = CONNECTED_ADMIN_USER, role = ROLE_ADMIN))))
  }

  fun makeOrganization(name: String): Organization {
    return Organization(
        name = name,
        ownerId = "my.account-tester@cosmotech.com",
        security =
            OrganizationSecurity(
                default = ROLE_ADMIN,
                accessControlList =
                    mutableListOf(
                        OrganizationAccessControl(id = CONNECTED_ADMIN_USER, role = "admin"))))
  }

  fun makeOrganizationWithRole(
      id: String = TEST_USER_MAIL,
      role: String = ROLE_ADMIN
  ): Organization {
    return Organization(
        id = UUID.randomUUID().toString(),
        name = "Organization NameRbac",
        ownerId = "my.account-tester@cosmotech.com",
        security =
            OrganizationSecurity(
                default = ROLE_NONE,
                accessControlList =
                    mutableListOf(
                        OrganizationAccessControl(id = CONNECTED_ADMIN_USER, role = ROLE_ADMIN),
                        OrganizationAccessControl(id = id, role = role))))
  }

  fun makeDatasetWithRole(
      organizationId: String = organizationSaved.id!!,
      parentId: String = "",
      id: String = TEST_USER_MAIL,
      role: String = ROLE_ADMIN,
      sourceType: DatasetSourceType = DatasetSourceType.Twincache
  ): Dataset {
    val random = UUID.randomUUID().toString()
    return Dataset(
        id = random,
        name = "My datasetRbac",
        organizationId = organizationId,
        parentId = parentId,
        ownerId = "ownerId",
        connector = DatasetConnector(connectorSaved.id!!),
        twingraphId = "graph-${random}",
        source = SourceInfo("location", "name", "path"),
        tags = mutableListOf("dataset"),
        sourceType = sourceType,
        security =
            DatasetSecurity(
                default = ROLE_NONE,
                accessControlList =
                    mutableListOf(
                        DatasetAccessControl(id = CONNECTED_ADMIN_USER, role = ROLE_ADMIN),
                        DatasetAccessControl(id = id, role = role))))
  }
}
