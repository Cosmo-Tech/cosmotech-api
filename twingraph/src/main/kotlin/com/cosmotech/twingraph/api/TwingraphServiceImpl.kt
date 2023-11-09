// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.twingraph.api

import com.cosmotech.api.CsmPhoenixService
import com.cosmotech.api.events.TwingraphImportJobInfoRequest
import com.cosmotech.api.exceptions.CsmClientException
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import com.cosmotech.api.exceptions.CsmServerException
import com.cosmotech.api.rbac.CsmRbac
import com.cosmotech.api.rbac.PERMISSION_DELETE
import com.cosmotech.api.rbac.PERMISSION_READ
import com.cosmotech.api.security.coroutine.SecurityCoroutineContext
import com.cosmotech.api.utils.ResourceScanner
import com.cosmotech.api.utils.bulkQueryKey
import com.cosmotech.api.utils.formatQuery
import com.cosmotech.api.utils.getLocalDateNow
import com.cosmotech.api.utils.redisGraphKey
import com.cosmotech.api.utils.toRedisMetaDataKey
import com.cosmotech.api.utils.unzip
import com.cosmotech.api.utils.zipBytesWithFileNames
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.service.getRbac
import com.cosmotech.twingraph.bulk.QueryBuffer
import com.cosmotech.twingraph.domain.GraphProperties
import com.cosmotech.twingraph.domain.TwinGraphBatchResult
import com.cosmotech.twingraph.domain.TwinGraphHash
import com.cosmotech.twingraph.domain.TwinGraphQuery
import com.cosmotech.twingraph.extension.toJsonString
import com.cosmotech.twingraph.utils.TwingraphUtils
import com.redislabs.redisgraph.impl.api.RedisGraph
import java.io.InputStream
import java.nio.charset.StandardCharsets.UTF_8
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVRecord
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

const val GRAPH_NAME = "graphName"
const val GRAPH_ROTATION = "graphRotation"
const val TYPE_NODE = "node"
const val TYPE_RELATIONSHIP = "relationship"
const val NODES_ZIP_FOLDER = "nodes"
const val EDGES_ZIP_FOLDER = "edges"

const val INITIAL_VERSION = "1"
const val DEFAULT_GRAPH_ROTATION = "3"

@Service
@Suppress("TooManyFunctions")
@Deprecated("Use dataset service instead")
class TwingraphServiceImpl(
    private val organizationService: OrganizationApiService,
    private val csmJedisPool: JedisPool,
    private val csmRedisGraph: RedisGraph,
    private val csmRbac: CsmRbac,
    private val resourceScanner: ResourceScanner
) : CsmPhoenixService(), TwingraphApiService {

  override fun createGraph(organizationId: String, graphId: String, body: Resource?) {
    val graphList = mutableListOf<String>()
    findAllTwingraphs(organizationId).forEach { graphList.add(it.split(":").first()) }
    if (graphList.contains(graphId))
        throw CsmServerException("There is already a graph with the id : $graphId")

    csmJedisPool.resource.use { jedis ->
      jedis.hset(
          graphId.toRedisMetaDataKey(),
          mutableMapOf(
              "lastVersion" to INITIAL_VERSION,
              "graphName" to "$graphId:1",
              "graphRotation" to DEFAULT_GRAPH_ROTATION,
              "lastModifiedDate" to getLocalDateNow()))
    }
    if (body != null) {
      val archiverType = ArchiveStreamFactory.detect(body.inputStream.buffered())
      if (ArchiveStreamFactory.ZIP != archiverType) {
        throw IllegalArgumentException(
            "Invalid archive type: '$archiverType'. A Zip Archive is expected.")
      }

      val queryBuffer = QueryBuffer(csmJedisPool.resource, getLastVersion(organizationId, graphId))
      unzip(body.inputStream, listOf(NODES_ZIP_FOLDER, EDGES_ZIP_FOLDER), "csv")
          .sortedByDescending { it.prefix } // Nodes first
          .forEach { node ->
            readCSV(node.content.inputStream()) {
              if (node.prefix == NODES_ZIP_FOLDER) {
                queryBuffer.addNode(node.filename, it)
              } else {
                queryBuffer.addEdge(node.filename, it)
              }
            }
          }
      queryBuffer.send()
    } else {
      // If no zip was given then we create an empty graph by querying one of his non-existent
      // element
      getEntities(organizationId, graphId, TYPE_NODE, listOf("node_a"))
    }
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
    val versions = getRedisKeyList("$graphId:*")
    versions.forEach { csmRedisGraph.deleteGraph(it) }
  }

  override fun findAllTwingraphs(organizationId: String): List<String> {
    val organization = organizationService.findOrganizationById(organizationId)
    csmRbac.verify(organization.getRbac(), PERMISSION_READ)
    return getRedisKeyList("*")
  }

  private fun getRedisKeyList(keyPattern: String, keyType: String = "graphdata"): List<String> {
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
      toCheckReadOnlyQuery: Boolean
  ) {
    val organization = organizationService.findOrganizationById(organizationId)
    csmRbac.verify(organization.getRbac(), PERMISSION_READ)
    if (toCheckReadOnlyQuery && !TwingraphUtils.isReadOnlyQuery(twinGraphQuery.query)) {
      throw CsmClientException("Read Only queries authorized only")
    }

    if (twinGraphQuery.version.isNullOrEmpty()) {
      csmJedisPool.resource.use { jedis ->
        twinGraphQuery.version = jedis.hget(graphId.toRedisMetaDataKey(), "lastVersion")
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
        requestBody
            .filterKeys { it == GRAPH_NAME || it == GRAPH_ROTATION }
            .forEach { (key, value) -> jedis.hset(graphId.toRedisMetaDataKey(), key, value) }
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
    val bulkQueryKey = bulkQueryKey(graphId, twinGraphQuery.query, twinGraphQuery.version!!)
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
      body: Resource
  ): TwinGraphBatchResult {
    checkTwinGraphPrerequisites(organizationId, graphId, twinGraphQuery, false)
    resourceScanner.scanMimeTypes(body, listOf("text/csv", "text/plain"))

    val result = TwinGraphBatchResult(0, 0, mutableListOf())
    processCSVBatch(body.inputStream, twinGraphQuery, result) {
      try {
        csmRedisGraph.query(redisGraphKey(graphId, twinGraphQuery.version!!), it)
        result.processedLines++
      } catch (e: JedisDataException) {
        result.errors.add("#${result.totalLines}: ${e.message}")
      }
    }
    return result
  }

  private fun readCSV(
      inputStream: InputStream,
      result: TwinGraphBatchResult? = null,
      actionLambda: (Map<String, String>) -> Unit
  ): Map<String, String> {
    var map = mapOf<String, String>()
    inputStream.bufferedReader().use { reader ->
      val csvFormat: CSVFormat = CSVFormat.DEFAULT.builder().setHeader().setTrim(true).build()

      val records: Iterable<CSVRecord> = csvFormat.parse(reader)
      records.forEach { record ->
        map = record.parser.headerNames.zip(record.values()).toMap()
        actionLambda(map)
        result?.let { result.totalLines++ }
      }
    }
    return map
  }

  fun processCSVBatch(
      inputStream: InputStream,
      twinGraphQuery: TwinGraphQuery,
      result: TwinGraphBatchResult,
      actionLambda: (String) -> Unit
  ) = readCSV(inputStream, result) { actionLambda(twinGraphQuery.query.formatQuery(it)) }

  override fun downloadGraph(organizationId: String, hash: String): Resource {
    val organization = organizationService.findOrganizationById(organizationId)
    csmRbac.verify(organization.getRbac(), PERMISSION_READ)

    val bulkQueryId = bulkQueryKey(hash)
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
      type: String,
      graphProperties: List<GraphProperties>
  ): String {
    var result = ""
    updateGraphMetaData(organizationId, graphId, mapOf("lastModifiedDate" to getLocalDateNow()))
    when (type) {
      TYPE_NODE ->
          graphProperties.forEach {
            result +=
                csmRedisGraph
                    .query(
                        getLastVersion(organizationId, graphId),
                        "CREATE (a:${it.type} {id:'${it.name}',${it.params}}) RETURN a")
                    .toJsonString()
          }
      TYPE_RELATIONSHIP ->
          graphProperties.forEach {
            result +=
                csmRedisGraph
                    .query(
                        getLastVersion(organizationId, graphId),
                        "MATCH (a),(b) WHERE a.id='${it.source}' AND b.id='${it.target}'" +
                            "CREATE (a)-[r:${it.type} {id:'${it.name}', ${it.params}}]->(b) RETURN r")
                    .toJsonString()
          }
      else -> throw CsmResourceNotFoundException("Bad Type : $type")
    }
    return result
  }

  override fun getEntities(
      organizationId: String,
      graphId: String,
      type: String,
      ids: List<String>
  ): String {
    var result = ""
    updateGraphMetaData(organizationId, graphId, mapOf("lastModifiedDate" to getLocalDateNow()))
    when (type) {
      TYPE_NODE ->
          ids.forEach {
            result +=
                csmRedisGraph
                    .query(
                        getLastVersion(organizationId, graphId),
                        "MATCH (a) WHERE a.id='$it' RETURN a")
                    .toJsonString()
          }
      TYPE_RELATIONSHIP ->
          ids.forEach {
            result +=
                csmRedisGraph
                    .query(
                        getLastVersion(organizationId, graphId),
                        "MATCH ()-[r]->() WHERE r.id='$it' RETURN r")
                    .toJsonString()
          }
      else -> throw CsmResourceNotFoundException("Bad Type : $type")
    }
    return result
  }

  override fun updateEntities(
      organizationId: String,
      graphId: String,
      type: String,
      graphProperties: List<GraphProperties>
  ): String {
    var result = ""
    updateGraphMetaData(organizationId, graphId, mapOf("lastModifiedDate" to getLocalDateNow()))
    when (type) {
      TYPE_NODE ->
          graphProperties.forEach {
            result +=
                csmRedisGraph
                    .query(
                        getLastVersion(organizationId, graphId),
                        "MATCH (a {id:'${it.name}'}) SET a = {id:'${it.name}',${it.params}} RETURN a")
                    .toJsonString()
          }
      TYPE_RELATIONSHIP ->
          graphProperties.forEach {
            result +=
                csmRedisGraph
                    .query(
                        getLastVersion(organizationId, graphId),
                        "MATCH ()-[r {id:'${it.name}'}]-() SET r = {id:'${it.name}', ${it.params}} RETURN r")
                    .toJsonString()
          }
      else -> throw CsmResourceNotFoundException("Bad Type : $type")
    }
    return result
  }

  override fun deleteEntities(
      organizationId: String,
      graphId: String,
      modelType: String,
      requestBody: List<String>
  ) {
    updateGraphMetaData(organizationId, graphId, mapOf("lastModifiedDate" to getLocalDateNow()))
    return when (modelType) {
      TYPE_NODE ->
          requestBody.forEach {
            csmRedisGraph.query(
                getLastVersion(organizationId, graphId), "MATCH (a) WHERE a.id='$it' DELETE a")
          }
      TYPE_RELATIONSHIP ->
          requestBody.forEach {
            csmRedisGraph.query(
                getLastVersion(organizationId, graphId),
                "MATCH ()-[r]-() WHERE r.id='$it' DELETE r")
          }
      else -> throw CsmResourceNotFoundException("Bad Type : $modelType")
    }
  }

  fun getLastVersion(organizationId: String, graphId: String): String {
    val graphMetadata = getGraphMetaData(organizationId, graphId)
    return graphId + ":" + graphMetadata["lastVersion"].toString()
  }
}
