// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.twingraph.api

import com.cosmotech.api.CsmPhoenixService
import com.cosmotech.api.events.TwingraphImportEvent
import com.cosmotech.api.events.TwingraphImportJobInfoRequest
import com.cosmotech.api.exceptions.CsmClientException
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.rbac.CsmRbac
import com.cosmotech.api.rbac.PERMISSION_DELETE
import com.cosmotech.api.rbac.PERMISSION_READ
import com.cosmotech.api.security.coroutine.SecurityCoroutineContext
import com.cosmotech.api.utils.ResourceScanner
import com.cosmotech.api.utils.bulkQueryKey
import com.cosmotech.api.utils.formatQuery
import com.cosmotech.api.utils.redisGraphKey
import com.cosmotech.api.utils.toRedisMetaDataKey
import com.cosmotech.api.utils.zipBytesWithFileNames
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.service.getRbac
import com.cosmotech.twingraph.domain.GraphProperties
import com.cosmotech.twingraph.domain.TwinGraphBatchResult
import com.cosmotech.twingraph.domain.TwinGraphHash
import com.cosmotech.twingraph.domain.TwinGraphImport
import com.cosmotech.twingraph.domain.TwinGraphImportInfo
import com.cosmotech.twingraph.domain.TwinGraphQuery
import com.cosmotech.twingraph.extension.toJsonString
import com.cosmotech.twingraph.utils.TwingraphUtils
import com.redislabs.redisgraph.impl.api.RedisGraph
import java.io.InputStream
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
import redis.clients.jedis.exceptions.JedisDataException

const val CSV_SEPARATOR = ","
const val GRAPH_NAME = "graphName"
const val GRAPH_ROTATION = "graphRotation"
const val TYPE_NODE = "node"
const val TYPE_RELATIONSHIP = "relationship"

@Service
@Suppress("TooManyFunctions")
class TwingraphServiceImpl(
    private val organizationService: OrganizationApiService,
    private val csmJedisPool: JedisPool,
    private val csmRedisGraph: RedisGraph,
    private val csmRbac: CsmRbac,
    private val resourceScanner: ResourceScanner,
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
    val organization = organizationService.findOrganizationById(organizationId)
    csmRbac.verify(organization.getRbac(), PERMISSION_DELETE)
    val versions = getRedisKeyList("$graphId:*", "graphdata")
    versions.forEach { csmRedisGraph.deleteGraph(it) }
  }

  override fun findAllTwingraphs(organizationId: String): List<String> {
    val organization = organizationService.findOrganizationById(organizationId)
    csmRbac.verify(organization.getRbac(), PERMISSION_READ)
    return getRedisKeyList("*", "graphdata")
  }

  private fun getRedisKeyList(keyPattern: String, keyType: String): List<String> {
    val matchingKeys = mutableSetOf<String>()
    csmJedisPool.resource.use { jedis ->
      var nextCursor = ScanParams.SCAN_POINTER_START
      do {
        val scanResult = jedis.scan(nextCursor, ScanParams().match(keyPattern), keyType)
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
      twinGraphQuery: TwinGraphQuery,
      isReadOnlyQuery: Boolean
  ) {
    val organization = organizationService.findOrganizationById(organizationId)
    csmRbac.verify(organization.getRbac(), PERMISSION_READ)
    if (isReadOnlyQuery && !TwingraphUtils.isReadOnlyQuery(twinGraphQuery.query)) {
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
    checkTwinGraphPrerequisites(organizationId, graphId, twinGraphQuery, true)
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

  override fun batchQuery(
      organizationId: String,
      graphId: String,
      twinGraphQuery: TwinGraphQuery
  ): TwinGraphHash {
    checkTwinGraphPrerequisites(organizationId, graphId, twinGraphQuery, true)
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

  override fun batchUploadUpdate(
      organizationId: String,
      graphId: String,
      twinGraphQuery: TwinGraphQuery,
      file: Resource
  ): TwinGraphBatchResult {
    checkTwinGraphPrerequisites(organizationId, graphId, twinGraphQuery, false)
    resourceScanner.scanMimeTypes(file, listOf("text/csv", "text/plain"))

    val result = TwinGraphBatchResult(0, 0, mutableListOf())
    processCSV(file.inputStream, twinGraphQuery, result) {
      try {
        csmRedisGraph.query(redisGraphKey(graphId, twinGraphQuery.version!!), it)
        result.processedLines++
      } catch (e: JedisDataException) {
        result.errors.add("#${result.totalLines}: ${e.message}")
      }
    }
    return result
  }

  fun processCSV(
      inputStream: InputStream,
      twinGraphQuery: TwinGraphQuery,
      result: TwinGraphBatchResult,
      actionLambda: (String) -> Unit
  ) {
    var cypherQuery = twinGraphQuery.query
    inputStream.bufferedReader(UTF_8).use { body ->
      val firstLine = body.readLine()
      val headers = firstLine.split(CSV_SEPARATOR).map { it.trim() }
      body.lineSequence().forEachIndexed { index, line ->
        result.totalLines++
        val values = line.split(CSV_SEPARATOR).map { it.trim() }
        val map = headers.zip(values).toMap()
        if (map.size != headers.size) {
          val contentLine = if (line.isBlank()) "Empty line" else line
          result.errors.add("#%d: %s".format(index + 1, contentLine))
          return@forEachIndexed
        }
        actionLambda(cypherQuery.formatQuery(map))
      }
    }
  }

  override fun downloadGraph(organizationId: String, hash: String): Resource {
    val organization = organizationService.findOrganizationById(organizationId)
    csmRbac.verify(organization.getRbac(), PERMISSION_READ)

    var bulkQueryId = bulkQueryKey(hash)
    csmJedisPool.resource.use { jedis ->
      if (!jedis.exists(bulkQueryId)) {
        throw CsmResourceNotFoundException("No graph found with hash: $hash  Try later")
      } else if (jedis.ttl(bulkQueryId) < 0) {
        throw CsmResourceNotFoundException(
            "Graph with hash: $hash is expired. Try to repeat bulk query")
      }
    }
    val httpServletResponse =
        ((RequestContextHolder.getRequestAttributes() as ServletRequestAttributes).response)
    val contentDisposition =
        ContentDisposition.builder("attachment").filename("TwinGraph-$hash.zip").build()
    httpServletResponse!!.setHeader(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
    return ByteArrayResource(csmJedisPool.resource.use { jedis -> jedis.get(bulkQueryId) })
  }

  override fun createEntities(
      organizationId: String,
      graphId: String,
      modelType: String,
      graphProperties: List<GraphProperties>
  ): List<String> {
    return when (modelType) {
      TYPE_NODE ->
          graphProperties.map {
            csmRedisGraph
                .query(graphId, "CREATE (a:${it.type} {id:'${it.name}',${it.params}}) RETURN a")
                .toJsonString()
          }
      TYPE_RELATIONSHIP ->
          graphProperties.map {
            csmRedisGraph
                .query(
                    graphId,
                    "MATCH (a),(b) WHERE a.id='${it.source}' AND b.id='${it.target}'" +
                        "CREATE (a)-[r:${it.type} {id:'${it.name}', ${it.params}}]->(b) RETURN r")
                .toJsonString()
          }
      else -> throw CsmResourceNotFoundException("Bad Type : $modelType")
    }
  }

  override fun getEntities(
      organizationId: String,
      graphId: String,
      modelType: String,
      requestBody: List<String>
  ): List<String> {
    return when (modelType) {
      TYPE_NODE ->
          requestBody.map {
            csmRedisGraph.query(graphId, "MATCH (a {id:'$it'}) RETURN a").toJsonString()
          }
      TYPE_RELATIONSHIP ->
          requestBody.map {
            csmRedisGraph.query(graphId, "MATCH ()-[r {id:'$it'}]->() RETURN r").toJsonString()
          }
      else -> throw CsmResourceNotFoundException("Bad Type : $modelType")
    }
  }

  override fun updateEntities(
      organizationId: String,
      graphId: String,
      modelType: String,
      graphProperties: List<GraphProperties>
  ): List<String> {
    return when (modelType) {
      TYPE_NODE ->
          graphProperties.map {
            csmRedisGraph
                .query(
                    graphId,
                    "MATCH (a {id:'${it.name}'}) SET a = {id:'${it.name}',${it.params}} RETURN a")
                .toJsonString()
          }
      TYPE_RELATIONSHIP ->
          graphProperties.map {
            csmRedisGraph
                .query(
                    graphId,
                    "MATCH ()-[r {id:'${it.name}'}]-() SET r = {id:'${it.name}', ${it.params}} RETURN r")
                .toJsonString()
          }
      else -> throw CsmResourceNotFoundException("Bad Type : $modelType")
    }
  }

  override fun deleteEntities(
      organizationId: String,
      graphId: String,
      modelType: String,
      requestBody: List<String>
  ) {
    return when (modelType) {
      TYPE_NODE ->
          requestBody.forEach {
            csmRedisGraph.query(graphId, "MATCH (a) WHERE a.id='$it' DELETE a")
          }
      TYPE_RELATIONSHIP ->
          requestBody.forEach {
            csmRedisGraph.query(graphId, "MATCH ()-[r]-() WHERE r.id='$it' DELETE r")
          }
      else -> throw CsmResourceNotFoundException("Bad Type : $modelType")
    }
  }
}
