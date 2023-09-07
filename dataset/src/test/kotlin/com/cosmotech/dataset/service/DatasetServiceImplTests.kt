// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset.service

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.events.CsmEventPublisher
import com.cosmotech.api.events.TwingraphImportJobInfoRequest
import com.cosmotech.api.exceptions.CsmAccessForbiddenException
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.id.CsmIdGenerator
import com.cosmotech.api.rbac.CsmAdmin
import com.cosmotech.api.rbac.CsmRbac
import com.cosmotech.api.security.ROLE_PLATFORM_ADMIN
import com.cosmotech.api.utils.getCurrentAccountIdentifier
import com.cosmotech.api.utils.getCurrentAuthenticatedRoles
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.connector.api.ConnectorApiService
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.dataset.domain.DatasetConnector
import com.cosmotech.dataset.domain.DatasetSourceType
import com.cosmotech.dataset.domain.DatasetTwinGraphQuery
import com.cosmotech.dataset.domain.SourceInfo
import com.cosmotech.dataset.domain.SubDatasetGraphQuery
import com.cosmotech.dataset.repository.DatasetRepository
import com.cosmotech.dataset.utils.toJsonString
import com.cosmotech.organization.api.OrganizationApiService
import com.redislabs.redisgraph.RedisGraph
import com.redislabs.redisgraph.ResultSet
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.verify
import java.io.File
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.apache.commons.compress.archivers.ArchiveException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.core.io.ByteArrayResource
import org.springframework.data.domain.Page
import redis.clients.jedis.JedisPool

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
  @Suppress("unused") @MockK var idGenerator: CsmIdGenerator = mockk(relaxed = true)
  @MockK var eventPublisher: CsmEventPublisher = mockk(relaxed = true)
  @MockK val connectorService: ConnectorApiService = mockk(relaxed = true)
  @MockK val organizationService: OrganizationApiService = mockk(relaxed = true)
  @MockK val datasetRepository: DatasetRepository = mockk(relaxed = true)
  @MockK val csmJedisPool: JedisPool = mockk(relaxed = true)
  @MockK val csmRedisGraph: RedisGraph = mockk(relaxed = true)
  @MockK var csmPlatformProperties: CsmPlatformProperties = mockk(relaxed = true)
  @Suppress("unused") @MockK private var csmAdmin: CsmAdmin = CsmAdmin(csmPlatformProperties)
  @SpyK var csmRbac: CsmRbac = CsmRbac(csmPlatformProperties, csmAdmin)
  @SpyK @InjectMockKs lateinit var datasetService: DatasetServiceImpl

  @BeforeEach
  fun setUp() {
    every { csmPlatformProperties.rbac.enabled } returns true

    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
    every { getCurrentAccountIdentifier(any()) } returns USER_ID
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "my.account-tester"
    every { getCurrentAuthenticatedRoles(any()) } returns listOf()

    every { csmJedisPool.resource } returns mockk(relaxed = true)
    every { csmJedisPool.resource.close() } returns Unit

    every { csmPlatformProperties.twincache.dataset.defaultPageSize } returns 10

    MockKAnnotations.init(this)
  }

  @Test
  fun `findAllDatasets should return empty list when no dataset exists`() {

    every { datasetRepository.findByOrganizationIdAndMain(ORGANIZATION_ID, true, any()) } returns Page.empty()
    val result = datasetService.findAllDatasets(ORGANIZATION_ID, null, null)
    assertEquals(emptyList<Dataset>(), result)
  }

  @Test
  fun `findDatasetById should return the dataset when it exists`() {
    val dataset = baseDataset()
    every { datasetRepository.findById(DATASET_ID) } returns Optional.of(dataset)
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
    every { datasetRepository.save(any()) } returnsArgument 0
    val result = datasetService.createDataset(ORGANIZATION_ID, dataset)
    assertEquals(dataset.organizationId, result.organizationId)
    assertEquals(
        DatasetSourceType.File,
        result.sourceType,
    )
    assertEquals(Dataset.Status.DRAFT, result.status)
  }

  @Test
  fun `createDataset should throw IllegalArgumentException when the name is empty`() {
    val dataset = baseDataset().copy(name = "")
    every { datasetRepository.save(any()) } returnsArgument 0
    assertThrows<IllegalArgumentException> {
      datasetService.createDataset(ORGANIZATION_ID, dataset)
    }
  }

  @Test
  fun `createDataset should throw IllegalArgumentException when source is empty for ADT or Storage type`() {
    val typeList = listOf(DatasetSourceType.ADT, DatasetSourceType.Storage)
    typeList.forEach { type ->
      val dataset = baseDataset().copy(sourceType = type, source = null)
      every { datasetRepository.save(any()) } returnsArgument 0
      assertThrows<IllegalArgumentException> {
        datasetService.createDataset(ORGANIZATION_ID, dataset)
      }
    }
  }

  @Test
  fun `createDataset should throw IllegalArgumentException when connector is empty`() {
    val dataset = baseDataset()
    every { datasetRepository.save(any()) } returnsArgument 0
    assertThrows<IllegalArgumentException> {
      datasetService.createDataset(ORGANIZATION_ID, dataset)
    }
  }

  @Test
  fun `createSubDataset create Dataset copy with new id, name, description, parentId & twingraphId`() {
    val dataset =
        baseDataset()
            .copy(
                status = Dataset.Status.COMPLETED,
                sourceType = DatasetSourceType.ADT,
                source = SourceInfo("http://storage.location"),
                twingraphId = "twingraphId")
    val subDatasetGraphQuery =
        SubDatasetGraphQuery(
            name = "My Sub Dataset",
            description = "My Sub Dataset description",
        )
    every { datasetRepository.findById(DATASET_ID) } returns Optional.of(dataset)
    every { csmJedisPool.resource.exists(any<String>()) } returns true
    every { csmJedisPool.resource.hgetAll(any<String>()) } returns
        mapOf("lastVersion" to "lastVersion", "graphRotation" to "2")
    every { csmJedisPool.resource.dump(any<String>()) } returns ByteArray(0)
    every { datasetRepository.save(any()) } returnsArgument 0
    val result =
        datasetService.createSubDataset(ORGANIZATION_ID, dataset.id!!, subDatasetGraphQuery)
    assertEquals(dataset.organizationId, result.organizationId)
    assertEquals(Dataset.Status.COMPLETED, result.status)
    assertEquals(dataset.sourceType, result.sourceType)
    assertEquals(dataset.source, result.source)
    assertEquals(subDatasetGraphQuery.name, result.name)
    assertEquals(subDatasetGraphQuery.description, result.description)
    assertNotEquals(dataset.id, result.id)
    assertNotEquals(dataset.twingraphId, result.twingraphId)
    assertEquals(dataset.id, result.parentId)
  }

  @Test
  fun `createSubDataset should throw IllegalArgumentException when twingraphId is empty`() {
    val dataset = baseDataset().copy(twingraphId = "")
    val subDatasetGraphQuery = SubDatasetGraphQuery()
    every { datasetRepository.findById(DATASET_ID) } returns Optional.of(dataset)
    assertThrows<IllegalArgumentException> {
      datasetService.createSubDataset(ORGANIZATION_ID, dataset.id!!, subDatasetGraphQuery)
    }
  }

  @Test
  fun `createSubDataset should throw CsmResourceNotFoundException when Twingraph not found`() {
    val dataset = baseDataset().copy(twingraphId = "twingraphId")
    val subDatasetGraphQuery = SubDatasetGraphQuery()
    every { datasetRepository.findById(DATASET_ID) } returns Optional.of(dataset)
    every { csmJedisPool.resource.dump(any<String>()) } throws CsmResourceNotFoundException("")
    assertThrows<CsmResourceNotFoundException> {
      datasetService.createSubDataset(ORGANIZATION_ID, dataset.id!!, subDatasetGraphQuery)
    }
  }

  @Test
  fun `uploadTwingraph should throw ArchiveException when resource is not Archive`() {
    val fileName = this::class.java.getResource("/Users.csv")?.file
    val file = File(fileName!!)
    val resource = ByteArrayResource(file.readBytes())
    assertThrows<ArchiveException> {
      datasetService.uploadTwingraph(ORGANIZATION_ID, DATASET_ID, resource)
    }
  }

  @Test
  fun `uploadTwingraph should throw IllegalArgumentException when resource is not Zip Archive`() {
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
    every { datasetRepository.findById(DATASET_ID) } returns Optional.of(dataset)
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
    every { datasetRepository.findById(DATASET_ID) } returns Optional.of(dataset)
    assertThrows<CsmResourceNotFoundException> {
      datasetService.uploadTwingraph(ORGANIZATION_ID, DATASET_ID, resource)
    }
  }

  @Test
  fun `uploadTwingraph should throw CsmResourceNotFoundException when Dataset has Pending status`() {
    val dataset =
        baseDataset()
            .copy(
                status = Dataset.Status.PENDING,
                sourceType = DatasetSourceType.File,
                twingraphId = "twingraphId")
    val fileName = this::class.java.getResource("/Graph.zip")?.file
    val file = File(fileName!!)
    val resource = ByteArrayResource(file.readBytes())
    every { datasetRepository.findById(DATASET_ID) } returns Optional.of(dataset)
    assertThrows<CsmResourceNotFoundException> {
      datasetService.uploadTwingraph(ORGANIZATION_ID, DATASET_ID, resource)
    }
  }

  @Test
  fun `uploadTwingraph should save Dataset with Pending status and start uploading process`() {
    val dataset =
        baseDataset()
            .copy(
                status = Dataset.Status.DRAFT,
                sourceType = DatasetSourceType.File,
                twingraphId = "twingraphId")
    val fileName = this::class.java.getResource("/Graph.zip")?.file
    val file = File(fileName!!)
    val resource = ByteArrayResource(file.readBytes())
    every { datasetRepository.findById(DATASET_ID) } returns Optional.of(dataset)
    every { csmJedisPool.resource.exists(any<String>()) } returns true
    every { datasetRepository.save(any()) } returnsArgument 0

    datasetService.uploadTwingraph(ORGANIZATION_ID, DATASET_ID, resource)
    verify(exactly = 1) { datasetRepository.save(any()) }
    verify { datasetRepository.save(match { it.status == Dataset.Status.PENDING }) }
  }

  @Test
  fun `getDatasetTwingraphStatus should return Pending status for File Dataset if twingraph not exists`() {
    val dataset =
        baseDataset()
            .copy(
                status = Dataset.Status.DRAFT,
                sourceType = DatasetSourceType.File,
                twingraphId = "twingraphId")
    every { datasetRepository.findById(DATASET_ID) } returns Optional.of(dataset)
    every { csmJedisPool.resource.exists(any<String>()) } returns false
    val result = datasetService.getDatasetTwingraphStatus(ORGANIZATION_ID, DATASET_ID, "JOB_ID")
    assertEquals(Dataset.Status.PENDING.value, result)
  }

  @Test
  fun `getDatasetTwingraphStatus should return Completed status for File Dataset if twingraph exists`() {
    val dataset =
        baseDataset()
            .copy(
                status = Dataset.Status.DRAFT,
                sourceType = DatasetSourceType.File,
                twingraphId = "twingraphId")
    every { datasetRepository.findById(DATASET_ID) } returns Optional.of(dataset)
    every { csmJedisPool.resource.exists(any<String>()) } returns true
    val result = datasetService.getDatasetTwingraphStatus(ORGANIZATION_ID, DATASET_ID, "JOB_ID")
    assertEquals(Dataset.Status.COMPLETED.value, result)
  }

  @Test
  fun `getDatasetTwingraphStatus should return default Unknow status for ADT Dataset`() {
    val dataset =
        baseDataset()
            .copy(
                status = Dataset.Status.DRAFT,
                sourceType = DatasetSourceType.ADT,
                twingraphId = "twingraphId")
    mockkConstructor(TwingraphImportJobInfoRequest::class)
    every { anyConstructed<TwingraphImportJobInfoRequest>().response } returns
        Dataset.Status.PENDING.value
    every { datasetRepository.findById(DATASET_ID) } returns Optional.of(dataset)
    val result = datasetService.getDatasetTwingraphStatus(ORGANIZATION_ID, DATASET_ID, "JOB_ID")
    assertEquals(Dataset.Status.PENDING.value, result)
  }

  @Test
  fun `getDatasetTwingraphStatus should return COMPLETED Status for ADT Dataset if twingraph exists`() {
    val dataset =
        baseDataset()
            .copy(
                status = Dataset.Status.PENDING,
                sourceType = DatasetSourceType.ADT,
                twingraphId = "twingraphId")
    mockkConstructor(TwingraphImportJobInfoRequest::class)
    every { anyConstructed<TwingraphImportJobInfoRequest>().response } returns
        Dataset.Status.COMPLETED.value
    every { datasetRepository.findById(DATASET_ID) } returns Optional.of(dataset)
    every { csmJedisPool.resource.exists(any<String>()) } returns true
    every { datasetRepository.save(any()) } returnsArgument 0
    val result = datasetService.getDatasetTwingraphStatus(ORGANIZATION_ID, DATASET_ID, "JOB_ID")
    assertEquals(Dataset.Status.COMPLETED.value, result)
  }

  @Test
  fun `refreshDataset should throw CsmResourceNotFoundException when sourceType is not File`() {
    val dataset =
        baseDataset()
            .copy(
                sourceType = DatasetSourceType.File, source = SourceInfo("http://storage.location"))
    every { datasetRepository.findById(DATASET_ID) } returns Optional.of(dataset)
    assertThrows<CsmResourceNotFoundException> {
      datasetService.refreshDataset(ORGANIZATION_ID, DATASET_ID)
    }
  }

  @Test
  fun `refreshDataset should save Dataset if Status is not PENDING`() {
    val dataset =
        baseDataset()
            .copy(
                status = Dataset.Status.DRAFT,
                sourceType = DatasetSourceType.ADT,
                source = SourceInfo("http://storage.location"),
                twingraphId = "twingraphId")
    every { datasetRepository.findById(DATASET_ID) } returns Optional.of(dataset)
    every { csmJedisPool.resource.exists(any<String>()) } returns true
    every { datasetRepository.save(any()) } returnsArgument 0
    val datasetInfo = datasetService.refreshDataset(ORGANIZATION_ID, DATASET_ID)
    verify(exactly = 1) { datasetRepository.save(any()) }
    assertEquals(dataset.id, datasetInfo.datasetId)
    assertEquals(dataset.status!!.value, datasetInfo.status)
  }

  @Test
  fun `deleteDataset should throw CsmResourceNotFoundException when Dataset is not found`() {
    every { datasetRepository.findById(DATASET_ID) } returns Optional.empty()
    assertThrows<CsmResourceNotFoundException> {
      datasetService.deleteDataset(ORGANIZATION_ID, DATASET_ID)
    }
  }

  @Test
  fun `deleteDataset should throw CsmAccessForbiddenException`() {
    val dataset = baseDataset()
    every { datasetRepository.findById(DATASET_ID) } returns Optional.of(dataset)
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns "my.account-tester"
    assertThrows<CsmAccessForbiddenException> {
      datasetService.deleteDataset(ORGANIZATION_ID, DATASET_ID)
    }
  }

  @Test
  fun `deleteDataset should delete Dataset and its twingraph`() {
    val dataset =
        baseDataset()
            .copy(
                twingraphId = "twingraphId",
            )
    every { datasetRepository.findById(DATASET_ID) } returns Optional.of(dataset)
    every { getCurrentAuthenticatedRoles(csmPlatformProperties) } returns
        listOf(ROLE_PLATFORM_ADMIN)
    every { csmJedisPool.resource.exists(any<String>()) } returns true
    every { csmJedisPool.resource.del(any<String>()) } returns 1
    every { datasetRepository.delete(any()) } returns Unit
    datasetService.deleteDataset(ORGANIZATION_ID, DATASET_ID)
    verify(exactly = 1) { datasetRepository.delete(any()) }
    verify(exactly = 1) { csmJedisPool.resource.del(any<String>()) }
  }

  @Test
  fun `twingraphQuery should call query and set data to Redis`() {
    val dataset =
        baseDataset()
            .copy(
                twingraphId = "graphId",
            )
    every { datasetRepository.findById(DATASET_ID) } returns Optional.of(dataset)
    every { csmPlatformProperties.twincache.queryBulkTTL } returns 1000L

    every { csmJedisPool.resource.exists(any<String>()) } returns true
    every { csmJedisPool.resource.hgetAll(any<String>()) } returns
        mapOf("graphName" to "graphName", "graphRotation" to "2")

    every { csmRedisGraph.query(any(), any(), any<Long>()) } returns mockEmptyResultSet()

    val twinGraphQuery = DatasetTwinGraphQuery("MATCH(n) RETURN n")
    datasetService.twingraphQuery(ORGANIZATION_ID, DATASET_ID, twinGraphQuery)

    verify(exactly = 1) { csmRedisGraph.query(any(), any(), any<Long>()) }
  }

  private fun mockEmptyResultSet(): ResultSet {
    val resultSet = mockk<ResultSet>()
    every { resultSet.toJsonString() } returns "[]"
    every { resultSet.iterator() } returns
        mockk<MutableIterator<com.redislabs.redisgraph.Record>> {
          every { hasNext() } returns false
        }
    return resultSet
  }
}
