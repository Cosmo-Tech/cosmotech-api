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
import com.cosmotech.organization.azure.getRbac
import com.cosmotech.twingraph.domain.TwinGraphImport
import com.cosmotech.twingraph.domain.TwinGraphImportInfo
import com.cosmotech.twingraph.domain.TwinGraphQuery
import com.cosmotech.twingraph.extension.toJsonString
import com.cosmotech.twingraph.utils.TwingraphUtils
import org.springframework.stereotype.Service
import redis.clients.jedis.UnifiedJedis
import redis.clients.jedis.graph.ResultSet
import redis.clients.jedis.params.ScanParams
import redis.clients.jedis.params.ScanParams.SCAN_POINTER_START

@Service
class TwingraphServiceImpl(
    private val organizationService: OrganizationApiService,
    val jedis: UnifiedJedis,
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
    val matchingKeys = mutableSetOf<String>()
    var nextCursor = SCAN_POINTER_START
    do {
      val scanResult = jedis.scan(nextCursor, ScanParams().match("$graphId*"))
      nextCursor = scanResult.cursor
      matchingKeys.addAll(scanResult.result)
    } while (!nextCursor.equals(SCAN_POINTER_START))

    val count = jedis.del(*matchingKeys.toTypedArray())
    logger.debug("$count keys are removed from Twingraph with prefix $graphId")
  }

  override fun findAllTwingraphs(organizationId: String): List<String> {
    val organization = organizationService.findOrganizationById(organizationId)
    csmRbac.verify(organization.getRbac(), PERMISSION_READ)
    val matchingKeys = mutableSetOf<String>()
    var nextCursor = SCAN_POINTER_START
    do {
      val scanResult = jedis.scan(nextCursor, ScanParams().match("*"), "graphdata")
      nextCursor = scanResult.cursor
      matchingKeys.addAll(scanResult.result)
    } while (!nextCursor.equals(SCAN_POINTER_START))
    return matchingKeys.toList()
  }

  override fun getGraphMetaData(organizationId: String, graphId: String): Map<String, String> {
    val organization = organizationService.findOrganizationById(organizationId)
    csmRbac.verify(organization.getRbac(), PERMISSION_READ)
    val metaDataKey = "${graphId}MetaData"
    if (jedis.exists(metaDataKey)) {
      return jedis.hgetAll(metaDataKey)
    }
    throw CsmResourceNotFoundException("No metadata found for graphId $graphId")
  }

  override fun query(
      organizationId: String,
      twingraphId: String,
      twinGraphQuery: TwinGraphQuery
  ): String {
    val organization = organizationService.findOrganizationById(organizationId)
    csmRbac.verify(organization.getRbac(), PERMISSION_READ)

    if (!TwingraphUtils.isReadOnlyQuery(twinGraphQuery.query)) {
      throw CsmClientException("Read Only queries authorized only")
    }

    if (twinGraphQuery.version.isNullOrEmpty()) {
      twinGraphQuery.version = jedis.hget("${twingraphId}MetaData", "lastVersion")
      if (twinGraphQuery.version.isNullOrEmpty()) {
        throw CsmResourceNotFoundException("Cannot find lastVersion in ${twingraphId}MetaData")
      }
    }

    val redisGraphId = "${twingraphId}:${twinGraphQuery.version}"
    val resultSet: ResultSet =
        jedis.graphQuery(
            redisGraphId, twinGraphQuery.query, csmPlatformProperties.twincache.queryTimeout)

    if (resultSet.size() == 0) {
      throw CsmResourceNotFoundException(
          "TwinGraph empty with given $twingraphId " + "and version ${twinGraphQuery.version}")
    }

    return resultSet.toJsonString()
  }
}
