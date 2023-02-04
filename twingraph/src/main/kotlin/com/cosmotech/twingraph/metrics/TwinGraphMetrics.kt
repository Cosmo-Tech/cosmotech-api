// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.twingraph.metrics

import com.cosmotech.api.events.CsmEventPublisher
import com.cosmotech.api.events.PersistentMetricEvent
import com.cosmotech.api.metrics.PersistentMetric
import com.cosmotech.api.metrics.PersitentMetricType
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import redis.clients.jedis.JedisPool

private const val SERVICE_NAME = "twingraph"

@Service
internal class TwinGraphMetrics(
    private val jedisPool: JedisPool,
    private val eventPublisher: CsmEventPublisher,
) {
  private val logger = LoggerFactory.getLogger(TwinGraphMetrics::class.java)

  private fun getTwinGraphList(): List<String> {
    jedisPool.resource.use { jedis ->
      return jedis.sendCommand { "GRAPH.LIST".toByteArray() } as List<String>
    }
  }

  @Scheduled(fixedDelay = 10000)
  fun publishTwinGraphCount() {
    logger.debug("METRICS: publishTwinGraphCount")
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
        )
    eventPublisher.publishEvent(PersistentMetricEvent(this, metric))
  }
}