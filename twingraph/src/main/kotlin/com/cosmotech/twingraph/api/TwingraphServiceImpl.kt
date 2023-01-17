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
import com.cosmotech.organization.api.OrganizationApiService
import com.cosmotech.organization.services.getRbac
import com.cosmotech.twingraph.domain.TwinGraphImport
import com.cosmotech.twingraph.domain.TwinGraphImportInfo
import com.cosmotech.twingraph.domain.TwinGraphQuery
import com.cosmotech.twingraph.extension.toJsonString
import com.cosmotech.twingraph.utils.TwingraphUtils
import com.redislabs.redisgraph.ResultSet
import com.redislabs.redisgraph.graph_entities.Edge
import com.redislabs.redisgraph.graph_entities.Node
import com.redislabs.redisgraph.impl.api.RedisGraph
import org.springframework.stereotype.Service
import redis.clients.jedis.JedisPool
import redis.clients.jedis.ScanParams

@Service
class TwingraphServiceImpl(
    private val organizationService: OrganizationApiService,
    private val csmJedisPool: JedisPool,
    private val csmRbac: CsmRbac
) : CsmPhoenixService(), TwingraphApiService {

  override fun importGraph(
      organizationId: String,
      twinGraphImport: TwinGraphImport
  ): TwinGraphImportInfo {
    val organization = organizationService.findOrganizationById(organizationId)
    csmRbac.verify(organization.getRbac(), PERMISSION_READ)
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
    val organization = organizationService.findOrganizationById(organizationId)
    csmRbac.verify(organization.getRbac(), PERMISSION_READ)
    val twingraphImportJobInfoRequest = TwingraphImportJobInfoRequest(this, jobId, organizationId)
    this.eventPublisher.publishEvent(twingraphImportJobInfoRequest)
    logger.debug("TwingraphImportEventResponse={}", twingraphImportJobInfoRequest.response)
    return twingraphImportJobInfoRequest.response ?: "Unknown"
  }

  @Suppress("SpreadOperator")
  override fun delete(organizationId: String, graphId: String) {
    val organization = organizationService.findOrganizationById(organizationId)
    csmRbac.verify(organization.getRbac(), PERMISSION_READ)

    csmJedisPool.resource.use { jedis ->
      val matchingKeys = mutableSetOf<String>()
      var nextCursor = ScanParams.SCAN_POINTER_START
      do {
        val scanResult = jedis.scan(nextCursor, ScanParams().match("$graphId*"))
        nextCursor = scanResult.cursor
        matchingKeys.addAll(scanResult.result)
      } while (!nextCursor.equals(ScanParams.SCAN_POINTER_START))

      val count = jedis.del(*matchingKeys.toTypedArray())
      logger.debug("$count keys are removed from Twingraph with prefix $graphId")
    }
  }

  override fun findAllTwingraphs(organizationId: String): List<String> {
    val organization = organizationService.findOrganizationById(organizationId)
    csmRbac.verify(organization.getRbac(), PERMISSION_READ)
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
    val organization = organizationService.findOrganizationById(organizationId)
    csmRbac.verify(organization.getRbac(), PERMISSION_READ)
    val metaDataKey = "${graphId}MetaData"
    csmJedisPool.resource.use { jedis ->
      if (jedis.exists(metaDataKey)) {
        return jedis.hgetAll(metaDataKey)
      }
      throw CsmResourceNotFoundException("No metadata found for graphId $graphId")
    }
  }

  override fun query(
      organizationId: String,
      graphId: String,
      twinGraphQuery: TwinGraphQuery
  ): String {
    val organization = organizationService.findOrganizationById(organizationId)
    csmRbac.verify(organization.getRbac(), PERMISSION_READ)

    if (!TwingraphUtils.isReadOnlyQuery(twinGraphQuery.query)) {
      throw CsmClientException("Read Only queries authorized only")
    }

    if (twinGraphQuery.version.isNullOrEmpty()) {
      csmJedisPool.resource.use { jedis ->
        twinGraphQuery.version = jedis.hget("${graphId}MetaData", "lastVersion")
        if (twinGraphQuery.version.isNullOrEmpty()) {
          throw CsmResourceNotFoundException("Cannot find lastVersion in ${graphId}MetaData")
        }
      }
    }

    val redisGraphId = "${graphId}:${twinGraphQuery.version}"

    csmJedisPool.resource.use { jedis ->
      val redisGraphMatchingKeys = jedis.keys(redisGraphId)
      if (redisGraphMatchingKeys.size == 0) {
        throw CsmResourceNotFoundException("No graph found with id: $redisGraphId")
      }
    }
    val redisGraph = RedisGraph(csmJedisPool)
    val resultSet =
        redisGraph.query(
            redisGraphId, twinGraphQuery.query, csmPlatformProperties.twincache.queryTimeout)

    return resultSet.toJsonString()
  }
}
