// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
@file:Suppress("DEPRECATION")

package com.cosmotech.twingraph.metrics

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.events.CsmEventPublisher
import com.cosmotech.api.events.PersistentMetricEvent
import com.cosmotech.api.metrics.PersistentMetric
import com.cosmotech.api.metrics.PersitentMetricType
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import redis.clients.jedis.UnifiedJedis

private const val SERVICE_NAME = "twingraph"
private const val MILLISECONDS_IN_DAY = 86400000

@Service
internal class TwinGraphMetrics(
    private val unifiedJedis: UnifiedJedis,
    private val eventPublisher: CsmEventPublisher,
    private val csmPlatformProperties: CsmPlatformProperties,
) {
  private fun getTwinGraphList(): List<String> {
    return unifiedJedis.graphList()
  }

  // Every 30mn
  @Scheduled(fixedDelay = 3600000)
  fun publishTwinGraphCount() {
    if (!csmPlatformProperties.metrics.enabled) return
    val count = this.getTwinGraphList().size.toDouble()
    val metric =
        PersistentMetric(
            service = SERVICE_NAME,
            name = "twingraph",
            value = count,
            qualifier = "total",
            labels =
                mapOf(
                    "usage" to "licensing",
                ),
            type = PersitentMetricType.GAUGE,
            retention =
                csmPlatformProperties.metrics.downSamplingRetentionDays.toLong() *
                    MILLISECONDS_IN_DAY)
    eventPublisher.publishEvent(PersistentMetricEvent(this, metric))
  }
}
