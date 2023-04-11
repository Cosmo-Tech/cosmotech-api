// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.twingraph.api

import com.cosmotech.api.CsmPhoenixService
import com.cosmotech.api.events.TwingraphImportEvent
import com.cosmotech.api.events.TwingraphImportJobInfoRequest
import com.cosmotech.api.exceptions.CsmClientException
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.rbac.CsmRbac
import com.cosmotech.api.rbac.PERMISSION_READ
import com.cosmotech.api.security.coroutine.SecurityCoroutineContext
import com.cosmotech.api.utils.bulkQueryKey
import com.cosmotech.api.utils.redisGraphKey
import com.cosmotech.api.utils.toRedisMetaDataKey
import com.cosmotech.api.utils.zipBytesWithFileNames
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.service.getRbac
import com.cosmotech.twingraph.domain.GraphProperties
import com.cosmotech.twingraph.domain.TwinGraphHash
import com.cosmotech.twingraph.domain.TwinGraphImport
import com.cosmotech.twingraph.domain.TwinGraphImportInfo
import com.cosmotech.twingraph.domain.TwinGraphQuery
import com.cosmotech.twingraph.extension.toJsonString
import com.cosmotech.twingraph.utils.TwingraphUtils
import com.redislabs.redisgraph.ResultSet
import com.redislabs.redisgraph.impl.api.RedisGraph
import java.nio.charset.StandardCharsets.UTF_8
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import redis.clients.jedis.JedisPool
import redis.clients.jedis.ScanParams

const val GRAPH_NAME = "graphName"
const val GRAPH_ROTATION = "graphRotation"

@Service
@Suppress("TooManyFunctions")
class TwingraphServiceImpl(
    private val organizationService: OrganizationApiService,
    private val csmJedisPool: JedisPool,
    private val csmRedisGraph: RedisGraph,
    private val csmRbac: CsmRbac
) : CsmPhoenixService(), TwingraphApiService {

  override fun importGraph(
      organizationId: String,
      twinGraphImport: TwinGraphImport
  ): TwinGraphImportInfo {
    val requestJobId = this.idGenerator.generate(scope = "graphdataimport", prependPrefix = "gdi-")
    val graphImportEvent =
        TwingraphImportEvent(
            this,
            requestJobId,
            organizationId,
            twinGraphImport.graphId,
            twinGraphImport.source.name ?: "",
            twinGraphImport.source.location,
            twinGraphImport.source.path ?: "",
            twinGraphImport.source.type.value,
            twinGraphImport.version)
    this.eventPublisher.publishEvent(graphImportEvent)
    logger.debug("TwingraphImportEventResponse={}", graphImportEvent.response)
    return TwinGraphImportInfo(jobId = requestJobId, graphName = twinGraphImport.graphId)
  }

  override fun jobStatus(organizationId: String, jobId: String): String {
    val twingraphImportJobInfoRequest = TwingraphImportJobInfoRequest(this, jobId, organizationId)
    this.eventPublisher.publishEvent(twingraphImportJobInfoRequest)
    logger.debug("TwingraphImportEventResponse={}", twingraphImportJobInfoRequest.response)
    return twingraphImportJobInfoRequest.response ?: "Unknown"
  }

  @Suppress("SpreadOperator")
  override fun delete(organizationId: String, graphId: String) {
    for (item in getVersions(graphId)) {
      csmRedisGraph.deleteGraph(item)
    }
  }

  override fun findAllTwingraphs(organizationId: String): List<String> {
    val matchingKeys = mutableSetOf<String>()
    csmJedisPool.resource.use { jedis ->
      var nextCursor = ScanParams.SCAN_POINTER_START
      do {
        val scanResult = jedis.scan(nextCursor, ScanParams().match("*"), "graphdata")
        nextCursor = scanResult.cursor
        matchingKeys.addAll(scanResult.result)
      } while (!nextCursor.equals(ScanParams.SCAN_POINTER_START))
    }
    return matchingKeys.toList()
  }

  override fun getGraphMetaData(organizationId: String, graphId: String): Map<String, String> {
    csmJedisPool.resource.use { jedis ->
      if (jedis.exists(graphId.toRedisMetaDataKey())) {
        return jedis.hgetAll(graphId.toRedisMetaDataKey())
      }
      throw CsmResourceNotFoundException("No metadata found for graphId $graphId")
    }
  }

  private fun checkTwinGraphPrerequisites(
      organizationId: String,
      graphId: String,
      twinGraphQuery: TwinGraphQuery
  ): Unit {
    val organization = organizationService.findOrganizationById(organizationId)
    csmRbac.verify(organization.getRbac(), PERMISSION_READ)
    if (!TwingraphUtils.isReadOnlyQuery(twinGraphQuery.query)) {
      throw CsmClientException("Read Only queries authorized only")
    }

    if (twinGraphQuery.version.isNullOrEmpty()) {
      csmJedisPool.resource.use { jedis ->
        twinGraphQuery.version = jedis.hget("${graphId.toRedisMetaDataKey()}", "lastVersion")
        if (twinGraphQuery.version.isNullOrEmpty()) {
          throw CsmResourceNotFoundException(
              "Cannot find lastVersion in ${graphId.toRedisMetaDataKey()}")
        }
      }
    }
    val redisGraphId = redisGraphKey(graphId, twinGraphQuery.version!!)
    csmJedisPool.resource.use { jedis ->
      val redisGraphMatchingKeys = jedis.keys(redisGraphId)
      if (redisGraphMatchingKeys.size == 0) {
        throw CsmResourceNotFoundException("No graph found with id: $redisGraphId")
      }
    }
  }

  override fun query(
      organizationId: String,
      graphId: String,
      twinGraphQuery: TwinGraphQuery
  ): String {
    checkTwinGraphPrerequisites(organizationId, graphId, twinGraphQuery)
    val resultSet =
        csmRedisGraph.query(
            redisGraphKey(graphId, twinGraphQuery.version!!),
            twinGraphQuery.query,
            csmPlatformProperties.twincache.queryTimeout)
    return resultSet.toJsonString()
  }

  override fun updateGraphMetaData(
      organizationId: String,
      graphId: String,
      requestBody: Map<String, String>
  ): Any {
    val organization = organizationService.findOrganizationById(organizationId)
    csmRbac.verify(organization.getRbac(), PERMISSION_READ)

    val graphRotation = requestBody[GRAPH_ROTATION]?.toInt()
    if (graphRotation != null && graphRotation < 1) {
      throw CsmClientException("GraphRotation should be a positive integer")
    }

    csmJedisPool.resource.use { jedis ->
      if (jedis.exists(graphId.toRedisMetaDataKey())) {
        requestBody.filterKeys { it == GRAPH_NAME || it == GRAPH_ROTATION }.forEach { (key, value)
          ->
          jedis.hset(graphId.toRedisMetaDataKey(), key, value)
        }
        return jedis.hgetAll(graphId.toRedisMetaDataKey())
      }
      throw CsmResourceNotFoundException("No metadata found for graphId $graphId")
    }
  }

  override fun bulkQuery(
      organizationId: String,
      graphId: String,
      twinGraphQuery: TwinGraphQuery
  ): TwinGraphHash {
    checkTwinGraphPrerequisites(organizationId, graphId, twinGraphQuery)
    val redisGraphKey = redisGraphKey(graphId, twinGraphQuery.version!!)
    var bulkQueryKey = bulkQueryKey(graphId, twinGraphQuery.query, twinGraphQuery.version!!)
    val twinGraphHash = TwinGraphHash(bulkQueryKey.second)
    csmJedisPool.resource.use { jedis ->
      val keyExists = jedis.exists(bulkQueryKey.first)
      if (keyExists) {
        return twinGraphHash
      }
      val resultSet = csmRedisGraph.query(redisGraphKey, twinGraphQuery.query)

      GlobalScope.launch(SecurityCoroutineContext()) {
        val zip =
            zipBytesWithFileNames(
                mapOf("bulkQuery.json" to resultSet.toJsonString().toByteArray(UTF_8)))
        jedis.setex(bulkQueryKey.first, csmPlatformProperties.twincache.queryBulkTTL, zip)
      }
    }
    return twinGraphHash
  }

  override fun downloadGraph(organizationId: String, graphQueryHash: String): Resource {
    val organization = organizationService.findOrganizationById(organizationId)
    csmRbac.verify(organization.getRbac(), PERMISSION_READ)

    var bulkQueryId = bulkQueryKey(graphQueryHash)
    csmJedisPool.resource.use { jedis ->
      if (!jedis.exists(bulkQueryId)) {
        throw CsmResourceNotFoundException("No graph found with hash: $graphQueryHash  Try later")
      } else if (jedis.ttl(bulkQueryId) < 0) {
        throw CsmResourceNotFoundException(
            "Graph with hash: $graphQueryHash is expired. Try to repeat bulk query")
      }
    }
    val httpServletResponse =
        ((RequestContextHolder.getRequestAttributes() as ServletRequestAttributes).response)
    val contentDisposition =
        ContentDisposition.builder("attachment").filename("TwinGraph-$graphQueryHash.zip").build()
    httpServletResponse!!.setHeader(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
    return ByteArrayResource(csmJedisPool.resource.use { jedis -> jedis.get(bulkQueryId) })
  }

  private fun getVersions(graphId: String): List<String> {
    val matchingKeys = mutableSetOf<String>()
    csmJedisPool.resource.use { jedis ->
      var nextCursor = ScanParams.SCAN_POINTER_START
      do {
        val scanResult = jedis.scan(nextCursor, ScanParams().match("${graphId}:*"), "graphdata")
        nextCursor = scanResult.cursor
        matchingKeys.addAll(scanResult.result)
      } while (!nextCursor.equals(ScanParams.SCAN_POINTER_START))
    }
    return matchingKeys.toList()
  }

  override fun createEntities(
      organizationId: String,
      graphId: String,
      type: String,
      graphProperties: List<GraphProperties>
  ): List<ResultSet> {
    return when (type) {
      "node" ->
          graphProperties.map {
            csmRedisGraph.query(
                graphId, "CREATE (a:${it.type} {id:'${it.name}',${it.params}}) RETURN a")
          }
      "relationship" ->
          graphProperties.map {
            csmRedisGraph.query(
                graphId,
                "MATCH (a),(b) WHERE a.id='${it.source}' AND b.id='${it.target}'" +
                    "CREATE (a)-[r:${it.type} {id:'${it.name}', ${it.params}}]->(b) RETURN r")
          }
      else -> throw CsmResourceNotFoundException("Bad Type : $type")
    }
  }

  override fun getEntities(
      organizationId: String,
      graphId: String,
      type: String,
      requestBody: List<String>
  ): List<ResultSet> {
    return when (type) {
      "node" -> requestBody.map { csmRedisGraph.query(graphId, "MATCH (a {id:'$it'}) RETURN a") }
      "relationship" ->
          requestBody.map { csmRedisGraph.query(graphId, "MATCH ()-[r {id:'$it'}]-() RETURN r") }
      else -> throw CsmResourceNotFoundException("Bad Type : $type")
    }
  }

  override fun updateEntities(
      organizationId: String,
      graphId: String,
      type: String,
      graphProperties: List<GraphProperties>
  ): List<ResultSet> {
    return when (type) {
      "node" ->
          graphProperties.map {
            csmRedisGraph.query(
                graphId,
                "MATCH (a {id:'${it.name}'}) SET a = {id:'${it.name}',${it.params}} RETURN a")
          }
      "relationship" ->
          graphProperties.map {
            csmRedisGraph.query(
                graphId,
                "MATCH ()-[r {id:'${it.name}'}]-() SET r = {id:'${it.name}', ${it.params}} RETURN r")
          }
      else -> throw CsmResourceNotFoundException("Bad Type : $type")
    }
  }

  override fun deleteEntities(
      organizationId: String,
      graphId: String,
      type: String,
      requestBody: List<String>
  ) {
    return when (type) {
      "node" ->
          requestBody.forEach {
            csmRedisGraph.query(graphId, "MATCH (a) WHERE a.id='$it' DELETE a")
          }
      "relationship" ->
          requestBody.forEach {
            csmRedisGraph.query(graphId, "MATCH ()-[r]-() WHERE r.id='$it' DELETE r")
          }
      else -> throw CsmResourceNotFoundException("Bad Type : $type")
    }
  }
}
