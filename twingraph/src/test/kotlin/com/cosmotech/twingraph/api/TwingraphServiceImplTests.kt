// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.twingraph.api

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.rbac.CsmAdmin
import com.cosmotech.api.rbac.CsmRbac
import com.cosmotech.api.utils.getCurrentAuthenticatedMail
import com.cosmotech.api.utils.getCurrentAuthenticatedRoles
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.api.utils.getCurrentAuthentication
import com.cosmotech.api.utils.shaHash
import com.cosmotech.organization.api.OrganizationApiService
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
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.extension.ExtendWith
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
  @MockK private lateinit var organizationService: OrganizationApiService

  @SpyK @InjectMockKs private lateinit var twingraphServiceImpl: TwingraphServiceImpl

  @BeforeTest
  fun setUp() {
    MockKAnnotations.init(this)

    every { csmJedisPool.resource.close() } returns Unit

    mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
    every { getCurrentAuthenticatedMail(any()) } returns CONNECTED_DEFAULT_USER
    every { getCurrentAuthenticatedUserName() } returns AUTHENTICATED_USERNAME
    every { getCurrentAuthenticatedRoles(any()) } returns listOf()

    every { csmPlatformProperties.rbac.enabled } returns true
  }

  @AfterTest
  fun tearDown() {
    unmockkStatic(::getCurrentAuthentication)
  }

  @Test
  fun `test bulkQueryGraphs as Admin - should call query and set data to Redis`() {
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")
    every { organizationService.findOrganizationById(any()) } returns mockk(relaxed = true)

    every { csmJedisPool.resource.keys(any<String>()) } returns setOf("graphId")
    every { csmJedisPool.resource.exists(any<ByteArray>()) } returns false

    mockkConstructor(RedisGraph::class)
    every { anyConstructed<RedisGraph>().query(any(), any()) } returns mockEmptyResultSet()
    every { csmJedisPool.resource.setex(any(), any<Long>(), any<ByteArray>()) } returns "OK"

    val twinGraphQuery = TwinGraphQuery("MATCH(n) RETURN n", "1")
    val jsonHash = twingraphServiceImpl.bulkQuery("orgId", "graphId", twinGraphQuery)

    assertEquals(jsonHash.hash, "graphId:1:MATCH(n) RETURN n".shaHash())
    verify { anyConstructed<RedisGraph>().query(any(), any()) }
    verify { csmJedisPool.resource.setex(any(), any<Long>(), any<ByteArray>()) }
  }

  @Test
  fun `test bulkQueryGraphs as Admin - should return existing Hash when data found`() {
    every { getCurrentAuthenticatedRoles(any()) } returns listOf("Platform.Admin")
    every { organizationService.findOrganizationById(any()) } returns mockk(relaxed = true)

    every { csmJedisPool.resource.keys(any<String>()) } returns setOf("graphId")
    every { csmJedisPool.resource.exists(any<ByteArray>()) } returns true

    val twinGraphQuery = TwinGraphQuery("MATCH(n) RETURN n", "1")
    val jsonHash = twingraphServiceImpl.bulkQuery("orgId", "graphId", twinGraphQuery)
    assertEquals(jsonHash.hash, "graphId:1:MATCH(n) RETURN n".shaHash())
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
