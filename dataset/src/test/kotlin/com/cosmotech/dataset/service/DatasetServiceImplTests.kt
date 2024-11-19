// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
@file:Suppress("DEPRECATION")

package com.cosmotech.dataset.service

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.events.CsmEventPublisher
import com.cosmotech.api.events.TwingraphImportJobInfoRequest
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.id.CsmIdGenerator
import com.cosmotech.api.rbac.CsmAdmin
import com.cosmotech.api.rbac.CsmRbac
import com.cosmotech.api.rbac.PERMISSION_CREATE_CHILDREN
import com.cosmotech.api.security.ROLE_PLATFORM_ADMIN
import com.cosmotech.api.utils.ResourceScanner
import com.cosmotech.api.utils.getCurrentAccountIdentifier
import com.cosmotech.api.utils.getCurrentAuthenticatedRoles
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.api.utils.shaHash
import com.cosmotech.connector.ConnectorApiServiceInterface
import com.cosmotech.connector.domain.Connector
import com.cosmotech.connector.domain.IoTypesEnum
import com.cosmotech.dataset.domain.*
import com.cosmotech.dataset.repository.DatasetRepository
import com.cosmotech.dataset.utils.toJsonString
import com.cosmotech.organization.OrganizationApiServiceInterface
import com.cosmotech.organization.domain.Organization
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import java.io.File
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.apache.commons.compress.archivers.ArchiveException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.core.io.ByteArrayResource
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import redis.clients.jedis.UnifiedJedis
import redis.clients.jedis.graph.ResultSet

const val USER_ID = "bob@mycompany.com"
const val ORGANIZATION_ID = "O-OrganizationId"
const val DATASET_ID = "D-DatasetId"

fun baseDataset() =
    Dataset(
        id = DATASET_ID,
        name = "My Dataset",
        description = "My Dataset description",
        organizationId = ORGANIZATION_ID,
    )

@ExtendWith(MockKExtension::class)
class DatasetServiceImplTests {
  @Suppress("unused") var idGenerator: CsmIdGenerator = mockk(relaxed = true)

  @MockK var eventPublisher: CsmEventPublisher = mockk(relaxed = true)

  @Suppress("unused") @MockK private lateinit var connectorService: ConnectorApiServiceInterface

  @MockK private lateinit var organizationService: OrganizationApiServiceInterface

  @MockK private lateinit var datasetRepository: DatasetRepository

  @MockK private lateinit var unifiedJedis: UnifiedJedis

  @Suppress("unused") @MockK private lateinit var resourceScanner: ResourceScanner

  private var csmPlatformProperties: CsmPlatformProperties = mockk(relaxed = true)

  private var csmAdmin: CsmAdmin = CsmAdmin(csmPlatformProperties)

  @Suppress("unused") private var csmRbac: CsmRbac = CsmRbac(csmPlatformProperties, csmAdmin)

  @InjectMockKs private lateinit var datasetService: DatasetServiceImpl

  @BeforeEach
  fun setUp() {
    every { csmPlatformProperties.rbac.enabled } returns false

    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
    every { getCurrentAccountIdentifier(any()) } returns USER_ID
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "my.account-tester"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf()

    every { csmPlatformProperties.twincache.dataset.defaultPageSize } returns 10
  }

  @Test
  fun `findAllDatasets should return empty list when no dataset exists`() {
    every { organizationService.getVerifiedOrganization(ORGANIZATION_ID) } returns Organization()
    every { datasetRepository.findAll(any<Pageable>()) } returns Page.empty()

    val result = datasetService.findAllDatasets(ORGANIZATION_ID, null, null)
    assertEquals(emptyList(), result)
  }

  @Test
  fun `findDatasetById should return the dataset when it exists`() {
    val dataset = baseDataset()
    every { organizationService.getVerifiedOrganization(ORGANIZATION_ID) } returns Organization()
    every { datasetRepository.findBy(ORGANIZATION_ID, DATASET_ID) } returns Optional.of(dataset)
    val result = datasetService.findDatasetById(ORGANIZATION_ID, DATASET_ID)
    assertEquals(dataset, result)
  }

  @Test
  fun `createDataset should return the dataset when it is created with Draft status and File type by default`() {
    val dataset =
        baseDataset()
            .copy(
                connector = DatasetConnector(id = "connectorId", name = "connectorName"),
            )
    every {
      organizationService.getVerifiedOrganization(ORGANIZATION_ID, PERMISSION_CREATE_CHILDREN)
    } returns Organization()
    every { connectorService.findConnectorById(any()) } returns
        Connector(
            key = "key",
            name = "name",
            repository = "repository",
            version = "version",
            ioTypes = listOf(IoTypesEnum.read))
    every { datasetRepository.save(any()) } returnsArgument 0
    val result = datasetService.createDataset(ORGANIZATION_ID, dataset)
    assertEquals(dataset.organizationId, result.organizationId)
    assertEquals(
        DatasetSourceType.None,
        result.sourceType,
    )
    assertEquals(IngestionStatusEnum.NONE, result.ingestionStatus)
  }

  @Test
  fun `createDataset should throw IllegalArgumentException when the name is empty`() {
    val dataset = baseDataset().copy(name = "")
    every {
      organizationService.getVerifiedOrganization(ORGANIZATION_ID, PERMISSION_CREATE_CHILDREN)
    } returns Organization()
    every { datasetRepository.save(any()) } returnsArgument 0
    assertThrows<IllegalArgumentException> {
      datasetService.createDataset(ORGANIZATION_ID, dataset)
    }
  }

  @Test
  fun `createDataset should throw IllegalArgumentException when source is empty for ADT or Storage type`() {
    val typeList = listOf(DatasetSourceType.ADT, DatasetSourceType.AzureStorage)
    every {
      organizationService.getVerifiedOrganization(ORGANIZATION_ID, PERMISSION_CREATE_CHILDREN)
    } returns Organization()
    typeList.forEach { type ->
      val dataset = baseDataset().copy(sourceType = type, source = null)
      every { datasetRepository.save(any()) } returnsArgument 0
      assertThrows<IllegalArgumentException> {
        datasetService.createDataset(ORGANIZATION_ID, dataset)
      }
    }
  }

  @Test
  fun `createSubDataset create Dataset copy with new id, name, description, parentId & twingraphId`() {
    val SUB_TWINGRAPH_ID = "Sub-twingraph-id"
    runTest {
      val dataset =
          baseDataset()
              .copy(
                  ingestionStatus = IngestionStatusEnum.SUCCESS,
                  sourceType = DatasetSourceType.Twincache,
                  source = SourceInfo("http://storage.location"),
                  twingraphId = "twingraphId")
      val subDatasetGraphQuery =
          SubDatasetGraphQuery(
              name = "My Sub Dataset",
              description = "My Sub Dataset description",
          )
      every { organizationService.getVerifiedOrganization(ORGANIZATION_ID) } returns Organization()
      every { idGenerator.generate("twingraph") } returns SUB_TWINGRAPH_ID
      every { datasetRepository.findBy(ORGANIZATION_ID, DATASET_ID) } returns Optional.of(dataset)
      every { unifiedJedis.eval(any(), any(), dataset.twingraphId, SUB_TWINGRAPH_ID) } returns Unit
      every { unifiedJedis.hgetAll(any<String>()) } returns
          mapOf("lastVersion" to "lastVersion", "graphRotation" to "2")
      every { unifiedJedis.graphReadonlyQuery(any(), any(), any<Long>()) } returns
          mockEmptyResultSet()
      every { unifiedJedis.dump(any<String>()) } returns ByteArray(0)
      every { datasetRepository.save(any()) } returnsArgument 0
      val result =
          datasetService.createSubDataset(ORGANIZATION_ID, dataset.id!!, subDatasetGraphQuery)

      advanceUntilIdle()
      assertEquals(dataset.organizationId, result.organizationId)
      assertEquals(dataset.sourceType, result.sourceType)
      assertEquals(dataset.source, result.source)
      assertEquals(subDatasetGraphQuery.name, result.name)
      assertEquals(subDatasetGraphQuery.description, result.description)
      assertNotEquals(dataset.id, result.id)
      assertNotEquals(dataset.twingraphId, result.twingraphId)
      assertEquals(dataset.id, result.parentId)
    }
  }

  @Test
  fun `createSubDataset should throw IllegalArgumentException when twingraphId is empty`() {
    val dataset =
        baseDataset().copy(twingraphId = "", ingestionStatus = IngestionStatusEnum.SUCCESS)
    val subDatasetGraphQuery = SubDatasetGraphQuery()
    every { organizationService.getVerifiedOrganization(ORGANIZATION_ID) } returns Organization()
    every { datasetRepository.findBy(ORGANIZATION_ID, DATASET_ID) } returns Optional.of(dataset)
    assertThrows<CsmResourceNotFoundException> {
      datasetService.createSubDataset(ORGANIZATION_ID, dataset.id!!, subDatasetGraphQuery)
    }
  }

  @Test
  fun `uploadTwingraph should throw ArchiveException when resource is not Archive`() {
    val dataset =
        baseDataset()
            .copy(
                twingraphId = "twingraphId",
                sourceType = DatasetSourceType.File,
                ingestionStatus = IngestionStatusEnum.SUCCESS)
    every { organizationService.getVerifiedOrganization(ORGANIZATION_ID) } returns Organization()
    every { datasetRepository.findBy(ORGANIZATION_ID, DATASET_ID) } returns Optional.of(dataset)

    val fileName = this::class.java.getResource("/Users.csv")?.file
    val file = File(fileName!!)
    val resource = ByteArrayResource(file.readBytes())
    assertThrows<ArchiveException> {
      datasetService.uploadTwingraph(ORGANIZATION_ID, DATASET_ID, resource)
    }
  }

  @Test
  fun `uploadTwingraph should throw IllegalArgumentException when resource is not Zip Archive`() {
    val dataset =
        baseDataset()
            .copy(
                twingraphId = "twingraphId",
                sourceType = DatasetSourceType.File,
                ingestionStatus = IngestionStatusEnum.SUCCESS)
    every { organizationService.getVerifiedOrganization(ORGANIZATION_ID) } returns Organization()
    every { datasetRepository.findBy(ORGANIZATION_ID, DATASET_ID) } returns Optional.of(dataset)

    val fileName = this::class.java.getResource("/Users.7z")?.file
    val file = File(fileName!!)
    val resource = ByteArrayResource(file.readBytes())

    assertThrows<IllegalArgumentException> {
      datasetService.uploadTwingraph(ORGANIZATION_ID, DATASET_ID, resource)
    }
  }

  @Test
  fun `uploadTwingraph should throw CsmResourceNotFoundException when Dataset is not File type`() {
    val dataset =
        baseDataset()
            .copy(
                sourceType = DatasetSourceType.ADT, source = SourceInfo("http://storage.location"))
    val fileName = this::class.java.getResource("/Graph.zip")?.file
    val file = File(fileName!!)
    val resource = ByteArrayResource(file.readBytes())
    every { organizationService.getVerifiedOrganization(ORGANIZATION_ID) } returns Organization()
    every { datasetRepository.findBy(ORGANIZATION_ID, DATASET_ID) } returns Optional.of(dataset)
    assertThrows<CsmResourceNotFoundException> {
      datasetService.uploadTwingraph(ORGANIZATION_ID, DATASET_ID, resource)
    }
  }

  @Test
  fun `uploadTwingraph should throw CsmResourceNotFoundException when twinGraphId is missing`() {
    val dataset =
        baseDataset()
            .copy(
                sourceType = DatasetSourceType.File, source = SourceInfo("http://storage.location"))
    val fileName = this::class.java.getResource("/Graph.zip")?.file
    val file = File(fileName!!)
    val resource = ByteArrayResource(file.readBytes())
    every { organizationService.getVerifiedOrganization(ORGANIZATION_ID) } returns Organization()
    every { datasetRepository.findBy(ORGANIZATION_ID, DATASET_ID) } returns Optional.of(dataset)
    assertThrows<CsmResourceNotFoundException> {
      datasetService.uploadTwingraph(ORGANIZATION_ID, DATASET_ID, resource)
    }
  }

  @Test
  fun `uploadTwingraph should save Dataset with Pending status and start uploading process`() {
    val dataset =
        baseDataset()
            .copy(
                ingestionStatus = IngestionStatusEnum.NONE,
                sourceType = DatasetSourceType.File,
                twingraphId = "twingraphId")
    val fileName = this::class.java.getResource("/Graph.zip")?.file
    val file = File(fileName!!)
    val resource = ByteArrayResource(file.readBytes())
    every { organizationService.getVerifiedOrganization(ORGANIZATION_ID) } returns Organization()
    every { datasetRepository.findBy(ORGANIZATION_ID, DATASET_ID) } returns Optional.of(dataset)
    every { unifiedJedis.exists(any<String>()) } returns true
    every { datasetRepository.save(any()) } returnsArgument 0

    val fileUploadValidation = datasetService.uploadTwingraph(ORGANIZATION_ID, DATASET_ID, resource)

    assertEquals(
        FileUploadValidation(
            mutableListOf(FileUploadMetadata("Users", 749)),
            mutableListOf(FileUploadMetadata("Follows", 50))),
        fileUploadValidation)
  }

  @Test
  fun `getDatasetTwingraphStatus should return Draft status for File Dataset if twingraph not exists`() {
    val dataset =
        baseDataset()
            .copy(
                ingestionStatus = IngestionStatusEnum.NONE,
                sourceType = DatasetSourceType.File,
                twingraphId = "twingraphId")
    every { organizationService.getVerifiedOrganization(ORGANIZATION_ID) } returns Organization()
    every { datasetRepository.findBy(ORGANIZATION_ID, DATASET_ID) } returns Optional.of(dataset)
    every { unifiedJedis.exists(any<String>()) } returns false
    val result = datasetService.getDatasetTwingraphStatus(ORGANIZATION_ID, DATASET_ID)
    assertEquals(IngestionStatusEnum.NONE.value, result)
  }

  @Test
  fun `getDatasetTwingraphStatus should return Completed status for File Dataset if twingraph exists`() {
    val dataset =
        baseDataset()
            .copy(
                ingestionStatus = IngestionStatusEnum.SUCCESS,
                sourceType = DatasetSourceType.File,
                twingraphId = "twingraphId")
    every { organizationService.getVerifiedOrganization(ORGANIZATION_ID) } returns Organization()
    every { datasetRepository.findBy(ORGANIZATION_ID, DATASET_ID) } returns Optional.of(dataset)
    every { unifiedJedis.exists(any<String>()) } returns true
    every { datasetRepository.save(any()) } returns mockk()
    val result = datasetService.getDatasetTwingraphStatus(ORGANIZATION_ID, DATASET_ID)
    assertEquals(IngestionStatusEnum.SUCCESS.value, result)
  }

  @Test
  fun `getDatasetTwingraphStatus should return COMPLETED Status for ADT Dataset if twingraph exists`() {
    val dataset =
        baseDataset()
            .copy(
                ingestionStatus = IngestionStatusEnum.PENDING,
                sourceType = DatasetSourceType.ADT,
                source = SourceInfo(location = "test", jobId = "0"),
                twingraphId = "twingraphId")
    mockkConstructor(TwingraphImportJobInfoRequest::class)
    every { organizationService.getVerifiedOrganization(ORGANIZATION_ID) } returns Organization()
    every { anyConstructed<TwingraphImportJobInfoRequest>().response } returns "Succeeded"
    every { datasetRepository.findBy(ORGANIZATION_ID, DATASET_ID) } returns Optional.of(dataset)
    every { unifiedJedis.exists(any<String>()) } returns true
    every { datasetRepository.save(any()) } returnsArgument 0

    val result = datasetService.getDatasetTwingraphStatus(ORGANIZATION_ID, DATASET_ID)

    assertEquals(IngestionStatusEnum.SUCCESS.value, result)
  }

  @Test
  fun `refreshDataset should throw CsmResourceNotFoundException when sourceType is not File`() {
    val dataset =
        baseDataset()
            .copy(
                sourceType = DatasetSourceType.File, source = SourceInfo("http://storage.location"))
    every { organizationService.getVerifiedOrganization(ORGANIZATION_ID) } returns Organization()
    every { datasetRepository.findBy(ORGANIZATION_ID, DATASET_ID) } returns Optional.of(dataset)
    assertThrows<CsmResourceNotFoundException> {
      datasetService.refreshDataset(ORGANIZATION_ID, DATASET_ID)
    }
  }

  @Test
  fun `refreshDataset should save Dataset if Status is not PENDING`() {
    val dataset =
        baseDataset()
            .copy(
                ingestionStatus = IngestionStatusEnum.NONE,
                sourceType = DatasetSourceType.ADT,
                source = SourceInfo("http://storage.location", jobId = "0"),
                twingraphId = "twingraphId")
    every { organizationService.getVerifiedOrganization(ORGANIZATION_ID) } returns Organization()
    every { datasetRepository.findBy(ORGANIZATION_ID, DATASET_ID) } returns Optional.of(dataset)
    every { unifiedJedis.exists(any<String>()) } returns true
    every { datasetRepository.save(any()) } returnsArgument 0
    val datasetInfo = datasetService.refreshDataset(ORGANIZATION_ID, DATASET_ID)
    verify(atLeast = 1) { datasetRepository.save(any()) }
    assertEquals(dataset.id, datasetInfo.datasetId)
    assertEquals(dataset.ingestionStatus!!.value, datasetInfo.status)
  }

  @Test
  fun `deleteDataset should throw CsmResourceNotFoundException when Dataset is not found`() {
    every { organizationService.getVerifiedOrganization(ORGANIZATION_ID) } returns Organization()
    every { datasetRepository.findBy(ORGANIZATION_ID, DATASET_ID) } returns Optional.empty()
    assertThrows<CsmResourceNotFoundException> {
      datasetService.deleteDataset(ORGANIZATION_ID, DATASET_ID)
    }
  }

  @Test
  fun `deleteDataset do not throw error - rbac is disabled`() {
    val dataset = baseDataset().apply { twingraphId = "mytwingraphId" }
    every { organizationService.getVerifiedOrganization(ORGANIZATION_ID) } returns Organization()
    every { datasetRepository.findBy(ORGANIZATION_ID, DATASET_ID) } returns Optional.of(dataset)
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "my.account-tester"
    datasetService.deleteDataset(ORGANIZATION_ID, DATASET_ID)
    verify(exactly = 1) { datasetRepository.delete(any()) }
  }

  @Test
  fun `deleteDataset should delete Dataset and its twingraph`() {
    val dataset =
        baseDataset()
            .copy(
                twingraphId = "twingraphId",
            )
    every { organizationService.getVerifiedOrganization(ORGANIZATION_ID) } returns Organization()
    every { datasetRepository.findBy(ORGANIZATION_ID, DATASET_ID) } returns Optional.of(dataset)
    every { getCurrentAuthenticatedRoles(csmPlatformProperties) } returns
        listOf(ROLE_PLATFORM_ADMIN)
    every { unifiedJedis.exists(any<String>()) } returns true
    every { unifiedJedis.del(any<String>()) } returns 1
    every { datasetRepository.delete(any()) } returns Unit
    datasetService.deleteDataset(ORGANIZATION_ID, DATASET_ID)
    verify(exactly = 1) { datasetRepository.delete(any()) }
    verify(exactly = 1) { unifiedJedis.del(any<String>()) }
  }

  @Test
  fun `twingraphQuery should call query and set data to Redis`() {
    val dataset =
        baseDataset().copy(twingraphId = "graphId", ingestionStatus = IngestionStatusEnum.SUCCESS)
    val graphQuery = "MATCH(n) RETURN n"
    val twinGraphQuery = DatasetTwinGraphQuery(graphQuery)

    every { organizationService.getVerifiedOrganization(ORGANIZATION_ID) } returns Organization()
    every { datasetRepository.save(any()) } returnsArgument 0
    every { datasetRepository.findBy(ORGANIZATION_ID, DATASET_ID) } returns Optional.of(dataset)
    every { csmPlatformProperties.twincache.queryBulkTTL } returns 1000L

    every { unifiedJedis.graphQuery("graphId", graphQuery, 0) } returns mockEmptyResultSet()
    every { unifiedJedis.exists(any<String>()) } returns true
    every { unifiedJedis.hgetAll(any<String>()) } returns
        mapOf("graphName" to "graphName", "graphRotation" to "2")

    every { unifiedJedis.graphReadonlyQuery(any(), any(), any<Long>()) } returns
        mockEmptyResultSet()

    datasetService.twingraphQuery(ORGANIZATION_ID, DATASET_ID, twinGraphQuery)

    verify { unifiedJedis.graphQuery("graphId", graphQuery, 0) }
  }

  @Test
  fun `test processCSV - should create cypher requests by line`() {
    val fileName = this::class.java.getResource("/Users.csv")?.file
    val file = File(fileName!!)
    val query =
        DatasetTwinGraphQuery(
            "CREATE (:Person {id: toInteger(\$id), name: \$name, rank: toInteger(\$rank), object: \$object})")
    val result = TwinGraphBatchResult(0, 0, mutableListOf())
    datasetService.processCSVBatch(file.inputStream(), query, result) { result.processedLines++ }
    assertEquals(9, result.totalLines)
    assertEquals(9, result.processedLines)
    assertEquals(0, result.errors.size)
  }

  @Test
  fun `test bulkQueryGraphs as Admin - should call query and set data to Redis`() {
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")
    every { organizationService.getVerifiedOrganization(ORGANIZATION_ID) } returns Organization()
    val dataset =
        baseDataset().copy(twingraphId = "graphId", ingestionStatus = IngestionStatusEnum.SUCCESS)
    val graphQuery = "MATCH(n) RETURN n"
    every { datasetRepository.save(any()) } returnsArgument 0
    every { datasetRepository.findBy(ORGANIZATION_ID, DATASET_ID) } returns Optional.of(dataset)
    every { csmPlatformProperties.twincache.queryBulkTTL } returns 1000L

    every { unifiedJedis.keys(any<String>()) } returns setOf("graphId")
    every { unifiedJedis.exists(any<ByteArray>()) } returns false
    every { unifiedJedis.graphQuery("graphId", graphQuery, 0) } returns mockEmptyResultSet()
    every { unifiedJedis.graphQuery(any(), any()) } returns mockEmptyResultSet()
    every { unifiedJedis.setex(any<ByteArray>(), any<Long>(), any<ByteArray>()) } returns "OK"

    val twinGraphQuery = DatasetTwinGraphQuery("MATCH(n) RETURN n")
    val jsonHash = datasetService.twingraphBatchQuery(ORGANIZATION_ID, DATASET_ID, twinGraphQuery)

    assertEquals(jsonHash.hash, "graphId:MATCH(n) RETURN n".shaHash())
    verifyAll {
      unifiedJedis.exists(any<ByteArray>())
      unifiedJedis.graphQuery("graphId", graphQuery, 0)
      unifiedJedis.setex(any<ByteArray>(), any<Long>(), any<ByteArray>())
    }
  }

  @Test
  fun `test bulkQueryGraphs as Admin - should return existing Hash when data found`() {
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")
    every { organizationService.getVerifiedOrganization(ORGANIZATION_ID) } returns Organization()
    val dataset =
        baseDataset().copy(twingraphId = "graphId", ingestionStatus = IngestionStatusEnum.SUCCESS)
    every { datasetRepository.save(any()) } returnsArgument 0
    every { datasetRepository.findBy(ORGANIZATION_ID, DATASET_ID) } returns Optional.of(dataset)
    every { unifiedJedis.keys(any<String>()) } returns setOf("graphId")
    every { unifiedJedis.exists(any<ByteArray>()) } returns true

    val twinGraphQuery = DatasetTwinGraphQuery("MATCH(n) RETURN n")
    val jsonHash = datasetService.twingraphBatchQuery(ORGANIZATION_ID, DATASET_ID, twinGraphQuery)
    assertEquals(jsonHash.hash, "graphId:MATCH(n) RETURN n".shaHash())
  }

  @Test
  fun `test downloadGraph as Admin - should get graph data`() {
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")
    every { organizationService.getVerifiedOrganization(ORGANIZATION_ID) } returns Organization()

    mockkStatic("org.springframework.web.context.request.RequestContextHolder")
    every {
      (RequestContextHolder.getRequestAttributes() as ServletRequestAttributes).response
    } returns mockk(relaxed = true)

    every { unifiedJedis.exists(any<ByteArray>()) } returns true
    every { unifiedJedis.ttl(any<ByteArray>()) } returns 1000L
    every { unifiedJedis.get(any<ByteArray>()) } returns "[]".toByteArray()

    datasetService.downloadTwingraph(ORGANIZATION_ID, "hash")
    verifyAll {
      unifiedJedis.exists(any<ByteArray>())
      unifiedJedis.ttl(any<ByteArray>())
      unifiedJedis.get(any<ByteArray>())
    }
  }

  @Test
  fun `test downloadGraph as Admin - should throw exception if data not found`() {
    every { organizationService.getVerifiedOrganization(ORGANIZATION_ID) } returns Organization()
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")
    every { organizationService.findOrganizationById(any()) } returns mockk()
    every { unifiedJedis.exists(any<ByteArray>()) } returns false

    assertThrows<CsmResourceNotFoundException> {
      datasetService.downloadTwingraph(ORGANIZATION_ID, "hash")
    }
  }

  @Test
  fun `test downloadGraph as Admin - should throw exception if data expired`() {
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")
    every { organizationService.getVerifiedOrganization(ORGANIZATION_ID) } returns Organization()

    every { unifiedJedis.exists(any<ByteArray>()) } returns true
    every { unifiedJedis.ttl(any<ByteArray>()) } returns -1L

    assertThrows<CsmResourceNotFoundException> {
      datasetService.downloadTwingraph(ORGANIZATION_ID, "hash")
    }
  }

  private fun mockEmptyResultSet(): ResultSet {
    val resultSet = mockk<ResultSet>()
    every { resultSet.toJsonString() } returns "[]"
    every { resultSet.iterator() } returns
        mockk<MutableIterator<redis.clients.jedis.graph.Record>> {
          every { hasNext() } returns false
        }
    return resultSet
  }
}
