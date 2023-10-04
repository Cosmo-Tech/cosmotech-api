// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.twingraph.api

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.rbac.CsmAdmin
import com.cosmotech.api.rbac.CsmRbac
import com.cosmotech.api.utils.ResourceScanner
import com.cosmotech.api.utils.getCurrentAccountIdentifier
import com.cosmotech.api.utils.getCurrentAuthenticatedRoles
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.api.utils.getCurrentAuthentication
import com.cosmotech.api.utils.shaHash
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.twingraph.domain.TwinGraphBatchResult
import com.cosmotech.twingraph.domain.TwinGraphQuery
import com.cosmotech.twingraph.extension.toJsonString
import com.redislabs.redisgraph.ResultSet
import com.redislabs.redisgraph.impl.api.RedisGraph
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.verifyAll
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import redis.clients.jedis.JedisPool

const val AUTHENTICATED_USERNAME = "authenticated-user"
const val CONNECTED_ADMIN_USER = "test.admin@cosmotech.com"
const val CONNECTED_DEFAULT_USER = "test.user@cosmotech.com"

@ExtendWith(MockKExtension::class)
class TwingraphServiceImplTests {

  @Suppress("unused")
  @MockK
  private var csmPlatformProperties: CsmPlatformProperties = mockk(relaxed = true)
  @Suppress("unused") @MockK private var csmAdmin: CsmAdmin = CsmAdmin(csmPlatformProperties)

  @Suppress("unused") @SpyK private var csmRbac: CsmRbac = CsmRbac(csmPlatformProperties, csmAdmin)

  @MockK private lateinit var csmJedisPool: JedisPool
  @MockK private lateinit var csmRedisGraph: RedisGraph
  @Suppress("unused") @MockK private lateinit var resourceScanner: ResourceScanner
  @MockK private lateinit var organizationService: OrganizationApiService

  @SpyK @InjectMockKs private lateinit var twingraphServiceImpl: TwingraphServiceImpl

  @BeforeTest
  fun setUp() {
    MockKAnnotations.init(this)

    every { csmJedisPool.resource } returns mockk(relaxed = true)
    every { csmJedisPool.resource.close() } returns Unit

    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
    every { getCurrentAccountIdentifier(any()) } returns CONNECTED_DEFAULT_USER
    every { getCurrentAuthenticatedUserName(csmPlatformProperties) } returns AUTHENTICATED_USERNAME
    every { getCurrentAuthenticatedRoles(any()) } returns listOf()

    every { csmPlatformProperties.rbac.enabled } returns true
  }

  @AfterTest
  fun tearDown() {
    unmockkStatic(::getCurrentAuthentication)
  }

  @Test
  fun `test delete graph as Admin - should call delete`() {
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")
    every { organizationService.findOrganizationById(any()) } returns mockk(relaxed = true)

    every { csmJedisPool.resource.scan(any<String>(), any(), any()) } returns
        mockk {
          every { result } returns listOf("graphId")
          every { cursor } answers { "0" }
        }

    every { csmRedisGraph.deleteGraph(any()) } returns 1L.toString()
    twingraphServiceImpl.delete("orgId", "graphId")

    verify { csmJedisPool.resource.scan(any<String>(), any(), any()) }
    verify { csmRedisGraph.deleteGraph(any()) }
  }

  @Test
  fun `test findAllTwingraphs as Admin - should return all graphs`() {
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")
    every { organizationService.findOrganizationById(any()) } returns mockk(relaxed = true)

    every { csmJedisPool.resource.scan(any<String>(), any(), any()) } returns
        mockk {
          every { result } returns listOf("graphId", "graphId2")
          every { cursor } answers { "0" }
        }

    val twingraphs = twingraphServiceImpl.findAllTwingraphs("orgId")
    assertEquals(twingraphs.size, 2)
  }

  @Test
  fun `test getGraphMetadata as Admin - should return metadata`() {
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")
    every { organizationService.findOrganizationById(any()) } returns mockk(relaxed = true)

    every { csmJedisPool.resource.exists(any<String>()) } returns true
    every { csmJedisPool.resource.hgetAll(any<String>()) } returns
        mapOf("lastVersion" to "lastVersion", "graphRotation" to "2")
    val metadataMap = twingraphServiceImpl.getGraphMetaData("orgId", "graphId")
    assertEquals(metadataMap["lastVersion"], "lastVersion")
    assertEquals(metadataMap["graphRotation"], "2")
  }

  @Test
  fun `test getGraphMetadata as Admin - should throw exception if graph does not exist`() {
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")
    every { organizationService.findOrganizationById(any()) } returns mockk(relaxed = true)

    every { csmJedisPool.resource.exists(any<String>()) } returns false
    assertThrows<CsmResourceNotFoundException> {
      twingraphServiceImpl.getGraphMetaData("orgId", "graphId")
    }
  }

  @Test
  fun `test bulkQueryGraphs as Admin - should call query and set data to Redis`() {
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")
    every { organizationService.findOrganizationById(any()) } returns mockk(relaxed = true)
    every { csmPlatformProperties.twincache.queryBulkTTL } returns 1000L

    every { csmJedisPool.resource.keys(any<String>()) } returns setOf("graphId")
    every { csmJedisPool.resource.exists(any<ByteArray>()) } returns false

    every { csmRedisGraph.query(any(), any()) } returns mockEmptyResultSet()
    every { csmJedisPool.resource.setex(any<ByteArray>(), any<Long>(), any<ByteArray>()) } returns
        "OK"

    val twinGraphQuery = TwinGraphQuery("MATCH(n) RETURN n", "1")
    val jsonHash = twingraphServiceImpl.batchQuery("orgId", "graphId", twinGraphQuery)

    assertEquals(jsonHash.hash, "graphId:1:MATCH(n) RETURN n".shaHash())
  }

  @Test
  fun `test bulkQueryGraphs as Admin - should return existing Hash when data found`() {
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")
    every { organizationService.findOrganizationById(any()) } returns mockk(relaxed = true)

    every { csmJedisPool.resource.keys(any<String>()) } returns setOf("graphId")
    every { csmJedisPool.resource.exists(any<ByteArray>()) } returns true

    val twinGraphQuery = TwinGraphQuery("MATCH(n) RETURN n", "1")
    val jsonHash = twingraphServiceImpl.batchQuery("orgId", "graphId", twinGraphQuery)
    assertEquals(jsonHash.hash, "graphId:1:MATCH(n) RETURN n".shaHash())
  }

  @Test
  fun `test downloadGraph as Admin - should get graph data`() {
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")
    every { organizationService.findOrganizationById(any()) } returns mockk(relaxed = true)

    every { csmJedisPool.resource.exists(any<ByteArray>()) } returns true
    every { csmJedisPool.resource.ttl(any<ByteArray>()) } returns 1000L
    mockkStatic("org.springframework.web.context.request.RequestContextHolder")
    every {
      (RequestContextHolder.getRequestAttributes() as ServletRequestAttributes).response
    } returns mockk(relaxed = true)
    every { csmJedisPool.resource.get(any<ByteArray>()) } returns "[]".toByteArray()

    twingraphServiceImpl.downloadGraph("orgId", "hash")

    verify { csmJedisPool.resource.exists(any<ByteArray>()) }
    verify { csmJedisPool.resource.ttl(any<ByteArray>()) }
    verify { csmJedisPool.resource.get(any<ByteArray>()) }
  }

  @Test
  fun `test downloadGraph as Admin - should throw exception if data not found`() {
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")
    every { organizationService.findOrganizationById(any()) } returns mockk(relaxed = true)

    every { csmJedisPool.resource.exists(any<ByteArray>()) } returns false

    assertThrows<CsmResourceNotFoundException> {
      twingraphServiceImpl.downloadGraph("orgId", "hash")
    }
  }

  @Test
  fun `test downloadGraph as Admin - should throw exception if data expired`() {
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")
    every { organizationService.findOrganizationById(any()) } returns mockk(relaxed = true)

    every { csmJedisPool.resource.exists(any<ByteArray>()) } returns true
    every { csmJedisPool.resource.ttl(any<ByteArray>()) } returns -1L

    assertThrows<CsmResourceNotFoundException> {
      twingraphServiceImpl.downloadGraph("orgId", "hash")
    }
  }

  @Test
  fun `test updateGraphMetaData as Admin should modify graphName & graphVersion`() {
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")
    every { organizationService.findOrganizationById(any()) } returns mockk(relaxed = true)

    every { csmJedisPool.resource.exists(any<String>()) } returns true
    every { csmJedisPool.resource.hset(any<String>(), any<String>(), any<String>()) } returns 1L
    every { csmJedisPool.resource.hgetAll(any<String>()) } returns
        mapOf("graphName" to "graphName", "graphRotation" to "2")

    var metadata =
        mapOf(
            "lastVersion" to "last",
            "graphName" to "graphName",
            "graphRotation" to "2",
            "url" to "dummy")
    twingraphServiceImpl.updateGraphMetaData("orgId", "graphId", metadata)
    verifyAll {
      csmJedisPool.resource.exists(any<String>())
      csmJedisPool.resource.hset(any(), "graphName", "graphName")
      csmJedisPool.resource.hset(any(), "graphRotation", "2")
      csmJedisPool.resource.hgetAll(any<String>())
      csmJedisPool.resource.close()
    }
  }

  @Test
  fun `test updateGraphMetaData as Admin should not modify graphName & graphVersion if not present`() {
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")
    every { organizationService.findOrganizationById(any()) } returns mockk(relaxed = true)

    every { csmJedisPool.resource.exists(any<String>()) } returns true
    every { csmJedisPool.resource.hset(any<String>(), any<String>(), any<String>()) } returns 1L
    every { csmJedisPool.resource.hgetAll(any<String>()) } returns
        mapOf("graphName" to "graphName", "graphRotation" to "2")

    var metadata = mapOf("lastVersion" to "last", "url" to "dummy")
    twingraphServiceImpl.updateGraphMetaData("orgId", "graphId", metadata)
    verifyAll {
      csmJedisPool.resource.exists(any<String>())
      csmJedisPool.resource.hgetAll(any<String>())
      csmJedisPool.resource.close()
    }
  }

  @Test
  fun `test processCSV - should create cypher requests by line`() {
    val fileName = this::class.java.getResource("/Users.csv")?.file
    val file = File(fileName!!)
    val query =
        TwinGraphQuery(
            "CREATE (:Person {id: toInteger(\$id), name: \$name, rank: toInteger(\$rank), object: \$object})")
    val result = TwinGraphBatchResult(0, 0, mutableListOf())
    twingraphServiceImpl.processCSVBatch(file.inputStream(), query, result) {
      result.processedLines++
    }
    assertEquals(9, result.totalLines)
    assertEquals(9, result.processedLines)
    assertEquals(0, result.errors.size)
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
