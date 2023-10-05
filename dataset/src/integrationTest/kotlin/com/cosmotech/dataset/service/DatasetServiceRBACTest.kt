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
import com.cosmotech.api.tests.CsmRedisTestBase
import com.cosmotech.api.utils.getCurrentAccountIdentifier
import com.cosmotech.api.utils.getCurrentAuthenticatedRoles
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.connector.api.ConnectorApiService
import com.cosmotech.connector.domain.Connector
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
import com.cosmotech.dataset.domain.SourceInfo
import com.cosmotech.dataset.domain.SubDatasetGraphQuery
import com.cosmotech.dataset.repository.DatasetRepository
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.domain.Organization
import com.cosmotech.organization.domain.OrganizationAccessControl
import com.cosmotech.organization.domain.OrganizationSecurity
import com.redis.om.spring.RediSearchIndexer
import com.redis.testcontainers.RedisStackContainer
import com.redislabs.redisgraph.impl.api.RedisGraph
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.mockkStatic
import java.io.InputStream
import java.util.*
import kotlin.test.assertEquals
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ByteArrayResource
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.util.ReflectionTestUtils
import redis.clients.jedis.JedisPool

@ActiveProfiles(profiles = ["dataset-test"])
@ExtendWith(MockKExtension::class)
@ExtendWith(SpringExtension::class)
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Suppress("LargeClass", "TooManyFunctions")
class DatasetServiceRBACTest : CsmRedisTestBase() {

  private val TEST_USER_MAIL = "testuser@mail.fr"

  @Autowired lateinit var rediSearchIndexer: RediSearchIndexer
  @Autowired lateinit var datasetApiService: DatasetServiceImpl
  @Autowired lateinit var datasetRepository: DatasetRepository
  @Autowired lateinit var connectorApiService: ConnectorApiService
  @Autowired lateinit var organizationApiService: OrganizationApiService
  @Autowired lateinit var csmPlatformProperties: CsmPlatformProperties

  lateinit var datasetSaved: Dataset
  lateinit var connectorSaved: Connector
  lateinit var organizationSaved: Organization

  lateinit var jedisPool: JedisPool
  lateinit var redisGraph: RedisGraph

  @BeforeAll
  fun beforeAll() {
    val context = getContext(redisStackServer)
    val containerIp =
        (context.server as RedisStackContainer).containerInfo.networkSettings.ipAddress
    jedisPool = JedisPool(containerIp, REDIS_PORT)
    redisGraph = RedisGraph(jedisPool)
    ReflectionTestUtils.setField(datasetApiService, "csmJedisPool", jedisPool)
    ReflectionTestUtils.setField(datasetApiService, "csmRedisGraph", redisGraph)
    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
  }

  @BeforeEach
  fun beforeEach() {
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns TEST_USER_MAIL
    every { getCurrentAuthenticatedRoles(any()) } returns listOf()
    rediSearchIndexer.createIndexFor(Connector::class.java)
    rediSearchIndexer.createIndexFor(Organization::class.java)
    rediSearchIndexer.createIndexFor(Dataset::class.java)

    organizationSaved = organizationApiService.registerOrganization(makeOrganizationWithRole())
    connectorSaved = connectorApiService.registerConnector(makeConnector())
    datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, makeDatasetWithRole())

    materializeTwingraph(datasetSaved)
  }

  @TestFactory
  fun `test RBAC addOrReplaceDatasetCompatibilityElements`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC addOrReplaceDatasetCompatibilityElements : $role") {
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
                          organizationSaved.id!!, datasetSaved.id!!, listOf(datasetCompatibility))
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
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${organizationSaved.id!!} - User does not have permission $PERMISSION_CREATE_CHILDREN",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  datasetApiService.createDataset(organizationSaved.id!!, dataset)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC createSubDataset`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC createSubDataset : $role") {
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
  fun `test RBAC createTwingraphEntities`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC createTwingraphEntities : $role") {
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
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_DELETE",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  datasetApiService.deleteDataset(organizationSaved.id!!, datasetSaved.id!!)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC deleteTwingraphEntities`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC deleteTwingraphEntities : $role") {
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

  /*@TestFactory
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

              val organization = makeOrganizationWithRole()
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole(role = role)
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              materializeTwingraph()
              //                every{ jedisPool. }
              0
              every { getCurrentAccountIdentifier(any()) } returns TEST_USER_MAIL

              if (shouldThrow) {
                val exception =
                    assertThrows<CsmAccessForbiddenException> {
                      datasetApiService.downloadTwingraph(organizationSaved.id!!, "hash")
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.downloadTwingraph(organizationSaved.id!!, "hash")
                }
              }
            }
          }*/

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
  fun `test RBAC findDatasetById`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC findDatasetById : $role") {
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
  fun `test RBAC getDatasetTwingraphStatus`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC getDatasetTwingraphStatus : $role") {
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
                          organizationSaved.id!!, datasetSaved.id!!, null)
                    }
                assertEquals(
                    "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                    exception.message)
              } else {
                assertDoesNotThrow {
                  datasetApiService.getDatasetTwingraphStatus(
                      organizationSaved.id!!, datasetSaved.id!!, null)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC getTwingraphEntities`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC getTwingraphEntities : $role") {
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
  fun `test RBAC refreshDataset`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC refreshDataset : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole()
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole(role = role, sourceType = DatasetSourceType.None)
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              materializeTwingraph()

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

  @TestFactory
  fun `test RBAC removeAllDatasetCompatibilityElements`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC removeAllDatasetCompatibilityElements : $role") {
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
  fun `test RBAC twingraphBatchQuery`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC twingraphBatchQuery : $role") {
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

  /* @TestFactory
  fun `test RBAC twingraphBatchUpdate`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC twingraphBatchUpdate : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

                val organization = makeOrganizationWithRole()
                organizationSaved = organizationApiService.registerOrganization(organization)
                val dataset = makeDatasetWithRole(role = role)
                datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
                materializeTwingraph()

              val datasetTwinGraphQuery = DatasetTwinGraphQuery("MATCH (n) RETURN n")
                val resource = mockk<ByteArrayResource>()
                val resourceScanner = mockk<ResourceScanner>()
                val inputStream = mockk<InputStream>()
              ReflectionTestUtils.setField(datasetApiService, "resourceScanner", resourceScanner)
              every { resourceScanner.scanMimeTypes(any(), any()) } returns Unit
              every { resource.inputStream } returns inputStream
                every { datasetApiService.trx<Map<String, String>>(any(), any()) } returns emptyMap()

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
          }*/

  @TestFactory
  fun `test RBAC twingraphQuery`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC twingraphQuery : $role") {
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
                  datasetApiService.updateDataset(
                      organizationSaved.id!!, datasetSaved.id!!, dataset)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC updateTwingraphEntities`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC updateTwingraphEntities : $role") {
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
  fun `test RBAC uploadTwingraph`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to false,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC uploadTwingraph : $role") {
              every { getCurrentAccountIdentifier(any()) } returns CONNECTED_ADMIN_USER

              val organization = makeOrganizationWithRole()
              organizationSaved = organizationApiService.registerOrganization(organization)
              val dataset = makeDatasetWithRole(role = role)
              datasetSaved = datasetApiService.createDataset(organizationSaved.id!!, dataset)
              materializeTwingraph()
              mockkStatic(ArchiveStreamFactory::class)
              every { ArchiveStreamFactory.detect(any()) } returns "zip"
              val resource = mockk<ByteArrayResource>()
              val inputStream = mockk<InputStream>()
              every { resource.inputStream } returns inputStream

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
  fun `test RBAC addDatasetAccessControl`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC addDatasetAccessControl : $role") {
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
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_WRITE_SECURITY",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  datasetApiService.addDatasetAccessControl(
                      organizationSaved.id!!, datasetSaved.id!!, datasetAccessControl)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC getDatasetAccessControl`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC getDatasetAccessControl : $role") {
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
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ_SECURITY",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  datasetApiService.getDatasetAccessControl(
                      organizationSaved.id!!, datasetSaved.id!!, TEST_USER_MAIL)
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
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_WRITE_SECURITY",
                      exception.message)
                }
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
  fun `test RBAC removeDatasetAccessControl`() =
      mapOf(
              ROLE_VIEWER to true,
              ROLE_EDITOR to true,
              ROLE_USER to true,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC removeDatasetAccessControl : $role") {
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
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_WRITE_SECURITY",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  datasetApiService.removeDatasetAccessControl(
                      organizationSaved.id!!, datasetSaved.id!!, TEST_USER_MAIL)
                }
              }
            }
          }

  @TestFactory
  fun `test RBAC getDatasetSecurityUsers`() =
      mapOf(
              ROLE_VIEWER to false,
              ROLE_EDITOR to false,
              ROLE_USER to false,
              ROLE_NONE to true,
              ROLE_ADMIN to false,
          )
          .map { (role, shouldThrow) ->
            DynamicTest.dynamicTest("Test RBAC getDatasetSecurityUsers : $role") {
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
                if (role == ROLE_NONE) {
                  assertEquals(
                      "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ",
                      exception.message)
                } else {
                  assertEquals(
                      "RBAC ${datasetSaved.id!!} - User does not have permission $PERMISSION_READ_SECURITY",
                      exception.message)
                }
              } else {
                assertDoesNotThrow {
                  datasetApiService.getDatasetSecurityUsers(
                      organizationSaved.id!!, datasetSaved.id!!)
                }
              }
            }
          }

  fun makeOrganizationWithRole(
      userName: String = TEST_USER_MAIL,
      role: String = ROLE_ADMIN
  ): Organization {
    return Organization(
        id = UUID.randomUUID().toString(),
        name = "Organization Name",
        ownerId = "my.account-tester@cosmotech.com",
        security =
            OrganizationSecurity(
                default = ROLE_NONE,
                accessControlList =
                    mutableListOf(
                        OrganizationAccessControl(id = CONNECTED_ADMIN_USER, role = ROLE_ADMIN),
                        OrganizationAccessControl(id = userName, role = role))))
  }

  fun makeDatasetWithRole(
      organizationId: String = organizationSaved.id!!,
      userName: String = TEST_USER_MAIL,
      role: String = ROLE_ADMIN,
      sourceType: DatasetSourceType = DatasetSourceType.File
  ): Dataset {
    return Dataset(
        id = UUID.randomUUID().toString(),
        name = "My dataset",
        organizationId = organizationId,
        ownerId = "ownerId",
        connector = DatasetConnector(connectorSaved.id!!),
        twingraphId = "graph",
        source = SourceInfo("location", "name", "path"),
        tags = mutableListOf("dataset"),
        sourceType = sourceType,
        security =
            DatasetSecurity(
                default = ROLE_NONE,
                accessControlList =
                    mutableListOf(
                        DatasetAccessControl(id = CONNECTED_ADMIN_USER, role = ROLE_ADMIN),
                        DatasetAccessControl(id = userName, role = role))))
  }

  private fun materializeTwingraph(dataset: Dataset = datasetSaved): Dataset {
    dataset.apply {
      redisGraph.query(this.twingraphId, "CREATE (n:labelrouge)")
      this.status = Dataset.Status.READY
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
}
